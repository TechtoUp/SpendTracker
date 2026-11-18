package com.example.spendtracker

data class Parsed(val type: String, val amount: Double, val merchant: String, val cat: String)

object Parser {
    private val amt = Regex("(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    private val dr = Regex("\\b(debited|spent|paid|withdrawn|sent|purchase|deducted)\\b", RegexOption.IGNORE_CASE)
    private val cr = Regex("\\b(credited|received|deposited|refund(?:ed)?)\\b", RegexOption.IGNORE_CASE)
    private val skip = Regex("\\b(otp|one time|will be|due|requested|request|minimum|offer|cashback offer)\\b", RegexOption.IGNORE_CASE)
    private val mer = Regex(
        "(?:\\bto|\\bat|towards|\\bfrom|\\bby|VPA|info[:\\-])\\s+([A-Za-z0-9@._&' -]{2,40}?)(?=\\s+(?:on|ref|via|using|avl|bal|upi|a/c|acct|from|for)\\b|[.,;(]|$)",
        RegexOption.IGNORE_CASE
    )

    private val rules = listOf(
        "Food" to listOf("swiggy", "zomato", "dominos", "mcdonald", "kfc", "restaurant", "cafe", "bakery", "eatery"),
        "Travel" to listOf("uber", "ola", "rapido", "irctc", "redbus", "metro", "fuel", "petrol", "hpcl", "bpcl", "indianoil", "makemytrip"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "bigbasket", "blinkit", "zepto", "dmart"),
        "Bills" to listOf("jio", "airtel", "vodafone", "electricity", "bescom", "mseb", "adani", "recharge", "broadband", "gas", "insurance", "lic"),
        "Fun" to listOf("netflix", "spotify", "hotstar", "prime", "bookmyshow", "pvr", "inox", "youtube"),
        "Health" to listOf("pharmacy", "medical", "apollo", "hospital", "clinic", "1mg", "pharmeasy")
    )

    fun category(m: String, type: String): String {
        if (type == "credit") return "Income"
        val l = m.lowercase()
        for ((k, v) in rules) if (v.any { l.contains(it) }) return k
        return "Other"
    }

    fun parse(text: String): Parsed? {
        val t = text.replace("\n", " ").trim()
        if (skip.containsMatchIn(t)) return null
        val a = amt.find(t) ?: return null
        val amount = a.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        val d = dr.find(t)?.range?.first ?: Int.MAX_VALUE
        val c = cr.find(t)?.range?.first ?: Int.MAX_VALUE
        if (d == Int.MAX_VALUE && c == Int.MAX_VALUE) return null
        val type = if (d < c) "debit" else "credit"
        val m = mer.find(t)?.groupValues?.get(1)?.trim()?.trimEnd('-', ' ') ?: "Unknown"
        return Parsed(type, amount, m, category(m, type))
    }
}
