package com.example.moereng.utils.text

import android.content.res.AssetManager

class ZHJAENMixCleaners(assetManager: AssetManager) {
    private val chineseCleaners = ChineseCleaners()
    private val japaneseCleaners = JapaneseCleaners(assetManager)
    private val englishCleaners = EnglishCleaners(assetManager)

    /**
     * 中日英混合cleaner
     * */
    fun zh_ja_en_mixture_cleaners(inputs: String): String{
        var text = Regex("\\[ZH](.*?)\\[ZH]").replace(inputs) {
            chineseCleaners.chinese_to_romaji(it.groupValues[1]) + " "
        }
        text = Regex("\\[JA](.*?)\\[JA]").replace(text) {
            japaneseCleaners.japanese_to_romaji_with_accent(it.groupValues[1])
                .replace("ts", "ʦ")
                .replace("u", "ɯ")
                .replace("...", "…") + " "
        }
        text = Regex("\\[EN](.*?)\\[EN]").replace(text) {
            englishCleaners.english_clean_text(it.groupValues[1]) + " "
        }
        if (text == inputs){
            throw RuntimeException("请检查输入，用[ZH]或[JA]区分中文和日文！")
        }
        text = Regex("\\s+$").replace(text, "")
        text = Regex("([^.,!?\\-…~])$").replace(text) { it.groupValues[1] + "." }
        return text
    }
}