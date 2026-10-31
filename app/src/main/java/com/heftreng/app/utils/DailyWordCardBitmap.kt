package com.heftreng.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Günlük kelime listesini kart tasarımında Bitmap olarak üretir.
 * Hem bildirim (BigPictureStyle) hem de paylaş işlevi kullanır.
 *
 * Layout:
 *   Header : [AppIcon] Peyvên Rojê          tarih
 *   Kelime kartları (her kelime için ayrı kart)
 *   Footer : [AppIcon]  Heft Reng Kurdî  [PlayStore]
 */
object DailyWordCardBitmap {

    data class WordItem(
        val word      : String = "",
        val meaningTr : String = "",
        val meaningKu : String = "",
        val exampleKu : String = "",
        val exampleTr : String = "",
        val emoji     : String = "",
    )

    /**
     * @param words Kelime listesi (en az 1 eleman)
     * @param notifMode Bildirim için daha dar/kısa bitmap (thumbnail uyumlu)
     */
    fun build(context: Context, words: List<WordItem>, notifMode: Boolean = false): Bitmap? {
        if (words.isEmpty()) return null
        return try {
            buildInternal(context, words, notifMode)
        } catch (e: Exception) {
            android.util.Log.e("DailyWordCardBitmap", "build error: ${e.message}")
            null
        }
    }

    private fun buildInternal(context: Context, words: List<WordItem>, notifMode: Boolean): Bitmap {
        val width   = if (notifMode) 1024 else 1080
        val pad     = if (notifMode) 32f  else 48f
        val cardPad = if (notifMode) 24f  else 36f
        val cardGap = if (notifMode) 14f  else 20f
        val cardR   = if (notifMode) 20f  else 32f

        // ── Renk paleti ──────────────────────────────────────────────
        val cardBgColors = intArrayOf(
            0xFFF0FAF0.toInt(), 0xFFFFF8F0.toInt(), 0xFFF3F0FA.toInt(),
            0xFFEFF6FF.toInt(), 0xFFFFF0F0.toInt(),
        )
        val accentColors = intArrayOf(
            0xFF2E7D32.toInt(), 0xFFE65100.toInt(), 0xFF4527A0.toInt(),
            0xFF1565C0.toInt(), 0xFFC62828.toInt(),
        )

        // ── Paint nesneleri ──────────────────────────────────────────
        val scale   = if (notifMode) 0.82f else 1f
        val headerTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = 0xFF8B5CF6.toInt()
            typeface  = Typeface.create("sans-serif", Typeface.BOLD)
            textSize  = 40f * scale
            textAlign = Paint.Align.LEFT
        }
        val dateTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = 0x99333355.toInt()
            typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize  = 32f * scale
            textAlign = Paint.Align.RIGHT
        }
        val numTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = 0xFFFFFFFF.toInt()
            typeface  = Typeface.create("sans-serif", Typeface.BOLD)
            textSize  = 38f * scale
            textAlign = Paint.Align.CENTER
        }
        val wordTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = 0xFF1A1A2E.toInt()
            typeface  = Typeface.create("sans-serif", Typeface.BOLD)
            textSize  = 54f * scale
            textAlign = Paint.Align.LEFT
        }
        val dashTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize  = 40f * scale
            textAlign = Paint.Align.LEFT
        }
        val trTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface  = Typeface.create("sans-serif", Typeface.BOLD)
            textSize  = 40f * scale
            textAlign = Paint.Align.LEFT
        }
        val meaningKuTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = (0xFF1A1A2E.toInt() and 0x00FFFFFF) or (0x99 shl 24)
            typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize  = 34f * scale
            textAlign = Paint.Align.LEFT
        }
        val exTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = (0xFF1A1A2E.toInt() and 0x00FFFFFF) or (0xCC shl 24)
            typeface  = Typeface.create("sans-serif", Typeface.ITALIC)
            textSize  = 34f * scale
            textAlign = Paint.Align.LEFT
        }
        val exTrTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = (0xFF1A1A2E.toInt() and 0x00FFFFFF) or (0x73 shl 24)
            typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize  = 30f * scale
            textAlign = Paint.Align.LEFT
        }
        val footerTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = 0x88404060.toInt()
            typeface  = Typeface.create("sans-serif", Typeface.BOLD)
            textSize  = 34f * scale
            textAlign = Paint.Align.CENTER
        }

        // ── Kart yüksekliklerini hesapla ─────────────────────────────
        val boxSize = 76f * scale
        val lineH1  = 64f * scale
        val lineH2  = 44f * scale
        val lineH3  = 44f * scale
        val lineH4  = 38f * scale

        data class RowLayout(val h: Float)
        val rowLayouts = words.map { item ->
            var h = cardPad + lineH1
            if (item.meaningKu.isNotBlank()) h += lineH2 + 6f
            if (item.exampleKu.isNotBlank()) {
                h += lineH3 + 12f
                if (item.exampleTr.isNotBlank()) h += lineH4 + 4f
            }
            h += cardPad
            RowLayout(h)
        }

        val headerH = 130f * scale
        val footerH = 110f * scale
        val totalH  = (headerH + rowLayouts.sumOf { it.h.toDouble() } +
                       cardGap * words.size + footerH + pad * 2).toInt()

        val bitmap = Bitmap.createBitmap(width, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFF8F7FC.toInt())

        // ── Uygulama ikonu yükle ──────────────────────────────────────
        val appIconBmp: Bitmap? = try {
            val d = androidx.core.content.res.ResourcesCompat.getDrawable(
                context.resources, com.heftreng.app.R.drawable.ic_app_logo, null)
            val b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
            d?.setBounds(0, 0, 80, 80); d?.draw(Canvas(b)); b
        } catch (_: Exception) { null }

        // ── Play Store ikonu yükle ───────────────────────────────────
        val psIconBmp: Bitmap? = try {
            val d = androidx.core.content.res.ResourcesCompat.getDrawable(
                context.resources, com.heftreng.app.R.drawable.ic_google_play, null)
            val s = (52f * scale).toInt()
            val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            d?.setBounds(0, 0, s, s); d?.draw(Canvas(b)); b
        } catch (_: Exception) { null }

        // ── Header ───────────────────────────────────────────────────
        var y = pad
        val iconSize = 72f * scale
        val iconTop  = y + (headerH - iconSize) / 2f
        if (appIconBmp != null) {
            val iconRect = RectF(pad, iconTop, pad + iconSize, iconTop + iconSize)
            val clip = Path().also {
                it.addRoundRect(iconRect, 18f, 18f, Path.Direction.CW)
            }
            canvas.save(); canvas.clipPath(clip)
            canvas.drawBitmap(appIconBmp, null, iconRect, Paint(Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
            val textLeft = pad + iconSize + 16f
            val fm = headerTp.fontMetrics
            val textY = iconTop + iconSize / 2f - (fm.ascent + fm.descent) / 2f
            canvas.drawText("Peyvên Rojê", textLeft, textY, headerTp)
        } else {
            canvas.drawText("Peyvên Rojê", pad, y + 80f * scale, headerTp)
        }
        val dateStr = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("tr"))
            .format(java.util.Date())
        val dfm = dateTp.fontMetrics
        val dateY = iconTop + iconSize / 2f - (dfm.ascent + dfm.descent) / 2f
        canvas.drawText(dateStr, width - pad, dateY, dateTp)
        y += headerH

        // ── Kelime kartları ──────────────────────────────────────────
        words.forEachIndexed { i, item ->
            val layout  = rowLayouts[i]
            val accent  = accentColors[i % accentColors.size]
            val cardBg  = cardBgColors[i % cardBgColors.size]
            val cardTop = y + cardGap / 2f
            val cardBot = cardTop + layout.h

            // Gölge
            canvas.drawRoundRect(
                RectF(pad + 4f, cardTop + 6f, width - pad + 4f, cardBot + 6f),
                cardR, cardR,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x18000000.toInt() }
            )
            // Kart
            canvas.drawRoundRect(
                RectF(pad, cardTop, width - pad, cardBot), cardR, cardR,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg }
            )

            // Numara kutusu
            val bx    = pad + cardPad
            val by    = cardTop + cardPad
            canvas.drawRoundRect(
                RectF(bx, by, bx + boxSize, by + boxSize), 16f, 16f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            )
            val nfm = numTp.fontMetrics
            val ncy = by + boxSize / 2f - (nfm.ascent + nfm.descent) / 2f
            canvas.drawText("${i + 1}", bx + boxSize / 2f, ncy, numTp)

            val textX = bx + boxSize + 24f
            var cy    = by

            // Kelime + — + anlam (TR)
            dashTp.color = accent; trTp.color = accent
            val wfm  = wordTp.fontMetrics
            val wy   = cy + lineH1 / 2f - (wfm.ascent + wfm.descent) / 2f
            val wordW = wordTp.measureText(item.word)
            val dashW = dashTp.measureText(" — ")
            val maxTrW = width - pad - textX - wordW - dashW - pad
            val trText = run {
                var t = item.meaningTr
                while (t.isNotEmpty() && trTp.measureText(t) > maxTrW) t = t.dropLast(1)
                if (t.length < item.meaningTr.length) "$t…" else t
            }
            canvas.drawText(item.word, textX, wy, wordTp)
            canvas.drawText(" — ", textX + wordW, wy, dashTp)
            canvas.drawText(trText, textX + wordW + dashW, wy, trTp)

            if (item.emoji.isNotBlank()) {
                val emojiTp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 52f * scale }
                canvas.drawText(item.emoji, width - pad - cardPad - 60f * scale, wy, emojiTp)
            }
            cy += lineH1

            if (item.meaningKu.isNotBlank()) {
                cy += 6f
                canvas.drawText(item.meaningKu, textX, cy + lineH2 * 0.75f, meaningKuTp)
                cy += lineH2
            }
            if (item.exampleKu.isNotBlank()) {
                cy += 12f
                val maxW  = width - pad - textX - cardPad
                val exRaw = "💬 ${item.exampleKu}"
                val exText = if (exTp.measureText(exRaw) > maxW) {
                    var t = item.exampleKu
                    while (t.isNotEmpty() && exTp.measureText("💬 $t…") > maxW) t = t.dropLast(1)
                    "💬 $t…"
                } else exRaw
                canvas.drawText(exText, textX, cy + lineH3 * 0.75f, exTp)
                cy += lineH3
                if (item.exampleTr.isNotBlank()) {
                    cy += 4f
                    val trRaw = "(${item.exampleTr})"
                    val trEx = if (exTrTp.measureText(trRaw) > maxW) {
                        var t = item.exampleTr
                        while (t.isNotEmpty() && exTrTp.measureText("($t…)") > maxW) t = t.dropLast(1)
                        "($t…)"
                    } else trRaw
                    canvas.drawText(trEx, textX, cy + lineH4 * 0.75f, exTrTp)
                }
            }

            y += layout.h + cardGap
        }

        y += cardGap / 2f + 8f

        // ── Footer ───────────────────────────────────────────────────
        val footerCy = y + footerH / 2f
        val fiSize   = 52f * scale
        val label    = "Heft Reng Kurdî"
        val labelW   = footerTp.measureText(label)
        val gap      = 16f
        val totalFW  = fiSize + gap + labelW + gap + fiSize
        val startX   = (width - totalFW) / 2f
        val ffm      = footerTp.fontMetrics
        val textCy   = footerCy - (ffm.ascent + ffm.descent) / 2f

        if (appIconBmp != null) {
            val fiRect = RectF(startX, footerCy - fiSize / 2f, startX + fiSize, footerCy + fiSize / 2f)
            val fClip = Path().also { it.addRoundRect(fiRect, 12f, 12f, Path.Direction.CW) }
            canvas.save(); canvas.clipPath(fClip)
            canvas.drawBitmap(appIconBmp, null, fiRect, Paint(Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        }
        footerTp.textAlign = Paint.Align.LEFT
        canvas.drawText(label, startX + fiSize + gap, textCy, footerTp)
        val psLeft = startX + fiSize + gap + labelW + gap
        if (psIconBmp != null) {
            val psRect = RectF(psLeft, footerCy - fiSize / 2f, psLeft + fiSize, footerCy + fiSize / 2f)
            canvas.drawBitmap(psIconBmp, null, psRect, Paint(Paint.ANTI_ALIAS_FLAG))
        } else {
            val gpTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color    = 0xFF01875F.toInt()
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textSize = 28f * scale
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText("▶ Play", psLeft, textCy, gpTp)
        }

        return bitmap
    }
}
