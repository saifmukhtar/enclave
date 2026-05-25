package com.enclave.app.ui.profile.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.enclave.app.ui.profile.ProfileViewModel

@Composable
fun E2eeAvatar(
    avatarBase64: String?,
    isMe: Boolean,
    profileViewModel: ProfileViewModel?,
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    displayName: String? = null,
    username: String? = null,
    bio: String? = null,
    statusText: String? = null,
    enablePreview: Boolean = true
) {
    var avatarBitmap by remember(avatarBase64) { mutableStateOf<ImageBitmap?>(null) }
    var showLightbox by remember { mutableStateOf(false) }

    val BlushAccent = Color(0xFFE598A7)
    val BlushCard   = Color(0xFFFCE2E6)
    val Charcoal    = Color(0xFF2A1B1D)

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
                Brush.radialGradient(
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
                contentScale = ContentScale.Crop
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
        Dialog(
            onDismissRequest = { showLightbox = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
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
                                    contentScale = ContentScale.Crop
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
