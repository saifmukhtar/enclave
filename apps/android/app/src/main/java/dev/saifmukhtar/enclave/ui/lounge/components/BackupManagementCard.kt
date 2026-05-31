package dev.saifmukhtar.enclave.ui.lounge.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saifmukhtar.enclave.ui.theme.PlayfairFont
import dev.saifmukhtar.enclave.ui.theme.InterFont

@Composable
fun BackupManagementCard(
    onExportClick: () -> Unit,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Enclave Database Encrypted Backup", fontWeight = FontWeight.Bold, fontFamily = PlayfairFont, color = Color(0xFF2A1B1D), fontSize = 14.sp)
            Text("Safeguard or migrate your ratchet databases via secure AES-256 GCM passphrase-encrypted backup files.", fontFamily = InterFont, color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onExportClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1B1D)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Backup", fontSize = 11.sp)
                }

                Button(
                    onClick = onRestoreClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore Backup", fontSize = 11.sp)
                }
            }
        }
    }
}
