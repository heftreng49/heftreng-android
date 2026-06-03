package com.heftreng.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

enum class ShareTarget { ANY, WHATSAPP, INSTAGRAM }

fun shareBitmap(context: Context, bitmap: Bitmap, target: ShareTarget = ShareTarget.ANY) {
    val file = File(context.cacheDir, "heftreng_post_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        when (target) {
            ShareTarget.WHATSAPP  -> setPackage("com.whatsapp")
            ShareTarget.INSTAGRAM -> setPackage("com.instagram.android")
            ShareTarget.ANY       -> {}
        }
    }
    try {
        context.startActivity(
            if (target == ShareTarget.ANY)
                Intent.createChooser(intent, "Paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            else intent
        )
    } catch (_: Exception) {
        // Uygulama yüklü değilse sistem seçiciye düş
        context.startActivity(
            Intent.createChooser(intent, "Paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
