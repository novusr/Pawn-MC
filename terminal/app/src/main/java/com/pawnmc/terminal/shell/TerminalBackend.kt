package com.pawnmc.terminal.shell

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.pawnmc.terminal.ui.TerminalSurface
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient

/**
 * Glue between a [TerminalSession] and the widget that renders it.
 *
 * A single instance is shared by the view and every session: the Termux interfaces have
 * no per-session state here, and swapping the client on every session switch is what the
 * upstream editor does too.
 */
class TerminalBackend : TerminalViewClient, TerminalSessionClient {

    override fun onTextChanged(changedSession: TerminalSession) {
        TerminalSurface.view?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) = Unit

    override fun onSessionFinished(finishedSession: TerminalSession) {
        // The session prints "[Process completed - press Enter]" itself; nothing to add.
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        TerminalSurface.clipboard?.setText(text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val text = TerminalSurface.clipboard?.text?.toString().orEmpty()
        if (text.isNotEmpty()) TerminalSurface.view?.mEmulator?.paste(text)
    }

    override fun onBell(session: TerminalSession) = Unit

    override fun onColorsChanged(session: TerminalSession) = Unit

    override fun onTerminalCursorStateChange(state: Boolean) = Unit

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit

    override fun getTerminalCursorStyle(): Int = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK

    override fun logError(tag: String?, message: String?) {
        Log.e(tag.orEmpty(), message.orEmpty())
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag.orEmpty(), message.orEmpty())
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag.orEmpty(), message.orEmpty())
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag.orEmpty(), message.orEmpty())
    }

    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag.orEmpty(), message.orEmpty())
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag.orEmpty(), message.orEmpty(), e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag.orEmpty(), "stack trace", e)
    }

    /**
     * Pinch zoom. The Termux view reports a raw scale factor and expects the new font
     * size back; the range matches what is comfortably readable on a phone.
     */
    override fun onScale(scale: Float): Float {
        val fontScale = scale.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        TerminalSurface.view?.setTextSize(fontScale.toInt())
        return fontScale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        TerminalSurface.showSoftKeyboard()
    }

    // Android should receive Back so its IME can dismiss before Compose navigation.
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    /** Character-based input keeps the backspace key from being swallowed by IME heuristics. */
    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true

    override fun shouldSupportClipboardKeybindings(): Boolean = true

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) = Unit

    /**
     * Enter on a finished session restarts the shell, which is what a terminal user
     * expects from "[Process completed - press Enter]".
     */
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !session.isRunning) {
            TerminalSurface.onSessionExited?.invoke(session)
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    // The virtual key row (CTRL/ALT/SHIFT/FN) is not part of this build, so all four
    // modifiers read as released.
    override fun readControlKey(): Boolean = false

    override fun readAltKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() {
        TerminalSurface.view?.let { view ->
            if (view.mEmulator != null) view.setTerminalCursorBlinkerState(true, true)
        }
    }

    private companion object {
        const val MIN_FONT_SIZE = 8f
        const val MAX_FONT_SIZE = 40f
    }
}
