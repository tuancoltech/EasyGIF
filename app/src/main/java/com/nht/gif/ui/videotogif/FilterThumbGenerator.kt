package com.nht.gif.ui.videotogif

import android.graphics.Bitmap
import com.nht.gif.model.ExportColorFilter

interface FilterThumbGenerator {
    suspend fun generate(emit: suspend (ExportColorFilter, Bitmap?) -> Unit)
}
