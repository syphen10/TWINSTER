package com.twinster.app.util

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun captureWindowRegion(activity: Activity, bounds: Rect): ImageBitmap? {
    if (bounds.width() <= 0 || bounds.height() <= 0) return null
    val raw = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
    val success = suspendCancellableCoroutine<Boolean> { cont ->
        PixelCopy.request(
            activity.window,
            bounds,
            raw,
            { result -> if (cont.isActive) cont.resume(result == PixelCopy.SUCCESS) },
            Handler(Looper.getMainLooper())
        )
    }
    if (!success) return null
    return composeOntoShareCanvas(raw).asImageBitmap()
}

private fun composeOntoShareCanvas(source: Bitmap): Bitmap {
    val target = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(target)
    canvas.drawColor(Color.parseColor("#0A0A0F"))
    val scale = minOf(1080f / source.width, 1920f / source.height)
    val scaledW = source.width * scale
    val scaledH = source.height * scale
    val left = (1080f - scaledW) / 2f
    val top = (1920f - scaledH) / 2f
    val destRect = android.graphics.RectF(left, top, left + scaledW, top + scaledH)
    canvas.drawBitmap(source, null, destRect, null)
    return target
}
