package com.example.data.sync

enum class SyncStatus {
    PENDING,
    SYNCED,
    SYNC_CONFLICT,
    FAILED
}

enum class ConflictType {
    INSUFFICIENT_STOCK,
    INSUFFICIENT_BATCH_STOCK,
    MISSING_BATCH,
    BATCH_MISMATCH,
    BATCH_EXPIRED,
    PRICING_DISCREPANCY,
    FINANCIAL_MISMATCH,
    DUPLICATE_TRANSACTION
}

data class SyncConflict(
    val id: String,
    val clientTransactionId: String,
    val conflictType: ConflictType,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SyncResult(
    val status: SyncStatus,
    val clientTransactionId: String,
    val remoteId: String? = null,
    val conflict: SyncConflict? = null,
    val errorMessage: String? = null
)

data class SaleSyncRequest(
    val clientTransactionId: String,
    val receiptNumber: String,
    val customerName: String,
    val totalAmount: Double,
    val itemsSummary: String,
    val timestamp: Long,
    val branchId: String,
    val cashierUid: String,
    val lineItems: List<SaleLineItemRequest> = emptyList()
)

data class SaleLineItemRequest(
    val productId: String,
    val batchId: String?,
    val quantity: Int,
    val unitPrice: Double,
    val unitCost: Double
)
