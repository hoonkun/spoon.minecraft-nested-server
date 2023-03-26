package kiwi.hoonkun.plugins.spoon.server.structures

import kiwi.hoonkun.plugins.spoon.extensions.spoon
import kotlinx.serialization.Serializable
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player


@Serializable
data class SpoonLocation(
    val x: Double,
    val y: Double,
    val z: Double,
    val environment: String
) {
    companion object {
        fun bukkit(it: Location): SpoonLocation =
            SpoonLocation(it.x, it.y, it.z, it.world?.environment.spoon())
    }
}

@Serializable
data class SpoonOnlinePlayer(
    val uniqueId: String,
    val name: String,
    val gameMode: String,
    val health: Double,
    val level: Int,
    val exp: Float,
    val location: SpoonLocation
) {
    companion object {
        fun bukkit(it: Player): SpoonOnlinePlayer =
            SpoonOnlinePlayer(
                uniqueId = it.playerProfile.uniqueId.toString(),
                name = it.name, gameMode = it.gameMode.name, health = it.health, level = it.level, exp = it.exp,
                location = SpoonLocation.bukkit(it.location)
            )
    }
}

@Serializable
data class SpoonOfflinePlayer(
    val uniqueId: String,
    val name: String,
) {
    companion object {
        fun bukkit(it: OfflinePlayer): SpoonOfflinePlayer =
            SpoonOfflinePlayer(
                uniqueId = it.playerProfile.uniqueId.toString(), name = it.name ?: "unknown"
            )
    }
}