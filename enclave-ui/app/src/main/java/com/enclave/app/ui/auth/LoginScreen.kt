package com.enclave.app.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.R

private val BlushBackground  = Color(0xFFFFF5F6)
private val BlushSent        = Color(0xFFFCE2E6)
private val BlushAccent      = Color(0xFFE598A7)
private val CharcoalText     = Color(0xFF2A1B1D)
private val DeepRose         = Color(0xFFD4607A)

enum class LoginState { IDLE, LOADING, ERROR }

@Composable
fun LoginScreen(
    onLoginSuccess: (email: String, password: String) -> Unit,
    onSignUpSuccess: (email: String, password: String) -> Unit,
    errorMessage: String? = null,
    isLoading: Boolean = false
) {
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(false) }
    
    val isEmailValid = remember(email) {
        android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    }
    val isPasswordValid = remember(password) {
        password.length >= 6
    }
    val isEmailWhitelisted = true // Open signup: all valid email addresses are accepted

    val loginState = when {
        isLoading -> LoginState.LOADING
        errorMessage != null -> LoginState.ERROR
        else -> LoginState.IDLE
    }

    // Gentle pulse on the lock icon
    val infiniteTransition = rememberInfiniteTransition(label = "lock_pulse")
    val lockScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lock_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFF0F2), Color(0xFFFCE2E6), Color(0xFFF5C6CF))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
        ) {
            // Animated lock badge
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(lockScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(BlushAccent.copy(alpha = 0.3f), Color.Transparent))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.login_app_title),
                        tint = BlushAccent,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isSignUp) "Create Space" else stringResource(R.string.login_app_title),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = CharcoalText,
                letterSpacing = 2.sp
            )
            Text(
                text = if (isSignUp) "Register your secure intimate connection" else stringResource(R.string.login_app_subtitle),
                fontSize = 13.sp,
                color = CharcoalText.copy(alpha = 0.55f),
                fontFamily = FontFamily.Default,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Glassmorphic card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.55f))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    // Email field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.login_email_label), color = CharcoalText.copy(alpha = 0.7f)) },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = BlushAccent)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = BlushAccent,
                            unfocusedBorderColor = BlushAccent.copy(alpha = 0.4f),
                            focusedTextColor     = CharcoalText,
                            unfocusedTextColor   = CharcoalText,
                            cursorColor          = BlushAccent,
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.login_password_label), color = CharcoalText.copy(alpha = 0.7f)) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = BlushAccent)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) stringResource(R.string.login_hide_password) else stringResource(R.string.login_show_password),
                                    tint = CharcoalText.copy(alpha = 0.5f)
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = BlushAccent,
                            unfocusedBorderColor = BlushAccent.copy(alpha = 0.4f),
                            focusedTextColor     = CharcoalText,
                            unfocusedTextColor   = CharcoalText,
                            cursorColor          = BlushAccent,
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    // Client-side Input Validation Messages
                    if (email.isNotEmpty() && !isEmailValid) {
                        Text(
                            text = stringResource(R.string.login_email_invalid),
                            color = DeepRose,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, start = 8.dp),
                            textAlign = TextAlign.Start
                        )
                    } else if (isSignUp && email.isNotEmpty() && !isEmailWhitelisted) {
                        Text(
                            text = "Unauthorized email for this private Enclave.",
                            color = DeepRose,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, start = 8.dp),
                            textAlign = TextAlign.Start
                        )
                    }

                    if (password.isNotEmpty() && !isPasswordValid) {
                        Text(
                            text = stringResource(R.string.login_password_invalid),
                            color = DeepRose,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, start = 8.dp),
                            textAlign = TextAlign.Start
                        )
                    }

                    // Server-side Error message
                    if (loginState == LoginState.ERROR && errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorMessage,
                            color = DeepRose,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Sign In / Sign Up button
                    Button(
                        onClick = {
                            if (isEmailValid && isPasswordValid && isEmailWhitelisted) {
                                if (isSignUp) {
                                    onSignUpSuccess(email.trim(), password)
                                } else {
                                    onLoginSuccess(email.trim(), password)
                                }
                            }
                        },
                        enabled = loginState != LoginState.LOADING && isEmailValid && isPasswordValid && isEmailWhitelisted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BlushAccent,
                            contentColor   = Color.White,
                            disabledContainerColor = BlushAccent.copy(alpha = 0.45f),
                            disabledContentColor   = Color.White.copy(alpha = 0.7f)
                        )
                    ) {
                        if (loginState == LoginState.LOADING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color    = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text       = if (isSignUp) "Create Space & Sign Up" else stringResource(R.string.login_button_text),
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isSignUp) "Already have an account? Sign In" else "Create a new private space? Sign Up",
                        color = BlushAccent,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable {
                                isSignUp = !isSignUp
                            }
                            .padding(8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text  = stringResource(R.string.login_footer_e2ee),
                fontSize = 11.sp,
                color = CharcoalText.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
    }
}
