package com.example.spendtracker

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifService : NotificationListenerService() {

    private val pk = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        "com.dreamplug.androidapp",
        "indwin.c3.shareapp"
    )

    private val mail = setOf(
        "com.google.android.gm",
        "com.google.android.gm.lite",
        "com.microsoft.office.outlook"
    )

    private val keys = listOf("android.title", "android.text", "android.bigText", "android.subText")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val isMail = pkg in mail
        if (pkg !in pk && !isMail) return
        val e = sbn.notification.extras
        val t = keys.joinToString(" ") { e.getCharSequence(it)?.toString() ?: "" }.trim()
        if (isMail) {
            val kw = (getSharedPreferences("st", MODE_PRIVATE).getString("mail_keys", "slice") ?: "slice")
                .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val low = t.lowercase()
            if (kw.none { low.contains(it) }) return
        }
        val db = Db(this)
        val p = Parser.parse(t)
        if (p == null) {
            db.log(pkg, t, Parser.explain(t))
            return
        }
        val ok = db.add(sbn.postTime, p.type, p.amount, p.merchant, p.cat, if (isMail) "mail" else "notif")
        db.log(pkg, t, if (ok) "added " + p.type + " " + p.amount + " " + p.merchant else "duplicate, already saved")
        if (ok) {
            try {
                Alerts.check(this)
            } catch (x: Exception) {
            }
        }
    }
}
