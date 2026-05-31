package dev.saifmukhtar.enclave.ui.vault

import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

class VaultMediaHandler(
    val pickerLauncher: ManagedActivityResultLauncher<PickVisualMediaRequest, List<android.net.Uri>>,
    val permissionLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>
)

@Composable
fun rememberVaultMediaHandler(
    viewModel: VaultViewModel,
    context: Context,
    pendingPermission: android.content.IntentSender?
): VaultMediaHandler {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importPublicMedia(uris, context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.clearPendingPermissionIntent()
            viewModel.importPublicMedia(emptyList(), context) // Trigger reload
        }
    }

    LaunchedEffect(pendingPermission) {
        pendingPermission?.let { intentSender ->
            val request = IntentSenderRequest.Builder(intentSender).build()
            permissionLauncher.launch(request)
        }
    }

    return VaultMediaHandler(pickerLauncher, permissionLauncher)
}
