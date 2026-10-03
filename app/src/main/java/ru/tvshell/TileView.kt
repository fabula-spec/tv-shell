package ru.tvshell

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Плитка в стиле макета А: скруглённая карточка, при фокусе приподнимается
 * (увеличение, сдвиг вверх, тень), подпись снизу.
 *
 * @param scale коэффициент «пиксель макета → пиксель экрана».
 * @param captionOnFocusOnly подпись видна только у выбранной плитки (ряд-док).
 */
class TileView(
    context: Context,
    private val scale: Float,
    widthDesign: Int,
    heightDesign: Int,
    title: String,
    banner: Drawable? = null,
    icon: Drawable? = null,
    glyph: String? = null,
    background: Int = Color.WHITE,
    glyphColor: Int = INK,
    private val captionOnFocusOnly: Boolean = false,
) : LinearLayout(context) {

    private val card = FrameLayout(context)
    private val caption = TextView(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isFocusable = true
        isClickable = true
        clipChildren = false
        clipToPadding = false

        card.background = GradientDrawable().apply {
            cornerRadius = u(18).toFloat()
            setColor(background)
        }
        card.outlineProvider = ViewOutlineProvider.BACKGROUND
        card.clipToOutline = true
        card.elevation = u(1).toFloat()

        when {
            banner != null -> card.addView(
                ImageView(context).apply {
                    setImageDrawable(banner)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                },
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
            icon != null -> card.addView(
                ImageView(context).apply {
                    setImageDrawable(icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                FrameLayout.LayoutParams(u(56), u(56), Gravity.CENTER),
            )
            glyph != null -> card.addView(
                TextView(context).apply {
                    text = glyph
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, u(30).toFloat())
                    setTextColor(glyphColor)
                    typeface = Fonts.get(context, 600)
                    gravity = Gravity.CENTER
                },
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
        }
        addView(card, LayoutParams(u(widthDesign), u(heightDesign)))

        caption.apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u(if (captionOnFocusOnly) 16 else 14).toFloat())
            typeface = Fonts.get(context, if (captionOnFocusOnly) 600 else 500)
            setTextColor(if (captionOnFocusOnly) INK else MUTED)
            gravity = Gravity.CENTER_HORIZONTAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, u(12), 0, 0)
            visibility = if (captionOnFocusOnly) View.INVISIBLE else View.VISIBLE
        }
        addView(caption, LayoutParams(u(widthDesign + 20), LayoutParams.WRAP_CONTENT))
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        val s = if (gainFocus) 1.14f else 1f
        card.animate()
            .scaleX(s)
            .scaleY(s)
            .translationY(if (gainFocus) -u(6).toFloat() else 0f)
            .translationZ(if (gainFocus) u(18).toFloat() else 0f)
            .setDuration(160)
            .start()
        if (captionOnFocusOnly) {
            caption.visibility = if (gainFocus) View.VISIBLE else View.INVISIBLE
        } else {
            caption.setTextColor(if (gainFocus) INK else MUTED)
        }
    }

    private fun u(v: Int) = Math.round(v * scale)

    companion object {
        val INK = Color.parseColor("#15171c")
        val MUTED = Color.parseColor("#5a6070")
    }
}
