package com.out.androidtvremote.ui.frags.remote

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.out.androidtvremote.R

/**
 * Manages all visual state for the voice button:
 *   - Red tint + mic icon colour when active
 *   - Pulsing ripple rings ImageView behind the button
 *   - Bounce-in / bounce-out scale animation on state change
 *
 * Layout requirement: place an ImageView (id = iv_voice_ripple) behind
 * btn_voice in your XML, same centre position, ~120dp x 120dp, invisible.
 */
object VoiceButtonAnimator {

    fun setActive(button: MaterialButton, rippleView: ImageView, active: Boolean) {
        if (active) activateButton(button, rippleView)
        else deactivateButton(button, rippleView)
    }

    // ── Active state ─────────────────────────────────────────────────────────

    private fun activateButton(button: MaterialButton, rippleView: ImageView) {
        val ctx = button.context

        // 1. Red background tint
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(ctx, R.color.voice_active_bg)   // #33F44336
        )
        // 2. Red icon tint
        button.iconTint = ColorStateList.valueOf(
            ContextCompat.getColor(ctx, R.color.voice_active_icon) // #F44336
        )

        // 3. Bounce-scale the button to give satisfying "press" feedback
        bounceScale(button, fromScale = 0.85f, toScale = 1.0f)

        // 4. Show + start ripple rings
        rippleView.visibility = View.VISIBLE
        rippleView.alpha = 1f
        val avd = ContextCompat.getDrawable(ctx, R.drawable.voice_ripple_animation)
                as? AnimatedVectorDrawable
        rippleView.setImageDrawable(avd)
        avd?.start()
    }



    // ── Idle state ───────────────────────────────────────────────────────────

    private fun deactivateButton(button: MaterialButton, rippleView: ImageView) {
        val ctx = button.context

        // 1. Restore surface tint
        button.backgroundTintList = ContextCompat.getColorStateList(
            ctx, com.google.android.material.R.attr.colorSurfaceContainerHigh
                .resolveColorAttr(ctx)
        ) ?: ColorStateList.valueOf(
            ContextCompat.getColor(ctx, android.R.color.transparent)
        )

        // Fallback: just clear to theme default via null (Material handles it)
        button.backgroundTintList =
            ColorStateList.valueOf(resolveAttrColor(button, com.google.android.material.R.attr.colorSurfaceContainerHigh))

        // 2. Restore default icon tint (colorOnSurface)
        button.iconTint =
            ColorStateList.valueOf(resolveAttrColor(button, com.google.android.material.R.attr.colorOnSurface))

        // 3. Bounce scale out
        bounceScale(button, fromScale = 1.1f, toScale = 1.0f)

        // 4. Fade out + stop ripple
        rippleView.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction {
                rippleView.visibility = View.INVISIBLE
                (rippleView.drawable as? AnimatedVectorDrawable)?.stop()
                rippleView.setImageDrawable(null)
            }
            .start()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun bounceScale(view: View, fromScale: Float, toScale: Float) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, fromScale, toScale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, fromScale, toScale)
            )
            duration = 300
            interpolator = OvershootInterpolator(2.5f)
            start()
        }
    }

    private fun resolveAttrColor(view: View, attr: Int): Int {
        val ta = view.context.obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0xFF888888.toInt())
        ta.recycle()
        return color
    }

    private fun Int.resolveColorAttr(ctx: android.content.Context): Int {
        val ta = ctx.obtainStyledAttributes(intArrayOf(this))
        val res = ta.getResourceId(0, 0)
        ta.recycle()
        return res
    }
}