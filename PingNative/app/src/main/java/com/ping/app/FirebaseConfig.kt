package com.ping.app

import org.json.JSONObject

object FirebaseConfig {
    val keys = listOf("apiKey", "databaseURL", "projectId", "appId")
    val sample = """
        {
          apiKey: "...",
          databaseURL: "...",
          projectId: "...",
          appId: "..."
        }
    """.trimIndent()

    fun parse(text: String): JSONObject {
        // JSONTokener also accepts the unquoted keys in Firebase's JavaScript config.
        val source = text.trim().removePrefix("export ")
            .replaceFirst(Regex("^(?:const|let|var)\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*=\\s*"), "")
            .removeSuffix(";").trim()
        require(source.startsWith("{") && source.endsWith("}")) { "Enter a Firebase config object enclosed in { }." }
        val json = JSONObject(source)
        keys.forEach { key ->
            require(!json.has(key) || json.isNull(key) || json.get(key) is String) { "$key must be a text value." }
        }
        return json
    }

    fun fields(json: JSONObject): Map<String, String> = keys.associateWith { key ->
        if (json.isNull(key)) "" else json.optString(key, "")
    }

    fun mergeFields(base: JSONObject, values: Map<String, String>): JSONObject {
        val json = JSONObject(base.toString())
        keys.forEach { key ->
            val value = values[key].orEmpty().trim()
            if (value.isEmpty()) json.remove(key) else json.put(key, value)
        }
        return json
    }

    fun validate(json: JSONObject) {
        for (key in listOf("apiKey", "databaseURL")) {
            require(fields(json)[key].orEmpty().isNotBlank()) { "$key is required." }
        }
        keys.forEach { key ->
            require(fields(json)[key] != "...") { "Replace the sample value for $key." }
        }
        val uri = java.net.URI(json.getString("databaseURL"))
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "databaseURL must be a valid https:// URL." }
    }
}
