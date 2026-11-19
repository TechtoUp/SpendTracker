package com.example.spendtracker

data class Parsed(val type: String, val amount: Double, val merchant: String, val cat: String)

object Parser {
    private val amt = Regex("(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    private val dr = Regex("\\b(debited|spent|paid|withdrawn|sent|purchase|deducted)\\b", RegexOption.IGNORE_CASE)
    private val cr = Regex("\\b(credited|received|deposited|refund(?:ed)?)\\b", RegexOption.IGNORE_CASE)
    private val skip = Regex(
        "\\b(otp|one time|will be|due|requested|request|minimum|offer|cashback offer|pre-approved|preapproved|apply now|click|failed|declined|unsuccessful|insufficient)\\b",
        RegexOption.IGNORE_CASE
    )
    private val atm = Regex("\\batm\\b|cash withdrawal", RegexOption.IGNORE_CASE)
    private val nonAlnum = Regex("[^a-z0-9]")
    private val mer = Regex(
        "(?:\\bto|\\bat|towards|\\bfrom|\\bby|VPA|info[:\\-])\\s+([A-Za-z0-9@._&' -]{2,40}?)(?=\\s+(?:on|ref|via|using|avl|bal|upi|a/c|acct|from|for)\\b|[.,;(]|$)",
        RegexOption.IGNORE_CASE
    )

    private val rules = listOf(
        "Groceries" to listOf("bigbasket", "blinkit", "zepto", "dmart", "jiomart", "instamart", "grocery", "supermarket", "reliance fresh", "more retail", "licious", "nature's basket"),
        "Food" to listOf("swiggy", "zomato", "dominos", "mcdonald", "kfc", "restaurant", "cafe", "bakery", "eatery", "starbucks", "chaayos", "subway", "pizza", "burger", "biryani", "hotel", "dhaba"),
        "Travel" to listOf("uber", "ola", "rapido", "irctc", "redbus", "metro", "fuel", "petrol", "diesel", "hpcl", "bpcl", "indianoil", "makemytrip", "fastag", "toll", "indigo", "airlines", "railway"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "croma", "reliance digital", "decathlon", "lifestyle", "pantaloons", "shoppers stop"),
        "Bills" to listOf("jio", "airtel", "vodafone", "vi prepaid", "electricity", "bescom", "mseb", "adani", "recharge", "broadband", "gas", "insurance", "lic", "emi", "rent", "tata power", "water bill"),
        "Fun" to listOf("netflix", "spotify", "hotstar", "prime", "bookmyshow", "pvr", "inox", "youtube", "gaming", "steam"),
        "Health" to listOf("pharmacy", "medical", "apollo", "hospital", "clinic", "1mg", "pharmeasy", "diagnostic", "lab", "dental"),
        "Cash" to listOf("atm", "cash withdrawal")
    )

    private fun hit(l: String, k: String): Boolean =
        if (k.length <= 3) " ${l.replace(nonAlnum, " ")} ".contains(" $k ") else l.contains(k)

    fun category(m: String, type: String): String {
        if (type == "credit") return "Income"
        val l = m.lowercase()
        for ((k, v) in rules) if (v.any { hit(l, it) }) return k
        return "Other"
    }

    private fun pickAmount(t: String): Double? {
        for (m in amt.findAll(t)) {
            val s = m.range.first
            val pre = t.substring(maxOf(0, s - 18), s).lowercase()
            if (pre.contains("bal") || pre.contains("limit") || pre.contains("avl")) continue
            val v = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            if (v > 0) return v
        }
        return null
    }

    fun parse(text: String): Parsed? {
        val t = text.replace("\n", " ").trim()
        if (skip.containsMatchIn(t)) return null
        val amount = pickAmount(t) ?: return null
        val d = dr.find(t)?.range?.first ?: Int.MAX_VALUE
        val c = cr.find(t)?.range?.first ?: Int.MAX_VALUE
        if (d == Int.MAX_VALUE && c == Int.MAX_VALUE) return null
        val type = if (d < c) "debit" else "credit"
        var m = mer.find(t)?.groupValues?.get(1)?.trim()?.trimEnd('-', ' ') ?: "Unknown"
        if (type == "debit" && atm.containsMatchIn(t)) m = "ATM Withdrawal"
        if (m.isBlank()) m = "Unknown"
        val cat = if (m == "ATM Withdrawal") "Cash" else category(m, type)
        return Parsed(type, amount, m, cat)
    }
}
