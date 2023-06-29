package com.example.moereng.utils.text

import android.content.res.AssetManager

class ZHJAMixCleaners(assetManager: AssetManager){
    private val chineseCleaners = ChineseCleaners()
    private val japaneseCleaners = JapaneseCleaners(assetManager)

    /**
     * 中日混合cleaner，仅支持日文模型的中文发音
     * */
    fun zh_ja_mixture_cleaners(inputs: String): String{
        var text = Regex("\\[ZH](.*?)\\[ZH]").replace(inputs) {
            chineseCleaners.chinese_to_romaji(it.groupValues[1]) + " "
        }
        text = Regex("\\[JA](.*?)\\[JA]").replace(text) {
            japaneseCleaners.japanese_to_romaji_with_accent(it.groupValues[1])
                .replace("ts", "ʦ")
                .replace("u", "ɯ")
                .replace("...", "…") + " "
        }
        if (text == inputs){
            throw RuntimeException("请检查输入，用[ZH]或[JA]区分中文和日文！")
        }
        text = Regex("\\s+$").replace(text, "")
        text = Regex("([^.,!?\\-…~])$").replace(text) { it.groupValues[1] + "." }
        return text
    }
}