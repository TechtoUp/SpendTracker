package com.example.spendtracker

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Telephony
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun MainActivity.importPicker() {
    val labels = arrayOf("Last 3 months", "Last 6 months", "Last 1 year", "All SMS (everything)", "Skip")
    val days = intArrayOf(90, 180, 365, 0, -1)
    AlertDialog.Builder(this)
        .setTitle("Import previous spending?")
        .setItems(labels) { _, i -> if (days[i] >= 0) startImport(days[i]) }
        .show()
}

fun MainActivity.maybeFirstImport() {
    if (prefs.getBoolean("asked", false)) return
    prefs.edit().putBoolean("asked", true).apply()
    importPicker()
}

fun MainActivity.startImport(days: Int) {
    if (hasSms()) {
        importSms(days)
    } else {
        pending = days
        perm.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
    }
}

fun MainActivity.importSms(days: Int) {
    val from = if (days == 0) 0L else System.currentTimeMillis() - days * 86400000L
    val dlg = AlertDialog.Builder(this)
        .setTitle("Importing")
        .setMessage("Reading SMS, please wait...")
        .setCancelable(false)
        .show()
    Thread {
        var added = 0
        var scanned = 0
        var err: String? = null
        try {
            db.batch {
                val c = contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf("body", "date"),
                    "date>=?",
                    arrayOf(from.toString()),
                    "date ASC"
                )
                c?.use {
                    while (it.moveToNext()) {
                        scanned++
                        val body = it.getString(0) ?: continue
                        val ts = it.getLong(1)
                        val p = Parser.parse(body) ?: continue
                        if (db.add(ts, p.type, p.amount, p.merchant, p.cat, "sms")) added++
                    }
                }
            }
        } catch (e: Exception) {
            err = e.message ?: "unknown error"
        }
        runOnUiThread {
            dlg.dismiss()
            val msg = if (err != null) "Failed: $err" else "Scanned $scanned SMS\nAdded $added transactions"
            AlertDialog.Builder(this)
                .setTitle(if (err != null) "Error" else "Done")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
            refresh()
        }
    }.start()
}

fun MainActivity.pickCat(t: Tx) {
    if (t.type == "credit") return
    val opts = Ui.cats.toTypedArray()
    AlertDialog.Builder(this)
        .setTitle("Category for ${t.merchant}")
        .setItems(opts) { _, i ->
            db.setCat(t.id, t.merchant, opts[i])
            refresh()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

fun MainActivity.confirmDel(t: Tx) {
    AlertDialog.Builder(this)
        .setMessage("Delete ${t.merchant} ${money2(t.amount)}?")
        .setPositiveButton("Delete") { _, _ ->
            db.del(t.id)
            refresh()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

fun MainActivity.confirmClear() {
    AlertDialog.Builder(this)
        .setTitle("Clear all data?")
        .setMessage("All stored transactions will be deleted. SMS on phone are not touched.")
        .setPositiveButton("Clear") { _, _ ->
            db.clear()
            refresh()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

fun MainActivity.addDialog() {
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), 0)
    }
    val amt = EditText(this).apply {
        hint = "Amount"
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }
    val mer = EditText(this).apply {
        hint = "Merchant or note"
        inputType = InputType.TYPE_CLASS_TEXT
    }
    val inc = CheckBox(this).apply { text = "This is income" }
    box.addView(amt)
    box.addView(mer)
    box.addView(inc)
    AlertDialog.Builder(this)
        .setTitle("Add transaction")
        .setView(box)
        .setPositiveButton("Save") { _, _ ->
            val a = amt.text.toString().toDoubleOrNull()
            if (a == null || a <= 0) {
                toast("Enter valid amount")
                return@setPositiveButton
            }
            val m = mer.text.toString().trim().ifEmpty { "Manual entry" }
            val type = if (inc.isChecked) "credit" else "debit"
            db.insertRaw(System.currentTimeMillis(), type, a, m, Parser.category(m, type), "manual")
            try {
                Alerts.check(this)
            } catch (x: Exception) {
            }
            refresh()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

fun MainActivity.exportCsv() {
    val all = db.range(0L, Long.MAX_VALUE)
    if (all.isEmpty()) {
        toast("Nothing to export")
        return
    }
    val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    val sb = StringBuilder("date,type,amount,merchant,category,source\n")
    all.forEach {
        sb.append(f.format(Date(it.ts))).append(',')
            .append(it.type).append(',')
            .append(it.amount).append(',')
            .append('"').append(it.merchant.replace("\"", "\"\"")).append('"').append(',')
            .append(it.cat).append(',')
            .append(it.src).append('\n')
    }
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "spendtracker_export.csv")
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            if (uri == null) {
                toast("Export failed")
                return
            }
            contentResolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray()) }
            toast("Saved in Downloads")
        } else {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, sb.toString())
            }
            startActivity(Intent.createChooser(i, "Export CSV"))
        }
    } catch (e: Exception) {
        toast("Export failed")
    }
}
