@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.win

import com.example.timeboxvibe.engine.SongCatalog
import com.example.timeboxvibe.engine.core.TimerPreset
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.windows.CREATE_ALWAYS
import platform.windows.CloseHandle
import platform.windows.CreateDirectoryW
import platform.windows.CreateFileW
import platform.windows.DWORDVar
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_READ
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetEnvironmentVariableW
import platform.windows.GetFileSize
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OPEN_EXISTING
import platform.windows.ReadFile
import platform.windows.WriteFile

/**
 * Dummy persistence terminal. Engine settings stay in TimerActions;
 * this only reads and writes %APPDATA%\\TimeBox\\settings.txt.
 */
internal class Win32SettingsStore {
    fun load(): PersistedWin32Settings {
        val bytes = readAllBytes() ?: return PersistedWin32Settings()
        return parse(bytes.decodeToString())
    }

    fun save(settings: PersistedWin32Settings) {
        val dir = settingsDirectory() ?: return
        CreateDirectoryW(dir, null)
        val path = settingsFilePath() ?: return
        val text = encode(settings)
        val bytes = text.encodeToByteArray()
        val handle = CreateFileW(
            path,
            GENERIC_WRITE.toUInt(),
            0u,
            null,
            CREATE_ALWAYS.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(),
            null
        )
        if (handle == null || handle == INVALID_HANDLE_VALUE) return
        try {
            memScoped {
                val written = alloc<DWORDVar>()
                bytes.usePinned { pinned ->
                    WriteFile(
                        handle,
                        pinned.addressOf(0),
                        bytes.size.toUInt(),
                        written.ptr,
                        null
                    )
                }
            }
        } finally {
            CloseHandle(handle)
        }
    }

    private fun readAllBytes(): ByteArray? {
        val path = settingsFilePath() ?: return null
        val handle = CreateFileW(
            path,
            GENERIC_READ.toUInt(),
            FILE_SHARE_READ.toUInt(),
            null,
            OPEN_EXISTING.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(),
            null
        )
        if (handle == null || handle == INVALID_HANDLE_VALUE) return null
        try {
            val size = GetFileSize(handle, null)
            if (size == INVALID_FILE_SIZE || size == 0u) return null
            if (size > MAX_SETTINGS_FILE_BYTES.toUInt()) return null
            val bytes = ByteArray(size.toInt())
            memScoped {
                val read = alloc<DWORDVar>()
                val ok = bytes.usePinned { pinned ->
                    ReadFile(
                        handle,
                        pinned.addressOf(0),
                        size,
                        read.ptr,
                        null
                    )
                }
                if (ok == 0 || read.value != size) return null
            }
            return bytes
        } finally {
            CloseHandle(handle)
        }
    }

    private fun settingsDirectory(): String? {
        val appData = roamingAppData() ?: return null
        return appData + "\\" + SETTINGS_DIR_NAME
    }

    private fun settingsFilePath(): String? {
        val dir = settingsDirectory() ?: return null
        return dir + "\\" + SETTINGS_FILE_NAME
    }

    private fun roamingAppData(): String? = memScoped {
        val buffer = allocArray<UShortVar>(MAX_SETTINGS_PATH)
        val length = GetEnvironmentVariableW("APPDATA", buffer, MAX_SETTINGS_PATH.toUInt())
        if (length == 0u || length >= MAX_SETTINGS_PATH.toUInt()) return null
        val builder = StringBuilder(length.toInt())
        var index = 0
        while (index < length.toInt()) {
            val unit = buffer[index].toInt()
            if (unit == 0) break
            builder.append(unit.toChar())
            index++
        }
        if (builder.isEmpty()) null else builder.toString()
    }

    private fun encode(settings: PersistedWin32Settings): String {
        val builder = StringBuilder()
        appendPair(builder, "language", settings.language)
        appendPair(builder, "appTheme", settings.appTheme)
        appendPair(builder, "currentTaskName", settings.currentTask)
        appendPair(builder, "strictMode", if (settings.strictMode) "1" else "0")
        appendPair(builder, "tickEnabled", if (settings.tickEnabled) "1" else "0")
        appendPair(builder, "vibeIntensity", settings.vibeIntensity.toString())
        appendPair(builder, "volume", settings.volume.toString())
        appendPair(builder, "selectedFocusSound", settings.selectedFocusSound)
        appendPair(builder, "selectedRelaxSound", settings.selectedRelaxSound)
        appendPair(builder, "activePresetId", settings.activePresetId)
        appendPair(builder, "custom_presets_json", PRESET_JSON.encodeToString(settings.customPresets))
        return builder.toString()
    }

    private fun appendPair(builder: StringBuilder, key: String, value: String) {
        builder.append(key)
        builder.append('=')
        builder.append(value.replace("\r", "").replace("\n", " "))
        builder.append('\n')
    }

    private fun parse(text: String): PersistedWin32Settings {
        var language = "en"
        var appTheme = "reimu"
        var currentTask = ""
        var strictMode = false
        var tickEnabled = false
        var vibeIntensity = 0.8f
        var volume = 0.5f
        var selectedFocusSound = SongCatalog.DEFAULT_FOCUS_ID
        var selectedRelaxSound = SongCatalog.DEFAULT_RELAX_ID
        var activePresetId = "dual_box"
        var customPresets = emptyList<TimerPreset>()
        val lines = text.split('\n')
        var lineIndex = 0
        while (lineIndex < lines.size) {
            val raw = lines[lineIndex]
            lineIndex++
            val line = if (raw.endsWith('\r')) raw.substring(0, raw.length - 1) else raw
            if (line.isEmpty()) continue
            val split = line.indexOf('=')
            if (split <= 0) continue
            val key = line.substring(0, split)
            val value = line.substring(split + 1)
            when (key) {
                "language" -> if (value.isNotEmpty()) language = value
                "appTheme" -> if (value.isNotEmpty()) appTheme = value
                "currentTaskName" -> currentTask = value
                "strictMode" -> strictMode = value == "1" || value == "true"
                "tickEnabled" -> tickEnabled = value == "1" || value == "true"
                "vibeIntensity" -> value.toFloatOrNull()?.let { vibeIntensity = it }
                "volume" -> value.toFloatOrNull()?.let { volume = it }
                "selectedFocusSound", "selectedSound" -> {
                    if (SongCatalog.byId(value) != null) selectedFocusSound = value
                }
                "selectedRelaxSound" -> {
                    if (SongCatalog.byId(value) != null) selectedRelaxSound = value
                }
                "activePresetId" -> if (value.isNotEmpty()) activePresetId = value
                "custom_presets_json" -> customPresets = parseCustomPresets(value)
            }
        }
        return PersistedWin32Settings(
            language = language,
            appTheme = appTheme,
            currentTask = currentTask,
            strictMode = strictMode,
            tickEnabled = tickEnabled,
            vibeIntensity = vibeIntensity,
            volume = volume,
            selectedFocusSound = selectedFocusSound,
            selectedRelaxSound = selectedRelaxSound,
            activePresetId = activePresetId,
            customPresets = customPresets
        )
    }

    private fun parseCustomPresets(json: String): List<TimerPreset> {
        if (json.isEmpty()) return emptyList()
        return try {
            PRESET_JSON.decodeFromString<List<TimerPreset>>(json).map { it.normalized(logFailures = true) }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

internal data class PersistedWin32Settings(
    val language: String = "en",
    val appTheme: String = "reimu",
    val currentTask: String = "",
    val strictMode: Boolean = false,
    val tickEnabled: Boolean = false,
    val vibeIntensity: Float = 0.8f,
    val volume: Float = 0.5f,
    val selectedFocusSound: String = SongCatalog.DEFAULT_FOCUS_ID,
    val selectedRelaxSound: String = SongCatalog.DEFAULT_RELAX_ID,
    val activePresetId: String = "dual_box",
    val customPresets: List<TimerPreset> = emptyList()
)

private const val SETTINGS_DIR_NAME = "TimeBox"
private const val SETTINGS_FILE_NAME = "settings.txt"
private const val MAX_SETTINGS_PATH = 512
private const val MAX_SETTINGS_FILE_BYTES = 262144
private const val INVALID_FILE_SIZE: UInt = 0xFFFFFFFFu

private val PRESET_JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
