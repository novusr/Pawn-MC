package com.pawnmc.terminal.ui

import android.content.ClipboardManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowInsetsCompat
import com.pawnmc.terminal.shell.TerminalService
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import java.lang.ref.WeakReference

/**
 * The pieces of the live terminal the backend needs to reach.
 *
 * The Termux `TerminalViewClient` callbacks are plain methods on an object that is not
 * owned by a composable, so they cannot receive the widget as a parameter. Rather than
 * threading a `WeakReference` through every callback, the currently attached widget and
 * clipboard are published here — weakly, so a disposed screen is never kept alive.
 */
internal object TerminalSurface {

    private var viewRef = WeakReference<TerminalView?>(null)

    var view: TerminalView?
        get() = viewRef.get()
        set(value) {
            viewRef = WeakReference(value)
        }

    /** Set by the screen so [com.pawnmc.terminal.shell.TerminalBackend.onKeyDown] can restart an exited session. */
    var onSessionExited: ((TerminalSession) -> Unit)? = null

    var clipboard: ClipboardManager? = null

    /**
     * Raises the soft keyboard over the terminal.
     *
     * `requestFocus()` alone is not enough: when the view already holds focus — which it
     * does after the first tap, and immediately after creation because the factory calls
     * it — the call is a no-op and the IME is never shown. The keyboard has to be asked
     * for explicitly, and that request must run after the view is attached, otherwise the
     * window token is not ready yet and the request is silently dropped.
     */
    fun showSoftKeyboard() {
        val target = viewRef.get() ?: return
        target.requestFocus()
        target.post {
            val manager = target.context
                .getSystemService(InputMethodManager::class.java) ?: return@post
            manager.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** Opens a fresh shell after a user requests an additional terminal session. */
    fun requestNewSession(service: TerminalService): String {
        hideSoftKeyboard()
        val nextId = service.sessionIds.value.nextTerminalSessionId()
        service.activeSessionId.value = nextId
        return nextId
    }

    private fun List<String>.nextTerminalSessionId(): String {
        var index = 1
        while (true) {
            val candidate = if (index == 1) "main" else "main $index"
            if (candidate !in this) return candidate
            index++
        }
    }

    /** Returns true when the keyboard is visible and asks Android to hide it. */
    fun hideSoftKeyboard(): Boolean {
        val target = viewRef.get() ?: return false
        val manager = target.context
            .getSystemService(InputMethodManager::class.java) ?: return false
        val visible = target.rootWindowInsets?.let {
            WindowInsetsCompat.toWindowInsetsCompat(it, target)
                .isVisible(WindowInsetsCompat.Type.ime())
        } ?: false
        if (visible) manager.hideSoftInputFromWindow(target.windowToken, 0)
        return visible
    }

    /** Clears the published references when the screen leaves composition. */
    fun detach(view: TerminalView) {
        if (viewRef.get() === view) viewRef = WeakReference(null)
        onSessionExited = null
    }
}
