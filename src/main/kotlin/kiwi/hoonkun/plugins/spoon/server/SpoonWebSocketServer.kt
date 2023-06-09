package kiwi.hoonkun.plugins.spoon.server

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.extensions.spoon
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonOfflinePlayer
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonOnlinePlayer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.bukkit.GameMode
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class Connection(val session: DefaultWebSocketServerSession) {
    companion object {
        val lastId = AtomicInteger(0)
    }
    val name = "observer-${lastId.getAndIncrement()}"
    val subscribed = mutableSetOf<String>()
    val extras = mutableMapOf<String, String?>()

    var authorized = false
    var username: String? = null

    suspend fun initialData(parent: Main, which: String, extra: String?) {
        if (which == LiveDataType.Terrain && !authorized) return

        when (which) {
            LiveDataType.PlayerLocation -> {
                parent.server.onlinePlayers
                    .find { it.playerProfile.uniqueId.toString() == extra }
                    ?.let {
                        session.sendSerialized(
                            PlayerMoveData(
                                type = LiveDataType.PlayerLocation,
                                playerId = it.playerProfile.uniqueId.toString(),
                                location = listOf(it.location.x, it.location.y, it.location.z),
                                environment = it.location.world?.environment.spoon()
                            )
                        )
                    }
            }
            LiveDataType.PlayerView -> {
                parent.server.onlinePlayers
                    .find { it.playerProfile.uniqueId.toString() == extra }
                    ?.let {
                        session.sendSerialized(
                            PlayerViewData(
                                type = LiveDataType.PlayerView,
                                playerId = it.playerProfile.uniqueId.toString(),
                                yaw = it.location.yaw
                            )
                        )
                    }
            }
            LiveDataType.DaylightCycle -> {
                val overworld = parent.overworld ?: return
                session.sendSerialized(DaylightCycleData(type = LiveDataType.DaylightCycle, time = overworld.time))
            }
        }
    }
}

class LiveDataType {
    companion object {
        const val PlayerLocation = "PlayerLocation"
        const val PlayerView = "PlayerView"
        const val PlayerConnect = "PlayerConnect"
        const val PlayerDisconnect = "PlayerDisconnect"
        const val DaylightCycle = "DaylightCycle"
        const val PlayerHealth = "PlayerHealth"
        const val PlayerExp = "PlayerExp"
        const val PlayerGameMode = "PlayerGameMode"
        const val Terrain = "Terrain"
        const val CommandResponse = "CommandResponse"
        const val PlayerInventory = "PlayerInventory"
    }
}

@Serializable
data class SocketInitializeResponse(val type: String, val identifier: String)

@Serializable
data class LiveDataSubscribeRequest(val which: List<String>, val operation: String, val extra: Map<String, String>? = null)

@Serializable
data class PlayerMoveData(val type: String, val playerId: String, val location: List<Double>, val environment: String)

@Serializable
data class PlayerViewData(val type: String, val playerId: String, val yaw: Float)

@Serializable
data class PlayerConnectData(val type: String, val player: SpoonOnlinePlayer)

@Serializable
data class PlayerDisconnectData(val type: String, val player: SpoonOfflinePlayer)

@Serializable
data class DaylightCycleData(val type: String, val time: Long)

@Serializable
data class PlayerHealthData(val type: String, val playerId: String, val health: Double)

@Serializable
data class PlayerExpData(val type: String, val playerId: String, val level: Int, val exp: Float)

@Serializable
data class PlayerGameModeData(val type: String, val playerId: String, val gameMode: GameMode)

@Serializable
data class TerrainSubscriptionData(val type: String, val terrain: TerrainResponse)

@Serializable
data class RunCommandResponse(val type: String, val response: String)

@Serializable
data class PlayerItem(val key: String, val amount: Int)
@Serializable
data class PlayerInventoryData(val type: String, val playerId: String, val inventory: List<PlayerItem?>)

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
                            thisConnection.subscribed.addAll(request.which)
                            request.which.forEach { thisConnection.extras[it] = request.extra?.get(it) }
                        }
                        "unsubscribe" -> {
                            thisConnection.subscribed.removeAll(request.which.toSet())
                        }
                        "initial_data_request" -> {
                            request.which.forEach { thisConnection.initialData(parent, it, request.extra?.get(it)) }
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
