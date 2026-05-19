package com.nht.gif.model

/**
 * Semantic quality-level token shared by GIF clarity and WebP quality controls.
 *
 * Exposed by [VideoToGifExportOptionsViewModel] as a [StateFlow] so the Fragment can map it
 * to a localised string without the ViewModel depending on Android resources.
 */
enum class QualityTier { LOW, MID, HIGH, MAX, BEST }
