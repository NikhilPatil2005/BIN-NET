package com.binnet.app.dashboard.model

/**
 * Contact model for displaying contacts in the dashboard
 * Supports both device contacts and transaction receivers
 */
data class Contact(
    val id: String = "",
    val name: String,
    val phoneNumber: String = "",
    val phone: String = "", // Legacy field for compatibility
    val photoUri: String? = null,
    val upiId: String? = null,
    val bankName: String? = null
) {
    /**
     * Computed property for avatar initials
     * Falls back to first letters of name words
     */
    val avatarInitials: String
        get() {
            if (name.isBlank()) return "?"
            val parts = name.trim().split(" ").filter { it.isNotBlank() }
            return when {
                parts.size >= 2 -> {
                    val first = parts.first().firstOrNull()?.uppercaseChar() ?: '?'
                    val last = parts.last().firstOrNull()?.uppercaseChar() ?: '?'
                    "$first$last"
                }
                parts.isNotEmpty() && parts.first().isNotEmpty() -> {
                    val first = parts.first().firstOrNull()?.uppercaseChar() ?: '?'
                    if (parts.first().length > 1) {
                        val second = parts.first().getOrNull(1)?.uppercaseChar() ?: '?'
                        "$first$second"
                    } else {
                        "$first?"
                    }
                }
                else -> "?"
            }
        }
}

/**
 * RecentTransaction model for displaying transaction history
 */
data class RecentTransaction(
    val id: String,
    val name: String,
    val amount: String,
    val date: String,
    val status: TransactionStatus,
    val type: TransactionType,
    val avatarInitials: String = ""
)

enum class TransactionStatus {
    SUCCESS,
    PENDING,
    FAILED
}

enum class TransactionType {
    SENT,
    RECEIVED,
    SELF
}
