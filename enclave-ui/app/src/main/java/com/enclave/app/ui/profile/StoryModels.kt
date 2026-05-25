package com.enclave.app.ui.profile

import kotlinx.serialization.Serializable

@Serializable
data class StorySharePayload(
    val storyId: String,
    val contentType: String,
    val encryptedContent: String,
    val backgroundColor: String,
    val expiresAt: Long,
    val createdAt: Long
)
