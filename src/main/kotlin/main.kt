import com.xenecho.image.Image
import org.bytedeco.javacv.CanvasFrame
import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.OpenCVFrameGrabber
import java.io.File
import javax.imageio.ImageIO
import java.util.concurrent.CountDownLatch





fun main() {
    try {
        val app = App()
        println("Uncomment a test and tweak some of the values")
        //app.runAllImageTest()
        //app.runLargeFontTest()
        //app.runCamTest()
    } catch (ex: Exception) {
        ex.printStackTrace()
    }
}

class App {

    /**
     * https://stackoverflow.com/questions/14070370/how-to-capture-and-record-video-from-webcam-using-javacv
     */
    fun runCamTest() {
        val grabber = OpenCVFrameGrabber(0)
        grabber.imageWidth = 1920
        grabber.imageHeight = 1080

        grabber.start()

        var frame = grabber.grab()

        val canvasFrame = CanvasFrame("Cam")
        canvasFrame.setCanvasSize(frame.imageWidth, frame.imageHeight)

        println("framerate = " + grabber.frameRate)
        grabber.frameRate = grabber.frameRate

        val converter = Java2DFrameConverter()
        while (canvasFrame.isVisible && grabber.grab().also { frame = it } != null) {
            canvasFrame.showImage(
                Image(converter.getBufferedImage(frame), 0.1f)
                    .getAsciiImage(
                        fontSize = 9,
                        asciiRamp = Image.ASCII_BRIGHTNESS_RAMP_STANDARD,
                        asciiMode = Image.AsciiMode.COLOUR_INVERTED_BACKGROUND
                    )
            )
        }

        grabber.stop()
        canvasFrame.dispose()
    }

    fun runAllImageTest() {
        this::class.java.getResourceAsStream("images/test2.jpg").use {
            val image = Image(it, 0.6f)
            val fontSize = 6
            println("Starting image generation...")
            Image.AsciiMode.values().forEach { mode ->
                val modeName = mode.toString().toLowerCase()
                if (modeName.contains("inverted")) return@forEach
                Image.LuminanceMode.values().forEach { lum ->
                    val lumModeName = lum.toString().toLowerCase()
                    val ramp1 = "output/$modeName-smp-$lumModeName-result.jpg"
                    val ramp2 = "output/$modeName-std-$lumModeName-result.jpg"
                    val ramp3 = "output/$modeName-alt-$lumModeName-result.jpg"
                    val ramp4 = "output/$modeName-cst-$lumModeName-result.jpg"
                    // Some further optimisations could be done here, latching is used to prevent memory exceptions
                    // that I was too lazy to fix for the sake of a throw away demo app
                    val latch = CountDownLatch(4)
                    Thread {
                        println("Generating $ramp1...")
                        ImageIO.write(image.getAsciiImage(fontSize = fontSize, asciiMode = mode, luminanceMode = lum), "jpg", File(ramp1))
                        latch.countDown()
                        println("Done")
                    }.start()
                    Thread {
                        println("Generating $ramp2...")
                        ImageIO.write(image.getAsciiImage(Image.ASCII_BRIGHTNESS_RAMP_STANDARD, fontSize = fontSize, asciiMode = mode, luminanceMode = lum), "jpg", File(ramp2))
                        latch.countDown()
                        println("Done")
                    }.start()
                    Thread {
                        println("Generating $ramp3...")
                        ImageIO.write(image.getAsciiImage(Image.ASCII_BRIGHTNESS_RAMP_ALT, fontSize = fontSize, asciiMode = mode, luminanceMode = lum), "jpg", File(ramp3))
                        latch.countDown()
                        println("Done")
                    }.start()
                    Thread {
                        println("Generating $ramp4...")
                        ImageIO.write(image.getAsciiImage(Image.ASCII_BRIGHTNESS_RAMP_CUSTOM, fontSize = fontSize, asciiMode = mode, luminanceMode = lum), "jpg", File(ramp4))
                        latch.countDown()
                        println("Done")
                    }.start()
                    latch.await()
                }
            }
            println("Generation complete")
        }
    }

    fun runLargeFontTest() {
        this::class.java.getResourceAsStream("images/test3.jpg").use {
            val image = Image(it, 0.2f)
            ImageIO.write(
                image.getAsciiImage(
                    asciiRamp = Image.ASCII_BRIGHTNESS_RAMP_STANDARD, fontSize = 9,
                    asciiMode = Image.AsciiMode.GREYSCALE_INVERTED,
                    luminanceMode = Image.LuminanceMode.PERCEIVED_2
                ), "jpg", File("output/LARGER_FONT_TEST.jpg")
            )
        }
    }

}
