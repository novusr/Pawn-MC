package com.rvdjv.pawnmc

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * A custom dialog builder.
 *
 * Usage:
 * ```
 * PawnDialog(context)
 *     .setIcon(R.drawable.ic_info, R.color.vue_green_primary)
 *     .setTitle("Title")
 *     .setMessage("Message body")
 *     .setPositiveButton("OK") { dialog -> dialog.dismiss() }
 *     .setNegativeButton("Cancel", null)
 *     .show()
 * ```
 */
class PawnDialog(private val context: Context) {

    private var iconRes: Int = 0
    private var iconTintRes: Int = R.color.vue_green_primary
    private var iconBgRes: Int = 0
    private var title: CharSequence? = null
    private var message: CharSequence? = null
    private var positiveText: CharSequence? = null
    private var negativeText: CharSequence? = null
    private var neutralText: CharSequence? = null
    private var positiveAction: ((Dialog) -> Unit)? = null
    private var negativeAction: ((Dialog) -> Unit)? = null
    private var neutralAction: ((Dialog) -> Unit)? = null
    private var cancelable: Boolean = false

    fun setIcon(drawableRes: Int, tintColorRes: Int = R.color.vue_green_primary): PawnDialog {
        this.iconRes = drawableRes
        this.iconTintRes = tintColorRes
        return this
    }

    fun setIconBackground(drawableRes: Int): PawnDialog {
        this.iconBgRes = drawableRes
        return this
    }

    fun setTitle(text: CharSequence): PawnDialog {
        this.title = text
        return this
    }

    fun setMessage(text: CharSequence): PawnDialog {
        this.message = text
        return this
    }

    fun setPositiveButton(text: CharSequence, action: ((Dialog) -> Unit)?): PawnDialog {
        this.positiveText = text
        this.positiveAction = action
        return this
    }

    fun setNegativeButton(text: CharSequence, action: ((Dialog) -> Unit)?): PawnDialog {
        this.negativeText = text
        this.negativeAction = action
        return this
    }

    fun setNeutralButton(text: CharSequence, action: ((Dialog) -> Unit)?): PawnDialog {
        this.neutralText = text
        this.neutralAction = action
        return this
    }

    fun setCancelable(cancelable: Boolean): PawnDialog {
        this.cancelable = cancelable
        return this
    }

    fun show(): Dialog {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_pawn)
        dialog.setCancelable(cancelable)
        dialog.setCanceledOnTouchOutside(cancelable)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.CENTER)
            setDimAmount(0.6f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes?.windowAnimations = R.style.PawnMC_DialogAnimation
        }

        // Icon
        val layoutIcon = dialog.findViewById<FrameLayout>(R.id.layoutDialogIcon)
        val ivIcon = dialog.findViewById<ImageView>(R.id.ivDialogIcon)
        if (iconRes != 0) {
            ivIcon.setImageResource(iconRes)
            ivIcon.setColorFilter(context.getColor(iconTintRes))
            if (iconBgRes != 0) {
                layoutIcon.setBackgroundResource(iconBgRes)
            }
            layoutIcon.visibility = View.VISIBLE
        } else {
            layoutIcon.visibility = View.GONE
        }

        // Title
        val tvTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)
        if (title != null) {
            tvTitle.text = title
            tvTitle.visibility = View.VISIBLE
        } else {
            tvTitle.visibility = View.GONE
        }

        // Message
        val tvMessage = dialog.findViewById<TextView>(R.id.tvDialogMessage)
        if (message != null) {
            tvMessage.text = message
            tvMessage.visibility = View.VISIBLE
        } else {
            tvMessage.visibility = View.GONE
        }

        // Buttons
        val layoutButtons = dialog.findViewById<LinearLayout>(R.id.layoutDialogButtons)
        val btnPositive = dialog.findViewById<MaterialButton>(R.id.btnDialogPositive)
        val btnNegative = dialog.findViewById<MaterialButton>(R.id.btnDialogNegative)
        val btnNeutral = dialog.findViewById<MaterialButton>(R.id.btnDialogNeutral)

        var hasMainButtons = false

        if (positiveText != null) {
            btnPositive.text = positiveText
            btnPositive.visibility = View.VISIBLE
            btnPositive.setOnClickListener {
                positiveAction?.invoke(dialog) ?: dialog.dismiss()
            }
            hasMainButtons = true
        }

        if (negativeText != null) {
            btnNegative.text = negativeText
            btnNegative.visibility = View.VISIBLE
            btnNegative.setOnClickListener {
                negativeAction?.invoke(dialog) ?: dialog.dismiss()
            }
            hasMainButtons = true
        }

        if (positiveText != null && negativeText == null) {
            val lp = btnPositive.layoutParams as LinearLayout.LayoutParams
            lp.weight = 0f
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            btnPositive.layoutParams = lp
        } else if (negativeText != null && positiveText == null) {
            val lp = btnNegative.layoutParams as LinearLayout.LayoutParams
            lp.weight = 0f
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            btnNegative.layoutParams = lp
        }

        if (!hasMainButtons) {
            layoutButtons.visibility = View.GONE
        }

        if (neutralText != null) {
            btnNeutral.text = neutralText
            btnNeutral.visibility = View.VISIBLE
            btnNeutral.setOnClickListener {
                neutralAction?.invoke(dialog) ?: dialog.dismiss()
            }
        }

        dialog.show()
        return dialog
    }
}
