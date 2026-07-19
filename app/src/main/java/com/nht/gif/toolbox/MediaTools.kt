package com.nht.gif.toolbox

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.annotation.IntRange
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation
import com.nht.gif.MyApplication
import com.nht.gif.MyConstants
import com.nht.gif.toolbox.Toolbox.logRed
import com.nht.gif.toolbox.Toolbox.swapIf
import com.nht.gif.toolbox.Toolbox.toEmptyStringIf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap


/**
 * 当能够访问媒体文件的绝对路径时，请勿将其通转换为 Uri 传入给 FFmpegKit，
 * 因为这样传入时，文件将不带有文件后辍名，可能导致 FFmpeg 无法正确读取媒体文件！
 */
object MediaTools {

  /**
   * a function to generate a transparent [Bitmap] with the given width and height.
   * @param w The width of the [Bitmap].
   * @param h The height of the [Bitmap].
   * @return The generated transparent [Bitmap].
   */
  fun generateTransparentBitmap(w: Int, h: Int) =
    createBitmap(w, h).apply { eraseColor(Color.TRANSPARENT) }

  fun getVideoDurationByAndroidSystem(path: String) = with(MediaMetadataRetriever()) {
    try {
      setDataSource(path)
      val duration = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toInt()
      if (duration == 0) null else duration
    } catch (_: Exception) {
      null
    } finally {
      release()
    }
  }

  /**
   * Extracts a single frame at [timestamp_ms], trying MediaMetadataRetriever first and falling
   * back to FFmpeg. Returns null when neither method yields a decodable frame (unsupported codec,
   * a timestamp with no reachable frame, a failed decode, ...) so callers can degrade gracefully
   * instead of crashing on a force-unwrapped null.
   *
   * Suspends and runs the (heavy) retrieval + decode on [Dispatchers.IO] so it never blocks the
   * caller's thread; must be invoked from a coroutine.
   */
  suspend fun getVideoSingleFrame(path: String, timestamp_ms: Long): Bitmap? = withContext(Dispatchers.IO) {
    val frameFromRetriever = runCatching {
      with(MediaMetadataRetriever()) {
        try {
          setDataSource(path)
          // OPTION_CLOSEST_SYNC because OPTION_CLOSEST is slower and may throw NullPointerException.
          getFrameAtTime(timestamp_ms * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
          release()
        }
      }
    }.getOrNull()
    frameFromRetriever ?: decodeSingleFrameWithFFmpeg(path, timestamp_ms)
  }

  /**
   * FFmpeg fallback for [getVideoSingleFrame]: writes one frame to a temp file and decodes it.
   * Returns null when FFmpeg produced nothing decodable.
   */
  private fun decodeSingleFrameWithFFmpeg(path: String, timestamp_ms: Long): Bitmap? {
    val tempPath = MyConstants.GET_VIDEO_SINGLE_FRAME_WITH_FFMPEG_TEMP_PATH
    // Delete first: on failure (unsupported codec, audio-only, seek past EOF, ...) FFmpeg leaves the
    // output untouched, so without this a stale frame from a previously processed video would be
    // decoded and returned instead of null — silently bypassing callers' null-failure handling.
    File(tempPath).delete()
    getVideoSingleFrameWithFFmpeg(path, timestamp_ms, 5, tempPath)
    return BitmapFactory.decodeFile(tempPath)?.copy(Bitmap.Config.ARGB_8888, true)
  }

  fun getVideoSingleFrameWithFFmpeg(
    path: String, timestamp_ms: Long, @IntRange(2, 31) quality: Int, outputPath: String
  ) =
    FFmpegKit.execute("${MyConstants.FFMPEG_COMMAND_PREFIX_FOR_ALL_AN} -ss ${timestamp_ms}ms -i \"$path\" -frames:v 1 -q:v $quality -y \"$outputPath\"")!!

  fun Bitmap.saveToPng(path: String) = FileOutputStream(path).use { compress(Bitmap.CompressFormat.PNG, 100, it) }

  fun Bitmap.saveToJpg(path: String, @IntRange(0, 100) quality: Int) =
    FileOutputStream(path).use { compress(Bitmap.CompressFormat.JPEG, quality, it) }

  fun getVideoFps(path: String) = try {
    val fpsFraction = mediaInformation(path)!!.streams.first { it.type == "video" }.averageFrameRate
    val numerator = fpsFraction.split("/").toTypedArray()[0].toInt()
    val denominator = fpsFraction.split("/").toTypedArray()[1].toInt()
    numerator.toDouble() / denominator
  } catch (_: Exception) {
    null
  }

  fun getRotationFromProperties(properties: JSONObject) = try {
    var rotation = 0
    val sideDataListJSONArray = (properties.get("side_data_list") as JSONArray)
    (0 until sideDataListJSONArray.length()).forEach {
      try {
        rotation = -sideDataListJSONArray.getJSONObject(it).getInt("rotation")
      } catch (_: Exception) {
      }
    }
    if (rotation % 90 != 0) {
      logRed("rotation = $rotation", "rotation % 90 != 0")
      rotation = 0
    }
    while (rotation < 0) {
      rotation += 360
    }
    while (rotation >= 360) {
      rotation -= 360
    }
    rotation
  } catch (_: Exception) {
    0
  }

  fun getVideoRotation(path: String) =
    getRotationFromProperties(mediaInformation(path)!!.firstVideoStream()!!.allProperties)

  fun getImageRotation(path: String) = getRotationFromProperties(
    mediaInformation(path, true)!!.allProperties.getJSONArray("frames").getJSONObject(0)
  )

  /**
   * @param rotation can be obtained via getVideoRotation() or getImageRotation()
   */
  fun getRotatedWidthAndHeight(path: String, rotation: Int) = (mediaInformation(path)!!.firstVideoStream()!!).let {
    Pair(it.width.toInt(), it.height.toInt()).swapIf { rotation % 180 != 0 }
  }

  fun MediaInformation.firstVideoStream() = streams.firstOrNull { it.type == "video" }

  fun getVideoDurationMsByFFmpeg(path: String) = try {
    val mediaInformation = mediaInformation(path)!!
    (((mediaInformation.firstVideoStream()!!.getStringProperty("duration"))
      ?: (mediaInformation.duration)).toFloat() * 1000f).roundToInt()
  } catch (_: Exception) {
    null
  }

  fun mediaInformation(path: String, withFrames: Boolean = false): MediaInformation? =
    FFprobeKit.getMediaInformationFromCommand(
      "-v quiet -hide_banner -print_format json -show_format -show_streams ${("-show_frames ").toEmptyStringIf { !withFrames }}-i \"$path\""
    ).mediaInformation

  fun getImageWidthHeight(path: String) = with(BitmapFactory.Options()) {
    this.inJustDecodeBounds = true
    BitmapFactory.decodeFile(path, this)
    Pair(this.outWidth, this.outHeight)
  }

  /** Slow operation: this function may takes at least 5s! */
  fun videoKeyFramesTimestampList(path: String) =
    FFprobeKit.execute("-loglevel error -skip_frame nokey -select_streams v:0 -show_entries frame=pts_time \"$path\"")
      .allLogsAsString
      .split("\n")
      .filter { it.startsWith("pts_time=") }
      .map { ((it.split('=')[1]).toFloat() * 1000f).toInt() }

  /**
   * lossy should >= 0 .
   * return true when succeed, false when failed.
   * if outputGifPath is null, then output will overwrite input file.
   */
  fun gifsicleLossy(
    lossy: Int,
    inputGifPath: String,
    outputGifPath: String?,
    enableO3: Boolean,
  ): Boolean {
    val nativeLibraryDir = MyApplication.appContext.applicationInfo.nativeLibraryDir
    val gifsiclePath = "${nativeLibraryDir}/libgifsicle.so"
    val gifsicleEnvp = arrayOf("LD_LIBRARY_PATH=${nativeLibraryDir}")
    val gifsicleCmd =
      if (outputGifPath == null)
        "$gifsiclePath -b ${"-O3".toEmptyStringIf { !enableO3 }} --lossy=$lossy $inputGifPath"
      else
        "$gifsiclePath ${"-O3".toEmptyStringIf { !enableO3 }} --lossy=$lossy --output $outputGifPath $inputGifPath"
    return try {
      (Runtime.getRuntime().exec(gifsicleCmd, gifsicleEnvp).waitFor() == 0)
    } catch (e: Exception) {
      logRed("gifsicleLossy() failed", e.message)
      false
    }
  }

  fun extractVideoFromMvimg(mvimg: String, video: String): Boolean {
    try {
      val byteArray = File(mvimg).readBytes()
      val index = byteArray.toString(Charset.forName("ISO-8859-1")).indexOf("ftypmp42") - 4
      if (index == -5) return false
      FileOutputStream(video).use {
        it.write(byteArray, index, byteArray.size - index)
      }
      return true
    } catch (e: Exception) {
      e.printStackTrace()
      return false
    }
  }

}