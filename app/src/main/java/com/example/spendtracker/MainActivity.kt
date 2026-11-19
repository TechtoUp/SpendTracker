package com.example.spendtracker

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var db: Db
    private lateinit var prefs: SharedPreferences
    private val cal = Calendar.getInstance()
    private var allTime = false
    private var tab = 0
    private var filter = "all"
    private var query = ""
    private var pending = -1
    private var monthTx = listOf<Tx>()
    private var items = listOf<Tx>()
    private val chipViews = HashMap<String, TextView>()

    private val nf = NumberFormat.getInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
    private val nf2 = NumberFormat.getInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    private val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val mf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val ms = SimpleDateFormat("MMM", Locale.getDefault())

    private lateinit var monthBar: LinearLayout
    private lateinit var monthLabel: TextView
    private lateinit var prevBtn: TextView
    private lateinit var nextBtn: TextView
    private lateinit var allBtn: TextView
    private lateinit var dash: LinearLayout
    private lateinit var dashScroll: ScrollView
    private lateinit var txnPane: LinearLayout
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var setBox: LinearLayout
    private lateinit var setScroll: ScrollView
    private lateinit var navItems: List<TextView>

    private val perm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) {
            if (pending >= 0) {
                val d = pending
                pending = -1
                importSms(d)
            } else {
                maybeFirstImport()
            }
        } else {
            pending = -1
            toast("SMS permission denied")
        }
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(p: Int): Any = items[p]
        override fun getItemId(p: Int): Long = items[p].id
        override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
            val a = this@MainActivity
            val t = items[p]
            val row = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Ui.rounded(Color.WHITE, dp(12).toFloat())
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val dot = TextView(a).apply {
                text = t.merchant.take(1).uppercase()
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                val g = GradientDrawable()
                g.shape = GradientDrawable.OVAL
                g.setColor(Ui.catColor(t.cat))
                background = g
            }
            row.addView(dot, lp(dp(40), dp(40)))
            val mid = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
            }
            mid.addView(tv(t.merchant, 15f, Ui.text, true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            val extra = if (t.src == "manual") " · manual" else ""
            mid.addView(tv("${df.format(Date(t.ts))} · ${t.cat}$extra", 12f, Ui.sub))
            row.addView(mid, lp(0, WC, 1f))
            val credit = t.type == "credit"
            val sign = if (credit) "+" else "-"
            row.addView(tv("$sign₹${nf2.format(t.amount)}", 15f, if (credit) Ui.green else Ui.text, true))
            val wrap = FrameLayout(a).apply { setPadding(0, 0, 0, dp(6)) }
            wrap.addView(row, FrameLayout.LayoutParams(MP, WC))
            return wrap
        }
    }

    private fun dp(v: Int): Int = Ui.dp(this, v)

    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun money(v: Double): String = "₹" + nf.format(v)

    private fun tv(s: String, size: Float = 14f, color: Int = Ui.text, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(Ui.card, dp(14).toFloat())
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = lp(MP, WC).apply { setMargins(dp(12), dp(6), dp(12), dp(6)) }
    }

    private fun btn(s: String, filled: Boolean = true, onClick: () -> Unit): TextView =
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

    private fun kv(k: String, v: String, vc: Int = Ui.text): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(5), 0, dp(5))
        addView(tv(k, 14f, Ui.sub), lp(0, WC, 1f))
        addView(tv(v, 14f, vc, true))
    }

    private fun stat(k: String, v: String, c: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(tv(k, 12f, Ui.sub))
        addView(tv(v, 17f, c, true))
    }

    private fun progress(p: Double, col: Int): LinearLayout {
        val f = p.coerceIn(0.0, 1.0).toFloat()
        val a = this
        return LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(Ui.line, dp(5).toFloat())
            val bar = View(a)
            bar.background = Ui.rounded(col, dp(5).toFloat())
            addView(bar, lp(0, MP, f))
            addView(View(a), lp(0, MP, 1f - f))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        db = Db(this)
        prefs = getSharedPreferences("st", MODE_PRIVATE)
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg)
            fitsSystemWindows = true
        }

        monthBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(12), dp(4))
        }
        prevBtn = tv("‹", 28f, Ui.primary, true).apply {
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener {
                cal.add(Calendar.MONTH, -1)
                refresh()
            }
        }
        monthLabel = tv("", 18f, Ui.text, true).apply { gravity = Gravity.CENTER }
        nextBtn = tv("›", 28f, Ui.primary, true).apply {
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener {
                cal.add(Calendar.MONTH, 1)
                refresh()
            }
        }
        allBtn = tv("All time", 13f, Ui.primary, true).apply {
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = Ui.rounded(Ui.soft, dp(14).toFloat())
            setOnClickListener {
                allTime = !allTime
                if (allTime) {
                    cal.timeInMillis = System.currentTimeMillis()
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                }
                refresh()
            }
        }
        monthBar.addView(prevBtn)
        monthBar.addView(monthLabel, lp(0, WC, 1f))
        monthBar.addView(nextBtn)
        monthBar.addView(allBtn)

        dash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        dashScroll = ScrollView(this)
        dashScroll.addView(dash)

        txnPane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val search = EditText(this).apply {
            hint = "Search merchant or category"
            setSingleLine(true)
            textSize = 14f
            setTextColor(Ui.text)
            setHintTextColor(Ui.sub)
            background = Ui.rounded(Color.WHITE, dp(10).toFloat())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            doAfterTextChanged {
                query = it?.toString().orEmpty().trim()
                refreshList()
            }
        }
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        listOf("all" to "All", "debit" to "Spent", "credit" to "Income").forEach { (k, l) ->
            val v = tv(l, 13f, Ui.primary, true).apply {
                setPadding(dp(14), dp(7), dp(14), dp(7))
                setOnClickListener {
                    filter = k
                    refreshList()
                }
            }
            chipViews[k] = v
            chips.addView(v, lp(WC, WC).apply { rightMargin = dp(8) })
        }
        chips.addView(View(this), lp(0, 1, 1f))
        chips.addView(btn("+ Add") { addDialog() })

        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setPadding(dp(12), 0, dp(12), dp(8))
            clipToPadding = false
            selector = ColorDrawable(Color.TRANSPARENT)
            setOnItemClickListener { _, _, pos, _ -> pickCat(items[pos]) }
            setOnItemLongClickListener { _, _, pos, _ ->
                confirmDel(items[pos])
                true
            }
        }
        listView.adapter = adapter
        emptyView = tv("No transactions here", 14f, Ui.sub).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(40), 0, 0)
        }
        txnPane.addView(search, lp(MP, WC).apply { setMargins(dp(12), dp(4), dp(12), 0) })
        txnPane.addView(chips)
        txnPane.addView(listView, lp(MP, 0, 1f))
        txnPane.addView(emptyView, lp(MP, WC))

        setBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        setScroll = ScrollView(this)
        setScroll.addView(setBox)

        val frame = FrameLayout(this)
        frame.addView(dashScroll, FrameLayout.LayoutParams(MP, MP))
        frame.addView(txnPane, FrameLayout.LayoutParams(MP, MP))
        frame.addView(setScroll, FrameLayout.LayoutParams(MP, MP))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }
        navItems = listOf("📊 Dashboard", "🧾 Transactions", "⚙ Settings").mapIndexed { i, s ->
            tv(s, 13f, Ui.sub, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(14), 0, dp(14))
                setOnClickListener {
                    tab = i
                    refresh()
                }
            }
        }
        navItems.forEach { nav.addView(it, lp(0, WC, 1f)) }

        root.addView(monthBar)
        root.addView(frame, lp(MP, 0, 1f))
        root.addView(nav)
        setContentView(root)

        if (hasSms()) maybeFirstImport() else perm.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun hasSms() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

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

    private fun refresh() {
        monthLabel.text = if (allTime) "All time" else mf.format(cal.time)
        allBtn.text = if (allTime) "Month" else "All time"
        val arrows = if (allTime) View.INVISIBLE else View.VISIBLE
        prevBtn.visibility = arrows
        nextBtn.visibility = arrows
        monthBar.visibility = if (tab == 2) View.GONE else View.VISIBLE
        dashScroll.visibility = if (tab == 0) View.VISIBLE else View.GONE
        txnPane.visibility = if (tab == 1) View.VISIBLE else View.GONE
        setScroll.visibility = if (tab == 2) View.VISIBLE else View.GONE
        navItems.forEachIndexed { i, v -> v.setTextColor(if (i == tab) Ui.primary else Ui.sub) }
        val from = if (allTime) 0L else monthStart(0)
        val to = if (allTime) Long.MAX_VALUE else monthStart(1)
        monthTx = db.range(from, to)
        when (tab) {
            0 -> buildDash()
            1 -> refreshList()
            else -> buildSettings()
        }
    }

    private fun refreshList() {
        for ((k, v) in chipViews) {
            val on = k == filter
            v.setTextColor(if (on) Color.WHITE else Ui.primary)
            v.background = Ui.rounded(if (on) Ui.primary else Ui.soft, dp(16).toFloat())
        }
        val q = query.lowercase()
        items = monthTx.filter {
            (filter == "all" || it.type == filter) &&
                (q.isEmpty() || it.merchant.lowercase().contains(q) || it.cat.lowercase().contains(q))
        }
        adapter.notifyDataSetChanged()
        listView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildDash() {
        dash.removeAllViews()
        if (db.count() == 0) {
            val c = card()
            c.addView(tv("No transactions yet", 16f, Ui.text, true))
            c.addView(tv("Import your old bank SMS to see past spending. New SMS and UPI notifications are added automatically.", 13f, Ui.sub).apply {
                setPadding(0, dp(6), 0, dp(10))
            })
            c.addView(btn("Import SMS history") { importPicker() })
            dash.addView(c)
            return
        }

        val spendTx = monthTx.filter { it.type == "debit" }
        val inc = monthTx.filter { it.type == "credit" }.sumOf { it.amount }
        val spent = spendTx.sumOf { it.amount }
        val net = inc - spent

        val hero = card()
        hero.addView(tv(if (allTime) "Total spent" else "Spent this month", 13f, Ui.sub))
        hero.addView(tv(money(spent), 32f, Ui.red, true))
        if (!allTime) {
            val prev = db.spent(monthStart(-1), monthStart(0))
            if (prev > 0) {
                val dlt = (spent - prev) / prev * 100
                val arrow = if (dlt >= 0) "▲ " else "▼ "
                hero.addView(tv("$arrow${Math.abs(dlt).toInt()}% vs last month (${money(prev)})", 12f, if (spent > prev) Ui.red else Ui.green))
            } else {
                hero.addView(tv("No data for last month", 12f, Ui.sub))
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        row.addView(stat("Income", money(inc), Ui.green), lp(0, WC, 1f))
        row.addView(stat("Net", (if (net < 0) "-" else "") + money(Math.abs(net)), if (net < 0) Ui.red else Ui.green), lp(0, WC, 1f))
        row.addView(stat("Transactions", monthTx.size.toString(), Ui.text), lp(0, WC, 1f))
        hero.addView(row)
        dash.addView(hero)

        val budget = prefs.getFloat("budget", 0f).toDouble()
        if (!allTime && budget > 0) {
            val c = card()
            val pct = spent / budget
            c.addView(tv("Monthly budget", 13f, Ui.sub))
            c.addView(tv("${money(spent)} of ${money(budget)}  (${(pct * 100).toInt()}%)", 16f, if (pct > 1) Ui.red else Ui.text, true))
            val col = if (pct > 1) Ui.red else if (pct > 0.8) Color.parseColor("#F59E0B") else Ui.primary
            c.addView(progress(pct, col), lp(MP, dp(10)).apply { topMargin = dp(8) })
            val left = budget - spent
            c.addView(tv(if (left >= 0) "${money(left)} left" else "Over by ${money(-left)}", 12f, if (left >= 0) Ui.green else Ui.red).apply {
                setPadding(0, dp(6), 0, 0)
            })
            dash.addView(c)
        }

        val byCat = spendTx.groupBy { it.cat }
            .map { e -> Pair(e.key, e.value.sumOf { it.amount }) }
            .sortedByDescending { it.second }
        val cc = card()
        cc.addView(tv("Spending by category", 15f, Ui.text, true))
        if (byCat.isEmpty()) {
            cc.addView(tv("No spending in this period", 13f, Ui.sub).apply { setPadding(0, dp(12), 0, dp(4)) })
        } else {
            val donut = DonutView(this)
            donut.set(
                byCat.map { it.second }.toDoubleArray(),
                byCat.map { Ui.catColor(it.first) }.toIntArray(),
                money(spent),
                "spent"
            )
            cc.addView(donut, lp(MP, dp(190)).apply { topMargin = dp(8) })
            byCat.forEach { (name, v) ->
                val share = if (spent > 0) v / spent else 0.0
                val r = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(8), 0, dp(3))
                }
                val dotView = View(this)
                val g = GradientDrawable()
                g.shape = GradientDrawable.OVAL
                g.setColor(Ui.catColor(name))
                dotView.background = g
                r.addView(dotView, lp(dp(10), dp(10)).apply { rightMargin = dp(8) })
                r.addView(tv(name, 14f), lp(0, WC, 1f))
                r.addView(tv("${(share * 100).toInt()}%   ", 13f, Ui.sub))
  
