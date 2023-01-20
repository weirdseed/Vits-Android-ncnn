package com.example.moereng.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log


class RecordingUtils {
    private var recorder: AudioRecord? = null

    private val channels = AudioFormat.CHANNEL_IN_MONO

    private val audioFormat = AudioFormat.ENCODING_PCM_FLOAT

    private val sampleRate = 22050

    var isRecording = false

    private var audio: FloatArray? = null

    private var minBufferSize = 0

    private var initialized = false

    @SuppressLint("MissingPermission")
    fun initRecorder() {
        if (!initialized){
            minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channels, audioFormat)
            if (minBufferSize < 0) throw Exception("AudioRecorder不可用！")
            Log.d("RecordingUtils", "buffer size = $minBufferSize")
            recorder = AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                sampleRate,
                channels,
                audioFormat,
                minBufferSize
            )
            initialized = true
        }
        isRecording = true
    }

    fun record(): FloatArray? {
        audio = FloatArray(minBufferSize)
        // begin recording
        recorder?.startRecording()
        recorder?.read(audio!!, 0, minBufferSize, AudioRecord.READ_BLOCKING)
        return audio?.map { it * 10 }?.toFloatArray()
    }

    fun stop(){
        if (isRecording){
            recorder?.stop()
            isRecording = false
        }
    }

    fun release(){
        stop()
        recorder?.release()
        recorder = null
    }
}