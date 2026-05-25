@file:OptIn(ExperimentalMaterial3Api::class)
package com.enclave.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.ui.profile.components.E2eeAvatar
import com.enclave.app.ui.chat.ChatUiState
import com.enclave.app.ui.theme.CharcoalText
import com.enclave.app.ui.theme.InterFont
import com.enclave.app.ui.theme.OutfitFont
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.scale

@Composable
fun GlassmorphicTopBar(
    uiState: ChatUiState,
    partnerName: String = "Partner",
    partnerInitials: String = "P",
    isPartnerOnline: Boolean = false,
    lastSeenText: String = "",
    partnerAvatarUrl: String? = null,
    profileViewModel: com.enclave.app.ui.profile.ProfileViewModel? = null,
    partnerDisplayName: String? = null,
    partnerUsername: String? = null,
    partnerBio: String? = null,
    partnerStatusText: String? = null,
    onAudioCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onKissClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onToggleSearch: (Boolean) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color.White.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (isSearchActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onToggleSearch(false) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Cancel Search",
                        tint = CharcoalText
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "Search messages...",
                            fontFamily = InterFont,
                            color = CharcoalText.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = InterFont,
                        fontSize = 14.sp,
                        color = CharcoalText
                    )
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = CharcoalText
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar + name + online status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onProfileClick() }
                ) {
                    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "heartbeat")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (isPartnerOnline) 1.05f else 1f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )

                    Box(
                        modifier = Modifier.scale(pulseScale)
                    ) {
                        E2eeAvatar(
                            avatarBase64 = partnerAvatarUrl,
                            isMe = false,
                            profileViewModel = profileViewModel,
                            initials = partnerInitials.ifBlank { "P" },
                            modifier = Modifier.size(44.dp),
                            displayName = partnerDisplayName ?: partnerName,
                            username = partnerUsername,
                            bio = partnerBio,
                            statusText = partnerStatusText,
                            enablePreview = true
                        )

                        // Online indicator dot
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        if (isPartnerOnline) Color(0xFF4CAF50)
                                        else Color(0xFFBDBDBD)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = partnerName,
                            fontFamily = OutfitFont,
                            fontWeight = FontWeight.Bold,
                            color = CharcoalText,
                            fontSize = 16.sp
                        )
                        Text(
                            text = when {
                                uiState is ChatUiState.Secured && isPartnerOnline -> "Online · E2EE"
                                uiState is ChatUiState.Secured && lastSeenText.isNotBlank() -> lastSeenText
                                uiState is ChatUiState.Secured -> "Signal-Grade E2EE Active"
                                uiState is ChatUiState.Connecting -> "Connecting securely..."
                                uiState is ChatUiState.Handshaking -> "Exchanging PreKeys..."
                                uiState is ChatUiState.WaitingForPartner -> "Waiting for partner..."
                                else -> "Connection failed"
                            },
                            fontFamily = InterFont,
                            color = if (uiState is ChatUiState.Secured && isPartnerOnline)
                                Color(0xFF4CAF50) else CharcoalText.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onToggleSearch(true) }) {
                        Icon(Icons.Default.Search, contentDescription = "Search Messages", tint = CharcoalText)
                    }
                    IconButton(onClick = onKissClick) {
                        Icon(Icons.Default.Favorite, contentDescription = "Kiss", tint = Color(0xFFE598A7))
                    }
                    IconButton(onClick = onAudioCallClick) {
                        Icon(Icons.Default.Call, contentDescription = "Audio Call", tint = CharcoalText)
                    }
                    IconButton(onClick = onVideoCallClick) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = CharcoalText)
                    }
                }
            }
        }
    }
}
