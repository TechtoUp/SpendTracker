package com.example.spendtracker

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import java.util.Calendar
import java.util.Date

fun MainActivity.buildDash() {
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

    if (!allTime) {
        val cb = Ui.cats.filter { prefs.getFloat("cb_$it", 0f) > 0f }
        if (cb.isNotEmpty()) {
            val bc = card()
            bc.addView(tv("Category budgets", 15f, Ui.text, true))
            cb.forEach { name ->
                val lim = prefs.getFloat("cb_$name", 0f).toDouble()
                val used = spendTx.filter { it.cat == name }.sumOf { it.amount }
                val pc = used / lim
                val col = if (pc > 1) Ui.red else if (pc > 0.8) Color.parseColor("#F59E0B") else Ui.primary
                val r = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(10), 0, dp(3))
                }
                r.addView(tv(name, 14f), lp(0, WC, 1f))
                r.addView(tv("${money(used)} / ${money(lim)}", 13f, if (pc > 1) Ui.red else Ui.text, true))
                bc.addView(r)
                bc.addView(progress(pc, col), lp(MP, dp(6)))
            }
            dash.addView(bc)
        }
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
            r.addView(tv(money(v), 14f, Ui.text, true))
            cc.addView(r)
            cc.addView(progress(share, Ui.catColor(name)), lp(MP, dp(5)))
        }
    }
    dash.addView(cc)

    if (!allTime) {
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val arr = DoubleArray(days)
        val tmp = Calendar.getInstance()
        spendTx.forEach {
            tmp.timeInMillis = it.ts
            arr[tmp.get(Calendar.DAY_OF_MONTH) - 1] += it.amount
        }
        val dc = card()
        dc.addView(tv("Daily spending", 15f, Ui.text, true))
        val ch = BarChartView(this)
        ch.set(arr, List(days) { (it + 1).toString() }, Ui.primary)
        dc.addView(ch, lp(MP, dp(150)).apply { topMargin = dp(8) })
        dash.addView(dc)
    }

    val n = if (allTime) 12 else 6
    val vals = DoubleArray(n)
    val labels = ArrayList<String>()
    for (i in 0 until n) {
        val off = i - (n - 1)
        val a = monthStart(off)
        vals[i] = db.spent(a, monthStart(off + 1))
        labels.add(ms.format(Date(a)))
    }
    val tc = card()
    tc.addView(tv("Last $n months", 15f, Ui.text, true))
    val tch = BarChartView(this)
    tch.set(vals, labels, Color.parseColor("#0EA5E9"))
    tc.addView(tch, lp(MP, dp(160)).apply { topMargin = dp(8) })
    dash.addView(tc)

    val top = spendTx.groupBy { it.merchant.lowercase() }
        .map { e -> Triple(e.value[0].merchant, e.value.sumOf { it.amount }, e.value.size) }
        .sortedByDescending { it.second }
        .take(6)
    if (top.isNotEmpty()) {
        val mc = card()
        mc.addView(tv("Top merchants", 15f, Ui.text, true))
        top.forEach { (name, amt, cnt) ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(2))
            }
            r.addView(tv("$name  ×$cnt", 14f), lp(0, WC, 1f))
            r.addView(tv(money(amt), 14f, Ui.text, true))
            mc.addView(r)
        }
        dash.addView(mc)
    }

    val dd: Int = if (allTime) {
        val f = spendTx.minOfOrNull { it.ts }
        if (f == null) 1 else ((System.currentTimeMillis() - f) / 86400000L + 1).toInt()
    } else {
        val now = Calendar.getInstance()
        if (now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && now.get(Calendar.MONTH) == cal.get(Calendar.MONTH)) {
            now.get(Calendar.DAY_OF_MONTH)
        } else {
            cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
    }
    val biggest = spendTx.maxByOrNull { it.amount }
    val ic = card()
    ic.addView(tv("Insights", 15f, Ui.text, true))
    ic.addView(kv("Average per day", money(spent / dd)))
    if (spendTx.isNotEmpty()) ic.addView(kv("Average transaction", money(spent / spendTx.size)))
    if (biggest != null) ic.addView(kv("Biggest expense", "${biggest.merchant} · ${money(biggest.amount)}"))
    if (byCat.isNotEmpty()) ic.addView(kv("Top category", byCat[0].first))
    dash.addView(ic)
}
