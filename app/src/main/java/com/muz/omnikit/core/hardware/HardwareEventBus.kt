package com.muz.omnikit.core.hardware

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared event bus connecting MainActivity hardware callbacks (NFC intents,
 * hardware Volume buttons, and completed update install triggers) to active tools
 * cleanly without needing to modify MainActivity in later tool phases.
 */
object HardwareEventBus {

    private val _nfcIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 4)
    val nfcIntents: SharedFlow<Intent> = _nfcIntents.asSharedFlow()

    @Volatile
    var volumeKeyInterceptor: ((keyCode: Int) -> Boolean)? = null

    @Volatile
    var nfcForegroundActive: Boolean = false

    private val _pendingInstallApkPath = MutableStateFlow<String?>(null)
    val pendingInstallApkPath: StateFlow<String?> = _pendingInstallApkPath.asStateFlow()

    fun emitNfcIntent(intent: Intent) {
        _nfcIntents.tryEmit(intent)
    }

    fun setPendingInstallApk(path: String?) {
        _pendingInstallApkPath.value = path
    }
}
