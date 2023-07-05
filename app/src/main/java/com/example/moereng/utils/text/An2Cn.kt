package com.example.moereng.utils.text

import android.util.Log
import java.lang.StringBuilder

// Reference from https://github.com/Ailln/cn2an
class An2Cn {
    private val TAG = "An2Cn"
    private val numList = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    private val unitList = listOf(
        "", "十", "百", "千", "万", "十", "百", "千", "亿", "十", "百", "千", "万", "十", "百", "千"
    )
    private val allNum = "0123456789"


    fun an2cn(inputs: String): String {
        if (inputs.isEmpty() || inputs == "") {
            throw RuntimeException("输入数据为空")
        }

        // 检查输入值
        checkInputs(inputs)

        // 检查正负
        var sign = ""
        if (inputs[0] == '-') {
            sign = "负"
        }

        // 处理小数
        val inputsSplit = inputs.split(".")
        val output: String

        if (inputsSplit.size == 1) {
            val integerData = inputsSplit[0]
            output = integerConvert(integerData)
        } else if (inputsSplit.size == 2) {
            val integerData = inputsSplit[0]
            val decimalData = inputsSplit[1]
            output = "${integerConvert(integerData)}${decimalConvert(decimalData)}"
        } else {
            throw RuntimeException("数据格式有误！")
        }
        return "$sign$output"
    }

    private fun checkInputs(inputs: String) {
        val checkKeys = "$allNum.-"
        for (num in inputs) {
            if (!checkKeys.contains(num)) {
                throw RuntimeException("待转换的数字不在范围内！")
            }
        }
    }

    private fun integerConvert(data: String): String {
        val integerData = data
        if (integerData.length > unitList.size) {
            throw RuntimeException("最大支持${unitList.size}位数字")
        }

        val outputT = StringBuilder()
        for (i in integerData.indices) {
            // 处理0
            if (integerData[i].toString().toInt() != 0) {
                outputT.append("${numList[integerData[i].toString().toInt()]}${unitList[integerData.length - i - 1]}")
            } else {
                if ((integerData.length - i - 1) % 4 != 0) {
                    outputT.append("${numList[integerData[i].toString().toInt()]}${unitList[integerData.length - i - 1]}")
                }
                if (i > 0 && outputT.toString().last().toString() == "零") {
                    outputT.append(numList[integerData[i].toString().toInt()])
                }
            }
        }
        var output = outputT.toString()
            .replace("零零", "零")
            .replace("零万", "万")
            .replace("零亿", "亿")
            .replace("亿万", "亿")
            .replace("^零|零$".toRegex(), "")

        if (output.length > 2 && output.substring(0,2) == "一十"){
            output = output.substring(1, output.length)
        }

        if (output.isEmpty()){
            output = "零"
        }

        return output
    }

    private fun decimalConvert(data: String): String {
        var decimalData = data
        val output = StringBuilder()

        if (decimalData.length > 16){
            Log.w(TAG, "decimalConvert: 小数过长将截取16位有效数字！")
            decimalData = decimalData.substring(0, 16)
        }

        if (decimalData.isNotEmpty()){
            output.append("点")
        } else {
            output.append("")
        }

        for (num in decimalData){
            output.append(numList[num.toString().toInt()])
        }

        return output.toString()
    }
}