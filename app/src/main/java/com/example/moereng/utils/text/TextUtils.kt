package com.example.moereng.utils.text

import android.content.Context
import android.content.res.AssetManager

interface TextUtils {

    val symbols: List<String>
    val cleanerName: String
    val assetManager: AssetManager

    fun cleanInputs(text: String): String

    fun splitSentence(text: String): List<String>

    fun wordsToLabels(text: String): IntArray

    fun convertSentenceToLabels(
        text: String,
        context: Context
    ): List<IntArray>

    fun convertText(
        text: String,
        context: Context
    ): List<IntArray>

}