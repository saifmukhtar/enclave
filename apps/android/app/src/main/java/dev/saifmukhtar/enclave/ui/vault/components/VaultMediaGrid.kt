package dev.saifmukhtar.enclave.ui.vault.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saifmukhtar.enclave.data.local.MediaMetadataEntity
import dev.saifmukhtar.enclave.data.vault.VaultRepository
import dev.saifmukhtar.enclave.media.EncryptedFileDataSource
import dev.saifmukhtar.enclave.ui.theme.InterFont
import dev.saifmukhtar.enclave.ui.vault.VaultViewModel

fun LazyGridState.getItemIndexAtOffset(offset: Offset): Int? {
    val layoutInfo = this.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val gridX = offset.x
    val gridY = offset.y
    for (item in visibleItems) {
        val itemX = item.offset.x.toFloat()
        val itemY = item.offset.y.toFloat()
        val itemWidth = item.size.width.toFloat()
        val itemHeight = item.size.height.toFloat()
        if (gridX >= itemX && gridX <= itemX + itemWidth &&
            gridY >= itemY && gridY <= itemY + itemHeight) {
            return item.index
        }
    }
    return null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultGrid(
    viewModel: VaultViewModel,
    vaultRepository: VaultRepository,
    isUnlocked: Boolean,
    isSelectionMode: Boolean,
    selectedItems: MutableList<MediaMetadataEntity>,
    onItemClick: (Int) -> Unit,
    onToggleSelectionMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val vaultItems by viewModel.vaultItems.collectAsState()

    val displayItems = if (isUnlocked) vaultItems else List(12) {
        MediaMetadataEntity("mock_$it", "", "", "image/jpeg", 0, false, null)
    }

    if (isUnlocked && displayItems.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Secure Vault is empty.\nTap + to import E2EE media.",
                fontFamily = InterFont,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                fontSize = 16.sp
            )
        }
    } else {
        val gridState = rememberLazyGridState()
        var startDragIndex by remember { mutableStateOf<Int?>(null) }
        var dragModeIsSelect by remember { mutableStateOf(true) }
        var pressedItemIndex by remember { mutableStateOf<Int?>(null) }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(8.dp),
            modifier = modifier
                .fillMaxSize()
                .pointerInput(isUnlocked, isSelectionMode, displayItems) {
                    if (!isUnlocked || displayItems.isEmpty()) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val index = gridState.getItemIndexAtOffset(offset)
                            if (index != null && index < displayItems.size) {
                                startDragIndex = index
                                val entity = displayItems[index]
                                if (!isSelectionMode) {
                                    onToggleSelectionMode(true)
                                    selectedItems.add(entity)
                                    dragModeIsSelect = true
                                } else {
                                    if (selectedItems.contains(entity)) {
                                        selectedItems.remove(entity)
                                        dragModeIsSelect = false
                                    } else {
                                        selectedItems.add(entity)
                                        dragModeIsSelect = true
                                    }
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            val index = gridState.getItemIndexAtOffset(change.position)
                            val start = startDragIndex
                            if (index != null && start != null && index < displayItems.size) {
                                val range = if (start <= index) start..index else index..start
                                for (i in range) {
                                    if (i >= 0 && i < displayItems.size) {
                                        val item = displayItems[i]
                                        if (dragModeIsSelect) {
                                            if (!selectedItems.contains(item)) {
                                                selectedItems.add(item)
                                            }
                                        } else {
                                            selectedItems.remove(item)
                                        }
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            startDragIndex = null
                        },
                        onDragCancel = {
                            startDragIndex = null
                        }
                    )
                }
                .pointerInput(isUnlocked, isSelectionMode, displayItems) {
                    if (!isUnlocked || displayItems.isEmpty()) return@pointerInput
                    detectTapGestures(
                        onPress = { offset ->
                            val index = gridState.getItemIndexAtOffset(offset)
                            if (index != null) {
                                pressedItemIndex = index
                                try {
                                    awaitRelease()
                                } finally {
                                    pressedItemIndex = null
                                }
                            }
                        },
                        onTap = { offset ->
                            val index = gridState.getItemIndexAtOffset(offset)
                            if (index != null && index < displayItems.size) {
                                val entity = displayItems[index]
                                if (isSelectionMode) {
                                    if (selectedItems.contains(entity)) {
                                        selectedItems.remove(entity)
                                    } else {
                                        selectedItems.add(entity)
                                    }
                                } else {
                                    onItemClick(index)
                                }
                            }
                        }
                    )
                }
        ) {
            items(displayItems.size) { index ->
                val entity = displayItems[index]
                val fileName = entity.localEncryptedPath

                val isSelected = selectedItems.contains(entity)
                val isPressed = pressedItemIndex == index
                val cardScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.94f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "vault_card_scale"
                )

                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(1f)
                        .graphicsLayer(scaleX = cardScale, scaleY = cardScale)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFCE2E6))
                        .then(
                            if (isSelected && isUnlocked) {
                                Modifier.border(2.dp, Color(0xFFE598A7), RoundedCornerShape(16.dp))
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (isUnlocked && !fileName.startsWith("mock_") && fileName.isNotEmpty()) {
                        val isVideo = entity.mimeType.startsWith("video/")
                        val imagePath = if (isVideo && entity.thumbnailPath.isNotEmpty()) {
                            entity.thumbnailPath
                        } else {
                            fileName
                        }

                        if (isVideo && entity.thumbnailPath.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    tint = Color(0xFFE598A7),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        } else {
                            val bitmapState = produceState<Bitmap?>(initialValue = null, imagePath) {
                                value = vaultRepository.getDecryptedImageBitmap(imagePath, 512, 512)
                            }
                            bitmapState.value?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = fileName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (isVideo) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isSelectionMode && isUnlocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isSelected) Color(0xFFE598A7).copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                                    .background(
                                        if (isSelected) Color(0xFFE598A7) else Color.Black.copy(alpha = 0.3f),
                                        CircleShape
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .align(Alignment.TopEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
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

@OptIn(ExperimentalFoundationApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun FullscreenMediaPager(
    items: List<MediaMetadataEntity>,
    initialIndex: Int,
    repository: VaultRepository,
    viewModel: VaultViewModel,
    isSelectionMode: Boolean,
    selectedItems: MutableList<MediaMetadataEntity>,
    onToggleSelection: (MediaMetadataEntity) -> Unit,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { items.size })
    val context = LocalContext.current

    var dragOffsetY by remember { mutableStateOf(0f) }
    val backgroundAlpha by animateFloatAsState(
        targetValue = (1f - (kotlin.math.abs(dragOffsetY) / 800f)).coerceIn(0f, 1f),
        label = "bg_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            if (page < items.size) {
                val entity = items[page]
                val fileName = entity.localEncryptedPath
                val isVideo = entity.mimeType.startsWith("video/")

                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(scale) {
                            if (scale == 1f) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                    },
                                    onDragEnd = {
                                        if (kotlin.math.abs(dragOffsetY) > 200f) {
                                            onClose()
                                        } else {
                                            dragOffsetY = 0f
                                        }
                                    },
                                    onDragCancel = {
                                        dragOffsetY = 0f
                                    }
                                )
                            }
                        }
                        .graphicsLayer {
                            translationY = dragOffsetY
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideo) {
                        SecureVideoPlayer(
                            fileName = fileName,
                            repository = repository,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val bitmapState = produceState<Bitmap?>(initialValue = null, fileName) {
                            value = repository.getDecryptedImageBitmap(fileName, 0, 0)
                        }

                        bitmapState.value?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = fileName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                scale = if (scale > 1f) 1f else 3f
                                                offset = Offset.Zero
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 5f)
                                            if (scale > 1f) {
                                                offset += pan
                                            } else {
                                                offset = Offset.Zero
                                            }
                                        }
                                    }
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Top Header Bar
        val currentItem = if (pagerState.currentPage < items.size) items[pagerState.currentPage] else null
        val isSelected = currentItem?.let { selectedItems.contains(it) } ?: false

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "${pagerState.currentPage + 1} of ${items.size}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                fontFamily = InterFont,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            IconButton(
                onClick = {
                    currentItem?.let { onToggleSelection(it) }
                },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            if (isSelected) Color(0xFFE598A7) else Color.Transparent,
                            CircleShape
                        )
                        .border(
                            width = 1.5.dp,
                            color = Color.White,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Action Overlay at the bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 32.dp, end = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pagerState.currentPage < items.size) {
                val activeEntity = items[pagerState.currentPage]
                val activeFileName = activeEntity.localEncryptedPath

                IconButton(
                    onClick = {
                        viewModel.exportToPublic(activeFileName, context)
                    },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export File",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.shredFile(activeFileName)
                        if (items.size <= 1) {
                            onClose()
                        }
                    },
                    modifier = Modifier
                        .background(Color.Red.copy(alpha = 0.2f), CircleShape)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Shred File",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}



@Composable
fun SecureVideoPlayer(
    fileName: String,
    repository: VaultRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember(fileName) {
        val fileDataSource = EncryptedFileDataSource(repository.encryptedFileManager, fileName)
        val dataSourceFactory = androidx.media3.datasource.DataSource.Factory { fileDataSource }
        val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(androidx.media3.common.MediaItem.fromUri(Uri.parse("secure://vault/$fileName")))

        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
