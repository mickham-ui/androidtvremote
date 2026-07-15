package com.out.androidtvremote.ui.frags.remote

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.out.androidtvremote.App
import com.out.androidtvremote.androidLib.AndroidRemoteTv
import com.out.androidtvremote.androidLib.remote.Remotemessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.out.androidtvremote.androidLib.AndroidTvListener

class RemoteViewModel(application: Application) : AndroidViewModel(application), AndroidTvListener {

    private val app = getApplication<App>()

    private val _isConnected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> get() = _isConnected

    private val _volumeLevel = MutableLiveData<Int>(7)  // 0–15, start mid
    val volumeLevel: LiveData<Int> get() = _volumeLevel

    // ── Sequential Key Queue ────────────────────────────────────────────────────
    //
    // All keyboard events are funnelled through a single unbuffered Channel that is
    // consumed by one coroutine worker.  This guarantees:
    //   • Key events always arrive in the order they were typed.
    //   • A mandatory inter-key delay (KEY_DELAY_MS) gives the TV time to process
    //     each event before the next one arrives — preventing dropped characters.
    //
    private val keyChannel = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val KEY_DELAY_MS = 20L   // minimum ms between consecutive key events
    private val SHIFT_HOLD_MS = 20L  // ms to hold SHIFT before/after the letter key

    init {
        // Register this ViewModel as a listener for protocol events (like IME sync)
        app.androidRemoteTv?.addListener(this)

        // Single background worker that drains the channel in order
        viewModelScope.launch(Dispatchers.IO) {
            for (action in keyChannel) {
                try {
                    action()
                } catch (e: Exception) {
                    android.util.Log.e("RemoteViewModel", "Key queue error: ${e.message}")
                }
                delay(KEY_DELAY_MS) // mandatory spacing between events
            }
        }
    }

    // ── AndroidTvListener Implementation ────────────────────────────────────────

    override fun onSessionCreated() {}
    override fun onSecretRequested() {}
    override fun onPaired() {}
    override fun onConnectingToRemote() {}
    override fun onConnected() {
        _isConnected.postValue(true)
        observeRealVolumeFromTv()
    }
    override fun onDisconnect() {
        _isConnected.postValue(false)
        imeFieldCounter = 0
        imeCounter = 0
    }
    override fun onError(error: String) {}

    override fun onImeShow(text: String, fieldCounter: Int) {
        if (fieldCounter > 0) {
            imeFieldCounter = fieldCounter
            Log.d("RemoteViewModel", "IME sync updated: fieldCounter=$imeFieldCounter textLen=${text.length}")
        }
    }

    // ── Connection ──────────────────────────────────────────────────────────────

    fun refreshConnection() {
        val remote = app.androidRemoteTv
        _isConnected.value = remote != null
        remote?.addListener(this)
    }

    fun hasRemote(): Boolean = app.androidRemoteTv != null

    // ── Basic key sending (used by the D-pad / media buttons) ──────────────────

    fun sendKey(key: Remotemessage.RemoteKeyCode) {
        viewModelScope.launch(Dispatchers.IO) {
            app.androidRemoteTv?.sendCommand(key, Remotemessage.RemoteDirection.SHORT)
        }
    }

    fun sendKeyDown(key: Remotemessage.RemoteKeyCode) {
        viewModelScope.launch(Dispatchers.IO) {
            app.androidRemoteTv?.sendCommand(key, Remotemessage.RemoteDirection.START_LONG)
        }
    }

    fun sendKeyUp(key: Remotemessage.RemoteKeyCode) {
        viewModelScope.launch(Dispatchers.IO) {
            app.androidRemoteTv?.sendCommand(key, Remotemessage.RemoteDirection.END_LONG)
        }
    }

    // ── Volume ─────────────────────────────────────────────────────────────────

    fun volumeUp() {
        val next = ((_volumeLevel.value ?: 7) + 1).coerceAtMost(15)
        _volumeLevel.postValue(next)
        viewModelScope.launch(Dispatchers.IO) {
            app.androidRemoteTv?.sendCommand(
                Remotemessage.RemoteKeyCode.KEYCODE_VOLUME_UP,
                Remotemessage.RemoteDirection.SHORT
            )
        }
    }

    fun volumeDown() {
        val next = ((_volumeLevel.value ?: 7) - 1).coerceAtLeast(0)
        _volumeLevel.postValue(next)
        viewModelScope.launch(Dispatchers.IO) {
            app.androidRemoteTv?.sendCommand(
                Remotemessage.RemoteKeyCode.KEYCODE_VOLUME_DOWN,
                Remotemessage.RemoteDirection.SHORT
            )
        }
    }


    // ── Keyboard Input (IME-first + Key fallback) ──────────────────────────────

    private var imeFieldCounter: Int = 0
    private var imeCounter: Int = 0

    private data class KeyMapping(
        val keyCode: Remotemessage.RemoteKeyCode,
        val needsShift: Boolean = false
    )

    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    private fun mapCharToKey(ch: Char): KeyMapping? = when (ch) {
        // Lowercase letters
        'a' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_A)
        'b' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_B)
        'c' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_C)
        'd' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_D)
        'e' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_E)
        'f' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_F)
        'g' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_G)
        'h' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_H)
        'i' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_I)
        'j' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_J)
        'k' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_K)
        'l' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_L)
        'm' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_M)
        'n' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_N)
        'o' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_O)
        'p' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_P)
        'q' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_Q)
        'r' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_R)
        's' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_S)
        't' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_T)
        'u' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_U)
        'v' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_V)
        'w' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_W)
        'x' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_X)
        'y' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_Y)
        'z' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_Z)
        // Uppercase — same keycode, SHIFT held
        'A' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_A, true)
        'B' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_B, true)
        'C' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_C, true)
        'D' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_D, true)
        'E' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_E, true)
        'F' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_F, true)
        'G' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_G, true)
        'H' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_H, true)
        'I' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_I, true)
        'J' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_J, true)
        'K' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_K, true)
        'L' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_L, true)
        'M' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_M, true)
        'N' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_N, true)
        'O' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_O, true)
        'P' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_P, true)
        'Q' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_Q, true)
        'R' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_R, true)
        'S' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_S, true)
        'T' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_T, true)
        'U' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_U, true)
        'V' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_V, true)
        'W' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_W, true)
        'X' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_X, true)
        'Y' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_Y, true)
        'Z' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_Z, true)
        // Digits
        '0' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_0)
        '1' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_1)
        '2' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_2)
        '3' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_3)
        '4' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_4)
        '5' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_5)
        '6' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_6)
        '7' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_7)
        '8' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_8)
        '9' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_9)
        // Whitespace
        ' '        -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_SPACE)
        '\n', '\r' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_ENTER)
        '\t'       -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_TAB)
        // Punctuation — no shift
        '.'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_PERIOD)
        ','  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_COMMA)
        '-'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_MINUS)
        '='  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_EQUALS)
        '/'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_SLASH)
        '\\' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_BACKSLASH)
        '\'' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_APOSTROPHE)
        ';'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_SEMICOLON)
        '@'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_AT)
        '#'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_POUND)
        '*'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_STAR)
        '['  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_LEFT_BRACKET)
        ']'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_RIGHT_BRACKET)
        '`'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_GRAVE)
        '+'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_PLUS)
        // Punctuation — requires SHIFT (US keyboard)
        '!'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_1,             true)
        '"'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_APOSTROPHE,    true)
        ':'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_SEMICOLON,     true)
        '_'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_MINUS,         true)
        '?'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_SLASH,         true)
        '%'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_5,             true)
        '^'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_6,             true)
        '&'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_7,             true)
        '$'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_4,             true)
        '<'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_COMMA,         true)
        '>'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_PERIOD,        true)
        '{'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_LEFT_BRACKET,  true)
        '}'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_RIGHT_BRACKET, true)
        '|'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_BACKSLASH,     true)
        '~'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_GRAVE,         true)
        '('  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_9,             true)
        ')'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_0,             true)
        else -> null
    }

    /**
     * Enqueue a single character as key event(s) through the ordered keyChannel.
     * Uppercase / shifted chars → SHIFT_DOWN, letter, SHIFT_UP are separate events.
     */
    private fun enqueueChar(ch: Char) {
        val mapping = mapCharToKey(ch) ?: return
        if (mapping.needsShift) {
            keyChannel.trySend {
                app.androidRemoteTv?.sendCommand(
                    Remotemessage.RemoteKeyCode.KEYCODE_SHIFT_LEFT,
                    Remotemessage.RemoteDirection.START_LONG
                )
                delay(SHIFT_HOLD_MS)
            }
        }
        keyChannel.trySend {
            app.androidRemoteTv?.sendCommand(mapping.keyCode, Remotemessage.RemoteDirection.SHORT)
        }
        if (mapping.needsShift) {
            keyChannel.trySend {
                delay(SHIFT_HOLD_MS)
                app.androidRemoteTv?.sendCommand(
                    Remotemessage.RemoteKeyCode.KEYCODE_SHIFT_LEFT,
                    Remotemessage.RemoteDirection.END_LONG
                )
            }
        }
    }

    private fun enqueueBackspace() {
        keyChannel.trySend {
            app.androidRemoteTv?.sendCommand(
                Remotemessage.RemoteKeyCode.KEYCODE_DEL,
                Remotemessage.RemoteDirection.SHORT
            )
        }
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val minLen = minOf(a.length, b.length)
        var i = 0
        while (i < minLen && a[i] == b[i]) i++
        return i
    }

    /**
     * Sends full text through IME on every local text change.
     * Field counter is taken from sync when available, otherwise defaults to 1.
     */
    private fun trySendImeFullText(text: String): Boolean {
        if (!hasRemote()) return false
        val currentCounter = ++imeCounter
        val currentFieldCounter = if (imeFieldCounter > 0) imeFieldCounter else 1
        Log.d(
            "RemoteViewModel",
            "IME send: imeCounter=$currentCounter fieldCounter=$currentFieldCounter text=\"$text\""
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.androidRemoteTv?.sendText(text, currentCounter, currentFieldCounter)
            } catch (e: Exception) {
                Log.e("RemoteViewModel", "trySendImeFullText failed: ${e.message}")
            }
        }
        return true
    }

    /**
     * Sends live text changes immediately via IME.
     * Call this from TextWatcher using previous and current text.
     */
    fun sendLiveTextDelta(previousText: String, newText: String) {
        if (!hasRemote() || previousText == newText) return

        trySendImeFullText(newText)
    }

    /**
     * MODE 2 — Type text on the TV key-by-key.
     * Works regardless of what is currently focused on the TV screen.
     *
     * @param text      Text to send.
     * @param withEnter If true, appends an Enter after the text.
     */
    fun sendKeyByKeyText(text: String, withEnter: Boolean = false) {
        if (!hasRemote() || text.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            text.forEach { ch -> enqueueChar(ch) }
            if (withEnter) {
                keyChannel.trySend {
                    app.androidRemoteTv?.sendCommand(
                        Remotemessage.RemoteKeyCode.KEYCODE_ENTER,
                        Remotemessage.RemoteDirection.SHORT
                    )
                }
            }
        }
    }

    /** Send a single Backspace/Delete to the TV (key-by-key mode). */
    fun sendBackspace() {
        if (!hasRemote()) return
        enqueueBackspace()
    }

    // ── App launching ──────────────────────────────────────────────────────────

    /**
     * Legacy bulk-text sender (opens search, then types every char, then enters).
     * Kept for any callers outside the keyboard dialog.
     */
//    fun sendText(text: String, withSearch: Boolean = true) {
//        val sanitized = text.trim()
//        if (sanitized.isEmpty()) return
//
//        CoroutineScope(Dispatchers.IO).launch {
//            if (withSearch) {
//                app.androidRemoteTv?.sendCommand(
//                    Remotemessage.RemoteKeyCode.KEYCODE_SEARCH,
//                    Remotemessage.RemoteDirection.SHORT
//                )
//                delay(120)
//            }
//            sanitized.forEach { ch -> enqueueChar(ch) }
//            enqueueEnter()
//        }
//    }

    // ── Character → KeyCode + SHIFT mapping ─────────────────────────────────────
    //
    // Returns the raw key code AND whether SHIFT must be held when pressing it.
    // Uppercase letters → same base keycode as lowercase, needsShift = true.
    // Special chars that require SHIFT on a standard US keyboard → needsShift = true.
    //
//    private fun mapCharToKeyAndShift(ch: Char): KeyMapping? {
//        return when (ch) {
//            // ── Lowercase letters (no SHIFT) ─────────────────────────────────
//            'a' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_A)
//            'b' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_B)
//            'c' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_C)
//            'd' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_D)
//            'e' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_E)
//            'f' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_F)
//            'g' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_G)
//            'h' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_H)
//            'i' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_I)
//            'j' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_J)
//            'k' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_K)
//            'l' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_L)
//            'm' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_M)
//            'n' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_N)
//            'o' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_O)
//            'p' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_P)
//            'q' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_Q)
//            'r' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_R)
//            's' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_S)
//            't' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_T)
//            'u' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_U)
//            'v' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_V)
//            'w' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_W)
//            'x' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_X)
//            'y' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_Y)
//            'z' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_Z)
//            // ── Uppercase letters (SHIFT required) ───────────────────────────
//            'A' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_A, needsShift = true)
//            'B' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_B, needsShift = true)
//            'C' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_C, needsShift = true)
//            'D' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_D, needsShift = true)
//            'E' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_E, needsShift = true)
//            'F' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_F, needsShift = true)
//            'G' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_G, needsShift = true)
//            'H' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_H, needsShift = true)
//            'I' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_I, needsShift = true)
//            'J' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_J, needsShift = true)
//            'K' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_K, needsShift = true)
//            'L' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_L, needsShift = true)
//            'M' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_M, needsShift = true)
//            'N' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_N, needsShift = true)
//            'O' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_O, needsShift = true)
//            'P' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_P, needsShift = true)
//            'Q' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_Q, needsShift = true)
//            'R' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_R, needsShift = true)
//            'S' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_S, needsShift = true)
//            'T' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_T, needsShift = true)
//            'U' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_U, needsShift = true)
//            'V' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_V, needsShift = true)
//            'W' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_W, needsShift = true)
//            'X' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_X, needsShift = true)
//            'Y' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_Y, needsShift = true)
//            'Z' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_Z, needsShift = true)
//            // ── Digits (no SHIFT) ────────────────────────────────────────────
//            '0' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_0)
//            '1' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_1)
//            '2' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_2)
//            '3' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_3)
//            '4' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_4)
//            '5' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_5)
//            '6' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_6)
//            '7' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_7)
//            '8' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_8)
//            '9' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_9)
//            // ── Whitespace ───────────────────────────────────────────────────
//            ' '  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_SPACE)
//            '\n', '\r' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_ENTER)
//            '\t' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_TAB)
//            // ── Direct punctuation (no SHIFT needed) ─────────────────────────
//            '.'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_PERIOD)
//            ','  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_COMMA)
//            '-'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_MINUS)
//            '='  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_EQUALS)
//            '/'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_SLASH)
//            '\\' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_BACKSLASH)
//            '\'' -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_APOSTROPHE)
//            ';'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_SEMICOLON)
//            '@'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_AT)
//            '#'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_POUND)
//            '*'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_STAR)
//            '['  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_LEFT_BRACKET)
//            ']'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_RIGHT_BRACKET)
//            '`'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_GRAVE)
//            '+'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_PLUS)
//            // ── SHIFT-required special characters ────────────────────────────
//            // These are the unshifted base key; SHIFT is injected by enqueueChar.
//            '!'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_1,              needsShift = true)  // SHIFT+1
//            '"'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_APOSTROPHE,     needsShift = true)  // SHIFT+'
//            ':'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_SEMICOLON,      needsShift = true)  // SHIFT+;
//            '_'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_MINUS,          needsShift = true)  // SHIFT+-
//            '?'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_SLASH,          needsShift = true)  // SHIFT+/
//            '%'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_5,              needsShift = true)  // SHIFT+5
//            '^'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_6,              needsShift = true)  // SHIFT+6
//            '&'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_7,              needsShift = true)  // SHIFT+7
//            '$'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_4,              needsShift = true)  // SHIFT+4
//            '<'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_COMMA,          needsShift = true)  // SHIFT+,
//            '>'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_PERIOD,         needsShift = true)  // SHIFT+.
//            '{'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_LEFT_BRACKET,   needsShift = true)  // SHIFT+[
//            '}'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_RIGHT_BRACKET,  needsShift = true)  // SHIFT+]
//            '|'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_BACKSLASH,      needsShift = true)  // SHIFT+\
//            '~'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_GRAVE,          needsShift = true)  // SHIFT+`
//            '('  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_NUMPAD_LEFT_PAREN)
//            ')'  -> KeyMapping(Remotemessage.RemoteKeyCode.KEYCODE_NUMPAD_RIGHT_PAREN)
//            else -> null
//        }
//    }


    // ── App launching ──────────────────────────────────────────────────────────

    fun sendAppLink(appLink: String) {
        app.androidRemoteTv?.sendAppLink(appLink)
    }

    fun getAndroidVTV(): AndroidRemoteTv? = app.androidRemoteTv

    fun mapAppToUrl(appName: String): String? {
        return when (appName) {
            "Netflix" -> "https://www.netflix.com/title.*"
            "YouTube" -> "https://www.youtube.com"
            "Prime Video" -> "https://www.primevideo.com/"
            "Hotstar" -> "https://www.hotstar.com/"
            "Spotify" -> "https://open.spotify.com/"
            "YouTube Music" -> "https://music.youtube.com/"
            "Apple TV" -> "https://tv.apple.com/"
            "HBO Max" -> "https://www.hbomax.com/"
            "Hulu" -> "https://hulu.com"
            else -> null
        }
    }

    fun observeRealVolumeFromTv() {
        // Wire the normal ongoing listener 
        getAndroidVTV()?.setVolumeChangedCallback { level, _, _ ->
            _volumeLevel.postValue(level)
        }

        // Fetch instantly via ConnectSDK to fix initial blank/wrong sync
        com.out.androidtvremote.utils.Constant.connectableDevice?.volumeControl?.getVolume(
            object : com.connectsdk.service.capability.VolumeControl.VolumeListener {
                override fun onSuccess(volume: Float?) {
                    volume?.let {
                        val intVol = (it * 15).toInt().coerceIn(0, 15)
                        _volumeLevel.postValue(intVol)
                    }
                }
                override fun onError(error: com.connectsdk.service.command.ServiceCommandError?) {
                    android.util.Log.e("RemoteViewModel", "Could not fetch initial volume")
                }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        keyChannel.close()
    }
}
