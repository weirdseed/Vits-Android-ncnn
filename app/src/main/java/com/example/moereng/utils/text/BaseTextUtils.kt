package com.example.moereng.utils.text

import android.app.ActivityManager
import android.content.Context
import android.content.res.AssetManager
import android.text.format.Formatter
import android.util.Log


abstract class BaseTextUtils(
    override val symbols: List<String>,
    override val cleanerName: String,
    override val assetManager: AssetManager,
) : TextUtils {

    private val splitSymbols = listOf(
        ".", "。","……","!","！","?","？",";","；","~","—"
    )

    private val memSizeLevel = mapOf(
        "low" to 50,
        "medium" to 100,
        "high" to 200
    )

    override fun cleanInputs(text: String): String {
        var cleaned = text
        if (text.isNotEmpty() && splitSymbols.contains(text.last().toString())){
            cleaned = text + "\n"
        }
        return cleaned
    }

    override fun splitSentence(text: String): List<String> {
        return text.split("\n").filter { it.isNotEmpty() }
    }

    override fun convertSentenceToLabels(text: String, context: Context): List<IntArray> {
        val sentences = splitSentence(text)
        val converted = ArrayList<IntArray>()
        val validMemSize = getAvailMemory(context)
        Log.i("BaseTextUtils", "valid memory size is $validMemSize")
        val maxSentenceLen = dynamicSentenceLength(validMemSize)
        for (i in sentences.indices){
            if (sentences[i].length > maxSentenceLen){
                throw RuntimeException("每句话不能超过${maxSentenceLen}字！" +
                        "当前第${i+1}句有${sentences[i].length}字！")
            }
            if (sentences[i].isEmpty()) continue
            val labels = wordsToLabels(sentences[i])
            converted.add(labels)
        }
        return converted
    }

    override fun convertText(text: String, context: Context): List<IntArray> {
        // clean inputs
        val cleanedInputs = cleanInputs(text)

        // convert inputs
        return convertSentenceToLabels(cleanedInputs, context)
    }

    private fun getAvailMemory(context: Context): String{
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return Formatter.formatFileSize(context, mi.availMem)
    }

    private fun dynamicSentenceLength(validMemSize: String): Int{
        val pattern = "(\\d+\\.\\d+|\\d+)(?=\\s*[a-zA-Z]+)".toRegex()
        val matched = pattern.find(validMemSize) ?: throw RuntimeException("invalid memType!")
        val sizeAndUnit = matched.destructured.toList()
        val memSize = sizeAndUnit[0].toFloat()
        val unit = validMemSize.replace(memSize.toString(), "").replace(" ", "")
        var level = "low"
        if (unit == "MB"){
            level = "low"
        } else if (unit == "GB"){
            when{
                (memSize in 0.0..2.0) -> {
                    level = "low"
                }
                (memSize in 2.0..4.0)->{
                    level = "medium"
                }
                (memSize > 4.0) -> {
                    level = "high"
                }
            }
        }
        return memSizeLevel[level]!!
    }
}