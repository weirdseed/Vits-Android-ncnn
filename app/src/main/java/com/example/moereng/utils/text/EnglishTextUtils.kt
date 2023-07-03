package com.example.moereng.utils.text

import android.content.res.AssetManager

class EnglishTextUtils(override val symbols: List<String>,
                       override val cleanerName: String,
                       override val assetManager: AssetManager) :
    BaseTextUtils(symbols, cleanerName, assetManager) {

    private val cleaner = EnglishCleaners(assetManager)

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
            "english_cleaners" -> {
                cleanedText = cleaner.english_clean_text(text)
            }

            "english_cleaners1" -> {
                cleanedText = cleaner.english_clean_text(text)
            }

            "english_cleaners2" -> {
                cleanedText = cleaner.english_clean_text2(text)
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
}