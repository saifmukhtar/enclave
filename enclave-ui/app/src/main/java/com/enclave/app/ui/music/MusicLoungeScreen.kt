package com.enclave.app.ui.music

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MusicLoungeScreen(viewModel: MusicLoungeViewModel = viewModel()) {
    val consentState by viewModel.consentState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (consentState) {
                MusicConsentState.IDLE -> {
                    Text("Select a Track", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.selectTrack("https://stub-supabase-url.com/song.enc", "Ghazal Mix") }) {
                        Text("Play Ghazal Mix")
                    }
                }
                MusicConsentState.WAITING_FOR_PARTNER -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Waiting for partner to accept...")
                }
                MusicConsentState.RECEIVING_REQUEST -> {
                    // This would typically be a popup Dialog, shown inline here for the scaffold
                    Card(elevation = CardDefaults.cardElevation(8.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Partner wants to listen to: ${viewModel.requestedTrackName}")
                            Spacer(modifier = Modifier.height(16.dp))
                            Row {
                                TextButton(onClick = { viewModel.rejectRequest() }) { Text("Reject") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { viewModel.acceptRequest() }) { Text("Accept") }
                            }
                        }
                    }
                }
                MusicConsentState.PLAYING -> {
                    Text("Now Playing: ${viewModel.requestedTrackName}", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Mock Player UI
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { /* Sync Pause/Play via MusicSyncController */ }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(64.dp))
                        }
                    }
                    Slider(
                        value = 0f, // Would bind to ExoPlayer currentPosition
                        onValueChange = { /* Sync Seek via MusicSyncController */ },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}
