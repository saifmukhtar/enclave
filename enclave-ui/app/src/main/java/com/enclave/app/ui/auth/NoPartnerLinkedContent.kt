package com.enclave.app.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.ui.theme.InterFont
import com.enclave.app.ui.theme.PlayfairFont

/**
 * Shown when the user is authenticated but has not yet linked a partner.
 * Replaces the previous pattern of silently initialising the entire app with
 * hardcoded fallback UUIDs that poisoned every ViewModel with dummy data.
 */
@Composable
fun NoPartnerLinkedContent(
    myId: String,
    onPartnerLinked: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var pasteInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    // Subtle shimmer animation on the invite code box
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "Pair",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "Link Your Enclave",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PlayfairFont,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 0.5.sp
            )

            Text(
                text = "Share your invite code or enter your partner's to create a private encrypted space.",
                fontSize = 13.sp,
                fontFamily = InterFont,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            // Your invite code (your user ID — the other person pastes this)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = shimmerAlpha),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Enclave Invite", myId))
                        Toast.makeText(context, "Invite code copied!", Toast.LENGTH_SHORT).show()
                    }
                    .padding(16.dp)
            ) {
                Text(
                    text = "YOUR INVITE CODE",
                    fontSize = 10.sp,
                    fontFamily = InterFont,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = myId,
                        fontSize = 11.sp,
                        fontFamily = InterFont,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to copy · Share this with your partner",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontFamily = InterFont
                )
            }

            // Input: paste partner's invite code
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = pasteInput,
                    onValueChange = {
                        pasteInput = it.trim()
                        inputError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Paste partner's invite code", fontFamily = InterFont) },
                    singleLine = true,
                    isError = inputError != null,
                    supportingText = {
                        if (inputError != null) {
                            Text(inputError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val trimmed = pasteInput.trim()
                        when {
                            trimmed.isBlank() -> inputError = "Please paste your partner's invite code."
                            trimmed == myId   -> inputError = "You cannot link to yourself."
                            // Basic UUID format validation
                            !trimmed.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                                              -> inputError = "Invalid code format. Make sure you copied the full invite code."
                            else              -> onPartnerLinked(trimmed.lowercase())
                        }
                    }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                )
            }

            Button(
                onClick = {
                    val trimmed = pasteInput.trim()
                    when {
                        trimmed.isBlank() -> inputError = "Please paste your partner's invite code."
                        trimmed == myId   -> inputError = "You cannot link to yourself."
                        !trimmed.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                                          -> inputError = "Invalid code format. Make sure you copied the full invite code."
                        else              -> onPartnerLinked(trimmed.lowercase())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Link Partner",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFont
                )
            }

            TextButton(
                onClick = onLogout,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Sign out",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign Out", fontSize = 12.sp, fontFamily = InterFont)
            }
        }
    }
}
