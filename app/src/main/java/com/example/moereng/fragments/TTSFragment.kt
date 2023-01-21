package com.example.moereng.fragments

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.moereng.Vits
import com.example.moereng.application.MoeRengApplication
import com.example.moereng.data.Config
import com.example.moereng.databinding.FragmentTtsBinding
import com.example.moereng.utils.FileUtils
import com.example.moereng.utils.PermissionUtils.checkStoragePermission
import com.example.moereng.utils.PermissionUtils.requestStoragePermission
import com.example.moereng.utils.PlayerUtils
import com.example.moereng.utils.TextUtils
import com.example.moereng.utils.UIUtils.moerengToast
import com.example.moereng.utils.VitsUtils.checkConfig
import com.example.moereng.utils.VitsUtils.checkThreadsCpp
import com.example.moereng.utils.VitsUtils.initOpenJtalk
import com.example.moereng.utils.VitsUtils.testGpu
import com.example.moereng.utils.VitsUtils.words_split_cpp
import com.example.moereng.utils.WaveUtils.writeWav
import kotlin.concurrent.thread

class TTSFragment : Fragment() {

    private val ttsViewModel: TTSViewModel by activityViewModels()

    private var binding: FragmentTtsBinding? = null

    private val ttsBinding get() = binding!!

    private lateinit var japaneseTextUtils: TextUtils

    private var config: Config? = null

    private val playerUtils = PlayerUtils()

    private val audioArray = ArrayList<Float>()

    // 判断是否处理完毕
    private var finishFlag = true

    private var voiceConvert = false

    private var multi = true

    private var samplingRate = 22050

    private var currentThreads = 1

    private var noiseScale: Float = .667f

    private var noiseScaleW: Float = .8f

    private var lengthScale: Float = 1f

    private var targetFolder: String = ""

    private var sid = 0

    private var maxThreads = 1

    private var maxSpeaker = 1

    private var vulkanState = false

    private var openJtalkState = false

    private val REQUEST_CODE_SELECT_MODEL = 1

    private val REQUEST_CODE_SELECT_CONFIG = 2

    private val ttsContext = MoeRengApplication.context

    private var modelInitState: Boolean = false

    // init thread spinner
    private fun initSpinner() {
        val spinnerArray = IntArray(maxThreads)
        for (i in 1..maxThreads) {
            spinnerArray[i - 1] = i
        }
        val adapter =
            ArrayAdapter(ttsContext, android.R.layout.simple_spinner_item, spinnerArray.toList())
        ttsBinding.threadSpinner.adapter = adapter
        ttsBinding.threadSpinner.setSelection(Integer.min(currentThreads - 1, maxThreads - 1))
    }

    private fun cleanInputs(text: String): String {
        return text.replace("\"", "").replace("\'", "")
            .replace("\t", " ").replace("\n", "、")
            .replace("”", "")
    }

    @SuppressLint("SetTextI18n")
    private fun initPath() {
        ttsBinding.configPath.text = "请把文件放在\"Download\"文件夹"
        ttsBinding.modelPath.text = "请把文件放在\"Download\"文件夹"
    }

    // split sentence and clean text
    private fun sentenceSplit(text: String): List<List<Int>>? {
        val outputs = ArrayList<List<Int>>()
        var sentences = words_split_cpp(cleanInputs(text), requireActivity().assets)
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
                    requireActivity().runOnUiThread {
                        Toast.makeText(
                            ttsContext, "一句话不能超过50个字符！",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return null
                }
                val seq = japaneseTextUtils.text_to_sequence(
                    s,
                    symbols = config!!.symbols!!,
                    cleaner = config!!.data!!.text_cleaners!![0]
                )
                if (seq.isEmpty() || seq.sum() == 0)
                    continue
                outputs.add(seq)
                s = ""
            }
        }
        if (outputs.isEmpty()) {
            requireActivity().runOnUiThread {
                Toast.makeText(
                    ttsContext, "解析失败！仅支持日语！请检查是否包含不支持字符！",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return null
        }
        return outputs
    }

    // hide progress bar
    @SuppressLint("SetTextI18n")
    private fun hideProgressBar(hide: Boolean, length: Int = 0) {
        requireActivity().runOnUiThread {
            // hide/show progress bar
            var visible = View.GONE
            if (!hide) {
                visible = View.VISIBLE
                ttsBinding.currentProgress.progress = 0
                ttsBinding.progressText.text = "0/${length}"
            }
            ttsBinding.progressLayout.visibility = visible
        }
    }

    // hide buttons
    private fun hideButtons(hide: Boolean) {
        requireActivity().runOnUiThread {
            var visible = View.GONE
            if (!hide) visible = View.VISIBLE
            ttsBinding.playBtn.visibility = visible
            ttsBinding.exportBtn.visibility = visible
        }
    }

    // processing inputs
    @SuppressLint("SetTextI18n")
    private fun processWords(text: String) {
        finishFlag = false
        ttsViewModel.setGenerationFinishValue(finishFlag)
        if (!openJtalkState) {
            requireActivity().runOnUiThread {
                moerengToast("初始化openjtalk字典...")
            }
            // initialize openjtalk
            openJtalkState = initOpenJtalk(requireActivity().assets)
            requireActivity().runOnUiThread {
                moerengToast("初始化字典完成！")
            }
        }
        audioArray.clear()
        if (ttsBinding.playBtn.visibility == View.VISIBLE
            || ttsBinding.exportBtn.visibility == View.VISIBLE
        ) {
            hideButtons(true)
        }
        try {
            // split inputs
            val sentences = sentenceSplit(text)

            if (sentences != null) {
                // progress visibility
                hideProgressBar(false, sentences.size)

                // inference for each sentence
                for (i in sentences.indices) {
                    // start inference
                    val output =
                        Vits.forward(
                            sentences[i].toIntArray(),
                            vulkanState,
                            multi,
                            sid,
                            noiseScale,
                            noiseScaleW,
                            lengthScale,
                            currentThreads
                        )

                    if (output != null) {
                        audioArray.addAll(output.toList())
                    }

                    // set current progress
                    requireActivity().runOnUiThread {
                        val p = (((i.toFloat() + 1.0) / sentences.size.toFloat()) * 100).toInt()
                        ttsBinding.currentProgress.progress = p
                        ttsBinding.progressText.text = "${i + 1}/${sentences.size}"
                    }
                }

                // prepare data
                playerUtils.createAudioTrack(audioArray.toFloatArray())

                // show play button and export button
                if (audioArray.isNotEmpty()) {
                    hideButtons(false)
                } else {
                    hideButtons(true)
                    hideProgressBar(true)
                }
            }
        } catch (e: Exception) {
            hideProgressBar(true)
            finishFlag = true
            ttsViewModel.setGenerationFinishValue(finishFlag)
            Log.e("TTSFragment", e.message.toString())
        }
        finishFlag = true
        ttsViewModel.setGenerationFinishValue(finishFlag)
    }

    // export voice to wav format file
    private fun export() {
        val tfolder = targetFolder.substring(0..targetFolder.length - 2)
        var name = ""
        try {
            name = config!!.speakers!![sid]
        } catch (e: Exception){
            Log.e("TTSFragment", e.message.toString())
        }
        if (name == "") name = "tts"
        else name = "tts-${name}"
        val path = writeWav(
            audioArray.toFloatArray(),
            tfolder,
            samplingRate,
            name
        )
        if (audioArray.isNotEmpty() && path != "") {
            // dialog
            AlertDialog.Builder(context).apply {
                setTitle("导出成功！")
                setMessage("成功导出到$path")
                setCancelable(false)
                setPositiveButton("好的") { dialog, which ->
                }
                show()
            }
        } else {
            moerengToast("导出失败！")
        }
    }

    // show/hide speakers' names
    private fun showSid(multi: Boolean) {
        if (multi) {
            if (ttsBinding.sidPickLayout.visibility == View.GONE) {
                ttsBinding.sidPickLayout.visibility = View.VISIBLE
            }
            ttsBinding.speakerId.maxValue = maxSpeaker - 1
            ttsBinding.speakerId.displayedValues = config?.speakers?.toTypedArray()
        } else {
            if (ttsBinding.sidPickLayout.visibility == View.VISIBLE)
                ttsBinding.sidPickLayout.visibility = View.GONE
        }
    }

    // show loading errors
    @SuppressLint("SetTextI18n")
    private fun showErrorText(type: String) {
        when (type) {
            "model" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ttsBinding.modelPath.text = "加载失败，请把文件放在Android/media/model/文件夹"
                } else {
                    ttsBinding.modelPath.text = "加载失败，请把文件放在Download文件夹"
                }
            }
            "config" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ttsBinding.configPath.text = "加载失败，请把文件放在Android/media/model/文件夹"
                } else {
                    ttsBinding.configPath.text = "加载失败，请把文件放在Download文件夹"
                }
            }
        }
    }

    // load config file
    private fun loadConfigs(path: String) {
        config = FileUtils.parseConfig(ttsContext, path)
        var type = "single"
        if (config != null && config!!.speakers != null) type = "multi"
        if (checkConfig(config, type)) {
            samplingRate = config!!.data!!.sampling_rate!!
            if (config!!.data!!.n_speakers!! > 1) {
                multi = true
                maxSpeaker = config!!.data!!.n_speakers!!
                showSid(multi)
            } else {
                multi = false
                showSid(multi)
            }
            ttsBinding.configPath.text = path
            ttsBinding.loadModelLayout.visibility = View.VISIBLE
            playerUtils.setTrackData(config!!.data!!.sampling_rate!!, 1)
            moerengToast("配置加载成功！")
        } else {
            showErrorText("config")
            moerengToast("配置加载失败！")
        }
    }

    // load model files
    private fun loadModel(path: String) {
        var folder = ""
        when {
            path.endsWith("dec.ncnn.bin") ->
                folder = path.replace("dec.ncnn.bin", "")
            path.endsWith("dp.ncnn.bin") ->
                folder = path.replace("dp.ncnn.bin", "")
            path.endsWith("flow.ncnn.bin") ->
                folder = path.replace("flow.ncnn.bin", "")
            path.endsWith("flow.reverse.ncnn.bin") ->
                folder = path.replace("flow.ncnn.bin", "")
            path.endsWith("emb_g.ncnn.bin") ->
                folder = path.replace("emb_g.ncnn.bin", "")
            path.endsWith("enc_p.ncnn.bin") ->
                folder = path.replace("enc_p.ncnn.bin", "")
            path.endsWith("enc_q.ncnn.bin") ->
                folder = path.replace("enc_q.ncnn.bin", "")
        }
        if (folder == "") {
            requireActivity().runOnUiThread {
                moerengToast("模型加载失败！")
                ttsBinding.modelPath.text = "该文件不是支持的模型文件！"
            }
        }
        if (targetFolder == "") targetFolder = folder
        ttsViewModel.setGenerationFinishValue(false)

        modelInitState = Vits.init_vits(
            requireActivity().assets,
            folder,
            voiceConvert,
            multi,
            maxThreads
        )
        ttsViewModel.setGenerationFinishValue(true)
        if (modelInitState) {
            requireActivity().runOnUiThread {
                moerengToast("模型加载成功！")
                ttsBinding.modelPath.text = path
            }
        } else {
            requireActivity().runOnUiThread {
                moerengToast("模型加载失败！")
                ttsBinding.modelPath.text = "模型初始化失败！"
            }
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTtsBinding.inflate(inflater, container, false)
        Log.i("TTSFragment", "onCreateView")
        // init config and model text
        initPath()

        // detect gpu availability
        ttsViewModel.gpuAvailableLiveData.observe(viewLifecycleOwner){ available->
            if (available) {
                ttsBinding.vulkanSwitcher.visibility = View.VISIBLE
            } else {
                ttsBinding.vulkanSwitcher.visibility = View.GONE
            }
        }

        // get max threads
        ttsViewModel.maxThreadsLiveData.observe(viewLifecycleOwner){ v->
            maxThreads = v
            // init thread spinner
            initSpinner()
        }
        return ttsBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i("TTSFragment", "onViewCreated")

        // vulkan switcher
        ttsBinding.vulkanSwitcher.setOnCheckedChangeListener { _, checked ->
            vulkanState = checked
        }

        // threads spinner listener
        ttsBinding.threadSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    currentThreads = Integer.min(p2 + 1, maxThreads)
                    Log.i("TTSFragment", "current threads = ${currentThreads}")
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                }
            }

        // select config button
        ttsBinding.selectConfig.setOnClickListener {
            if (!finishFlag){
                moerengToast("生成中，请稍等...")
            } else if (!checkStoragePermission(requireActivity()))
                requestStoragePermission(requireActivity())
            else {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
                    intent.type = "application/json"
                } else {
                    intent.type = "*/*"
                }
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                startActivityForResult(intent, REQUEST_CODE_SELECT_CONFIG)
            }
        }

        // select model button
        ttsBinding.selectModel.setOnClickListener {
            if (!finishFlag){
                moerengToast("生成中，请稍等...")
            }else if (!checkStoragePermission(requireActivity()))
                requestStoragePermission(requireActivity())
            else {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.type = "application/octet-stream"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                startActivityForResult(intent, REQUEST_CODE_SELECT_MODEL)
            }
        }

        // noise slider
        ttsBinding.noiseScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                noiseScale = p1.toFloat() / 100f
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {

            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                Toast.makeText(ttsContext, noiseScale.toString(), Toast.LENGTH_SHORT).show()
            }
        })

        // noise w slider
        ttsBinding.noiseScaleW.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                noiseScaleW = p1.toFloat() / 100f
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                Toast.makeText(ttsContext, noiseScaleW.toString(), Toast.LENGTH_SHORT).show()
            }

        })

        // length slider
        ttsBinding.lengthScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                lengthScale = p1.toFloat() / 100f
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                Toast.makeText(ttsContext, lengthScale.toString(), Toast.LENGTH_SHORT)
                    .show()
            }
        })

        // speakers' id picker's listener
        ttsBinding.speakerId.setOnValueChangedListener { p0, p1, p2 -> sid = p2 }

        // initialize cleaner
        japaneseTextUtils = TextUtils()

        // generateButton listener
        ttsBinding.generateBtn.setOnClickListener {
            val inputMethodManager =
                context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(ttsBinding.wordsInput.windowToken, 0)
            val inputText = ttsBinding.wordsInput.text
            if (config == null) {
                moerengToast("请加载配置文件！")
            } else if (!modelInitState) {
                moerengToast("请加载模型文件！")
            } else if (inputText != null && inputText.isEmpty()) {
                moerengToast("请输入文字")
            } else if (!finishFlag) {
                // hide keyboard
                moerengToast("稍等...")
            } else {
                thread {
                    processWords(inputText.toString())
                }
            }
        }

        // play button listener
        ttsBinding.playBtn.setOnClickListener {
            if (!playerUtils.isPlaying) {
                playerUtils.start(requireActivity(), ttsBinding, "tts")
            } else {
                playerUtils.stop(ttsBinding, "tts")
            }
        }

        // export button listener
        ttsBinding.exportBtn.setOnClickListener {
            export()
        }
    }

    // getting result for selecting config or model
    @SuppressLint("SetTextI18n")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        when (requestCode) {
            REQUEST_CODE_SELECT_CONFIG -> {
                ttsBinding.loadModelLayout.visibility = View.GONE
                initPath()
                ttsBinding.playBtn.visibility = View.GONE
                ttsBinding.progressLayout.visibility = View.GONE
                ttsBinding.exportBtn.visibility = View.GONE
                if (uri != null) {
                    try {
                        val realPath = FileUtils.getPathFromUri(ttsContext, uri)
                        if (realPath != null && realPath.endsWith("json")) {
                            loadConfigs(realPath)
                        } else {
                            moerengToast("请选择正确的配置文件，以.json结尾")
                            ttsBinding.configPath.text = "加载失败！"
                        }
                    } catch (e: Exception) {
                        Log.e("LoadConfig", e.message.toString())
                        showErrorText("config")
                        moerengToast("配置加载失败！")
                    }
                }
            }
            REQUEST_CODE_SELECT_MODEL -> {
                thread {
                    if (uri != null) {
                        try {
                            val realPath = FileUtils.getPathFromUri(ttsContext, uri)
                            if (realPath != null && realPath.endsWith(".bin")) {
                                loadModel(realPath)
                            }
                            requireActivity().runOnUiThread {
                                if (realPath != null && !realPath.endsWith(".bin")) {
                                    moerengToast("请选择正确的模型文件,以.bin结尾")
                                    ttsBinding.modelPath.text = "请选择.bin后缀的配置文件"
                                }
                                if (realPath == null) {
                                    moerengToast("模型加载失败！")
                                    showErrorText("model")
                                }
                            }
                            ttsBinding.selectModel.isClickable = true
                        } catch (e: Exception) {
                            Log.e("VCFragment", e.message.toString())
                            requireActivity().runOnUiThread {
                                showErrorText("model")
                                moerengToast("模型加载失败！")
                            }
                            ttsBinding.selectModel.isClickable = true
                        }
                    }
                }
            }
        }
    }


    override fun onStop() {
        super.onStop()
        playerUtils.stop(ttsBinding, "tts")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsBinding.wordsInput.setText("")
        playerUtils.release(ttsBinding, "tts")
        modelInitState = false
        config = null
        audioArray.clear()
        Log.i("TTSFragment", "cleared!")
        binding = null
    }
}