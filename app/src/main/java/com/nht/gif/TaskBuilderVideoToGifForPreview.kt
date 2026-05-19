package com.nht.gif

import com.nht.gif.model.ExportColorFilter
import com.nht.gif.toolbox.Toolbox.toEmptyStringIf
import java.io.Serializable

data class TaskBuilderVideoToGifForPreview(
  val shortLength: Int,
  val colorQuality: Int,
  val lossy: Int?,
  val videoWH: Pair<Int, Int>,
  val colorKey: Pair<String, Int>?,
  val colorFilter: ExportColorFilter = ExportColorFilter.NONE,
) : Serializable {
  fun getCacheShortLength() = "${MyConstants.VIDEO_TO_GIF_PREVIEW_CACHE_DIR}${shortLength}.png"
  fun getCacheShortLengthColorKey() = getCacheShortLength() + (".${colorKey.toString().replace(" ", "")}.png").toEmptyStringIf { colorKey == null }
  fun getCacheShortLengthColorKeyFilter() =
    if (colorFilter == ExportColorFilter.NONE) getCacheShortLengthColorKey()
    else "${getCacheShortLengthColorKey()}.${colorFilter.name.lowercase()}.png"

  fun getCacheFilterPaletteGen() = "${getCacheShortLengthColorKeyFilter()}.${colorQuality}.png"
  fun getCacheFilterPaletteUse() = "${getCacheShortLengthColorKeyFilter()}.${colorQuality}.gif"
  fun getCacheFilterPaletteUseLossy() = getCacheFilterPaletteUse() + (".$lossy.gif").toEmptyStringIf { lossy == null }
}
