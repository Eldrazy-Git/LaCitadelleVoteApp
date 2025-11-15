package fr.lacitadelle.votecompagnon.utils

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

class CustomTypefaceSpan(
    private val newType: Typeface
) : MetricAffectingSpan() {

    override fun updateDrawState(tp: TextPaint) = applyCustomTypeFace(tp, newType)
    override fun updateMeasureState(tp: TextPaint) = applyCustomTypeFace(tp, newType)

    private fun applyCustomTypeFace(paint: Paint, tf: Typeface) {
        val old = paint.typeface
        val oldStyle = old?.style ?: 0
        val fakeStyle = oldStyle and tf.style.inv()

        if (fakeStyle and Typeface.BOLD != 0) {
            paint.isFakeBoldText = true
        }
        if (fakeStyle and Typeface.ITALIC != 0) {
            paint.textSkewX = -0.25f
        }

        paint.typeface = tf
    }
}
