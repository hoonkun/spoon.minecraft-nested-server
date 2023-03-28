package kiwi.hoonkun.plugins.spoon.plugin.listeners

import kiwi.hoonkun.plugins.spoon.Main
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import io.ktor.server.websocket.sendSerialized
import kiwi.hoonkun.plugins.spoon.extensions.spoon
import kiwi.hoonkun.plugins.spoon.plugin.core.TerrainSurfaceGenerator
import kiwi.hoonkun.plugins.spoon.server.*
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonOfflinePlayer
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonOnlinePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import kotlin.concurrent.fixedRateTimer
import kotlin.math.absoluteValue

class LiveDataDelays {
    val onPlayerLocation = mutableMapOf<String, Long>()
    val onPlayerView = mutableMapOf<String, Long>()
}

class LiveDataCache {
    val onPlayerMove = mutableMapOf<String, PlayerMoveData>()
    val onPlayerView = mutableMapOf<String, Float>()
    var onDaylightCycle: Long = 0L
    val onPlayerHealth = mutableMapOf<String, Double>()
    val onPlayerExp = mutableMapOf<String, Pair<Int, Float>>()
    val onTerrain = mutableMapOf<TerrainRequest, TerrainResponse>()
}

class LiveDataObserver(private val parent: Main): Listener {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val defaultObservingThread = Thread {
        fixedRateTimer(name = "default-rapid", period = 500) {
            CoroutineScope(Dispatchers.Default).launch {
                observeHealth()
                observeExp()
            }
        }
        fixedRateTimer(name = "default-normal", period = 5000) {
            CoroutineScope(Dispatchers.Default).launch {
                observeTime()
            }
        }
    }
    private val heavyObservingThread = Thread {
        fixedRateTimer(name = "heavy", period = 3000) {
            CoroutineScope(Dispatchers.Default).launch {
                val responses = observeTerrain()
                responses.forEach { (connection, response) ->
                    response?.let {
                        connection.session.sendSerialized(TerrainSubscriptionData(type = "Terrain", terrain = it))
                    }
                }
            }
        }
    }

    private val delays = LiveDataDelays()

    val cache = LiveDataCache()

    fun observe() {
        defaultObservingThread.start()
        heavyObservingThread.start()
    }

    private suspend fun observeTime() {
        val overworld = parent.overworld ?: return
        if ((cache.onDaylightCycle - overworld.time).absoluteValue > 120) {
            parent.subscribers(LiveDataType.DaylightCycle)
                .forEach { it.session.sendSerialized(DaylightCycleData(type = LiveDataType.DaylightCycle, time = overworld.time)) }
        }
        cache.onDaylightCycle = overworld.time
    }

    private suspend fun observeHealth() {
        parent.server.onlinePlayers.forEach { player ->
            val playerId = player.playerProfile.uniqueId.toString()
            if (player.health == cache.onPlayerHealth[playerId]) return@forEach
            parent.subscribers(LiveDataType.PlayerHealth)
                .forEach { it.session.sendSerialized(PlayerHealthData(LiveDataType.PlayerHealth, playerId, player.health)) }

            cache.onPlayerHealth[playerId] = player.health
        }
    }

    private suspend fun observeExp() {
        parent.server.onlinePlayers.forEach { player ->
            val playerId = player.playerProfile.uniqueId.toString()
            val (level, exp) = cache.onPlayerExp[playerId] ?: (0 to 0)
            if (player.level == level && player.exp == exp) return@forEach
            parent.subscribers(LiveDataType.PlayerExp)
                .forEach { it.session.sendSerialized(PlayerExpData(LiveDataType.PlayerExp, playerId, player.level, player.exp)) }

            cache.onPlayerExp[playerId] = player.level to player.exp
        }
    }

    private suspend fun observeTerrain(): Map<Connection, TerrainResponse?> {
        val cached = mutableMapOf<TerrainRequest, TerrainResponse>()

        if (cache.onTerrain.keys.size > 15) {
            cache.onTerrain.clear()
        }

        return parent.subscribers(LiveDataType.Terrain, true)
            .mapNotNull { conn -> conn.extras[LiveDataType.Terrain]?.let { conn to it } }
            .associate { (connection, extra) ->
                val payload = Json.decodeFromString<TerrainRequest>(extra)

                val already = cached[payload]
                if (already != null) {
                    if (cache.onTerrain[payload] == already)
                        return@associate connection to null
                    return@associate connection to already
                }

                val world = when (payload.environment) {
                    "overworld" -> parent.overworld
                    "the_nether" -> parent.theNether
                    "the_end" -> parent.theEnd
                    else -> null
                } ?: return@associate connection to null

                val new = parent.mutex.withLock {
                    TerrainSurfaceGenerator.generate(parent, world, payload.scale, payload.center, payload.limit)
                }

                if (cache.onTerrain[payload] == new) return@associate connection to null

                cache.onTerrain[payload] = new
                cached[payload] = new

                connection to new
            }
    }

    fun unobserve() {
        defaultObservingThread.interrupt()
        heavyObservingThread.interrupt()
    }

    @EventHandler
    fun onPlayerGameMode(event: PlayerGameModeChangeEvent) {
        scope.launch {
            parent.subscribers(LiveDataType.PlayerGameMode)
                .forEach {
                    it.session.sendSerialized(
                        PlayerGameModeData(
                            LiveDataType.PlayerGameMode,
                            event.player.playerProfile.uniqueId.toString(),
                            event.newGameMode
                        )
                    )
                }
        }
    }

    @EventHandler
    fun onPlayerLocation(event: PlayerMoveEvent) {
        val current = System.currentTimeMillis()
        val playerUID = event.player.uniqueId.toString()
        if (current - (delays.onPlayerLocation[playerUID] ?: 0) < 225) return

        val location = event.player.location

        if (cache.onPlayerMove[playerUID].let { it != null && it.location[0] == location.x && it.location[1] == location.y && it.location[2] == location.z }) return

        val data = PlayerMoveData(
            type = LiveDataType.PlayerLocation,
            playerId = playerUID,
            location = listOf(location.x, location.y, location.z),
            environment = location.world?.environment.spoon()
        )

        cache.onPlayerMove[playerUID] = data

        delays.onPlayerLocation[playerUID] = current

        scope.launch {
            parent.subscribers(LiveDataType.PlayerLocation).forEach { it.session.sendSerialized(data) }
        }
    }

    @EventHandler
    fun onPlayerView(event: PlayerMoveEvent) {
        val current = System.currentTimeMillis()
        val playerUID = event.player.uniqueId.toString()
        if (current - (delays.onPlayerView[playerUID] ?: 0) < 150) return

        val new = event.player.location.yaw

        if (cache.onPlayerView[playerUID] == new) return

        val data = PlayerViewData(
            type = LiveDataType.PlayerView,
            playerId = playerUID,
            yaw = new
        )

        cache.onPlayerView[playerUID] = new
        delays.onPlayerView[playerUID] = current

        scope.launch {
            parent.subscribers(LiveDataType.PlayerView).forEach { it.session.sendSerialized(data) }
        }
    }

    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val location = event.to ?: return
        val data = PlayerMoveData(
            type = LiveDataType.PlayerLocation,
            playerId = event.player.playerProfile.uniqueId.toString(),
            location = listOf(location.x, location.y, location.z),
            environment = location.world?.environment.spoon()
        )

        scope.launch {
            parent.subscribers(LiveDataType.PlayerLocation).forEach { it.session.sendSerialized(data) }
        }
    }

    @EventHandler
    fun onPlayerConnect(event: PlayerJoinEvent) {
        val data = PlayerConnectData(
            type = LiveDataType.PlayerConnect,
            player = SpoonOnlinePlayer.bukkit(event.player)
        )

        scope.launch {
            parent.subscribers(LiveDataType.PlayerConnect).forEach { it.session.sendSerialized(data) }
        }
    }

    @EventHandler
    fun onPlayerDisconnect(event: PlayerQuitEvent) {
        val data = PlayerDisconnectData(
            type = LiveDataType.PlayerDisconnect,
            player = SpoonOfflinePlayer.bukkit(event.player)
        )

        scope.launch {
            parent.subscribers(LiveDataType.PlayerDisconnect).forEach { it.session.sendSerialized(data) }
        }
    }

}