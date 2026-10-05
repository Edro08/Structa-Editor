package com.edro08.structa.data.settings

import android.content.Context
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.workspace.*
import org.json.JSONArray
import org.json.JSONObject

class AndroidSessionRepository(context: Context, storageName: String = "structa_session") : SessionRepository {
    private val preferences = context.applicationContext.getSharedPreferences(storageName, Context.MODE_PRIVATE)

    override fun load(): EditorSession {
        val raw = preferences.getString("metadata", null) ?: return EditorSession()
        return try {
            val json = JSONObject(raw)
            val tabs = json.getJSONArray("tabs")
            EditorSession((0 until tabs.length()).map { index ->
                val tab = tabs.getJSONObject(index)
                SessionTab(DocumentId(tab.getString("id")), tab.optInt("anchor").coerceAtLeast(0),
                    tab.optInt("active").coerceAtLeast(0), tab.optDouble("x", 0.0).toFloat().safeScroll(),
                    tab.optDouble("y", 0.0).toFloat().safeScroll())
            }.distinctBy { it.id }, json.optString("active").takeIf { it.isNotEmpty() }?.let(::DocumentId))
        } catch (_: Exception) { EditorSession() }
    }

    override fun save(session: EditorSession) {
        val tabs = JSONArray()
        session.tabs.forEach { tab -> tabs.put(JSONObject().put("id", tab.id.value)
            .put("anchor", tab.anchor).put("active", tab.active)
            .put("x", tab.scrollX.safeScroll()).put("y", tab.scrollY.safeScroll())) }
        val json = JSONObject().put("tabs", tabs).put("active", session.active?.value ?: "")
        preferences.edit().putString("metadata", json.toString()).apply()
    }

    private fun Float.safeScroll() = if (isFinite()) coerceAtLeast(0f) else 0f
}
