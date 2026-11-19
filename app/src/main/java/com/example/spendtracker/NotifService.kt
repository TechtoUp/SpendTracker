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

    private val mailKey = Regex("slice", RegexOption.IGNORE_CASE)

    private val keys = listOf("android.title", "android.text", "android.bigText", "android.subText")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val isMail = sbn.packageName in mail
        if (sbn.packageName !in pk && !isMail) return
        val e = sbn.notification.extras
        val t = keys.joinToString(" ") { e.getCharSequence(it)?.toString() ?: "" }
        if (isMail && !mailKey.containsMatchIn(t)) return
        val p = Parser.parse(t) ?: return
        Db(this).add(sbn.postTime, p.type, p.amount, p.merchant, p.cat, "notif")
    }
}
