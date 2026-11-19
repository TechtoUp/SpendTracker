package com.example.spendtracker

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.text.NumberFormat
import java.util.Locale

const val MP = ViewGroup.LayoutParams.MATCH_PARENT
const val WC = ViewGroup.LayoutParams.WRAP_CONTENT

private val nf0 = NumberFormat.getInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
private val nf2 = NumberFormat.getInstance(Locale("en", "IN")).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

fun money(v: Double): String = "₹" + nf0.format(v)

fun money2(v: Double): String = "₹" + nf2.format(v)

fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

fun Context.dp(v: Int): Int = Ui.dp(this, v)

fun Context.tv(s: String, size: Float = 14f, color: Int = Ui.text, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = Ui.rounded(Ui.card, dp(14).toFloat())
    setPadding(dp(16), dp(14), dp(16), dp(14))
    layoutParams = lp(MP, WC).apply { setMargins(dp(12), dp(6), dp(12), dp(6)) }
}

fun Context.btn(s: String, filled: Boolean = true, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = s
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(if (filled) Color.WHITE else Ui.primary)
        setTypeface(typeface, Typeface.BOLD)
        background = Ui.rounded(if (filled) Ui.primary else Ui.soft, dp(10).toFloat())
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { onClick() }
    }

fun Context.kv(k: String, v: String, vc: Int = Ui.text): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    setPadding(0, dp(5), 0, dp(5))
    addView(tv(k, 14f, Ui.sub), lp(0, WC, 1f))
    addView(tv(v, 14f, vc, true))
}

fun Context.stat(k: String, v: String, c: Int): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    addView(tv(k, 12f, Ui.sub))
    addView(tv(v, 17f, c, true))
}

fun Context.progress(p: Double, col: Int): LinearLayout {
    val f = p.coerceIn(0.0, 1.0).toFloat()
    val ctx = this
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        background = Ui.rounded(Ui.line, dp(5).toFloat())
        val bar = View(ctx)
        bar.background = Ui.rounded(col, dp(5).toFloat())
        addView(bar, lp(0, MP, f))
        addView(View(ctx), lp(0, MP, 1f - f))
    }
}
