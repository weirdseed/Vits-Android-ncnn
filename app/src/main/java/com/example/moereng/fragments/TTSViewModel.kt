package com.example.moereng.fragments

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TTSViewModel: ViewModel() {
    fun setGenerationFinishValue(finished: Boolean){
        generationFinish.postValue(finished)
    }
    fun setMaxThreads(maxThreads: Int){
        maxThreadsLiveData.value = maxThreads
    }
    fun setVulkanState(vulkan: Boolean){
        gpuAvailableLiveData.value = vulkan
    }
    val generationFinish = MutableLiveData<Boolean>()
    val maxThreadsLiveData = MutableLiveData<Int>()
    val gpuAvailableLiveData = MutableLiveData<Boolean>()

}