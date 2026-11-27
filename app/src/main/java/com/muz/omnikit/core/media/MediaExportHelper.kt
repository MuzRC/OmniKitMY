package com.muz.omnikit.core.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object MediaExportHelper {

    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 95
    ): Uri? {
        val mimeType = when (format) {
            Bitmap.CompressFormat.JPEG -> "image/jpeg"
            Bitmap.CompressFormat.PNG -> "image/png"
            else -> "image/webp"
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/OmniKitMY")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(format, quality, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "OmniKitMY")
                dir.mkdirs()
                val file = File(dir, displayName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(format, quality, out)
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun saveBytesToDownloads(
        context: Context,
        displayName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/OmniKitMY")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { writer(it) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "OmniKitMY")
                dir.mkdirs()
                val file = File(dir, displayName)
                FileOutputStream(file).use { writer(it) }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shareUri(context: Context, uri: Uri, mimeType: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        shareUri(context, uri, mimeType, chooserTitle)
    }
}
