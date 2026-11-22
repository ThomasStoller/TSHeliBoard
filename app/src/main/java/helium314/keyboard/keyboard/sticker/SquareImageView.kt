// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.sticker

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView

/** ImageView that always measures as a square, so the sticker grid stays uniform. */
class SquareImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
