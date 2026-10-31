package com.example.core.export

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles exporting high-resolution rendering snapshots to local JPG files.
 */
object ImageExporter {

    fun exportBitmapToJpgFile(
        context: Context,
        bitmap: Bitmap,
        projectName: String,
        quality: Int = 95
    ): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanName = projectName.replace("\\s+".toRegex(), "_")
        val file = File(exportDir, "RENDER_${cleanName}_$timeStamp.jpg")

        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }
        return file
    }
}
