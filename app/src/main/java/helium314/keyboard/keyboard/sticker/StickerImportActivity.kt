// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.sticker

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import helium314.keyboard.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Import activity for stickers. It is used in two ways:
 *  - as a share target (ACTION_SEND / ACTION_SEND_MULTIPLE with image MIME types)
 *  - from the sticker panel with ACTION_IMPORT_STICKERS, which opens the system photo picker
 *
 * There is intentionally no UI: the activity copies the selected images and finishes.
 */
class StickerImportActivity : ComponentActivity() {

    @SuppressLint("InvalidFragmentVersionForActivityResult") // false positive: no fragments are used
    private val picker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> importAndFinish(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            if (intent?.action != ACTION_IMPORT_STICKERS) finish()
            return
        }

        if (intent?.action == ACTION_IMPORT_STICKERS) {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            importAndFinish(collectUris(intent))
        }
    }

    private fun collectUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        intent.clipData?.let { clip ->
            val fromClip = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
            if (fromClip.isNotEmpty()) return fromClip
        }
        val extra = intent.extras?.get(Intent.EXTRA_STREAM)
        val uris = when (extra) {
            is Uri -> listOf(extra)
            is String -> listOfNotNull(Uri.parse(extra))
            is ArrayList<*> -> extra.filterIsInstance<Uri>()
            is Array<*> -> extra.filterIsInstance<Uri>()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) return uris
        intent.data?.let {
            if (intent.type?.startsWith("image/") == true || it.scheme == "content") return listOf(it)
        }
        return emptyList()
    }

    private fun importAndFinish(uris: List<Uri>) {
        if (uris.isEmpty()) {
            finish()
            return
        }
        lifecycleScope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.count { StickerRepository.import(this@StickerImportActivity, it) }
            }
            Toast.makeText(
                this@StickerImportActivity,
                resources.getQuantityString(R.plurals.stickers_imported, added, added),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    companion object {
        const val ACTION_IMPORT_STICKERS = "com.thoma.tsheliboard.action.IMPORT_STICKERS"
    }
}
