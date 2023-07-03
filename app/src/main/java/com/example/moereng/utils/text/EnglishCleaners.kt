package com.example.moereng.utils.text

import android.content.res.AssetManager
import android.util.Log
import java.lang.NumberFormatException

class EnglishCleaners(assetManager: AssetManager) {
    private val numConverter = An2En()

    private val ipaConverter = Eng2IPA(assetManager)

    private val abbreviations = mapOf(
        Regex("\\bmrs\\.", RegexOption.IGNORE_CASE) to "misess",
        Regex("\\bmr\\.", RegexOption.IGNORE_CASE) to "mister",
        Regex("\\bdr\\.", RegexOption.IGNORE_CASE) to "doctor",
        Regex("\\bst\\.", RegexOption.IGNORE_CASE) to "saint",
        Regex("\\bco\\.", RegexOption.IGNORE_CASE) to "company",
        Regex("\\bjr\\.", RegexOption.IGNORE_CASE) to "junior",
        Regex("\\bmaj\\.", RegexOption.IGNORE_CASE) to "major",
        Regex("\\bgen\\.", RegexOption.IGNORE_CASE) to "general",
        Regex("\\bdrs\\.", RegexOption.IGNORE_CASE) to "doctors",
        Regex("\\brev\\.", RegexOption.IGNORE_CASE) to "reverend",
        Regex("\\blt\\.", RegexOption.IGNORE_CASE) to "lieutenant",
        Regex("\\bhon\\.", RegexOption.IGNORE_CASE) to "honorable",
        Regex("\\bsgt\\.", RegexOption.IGNORE_CASE) to "sergeant",
        Regex("\\bcapt\\.", RegexOption.IGNORE_CASE) to "captain",
        Regex("\\besq\\.", RegexOption.IGNORE_CASE) to "esquire",
        Regex("\\bltd\\.", RegexOption.IGNORE_CASE) to "limited",
        Regex("\\bcol\\.", RegexOption.IGNORE_CASE) to "colonel",
        Regex("\\bft\\.", RegexOption.IGNORE_CASE) to "fort",
    )

    private val commaNumberRe = Regex("([0-9][0-9,]+[0-9])")

    private val poundsRe = Regex("£([0-9,]*[0-9]+)")

    private val dollarsRe = Regex("\\$([0-9.,]*[0-9]+)")

    private val decimalRe = Regex("[0-9]+\\.[0-9]+")

    private val ordinalRe = Regex("[0-9]+(st|nd|rd|th)")

    private val numberRe = Regex("[0-9]+")

    private val ipa2ipa2 = mapOf(
        Regex("r") to "ɹ",
        Regex("ʤ") to "dʒ",
        Regex("ʧ") to "tʃ"
    )

    private fun expandDollars(inputs: String): String {
        val splits = inputs.split(".")
        if (splits.size > 2) {
            return "$inputs dollars"
        }

        // 转换美元及美分
        val dollars = splits[0].toIntOrNull() ?: 0
        val cents = splits.getOrNull(1)?.toIntOrNull() ?: 0
        val dollarsUnit = if (dollars == 1) "dollar" else "dollars"
        val centsUnit = if (cents == 1) "cent" else "cents"

        when {
            (dollars != 0 && cents != 0) -> return "$dollars $dollarsUnit, $cents $centsUnit"
            (dollars != 0) -> return "$dollars $dollarsUnit"
            (cents != 0) -> return "$cents $centsUnit"
            else -> return "zero dollars"
        }
    }

    private fun expandAbbreviations(inputs: String): String {
        var text = inputs
        for ((k, v) in abbreviations) {
            text = k.replace(text, v)
        }
        return text
    }

    private fun normalizeNumbers(inputs: String): String {
        // 处理逗号
        var text = commaNumberRe.replace(inputs) {
            it.groupValues[1].replace(",", "")
        }

        // 处理£符号
        text = poundsRe.replace(text) {
            it.groupValues[1] + " pounds"
        }

        // 处理$符号
        text = dollarsRe.replace(text) {
            expandDollars(it.groupValues[1])
        }

        // 处理小数点
        text = decimalRe.replace(text) {
            it.groupValues[1].replace(".", " point ")
        }

        try {

            // 处理序数
            text = ordinalRe.replace(text) {
                numConverter.numberToWords(it.groupValues[0], it.groupValues[1])
            }

            // 处理数字
            text = numberRe.replace(text) {
                numConverter.numberToWords(it.groupValues[0], null)
            }
        } catch (e: NumberFormatException) {
            Log.e("EnglishCleaners", "${e.message}")
        }
        return text
    }

    fun english_clean_text(inputs: String): String {
        // 转换小写
        var text = inputs.lowercase()

        // 展开缩写
        text = expandAbbreviations(text)

        // 转换数字
        text = normalizeNumbers(text)

        // 转换音标
        var phones = ipaConverter.convert(text)

        // 去除空格
        phones = Regex("\\s+").replace(phones, " ")

        return phones
    }

    fun english_clean_text2(inputs: String): String {
        var text = english_clean_text(inputs)
        text = text.replace(Regex("l([^aeiouæɑɔəɛɪʊ ]*(?: |$))")) { "ɫ${it.groupValues[1]}" }
        ipa2ipa2.forEach{(regex, replacement) ->
            text = regex.replace(text, replacement)
        }
        return text.replace("...", "…")
    }
}