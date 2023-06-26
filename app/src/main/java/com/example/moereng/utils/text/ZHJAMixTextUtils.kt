package com.example.moereng.utils.text

import android.content.res.AssetManager

class ZHJAMixTextUtils(
    override val symbols: List<String>,
    override val cleanerName: String,
    override val assetManager: AssetManager
) : TextUtils {

    private val splitSymbols = listOf(
        ".", "。","……","!","！","?","？",";","；"
    )

    private val cleaner = ZHJAMixCleaners(assetManager)

    override fun cleanInputs(text: String): String {
        var tempText = text
        splitSymbols.forEach {
            tempText = tempText.replace(it, "${it}\n")
        }
        return tempText
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

        when(cleanerName){
            "zh_ja_mixture_cleaners"-> {
                cleanedText = cleaner.zh_ja_mixture_cleaners(text)
            }
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

    override fun convertSentenceToLabels(text: String): List<IntArray> {
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

    override fun convertText(text: String): List<IntArray> {
        // clean inputs
        val cleanedInputs = cleanInputs(text)

        // convert inputs
        return convertSentenceToLabels(cleanedInputs)
    }
}