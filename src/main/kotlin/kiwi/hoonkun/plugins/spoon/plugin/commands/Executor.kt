package kiwi.hoonkun.plugins.spoon.plugin.commands

import org.bukkit.command.CommandSender

abstract class Executor {

    abstract fun exec(sender: CommandSender, args: List<String>): Boolean

}