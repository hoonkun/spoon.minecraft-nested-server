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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import kotlin.math.absoluteValue

class LiveDataDelays {
    val onPlayerMove = mutableMapOf<String, Long>()
}

class LiveDataCache {
    val onPlayerMove = mutableMapOf<String, PlayerMoveData>()
    var onDaylightCycle: Long = 0L
    val onPlayerHealth = mutableMapOf<String, Double>()
    val onPlayerExp = mutableMapOf<String, Pair<Int, Float>>()
}

class LiveDataObserver(private val parent: Main): Listener {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val delays = LiveDataDelays()

    private val cache = LiveDataCache()

    private var state = "idle"

    fun observe() {
        state = "observing"
        scope.launch {
            while (state == "observing") {
                observeTime()
                observeHealth()
                observeExp()
                delay(500)
            }
        }
        scope.launch {
            while (state == "observing") {
                observeTerrain()
                delay(750)
            }
        }
    }

    private suspend fun observeTime() {
        val overworld = parent.overworld ?: return
        if ((cache.onDaylightCycle - overworld.time).absoluteValue > 20) {
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

    private suspend fun observeTerrain() {
        val cached = mutableMapOf<String, TerrainResponse>()
        parent.subscribers(LiveDataType.Terrain).mapNotNull { conn -> conn.extras[LiveDataType.Terrain]?.let { conn to it } }
            .forEach { (connection, extra) ->
                val already = cached[extra]
                if (already != null) {
                    connection.session.sendSerialized(already)
                    return
                }

                val payload = Json.decodeFromString<TerrainRequest>(extra)
                val world = when (payload.environment) {
                    "overworld" -> parent.overworld
                    "the_nether" -> parent.theNether
                    "the_end" -> parent.theEnd
                    else -> null
                } ?: return
                val new = TerrainSurfaceGenerator.generate(parent, world, payload.scale, payload.center, payload.limit)
                connection.session.sendSerialized(TerrainSubscriptionData(type = "Terrain", terrain = new))
                cached[extra] = new
            }
    }

    fun unobserve() {
        state = "idle"
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
    fun onPlayerMove(event: PlayerMoveEvent) {
        val current = System.currentTimeMillis()
        val playerUID = event.player.uniqueId.toString()
        if (current - (delays.onPlayerMove[playerUID] ?: 0) < 150) return

        val location = event.player.location

        if (cache.onPlayerMove[playerUID].let { it != null && it.x == location.x && it.z == location.z }) return

        val data = PlayerMoveData(
            type = LiveDataType.PlayerMove,
            playerId = playerUID,
            x = location.x,
            y = location.y,
            z = location.z
        )

        cache.onPlayerMove[playerUID] = data

        delays.onPlayerMove[playerUID] = current

        scope.launch {
            parent.subscribers(LiveDataType.PlayerMove).forEach { it.session.sendSerialized(data) }
        }
    }

    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val location = event.to ?: return
        val data = PlayerMoveData(
            type = LiveDataType.PlayerMove,
            playerId = event.player.playerProfile.uniqueId.toString(),
            x = location.x,
            y = location.y,
            z = location.z
        )

        scope.launch {
            parent.subscribers(LiveDataType.PlayerMove).forEach { it.session.sendSerialized(data) }
        }

        if (event.from.world?.environment == event.to?.world?.environment) return

        val portalData = PlayerPortalData(
            type = LiveDataType.PlayerPortal,
            playerId = event.player.playerProfile.uniqueId.toString(),
            into = event.to?.world?.environment.spoon()
        )
        scope.launch {
            parent.subscribers(LiveDataType.PlayerPortal).forEach { it.session.sendSerialized(portalData) }
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