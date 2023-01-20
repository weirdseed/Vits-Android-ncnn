package com.example.moereng.utils

import android.content.res.AssetManager
import com.example.moereng.data.Config

object VitsUtils {
    fun checkConfig(config: Config?, type: String): Boolean =
        when(type){
            "vc"-> config?.data?.n_speakers != null
                    && config.data.sampling_rate != null
            "multi" -> config?.data?.n_speakers != null
                    && config.data.sampling_rate != null
                    && !config.data.text_cleaners.isNullOrEmpty()
                    && !config.speakers.isNullOrEmpty()
                    && !config.symbols.isNullOrEmpty()
            "single" -> config?.data?.sampling_rate != null
                    && !config.data.text_cleaners.isNullOrEmpty()
                    && !config.symbols.isNullOrEmpty()
            else -> false
        }


    external fun testGpu(): Boolean

    external fun checkThreadsCpp(): Int

    external fun initOpenJtalk(assetManager: AssetManager): Boolean

    external fun words_split_cpp(text: String, assetManager: AssetManager): String

    external fun destroyOpenJtalk()

    init {
        System.loadLibrary("moereng")
    }
}