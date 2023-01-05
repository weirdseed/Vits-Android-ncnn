package com.example.moereng

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Environment.DIRECTORY_DOWNLOADS
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.NumberPicker
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.moereng.data.Configs
import com.example.moereng.databinding.ActivityMainBinding
import com.example.moereng.utils.Cleaner
import com.example.moereng.utils.ModelFileUtils
import com.example.moereng.utils.ModelFileUtils.getPathFromUri
import com.example.moereng.utils.Player
import java.io.File
import java.io.IOException
import java.lang.Integer.min
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var japanese_cleaner: Cleaner

    private var module: Vits? = null

    private var configs: Configs? = null

    private lateinit var tracker: AudioTrack

    private val audioStream = ArrayList<Float>()

    private var isRefused = true

    // 判断是否处理完毕
    private var flag = true

    private var vulkan_state = false

    private val REQUEST_CODE_GRANT = 0

    private val REQUEST_CODE_SELECT_MODEL = 1

    private val REQUEST_CODE_SELECT_CONFIG = 2

    private fun initOpenjtalk(assetManager: AssetManager) {
        InitOpenJtalk(assetManager)
    }

    private var noise_scale: Float = .667f

    private var length_scale: Float = 1f

    private var sid = 0

    private var max_speaker = 1

    private fun sentence_split(text: String): List<List<Int>>?{
        val outputs = ArrayList<List<Int>>()
        val sentences = words_split_cpp(text, assets).split("\n")
        var s = ""
        for (sentence in sentences){
            if (sentence.contains("EOS"))
                continue
            s += sentence.split("\t")[0]
            if (sentence.contains("変接続") || sentence.contains("記号") || sentence == sentences.last()){
                if (s.length > 50) {
                    runOnUiThread {
                        Toast.makeText(this, "一句话不能超过50个字符", Toast.LENGTH_SHORT).show()
                    }
                    return null
                }
                if (s.isEmpty()){
                    runOnUiThread {
                        Toast.makeText(this, "未知错误！", Toast.LENGTH_SHORT).show()
                    }
                    return null
                }
                if (s.length == 1){
                    runOnUiThread {
                        Toast.makeText(this, "句子不能过短！", Toast.LENGTH_SHORT).show()
                    }
                    return null
                }
                val seq = japanese_cleaner.text_to_sequence(
                    s,
                    symbols = configs!!.symbols,
                    cleaner = configs!!.data.text_cleaners[0]
                )
                outputs.add(seq)
                s = ""
            }
        }
        if (outputs.isEmpty()) {
            runOnUiThread {
                Toast.makeText(this, "未知错误！", Toast.LENGTH_SHORT).show()
            }
            return null
        }
        return outputs
    }

    @SuppressLint("SetTextI18n")
    private fun processWords(text: String) {
        flag = false
        runOnUiThread {
            binding.currentProgress.visibility = View.VISIBLE
            binding.progressText.visibility = View.VISIBLE
            binding.currentProgress.progress = 0
            binding.progressText.text = "0/100"
            binding.showProgressName.visibility = View.VISIBLE
        }
        val sentences = sentence_split(text.replace(".", "、").replace("\n",""))
        if (sentences != null){
            tracker.play()
            for (i in sentences.indices) {
                // 运行推理
                val output =
                    module?.forward(sentences[i].toIntArray(), vulkan_state, sid, noise_scale, length_scale)

                if (output != null) {
                    audioStream.addAll(output.toList())
                }
                runOnUiThread {
                    val p = (((i.toFloat() + 1.0)/sentences.size.toFloat()) * 100).toInt()
                    binding.currentProgress.progress = p
                    binding.progressText.text = p.toString() + "/100"
                }
            }
        }
        if (audioStream.isNotEmpty())
            tracker.write(audioStream.toFloatArray(), 0, audioStream.size, AudioTrack.WRITE_BLOCKING)

        runOnUiThread {
            binding.currentProgress.visibility = View.GONE
            binding.progressText.visibility = View.GONE
            binding.showProgressName.visibility = View.GONE
        }
        tracker.stop()
        audioStream.clear()
        flag = true
    }

    private fun requestExternalStorage() {
        // 动态申请权限
        if (Build.VERSION.SDK_INT >= 23) {
            val read_req = Manifest.permission.READ_EXTERNAL_STORAGE
            val permission = ContextCompat.checkSelfPermission(this, read_req)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(read_req), 100)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isRefused) {
            if (!Environment.isExternalStorageEmulated()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("pacakage$packageName")
                startActivityForResult(intent, 1024)
            }
        }
    }

    private fun load_model(path: String): Boolean {
        var folder = ""
        if (path.endsWith("dec.ncnn.bin")) {
            folder = path.replace("dec.ncnn.bin", "")
        }
        if (path.endsWith("dp.ncnn.bin")) {
            folder = path.replace("dp.ncnn.bin", "")
        }
        if (path.endsWith("flow.ncnn.bin")) {
            folder = path.replace("flow.ncnn.bin", "")
        }
        if (path.endsWith("emb_g.ncnn.bin")) {
            folder = path.replace("emb_g.ncnn.bin", "")
        }
        if (path.endsWith("enc_p.ncnn.bin")) {
            folder = path.replace("enc_p.ncnn.bin", "")
        }
        if (folder == "") return false
        try {
            module = Vits()
            return module!!.init_vits(assets, folder)
        } catch (e: IOException) {
            return false
        }
    }

    private fun load_configs(path: String): Boolean {
        configs = null
        configs = ModelFileUtils.parseConfig(this, path)
        if (configs != null) {
            max_speaker = configs!!.data.n_speakers
            showSid()
        }
        return configs != null
    }

    private fun showSid() {
        binding.speakerId.maxValue = max_speaker - 1

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (testgpu()) {
            binding.vulkanSwitcher.visibility = View.VISIBLE
        } else {
            binding.vulkanSwitcher.visibility = View.GONE
        }
        // 权限申请
        requestExternalStorage()

        binding.selectModel.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(intent, REQUEST_CODE_SELECT_MODEL)
        }
        binding.selectConfig.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(intent, REQUEST_CODE_SELECT_CONFIG)
        }
        binding.vulkanSwitcher.setOnCheckedChangeListener { bottomview, ischecked ->
            vulkan_state = ischecked
        }
        binding.noiseScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                noise_scale = p1.toFloat() / 100f
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                Toast.makeText(this@MainActivity, noise_scale.toString(), Toast.LENGTH_SHORT).show()
            }
        })
        binding.lengthScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                length_scale = p1.toFloat() / 100f
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                Toast.makeText(this@MainActivity, length_scale.toString(), Toast.LENGTH_SHORT)
                    .show()
            }
        })
        binding.speakerId.setOnValueChangedListener(object : NumberPicker.OnValueChangeListener {
            override fun onValueChange(p0: NumberPicker?, p1: Int, p2: Int) {
                sid = p2
            }

        })

        // 初始化openjtalk模型
        initOpenjtalk(assets)
        japanese_cleaner = Cleaner()
        tracker = Player.buildTracker()

        binding.playBtn.setOnClickListener {
            val inputText = binding.wordsInput.text
            if (module != null && configs != null) {
                if (inputText!!.isNotEmpty()) {
                    // 处理完毕
                    if (flag) {
                        thread {
                            processWords(inputText.toString())
                        }
                    } else {
                        Toast.makeText(this, "别急。。。", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "请输入文字", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "请先加载配置文件和模型文件！", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        var realpath = ""
        when (requestCode) {
            REQUEST_CODE_GRANT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    isRefused = !Environment.isExternalStorageEmulated()
                }
            }
            REQUEST_CODE_SELECT_MODEL -> {
                thread {
                    if (uri != null && uri.path != null) {
                        val realpath = getPathFromUri(this, uri)!!
                        if (realpath.endsWith(".bin")) {
                            if (load_model(realpath) && module != null) {
                                runOnUiThread {
                                    Toast.makeText(this, "模型加载成功！", Toast.LENGTH_SHORT).show()
                                    binding.modelPath.text = "加载成功"
                                }
                            } else {
                                runOnUiThread {
                                    Toast.makeText(this, "模型加载失败！", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            runOnUiThread {
                                binding.modelPath.text = "加载失败"
                                Toast.makeText(this, "请选择正确的模型文件,以.bin结尾", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }
            }
            REQUEST_CODE_SELECT_CONFIG -> {
                thread {
                    if (uri != null) {
                        val realpath = getPathFromUri(this, uri)!!
                        if (realpath.endsWith("json")) {
                            if (load_configs(realpath) && configs != null) {
                                runOnUiThread {
                                    binding.configPath.text = "加载成功"
                                    Toast.makeText(this, "配置加载成功！", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                runOnUiThread {
                                    binding.configPath.text = "加载失败"
                                    Toast.makeText(this, "配置加载失败！", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(this, "请选择正确的配置文件，以.json结尾", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
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
        Log.i("MainActivity", "destroyed!")
        tracker.release()
        module = null
        module?.destroy()
        DestroyOpenJtalk()
    }

    /**
     * A native method that is implemented by the 'moereng' native library,
     * which is packaged with this application.
     */
    external fun InitOpenJtalk(assetManager: AssetManager)
    external fun DestroyOpenJtalk()
    external fun testgpu(): Boolean
    external fun words_split_cpp(text: String, assetManager: AssetManager): String
    companion object {
        init {
            System.loadLibrary("moereng")
        }
    }
}
