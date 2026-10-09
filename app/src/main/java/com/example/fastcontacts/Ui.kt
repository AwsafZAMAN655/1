package com.example.fastcontacts

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch

object Ui {

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun color(ctx: Context, res: Int): Int = ContextCompat.getColor(ctx, res)

    fun tint(ctx: Context, res: Int): ColorStateList = ColorStateList.valueOf(color(ctx, res))

    fun systemBars(activity: Activity) {
        val night = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val c = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        c.isAppearanceLightStatusBars = !night
        c.isAppearanceLightNavigationBars = !night
    }

    fun pop(v: View) {
        v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(90).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(130).start()
        }.start()
    }

    fun tap(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun selectable(ctx: Context): Drawable? {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return ContextCompat.getDrawable(ctx, tv.resourceId)
    }

    private fun borderless(ctx: Context): Drawable? {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        return ContextCompat.getDrawable(ctx, tv.resourceId)
    }

    class SheetAction(val label: String, val icon: Int, val danger: Boolean = false, val run: () -> Unit)

    fun showSheet(ctx: Context, title: String, actions: List<SheetAction>) {
        val dialog = BottomSheetDialog(ctx)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(0, dp(ctx, 8), 0, dp(ctx, 16))
        val t = TextView(ctx)
        t.text = title
        t.textSize = 18f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(color(ctx, R.color.app_text))
        t.setPadding(dp(ctx, 24), dp(ctx, 12), dp(ctx, 24), dp(ctx, 8))
        root.addView(t)
        for (a in actions) {
            val v = LayoutInflater.from(ctx).inflate(R.layout.row_sheet_action, root, false)
            val icon = v.findViewById<ImageView>(R.id.sheetIcon)
            val label = v.findViewById<TextView>(R.id.sheetLabel)
            icon.setImageResource(a.icon)
            label.text = a.label
            if (a.danger) {
                icon.imageTintList = tint(ctx, R.color.app_danger)
                label.setTextColor(color(ctx, R.color.app_danger))
            }
            v.setOnClickListener {
                dialog.dismiss()
                a.run()
            }
            root.addView(v)
        }
        dialog.setContentView(root)
        dialog.show()
    }

    // Rounded card section used on the details, organize and settings screens.
    fun card(ctx: Context, parent: LinearLayout, title: String?): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = ContextCompat.getDrawable(ctx, R.drawable.bg_card)
        c.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(ctx, 16), dp(ctx, 6), dp(ctx, 16), dp(ctx, 6))
        parent.addView(c, lp)
        if (title != null) {
            val t = TextView(ctx)
            t.text = title
            t.textSize = 14f
            t.setTypeface(null, Typeface.BOLD)
            t.setTextColor(color(ctx, R.color.app_accent))
            t.setPadding(0, 0, 0, dp(ctx, 4))
            c.addView(t)
        }
        return c
    }

    class Trail(val icon: Int, val desc: String, val tint: Boolean = true, val run: () -> Unit)

    fun row(
        ctx: Context, parent: ViewGroup, icon: Int?, primary: String, secondary: String?,
        onClick: (() -> Unit)? = null, onLong: (() -> Unit)? = null,
        trailing: List<Trail> = emptyList()
    ): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.minimumHeight = dp(ctx, 56)
        r.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6))

        val iv = ImageView(ctx)
        val ilp = LinearLayout.LayoutParams(dp(ctx, 24), dp(ctx, 24))
        ilp.marginEnd = dp(ctx, 20)
        iv.layoutParams = ilp
        iv.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        if (icon != null) iv.setImageResource(icon) else iv.visibility = View.INVISIBLE
        r.addView(iv)

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        r.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val p = TextView(ctx)
        p.text = primary
        p.textSize = 16f
        p.setTextColor(color(ctx, R.color.app_text))
        col.addView(p)
        if (secondary != null) {
            val s = TextView(ctx)
            s.text = secondary
            s.textSize = 13f
            s.setTextColor(color(ctx, R.color.app_text_secondary))
            col.addView(s)
        }

        for (t in trailing) {
            val b = ImageButton(ctx)
            b.setImageResource(t.icon)
            b.contentDescription = t.desc
            b.background = borderless(ctx)
            if (!t.tint) b.imageTintList = null
            b.layoutParams = LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 48))
            b.setOnClickListener { t.run() }
            r.addView(b)
        }

        if (onClick != null) {
            r.background = selectable(ctx)
            r.setOnClickListener { onClick() }
        }
        if (onLong != null) {
            r.setOnLongClickListener {
                onLong()
                true
            }
        }
        parent.addView(r)
        return r
    }

    fun switchRow(
        ctx: Context, parent: ViewGroup, title: String, subtitle: String?, checked: Boolean,
        onChange: (Boolean) -> Unit
    ): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.minimumHeight = dp(ctx, 56)
        r.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        r.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val p = TextView(ctx)
        p.text = title
        p.textSize = 16f
        p.setTextColor(color(ctx, R.color.app_text))
        col.addView(p)
        if (subtitle != null) {
            val s = TextView(ctx)
            s.text = subtitle
            s.textSize = 13f
            s.setTextColor(color(ctx, R.color.app_text_secondary))
            col.addView(s)
        }
        val sw = MaterialSwitch(ctx)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        r.addView(sw)
        r.setOnClickListener { sw.toggle() }
        parent.addView(r)
        return r
    }
}
