package com.example.core.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android Share Sheet and FileProvider integration helper for offline file distribution.
 */
object ShareExportHelper {

    /**
     * Shares a local file via Android's native Share Sheet.
     */
    fun shareFile(
        context: Context,
        file: File,
        mimeType: String,
        dialogTitle: String = "مشاركة الملف"
    ) {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, dialogTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    /**
     * MIME types for the various export formats.
     */
    const val MIME_PDF = "application/pdf"
    const val MIME_DXF = "application/dxf"
    const val MIME_JSON = "application/json"
    const val MIME_ROOM = "application/octet-stream"
    const val MIME_JPEG = "image/jpeg"
    const val MIME_APK = "application/vnd.android.package-archive"

    /**
     * Copies the currently running APK package into cache/exports/Kitchen3DStudio.apk
     * and opens the Android Share Sheet to save or share the APK file directly.
     */
    fun exportAndShareInstalledApk(context: Context): File {
        val sourceApk = File(context.applicationInfo.sourceDir)
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val targetApk = File(exportDir, "Kitchen3DStudio_Release.apk")
        sourceApk.copyTo(targetApk, overwrite = true)
        shareFile(
            context = context,
            file = targetApk,
            mimeType = MIME_APK,
            dialogTitle = "تصدير ومشاركة ملف التطبيق (Kitchen3DStudio.apk)"
        )
        return targetApk
    }
}
