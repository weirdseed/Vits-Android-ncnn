package com.example.moereng.utils.text

import android.content.res.AssetManager
import java.lang.StringBuilder

class ChineseTextUtils(
    override val symbols: List<String>,
    override val cleanerName: String,
    override val assetManager: AssetManager
) : TextUtils {

    private val splitSymbols = listOf(
        ".", "。","……","!","！","?","？",";","；"
    )

    private val cleaner = ChineseCleaners()

    override fun cleanInputs(text: String): String {
        val cleaned = StringBuilder()
        for (s in text.split("\n")){
            if (s.isNotEmpty() && splitSymbols.contains(s.last().toString())){
                cleaned.append(s + "\n")
            }
        }
        return cleaned.toString()
    }

    override fun splitSentence(text: String): List<String> {
        return text.split("\n").filter { it.isNotEmpty() }
    }

    override fun wordsToLabels(text: String): IntArray {
        val labels = ArrayList<Int>()
        labels.add(0)

        // symbol to id
        val symbolToIndex = HashMap<String, Int>()
        symbols.forEachIndexed { index, s ->
            symbolToIndex[s] = index
        }

        // clean text
        var cleanedText = ""

        when (cleanerName){
            "chinese_cleaners" -> {
                cleanedText = cleaner.chinese_clean_text(text)
            }

            "chinese_cleaners1" -> {
                cleanedText = cleaner.chinese_clean_text(text)
            }

            "chinese_cleaners_moegoe" -> {
                cleanedText = cleaner.chinese_clean_text_moegoe(text)
            }
        }

        if (cleanedText.isEmpty()){
            throw RuntimeException("转换失败，请检查输入！")
        }

        // symbol to label
        for (symbol in cleanedText) {
            if (!symbols.contains(symbol.toString())) {
                continue
            }
            val label = symbolToIndex[symbol.toString()]
            if (label != null) {
                labels.add(label)
                labels.add(0)
            }
        }
        return labels.toIntArray()
    }

    override fun convertSentenceToLabels(
        text: String
    ): List<IntArray> {
        val sentences = splitSentence(text)
        val converted = ArrayList<IntArray>()

        for (sentence in sentences){
            if (sentence.length > 200){
                throw RuntimeException("句子长度不能超过200字！当前${sentence.length}字！")
            }
            if (sentence.isEmpty()) continue
            val labels = wordsToLabels(sentence)
            converted.add(labels)
        }
        return converted
    }

    override fun convertText(
        text: String
    ): List<IntArray> {
        // clean inputs
        val cleanedInputs = cleanInputs(text)

        // convert inputs
        return convertSentenceToLabels(cleanedInputs)
    }
}

