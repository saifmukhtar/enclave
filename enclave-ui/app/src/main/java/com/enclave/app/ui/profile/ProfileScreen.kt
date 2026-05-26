@file:OptIn(ExperimentalMaterial3Api::class)
package com.enclave.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.data.local.UserProfileEntity
import com.enclave.app.ui.profile.components.ProfileHeader
import com.enclave.app.ui.profile.components.EnclaveTextField
import com.enclave.app.ui.profile.components.E2eeAvatar
import com.enclave.app.ui.profile.components.InvitePartnerDialog

// Hardcoded colors removed in favor of MaterialTheme.colorScheme

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onClose: () -> Unit
) {
    val myProfile by viewModel.myProfile.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    var editUsername    by remember(myProfile) { mutableStateOf(myProfile?.username ?: "") }
    var editDisplayName by remember(myProfile) { mutableStateOf(myProfile?.displayName ?: "") }
    var selectedMood    by remember(myProfile) {
        val bioText = myProfile?.bio ?: ""
        var foundMood = "❤️"
        for (emoji in MOOD_EMOJIS) {
            if (bioText.startsWith(emoji)) {
                foundMood = emoji
                break
            }
        }
        mutableStateOf(foundMood)
    }
    var editBio         by remember(myProfile) {
        var bioText = myProfile?.bio ?: ""
        for (emoji in MOOD_EMOJIS) {
            if (bioText.startsWith(emoji)) {
                bioText = bioText.removePrefix(emoji).trimStart()
                break
            }
        }
        mutableStateOf(bioText)
    }
    var showMoodPicker  by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var pendingAvatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var avatarBitmap by remember(myProfile?.avatarLocalPath, pendingAvatarBytes) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }

    LaunchedEffect(myProfile?.avatarLocalPath, pendingAvatarBytes) {
        if (pendingAvatarBytes != null) {
            val bmp = android.graphics.BitmapFactory.decodeByteArray(pendingAvatarBytes, 0, pendingAvatarBytes!!.size)
            avatarBitmap = bmp?.asImageBitmap()
        } else if (!myProfile?.avatarLocalPath.isNullOrEmpty()) {
            val bytes = viewModel.decryptLocalAvatar(myProfile!!.avatarLocalPath)
            if (bytes != null) {
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                avatarBitmap = bmp?.asImageBitmap()
            }
        } else {
            avatarBitmap = null
        }
    }

    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBytes = inputStream?.readBytes()
                inputStream?.close()
                if (originalBytes != null) {
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
                    if (bitmap != null) {
                        val size = minOf(bitmap.width, bitmap.height)
                        val x = (bitmap.width - size) / 2
                        val y = (bitmap.height - size) / 2
                        val croppedBitmap = android.graphics.Bitmap.createBitmap(bitmap, x, y, size, size)
                        val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(croppedBitmap, 512, 512, true)
                        val outputStream = java.io.ByteArrayOutputStream()
                        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
                        pendingAvatarBytes = outputStream.toByteArray()
                        
                        // Clean up
                        if (bitmap != croppedBitmap) bitmap.recycle()
                        if (croppedBitmap != scaledBitmap) croppedBitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileScreen", "Failed to load/compress avatar", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header gradient bar ──────────────────────────────────────
            ProfileHeader(
                avatarBitmap = avatarBitmap,
                editDisplayName = editDisplayName,
                editUsername = editUsername,
                selectedMood = selectedMood,
                onAvatarClick = {
                    com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                    pickAvatarLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onMoodClick = { showMoodPicker = true }
            )

            // ── Title ────────────────────────────────────────────────────
            Text(
                text = "My Profile",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                textAlign = TextAlign.Center,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Visible only inside Enclave · E2EE",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )

            Spacer(Modifier.height(24.dp))

            // ── Edit fields ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EnclaveTextField(
                    value = editUsername,
                    onValueChange = { editUsername = it.lowercase().replace(" ", "_").take(30) },
                    label = "Username",
                    placeholder = "e.g. your_username",
                    leadingText = "@",
                    icon = Icons.Default.AlternateEmail
                )

                EnclaveTextField(
                    value = editDisplayName,
                    onValueChange = { editDisplayName = it.take(40) },
                    label = "Display Name",
                    placeholder = "How your partner sees you",
                    icon = Icons.Default.Person
                )

                EnclaveTextField(
                    value = editBio,
                    onValueChange = { editBio = it.take(140) },
                    label = "Bio",
                    placeholder = "Something sweet or mysterious…",
                    icon = Icons.Default.Edit,
                    maxLines = 3,
                    trailingText = "${editBio.length}/140"
                )

                // Mood emoji row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { showMoodPicker = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mood, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Current Mood", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            Text("Tap to change", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    }
                    Text(selectedMood, fontSize = 28.sp)
                }

                Spacer(Modifier.height(8.dp))

                // Save button
                Button(
                    onClick = {
                        var cleanedBio = editBio.trim()
                        var foundPrefix = true
                        while (foundPrefix) {
                            foundPrefix = false
                            for (emoji in MOOD_EMOJIS) {
                                if (cleanedBio.startsWith(emoji)) {
                                    cleanedBio = cleanedBio.removePrefix(emoji).trimStart()
                                    foundPrefix = true
                                    break
                                }
                            }
                        }
                        viewModel.saveProfile(
                            username = editUsername,
                            displayName = editDisplayName,
                            bio = "$selectedMood $cleanedBio".trim(),
                            avatarBytes = pendingAvatarBytes
                        )
                        pendingAvatarBytes = null
                    },
                    enabled = !isSaving && editUsername.length >= 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save & Sync Profile", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = { showInviteDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Invite Partner", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        // ── Mood Picker Bottom Sheet ─────────────────────────────────────
        if (showMoodPicker) {
            AlertDialog(
                onDismissRequest = { showMoodPicker = false },
                title = { Text("Choose Your Mood", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        val rows = MOOD_EMOJIS.chunked(5)
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                row.forEach { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(if (emoji == selectedMood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                                            .clickable {
                                                selectedMood = emoji
                                                showMoodPicker = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(emoji, fontSize = 20.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMoodPicker = false }) {
                        Text("Done", color = MaterialTheme.colorScheme.primary)
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            )
        }

        if (showInviteDialog && myProfile?.userId != null) {
            InvitePartnerDialog(
                myId = myProfile!!.userId,
                onDismiss = { showInviteDialog = false }
            )
        }

        // Fixed Floating Back Button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                .zIndex(2f)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}


