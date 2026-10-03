package com.edro08.structa.application.editor

import com.edro08.structa.domain.filesystem.FileMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

open class FormatJsonDocument {
    open suspend operator fun invoke(content: String, mode: FileMode): String = withContext(Dispatchers.Default) {
        if (mode != FileMode.JSON) return@withContext content
        try {
            val value = content.trim()
            if (value.startsWith("[")) JSONArray(value).toString(2) else JSONObject(value).toString(2)
        } catch (_: JSONException) { content }
    }
}
