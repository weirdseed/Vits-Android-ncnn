package com.example.moereng.utils.ui

import android.widget.Toast
import com.example.moereng.application.MoeRengApplication

object UIUtils {
    fun moerengToast(text: String){
        Toast.makeText(MoeRengApplication.context, text, Toast.LENGTH_SHORT).show()
    }
}