package com.example.moereng.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.moereng.Vits
import com.example.moereng.application.MoeRengApplication
import com.example.moereng.data.Config
import com.example.moereng.databinding.FragmentVcBinding
import com.example.moereng.utils.VitsUtils.checkConfig
import com.example.moereng.utils.audio.PlayerUtils
import com.example.moereng.utils.audio.RecordingUtils
import com.example.moereng.utils.audio.WaveUtils
import com.example.moereng.utils.audio.WaveUtils.audioLenToDuration
import com.example.moereng.utils.file.FileUtils
import com.example.moereng.utils.permission.PermissionUtils.checkRecordPermission
import com.example.moereng.utils.permission.PermissionUtils.checkStoragePermission
import com.example.moereng.utils.permission.PermissionUtils.requestRecordPermission
import com.example.moereng.utils.permission.PermissionUtils.requestStoragePermission
import com.example.moereng.utils.ui.UIUtils.moerengToast
import java.lang.Integer.min
import kotlin.concurrent.thread

class VCFragment : Fragment() {

    private val vcViewModel: VCViewModel by activityViewModels()

    private var binding: FragmentVcBinding? = null

    private val vcBinding get() = binding!!

    private var player = PlayerUtils()

    private var recorder = RecordingUtils()

    val audioArray = ArrayList<Float>()

    private var targetFolder: String = ""

    private var n_vocab: Int = 0

    private var maxThreads = 1

    private var currentThreads = 1

    private var maxSpeaker = 1

    private var srcSid = 0

    private var tgtSid = 0

    private var config: Config? = null

    private var modelInitState: Boolean = false

    private var samplingRate = 22050

    private val vcContext = MoeRengApplication.context

    private var vulkanState = false

    private var loadingFinish = true

    private var convertFinish = true

    private var audioInputs: FloatArray? = null

    private var recordData = ArrayList<Float>()

    // init path
    @SuppressLint("SetTextI18n")
    private fun initPath() {
        vcBinding.configPath.text = "请把文件放在\"Download\"文件夹"
        vcBinding.modelPath.text = "请把文件放在\"Download\"文件夹"
    }

    // init thread spinner
    private fun initSpinner() {
        val spinnerArray = IntArray(maxThreads)
        for (i in 1..maxThreads) {
            spinnerArray[i - 1] = i
        }
        val adapter =
            ArrayAdapter(vcContext, android.R.layout.simple_spinner_item, spinnerArray.toList())
        vcBinding.threadSpinner.adapter = adapter
        vcBinding.threadSpinner.setSelection(min(currentThreads - 1, maxThreads - 1))
    }

    // show/hide speakers' names
    private fun showSids() {
        vcBinding.srcSidPicker.minValue = 0
        vcBinding.srcSidPicker.maxValue = maxSpeaker - 1
        vcBinding.srcSidPicker.displayedValues = config?.speakers?.toTypedArray()

        vcBinding.tgtSidPicker.minValue = 0
        vcBinding.tgtSidPicker.maxValue = maxSpeaker - 1
        vcBinding.tgtSidPicker.displayedValues = config?.speakers?.toTypedArray()
    }

    // show loading errors
    @SuppressLint("SetTextI18n")
    private fun showErrorText(type: String) {
        when (type) {
            "model" -> {
                vcBinding.modelPath.text = "模型加载失败！"
            }

            "config" -> {
                vcBinding.configPath.text = "配置加载失败！"

            }

            "wave" -> {
                vcBinding.audioPath.text = "音频加载失败！"
            }
        }
    }

    // load config file
    private fun loadConfig(path: String) {
        initPath()
        config = FileUtils.parseConfig(vcContext, path)

        if (checkConfig(config, "vc")) {
            samplingRate = config!!.data!!.sampling_rate!!

            if (config!!.data!!.n_vocabs != null){
                n_vocab = config!!.data!!.n_vocabs!!
            } else {
                n_vocab = config!!.symbols!!.size
            }

            if (config!!.data!!.n_speakers!! <= 1) {
                vcBinding.configPath.text = "仅支持多人模型！"
                moerengToast("配置加载失败！")
            } else {
                if (config!!.data!!.n_speakers != config!!.speakers!!.size){
                    throw RuntimeException("speakers与n_speakers不匹配，请检查配置文件！")
                }
                maxSpeaker = config!!.data!!.n_speakers!!
                vcBinding.configPath.text = path
                vcBinding.loadModelLayout.visibility = View.VISIBLE
                player.setTrackData(config!!.data!!.sampling_rate!!, 1)
                moerengToast("配置加载成功！")
                showSids()
            }
        } else {
            moerengToast("加载失败！")
            showErrorText("config")
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

            path.endsWith("emb_g.bin") ->
                folder = path.replace("emb_g.bin", "")

            path.endsWith("emb_t.bin") ->
                folder = path.replace("emb_t.bin", "")

            path.endsWith("enc_p.ncnn.bin") ->
                folder = path.replace("enc_p.ncnn.bin", "")

            path.endsWith("enc_q.ncnn.bin") ->
                folder = path.replace("enc_q.ncnn.bin", "")
        }
        if (folder == "") {
            requireActivity().runOnUiThread {
                moerengToast("模型加载失败！")
                vcBinding.modelPath.text = "该文件不是支持的模型文件！"
            }
        }
        if (targetFolder == "") targetFolder = folder
        vcViewModel.setConvertFinishValue(false)
        modelInitState = Vits.init_vits(
            requireActivity().assets,
            folder,
            true,
            true,
            n_vocab
        )
        vcViewModel.setConvertFinishValue(true)
        if (modelInitState) {
            requireActivity().runOnUiThread {
                moerengToast("模型加载成功！")
                vcBinding.modelPath.text = path
            }
        } else {
            requireActivity().runOnUiThread {
                moerengToast("模型加载失败！")
                vcBinding.modelPath.text = "模型初始化失败！"
            }
        }

    }

    // record audio stream
    @SuppressLint("SetTextI18n")
    private fun record() {
        try {
            recorder.initRecorder()
            requireActivity().runOnUiThread {
                // change text
                vcBinding.recordAudioBtn.text = "停止录制"
                vcBinding.audioLength.text = "已录制: 00:00:00"

                // hide progress bar
                if (vcBinding.progressLayout.visibility == View.VISIBLE) {
                    vcBinding.progressLayout.visibility = View.GONE
                }
                // hide buttons
                if (vcBinding.playBtn.visibility == View.VISIBLE)
                    vcBinding.playBtn.visibility = View.GONE
                if (vcBinding.exportBtn.visibility == View.VISIBLE)
                    vcBinding.exportBtn.visibility = View.GONE
            }
            recordData.clear()
            while (recorder.isRecording && recorder.initialized) {
                val audio = recorder.record()
                if (audio != null) {
                    recordData.addAll(audio.toList())
                    // set duration
                    requireActivity().runOnUiThread {
                        vcBinding.audioLength.text =
                            "已录制: ${audioLenToDuration(recordData.size, 22050)}"
                    }
                }
            }


        } catch (e: Exception) {
            Log.e("VCFragment", e.message.toString())
            requireActivity().runOnUiThread {
                moerengToast(e.message.toString())
            }
        }
    }

    // convert audio stream
    @SuppressLint("SetTextI18n")
    private fun convert(data: List<Float>) {
        try {
            // show progress bar
            requireActivity().runOnUiThread {
                if (vcBinding.progressLayout.visibility == View.GONE)
                    vcBinding.progressLayout.visibility = View.VISIBLE

            }
            audioArray.clear()
            val step = min(20000, data.size)
            for (chunkId in data.indices step step) {
                val end = min(chunkId + step, data.size)
                val chunk = data.subList(chunkId, end).toFloatArray()
                val out = Vits.voice_convert(chunk, srcSid, tgtSid, vulkanState, currentThreads)
                audioArray.addAll(out.toList())
                // set progress
                requireActivity().runOnUiThread {
                    val p = ((end.toFloat() / data.size.toFloat()) * 100).toInt()
                    vcBinding.currentProgress.progress = p
                    vcBinding.progressText.text = "$p/100"
                }
            }

            // prepare data
            player.createAudioTrack(audioArray.toFloatArray())

            // set finish flag
            convertFinish = true
            vcViewModel.setConvertFinishValue(convertFinish)

            // show buttons
            requireActivity().runOnUiThread {
                if (vcBinding.playBtn.visibility == View.GONE)
                    vcBinding.playBtn.visibility = View.VISIBLE
                if (vcBinding.exportBtn.visibility == View.GONE)
                    vcBinding.exportBtn.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e("VCFragment", e.message.toString())
            requireActivity().runOnUiThread {
                moerengToast("音频数据读取失败！")
            }
        }
    }

    // export audio
    private fun export() {
        val tfolder = targetFolder.substring(0..targetFolder.length - 2)
        val path = WaveUtils.writeWav(
            audioArray.toFloatArray(), tfolder, samplingRate,
            "vc-${config!!.speakers!![srcSid]}-${config!!.speakers!![tgtSid]}"
        )
        requireActivity().runOnUiThread {
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
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVcBinding.inflate(inflater, container, false)
        Log.i("VCFragment", "onCreateView")
        initPath()

        // init gpu and threads
        vcViewModel.gpuAvailableLiveData.observe(viewLifecycleOwner) { available ->
            if (available) {
                vcBinding.vulkanSwitcher.visibility = View.VISIBLE
            } else {
                vcBinding.vulkanSwitcher.visibility = View.GONE
            }
        }

        vcViewModel.maxThreadsLiveData.observe(viewLifecycleOwner) { v ->
            maxThreads = v
            initSpinner()
        }
        return vcBinding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i("VCFragment", "onViewCreated")

        vcBinding.vulkanSwitcher.setOnCheckedChangeListener { _, checked ->
            vulkanState = checked
        }

        vcBinding.threadSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    currentThreads = min(p2 + 1, maxThreads)
                    Log.i("VCFragment", "current threads = ${currentThreads}")
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                }
            }

        // record voice
        vcBinding.recordAudioBtn.setOnClickListener {
            if (config == null) {
                moerengToast("请加载配置文件！")
            } else if (!modelInitState) {
                moerengToast("请加载模型！")
            } else if (!convertFinish) {
                moerengToast("转换中，请稍等...")
                // check permission
            } else if (checkRecordPermission(requireActivity())) {
                if (recorder.isRecording) {
                    // change text
                    vcBinding.recordAudioBtn.text = "开始录制"
                    recorder.stop()
                } else {
                    // start record
                    thread {
                        record()
                    }
                }
            } else {
                requestRecordPermission(requireActivity())
            }
        }

        // Replacement of StartActivityForResult
        val loadAudioActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data: Intent? = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                val uri = data.data

                loadingFinish = false
                // hide progress bar
                if (vcBinding.progressLayout.visibility == View.VISIBLE) {
                    vcBinding.currentProgress.progress = 0
                    vcBinding.progressText.text = "0/100"
                    vcBinding.progressLayout.visibility = View.GONE
                }

                // hide buttons
                if (vcBinding.playBtn.visibility == View.VISIBLE)
                    vcBinding.playBtn.visibility = View.GONE
                if (vcBinding.exportBtn.visibility == View.VISIBLE)
                    vcBinding.exportBtn.visibility = View.GONE

                if (uri != null) {
                    try {
                        val realPath = FileUtils.getPathFromUri(vcContext, uri)
                        if (realPath != null) {
                            audioInputs =
                                WaveUtils.loadWav(realPath, config!!.data!!.sampling_rate!!)
                            moerengToast("读取成功！")
                            vcBinding.audioPath.text = realPath
                        } else {
                            moerengToast("读取失败！")
                            showErrorText("wave")
                        }
                    } catch (e: Exception) {
                        Log.e("VCFragment", e.message.toString())
                        moerengToast("读取失败！")
                        vcBinding.audioPath.text = e.message
                    }
                }
                loadingFinish = true
            }
        }

        // load wave file
        vcBinding.loadAudioBtn.setOnClickListener {
            if (config == null) moerengToast("请加载配置文件！")
            else if (!modelInitState) moerengToast("请加载模型文件！")
            else if (!convertFinish) {
                moerengToast("转换中，请稍等...")
            } else if (loadingFinish) {
                // check permission
                if (!checkStoragePermission(requireActivity()))
                    requestStoragePermission(
                        requireActivity()
                    )
                else {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    intent.type = "audio/x-wav"
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    loadAudioActivityResultLauncher.launch(intent)
                }
            } else {
                moerengToast("正在读取音频，请稍后...")
            }
        }

        // Replacement of StartActivityForResult
        val selectConfigActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data: Intent? = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                val uri = data.data
                if (vcBinding.exportBtn.visibility == View.VISIBLE) {
                    vcBinding.exportBtn.visibility = View.GONE
                }
                modelInitState = false
                if (uri != null) {
                    try {
                        val realPath = FileUtils.getPathFromUri(vcContext, uri)
                        if (realPath != null && realPath.endsWith("json")) {
                            loadConfig(realPath)
                        }
                        if (realPath != null && !realPath.endsWith("json")) {
                            moerengToast("配置加载失败！")
                            vcBinding.configPath.text = "请选择.json后缀的配置文件"
                        }
                        if (realPath == null) {
                            moerengToast("加载失败！")
                            showErrorText("config")
                        }
                    } catch (e: Exception) {
                        Log.e("VCFragment", e.message.toString())
                        moerengToast("配置加载失败！")
                        showErrorText("config")
                    }
                }
            }
        }

        // select config button
        vcBinding.selectConfig.setOnClickListener {
            if (!convertFinish) {
                moerengToast("转换中，请稍等...")
            } else if (!checkStoragePermission(requireActivity()))
                requestStoragePermission(
                    requireActivity()
                )
            else {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    intent.type = "application/json"
                } else {
                    intent.type = "*/*"
                }
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                selectConfigActivityResultLauncher.launch(intent)
            }
        }

        // Replacement of StartActivityForResult
        val selectModelActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data: Intent? = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                val uri = data.data
                thread {
                    if (uri != null) {
                        try {
                            val realPath = FileUtils.getPathFromUri(vcContext, uri)
                            if (realPath != null && realPath.endsWith(".bin")) {
                                loadModel(realPath)
                            }
                            requireActivity().runOnUiThread {
                                if (realPath != null && !realPath.endsWith(".bin")) {
                                    moerengToast("请选择正确的模型文件,以.bin结尾")
                                    vcBinding.modelPath.text = "请选择.bin后缀的配置文件"
                                }
                                if (realPath == null) {
                                    moerengToast("模型加载失败！")
                                    showErrorText("model")
                                }
                            }
                            vcBinding.selectModel.isClickable = true
                        } catch (e: Exception) {
                            Log.e("VCFragment", e.message.toString())
                            requireActivity().runOnUiThread {
                                showErrorText("model")
                                moerengToast("模型加载失败！")
                            }
                            vcBinding.selectModel.isClickable = true
                        }
                    }
                }
            }
        }

        // select model button
        vcBinding.selectModel.setOnClickListener {
            if (!convertFinish) {
                moerengToast("转换中，请稍等...")
            } else if (!checkStoragePermission(requireActivity()))
                requestStoragePermission(
                    requireActivity()
                )
            else {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.type = "application/octet-stream"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                selectModelActivityResultLauncher.launch(intent)
            }
        }

        // select src id
        vcBinding.srcSidPicker.wrapSelectorWheel = false
        vcBinding.srcSidPicker.setOnValueChangedListener { _, i, i2 -> srcSid = i2 }

        // select target id
        vcBinding.tgtSidPicker.wrapSelectorWheel = false
        vcBinding.tgtSidPicker.setOnValueChangedListener { _, i, i2 -> tgtSid = i2 }

        // convert
        vcBinding.convertBtn.setOnClickListener {
            if (config == null) {
                moerengToast("请加载配置文件！")
            } else if (!modelInitState) {
                moerengToast("请加载模型文件！")
            } else if (audioInputs == null && recordData.isEmpty()) {
                moerengToast("请加载音频文件或录制音频！")
            } else if (recorder.isRecording) {
                moerengToast("录制中，请完成录制后转换...")
            } else if (!convertFinish) {
                moerengToast("转换中，请稍等...")
            } else {
                // init progress bar
                vcBinding.currentProgress.progress = 0
                vcBinding.progressText.text = "0/100"
                // hide buttons
                if (vcBinding.playBtn.visibility == View.VISIBLE)
                    vcBinding.playBtn.visibility = View.GONE
                if (vcBinding.exportBtn.visibility == View.VISIBLE)
                    vcBinding.exportBtn.visibility = View.GONE
                try {
                    if (audioInputs != null && recordData.isNotEmpty()) {
                        AlertDialog.Builder(context).apply {
                            setTitle("提示")
                            setMessage("请选择要转换的音频")
                            setCancelable(true)
                            setPositiveButton("录制的音频") { dialog, which ->
                                // set finish flag
                                convertFinish = false
                                vcViewModel.setConvertFinishValue(convertFinish)
                                thread {
                                    convert(recordData)
                                }
                            }
                            setNegativeButton("加载的音频") { dialog, which ->
                                // set finish flag
                                convertFinish = false
                                vcViewModel.setConvertFinishValue(convertFinish)
                                thread {
                                    convert(audioInputs!!.toList())
                                }
                            }
                            show()
                        }
                    } else if (audioInputs != null) {
                        convertFinish = false
                        vcViewModel.setConvertFinishValue(convertFinish)
                        thread {
                            convert(audioInputs!!.toList())
                        }
                    } else {
                        convertFinish = false
                        vcViewModel.setConvertFinishValue(convertFinish)
                        thread {
                            convert(recordData)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VCFragment", e.message.toString())
                    moerengToast("转换失败！")
                    // hide progress bar
                    if (vcBinding.progressLayout.visibility == View.VISIBLE) {
                        vcBinding.currentProgress.progress = 0
                        vcBinding.progressText.text = "0/100"
                        vcBinding.progressLayout.visibility = View.GONE
                    }
                    // hide buttons
                    if (vcBinding.playBtn.visibility == View.VISIBLE)
                        vcBinding.playBtn.visibility = View.GONE
                    if (vcBinding.exportBtn.visibility == View.VISIBLE)
                        vcBinding.exportBtn.visibility = View.GONE
                }
            }
        }

        // play button
        vcBinding.playBtn.setOnClickListener {
            if (!player.isPlaying) {
                player.start(requireActivity(), vcBinding, "vc")
            } else {
                player.stop(vcBinding, "vc")
            }
        }

        // export button
        vcBinding.exportBtn.setOnClickListener {
            thread {
                export()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        recorder.stop()
        player.stop(vcBinding, "vc")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        audioArray.clear()
        audioInputs = null
        modelInitState = false
        config = null
        player.release(vcBinding, "vc")
        recorder.release()
        recordData.clear()
        Log.i("VCFragment", "cleared")
        binding = null
    }
}