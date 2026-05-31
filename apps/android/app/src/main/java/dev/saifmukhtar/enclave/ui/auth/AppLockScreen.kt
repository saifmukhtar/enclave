package dev.saifmukhtar.enclave.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.saifmukhtar.enclave.ui.theme.PlayfairFont
import dev.saifmukhtar.enclave.ui.theme.InterFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saifmukhtar.enclave.ui.vault.BiometricPromptManager
import androidx.compose.ui.input.pointer.pointerInput

// Hardcoded colors removed in favor of MaterialTheme.colorScheme

@Composable
fun AppLockScreen(
    authState: BiometricPromptManager.AuthState,
    onUnlock: () -> Unit,
    onLogout: () -> Unit
) {
    // Pulse animation for the fingerprint/lock area
    val infiniteTransition = rememberInfiniteTransition(label = "lock_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.05f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val driftTransition = rememberInfiniteTransition(label = "lock_bg")
    val rotation by driftTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate_bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF5F6))
            .pointerInput(Unit) {
                // Consume all touch events so they don't pass through to the screen below
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Slow rotating ambient fluid blurred orbs
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f
            val rad = size.minDimension * 0.6f

            // Rose accent orb
            val pinkX = centerX + (Math.cos(Math.toRadians(rotation.toDouble())) * (width * 0.25f)).toFloat()
            val pinkY = centerY + (Math.sin(Math.toRadians(rotation.toDouble())) * (height * 0.25f)).toFloat()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD1DC), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(pinkX, pinkY),
                    radius = rad
                ),
                radius = rad * 1.5f,
                center = androidx.compose.ui.geometry.Offset(pinkX, pinkY)
            )

            // Soft white/peach orb
            val peachX = centerX - (Math.cos(Math.toRadians(rotation.toDouble() + 180.0)) * (width * 0.25f)).toFloat()
            val peachY = centerY - (Math.sin(Math.toRadians(rotation.toDouble() + 180.0)) * (height * 0.25f)).toFloat()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF0F2), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(peachX, peachY),
                    radius = rad
                ),
                radius = rad * 1.5f,
                center = androidx.compose.ui.geometry.Offset(peachX, peachY)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
        ) {
            // Elegant Lock Badge
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.4f))
                    .border(1.dp, Color(0xFFFFE4E8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Color(0xFFFFD1DC), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (authState == BiometricPromptManager.AuthState.ERROR) Icons.Default.Lock else Icons.Default.Fingerprint,
                        contentDescription = "Encrypted Lock",
                        tint = if (authState == BiometricPromptManager.AuthState.ERROR) MaterialTheme.colorScheme.error else dev.saifmukhtar.enclave.ui.theme.RoseDeep,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Enclave Locked",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PlayfairFont,
                color = dev.saifmukhtar.enclave.ui.theme.CharcoalText,
                letterSpacing = 1.5.sp
            )
            
            Text(
                text = "Tap to unlock your private space",
                fontSize = 13.sp,
                color = dev.saifmukhtar.enclave.ui.theme.CharcoalText.copy(alpha = 0.6f),
                fontFamily = InterFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Glassmorphic Control Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.8f))
                    .border(1.dp, Color(0xFFFFE4E8), RoundedCornerShape(28.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onUnlock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = dev.saifmukhtar.enclave.ui.theme.RoseDeep,
                            contentColor   = Color.White
                        )
                    ) {
                        Text(
                            text = "Unlock Enclave",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (authState == BiometricPromptManager.AuthState.ERROR) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Authentication failed. Try again.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Sign Out / Exit Option
                    TextButton(
                        onClick = onLogout,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = dev.saifmukhtar.enclave.ui.theme.CharcoalText.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Log Out",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sign Out & Clear Session",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "🔒 Secure local storage protected by device Keystore",
                fontSize = 11.sp,
                color = dev.saifmukhtar.enclave.ui.theme.CharcoalText.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )
        }
    }
}
