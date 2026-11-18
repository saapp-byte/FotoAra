package com.fotoara

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore

object Indexer {
    fun photoUri(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    private fun decode(ctx: Context, uri: Uri, target: Int = 64): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var s = 1
        while (bounds.outWidth / (s * 2) >= target && bounds.outHeight / (s * 2) >= target) s *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = s }
        return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Yeni fotoğrafları bulup vektörlerini çıkarır. onProgress(biten, toplam) */
    suspend fun run(
        ctx: Context,
        store: EmbeddingStore,
        embedder: Embedder,
        onProgress: suspend (Int, Int) -> Unit
    ) {
        val ids = ArrayList<Long>()
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val col = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext()) ids.add(c.getLong(col))
        }
        val todo = ids.filter { !store.has(it) }
        todo.forEachIndexed { i, id ->
            val bmp = runCatching { decode(ctx, photoUri(id)) }.getOrNull()
            if (bmp != null) {
                store.put(id, embedder.embedImage(bmp))
                bmp.recycle()
            }
            if ((i + 1) % 10 == 0 || i == todo.lastIndex) {
                store.save()
                onProgress(i + 1, todo.size)
            }
        }
    }
}
