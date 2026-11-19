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
        } finally {
            d.endTransaction()
        }
    }

    fun range(from: Long, to: Long): List<Tx> {
        val out = ArrayList<Tx>()
        val c = readableDatabase.rawQuery(
            "SELECT id,ts,type,amount,merchant,cat,src FROM tx WHERE ts>=? AND ts<? ORDER BY ts DESC",
            arrayOf(from.toString(), to.toString())
        )
        while (c.moveToNext()) {
            out.add(Tx(c.getLong(0), c.getLong(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5), c.getString(6)))
        }
        c.close()
        return out
    }

    fun spent(from: Long, to: Long): Double {
        val c = readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(amount),0) FROM tx WHERE type='debit' AND ts>=? AND ts<?",
            arrayOf(from.toString(), to.toString())
        )
        val r = if (c.moveToFirst()) c.getDouble(0) else 0.0
        c.close()
        return r
    }

    fun spentByCat(from: Long, to: Long): Map<String, Double> {
        val out = HashMap<String, Double>()
        val c = readableDatabase.rawQuery(
            "SELECT cat,SUM(amount) FROM tx WHERE type='debit' AND ts>=? AND ts<? GROUP BY cat",
            arrayOf(from.toString(), to.toString())
        )
        while (c.moveToNext()) out[c.getString(0)] = c.getDouble(1)
        c.close()
        return out
    }

    fun count(): Int {
        val c = readableDatabase.rawQuery("SELECT COUNT(*) FROM tx", null)
        val r = if (c.moveToFirst()) c.getInt(0) else 0
        c.close()
        return r
    }

    fun minTs(): Long {
        val c = readableDatabase.rawQuery("SELECT MIN(ts) FROM tx", null)
        val r = if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        c.close()
        return r
    }

    @Synchronized
    fun setCat(id: Long, merchant: String, cat: String) {
        val d = writableDatabase
        d.execSQL("UPDATE tx SET cat=? WHERE id=?", arrayOf<Any>(cat, id))
        if (merchant.isNotBlank() && merchant != "Unknown") {
            val k = merchant.lowercase()
            d.execSQL("INSERT OR REPLACE INTO rules(merchant,cat) VALUES(?,?)", arrayOf<Any>(k, cat))
            d.execSQL(
                "UPDATE tx SET cat=? WHERE type='debit' AND (lower(merchant)=? OR lower(merchant) LIKE ?)",
                arrayOf<Any>(cat, k, "$k %")
            )
        }
    }

    @Synchronized
    fun recategorize(): Int {
        val d = writableDatabase
        var n = 0
        d.beginTransaction()
        try {
            val old = loadRules(d)
            d.execSQL("DELETE FROM rules")
            for ((k, c) in old) {
                val nk = Parser.cleanMerchant(k).lowercase()
                if (nk != "unknown") d.execSQL("INSERT OR REPLACE INTO rules(merchant,cat) VALUES(?,?)", arrayOf<Any>(nk, c))
            }
            val rules = loadRules(d)
            val rows = ArrayList<Tx>()
            val c = d.rawQuery("SELECT id,ts,type,amount,merchant,cat,src FROM tx WHERE type='debit' AND src<>'manual'", null)
            while (c.moveToNext()) {
                rows.add(Tx(c.getLong(0), c.getLong(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5), c.getString(6)))
            }
            c.close()
            for (r in rows) {
                val nm = if (r.merchant == "ATM Withdrawal") r.merchant else Parser.cleanMerchant(r.merchant)
                val rule = matchRule(rules, nm)
                val nc = rule ?: (if (r.cat == "Other") Parser.category(nm, "debit") else r.cat)
                if (nm != r.merchant || nc != r.cat) {
                    d.execSQL("UPDATE tx SET merchant=?, cat=? WHERE id=?", arrayOf<Any>(nm, nc, r.id))
                    n++
                }
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
        return n
    }

    fun allRules(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val c = readableDatabase.rawQuery("SELECT merchant,cat FROM rules", null)
        while (c.moveToNext()) out.add(Pair(c.getString(0), c.getString(1)))
        c.close()
        return out
    }

    @Synchronized
    fun putRule(merchant: String, cat: String) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO rules(merchant,cat) VALUES(?,?)", arrayOf<Any>(merchant.lowercase(), cat))
    }

    @Synchronized
    fun restoreTx(ts: Long, type: String, amount: Double, merchant: String, cat: String, src: String): Boolean {
        val c = writableDatabase.rawQuery(
            "SELECT 1 FROM tx WHERE ts=? AND type=? AND amount=CAST(? AS REAL) AND merchant=? LIMIT 1",
            arrayOf(ts.toString(), type, amount.toString(), merchant)
        )
        val dup = c.moveToFirst()
        c.close()
        if (dup) return false
        insertRaw(ts, type, amount, merchant, cat, src)
        return true
    }

    @Synchronized
    fun log(pkg: String, body: String, res: String) {
        val d = writableDatabase
        val v = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("pkg", pkg)
            put("body", body.take(200))
            put("res", res)
        }
        d.insert("nlog", null, v)
        d.execSQL("DELETE FROM nlog WHERE id NOT IN (SELECT id FROM nlog ORDER BY id DESC LIMIT 100)")
    }

    fun logs(): List<LogRow> {
        val out = ArrayList<LogRow>()
        val c = readableDatabase.rawQuery("SELECT ts,pkg,body,res FROM nlog ORDER BY id DESC", null)
        while (c.moveToNext()) out.add(LogRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)))
        c.close()
        return out
    }

    fun clearLogs() {
        writableDatabase.delete("nlog", null, null)
    }

    fun del(id: Long) {
        writableDatabase.delete("tx", "id=?", arrayOf(id.toString()))
    }

    fun clear() {
        writableDatabase.delete("tx", null, null)
    }
}
