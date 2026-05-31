package dev.saifmukhtar.enclave.ui.bootstrap

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.saifmukhtar.enclave.data.config.ConfigManager
import dev.saifmukhtar.enclave.security.ConfigEncryptor
import com.google.zxing.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Properties
import kotlin.random.Random

// ── Palette ────────────────────────────────────────────────────────────────────
private val BlushPink    = Color(0xFFFCE2E6)
private val BlushAccent  = Color(0xFFE598A7)
private val DeepRose     = Color(0xFFC83D60)
private val CharcoalDark = Color(0xFF1A0812)
private val LightCream   = Color(0xFFFDF8F5)
private val PinGold      = Color(0xFFFFD166)
private val PinGoldDark  = Color(0xFFFFA940)

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun generatePin(): String = String.format("%04d", Random.nextInt(0, 10000))

private fun buildConfigJson(cfg: ConfigManager): String = JSONObject().apply {
    put("supabaseUrl",  cfg.getSupabaseUrl()        ?: "")
    put("supabaseKey",  cfg.getSupabaseKey()         ?: "")
    put("signalingUrl", cfg.getSignalingServerUrl()  ?: "")
    put("turnUrl",      cfg.getTurnServerUrl()        ?: "")
    put("turnUser",     cfg.getTurnUsername()         ?: "")
    put("turnPass",     cfg.getTurnPassword()         ?: "")
    put("ntfyUrl",      cfg.getNtfyServerUrl()        ?: "")
    put("ntfyUser",     cfg.getNtfyUsername()         ?: "")
    put("ntfyPass",     cfg.getNtfyPassword()         ?: "")
    put("schema",       "enclave-v1")
}.toString()

private fun encodeQr(content: String, size: Int = 768): Bitmap? = try {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
        for (x in 0 until size) for (y in 0 until size)
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
} catch (_: Exception) { null }

private fun saveBitmapToGallery(context: android.content.Context, bmp: Bitmap): Boolean {
    val fileName = "enclave_setup_qr_${System.currentTimeMillis()}.png"
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Enclave")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
                }
            }
            uri != null
        } else {
            val dir = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Enclave"
            ).also { it.mkdirs() }
            FileOutputStream(java.io.File(dir, fileName)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }
    } catch (_: Exception) { false }
}

private fun decodeQRCode(bitmap: Bitmap): String? {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
    return try { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text }
    catch (_: Exception) { null }
}

// ── PostSetupQrDialog ──────────────────────────────────────────────────────────
// Non-dismissable. Appears after manual save. User must tap "Continue to Login".

@Composable
private fun PostSetupQrDialog(
    cfg: ConfigManager,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val pin     = remember { generatePin() }
    var saved   by remember { mutableStateOf<Boolean?>(null) }

    val encryptedPayload = remember {
        try { ConfigEncryptor.encrypt(buildConfigJson(cfg), pin) } catch (_: Exception) { null }
    }
    val qrBitmap: Bitmap? = remember(encryptedPayload) { encryptedPayload?.let { encodeQr(it) } }

    Dialog(
        onDismissRequest = { /* blocked — non-dismissable */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape  = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = LightCream),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Header ─────────────────────────────────────────────────
                Text("🔒 Setup Complete!", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    color = CharcoalDark, fontFamily = FontFamily.Serif)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Share this QR with your partner so they can configure their Enclave.\n" +
                           "Send the PIN separately over a trusted channel.",
                    fontSize = 12.sp, color = CharcoalDark.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center, lineHeight = 17.sp
                )

                Spacer(Modifier.height(20.dp))

                // ── QR Code ────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            2.dp,
                            Brush.linearGradient(listOf(BlushAccent, PinGold)),
                            RoundedCornerShape(16.dp)
                        )
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Setup QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚠️", fontSize = 28.sp)
                            Text("QR failed – check config.", fontSize = 11.sp,
                                color = BlushAccent, textAlign = TextAlign.Center)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── PIN display ────────────────────────────────────────────
                Text("Partner's Bootstrap PIN", fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CharcoalDark.copy(alpha = 0.6f), letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(
                            listOf(PinGoldDark.copy(alpha = 0.15f), PinGold.copy(alpha = 0.12f))
                        ))
                        .border(1.dp, PinGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pin,
                        fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 12.sp, color = PinGoldDark,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your partner enters this PIN after scanning the QR to decrypt the server config.",
                    fontSize = 11.sp, color = CharcoalDark.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center, lineHeight = 15.sp
                )

                Spacer(Modifier.height(20.dp))

                // ── Download button ────────────────────────────────────────
                OutlinedButton(
                    onClick = {
                        if (qrBitmap != null) {
                            val ok = saveBitmapToGallery(context, qrBitmap)
                            saved = ok
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BlushAccent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BlushAccent),
                    enabled = qrBitmap != null
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download QR to Gallery", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                // Feedback row
                saved?.let {
                    Spacer(Modifier.height(8.dp))
                    val (msg, tint) = if (it)
                        "Saved to Pictures/Enclave ✓" to Color(0xFF2E7D32)
                    else
                        "Save failed – check storage permission" to Color(0xFFC62828)
                    Text(msg, fontSize = 12.sp, color = tint, textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(tint.copy(alpha = 0.08f))
                            .padding(vertical = 8.dp, horizontal = 12.dp))
                    LaunchedEffect(saved) { kotlinx.coroutines.delay(3000); saved = null }
                }

                Spacer(Modifier.height(12.dp))

                // ── Continue to Login ──────────────────────────────────────
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRose)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Continue to Login", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "You can also access this QR any time from Profile → Invite Partner.",
                    fontSize = 10.sp, color = CharcoalDark.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center, lineHeight = 14.sp
                )
            }
        }
    }
}

// ── Main BootstrapScreen ───────────────────────────────────────────────────────

@Composable
fun BootstrapScreen(
    onSetupComplete: () -> Unit
) {
    val context       = LocalContext.current
    val configManager = remember { ConfigManager.getInstance(context) }

    var selectedTab by remember { mutableStateOf(0) }

    // Config fields
    var supabaseUrl  by remember { mutableStateOf("") }
    var supabaseKey  by remember { mutableStateOf("") }
    var signalingUrl by remember { mutableStateOf("") }
    var turnUrl      by remember { mutableStateOf("") }
    var turnUser     by remember { mutableStateOf("") }
    var turnPass     by remember { mutableStateOf("") }
    var ntfyUrl      by remember { mutableStateOf("") }
    var ntfyUser     by remember { mutableStateOf("") }
    var ntfyPass     by remember { mutableStateOf("") }

    var propertiesText by remember { mutableStateOf("") }

    // After manual save → show post-setup QR dialog
    var showPostSetupDialog by remember { mutableStateOf(false) }

    // Diagnostics dialog state
    var showDiagnostics by remember { mutableStateOf(false) }
    var diagSupabaseUrl by remember { mutableStateOf("") }
    var diagSupabaseKey by remember { mutableStateOf("") }
    var diagSignalingUrl by remember { mutableStateOf("") }
    var diagTurnUrl by remember { mutableStateOf("") }
    var diagNtfyUrl by remember { mutableStateOf("") }
    var onDiagnosticsSuccess by remember { mutableStateOf<() -> Unit>({}) }

    // QR import dialog state
    var showPinDialog  by remember { mutableStateOf(false) }
    var scannedPayload by remember { mutableStateOf("") }
    var enterPinText   by remember { mutableStateOf("") }

    // Initialize from existing config if present
    LaunchedEffect(Unit) {
        supabaseUrl  = configManager.getSupabaseUrl()        ?: ""
        supabaseKey  = configManager.getSupabaseKey()         ?: ""
        signalingUrl = configManager.getSignalingServerUrl()  ?: ""
        turnUrl      = configManager.getTurnServerUrl()        ?: ""
        turnUser     = configManager.getTurnUsername()         ?: ""
        turnPass     = configManager.getTurnPassword()         ?: ""
        ntfyUrl      = configManager.getNtfyServerUrl()        ?: ""
        ntfyUser     = configManager.getNtfyUsername()         ?: ""
        ntfyPass     = configManager.getNtfyPassword()         ?: ""
    }

    // Parse pasted properties block
    fun parseProperties(text: String) {
        try {
            val props = Properties().also { it.load(text.byteInputStream()) }
            supabaseUrl  = props.getProperty("SUPABASE_URL",          supabaseUrl)
            supabaseKey  = props.getProperty("SUPABASE_KEY",          supabaseKey)
            signalingUrl = props.getProperty("SIGNALING_SERVER_URL",  signalingUrl)
            turnUrl      = props.getProperty("TURN_SERVER_URL",       turnUrl)
            turnUser     = props.getProperty("TURN_USERNAME",         turnUser)
            turnPass     = props.getProperty("TURN_PASSWORD",         turnPass)
            ntfyUrl      = props.getProperty("NTFY_SERVER_URL",       ntfyUrl)
            ntfyUser     = props.getProperty("NTFY_USERNAME",         ntfyUser)
            ntfyPass     = props.getProperty("NTFY_PASSWORD",         ntfyPass)
            Toast.makeText(context, "Imported parameters successfully!", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Invalid properties format!", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery picker for QR import
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val decoded = decodeQRCode(bitmap)
                    if (decoded != null) {
                        scannedPayload = decoded
                        enterPinText   = ""
                        showPinDialog  = true
                    } else {
                        Toast.makeText(context, "No Enclave QR code found in that image.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read image: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Main scaffold ──────────────────────────────────────────────────────────
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = LightCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Default.Favorite, contentDescription = "Enclave",
                tint = BlushAccent, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(12.dp))
            Text("Welcome to Enclave", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = CharcoalDark, fontFamily = FontFamily.Serif)
            Text("Setup your secure dynamic node connection", fontSize = 13.sp,
                color = CharcoalDark.copy(alpha = 0.6f), textAlign = TextAlign.Center)

            Spacer(Modifier.height(24.dp))

            // ── Tabs ───────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Color.White.copy(alpha = 0.5f),
                contentColor     = DeepRose,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Manual Setup", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Scan / Import QR", fontWeight = FontWeight.Bold) })
            }

            Spacer(Modifier.height(20.dp))

            when (selectedTab) {

                // ── Tab 0: Manual Setup ────────────────────────────────────
                0 -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Admin Properties Block (Paste entire local.properties)",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CharcoalDark
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = propertiesText,
                            onValueChange = { propertiesText = it; parseProperties(it) },
                            placeholder = { Text("PASTE PROPERTIES BLOCK HERE...") },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = BlushPink)
                        Spacer(Modifier.height(16.dp))

                        Text("Granular Configuration Settings",
                            fontWeight = FontWeight.Bold, color = CharcoalDark, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))

                        BootstrapField("SUPABASE_URL",         supabaseUrl)  { supabaseUrl  = it }
                        BootstrapField("SUPABASE_KEY",         supabaseKey)  { supabaseKey  = it }
                        BootstrapField("SIGNALING_SERVER_URL", signalingUrl) { signalingUrl = it }
                        BootstrapField("TURN_SERVER_URL",      turnUrl)      { turnUrl      = it }
                        BootstrapField("TURN_USERNAME",        turnUser)     { turnUser     = it }
                        BootstrapField("TURN_PASSWORD",        turnPass)     { turnPass     = it }
                        BootstrapField("NTFY_SERVER_URL",      ntfyUrl)      { ntfyUrl      = it }
                        BootstrapField("NTFY_USERNAME",        ntfyUser)     { ntfyUser     = it }
                        BootstrapField("NTFY_PASSWORD",        ntfyPass)     { ntfyPass     = it }

                        Spacer(Modifier.height(28.dp))

                        Button(
                            onClick = {
                                if (supabaseUrl.isBlank() || supabaseKey.isBlank() || signalingUrl.isBlank()) {
                                    Toast.makeText(context,
                                        "Supabase URL, Key and Signaling URL are mandatory!",
                                        Toast.LENGTH_SHORT).show()
                                } else {
                                    configManager.saveConfig(
                                        supabaseUrl  = supabaseUrl,
                                        supabaseKey  = supabaseKey,
                                        signalingUrl = signalingUrl,
                                        turnUrl      = turnUrl,
                                        turnUser     = turnUser,
                                        turnPass     = turnPass,
                                        ntfyUrl      = ntfyUrl,
                                        ntfyUser     = ntfyUser,
                                        ntfyPass     = ntfyPass
                                    )
                                    // Trigger backend nodes connection diagnostics before proceeding
                                    diagSupabaseUrl = supabaseUrl
                                    diagSupabaseKey = supabaseKey
                                    diagSignalingUrl = signalingUrl
                                    diagTurnUrl = turnUrl
                                    diagNtfyUrl = ntfyUrl
                                    onDiagnosticsSuccess = {
                                        showDiagnostics = false
                                        showPostSetupDialog = true
                                    }
                                    showDiagnostics = true
                                }
                            },
                            colors   = ButtonDefaults.buttonColors(containerColor = BlushAccent),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save Node Settings", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── Tab 1: Scan / Import QR ────────────────────────────────
                1 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan",
                            tint = DeepRose, modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(18.dp))
                        Text("Import Partner Setup QR", fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, color = CharcoalDark)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Select the encrypted Enclave QR image shared by your partner to " +
                            "automatically configure your device.",
                            fontSize = 13.sp, color = CharcoalDark.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            colors   = ButtonDefaults.buttonColors(containerColor = BlushAccent),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Select Setup QR from Gallery", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "You'll need the 4-digit PIN your partner will tell you after scanning.",
                            fontSize = 11.sp, color = CharcoalDark.copy(alpha = 0.45f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // ── Post-setup QR dialog (non-dismissable) ─────────────────────────────────
    if (showPostSetupDialog) {
        PostSetupQrDialog(
            cfg        = configManager,
            onContinue = {
                showPostSetupDialog = false
                onSetupComplete()
            }
        )
    }

    // ── PIN entry dialog for QR import ─────────────────────────────────────────
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Enter Bootstrap PIN", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Enter the 4-digit PIN your partner shared with you. " +
                        "It decrypts the server configuration from the QR.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value         = enterPinText,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) enterPinText = it },
                        label         = { Text("4-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        singleLine    = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (enterPinText.length < 4) {
                            Toast.makeText(context, "Enter all 4 digits.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        try {
                            val decrypted = ConfigEncryptor.decrypt(scannedPayload, enterPinText)
                            val json      = JSONObject(decrypted)

                            // Support both camelCase (Profile QR) and snake_case (legacy Bootstrap QR)
                            val sUrl  = json.optString("supabaseUrl",  json.optString("supabase_url",          ""))
                            val sKey  = json.optString("supabaseKey",  json.optString("supabase_key",          ""))
                            val wsUrl = json.optString("signalingUrl", json.optString("signaling_server_url",  ""))
                            val tUrl  = json.optString("turnUrl",      json.optString("turn_server_url",       ""))
                            val tUser = json.optString("turnUser",     json.optString("turn_username",          ""))
                            val tPass = json.optString("turnPass",     json.optString("turn_password",          ""))
                            val nUrl  = json.optString("ntfyUrl",      json.optString("ntfy_server_url",        ""))
                            val nUser = json.optString("ntfyUser",     json.optString("ntfy_username",          ""))
                            val nPass = json.optString("ntfyPass",     json.optString("ntfy_password",          ""))

                            if (sUrl.isBlank() || sKey.isBlank() || wsUrl.isBlank()) {
                                Toast.makeText(context,
                                    "Invalid QR – required fields missing.", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            configManager.saveConfig(
                                supabaseUrl  = sUrl,  supabaseKey  = sKey,  signalingUrl = wsUrl,
                                turnUrl      = tUrl,  turnUser     = tUser, turnPass     = tPass,
                                ntfyUrl      = nUrl,  ntfyUser     = nUser, ntfyPass     = nPass
                            )

                            val partnerId = json.optString("userId", "")
                            if (partnerId.isNotBlank()) {
                                android.util.Log.i("Bootstrap", "Partner userId from QR: $partnerId")
                                // TODO: auto-initiate pairing with partnerId via ViewModel
                            }

                             diagSupabaseUrl = sUrl
                             diagSupabaseKey = sKey
                             diagSignalingUrl = wsUrl
                             diagTurnUrl = tUrl
                             diagNtfyUrl = nUrl
                             onDiagnosticsSuccess = {
                                 showDiagnostics = false
                                 showPinDialog = false
                                 val msg = if (partnerId.isNotBlank()) "Configured & Partner Found! ✓"
                                           else "Configuration applied! ✓"
                                 Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                 onSetupComplete()
                             }
                             showDiagnostics = true
                        } catch (_: Exception) {
                            Toast.makeText(context,
                                "Decryption failed – wrong PIN?", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlushAccent)
                ) {
                    Text("Decrypt & Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDiagnostics) {
        dev.saifmukhtar.enclave.ui.bootstrap.components.ConnectionDiagnosticsDialog(
            supabaseUrl = diagSupabaseUrl,
            supabaseKey = diagSupabaseKey,
            signalingUrl = diagSignalingUrl,
            turnUrl = diagTurnUrl,
            ntfyUrl = diagNtfyUrl,
            onDismiss = { showDiagnostics = false },
            onSuccess = { onDiagnosticsSuccess() }
        )
    }
}

// ── BootstrapField composable ──────────────────────────────────────────────────
@Composable
private fun BootstrapField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = CharcoalDark.copy(alpha = 0.8f))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value          = value,
            onValueChange  = onValueChange,
            modifier       = Modifier.fillMaxWidth(),
            shape          = RoundedCornerShape(10.dp),
            textStyle      = LocalTextStyle.current.copy(fontSize = 12.sp),
            singleLine     = true
        )
    }
}
