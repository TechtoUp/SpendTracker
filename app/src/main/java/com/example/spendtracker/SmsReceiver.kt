package com.example.spendtracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(i) ?: return
        if (msgs.isEmpty()) return
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val ts = msgs[0].timestampMillis
        val p = Parser.parse(body) ?: return
        val ok = Db(c).add(ts, p.type, p.amount, p.merchant, p.cat, "sms")
        if (ok) {
            try {
                Alerts.check(c)
            } catch (x: Exception) {
            }
        }
    }
}
