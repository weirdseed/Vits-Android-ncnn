package com.example.moereng

import android.content.res.AssetManager

class Vits {
    external fun init_vits(assetManager: AssetManager, path: String, voice_convert: Boolean, multi: Boolean, num_threads: Int): Boolean
    external fun forward(
        x: IntArray,
        vulkan: Boolean,
        multi: Boolean,
        sid: Int,
        noise_scale: Float,
        noise_scale_w: Float,
        length_scale: Float,
        num_threads: Int
    ): FloatArray?

    external fun voice_convert(
        audio: FloatArray, raw_sid: Int, target_sid: Int,
        vulkan: Boolean, num_threads: Int
    ): FloatArray

    external fun destroy()

    companion object {
        init {
            System.loadLibrary("moereng")
        }
    }
}