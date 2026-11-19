package com.example.spendtracker

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun MainActivity.saveText(name: String, mime: String, content: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            if (uri == null) {
                false
            } else {
                contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                true
            }
        } else {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content)
            }
            startActivity(Intent.createChooser(i, name))
            true
        }
    } catch (e: Exception) {
        false
    }
}

fun MainActivity.backupNow() {
    val root = JSONObject()
    root.put("version", 1)
    val txs = JSONArray()
    db.range(0L, Long.MAX_VALUE).forEach {
        val o = JSONObject()
        o.put("ts", it.ts)
        o.put("type", it.type)
        o.put("amount", it.amount)
        o.put("merchant", it.merchant)
        o.put("cat", it.cat)
        o.put("src", it.src)
        txs.put(o)
    }
    root.put("tx", txs)
    val rl = JSONArray()
    db.allRules().forEach {
        val o = JSONObject()
        o.put("m", it.first)
        o.put("c", it.second)
        rl.put(o)
    }
    root.put("rules", rl)
    val st = JSONObject()
    st.put("budget", prefs.getFloat("budget", 0f).toDouble())
    Ui.cats.forEach { st.put("cb_$it", prefs.getFloat("cb_$it", 0f).toDouble()) }
    st.put("mail_keys", prefs.getString("mail_keys", "slice") ?: "slice")
    root.put("settings", st)
    val name = "spendtracker_backup_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".json"
    toast(if (saveText(name, "application/json", root.toString())) "Backup saved in Downloads" else "Backup failed")
}

fun MainActivity.restoreFrom(uri: Uri) {
    Thread {
        var added = 0
        var total = 0
        var err: String? = null
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw Exception("cannot read file")
            val root = JSONObject(text)
            val txs = root.getJSONArray("tx")
            db.batch {
                for (i in 0 until txs.length()) {
                    val o = txs.getJSONObject(i)
                    total++
                    val ok = db.restoreTx(
                        o.getLong("ts"),
                        o.getString("type"),
                        o.getDouble("amount"),
                        o.getString("merchant"),
                        o.getString("cat"),
                        o.optString("src", "sms")
                    )
                    if (ok) added++
                }
                val rl = root.optJSONArray("rules")
                if (rl != null) {
                    for (i in 0 until rl.length()) {
                        val o = rl.getJSONObject(i)
                        db.putRule(o.getString("m"), o.getString("c"))
                    }
                }
            }
            val st = root.optJSONObject("settings")
            if (st != null) {
                val ed = prefs.edit()
                ed.putFloat("budget", st.optDouble("budget", 0.0).toFloat())
                Ui.cats.forEach { ed.putFloat("cb_$it", st.optDouble("cb_$it", 0.0).toFloat()) }
                ed.putString("mail_keys", st.optString("mail_keys", "slice"))
                ed.apply()
            }
        } catch (e: Exception) {
            err = e.message ?: "bad file"
        }
        runOnUiThread {
            val msg = if (err != null) "Failed: $err" else "Found $total records\nAdded $added new"
            AlertDialog.Builder(this)
                .setTitle(if (err != null) "Error" else "Restore done")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
            refresh()
        }
    }.start()
}
