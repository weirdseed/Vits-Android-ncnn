package com.example.moereng.utils.text

import android.content.res.AssetManager
import android.util.Log

object JapaneseTextUtils {
    private var openJtalkInitialized = false

    private fun initDictionary(assetManager: AssetManager) {
        // init openjatlk
        if (!openJtalkInitialized) {
            openJtalkInitialized = initOpenJtalk(assetManager)
            if (!openJtalkInitialized) {
                throw RuntimeException("初始化openjtalk字典失败！")
            }
            Log.i("TextUtils", "Openjtalk字典初始化成功！")
        }
    }

    private fun cleanInputs(text: String): String {
        return text.replace("\"", "").replace("\'", "")
            .replace("\t", " ").replace("\n", "、")
            .replace("”", "")
    }

    private fun wordsToLabels(text: String, symbols: List<String>, cleanerName: String): IntArray {
        val labels = ArrayList<Int>()
        labels.add(0)

        // symbol to id
        val symbolToIndex = HashMap<String, Int>()
        symbols.forEachIndexed { index, s ->
            symbolToIndex[s] = index
        }

        // clean text
        var cleanedText = ""

        if (cleanerName == "japanese_cleaners") {
            val cleaner = JapaneseCleaners()
            cleanedText = cleaner.japanese_clean_text1(text)
        } else if (cleanerName == "japanese_cleaners2") {
            val cleaner = JapaneseCleaners()
            cleanedText = cleaner.japanese_clean_text2(text)
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

    private fun convertSentenceToLabels(
        text: String,
        symbols: List<String>,
        cleanerName: String
    ): List<IntArray> {
        val sentenceSplitted = splitSentenceCpp(text)
        var sentences = sentenceSplitted.replace("EOS\n", "").split("\n")
        sentences = sentences.subList(0, sentences.size - 1)

        val outputs = ArrayList<IntArray>()

        var sentence = ""
        for (i in sentences.indices) {
            val s = sentences[i]
            sentence += s.split("\t")[0]
            if (s.contains("記号,読点") ||
                s.contains("記号,句点") ||
                s.contains("記号,一般") ||
                s.contains("記号,空白") ||
                i == sentences.size - 1
            ) {
                if (sentence.length > 100) {
                    throw RuntimeException("句子过长")
                }
                val labels = wordsToLabels(sentence, symbols, cleanerName)
                if (labels.isEmpty() || labels.sum() == 0)
                    continue
                outputs.add(labels)
                sentence = ""
            }
        }
        return outputs
    }

    fun convertText(
        text: String,
        cleanerName: String,
        symbols: List<String>,
        assetManager: AssetManager
    ): List<IntArray> {
        // init dict
        initDictionary(assetManager)

        // clean inputs
        val cleanedInputs = cleanInputs(text)

        // convert inputs
        return convertSentenceToLabels(cleanedInputs, symbols, cleanerName)
    }

    external fun initOpenJtalk(assetManager: AssetManager): Boolean

    external fun splitSentenceCpp(text: String): String

    init {
        System.loadLibrary("moereng")
    }
}