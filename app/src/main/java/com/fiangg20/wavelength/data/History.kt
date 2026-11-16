package com.fiangg20.wavelength.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What was played and how often. Stored as song ids on the phone, never sent anywhere. */
class HistoryStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("wavelength_history", Context.MODE_PRIVATE)

    private val _recent = MutableStateFlow(readRecent())
    /** Most recent first, each song once. */
    val recent: StateFlow<List<Long>> = _recent.asStateFlow()

    private val _counts = MutableStateFlow(readCounts())
    val counts: StateFlow<Map<Long, Int>> = _counts.asStateFlow()

    private fun readRecent(): List<Long> =
        (prefs.getString("recent", "") ?: "").split(',').mapNotNull { it.trim().toLongOrNull() }

    private fun readCounts(): Map<Long, Int> =
        (prefs.getString("counts", "") ?: "").split(',').mapNotNull {
            val parts = it.split(':')
            val id = parts.getOrNull(0)?.trim()?.toLongOrNull()
            val n = parts.getOrNull(1)?.trim()?.toIntOrNull()
            if (id != null && n != null) id to n else null
        }.toMap()

    @Synchronized
    fun record(id: Long) {
        val nextRecent = (listOf(id) + _recent.value.filter { it != id }).take(80)
        val nextCounts = _counts.value + (id to ((_counts.value[id] ?: 0) + 1))
        _recent.value = nextRecent
        _counts.value = nextCounts
        save(nextRecent, nextCounts)
    }

    @Synchronized
    fun clear() {
        _recent.value = emptyList()
        _counts.value = emptyMap()
        save(emptyList(), emptyMap())
    }

    private fun save(recent: List<Long>, counts: Map<Long, Int>) {
        prefs.edit()
            .putString("recent", recent.joinToString(","))
            .putString("counts", counts.entries.joinToString(",") { "${it.key}:${it.value}" })
            .apply()
    }

    companion object {
        @Volatile
        private var instance: HistoryStore? = null

        fun get(context: Context): HistoryStore =
            instance ?: synchronized(this) {
                instance ?: HistoryStore(context.applicationContext).also { instance = it }
            }
    }
}
