package com.example.moereng.utils.text

class An2En {
    private val singles =
        arrayOf("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    var teens = arrayOf(
        "ten",
        "eleven",
        "twelve",
        "thirteen",
        "fourteen",
        "fifteen",
        "sixteen",
        "seventeen",
        "eighteen",
        "nineteen"
    )
    private val tens = arrayOf(
        "",
        "ten",
        "twenty",
        "thirty",
        "forty",
        "fifty",
        "sixty",
        "seventy",
        "eighty",
        "ninety"
    )

    private val ordinal = mapOf(
        "ty" to "tieth",
        "one" to "first",
        "two" to "second",
        "three" to "third",
        "five" to "fifth",
        "eight" to "eighth",
        "nine" to "ninth",
        "twelve" to "twelfth"
    )

    private val ordinal_suff = Regex("(ty|one|two|three|five|eight|nine|twelve)\$")

    private val thousands = arrayOf("", "Thousand", "Million", "Billion")

    fun numberToWords(number: String, ordinal_suffix: String?): String {
        var num = if (ordinal_suffix != null) number.replace(ordinal_suffix, "")
            .toInt() else number.toInt()
        if (num == 0) {
            return "Zero"
        }
        val sb = StringBuffer()
        var i = 3
        var unit = 1000000000
        while (i >= 0) {
            val curNum = num / unit
            if (curNum != 0) {
                num -= curNum * unit
                val curr = StringBuffer()
                recursion(curr, curNum)
                curr.append(thousands[i]).append(" ")
                sb.append(curr)
            }
            i--
            unit /= 1000
        }
        var result = sb.toString().trim { it <= ' ' }
        if (ordinal_suffix != null) {
            val splits = result.split(" ")
            val match = ordinal_suff.find(splits.last())
            val ordinal_num = if (match != null) ordinal_suff.replace(splits.last()) {
                ordinal.getOrDefault(it.groupValues[1], splits.last())
            } else splits.last() + "th"
            val splits_sub = splits.subList(0, splits.size - 1).toMutableList()
            splits_sub.add(ordinal_num)
            result = splits_sub.joinToString(" ")
        }
        return result
    }

    private fun recursion(curr: StringBuffer, num: Int) {
        if (num == 0) {
            return
        } else if (num < 10) {
            curr.append(singles[num]).append(" ")
        } else if (num < 20) {
            curr.append(teens[num - 10]).append(" ")
        } else if (num < 100) {
            curr.append(tens[num / 10]).append(" ")
            recursion(curr, num % 10)
        } else {
            curr.append(singles[num / 100]).append(" Hundred ")
            recursion(curr, num % 100)
        }
    }
}