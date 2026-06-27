package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.InventoryItem
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcurementTabContent(
    inventory: List<InventoryItem>,
    viewModel: com.example.ui.PharmacyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Retrieve state from the view model
    val branches by viewModel.allBranches.collectAsStateWithLifecycle()
    val currentBranchId by viewModel.currentPharmacistBranchId.collectAsStateWithLifecycle()
    val currentBranchName by viewModel.currentPharmacistBranchName.collectAsStateWithLifecycle()
    val transfersList by viewModel.branchTransfers.collectAsStateWithLifecycle()

    // Exclude current branch from destination options
    val destinationBranches = remember(branches, currentBranchId) {
        branches.filter { it["id"] as? String != currentBranchId }
    }

    var activeSubTab by remember { mutableStateOf(0) } // 0 = AI Reorder, 1 = Bulk Transfer, 2 = Transfer Ledger

    // 1. AI Intelligent Reorder List Logic
    val procurementList = remember(inventory) {
        inventory.filter { it.stockQuantity <= it.minRequiredStock }.map { item ->
            val suggestedAmount = if (item.minRequiredStock > 0) {
                ((item.minRequiredStock * 3) - item.stockQuantity).coerceAtLeast(item.minRequiredStock)
            } else {
                10 // default suggestion if minRequired is 0
            }
            item to suggestedAmount
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(vertical = 4.dp)) {
        // Supply Chain Control Center Header
        Text(
            text = "Supply Chain & Logistics",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Manage external procurement and inter-branch stock re-allocation",
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Tab Selector Row (SaaS Dashboard Styled Pill Row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateBackgroundLight, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf(
                Triple("Reorders", Icons.Filled.ShoppingCart, 0),
                Triple("Bulk Transfer", Icons.Filled.SwapHoriz, 1),
                Triple("Ledger", Icons.Filled.History, 2)
            )

            tabs.forEach { (title, icon, index) ->
                val isSelected = activeSubTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) TealPrimary else Color.Transparent
                        )
                        .clickable { activeSubTab = index }
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isSelected) Color.Black else SlateTextMedium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else SlateTextMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Render Active Tab Content
        when (activeSubTab) {
            0 -> ReorderTabContent(
                procurementList = procurementList,
                onExportCsv = { exportProcurementList(context, procurementList) }
            )
            1 -> BulkTransferTabContent(
                inventory = inventory,
                destinationBranches = destinationBranches,
                onExecuteTransfer = { transfers, destBranch, reason ->
                    viewModel.performBulkBranchTransfer(transfers, destBranch, reason)
                }
            )
            2 -> TransferLedgerContent(
                transfers = transfersList,
                destinationBranches = branches,
                currentBranchName = currentBranchName ?: "Current Branch"
            )
        }
    }
}

// ================= TAB 0: AI REORDER LIST =================
@Composable
fun ReorderTabContent(
    procurementList: List<Pair<InventoryItem, Int>>,
    onExportCsv: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI Suggested Procurement Restocks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${procurementList.size} medical items requiring attention",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMedium
                )
            }
            
            if (procurementList.isNotEmpty()) {
                Button(
                    onClick = onExportCsv,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TealPrimary.copy(alpha = 0.15f),
                        contentColor = TealPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        if (procurementList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = OKGreen.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "All Inventories Fully Stocked",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All medical items remain comfortably above their minimum required stock levels. No procurement restocks suggested.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateTextMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(procurementList) { (item, suggestedAmount) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TealSurface),
                        border = BorderStroke(1.dp, SlateBorderLight),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                WarningRedContainerSoft,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Low Stock",
                                            color = WarningRed,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Dosage: ${item.dosage} | Form: ${item.unitForm}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextMedium
                                )
                                Text(
                                    text = "Stock: ${item.stockQuantity} (Min: ${item.minRequiredStock})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (item.brand.isNotBlank()) {
                                    Text(
                                        text = "Brand: ${item.brand}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TealPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            
                            // High density suggested restock badge
                            Column(
                                horizontalAlignment = Alignment.End, 
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Text(
                                    text = "Suggested Order", 
                                    style = MaterialTheme.typography.labelSmall, 
                                    color = SlateTextMedium,
                                    fontSize = 9.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .background(WarningRedContainerSoft, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+$suggestedAmount",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Black,
                                        color = WarningRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= TAB 1: BULK INTER-BRANCH TRANSFER =================
@Composable
fun BulkTransferTabContent(
    inventory: List<InventoryItem>,
    destinationBranches: List<Map<String, Any>>,
    onExecuteTransfer: (List<Pair<InventoryItem, Int>>, String, String) -> Unit
) {
    var selectedBranchId by remember { mutableStateOf("") }
    var selectedBranchName by remember { mutableStateOf("") }
    var transferReason by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Multi-item transfer state: Map of Item ID to Pair(Item, Quantity)
    val selectedTransfers = remember { mutableStateMapOf<Int, Pair<InventoryItem, Int>>() }

    // Quick select reasons
    val quickReasons = listOf(
        "Expiring Stock Redistribution",
        "Overstock Rebalancing",
        "Branch Emergency Request",
        "Inventory Restocking"
    )

    // Filtered inventory based on search query and availability of stock
    val filteredInventory = remember(inventory, searchQuery) {
        inventory.filter { item ->
            item.stockQuantity > 0 &&
            (item.name.contains(searchQuery, ignoreCase = true) ||
             item.brand.contains(searchQuery, ignoreCase = true) ||
             item.dosage.contains(searchQuery, ignoreCase = true))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Step 1: Destination and Reason Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TealSurface),
                border = BorderStroke(1.dp, SlateBorderLight),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "1. Configuration Details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Destination Branch Dropdown Selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDropdownExpanded = !isDropdownExpanded },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SlateBorderLight)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Destination Branch",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SlateTextMedium,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = if (selectedBranchName.isBlank()) "Select Destination Branch..." else selectedBranchName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selectedBranchName.isBlank()) FontWeight.Normal else FontWeight.Bold,
                                        color = if (selectedBranchName.isBlank()) SlateTextMedium else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = if (isDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = SlateTextMedium
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            if (destinationBranches.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No other branches registered", style = MaterialTheme.typography.bodyMedium) },
                                    onClick = { isDropdownExpanded = false }
                                )
                            } else {
                                destinationBranches.forEach { branch ->
                                    val bId = branch["id"] as? String ?: ""
                                    val bName = branch["name"] as? String ?: "Unnamed Branch"
                                    val bLoc = branch["location"] as? String ?: ""
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(bName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                if (bLoc.isNotBlank()) {
                                                    Text(bLoc, style = MaterialTheme.typography.labelSmall, color = SlateTextMedium)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedBranchId = bId
                                            selectedBranchName = bName
                                            isDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Reason Field
                    OutlinedTextField(
                        value = transferReason,
                        onValueChange = { transferReason = it },
                        label = { Text("Transfer Authorization Reason", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Redistribution of stock", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = SlateBorderLight,
                            focusedBorderColor = TealPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick select chips horizontally (Corrected Scrollable LazyRow)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quick:",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextMedium,
                            fontSize = 11.sp
                        )
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(quickReasons) { reason ->
                                val isSelectedReason = transferReason == reason
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelectedReason) TealPrimary.copy(alpha = 0.2f) else SlateBackgroundLight,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { transferReason = reason }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = reason,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelectedReason) TealPrimary else SlateTextMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Step 2: Item Selection and Quantity Configuration
        item {
            Column {
                Text(
                    text = "2. Select Inventory to Transfer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Select products and designate exact quantities to transfer",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMedium
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search product name, brand or category...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = SlateBorderLight,
                        focusedBorderColor = TealPrimary
                    )
                )
            }
        }

        if (filteredInventory.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No stock available matching query",
                        color = SlateTextMedium,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(filteredInventory) { item ->
                val isSelected = selectedTransfers.containsKey(item.id)
                val activeQty = selectedTransfers[item.id]?.second ?: 1

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSelected) {
                                selectedTransfers.remove(item.id)
                            } else {
                                selectedTransfers[item.id] = item to 1
                            }
                        },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) TealPrimary.copy(alpha = 0.08f) else TealSurface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) TealPrimary else SlateBorderLight
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    selectedTransfers[item.id] = item to activeQty
                                } else {
                                    selectedTransfers.remove(item.id)
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = TealPrimary)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Dosage: ${item.dosage} | Brand: ${if (item.brand.isBlank()) "Generic" else item.brand}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextMedium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Batch: ${item.batchNumber}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TealPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Stock: ${item.stockQuantity}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.stockQuantity <= item.minRequiredStock) WarningRed else OKGreenText
                                )
                            }
                        }

                        if (isSelected) {
                            // High density counter buttons
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (activeQty > 1) {
                                            selectedTransfers[item.id] = item to (activeQty - 1)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(SlateBackgroundLight, CircleShape)
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
                                }

                                Text(
                                    text = "$activeQty",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                IconButton(
                                    onClick = {
                                        if (activeQty < item.stockQuantity) {
                                            selectedTransfers[item.id] = item to (activeQty + 1)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(SlateBackgroundLight, CircleShape)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Sticky Footer Execution Card
    if (selectedTransfers.isNotEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(4.dp),
                shape = RoundedCornerShape(12.dp),
                color = TealSurface,
                border = BorderStroke(1.dp, SlateBorderLight),
                tonalElevation = 6.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedTransfers.size} items selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "To: ${if (selectedBranchName.isBlank()) "Select branch above" else selectedBranchName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            val list = selectedTransfers.values.toList()
                            onExecuteTransfer(list, selectedBranchName, transferReason)
                            selectedTransfers.clear()
                            transferReason = ""
                            selectedBranchId = ""
                            selectedBranchName = ""
                        },
                        enabled = selectedBranchName.isNotBlank() && transferReason.isNotBlank() && selectedTransfers.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Stock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ================= TAB 2: TRANSFER LEDGER / TIMELINE =================
@Composable
fun TransferLedgerContent(
    transfers: List<Map<String, Any>>,
    destinationBranches: List<Map<String, Any>>,
    currentBranchName: String
) {
    var filterBranchName by remember { mutableStateOf("") }
    var ledgerSearchQuery by remember { mutableStateOf("") }
    var isFilterDropdownExpanded by remember { mutableStateOf(false) }

    val formattedTransfers = remember(transfers, filterBranchName, ledgerSearchQuery) {
        transfers.filter { log ->
            val details = log["details"] as? String ?: ""
            val matchesSearch = ledgerSearchQuery.isBlank() || details.contains(ledgerSearchQuery, ignoreCase = true)
            val matchesBranch = filterBranchName.isBlank() || details.contains(filterBranchName, ignoreCase = true)
            matchesSearch && matchesBranch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Historical Transfers Ledger",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Immutable real-time audit logs of stock reallocations",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filters Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TealSurface),
            border = BorderStroke(1.dp, SlateBorderLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search field
                OutlinedTextField(
                    value = ledgerSearchQuery,
                    onValueChange = { ledgerSearchQuery = it },
                    placeholder = { Text("Search products...", fontSize = 13.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = SlateTextMedium) },
                    modifier = Modifier.weight(1.2f).height(54.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = SlateBorderLight,
                        focusedBorderColor = TealPrimary
                    ),
                    singleLine = true
                )

                // Branch filter button
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { isFilterDropdownExpanded = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (filterBranchName.isNotBlank()) TealPrimary.copy(alpha = 0.15f) else SlateBackgroundLight,
                            contentColor = if (filterBranchName.isNotBlank()) TealPrimary else SlateTextMedium
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (filterBranchName.isBlank()) "All Branches" else filterBranchName,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(12.dp))
                        }
                    }

                    DropdownMenu(
                        expanded = isFilterDropdownExpanded,
                        onDismissRequest = { isFilterDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Branches", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                filterBranchName = ""
                                isFilterDropdownExpanded = false
                            }
                        )
                        destinationBranches.forEach { branch ->
                            val name = branch["name"] as? String ?: ""
                            DropdownMenuItem(
                                text = { Text(name, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    filterBranchName = name
                                    isFilterDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (formattedTransfers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No transfers recorded matching filters",
                    color = SlateTextMedium,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(formattedTransfers) { log ->
                    val timestamp = (log["timestamp"] as? Number)?.toLong() ?: 0L
                    val details = log["details"] as? String ?: ""
                    val action = log["action"] as? String ?: "TRANSFER"
                    val userName = log["displayName"] as? String ?: "Authorized Staff"
                    val userRole = log["role"] as? String ?: "Pharmacist"
                    
                    val dateFormatted = remember(timestamp) {
                        if (timestamp > 0) {
                            SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(timestamp))
                        } else {
                            "Unknown Date"
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = TealSurface),
                        border = BorderStroke(1.dp, SlateBorderLight)
                    ) {
                        // Horizontal layout to accommodate a beautiful left vertical timeline tag
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                            // Visual Left timeline accent tag
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .fillMaxHeight()
                                    .background(if (action == "BULK_TRANSFER") TealPrimary else OKGreen)
                            )

                            Column(modifier = Modifier.padding(10.dp).weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (action == "BULK_TRANSFER") TealPrimary.copy(alpha = 0.15f) else OKGreenContainer,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = action,
                                                color = if (action == "BULK_TRANSFER") TealPrimary else OKGreenText,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = dateFormatted,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SlateTextMedium,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = details,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = SlateBorderLight.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AccountCircle,
                                            contentDescription = null,
                                            tint = TealPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "$userName ($userRole)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SlateTextMedium,
                                            fontSize = 9.sp
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = currentBranchName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SlateTextMedium,
                                            fontSize = 9.sp
                                        )
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

private fun exportProcurementList(context: Context, list: List<Pair<InventoryItem, Int>>) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val fileName = "Procurement_List_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)
            
            file.bufferedWriter().use { out ->
                out.write("Item Name,Brand,Category,Current Stock,Suggested Order Qty\n")
                list.forEach { (item, qty) ->
                    val safeName = item.name.replace(",", " ")
                    val safeBrand = item.brand.replace(",", " ")
                    val safeCategory = item.category.replace(",", " ")
                    out.write("$safeName,$safeBrand,$safeCategory,${item.stockQuantity},$qty\n")
                }
            }
            
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Procurement List")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(intent, "Share Procurement List")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Export error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
