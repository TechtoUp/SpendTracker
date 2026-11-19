package com.example.spendtracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View

object Ui {
    val primary = Color.parseColor("#0F766E")
    val bg = Color.parseColor("#F3F5F7")
    val card = Color.WHITE
    val text = Color.parseColor("#111827")
    val sub = Color.parseColor("#6B7280")
    val red = Color.parseColor("#DC2626")
    val green = Color.parseColor("#15803D")
    val line = Color.parseColor("#E5E7EB")
    val soft = Color.parseColor("#E6F2F1")
    val cats = listOf("Food", "Groceries", "Travel", "Shopping", "Bills", "Fun", "Health", "Cash", "Other")

    fun catColor(c: String): Int = when (c) {
        "Food" -> Color.parseColor("#F59E0B")
        "Groceries" -> Color.parseColor("#84CC16")
        "Travel" -> Color.parseColor("#3B82F6")
        "Shopping" -> Color.parseColor("#8B5CF6")
        "Bills" -> Color.parseColor("#EF4444")
        "Fun" -> Color.parseColor("#EC4899")
        "Health" -> Color.parseColor("#10B981")
        "Cash" -> Color.parseColor("#14B8A6")
        "Income" -> Color.parseColor("#15803D")
        else -> Color.parseColor("#6B7280")
    }

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun rounded(color: Int, radius: Float): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = radius
        return g
    }
}

class BarChartView(ctx: Context) : View(ctx) {
    private var vals = DoubleArray(0)
    private var labels: List<String> = emptyList()
    private var barColor = Ui.primary
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * d
        color = Ui.sub
        textAlign = Paint.Align.CENTER
    }

    fun set(v: DoubleArray, l: List<String>, c: Int) {
        vals = v
        labels = l
        barColor = c
        invalidate()
    }

    private fun short(v: Double): String = when {
        v >= 100000 -> String.format("%.1fL", v / 100000)
        v >= 1000 -> String.format("%.1fk", v / 1000)
        else -> v.toInt().toString()
    }

    override fun onDraw(canvas: Canvas) {
        if (vals.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val bottom = h - 18f * d
        val top = if (vals.size <= 12) 16f * d else 4f * d
        val mx = (vals.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        val slot = w / vals.size
        val bw = slot * 0.6f
        for (i in vals.indices) {
            val has = vals[i] > 0.0
            val bh = if (has) ((vals[i] / mx) * (bottom - top)).toFloat() else 2f * d
            val l = i * slot + (slot - bw) / 2f
            p.color = if (has) barColor else Ui.line
            canvas.drawRoundRect(l, bottom - bh, l + bw, bottom, 4f * d, 4f * d, p)
            if (vals.size <= 12 && has) {
                canvas.drawText(short(vals[i]), i * slot + slot / 2f, bottom - bh - 4f * d, tp)
            }
            val showLabel = vals.size <= 12 || i == 0 || (i + 1) % 5 == 0
            if (i < labels.size && showLabel) {
                canvas.drawText(labels[i], i * slot + slot / 2f, h - 3f * d, tp)
            }
        }
    }
}

class DonutView(ctx: Context) : View(ctx) {
    private var vals = DoubleArray(0)
    private var cols = IntArray(0)
    private var big = ""
    private var small = ""
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val t1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f * d
        color = Ui.text
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val t2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * d
        color = Ui.sub
        textAlign = Paint.Align.CENTER
    }

    fun set(v: DoubleArray, c: IntArray, b: String, s: String) {
        vals = v
        cols = c
        big = b
        small = s
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val size = minOf(w, h)
        val stroke = 24f * d
        p.strokeWidth = stroke
        val left = (w - size) / 2f + stroke / 2f
        val top = (h - size) / 2f + stroke / 2f
        val r = RectF(left, top, left + size - stroke, top + size - stroke)
        val total = vals.sum()
        if (total <= 0.0) {
            p.color = Ui.line
            canvas.drawArc(r, 0f, 360f, false, p)
        } else {
            var s = -90f
            for (i in vals.indices) {
                val sw = (vals[i] / total * 360.0).toFloat()
                p.color = cols[i]
                canvas.drawArc(r, s, maxOf(sw - 1.5f, 0.5f), false, p)
                s += sw
            }
        }
        canvas.drawText(big, w / 2f, h / 2f + 4f * d, t1)
        canvas.drawText(small, w / 2f, h / 2f + 22f * d, t2)
    }
}
