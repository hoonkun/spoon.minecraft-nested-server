package kiwi.hoonkun.plugins.spoon.server

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.server.auth.respondJWT
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonCommonPlayer
import kiwi.hoonkun.plugins.spoon.server.structures.SpoonPlayer


fun Application.apiServer(parent: Main) {
    val prefix = "/api"

    routing {
        post("/auth") { respondJWT(parent) }
        authenticate(optional = true) {
            get("$prefix/hello") { hello() }
            get("$prefix/connected-users") { connectedUsers(parent) }
        }
        authenticate {

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
