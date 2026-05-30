package com.enclave.app.ui.profile.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.enclave.app.data.config.ConfigManager
import com.enclave.app.security.ConfigEncryptor
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

// ── Color tokens (blush theme matching BootstrapScreen) ────────────────────────
private val BlushAccent  = Color(0xFFE598A7)
private val BlushCard    = Color(0xFFFCE2E6)
private val BlushLight   = Color(0xFFFFF0F3)
private val Charcoal     = Color(0xFF2A1B1D)
private val PinGold      = Color(0xFFFFD166)
private val PinGoldDark  = Color(0xFFFFA940)

/** Build a 4-digit numeric PIN string. */
private fun generatePin(): String = String.format("%04d", Random.nextInt(0, 10000))

/** Serialise all ConfigManager state + the user ID into a JSON string. */
private fun buildPayloadJson(cfg: ConfigManager, userId: String): String {
    return JSONObject().apply {
        put("userId",            userId)
        put("supabaseUrl",       cfg.getSupabaseUrl()         ?: "")
        put("supabaseKey",       cfg.getSupabaseKey()         ?: "")
        put("signalingUrl",      cfg.getSignalingServerUrl()  ?: "")
        put("turnUrl",           cfg.getTurnServerUrl()       ?: "")
        put("turnUser",          cfg.getTurnUsername()        ?: "")
        put("turnPass",          cfg.getTurnPassword()        ?: "")
        put("ntfyUrl",           cfg.getNtfyServerUrl()       ?: "")
        put("ntfyUser",          cfg.getNtfyUsername()        ?: "")
        put("ntfyPass",          cfg.getNtfyPassword()        ?: "")
        put("schema",            "enclave-v1")
    }.toString()
}

/** Render a QR code bitmap from a string payload. Returns null on failure. */
private fun encodeQr(content: String, size: Int = 768): Bitmap? {
    return try {
        val writer   = QRCodeWriter()
        val matrix   = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp      = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (_: Exception) { null }
}

/** Save a bitmap to the device's Pictures gallery. Returns the Uri on success. */
private fun saveBitmapToGallery(context: Context, bmp: Bitmap): Uri? {
    val fileName  = "enclave_pairing_${System.currentTimeMillis()}.png"
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
            uri
        } else {
            val dir  = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Enclave")
            dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Uri.fromFile(file)
        }
    } catch (_: Exception) { null }
}

/** Share the QR via the system share-sheet. */
private fun shareQrImage(context: Context, bmp: Bitmap) {
    try {
        val cacheDir  = File(context.cacheDir, "enclave_share").also { it.mkdirs() }
        val file      = File(cacheDir, "enclave_pairing_qr.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri       = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent    = Intent(Intent.ACTION_SEND).apply {
            type      = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Scan this QR in Enclave to connect with me. You'll need the PIN I'll share separately.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Pairing QR"))
    } catch (_: Exception) { /* ignore */ }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InvitePartnerDialog(
    myId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cfg     = remember { ConfigManager.getInstance(context) }

    // Generate PIN once per dialog open
    val pin          = remember { generatePin() }
    var snackMessage by remember { mutableStateOf<String?>(null) }

    // Build and encrypt payload (happens once; heavy work but acceptable for a dialog)
    val encryptedPayload = remember(myId) {
        try {
            val json = buildPayloadJson(cfg, myId)
            ConfigEncryptor.encrypt(json, pin)
        } catch (e: Exception) {
            null
        }
    }

    // Render QR from encrypted payload
    val qrBitmap: Bitmap? = remember(encryptedPayload) {
        encryptedPayload?.let { encodeQr(it) }
    }
    val qrImageBitmap = remember(qrBitmap) { qrBitmap?.asImageBitmap() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Header ─────────────────────────────────────────────────
                Text(
                    text = "🔐 Pair with Partner",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Charcoal,
                    fontFamily = FontFamily.Serif
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Share this QR so your partner can configure their Enclave " +
                           "and pair with you instantly. Send the PIN separately.",
                    fontSize = 12.sp,
                    color = Charcoal.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Spacer(Modifier.height(20.dp))

                // ── QR Code ────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(listOf(BlushAccent, PinGold)),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrImageBitmap != null) {
                        Image(
                            bitmap = qrImageBitmap,
                            contentDescription = "Pairing QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚠️", fontSize = 32.sp)
                            Text(
                                "QR generation failed.\nCheck server config.",
                                fontSize = 11.sp,
                                color = BlushAccent,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── PIN display ────────────────────────────────────────────
                Text(
                    text = "Partner's Bootstrap PIN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Charcoal.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(listOf(PinGoldDark.copy(alpha = 0.15f), PinGold.copy(alpha = 0.12f)))
                        )
                        .border(1.dp, PinGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pin,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 12.sp,
                        color = PinGoldDark,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tell this PIN to your partner over a trusted channel.\nIt decrypts the QR.",
                    fontSize = 11.sp,
                    color = Charcoal.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )

                Spacer(Modifier.height(20.dp))

                // ── Action buttons ─────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Download
                    OutlinedButton(
                        onClick = {
                            if (qrBitmap != null) {
                                val uri = saveBitmapToGallery(context, qrBitmap)
                                snackMessage = if (uri != null) "QR saved to Pictures/Enclave" else "Save failed – check storage permission"
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BlushAccent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BlushAccent),
                        enabled = qrBitmap != null
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    // Share
                    Button(
                        onClick = {
                            if (qrBitmap != null) shareQrImage(context, qrBitmap)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlushAccent),
                        enabled = qrBitmap != null
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share QR", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                // Snack feedback
                snackMessage?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        color = if (msg.startsWith("QR saved")) Color(0xFF2E7D32) else Color(0xFFC62828),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background((if (msg.startsWith("QR saved")) Color(0xFF2E7D32) else Color(0xFFC62828)).copy(alpha = 0.08f))
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                    )
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(3000)
                        snackMessage = null
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Charcoal.copy(alpha = 0.4f), fontSize = 13.sp)
                }
            }
        }
    }
}
