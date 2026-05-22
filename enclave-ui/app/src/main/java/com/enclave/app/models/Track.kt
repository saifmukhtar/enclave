package com.enclave.app.models

/**
 * Represents a single music track in the synchronized co-listening playlist.
 */
data class Track(
    val trackId: Int = 0,
    val trackName: String = "",
    val trackUrl: String = "",
    val artistName: String = ""
)
