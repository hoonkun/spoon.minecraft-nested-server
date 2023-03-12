package kiwi.hoonkun.plugins.spoon.server

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kiwi.hoonkun.plugins.spoon.Main


fun Application.apiServer(parent: Main) {
    val prefix = "/api"

    routing {
        get("$prefix/hello") {
            call.respondText("Hello, I'm spoon api server!")
        }
    }
}