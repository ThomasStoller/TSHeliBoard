// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.sticker

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.keyboard.clipboard.ClipboardLayoutParams
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

class StickerAdapter(
    private val layoutParams: ClipboardLayoutParams,
    private val onClick: (Sticker) -> Unit,
    private val onLongClick: (Sticker) -> Unit
) : RecyclerView.Adapter<StickerAdapter.ViewHolder>() {

    var itemBackgroundId = 0
    private val stickers = mutableListOf<Sticker>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(items: List<Sticker>) {
        stickers.clear()
        stickers.addAll(items)
        notifyDataSetChanged()
    }

    fun getById(id: Long): Sticker? = stickers.firstOrNull { it.id == id }

    fun remove(sticker: Sticker): Boolean {
        val index = stickers.indexOf(sticker)
        if (index < 0) return false
        stickers.removeAt(index)
        notifyItemRemoved(index)
        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.sticker_entry, parent, false)
        if (itemBackgroundId != 0) view.setBackgroundResource(itemBackgroundId)
        Settings.getValues().mColors.setBackground(view, ColorType.KEY_BACKGROUND)
        layoutParams.setItemProperties(view)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(stickers[position])

    override fun getItemCount() = stickers.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.sticker_image)
        private var current: Sticker? = null

        init {
            view.setOnClickListener { current?.let(onClick) }
            view.setOnLongClickListener {
                current?.let(onLongClick)
                current != null
            }
        }

        fun bind(sticker: Sticker) {
            current = sticker
            itemView.tag = sticker.id
            image.contentDescription = itemView.context.getString(R.string.sticker_content_description)
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            image.setImageBitmap(BitmapFactory.decodeFile(sticker.file.absolutePath, options))
        }
    }
}
