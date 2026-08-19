package com.example.data

data class CanonicalProduct(
    val id: String = "",
    val name: String = "",
    val dosage: String = "",
    val category: String = "General",
    val unitForm: String = "Tablet",
    val brand: String = "Generic",
    val defaultPrice: Double = 0.0,
    val minStockThreshold: Int = 10,
    val nafdacRegNumber: String = "",
    val defaultSupplier: String = "Standard Pharma Wholesaler",
    val isCustomAdded: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
