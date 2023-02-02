package com.example.moereng.utils.audio

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.moereng.databinding.FragmentTtsBinding
import com.example.moereng.databinding.FragmentVcBinding
import java.lang.Math.min
import kotlin.concurrent.thread

class PlayerUtils {
    private var audioTrack: AudioTrack? = null

    var isPlaying = false

    private var sampleRate = 22050 // default sample rate

    private var channels = AudioFormat.CHANNEL_OUT_MONO // default channels

    private var audioFormat = AudioFormat.ENCODING_PCM_FLOAT

    private var bufferSize = 0

    private var audio: FloatArray? = null

    private val chunkSize = 1000

    fun setTrackData(sr: Int, ch: Int) {
        if (ch == 2) channels = AudioFormat.CHANNEL_OUT_STEREO
        if (ch > 2 || ch < 0) throw Exception("不支持的通道数$ch！")
        if (sampleRate <= 0) throw Exception("不支持的采样率$sr！")
        sampleRate = sr
        Log.i("AudioTrack", "sampling rate:$sr channels:$ch")
    }

    fun createAudioTrack(audioArray: FloatArray) {
        bufferSize = AudioTrack.getMinBufferSize(sampleRate, channels, audioFormat)
        if (bufferSize <= 0) throw Exception("AudioTrack不可用！")
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setChannelMask(channels)
                    .setSampleRate(sampleRate).build()
            )
            .setBufferSizeInBytes(bufferSize).build()
        // set audio data
        audio = audioArray
        Log.i("AudioTrack", "created!")
    }

    fun start(activity: Activity, binding: Any, from: String) {
        if (!isPlaying) {
            audioTrack?.play()
            Log.i("AudioTrack", "start playing!")
            isPlaying = true
            when(from){
                "tts"-> (binding as FragmentTtsBinding).playBtn.text = "停止"
                "vc" -> (binding as FragmentVcBinding).playBtn.text = "停止"
            }
            thread {
                writeChunk()
                activity.runOnUiThread{
                    when(from){
                        "tts"-> (binding as FragmentTtsBinding).playBtn.text = "播放"
                        "vc" -> (binding as FragmentVcBinding).playBtn.text = "播放"
                    }
                    isPlaying = false
                }
            }
        }
    }

    private fun writeChunk(){
        if (audio != null){
            for (chunkId in 0 until audio!!.size step chunkSize){
                val end = min(chunkId+chunkSize, audio!!.size)
                val slicedAudio =  audio!!.sliceArray(chunkId until end)
                audioTrack!!.write( slicedAudio, 0, chunkSize, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    fun stop(binding: Any, from: String) {
        if (isPlaying) {
            audioTrack?.stop()
            when(from){
                "tts"-> (binding as FragmentTtsBinding).playBtn.text = "播放"
                "vc" -> (binding as FragmentVcBinding).playBtn.text = "播放"
            }
            isPlaying = false
        }
    }

    fun release(binding: Any, from: String) {
        stop(binding, from)
        audioTrack?.release()
        audio = null
    }
}