package com.oncet.smsquickforwarder.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.oncet.smsquickforwarder.R

object UiKit {
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun rounded(color: Int, radiusDp: Int = 14, strokeColor: Int? = null, strokeDp: Int = 1, context: Context): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(context, strokeDp), strokeColor)
        }

    fun page(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 20), dp(context, 22), dp(context, 20), dp(context, 18))
    }

    fun title(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(context.getColor(R.color.text_primary))
    }

    fun subtitle(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 13f
        setTextColor(context.getColor(R.color.text_secondary))
        setPadding(0, dp(context, 4), 0, dp(context, 10))
    }

    fun section(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(context.getColor(R.color.text_primary))
        setPadding(0, dp(context, 18), 0, dp(context, 8))
    }

    fun card(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
        background = rounded(context.getColor(R.color.surface_card), 14, context.getColor(R.color.surface_stroke), context = context)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(context, 12)
        }
    }

    fun tag(context: Context, text: String, color: Int): TextView = TextView(context).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4))
        background = rounded(color, 999, context = context)
    }

    fun primaryButton(context: Context, text: String, onClick: () -> Unit): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(context.getColor(R.color.primary), 12, context = context)
        setOnClickListener { onClick() }
    }

    fun secondaryButton(context: Context, text: String, onClick: () -> Unit): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        setTextColor(context.getColor(R.color.primary))
        background = rounded(context.getColor(R.color.primary_soft), 12, context = context)
        setOnClickListener { onClick() }
    }

    fun navButton(context: Context, text: String, selected: Boolean, onClick: () -> Unit): TextView = TextView(context).apply {
        this.text = text
        textSize = 13f
        gravity = Gravity.CENTER
        typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(if (selected) context.getColor(R.color.primary) else context.getColor(R.color.text_secondary))
        background = rounded(if (selected) context.getColor(R.color.primary_soft) else Color.TRANSPARENT, 12, context = context)
        setPadding(0, dp(context, 10), 0, dp(context, 10))
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { onClick() }
    }

    fun row(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun body(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(context.getColor(R.color.text_primary))
        setLineSpacing(0f, 1.0f)
    }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(context.getColor(R.color.surface_stroke))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1)).apply {
            topMargin = dp(context, 10)
            bottomMargin = dp(context, 10)
        }
    }
}
