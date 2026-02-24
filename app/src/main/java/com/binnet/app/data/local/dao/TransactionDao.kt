package com.binnet.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.binnet.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * TransactionDao - Data Access Object for transaction operations
 */
@Dao
interface TransactionDao {

    /**
     * Insert a new transaction
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    /**
     * Get all transactions ordered by timestamp (newest first)
     */
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    /**
     * Get last 5 unique receivers (distinct UPI IDs) the user has paid
     * This is used for the "People" section on the dashboard
     */
    @Query("""
        SELECT * FROM transactions 
        WHERE transactionType = 'UPI' 
        AND status = 'SUCCESS'
        ORDER BY timestamp DESC
    """)
    fun getRecentTransactions(): Flow<List<TransactionEntity>>

    /**
     * Get unique receivers from recent transactions (last 5)
     */
    @Query("""
        SELECT DISTINCT receiverName, receiverUpiId 
        FROM transactions 
        WHERE transactionType = 'UPI' 
        AND status = 'SUCCESS'
        ORDER BY timestamp DESC
        LIMIT 5
    """)
    fun getUniqueReceivers(): Flow<List<ReceiverInfo>>

    /**
     * Get transaction count
     */
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    /**
     * Get total amount sent
     */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE status = 'SUCCESS'")
    suspend fun getTotalAmountSent(): Double
}

/**
 * Data class for receiver info query result
 */
data class ReceiverInfo(
    val receiverName: String,
    val receiverUpiId: String
)
