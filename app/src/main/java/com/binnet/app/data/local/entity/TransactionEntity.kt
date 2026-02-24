package com.binnet.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * TransactionEntity - Room entity for storing transaction history
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val receiverName: String,
    val receiverUpiId: String,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SUCCESS", // SUCCESS, FAILED, PENDING
    val transactionType: String = "UPI", // UPI, BANK_TRANSFER, SELF_TRANSFER
    val note: String? = null
)
