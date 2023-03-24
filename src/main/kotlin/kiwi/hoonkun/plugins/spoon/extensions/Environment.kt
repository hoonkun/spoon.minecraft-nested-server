package kiwi.hoonkun.plugins.spoon.extensions

import org.bukkit.World

fun World.Environment?.spoon() = when (this) {
    World.Environment.NORMAL -> "overworld"
    World.Environment.NETHER -> "the_nether"
    World.Environment.THE_END -> "the_end"
    else -> "unknown"
}