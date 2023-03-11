package kiwi.hoonkun.plugins.spoon

import kiwi.hoonkun.plugins.spoon.server.SpoonApiServer
import org.bukkit.plugin.java.JavaPlugin

class Main: JavaPlugin() {

    private val apiServer = SpoonApiServer(this)

    override fun onEnable() {
        super.onEnable()
        apiServer.instance.start()
    }

    override fun onDisable() {
        super.onDisable()
        apiServer.instance.stop()
    }

}