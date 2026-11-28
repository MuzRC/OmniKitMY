package com.muz.sdprobe.core.updater

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.muz.sdprobe.BuildConfig
import com.muz.sdprobe.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Servis latar belakang untuk muat turun APK tunggal secara selamat:
 * 1. Padam semua fail lama sebelum mula (tiada append / saiz berganda).
 * 2. Tulis ke fail sementara .part dan semak saiz tepat + SHA-256 + magic bytes ZIP/APK.
 * 3. Lulus semakan -> tukar nama ke .apk dan terus buka arahan pasang.
 */
class UpdateDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            UpdateStateBus.requestCancel()
            return START_NOT_STICKY
        }

        // WAJIB: Panggil startForeground serta-merta (elak crash Android 14/15/16/17)
        val initialNotif = buildProgressNotification("Menyediakan muat turun...", 0, true)
        promoteToForeground(initialNotif)

        val info = extractInfo(intent)
        if (info == null) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        if (!UpdateStateBus.tryAcquireDownloadLock()) {
            // Muat turun sudah berjalan; jangan buka aliran kedua
            return START_NOT_STICKY
        }

        serviceScope.launch {
            try {
                executeSingleFileDownload(info)
            } finally {
                UpdateStateBus.releaseDownloadLock()
                stopSelfSafely()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun executeSingleFileDownload(info: UpdateInfo) {
        val updatesDir = File(cacheDir, UpdateConfig.UPDATE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

        // Bersihkan semua sisa APK / .part lama sebelum mula
        updatesDir.listFiles()?.forEach { it.delete() }

        val partFile = File(updatesDir, UpdateConfig.PART_FILE_NAME)
        val finalApk = File(updatesDir, UpdateConfig.FINAL_APK_NAME)

        UpdateStateBus.setState(
            UpdateState.Downloading(
                info = info,
                percent = 0,
                downloadedBytes = 0L,
                totalBytes = info.expectedSizeBytes.coerceAtLeast(0L)
            )
        )

        var conn: HttpURLConnection? = null
        try {
            conn = openFollowingRedirects(info.apkUrl)
            val contentLen = conn.contentLengthLong
            val targetSize = when {
                info.expectedSizeBytes > 0L -> info.expectedSizeBytes
                contentLen > 0L -> contentLen
                else -> -1L
            }

            // Semak awal jika Content-Length bercanggah teruk dengan version.json
            if (info.expectedSizeBytes > 0L && contentLen > 0L && contentLen != info.expectedSizeBytes) {
                throw IllegalStateException(
                    "Saiz pelayan ($contentLen B) tidak sepadan dengan version.json (${info.expectedSizeBytes} B)."
                )
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastUiPush = 0L
            val buffer = ByteArray(32 * 1024)
            var magicChecked = false

            conn.inputStream.use { input ->
                // append = false -> dijamin tulis fail tunggal dari bait 0
                FileOutputStream(partFile, false).use { output ->
                    while (true) {
                        if (UpdateStateBus.isCancelRequested()) {
                            partFile.delete()
                            UpdateStateBus.setState(UpdateState.Available(info))
                            return
                        }

                        val read = input.read(buffer)
                        if (read == -1) break

                        if (!magicChecked && read >= 4) {
                            // APK ialah arkib ZIP bermula dengan PK (0x50 0x4B 0x03 0x04)
                            val isZip = buffer[0] == 0x50.toByte() &&
                                buffer[1] == 0x4B.toByte() &&
                                buffer[2] == 0x03.toByte() &&
                                buffer[3] == 0x04.toByte()
                            if (!isZip) {
                                throw IllegalStateException("Fail yang diterima bukan APK yang sah.")
                            }
                            magicChecked = true
                        }

                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read

                        // Sekat jika muat turun melebihi saiz sepatutnya
                        if (info.expectedSizeBytes > 0L && downloaded > info.expectedSizeBytes) {
                            throw IllegalStateException("Muat turun melebihi saiz APK tunggal yang dijangka.")
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastUiPush >= 250L) {
                            lastUiPush = now
                            val pct = if (targetSize > 0L) {
                                ((downloaded * 100L) / targetSize).toInt().coerceIn(0, 99)
                            } else {
                                0
                            }
                            UpdateStateBus.setState(
                                UpdateState.Downloading(
                                    info = info,
                                    percent = pct,
                                    downloadedBytes = downloaded,
                                    totalBytes = targetSize.coerceAtLeast(0L)
                                )
                            )
                            updateNotification(
                                buildProgressNotification(
                                    "Memuat turun SDProbe v${info.versionName} ($pct%)",
                                    pct,
                                    targetSize <= 0L
                                )
                            )
                        }
                    }
                    output.fd.sync()
                }
            }

            // Fasa Pengesahan Ketulenan Fail
            UpdateStateBus.setState(UpdateState.Verifying(info))
            updateNotification(buildProgressNotification("Mengesahkan fail APK...", 100, true))

            val actualSize = partFile.length()
            if (actualSize <= 0L) {
                throw IllegalStateException("Fail APK kosong selepas muat turun.")
            }
            if (info.expectedSizeBytes > 0L && actualSize != info.expectedSizeBytes) {
                throw IllegalStateException(
                    "Saiz fail tidak sepadan (Diterima: $actualSize B, Dijangka: ${info.expectedSizeBytes} B)."
                )
            }
            if (info.sha256.isNotBlank()) {
                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualSha.equals(info.sha256, ignoreCase = true)) {
                    throw IllegalStateException("Semakan SHA-256 gagal. Fail mungkin rosak.")
                }
            }

            if (finalApk.exists()) finalApk.delete()
            if (!partFile.renameTo(finalApk)) {
                throw IllegalStateException("Gagal memuktamadkan fail APK.")
            }

            UpdateStateBus.setState(UpdateState.ReadyToInstall(info, finalApk))
            postCompletionNotification(info)

            // Terus cuba buka dialog pemasangan Android secara automatik
            ApkInstaller.launchInstall(applicationContext, finalApk)
        } catch (t: Throwable) {
            partFile.delete()
            finalApk.delete()
            UpdateStateBus.setState(
                UpdateState.Error(t.message ?: "Muat turun kemaskini gagal.")
            )
        } finally {
            conn?.disconnect()
        }
    }

    private fun openFollowingRedirects(startUrl: String, maxRedirects: Int = 5): HttpURLConnection {
        var currentUrl = startUrl
        repeat(maxRedirects) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "SDProbe-Android/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            if (code in listOf(
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307,
                    308
                )
            ) {
                val nextLocation = conn.getHeaderField("Location")
                conn.disconnect()
                if (nextLocation.isNullOrBlank()) {
                    throw IllegalStateException("Pautan ubah hala (redirect) kosong.")
                }
                currentUrl = nextLocation
            } else if (code == HttpURLConnection.HTTP_OK) {
                return conn
            } else {
                conn.disconnect()
                throw IllegalStateException("Muat turun ditolak pelayan (HTTP $code).")
            }
        }
        throw IllegalStateException("Terlalu banyak ubah hala pautan.")
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                UpdateConfig.NOTIFICATION_ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(UpdateConfig.NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            UpdateConfig.NOTIFICATION_CHANNEL_ID,
            "Kemaskini SDProbe",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Paparan progres muat turun kemaskini APK SDProbe"
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildProgressNotification(
        text: String,
        percent: Int,
        indeterminate: Boolean
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(UpdateConfig.EXTRA_OPEN_UPDATE_DIALOG, true)
        }
        val openPi = PendingIntent.getActivity(
            this,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, UpdateDownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPi = PendingIntent.getService(
            this,
            102,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, UpdateConfig.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Kemaskini SDProbe")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, indeterminate)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batal", cancelPi)
            .build()
    }

    private fun updateNotification(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(UpdateConfig.NOTIFICATION_ID_PROGRESS, notification)
    }

    private fun postCompletionNotification(info: UpdateInfo) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val installTapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(UpdateConfig.EXTRA_OPEN_UPDATE_DIALOG, true)
            putExtra(UpdateConfig.EXTRA_AUTO_INSTALL_READY, true)
        }
        val pi = PendingIntent.getActivity(
            this,
            103,
            installTapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, UpdateConfig.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("SDProbe v${info.versionName} sedia dipasang")
            .setContentText("Tekan untuk teruskan pemasangan APK.")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        manager.notify(UpdateConfig.NOTIFICATION_ID_COMPLETE, notif)
    }

    private fun extractInfo(intent: Intent?): UpdateInfo? {
        if (intent == null) return null
        val url = intent.getStringExtra(EXTRA_APK_URL) ?: return null
        return UpdateInfo(
            versionCode = intent.getIntExtra(EXTRA_VERSION_CODE, 0),
            versionName = intent.getStringExtra(EXTRA_VERSION_NAME) ?: "",
            apkUrl = url,
            expectedSizeBytes = intent.getLongExtra(EXTRA_EXPECTED_SIZE, -1L),
            sha256 = intent.getStringExtra(EXTRA_SHA256) ?: "",
            releaseNotes = intent.getStringExtra(EXTRA_RELEASE_NOTES) ?: "",
            updatedAt = intent.getStringExtra(EXTRA_UPDATED_AT) ?: ""
        )
    }

    companion object {
        private const val ACTION_START = "com.muz.sdprobe.action.START_UPDATE_DOWNLOAD"
        private const val ACTION_CANCEL = "com.muz.sdprobe.action.CANCEL_UPDATE_DOWNLOAD"

        private const val EXTRA_VERSION_CODE = "extra_version_code"
        private const val EXTRA_VERSION_NAME = "extra_version_name"
        private const val EXTRA_APK_URL = "extra_apk_url"
        private const val EXTRA_EXPECTED_SIZE = "extra_expected_size"
        private const val EXTRA_SHA256 = "extra_sha256"
        private const val EXTRA_RELEASE_NOTES = "extra_release_notes"
        private const val EXTRA_UPDATED_AT = "extra_updated_at"

        fun start(context: Context, info: UpdateInfo) {
            if (UpdateStateBus.isDownloadActive()) return
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_VERSION_CODE, info.versionCode)
                putExtra(EXTRA_VERSION_NAME, info.versionName)
                putExtra(EXTRA_APK_URL, info.apkUrl)
                putExtra(EXTRA_EXPECTED_SIZE, info.expectedSizeBytes)
                putExtra(EXTRA_SHA256, info.sha256)
                putExtra(EXTRA_RELEASE_NOTES, info.releaseNotes)
                putExtra(EXTRA_UPDATED_AT, info.updatedAt)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            UpdateStateBus.requestCancel()
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            try {
                context.startService(intent)
            } catch (_: Throwable) {
                // Servis sudah tamat
            }
        }
    }
}
