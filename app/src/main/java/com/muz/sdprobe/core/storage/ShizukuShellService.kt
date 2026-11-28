package com.muz.sdprobe.core.storage

import android.content.Context
import android.os.Process
import androidx.annotation.Keep
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Servis UserService rasmi Shizuku yang berjalan dalam proses berasingan (UID 2000 / 0).
 * Bertanggungjawab menarik info perkakasan & storan peringkat sistem tanpa kaedah lapuk.
 */
@Keep
class ShizukuShellService : IShizukuShellService.Stub {

    @Keep
    constructor() : super()

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : super()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exit() {
        exitProcess(0)
    }

    override fun runShellCommand(command: String?): String {
        if (command.isNullOrBlank()) return ""
        return execRaw(listOf("sh", "-c", command))
    }

    override fun readSysFile(path: String?): String {
        if (path.isNullOrBlank() || !path.startsWith("/sys/")) return ""
        return try {
            File(path).readText().trim()
        } catch (t: Throwable) {
            "ERR:${t.javaClass.simpleName}:${t.message.orEmpty()}"
        }
    }

    override fun collectCardDiagnostics(uuid: String?, mountPath: String?): String {
        val safeUuid = sanitizeToken(uuid)
        val safePath = sanitizePath(mountPath)
        val root = JSONObject()

        root.put("uid", Process.myUid())

        // 1. Dapatkan senarai volume & disk melalui pengurus storan rasmi (sm)
        val smVolumes = execRaw(listOf("sm", "list-volumes", "public"))
        val smDisks = execRaw(listOf("sm", "list-disks"))
        val matchedVolumeId = findMatchingVolumeId(smVolumes, safeUuid)
        val matchedDiskId = findMatchingDiskId(smDisks, matchedVolumeId)
        root.put("smVolumeId", matchedVolumeId)
        root.put("smDiskId", matchedDiskId)

        // 2. Semak /proc/mounts untuk filesystem sebenar & status rw/ro
        val mountInfo = inspectMountTable(safeUuid, safePath)
        root.put("fsType", mountInfo.fsType)
        root.put("mountState", mountInfo.mountState)
        root.put("mountOptions", mountInfo.options)
        root.put("blockDevicePath", mountInfo.devicePath)

        // 3. Semak saiz kluster / blok melalui stat -f jika laluan wujud
        val clusterSize = inspectClusterSize(safePath)
        root.put("clusterSize", clusterSize)

        // 4. Imbas perkakasan CID / OEM di /sys/block/mmcblk* (kesan sekatan SELinux dengan jujur)
        val cidJson = inspectMmcHardware()
        root.put("cid", cidJson)

        return root.toString()
    }

    private data class MountEntry(
        val devicePath: String = "",
        val fsType: String = "",
        val options: String = "",
        val mountState: String = "UNKNOWN"
    )

    private fun inspectMountTable(uuid: String, mountPath: String): MountEntry {
        val lines = try {
            File("/proc/mounts").readLines()
        } catch (_: Throwable) {
            execRaw(listOf("mount")).lines()
        }

        val candidates = lines.filter { line ->
            (uuid.isNotBlank() && line.contains(uuid, ignoreCase = true)) ||
                (mountPath.isNotBlank() && line.contains(mountPath, ignoreCase = true))
        }

        if (candidates.isEmpty()) return MountEntry()

        // Utamakan baris mount perkakasan sebenar (exfat/vfat/sdfat/f2fs) berbanding lapisan FUSE/sdcardfs
        val preferred = candidates.firstOrNull { line ->
            val parts = line.split(Regex("\\s+"))
            val fs = parts.getOrNull(2).orEmpty().lowercase()
            fs in setOf("exfat", "vfat", "sdfat", "fat32", "ntfs", "ntfs3", "f2fs", "ext4")
        } ?: candidates.first()

        val parts = preferred.split(Regex("\\s+"))
        val device = parts.getOrNull(0).orEmpty()
        val fs = parts.getOrNull(2).orEmpty()
        val opts = parts.getOrNull(3).orEmpty()
        val optTokens = opts.split(",")
        val state = when {
            optTokens.contains("ro") -> "READ_ONLY"
            optTokens.contains("rw") -> "READ_WRITE"
            else -> "UNKNOWN"
        }

        return MountEntry(
            devicePath = device,
            fsType = normalizeFsName(fs),
            options = opts,
            mountState = state
        )
    }

    private fun normalizeFsName(rawFs: String): String {
        return when (rawFs.lowercase()) {
            "vfat" -> "FAT32 / vFAT"
            "exfat" -> "exFAT"
            "sdfat" -> "exFAT / FAT32 (sdFAT)"
            "fuse", "sdcardfs" -> "FUSE / Virtual (lapisan Android)"
            "" -> ""
            else -> rawFs.uppercase()
        }
    }

    private fun inspectClusterSize(mountPath: String): Long {
        if (mountPath.isBlank()) return -1L
        val out = execRaw(listOf("stat", "-f", "-c", "%S", mountPath)).trim()
        return out.toLongOrNull() ?: -1L
    }

    private fun findMatchingVolumeId(smVolumesOut: String, uuid: String): String {
        val lines = smVolumesOut.lines().map { it.trim() }.filter { it.startsWith("public:") }
        if (lines.isEmpty()) return ""
        if (uuid.isNotBlank()) {
            val match = lines.firstOrNull { it.contains(uuid, ignoreCase = true) }
            if (match != null) return match.substringBefore(" ")
        }
        return lines.first().substringBefore(" ")
    }

    private fun findMatchingDiskId(smDisksOut: String, volumeId: String): String {
        val disks = smDisksOut.lines().map { it.trim() }.filter { it.startsWith("disk:") }
        if (disks.isEmpty()) return ""
        val volMajor = volumeId.removePrefix("public:").substringBefore(",")
        if (volMajor.isNotBlank()) {
            val match = disks.firstOrNull { it.startsWith("disk:$volMajor,") }
            if (match != null) return match
        }
        return disks.first()
    }

    private fun inspectMmcHardware(): JSONObject {
        val out = JSONObject()
        val sysBlock = File("/sys/block")
        val blockDirs = try {
            sysBlock.listFiles()
        } catch (se: SecurityException) {
            out.put("status", "SELINUX_BLOCKED")
            out.put("note", "Akses ke /sys/block disekat SELinux (${se.message.orEmpty()})")
            return out
        }

        if (blockDirs == null) {
            val probe = execRaw(listOf("ls", "/sys/block"))
            if (probe.contains("Permission denied", ignoreCase = true)) {
                out.put("status", "SELINUX_BLOCKED")
                out.put("note", "Senarai /sys/block disekat polisi SELinux peranti ini.")
            } else {
                out.put("status", "NOT_FOUND")
                out.put("note", "Tiada entri /sys/block ditemui.")
            }
            return out
        }

        val mmcRegex = Regex("^mmcblk[0-9]+$")
        val mmcCandidates = blockDirs
            .filter { mmcRegex.matches(it.name) }
            .sortedBy { it.name }

        if (mmcCandidates.isEmpty()) {
            out.put("status", "NOT_FOUND")
            out.put("note", "Tiada peranti mmcblk ditemui (mungkin guna pembaca USB OTG).")
            return out
        }

        // Cari peranti yang berjenis "SD" dahulu; elak eMMC dalaman ("MMC")
        var selectedDev: File? = null
        var sawPermissionDenied = false

        for (dir in mmcCandidates) {
            val typeFile = File(dir, "device/type")
            val typeResult = safeReadFile(typeFile)
            if (typeResult.denied) {
                sawPermissionDenied = true
                continue
            }
            if (typeResult.text.equals("SD", ignoreCase = true)) {
                selectedDev = dir
                break
            }
        }

        // Sandaran: jika tiada yang boleh dibaca device/type, cuba mmcblk1 jika wujud
        if (selectedDev == null) {
            selectedDev = mmcCandidates.firstOrNull { it.name != "mmcblk0" } ?: mmcCandidates.first()
        }

        val devDir = File(selectedDev, "device")
        val cidRead = safeReadFile(File(devDir, "cid"))
        val nameRead = safeReadFile(File(devDir, "name"))
        val manfRead = safeReadFile(File(devDir, "manfid"))
        val oemRead = safeReadFile(File(devDir, "oemid"))
        val dateRead = safeReadFile(File(devDir, "date"))
        val serialRead = safeReadFile(File(devDir, "serial"))
        val typeRead = safeReadFile(File(devDir, "type"))
        val eraseRead = safeReadFile(File(devDir, "preferred_erase_size"))
        val sectorsRead = safeReadFile(File(selectedDev, "size"))

        val anyDenied = sawPermissionDenied || cidRead.denied || manfRead.denied || nameRead.denied
        val hasAnyData = cidRead.text.isNotBlank() || nameRead.text.isNotBlank() || manfRead.text.isNotBlank()

        if (!hasAnyData && anyDenied) {
            out.put("status", "SELINUX_BLOCKED")
            out.put("blockDevice", selectedDev.name)
            out.put("note", "ROM peranti menyekat bacaan /sys/block/${selectedDev.name}/device/cid melalui SELinux.")
            return out
        }

        if (!hasAnyData) {
            out.put("status", "NOT_FOUND")
            out.put("blockDevice", selectedDev.name)
            out.put("note", "Atribut CID tidak didedahkan oleh kernel peranti ini.")
            return out
        }

        val sectors = sectorsRead.text.toLongOrNull() ?: -1L
        val rawBytes = if (sectors > 0L) sectors * 512L else -1L
        val eraseBytes = eraseRead.text.toLongOrNull() ?: -1L

        out.put("status", "AVAILABLE")
        out.put("blockDevice", selectedDev.name)
        out.put("cardType", typeRead.text.ifBlank { "SD" })
        out.put("cid", cidRead.text)
        out.put("manfid", manfRead.text)
        out.put("oemid", oemRead.text)
        out.put("name", nameRead.text)
        out.put("date", dateRead.text)
        out.put("serial", serialRead.text)
        out.put("rawBytes", rawBytes)
        out.put("eraseBytes", eraseBytes)
        out.put("note", if (cidRead.denied) "Sebahagian atribut disekat SELinux." else "")

        return out
    }

    private data class ReadResult(val text: String = "", val denied: Boolean = false)

    private fun safeReadFile(file: File): ReadResult {
        return try {
            if (!file.exists()) return ReadResult()
            ReadResult(text = file.readText().trim(), denied = false)
        } catch (t: Throwable) {
            val msg = t.message.orEmpty()
            val isDenied = t is SecurityException ||
                msg.contains("EACCES", ignoreCase = true) ||
                msg.contains("Permission denied", ignoreCase = true)
            ReadResult(text = "", denied = isDenied)
        }
    }

    private fun execRaw(cmd: List<String>, timeoutSec: Long = 6L): String {
        return try {
            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return ""
            }
            proc.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun sanitizeToken(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace(Regex("[^a-zA-Z0-9_-]"), "")
    }

    private fun sanitizePath(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace(Regex("[^a-zA-Z0-9._/:-]"), "")
    }
}
