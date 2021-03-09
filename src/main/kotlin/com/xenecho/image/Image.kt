package com.xenecho.image

import java.awt.Color
import java.awt.Font
import java.awt.Image
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileWriter
import java.io.InputStream
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt


/**
 * Creates a [BufferedImage] for use internally can generate ascii text from the image and convert
 * it into an image with various mode switches to tweak the result
 *
 * @param stream [InputStream] to convert to a [BufferedImage]
 * @param scale [Float] between 0..1 represents how to scale the image with 1.0 being full size
 *
 */
class Image {

    /**
     * http://paulbourke.net/dataformats/asciiart
     */
    companion object {
        const val ASCII_BRIGHTNESS_RAMP_STANDARD = "\$@B%8&WM#*oahkbdpqwmZO0QLCJUYXzcvunxrjft/\\|()1{}[]?-_+~i!lI;:,\"^`"
        const val ASCII_BRIGHTNESS_RAMP_ALT = "@8LF#]{}[*+=-;:,. "
        const val ASCII_BRIGHTNESS_RAMP_SIMPLE = "@%#*+=-:. "
        const val ASCII_BRIGHTNESS_RAMP_CUSTOM = "@8&#*+=<->:. "
    }

    private val image: BufferedImage // Source image
    private lateinit var _pixelArray: List<List<RGB>> // Late init to use as a cache

    constructor(stream: InputStream, scale: Float = 1f) : this(ImageIO.read(stream), scale)

    constructor(image: BufferedImage, scale: Float = 1f) {

        // A scale of 1 means keep the source image size
        // Any other value means we need to resize the image

        val s = clampScale(scale)
        this.image = if (s == 1f) {
            image
        } else {
            scaleBufferedImage(image, s)
        }

    }

    /**
     * Get a RGB pixel array from the image
     */
    fun getPixelArray(): List<List<RGB>> {
        if (!this::_pixelArray.isInitialized) {
            val result = mutableListOf<List<RGB>>()
            val img = image
            val maxY = img.height
            val maxX = img.width
            for (y in 0 until maxY) {
                val row = mutableListOf<RGB>()
                for (x in 0 until maxX) {
                    val clr = Color(img.getRGB(x, y))
                    val red = clr.red
                    val green = clr.green
                    val blue = clr.blue
                    row.add(RGB(red, green, blue))
                }
                result.add(row)
            }
            _pixelArray = result
        }
        return _pixelArray
    }

    /**
     * Build an ascii array from the image
     */
    fun getAsciiArray(asciiRamp: String = ASCII_BRIGHTNESS_RAMP_SIMPLE, luminanceMode: LuminanceMode = LuminanceMode.RELATIVE): List<List<Char>> {
        val pixels = getPixelArray()
        return pixels.map { it.map { toAscii(it, asciiRamp, luminanceMode) } }
    }

    /**
     * Writes the ascii content to a file
     */
    fun writeAsciiFile(file: File, asciiRamp: String = ASCII_BRIGHTNESS_RAMP_SIMPLE) {
        val ascii = getAsciiArray(asciiRamp)
        FileWriter(file, false).use { f ->
            ascii.forEach { row ->
                row.forEach { ch ->
                    f.write(ch.toInt())
                }
                f.write(System.lineSeparator())
            }
        }
    }

    /**
     * Build an ascii image using a monospaced font
     */
    fun getAsciiImage(
        asciiRamp: String = ASCII_BRIGHTNESS_RAMP_SIMPLE,
        fontSize: Int = 3,
        asciiMode: AsciiMode = AsciiMode.GREYSCALE,
        luminanceMode: LuminanceMode = LuminanceMode.RELATIVE
    ): BufferedImage {

        // Convert the image to ascii
        val ascii = getAsciiArray(asciiRamp, luminanceMode)

        // Massage the arrays to make them easier to deal with
        val lines = ascii.map { it.map { ch -> Character.toString(ch) }.joinToString("") }

        // Build a font to use
        val textAttributes: MutableMap<TextAttribute, Any> = HashMap()
        textAttributes[TextAttribute.FAMILY] = "Courier New"
        textAttributes[TextAttribute.FONT] = Font.PLAIN
        textAttributes[TextAttribute.SIZE] = fontSize
        val font = Font(textAttributes)

        var width = 0
        var height = 0
        var charWidth: Int
        var pad: Int

        lines.first().let { line ->
            val tmp = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            val tmpg2d = tmp.createGraphics()
            tmpg2d.font = font
            val fontMetrics = tmpg2d.fontMetrics
            // Calculate the width of a single character we're using monospace fonts so they should all be the same
            charWidth = fontMetrics.stringWidth("1")
            // Calculate how much we need to pad each character, prevents aspect ratio skewing
            pad = tmpg2d.fontMetrics.height - charWidth
            // The width is the length of the line + any character padding
            width = fontMetrics.stringWidth(line) + (pad * line.length)
            // The height is simply the font height * the number of lines
            if (height == 0) height = fontMetrics.height * lines.size
            tmpg2d.dispose()
        }

        // Final result image
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g2d = img.createGraphics()
        g2d.font = font

        //region Rendering Hints

        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 100)

        //endregion

        // Background

        g2d.color = if (asciiMode == AsciiMode.GREYSCALE_INVERTED || asciiMode == AsciiMode.COLOUR_INVERTED_BACKGROUND) {
            Color.BLACK
        } else {
            Color.WHITE
        }

        g2d.fillRect(0, 0, width, height)

        // Text colour, overridden later if ascii mode colour

        g2d.color = if (asciiMode == AsciiMode.GREYSCALE_INVERTED) {
            Color.WHITE
        } else {
            Color.BLACK
        }

        // Write the text to the image

        val isColour = asciiMode == AsciiMode.COLOUR || asciiMode == AsciiMode.COLOUR_INVERTED_BACKGROUND
        var lineY = 0
        var lineX = 0
        lines.forEachIndexed { i, line ->
            line.forEachIndexed { j, ch ->
                if (isColour) {
                    val rgb = getPixelArray()[i][j]
                    g2d.color = Color(rgb.r, rgb.g, rgb.b)
                }
                g2d.drawString(ch.toString(), lineX, lineY)
                lineX += charWidth + pad
            }
            lineX = 0
            lineY += g2d.fontMetrics.height
        }

        g2d.dispose()

        return img
    }

    /**
     * Converts the RGB value to ascii character using the provided ramp
     */
    private fun toAscii(rgb: RGB, asciiRamp: String, luminanceMode: LuminanceMode): Char {
        val y = calculateLuminance(rgb, luminanceMode)

        //((Input - InputLow) / (InputHigh - InputLow)) * (OutputHigh - OutputLow) + OutputLow;

        val inputLow = 0
        val inputHigh = 1
        val outputLow = 0
        val outputHigh = asciiRamp.length - 1

        val converted = ((y - inputLow) / (inputHigh - inputLow)) * (outputHigh - outputLow) + outputLow;

        return asciiRamp[converted.roundToInt()]
    }

    /**
     * Convert the RGB value into a single luminance value
     *
     * https://en.wikipedia.org/wiki/Luma_(video)
     *
     */
    private fun calculateLuminance(rgb: RGB, luminanceMode: LuminanceMode): Double {
        val r = rgb.r / 255f
        val g = rgb.g / 255f
        val b = rgb.b / 255f
        return when (luminanceMode) {
            LuminanceMode.PERCEIVED_1 -> 0.299 * r + 0.587 * g + 0.114 * b
            LuminanceMode.PERCEIVED_2 -> sqrt(0.299 * r.pow(2) + 0.587 * g.pow(2) + 0.114 * b.pow(2))
            else -> 0.2126 * r + 0.7152 * g + 0.0722 * b
        }
    }

    /**
     * Clamps the scale between 0 and 1
     */
    private fun clampScale(scale: Float): Float {
        return when {
            scale > 1 -> 1f
            scale < 0 -> 0.1f
            else -> scale
        }
    }

    /**
     * Scales a buffered image by multiplying width and height by a scale value between
     * 0 and 1
     */
    private fun scaleBufferedImage(it: BufferedImage, scale: Float): BufferedImage {
        val newWidth = (it.width * scale).toInt()
        val newHeight = (it.height * scale).toInt()
        val tmp = it.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
        val img = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = img.createGraphics()
        g2d.drawImage(tmp, 0, 0, null)
        g2d.dispose()
        return img
    }

    /**
     * Mode to render the ascii image
     */
    enum class AsciiMode {

        /**
         * Render the ascii image with a white background and black text
         */
        GREYSCALE,

        /**
         * Render the ascii image with a black background and white text
         */
        GREYSCALE_INVERTED,

        /**
         * Render the ascii image with a white background and coloured text matching the original image pixel values
         */
        COLOUR,

        /**
         * Render the ascii image with a black background and coloured text matching the original image pixel values
         */
        COLOUR_INVERTED_BACKGROUND

    }

    enum class LuminanceMode {

        /**
         * [Standard for certain colour spaces](https://en.wikipedia.org/wiki/Relative_luminance)
         */
        RELATIVE,

        /**
         * [Perceived 1](https://www.w3.org/TR/AERT/#color-contrast)
         */
        PERCEIVED_1,

        /**
         * [Perceived 2](https://alienryderflex.com/hsp.html)
         */
        PERCEIVED_2

    }

    /**
     * Simple data class represents a RGB pixel
     */
    data class RGB(val r: Int, val g: Int, val b: Int) {
        init {
            val valueError = "value must be between 0 and 255"
            if (r < 0 || r > 255) throw Exception(valueError)
            if (g < 0 || g > 255) throw Exception(valueError)
            if (b < 0 || b > 255) throw Exception(valueError)
        }
    }

}
