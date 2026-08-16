@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.win

import com.example.timeboxvibe.engine.SongCatalog
import com.example.timeboxvibe.engine.core.TimerPreset
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.windows.CREATE_ALWAYS
import platform.windows.CloseHandle
import platform.windows.CreateDirectoryW
import platform.windows.CreateEventW
import platform.windows.CreateFileW
import platform.windows.CreateMutexW
import platform.windows.CreateThread
import platform.windows.DeleteFileW
import platform.windows.DWORDVar
import platform.windows.FALSE
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_READ
import platform.windows.FlushFileBuffers
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetEnvironmentVariableW
import platform.windows.GetFileSize
import platform.windows.HANDLE
import platform.windows.HANDLEVar
import platform.windows.INFINITE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.MOVEFILE_REPLACE_EXISTING
import platform.windows.MOVEFILE_WRITE_THROUGH
import platform.windows.MoveFileExW
import platform.windows.OPEN_EXISTING
import platform.windows.ReadFile
import platform.windows.ReleaseMutex
import platform.windows.SetEvent
import platform.windows.TRUE
import platform.windows.WAIT_ABANDONED
import platform.windows.WAIT_OBJECT_0
import platform.windows.WAIT_TIMEOUT
import platform.windows.WaitForMultipleObjects
import platform.windows.WaitForSingleObject
import platform.windows.WriteFile

/**
 * Dummy persistence terminal. Engine settings stay in TimerActions;
 * this only reads and writes %APPDATA%\\TimeBox\\settings.txt.
 */
internal class Win32SettingsStore {
    private val saveEvent: HANDLE? = CreateEventW(null, FALSE, FALSE, null)
    private val stopEvent: HANDLE? = CreateEventW(null, TRUE, FALSE, null)
    private val stateMutex: HANDLE? = CreateMutexW(null, FALSE, null)
    private var writerThread: HANDLE? = null
    private var stableSelf: StableRef<Win32SettingsStore>? = null
    private var pendingSave = false
    private var pendingLanguage = "en"
    private var pendingAppTheme = "reimu"
    private var pendingCurrentTask = ""
    private var pendingStrictMode = false
    private var pendingTickEnabled = false
    private var pendingVibeIntensity = 0.8f
    private var pendingVolume = 0.5f
    private var pendingFocusSound = SongCatalog.DEFAULT_FOCUS_ID
    private var pendingRelaxSound = SongCatalog.DEFAULT_RELAX_ID
    private var pendingActivePresetId = "dual_box"
    private var pendingCustomPresets = emptyList<TimerPreset>()
    private var closed = false

    init {
        startWriter()
    }

    fun load(): PersistedWin32Settings {
        val bytes = readAllBytes() ?: return PersistedWin32Settings()
        return parse(bytes.decodeToString())
    }

    fun requestSave(
        language: String,
        appTheme: String,
        currentTask: String,
        strictMode: Boolean,
        tickEnabled: Boolean,
        vibeIntensity: Float,
        volume: Float,
        selectedFocusSound: String,
        selectedRelaxSound: String,
        activePresetId: String,
        customPresets: List<TimerPreset>
    ) {
        if (closed) return
        if (writerThread == null) {
            writeSettingsAtomically(
                PersistedWin32Settings(
                    language,
                    appTheme,
                    currentTask,
                    strictMode,
                    tickEnabled,
                    vibeIntensity,
                    volume,
                    selectedFocusSound,
                    selectedRelaxSound,
                    activePresetId,
                    customPresets
                )
            )
            return
        }
        if (!replacePending(
                language,
                appTheme,
                currentTask,
                strictMode,
                tickEnabled,
                vibeIntensity,
                volume,
                selectedFocusSound,
                selectedRelaxSound,
                activePresetId,
                customPresets
            )
        ) {
            println("Win32 settings queue lock failed")
            return
        }
        if (saveEvent?.let { SetEvent(it) } == 0) {
            println("SetEvent failed for Win32 settings writer")
        }
    }

    fun shutdown() {
        if (closed) return
        val thread = writerThread
        if (thread != null) {
            val stop = stopEvent
            if (stop == null || SetEvent(stop) == 0) {
                println("Win32 settings writer could not be stopped; native handles were retained")
                return
            }
            val waitResult = WaitForSingleObject(thread, INFINITE)
            if (waitResult != WAIT_OBJECT_0) {
                println("Win32 settings writer did not terminate; native handles were retained")
                return
            }
            CloseHandle(thread)
            writerThread = null
        } else {
            takePendingSettings()?.let { writeSettingsAtomically(it) }
        }

        stableSelf?.dispose()
        stableSelf = null
        saveEvent?.let { CloseHandle(it) }
        stopEvent?.let { CloseHandle(it) }
        stateMutex?.let { CloseHandle(it) }
        closed = true
    }

    private fun startWriter() {
        if (saveEvent == null || stopEvent == null || stateMutex == null) {
            println("Win32 settings writer initialization failed; using synchronous fallback")
            return
        }
        val ref = StableRef.create(this)
        val thread = CreateThread(
            null,
            0u,
            staticCFunction(::win32SettingsWriterThunk),
            ref.asCPointer(),
            0u,
            null
        )
        if (thread == null) {
            ref.dispose()
            println("CreateThread failed for Win32 settings writer; using synchronous fallback")
            return
        }
        stableSelf = ref
        writerThread = thread
    }

    internal fun writerLoop() {
        val save = saveEvent ?: return
        val stop = stopEvent ?: return
        memScoped {
            val handles = allocArray<HANDLEVar>(2)
            handles[0] = stop
            handles[1] = save
            var debounceActive = false
            while (true) {
                val timeout = if (debounceActive) SETTINGS_SAVE_DEBOUNCE_MS else INFINITE
                when (WaitForMultipleObjects(2u, handles, FALSE, timeout)) {
                    WAIT_OBJECT_0 -> {
                        drainPendingWrites()
                        return
                    }
                    WAIT_OBJECT_0 + 1u -> debounceActive = true
                    WAIT_TIMEOUT.toUInt() -> {
                        writePendingOnce()
                        debounceActive = false
                    }
                    else -> {
                        println("WaitForMultipleObjects failed for Win32 settings writer")
                        drainPendingWrites()
                        return
                    }
                }
            }
        }
    }

    private fun drainPendingWrites() {
        while (true) {
            val settings = takePendingSettings() ?: return
            writeSettingsAtomically(settings)
        }
    }

    private fun writePendingOnce() {
        val settings = takePendingSettings() ?: return
        writeSettingsAtomically(settings)
    }

    private fun writeSettingsAtomically(settings: PersistedWin32Settings): Boolean {
        return writeBytesAtomically(encode(settings).encodeToByteArray())
    }

    private fun writeBytesAtomically(bytes: ByteArray): Boolean {
        val dir = settingsDirectory() ?: return false
        CreateDirectoryW(dir, null)
        val path = settingsFilePath() ?: return false
        val temporaryPath = path + SETTINGS_TEMP_SUFFIX
        val handle = CreateFileW(
            temporaryPath,
            GENERIC_WRITE.toUInt(),
            0u,
            null,
            CREATE_ALWAYS.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(),
            null
        )
        if (handle == null || handle == INVALID_HANDLE_VALUE) {
            println("CreateFileW failed for temporary Win32 settings file")
            return false
        }
        var complete = false
        try {
            memScoped {
                val written = alloc<DWORDVar>()
                val ok = bytes.usePinned { pinned ->
                    WriteFile(
                        handle,
                        pinned.addressOf(0),
                        bytes.size.toUInt(),
                        written.ptr,
                        null
                    )
                }
                complete = ok != 0 && written.value == bytes.size.toUInt() && FlushFileBuffers(handle) != 0
            }
        } finally {
            CloseHandle(handle)
        }
        if (!complete) {
            DeleteFileW(temporaryPath)
            println("WriteFile failed for Win32 settings")
            return false
        }
        val moved = MoveFileExW(
            temporaryPath,
            path,
            (MOVEFILE_REPLACE_EXISTING.toInt() or MOVEFILE_WRITE_THROUGH.toInt()).toUInt()
        ) != 0
        if (!moved) {
            DeleteFileW(temporaryPath)
            println("MoveFileExW failed for Win32 settings")
        }
        return moved
    }

    private fun replacePending(
        language: String,
        appTheme: String,
        currentTask: String,
        strictMode: Boolean,
        tickEnabled: Boolean,
        vibeIntensity: Float,
        volume: Float,
        selectedFocusSound: String,
        selectedRelaxSound: String,
        activePresetId: String,
        customPresets: List<TimerPreset>
    ): Boolean {
        val mutex = stateMutex ?: return false
        val waitResult = WaitForSingleObject(mutex, INFINITE)
        if (waitResult != WAIT_OBJECT_0 && waitResult != WAIT_ABANDONED) return false
        try {
            pendingLanguage = language
            pendingAppTheme = appTheme
            pendingCurrentTask = currentTask
            pendingStrictMode = strictMode
            pendingTickEnabled = tickEnabled
            pendingVibeIntensity = vibeIntensity
            pendingVolume = volume
            pendingFocusSound = selectedFocusSound
            pendingRelaxSound = selectedRelaxSound
            pendingActivePresetId = activePresetId
            pendingCustomPresets = customPresets
            pendingSave = true
            return true
        } finally {
            ReleaseMutex(mutex)
        }
    }

    private fun takePendingSettings(): PersistedWin32Settings? {
        val mutex = stateMutex ?: return null
        val waitResult = WaitForSingleObject(mutex, INFINITE)
        if (waitResult != WAIT_OBJECT_0 && waitResult != WAIT_ABANDONED) return null
        try {
            if (!pendingSave) return null
            pendingSave = false
            return PersistedWin32Settings(
                pendingLanguage,
                pendingAppTheme,
                pendingCurrentTask,
                pendingStrictMode,
                pendingTickEnabled,
                pendingVibeIntensity,
                pendingVolume,
                pendingFocusSound,
                pendingRelaxSound,
                pendingActivePresetId,
                pendingCustomPresets
            )
        } finally {
            ReleaseMutex(mutex)
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
private const val SETTINGS_TEMP_SUFFIX = ".tmp"
private const val SETTINGS_SAVE_DEBOUNCE_MS: UInt = 300u
private const val MAX_SETTINGS_PATH = 512
private const val MAX_SETTINGS_FILE_BYTES = 262144
private const val INVALID_FILE_SIZE: UInt = 0xFFFFFFFFu

private val PRESET_JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun win32SettingsWriterThunk(userData: COpaquePointer?): UInt {
    if (userData == null) return 0u
    try {
        val self = userData.asStableRef<Win32SettingsStore>().get()
        self.writerLoop()
    } catch (failure: Throwable) {
        println("Win32 settings writer terminated: ${failure.message}")
    }
    return 0u
}
