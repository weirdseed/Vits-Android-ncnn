package com.example.moereng.utils.text

import android.util.Log
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

    private val AN2CN = mapOf(
        "0" to "零",
        "1" to "一",
        "2" to "二",
        "3" to "三",
        "4" to "四",
        "5" to "五",
        "6" to "六",
        "7" to "七",
        "8" to "八",
        "9" to "九"
    )

    private val bopomofo_table = arrayOf(
        "bpmfdtnlgkhjqxZCSrzcsiuvaoeEAIOUMNKGR12340",
        "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙㄧㄨㄩㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦˉˊˇˋ˙"
    )

    private val bopomofo_table_len = 42

    private fun no_punctuation(s: String): String {
        var ans = s
        val punctuation_list = arrayOf("，", "、", "；", "：", "。", "？", ",", ".", "?", "\"", "'")
        for (i in punctuation_list) {
            ans = ans.replace(i, " ")
        }
        return ans
    }

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
        var pinyin: String?
        for (i in s.indices) {
            pinyin = null
            try {
                pinyin = PinyinHelper.toHanyuPinyinStringArray(s[i])[0]
            } catch (ignore: Exception) {
            }
            if (pinyin == null) {  //无法转拼音，直接拷贝
                sb.append(s[i])
            } else {
                var bopomofo: String = pinyin
                for ((key, value) in pinyin_map) {
                    bopomofo = bopomofo.replace(key.toRegex(), value)
                    Log.e("tmp", "$bopomofo $key $value")
                }
                Log.e("tmp", bopomofo)
                for (j in 0 until bopomofo_table_len) bopomofo =
                    bopomofo.replace(bopomofo_table[0][j], bopomofo_table[1][j])
                sb.append(bopomofo)
            }
            if (sb.isNotEmpty()) sb.append(' ')
        }
        return sb.toString()
    }

    fun chinese_clean_text(text: String): String {
        val format = HanyuPinyinOutputFormat()
        format.toneType = HanyuPinyinToneType.WITH_TONE_NUMBER
        val sb = StringBuilder()
        for (element in text) {
            var pinyin = ""
            try {
                pinyin = PinyinHelper.toHanyuPinyinStringArray(element)[0]
            } catch (ignore: Exception) {
            }
            sb.append(pinyin)
            if (sb.isNotEmpty()) sb.append(' ')
        }
        return sb.toString()
    }

    fun chinese_clean_text_moegoe(inputs: String): String {
        var text = number2chinese(inputs)
        text = chinese_to_bopomofo(text)
        text = latin_to_bopomofo(text)
        val regex = Regex("([ˉˊˇˋ˙])$")
        text = regex.replace(text, "$1。")
        return text
    }

    private fun number2chinese(inputs: String): String{
        val an2cn = An2Cn()
        val regex = Regex("\\d+(?:\\.?\\d+)?")
        val numbers = regex.findAll(inputs).map { it.value }.toList()
        var converts = inputs
        for (number in numbers){
            converts = converts.replace(number, an2cn.an2cn(number))
        }
        return converts
    }

    private fun chinese_to_romaji() {

    }

}