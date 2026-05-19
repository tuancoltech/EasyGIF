package com.nht.gif.ui.videotogif

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class SmartTrimDetectorImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tempBaseDir = System.getProperty("java.io.tmpdir")!!

    private fun makeDetector(
        startMs: Long = 0L,
        endMs: Long = 5000L,
        ffmpegExecutor: (String) -> Boolean = { false },
        bitmapDecoder: (String) -> Bitmap? = { null },
    ) = SmartTrimDetectorImpl(
        inputVideoPath = "/data/test.mp4",
        startMs = startMs,
        endMs = endMs,
        tempBaseDir = tempBaseDir,
        ioDispatcher = testDispatcher,
        defaultDispatcher = testDispatcher,
        ffmpegExecutor = ffmpegExecutor,
        bitmapDecoder = bitmapDecoder,
    )

    /**
     * Returns an executor that writes [count] fake PNG frames to the output directory
     * parsed from the FFmpeg command string, then reports success.
     */
    private fun executorCreatingFrames(count: Int): (String) -> Boolean = { cmd ->
        val outputDir = cmd.substringAfterLast("-y \"").substringBefore("/frame_")
        File(outputDir).mkdirs()
        repeat(count) { i -> File(outputDir, "frame_%06d.png".format(i + 1)).createNewFile() }
        true
    }

    /**
     * Returns a mock [Bitmap] whose single pixel reports [intensity] in the red channel.
     * Frames with the same intensity produce a histogram correlation of 1.0 (perfect match);
     * frames with a different intensity produce a correlation near 0 (below the 0.85 threshold).
     */
    private fun bitmapWithIntensity(intensity: Int): Bitmap = mockk<Bitmap>().also { bm ->
        every { bm.width } returns 1
        every { bm.height } returns 1
        every { bm.getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
            firstArg<IntArray>()[0] =
                (0xFF shl 24) or (intensity shl 16) or (intensity shl 8) or intensity
        }
    }

    // T3.7 — returns null when no candidate exceeds the similarity threshold
    @Test
    fun `detect returns null when no frame exceeds similarity threshold`() =
        runTest(testDispatcher) {
            // Reference intensity 200; all candidates at different intensities → correlation ≈ 0
            val intensities = listOf(200, 100, 50, 30)
            var callIndex = 0
            val detector = makeDetector(
                ffmpegExecutor = executorCreatingFrames(4),
                bitmapDecoder = { bitmapWithIntensity(intensities[callIndex++]) },
            )
            assertNull(detector.detect())
        }

    // T3.8 — returns the timestamp of the highest-scoring frame above threshold
    @Test
    fun `detect returns the correct timestamp for the best matching frame`() =
        runTest(testDispatcher) {
            // 5 s clip → fps=10 → frameDuration=100 ms
            // Frames: [ref=200, low=100, match=200, low=50]
            // Matching frame is index 1 after drop(1) → timestamp = 0 + (1+1)*100 = 200 ms
            val intensities = listOf(200, 100, 200, 50)
            var callIndex = 0
            val detector = makeDetector(
                startMs = 0L,
                endMs = 5000L,
                ffmpegExecutor = executorCreatingFrames(4),
                bitmapDecoder = { bitmapWithIntensity(intensities[callIndex++]) },
            )
            assertEquals(200L, detector.detect())
        }

    // T3.9 — histogram correlation is dispatched on defaultDispatcher, not ioDispatcher
    @Test
    fun `histogram correlation is dispatched on defaultDispatcher`() = runTest(testDispatcher) {
        var defaultDispatched = false
        val defaultDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                defaultDispatched = true
                testDispatcher.dispatch(context, block)
            }
        }
        SmartTrimDetectorImpl(
            inputVideoPath = "/data/test.mp4",
            startMs = 0L,
            endMs = 5000L,
            tempBaseDir = tempBaseDir,
            ioDispatcher = testDispatcher,
            defaultDispatcher = defaultDispatcher,
            ffmpegExecutor = executorCreatingFrames(2),
            bitmapDecoder = { null },
        ).detect()
        assertTrue("defaultDispatcher must be invoked for histogram correlation", defaultDispatched)
    }

    // T3.10 — temp thumbnails are deleted after detection completes
    @Test
    fun `temp files are deleted after detection completes`() = runTest(testDispatcher) {
        val baseDir = Files.createTempDirectory("t310").toFile()
        try {
            SmartTrimDetectorImpl(
                inputVideoPath = "/data/test.mp4",
                startMs = 0L,
                endMs = 5000L,
                tempBaseDir = baseDir.absolutePath,
                ioDispatcher = testDispatcher,
                defaultDispatcher = testDispatcher,
                ffmpegExecutor = executorCreatingFrames(3),
                bitmapDecoder = { null },
            ).detect()
            assertFalse(
                "temp dir must be gone after completion",
                File(baseDir, "smart_trim_thumbs/0").exists(),
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    // T3.11 — temp thumbnails are deleted when detection throws
    @Test
    fun `temp files are deleted when detection throws`() = runTest(testDispatcher) {
        val baseDir = Files.createTempDirectory("t311").toFile()
        try {
            SmartTrimDetectorImpl(
                inputVideoPath = "/data/test.mp4",
                startMs = 0L,
                endMs = 5000L,
                tempBaseDir = baseDir.absolutePath,
                ioDispatcher = testDispatcher,
                defaultDispatcher = testDispatcher,
                ffmpegExecutor = executorCreatingFrames(2),
                bitmapDecoder = { throw RuntimeException("decode error") },
            ).let { runCatching { it.detect() } }
            assertFalse(
                "temp dir must be gone after throw",
                File(baseDir, "smart_trim_thumbs/0").exists(),
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }
}
