package com.example.spinetrack.data.local

import android.content.Context

object CompletedLessonsStore {
    private const val PREFS = "spinetrack_prefs"
    private const val KEY = "completed_lessons"

    fun getCompletedIds(context: Context): Set<Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun isCompleted(context: Context, id: Int): Boolean {
        return getCompletedIds(context).contains(id)
    }

    fun markCompleted(context: Context, id: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        raw.add(id.toString())
        prefs.edit().putStringSet(KEY, raw).apply()
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY).apply()
    }
}

