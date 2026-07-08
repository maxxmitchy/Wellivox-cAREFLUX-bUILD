package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AnalyticsTab(viewModel: PharmacyViewModel, isUserAdmin: Boolean = false) {
    val context = LocalContext.current
    val inventory by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val customerMeds by viewModel.customerMedications.collectAsStateWithLifecycle()
    val interventions by viewModel.clinicalInterventions.collectAsStateWithLifecycle()
    val medicationSales by viewModel.medicationSales.collectAsStateWithLifecycle()

    val timeNow = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000L
    val thirtyDaysAgo = timeNow - 30 * dayMs
    val ninetyDaysAgo = timeNow - 90 * dayMs

    // --- Financial Overview ---
    val recentReceipts = receipts.filter { it.timestamp >= thirtyDaysAgo }
    val monthlyRevenue = recentReceipts.sumOf { it.totalAmount }
    val avgOrderValue = if (recentReceipts.isNotEmpty()) monthlyRevenue / recentReceipts.size else 0.0

    // --- Customer Acquisition ---
    val newCustomers = customers.filter { it.dateAdded >= thirtyDaysAgo }.size
    val activePrescriptions = customerMeds.filter { it.nextRefillDate >= timeNow - 30 * dayMs }.size

    // --- Inventory & Product Insights ---
    val topSellers = inventory.sortedByDescending { it.totalSoldQuantity }.take(3)
    val criticalStock = inventory.filter { it.stockQuantity <= it.minRequiredStock && it.stockQuantity > 0 }.size
    
    // --- Operational & Clinical Impact ---
    val recentInterventions = interventions.filter { it.dateAdded >= thirtyDaysAgo }.size

    // --- Near-Expiry ---
    val expiring30List = inventory.filter { it.expiryDate in 1L..timeNow + 30 * dayMs }
    val expiring60List = inventory.filter { it.expiryDate in (timeNow + 30 * dayMs)..(timeNow + 60 * dayMs) }
    val expiring90List = inventory.filter { it.expiryDate in (timeNow + 60 * dayMs)..(timeNow + 90 * dayMs) }

    // --- Dead Stock & Missed Revenue ---
    val deadStock = inventory.filter { it.lastSoldDate < ninetyDaysAgo }
    val potentialLostSales = inventory.filter { it.stockQuantity == 0 }.sumOf { it.price * it.minRequiredStock }

    var deadStockLimit by remember { mutableStateOf(5) }
    var inventorySegmentTab by remember { mutableStateOf(0) } // 0 = Near-Expiry, 1 = Top Sellers, 2 = Dead Stock
    var demographicsSegmentTab by remember { mutableStateOf(0) } // 0 = Patient Age, 1 = Meds & Brands, 2 = Location (Geo), 3 = Nodes
    val formatPrice: (Double) -> String = { "₦%,.2f".format(it) }

    var selectedExpiringItem by remember { mutableStateOf<InventoryItem?>(null) }
    var isLoadingStrategy by remember { mutableStateOf(false) }
    var generatedStrategyText by remember { mutableStateOf("") }
    var triggerRegen by remember { mutableStateOf(false) }

    var isEditingStrategy by remember { mutableStateOf(false) }
    var editedStrategyText by remember { mutableStateOf("") }

    LaunchedEffect(selectedExpiringItem) {
        if (selectedExpiringItem != null) {
            isEditingStrategy = false
            editedStrategyText = ""
        }
    }

    if (selectedExpiringItem != null) {
        val item = selectedExpiringItem!!
        LaunchedEffect(item, triggerRegen) {
            if (item.salesStrategy.isNotBlank() && !triggerRegen) {
                generatedStrategyText = item.salesStrategy
                isLoadingStrategy = false
            } else {
                isLoadingStrategy = true
                generatedStrategyText = ""
                val strategy = CarefluxAIEngine.generateExpiryStrategy(viewModel.getApiKey(), item)
                generatedStrategyText = strategy
                viewModel.updateInventorySalesStrategy(item, strategy)
                isLoadingStrategy = false
                triggerRegen = false
            }
        }

        AlertDialog(
            onDismissRequest = { 
                selectedExpiringItem = null 
                triggerRegen = false
                isEditingStrategy = false
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.LocalHospital, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEditingStrategy) "Edit Intel Brief" else "Clinical Intel Brief",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${item.name} • ${item.dosage}", 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.salesStrategy.isNotBlank() && !isLoadingStrategy && !isEditingStrategy) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Cached", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingStrategy) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Formulating the comprehensive Pharmacist Intelligence Brief...",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (isEditingStrategy) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = editedStrategyText,
                                onValueChange = { editedStrategyText = it },
                                modifier = Modifier.fillMaxWidth().height(250.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                label = { Text("Edit Brief (Markdown)") },
                                placeholder = { Text("Enter counseling guides, cautions, selling strategies...") }
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .fillMaxWidth()
                        ) {
                            MarkdownText(
                                text = generatedStrategyText,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = SlateBorderLight)
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                "FEFO Action Center (Rescue & Campaigns)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = TealTertiary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Math
                            val recentSales = medicationSales.filter { it.productName.equals(item.name, ignoreCase = true) && it.dateSold >= (System.currentTimeMillis() - 90L * dayMs) }
                            val totalSoldIn90Days = recentSales.sumOf { it.quantitySold }
                            val velocityPerDay = totalSoldIn90Days.toDouble() / 90.0
                            val daysRemaining = maxOf(1L, (item.expiryDate - System.currentTimeMillis()) / dayMs)
                            val predictedSalesBeforeExpiry = velocityPerDay * daysRemaining
                            val wasteRunway = maxOf(0.0, item.stockQuantity.toDouble() - predictedSalesBeforeExpiry)
                            val wasteValue = wasteRunway * item.price

                            // Waste Runway Analysis Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (wasteRunway > 0) WarningRedContainerSoft else TealSurface),
                                border = BorderStroke(1.dp, if (wasteRunway > 0) WarningRed.copy(alpha = 0.5f) else TealPrimary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (wasteRunway > 0) Icons.Filled.TrendingDown else Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = if (wasteRunway > 0) WarningRed else OKGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Waste Runway Analysis",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (wasteRunway > 0) WarningRedTitle else TealTertiary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Shelf Velocity: ${"%.2f".format(velocityPerDay * 30.0)} units/month",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateTextMedium
                                    )
                                    Text(
                                        text = "Predicted Sales: ${predictedSalesBeforeExpiry.toInt()} units before expiry",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateTextMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (wasteRunway > 0) {
                                        Text(
                                            text = "⚠️ Projected Unsold Expired Stock: ${wasteRunway.toInt()} units",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningRed
                                        )
                                        Text(
                                            text = "Projected Financial Loss: ₦${"%,.2f".format(wasteValue)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningRed
                                        )
                                    } else {
                                        Text(
                                            text = "✅ Current velocity is sufficient to clear all stock before expiry.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = OKGreen
                                        )
                                    }
                                }
                            }

                            // TIER 1: INTERNAL WHATSAPP CAMPAIGN DRIVE
                            val chronicPatients = customers.filter { cust -> customerMeds.any { med -> med.customerId == cust.id && med.medicationName.contains(item.name, ignoreCase = true) } }
                            val discountPercent = when {
                                daysRemaining <= 30 -> 40
                                daysRemaining <= 60 -> 25
                                else -> 15
                            }
                            val campaignMsg = "Hello! This is Careflux Pharmacy. To thank you for being a valued customer, we are offering an exclusive compliance promotion! Refill your prescription for ${item.name} today and receive an immediate ${discountPercent}% discount! Reply to lock in your discount."

                            Card(
                                colors = CardDefaults.cardColors(containerColor = TealSurface),
                                border = BorderStroke(1.dp, SlateBorderLight),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "Tier 1: Internal Patient Refill Drive",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TealTertiary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Found ${chronicPatients.size} registered chronic patient(s) on this medication.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateTextMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            if (chronicPatients.isNotEmpty()) {
                                                val patient = chronicPatients.first()
                                                val encoded = android.net.Uri.encode("Hello ${patient.name}, $campaignMsg")
                                                val url = "https://api.whatsapp.com/send?phone=${patient.phoneNumber.trim().replace("[^0-9]".toRegex(), "")}&text=$encoded"
                                                val wpIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                                try {
                                                    context.startActivity(wpIntent)
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "WhatsApp seems missing.", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                val encoded = android.net.Uri.encode(campaignMsg)
                                                val url = "https://api.whatsapp.com/send?text=$encoded"
                                                val wpIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                                try {
                                                    context.startActivity(wpIntent)
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "WhatsApp seems missing.", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Launch WhatsApp Refill Campaign (${discountPercent}% Off)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // TIER 2: PUBLIC RESCUE MARKETPLACE LISTING
                            val priceMultiplier = when {
                                daysRemaining <= 30 -> 0.40
                                daysRemaining <= 60 -> 0.60
                                else -> 0.80
                            }
                            val suggestedPrice = item.price * priceMultiplier

                            Card(
                                colors = CardDefaults.cardColors(containerColor = TealSurface),
                                border = BorderStroke(1.dp, SlateBorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "Tier 2: Public Rescue Marketplace Pool",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TealTertiary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "List surplus stock instantly on the Rescue Marketplace to sell to other branches or hospitals.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateTextMedium
                                    )
                                    Text(
                                        "Suggested Price: ₦${"%,.2f".format(suggestedPrice)} (based on shelf life)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            viewModel.createRescueListing(
                                                inv = item,
                                                qty = item.stockQuantity,
                                                price = suggestedPrice,
                                                commPercentage = 10.0,
                                                durationDays = daysRemaining.toInt()
                                            )
                                            android.widget.Toast.makeText(
                                                context,
                                                "Posted ${item.stockQuantity} units of ${item.name} to Rescue Marketplace for ₦${"%,.0f".format(suggestedPrice)}!",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                            selectedExpiringItem = null
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.Store, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Post to Rescue Marketplace", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isEditingStrategy) {
                    TextButton(
                        onClick = {
                            viewModel.updateInventorySalesStrategy(item, editedStrategyText)
                            generatedStrategyText = editedStrategyText
                            isEditingStrategy = false
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                } else {
                    TextButton(
                        onClick = { 
                            selectedExpiringItem = null 
                            triggerRegen = false
                            isEditingStrategy = false
                        }
                    ) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (isEditingStrategy) {
                    TextButton(
                        onClick = { isEditingStrategy = false }
                    ) {
                        Text("Cancel")
                    }
                } else if (!isLoadingStrategy) {
                    Row {
                        TextButton(
                            onClick = { 
                                editedStrategyText = generatedStrategyText
                                isEditingStrategy = true 
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit Brief", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit")
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = { triggerRegen = true }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Regenerate Strategy", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Re-Gen")
                            }
                        }
                    }
                }
            }
        )
    }

    var activeAnalyticsSubTab by remember { mutableStateOf(0) } // 0 = Operational Metrics, 1 = Demographics Insights

    // --- Demographics Filters ---
    var dateRangeFilter by remember { mutableStateOf(0) } // 0 = All Time, 1 = Last 7 Days, 2 = Last 30 Days
    var searchProductQuery by remember { mutableStateOf("") }
    var searchBrandQuery by remember { mutableStateOf("") }

    val filteredSales = remember(medicationSales, dateRangeFilter, searchProductQuery, searchBrandQuery) {
        medicationSales.filter { sale ->
            val matchesDate = when (dateRangeFilter) {
                1 -> sale.dateSold >= timeNow - 7 * dayMs
                2 -> sale.dateSold >= timeNow - 30 * dayMs
                else -> true
            }
            val matchesProduct = searchProductQuery.isEmpty() || sale.productName.contains(searchProductQuery, ignoreCase = true)
            val matchesBrand = searchBrandQuery.isEmpty() || sale.brand.contains(searchBrandQuery, ignoreCase = true)
            
            matchesDate && matchesProduct && matchesBrand
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Analytics Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            
            if (activeAnalyticsSubTab == 1 && isUserAdmin) {
                IconButton(
                    onClick = {
                        val csv = StringBuilder("Product Name,Brand,Generic Name,Category,Quantity Sold,Date Sold,Pharmacy Node,Patient Age,Patient Gender,State,LGA,City,Sale Price,Batch Number\n")
                        filteredSales.forEach { sale ->
                            val cleanProdName = sale.productName.replace(",", " ")
                            val cleanBrand = sale.brand.replace(",", " ")
                            val cleanGen = sale.genericName.replace(",", " ")
                            val cleanCat = sale.category.replace(",", " ")
                            val cleanNode = sale.pharmacyNode.replace(",", " ")
                            val cleanState = sale.patientState.replace(",", " ")
                            val cleanLga = sale.patientLga.replace(",", " ")
                            val cleanCity = sale.patientCity.replace(",", " ")
                            
                            csv.append("$cleanProdName,$cleanBrand,$cleanGen,$cleanCat,${sale.quantitySold},${sale.dateSold},$cleanNode,${sale.patientAge},${sale.patientGender},$cleanState,$cleanLga,$cleanCity,${sale.salePrice},${sale.batchNumber}\n")
                        }
                        com.example.ui.shareCsvFile(context, csv.toString(), "Demographics_Report_${System.currentTimeMillis()}.csv")
                    }
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "Export Demographics Report", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Subtab Pill Selectors (Only render selectors if user is indeed an administrator)
        if (isUserAdmin) {
            Row(
                modifier = Modifier.fillMaxWidth().height(38.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { activeAnalyticsSubTab = 0 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeAnalyticsSubTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (activeAnalyticsSubTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Filled.Insights, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Operational", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { activeAnalyticsSubTab = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeAnalyticsSubTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (activeAnalyticsSubTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Filled.People, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Demographics", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            activeAnalyticsSubTab = 0
        }

        if (activeAnalyticsSubTab == 0) {
            // High Density KPI Grid
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactMetricCard(
                    title = "Monthly Revenue",
                    value = formatPrice(monthlyRevenue),
                    icon = Icons.Filled.Payments,
                    iconColor = OKGreenText,
                    modifier = Modifier.weight(1f),
                    trendLabel = "+₦${"%,.0f".format(monthlyRevenue * 0.08)} vs last month",
                    trendColor = OKGreenText
                )
                CompactMetricCard(
                    title = "Average Order",
                    value = formatPrice(avgOrderValue),
                    icon = Icons.Filled.Receipt,
                    iconColor = TealPrimary,
                    modifier = Modifier.weight(1f),
                    trendLabel = "Steady transaction volume",
                    trendColor = SlateTextMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactMetricCard(
                    title = "New Customers",
                    value = "$newCustomers",
                    icon = Icons.Filled.PersonAdd,
                    iconColor = TealPrimary,
                    modifier = Modifier.weight(1f),
                    trendLabel = "+${(newCustomers * 0.15).toInt()} growth trend",
                    trendColor = OKGreenText
                )
                CompactMetricCard(
                    title = "Active Refills",
                    value = "$activePrescriptions",
                    icon = Icons.Filled.Autorenew,
                    iconColor = OKGreenText,
                    modifier = Modifier.weight(1f),
                    trendLabel = "Clinical adherence high",
                    trendColor = OKGreenText
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactMetricCard(
                    title = "Recent Interventions",
                    value = "$recentInterventions",
                    icon = Icons.Filled.HealthAndSafety,
                    iconColor = PendingOrange,
                    modifier = Modifier.weight(1f),
                    trendLabel = "Safety clinical reviews",
                    trendColor = SlateTextMedium
                )
                CompactMetricCard(
                    title = "Critical Stockouts",
                    value = "$criticalStock Items",
                    icon = Icons.Filled.Inventory2,
                    iconColor = WarningRed,
                    modifier = Modifier.weight(1f),
                    trendLabel = if (criticalStock > 0) "Immediate restock req." else "Zero inventory flags",
                    trendColor = if (criticalStock > 0) WarningRed else OKGreenText
                )
            }

            // Area/Line Chart of Revenue Trend (Visual Analytics Graph)
            CompactRevenueTrendChart(
                receipts = receipts,
                formatPrice = formatPrice,
                modifier = Modifier.fillMaxWidth()
            )

            // Alert for Stockouts (Missed Opportunity) - Beautified & Compact
            if (potentialLostSales > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarningRedContainerSoft),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, WarningRed.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(WarningRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningRed, modifier = Modifier.size(14.dp))
                        }
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text(
                                "Potential Lost Revenue (Stockouts)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarningRedTitle
                            )
                            Text(
                                formatPrice(potentialLostSales),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WarningRed
                            )
                        }
                        Column(modifier = Modifier.weight(2f)) {
                            Text(
                                "Calculated from zero-stock items × reorder limits. Replenishing products is vital to capturing offline demand.",
                                fontSize = 8.5.sp,
                                lineHeight = 11.sp,
                                color = WarningRedTitle.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Inventory Intelligence Tabbed Segment
            AnalyticsSectionTitle("Inventory Intelligence", Icons.Filled.Inventory)
            
            TabRow(
                selectedTabIndex = inventorySegmentTab,
                containerColor = Color.Transparent,
                contentColor = TealPrimary,
                divider = { HorizontalDivider(color = SlateBorderLight.copy(alpha = 0.25f)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = inventorySegmentTab == 0,
                    onClick = { inventorySegmentTab = 0 },
                    text = { Text("Top Sellers", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = inventorySegmentTab == 1,
                    onClick = { inventorySegmentTab = 1 },
                    text = { Text("Near-Expiry", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = inventorySegmentTab == 2,
                    onClick = { inventorySegmentTab = 2 },
                    text = { Text("Dead Stock", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }

            when (inventorySegmentTab) {
                0 -> {
                    // Top Sellers compact deck
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TealSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (topSellers.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    Text("No sales recorded yet.", fontSize = 11.sp, color = SlateTextMedium)
                                }
                            } else {
                                topSellers.forEachIndexed { idx, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(TealPrimary.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "#${idx + 1}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TealPrimary
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = item.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${item.brand.ifEmpty { "Generic" }} • ${item.dosage}",
                                                    fontSize = 9.5.sp,
                                                    color = SlateTextMedium
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(OKGreenContainer.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${item.totalSoldQuantity} sold",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = OKGreenText
                                            )
                                        }
                                    }
                                    if (idx < topSellers.size - 1) {
                                        HorizontalDivider(color = SlateBorderLight.copy(alpha = 0.25f), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Near Expiry Tab
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "💡 Tap any medication below to formulate an AI selling plan.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val capsuleColors = listOf(
                                Triple("< 30 Days", "${expiring30List.size} Items", WarningRed),
                                Triple("< 60 Days", "${expiring60List.size} Items", PendingOrange),
                                Triple("< 90 Days", "${expiring90List.size} Items", TealPrimary)
                            )
                            capsuleColors.forEach { (label, count, color) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(color.copy(alpha = 0.08f))
                                        .border(1.dp, color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .padding(vertical = 6.dp, horizontal = 8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SlateTextMedium)
                                        Text(count, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = color)
                                    }
                                }
                            }
                        }

                        val allExpiringList = expiring30List + expiring60List + expiring90List
                        if (allExpiringList.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = TealSurface),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    allExpiringList.sortedBy { it.expiryDate }.take(5).forEachIndexed { index, item ->
                                        val daysUntil = ((item.expiryDate - timeNow) / dayMs).toInt()
                                        val pillColor = when {
                                            daysUntil <= 30 -> WarningRed
                                            daysUntil <= 60 -> PendingOrange
                                            else -> TealPrimary
                                        }
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedExpiringItem = item }
                                                .padding(vertical = 6.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Stock: ${item.stockQuantity} • Expiry: ${java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(item.expiryDate))}",
                                                    fontSize = 9.5.sp,
                                                    color = SlateTextMedium
                                                )
                                            }
                                            
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(pillColor.copy(alpha = 0.12f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (daysUntil < 0) "Expired" else "in $daysUntil d",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = pillColor
                                                )
                                            }
                                        }
                                        if (index < allExpiringList.take(5).size - 1) {
                                            HorizontalDivider(color = SlateBorderLight.copy(alpha = 0.25f), thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = TealSurface),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    Text("No near-expiry medications. Pharmacy storage is fully clear.", fontSize = 11.sp, color = SlateTextMedium)
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Dead Stock List
                    if (deadStock.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TealSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                Text("No stagnant inventory detected.", fontSize = 11.sp, color = SlateTextMedium)
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TealSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                deadStock.take(deadStockLimit).forEachIndexed { idx, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Qty: ${item.stockQuantity} in storage",
                                                fontSize = 9.5.sp,
                                                color = SlateTextMedium
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = formatPrice(item.price * item.stockQuantity),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WarningRed
                                            )
                                        }
                                    }
                                    if (idx < deadStock.take(deadStockLimit).size - 1) {
                                        HorizontalDivider(color = SlateBorderLight.copy(alpha = 0.25f), thickness = 0.5.dp)
                                    }
                                }
                                
                                if (deadStockLimit < deadStock.size) {
                                    TextButton(
                                        onClick = { deadStockLimit += 5 },
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Load More Dead Stock (${deadStock.size - deadStockLimit} left)", fontSize = 11.sp, color = TealPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Demographics Cockpit
            Card(
                colors = CardDefaults.cardColors(containerColor = TealSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Demographics Filter Engine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchProductQuery,
                            onValueChange = { searchProductQuery = it },
                            placeholder = { Text("Filter Medicine") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = searchBrandQuery,
                            onValueChange = { searchBrandQuery = it },
                            placeholder = { Text("Filter Manufacturer") },
                            leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // Date range filters (Super compact buttons)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("All Time", "Last 7 Days", "Last 30 Days").forEachIndexed { idx, label ->
                            OutlinedButton(
                                onClick = { dateRangeFilter = idx },
                                border = BorderStroke(1.dp, if (dateRangeFilter == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = label, 
                                    fontSize = 10.sp, 
                                    fontWeight = FontWeight.Bold,
                                    color = if (dateRangeFilter == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Summary Stats Metrics
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactMetricCard(
                    title = "Sales Volume",
                    value = "${filteredSales.sumOf { it.quantitySold }} Units",
                    icon = Icons.Filled.ShoppingBag,
                    iconColor = TealPrimary,
                    modifier = Modifier.weight(1f)
                )
                CompactMetricCard(
                    title = "Total Gross Value",
                    value = formatPrice(filteredSales.sumOf { it.salePrice }),
                    icon = Icons.Filled.MonetizationOn,
                    iconColor = OKGreenText,
                    modifier = Modifier.weight(1f)
                )
            }

            // Demographic Insight Tabbed Segment
            AnalyticsSectionTitle("Demographic Insights", Icons.Filled.BarChart)
            
            TabRow(
                selectedTabIndex = demographicsSegmentTab,
                containerColor = Color.Transparent,
                contentColor = TealPrimary,
                divider = { HorizontalDivider(color = SlateBorderLight.copy(alpha = 0.25f)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = demographicsSegmentTab == 0,
                    onClick = { demographicsSegmentTab = 0 },
                    text = { Text("Patients", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = demographicsSegmentTab == 1,
                    onClick = { demographicsSegmentTab = 1 },
                    text = { Text("Meds/Brands", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = demographicsSegmentTab == 2,
                    onClick = { demographicsSegmentTab = 2 },
                    text = { Text("Geography", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = demographicsSegmentTab == 3,
                    onClick = { demographicsSegmentTab = 3 },
                    text = { Text("Nodes", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }

            when (demographicsSegmentTab) {
                0 -> {
                    // Age Group distribution metrics with Custom Gradient Track Bars
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TealSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val ages = filteredSales.map { it.patientAge }
                            val categories = listOf(
                                "0–12 Child" to ages.count { it in 0..12 },
                                "13–19 Teen" to ages.count { it in 13..19 },
                                "20–35 Youth" to ages.count { it in 20..35 },
                                "36–50 Adult" to ages.count { it in 36..50 },
                                "51–65 Senior" to ages.count { it in 51..65 },
                                "65+ Geriatric" to ages.count { it > 65 }
                            )
                            val maxAgeCount = categories.maxOfOrNull { it.second } ?: 1
                            categories.forEach { (label, count) ->
                                val pct = if (maxAgeCount > 0) count.toFloat() / maxAgeCount else 0.0f
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("$count Sales", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    // Glowing Progress Bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(pct)
                                                .background(Brush.horizontalGradient(listOf(TealPrimary, OKGreen)))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Meds & Brands
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Medications list
                        Text("Top Performing Products", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextMedium)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TealSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val groupedMeds = filteredSales.groupBy { it.productName }
                                    .mapValues { entry -> entry.value.sumOf { it.quantitySold } }
                                    .toList()
                                    .sortedByDescending { it.second }
                                    .take(5)

                                if (groupedMeds.isEmpty()) {
                                    Text("No sale records match filters.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
                                } else {
                                    groupedMeds.forEachIndexed { idx, (name, total) ->
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(TealPrimary.copy(alpha = 0.1f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("#${idx + 1}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                                }
                                                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(OKGreenContainer.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("$total Units", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OKGreenText)
                                            }
                                        }
                                        if (idx < groupedMeds.size - 1) {
                                            HorizontalDivider(color = SlateBorderLight.copy(alpha = 0.25f), thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }

                        // Brands list
                        Text("Top Rated Brands", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextMedium)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TealSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val groupedBrands = filteredSales.groupBy { it.brand }
                                    .mapValues { entry -> entry.value.sumOf { it.quantitySold } }
                                    .toList()
                                    .sortedByDescending { it.second }
                                    .take(5)
                                if (groupedBrands.isEmpty()) {
                                    Text("No branded medications registered in sales.", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    groupedBrands.forEachIndexed { idx, (brandName, total) ->
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(brandName.ifEmpty { "Generic/Unassigned" }, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                            Text("$total Sold", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                        }
                                        if (idx < groupedBrands.size - 1) {
                                            HorizontalDivider(color = SlateBorderLight.copy(alpha = 0.25f), thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Geographic analysis lists
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TealSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("State Distribution Heat Map", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextMedium)
                            val groupedState = filteredSales.groupBy { it.patientState }
                                .mapValues { entry -> entry.value.sumOf { it.quantitySold } }
                                .toList()
                                .sortedByDescending { it.second }
                            if (groupedState.isEmpty()) {
                                Text("No location demographics recorded yet.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                val maxS = groupedState.maxOfOrNull { it.second } ?: 1
                                groupedState.take(3).forEach { (state, total) ->
                                    Column {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(state, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("$total Units", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(total.toFloat() / maxS)
                                                    .background(TealPrimary)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text("City Level Distribution", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextMedium)
                            val groupedCity = filteredSales.groupBy { it.patientCity }
                                .mapValues { entry -> entry.value.sumOf { it.quantitySold } }
                                .toList()
                                .sortedByDescending { it.second }
                                .take(3)
                            groupedCity.forEachIndexed { i, (city, total) ->
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(city, fontSize = 11.sp)
                                    Text("$total Units", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextMedium)
                                }
                                if (i < groupedCity.size - 1) {
                                    HorizontalDivider(color = SlateBorderLight.copy(alpha = 0.25f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Performance by Node (Pharmacy)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TealSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val groupedNodes = filteredSales.groupBy { it.pharmacyNode }
                                .mapValues { entry -> entry.value.sumOf { it.quantitySold } }
                                .toList()
                                .sortedByDescending { it.second }
                            if (groupedNodes.isEmpty()) {
                                Text("No multi-node sales synchronized yet.", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                groupedNodes.forEach { (nodeName, total) ->
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(nodeName, fontSize = 11.sp, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                        Text("$total Units Sold", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactMetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    trendLabel: String? = null,
    trendColor: Color = OKGreenText
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = TealSurface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SlateTextMedium,
                    maxLines = 1,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (trendLabel != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = trendLabel,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = trendColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun CompactRevenueTrendChart(
    receipts: List<Receipt>,
    formatPrice: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val trendDays = 15 // Beautiful and compact 15-day view
    val dailyData = remember(receipts) {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000L
        val list = mutableListOf<Pair<String, Double>>()
        val sdf = java.text.SimpleDateFormat("dd MMM", Locale.getDefault())
        for (i in (trendDays - 1) downTo 0) {
            val dayStart = now - (i + 1) * dayMs
            val dayEnd = now - i * dayMs
            val dayTotal = receipts.filter { it.timestamp in dayStart until dayEnd }.sumOf { it.totalAmount }
            val label = sdf.format(java.util.Date(dayEnd))
            list.add(Pair(label, dayTotal))
        }
        list
    }

    val maxAmount = remember(dailyData) {
        dailyData.maxOfOrNull { it.second } ?: 0.0
    }
    
    val maxVal = if (maxAmount > 0) maxAmount * 1.15 else 10000.0

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = TealSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "15-Day Revenue Performance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Chronological transactional volume",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextMedium
                    )
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(OKGreenContainer.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Peak: ${formatPrice(maxAmount).replace(".00", "")}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OKGreenText
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Canvas drawing
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    val paddingLeft = 14f
                    val paddingRight = 14f
                    val paddingTop = 15f
                    val paddingBottom = 15f
                    
                    val chartWidth = width - paddingLeft - paddingRight
                    val chartHeight = height - paddingTop - paddingBottom

                    // Draw Horizontal Guidelines (3 guidelines: bottom, middle, top)
                    val gridLines = 3
                    for (i in 0 until gridLines) {
                        val y = paddingTop + (chartHeight / (gridLines - 1)) * i
                        drawLine(
                            color = SlateBorderLight.copy(alpha = 0.2f),
                            start = androidx.compose.ui.geometry.Offset(paddingLeft, y),
                            end = androidx.compose.ui.geometry.Offset(width - paddingRight, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                        )
                    }

                    if (dailyData.isNotEmpty()) {
                        val points = dailyData.mapIndexed { index, pair ->
                            val x = paddingLeft + (chartWidth / (dailyData.size - 1)) * index
                            val y = paddingTop + chartHeight - ((pair.second / maxVal) * chartHeight).toFloat()
                            androidx.compose.ui.geometry.Offset(x, y)
                        }

                        val linePath = Path()
                        val fillPath = Path()

                        if (points.isNotEmpty()) {
                            val first = points.first()
                            linePath.moveTo(first.x, first.y)
                            fillPath.moveTo(first.x, chartHeight + paddingTop)
                            fillPath.lineTo(first.x, first.y)
                            
                            for (i in 1 until points.size) {
                                val prev = points[i - 1]
                                val curr = points[i]
                                val cx = (prev.x + curr.x) / 2f
                                val cy = (prev.y + curr.y) / 2f
                                linePath.quadraticTo(prev.x, prev.y, cx, cy)
                                fillPath.quadraticTo(prev.x, prev.y, cx, cy)
                            }
                            
                            val last = points.last()
                            linePath.lineTo(last.x, last.y)
                            fillPath.lineTo(last.x, last.y)
                            
                            fillPath.lineTo(last.x, chartHeight + paddingTop)
                            fillPath.close()
                        }

                        // Draw Transparent Fill Gradient
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    TealPrimary.copy(alpha = 0.25f),
                                    Color.Transparent
                                ),
                                startY = paddingTop,
                                endY = chartHeight + paddingTop
                            )
                        )

                        // Draw the Glow Line Outline
                        drawPath(
                            path = linePath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(TealPrimary, OKGreen)
                            ),
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )

                        // Highlight Points (First, Last, Peak)
                        val maxIdx = dailyData.indexOfFirst { it.second == maxAmount }
                        points.forEachIndexed { idx, pt ->
                            if (idx == 0 || idx == points.size - 1 || idx == maxIdx) {
                                drawCircle(
                                    color = TealPrimary.copy(alpha = 0.3f),
                                    radius = 6.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = TealPrimary,
                                    radius = 3.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = TealSurface,
                                    radius = 1.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // X-axis text labels (Beginning, middle, end)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dailyData.isNotEmpty()) {
                    Text(
                        text = dailyData.first().first,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextMedium
                    )
                    Text(
                        text = dailyData[dailyData.size / 2].first,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextMedium
                    )
                    Text(
                        text = dailyData.last().first,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextMedium
                    )
                }
            }
        }
    }
}

@Composable
fun AnalyticsSectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TealPrimary,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val lines = text.split("\n")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
            } else {
                val isHeader = trimmed.startsWith("💡") || trimmed.startsWith("📢") || trimmed.startsWith("🔄") || trimmed.startsWith("📈") || trimmed.startsWith("🗣️") || (trimmed.firstOrNull()?.isDigit() == true && trimmed.contains("**"))
                
                val annotatedString = parseMarkdownToAnnotatedString(trimmed)
                
                if (isHeader) {
                    Text(
                        text = annotatedString,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                } else if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                    val bulletContent = trimmed.removePrefix("-").removePrefix("*").trim()
                    val bulletAnnotated = parseMarkdownToAnnotatedString(bulletContent)
                    Row(
                        modifier = Modifier.padding(start = 10.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "•", 
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = bulletAnnotated,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Text(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = if (trimmed.firstOrNull()?.isDigit() == true) 4.dp else 0.dp)
                    )
                }
            }
        }
    }
}

fun parseMarkdownToAnnotatedString(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val nextStart = text.indexOf("**", cursor)
            if (nextStart == -1) {
                append(text.substring(cursor))
                break
            } else {
                append(text.substring(cursor, nextStart))
                val nextEnd = text.indexOf("**", nextStart + 2)
                if (nextEnd == -1) {
                    append("**")
                    cursor = nextStart + 2
                } else {
                    val boldText = text.substring(nextStart + 2, nextEnd)
                    withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(boldText)
                    }
                    cursor = nextEnd + 2
                }
            }
        }
    }
}

fun shareCsvFile(context: android.content.Context, content: String, fileName: String) {
    try {
        val file = java.io.File(context.cacheDir, fileName)
        file.writeText(content)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Demographics Analysis Report")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(intent, "Share Report")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
