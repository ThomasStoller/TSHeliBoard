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
    private const val PREF_REOPEN = "sticker_folder_reopen"
    private const val PREF_LAST_SYNC = "sticker_folder_last_sync"
    private const val RECENT_SYNC_WINDOW_MS = 3000L
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
        val oldUri = context.prefs().getString(PREF_URI, null)
        context.prefs().edit {
            putString(PREF_URI, uri.toString())
            // Keep the import cache when the same folder is selected again, so nothing has to be
            // re-checked. A changed file still re-imports because its length/timestamp changes.
            if (oldUri != uri.toString()) remove(PREF_IMPORTED)
        }
        return true
    }

    fun requestReopen(context: Context) {
        context.prefs().edit { putBoolean(PREF_REOPEN, true) }
    }

    fun consumeReopen(context: Context): Boolean {
        val prefs = context.prefs()
        if (!prefs.getBoolean(PREF_REOPEN, false)) return false
        prefs.edit { remove(PREF_REOPEN) }
        return true
    }

    /**
     * Whether the folder was synced very recently. The folder picker syncs before it closes, so the
     * panel can show the imported stickers immediately without walking the SAF tree again.
     */
    fun wasSyncedRecently(context: Context): Boolean {
        val last = context.prefs().getLong(PREF_LAST_SYNC, 0L)
        return last > 0 && android.os.SystemClock.elapsedRealtime() - last < RECENT_SYNC_WINDOW_MS
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
            remove(PREF_LAST_SYNC)
        }
    }

    /** Imports images that were added to the folder since the previous sync. */
    suspend fun sync(context: Context): Int = syncMutex.withLock {
        val added = withContext(Dispatchers.IO) {
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
        context.prefs().edit { putLong(PREF_LAST_SYNC, android.os.SystemClock.elapsedRealtime()) }
        added
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
