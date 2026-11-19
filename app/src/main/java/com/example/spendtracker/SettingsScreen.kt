package com.example.spendtracker

import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import java.util.Date

fun MainActivity.buildSettings() {
    setBox.removeAllViews()

    val b = card()
    b.addView(tv("Monthly budget", 15f, Ui.text, true))
    b.addView(tv("Shown on dashboard. Enter 0 to turn off.", 12f, Ui.sub))
    val cur = prefs.getFloat("budget", 0f)
    val et = EditText(this).apply {
        hint = "e.g. 30000"
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setText(if (cur > 0) cur.toLong().toString() else "")
        setTextColor(Ui.text)
        setHintTextColor(Ui.sub)
    }
    b.addView(et)
    b.addView(btn("Save budget") {
        prefs.edit().putFloat("budget", et.text.toString().toFloatOrNull() ?: 0f).apply()
        toast("Saved")
    }, lp(MP, WC).apply { topMargin = dp(8) })
    setBox.addView(b)

    val i = card()
    i.addView(tv("Import past spending", 15f, Ui.text, true))
    i.addView(tv("Reads bank SMS already on this phone. Safe to run again, no duplicates.", 12f, Ui.sub))
    listOf("Last 3 months" to 90, "Last 6 months" to 180, "Last 1 year" to 365, "All SMS" to 0).forEach { (l, d) ->
        i.addView(btn(l, false) { startImport(d) }, lp(MP, WC).apply { topMargin = dp(8) })
    }
    setBox.addView(i)

    val n = card()
    n.addView(tv("UPI notifications", 15f, Ui.text, true))
    n.addView(tv("Captures GPay, PhonePe, Paytm alerts live. If switch is greyed out: phone Settings > Apps > Spend Tracker > menu > Allow restricted settings.", 12f, Ui.sub))
    n.addView(btn("Open notification access", false) {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }, lp(MP, WC).apply { topMargin = dp(8) })
    setBox.addView(n)

    val e = card()
    e.addView(tv("Export", 15f, Ui.text, true))
    e.addView(tv("Saves all transactions as CSV in Downloads.", 12f, Ui.sub))
    e.addView(btn("Export CSV", false) { exportCsv() }, lp(MP, WC).apply { topMargin = dp(8) })
    setBox.addView(e)

    val total = db.count()
    val first = db.minTs()
    val a = card()
    a.addView(tv("Data", 15f, Ui.text, true))
    a.addView(kv("Transactions stored", total.toString()))
    if (total > 0 && first > 0) a.addView(kv("Oldest record", df.format(Date(first))))
    val dz = btn("Clear all data", false) { confirmClear() }
    dz.setTextColor(Ui.red)
    dz.background = Ui.rounded(Color.parseColor("#FEE2E2"), dp(10).toFloat())
    a.addView(dz, lp(MP, WC).apply { topMargin = dp(10) })
    setBox.addView(a)
}
