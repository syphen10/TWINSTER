package com.twinster.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.FileProvider
import com.twinster.app.BuildConfig
import java.io.File
import java.io.FileOutputStream

object ShareCard {

    fun saveBitmapToCache(context: Context, imageBitmap: ImageBitmap): Uri {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "twinster_card_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            imageBitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun isInstagramInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo("com.instagram.android", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun shareToInstagramStory(context: Context, imageUri: Uri): Boolean {
        if (!isInstagramInstalled(context)) return false
        return try {
            val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
                setDataAndType(imageUri, "image/png")
                putExtra("source_application", SecretObfuscator.decode(BuildConfig.FACEBOOK_APP_ID_OBF))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.grantUriPermission("com.instagram.android", imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun shareGeneric(context: Context, imageUri: Uri, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share your Twinster card"))
    }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Twinster summary", text))
    }
}
