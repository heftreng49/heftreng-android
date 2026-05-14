package com.heftreng.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val fullUrl = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        else -> "https://$url"
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {}
}
