package com.example.util

import com.example.data.InventoryItem

data class StockTransferPayload(
    val sourceGlobalId: String = "",
    val sourceItemId: Int = 0,
    val name: String,
    val dosage: String,
    val unitForm: String = "",
    val brand: String = "",
    val category: String = "General",
    val batchNumber: String = "",
    val expiryDate: Long = 0L,
    val price: Double = 0.0,
    val quantity: Int,
    val fromBranch: String = "",
    val destinationBranch: String = "",
    val reason: String = ""
) {
    fun encodeToTaskDescription(): String {
        val cleanBatch = if (batchNumber.isNotBlank()) batchNumber.trim() else "DEFAULT"
        val cleanUnit = if (unitForm.isNotBlank()) unitForm.trim() else "Unit"
        val cleanBrand = if (brand.isNotBlank()) brand.trim() else "Standard"
        val cleanCat = if (category.isNotBlank()) category.trim() else "General"
        val cleanReason = if (reason.isNotBlank()) reason.trim() else "Stock Transfer"
        val cleanFrom = if (fromBranch.isNotBlank()) fromBranch.trim() else "Source Branch"
        val cleanGlobalId = if (sourceGlobalId.isNotBlank()) sourceGlobalId.trim() else "N/A"

        return "ITEM: ${name.trim()} | DOSAGE: ${dosage.trim()} | QTY: $quantity | FROM: $cleanFrom | REASON: $cleanReason | UNIT_FORM: $cleanUnit | BRAND: $cleanBrand | CATEGORY: $cleanCat | BATCH: $cleanBatch | EXPIRY: $expiryDate | PRICE: $price | SOURCE_GLOBAL_ID: $cleanGlobalId | SOURCE_ITEM_ID: $sourceItemId"
    }

    companion object {
        fun decodeFromDescription(description: String): StockTransferPayload? {
            if (!description.contains("ITEM: ") || !description.contains("QTY: ")) {
                return null
            }
            try {
                val itemName = description.substringAfter("ITEM: ").substringBefore(" | DOSAGE: ").trim()
                val itemDosage = if (description.contains("DOSAGE: ")) {
                    description.substringAfter("DOSAGE: ").substringBefore(" | QTY: ").trim()
                } else ""
                val itemQty = description.substringAfter("QTY: ").substringBefore(" | FROM: ").trim().toIntOrNull() ?: 0
                val fromBranch = if (description.contains("FROM: ")) {
                    description.substringAfter("FROM: ").substringBefore(" | REASON: ").trim()
                } else ""

                val afterReason = description.substringAfter("REASON: ")
                val reason = if (afterReason.contains(" | ")) {
                    afterReason.substringBefore(" | ").trim()
                } else {
                    afterReason.trim()
                }

                val unitForm = if (description.contains("UNIT_FORM: ")) {
                    description.substringAfter("UNIT_FORM: ").substringBefore(" | ").trim()
                } else ""

                val brand = if (description.contains("BRAND: ")) {
                    description.substringAfter("BRAND: ").substringBefore(" | ").trim()
                } else ""

                val category = if (description.contains("CATEGORY: ")) {
                    description.substringAfter("CATEGORY: ").substringBefore(" | ").trim()
                } else "General"

                val batchNumber = if (description.contains("BATCH: ")) {
                    val b = description.substringAfter("BATCH: ").substringBefore(" | ").trim()
                    if (b == "DEFAULT") "" else b
                } else ""

                val expiryDate = if (description.contains("EXPIRY: ")) {
                    description.substringAfter("EXPIRY: ").substringBefore(" | ").trim().toLongOrNull() ?: 0L
                } else 0L

                val price = if (description.contains("PRICE: ")) {
                    description.substringAfter("PRICE: ").substringBefore(" | ").trim().toDoubleOrNull() ?: 0.0
                } else 0.0

                val sourceGlobalId = if (description.contains("SOURCE_GLOBAL_ID: ")) {
                    val g = description.substringAfter("SOURCE_GLOBAL_ID: ").substringBefore(" | ").trim()
                    if (g == "N/A") "" else g
                } else ""

                val sourceItemId = if (description.contains("SOURCE_ITEM_ID: ")) {
                    description.substringAfter("SOURCE_ITEM_ID: ").substringBefore(" | ").trim().toIntOrNull() ?: 0
                } else 0

                if (itemName.isBlank() || itemQty <= 0) return null

                return StockTransferPayload(
                    sourceGlobalId = sourceGlobalId,
                    sourceItemId = sourceItemId,
                    name = itemName,
                    dosage = itemDosage,
                    unitForm = if (unitForm == "Unit") "" else unitForm,
                    brand = if (brand == "Standard") "" else brand,
                    category = category,
                    batchNumber = batchNumber,
                    expiryDate = expiryDate,
                    price = price,
                    quantity = itemQty,
                    fromBranch = fromBranch,
                    destinationBranch = "",
                    reason = reason
                )
            } catch (e: Exception) {
                return null
            }
        }

        /**
         * Resolves the exact destination InventoryItem for this payload from a candidate list
         * of branch inventory items using strict variant identity rules:
         * 1. Name match (case-insensitive)
         * 2. Dosage match (case-insensitive)
         * 3. Authoritative sourceGlobalId / globalId verification (Single & Multi candidate)
         * 4. Multi-candidate scoring with strict tie/ambiguity detection (FAILS CLOSED on ties/conflicts).
         * Returns null if no exact or safe match is found, or if candidates are ambiguous or conflicting.
         */
        fun resolveMatchingInventoryItem(
            candidates: List<InventoryItem>,
            payload: StockTransferPayload
        ): InventoryItem? {
            val matchingNameAndDose = candidates.filter {
                it.name.trim().equals(payload.name.trim(), ignoreCase = true) &&
                it.dosage.trim().equals(payload.dosage.trim(), ignoreCase = true)
            }

            if (matchingNameAndDose.isEmpty()) return null

            // Helper to validate core variant attribute compatibility (unitForm, brand, category)
            fun isVariantCompatible(candidate: InventoryItem): Boolean {
                if (payload.unitForm.isNotBlank() && candidate.unitForm.isNotBlank() &&
                    !candidate.unitForm.trim().equals(payload.unitForm.trim(), ignoreCase = true)
                ) {
                    return false
                }
                if (payload.brand.isNotBlank() && candidate.brand.isNotBlank() &&
                    !candidate.brand.trim().equals(payload.brand.trim(), ignoreCase = true)
                ) {
                    return false
                }
                if (payload.category.isNotBlank() && candidate.category.isNotBlank() &&
                    !candidate.category.trim().equals(payload.category.trim(), ignoreCase = true)
                ) {
                    return false
                }
                return true
            }

            // Global ID verification rule: When sourceGlobalId is present, the transfer MUST NOT
            // be accepted unless verified by an authoritative matching globalId on the candidate.
            if (payload.sourceGlobalId.isNotBlank()) {
                val matchingGlobalId = matchingNameAndDose.filter {
                    it.globalId.isNotBlank() &&
                    it.globalId.trim().equals(payload.sourceGlobalId.trim(), ignoreCase = true)
                }

                if (matchingGlobalId.size == 1) {
                    // Case 1 & Case 4: Exactly one candidate matches sourceGlobalId
                    val candidate = matchingGlobalId.first()
                    return if (isVariantCompatible(candidate)) candidate else null
                }

                // Case 2 (conflicting globalId), Case 3 (blank candidate globalId),
                // Case 5 (no candidates match), Case 6 (duplicate candidate globalId): FAIL CLOSED
                return null
            }

            // Fallback for legacy transfers (no sourceGlobalId) or legacy records without globalId:
            if (matchingNameAndDose.size == 1) {
                val candidate = matchingNameAndDose.first()
                return if (isVariantCompatible(candidate)) candidate else null
            }

            // Multiple candidates: disambiguate with strict scoring
            val scored = matchingNameAndDose.map { candidate ->
                var score = 0

                // 1. Source global ID matching (+100)
                if (payload.sourceGlobalId.isNotBlank() && candidate.globalId.isNotBlank() &&
                    candidate.globalId.trim().equals(payload.sourceGlobalId.trim(), ignoreCase = true)
                ) {
                    score += 100
                }

                // 2. Unit form matching (+50 if match, -100 if conflict)
                if (payload.unitForm.isNotBlank() && candidate.unitForm.isNotBlank()) {
                    if (candidate.unitForm.trim().equals(payload.unitForm.trim(), ignoreCase = true)) score += 50
                    else score -= 100 // Hard penalty on unit form mismatch
                }

                // 3. Brand matching (+40 if match, -80 if conflict)
                if (payload.brand.isNotBlank() && candidate.brand.isNotBlank()) {
                    if (candidate.brand.trim().equals(payload.brand.trim(), ignoreCase = true)) score += 40
                    else score -= 80 // Hard penalty on brand mismatch
                }

                // 4. Batch number matching (+20)
                if (payload.batchNumber.isNotBlank() && candidate.batchNumber.isNotBlank()) {
                    if (candidate.batchNumber.trim().equals(payload.batchNumber.trim(), ignoreCase = true)) score += 20
                }

                // 5. Category matching (+10)
                if (payload.category.isNotBlank() && candidate.category.isNotBlank()) {
                    if (candidate.category.trim().equals(payload.category.trim(), ignoreCase = true)) score += 10
                }

                Pair(candidate, score)
            }

            // Exclude disqualified candidates with negative scores (hard conflicts)
            val eligible = scored.filter { it.second >= 0 }
            if (eligible.isEmpty()) return null

            val maxScore = eligible.maxOf { it.second }
            val topCandidates = eligible.filter { it.second == maxScore }

            // STRICT AMBIGUITY INVARIANT: If two or more candidates have the exact same top score, FAIL CLOSED.
            if (topCandidates.size > 1) {
                return null
            }

            return topCandidates.first().first
        }

        /**
         * Resolves the destination InventoryBatch for a given InventoryItem and transfer payload:
         * - Matches physical lot by batchNumber.
         * - Enforces immutable lot metadata (e.g. expiryDate) consistency.
         * - If matching batch exists without conflict -> updates existing batch.
         * - If batch does not exist -> creates a new InventoryBatch under destinationItemId.
         * - If conflicting immutable metadata is detected -> FAILS CLOSED with conflict error.
         */
        fun resolveDestinationBatch(
            existingBatches: List<com.example.data.InventoryBatch>,
            destinationItemId: Int,
            payload: StockTransferPayload
        ): BatchResolutionResult {
            if (payload.batchNumber.isBlank()) {
                val singleBatch = existingBatches.firstOrNull()
                return if (singleBatch != null && existingBatches.size == 1) {
                    BatchResolutionResult(matchedBatch = singleBatch, isNewBatch = false)
                } else {
                    BatchResolutionResult(matchedBatch = null, isNewBatch = true)
                }
            }

            val matchingBatch = existingBatches.find {
                it.batchNumber.trim().equals(payload.batchNumber.trim(), ignoreCase = true)
            }

            if (matchingBatch != null) {
                // Verify immutable lot metadata: expiry date consistency
                if (payload.expiryDate > 0L && matchingBatch.expiryDate > 0L) {
                    val diff = kotlin.math.abs(matchingBatch.expiryDate - payload.expiryDate)
                    // Allow small time drift of 24h for timezones, but reject distinct expiration months/years
                    if (diff > (24L * 60L * 60L * 1000L)) {
                        return BatchResolutionResult(
                            hasConflict = true,
                            conflictReason = "Conflicting expiry date for Batch '${payload.batchNumber}' (Destination: ${matchingBatch.expiryDate}, Transfer: ${payload.expiryDate})"
                        )
                    }
                }
                return BatchResolutionResult(matchedBatch = matchingBatch, isNewBatch = false)
            }

            // New batch for this inventory item
            return BatchResolutionResult(matchedBatch = null, isNewBatch = true)
        }
    }
}

data class BatchResolutionResult(
    val matchedBatch: com.example.data.InventoryBatch? = null,
    val isNewBatch: Boolean = false,
    val hasConflict: Boolean = false,
    val conflictReason: String = ""
)
