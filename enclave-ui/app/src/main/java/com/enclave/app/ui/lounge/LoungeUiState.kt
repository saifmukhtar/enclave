package com.enclave.app.ui.lounge

data class LoungeUiState(
    val myStatus: ProfileStatus = ProfileStatus("❤️", "Online inside Enclave", 100, "Nothing playing", "12:00 PM"),
    val partnerStatus: ProfileStatus = ProfileStatus("❤️", "Resting...", 100, "Offline", "12:00 PM"),
    val isDiceRolling: Boolean = false,
    val diceValue: Int = 1,
    val currentPrompt: String = "Tap 'Pick a Card' to start playing Truth or Dare...",
    val isTruthSelected: Boolean = true,
    val scratchState: ScratchState? = null,
    val isUploading: Boolean = false,
    val isDrawingUploading: Boolean = false,
    val isScrapbookUploading: Boolean = false
)
