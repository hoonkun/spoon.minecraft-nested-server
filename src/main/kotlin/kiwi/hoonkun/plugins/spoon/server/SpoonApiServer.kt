package kiwi.hoonkun.plugins.spoon.server

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.server.auth.respondJWT


fun Application.apiServer(parent: Main) {
    val prefix = "/api"

    routing {
        post("/auth") { respondJWT(parent) }
        authenticate(optional = true) {
            get("$prefix/hello") {
                val principal = call.principal<JWTPrincipal>()
                if (principal != null) {
                    val username = principal.payload.claims.getValue("username").asString()
                    call.respondText("Hello ${username}, I'm spoon api server!")
                } else {
                    call.respondText("Hello, I'm spoon api server!")
                }
            }
        }
    }
}