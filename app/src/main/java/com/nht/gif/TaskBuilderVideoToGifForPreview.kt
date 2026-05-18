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
  fun getCache_shortLength() = "${MyConstants.VIDEO_TO_GIF_PREVIEW_CACHE_DIR}${shortLength}.png"
  fun getCache_shortLength_colorKey() = getCache_shortLength() + (".${colorKey.toString().replace(" ", "")}.png").toEmptyStringIf { colorKey == null }
  fun getCache_shortLength_colorKey_filter() =
    if (colorFilter == ExportColorFilter.NONE) getCache_shortLength_colorKey()
    else "${getCache_shortLength_colorKey()}.${colorFilter.name.lowercase()}.png"
  fun getCache_filter_palettegen() = "${getCache_shortLength_colorKey_filter()}.${colorQuality}.png"
  fun getCache_filter_paletteuse() = "${getCache_shortLength_colorKey_filter()}.${colorQuality}.gif"
  fun getCache_filter_paletteuse_lossy() = getCache_filter_paletteuse() + (".$lossy.gif").toEmptyStringIf { lossy == null }
}
