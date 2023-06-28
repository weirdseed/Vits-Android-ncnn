package com.example.moereng.utils.text

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType

class ChineseCleaners {
    private val latin_map = mapOf(
        "a" to "ㄟˉ",
        "b" to "ㄅㄧˋ",
        "c" to "ㄙㄧˉ",
        "d" to "ㄉㄧˋ",
        "e" to "ㄧˋ",
        "f" to "ㄝˊㄈㄨˋ",
        "g" to "ㄐㄧˋ",
        "h" to "ㄝˇㄑㄩˋ",
        "i" to "ㄞˋ",
        "j" to "ㄐㄟˋ",
        "k" to "ㄎㄟˋ",
        "l" to "ㄝˊㄛˋ",
        "m" to "ㄝˊㄇㄨˋ",
        "n" to "ㄣˉ",
        "o" to "ㄡˉ",
        "p" to "ㄆㄧˉ",
        "q" to "ㄎㄧㄡˉ",
        "r" to "ㄚˋ",
        "s" to "ㄝˊㄙˋ",
        "t" to "ㄊㄧˋ",
        "u" to "ㄧㄡˉ",
        "v" to "ㄨㄧˉ",
        "w" to "ㄉㄚˋㄅㄨˋㄌㄧㄡˋ",
        "x" to "ㄝˉㄎㄨˋㄙˋ",
        "y" to "ㄨㄞˋ",
        "z" to "ㄗㄟˋ",
        "1" to "ㄧˉ",
        "2" to "ㄦˋ",
        "3" to "ㄙㄢˉ",
        "4" to "ㄙˋ",
        "5" to "ㄨˇ",
        "6" to "ㄌㄧㄡˋ",
        "7" to "ㄑㄧˉ",
        "8" to "ㄅㄚˉ",
        "9" to "ㄐㄧㄡˇ",
        "0" to "ㄌㄧㄥˊ",
    )

    private val pinyin_map = mapOf(
        "^m(\\d)$" to "mu$1",
        "^n(\\d)$" to "N$1",
        "^r5$" to "er5",
        "iu" to "iou",
        "ui" to "uei",
        "ong" to "ung",
        "^yi?" to "i",
        "^wu?" to "u",
        "iu" to "v",
        "^([jqx])u" to "$1v",
        "([iuv])n" to "$1en",
        "^zhi?" to "Z",
        "^chi?" to "C",
        "^shi?" to "S",
        "^([zcsr])i" to "$1",
        "ai" to "A",
        "ei" to "I",
        "ao" to "O",
        "ou" to "U",
        "ang" to "K",
        "eng" to "G",
        "an" to "M",
        "en" to "N",
        "er" to "R",
        "eh" to "E",
        "([iv])e" to "$1E",
        "([^0-4])$" to "\$g<1>0"
    )

    private val bopomofo_table = arrayOf(
        "bpmfdtnlgkhjqxZCSrzcsiuvaoeEAIOUMNKGR12340",
        "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙㄧㄨㄩㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦˉˊˇˋ˙"
    )

    private val bopomofo_romaji = mapOf(
        Regex("ㄅㄛ") to "p⁼wo",
        Regex("ㄆㄛ") to "pʰwo",
        Regex("ㄇㄛ") to "mwo",
        Regex("ㄈㄛ") to "fwo",
        Regex("ㄅ") to "p⁼",
        Regex("ㄆ") to "pʰ",
        Regex("ㄇ") to "m",
        Regex("ㄈ") to "f",
        Regex("ㄉ") to "t⁼",
        Regex("ㄊ") to "tʰ",
        Regex("ㄋ") to "n",
        Regex("ㄌ") to "l",
        Regex("ㄍ") to "k⁼",
        Regex("ㄎ") to "kʰ",
        Regex("ㄏ") to "h",
        Regex("ㄐ") to "ʧ⁼",
        Regex("ㄑ") to "ʧʰ",
        Regex("ㄒ") to "ʃ",
        Regex("ㄓ") to "ʦ`⁼",
        Regex("ㄔ") to "ʦ`ʰ",
        Regex("ㄕ") to "s`",
        Regex("ㄖ") to "ɹ`",
        Regex("ㄗ") to "ʦ⁼",
        Regex("ㄘ") to "ʦʰ",
        Regex("ㄙ") to "s",
        Regex("ㄚ") to "a",
        Regex("ㄛ") to "o",
        Regex("ㄜ") to "ə",
        Regex("ㄝ") to "e",
        Regex("ㄞ") to "ai",
        Regex("ㄟ") to "ei",
        Regex("ㄠ") to "au",
        Regex("ㄡ") to "ou",
        Regex("ㄧㄢ") to "yeNN",
        Regex("ㄢ") to "aNN",
        Regex("ㄧㄣ") to "iNN",
        Regex("ㄣ") to "əNN",
        Regex("ㄤ") to "aNg",
        Regex("ㄧㄥ") to "iNg",
        Regex("ㄨㄥ") to "uNg",
        Regex("ㄩㄥ") to "yuNg",
        Regex("ㄥ") to "əNg",
        Regex("ㄦ") to "əɻ",
        Regex("ㄧ") to "i",
        Regex("ㄨ") to "u",
        Regex("ㄩ") to "ɥ",
        Regex("ˉ") to "→",
        Regex("ˊ") to "↑",
        Regex("ˇ") to "↓↑",
        Regex("ˋ") to "↓",
        Regex("˙") to "",
        Regex("，") to ",",
        Regex("。") to ".",
        Regex("！") to "!",
        Regex("？") to "?",
        Regex("—") to "-"
    )

    private val bopomofo_table_len = 42

    private fun latin_to_bopomofo(s: String): String {
        var ans = s
        for ((key, value) in latin_map) {
            ans = ans.replace(key.toRegex(), value)
        }
        return ans
    }

    private fun chinese_to_bopomofo(s: String): String {
        val format = HanyuPinyinOutputFormat()
        format.toneType = HanyuPinyinToneType.WITH_TONE_NUMBER
        val sb = StringBuilder()

        for (i in s.indices) {
            var bopomofo : String
            try {
                bopomofo = PinyinHelper.toHanyuPinyinStringArray(s[i])[0]
            } catch (e: Exception){
                bopomofo = ""
            }
            if (bopomofo.isEmpty()) {
                // 遇到不能转换的加上空格
                sb.append(s[i] + " ")
            } else {
                for ((key, value) in pinyin_map) {
                    bopomofo = bopomofo.replace(key.toRegex(), value)
                }
                for (j in 0 until bopomofo_table_len) {
                    bopomofo = bopomofo.replace(bopomofo_table[0][j], bopomofo_table[1][j])
                }
                sb.append(bopomofo)
            }
        }
        return sb.toString()
    }

    /**
     * text_cleaners: chinese_cleaners
     * */
    fun chinese_clean_text(inputs: String): String {
        // 数字转中文
        val text = number2chinese(inputs)
        val format = HanyuPinyinOutputFormat()
        format.toneType = HanyuPinyinToneType.WITH_TONE_NUMBER
        val cleaned_text = StringBuilder()
        for (element in text) {
            var s: String
            try {
                s = PinyinHelper.toHanyuPinyinStringArray(element)[0]
            } catch (ignore: Exception) {
                s = element.toString()
            }
            cleaned_text.append(s)
            if (cleaned_text.isNotEmpty()) cleaned_text.append(' ')
        }
        return cleaned_text.toString()
    }

    /**
     * text_cleaners: chinese_cleaners_moegoe
     * */
    fun chinese_clean_text_moegoe(inputs: String): String {
        var text = number2chinese(inputs)
        text = chinese_to_bopomofo(text)
        text = latin_to_bopomofo(text)
        val regex = Regex("([ˉˊˇˋ˙])$")
        text = regex.replace(text, "$1。")
        return text
    }

    private fun number2chinese(inputs: String): String {
        val an2cn = An2Cn()
        val regex = Regex("\\d+(?:\\.?\\d+)?")
        val numbers = regex.findAll(inputs).map { it.value }.toList()
        var converts = inputs
        for (number in numbers) {
            converts = converts.replace(number, an2cn.an2cn(number))
        }
        return converts
    }

    private fun bopomofo2romaji(inputs: String): String {
        var text = inputs
        for ((k, v) in bopomofo_romaji) {
            text = k.replace(text, v)
        }
        return text
    }

    fun chinese_to_romaji(inputs: String): String {
        var text = number2chinese(inputs)
        text = chinese_to_bopomofo(text)
        text = latin_to_bopomofo(text)
        text = bopomofo2romaji(text)
        text = Regex("i([aoe])").replace(text) { "y" + it.groupValues[1] }
        text = Regex("u([aoəe])").replace(text) { "w" + it.groupValues[1] }
        text = Regex("([ʦsɹ]`[⁼ʰ]?)([→↓↑ ]+|$)").replace(text) {
            it.groupValues[1] + "ɹ`" + it.groupValues[2]
        }
        text = text.replace("ɻ", "ɹ`")
        text = Regex("([ʦs][⁼ʰ]?)([→↓↑ ]+|\$)").replace(text) {
            it.groupValues[1] + "ɹ" + it.groupValues[2]
        }
        return text
    }

}
