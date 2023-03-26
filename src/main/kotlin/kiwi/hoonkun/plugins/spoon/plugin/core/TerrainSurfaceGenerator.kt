package kiwi.hoonkun.plugins.spoon.plugin.core

import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.extensions.forEach
import kiwi.hoonkun.plugins.spoon.server.TerrainData
import kiwi.hoonkun.plugins.spoon.server.TerrainRequestLocation
import kiwi.hoonkun.plugins.spoon.server.TerrainResponse
import org.bukkit.ChunkSnapshot
import org.bukkit.HeightMap
import org.bukkit.Material
import org.bukkit.World
import kotlin.math.absoluteValue

class TerrainSurfaceGenerator {

    companion object {

        fun generate(parent: Main, world: World, scale: Int, center: TerrainRequestLocation, limit: Int?): TerrainResponse {
            val calcBitsPerBlock: (Int) -> Int = {
                var result = 4
                var value = 2 * 2 * 2 * 2
                while (it > value) {
                    value *= 2
                    result++
                }
                result
            }

            val radius = scale / 2
            val fromX = (center.x - radius) * 16
            val toX = (center.x + radius + 1) * 16
            val fromZ = (center.z - radius) * 16
            val toZ = (center.z + radius + 1) * 16

            val blockKeys = mutableListOf<String>()
            val blockYs = mutableListOf<Int>()

            val blockLongs = mutableListOf<Long>()
            val yLimitedLongs = mutableListOf<Long>()
            val shadowLongs = mutableListOf<Long>()

            var yLimitedDataBits = 0L
            var yLimitedDataBitIndex = 0

            val setYLimited = {
                yLimitedDataBits = yLimitedDataBits shl 1
                yLimitedDataBits = yLimitedDataBits or 1L
            }

            val setYNotLimited = {
                yLimitedDataBits = yLimitedDataBits shl 1
            }

            var hasRemainingYLimitedData = false

            val addBlockY: (Material, Int, Int, Int) -> Unit = { material, x, y, z ->
                if (material.key.key == "water") blockYs.add(world.getHighestBlockAt(x, z, HeightMap.OCEAN_FLOOR).y)
                else blockYs.add(y)
            }

            val chunks = mutableMapOf<Pair<Int, Int>, ChunkSnapshot>()

            ((fromX until toX) to (fromZ until toZ)).forEach block@ { x, z ->
                val chunkX = (x / 16).let { if (x >= 0) it else it - 1 }
                val chunkZ = (z / 16).let { if (z >= 0) it else it - 1 }

                val chunk = chunks.getOrPut(chunkX to chunkZ) { world.getChunkAt(chunkX, chunkZ).chunkSnapshot }
                val chunkBlockX = (x % 16).absoluteValue.let { if (x < 0) 15 - it else it }
                val chunkBlockZ = (z % 16).absoluteValue.let { if (z < 0) 15 - it else it }

                val highestY = chunk.getHighestBlockYAt(chunkBlockX, chunkBlockZ)
                if (highestY == -1) {
                    blockKeys.add("air")
                    addBlockY(Material.AIR, x, -1, z)
                    return@block
                }
                val highest = chunk.getBlockType(chunkBlockX, highestY, chunkBlockZ)

                if (limit == null) {
                    blockKeys.add(highest.key.key)
                    addBlockY(highest, x, highestY, z)
                    return@block
                }

                if (highestY < limit) {
                    blockKeys.add(highest.key.key)
                    addBlockY(highest, x, highestY, z)
                    setYNotLimited()
                } else {
                    var y = limit
                    var block = chunk.getBlockType(chunkBlockX, y, chunkBlockZ)

                    val validBlock: (Material) -> Boolean = { (it.isSolid || it == Material.WATER || it == Material.LAVA) && !it.isAir }

                    if (validBlock(block)) setYLimited()
                    else setYNotLimited()

                    while (!validBlock(block) && y >= 0) {
                        block = chunk.getBlockType(chunkBlockX, --y, chunkBlockZ)
                    }

                    blockKeys.add(block.key.key)
                    addBlockY(block, x, y, z)
                }

                hasRemainingYLimitedData = true
                yLimitedDataBitIndex++

                if (yLimitedDataBitIndex == Int.SIZE_BITS) {
                    yLimitedLongs.add(yLimitedDataBits)
                    yLimitedDataBits = 0L
                    yLimitedDataBitIndex = 0
                    hasRemainingYLimitedData = false
                }
            }

            if (hasRemainingYLimitedData) yLimitedLongs.add(yLimitedDataBits)

            val palette = blockKeys.toSet().toList()
            val colors = palette.map { parent.resources.blockColors[it] }

            val bitsPerBlock = calcBitsPerBlock(palette.size)

            var blockDataBitIndex = 0
            var blockDataBits: Long = 0

            var hasRemainingBlockData = false

            var shadowDataBits = 0L
            var shadowDataBitIndex = 0

            var hasRemainingShadowData = false

            val setBlockShadowed = {
                shadowDataBits = shadowDataBits shl 2
                shadowDataBits = shadowDataBits or 1
            }

            val setBlockLighted = {
                shadowDataBits = shadowDataBits shl 2
                shadowDataBits = shadowDataBits or 2
            }

            val setBlockNotShadowed = {
                shadowDataBits = shadowDataBits shl 2
            }

            for (index in 0 until blockKeys.size) {
                val key = blockKeys[index]
                val y = blockYs[index]

                val paletteIndex = palette.indexOf(key).toLong()

                blockDataBits = blockDataBits shl bitsPerBlock
                blockDataBits = blockDataBits or paletteIndex

                blockDataBitIndex += bitsPerBlock

                hasRemainingBlockData = true

                if (blockDataBitIndex + bitsPerBlock > Int.SIZE_BITS) {
                    blockLongs.add(blockDataBits)
                    blockDataBits = 0
                    blockDataBitIndex = 0
                    hasRemainingBlockData = false
                }

                val aboveYIndex = if (index % (16 * scale) == 0) -1 else index - 1
                if (aboveYIndex < 0 || blockYs[aboveYIndex] > y) setBlockShadowed()
                else if (blockYs[aboveYIndex] < y) setBlockLighted()
                else setBlockNotShadowed()

                shadowDataBitIndex += 2
                hasRemainingShadowData = true

                if (shadowDataBitIndex == Int.SIZE_BITS) {
                    shadowLongs.add(shadowDataBits)
                    shadowDataBitIndex = 0
                    shadowDataBits = 0
                    hasRemainingShadowData = false
                }
            }

            if (hasRemainingBlockData) blockLongs.add(blockDataBits)
            if (hasRemainingShadowData) shadowLongs.add(shadowDataBits)

            return TerrainResponse(
                TerrainData(yLimitedLongs, blockLongs, shadowLongs),
                palette,
                colors
            )
        }

    }

}