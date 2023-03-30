package kiwi.hoonkun.plugins.spoon.plugin.core

import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.extensions.forEach
import kiwi.hoonkun.plugins.spoon.server.TerrainData
import kiwi.hoonkun.plugins.spoon.server.TerrainRequestLocation
import kiwi.hoonkun.plugins.spoon.server.TerrainResponse
import org.bukkit.ChunkSnapshot
import org.bukkit.Material
import org.bukkit.World
import kotlin.math.absoluteValue

class TerrainSurfaceGenerator {

    companion object {

        fun generate(parent: Main, world: World, scale: Int, center: TerrainRequestLocation, limit: Int?): TerrainResponse {
            val validKeys = parent.resources.blockColors.keys

            val calcBitsPerBlock: (Int) -> Int = {
                var result = 4
                var value = 2 * 2 * 2 * 2
                while (it > value) {
                    value *= 2
                    result++
                }
                result
            }

            val isValidBlock: (Material) -> Boolean = { validKeys.contains(it.key.key) }
            val isValidNonWaterBlock: (Material) -> Boolean = { validKeys.contains(it.key.key) && it != Material.WATER }

            val getHighestValidBlock: (ChunkSnapshot, Int, Int, Int) -> Pair<Material, Int> = lambda@ { chunk, x, initialY, z ->
                if (initialY < 0) return@lambda Material.AIR to -1
                var resultType = chunk.getBlockType(x, initialY, z)
                var resultY = initialY
                while (!isValidBlock(resultType)) {
                    resultType = chunk.getBlockType(x, --resultY, z)
                }
                if (resultType == Material.WATER) {
                    var nonWaterType = resultType
                    while (!isValidNonWaterBlock(nonWaterType)) {
                        nonWaterType = chunk.getBlockType(x, --resultY, z)
                    }
                }
                resultType to resultY
            }

            val radius = scale / 2
            val fromX = (center.x - radius) * 16
            val toX = (center.x + radius + 1) * 16
            val fromZ = (center.z - radius) * 16
            val toZ = (center.z + radius + 1) * 16

            val blockKeySet = mutableSetOf<String>()
            val blockIndexes = mutableListOf<Int>()
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

            val chunks = mutableMapOf<Pair<Int, Int>, ChunkSnapshot>()

            ((fromX until toX) to (fromZ until toZ)).forEach block@ { x, z ->
                val chunkX = (x / 16).let { if (x >= 0) it else it - 1 }
                val chunkZ = (z / 16).let { if (z >= 0) it else it - 1 }

                val blockX = (x % 16).absoluteValue.let { if (x < 0) 15 - it else it }
                val blockZ = (z % 16).absoluteValue.let { if (z < 0) 15 - it else it }

                val chunk = chunks.getOrPut(chunkX to chunkZ) { world.getChunkAt(chunkX, chunkZ).chunkSnapshot }

                val (highestBlock, highestY) = getHighestValidBlock(chunk, blockX, chunk.getHighestBlockYAt(blockX, blockZ), blockZ)

                if (limit == null || highestY < limit) {
                    blockKeySet.add(highestBlock.key.key)
                    blockIndexes.add(blockKeySet.indexOf(highestBlock.key.key))
                    blockYs.add(highestY)

                    setYNotLimited()
                } else {
                    val (limitedHighestBlock, limitedHighestY) = getHighestValidBlock(chunk, blockX, limit, blockZ)

                    blockKeySet.add(limitedHighestBlock.key.key)
                    blockIndexes.add(blockKeySet.indexOf(limitedHighestBlock.key.key))
                    blockYs.add(limitedHighestY)

                    if (limitedHighestY == limit) {
                        setYLimited()
                    } else {
                        setYNotLimited()
                    }
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

            val palette = blockKeySet.toList()
            val colors = palette.map { parent.resources.blockColors[it] }

            val bitsPerBlock = calcBitsPerBlock(palette.size)

            var blockDataBitIndex = 0
            var blockDataBits: Long = 0

            var hasRemainingBlockData = false

            var shadowDataBits = 0L
            var shadowDataBitIndex = 0

            var hasRemainingShadowData = false

            for (index in 0 until blockIndexes.size) {
                val paletteIndex = blockIndexes[index].toLong()
                val y = blockYs[index]

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
                if (aboveYIndex < 0 || blockYs[aboveYIndex] > y) {
                    shadowDataBits = shadowDataBits shl 2
                    shadowDataBits = shadowDataBits or 1
                } else if (blockYs[aboveYIndex] < y) {
                    shadowDataBits = shadowDataBits shl 2
                    shadowDataBits = shadowDataBits or 2
                } else {
                    shadowDataBits = shadowDataBits shl 2
                }

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