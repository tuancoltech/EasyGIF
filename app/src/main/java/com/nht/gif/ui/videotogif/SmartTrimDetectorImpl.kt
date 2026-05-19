package com.nht.gif.ui.videotogif

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.arthenica.ffmpegkit.FFmpegKit
import com.nht.gif.MyConstants.FFMPEG_COMMAND_PREFIX_FOR_ALL_AN
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

private const val THUMB_SIZE_PX = 64
private const val FPS_SHORT_CLIP = 10
private const val FPS_LONG_CLIP = 5
private const val SHORT_CLIP_THRESHOLD_S = 10.0
private const val SIMILARITY_THRESHOLD = 0.85f

/**
 * Implements [SmartTrimDetector] by extracting downscaled grayscale thumbnails via FFmpeg
 * and comparing each frame's histogram against the first frame using Pearson correlation.
 *
 * Thumbnail extraction runs on [ioDispatcher]; histogram correlation runs on [defaultDispatcher].
 * All temp files are always deleted after detection completes, returns null, or throws.
 *
 * @param inputVideoPath Absolute path to the source video file.
 * @param startMs Trim start position in milliseconds.
 * @param endMs Trim end position in milliseconds.
 * @param tempBaseDir Base directory under which per-run thumbnail subdirectories are created.
 * @param ioDispatcher Dispatcher used for FFmpeg execution and file I/O.
 * @param defaultDispatcher Dispatcher used for in-memory histogram correlation.
 * @param ffmpegExecutor Executes an FFmpeg command string; returns true on success.
 * @param bitmapDecoder Decodes an image file at the given path into a [Bitmap], or null on failure.
 */
class SmartTrimDetectorImpl(
    private val inputVideoPath: String,
    private val startMs: Long,
    private val endMs: Long,
    private val tempBaseDir: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ffmpegExecutor: (String) -> Boolean = { command ->
        FFmpegKit.execute(command)?.returnCode?.isValueSuccess == true
    },
    private val bitmapDecoder: (String) -> Bitmap? = BitmapFactory::decodeFile,
) : SmartTrimDetector {

    private val runCounter = AtomicLong()

    override suspend fun detect(): Long? {
        val tempDir = File("$tempBaseDir/smart_trim_thumbs/${runCounter.getAndIncrement()}/")
        tempDir.mkdirs()
        try {
            val extracted = withContext(ioDispatcher) {
                ffmpegExecutor(buildExtractThumbnailsCommand(tempDir.absolutePath))
            }
            if (!extracted) return null
            val frames = tempDir.listFiles()
                ?.filter { it.extension == "png" }
                ?.sortedBy { it.name }
                ?: return null
            if (frames.size < 2) return null
            return computeBestTimestamp(frames)
        } finally {
            withContext(ioDispatcher) {
                tempDir.deleteRecursively()
            }
        }
    }

    /**
     * Builds the FFmpeg command that extracts [THUMB_SIZE_PX]×[THUMB_SIZE_PX] grayscale
     * thumbnails from [startMs] to [endMs] into [outputDir].
     * Frame rate is [FPS_SHORT_CLIP] for clips ≤[SHORT_CLIP_THRESHOLD_S]s, [FPS_LONG_CLIP] otherwise.
     */
    internal fun buildExtractThumbnailsCommand(outputDir: String): String {
        val durationSec = (endMs - startMs) / 1000.0
        val fps = if (durationSec <= SHORT_CLIP_THRESHOLD_S) FPS_SHORT_CLIP else FPS_LONG_CLIP
        return "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN " +
            "-ss ${startMs}ms -to ${endMs}ms " +
            "-i \"$inputVideoPath\" " +
            "-vf \"fps=$fps,scale=${THUMB_SIZE_PX}:${THUMB_SIZE_PX}:force_original_aspect_ratio=disable,format=gray\" " +
            "-y \"$outputDir/frame_%06d.png\""
    }

    /**
     * Computes histogram correlation between the first frame (reference) and each subsequent
     * frame in [frames]. Returns the timestamp (ms) of the highest-scoring frame above
     * [SIMILARITY_THRESHOLD], or null if no frame qualifies or the reference cannot be decoded.
     */
    private suspend fun computeBestTimestamp(frames: List<File>): Long? =
        withContext(defaultDispatcher) {
            val refHist = bitmapDecoder(frames.first().absolutePath)
                ?.let { histogram(it) } ?: return@withContext null
            val fps = if ((endMs - startMs) / 1000.0 <= SHORT_CLIP_THRESHOLD_S) FPS_SHORT_CLIP else FPS_LONG_CLIP
            val frameDurationMs = 1000L / fps
            var bestScore = SIMILARITY_THRESHOLD
            var bestTimestampMs: Long? = null
            frames.drop(1).forEachIndexed { index, file ->
                val score = bitmapDecoder(file.absolutePath)
                    ?.let { correlate(refHist, histogram(it)) } ?: return@forEachIndexed
                if (score > bestScore) {
                    bestScore = score
                    bestTimestampMs = startMs + (index + 1) * frameDurationMs
                }
            }
            bestTimestampMs
        }

    /**
     * Returns a 256-bin normalized grayscale histogram for [bitmap].
     * Uses the red channel as the intensity value (equal to green and blue for grayscale images).
     */
    private fun histogram(bitmap: Bitmap): FloatArray {
        val hist = FloatArray(256)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixel in pixels) hist[(pixel shr 16) and 0xFF]++
        val total = pixels.size.toFloat()
        for (i in hist.indices) hist[i] /= total
        return hist
    }

    /**
     * Returns the Pearson correlation coefficient between two normalized histograms,
     * in the range [-1, 1]. Returns 0 if both histograms are uniform (zero denominator).
     * Both histograms sum to 1.0 over 256 bins, so their mean is always 1/256.
     */
    private fun correlate(h1: FloatArray, h2: FloatArray): Float {
        val mean = 1f / h1.size
        var num = 0f; var den1 = 0f; var den2 = 0f
        for (i in h1.indices) {
            val d1 = h1[i] - mean
            val d2 = h2[i] - mean
            num += d1 * d2
            den1 += d1 * d1
            den2 += d2 * d2
        }
        val den = sqrt(den1 * den2)
        return if (den == 0f) 0f else num / den
    }
}
