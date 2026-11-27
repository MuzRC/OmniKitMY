package com.muz.omnikit.tools.audio

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.muz.omnikit.BuildConfig
import com.muz.omnikit.core.media.MediaExportHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

sealed class ShareSource(
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String
) {
    class LocalFile(
        val file: File,
        displayName: String,
        mimeType: String
    ) : ShareSource(displayName, file.length(), mimeType)

    class ContentUri(
        val uri: Uri,
        displayName: String,
        sizeBytes: Long,
        mimeType: String
    ) : ShareSource(displayName, sizeBytes, mimeType)

    fun openStream(context: Context): InputStream? = when (this) {
        is LocalFile -> runCatching { FileInputStream(file) }.getOrNull()
        is ContentUri -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }
}

data class LanInterfaceAddress(
    val interfaceName: String,
    val ipAddress: String,
    val isLikelyHotspot: Boolean
)

data class DownloadedLocalFile(
    val fileName: String,
    val sizeBytes: Long,
    val isApk: Boolean,
    val localApkPath: String?
)

class LocalLanTransferServer(
    private val context: Context,
    private val onDownloadCountChanged: (Int) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val downloads = AtomicInteger(0)

    @Volatile
    var activeSource: ShareSource? = null
        private set

    @Volatile
    var activeToken: String = ""
        private set

    @Volatile
    var boundPort: Int = 0
        private set

    fun start(source: ShareSource): Result<Int> {
        stop()
        activeSource = source
        activeToken = UUID.randomUUID().toString().replace("-", "").take(4).uppercase(Locale.US)
        downloads.set(0)

        val socket = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(8765))
            }
        }.getOrElse {
            runCatching {
                ServerSocket(0).apply { reuseAddress = true }
            }.getOrElse { err ->
                return Result.failure(err)
            }
        }

        serverSocket = socket
        boundPort = socket.localPort

        acceptJob = scope.launch {
            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch {
                    handleClient(client)
                }
            }
        }
        return Result.success(boundPort)
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        boundPort = 0
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    private fun handleClient(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 15_000
            val input = BufferedInputStream(sock.getInputStream())
            val output = BufferedOutputStream(sock.getOutputStream())

            val requestLine = readAsciiLine(input) ?: return
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) break
            }

            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0)?.uppercase(Locale.US) ?: "GET"
            val rawPath = parts.getOrNull(1)?.substringBefore('?') ?: "/"
            val source = activeSource

            if (source == null || (method != "GET" && method != "HEAD")) {
                writeSimpleResponse(output, "404 Not Found", "text/plain; charset=utf-8", "No active file shared.")
                return
            }

            val dlPath = "/dl/$activeToken"
            when {
                rawPath == "/" || rawPath == "/$activeToken" -> {
                    val html = buildLandingHtml(source, dlPath)
                    writeSimpleResponse(output, "200 OK", "text/html; charset=utf-8", html)
                }

                rawPath == dlPath || rawPath == "/download" -> {
                    val safeFileName = source.displayName.replace("\"", "_")
                    val headers = StringBuilder().apply {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: ${source.mimeType}\r\n")
                        if (source.sizeBytes > 0L) {
                            append("Content-Length: ${source.sizeBytes}\r\n")
                        }
                        append("Content-Disposition: attachment; filename=\"$safeFileName\"\r\n")
                        append("Cache-Control: no-store\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    output.write(headers.toString().toByteArray(StandardCharsets.UTF_8))
                    if (method == "GET") {
                        source.openStream(context)?.use { stream ->
                            val buf = ByteArray(32 * 1024)
                            while (true) {
                                val read = stream.read(buf)
                                if (read == -1) break
                                output.write(buf, 0, read)
                            }
                            output.flush()
                        }
                        val count = downloads.incrementAndGet()
                        scope.launch(Dispatchers.Main) {
                            onDownloadCountChanged(count)
                        }
                    } else {
                        output.flush()
                    }
                }

                else -> {
                    writeSimpleResponse(output, "404 Not Found", "text/plain; charset=utf-8", "Invalid SuaraDrop link.")
                }
            }
        }
    }

    private fun writeSimpleResponse(out: BufferedOutputStream, status: String, contentType: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $status\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun buildLandingHtml(source: ShareSource, dlPath: String): String {
        val sizeStr = formatLanBytes(source.sizeBytes)
        val escapedName = source.displayName
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return buildString {
            append("<!DOCTYPE html><html lang=\"ms\"><head><meta charset=\"utf-8\"/>")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>")
            append("<title>OmniKitMY - SuaraDrop Pro</title>")
            append("<style>")
            append("body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0A1222;color:#F1F5F9;font-family:system-ui,-apple-system,sans-serif;padding:20px;box-sizing:border-box}")
            append(".card{width:100%;max-width:420px;background:#101B30;border:1px solid #283A5C;border-radius:22px;padding:26px;text-align:center;box-shadow:0 18px 42px rgba(0,0,0,.45)}")
            append(".badge{display:inline-block;background:#0F3559;color:#38BDF8;padding:6px 14px;border-radius:999px;font-size:12px;font-weight:700;letter-spacing:.5px;margin-bottom:14px}")
            append("h1{margin:0 0 6px;font-size:22px}")
            append(".file{background:#162440;border-radius:14px;padding:14px;margin:16px 0;word-break:break-all;font-family:ui-monospace,monospace;font-size:14px;color:#38BDF8}")
            append(".meta{color:#94A3B8;font-size:13px;margin-bottom:20px}")
            append(".btn{display:block;width:100%;box-sizing:border-box;background:#0284C7;color:#fff;text-decoration:none;font-weight:700;font-size:16px;padding:15px 20px;border-radius:14px}")
            append(".note{margin-top:16px;font-size:12px;color:#94A3B8;line-height:1.5}")
            append("</style></head><body><div class=\"card\">")
            append("<div class=\"badge\">OMNIKITMY • SUARADROP PRO (OFFLINE)</div>")
            append("<h1>Fail Sedia Dimuat Turun</h1>")
            append("<div class=\"file\">").append(escapedName).append("</div>")
            append("<div class=\"meta\">Saiz: ").append(sizeStr).append(" • 0 KB Data Internet</div>")
            append("<a class=\"btn\" href=\"").append(dlPath).append("\">Muat Turun Sekarang / Download</a>")
            append("<div class=\"note\">Pindahan terus melalui Wi-Fi / Hotspot tempatan tanpa sambungan internet.</div>")
            append("</div></body></html>")
        }
    }

    companion object {
        fun buildSelfApkSource(context: Context): ShareSource.LocalFile? {
            val apkPath = context.applicationInfo.publicSourceDir ?: context.applicationInfo.sourceDir ?: return null
            val apkFile = File(apkPath)
            if (!apkFile.exists() || !apkFile.canRead()) return null
            return ShareSource.LocalFile(
                file = apkFile,
                displayName = "OmniKitMY-v${BuildConfig.VERSION_NAME}.apk",
                mimeType = "application/vnd.android.package-archive"
            )
        }

        fun buildUriSource(context: Context, uri: Uri): ShareSource.ContentUri {
            var name = "OmniKitMY_File_${System.currentTimeMillis()}"
            var size = -1L
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) c.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                        if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                    }
                }
            }
            val mime = context.contentResolver.getType(uri)
                ?: if (name.endsWith(".apk", ignoreCase = true)) "application/vnd.android.package-archive" else "application/octet-stream"
            return ShareSource.ContentUri(uri = uri, displayName = name, sizeBytes = size, mimeType = mime)
        }

        fun getLocalIpv4Addresses(): List<LanInterfaceAddress> {
            val found = mutableListOf<LanInterfaceAddress>()
            runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
                while (interfaces.hasMoreElements()) {
                    val nif = interfaces.nextElement()
                    if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
                    val name = nif.name.lowercase(Locale.US)
                    if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun") || name.startsWith("dummy")) {
                        continue
                    }
                    val addrs = nif.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                            val ip = addr.hostAddress ?: continue
                            val isHotspot = name.contains("ap") ||
                                name.contains("swlan") ||
                                name.contains("wlan1") ||
                                ip.startsWith("192.168.43.") ||
                                ip.startsWith("192.168.137.") ||
                                ip.startsWith("192.168.49.")
                            found.add(LanInterfaceAddress(nif.name, ip, isHotspot))
                        }
                    }
                }
            }
            return found.sortedByDescending { if (it.isLikelyHotspot) 2 else if (it.interfaceName.startsWith("wlan")) 1 else 0 }
        }

        /**
         * Downloads a file from a local SuaraDrop HTTP endpoint using a raw Socket explicitly
         * bound to Wi-Fi when available, bypassing Android cleartext restrictions and ensuring
         * downloads succeed over no-internet Hotspots even if Mobile Data is left ON.
         */
        suspend fun downloadFromCompactKey(
            context: Context,
            compactKey: String,
            onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
        ): Result<DownloadedLocalFile> = withContext(Dispatchers.IO) {
            val normalized = compactKey.trim()
                .removePrefix("http://")
                .removePrefix("https://")
            val hostPort = normalized.substringBefore('/')
            val token = normalized.substringAfter('/', "").removePrefix("dl/").trim()
            val host = hostPort.substringBefore(':')
            val port = hostPort.substringAfter(':', "8765").toIntOrNull() ?: 8765

            if (host.isBlank() || token.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Invalid local transfer address"))
            }

            val socket = openWifiPreferredSocket(context, host, port)
                .getOrElse { return@withContext Result.failure(it) }

            try {
                socket.use { sock ->
                    sock.soTimeout = 20_000
                    val out = sock.getOutputStream()
                    val req = "GET /dl/$token HTTP/1.1\r\n" +
                        "Host: $host:$port\r\n" +
                        "User-Agent: OmniKitMY-SuaraDrop\r\n" +
                        "Connection: close\r\n\r\n"
                    out.write(req.toByteArray(StandardCharsets.UTF_8))
                    out.flush()

                    val input = BufferedInputStream(sock.getInputStream(), 32 * 1024)
                    val statusLine = readAsciiLine(input)
                        ?: return@withContext Result.failure(IllegalStateException("Empty HTTP response"))
                    if (!statusLine.contains(" 200 ")) {
                        return@withContext Result.failure(IllegalStateException("Server error: $statusLine"))
                    }

                    var contentLength = -1L
                    var contentType = "application/octet-stream"
                    var fileName = "SuaraDrop_${System.currentTimeMillis()}.bin"

                    while (true) {
                        val line = readAsciiLine(input) ?: break
                        if (line.isEmpty()) break
                        val colon = line.indexOf(':')
                        if (colon > 0) {
                            val key = line.substring(0, colon).trim().lowercase(Locale.US)
                            val value = line.substring(colon + 1).trim()
                            when (key) {
                                "content-length" -> contentLength = value.toLongOrNull() ?: -1L
                                "content-type" -> contentType = value.substringBefore(';').trim()
                                "content-disposition" -> {
                                    val fnMarker = "filename="
                                    val idx = value.indexOf(fnMarker, ignoreCase = true)
                                    if (idx >= 0) {
                                        fileName = value.substring(idx + fnMarker.length)
                                            .trim()
                                            .trim('"')
                                            .substringAfterLast('/')
                                            .substringAfterLast('\\')
                                            .ifBlank { fileName }
                                    }
                                }
                            }
                        }
                    }

                    val isApk = fileName.endsWith(".apk", ignoreCase = true) ||
                        contentType.equals("application/vnd.android.package-archive", ignoreCase = true)

                    if (isApk) {
                        val dlDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                        dlDir.mkdirs()
                        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                            .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
                        val destFile = File(dlDir, safeName)
                        var copied = 0L
                        var lastReport = 0L
                        val buf = ByteArray(32 * 1024)
                        FileOutputStream(destFile).use { fos ->
                            while (isActive) {
                                val r = input.read(buf)
                                if (r == -1) break
                                fos.write(buf, 0, r)
                                copied += r
                                val now = System.currentTimeMillis()
                                if (now - lastReport >= 120L || (contentLength > 0 && copied >= contentLength)) {
                                    lastReport = now
                                    withContext(Dispatchers.Main) { onProgress(copied, contentLength) }
                                }
                            }
                        }
                        Result.success(
                            DownloadedLocalFile(
                                fileName = safeName,
                                sizeBytes = destFile.length(),
                                isApk = true,
                                localApkPath = destFile.absolutePath
                            )
                        )
                    } else {
                        var copied = 0L
                        val buf = ByteArray(32 * 1024)
                        val savedUri = MediaExportHelper.saveBytesToDownloads(
                            context = context,
                            displayName = fileName,
                            mimeType = contentType
                        ) { fos ->
                            while (isActive) {
                                val r = input.read(buf)
                                if (r == -1) break
                                fos.write(buf, 0, r)
                                copied += r
                            }
                        }
                        withContext(Dispatchers.Main) { onProgress(copied, contentLength) }
                        if (savedUri == null) {
                            Result.failure(IllegalStateException("Failed to save file to Downloads/OmniKitMY"))
                        } else {
                            Result.success(
                                DownloadedLocalFile(
                                    fileName = fileName,
                                    sizeBytes = copied,
                                    isApk = false,
                                    localApkPath = null
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        private fun openWifiPreferredSocket(context: Context, host: String, port: Int): Result<Socket> {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val wifiNet: Network? = runCatching {
                @Suppress("DEPRECATION")
                cm?.allNetworks?.firstOrNull { net ->
                    cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }
            }.getOrNull()

            if (wifiNet != null) {
                val boundResult = runCatching {
                    val s = wifiNet.socketFactory.createSocket()
                    s.connect(InetSocketAddress(host, port), 7000)
                    s
                }
                if (boundResult.isSuccess) return boundResult
            }

            return runCatching {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 7000)
                s
            }
        }

        private fun readAsciiLine(input: InputStream): String? {
            val baos = ByteArrayOutputStream()
            var any = false
            while (true) {
                val b = input.read()
                if (b == -1) {
                    return if (any) baos.toString(StandardCharsets.UTF_8.name()) else null
                }
                any = true
                if (b == '\n'.code) break
                if (b != '\r'.code) baos.write(b)
            }
            return baos.toString(StandardCharsets.UTF_8.name())
        }

        fun formatLanBytes(bytes: Long): String = when {
            bytes < 0L -> "--"
            bytes >= 1_048_576L -> String.format(Locale.US, "%.2f MB", bytes / 1_048_576.0)
            bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
