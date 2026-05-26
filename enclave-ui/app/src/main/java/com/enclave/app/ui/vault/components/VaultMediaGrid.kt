package com.enclave.app.ui.vault.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
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
import com.enclave.app.data.local.MediaMetadataEntity
import com.enclave.app.data.vault.VaultRepository
import com.enclave.app.media.EncryptedFileDataSource
import com.enclave.app.ui.theme.InterFont
import com.enclave.app.ui.vault.VaultViewModel

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
    val context = LocalContext.current

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
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(8.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(displayItems.size) { index ->
                val entity = displayItems[index]
                val fileName = entity.localEncryptedPath
                var expandedMenu by remember { mutableStateOf(false) }

                val isSelected = selectedItems.contains(entity)

                val isCardPressed = remember { mutableStateOf(false) }
                val cardScale by animateFloatAsState(
                    targetValue = if (isCardPressed.value) 0.94f else 1.0f,
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
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isCardPressed.value = true
                                    try {
                                        awaitRelease()
                                    } finally {
                                        isCardPressed.value = false
                                    }
                                },
                                onLongPress = {
                                    if (isUnlocked) {
                                        if (!isSelectionMode) {
                                            onToggleSelectionMode(true)
                                            selectedItems.add(entity)
                                        } else {
                                            expandedMenu = true
                                        }
                                    }
                                },
                                onTap = {
                                    if (isUnlocked) {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedItems.remove(entity)
                                            else selectedItems.add(entity)
                                        } else {
                                            onItemClick(index)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    if (isUnlocked && !fileName.startsWith("mock_") && fileName.isNotEmpty()) {
                        val isVideo = entity.mimeType.startsWith("video/")
                        val imagePath = if (isVideo && entity.thumbnailPath.isNotEmpty()) {
                            entity.thumbnailPath
                        } else {
                            fileName
                        }

                        if (isVideo && entity.thumbnailPath.isEmpty()) {
                            // Fallback video player card
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
                                    if (isSelected) Color(0xFFE598A7).copy(alpha = 0.4f)
                                    else Color.Transparent
                                )
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(20.dp)
                                        .background(Color(0xFFE598A7), CircleShape)
                                        .align(Alignment.TopStart),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false },
                        modifier = Modifier.background(Color(0xFFFFF5F6))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export to Public Gallery", color = Color(0xFF2A1B1D)) },
                            onClick = {
                                expandedMenu = false
                                viewModel.exportToPublic(fileName, context)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Securely Shred File", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold) },
                            onClick = {
                                expandedMenu = false
                                viewModel.shredFile(fileName)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenMediaPager(
    items: List<MediaMetadataEntity>,
    initialIndex: Int,
    repository: VaultRepository,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { items.size })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val entity = items[page]
            val fileName = entity.localEncryptedPath
            val isVideo = entity.mimeType.startsWith("video/")

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isVideo) {
                    SecureVideoPlayer(
                        fileName = fileName,
                        repository = repository,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    ZoomableImage(
                        fileName = fileName,
                        repository = repository
                    )
                }
            }
        }

        // Close Trigger Button overlay
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .padding(24.dp)
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Pager",
                tint = Color.White
            )
        }
    }
}

@Composable
fun ZoomableImage(
    fileName: String,
    repository: VaultRepository
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

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
