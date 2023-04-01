import com.google.common.math.IntMath.pow
import java.io.File
import javax.imageio.ImageIO

class Palette {

    fun start(file: File): Triple<Int, Int, Int>? {
        val stream = ImageIO.createImageInputStream(file)
        val iterator = ImageIO.getImageReaders(stream)

        if (!iterator.hasNext()) {
            return null
        }

        val image = iterator.next().let {
            it.input = stream
            it.read(0)
        }

        val width = image.width
        val height = image.height

        var r = 0f
        var g = 0f
        var b = 0f
        var invalids = 0
        for (x in 0 until width) {
            for (y in 0 until height) {
                val color = Color(image.getRGB(x, y))
                if (color.alpha != 1f) {
                    invalids++
                    continue
                }
                r += color.red * 255
                g += color.green * 255
                b += color.blue * 255
            }
        }

        val pixels = width * height - invalids
        return Triple((r / pixels).toInt(), (g / pixels).toInt(), (b / pixels).toInt())
    }

}

class Color {

    companion object {
        private fun Float.normalize(): Int = (this * 255).toInt()
        private fun Float.hex(): String = (this * 255).toInt().toString(16).padStart(2, '0')
    }

    val value: Int

    val alpha: Float
    val red: Float
    val green: Float
    val blue: Float

    val hex: String

    constructor(color: Int) {
        this.value = color

        val mask = pow(2, 8) - 1
        this.alpha = (color ushr 24) / 255f
        this.red = ((color ushr 16) and mask) / 255f
        this.green = ((color ushr 8) and mask) / 255f
        this.blue = (color and mask) / 255f

        this.hex = "#".plus("${alpha.hex()}${red.hex()}${green.hex()}${blue.hex()}")
    }

    constructor(hex: String) : this(hex.removePrefix("#").toInt(16))

    constructor(r: Int, g: Int, b: Int, a: Int = 255) : this((a shl 24) or (r shl 16) or (g shl 8) or (b))

    constructor(r: Float, g: Float, b: Float, a: Float = 1f) : this((a.normalize() shl 24) or (r.normalize() shl 16) or (g.normalize() shl 8) or (b.normalize()))

}