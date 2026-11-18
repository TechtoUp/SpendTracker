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

class Db(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "spend.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tx(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, type TEXT, amount REAL, merchant TEXT, cat TEXT, src TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    @Synchronized
    fun add(ts: Long, type: String, amount: Double, merchant: String, cat: String, src: String): Boolean {
        val d = writableDatabase
        val c = d.rawQuery(
            "SELECT 1 FROM tx WHERE type=? AND amount=CAST(? AS REAL) AND ABS(ts-?)<180000 LIMIT 1",
            arrayOf(type, amount.toString(), ts.toString())
        )
        val dup = c.moveToFirst()
        c.close()
        if (dup) return false
        val v = ContentValues().apply {
            put("ts", ts)
            put("type", type)
            put("amount", amount)
            put("merchant", merchant)
            put("cat", cat)
            put("src", src)
        }
        d.insert("tx", null, v)
        return true
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

    fun del(id: Long) {
        writableDatabase.delete("tx", "id=?", arrayOf(id.toString()))
    }
}
