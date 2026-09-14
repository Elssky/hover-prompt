package com.example.hoverprompt

import android.content.Context
import org.json.JSONArray

/** Stores the most recently used scripts without putting their text in the UI state only. */
internal object PromptHistoryStore {
    private const val PREFS_NAME = "hover_prompt_history"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 8

    fun load(context: Context): List<String> {
        val raw = preferences(context).getString(KEY_ENTRIES, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val text = array.optString(index).trim()
                    if (text.isNotEmpty()) add(text)
                }
            }.take(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    fun record(context: Context, text: String): List<String> {
        val normalized = text.trim()
        if (normalized.isEmpty()) return load(context)

        val updated = buildList {
            add(normalized)
            load(context).forEach { previous ->
                if (previous != normalized) add(previous)
            }
        }.take(MAX_ENTRIES)
        save(context, updated)
        return updated
    }

    fun delete(context: Context, text: String): List<String> {
        val updated = load(context).filterNot { it == text }
        save(context, updated)
        return updated
    }

    fun clear(context: Context) {
        preferences(context).edit().remove(KEY_ENTRIES).apply()
    }

    private fun save(context: Context, entries: List<String>) {
        val array = JSONArray()
        entries.forEach(array::put)
        preferences(context).edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
