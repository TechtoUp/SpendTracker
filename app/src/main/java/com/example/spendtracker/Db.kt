package com.example.spendtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Tx(
    val id: Long,
    val ts: Long,
    val type: String,
    val amount: Double,
    val merchant: String,
    val cat: String,
    val src: String
)

data class LogRow(val ts: Long, val pkg: String, val body: String, val res: String)

class Db(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "spend.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tx(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, type TEXT, amount REAL, merchant TEXT, cat TEXT, src TEXT)"
        )
        extras(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {
        extras(db)
    }

    private fun extras(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS rules(merchant TEXT PRIMARY KEY, cat TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ts ON tx(ts)")
        db.execSQL("CREATE TABLE IF NOT EXISTS nlog(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, pkg TEXT, body TEXT, res TEXT)")
    }

    private fun loadRules(d: SQLiteDatabase): Map<String, String> {
        val m = HashMap<String, String>()
        val c = d.rawQuery("SELECT merchant,cat FROM rules", null)
        while (c.moveToNext()) m[c.getString(0)] = c.getString(1)
        c.close()
        return m
    }

    private fun matchRule(m: Map<String, String>, merchant: String): String? {
        val k = merchant.lowercase()
        val exact = m[k]
        if (exact != null) return exact
        var best: String? = null
        var bl = 0
        for ((r, cat) in m) {
            if (r.length < 4) continue
            val hit = k.startsWith("$r ") || (k.length >= 4 && r.startsWith("$k "))
            if (hit && r.length > bl) {
                best = cat
                bl = r.length
            }
        }
        return best
    }

    private fun ruleFor(d: SQLiteDatabase, merchant: String): String? = matchRule(loadRules(d), merchant)

    @Synchronized
    fun add(ts: Long, type: String, amount: Double, merchant: String, cat: String, src: String): Boolean {
        val d = writableDatabase
        val c = d.rawQuery(
            "SELECT id,merchant FROM tx WHERE type=? AND amount=CAST(? AS REAL) AND ABS(ts-?)<(CASE WHEN src='mail' OR ?='mail' THEN 1800000 ELSE 120000 END) AND (src<>? OR merchant=?) LIMIT 1",
            arrayOf(type, amount.toString(), ts.toString(), src, src, merchant)
        )
        var dupId = -1L
        var dupMerchant = ""
        if (c.moveToFirst()) {
            dupId = c.getLong(0)
            dupMerchant = c.getString(1)
        }
        c.close()
        if (dupId >= 0) {
            if (dupMerchant == "Unknown" && merchant != "Unknown") {
                val fc = if (type == "debit") (ruleFor(d, merchant) ?: cat) else cat
                d.execSQL("UPDATE tx SET merchant=?, cat=? WHERE id=?", arrayOf<Any>(merchant, fc, dupId))
            }
            return false
        }
        val finalCat = if (type == "debit") (ruleFor(d, merchant) ?: cat) else cat
        insertRaw(ts, type, amount, merchant, finalCat, src)
        return true
    }

    @Synchronized
    fun insertRaw(ts: Long, type: String, amount: Double, merchant: String, cat: String, src: String) {
        val v = ContentValues().apply {
            put("ts", ts)
            put("type", type)
            put("amount", amount)
            put("merchant", merchant)
            put("cat", cat)
            put("src", src)
        }
        writableDatabase.insert("tx", null, v)
    }

    @Synchronized
    fun batch(f: () -> Unit) {
        val d = writableDatabase
        d.beginTransaction()
        try {
            f()
            d.setTransactionSuccessful()
