package kiwi.hoonkun.plugins.spoon

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kiwi.hoonkun.plugins.spoon.server.apiServer
import kiwi.hoonkun.plugins.spoon.server.websocketServer
import org.bukkit.plugin.java.JavaPlugin

class Main: JavaPlugin() {

    private val spoon = embeddedServer(Netty, port = 25566, module = { spoon(this@Main) })

    override fun onEnable() {
        super.onEnable()
        spoon.start()
    }

    override fun onDisable() {
        super.onDisable()
        spoon.stop()
    }

}

fun Application.spoon(parent: Main) {
    apiServer(parent)
    websocketServer(parent)
}
