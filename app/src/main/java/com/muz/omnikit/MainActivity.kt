package com.muz.omnikit

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muz.omnikit.core.hardware.HardwareEventBus
import com.muz.omnikit.core.model.AppSettings
import com.muz.omnikit.core.storage.PreferencesRepository
import com.muz.omnikit.core.updater.ApkInstaller
import com.muz.omnikit.core.updater.UpdateStateHolder
import com.muz.omnikit.ui.navigation.OmniKitNavHost
import com.muz.omnikit.ui.theme.OmniKitTheme
import com.muz.omnikit.ui.theme.isDarkThemeResolved
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        handleIncomingIntent(intent)

        val prefsRepo = PreferencesRepository(applicationContext)

        setContent {
            val settings by prefsRepo.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            val updateState by UpdateStateHolder.state.collectAsStateWithLifecycle()
            val pendingInstallPath by HardwareEventBus.pendingInstallApkPath.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            val isDark = isDarkThemeResolved(settings.themeMode)

            // Adapt status & navigation bar icon contrast whenever theme changes
            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }

            val installAction = remember(this) {
                { apkPath: String ->
                    ApkInstaller.promptInstall(this, apkPath)
                    Unit
                }
            }

            OmniKitTheme(themeMode = settings.themeMode) {
                OmniKitNavHost(
                    settings = settings,
                    updateState = updateState,
                    onSetThemeMode = { mode -> scope.launch { prefsRepo.setThemeMode(mode) } },
                    onSetLanguage = { lang -> scope.launch { prefsRepo.setLanguage(lang) } },
                    onSetToolVisible = { id, vis -> scope.launch { prefsRepo.setToolVisible(id, vis) } },
                    onShowAllTools = { scope.launch { prefsRepo.showAllTools() } },
                    onHideAllTools = { scope.launch { prefsRepo.hideAllTools() } },
                    onInstallUpdate = installAction
                )

                // Immediately prompt user to install once background download completes
                if (!pendingInstallPath.isNullOrBlank()) {
                    val lang = settings.language
                    val targetPath = pendingInstallPath!!
                    AlertDialog(
                        onDismissRequest = { HardwareEventBus.setPendingInstallApk(null) },
                        title = {
                            Text(lang.tr("Kemaskini Sedia Dipasang", "Update Ready to Install"))
                        },
                        text = {
                            Text(
                                lang.tr(
                                    "Muat turun kemaskini OmniKitMY telah selesai. Adakah anda ingin memasang sekarang?",
                                    "OmniKitMY update download is complete. Would you like to install it now?"
                                )
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    HardwareEventBus.setPendingInstallApk(null)
                                    ApkInstaller.promptInstall(this@MainActivity, targetPath)
                                }
                            ) {
                                Text(lang.tr("Pasang Sekarang", "Install Now"))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { HardwareEventBus.setPendingInstallApk(null) }) {
                                Text(lang.tr("Nanti", "Later"))
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableNfcDispatchIfSupported()
    }

    override fun onPause() {
        runCatching { nfcAdapter?.disableForegroundDispatch(this) }
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            val handled = HardwareEventBus.volumeKeyInterceptor?.invoke(event.keyCode) == true
            if (handled) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val apkPath = intent.getStringExtra(EXTRA_INSTALL_APK_PATH)
        if (!apkPath.isNullOrBlank()) {
            intent.removeExtra(EXTRA_INSTALL_APK_PATH)
            HardwareEventBus.setPendingInstallApk(apkPath)
        }
        val action = intent.action
        if (action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            HardwareEventBus.emitNfcIntent(intent)
        }
    }

    private fun enableNfcDispatchIfSupported() {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        try {
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
            adapter.enableForegroundDispatch(this, pendingIntent, null, null)
        } catch (_: Exception) {
            // Device does not support foreground NFC dispatch
        }
    }

    companion object {
        const val EXTRA_INSTALL_APK_PATH = "com.muz.omnikit.EXTRA_INSTALL_APK_PATH"
    }
}
