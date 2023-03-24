package kiwi.hoonkun.plugins.spoon

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import kiwi.hoonkun.plugins.spoon.plugin.ConsoleFilter
import kiwi.hoonkun.plugins.spoon.plugin.commands.UserExecutor
import kiwi.hoonkun.plugins.spoon.plugin.listeners.LiveDataObserver
import kiwi.hoonkun.plugins.spoon.server.*
import kiwi.hoonkun.plugins.spoon.server.auth.jwtAuthentication
import kiwi.hoonkun.plugins.spoon.server.structures.User
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Logger
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*
import kotlin.collections.LinkedHashSet


class Main : JavaPlugin() {

    val configurations = parseConfiguration(File("${dataFolder.absolutePath}/.config.env"))
    val resources = parseResources()
    val users = parseUser(File("${dataFolder.absolutePath}/.user.json")).toMutableList()

    val logs = mutableListOf<SpoonLog>()

    private val spoon = embeddedServer(Netty, port = 25566, module = { spoon(this@Main) })
    val spoonSocketConnections: MutableSet<Connection> = Collections.synchronizedSet(LinkedHashSet())

    private val userExecutor = UserExecutor(this)

    val overworld get() = server.worlds.find { it.environment == World.Environment.NORMAL }

    private val observer = LiveDataObserver(this)

    override fun onEnable() {
        super.onEnable()
        registerLoggerHandlers()

        spoon.start()

        server.pluginManager.registerEvents(observer, this)
        observer.observe()
    }

    override fun onDisable() {
        super.onDisable()

        observer.unobserve()
        spoon.stop()
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (command.name != "spoon") return true

        if (args.isEmpty()) {
            sender.sendMessage("Hello, I'm spoon command receiver!")
            return true
        }

        val nextArgs = args.slice(1 until args.size)
        return when (args[0]) {
            "user" -> userExecutor.exec(sender, nextArgs)
            else -> true
        }
    }

    private fun registerLoggerHandlers() {
        (LogManager.getRootLogger() as Logger).addFilter(ConsoleFilter(this))
    }

    fun subscribers(which: String): List<Connection> {
        return spoonSocketConnections.filter { it.subscribed.contains(which) }
    }

}

fun Application.spoon(parent: Main) {
    install(ContentNegotiation) { json() }
    install(CORS) {
        methods.addAll(
            listOf(
                HttpMethod.Options,
                HttpMethod.Put,
                HttpMethod.Delete,
                HttpMethod.Patch,
                HttpMethod.Get,
                HttpMethod.Post,
                HttpMethod.Head
            )
        )
        headers.addAll(listOf(HttpHeaders.Authorization, HttpHeaders.AccessControlAllowOrigin))
        allowNonSimpleContentTypes = true
        allowCredentials = true
        allowSameOrigin = true
        anyHost() // FIXME: 언젠가 수정합시다 이거
    }

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
    val dict = text.split("\n")
        .filter { it.isNotEmpty() }
        .associate { line -> line.split("=", limit = 2).let { it[0] to it[1] } }

    return SpoonConfiguration(
        secret = dict.getValue("secret"),
        host = "http://${dict.getValue("host")}:25566"
    )
}

data class SpoonResources(
    val blockColors: Map<String, String?>
)
fun parseResources(): SpoonResources {
    val colorResource = {}::class.java.getResource("/block_colors.json")

    return SpoonResources(
        Json.decodeFromString(colorResource?.readText() ?: "{}")
    )
}

fun parseUser(userFile: File): List<User> {
    val text = if (userFile.exists()) userFile.readText() else "[]"
    return Json.decodeFromString(text)
}
