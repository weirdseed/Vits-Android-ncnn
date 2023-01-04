package com.example.moereng

import android.content.res.AssetManager

class Vits {
    external fun init_vits(assetManager: AssetManager, path: String): Boolean
    external fun forward(x: IntArray, vulkan: Boolean, sid: Int, noise_scale: Float, length_scale: Float):FloatArray
    external fun destroy()
    companion object {
        init {
            System.loadLibrary("moereng")
        }
    }
}