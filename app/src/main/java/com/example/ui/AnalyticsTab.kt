package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.TealPrimary
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

    var deadStockLimit by remember { mutableStateOf(10) }
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
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEditingStrategy) "Edit Intelligence Brief" else "Pharmacist Intelligence Brief",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${item.name} (${item.brand.ifEmpty { "Generic" }}) • ${item.dosage}", 
                            style = MaterialTheme.typography.bodyMedium,
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
                        .heightIn(max = 400.dp)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingStrategy) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Formulating the comprehensive Pharmacist Intelligence Brief...",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (isEditingStrategy) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = editedStrategyText,
                                onValueChange = { editedStrategyText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(350.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                label = { Text("Edit Pharmacist Brief & Strategy (Markdown format)") },
                                placeholder = { Text("Enter detailed snapshot, mechanism, counseling scripts, cautions...") }
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
                            Text("Save Brief")
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
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "Edit Manual Brief",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Brief")
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { triggerRegen = true }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Regenerate Strategy",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Regenerate")
                            }
                        }
                    }
                }
            }
        )
    }

    val medicationSales by viewModel.medicationSales.collectAsStateWithLifecycle()

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
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Analytics Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { activeAnalyticsSubTab = 0 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeAnalyticsSubTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (activeAnalyticsSubTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Operational Metrics", fontSize = 13.sp)
                }
                Button(
                    onClick = { activeAnalyticsSubTab = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeAnalyticsSubTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (activeAnalyticsSubTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.People, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Demographics & Node", fontSize = 13.sp)
                }
            }
        } else {
            activeAnalyticsSubTab = 0
        }

        if (activeAnalyticsSubTab == 0) {
            // 1. Financial Overview
            SectionTitle("Financial Overview (Last 30 Days)", Icons.Filled.AttachMoney)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(title = "Total Revenue", value = formatPrice(monthlyRevenue), modifier = Modifier.weight(1f))
                MetricCard(title = "Avg Order", value = formatPrice(avgOrderValue), modifier = Modifier.weight(1f))
            }

            // 2. Customer Acquisition
            SectionTitle("Customer Acquisition", Icons.Filled.People)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(title = "New Customers", value = "$newCustomers", modifier = Modifier.weight(1f))
                MetricCard(title = "Active Prescriptions", value = "$activePrescriptions", modifier = Modifier.weight(1f))
            }

            // 3. Operational & Clinical Impact
            SectionTitle("Clinical Impact", Icons.Filled.LocalHospital)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(title = "Recent Interventions", value = "$recentInterventions", modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
            }

            // 4. Lost Sales & Stockouts
            SectionTitle("Missed Opportunities", Icons.Filled.Warning)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Potential Lost Sales (Stockouts)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(formatPrice(potentialLostSales), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("Calculated from items with 0 stock × min required restock amount.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            // 5. Expiry Warnings
            SectionTitle("Near-Expiry Products", Icons.Filled.DateRange)
            Text(
                text = "💡 Tap any medication below to formulate an AI-powered Clinical Selling Plan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(modifier = Modifier.weight(1f)) { Column(Modifier.padding(8.dp)) { Text("< 30 Days", fontWeight = FontWeight.Bold); Text("${expiring30List.size} Items", color = Color.Red) } }
                OutlinedCard(modifier = Modifier.weight(1f)) { Column(Modifier.padding(8.dp)) { Text("< 60 Days", fontWeight = FontWeight.Bold); Text("${expiring60List.size} Items", color = Color(0xffff8c00)) } }
                OutlinedCard(modifier = Modifier.weight(1f)) { Column(Modifier.padding(8.dp)) { Text("< 90 Days", fontWeight = FontWeight.Bold); Text("${expiring90List.size} Items") } }
            }

            val allExpiringList = expiring30List + expiring60List + expiring90List
            if (allExpiringList.isNotEmpty()) {
                allExpiringList.sortedBy { it.expiryDate }.forEach { item ->
                    val daysUntil = ((item.expiryDate - timeNow) / dayMs).toInt()
                    val color = when {
                        daysUntil <= 30 -> Color.Red
                        daysUntil <= 60 -> Color(0xffff8c00)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    ListItem(
                        modifier = Modifier.clickable { selectedExpiringItem = item },
                        headlineContent = { Text(item.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Stock: ${item.stockQuantity} • Expiry: ${java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(item.expiryDate))}\n👉 Tap to view Pharmacist Intelligence Brief") },
                        trailingContent = { 
                            Text(
                                if (daysUntil < 0) "Expired ${-daysUntil} days ago" else "in $daysUntil days",
                                color = color, 
                                fontWeight = FontWeight.Bold
                            ) 
                        }
                    )
                }
            } else {
                Text("No near-expiry items found.", style = MaterialTheme.typography.bodyMedium)
            }

            // 6. Top Sellers
            SectionTitle("Top Sellers", Icons.Filled.TrendingUp)
            topSellers.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text("${item.category} • ${item.dosage}") },
                    trailingContent = { Text("${item.totalSoldQuantity} Sold", fontWeight = FontWeight.Bold) }
                )
            }

            // 7. Dead Stock
            SectionTitle("Dead Stock (>90 Days No Sale)", Icons.Filled.Inventory)
            if (deadStock.isEmpty()) {
                Text("No dead stock found.", style = MaterialTheme.typography.bodyMedium)
            } else {
                deadStock.take(deadStockLimit).forEach { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = { Text("Stock: ${item.stockQuantity} • Value: ${formatPrice(item.price * item.stockQuantity)}") }
                    )
                }
                if (deadStockLimit < deadStock.size) {
                    TextButton(
                        onClick = { deadStockLimit += 10 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load More")
                    }
                }
            }
        } else {
            // Demographics Cockpit
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Demographics Filter Cockpit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    
                    // Product filter field
                    OutlinedTextField(
                        value = searchProductQuery,
                        onValueChange = { searchProductQuery = it },
                        placeholder = { Text("Filter by Product/Med Name") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Brand filter field
                    OutlinedTextField(
                        value = searchBrandQuery,
                        onValueChange = { searchBrandQuery = it },
                        placeholder = { Text("Filter by Manufacturer/Brand") },
                        leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Date range filters
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("All Time", "Last 7 Days", "Last 30 Days").forEachIndexed { idx, label ->
                            OutlinedButton(
                                onClick = { dateRangeFilter = idx },
                                border = BorderStroke(1.dp, if (dateRangeFilter == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(label, fontSize = 11.sp, color = if (dateRangeFilter == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Summary Stats Metrics
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(title = "Items Monitored", value = "${filteredSales.sumOf { it.quantitySold }}", modifier = Modifier.weight(1f))
                MetricCard(title = "Total Volume", value = formatPrice(filteredSales.sumOf { it.salePrice }), modifier = Modifier.weight(1f))
            }

            // Age Group distribution metrics
            SectionTitle("Medication Usage by Age Group", Icons.Filled.Face)
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ages = filteredSales.map { it.patientAge }
                    val categories = listOf(
                        "0–12" to ages.count { it in 0..12 },
                        "13–19" to ages.count { it in 13..19 },
                        "20–35" to ages.count { it in 20..35 },
                        "36–50" to ages.count { it in 36..50 },
                        "51–65" to ages.count { it in 51..65 },
                        "65+" to ages.count { it > 65 }
                    )
                    val maxAgeCount = categories.maxOfOrNull { it.second } ?: 1
                    categories.forEach { (label, count) ->
                        val pct = if (maxAgeCount > 0) count.toFloat() / maxAgeCount else 0.0f
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("$count Sales", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            // Simple visual progress bar for analytics
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(pct)
                                        .background(TealPrimary)
                                )
                            }
                        }
                    }
                }
            }

            // Most Used Medications Overall
            SectionTitle("Top Medications Overall", Icons.Filled.Leaderboard)
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val groupedMeds = filteredSales.groupBy { it.productName }
                        .mapValues { entry -> entry.value.sumOf { it.quantitySold } }
                        .toList()
                        .sortedByDescending { it.second }
                        .take(5)

                    if (groupedMeds.isEmpty()) {
                        Text("No sale records match filters.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
                    } else {
                        groupedMeds.forEach { (name, total) ->
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text("$total Units", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }

            // Geographic analysis lists
            SectionTitle("Usage Heat lists by Location", Icons.Filled.Map)
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("State Distribution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val groupedState = filteredSales.groupBy { it.patientState }
                        .mapValues { entry -> entry.value.sumOf { it.quantitySold } }
                        .toList()
                        .sortedByDescending { it.second }
                    if (groupedState.isEmpty()) {
                        Text("No location demographics recorded yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        groupedState.forEach { (state, total) ->
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(state, style = MaterialTheme.typography.bodyMedium)
                                Text("$total Units", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("City Distribution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val groupedCity = filteredSales.groupBy { it.patientCity }
                        .mapValues { entry -> entry.value.sumOf { it.quantitySold } }
                        .toList()
                        .sortedByDescending { it.second }
                        .take(5)
                    groupedCity.forEach { (city, total) ->
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(city, style = MaterialTheme.typography.bodyMedium)
                            Text("$total Units", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Node level analysis
            SectionTitle("Performance by Node (Pharmacy)", Icons.Filled.Store)
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val groupedNodes = filteredSales.groupBy { it.pharmacyNode }
                        .mapValues { entry -> entry.value.sumOf { it.quantitySold } }
                        .toList()
                        .sortedByDescending { it.second }
                    if (groupedNodes.isEmpty()) {
                        Text("No multi-node sales synchronized yet.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        groupedNodes.forEach { (nodeName, total) ->
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(nodeName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("$total Units Sold", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }

            // Brand Performance
            SectionTitle("Top Performing Brands", Icons.Filled.Business)
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val groupedBrands = filteredSales.groupBy { it.brand }
                        .mapValues { entry -> entry.value.sumOf { it.quantitySold } }
                        .toList()
                        .sortedByDescending { it.second }
                        .take(5)
                    if (groupedBrands.isEmpty()) {
                        Text("No branded medications registered in sales.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        groupedBrands.forEach { (brandName, total) ->
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(brandName.ifEmpty { "Generic / Unassigned" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("$total Sold", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

    }
}

 

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val lines = text.split("\n")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                } else if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                    val bulletContent = trimmed.removePrefix("-").removePrefix("*").trim()
                    val bulletAnnotated = parseMarkdownToAnnotatedString(bulletContent)
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "•", 
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 8.dp)
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
