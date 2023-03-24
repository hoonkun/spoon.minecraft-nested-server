package kiwi.hoonkun.plugins.spoon.plugin.listeners

import kiwi.hoonkun.plugins.spoon.Main
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import io.ktor.server.websocket.sendSerialized
import kiwi.hoonkun.plugins.spoon.extensions.spoon
import kiwi.hoonkun.plugins.spoon.server.*
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonOfflinePlayer
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonOnlinePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
                delay(500)
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

    fun unobserve() {
        state = "idle"
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