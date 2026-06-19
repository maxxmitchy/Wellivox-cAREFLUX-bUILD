package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Customer
import com.example.data.InventoryItem
import com.example.data.RescueListing
import com.example.ui.theme.TealPrimary
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RescueMarketplaceScreen(viewModel: PharmacyViewModel) {
    val listings by viewModel.rescueListings.collectAsStateWithLifecycle()
    val inventory by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedSection by remember { mutableStateOf(0) } // 0 = Deals Feed, 1 = My Rescued stock, 2 = List Near-expiry
    
    // Posting form states
    var selectedItemForPost by remember { mutableStateOf<InventoryItem?>(null) }
    var postQtyString by remember { mutableStateOf("") }
    var rescuePriceString by remember { mutableStateOf("") }
    var commissionString by remember { mutableStateOf("15.0") }
    var durationDaysString by remember { mutableStateOf("45") }
    var isPostingDropdownExpanded by remember { mutableStateOf(false) }

    // Selling / checkout dialog state
    var listingToSell by remember { mutableStateOf<RescueListing?>(null) }
    var quantityToSellString by remember { mutableStateOf("") }
    var selectedCustomerForRescueSale by remember { mutableStateOf<Customer?>(null) }
    var isCustomerDropdownExpanded by remember { mutableStateOf(false) }

    val currentDeviceId = viewModel.deviceId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header banner
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TealPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = Color.Black
                    )
                }
                Column {
                    Text(
                        "Expiry Rescue Marketplace",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Cooperatively share, rescue, and liquidate near-expiry inventory across nodes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Subtabs selection
        TabRow(selectedTabIndex = selectedSection) {
            Tab(
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 },
                text = { Text("Community Deals", fontSize = 12.sp) },
                icon = { Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = selectedSection == 1,
                onClick = { selectedSection = 1 },
                text = { Text("My Rescued", fontSize = 12.sp) },
                icon = { Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = selectedSection == 2,
                onClick = { selectedSection = 2 },
                text = { Text("Post Expiry", fontSize = 12.sp) },
                icon = { Icon(Icons.Filled.PostAdd, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        when (selectedSection) {
            0 -> {
                // Browse feed (excluding own list items)
                val feedDeals = listings.filter { 
                    it.ownerDeviceId != currentDeviceId && it.status == "Available"
                }

                Text(
                    text = "Browse Community Deals (${feedDeals.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (feedDeals.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Inbox, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No expiry rescue deals offered by other pharmacies yet.")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(feedDeals) { deal ->
                            Card(
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column {
                                            Text(deal.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("Batch #: ${deal.batchNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(
                                            formatRescuePrice(deal.sellingPrice),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = TealPrimary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Stock Qty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${deal.quantity} Units", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Earn Commission", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${deal.commissionPercentage}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TealPrimary)
                                        }
                                        Column(modifier = Modifier.weight(1.5f)) {
                                            Text("Expires on", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            val expStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(deal.expiryDate))
                                            Text(expStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Owner: ${deal.ownerDeviceModel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Button(
                                            onClick = {
                                                viewModel.acceptRescueListing(deal)
                                                Toast.makeText(context, "rescue listing claimed! shifted to stock", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Rescue Deal",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // listings accepted by this device that are not fully sold
                val rescuedItems = listings.filter { 
                    it.acceptedByDeviceId == currentDeviceId && it.status == "Accepted" && it.quantity > 0
                }

                Text(
                    text = "My Rescued Community Stock (${rescuedItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (rescuedItems.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(48.dp), tint = TealPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No rescued items in custody. Grab deals on Feed!")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(rescuedItems) { item ->
                            Card(
                                border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(item.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                                .padding(6.dp)
                                        ) {
                                            Text("Custody Qty: ${item.quantity}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(6.dp)
                                        ) {
                                            Text("Retail: ${formatRescuePrice(item.sellingPrice)}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(6.dp)
                                        ) {
                                            Text("Node Comm: ${item.commissionPercentage}%", style = MaterialTheme.typography.bodySmall, color = TealPrimary)
                                        }
                                    }

                                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Source: ${item.ownerDeviceModel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Button(
                                            onClick = {
                                                listingToSell = item
                                                quantityToSellString = "1"
                                                selectedCustomerForRescueSale = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Sell Rescued Stock",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // POST expiry
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Offer Near-Expiry Medication",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Selection fields
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isPostingDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedItemForPost?.let { "${it.name} (Stock: ${it.stockQuantity})" } ?: "Select Expiring Medication from Stock")
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = isPostingDropdownExpanded,
                            onDismissRequest = { isPostingDropdownExpanded = false }
                        ) {
                            inventory.filter { it.stockQuantity > 0 }.forEach { invItem ->
                                DropdownMenuItem(
                                    text = { Text("${invItem.name} — Qty: ${invItem.stockQuantity} (${invItem.brand})") },
                                    onClick = {
                                        selectedItemForPost = invItem
                                        isPostingDropdownExpanded = false
                                        postQtyString = invItem.stockQuantity.toString()
                                        rescuePriceString = (invItem.price * 0.7).toString() // Pre-fill with a 30% discount suggestion
                                    }
                                )
                            }
                        }
                    }

                    if (selectedItemForPost != null) {
                        val maxQty = selectedItemForPost!!.stockQuantity
                        
                        OutlinedTextField(
                            value = postQtyString,
                            onValueChange = { postQtyString = it },
                            label = { Text("Quantity for Rescue (Max: $maxQty)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = rescuePriceString,
                            onValueChange = { rescuePriceString = it },
                            label = { Text("Liquidation Offer Price (Suggested Discount)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = commissionString,
                            onValueChange = { commissionString = it },
                            label = { Text("Rescuer Node Commission Percentage (%)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = durationDaysString,
                            onValueChange = { durationDaysString = it },
                            label = { Text("Marketplace Listing Duration (Days)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                val qtyVal = postQtyString.toIntOrNull() ?: 0
                                val priceVal = rescuePriceString.toDoubleOrNull() ?: 0.0
                                val commVal = commissionString.toDoubleOrNull() ?: 15.0
                                val daysVal = durationDaysString.toIntOrNull() ?: 30

                                if (qtyVal <= 0 || qtyVal > maxQty) {
                                    Toast.makeText(context, "Invalid Quantity offered.", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                if (priceVal <= 0.0) {
                                    Toast.makeText(context, "Invalid Liquidation Price.", Toast.LENGTH_LONG).show()
                                    return@Button
                                }

                                // Check for active duplicates listed by this node
                                val isDuplicate = listings.any {
                                    it.productName.equals(selectedItemForPost!!.name, ignoreCase = true) &&
                                    it.batchNumber.equals(selectedItemForPost!!.batchNumber, ignoreCase = true) &&
                                    it.ownerDeviceId == viewModel.deviceId &&
                                    (it.status == "Available" || it.status == "Accepted")
                                }
                                if (isDuplicate) {
                                    Toast.makeText(context, "Error: This specific product batch is already actively listed in the cooperative pool!", Toast.LENGTH_LONG).show()
                                    return@Button
                                }

                                viewModel.createRescueListing(
                                    inv = selectedItemForPost!!,
                                    qty = qtyVal,
                                    price = priceVal,
                                    commPercentage = commVal,
                                    durationDays = daysVal
                                )

                                Toast.makeText(context, "Successfully listed to Cooperative feed!", Toast.LENGTH_LONG).show()
                                selectedItemForPost = null
                                selectedSection = 0 // Switch to deals feed
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Advertise In Cooperative Pool")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))

                    val myOffers = listings.filter { it.ownerDeviceId == currentDeviceId }
                    Text(
                        text = "My Posted Offers (${myOffers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (myOffers.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "You haven't listed any near-expiry medications yet. Select a product from stock above to list it.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        myOffers.forEach { offer ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(offer.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("Batch: ${offer.batchNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        
                                        // Status sticker/badge
                                        val (statusColor, containerColor) = when (offer.status) {
                                            "Available" -> TealPrimary to TealPrimary.copy(alpha = 0.15f)
                                            "Accepted" -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                            else -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(containerColor, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = offer.status,
                                                fontSize = 11.sp,
                                                color = statusColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Column {
                                            Text("Remaining Qty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${offer.quantity} Units", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                        Column {
                                            Text("Liquidation Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(formatRescuePrice(offer.sellingPrice), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                        Column {
                                            Text("Commission Offered", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${offer.commissionPercentage}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    if (offer.status == "Accepted") {
                                        Text(
                                            text = "Custody held by reseller node: ${offer.acceptedByDeviceModel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (offer.status == "Available" || offer.status == "Accepted") {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.retractRescueListing(offer)
                                                Toast.makeText(context, "Listing for ${offer.productName} retracted successfully!", Toast.LENGTH_LONG).show()
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Retract & Remove Listing", fontSize = 11.sp)
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

    // Checkout / Sell rescued stock dialog
    if (listingToSell != null) {
        val activeListing = listingToSell!!
        val maxAvailable = activeListing.quantity

        AlertDialog(
            onDismissRequest = { listingToSell = null },
            title = { Text("Sell Rescued Medication", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Liquidation Product: ${activeListing.productName}")
                    Text("Available custody: $maxAvailable units")

                    OutlinedTextField(
                        value = quantityToSellString,
                        onValueChange = { quantityToSellString = it },
                        label = { Text("Quantity Sold") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Customer demographic mapping dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isCustomerDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedCustomerForRescueSale?.name ?: "Assign Customer (For Demographics)")
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = isCustomerDropdownExpanded,
                            onDismissRequest = { isCustomerDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Walk-in Guest Patient") },
                                onClick = {
                                    selectedCustomerForRescueSale = null
                                    isCustomerDropdownExpanded = false
                                }
                            )
                            customers.forEach { cust ->
                                DropdownMenuItem(
                                    text = { Text("${cust.name} — ${cust.gender} (${cust.age} yrs)") },
                                    onClick = {
                                        selectedCustomerForRescueSale = cust
                                        isCustomerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Profit Splits Details
                    val qtyInput = quantityToSellString.toIntOrNull() ?: 1
                    val actualQty = qtyInput.coerceIn(1, maxAvailable)
                    val rev = activeListing.sellingPrice * actualQty
                    val resCo = rev * (activeListing.commissionPercentage / 100.0)
                    val ownRe = rev - resCo

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Total Revenue: ${formatRescuePrice(rev)}", fontWeight = FontWeight.Bold)
                            Text("Rescuer Profit share (keeps): ${formatRescuePrice(resCo)} (${activeListing.commissionPercentage}%)")
                            Text("Owner Payback (remits): ${formatRescuePrice(ownRe)}")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qtySold = quantityToSellString.toIntOrNull() ?: 0
                        if (qtySold <= 0 || qtySold > maxAvailable) {
                            Toast.makeText(context, "Invalid quantity value input.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        viewModel.sellRescueListing(
                            listing = activeListing,
                            qtyToSell = qtySold,
                            customer = selectedCustomerForRescueSale
                        )
                        Toast.makeText(context, "Liquidated transaction logged!", Toast.LENGTH_SHORT).show()
                        listingToSell = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black)
                ) {
                    Text("Complete Rescue Sale")
                }
            },
            dismissButton = {
                TextButton(onClick = { listingToSell = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatRescuePrice(price: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "NG"))
    format.maximumFractionDigits = 0
    return format.format(price).replace("NGN", "₦").replace("NG", "₦").replace("¤", "₦")
}
