package com.example.moereng.utils

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.moereng.application.MoeRengApplication.Companion.context

object PermissionUtils {
    fun requestAppPermission(activity: Activity) {
        // 动态申请权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // read permission
            val readRequest = Manifest.permission.READ_EXTERNAL_STORAGE

            // write permission
            val writeRequest = Manifest.permission.WRITE_EXTERNAL_STORAGE

            // record permission
            val recordRequest = Manifest.permission.RECORD_AUDIO

            ActivityCompat.requestPermissions(activity, arrayOf(readRequest, writeRequest, recordRequest), 1024)
        }else {
            AlertDialog.Builder(activity).apply {
                setTitle("警告")
                setMessage("是否允许文件存储权限？")
                setCancelable(false)
                setPositiveButton("是"){ dialog, which->
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    activity.startActivity(intent)
                }
                setNegativeButton("否"){ dialog, which->
                }
                show()
            }
        }

    }

    fun checkAppPermission(activity: Activity): Boolean{
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
            // read permission
            val readRequest = Manifest.permission.READ_EXTERNAL_STORAGE
            val permissionRead = ContextCompat.checkSelfPermission(activity, readRequest)

            // write permission
            val writeRequest = Manifest.permission.WRITE_EXTERNAL_STORAGE
            val permissionWrite = ContextCompat.checkSelfPermission(activity, writeRequest)

            // record permission
            val recordRequest = Manifest.permission.RECORD_AUDIO
            val permissionRecord = ActivityCompat.checkSelfPermission(activity, recordRequest)

            return (permissionRead == PackageManager.PERMISSION_GRANTED
                    && permissionWrite == PackageManager.PERMISSION_GRANTED
                    && permissionRecord == PackageManager.PERMISSION_GRANTED)

        } else {
            return Environment.isExternalStorageManager()
        }
    }
}