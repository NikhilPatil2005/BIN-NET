package com.binnet.app.dashboard.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.binnet.app.dashboard.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ContactUtil - Utility class for device contacts operations
 * Uses ContentResolver to query device contacts
 */
object ContactUtil {

    /**
     * Fetches all contacts with phone numbers from the device
     * Uses Dispatchers.IO to prevent UI lag
     */
    suspend fun getAllContacts(context: Context): List<Contact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<Contact>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: "Unknown"
                val number = it.getString(numberIndex)?.replace("[^0-9+]".toRegex(), "") ?: ""

                if (number.isNotEmpty()) {
                    contacts.add(
                        Contact(
                            name = name,
                            phoneNumber = number,
                            phone = number
                        )
                    )
                }
            }
        }

        // Remove duplicates based on phone number
        contacts.distinctBy { it.phone }
    }

    /**
     * Smart search: If query is a phone number, search for matching name.
     * If found, return Contact(name, number). If not found, return Contact("Unknown", number).
     */
    suspend fun searchContact(context: Context, query: String): Contact? = withContext(Dispatchers.IO) {
        val normalizedQuery = query.replace("[^0-9+]".toRegex(), "")

        // If query is a phone number, search for contact by phone
        if (normalizedQuery.length >= 10) {
            val contentResolver: ContentResolver = context.contentResolver

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            val selectionArgs = arrayOf("%$normalizedQuery")

            val cursor: Cursor? = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    val name = it.getString(nameIndex) ?: "Unknown"
                    val number = it.getString(numberIndex)?.replace("[^0-9+]".toRegex(), "") ?: normalizedQuery

                    return@withContext Contact(
                        name = name,
                        phoneNumber = number,
                        phone = number
                    )
                }
            }

            // If phone number not found in contacts, return Unknown
            return@withContext Contact(
                name = "Unknown",
                phoneNumber = normalizedQuery,
                phone = normalizedQuery
            )
        }

        // If query is not a phone number, search by name
        val allContacts = getAllContacts(context)
        allContacts.find {
            it.name.contains(query, ignoreCase = true) ||
            it.phone.contains(normalizedQuery)
        }
    }

    /**
     * Search contacts by name or phone number
     */
    suspend fun searchContacts(context: Context, query: String): List<Contact> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }

        val normalizedQuery = query.replace("[^0-9a-zA-Z ]".toRegex(), "").lowercase()
        val allContacts = getAllContacts(context)

        allContacts.filter { contact ->
            contact.name.lowercase().contains(normalizedQuery) ||
            contact.phone.replace("[^0-9]".toRegex(), "").contains(normalizedQuery.replace("[^0-9]".toRegex(), ""))
        }
    }

    /**
     * Generate initials from a name
     */
    private fun getInitials(name: String): String {
        val parts = name.trim().split(" ")
        return when {
            parts.size >= 2 -> {
                val first = parts.first().firstOrNull()?.uppercaseChar() ?: '?'
                val last = parts.last().firstOrNull()?.uppercaseChar() ?: '?'
                "$first$last"
            }
            parts.isNotEmpty() && parts.first().isNotEmpty() -> {
                val first = parts.first().firstOrNull()?.uppercaseChar() ?: '?'
                val second = parts.first().getOrNull(1)?.uppercaseChar() ?: '?'
                "$first$second"
            }
            else -> "??"
        }
    }
}
