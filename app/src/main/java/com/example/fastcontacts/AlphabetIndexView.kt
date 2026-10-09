package com.example.fastcontacts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

// Thin A-Z side index. Touch or drag to jump between sections.
class AlphabetIndexView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    private val letters: List<String> = ("ABCDEFGHIJKLMNOPQRSTUVWXYZ".map { it.toString() }) + "#"
    var onLetter: ((String) -> Unit)? = null
    var onRelease: (() -> Unit)? = null
    private var active = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat() / letters.size
        paint.textSize = min(h * 0.72f, 11f * resources.displayMetrics.scaledDensity)
        for (i in letters.indices) {
            val cy = h * i + h / 2f
            if (i == active) {
                pill.color = ContextCompat.getColor(context, R.color.app_accent)
                canvas.drawCircle(width / 2f, cy, min(width / 2f, h / 2f), pill)
                paint.color = ContextCompat.getColor(context, R.color.app_on_accent)
            } else {
                paint.color = ContextCompat.getColor(context, R.color.app_text_secondary)
            }
            canvas.drawText(letters[i], width / 2f, cy - (paint.ascent() + paint.descent()) / 2f, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val idx = (e.y / (height.toFloat() / letters.size)).toInt().coerceIn(0, letters.size - 1)
                if (idx != active) {
                    active = idx
                    invalidate()
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onLetter?.invoke(letters[idx])
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                active = -1
                invalidate()
                onRelease?.invoke()
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
