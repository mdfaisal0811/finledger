package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID

data class PreparedReceiptImage(
    val localFile: File,
    val fileName: String,
    val fileType: String,
    val fileSizeBytes: Long
)

object ReceiptImageManager {

    private const val TAG = "ReceiptImageManager"
    const val MAX_RAW_INPUT_BYTES = 15L * 1024L * 1024L // 15 MB raw pick limit
    const val MAX_COMPRESSED_BYTES = 5L * 1024L * 1024L // 5 MB processed limit
    const val MAX_DIMENSION_PX = 1920

    val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
    val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    fun createCameraOutputUri(context: Context): Pair<Uri, File> {
        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(cameraDir, "cam_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return uri to file
    }

    /**
     * Validates image format (JPG, JPEG, PNG, WEBP) and size, applies EXIF orientation correction,
     * downscales oversized images while keeping receipt text sharp, and stores a compressed file in app storage.
     */
    suspend fun validateAndCompress(context: Context, sourceUri: Uri): Result<PreparedReceiptImage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cr = context.contentResolver
                val rawName = queryDisplayName(context, sourceUri)
                val detectedMime = detectAndValidateMimeType(context, sourceUri, rawName)
                    ?: throw IllegalArgumentException("Unsupported file format. Please choose JPG, JPEG, PNG, or WEBP.")

                val rawSize = queryFileSize(context, sourceUri)
                if (rawSize > MAX_RAW_INPUT_BYTES) {
                    throw IllegalArgumentException("Image exceeds 15 MB limit. Please select a smaller file.")
                }

                // 1. Decode bounds
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                cr.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, boundsOpts)
                } ?: throw IllegalArgumentException("Unable to read selected image.")

                if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                    throw IllegalArgumentException("Selected file is not a valid image.")
                }

                // 2. Compute power-of-two sample size
                val sampleSize = computeInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, MAX_DIMENSION_PX)
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = cr.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOpts)
                } ?: throw IllegalArgumentException("Could not decode receipt image.")

                // 3. Apply EXIF rotation + exact scaling
                val oriented = applyExifOrientation(context, sourceUri, decoded)
                val scaled = scaleDownIfNeeded(oriented, MAX_DIMENSION_PX)
                if (oriented !== decoded && !decoded.isRecycled) decoded.recycle()
                if (scaled !== oriented && !oriented.isRecycled) oriented.recycle()

                // 4. Compress keeping receipt readability
                val (compressFormat, normalizedExt, normalizedMime) = resolveCompressFormat(detectedMime)
                val outBytes = compressWithAdaptiveQuality(scaled, compressFormat)
                if (!scaled.isRecycled) scaled.recycle()

                if (outBytes.size > MAX_COMPRESSED_BYTES) {
                    throw IllegalArgumentException("Compressed image still exceeds 5 MB limit.")
                }

                val receiptsDir = File(context.filesDir, "receipts").apply { mkdirs() }
                val cleanBase = (rawName?.substringBeforeLast('.') ?: "receipt_${System.currentTimeMillis()}")
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    .take(40)
                    .ifBlank { "receipt" }
                val finalFileName = "${cleanBase}_${System.currentTimeMillis().toString().takeLast(6)}.$normalizedExt"
                val destFile = File(receiptsDir, finalFileName)
                FileOutputStream(destFile).use { it.write(outBytes) }

                PreparedReceiptImage(
                    localFile = destFile,
                    fileName = finalFileName,
                    fileType = normalizedMime,
                    fileSizeBytes = outBytes.size.toLong()
                )
            }
        }

    /**
     * Uploads a compressed receipt image to Firebase Storage under `users/{uid}/transactions/{txId}/`.
     * Returns download URL on success, or null if Storage bucket is unavailable/offline (so local fallback remains usable).
     */
    suspend fun uploadToFirebaseStorage(
        storageRefPath: String,
        localFile: File,
        mimeType: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val storageRef = FirebaseStorage.getInstance().reference.child(storageRefPath)
            val metadata = storageMetadata { contentType = mimeType }
            storageRef.putFile(Uri.fromFile(localFile), metadata).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Storage upload skipped/deferred ($storageRefPath): ${e.message}")
            null
        }
    }

    suspend fun deleteFromFirebaseStorage(storageRefPath: String) = withContext(Dispatchers.IO) {
        if (storageRefPath.isBlank()) return@withContext
        try {
            FirebaseStorage.getInstance().reference.child(storageRefPath).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Storage delete skipped ($storageRefPath): ${e.message}")
        }
    }

    /**
     * Downloads an original/stored receipt image (from local cache, Firebase Storage ref, or HTTP URL)
     * into a user-selected SAF destination URI.
     */
    suspend fun saveImageToUserUri(
        context: Context,
        destUri: Uri,
        localFilePath: String?,
        storageRefPath: String?,
        downloadUrl: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes: ByteArray = when {
                !localFilePath.isNullOrBlank() && File(localFilePath).exists() -> {
                    File(localFilePath).readBytes()
                }
                !storageRefPath.isNullOrBlank() -> {
                    try {
                        FirebaseStorage.getInstance().reference
                            .child(storageRefPath)
                            .getBytes(MAX_COMPRESSED_BYTES * 2)
                            .await()
                    } catch (e: Exception) {
                        if (!downloadUrl.isNullOrBlank() && downloadUrl.startsWith("http")) {
                            URL(downloadUrl).openStream().use { it.readBytes() }
                        } else if (!downloadUrl.isNullOrBlank()) {
                            val u = Uri.parse(downloadUrl)
                            context.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: throw e
                        } else {
                            throw e
                        }
                    }
                }
                !downloadUrl.isNullOrBlank() && downloadUrl.startsWith("http") -> {
                    URL(downloadUrl).openStream().use { it.readBytes() }
                }
                !downloadUrl.isNullOrBlank() -> {
                    val u = Uri.parse(downloadUrl)
                    context.contentResolver.openInputStream(u)?.use { it.readBytes() }
                        ?: error("Image file no longer exists on device.")
                }
                else -> error("No image source available to download.")
            }

            context.contentResolver.openOutputStream(destUri)?.use { out ->
                out.write(bytes)
            } ?: error("Unable to write to destination file.")
        }
    }

    fun detectAndValidateMimeType(context: Context, uri: Uri, displayName: String?): String? {
        val resolverType = context.contentResolver.getType(uri)?.lowercase()
        if (resolverType != null && resolverType in ALLOWED_MIME_TYPES) {
            return if (resolverType == "image/jpg") "image/jpeg" else resolverType
        }
        val extFromName = displayName?.substringAfterLast('.', "")?.lowercase()
        val extFromPath = MimeTypeMap.getFileExtensionFromUrl(uri.toString())?.lowercase()
        val ext = listOfNotNull(extFromName, extFromPath).firstOrNull { it in ALLOWED_EXTENSIONS }
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).length() } ?: 0L
        }
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        while (width / inSampleSize > maxDim * 2 || height / inSampleSize > maxDim * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = if (w >= h) maxDim.toFloat() / w else maxDim.toFloat() / h
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun resolveCompressFormat(mimeType: String): Triple<Bitmap.CompressFormat, String, String> {
        return when (mimeType.lowercase()) {
            "image/png" -> Triple(Bitmap.CompressFormat.PNG, "png", "image/png")
            "image/webp" -> {
                val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                Triple(fmt, "webp", "image/webp")
            }
            else -> Triple(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
        }
    }

    private fun compressWithAdaptiveQuality(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat
    ): ByteArray {
        if (format == Bitmap.CompressFormat.PNG) {
            val pngStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngStream)
            val bytes = pngStream.toByteArray()
            if (bytes.size <= 1_800_000) return bytes
            // Large PNG receipt (>1.8 MB): preserve lossless quality up to MAX_COMPRESSED_BYTES or re-encode crisp 90% JPEG/WebP
            if (bytes.size <= MAX_COMPRESSED_BYTES) return bytes
        }
        var quality = 88
        var data: ByteArray
        do {
            val baos = ByteArrayOutputStream()
            val targetFormat = if (format == Bitmap.CompressFormat.PNG) Bitmap.CompressFormat.JPEG else format
            bitmap.compress(targetFormat, quality, baos)
            data = baos.toByteArray()
            quality -= 10
        } while (data.size > 1_600_000 && quality >= 55)
        return data
    }
}
