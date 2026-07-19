package com.nht.gif.ui.videotogif

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.arthenica.ffmpegkit.FFmpegKit
import com.nht.gif.CropParams
import com.nht.gif.MyConstants.FFMPEG_COMMAND_PREFIX_FOR_ALL_AN
import com.nht.gif.MyConstants.VIDEO_TO_GIF_PREVIEW_CACHE_DIR
import com.nht.gif.TaskBuilderVideoToGifForPreview
import com.nht.gif.toolbox.FileTools.resetDirectory
import com.nht.gif.toolbox.MediaTools.gifsicleLossy
import com.nht.gif.toolbox.MediaTools.saveToPng
import com.nht.gif.toolbox.Toolbox.logRed
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Owns the static-preview rendering pipeline for the export-options dialog.
 *
 * Holds the in-memory bitmap cache and the on-disk file-existence cache for intermediate FFmpeg
 * outputs. Each rendering stage is guarded by [ensureCached] so only stages invalidated by a
 * setting change are re-executed during a single dialog session.
 *
 * Cleans the preview cache directory on construction (discards any leftover files from a
 * previous session) and again on [clear] (called from [onDestroyView]).
 *
 * @param frame Single video frame pre-composited with the text overlay and cropped to the user's
 *   crop rectangle. Serves as the input base for every preview render.
 * @param cropParams Used to compute the scaled output resolution for a given short-side length.
 */
class PreviewController(
    private val frame: Bitmap,
    private val cropParams: CropParams,
) {
    private val bitmapCache = mutableMapOf<TaskBuilderVideoToGifForPreview, Bitmap>()
    private val fileExistsCache = mutableSetOf<String>()

    // All disk/cache access is serialized on a single background thread so the mutable caches and
    // temp files are never touched concurrently, regardless of how many render requests overlap.
    private val renderContext = Dispatchers.IO.limitedParallelism(1)

    init {
        resetDirectory(VIDEO_TO_GIF_PREVIEW_CACHE_DIR)
    }

    /**
     * Produces the preview bitmap for [taskBuilder] by running the FFmpeg palette pipeline on
     * [frame]. Intermediate outputs (scaled, colour-keyed, filtered, palette-gen, palette-use,
     * lossy) are each written once and guarded by [fileExistsCache].
     *
     * Suspends and runs on [renderContext] so the pipeline never blocks the caller and never
     * mutates the caches concurrently.
     */
    suspend fun render(taskBuilder: TaskBuilderVideoToGifForPreview): Bitmap =
        withContext(renderContext) { renderBlocking(taskBuilder) }

    // suspend + ensureActive() between stages: a superseded render aborts at the next stage instead
    // of finishing the whole pipeline, freeing the single renderContext thread for the latest request.
    // ensureActive() (not yield()) never releases the thread, so live renders still run atomically and
    // the cache serialization guaranteed by the single-threaded dispatcher is preserved.
    private suspend fun renderBlocking(taskBuilder: TaskBuilderVideoToGifForPreview): Bitmap = with(taskBuilder) {
        ensureCacheDirectory()
        ensureCached(getCacheShortLength()) {
            val (w, h) = cropParams.calcScaledResolution(shortLength)
            frame.scale(w, h).saveToPng(getCacheShortLength())
        }
        ensureCached(getCacheShortLengthColorKey()) {
            colorKey?.let { ck ->
                val cmd = "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -i \"${getCacheShortLength()}\" " +
                    "-vf colorkey=#${ck.first}:${ck.second / 100f}:0 -y \"${getCacheShortLengthColorKey()}\""
                logRed("colorKey cmd", cmd)
                FFmpegKit.execute(cmd)
            }
        }
        ensureCached(getCacheShortLengthColorKeyFilter()) {
            colorFilter.vfChain?.let { vfChain ->
                FFmpegKit.execute(
                    "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -i \"${getCacheShortLengthColorKey()}\" " +
                        "-vf \"$vfChain\" -y \"${getCacheShortLengthColorKeyFilter()}\""
                )
            }
        }
        ensureCached(getCacheFilterPaletteGen()) {
            FFmpegKit.execute(
                "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -i \"${getCacheShortLengthColorKeyFilter()}\" " +
                    "-filter_complex palettegen=max_colors=$colorQuality:stats_mode=diff -y \"${getCacheFilterPaletteGen()}\""
            )
        }
        ensureCached(getCacheFilterPaletteUse()) {
            FFmpegKit.execute(
                "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -i \"${getCacheShortLengthColorKeyFilter()}\" " +
                    "-i ${getCacheFilterPaletteGen()} -filter_complex \"[0:v][1:v] paletteuse=dither=bayer\" " +
                    "-y \"${getCacheFilterPaletteUse()}\""
            )
        }
        currentCoroutineContext().ensureActive()
        bitmapCache.getOrPut(this.copy(lossy = null)) { BitmapFactory.decodeFile(getCacheFilterPaletteUse()) }
        if (!bitmapCache.containsKey(this)) {
            currentCoroutineContext().ensureActive()
            gifsicleLossy(lossy!!, getCacheFilterPaletteUse(), getCacheFilterPaletteUseLossy(), false)
            fileExistsCache += getCacheFilterPaletteUseLossy()
            bitmapCache[this] = BitmapFactory.decodeFile(getCacheFilterPaletteUseLossy())
        }
        bitmapCache[this]!!
    }

    /** Clears both in-memory caches and deletes all files in the preview cache directory. */
    suspend fun clear() = withContext(renderContext) {
        bitmapCache.clear()
        fileExistsCache.clear()
        resetDirectory(VIDEO_TO_GIF_PREVIEW_CACHE_DIR)
    }

    /**
     * Re-creates the cache directory if it has gone missing since construction. The OS can purge
     * the app cache dir (which backs this preview cache) while the dialog is backgrounded, and
     * [render] runs again on every STARTED transition via repeatOnLifecycle. FileOutputStream and
     * FFmpeg do not create parent directories, so a write would otherwise fail with ENOENT. When
     * the directory is gone the files behind both caches are gone too, so drop them and start clean.
     */
    private fun ensureCacheDirectory() {
        if (File(VIDEO_TO_GIF_PREVIEW_CACHE_DIR).exists()) return
        bitmapCache.clear()
        fileExistsCache.clear()
        resetDirectory(VIDEO_TO_GIF_PREVIEW_CACHE_DIR)
    }

    private suspend fun ensureCached(key: String, produce: () -> Unit) {
        // Abort before each stage if the render was superseded; the check is before produce(), so a
        // stage is either fully done and cached or not started — never a half-written file marked cached.
        currentCoroutineContext().ensureActive()
        if (key !in fileExistsCache) { produce(); fileExistsCache += key }
    }
}
