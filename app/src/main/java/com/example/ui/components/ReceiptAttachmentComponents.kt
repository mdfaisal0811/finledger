package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.export.Formatters
import com.example.data.media.PreparedReceiptImage
import com.example.data.media.ReceiptImageManager
import com.example.domain.model.TransactionImage
import com.example.ui.theme.LocalFinLedgerColors
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed interface AttachmentItem {
    val itemId: String
    val fileName: String
    val fileType: String
    val fileSizeBytes: Long
    val uploadedAtMillis: Long

    data class Pending(
        val prepared: PreparedReceiptImage,
        override val itemId: String = UUID.randomUUID().toString(),
        override val uploadedAtMillis: Long = System.currentTimeMillis()
    ) : AttachmentItem {
        override val fileName: String get() = prepared.fileName
        override val fileType: String get() = prepared.fileType
        override val fileSizeBytes: Long get() = prepared.fileSizeBytes
    }

    data class Saved(
        val image: TransactionImage
    ) : AttachmentItem {
        override val itemId: String get() = image.id
        override val fileName: String get() = image.fileName
        override val fileType: String get() = image.fileType
        override val fileSizeBytes: Long get() = image.fileSizeBytes
        override val uploadedAtMillis: Long get() = image.uploadedAtMillis
    }

    data class LegacyUri(
        val uriString: String
    ) : AttachmentItem {
        override val itemId: String get() = "legacy_$uriString"
        override val fileName: String get() = uriString.substringAfterLast('/').ifBlank { "receipt.jpg" }
        override val fileType: String get() = "image/jpeg"
        override val fileSizeBytes: Long get() = 0L
        override val uploadedAtMillis: Long get() = 0L
    }

    fun coilModel(): Any {
        return when (this) {
            is Pending -> prepared.localFile
            is Saved -> {
                val local = image.localFilePath?.let { File(it) }
                if (local != null && local.exists()) local else (image.downloadUrl ?: "")
            }
            is LegacyUri -> uriString
        }
    }

    fun localPathOrNull(): String? = when (this) {
        is Pending -> prepared.localFile.absolutePath
        is Saved -> image.localFilePath
        is LegacyUri -> if (uriString.startsWith("file://")) Uri.parse(uriString).path else null
    }

    fun storageRefOrNull(): String? = when (this) {
        is Saved -> image.storageRefPath.takeIf { it.isNotBlank() }
        else -> null
    }

    fun downloadUrlOrNull(): String? = when (this) {
        is Saved -> image.downloadUrl
        is LegacyUri -> uriString
        is Pending -> null
    }
}

@Composable
fun ReceiptAttachmentSection(
    savedImages: List<TransactionImage>,
    pendingImages: List<PreparedReceiptImage>,
    legacyReceiptUri: String? = null,
    onAddPendingImages: (List<PreparedReceiptImage>) -> Unit,
    onRemovePendingImage: (PreparedReceiptImage) -> Unit,
    onReplacePendingImage: (PreparedReceiptImage, PreparedReceiptImage) -> Unit,
    onDeleteSavedImage: (TransactionImage) -> Unit,
    onReplaceSavedImage: (TransactionImage, PreparedReceiptImage) -> Unit,
    onClearLegacyReceipt: () -> Unit = {},
    readOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val semantic = LocalFinLedgerColors.current

    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var viewerInitialIndex by remember { mutableIntStateOf(-1) }
    var replacingTarget by remember { mutableStateOf<AttachmentItem?>(null) }

    val items = remember(savedImages, pendingImages, legacyReceiptUri) {
        buildList {
            savedImages.forEach { add(AttachmentItem.Saved(it)) }
            pendingImages.forEach { add(AttachmentItem.Pending(it)) }
            if (!legacyReceiptUri.isNullOrBlank()) {
                val alreadyRepresented = savedImages.any { it.localFilePath != null && legacyReceiptUri.contains(it.localFilePath) }
                if (!alreadyRepresented) add(AttachmentItem.LegacyUri(legacyReceiptUri))
            }
        }
    }

    fun processUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            isProcessing = true
            errorMessage = null
            val prepared = mutableListOf<PreparedReceiptImage>()
            val failures = mutableListOf<String>()
            for (uri in uris) {
                ReceiptImageManager.validateAndCompress(context, uri)
                    .onSuccess { prepared.add(it) }
                    .onFailure { failures.add(it.localizedMessage ?: "Unsupported image") }
            }
            if (prepared.isNotEmpty()) {
                onAddPendingImages(prepared)
            }
            if (failures.isNotEmpty()) {
                errorMessage = failures.first()
            }
            isProcessing = false
        }
    }

    fun processReplaceUri(target: AttachmentItem, uri: Uri) {
        scope.launch {
            isProcessing = true
            errorMessage = null
            ReceiptImageManager.validateAndCompress(context, uri)
                .onSuccess { newPrepared ->
                    when (target) {
                        is AttachmentItem.Pending -> onReplacePendingImage(target.prepared, newPrepared)
                        is AttachmentItem.Saved -> onReplaceSavedImage(target.image, newPrepared)
                        is AttachmentItem.LegacyUri -> {
                            onClearLegacyReceipt()
                            onAddPendingImages(listOf(newPrepared))
                        }
                    }
                }
                .onFailure { err ->
                    errorMessage = err.localizedMessage ?: "Failed to process replacement image."
                }
            replacingTarget = null
            isProcessing = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            val target = replacingTarget
            if (target != null) {
                processReplaceUri(target, uri)
            } else {
                processUris(listOf(uri))
            }
        } else {
            replacingTarget = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, _) = ReceiptImageManager.createCameraOutputUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            errorMessage = "Camera permission is required to capture receipts."
            replacingTarget = null
        }
    }

    fun launchCamera() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val (uri, _) = ReceiptImageManager.createCameraOutputUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryMultiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris -> processUris(uris) }

    val fileMultiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> processUris(uris) }

    val replacePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = replacingTarget
        if (uri != null && target != null) {
            processReplaceUri(target, uri)
        } else {
            replacingTarget = null
        }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Receipts & Bills",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    text = if (items.isEmpty()) "Optional" else "${items.size} attached",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (items.isEmpty()) semantic.textSecondary else MaterialTheme.colorScheme.primary
                )
            }

            if (!readOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            replacingTarget = null
                            launchCamera()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).testTag("receipt_camera_btn")
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Camera", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            replacingTarget = null
                            galleryMultiLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).testTag("receipt_gallery_btn")
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Gallery", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            replacingTarget = null
                            fileMultiLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).testTag("receipt_files_btn")
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Files", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
                Text(
                    text = "Supports multiple JPG, JPEG, PNG, WEBP images (auto-compressed).",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.textSecondary
                )
            }

            if (isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.expense
                )
            }

            if (items.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items.forEachIndexed { index, item ->
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { viewerInitialIndex = index }
                                .testTag("receipt_thumb_$index")
                        ) {
                            AsyncImage(
                                model = item.coilModel(),
                                contentDescription = "Receipt thumbnail ${item.fileName}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (!readOnly) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.62f),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(22.dp)
                                        .clickable {
                                            when (item) {
                                                is AttachmentItem.Pending -> onRemovePendingImage(item.prepared)
                                                is AttachmentItem.Saved -> onDeleteSavedImage(item.image)
                                                is AttachmentItem.LegacyUri -> onClearLegacyReceipt()
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "Delete image",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Surface(
                                    color = Color.Black.copy(alpha = 0.62f),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(22.dp)
                                        .clickable {
                                            replacingTarget = item
                                            replacePickerLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.FindReplace,
                                            contentDescription = "Replace image",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewerInitialIndex in items.indices) {
        FullScreenImageViewerDialog(
            items = items,
            initialIndex = viewerInitialIndex,
            readOnly = readOnly,
            onReplace = { item ->
                replacingTarget = item
                replacePickerLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
            },
            onDelete = { item ->
                when (item) {
                    is AttachmentItem.Pending -> onRemovePendingImage(item.prepared)
                    is AttachmentItem.Saved -> onDeleteSavedImage(item.image)
                    is AttachmentItem.LegacyUri -> onClearLegacyReceipt()
                }
                if (items.size <= 1) {
                    viewerInitialIndex = -1
                } else if (viewerInitialIndex >= items.size - 1) {
                    viewerInitialIndex = (items.size - 2).coerceAtLeast(0)
                }
            },
            onDismiss = { viewerInitialIndex = -1 }
        )
    }
}

@Composable
fun FullScreenImageViewerDialog(
    items: List<AttachmentItem>,
    initialIndex: Int = 0,
    readOnly: Boolean = false,
    onReplace: ((AttachmentItem) -> Unit)? = null,
    onDelete: ((AttachmentItem) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentIndex by remember(items.size) { mutableIntStateOf(initialIndex.coerceIn(0, items.lastIndex)) }
    val currentItem = items[currentIndex]

    var zoom by remember(currentIndex) { mutableFloatStateOf(1f) }
    var offset by remember(currentIndex) { mutableStateOf(Offset.Zero) }
    var isDownloading by remember { mutableStateOf(false) }

    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(currentItem.fileType)
    ) { destUri ->
        if (destUri != null) {
            scope.launch {
                isDownloading = true
                val res = ReceiptImageManager.saveImageToUserUri(
                    context = context,
                    destUri = destUri,
                    localFilePath = currentItem.localPathOrNull(),
                    storageRefPath = currentItem.storageRefOrNull(),
                    downloadUrl = currentItem.downloadUrlOrNull()
                )
                isDownloading = false
                Toast.makeText(
                    context,
                    if (res.isSuccess) "Image downloaded successfully" else "Download failed: ${res.exceptionOrNull()?.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF090D16),
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentItem.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = buildString {
                            append(currentItem.fileType.uppercase().removePrefix("IMAGE/"))
                            if (currentItem.fileSizeBytes > 0L) {
                                append(" • ")
                                append(Formatter.formatShortFileSize(context, currentItem.fileSizeBytes))
                            }
                            if (currentItem.uploadedAtMillis > 0L) {
                                append(" • ")
                                append(Formatters.formatDate(currentItem.uploadedAtMillis))
                            }
                        }
                        Text(meta, style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close viewer", tint = Color.White)
                    }
                }

                if (isDownloading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // Zoomable / Pannable Image Surface
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(currentIndex) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val newZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                zoom = newZoom
                                offset = if (newZoom > 1f) {
                                    Offset(offset.x + pan.x, offset.y + pan.y)
                                } else {
                                    Offset.Zero
                                }
                            }
                        }
                        .pointerInput(currentIndex) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoom > 1.2f) {
                                        zoom = 1f
                                        offset = Offset.Zero
                                    } else {
                                        zoom = 2.5f
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = currentItem.coilModel(),
                        contentDescription = currentItem.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoom,
                                scaleY = zoom,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )

                    if (items.size > 1) {
                        if (currentIndex > 0) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(12.dp)
                            ) {
                                IconButton(onClick = { currentIndex-- }) {
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous", tint = Color.White)
                                }
                            }
                        }
                        if (currentIndex < items.lastIndex) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(12.dp)
                            ) {
                                IconButton(onClick = { currentIndex++ }) {
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // Bottom Controls Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (items.size > 1) {
                            Text(
                                text = "Image ${currentIndex + 1} of ${items.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFCBD5E1)
                            )
                        } else {
                            Text(
                                text = "Pinch or tap + / - to zoom",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                zoom = (zoom - 0.5f).coerceAtLeast(1f)
                                if (zoom <= 1f) offset = Offset.Zero
                            }) {
                                Icon(Icons.Outlined.ZoomOut, contentDescription = "Zoom out", tint = Color.White)
                            }
                            TextButton(onClick = { zoom = 1f; offset = Offset.Zero }) {
                                Text("${(zoom * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Medium)
                            }
                            IconButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(5f) }) {
                                Icon(Icons.Outlined.ZoomIn, contentDescription = "Zoom in", tint = Color.White)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { downloadLauncher.launch(currentItem.fileName) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f).testTag("viewer_download_btn")
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Download", maxLines = 1)
                        }

                        if (!readOnly && onReplace != null) {
                            OutlinedButton(
                                onClick = { onReplace(currentItem) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f).testTag("viewer_replace_btn")
                            ) {
                                Icon(Icons.Outlined.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Replace", maxLines = 1)
                            }
                        }

                        if (!readOnly && onDelete != null) {
                            OutlinedButton(
                                onClick = { onDelete(currentItem) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5)),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5).copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f).testTag("viewer_delete_btn")
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
