package com.example.spendtracker

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun pkgLabel(p: String): String = when (p) {
    "com.google.android.gm", "com.google.android.gm.lite" -> "Gmail"
    "com.microsoft.office.outlook" -> "Outlook"
    "indwin.c3.shareapp" -> "Slice app"
    "com.google.android.apps.nbu.paisa.user" -> "GPay"
    "com.phonepe.app" -> "PhonePe"
    "net.one97.paytm" -> "Paytm"
    "in.org.npci.upiapp" -> "BHIM"
    "com.dreamplug.androidapp" -> "CRED"
    else -> p
}

fun MainActivity.askNotifPerm() {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

fun MainActivity.showLog() {
    val rows = db.logs()
    val f = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), dp(8))
    }
    if (rows.isEmpty()) {
        box.addView(TextView(this).apply {
            text = "Nothing captured yet. Make a UPI payment or wait for a Slice email, then open this again.\n\nIf it stays empty: check notification access is on, battery is Unrestricted, and Gmail notifications are on."
            textSize = 13f
        })
    }
    rows.forEach { r ->
        box.addView(TextView(this).apply {
            text = f.format(Date(r.ts)) + " · " + pkgLabel(r.pkg) + "\n" + r.res + "\n" + r.body
            textSize = 12f
            setPadding(0, dp(8), 0, dp(8))
        })
    }
    val sv = ScrollView(this)
    sv.addView(box)
    AlertDialog.Builder(this)
        .setTitle("Notification log")
        .setView(sv)
        .setPositiveButton("Close", null)
        .setNegativeButton("Clear") { _, _ -> db.clearLogs() }
        .show()
}

fun MainActivity.catBudgetDialog() {
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), 0)
    }
    val fields = LinkedHashMap<String, EditText>()
    Ui.cats.forEach { c ->
        val cur = prefs.getFloat("cb_$c", 0f)
        val et = EditText(this).apply {
            hint = "No limit"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (cur > 0) cur.toLong().toString() else "")
        }
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        r.addView(TextView(this).apply { text = c }, lp(dp(100), WC))
        r.addView(et, lp(0, WC, 1f))
        box.addView(r)
        fields[c] = et
    }
    val sv = ScrollView(this)
    sv.addView(box)
    AlertDialog.Builder(this)
        .setTitle("Category budgets per month")
        .setView(sv)
        .setPositiveButton("Save") { _, _ ->
            val ed = prefs.edit()
            fields.forEach { (c, et) -> ed.putFloat("cb_$c", et.text.toString().toFloatOrNull() ?: 0f) }
            ed.apply()
            askNotifPerm()
            toast("Saved")
            refresh()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

fun MainActivity.recatAll() {
    Thread {
        var n = 0
        var err: String? = null
        try {
            n = db.recategorize()
        } catch (e: Exception) {
            err = e.message ?: "error"
        }
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(if (err != null) "Error" else "Done")
                .setMessage(if (err != null) "Failed: $err" else "Updated $n transactions")
                .setPositiveButton("OK", null)
                .show()
            refresh()
        }
    }.start()
}
