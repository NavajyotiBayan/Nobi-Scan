package com.nobi.scan.cv

import android.content.Context
import android.graphics.Bitmap

internal fun detectStillDocument(context: Context, bitmap: Bitmap) =
    DocumentDetector().detectFromBitmap(bitmap)
