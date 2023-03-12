package kiwi.hoonkun.plugins.spoon.server

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.server.auth.generateJWT


fun Application.apiServer(parent: Main) {
    val prefix = "/api"

    routing {
        post("/auth") { generateJWT(parent.configurations) }
        authenticate(optional = true) {
            get("$prefix/hello") {
                val principal = call.principal<JWTPrincipal>()
                if (principal != null) {
                    call.respondText("Hello ${principal.payload.claims.getValue("username").asString()}, I'm spoon api server!")
                } else {
                    call.respondText("Hello, I'm spoon api server!")
                }
            }
        }
    }
}