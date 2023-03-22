package kiwi.hoonkun.plugins.spoon.server.structures

import kotlinx.serialization.Serializable
import org.bukkit.Location
import org.bukkit.entity.Player


@Serializable
data class SpoonLocation(
    val x: Double,
    val y: Double,
    val z: Double
) {
    companion object {
        fun bukkit(it: Location): SpoonLocation =
            SpoonLocation(it.x, it.y, it.z)
    }
}

@Serializable
data class SpoonPlayer(
    val userId: String,
    val name: String,
    val texture: String?,
    val gameMode: String,
    val health: Double,
    val level: Int,
    val exp: Float,
    val location: SpoonLocation
) {
    companion object {
        fun bukkit(it: Player): SpoonPlayer =
            SpoonPlayer(
                userId = it.playerProfile.uniqueId.toString(),
                name = it.name, gameMode = it.gameMode.name, health = it.health, level = it.level, exp = it.exp,
                texture = it.playerProfile.textures.skin?.toExternalForm(),
                location = SpoonLocation.bukkit(it.location)
            )
    }
}

@Serializable
data class SpoonCommonPlayer(
    val userId: String,
    val name: String,
    val texture: String?,
    val gameMode: String
) {
    companion object {
        fun bukkit(it: Player): SpoonCommonPlayer =
            SpoonCommonPlayer(
                userId = it.playerProfile.uniqueId.toString(), name = it.name, gameMode = it.gameMode.name,
                texture = it.playerProfile.textures.skin?.toExternalForm()
            )
    }
}