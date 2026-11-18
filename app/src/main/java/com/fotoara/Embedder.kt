package com.fotoara

import android.graphics.Bitmap
import android.graphics.Color
import java.util.Locale

/** Fotoğraf ve yazıyı aynı "anlam uzayında" vektöre çevirir. Sonra gerçek CLIP modeliyle değiştirilecek. */
interface Embedder {
    val id: String
    fun embedImage(bitmap: Bitmap): FloatArray
    fun embedText(text: String): FloatArray
}

/** 1. aşama: sadece renk arar ("kırmızı", "mavi"...). Altyapıyı denemek içindir. */
class ColorEmbedder : Embedder {
    override val id = "color1"

    private val names = listOf("kirmizi", "turuncu", "sari", "yesil", "mavi", "mor", "siyah", "beyaz", "gri")

    private fun normalize(s: String): String =
        s.lowercase(Locale("tr"))
            .replace('ı', 'i').replace('ş', 's').replace('ç', 'c')
            .replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o')

    private fun bucket(h: Float, s: Float, v: Float): Int = when {
        v < 0.2f -> 6
        s < 0.15f -> if (v > 0.8f) 7 else 8
        h < 15f || h >= 345f -> 0
        h < 45f -> 1
        h < 70f -> 2
        h < 170f -> 3
        h < 260f -> 4
        else -> 5
    }

    override fun embedImage(bitmap: Bitmap): FloatArray {
        val out = FloatArray(names.size)
        val hsv = FloatArray(3)
        var n = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            Color.colorToHSV(bitmap.getPixel(x, y), hsv)
            out[bucket(hsv[0], hsv[1], hsv[2])] += 1f
            n++
        }
        if (n > 0) for (i in out.indices) out[i] /= n
        return out
    }

    override fun embedText(text: String): FloatArray {
        val t = normalize(text)
        val out = FloatArray(names.size)
        names.forEachIndexed { i, name -> if (t.contains(name)) out[i] = 1f }
        return out
    }
}
