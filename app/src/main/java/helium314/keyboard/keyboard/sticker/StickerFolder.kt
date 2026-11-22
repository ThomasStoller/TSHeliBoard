// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.sticker

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps a user-selected folder (for example DCIM/Clipped images) in sync with the
 * app-private sticker library. SAF is used, so no storage permission is required.
 */
object StickerFolder {
    const val PREF_URI = "sticker_folder_uri"
    const val PREF_IMPORTED = "sticker_folder_imported"
    private val syncMutex = Mutex()

    fun getUri(context: Context): Uri? =
        context.prefs().getString(PREF_URI, null)?.let(Uri::parse)

    fun displayName(context: Context): String? =
        getUri(context)?.let { DocumentFile.fromTreeUri(context, it)?.name }

    fun setUri(context: Context, uri: Uri): Boolean {
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.isSuccess
        if (!granted) return false
        context.prefs().edit {
            putString(PREF_URI, uri.toString())
            remove(PREF_IMPORTED)
        }
        return true
    }

    fun clear(context: Context) {
        getUri(context)?.let {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        context.prefs().edit {
            remove(PREF_URI)
            remove(PREF_IMPORTED)
        }
    }

    /** Imports images that were added to the folder since the previous sync. */
    suspend fun sync(context: Context): Int = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = getUri(context) ?: return@runCatching 0
                val root = DocumentFile.fromTreeUri(context, uri) ?: return@runCatching 0
                val prefs = context.prefs()
                val imported = prefs.getStringSet(PREF_IMPORTED, emptySet())!!.toMutableSet()
                val files = mutableListOf<DocumentFile>()
                collectImages(root, files)
                var added = 0
                files.forEach { file ->
                    val key = file.uri.toString() + "|" + file.length() + "|" + file.lastModified()
                    if (imported.contains(key)) return@forEach
                    if (StickerRepository.import(context, file.uri)) added++
                    imported.add(key)
                }
                prefs.edit { putStringSet(PREF_IMPORTED, imported) }
                added
            }.getOrDefault(0)
        }
    }

    private fun collectImages(dir: DocumentFile, out: MutableList<DocumentFile>) {
        dir.listFiles().forEach { file ->
            when {
                file.isDirectory -> collectImages(file, out)
                file.isFile && file.type?.startsWith("image/") == true -> out.add(file)
            }
        }
    }
}
