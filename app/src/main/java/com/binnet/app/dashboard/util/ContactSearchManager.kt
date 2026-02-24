package com.binnet.app.dashboard.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import com.binnet.app.dashboard.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ContactSearchManager - Handles contact fetching and searching from device contacts
 * Uses ContentResolver to search both Names AND Phone Numbers simultaneously
 * Runs on Dispatchers.IO for background thread performance
 */
class ContactSearchManager(private val context: Context) {

    companion object {
        private const val TAG = "ContactSearchManager"
    }

    /**
     * Searches contacts by query string
     * Searches BOTH contact names AND phone numbers simultaneously
     * @param query - The search query (name or phone number)
     * @return List of matching contacts sorted alphabetically by name
     */
    suspend fun searchContacts(query: String): List<Contact> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }

        val contacts = mutableSetOf<Contact>() // Using Set to avoid duplicates
        val contentResolver: ContentResolver = context.contentResolver

        try {
            // Clean the query for phone number comparison
            val normalizedQuery = query.replace(Regex("[^0-9]"), "")
            val nameQuery = "%$query%" // Keep original for name search

            // SEARCH 1: By phone number in contacts
            if (normalizedQuery.isNotEmpty()) {
                val phoneQuery = "%$normalizedQuery%"
                val phoneCursor: Cursor? = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                    ),
                    "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                    arrayOf(phoneQuery),
                    null
                )

                phoneCursor?.use { cursor ->
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                    while (cursor.moveToNext()) {
                        val contactId = cursor.getString(idIndex) ?: ""
                        val name = cursor.getString(nameIndex) ?: "Unknown"
                        val number = cursor.getString(numberIndex) ?: ""
                        val photoUri = cursor.getString(photoIndex)

                        // Normalize number for comparison
                        val normalizedNumber = number.replace(Regex("[^0-9]"), "")

                        contacts.add(
                            Contact(
                                id = contactId,
                                name = name,
                                phoneNumber = normalizedNumber,
                                photoUri = photoUri
                            )
                        )
                    }
                }
            }

            // SEARCH 2: By display name (simultaneous - not just fallback)
            val nameCursor: Cursor? = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.PHOTO_URI
                ),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ? COLLATE NOCASE",
                arrayOf(nameQuery),
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
            )

            nameCursor?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val photoIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(idIndex) ?: ""
                    val name = cursor.getString(nameIndex) ?: "Unknown"
                    val photoUri = cursor.getString(photoIndex)

                    // Get phone number for this contact
                    val phoneNumber = getPrimaryPhoneNumber(contactId)

                    if (phoneNumber.isNotEmpty()) {
                        contacts.add(
                            Contact(
                                id = contactId,
                                name = name,
                                phoneNumber = phoneNumber,
                                photoUri = photoUri
                            )
                        )
                    }
                }
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to read contacts", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching contacts", e)
        }

        // Return as list, sorted by name alphabetically
        contacts.toList().sortedBy { it.name.lowercase() }
    }

    /**
     * Gets the primary phone number for a contact ID
     */
    private fun getPrimaryPhoneNumber(contactId: String): String {
        var phoneNumber = ""
        val contentResolver = context.contentResolver

        val phoneCursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )

        phoneCursor?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                phoneNumber = cursor.getString(numberIndex)?.replace(Regex("[^0-9]"), "") ?: ""
            }
        }

        return phoneNumber
    }

    /**
     * Checks if a phone number exists in device contacts
     * @return Contact if found, null if not found
     */
    suspend fun findContactByPhoneNumber(phoneNumber: String): Contact? = withContext(Dispatchers.IO) {
        val normalizedNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        
        val contentResolver = context.contentResolver
        val phoneQuery = "%$normalizedNumber%"

        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            ),
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
            arrayOf(phoneQuery),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                return@withContext Contact(
                    id = it.getString(idIndex) ?: "",
                    name = it.getString(nameIndex) ?: "Unknown",
                    phoneNumber = it.getString(numberIndex)?.replace(Regex("[^0-9]"), "") ?: "",
                    photoUri = it.getString(photoIndex)
                )
            }
        }
        
        return@withContext null
    }
}
