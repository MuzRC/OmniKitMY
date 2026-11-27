package com.muz.omnikit.core.updater

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UpdateConfig {
    // Primary manifest updated by your GitHub Actions YAML workflow
    const val VERSION_JSON_URL = "https://raw.githubusercontent.com/MuzRC/OmniKitMY/main/version.json"

    // Automatic fallback to latest GitHub Release if version.json is unavailable
    const val GITHUB_RELEASES_API = "https://api.github.com/repos/MuzRC/OmniKitMY/releases/latest"
}

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val fileSizeBytes: Long = -1L
)

enum class UpdateStatus {
    IDLE,
    CHECKING,
    AVAILABLE,
    UP_TO_DATE,
    DOWNLOADING,
    COMPLETED,
    ERROR
}

data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val updateInfo: UpdateInfo? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val progressPercent: Int = 0,
    val downloadedApkPath: String? = null,
    val errorMessage: String? = null
)

object UpdateStateHolder {
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun update(transform: (UpdateUiState) -> UpdateUiState) {
        _state.value = transform(_state.value)
    }

    fun resetError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
