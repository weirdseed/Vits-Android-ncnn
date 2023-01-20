package com.example.moereng.fragments

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class VCViewModel: ViewModel() {
    fun setConvertFinishValue(finished: Boolean){
        convertFinish.postValue(finished)
    }
    fun setMaxThreads(maxThreads: Int){
        maxThreadsLiveData.value = maxThreads
    }
    fun setVulkanState(vulkan: Boolean){
        gpuAvailableLiveData.value = vulkan
    }
    val convertFinish = MutableLiveData<Boolean>()
    val maxThreadsLiveData = MutableLiveData<Int>()
    val gpuAvailableLiveData = MutableLiveData<Boolean>()
}