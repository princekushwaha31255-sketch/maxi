package com.example.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import com.example.data.AttachmentPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object MaxFileAttachmentProcessor {

    suspend fun processUri(context: Context, uri: Uri): AttachmentPayload? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: guessMimeType(uri.toString())
            var fileName = "attachment"
            var fileSize = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: "attachment"
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) fileSize = cursor.getLong(sizeIndex)
                }
            }

            when {
                mimeType.startsWith("image/") -> {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val compressedBytes = if (bitmap != null) {
                        val scaled = scaleBitmapDown(bitmap, 1280)
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                        out.toByteArray()
                    } else {
                        bytes
                    }
                    val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
                    AttachmentPayload(
                        fileName = fileName,
                        mimeType = "image/jpeg",
                        base64Data = base64,
                        extractedTextPreview = "[Image attached: $fileName (${compressedBytes.size / 1024} KB)]",
                        sizeBytes = if (fileSize > 0) fileSize else compressedBytes.size.toLong()
                    )
                }

                mimeType == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true) -> {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
                    var pageCount = 0
                    try {
                        contentResolver.openFileDescriptor(uri, "r")?.use { pfd: ParcelFileDescriptor ->
                            PdfRenderer(pfd).use { renderer ->
                                pageCount = renderer.pageCount
                            }
                        }
                    } catch (_: Exception) {
                    }
                    // Cap inline PDF payload at 4 MB for fast multimodal request
                    val cappedBytes = if (bytes.size > 4 * 1024 * 1024) bytes.copyOf(4 * 1024 * 1024) else bytes
                    val base64 = Base64.encodeToString(cappedBytes, Base64.NO_WRAP)
                    AttachmentPayload(
                        fileName = fileName,
                        mimeType = "application/pdf",
                        base64Data = base64,
                        extractedTextPreview = "[PDF Document: $fileName${if (pageCount > 0) " • $pageCount pages" else ""} (${bytes.size / 1024} KB)]",
                        sizeBytes = if (fileSize > 0) fileSize else bytes.size.toLong()
                    )
                }

                else -> {
                    // Text, code, JSON, CSV, Markdown, Kotlin, Python, JS, HTML files
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
                    val textContent = bytes.decodeToString(throwOnInvalidSequence = false).take(24000)
                    val base64 = Base64.encodeToString(bytes.take(512 * 1024).toByteArray(), Base64.NO_WRAP)
                    AttachmentPayload(
                        fileName = fileName,
                        mimeType = mimeType.ifBlank { "text/plain" },
                        base64Data = base64,
                        extractedTextPreview = textContent,
                        sizeBytes = if (fileSize > 0) fileSize else bytes.size.toLong()
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val (newW, newH) = if (ratio > 1f) {
            maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun guessMimeType(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") -> "image/jpeg"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".csv") -> "text/csv"
            else -> "text/plain"
        }
    }
}
