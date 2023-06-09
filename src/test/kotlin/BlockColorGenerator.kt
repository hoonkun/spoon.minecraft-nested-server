import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import org.bukkit.Material
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class BlockColorGenerator: StringSpec() {

    init {

        val paths = Paths.get("")
        val projectRoot = File(paths.absolutePathString()).absolutePath
        val minecraftRoot = File(paths.absolutePathString()).parentFile.parentFile.parentFile.parentFile.absolutePath
        val texturesDir = File("${minecraftRoot}/minecraft/blocks")

        val mappings = mapOf(
            "log" to "log_top",
            "wood" to "log_top",
            "pointed_dripstone" to "pointed_dripstone_down_base",
            "(acacia|birch|dark_oak|oak|jungle|spruce|crimson|warped)_slab" to "$1_planks",
            "(acacia|birch|dark_oak|oak|jungle|spruce|crimson|warped)_stairs" to "$1_planks",
            "_slab" to "",
            "_stairs" to "",
            "hyphae" to "stem_top",
            "infested_" to "",
            "magma_block" to "magma",
            "snow_block" to "snow",
            "waxed_" to "",
            "dried_kelp_block" to "dried_kelp_top",
            "petrified_" to ""
        )

        val conditionalMappings = mapOf(
            "smooth_" to ""
        )

        val exceptionalNames = listOf("SLAB", "STAIRS", "LEAVES")
        val exactMatchExceptions = listOf("WATER", "LAVA", "DIRT_PATH", "POINTED_DRIPSTONE")

        val fixedColors = mapOf(
            "water" to "3D6F8D",
            "grass_block" to "3F6530",
            "dirt_path" to "8E7340"
        )

        val mapName: String.(where: Map<String, String>) -> String = map@ { where ->
            var result = this
            where.forEach {
                if (contains(Regex(it.key))) result = result.replace(Regex(it.key), it.value)
            }
            result
        }

        val isExceptional: Material.() -> Boolean = check@ {
            exceptionalNames.any { this.name.contains(it) } || exactMatchExceptions.any { this.name == it }
        }

        "generate data.json" {
            val textureFiles = texturesDir.listFiles()
            textureFiles shouldNotBe null

            val palette = Palette()
            val results = textureFiles!!
                .mapNotNull {
                    palette.start(it)?.let { color ->
                        val (r, g, b) = color
                        Pair(it.nameWithoutExtension, Color(r, g, b).hex.removePrefix("#").removePrefix("ff").uppercase())
                    }
                }
                .sortedBy { it.first }

            val validBlocks = Material.values()
                .filter { (it.isBlock && it.isSolid && it.isOccluding && !it.isAir) || it.isExceptional() }
                .filter { !it.name.contains("LEGACY") }
                .sortedBy { it.name }
                .toMutableList()

            val outputFile = File("$projectRoot/src/main/resources/block_colors.json")
            outputFile.writeBytes(
                "{\n${validBlocks.joinToString(",\n") {
                    val key = it.key.key
                    val name = it.name.lowercase()
                    val value = fixedColors[name]
                        ?: results.find { (t, _) -> t == name.mapName(mappings) }?.second
                        ?: results.find { (t, _) -> t.contains(name.mapName(mappings)) }?.second
                        ?: results.find { (t, _) -> t.contains(name.mapName(mappings).mapName(conditionalMappings)) }?.second
                    "  \"${key}\": ${if (value == null) "null" else "\"#$value\""}"
                }}\n}".toByteArray()
            )
            println("generate blocks: SUCCESS with size ${validBlocks.size}")
        }

    }

}