package com.budora.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import com.budora.R
import com.budora.widget.BudoraWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

enum class LogoShape(val key: String) {
    ROUND("round"),
    SIDE_ROUND("side_round"),
    SQUARE("square");

    companion object {
        fun fromKey(key: String?): LogoShape =
            entries.firstOrNull { it.key == key } ?: ROUND
    }
}

object AppLogoManager {
    private const val FILE_NAME = "custom_app_logo.png"
    private const val PREFS_NAME = "app_branding"
    private const val KEY_SHAPE = "logo_shape"

    private val _logoVersion = MutableStateFlow(System.currentTimeMillis())
    val logoVersion: StateFlow<Long> = _logoVersion.asStateFlow()

    fun getLogoShape(context: Context): LogoShape {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LogoShape.fromKey(prefs.getString(KEY_SHAPE, LogoShape.ROUND.key))
    }

    fun setLogoShape(context: Context, shape: LogoShape) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SHAPE, shape.key).apply()
        _logoVersion.value = System.currentTimeMillis()
        BudoraWidgetProvider.updateAllWidgets(context)
    }

    fun hasCustomLogo(context: Context): Boolean {
        val file = File(context.filesDir, FILE_NAME)
        return file.exists() && file.length() > 0
    }

    fun getRawLogoBitmap(context: Context): Bitmap {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists() && file.length() > 0) {
            try {
                val custom = BitmapFactory.decodeFile(file.absolutePath)
                if (custom != null) {
                    return custom
                }
            } catch (_: Exception) {
                // Fall through to default
            }
        }
        return BitmapFactory.decodeResource(context.resources, R.drawable.ic_headphones_preview)
    }

    /**
     * Returns the logo styled with the chosen shape (Round / Side Round / Square).
     */
    fun getStyledLogoBitmap(context: Context, shape: LogoShape = getLogoShape(context)): Bitmap {
        val raw = getRawLogoBitmap(context)
        val targetSize = 512
        val scaled = if (raw.width != targetSize || raw.height != targetSize) {
            Bitmap.createScaledBitmap(raw, targetSize, targetSize, true)
        } else {
            raw
        }

        if (shape == LogoShape.SQUARE) {
            return scaled
        }

        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val path = Path()
        when (shape) {
            LogoShape.ROUND -> {
                path.addCircle(targetSize / 2f, targetSize / 2f, targetSize / 2f, Path.Direction.CW)
            }
            LogoShape.SIDE_ROUND -> {
                val rect = RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())
                val cornerRadius = targetSize * 0.22f
                path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            LogoShape.SQUARE -> Unit
        }

        canvas.clipPath(path)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        return output
    }

    fun getLogoBitmap(context: Context): Bitmap {
        return getStyledLogoBitmap(context)
    }

    fun saveCustomLogo(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            _logoVersion.value = System.currentTimeMillis()
            BudoraWidgetProvider.updateAllWidgets(context)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun resetToDefault(context: Context): Boolean {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
            _logoVersion.value = System.currentTimeMillis()
            BudoraWidgetProvider.updateAllWidgets(context)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Safely loads and downsamples an image from a content Uri, respecting EXIF orientation.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? {
        return try {
            // Read EXIF orientation
            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            // Decode dimensions
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            var inSampleSize = 1
            val maxActual = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxActual / (inSampleSize * 2) >= maxDim) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // Apply EXIF rotation if needed
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(
                    rawBitmap,
                    0,
                    0,
                    rawBitmap.width,
                    rawBitmap.height,
                    matrix,
                    true
                )
                if (rotated != rawBitmap) {
                    rawBitmap.recycle()
                }
                rotated
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            null
        }
    }
}
