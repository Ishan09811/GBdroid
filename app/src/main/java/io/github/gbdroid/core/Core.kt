
package io.github.gbdroid.core

import android.net.Uri
import io.github.gbdroid.GBdroidApplication
import java.io.ByteArrayOutputStream

enum class PLATFORM(val value: Int) {
    NONE(-1), GBA(0), GB(1);

    companion object {
        fun from(value: Int): PLATFORM = when (value) {
            -1 -> NONE
            0 -> GBA
            1 -> GB
            else -> NONE
        }
    }
}

object Core {
    init {
        System.loadLibrary("gbdroid")
    }

    private var initialized = false

    var gameVersion = "v0"

    private var videoBuffer: IntArray = IntArray(0)

    var width: Int = 0
        private set
    var height: Int = 0
        private set

    private const val GBA_VERSION_OFFSET = 0xBC
    private const val GB_VERSION_OFFSET = 0x14C

    fun init(): Boolean {
        if (initialized) return true
        initialized = nativeInit()
        if (initialized) {
            width = nativeGetWidth()
            height = nativeGetHeight()
            videoBuffer = IntArray(width * height)
        }
        return initialized
    }

    fun shutdown() {
        if (!initialized) return
        nativeShutdown()
        initialized = false
    }

    fun quickLoadRom(uri: Uri): Boolean {
        check(initialized) { "Core.init() must succeed before loadRom()" }
        val romBytes = GBdroidApplication.context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            input.copyTo(output)
            output.toByteArray()
        } ?: return false
        val ok = nativeQuickLoadRom(romBytes)
        if (ok) gameVersion = "v${readRomVersion(romBytes, isGba())}"
        return ok
    }

    fun loadRom(uri: Uri): Boolean {
        check(initialized) { "Core.init() must succeed before loadRom()" }
        val romBytes = GBdroidApplication.context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            input.copyTo(output)
            output.toByteArray()
        } ?: return false
        val ok = nativeLoadRom(romBytes)
        if (ok) {
            width = nativeGetWidth()
            height = nativeGetHeight()
            if (width > 0 && height > 0) {
                videoBuffer = IntArray(width * height)
            }

            gameVersion = "v${readRomVersion(romBytes, isGba())}"
        }
        return ok
    }

    fun reset() = nativeReset()

    fun runFrame() = nativeRunFrame()

    // Returns the current frame's pixels as ARGB8888 ints and sized widthxheight
    fun getVideoBuffer(): IntArray {
        val buffer = videoBuffer
        nativeGetVideoBuffer(buffer)
        return buffer
    }

    fun setKeys(keyMask: Int) = nativeSetKeys(keyMask)

    fun gameTitle(): String = nativeGetGameTitle()
    fun gameCode(): String = nativeGetGameCode()

    fun readRomVersion(romBytes: ByteArray, isGba: Boolean): Int {
        val offset = if (isGba) GBA_VERSION_OFFSET else GB_VERSION_OFFSET
        if (romBytes.size <= offset) return 0
        return romBytes[offset].toInt() and 0xFF
    }

    fun getPlatform(): PLATFORM {
        return PLATFORM.from(nativeGetPlatform())
    }

    fun isGba(): Boolean = getPlatform() == PLATFORM.GBA

    fun loadSaveData(saveBytes: ByteArray): Boolean = nativeLoadSaveData(saveBytes)
    fun exportSaveData(): ByteArray = nativeExportSaveData()

    private external fun nativeInit(): Boolean
    private external fun nativeShutdown()
    private external fun nativeLoadRom(romData: ByteArray): Boolean
    private external fun nativeQuickLoadRom(romData: ByteArray): Boolean
    private external fun nativeReset()
    private external fun nativeRunFrame()
    private external fun nativeGetVideoBuffer(outPixels: IntArray)
    private external fun nativeGetWidth(): Int
    private external fun nativeGetHeight(): Int
    private external fun nativeSetKeys(keyMask: Int)
    private external fun nativeLoadSaveData(saveData: ByteArray): Boolean
    private external fun nativeExportSaveData(): ByteArray
    private external fun nativeGetGameTitle(): String
    private external fun nativeGetGameCode(): String
    private external fun nativeGetPlatform(): Int
}
