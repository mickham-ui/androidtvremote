package com.out.androidtvremote.ui.frags.remote

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.out.androidtvremote.R

object KeyboardDialog {

    fun show(activity: Activity, keyboard: TvImeKeyboard): AlertDialog {
        val dialogView = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_keyboard, null, false)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.etKeyboardInput)
        val closeButton = dialogView.findViewById<MaterialButton>(R.id.btnKeyboardClose)
        val clearButton = dialogView.findViewById<MaterialButton>(R.id.btnKeyboardClear)
        val enterButton = dialogView.findViewById<MaterialButton>(R.id.btnKeyboardEnter)

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s ?: return
                when {
                    count > 0 && before == 0 -> {
                        val inserted = text.subSequence(start, start + count).toString()
                        inserted.forEach { ch ->
                            keyboard.sendText(ch.toString())
                        }
                    }
                    count == 0 && before > 0 -> {
                        repeat(before) { keyboard.sendBackspace() }
                    }
                    count > 0 && before > 0 -> {
                        repeat(before) { keyboard.sendBackspace() }
                        val replaced = text.subSequence(start, start + count).toString()
                        replaced.forEach { ch ->
                            keyboard.sendText(ch.toString())
                        }
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener { dialog.dismiss() }
        clearButton.setOnClickListener { editText.text?.clear() }
        enterButton.setOnClickListener { keyboard.sendEnter() }

        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                keyboard.sendEnter()
                true
            } else {
                false
            }
        }

        dialog.setOnDismissListener {
            val imm = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editText.windowToken, 0)
        }

        dialog.show()
        dialog.window?.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        editText.requestFocus()
        editText.postDelayed({
            val imm = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }, 200)

        return dialog
    }
}
