package com.example.spendtracker

import android.Manifest
import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    internal lateinit var db: Db
    internal lateinit var prefs: SharedPreferences
    internal val cal: Calendar = Calendar.getInstance()
    internal var allTime = false
    internal var tab = 0
    internal var pending = -1
    internal var monthTx = listOf<Tx>()
    internal val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    internal val ms = SimpleDateFormat("MMM", Locale.getDefault())
    internal lateinit var dash: LinearLayout
    internal lateinit var setBox: LinearLayout

    private val mf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private var filter = "all"
    private var query = ""
    private var catFilter = "All"
    private var sortMode = 0
    private var lastActive = 0L
    private var prompting = false
    private lateinit var catChip: TextView
    private lateinit var sortChip: TextView
    internal lateinit var lockView: FrameLayout
    private var items = listOf<Tx>()
    private val chipViews = HashMap<String, TextView>()
    private lateinit var monthBar: LinearLayout
    private lateinit var monthLabel: TextView
    private lateinit var prevBtn: TextView
    private lateinit var nextBtn: TextView
    private lateinit var allBtn: TextView
    private lateinit var dashScroll: ScrollView
    private lateinit var txnPane: LinearLayout
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var setScroll: ScrollView
    private lateinit var navItems: List<TextView>

    internal val perm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
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

    internal val restorePick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreFrom(uri)
    }

    internal val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
            row.addView(tv(sign + money2(t.amount), 15f, if (credit) Ui.green else Ui.text, true))
            val wrap = FrameLayout(a).apply { setPadding(0, 0, 0, dp(6)) }
            wrap.addView(row, FrameLayout.LayoutParams(MP, WC))
            return wrap
        }
    }

    internal fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    internal fun hasSms() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    internal fun monthStart(offset: Int): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.MONTH, offset)
        return c.timeInMillis
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
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), dp(8))
        }
        catChip = tv("Category: All ▾", 13f, Ui.primary, true).apply {
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = Ui.rounded(Ui.soft, dp(16).toFloat())
            setOnClickListener { pickCatFilter() }
        }
        sortChip = tv("Sort: Newest ▾", 13f, Ui.primary, true).apply {
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = Ui.rounded(Ui.soft, dp(16).toFloat())
            setOnClickListener {
                sortMode = (sortMode + 1) % 3
                refreshList()
            }
        }
        row2.addView(catChip, lp(WC, WC).apply { rightMargin = dp(8) })
        row2.addView(sortChip, lp(WC, WC))
        txnPane.addView(row2)
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
        val outer = FrameLayout(this)
        outer.addView(root, FrameLayout.LayoutParams(MP, MP))
        lockView = FrameLayout(this).apply {
            setBackgroundColor(Ui.bg)
            visibility = View.GONE
            isClickable = true
        }
        val lbox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        lbox.addView(tv("🔒", 40f).apply { gravity = Gravity.CENTER }, lp(WC, WC))
        lbox.addView(tv("Spend Tracker is locked", 16f, Ui.text, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(16))
        }, lp(WC, WC))
        lbox.addView(btn("Unlock") { if (!prompting) authenticate() }, lp(dp(200), WC))
        lockView.addView(lbox, FrameLayout.LayoutParams(MP, MP))
        outer.addView(lockView, FrameLayout.LayoutParams(MP, MP))
        setContentView(outer)
        applySecure()

        if (hasSms()) maybeFirstImport() else perm.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
    }

    private fun authFlags(): Int =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    internal fun canLock(): Boolean =
        BiometricManager.from(this).canAuthenticate(authFlags()) == BiometricManager.BIOMETRIC_SUCCESS

    internal fun applySecure() {
        if (prefs.getBoolean("lock", false)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun authenticate() {
        prompting = true
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                lastActive = System.currentTimeMillis()
                lockView.visibility = View.GONE
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Spend Tracker")
            .setAllowedAuthenticators(authFlags())
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), cb).authenticate(info)
    }

    override fun onStart() {
        super.onStart()
        if (!prefs.getBoolean("lock", false) || !canLock()) {
            lockView.visibility = View.GONE
            return
        }
        if (lockView.visibility == View.VISIBLE || System.currentTimeMillis() - lastActive > 30000) {
            lockView.visibility = View.VISIBLE
            if (!prompting) authenticate()
        }
    }

    override fun onStop() {
        super.onStop()
        lastActive = System.currentTimeMillis()
    }

    private fun pickCatFilter() {
        val opts = (listOf("All") + Ui.cats + listOf("Income")).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Filter by category")
            .setItems(opts) { _, i ->
                catFilter = opts[i]
                refreshList()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    internal fun refresh() {
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
        catChip.text = "Category: $catFilter ▾"
        sortChip.text = "Sort: " + listOf("Newest", "Highest", "Lowest")[sortMode] + " ▾"
        val base = monthTx.filter {
            (filter == "all" || it.type == filter) &&
                (catFilter == "All" || it.cat == catFilter) &&
                (q.isEmpty() || it.merchant.lowercase().contains(q) || it.cat.lowercase().contains(q))
        }
        items = when (sortMode) {
            1 -> base.sortedByDescending { it.amount }
            2 -> base.sortedBy { it.amount }
            else -> base
        }
        adapter.notifyDataSetChanged()
        listView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }
}
