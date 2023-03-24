package kiwi.hoonkun.plugins.spoon.plugin.core

import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.extensions.forEach
import kiwi.hoonkun.plugins.spoon.server.TerrainData
import kiwi.hoonkun.plugins.spoon.server.TerrainRequestLocation
import kiwi.hoonkun.plugins.spoon.server.TerrainResponse
import org.bukkit.HeightMap
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block

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

            ((fromX until toX) to (fromZ until toZ)).forEach block@ { x, z ->
                val highest = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING)

                val addBlockY: (Block) -> Unit = {
                    if (it.type.key.key == "water") blockYs.add(world.getHighestBlockAt(x, z, HeightMap.OCEAN_FLOOR).y)
                    else blockYs.add(it.y)
                }

                if (limit == null) {
                    blockKeys.add(highest.type.key.key)
                    addBlockY(highest)
                    return@block
                }

                if (highest.y < limit) {
                    blockKeys.add(highest.type.key.key)
                    addBlockY(highest)
                    setYNotLimited()
                } else {
                    var block = world.getBlockAt(x, limit, z)

                    val validBlock = { (block.type.isSolid || block.type == Material.WATER || block.type == Material.LAVA) && !block.type.isAir }

                    if (validBlock()) setYLimited()
                    else setYNotLimited()

                    while (!validBlock()) {
                        block = world.getBlockAt(x, block.y - 1, z)
                    }

                    blockKeys.add(block.type.key.key)
                    addBlockY(block)
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

            blockKeys.forEach { key ->
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
            }

            if (hasRemainingBlockData) blockLongs.add(blockDataBits)

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

            blockYs.forEachIndexed blockY@ { index, it ->
                val aboveYIndex = if (index % (16 * scale) == 0) -1 else index - 1
                if (aboveYIndex < 0 || blockYs[aboveYIndex] > it) setBlockShadowed()
                else if (blockYs[aboveYIndex] < it) setBlockLighted()
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

            if (hasRemainingShadowData) shadowLongs.add(shadowDataBits)

            return TerrainResponse(
                TerrainData(yLimitedLongs, blockLongs, shadowLongs),
                palette,
                colors
            )
        }

    }

}