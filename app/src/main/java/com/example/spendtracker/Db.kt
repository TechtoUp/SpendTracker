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

class Db(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "spend.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tx(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, type TEXT, amount REAL, merchant TEXT, cat TEXT, src TEXT)"
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS rules(merchant TEXT PRIMARY KEY, cat TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ts ON tx(ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {
        db.execSQL("CREATE TABLE IF NOT EXISTS rules(merchant TEXT PRIMARY KEY, cat TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ts ON tx(ts)")
    }

    private fun ruleFor(d: SQLiteDatabase, merchant: String): String? {
        val c = d.rawQuery("SELECT cat FROM rules WHERE merchant=?", arrayOf(merchant.lowercase()))
        val r = if (c.moveToFirst()) c.getString(0) else null
        c.close()
        return r
    }

    @Synchronized
    fun add(ts: Long, type: String, amount: Double, merchant: String, cat: String, src: String): Boolean {
        val d = writableDatabase
        val c = d.rawQuery(
            "SELECT 1 FROM tx WHERE type=? AND amount=CAST(? AS REAL) AND ABS(ts-?)<120000 AND (src<>? OR merchant=?) LIMIT 1",
            arrayOf(type, amount.toString(), ts.toString(), src, merchant)
        )
        val dup = c.moveToFirst()
        c.close()
        if (dup) return false
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
            d.execSQL("UPDATE tx SET cat=? WHERE type='debit' AND lower(merchant)=?", arrayOf<Any>(cat, k))
        }
    }

    fun del(id: Long) {
        writableDatabase.delete("tx", "id=?", arrayOf(id.toString()))
    }

    fun clear() {
        writableDatabase.delete("tx", null, null)
    }
}
