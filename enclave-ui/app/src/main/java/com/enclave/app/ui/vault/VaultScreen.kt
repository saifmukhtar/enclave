package com.enclave.app.ui.vault

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.data.local.MediaMetadataEntity
import com.enclave.app.data.vault.VaultRepository
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.BackHandler
import com.enclave.app.ui.vault.components.VaultGrid
import com.enclave.app.ui.vault.components.FullscreenMediaPager

import com.enclave.app.ui.theme.InterFont
import com.enclave.app.ui.theme.PlayfairFont

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    biometricManager: BiometricPromptManager,
    vaultRepository: VaultRepository
) {
    val authState by biometricManager.authState.collectAsState()
    val context = LocalContext.current

    // Horizontal Folder chip bar states
    val folders by viewModel.folders.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    var showCreateAlbumDialog by remember { mutableStateOf(false) }

    // Multi-Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<MediaMetadataEntity>() }

    // Pager states
    val vaultItems by viewModel.vaultItems.collectAsState()
    var activePagerIndex by remember { mutableStateOf(-1) }

    BackHandler(enabled = activePagerIndex != -1 || showCreateAlbumDialog || isSelectionMode) {
        if (activePagerIndex != -1) {
            activePagerIndex = -1
        } else if (showCreateAlbumDialog) {
            showCreateAlbumDialog = false
        } else if (isSelectionMode) {
            isSelectionMode = false
            selectedItems.clear()
        }
    }

    val pendingPermission by viewModel.pendingPermissionIntent.collectAsState()
    val mediaHandler = rememberVaultMediaHandler(
        viewModel = viewModel,
        context = context,
        pendingPermission = pendingPermission
    )

    LaunchedEffect(authState) {
        if (authState == BiometricPromptManager.AuthState.LOCKED) {
            biometricManager.authenticate(
                title = "Unlock Vault",
                subtitle = "Confirm fingerprint or PIN to view encrypted files"
            )
            viewModel.clearVaultItems()
        } else if (authState == BiometricPromptManager.AuthState.UNLOCKED) {
            viewModel.syncVault()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFFF5F6) // Blush minimal base background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val isUnlocked = authState == BiometricPromptManager.AuthState.UNLOCKED

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (!isUnlocked) 16.dp else 0.dp)
            ) {
                // 1. Soft Blushing Folder Chip bar with "New Album" trigger
                if (isUnlocked) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(folders) { folder ->
                                val active = folder == selectedFolder
                                FilterChip(
                                    selected = active,
                                    onClick = { viewModel.selectFolder(folder) },
                                    label = { Text(folder, fontFamily = InterFont) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFE598A7),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFFFCE2E6),
                                        labelColor = Color(0xFF2A1B1D)
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { showCreateAlbumDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFE598A7).copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Album",
                                tint = Color(0xFFE598A7),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 2. Main Vault Grid
                VaultGrid(
                    viewModel = viewModel,
                    vaultRepository = vaultRepository,
                    isUnlocked = isUnlocked,
                    isSelectionMode = isSelectionMode,
                    selectedItems = selectedItems,
                    onItemClick = { index ->
                        activePagerIndex = index
                    },
                    onToggleSelectionMode = { active ->
                        isSelectionMode = active
                        if (!active) selectedItems.clear()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Selection Actions Overlay Bar
            if (isUnlocked && isSelectionMode && selectedItems.isNotEmpty()) {
                var showMoveDialog by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFFFFF5F6),
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE598A7).copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.shredItems(selectedItems.map { it.localEncryptedPath })
                                    selectedItems.clear()
                                    isSelectionMode = false
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Shred", tint = Color(0xFFD32F2F))
                            }

                            IconButton(
                                onClick = {
                                    viewModel.exportItems(selectedItems.map { it.localEncryptedPath }, context)
                                    selectedItems.clear()
                                    isSelectionMode = false
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Export", tint = Color(0xFFE598A7))
                            }

                            IconButton(
                                onClick = { showMoveDialog = true }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Move Album", tint = Color(0xFF2A1B1D))
                            }

                            IconButton(
                                onClick = {
                                    isSelectionMode = false
                                    selectedItems.clear()
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Gray)
                            }
                        }
                    }

                    if (showMoveDialog) {
                        AlertDialog(
                            onDismissRequest = { showMoveDialog = false },
                            title = { Text("Move to Album", fontFamily = PlayfairFont) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    folders.forEach { folder ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.moveItemsToFolder(
                                                        selectedItems.map { it.mediaId },
                                                        folder
                                                    )
                                                    selectedItems.clear()
                                                    isSelectionMode = false
                                                    showMoveDialog = false
                                                }
                                                .padding(vertical = 12.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Color(0xFFE598A7))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(folder, fontFamily = InterFont, fontSize = 16.sp)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showMoveDialog = false }) {
                                    Text("Cancel", color = Color(0xFFE598A7))
                                }
                            }
                        )
                    }
                }
            }

            // 4. Floating Media Importer Trigger
            if (isUnlocked && !isSelectionMode) {
                FloatingActionButton(
                    onClick = {
                        com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                    mediaHandler.pickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                    },
                    containerColor = Color(0xFFFCE2E6),
                    contentColor = Color(0xFFE598A7),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 24.dp, end = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Import Media",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 5. Authentication Overlay for Locked State
            if (!isUnlocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFFF5F6).copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Vault Locked",
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFF2A1B1D)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (authState == BiometricPromptManager.AuthState.ERROR || authState == BiometricPromptManager.AuthState.LOCKED) {
                            Button(
                                onClick = {
                                    biometricManager.authenticate(
                                        title = "Unlock Vault",
                                        subtitle = "Confirm fingerprint or PIN to view encrypted files"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1B1D))
                            ) {
                                Text("Unlock Vault", color = Color(0xFFFFF5F6))
                            }
                        }
                    }
                }
            }

            // 6. Dialog: Create Album
            if (showCreateAlbumDialog) {
                var newAlbumName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showCreateAlbumDialog = false },
                    title = { Text("New Album Name", fontFamily = PlayfairFont) },
                    text = {
                        TextField(
                            value = newAlbumName,
                            onValueChange = { newAlbumName = it },
                            placeholder = { Text("Album name...") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newAlbumName.isNotBlank()) {
                                    viewModel.createFolder(newAlbumName)
                                }
                                showCreateAlbumDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7))
                        ) {
                            Text("Create")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateAlbumDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                )
            }

            // 7. Fullscreen Image/Video Horizontal Pager Overlay
            if (isUnlocked && activePagerIndex >= 0 && activePagerIndex < vaultItems.size) {
                FullscreenMediaPager(
                    items = vaultItems,
                    initialIndex = activePagerIndex,
                    repository = vaultRepository,
                    onClose = { activePagerIndex = -1 }
                )
            }
        }
    }
}


