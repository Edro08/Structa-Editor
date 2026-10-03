package com.edro08.structa.application.editor

import com.edro08.structa.domain.filesystem.FileMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

open class ContentFormatDetector {
    open fun detect(content: String): FileMode {
        val value = content.trim()
        try {
            if (value.startsWith("{") && value.endsWith("}")) { JSONObject(value); return FileMode.JSON }
            if (value.startsWith("[") && value.endsWith("]")) { JSONArray(value); return FileMode.JSON }
        } catch (_: JSONException) {
            // Invalid JSON continues through the YAML/text heuristic.
        }
        if (value.lineSequence().any { it.trim().matches(YAML_LINE) }) return FileMode.YAML
        return FileMode.TEXT
    }

    private companion object { val YAML_LINE = Regex("^[A-Za-z0-9_.-]+\\s*:.*$") }
}
