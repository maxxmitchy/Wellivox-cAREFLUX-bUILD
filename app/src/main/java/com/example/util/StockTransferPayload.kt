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
         * 3. Scoring / disambiguation on UnitForm, Brand, Category, and Batch.
         * Returns null if no exact or safe match is found.
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
            if (matchingNameAndDose.size == 1) {
                val candidate = matchingNameAndDose.first()
                // If unitForm is defined in both and conflicts, do NOT falsely collide!
                if (payload.unitForm.isNotBlank() && candidate.unitForm.isNotBlank() &&
                    !candidate.unitForm.trim().equals(payload.unitForm.trim(), ignoreCase = true)
                ) {
                    return null
                }
                // If brand is defined in both and conflicts, do NOT falsely collide!
                if (payload.brand.isNotBlank() && candidate.brand.isNotBlank() &&
                    !candidate.brand.trim().equals(payload.brand.trim(), ignoreCase = true)
                ) {
                    return null
                }
                return candidate
            }

            // Multiple candidates with same name and dosage: disambiguate with strict scoring
            val scored = matchingNameAndDose.map { candidate ->
                var score = 0
                if (payload.unitForm.isNotBlank() && candidate.unitForm.isNotBlank()) {
                    if (candidate.unitForm.trim().equals(payload.unitForm.trim(), ignoreCase = true)) score += 50
                    else score -= 100 // Hard penalty on unit form mismatch
                }
                if (payload.brand.isNotBlank() && candidate.brand.isNotBlank()) {
                    if (candidate.brand.trim().equals(payload.brand.trim(), ignoreCase = true)) score += 40
                    else score -= 80 // Hard penalty on brand mismatch
                }
                if (payload.batchNumber.isNotBlank() && candidate.batchNumber.isNotBlank()) {
                    if (candidate.batchNumber.trim().equals(payload.batchNumber.trim(), ignoreCase = true)) score += 20
                }
                if (payload.category.isNotBlank() && candidate.category.isNotBlank()) {
                    if (candidate.category.trim().equals(payload.category.trim(), ignoreCase = true)) score += 10
                }
                Pair(candidate, score)
            }

            val bestMatch = scored.maxByOrNull { it.second }
            return if (bestMatch != null && bestMatch.second >= 0) bestMatch.first else null
        }
    }
}
