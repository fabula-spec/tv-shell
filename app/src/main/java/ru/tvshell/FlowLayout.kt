package ru.tvshell

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

/** Простая раскладка «в строку с переносом» — для меток кинотеатров. */
class FlowLayout(context: Context, private val hGap: Int, private val vGap: Int) : ViewGroup(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowHeight = 0
        var widest = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            child.measure(
                MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            if (x > 0 && x + child.measuredWidth > maxWidth) {
                x = 0
                y += rowHeight + vGap
                rowHeight = 0
            }
            x += child.measuredWidth + hGap
            widest = max(widest, x - hGap)
            rowHeight = max(rowHeight, child.measuredHeight)
        }
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(widthMeasureSpec)
        } else {
            widest + paddingLeft + paddingRight
        }
        setMeasuredDimension(w, y + rowHeight + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowHeight = 0
        // выравнивание по центру строки
        val rows = ArrayList<MutableList<View>>()
        var current = ArrayList<View>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            if (x > 0 && x + child.measuredWidth > maxWidth) {
                rows += current
                current = ArrayList()
                x = 0
            }
            current += child
            x += child.measuredWidth + hGap
        }
        if (current.isNotEmpty()) rows += current
        for (row in rows) {
            rowHeight = row.maxOf { it.measuredHeight }
            x = 0
            for (child in row) {
                val top = paddingTop + y + (rowHeight - child.measuredHeight) / 2
                child.layout(paddingLeft + x, top, paddingLeft + x + child.measuredWidth, top + child.measuredHeight)
                x += child.measuredWidth + hGap
            }
            y += rowHeight + vGap
        }
    }
}
