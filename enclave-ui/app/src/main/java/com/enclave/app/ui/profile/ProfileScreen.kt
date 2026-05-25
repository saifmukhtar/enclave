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

private val BlushBg     = Color(0xFFFFF5F6)
private val BlushAccent = Color(0xFFE598A7)
private val BlushCard   = Color(0xFFFCE2E6)
private val Charcoal    = Color(0xFF2A1B1D)

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
            .background(BlushBg)
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
                color = Charcoal
            )
            Text(
                text = "Visible only inside Enclave · E2EE",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = Charcoal.copy(alpha = 0.45f)
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
                        .background(BlushCard)
                        .clickable { showMoodPicker = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mood, contentDescription = null, tint = BlushAccent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Current Mood", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Charcoal)
                            Text("Tap to change", fontSize = 11.sp, color = Charcoal.copy(alpha = 0.5f))
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
                    colors = ButtonDefaults.buttonColors(containerColor = BlushAccent)
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BlushAccent),
                    border = BorderStroke(1.dp, BlushAccent)
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
                                            .background(if (emoji == selectedMood) BlushAccent else BlushCard)
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
                        Text("Done", color = BlushAccent)
                    }
                },
                containerColor = BlushBg
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
                .background(Color.White.copy(alpha = 0.8f), CircleShape)
                .zIndex(2f)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Charcoal)
        }
    }
}

@Composable
private fun EnclaveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    leadingText: String? = null,
    trailingText: String? = null,
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(placeholder, color = Charcoal.copy(alpha = 0.35f), fontSize = 13.sp) },
        leadingIcon = if (leadingText != null) ({
            Text(leadingText, color = BlushAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(start = 12.dp))
        }) else ({
            Icon(icon, contentDescription = null, tint = BlushAccent, modifier = Modifier.size(18.dp))
        }),
        trailingIcon = if (trailingText != null) ({
            Text(trailingText, fontSize = 10.sp, color = Charcoal.copy(alpha = 0.4f),
                modifier = Modifier.padding(end = 8.dp))
        }) else null,
        maxLines = maxLines,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BlushAccent,
            unfocusedBorderColor = BlushCard,
            focusedLabelColor = BlushAccent
        )
    )
}

@Composable
fun E2eeAvatar(
    avatarBase64: String?,
    isMe: Boolean,
    profileViewModel: ProfileViewModel?,
    initials: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    displayName: String? = null,
    username: String? = null,
    bio: String? = null,
    statusText: String? = null,
    enablePreview: Boolean = true
) {
    var avatarBitmap by remember(avatarBase64) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var showLightbox by remember { mutableStateOf(false) }

    LaunchedEffect(avatarBase64) {
        if (!avatarBase64.isNullOrEmpty() && profileViewModel != null) {
            val bytes = if (isMe) {
                profileViewModel.decryptLocalAvatar(avatarBase64)
            } else {
                profileViewModel.decryptPartnerAvatar(avatarBase64)
            }
            if (bytes != null) {
                try {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        avatarBitmap = bmp.asImageBitmap()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("E2eeAvatar", "decode failed", e)
                }
            } else {
                avatarBitmap = null
            }
        } else {
            avatarBitmap = null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(Color(0xFFF5C6CF), Color(0xFFE598A7))
                )
            )
            .clickable(enabled = enablePreview) {
                showLightbox = true
            },
        contentAlignment = Alignment.Center
    ) {
        val bitmap = avatarBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Text(
                text = initials.take(2).uppercase().ifBlank { "?" },
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = (size.value * 0.35f).sp
            )
        }
    }

    if (showLightbox) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showLightbox = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { showLightbox = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clickable(enabled = false) {} // Prevent click-through closing
                ) {
                    // Header E2EE secure indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = BlushAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Signal E2EE Encrypted Profile",
                            color = BlushAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Centered high-resolution image with premium double border
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFFF5F6), Color(0xFFE598A7), Color(0xFFFFF5F6))
                                )
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White, CircleShape)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val bitmap = avatarBitmap
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Avatar Preview",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(BlushCard, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials.take(2).uppercase().ifBlank { "?" },
                                        fontWeight = FontWeight.Bold,
                                        color = Charcoal,
                                        fontSize = 64.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Dynamic Profile Detail Glassmorphic Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = displayName?.ifBlank { "Secure User" } ?: "Secure User",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Charcoal,
                                textAlign = TextAlign.Center
                            )
                            if (!username.isNullOrBlank()) {
                                Text(
                                    text = "@$username",
                                    fontSize = 14.sp,
                                    color = BlushAccent,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 4.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (!statusText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(BlushCard)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        fontSize = 12.sp,
                                        color = Charcoal,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (!bio.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = BlushCard.copy(alpha = 0.5f), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = bio,
                                    fontSize = 14.sp,
                                    color = Charcoal.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    fontFamily = FontFamily.Serif
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Close Button
                    IconButton(
                        onClick = { showLightbox = false },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InvitePartnerDialog(
    myId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val inviteLink = "enclave://invite?token=$myId"
    
    val qrBitmap = remember(inviteLink) {
        try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(inviteLink, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Invite Partner",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Charcoal
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Scan this QR code from their device, or share the secure invite link.",
                    fontSize = 13.sp,
                    color = Charcoal.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "QR Code",
                        modifier = Modifier.size(200.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(BlushCard, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("QR Generation Failed", color = BlushAccent)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        val sendIntent: android.content.Intent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, "Connect with me on Enclave! Tap the link: $inviteLink")
                            type = "text/plain"
                        }
                        val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Invite Link")
                        context.startActivity(shareIntent)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlushAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Invite Link", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
