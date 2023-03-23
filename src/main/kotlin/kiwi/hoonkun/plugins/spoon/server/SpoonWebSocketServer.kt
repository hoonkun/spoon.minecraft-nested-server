package kiwi.hoonkun.plugins.spoon.server

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonPlayer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class Connection(val session: DefaultWebSocketServerSession) {
    companion object {
        val lastId = AtomicInteger(0)
    }
    val name = "observer-${lastId.getAndIncrement()}"
    val subscribed = mutableSetOf<String>()

    suspend fun init(parent: Main, which: String) {
        when (which) {
            LiveDataType.PlayerMove -> {
                parent.server.onlinePlayers.forEach {
                    session.sendSerialized(
                        PlayerMoveData(
                            type = LiveDataType.PlayerMove,
                            playerId = it.playerProfile.uniqueId.toString(),
                            x = it.location.x,
                            y = it.location.y,
                            z = it.location.z,
                        )
                    )
                }
            }
        }
    }
}

class LiveDataType {
    companion object {
        const val PlayerMove = "PlayerMove"
        const val PlayerConnect = "PlayerConnect"
        const val PlayerDisconnect = "PlayerDisconnect"
    }
}

@Serializable
data class SocketInitializeResponse(val type: String, val identifier: String)

@Serializable
data class LiveDataSubscribeRequest(val which: String, val operation: String)

@Serializable
data class PlayerMoveData(val type: String, val playerId: String, val x: Double, val y: Double, val z: Double)

@Serializable
data class PlayerConnectData(val type: String, val player: SpoonPlayer)

@Serializable
data class PlayerDisconnectData(val type: String, val playerId: String)

fun Application.websocketServer(parent: Main) {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }

    routing {
        webSocket("/livedata") {
            val thisConnection = Connection(this)
            parent.spoonSocketConnections += thisConnection

            try {
                sendSerialized(SocketInitializeResponse("Connected", thisConnection.name))
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val request = Json.decodeFromString<LiveDataSubscribeRequest>(frame.readText())
                    when (request.operation) {
                        "subscribe" -> {
                            thisConnection.subscribed.add(request.which)
                        }
                        "unsubscribe" -> {
                            thisConnection.subscribed.remove(request.which)
                        }
                        "initial_data_request" -> {
                            thisConnection.init(parent, request.which)
                        }
                    }
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
            } finally {
                parent.spoonSocketConnections -= thisConnection
            }
        }
    }
}
