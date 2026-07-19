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

    init {
        resetDirectory(VIDEO_TO_GIF_PREVIEW_CACHE_DIR)
    }

    /**
     * Produces the preview bitmap for [taskBuilder] by running the FFmpeg palette pipeline on
     * [frame]. Intermediate outputs (scaled, colour-keyed, filtered, palette-gen, palette-use,
     * lossy) are each written once and guarded by [fileExistsCache].
     */
    fun render(taskBuilder: TaskBuilderVideoToGifForPreview): Bitmap = with(taskBuilder) {
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
        bitmapCache.getOrPut(this.copy(lossy = null)) { BitmapFactory.decodeFile(getCacheFilterPaletteUse()) }
        if (!bitmapCache.containsKey(this)) {
            gifsicleLossy(lossy!!, getCacheFilterPaletteUse(), getCacheFilterPaletteUseLossy(), false)
            fileExistsCache += getCacheFilterPaletteUseLossy()
            bitmapCache[this] = BitmapFactory.decodeFile(getCacheFilterPaletteUseLossy())
        }
        bitmapCache[this]!!
    }

    /** Clears both in-memory caches and deletes all files in the preview cache directory. */
    fun clear() {
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

    private fun ensureCached(key: String, produce: () -> Unit) {
        if (key !in fileExistsCache) { produce(); fileExistsCache += key }
    }
}
