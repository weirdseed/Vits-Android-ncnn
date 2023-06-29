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
        ".", "。","……","!","！","?","？",";","；"
    )

    private val memSizeLevel = mapOf(
        "low" to 50,
        "medium" to 100,
        "high" to 200
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

    override fun convertSentenceToLabels(text: String, context: Context): List<IntArray> {
        val sentences = splitSentence(text)
        val converted = ArrayList<IntArray>()
        val validMemSize = getAvailMemory(context)
        Log.i("BaseTextUtils", "valid memory size is $validMemSize")
        val maxSentenceLen = dynamicSentenceLength(validMemSize)
        for (i in sentences.indices){
            if (sentences[i].length > maxSentenceLen){
                throw RuntimeException("句子长度不能超过${maxSentenceLen}字！" +
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
        val sizeAndUnit = validMemSize.split(" ")
        val memSize = sizeAndUnit[0].toFloat()
        val unit = sizeAndUnit[1]
        var level = "low"
        if (unit == "MB"){
            level = "low"
        } else if (unit == "GB"){
            if (memSize in 1.0..2.0){
                level = "medium"
            }
            if (memSize > 2.0){
                level = "high"
            }
        }
        return memSizeLevel[level]!!
    }
}