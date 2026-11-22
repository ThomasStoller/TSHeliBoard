// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.sticker

import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputContentInfoCompat
import helium314.keyboard.latin.R
import java.io.File

/** A user-imported sticker stored as a PNG file in the app's private storage. */
class Sticker(val file: File) {
    val id: Long = file.name.hashCode().toLong()
    val mimeType: String = "image/png"

    fun getContentUri(context: Context): Uri = FileProvider.getUriForFile(
        context,
        context.getString(R.string.clipboard_provider_authority),
        file
    )

    fun getContentInfo(context: Context): InputContentInfoCompat = InputContentInfoCompat(
        getContentUri(context),
        ClipDescription("sticker", arrayOf(mimeType)),
        null
    )
}
