package com.example.moereng.utils.text

import android.content.res.AssetManager
import java.lang.StringBuilder

abstract class BaseTextUtils(
    override val symbols: List<String>,
    override val cleanerName: String,
    override val assetManager: AssetManager
) : TextUtils {

    private val splitSymbols = listOf(
        ".", "。","……","!","！","?","？",";","；"
    )

    override fun cleanInputs(text: String): String {
        val cleaned = StringBuilder()
        for (s in text.split("\n")){
            if (s.isNotEmpty() && splitSymbols.contains(s.last().toString())){
                cleaned.append(s + "\n")
            } else {
                cleaned.append(s)
            }
        }
        return cleaned.toString()
    }

    override fun splitSentence(text: String): List<String> {
        return text.split("\n").filter { it.isNotEmpty() }
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