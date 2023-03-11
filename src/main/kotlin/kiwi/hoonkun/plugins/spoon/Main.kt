package kiwi.hoonkun.plugins.spoon

import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class Main: JavaPlugin() {

    override fun onEnable() {
        super.onEnable()
        server.logger.log(Level.INFO, "Enabled!")
    }

    override fun onDisable() {
        super.onDisable()
        server.logger.log(Level.INFO, "Disabled!!")
    }

}