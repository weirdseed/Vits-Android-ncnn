package com.example.moereng.utils

import android.content.res.AssetManager
import java.text.Normalizer

class Cleaner {
    private val _symbols_to_japanese : Map<String, String> = mapOf(
        "％" to "パーセント",
        "%" to "パーセント"
    )
    private val _japanese_marks = Regex("[^A-Za-z\\d\\u3005\\u3040-\\u30ff\\u4e00-\\u9fff\\uff11-\\uff19\\uff21-\\uff3a\\uff41-\\uff5a\\uff66-\\uff9d]")
    private val _japanese_characters = Regex("[A-Za-z\\d\\u3005\\u3040-\\u30ff\\u4e00-\\u9fff\\uff11-\\uff19\\uff21-\\uff3a\\uff41-\\uff5a\\uff66-\\uff9d]")
    private val reng_symbols = listOf(
        "_", ",", ".", "!", "?", "-", "~", "\u2026", "A", "E", "I", "N", "O", "Q", "U", "a", "b", "d", "e", "f", "g", "h", "i", "j", "k", "m", "n", "o", "p", "r", "s", "t", "u", "v", "w", "y", "z", "\u0283", "\u02a7", "\u02a6", "\u2193", "\u2191", " "
    )
    private fun symbols_to_japanese(text: String): String{
        var result = text
        for (s in _symbols_to_japanese){
            val pattern = Regex(s.key)
            result = pattern.replace(result, s.value)
        }
        return result
    }
    private fun japanese_to_romaji_with_accent(_text: String): String{
        var text = symbols_to_japanese(_text)
        val sentences = text.split(_japanese_marks)
        val marks = ArrayList<String>()
        _japanese_marks.findAll(text).forEach {
            marks.add(it.value)
        }
        text = ""
        for (i in 0..sentences.size-1){
            val sentence = sentences[i]
            if (_japanese_characters.find(sentence)?.value != null){
                if (text != ""){
                    text += " "
                }
                val labels = extract_labels(sentence)
                for(n in 0..labels.size-1){
                    val label = labels[n]
                    val phoneme = Regex("\\-([^\\+]*)\\+").find(label)!!.groups[1]!!.value
                    if (phoneme != "sil" && phoneme != "pau"){
                        text += phoneme.replace("ch","ʧ").replace("sh","ʃ").replace("cl","Q")
                    } else{
                        continue
                    }
                    val a1 = Regex("/A:(\\-?[0-9]+)\\+").find(label)!!.groups[1]!!.value.toInt()
                    val a2 = Regex("\\+(\\d+)\\+").find(label)!!.groups[1]!!.value.toInt()
                    val a3 = Regex("\\+(\\d+)/").find(label)!!.groups[1]!!.value.toInt()
                    var a2_next = 0
                    val t = Regex("\\-([^\\+]*)\\+").find(labels[n+1])!!.groups[1]!!.value
                    if (t == "sil" || t == "pau"){
                        a2_next = -1
                    } else{
                        a2_next = Regex("\\+(\\d+)\\+").find(labels[n+1])!!.groups[1]!!.value.toInt()
                    }
                    // Accent phrase boundary
                    if (a3 == 1 && a2_next == 1){
                        text += ' '
                    } else if(a1 == 0 && a2_next == a2 + 1){
                        text += '↓'
                    } else if(a2 == 1 && a2_next == 2){
                        text += '↑'
                    }
                }
            }
            if (i < marks.count()){
                val normalized = Normalizer.normalize(marks[i], Normalizer.Form.NFD)
                text += normalized.replace(" ","")
            }
        }
        return text
    }

    private fun clean_text(text: String): String{
        val _text = japanese_to_romaji_with_accent(text)
        val cleaned1 = _text.replace(Regex("([A-Za-z])\$"),"$1.")
        var cleaned2 = cleaned1.replace("ts", "ʦ")
            .replace("...","…")
            .replace("、",",")
        val brackets = listOf(
            "(",")","{","}","[","]","<",">","*"
        )
        for (b in brackets){
            if (cleaned2.contains(b)){
                cleaned2 = cleaned2.replace(b,"")
            }
        }
        return cleaned2
    }

    fun text_to_sequence(text: String, addblank: Boolean = true): List<Long>{
        val sequence = ArrayList<Long>()
        if (addblank){
            sequence.add(0L)
        }
        val _symbol_to_id = HashMap<String, Long>()
        for (i in 0..reng_symbols.size-1){
            _symbol_to_id[reng_symbols[i]] = i.toLong()
        }
        val cleaned_text = clean_text(text)
        for(symbol in cleaned_text){
            if (!reng_symbols.contains(symbol.toString())){
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

    // 生成labels
    external fun extract_labels(text: String): List<String>
}