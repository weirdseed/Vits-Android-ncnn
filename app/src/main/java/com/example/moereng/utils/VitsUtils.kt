package com.example.moereng.utils

import com.example.moereng.data.Config

object VitsUtils {
    fun checkConfig(config: Config?, type: String): Boolean =
        when{
            (type == "multi" || type == "vc") ->
                config?.data?.n_speakers != null
                    && config.data.sampling_rate != null
                    && !config.data.text_cleaners.isNullOrEmpty()
                    && !config.speakers.isNullOrEmpty()
                    && !config.symbols.isNullOrEmpty()
            (type == "single") -> config?.data?.sampling_rate != null
                    && !config.data.text_cleaners.isNullOrEmpty()
                    && !config.symbols.isNullOrEmpty()
            else -> false
        }

    external fun testGpu(): Boolean

    external fun checkThreadsCpp(): Int

    init {
        System.loadLibrary("moereng")
    }
}