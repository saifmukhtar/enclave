package dev.saifmukhtar.enclave.ui.profile.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EnclaveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    leadingText: String? = null,
    trailingText: String? = null,
    maxLines: Int = 1
) {
    val BlushAccent = Color(0xFFE598A7)
    val BlushCard   = Color(0xFFFCE2E6)
    val Charcoal    = Color(0xFF2A1B1D)

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
