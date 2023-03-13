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
import kotlinx.serialization.Serializable
import org.bukkit.HeightMap
import org.bukkit.World.Environment


fun Application.apiServer(parent: Main) {
    val prefix = "/api"

    routing {
        post("/auth") { respondJWT(parent) }
        authenticate(optional = true) {
            get("$prefix/hello") { hello() }
            get("$prefix/connected-users") { connectedUsers(parent) }
        }
        authenticate {
            post("$prefix/run") { runCommand(parent) }
            get("$prefix/terrain") { terrain(parent) }
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

@Serializable
data class TerrainRequestLocation(val x: Int, val z: Int)
@Serializable
data class TerrainRequest(val environment: String, val location: TerrainRequestLocation)
@Serializable
data class TerrainResponse(val palette: List<String>, val colors: List<String?>, val blocks: List<Long>)
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

    val minX = (data.location.x - 1) * 16
    val maxX = (data.location.x + 1) * 16
    val minZ = (data.location.z - 1) * 16
    val maxZ = (data.location.z + 1) * 16

    val blocks = mutableListOf<String>()

    var blockSegmentIndex = 0
    var blockSegment: Long = 0

    ((minX until maxX) to (minZ until maxZ)).forEach { x, z ->
        blocks.add(world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING).type.key.key)
    }

    val palette = blocks.toSet()
    val bitsPerBlock = calcBitsPerBlock(palette.size)

    val longs = mutableListOf<Long>()

    blocks.forEach { block ->
        val paletteIndex = palette.indexOf(block).toLong()

        blockSegmentIndex++
        blockSegment = blockSegment or paletteIndex
        blockSegment = blockSegment shl bitsPerBlock

        if ((blockSegmentIndex + 1) * bitsPerBlock > Long.SIZE_BITS) {
            longs.add(blockSegment)
            blockSegment = 0
            blockSegmentIndex = 0
        }
    }
    if (blockSegment != 0L) longs.add(blockSegment)

    val orderedPalette = palette.toList()
    val colors = palette.map { parent.resources.blockColors[it] }

    call.respond(TerrainResponse(orderedPalette, colors, longs))
}
