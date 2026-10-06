package com.edro08.structa.data.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileRef
import com.edro08.structa.domain.settings.SettingsRepository
import com.edro08.structa.domain.workspace.WorkspaceHistoryRepository
import com.edro08.structa.domain.workspace.WorkspaceShortcut
import org.json.JSONArray
import org.json.JSONObject

class AndroidWorkspaceHistoryRepository(context: Context, settings: SettingsRepository,
    name: String = "structa_workspaces") : WorkspaceHistoryRepository {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    private var entries = read()

    init {
        if (!preferences.getBoolean("migrated_last_folder", false)) {
            settings.lastFolderRef()?.let { ref ->
                val id = ref.id
                if (entries.none { it.id == id }) {
                    val (name, path) = describe(id)
                    entries = entries + WorkspaceShortcut(id, name, path, System.currentTimeMillis(), providerId = ref.fileSystem)
                }
            }
            preferences.edit().putBoolean("migrated_last_folder", true).apply()
            persist()
        }
    }

    override fun all(): List<WorkspaceShortcut> = entries.toList()

    override fun opened(id: DocumentId, at: Long) {
        val (name, path) = describe(id)
        val previous = entries.firstOrNull { it.id == id }
        entries = entries.filterNot { it.id == id } + WorkspaceShortcut(id,
            previous?.name ?: name, previous?.path ?: path, at, previous?.favorite ?: false,
            previous?.providerId ?: FileRef.of(id).fileSystem)
        persist()
    }

    override fun rename(id: DocumentId, name: String) {
        entries = entries.map { if (it.id == id) it.copy(name = name) else it }
        persist()
    }

    override fun setFavorite(id: DocumentId, favorite: Boolean) {
        entries = entries.map { if (it.id == id) it.copy(favorite = favorite) else it }
        persist()
    }

    override fun remove(id: DocumentId) {
        entries = entries.filterNot { it.id == id }
        persist()
    }

    private fun read(): List<WorkspaceShortcut> = try {
        val array = JSONArray(preferences.getString("history", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            WorkspaceShortcut(DocumentId(item.getString("id")), item.getString("name"),
                if (item.isNull("path")) null else item.optString("path").takeIf { it.isNotBlank() }, item.getLong("opened"),
                item.optBoolean("favorite"), item.optString("provider", FileRef.of(DocumentId(item.getString("id"))).fileSystem))
        }.distinctBy { it.id }
    } catch (_: Exception) { emptyList() }

    private fun persist() {
        val array = JSONArray()
        entries.forEach { item ->
            array.put(JSONObject().put("id", item.id.value).put("name", item.name)
                .put("path", item.path).put("opened", item.lastOpenedAt).put("favorite", item.favorite)
                .put("provider", item.providerId))
        }
        preferences.edit().putString("history", array.toString()).apply()
    }

    companion object {
        fun describe(id: DocumentId): Pair<String, String?> {
            if (FileRef.of(id).fileSystem == "direct") return java.io.File(id.value).name to id.value
            val uri = Uri.parse(id.value)
            val treeId = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { null }
            val path = treeId?.substringAfter(':', "")?.takeIf { it.isNotBlank() }
            val name = path?.substringAfterLast('/') ?: treeId?.substringAfterLast('/')
                ?: uri.lastPathSegment?.substringAfterLast('/') ?: id.value
            return name to path
        }
    }
}
