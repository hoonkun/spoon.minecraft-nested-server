package kiwi.hoonkun.plugins.spoon.plugin.listeners

import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.server.LiveDataType
import kiwi.hoonkun.plugins.spoon.server.PlayerMoveData
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import io.ktor.server.websocket.sendSerialized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LiveDataDelays {
    val onPlayerMove = mutableMapOf<String, Long>()
}

class LiveDataObserver(private val parent: Main): Listener {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val delays = LiveDataDelays()

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val current = System.currentTimeMillis()
        val playerUID = event.player.uniqueId.toString()
        if (current - (delays.onPlayerMove[playerUID] ?: 0) < 1000) return

        val data = PlayerMoveData(playerUID, event.player.location.x, event.player.location.y, event.player.location.z)

        delays.onPlayerMove[playerUID] = current

        scope.launch {
            parent.spoonSocketConnections
                .filter { it.subscribed.contains(LiveDataType.PlayerMove) }
                .forEach { it.session.sendSerialized(data) }
        }
    }

}