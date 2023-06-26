package com.example.moereng.utils.file

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.example.moereng.data.Config
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream


object FileUtils {
    private val REQUEST_CODE_SELECT_MODEL = 1
    private val REQUEST_CODE_SELECT_CONFIG = 2

    fun getPathFromUri(context: Context, uri: Uri): String? {
        var path: String? = null
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            // android 8.0
            if (docId.startsWith("raw:")) {
                return docId.replaceFirst("raw:", "")
            }
            val splits = docId.split(":").toTypedArray()
            var type: String? = null
            var id: String? = null
            if (splits.size == 2) {
                type = splits[0]
                id = splits[1]
            }
            val contentUriPrefixesToTry = arrayOf(
                "content://downloads/public_downloads",
                "content://downloads/my_downloads",
                "content://downloads/all_downloads"
            )
            when (uri.authority) {
                "com.android.externalstorage.documents" -> if ("primary" == type) {
                    path =
                        Environment.getExternalStorageDirectory().toString() + File.separator + id
                }
                "com.android.providers.downloads.documents" -> {
                    if ("raw" == type) {
                        path = id
                    } else {
                        for (contentUriPrefix in contentUriPrefixesToTry) {
                            if (id != null) {
                                val contentUri = ContentUris.withAppendedId(
                                    Uri.parse(contentUriPrefix),
                                    id.toLong()
                                )
                                val t_path = getMediaPathFromUri(context, contentUri,
                                    null, null)
                                if (t_path != null) {
                                    path = t_path
                                    break
                                }
                            }
                        }
                    }
                }
                "com.android.providers.media.documents" -> {
                    var externalUri: Uri? = null
                    when (type) {
                        "image" -> externalUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> externalUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "wave_utils" -> externalUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        "document" -> externalUri = MediaStore.Files.getContentUri("external")
                    }
                    val selection = "_id=?"
                    val selectionArgs = arrayOf(id)
                    if (externalUri != null) {
                        path = getMediaPathFromUri(context, externalUri, selection, selectionArgs)
                    }
                }
            }
        } else if (ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) {
            path = getMediaPathFromUri(context, uri, null, null)
        } else if (ContentResolver.SCHEME_FILE.equals(uri.scheme, ignoreCase = true)) {
            path = uri.path
        }
        return if (path == null) null else if (File(path).exists()) path else null
    }

    private fun getMediaPathFromUri(
        context: Context,
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String?>?
    ): String? {
        var path: String?
        path = uri.path
        val sdPath = Environment.getExternalStorageDirectory().absolutePath
        if (!path!!.startsWith(sdPath)) {
            val sepIndex = path.indexOf(File.separator, 1)
            path = if (sepIndex == -1) null else {
                sdPath.toString() + path.substring(sepIndex)
            }
        }
        if (path == null || !File(path).exists()) {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            val cursor = resolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    try {
                        val index = cursor.getColumnIndexOrThrow(projection[0])
                        if (index != -1) path = cursor.getString(index)
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                        path = null
                    } finally {
                        cursor.close()
                    }
                }
            }
        }
        return path
    }


    // parse and load config file
    fun parseConfig(context: Context, path: String): Config? {
        try {
            val configFile = File(path)
            val configStream = FileInputStream(configFile)
            val configBuffer = configStream.bufferedReader().use { it.readText() }
            configStream.close()
            val config = Gson().fromJson(configBuffer, Config::class.java)
            if (config.data?.text_cleaners.isNullOrEmpty()){
                return null
            }
            if (config.symbols!!.isEmpty()) {
                Toast.makeText(context, "配置文件必须包含symbols！", Toast.LENGTH_SHORT).show()
                return null
            }
            return config
        } catch (e: Exception) {
            Log.e("LoadConfig", e.message.toString())
            return null
        }
    }
}