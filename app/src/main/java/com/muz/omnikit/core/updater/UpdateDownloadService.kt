package com.muz.omnikit.core.updater

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.muz.omnikit.MainActivity
import com.muz.omnikit.R
import com.muz.omnikit.core.hardware.HardwareEventBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class UpdateDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                downloadJob?.cancel()
                UpdateStateHolder.update {
                    it.copy(
                        status = if (it.updateInfo != null) UpdateStatus.AVAILABLE else UpdateStatus.IDLE,
                        progressPercent = 0,
                        downloadedBytes = 0L
                    )
                }
                releaseLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val apkUrl = intent.getStringExtra(EXTRA_APK_URL).orEmpty()
                val versionName = intent.getStringExtra(EXTRA_VERSION_NAME).orEmpty()
                val expectedSize = intent.getLongExtra(EXTRA_FILE_SIZE, -1L)
                if (apkUrl.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                startInForeground(buildProgressNotification(versionName, 0, 0L, expectedSize))
                acquireLocks()

                downloadJob?.cancel()
                downloadJob = serviceScope.launch {
                    runDownload(apkUrl, versionName, expectedSize)
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runDownload(apkUrl: String, versionName: String, expectedSize: Long) {
        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: cacheDir
        downloadsDir.mkdirs()
        val tempFile = File(downloadsDir, "OmniKitMY_update.apk.part")
        val finalFile = File(downloadsDir, "OmniKitMY-v${versionName.ifBlank { "latest" }}-signed.apk")

        try {
            UpdateStateHolder.update {
                it.copy(
                    status = UpdateStatus.DOWNLOADING,
                    downloadedBytes = 0L,
                    totalBytes = expectedSize,
                    progressPercent = 0,
                    errorMessage = null
                )
            }

            val conn = openConnectionFollowingRedirects(apkUrl)
            try {
                val headerLength = conn.contentLengthLong
                val totalBytes = if (headerLength > 0L) headerLength else expectedSize
                var downloaded = 0L
                var lastNotifyMs = 0L
                val buffer = ByteArray(16 * 1024)

                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        while (serviceScope.isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastNotifyMs >= 250L || (totalBytes > 0 && downloaded >= totalBytes)) {
                                lastNotifyMs = now
                                val percent = if (totalBytes > 0) {
                                    ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                UpdateStateHolder.update {
                                    it.copy(
                                        status = UpdateStatus.DOWNLOADING,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes,
                                        progressPercent = percent
                                    )
                                }
                                updateNotification(
                                    NOTIFICATION_ID_PROGRESS,
                                    buildProgressNotification(versionName, percent, downloaded, totalBytes)
                                )
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            if (!serviceScope.isActive) return

            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            UpdateStateHolder.update {
                it.copy(
                    status = UpdateStatus.COMPLETED,
                    progressPercent = 100,
                    downloadedBytes = finalFile.length(),
                    totalBytes = finalFile.length(),
                    downloadedApkPath = finalFile.absolutePath,
                    errorMessage = null
                )
            }

            // Paparkan dialog pasang automatik di skrin app & notifikasi berkeutamaan tinggi bila skrin padam
            HardwareEventBus.setPendingInstallApk(finalFile.absolutePath)
            stopForeground(STOP_FOREGROUND_REMOVE)
            updateNotification(
                NOTIFICATION_ID_COMPLETE,
                buildCompleteNotification(versionName, finalFile.absolutePath)
            )
        } catch (_: CancellationException) {
            tempFile.delete()
        } catch (e: Exception) {
            tempFile.delete()
            UpdateStateHolder.update {
                it.copy(
                    status = UpdateStatus.ERROR,
                    errorMessage = e.localizedMessage ?: "Download failed"
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            updateNotification(NOTIFICATION_ID_COMPLETE, buildErrorNotification(e.localizedMessage))
        } finally {
            releaseLocks()
            stopSelf()
        }
    }

    private fun openConnectionFollowingRedirects(startUrl: String): HttpURLConnection {
        var currentUrl = startUrl
        repeat(8) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", "OmniKitMY-Updater")
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (next.isNullOrBlank()) throw IllegalStateException("Redirect missing Location")
                currentUrl = URL(URL(currentUrl), next).toString()
            } else if (code in 200..299) {
                return conn
            } else {
                conn.disconnect()
                throw IllegalStateException("HTTP $code")
            }
        }
        throw IllegalStateException("Too many redirects")
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OmniKitMY::UpdateDownloadLock")
                ?.apply { acquire(30 * 60 * 1000L) }

            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm?.createWifiLock(wifiMode, "OmniKitMY::UpdateWifiLock")?.apply { acquire() }
        } catch (_: Exception) {
            // Teruskan walaupun peranti menyekat lock
        }
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        wakeLock = null
        wifiLock = null
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Kemaskini OmniKitMY / Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Status muat turun & pemasangan kemaskini OmniKitMY"
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildProgressNotification(
        versionName: String,
        percent: Int,
        downloaded: Long,
        total: Long
    ): Notification {
        val cancelIntent = Intent(this, UpdateDownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this,
            101,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            102,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sizeText = if (total > 0) {
            String.format(
                Locale.US,
                "%.1f MB / %.1f MB (%d%%)",
                downloaded / 1_048_576.0,
                total / 1_048_576.0,
                percent
            )
        } else {
            String.format(Locale.US, "%.1f MB", downloaded / 1_048_576.0)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Memuat turun OmniKitMY v$versionName")
            .setContentText(sizeText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, percent, total <= 0)
            .setContentIntent(openPending)
            .addAction(0, "Batal / Cancel", cancelPending)
            .build()
    }

    private fun buildCompleteNotification(versionName: String, apkPath: String): Notification {
        val installTriggerIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_INSTALL_APK_PATH, apkPath)
        }
        val pending = PendingIntent.getActivity(
            this,
            103,
            installTriggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Kemaskini OmniKitMY v$versionName Sedia")
            .setContentText("Muat turun selesai. Ketik untuk pasang sekarang.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addAction(0, "Pasang Sekarang / Install", pending)
            .build()
    }

    private fun buildErrorNotification(reason: String?): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Muat Turun Kemaskini Gagal")
            .setContentText(reason ?: "Sila cuba semula.")
            .setAutoCancel(true)
            .build()
    }

    private fun updateNotification(id: Int, notification: Notification) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }

    override fun onDestroy() {
        releaseLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "omnikit_update_channel"
        private const val NOTIFICATION_ID_PROGRESS = 9001
        private const val NOTIFICATION_ID_COMPLETE = 9002

        const val ACTION_START = "com.muz.omnikit.action.START_UPDATE_DOWNLOAD"
        const val ACTION_CANCEL = "com.muz.omnikit.action.CANCEL_UPDATE_DOWNLOAD"
        const val EXTRA_APK_URL = "extra_apk_url"
        const val EXTRA_VERSION_NAME = "extra_version_name"
        const val EXTRA_FILE_SIZE = "extra_file_size"

        fun startDownload(context: Context, info: UpdateInfo) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_APK_URL, info.apkUrl)
                putExtra(EXTRA_VERSION_NAME, info.versionName)
                putExtra(EXTRA_FILE_SIZE, info.fileSizeBytes)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelDownload(context: Context) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
