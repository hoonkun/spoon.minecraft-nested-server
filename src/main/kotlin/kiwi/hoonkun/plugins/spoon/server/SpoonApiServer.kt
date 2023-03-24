package kiwi.hoonkun.plugins.spoon.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.extensions.forEach
import kiwi.hoonkun.plugins.spoon.server.auth.respondJWT
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonOfflinePlayer
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonOnlinePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.bukkit.HeightMap
import org.bukkit.World.Environment
import org.bukkit.block.Block
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.URL
import javax.imageio.ImageIO


fun Application.apiServer(parent: Main) {
    val prefix = "/api"

    routing {
        post("/auth") { respondJWT(parent) }
        authenticate(optional = true) {
            get("/me") { call.respond(call.principal<JWTPrincipal>()!!.payload.claims.getValue("username").asString()) }
            get("$prefix/hello") { hello() }
            get("$prefix/players/online") { onlinePlayers(parent) }
            get("$prefix/players/offline") { offlinePlayers(parent) }
            get("$prefix/players/graphic/{userId}") { userSkin(parent) }
        }
        authenticate {
            post("$prefix/run") { runCommand(parent) }
            post("$prefix/terrain") { terrain(parent) }
            get("$prefix/logs") { logs(parent) }
        }
    }
}

suspend fun PipelineContext<Unit, ApplicationCall>.hello() {
    val principal = call.principal<JWTPrincipal>()
    if (principal != null) {
        val username = principal.payload.claims.getValue("username").asString()
        call.respondText("Hello ${username}, I'm spoon api server!")
    } else {
        call.respondText("Hello, I'm spoon api server!")
    }
}

suspend fun PipelineContext<Unit, ApplicationCall>.onlinePlayers(parent: Main) {
    val players = parent.server.onlinePlayers.map { SpoonOnlinePlayer.bukkit(it) }
    call.respond(players)
}

suspend fun PipelineContext<Unit, ApplicationCall>.offlinePlayers(parent: Main) {
    val players = parent.server.offlinePlayers.map { SpoonOfflinePlayer.bukkit(it) }
    call.respond(players)
}

@Serializable
data class RunCommandRequest(val command: String)
suspend fun PipelineContext<Unit, ApplicationCall>.runCommand(parent: Main) {
    val data = call.receive<RunCommandRequest>()
    parent.server.scheduler.runTask(parent, Runnable {
        parent.server.dispatchCommand(parent.server.consoleSender, data.command)
    })
    call.respond(HttpStatusCode.OK)
}

@Serializable
data class SpoonLog(val time: Long, val message: String)
suspend fun PipelineContext<Unit, ApplicationCall>.logs(parent: Main) {
    call.respond(parent.logs)
}

suspend fun PipelineContext<Unit, ApplicationCall>.userSkin(parent: Main) {
    val userId = call.parameters["userId"]

    if (userId.isNullOrEmpty()) {
        call.respond(HttpStatusCode.BadRequest, "no userId passed to url.")
        return
    }

    val onlinePlayer = parent.server.onlinePlayers.find { it.playerProfile.uniqueId.toString() == userId }
    val offlinePlayer = parent.server.offlinePlayers.find { it.playerProfile.uniqueId.toString() == userId }

    if (onlinePlayer == null && offlinePlayer == null) {
        call.respond(HttpStatusCode.NotFound, "no player found with given id from server")
        return
    }

    val skin = if (onlinePlayer != null) {
        onlinePlayer.playerProfile.textures.skin
    } else {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://sessionserver.mojang.com/session/minecraft/profile/$userId")
                val connection = url.openConnection()
                val content =
                    BufferedReader(InputStreamReader(connection.getInputStream())).use { reader -> reader.readText() }
                val response = Json.decodeFromString<InternalSessionServerResponse>(content)
                val decoded =
                    Json.decodeFromString<InternalEncodedProfile>(response.properties[0]["value"]!!.decodeBase64String())
                URL(decoded.textures.SKIN["url"])
            } catch (e: Exception) {
                null
            }
        }
    }

    if (skin == null) {
        call.respond(HttpStatusCode.NotFound, "no skin data found from server")
        return
    }

    val bytes = withContext(Dispatchers.IO) {
        val image = ImageIO.read(skin)

        val face = image.getSubimage(8, 8, 8, 8)

        val outputStream = ByteArrayOutputStream()
        ImageIO.write(face, "png", outputStream)

        outputStream.toByteArray()
    }

    call.respondBytes(bytes, ContentType.Image.PNG)
}

@Serializable
data class InternalSessionServerResponse(
    val id: String,
    val name: String,
    val properties: List<Map<String, String>>
)
@Serializable
data class InternalEncodedProfile(
    val profileId: String,
    val profileName: String,
    val textures: InternalEncodedProfileTextures,
    val timestamp: Long
)
@Serializable
data class InternalEncodedProfileTextures(
    val SKIN: Map<String, String>
)

@Serializable
data class TerrainRequestLocation(val x: Int, val z: Int)
@Serializable
data class TerrainRequest(val environment: String, val center: TerrainRequestLocation, val scale: Int, val limit: Int? = null)
@Serializable
data class TerrainData(val limited: List<Long>, val blocks: List<Long>, val shadows: List<Long>)
@Serializable
data class TerrainResponse(val terrain: TerrainData, val palette: List<String>, val colors: List<String?>)
suspend fun PipelineContext<Unit, ApplicationCall>.terrain(parent: Main) {
    val data = call.receive<TerrainRequest>()
    val environment =
        when (data.environment) {
            "overworld" -> Environment.NORMAL
            "the_nether" -> Environment.NETHER
            "the_end" -> Environment.THE_END
            else -> null
        }

    if (environment == null) {
        call.respond(HttpStatusCode.BadRequest, "invalid environment '${data.environment}'")
        return
    }

    val world = parent.server.worlds.find { it.environment == environment }
    if (world == null) {
        call.respond(HttpStatusCode.NotFound, "no worlds found with given environment '${data.environment}'")
        return
    }

    val calcBitsPerBlock: (Int) -> Int = {
        var result = 4
        var value = 2 * 2 * 2 * 2
        while (it > value) {
            value *= 2
            result++
        }
        result
    }

    val radius = data.scale / 2
    val fromX = (data.center.x - radius) * 16
    val toX = (data.center.x + radius + 1) * 16
    val fromZ = (data.center.z - radius) * 16
    val toZ = (data.center.z + radius + 1) * 16

    val blockKeys = mutableListOf<String>()
    val blockYs = mutableListOf<Int>()

    val blockLongs = mutableListOf<Long>()
    val yLimitedLongs = mutableListOf<Long>()
    val shadowLongs = mutableListOf<Long>()

    var yLimitedDataBits = 0L
    var yLimitedDataBitIndex = 0

    val setYLimited = {
        yLimitedDataBits = yLimitedDataBits shl 1
        yLimitedDataBits = yLimitedDataBits or 1L
    }

    val setYNotLimited = {
        yLimitedDataBits = yLimitedDataBits shl 1
    }

    var hasRemainingYLimitedData = false

    ((fromX until toX) to (fromZ until toZ)).forEach block@ { x, z ->
        val highest = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING)

        val addBlockY: (Block) -> Unit = {
            if (it.type.key.key == "water") blockYs.add(world.getHighestBlockAt(x, z, HeightMap.OCEAN_FLOOR).y)
            else blockYs.add(it.y)
        }

        val limit = data.limit
        if (limit == null) {
            blockKeys.add(highest.type.key.key)
            addBlockY(highest)
            return@block
        }

        if (highest.y < limit) {
            blockKeys.add(highest.type.key.key)
            addBlockY(highest)
            setYNotLimited()
        } else {
            var block = world.getBlockAt(x, limit, z)

            if (block.type.isSolid) setYLimited()
            else setYNotLimited()

            while (!block.type.isSolid) {
                block = world.getBlockAt(x, block.y - 1, z)
            }

            blockKeys.add(block.type.key.key)
            addBlockY(block)
        }

        hasRemainingYLimitedData = true
        yLimitedDataBitIndex++

        if (yLimitedDataBitIndex == Int.SIZE_BITS) {
            yLimitedLongs.add(yLimitedDataBits)
            yLimitedDataBits = 0L
            yLimitedDataBitIndex = 0
            hasRemainingYLimitedData = false
        }
    }

    if (hasRemainingYLimitedData) yLimitedLongs.add(yLimitedDataBits)

    val palette = blockKeys.toSet().toList()
    val colors = palette.map { parent.resources.blockColors[it] }

    val bitsPerBlock = calcBitsPerBlock(palette.size)

    var blockDataBitIndex = 0
    var blockDataBits: Long = 0

    var hasRemainingBlockData = false

    blockKeys.forEach { key ->
        val paletteIndex = palette.indexOf(key).toLong()

        blockDataBits = blockDataBits shl bitsPerBlock
        blockDataBits = blockDataBits or paletteIndex

        blockDataBitIndex += bitsPerBlock

        hasRemainingBlockData = true

        if (blockDataBitIndex + bitsPerBlock > Int.SIZE_BITS) {
            blockLongs.add(blockDataBits)
            blockDataBits = 0
            blockDataBitIndex = 0
            hasRemainingBlockData = false
        }
    }

    if (hasRemainingBlockData) blockLongs.add(blockDataBits)

    var shadowDataBits = 0L
    var shadowDataBitIndex = 0

    var hasRemainingShadowData = false

    val setBlockShadowed = {
        shadowDataBits = shadowDataBits shl 2
        shadowDataBits = shadowDataBits or 1
    }

    val setBlockLighted = {
        shadowDataBits = shadowDataBits shl 2
        shadowDataBits = shadowDataBits or 2
    }

    val setBlockNotShadowed = {
        shadowDataBits = shadowDataBits shl 2
    }

    blockYs.forEachIndexed blockY@ { index, it ->
        val aboveYIndex = if (index % (16 * data.scale) == 0) -1 else index - 1
        if (aboveYIndex < 0 || blockYs[aboveYIndex] > it) setBlockShadowed()
        else if (blockYs[aboveYIndex] < it) setBlockLighted()
        else setBlockNotShadowed()

        shadowDataBitIndex += 2
        hasRemainingShadowData = true

        if (shadowDataBitIndex == Int.SIZE_BITS) {
            shadowLongs.add(shadowDataBits)
            shadowDataBitIndex = 0
            shadowDataBits = 0
            hasRemainingShadowData = false
        }
    }

    if (hasRemainingShadowData) shadowLongs.add(shadowDataBits)

    call.respond(
        TerrainResponse(
            TerrainData(yLimitedLongs, blockLongs, shadowLongs),
            palette,
            colors
        )
    )
}
