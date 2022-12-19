package com.example.moereng

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.moereng.databinding.ActivityMainBinding
import com.example.moereng.utils.Cleaner
import com.example.moereng.utils.Player
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var japanese_cleaner: Cleaner

    private var module: Module? = null

    private lateinit var tracker: AudioTrack

    private fun initOpenjtalk(assetManager: AssetManager){
        InitOpenJtalk(assetManager)
    }

    // 判断是否处理完毕
    private var flag = true

    private fun processWords(text: String) {
        flag = false
        val seq = japanese_cleaner.text_to_sequence(text)

        val shape1 = longArrayOf(1, seq.size.toLong())
        val inputTensor = Tensor.fromBlob(seq.toLongArray(), shape1)

        // 运行推理
        val output = module?.forward(IValue.from(inputTensor))?.toTensor()?.dataAsFloatArray

        if (output != null){
            tracker.write(output, 0, output.size, AudioTrack.WRITE_BLOCKING)
            tracker.play()
            if (tracker.state == AudioTrack.PLAYSTATE_STOPPED){
                flag = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化openjtalk模型
        initOpenjtalk(assets);
        japanese_cleaner = Cleaner()
        tracker = Player.buildTracker()

        // 加载模型
        try {
            module = LiteModuleLoader.load(assetFilePath(this,"net_g.ptl"))
            Log.d("MainActivity","model loaded!")
        } catch (e: IOException){
            Log.e("Main",e.toString())
        }

        binding.playBtn.setOnClickListener {
            val inputText = binding.wordsInput.text
            if (inputText != null && module != null) {
                if (inputText.length > 0){
                    // 处理完毕
                    if (flag){
                        // 建立线程
                        thread {
                            processWords(inputText.toString())
                        }
                    } else {
                        Toast.makeText(this, "别急。。。", Toast.LENGTH_SHORT).show()
                    }

                } else{
                    Toast.makeText(this, "请输入文字", Toast.LENGTH_SHORT).show()
                }
            }

        }
    }

    override fun onPause() {
        super.onPause()
        tracker.pause()
    }

    override fun onStop() {
        super.onStop()
        tracker.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("MainActivity","destroyed!")
        tracker.release()
        module = null
        DestroyOpenJtalk()
    }

    @Throws(IOException::class)
    fun assetFilePath(context: Context, assetName: String): String? {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        context.assets.open(assetName).use { `is` ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
            return file.absolutePath
        }
    }

    /**
     * A native method that is implemented by the 'moereng' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String
    external fun InitOpenJtalk(assetManager: AssetManager)
    external fun DestroyOpenJtalk()
    companion object {
        // Used to load the 'moereng' library on application startup.
        init {
            System.loadLibrary("moereng")
        }
    }
}