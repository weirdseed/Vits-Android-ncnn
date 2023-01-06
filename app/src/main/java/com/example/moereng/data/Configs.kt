package com.example.moereng.data

data class Configs(val data: MData, val symbols: List<String>, val speakers: List<String>) {
    data class MData(val text_cleaners: List<String>, val n_speakers: Int)
}
