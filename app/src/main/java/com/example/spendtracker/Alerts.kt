package com.example.spendtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object Alerts {

    fun check(ctx: Context) {
        val prefs = ctx.getSharedPreferences("st", Context.MODE_PRIVATE)
        val db = Db(ctx)
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val from = c.timeInMillis
        val key = SimpleDateFormat("yyyyMM", Locale.US).format(c.time)
        c.add(Calendar.MONTH, 1)
        val to = c.timeInMillis

        val total = db.spent(from, to)
        val budget = prefs.getFloat("budget", 0f).toDouble()
        if (budget > 0) level(ctx, prefs, key, "Monthly budget", total, budget)

        val by = db.spentByCat(from, to)
        for (cat in Ui.cats) {
            val lim = prefs.getFloat("cb_$cat", 0f).toDouble()
            if (lim > 0) level(ctx, prefs, key, cat, by[cat] ?: 0.0, lim)
        }
    }

    private fun level(ctx: Context, prefs: android.content.SharedPreferences, key: String, name: String, spent: Double, limit: Double) {
        val lv = if (spent >= limit) 100 else if (spent >= limit * 0.8) 80 else 0
        if (lv == 0) return
        val k = "al_" + key + "_" + name
        if (prefs.getInt(k, 0) >= lv) return
        prefs.edit().putInt(k, lv).apply()
        val title = if (lv >= 100) "$name budget crossed" else "$name at 80% of budget"
        val text = money(spent) + " spent of " + money(limit)
        send(ctx, name.hashCode() and 0xffff, title, text)
    }

    private fun send(ctx: Context, id: Int, title: String, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel("budget", "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, "budget")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(id, n)
    }
}
