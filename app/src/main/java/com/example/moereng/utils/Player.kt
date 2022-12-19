package com.example.moereng.utils

import android.media.AudioAttributes
import android.media.AudioFormat

import android.media.AudioTrack




object Player {
    fun buildTracker(sr: Int = 22050): AudioTrack {
        val channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat: Int = AudioFormat.ENCODING_PCM_FLOAT
        val bufferSize = AudioTrack.getMinBufferSize(sr, channelConfig, audioFormat)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().
                setUsage(AudioAttributes.USAGE_MEDIA).
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(audioFormat)
                .setChannelMask(channelConfig)
                .setSampleRate(sr).build())
            .setBufferSizeInBytes(bufferSize).build()
        return audioTrack
    }
}