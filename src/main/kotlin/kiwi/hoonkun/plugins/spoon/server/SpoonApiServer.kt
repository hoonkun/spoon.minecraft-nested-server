package kiwi.hoonkun.plugins.spoon.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.extensions.forEach
import kiwi.hoonkun.plugins.spoon.server.auth.respondJWT
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonCommonPlayer
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.bukkit.HeightMap
import org.bukkit.World.Environment
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO


fun Application.apiServer(parent: Main) {
    val prefix = "/api"

    routing {
        post("/auth") { respondJWT(parent) }
        authenticate(optional = true) {
            get("/me") { call.respond(call.principal<JWTPrincipal>()!!.payload.claims.getValue("username").asString()) }
            get("$prefix/hello") { hello() }
            get("$prefix/connected-users") { connectedUsers(parent) }
            get("$prefix/connected-user-graphic/{userId}") { userSkin(parent) }
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

suspend fun PipelineContext<Unit, ApplicationCall>.connectedUsers(parent: Main) {
    val principal = call.principal<JWTPrincipal>()

    if (principal != null) {
        val players = parent.server.onlinePlayers.map { SpoonPlayer.bukkit(it) }
        call.respond(players)
    } else {
        val players = parent.server.onlinePlayers.map { SpoonCommonPlayer.bukkit(it) }
        call.respond(players)
    }
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

    val player = parent.server.onlinePlayers.find { it.playerProfile.uniqueId.toString() == userId }
    if (player == null) {
        call.respond(HttpStatusCode.NotFound, "no online player with given id found from server")
        return
    }

    val skin = player.playerProfile.textures.skin
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
data class TerrainRequestLocation(val x: Int, val z: Int)
@Serializable
data class TerrainRequest(val environment: String, val locations: List<TerrainRequestLocation>, val limit: Int? = null)
@Serializable
data class TerrainChunk(val location: TerrainRequestLocation, val blocks: List<Long>, val limited: List<Long>)
@Serializable
data class TerrainResponse(val chunks: List<TerrainChunk>, val palette: List<String>, val colors: List<String?>)
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

    val blockKeysByChunk = mutableMapOf<TerrainRequestLocation, MutableList<String>>()
    val blockLongsByChunk = mutableMapOf<TerrainRequestLocation, MutableList<Long>>()
    val yLimitedLongsByChunk = mutableMapOf<TerrainRequestLocation, MutableList<Long>>()

    data.locations.forEach { location ->
        val blockKeys = mutableListOf<String>()

        var yLimitedDataBits = 0L
        var yLimitedDataBitIndex = 0
        val yLimitedLongs = mutableListOf<Long>()

        val setYLimited = {
            yLimitedDataBits = yLimitedDataBits or 1L
            yLimitedDataBits = yLimitedDataBits shl 1
        }

        val setYNotLimited = {
            yLimitedDataBits = yLimitedDataBits shl 1
        }

        ((location.x * 16 until (location.x + 1) * 16) to (location.z * 16 until (location.z + 1) * 16)).forEach block@ { x, z ->
            val highest = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING)

            val limit = data.limit
            if (limit == null) {
                blockKeys.add(highest.type.key.key)
                return@block
            }

            if (yLimitedDataBitIndex == Long.SIZE_BITS) {
                yLimitedLongs.add(yLimitedDataBits)
                yLimitedDataBits = 0L
                yLimitedDataBitIndex = 0
            }

            if (highest.y < limit) {
                blockKeys.add(highest.type.key.key)
                setYNotLimited()
            } else {
                var block = world.getBlockAt(x, limit, z)

                if (block.type.isSolid) setYLimited()
                else setYNotLimited()

                while (!block.type.isSolid) {
                    block = world.getBlockAt(x, block.y - 1, z)
                }

                blockKeys.add(block.type.key.key)
            }

            yLimitedDataBitIndex++
        }

        if (data.limit != null) yLimitedLongs.add(yLimitedDataBits)

        blockKeysByChunk[location] = blockKeys
        yLimitedLongsByChunk[location] = yLimitedLongs
    }

    val palette = blockKeysByChunk.values.flatten().toSet().toList()
    val colors = palette.map { parent.resources.blockColors[it] }

    val bitsPerBlock = calcBitsPerBlock(palette.size)

    blockKeysByChunk.forEach { (location, keys) ->
        var blockDataBitIndex = 0
        var blockDataBits: Long = 0
        val blockLongs = mutableListOf<Long>()

        var hasRemainingBlockData = false

        keys.forEach { key ->
            val paletteIndex = palette.indexOf(key).toLong()

            blockDataBitIndex++
            blockDataBits = blockDataBits or paletteIndex
            blockDataBits = blockDataBits shl bitsPerBlock

            hasRemainingBlockData = true

            if ((blockDataBitIndex + 1) * bitsPerBlock > Long.SIZE_BITS) {
                blockLongs.add(blockDataBits)
                blockDataBits = 0
                blockDataBitIndex = 0
                hasRemainingBlockData = false
            }
        }

        if (hasRemainingBlockData) blockLongs.add(blockDataBits)

        blockLongsByChunk[location] = blockLongs
    }

    call.respond(
        TerrainResponse(
            data.locations.map { TerrainChunk(it, blockLongsByChunk.getValue(it), yLimitedLongsByChunk.getValue(it)) },
            palette,
            colors
        )
    )
}
