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
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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

    private var current_threads = 1

    private var max_threads = 1

    private val REQUEST_CODE_GRANT = 0

    private val REQUEST_CODE_SELECT_MODEL = 1

    private val REQUEST_CODE_SELECT_CONFIG = 2

    private var noise_scale: Float = .667f

    private var length_scale: Float = 1f

    private var sid = 0

    private var max_speaker = 1

    private fun initOpenjtalk(assetManager: AssetManager) {
        InitOpenJtalk(assetManager)
    }

    private fun clean_inputs(text: String): String {
        return text.replace("\"", "").replace("\'", "")
            .replace("\t", " ").replace("\n", "、")
            .replace("”", "")
    }

    private fun check_threads() {
        max_threads = check_threads_cpp()

    }

    private fun init_spinner() {
        val spinner_array = IntArray(max_threads)
        for (i in 1..max_threads) {
            spinner_array[i - 1] = i
        }
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, spinner_array.toList())
        binding.threadSpinner.adapter = adapter
        binding.threadSpinner.setSelection(min(current_threads - 1, max_threads - 1))
    }

    @SuppressLint("SetTextI18n")
    private fun show_tips(type: String) {
        when (type) {
            "model" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    binding.modelPath.text = "加载失败，请把文件放在Android/media下"
                } else {
                    binding.modelPath.text = "加载失败，请把文件放在Download文件夹下"
                }
            }
            "config" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    binding.configPath.text = "加载失败，请把文件放在Android/media下"
                } else {
                    binding.configPath.text = "加载失败，请把文件放在Download文件夹下"
                }
            }
        }
    }

    // split sentence and clean text
    private fun sentence_split(text: String): List<List<Int>>? {
        val outputs = ArrayList<List<Int>>()
        var sentences = words_split_cpp(clean_inputs(text), assets)
            .replace("EOS\n", "").split("\n")
        sentences = sentences.subList(0, sentences.size - 1)
        var s = ""
        for (i in sentences.indices) {
            val sentence = sentences[i]
            s += sentence.split("\t")[0]
            if (sentence.contains("記号,読点") ||
                sentence.contains("記号,句点") ||
                sentence.contains("記号,一般") ||
                sentence.contains("記号,空白") ||
                i == sentences.size - 1
            ) {
                if (s.length > 50) {
                    runOnUiThread {
                        Toast.makeText(
                            this, "一句话不能超过50个字符！",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return null
                }
                val seq = japanese_cleaner.text_to_sequence(
                    s,
                    symbols = configs!!.symbols,
                    cleaner = configs!!.data.text_cleaners[0]
                )
                if (seq.isEmpty() || seq.sum() == 0)
                    continue
                outputs.add(seq)
                s = ""
            }
        }
        if (outputs.isEmpty()) {
            runOnUiThread {
                Toast.makeText(
                    this, "解析失败！仅支持日语！请检查是否包含不支持字符！",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return null
        }
        return outputs
    }

    // inference and play sound
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
        try {
            val sentences = sentence_split(text)
            if (sentences != null) {
                tracker.play()
                for (i in sentences.indices) {
                    // 运行推理
                    val output =
                        module?.forward(
                            sentences[i].toIntArray(),
                            vulkan_state,
                            sid,
                            noise_scale,
                            length_scale,
                            current_threads
                        )
                    if (output != null) {
                        audioStream.addAll(output.toList())
                    }
                    runOnUiThread {
                        val p = (((i.toFloat() + 1.0) / sentences.size.toFloat()) * 100).toInt()
                        binding.currentProgress.progress = p
                        binding.progressText.text = "$p/100"
                    }
                }
            }
            if (audioStream.isNotEmpty())
                tracker.write(
                    audioStream.toFloatArray(),
                    0,
                    audioStream.size,
                    AudioTrack.WRITE_BLOCKING
                )

            runOnUiThread {
                binding.currentProgress.visibility = View.GONE
                binding.progressText.visibility = View.GONE
                binding.showProgressName.visibility = View.GONE
            }
            tracker.stop()
            audioStream.clear()

        } catch (e: Exception) {
            Log.e("MainActivity", e.message.toString())
        }
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
            return module!!.init_vits(assets, folder, max_threads)
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
        binding.speakerId.displayedValues = configs?.speakers?.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 权限申请
        requestExternalStorage()

        if (testgpu()) {
            binding.vulkanSwitcher.visibility = View.VISIBLE
        } else {
            binding.vulkanSwitcher.visibility = View.GONE
        }

        check_threads()

        init_spinner()

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

        binding.speakerId.setOnValueChangedListener { p0, p1, p2 -> sid = p2 }

        binding.threadSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                current_threads = min(p2 + 1, max_threads)
                Log.i("MainActivity", "current threads = ${current_threads}")
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
            }

        }

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
                        Toast.makeText(
                            this, "稍等...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this, "请输入文字",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    this, "请先加载配置文件和模型文件！",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        when (requestCode) {
            REQUEST_CODE_GRANT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    isRefused = !Environment.isExternalStorageEmulated()
                }
            }
            REQUEST_CODE_SELECT_MODEL -> {
                thread {
                    if (uri != null && uri.path != null) {
                        try {
                            val realpath = getPathFromUri(this, uri)
                            if (realpath != null && realpath.endsWith(".bin")) {
                                if (load_model(realpath) && module != null) {
                                    runOnUiThread {
                                        Toast.makeText(
                                            this, "模型加载成功！",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        binding.modelPath.text = realpath
                                    }
                                } else {
                                    runOnUiThread {
                                        show_tips("model")
                                        Toast.makeText(
                                            this, "模型加载失败！",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                runOnUiThread {
                                    Toast.makeText(
                                        this, "请选择正确的模型文件,以.bin结尾",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", e.message.toString())
                            runOnUiThread {
                                show_tips("model")
                                Toast.makeText(
                                    this, "模型加载失败！",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
            REQUEST_CODE_SELECT_CONFIG -> {
                if (uri != null) {
                    try {
                        val realpath = getPathFromUri(this, uri)!!
                        if (realpath.endsWith("json")) {
                            if (load_configs(realpath) && configs != null) {
                                binding.configPath.text = realpath
                                Toast.makeText(
                                    this, "配置加载成功！",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                show_tips("config")
                                Toast.makeText(
                                    this, "配置加载失败！",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                this, "请选择正确的配置文件，以.json结尾",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                    } catch (e: Exception) {
                        show_tips("config")
                        Toast.makeText(
                            this, "配置加载失败！",
                            Toast.LENGTH_SHORT
                        ).show()
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
    }

    /**
     * A native method that is implemented by the 'moereng' native library,
     * which is packaged with this application.
     */
    private external fun InitOpenJtalk(assetManager: AssetManager)
    private external fun testgpu(): Boolean
    private external fun words_split_cpp(text: String, assetManager: AssetManager): String
    private external fun check_threads_cpp(): Int

    companion object {
        init {
            System.loadLibrary("moereng")
        }
    }
}
