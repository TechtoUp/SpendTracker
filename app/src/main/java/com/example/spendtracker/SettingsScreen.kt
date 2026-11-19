package com.example.spendtracker

import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import java.util.Date

fun MainActivity.buildSettings() {
    setBox.removeAllViews()

    val b = card()
    b.addView(tv("Monthly budget", 15f, Ui.text, true))
    b.addView(tv("Shown on dashboard, alert at 80% and 100%. Enter 0 to turn off.", 12f, Ui.sub))
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
        askNotifPerm()
        toast("Saved")
    }, lp(MP, WC).apply { topMargin = dp(8) })
    b.addView(btn("Category budgets", false) { catBudgetDialog() }, lp(MP, WC).apply { topMargin = dp(8) })
    setBox.addView(b)

    val i = card()
    i.addView(tv("Import past spending", 15f, Ui.text, true))
    i.addView(tv("Reads bank SMS already on this phone. Safe to run again, no duplicates.", 12f, Ui.sub))
    listOf("Last 3 months" to 90, "Last 6 months" to 180, "Last 1 year" to 365, "All SMS" to 0).forEach { (l, d) ->
        i.addView(btn(l, false) { startImport(d) }, lp(MP, WC).apply { topMargin = dp(8) })
    }
    setBox.addView(i)

    val n = card()
    n.addView(tv("UPI and email capture", 15f, Ui.text, true))
    n.addView(tv("Captures GPay, PhonePe, Paytm, Slice alerts live. Also reads Gmail notifications that contain the words below. If switch is greyed out: phone Settings > Apps > Spend Tracker > menu > Allow restricted settings.", 12f, Ui.sub))
    n.addView(btn("Open notification access", false) {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }, lp(MP, WC).apply { topMargin = dp(8) })
    n.addView(tv("Email words (comma separated)", 12f, Ui.sub).apply { setPadding(0, dp(10), 0, 0) })
    val mk = EditText(this).apply {
        setText(prefs.getString("mail_keys", "slice") ?: "slice")
        setSingleLine(true)
        setTextColor(Ui.text)
        setHintTextColor(Ui.sub)
    }
    n.addView(mk)
    n.addView(btn("Save email words", false) {
        prefs.edit().putString("mail_keys", mk.text.toString().trim()).apply()
        toast("Saved")
    }, lp(MP, WC).apply { topMargin = dp(8) })
    n.addView(btn("View notification log", false) { showLog() }, lp(MP, WC).apply { topMargin = dp(8) })
    setBox.addView(n)

    val k = card()
    k.addView(tv("Categories", 15f, Ui.text, true))
    k.addView(tv("Tap any transaction to change its category. The app remembers it for that merchant and similar names. This button cleans old merchant names and applies your saved choices to everything.", 12f, Ui.sub))
    k.addView(btn("Re-categorize all", false) { recatAll() }, lp(MP, WC).apply { topMargin = dp(8) })
    setBox.addView(k)

    val l = card()
    l.addView(tv("App lock", 15f, Ui.text, true))
    l.addView(tv("Asks for fingerprint or screen lock when you open the app after 30 seconds away.", 12f, Ui.sub))
    val lockBox = CheckBox(this).apply {
        text = "Lock app"
        setTextColor(Ui.text)
        isChecked = prefs.getBoolean("lock", false)
        setOnCheckedChangeListener { v, on ->
            if (on && !canLock()) {
                v.isChecked = false
                toast("Set a fingerprint or screen lock in phone settings first")
            } else {
                prefs.edit().putBoolean("lock", on).apply()
                applySecure()
            }
        }
    }
    l.addView(lockBox)
    setBox.addView(l)

    val bk = card()
    bk.addView(tv("Backup and restore", 15f, Ui.text, true))
    bk.addView(tv("Backup saves transactions, category choices and budgets as a file in Downloads. Restore adds them back without duplicates.", 12f, Ui.sub))
    bk.addView(btn("Backup now", false) { backupNow() }, lp(MP, WC).apply { topMargin = dp(8) })
    bk.addView(btn("Restore from file", false) { restorePick.launch(arrayOf("*/*")) }, lp(MP, WC).apply { topMargin = dp(8) })
    setBox.addView(bk)

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
