package com.example.kofiles.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

object ImageLoader {
    private const val MAX_BYTES = 80L * 1024 * 1024

    /** Загружает картинку с уменьшением под экран и поворотом по EXIF. maxPx — максимальный размер стороны. */
    fun load(src: ByteSource, maxPx: Int): Bitmap {
        val size = src.size()
        if (size <= 0) throw IOException("Пустой файл")
        if (size > MAX_BYTES) throw IOException("Файл слишком большой для просмотра")

        val out = ByteArrayOutputStream(size.toInt())
        var off = 0L
        while (off < size) {
            val chunk = src.read(off, minOf(256L * 1024, size - off).toInt())
            if (chunk.isEmpty()) break
            out.write(chunk)
            off += chunk.size
        }
        val bytes = out.toByteArray()

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Формат изображения не поддерживается")

        var sample = 1
        while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Не удалось декодировать изображение")

        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
}
