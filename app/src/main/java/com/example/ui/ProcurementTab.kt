package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val ledgerEntries by viewModel.inventoryLedgerEntries.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentPharmacistRole.collectAsStateWithLifecycle()
    val canonicalCatalog by viewModel.canonicalProductCatalog.collectAsStateWithLifecycle()

    var showCanonicalPickerModal by remember { mutableStateOf(false) }
    var showAddCustomModal by remember { mutableStateOf(false) }

    // Exclude current branch from destination options
    val destinationBranches = remember(branches, currentBranchId) {
        branches.filter { it["id"] as? String != currentBranchId }
    }

    var activeSubTab by remember { mutableStateOf(0) } // 0 = Universal Catalog, 1 = AI Reorder, 2 = Bulk Transfer, 3 = Transfer Ledger

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
            text = "Manage external procurement, canonical universal catalog, and inter-branch stock re-allocation",
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
                Triple("Catalog", Icons.Filled.MenuBook, 0),
                Triple("Reorders", Icons.Filled.ShoppingCart, 1),
                Triple("Bulk Transfer", Icons.Filled.SwapHoriz, 2),
                Triple("Ledger", Icons.Filled.History, 3)
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
                            fontSize = 10.sp,
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
            0 -> UniversalCatalogTabContent(
                inventory = inventory,
                viewModel = viewModel,
                onOpenCatalogPicker = { showCanonicalPickerModal = true },
                onOpenAddCustomModal = { showAddCustomModal = true }
            )
            1 -> ReorderTabContent(
                procurementList = procurementList,
                onExportCsv = { exportProcurementList(context, procurementList) },
                viewModel = viewModel
            )
            2 -> BulkTransferTabContent(
                inventory = inventory,
                destinationBranches = destinationBranches,
                onExecuteTransfer = { transfers, destBranch, reason ->
                    viewModel.performBulkBranchTransfer(transfers, destBranch, reason)
                }
            )
            3 -> TransferLedgerContent(
                transfers = transfersList,
                doubleEntryEntries = ledgerEntries,
                destinationBranches = branches,
                currentBranchName = currentBranchName ?: "Current Branch",
                isUserManager = currentRole == "Branch Manager" || viewModel.isCurrentUserAdmin(),
                onVerifyClick = { logId -> viewModel.verifyStockAdjustment(logId) }
            )
        }
    }

    if (showCanonicalPickerModal) {
        CanonicalProductPickerModal(
            canonicalCatalog = canonicalCatalog,
            branchInventory = inventory,
            onDismiss = { showCanonicalPickerModal = false },
            onImportSelected = { selectedItems ->
                viewModel.addProductsFromCanonicalCatalog(selectedItems) { success, msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    if (success) showCanonicalPickerModal = false
                }
            },
            onOpenAddCustomModal = {
                showCanonicalPickerModal = false
                showAddCustomModal = true
            }
        )
    }

    if (showAddCustomModal) {
        AddCustomCanonicalProductModal(
            onDismiss = { showAddCustomModal = false },
            onAddCustom = { name, dosage, category, unitForm, brand, price, minStock, supplier, initialQty, batch ->
                viewModel.addCustomCanonicalProduct(
                    name = name,
                    dosage = dosage,
                    category = category,
                    unitForm = unitForm,
                    brand = brand,
                    defaultPrice = price,
                    minStockThreshold = minStock,
                    supplier = supplier,
                    initialQty = initialQty,
                    batchNumber = batch
                ) { success, msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    if (success) showAddCustomModal = false
                }
            }
        )
    }
}

// ================= TAB 0: AI REORDER LIST =================
@Composable
fun ReorderTabContent(
    procurementList: List<Pair<InventoryItem, Int>>,
    onExportCsv: () -> Unit,
    viewModel: com.example.ui.PharmacyViewModel
) {
    val rescueListings by viewModel.rescueListings.collectAsStateWithLifecycle()

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
                    val matchingListing = rescueListings.find {
                        it.productName.equals(item.name, ignoreCase = true) &&
                        it.ownerDeviceId != viewModel.deviceId &&
                        it.status == "Available"
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TealSurface),
                        border = BorderStroke(1.dp, if (matchingListing != null) TealPrimary.copy(alpha = 0.8f) else SlateBorderLight),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
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

                            if (matchingListing != null) {
                                val context = LocalContext.current
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(TealPrimary.copy(alpha = 0.08f))
                                        .border(
                                            width = 1.dp,
                                            color = TealPrimary.copy(alpha = 0.3f)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Lightbulb,
                                                contentDescription = null,
                                                tint = TealPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Real-Time Match Alert",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = TealTertiary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Another branch in ${matchingListing.ownerLga}, ${matchingListing.ownerState} listed ${matchingListing.quantity} units of this exact item for ₦${"%,.2f".format(matchingListing.sellingPrice)}!",
                                            fontSize = 10.5.sp,
                                            color = SlateTextMedium,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        Button(
                                            onClick = {
                                                viewModel.acceptRescueListing(matchingListing) { success, msg ->
                                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = TealPrimary,
                                                contentColor = Color.Black
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.AssignmentTurnedIn,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Claim Surplus Handoff", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

                // Search Bar (Using BasicTextField for robust vertical alignment and clipping-free 44dp height styling)
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    ),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(TealPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    color = TealSurface,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = SlateBorderLight.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = SlateTextMedium,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search product name, brand or category...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SlateTextMedium,
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear",
                                        tint = SlateTextMedium,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
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

// ================= TAB 2: TRANSFER LEDGER / DOUBLE-ENTRY INVENTORY LEDGER =================
@Composable
fun TransferLedgerContent(
    transfers: List<Map<String, Any>>,
    doubleEntryEntries: List<com.example.data.InventoryLedgerEntry> = emptyList(),
    destinationBranches: List<Map<String, Any>>,
    currentBranchName: String,
    isUserManager: Boolean = false,
    onVerifyClick: (String) -> Unit = {}
) {
    var ledgerMode by remember { mutableStateOf(0) } // 0 = Double-Entry Ledger, 1 = Audit Trail Timeline
    var filterType by remember { mutableStateOf("ALL") }
    var filterBranchName by remember { mutableStateOf("") }
    var ledgerSearchQuery by remember { mutableStateOf("") }
    var isFilterDropdownExpanded by remember { mutableStateOf(false) }

    // Deduplicate and normalize branch names for managers
    val availableBranches = remember(destinationBranches, transfers) {
        val branchNamesFromTransfers = transfers.mapNotNull { it["branchName"] as? String ?: it["destinationBranch"] as? String }
        val branchNamesFromList = destinationBranches.mapNotNull { it["name"] as? String }
        val allNames = (branchNamesFromList + branchNamesFromTransfers)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        listOf("All Branches") + allNames
    }

    val formattedLedgerEntries = remember(doubleEntryEntries, filterType, ledgerSearchQuery) {
        doubleEntryEntries.filter { entry ->
            val matchesType = when (filterType) {
                "PURCHASE" -> entry.transactionType == "PURCHASE" || entry.transactionType == "INITIAL_ACQUISITION"
                "SALE" -> entry.transactionType == "SALE"
                "BRANCH_TRANSFER" -> entry.transactionType == "BRANCH_TRANSFER"
                "RETURN" -> entry.transactionType == "RETURN"
                "WRITE_OFF" -> entry.transactionType == "WRITE_OFF"
                "RECONCILIATION" -> entry.transactionType.startsWith("RECONCILIATION")
                else -> true
            }
            val query = ledgerSearchQuery.trim().lowercase()
            val matchesQuery = query.isEmpty() ||
                entry.itemName.lowercase().contains(query) ||
                entry.batchNumber.lowercase().contains(query) ||
                entry.debitAccount.lowercase().contains(query) ||
                entry.creditAccount.lowercase().contains(query) ||
                entry.actorName.lowercase().contains(query) ||
                entry.referenceId.lowercase().contains(query)

            matchesType && matchesQuery
        }
    }

    val totalDebitValue = remember(doubleEntryEntries) { doubleEntryEntries.sumOf { it.totalValue } }
    val totalDebitQty = remember(doubleEntryEntries) { doubleEntryEntries.sumOf { it.quantity } }

    val formattedTransfers = remember(transfers, filterBranchName, ledgerSearchQuery, isUserManager, currentBranchName) {
        transfers.filter { log ->
            val bName = (log["branchName"] as? String ?: log["destinationBranch"] as? String ?: "").trim()
            val details = log["details"] as? String ?: ""
            val pharmacist = log["pharmacistName"] as? String ?: ""
            val action = log["action"] as? String ?: ""

            val matchesBranch = if (!isUserManager) {
                bName.isEmpty() || bName.equals(currentBranchName, ignoreCase = true) || details.contains(currentBranchName, ignoreCase = true)
            } else {
                filterBranchName.isBlank() || filterBranchName == "All Branches" || bName.equals(filterBranchName, ignoreCase = true) || details.contains(filterBranchName, ignoreCase = true)
            }

            val query = ledgerSearchQuery.trim()
            val matchesSearch = query.isBlank() || 
                details.contains(query, ignoreCase = true) || 
                pharmacist.contains(query, ignoreCase = true) || 
                action.contains(query, ignoreCase = true)

            matchesBranch && matchesSearch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mode Selector: Double-Entry Ledger vs Audit Trail Logs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .background(SlateBackgroundLight, RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (ledgerMode == 0) TealPrimary else Color.Transparent)
                        .clickable { ledgerMode = 0 }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Double-Entry Ledger",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (ledgerMode == 0) Color.Black else SlateTextMedium
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (ledgerMode == 1) TealPrimary else Color.Transparent)
                        .clickable { ledgerMode = 1 }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Audit Logs",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (ledgerMode == 1) Color.Black else SlateTextMedium
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = "Branch",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = currentBranchName.ifBlank { "Current Branch" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }

        if (ledgerMode == 0) {
            // DOUBLE-ENTRY LEDGER VIEW
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = TealSurface),
                border = BorderStroke(1.dp, SlateBorderLight)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Double-Entry General Ledger", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .background(OKGreenContainer, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OKGreenText, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Dr == Cr Balanced", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OKGreenText)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Transactions", fontSize = 10.sp, color = SlateTextMedium)
                            Text("${doubleEntryEntries.size} Entries", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Total Qty Movement", fontSize = 10.sp, color = SlateTextMedium)
                            Text("$totalDebitQty Units", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Ledger Total Value", fontSize = 10.sp, color = SlateTextMedium)
                            Text("₦${String.format("%,.2f", totalDebitValue)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                        }
                    }
                }
            }

            // Search and Filter Bar
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = TealSurface),
                border = BorderStroke(1.dp, SlateBorderLight),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    OutlinedTextField(
                        value = ledgerSearchQuery,
                        onValueChange = { ledgerSearchQuery = it },
                        placeholder = { Text("Search items, accounts, batches, ref...", fontSize = 12.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = SlateTextMedium) },
                        trailingIcon = {
                            if (ledgerSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { ledgerSearchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = SlateBorderLight,
                            focusedBorderColor = TealPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val types = listOf(
                            "ALL" to "All",
                            "PURCHASE" to "Purchases",
                            "SALE" to "Sales",
                            "BRANCH_TRANSFER" to "Transfers",
                            "RETURN" to "Returns",
                            "WRITE_OFF" to "Write-Offs",
                            "RECONCILIATION" to "Reconciliations"
                        )
                        items(types) { (key, label) ->
                            val isSelected = filterType == key
                            FilterChip(
                                selected = isSelected,
                                onClick = { filterType = key },
                                label = { Text(label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TealPrimary,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
            }

            if (formattedLedgerEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No double-entry ledger transactions recorded yet",
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
                    items(formattedLedgerEntries) { entry ->
                        DoubleEntryCard(entry = entry)
                    }
                }
            }
        } else {
            // AUDIT TRAIL LOGS VIEW
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
                    OutlinedTextField(
                        value = ledgerSearchQuery,
                        onValueChange = { ledgerSearchQuery = it },
                        placeholder = { Text("Search products, actions, staff...", fontSize = 13.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = SlateTextMedium) },
                        trailingIcon = {
                            if (ledgerSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { ledgerSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = if (isUserManager) Modifier.weight(1.2f) else Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = SlateBorderLight,
                            focusedBorderColor = TealPrimary
                        ),
                        singleLine = true
                    )

                    if (isUserManager) {
                        Box(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = { isFilterDropdownExpanded = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (filterBranchName.isNotBlank() && filterBranchName != "All Branches") TealPrimary.copy(alpha = 0.15f) else SlateBackgroundLight,
                                    contentColor = if (filterBranchName.isNotBlank() && filterBranchName != "All Branches") TealPrimary else SlateTextMedium
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
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
                                availableBranches.forEach { branch ->
                                    DropdownMenuItem(
                                        text = { Text(branch, style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            filterBranchName = if (branch == "All Branches") "" else branch
                                            isFilterDropdownExpanded = false
                                        }
                                    )
                                }
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
                            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                                val leftTagColor = when (action) {
                                    "BULK_TRANSFER" -> TealPrimary
                                    "STOCK_ADJUSTMENT" -> Color(0xFF8B5CF6)
                                    else -> OKGreen
                                }
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .fillMaxHeight()
                                        .background(leftTagColor)
                                )

                                Column(modifier = Modifier.padding(10.dp).weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val badgeBg = when (action) {
                                                "BULK_TRANSFER" -> TealPrimary.copy(alpha = 0.15f)
                                                "STOCK_ADJUSTMENT" -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                                else -> OKGreenContainer
                                            }
                                            val badgeColor = when (action) {
                                                "BULK_TRANSFER" -> TealPrimary
                                                "STOCK_ADJUSTMENT" -> Color(0xFF8B5CF6)
                                                else -> OKGreenText
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .background(badgeBg, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = action.replace("_", " "),
                                                    color = badgeColor,
                                                    fontSize = 9.sp,
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

                                    val reason = log["reason"] as? String ?: ""
                                    if (reason.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Justification: \"$reason\"",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = SlateTextMedium,
                                            fontSize = 10.sp
                                        )
                                    }

                                    if (action == "STOCK_ADJUSTMENT") {
                                        val verified = log["verified"] as? Boolean ?: true
                                        val verifiedBy = log["verifiedBy"] as? String ?: ""
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (verified) OKGreenContainer.copy(alpha = 0.5f)
                                                    else WarningRedContainerSoft.copy(alpha = 0.5f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (verified) Icons.Filled.Verified else Icons.Filled.PendingActions,
                                                contentDescription = null,
                                                tint = if (verified) OKGreenText else WarningRed,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = if (verified) "Verified Compliance" else "Awaiting Manager Sign-off",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (verified) OKGreenText else WarningRed,
                                                    fontSize = 11.sp
                                                )
                                                if (verified && verifiedBy.isNotBlank()) {
                                                    Text(
                                                        text = "Signed off by $verifiedBy",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = SlateTextMedium,
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }
                                            
                                            if (!verified && isUserManager) {
                                                Spacer(modifier = Modifier.weight(1f))
                                                Button(
                                                    onClick = { onVerifyClick(log["id"] as? String ?: "") },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = OKGreen,
                                                        contentColor = Color.White
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text("Sign & Verify", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

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
}

@Composable
fun DoubleEntryCard(entry: com.example.data.InventoryLedgerEntry) {
    val dateFormatted = remember(entry.timestamp) {
        if (entry.timestamp > 0) {
            SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(entry.timestamp))
        } else "Unknown Date"
    }

    val (badgeBg, badgeColor, typeLabel) = when (entry.transactionType) {
        "PURCHASE", "INITIAL_ACQUISITION" -> Triple(OKGreenContainer, OKGreenText, "PURCHASE INTAKE")
        "SALE" -> Triple(Color(0xFFE0F2FE), Color(0xFF0284C7), "POS SALE")
        "BRANCH_TRANSFER" -> Triple(Color(0xFFF3E8FF), Color(0xFF9333EA), "BRANCH TRANSFER")
        "RETURN" -> Triple(TealSurface, TealPrimary, "PRODUCT RETURN")
        "WRITE_OFF" -> Triple(WarningRedContainerSoft, WarningRed, "EXPIRY WRITE-OFF")
        "RECONCILIATION_GAIN" -> Triple(OKGreenContainer, OKGreenText, "CYCLE COUNT GAIN")
        "RECONCILIATION_LOSS" -> Triple(WarningRedContainerSoft, WarningRed, "CYCLE COUNT LOSS")
        else -> Triple(SlateBackgroundLight, SlateTextMedium, entry.transactionType)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TealSurface),
        border = BorderStroke(1.dp, SlateBorderLight)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(badgeColor)
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
                                .background(badgeBg, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(typeLabel, color = badgeColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(dateFormatted, fontSize = 10.sp, color = SlateTextMedium)
                    }

                    Text(
                        text = "#LEDGER-${entry.id.toString().padStart(6, '0')}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextMedium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.itemName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entry.batchNumber.isNotBlank()) {
                            Text(
                                text = "Batch: ${entry.batchNumber}",
                                fontSize = 10.sp,
                                color = SlateTextMedium
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Qty: ${entry.quantity} units",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "₦${String.format("%,.2f", entry.totalValue)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TealPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateBackgroundLight, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DEBIT (Dr)", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = OKGreenText)
                        Text(entry.debitAccount, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Transfer",
                        tint = SlateTextMedium,
                        modifier = Modifier.size(14.dp).padding(horizontal = 2.dp)
                    )

                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("CREDIT (Cr)", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = WarningRed)
                        Text(entry.creditAccount, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (entry.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Notes: \"${entry.notes}\"",
                        fontSize = 10.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = SlateTextMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Actor: ${entry.actorName}",
                        fontSize = 9.sp,
                        color = SlateTextMedium
                    )
                    if (entry.referenceId.isNotBlank()) {
                        Text(
                            text = "Ref: ${entry.referenceId}",
                            fontSize = 9.sp,
                            color = SlateTextMedium
                        )
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

// ================= UNIVERSAL CANONICAL CATALOG COMPOSABLES =================

private val WarningRedBorder = Color(0xFFEF5350)
private val WarningRedText = Color(0xFFC62828)
private val WarningRedContainerSoft = Color(0xFFFFEBEE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalCatalogTabContent(
    inventory: List<InventoryItem>,
    viewModel: com.example.ui.PharmacyViewModel,
    onOpenCatalogPicker: () -> Unit,
    onOpenAddCustomModal: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Antimalarial", "Antibiotic", "Antihypertensive", "Antidiabetic", "Analgesic", "Bronchodilator", "Vitamins/Supplements", "Gastrointestinal")

    val filteredInventory = remember(inventory, searchQuery, selectedCategory) {
        inventory.filter { item ->
            val matchesQuery = searchQuery.isBlank() || 
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.dosage.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true) ||
                item.brand.contains(searchQuery, ignoreCase = true)
            
            val matchesCategory = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Universal Catalog Banner Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TealSurface),
            border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(TealPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "Universal Canonical Catalog",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "30+ Standard Pharmaceuticals • Real-time Cloud Sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextMedium,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OKGreenContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Master Active", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OKGreenText)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Select any product from our canonical universal product list to add to your inventory, or manually add missing products which in turn get added to our universal catalog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenCatalogPicker,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 10.dp)
                    ) {
                        Icon(Icons.Filled.LibraryAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select from Universal List", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onOpenAddCustomModal,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TealPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 10.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add Custom Product", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search & Filter Row
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search branch active inventory...", fontSize = 12.sp, color = SlateTextMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = SlateTextMedium, modifier = Modifier.size(18.dp)) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) } }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealPrimary,
                unfocusedBorderColor = SlateBorderLight
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category Filter Pill Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { category ->
                val isSelected = category == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) TealPrimary else SlateBackgroundLight)
                        .border(0.5.dp, if (isSelected) TealPrimary else SlateBorderLight, RoundedCornerShape(16.dp))
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = category,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else SlateTextMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Branch Inventory List Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Branch Active Stock (${filteredInventory.size} items)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Total SKUs: ${inventory.size}",
                fontSize = 11.sp,
                color = SlateTextMedium
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredInventory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Inventory, contentDescription = null, modifier = Modifier.size(48.dp), tint = SlateTextMedium.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No Inventory Items Match", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Click 'Select from Universal List' above to add products to your branch inventory, or add custom products.", fontSize = 12.sp, color = SlateTextMedium, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredInventory) { item ->
                    val isStockLow = item.stockQuantity <= item.minRequiredStock
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, if (isStockLow) WarningRedBorder.copy(alpha = 0.5f) else SlateBorderLight),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Top Row: Title + Manufacturer/Brand Chip
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.name} ${item.dosage}".trim(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                if (item.brand.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = TealSurface,
                                        border = BorderStroke(0.5.dp, TealPrimary.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = item.brand,
                                            fontSize = 9.5.sp,
                                            color = TealTertiary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Subtitle Metadata Row: Category • Unit Form • Batch
                            Text(
                                text = "${item.category} • ${item.unitForm.ifBlank { "Tablet" }} • Batch: ${item.batchNumber.ifBlank { "N/A" }}",
                                fontSize = 11.sp,
                                color = SlateTextMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            HorizontalDivider(thickness = 0.5.dp, color = SlateBorderLight.copy(alpha = 0.5f))

                            // Bottom Row: Price & Min Req on Left | Stock Pill Chip on Right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "₦%,.2f".format(item.price),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TealPrimary
                                    )
                                    Text(
                                        text = "Min Req: ${item.minRequiredStock}",
                                        fontSize = 10.sp,
                                        color = SlateTextMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isStockLow) WarningRedContainerSoft else OKGreenContainer,
                                    border = BorderStroke(0.5.dp, if (isStockLow) WarningRedText.copy(alpha = 0.3f) else OKGreenText.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = if (isStockLow) "Low Stock: ${item.stockQuantity}" else "In Stock: ${item.stockQuantity}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isStockLow) WarningRedText else OKGreenText,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        maxLines = 1,
                                        softWrap = false
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanonicalProductPickerModal(
    canonicalCatalog: List<com.example.data.CanonicalProduct>,
    branchInventory: List<InventoryItem>,
    onDismiss: () -> Unit,
    onImportSelected: (List<Triple<com.example.data.CanonicalProduct, Int, Double>>) -> Unit,
    onOpenAddCustomModal: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val selectedItemsMap = remember { mutableStateMapOf<String, Pair<Int, Double>>() }

    val categories = remember(canonicalCatalog) {
        val list = mutableListOf("All")
        val cats = canonicalCatalog.map { it.category }.distinct().filter { it.isNotBlank() }
        list.addAll(cats)
        list
    }

    val filteredCatalog = remember(canonicalCatalog, searchQuery, selectedCategory) {
        canonicalCatalog.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.dosage.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true) ||
                item.brand.contains(searchQuery, ignoreCase = true)

            val matchesCat = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
            matchesSearch && matchesCat
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = TealPrimary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Universal Canonical Product List", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Select products to add to your branch inventory", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search catalog by name, category...", fontSize = 11.sp, color = SlateTextMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = SlateTextMedium) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) } }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TealPrimary)
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(categories) { cat ->
                        val isSelected = cat == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) TealPrimary else SlateBackgroundLight)
                                .border(0.5.dp, if (isSelected) TealPrimary else SlateBorderLight, RoundedCornerShape(12.dp))
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(cat, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color.Black else SlateTextMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredCatalog.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No products match '$searchQuery'", fontSize = 12.sp, color = SlateTextMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(onClick = onOpenAddCustomModal) {
                                Text("+ Add '$searchQuery' as Custom Product", color = TealPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredCatalog) { product ->
                            val isSelected = selectedItemsMap.containsKey(product.id)
                            val existingBranchItem = branchInventory.find {
                                it.name.equals(product.name, ignoreCase = true) &&
                                it.dosage.equals(product.dosage, ignoreCase = true)
                            }

                            val currentQty = selectedItemsMap[product.id]?.first ?: 20
                            val currentPrice = selectedItemsMap[product.id]?.second ?: product.defaultPrice

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) {
                                            selectedItemsMap.remove(product.id)
                                        } else {
                                            selectedItemsMap[product.id] = Pair(20, product.defaultPrice)
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) TealSurface else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) TealPrimary else SlateBorderLight
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    if (checked) {
                                                        selectedItemsMap[product.id] = Pair(20, product.defaultPrice)
                                                    } else {
                                                        selectedItemsMap.remove(product.id)
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = TealPrimary)
                                            )

                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "${product.name} ${product.dosage}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (product.isCustomAdded) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        ) {
                                                            Text("Custom", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = "${product.category} • ${product.brand} • ₦%,.2f".format(product.defaultPrice),
                                                    fontSize = 10.sp,
                                                    color = SlateTextMedium
                                                )
                                            }
                                        }

                                        if (existingBranchItem != null) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(OKGreenContainer)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("In Stock (${existingBranchItem.stockQuantity})", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = OKGreenText)
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(SlateBackgroundLight)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("Not in Branch", fontSize = 9.sp, color = SlateTextMedium)
                                            }
                                        }
                                    }

                                    if (isSelected) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = currentQty.toString(),
                                                onValueChange = { input ->
                                                    val qty = input.filter { it.isDigit() }.toIntOrNull() ?: 0
                                                    selectedItemsMap[product.id] = Pair(qty, currentPrice)
                                                },
                                                label = { Text("Initial Qty", fontSize = 9.sp) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(6.dp),
                                                singleLine = true
                                            )

                                            OutlinedTextField(
                                                value = currentPrice.toString(),
                                                onValueChange = { input ->
                                                    val price = input.toDoubleOrNull() ?: 0.0
                                                    selectedItemsMap[product.id] = Pair(currentQty, price)
                                                },
                                                label = { Text("Selling Price (₦)", fontSize = 9.sp) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(6.dp),
                                                singleLine = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val itemsToImport = selectedItemsMap.mapNotNull { (prodId, pair) ->
                        val product = canonicalCatalog.find { it.id == prodId } ?: return@mapNotNull null
                        Triple(product, pair.first, pair.second)
                    }
                    onImportSelected(itemsToImport)
                },
                enabled = selectedItemsMap.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Add Selected (${selectedItemsMap.size}) to Inventory", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenAddCustomModal) {
                    Text("+ Add Custom Product", color = TealPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = SlateTextMedium, fontSize = 11.sp)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomCanonicalProductModal(
    onDismiss: () -> Unit,
    onAddCustom: (
        name: String,
        dosage: String,
        category: String,
        unitForm: String,
        brand: String,
        defaultPrice: Double,
        minStockThreshold: Int,
        supplier: String,
        initialQty: Int,
        batchNumber: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Antimalarial") }
    var unitForm by remember { mutableStateOf("Tablet") }
    var brand by remember { mutableStateOf("") }
    var priceStr by remember { mutableStateOf("1500") }
    var minStockStr by remember { mutableStateOf("10") }
    var supplier by remember { mutableStateOf("") }
    var initialQtyStr by remember { mutableStateOf("30") }
    var batchNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.AddBusiness, contentDescription = null, tint = TealPrimary)
                    Text("Add Custom Product to Universal List", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("Item will be added to your inventory AND saved to our master catalog.", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium, fontSize = 11.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product / Drug Name*", fontSize = 11.sp) },
                    placeholder = { Text("e.g. Cataflam, Coartem", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = dosage,
                        onValueChange = { dosage = it },
                        label = { Text("Dosage / Strength*", fontSize = 11.sp) },
                        placeholder = { Text("e.g. 50mg, 100ml", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category*", fontSize = 11.sp) },
                        placeholder = { Text("e.g. Analgesic", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = unitForm,
                        onValueChange = { unitForm = it },
                        label = { Text("Unit Form", fontSize = 11.sp) },
                        placeholder = { Text("Tablet, Syrup...", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Brand / Mfr", fontSize = 11.sp) },
                        placeholder = { Text("e.g. Novartis", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = { Text("Unit Selling Price (₦)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = initialQtyStr,
                        onValueChange = { initialQtyStr = it },
                        label = { Text("Initial Stock Qty", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = minStockStr,
                        onValueChange = { minStockStr = it },
                        label = { Text("Min Stock Threshold", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = batchNumber,
                        onValueChange = { batchNumber = it },
                        label = { Text("Batch Number", fontSize = 11.sp) },
                        placeholder = { Text("Auto-generated if empty", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text("Supplier / Wholesaler", fontSize = 11.sp) },
                    placeholder = { Text("e.g. Fidson, Mega Life Sciences", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceStr.toDoubleOrNull() ?: 0.0
                    val minStock = minStockStr.toIntOrNull() ?: 10
                    val initialQty = initialQtyStr.toIntOrNull() ?: 20
                    onAddCustom(
                        name,
                        dosage,
                        category,
                        unitForm,
                        brand,
                        price,
                        minStock,
                        supplier,
                        initialQty,
                        batchNumber
                    )
                },
                enabled = name.isNotBlank() && dosage.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save to Catalog & Inventory", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SlateTextMedium, fontSize = 11.sp)
            }
        }
    )
}
