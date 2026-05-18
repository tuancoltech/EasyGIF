package com.nht.gif.ui.videotogif

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.arthenica.ffmpegkit.FFmpegKit
import com.nht.gif.MyConstants.FFMPEG_COMMAND_PREFIX_FOR_ALL_AN
import com.nht.gif.model.ExportColorFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

private const val THUMBNAIL_SIZE_PX = 72

class FilterThumbGeneratorImpl(
    private val inputVideoPath: String,
    private val clipDurationMs: Long,
    private val tempBaseDir: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val ffmpegExecutor: (String) -> Boolean = { command ->
        FFmpegKit.execute(command)?.returnCode?.isValueSuccess == true
    },
    private val bitmapDecoder: (String) -> Bitmap? = BitmapFactory::decodeFile,
) : FilterThumbGenerator {

    private val runCounter = AtomicLong()

    override suspend fun generate(emit: suspend (ExportColorFilter, Bitmap?) -> Unit) {
        val tempDir = File("$tempBaseDir/filter_thumbs/${runCounter.getAndIncrement()}/")
        val baseFramePath = "${tempDir.absolutePath}/base_frame.png"
        tempDir.mkdirs()
        try {
            withContext(ioDispatcher) {
                ffmpegExecutor(buildExtractFrameCommand(baseFramePath))
            }
            coroutineScope {
                ExportColorFilter.entries.map { filter ->
                    async(ioDispatcher) {
                        val bitmap = try {
                            thumbnailFor(filter, baseFramePath, tempDir.absolutePath)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                        emit(filter, bitmap)
                    }
                }.awaitAll()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun thumbnailFor(filter: ExportColorFilter, baseFramePath: String, tempDir: String): Bitmap? {
        if (filter == ExportColorFilter.NONE) {
            return bitmapDecoder(baseFramePath)?.centerCropTo(THUMBNAIL_SIZE_PX)
        }
        val thumbPath = "$tempDir/${filter.name.lowercase()}.jpg"
        return if (ffmpegExecutor(buildApplyFilterCommand(baseFramePath, filter, thumbPath))) {
            bitmapDecoder(thumbPath)
        } else {
            null
        }
    }

    internal fun buildExtractFrameCommand(outputPath: String): String {
        val timestampMs = clipDurationMs / 2
        return "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -ss ${timestampMs}ms -i \"$inputVideoPath\" -frames:v 1 -y \"$outputPath\""
    }

    internal fun buildApplyFilterCommand(baseFramePath: String, filter: ExportColorFilter, outputPath: String): String {
        val vfChain = checkNotNull(filter.vfChain) { "${filter.name} has no vfChain" }
        return "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -i \"$baseFramePath\" " +
            "-vf \"$vfChain,crop=min(iw\\,ih):min(iw\\,ih),scale=$THUMBNAIL_SIZE_PX:$THUMBNAIL_SIZE_PX\" " +
            "-frames:v 1 -y \"$outputPath\""
    }

    private fun Bitmap.centerCropTo(size: Int): Bitmap {
        val srcSize = minOf(width, height)
        val x = (width - srcSize) / 2
        val y = (height - srcSize) / 2
        val cropped = Bitmap.createBitmap(this, x, y, srcSize, srcSize)
        return if (cropped.width == size) cropped
        else Bitmap.createScaledBitmap(cropped, size, size, true)
    }
}
