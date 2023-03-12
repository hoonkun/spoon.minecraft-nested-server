package kiwi.hoonkun.plugins.spoon

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import kiwi.hoonkun.plugins.spoon.server.apiServer
import kiwi.hoonkun.plugins.spoon.server.auth.jwtAuthentication
import kiwi.hoonkun.plugins.spoon.server.websocketServer
import org.bukkit.plugin.java.JavaPlugin
import java.io.File


class Main : JavaPlugin() {

    val configurations = parseConfiguration(File("${dataFolder.absolutePath}/.config.env"))

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
    install(ContentNegotiation) { json() }
    jwtAuthentication(parent.configurations)

    apiServer(parent)
    websocketServer(parent)
}

data class SpoonConfiguration(
    val secret: String,
    val host: String
)

fun parseConfiguration(configFile: File): SpoonConfiguration {
    if (!configFile.exists()) throw Exception("config file not exists, which must be located in ${configFile.absolutePath}")

    val text = configFile.readText()
    val dict = text.split("\n").filter { it.isNotEmpty() }.associate { line -> line.split("=", limit = 2).let { it[0] to it[1] } }

    return SpoonConfiguration(
        secret = dict.getValue("secret"),
        host = "http://${dict.getValue("host")}:25566"
    )
}
