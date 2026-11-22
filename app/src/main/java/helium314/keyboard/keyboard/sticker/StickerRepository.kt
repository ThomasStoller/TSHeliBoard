// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import helium314.keyboard.latin.utils.ChecksumCalculator
import helium314.keyboard.latin.utils.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Stores stickers as normalized PNGs in filesDir/stickers.
 *
 * Normalizing to PNG keeps commitContent compatibility high: every sticker is image/png,
 * alpha is preserved, and huge images cannot be handed to another app by accident.
 */
object StickerRepository {
    private const val TAG = "StickerRepository"
    private const val DIR_NAME = "stickers"
    private const val MAX_DIMENSION = 1024
    private const val MAX_INPUT_BYTES = 8 * 1024 * 1024

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun list(context: Context): List<Sticker> = dir(context).listFiles()
        ?.filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() }
        ?.map { Sticker(it) }
        ?: emptyList()

    fun delete(sticker: Sticker): Boolean = sticker.file.delete()

    /** Returns true if a new sticker was added, false for duplicates or unsupported input. */
    fun import(context: Context, uri: Uri): Boolean {
        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (mimeType != null && !mimeType.startsWith("image/")) return false

        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { readBounded(it) }
        }.getOrNull()
        if (bytes == null || bytes.isEmpty()) return false

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull() ?: return false

        val scaled = scaleDown(decoded)
        if (scaled !== decoded) decoded.recycle()

        val temp = File(context.cacheDir, "sticker_import.png")
        try {
            FileOutputStream(temp).use { output ->
                if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) return false
            }
        } finally {
            scaled.recycle()
        }

        val hash = ChecksumCalculator.checksum(temp) ?: run {
            temp.delete()
            return false
        }
        val target = File(dir(context), "$hash.png")
        if (target.exists()) {
            temp.delete()
            return false
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            return false
        }
        return true
    }

    private fun readBounded(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_INPUT_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width / (sample * 2), height / (sample * 2)) >= MAX_DIMENSION) {
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
