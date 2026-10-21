package com.moguru.game.android

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard

/**
 * Takes the place of `LocalClipboard` so Compose tests never touch the device clipboard. It keeps
 * the plain text of each write, or refuses every write as a platform could when [rejectWrites].
 */
internal class RecordingClipboard(private val rejectWrites: Boolean = false) : Clipboard {
    val writtenTexts = mutableListOf<String>()
    var reads = 0
        private set

    override suspend fun getClipEntry(): ClipEntry? {
        reads++
        return null
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        if (rejectWrites) throw SecurityException("the platform refused the clip")
        val clip = checkNotNull(clipEntry) { "the history never clears the clipboard" }.clipData
        check(clip.itemCount == 1) { "the history writes one plain text item" }
        writtenTexts += clip.getItemAt(0).text.toString()
    }

    override val nativeClipboard: NativeClipboard
        get() = error("tests must not reach the device clipboard")
}
