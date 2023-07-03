package com.example.moereng.utils.text

import android.content.res.AssetManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Reference from https://github.com/mphilli/English-to-IPA
class Eng2IPA(val assetManager: AssetManager) {
    private var cmuDict: Map<String, List<String>>? = null

    init {
        val gson = Gson()
        val jsonString = assetManager.open("english_dicts/CMU_dict.json").bufferedReader().use {
            it.readText()
        }
        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        cmuDict = gson.fromJson<Map<String, List<String>>>(jsonString, type)
    }

    private val symbols = mapOf(
        "a" to "ə", "ey" to "eɪ", "aa" to "ɑ", "ae" to "æ", "ah" to "ə", "ao" to "ɔ",
        "aw" to "aʊ", "ay" to "aɪ", "ch" to "ʧ", "dh" to "ð", "eh" to "ɛ", "er" to "ər",
        "hh" to "h", "ih" to "ɪ", "jh" to "ʤ", "ng" to "ŋ", "ow" to "oʊ", "oy" to "ɔɪ",
        "sh" to "ʃ", "th" to "θ", "uh" to "ʊ", "uw" to "u", "zh" to "ʒ", "iy" to "i", "y" to "j"
    )

    private val PHONES = mapOf(
        "aa" to "vowel", "ae" to "vowel", "ah" to "vowel", "ao" to "vowel",
        "aw" to "vowel", "ay" to "vowel", "b" to "stop", "ch" to "affricate", "d" to "stop",
        "dh" to "fricative", "eh" to "vowel", "er" to "vowel", "ey" to "vowel", "f" to "fricative",
        "g" to "stop", "hh" to "aspirate", "ih" to "vowel", "iy" to "vowel", "jh" to "affricate",
        "k" to "stop", "l" to "liquid", "m" to "nasal", "n" to "nasal", "ng" to "nasal",
        "ow" to "vowel", "oy" to "vowel", "p" to "stop", "r" to "liquid", "s" to "fricative",
        "sh" to "fricative", "t" to "stop", "th" to "fricative", "uh" to "vowel", "uw" to "vowel",
        "v" to "fricative", "w" to "semivowel", "y" to "semivowel", "z" to "fricative",
        "zh" to "fricative"
    )

    private val hiatus = listOf(
        listOf("er", "iy"), listOf("iy", "ow"),
        listOf("uw", "ow"), listOf("iy", "ah"),
        listOf("iy", "ey"), listOf("uw", "eh"),
        listOf("er", "eh")
    )

    private fun preprocess(wordIn: String): String {
        val punctStr = "!\"#$%&'()*+,-./:;<=>/?@[\\]^_`{|}~«» "
        var words = wordIn
        for (punc in punctStr) {
            words = words.trim(punc).lowercase()
        }
        return words.split(" ").joinToString(" ")
    }

    private fun preservePunc(wordIn: String): List<List<String>> {
        val wordsPreserved = ArrayList<List<String>>()
        for (w in wordIn.split(" ")) {
            val punctList = mutableListOf("", preprocess(w), "")
            val before = Regex("^([^A-Za-z0-9]+)[A-Za-z]").find(w)
            val after = Regex("[A-Za-z]([^A-Za-z0-9]+)$").find(w)
            if (before != null) {
                punctList[0] = before.groupValues[1]
            }
            if (after != null) {
                punctList[2] = after.groupValues[1]
            }
            wordsPreserved.add(punctList)
        }
        return wordsPreserved
    }

    private fun puncReplaceWords(
        words: List<List<String>>,
        ipa: List<List<String>>
    ): List<List<String>> {
        val transcription = ipa.toMutableList()
        for (i in transcription.indices) {
            val transList = transcription[i].toMutableList()
            for (j in transList.indices) {
                val triple = listOf(words[i][0], transList[j], words[i][2])
                val trans = transcription[i].toMutableList()
                trans[j] = triple.joinToString("")
                transcription[i] = trans
            }
        }
        return transcription
    }

    private fun fetchWords(wordsIn: List<String>) =
        cmuDict?.filter { (k, _) -> k in wordsIn }?.map { mapOf(it.key to it.value) }
            ?: emptyList()

    private fun getCMU(wordsIn: List<String>): List<List<String>> {
        val result: List<Map<String, List<String>>> = fetchWords(wordsIn)
        return wordsIn.map { word ->
            result.mapNotNull { it[word] }.flatten().ifEmpty { listOf("__IGNORE__$word") }
        }
    }

    private fun cmuSyllableCount(wordIn: String): Int {
        val word = wordIn.replace("\\d".toRegex(), "").split(" ")
        if ("__IGNORE__" in word[0]) {
            return 0
        } else {
            var nuclei = 0
            for (i in word.indices) {
                val sym = word[i]
                val wordIndex = if (i - 1 == -1) word.size - 1 else i - 1
                val prePhone = PHONES[word[wordIndex]]
                val preSym = word[wordIndex]
                if (PHONES[sym] == "vowel") {
                    if (i > 0 && prePhone != "vowel" || i == 0) {
                        nuclei += 1
                    } else if (listOf(preSym, sym) in hiatus) {
                        nuclei += 1
                    }
                }
            }
            return nuclei
        }
    }

    private fun findStress(wordIn: String): String {
        val syllCount = cmuSyllableCount(wordIn)
        if (!wordIn.startsWith("__IGNORE__") && syllCount > 1) {
            val splitSymbols = wordIn.split(" ")
            val stressMap = mapOf("1" to "ˈ", "2" to "ˌ")
            val newWord = ArrayList<String>()
            val clusters = listOf("sp", "st", "sk", "fr", "fl")
            val stopSet = listOf("nasal", "fricative", "vowel")
            for (c in splitSymbols) {
                if (c.last().toString() in stressMap.keys) {
                    if (newWord.isEmpty()) {
                        val stressKey = Regex("\\d").findAll(c).toList()[0].value
                        val stress = stressMap[stressKey] + c
                        newWord.add(Regex("\\d").replace(stress, ""))
                    } else {
                        val stressMark = stressMap[c.last().toString()]
                        var placed = false
                        var hiatus = false
                        var newWordReversed = newWord.reversed()
                        newWord.clear()
                        newWord.addAll(newWordReversed)
                        for (i in newWord.indices) {
                            val sym = newWord[i]
                            val wordIndex = if (i - 1 == -1) newWord.size - 1 else i - 1
                            val newSym = sym.replace("[0-9ˈˌ]".toRegex(), "")
                            val prevSym = newWord[wordIndex].replace("[0-9ˈˌ]".toRegex(), "")
                            val prevPhone =
                                PHONES[newWord[wordIndex].replace("[0-9ˈˌ]".toRegex(), "")]
                            if (PHONES[newSym] in stopSet
                                || i > 0 && prevPhone == "stop"
                                || newSym in arrayOf("er", "w", "j")
                            ) {
                                if (newSym + prevSym in clusters) {
                                    newWord[i] = stressMark + newWord[i]
                                } else if (!(prevPhone == "vowel") && i > 0) {
                                    newWord[wordIndex] = stressMark + newWord[wordIndex]
                                } else {
                                    if (PHONES[newSym] == "vowel") {
                                        hiatus = true
                                        val prevNewWords = newWord.toList()
                                        newWord.clear()
                                        newWord.add(
                                            stressMark + newSym.replace(
                                                "[0-9ˈˌ]".toRegex(),
                                                ""
                                            )
                                        )
                                        newWord.addAll(prevNewWords)
                                    } else {
                                        newWord[i] = stressMark + newWord[i]
                                    }
                                }
                                placed = true
                                break
                            }
                        }
                        if (!placed && newWord.isNotEmpty()) {
                            newWord[newWord.size - 1] = stressMark + newWord.last().toString()
                        }
                        newWordReversed = newWord.reversed()
                        newWord.clear()
                        newWord.addAll(newWordReversed)
                        if (!hiatus) {
                            newWord.add(Regex("\\d").replace(c, ""))
                        }
                    }
                } else {
                    if (c.startsWith("__IGNORE__")) {
                        newWord.add(c)
                    } else {
                        newWord.add(Regex("\\d").replace(c, ""))
                    }
                }
            }
            return newWord.joinToString(" ")
        } else {
            if (wordIn.startsWith("__IGNORE__")) {
                return wordIn
            } else {
                return Regex("[0-9]").replace(wordIn, "")
            }
        }
    }

    private fun CMU2IPA(cmuList: List<List<String>>): List<List<String>> {
        val ipaList = ArrayList<List<String>>()
        for (wordList in cmuList) {
            val ipaWordList = ArrayList<String>()
            for (w in wordList) {
                val word = findStress(w)
                var ipaForm = ""
                if (word.startsWith("__IGNORE__")) {
                    ipaForm = word.replace("__IGNORE__", "")
                    if (Regex("\\d*").replace(ipaForm, "") != "") {
                        ipaForm += "*"
                    }
                } else {
                    var mark = ""
                    for (piece in word.split(" ")) {
                        var marked = false
                        var unmarked = piece
                        if (piece[0].toString() in listOf("ˈ", "ˌ")) {
                            marked = true
                            mark = piece[0].toString()
                            unmarked = piece.substring(1)
                        }
                        if (unmarked in symbols) {
                            ipaForm += if (marked) mark + symbols[unmarked] else symbols[unmarked]
                        } else {
                            ipaForm += piece
                        }
                    }
                }
                val swapList = listOf(listOf("ˈər", "əˈr"), listOf("ˈie", "iˈe"))
                for (sym in swapList) {
                    if (!ipaForm.startsWith(sym[0])) {
                        ipaForm = ipaForm.replace(sym[0], sym[1])
                    }
                }
                ipaWordList.add(ipaForm)
            }
            ipaList.add(ipaWordList.toSet().toList().sorted())
        }
        return ipaList
    }

    private fun getIPA(wordsIn: List<String>): List<List<String>> {
        val words = wordsIn.map { w ->
            preservePunc(w.lowercase())[0]
        }
        val cmuWordsIn = words.map { w -> w[1] }
        val cmu = getCMU(cmuWordsIn)
        val ipa = CMU2IPA(cmu)
        return puncReplaceWords(words, ipa)
    }

    fun convert(text: String): String {
        val words = text.split(" ")
        val ipa = getIPA(words)
        return ipa.joinToString(" ") { wordList -> wordList.last() }
    }
}
