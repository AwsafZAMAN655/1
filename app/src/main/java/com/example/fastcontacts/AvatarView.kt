package com.example.fastcontacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.util.AttributeSet
import android.util.LruCache
import android.view.View
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object Avatar {
    private val colors = intArrayOf(
        0xFF1A73E8.toInt(), 0xFFD93025.toInt(), 0xFF188038.toInt(), 0xFFE37400.toInt(),
        0xFF9334E6.toInt(), 0xFF129EAF.toInt(), 0xFFC2185B.toInt(), 0xFF5F6368.toInt()
    )

    fun letter(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"

    fun colorFor(name: String): Int = colors[(name.hashCode() and 0x7fffffff) % colors.size]
}

object Photos {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(ctx: Context, uri: String?): Bitmap? {
        if (uri.isNullOrEmpty()) return null
        cache.get(uri)?.let { return it }
        return try {
            ctx.contentResolver.openInputStream(Uri.parse(uri))?.use {
                val b = BitmapFactory.decodeStream(it)
                if (b != null) cache.put(uri, b)
                b
            }
        } catch (e: Exception) {
            null
        }
    }

    fun decodeSquare(ctx: Context, uri: Uri, size: Int): Bitmap? {
        return try {
            val cr = ctx.contentResolver
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / (s * 2) >= size && o.outHeight / (s * 2) >= size) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
            val side = min(bmp.width, bmp.height)
            val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            Bitmap.createScaledBitmap(sq, size, size, true)
        } catch (e: Exception) {
            null
        }
    }

    fun toJpeg(b: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }
}

class AvatarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var letter = "?"
    private var bitmap: Bitmap? = null

    fun bind(c: Contact, full: Boolean = false) {
        bindPlain(c.name, Photos.load(context, if (full) (c.photoFull ?: c.photoThumb) else c.photoThumb))
    }

    fun bindPlain(name: String, bmp: Bitmap?) {
        letter = Avatar.letter(name)
        bg.color = Avatar.colorFor(name)
        bitmap = bmp
        contentDescription = name
        rebuild()
        invalidate()
    }

    fun setPhotoBitmap(bmp: Bitmap?) {
        bitmap = bmp
        rebuild()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        val bmp = bitmap
        if (bmp == null || width == 0 || height == 0) {
            photoPaint.shader = null
            return
        }
        val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = max(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val m = Matrix()
        m.setScale(scale, scale)
        m.postTranslate((width - bmp.width * scale) / 2f, (height - bmp.height * scale) / 2f)
        shader.setLocalMatrix(m)
        photoPaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        if (bitmap != null && photoPaint.shader != null) {
            canvas.drawCircle(cx, cy, r, photoPaint)
        } else {
            canvas.drawCircle(cx, cy, r, bg)
            txt.textSize = r * 0.9f
            val y = cy - (txt.descent() + txt.ascent()) / 2f
            canvas.drawText(letter, cx, y, txt)
        }
    }
}
