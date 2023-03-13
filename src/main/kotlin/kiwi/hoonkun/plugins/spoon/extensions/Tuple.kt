package kiwi.hoonkun.plugins.spoon.extensions

fun Pair<IntRange, IntRange>.forEach(block: (Int, Int) -> Unit) {
    for (x in first) {
        for (z in second) {
            block(x, z)
        }
    }
}