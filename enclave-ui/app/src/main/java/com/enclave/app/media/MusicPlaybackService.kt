package com.enclave.app.media

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground service ensuring audio playback continues outside the app.
 * Publishes the system media notification automatically via Media3.
 */
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // Handle Audio Focus automatically (pauses on calls, etc.)
            )
            .build()
            
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val clientPackage = controllerInfo.packageName
        // 1. Authorize our own app package to control media
        if (clientPackage == packageName) {
            return mediaSession
        }
        // 2. Authorize standard Android OS UI, system packages, and projection systems (e.g. Android Auto)
        if (clientPackage.startsWith("com.android.") || 
            clientPackage.startsWith("android.media.") || 
            clientPackage == "com.google.android.projection.gearhead"
        ) {
            return mediaSession
        }
        // 3. Reject any untrusted third-party apps
        return null
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
