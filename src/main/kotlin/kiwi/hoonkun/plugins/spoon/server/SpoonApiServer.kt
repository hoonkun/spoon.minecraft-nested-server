package kiwi.hoonkun.plugins.spoon.server

import io.ktor.server.application.Application
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kiwi.hoonkun.plugins.spoon.Main


class SpoonApiServer(val parent: Main) {

    val instance = embeddedServer(Netty, port = 25566, module = { apiServer(this@SpoonApiServer) })

}

fun Application.apiServer(parent: SpoonApiServer) {
    
}