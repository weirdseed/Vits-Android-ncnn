package com.example.moereng.utils

import com.example.moereng.utils.cleaners.ChineseCleaners
import com.example.moereng.utils.cleaners.JapaneseCleaners

class TextUtils {

    fun text_to_sequence(
        text: String,
        addblank: Boolean = true,
        symbols: List<String>,
        cleaner: String
    ): List<Int> {
        val sequence = ArrayList<Int>()
        if (addblank) {
            sequence.add(0)
        }
        val _symbol_to_id = HashMap<String, Int>()
        for (i in 0..symbols.size - 1) {
            _symbol_to_id[symbols[i]] = i
        }
        var cleaned_text = ""

        // check cleaners
        when(cleaner){
            "japanese_cleaners"->{
                val japaneseCleaners = JapaneseCleaners()
                cleaned_text = japaneseCleaners.japanese_clean_text1(text)
            }
            "japanese_cleaners2"->{
                val japaneseCleaners = JapaneseCleaners()
                cleaned_text = japaneseCleaners.japanese_clean_text2(text)
            }
            "chinese_cleaners"->{
                val chineseCleaners = ChineseCleaners()
                cleaned_text = chineseCleaners.chinese_clean_text1(text)
            }
        }

        for (symbol in cleaned_text) {
            if (!symbols.contains(symbol.toString())) {
                continue
            }
            val symbol_id = _symbol_to_id[symbol.toString()]
            if (symbol_id != null) {
                sequence.add(symbol_id)
                if (addblank) sequence.add(0)
            }
        }

        return sequence
    }


}