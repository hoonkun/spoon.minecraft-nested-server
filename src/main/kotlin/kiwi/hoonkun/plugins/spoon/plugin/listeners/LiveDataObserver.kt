package kiwi.hoonkun.plugins.spoon.plugin.listeners

import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.server.LiveDataType
import kiwi.hoonkun.plugins.spoon.server.PlayerMoveData
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import io.ktor.server.websocket.sendSerialized
import kiwi.hoonkun.plugins.spoon.server.PlayerConnectData
import kiwi.hoonkun.plugins.spoon.server.PlayerDisconnectData
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class LiveDataDelays {
    val onPlayerMove = mutableMapOf<String, Long>()
}

class LiveDataCache {
    val onPlayerMove = mutableMapOf<String, PlayerMoveData>()
}

class LiveDataObserver(private val parent: Main): Listener {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val delays = LiveDataDelays()

    private val cache = LiveDataCache()

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
            parent.spoonSocketConnections
                .filter { it.subscribed.contains(LiveDataType.PlayerMove) }
                .forEach { it.session.sendSerialized(data) }
        }
    }

    @EventHandler
    fun onPlayerConnect(event: PlayerJoinEvent) {
        val data = PlayerConnectData(
            type = LiveDataType.PlayerConnect,
            player = SpoonPlayer.bukkit(event.player)
        )

        scope.launch {
            parent.spoonSocketConnections
                .filter { it.subscribed.contains(LiveDataType.PlayerConnect) }
                .forEach { it.session.sendSerialized(data) }
        }
    }

    @EventHandler
    fun onPlayerDisconnect(event: PlayerQuitEvent) {
        val data = PlayerDisconnectData(
            type = LiveDataType.PlayerDisconnect,
            playerId = event.player.playerProfile.uniqueId.toString()
        )

        scope.launch {
            parent.spoonSocketConnections
                .filter { it.subscribed.contains(LiveDataType.PlayerDisconnect) }
                .forEach { it.session.sendSerialized(data) }
        }
    }

}