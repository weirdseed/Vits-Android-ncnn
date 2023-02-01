package com.example.moereng.utils

import android.content.res.AssetManager
import com.example.moereng.utils.text.ChineseTextUtils
import com.example.moereng.utils.text.JapaneseTextUtils

object TextUtils {

    private val supportedCleaners = listOf(
        "chinese_cleaners",
        "japanese_cleaners",
        "japanese_cleaners2"
    )

    fun processInputs(
        text: String,
        cleanerName: String,
        symbols: List<String>,
        assetManager: AssetManager
    ): List<IntArray>? {
        // check cleaner
        if (cleanerName !in supportedCleaners) {
            throw RuntimeException("暂不支持${cleanerName}!")
        }

        var result: List<IntArray>? = null

        // convert inputs
        when (cleanerName) {
            "japanese_cleaners" -> {
                result = JapaneseTextUtils.convertText(text, cleanerName, symbols, assetManager)
            }
            "japanese_cleaners2" -> {
                result = JapaneseTextUtils.convertText(text, cleanerName, symbols, assetManager)
            }
            "chinese_cleaners" -> {
                result = ChineseTextUtils.convertText(text, cleanerName, symbols)
            }
        }

        return result
    }

}