package com.example.spendtracker

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: Db
    private lateinit var sum: TextView
    private lateinit var label: TextView
    private lateinit var list: ListView
    private val cal = Calendar.getInstance()
    private var items = listOf<Tx>()
    private val nf = NumberFormat.getInstance(Locale("en", "IN"))
    private val df = SimpleDateFormat("dd MMM", Locale.getDefault())
    private val mf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    private val perm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { g -> g }) importSms() else Toast.makeText(this, "SMS permission denied", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Db(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            fitsSystemWindows = true
        }

        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val prev = Button(this).apply {
            text = "<"
            setOnClickListener { cal.add(Calendar.MONTH, -1); refresh() }
        }
        label = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val next = Button(this).apply {
            text = ">"
            setOnClickListener { cal.add(Calendar.MONTH, 1); refresh() }
        }
        nav.addView(prev)
        nav.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(next)

        sum = TextView(this).apply {
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }

        list = ListView(this)
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val t = items[pos]
            AlertDialog.Builder(this)
                .setMessage("Delete ${t.merchant} ${nf.format(t.amount)}?")
                .setPositiveButton("Delete") { _, _ -> db.del(t.id); refresh() }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        val bar = LinearLayout(this)
        val b1 = Button(this).apply {
            text = "Notif access"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        val b2 = Button(this).apply {
            text = "Import SMS"
            setOnClickListener { askSms() }
        }
        bar.addView(b1, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(b2, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(nav)
        root.addView(sum)
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bar)
        setContentView(root)

        if (!hasSms()) perm.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun hasSms() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    private fun askSms() {
        if (hasSms()) importSms() else perm.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
    }

    private fun monthStart(offset: Int): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.MONTH, offset)
        return c.timeInMillis
    }

    private fun importSms() {
        val from = monthStart(0)
        val to = monthStart(1)
        Thread {
            var n = 0
            val c = contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf("body", "date"),
                "date>=? AND date<?",
                arrayOf(from.toString(), to.toString()),
                "date ASC"
            )
            c?.use {
                while (it.moveToNext()) {
                    val body = it.getString(0) ?: continue
                    val ts = it.getLong(1)
                    val p = Parser.parse(body) ?: continue
                    if (db.add(ts, p.type, p.amount, p.merchant, p.cat, "sms")) n++
                }
            }
            runOnUiThread {
                Toast.makeText(this, "Imported $n", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }.start()
    }

    private fun refresh() {
        label.text = mf.format(cal.time)
        items = db.range(monthStart(0), monthStart(1))
        var inc = 0.0
        var ex = 0.0
        items.forEach { if (it.type == "credit") inc += it.amount else ex += it.amount }
        sum.text = "Income ${nf.format(inc)}   Spent ${nf.format(ex)}   Balance ${nf.format(inc - ex)}"
        val rows = items.map {
            val s = if (it.type == "credit") "+" else "-"
            "${df.format(it.ts)}   $s${nf.format(it.amount)}\n${it.merchant} · ${it.cat}"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
    }
}
