package com.vega.brain

import android.Manifest
import android.content.Context
import android.os.SystemClock
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.vega.actions.has
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactResolver(private val context: Context) {
    @Volatile private var cache: List<Contact>? = null
    @Volatile private var loadedAt = 0L

    suspend fun resolve(query: String): ContactMatch = withContext(Dispatchers.IO) {
        if (!context.has(Manifest.permission.READ_CONTACTS)) ContactMatch.NoPermission
        else NameMatcher.match(query, all())
    }

    /** Contact names to boost in speech-to-text. Call off the main thread. */
    fun hotwords(): List<String> {
        if (!context.has(Manifest.permission.READ_CONTACTS)) return emptyList()
        val contacts = all()
        return (contacts.map { it.name } + contacts.map { it.name.substringBefore(' ') }).distinct().take(500)
    }

    private fun all(): List<Contact> {
        val now = SystemClock.elapsedRealtime()
        cache?.let { if (now - loadedAt < CACHE_MS) return it }
        val list = mutableListOf<Contact>()
        context.contentResolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, "${Phone.DISPLAY_NAME} ASC",
        )?.use { c ->
            val nameCol = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
            val numberCol = c.getColumnIndexOrThrow(Phone.NUMBER)
            while (c.moveToNext()) {
                val name = c.getString(nameCol)
                val number = c.getString(numberCol)
                if (!name.isNullOrBlank() && !number.isNullOrBlank()) list += Contact(name.trim(), number)
            }
        }
        return list.distinctBy { it.name.lowercase() }.also { cache = it; loadedAt = now }
    }

    private companion object {
        const val CACHE_MS = 60_000L
    }
}
