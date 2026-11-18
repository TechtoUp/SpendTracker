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

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in pk) return
        val e = sbn.notification.extras
        val t = "${e.getCharSequence("android.title") ?: ""} ${e.getCharSequence("android.text") ?: ""}"
        val p = Parser.parse(t) ?: return
        Db(this).add(sbn.postTime, p.type, p.amount, p.merchant, p.cat, "notif")
    }
}
