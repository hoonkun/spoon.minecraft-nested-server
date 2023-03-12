package kiwi.hoonkun.plugins.spoon.plugin.commands

import at.favre.lib.crypto.bcrypt.BCrypt
import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.server.structures.User
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import java.io.File

class UserExecutor(private val parent: Main): Executor() {

    override fun exec(sender: CommandSender, args: List<String>): Boolean {
        if (sender !is ConsoleCommandSender) return true

        val nextArgs = args.slice(1 until args.size)
        when (args[0]) {
            "add" -> add(sender, nextArgs)
            "delete" -> delete(sender, nextArgs)
            "update" -> update(sender, nextArgs)
        }
        return true
    }

    private fun save() {
        File("${parent.dataFolder.absolutePath}/.user.json").writeText(Json.encodeToString(parent.users))
    }

    private fun add(sender: CommandSender, args: List<String>) {
        if (args.size < 2) {
            sender.sendMessage("adding user to spoon registry needs more arguments.")
            sender.sendMessage("usage: /spoon user add <username> <password>")
        }

        val username = args[0]
        val password = args[1]

        if (parent.users.any { it.username == username }) {
            sender.sendMessage("user with given username \"$username\" already exists.")
            sender.sendMessage("you can try `user delete` first or `user update` respectively.")
            return
        }

        val passwordHash = BCrypt.withDefaults().hashToString(12, password.toCharArray())

        parent.users.add(User(username, passwordHash))
        save()

        sender.sendMessage("successfully created user \"$username\".")
    }

    private fun delete(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            sender.sendMessage("deleting user from spoon registry needs more arguments.")
            sender.sendMessage("usage: /spoon user delete <username>")
        }

        val targetUsername = args[0]

        val removed = parent.users.removeIf { it.username == targetUsername }
        if (removed) {
            save()
            sender.sendMessage("successfully deleted user \"$targetUsername\".")
        } else {
            sender.sendMessage("no user found with given username, no changes.")
        }
    }

    private fun update(sender: CommandSender, args: List<String>) {
        if (args.size < 2) {
            sender.sendMessage("deleting user from spoon registry needs more arguments.")
            sender.sendMessage("usage: /spoon user update <username> <new-password>")
        }

        val targetUsername = args[0]
        val newPassword = args[1]

        val targetUser = parent.users.find { it.username == targetUsername }
        if (targetUser == null) {
            sender.sendMessage("no user found with given username, no changes.")
        } else {
            val newPasswordHash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())

            parent.users.remove(targetUser)
            parent.users.add(targetUser.copy(password = newPasswordHash))
            save()

            sender.sendMessage("successfully updated user \"$targetUser\" with new password.")
        }
    }

}