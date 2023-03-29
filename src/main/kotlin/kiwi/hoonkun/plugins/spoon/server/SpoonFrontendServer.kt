package kiwi.hoonkun.plugins.spoon.server

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*


fun Application.frontendServer() {
    routing {
        singlePageApplication {
            react("frontend")
            useResources = true
        }
    }
}