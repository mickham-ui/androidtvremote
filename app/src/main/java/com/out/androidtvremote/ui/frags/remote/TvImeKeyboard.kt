package com.out.androidtvremote.ui.frags.remote

import com.out.androidtvremote.androidLib.AndroidRemoteTv
import com.out.androidtvremote.androidLib.remote.Remotemessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import android.util.Log

class TvImeKeyboard(
    private val tv: AndroidRemoteTv,
    private val scope: CoroutineScope
) {
    private var imeCounter = 0
    private var fieldCounter = -1  // -1 = not synced yet

    fun onImeShow(text: String, tvFieldCounter: Int) {
        fieldCounter = tvFieldCounter
        imeCounter = 0
        Log.d("TvKeyboard", "✅ Synced fieldCounter=$fieldCounter text='$text'")
    }

    fun sendText(text: String) {
        scope.launch(Dispatchers.IO) {
            Log.d("TvKeyboard", "sendText: '$text' imeCounter=$imeCounter fieldCounter=$fieldCounter")
            tv.sendText(text, imeCounter++, fieldCounter)
        }
    }

    fun sendBackspace() {
        scope.launch(Dispatchers.IO) {
            tv.sendCommand(
                Remotemessage.RemoteKeyCode.KEYCODE_DEL,
                Remotemessage.RemoteDirection.SHORT
            )
        }
    }

    fun sendEnter() {
        scope.launch(Dispatchers.IO) {
            tv.sendImeEnter()
        }
    }

    fun isReady(): Boolean = fieldCounter >= 0
}