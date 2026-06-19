package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
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
    val medicationSales by viewModel.medicationSales.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedSection by remember { mutableStateOf(0) } // 0 = Deals Feed, 1 = My Rescued stock, 2 = List Near-expiry
    var simulatedTimelineHour by remember { mutableStateOf(48) } // 0, 24, or 48. Default is 48 is open public pool
    var isSimControlExpanded by remember { mutableStateOf(false) } // Collapsible for maximizing deals scroll area
    
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
        // Hero Header banner styled with stunning modern graphic gradient
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                TealPrimary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(TealPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storefront,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            "Cooperative Rescue Marketplace",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Share, rescue, and cooperatively liquidate near-expiry stocks across clinical node pools with zero waste.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Custom Pill Tabs Segmented Layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                Triple("Community Feed", Icons.Filled.Group, 0),
                Triple("My Rescued Stock", Icons.Filled.VerifiedUser, 1),
                Triple("Post Expiry Post", Icons.Filled.PostAdd, 2)
            ).forEach { (title, icon, index) ->
                val isSelected = selectedSection == index
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) TealPrimary else Color.Transparent)
                        .clickable { selectedSection = index }
                        .padding(vertical = 11.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        when (selectedSection) {
            0 -> {
                // Browse feed (excluding own list items)
                val feedDeals = listings.filter { 
                    it.ownerDeviceId != currentDeviceId && it.status == "Available"
                }

                val currentLga = viewModel.getPharmacyLga()
                val currentState = viewModel.getPharmacyState()

                // Calculate escalation tier of each deal relative to current node
                fun getDealTierForCurrentNode(deal: RescueListing): Int {
                    var score = 0
                    if (deal.ownerLga.isBlank() || deal.ownerLga.equals(currentLga, ignoreCase = true)) {
                        score += 40
                    } else if (deal.ownerState.isBlank() || deal.ownerState.equals(currentState, ignoreCase = true)) {
                        score += 15
                    }
                    
                    val salesQty = medicationSales.filter { it.productName.equals(deal.productName, ignoreCase = true) }
                        .sumOf { it.quantitySold }
                    if (salesQty > 0) {
                        score += 30
                    }

                    return when {
                        score >= 60 -> 1 // High Priority Match: Targeted immediately
                        score >= 35 -> 2 // Regional Escalation: After 24 hours of list
                        else -> 3        // Open Cooperative Pool: After 48 hours
                    }
                }

                // Filter based on the selected passage of simulated time (Stepwise Escalation)
                val visibleFeedDeals = feedDeals.filter { deal ->
                    val tier = getDealTierForCurrentNode(deal)
                    when (tier) {
                        1 -> true // Always visible (immediate)
                        2 -> simulatedTimelineHour >= 24 // Escales after 24 hours
                        else -> simulatedTimelineHour >= 48 // Open general pool after 48 hours
                    }
                }

                // Match Simulator Controls Layout - Designed as a sleek, expandable card component to minimize screen usage
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSimControlExpanded = !isSimControlExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.HistoryToggleOff, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(
                                        "Logistics Match Simulator",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimary
                                    )
                                    if (!isSimControlExpanded) {
                                        Text(
                                            text = "Security Ring Routing • Hr $simulatedTimelineHour Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isSimControlExpanded) {
                                    // Header pill triggers for simulation hours when collapsed for high-efficiency space saving
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(0, 24, 48).forEach { hour ->
                                            val isSelected = simulatedTimelineHour == hour
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) TealPrimary 
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                    .clickable { simulatedTimelineHour = hour }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "Hr $hour", 
                                                    fontSize = 9.sp, 
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Icon(
                                    imageVector = if (isSimControlExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = "Toggle simulator details",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        if (isSimControlExpanded) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Step-wise Routing: to avoid transit friction, near-expiry stock is routed first to proximate, high-capacity nodes. Below, simulate time passing (Hour 0 to 48) to trace listings trickling down the security ring.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Simulation Focus:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(0, 24, 48).forEach { hour ->
                                        val isSelected = simulatedTimelineHour == hour
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) TealPrimary 
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                )
                                                .clickable { simulatedTimelineHour = hour }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "Hr $hour", 
                                                fontSize = 11.sp, 
                                                fontWeight = FontWeight.Black,
                                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(12.dp), tint = TealPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Your Profile: $currentLga LGA, $currentState State",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.seedSimulationNodesAndSales() },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Populate Live Seed List", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Browse Community Deals",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "${visibleFeedDeals.size} of ${feedDeals.size} visible",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (visibleFeedDeals.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Inbox, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Text(
                                text = "Cooperative pool is quiet here.",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Select Hour 24 or 48 above to allow listings to escalate into wider geographic tiers, or click 'Populate Live Seed List' to fill the community feed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visibleFeedDeals) { deal ->
                            val daysRemaining = ((deal.expiryDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                            val (expiryColor, expiryContainerColor) = when {
                                daysRemaining <= 15 -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                daysRemaining <= 45 -> Color(0xFFFF9800) to Color(0xFFFF9800).copy(alpha = 0.1f)
                                else -> Color(0xFF4CAF50) to Color(0xFF4CAF50).copy(alpha = 0.1f)
                            }

                            Card(
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = deal.productName, 
                                                style = MaterialTheme.typography.titleMedium, 
                                                fontWeight = FontWeight.Black,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Batch: ${deal.batchNumber}", 
                                                style = MaterialTheme.typography.bodySmall, 
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            formatRescuePrice(deal.sellingPrice),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Black,
                                            color = TealPrimary
                                        )
                                    }

                                    // Clear parameters row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Deal Qty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${deal.quantity} Units", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("Your Commission", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${deal.commissionPercentage}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TealPrimary)
                                        }
                                        Column {
                                            Text("Time Remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(expiryContainerColor)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "$daysRemaining Days Left", 
                                                    style = MaterialTheme.typography.bodySmall, 
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = expiryColor
                                                )
                                            }
                                        }
                                    }

                                    val isLocalLga = deal.ownerLga.isNotBlank() && deal.ownerLga.equals(currentLga, ignoreCase = true)
                                    val isLocalState = deal.ownerState.isNotBlank() && deal.ownerState.equals(currentState, ignoreCase = true)
                                    val salesQty = medicationSales.filter { it.productName.equals(deal.productName, ignoreCase = true) }.sumOf { it.quantitySold }

                                    if (isLocalLga || isLocalState || salesQty > 0) {
                                        Row(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalArrangement = Arrangement.spacedBy(6.dp),
                                             verticalAlignment = Alignment.CenterVertically
                                        ) {
                                             if (isLocalLga) {
                                                 SuggestionChip(
                                                     onClick = {},
                                                     label = { Text("Local ${deal.ownerLga} LGA (Proximate)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TealPrimary) },
                                                     icon = { Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(12.dp), tint = TealPrimary) }
                                                 )
                                             } else if (isLocalState) {
                                                 SuggestionChip(
                                                     onClick = {},
                                                     label = { Text("${deal.ownerState} State Ring", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                                     icon = { Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                                 )
                                             }
                                             
                                             if (salesQty > 0) {
                                                 SuggestionChip(
                                                     onClick = {},
                                                     colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                                     label = { Text("High Local Demand ($salesQty sold)", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                                     icon = { Icon(Icons.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(12.dp), tint = TealPrimary) }
                                                 )
                                             }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Host Node: ${deal.ownerDeviceModel}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.acceptRescueListing(deal)
                                                Toast.makeText(context, "Medication claimed into custody!", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.Black)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Rescue Stock",
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
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
                    text = "My Rescued Stock in Custody",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )

                if (rescuedItems.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(54.dp), tint = TealPrimary)
                            Text("No rescued items in your custody.", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Navigate to the Community Feed and claim near-expiry stocks to help partner pharmacies liquidate their inventory and earn commission rewards.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(rescuedItems) { item ->
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(item.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                    
                                    // Custom visual metrics splits
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(TealPrimary.copy(alpha = 0.12f))
                                                .padding(8.dp)
                                        ) {
                                            Column {
                                                Text("CUSTODY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                                Text("${item.quantity} Units left", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                                .padding(8.dp)
                                        ) {
                                            Column {
                                                Text("RETAIL PRICE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(formatRescuePrice(item.sellingPrice), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                                .padding(8.dp)
                                        ) {
                                            Column {
                                                Text("COMMISSION", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                                Text("${item.commissionPercentage}% share", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                            }
                                        }
                                    }

                                    // Graphical representation of profit split
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("REVENUE DISTRIBUTION MODEL:", fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "Rescuer Node Yield: ${item.commissionPercentage}%", 
                                                fontSize = 11.sp, 
                                                color = TealPrimary, 
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Node Payback: ${(100.0 - item.commissionPercentage).toInt()}%", 
                                                fontSize = 11.sp, 
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { (item.commissionPercentage / 100.0f).toFloat() },
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                            color = TealPrimary,
                                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Owner Node: ${item.ownerDeviceModel}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                listingToSell = item
                                                quantityToSellString = "1"
                                                selectedCustomerForRescueSale = null
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.Black)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Liquidate Sale",
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Offer Near-Expiry Medication",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )

                    // Selection fields
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isPostingDropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    selectedItemForPost?.let { "${it.name} (Stock: ${it.stockQuantity})" } ?: "Select Expiring Medication",
                                    color = if (selectedItemForPost != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = rescuePriceString,
                            onValueChange = { rescuePriceString = it },
                            label = { Text("Cooperative Deal Price (Suggested 30% off)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(12.dp),
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = durationDaysString,
                            onValueChange = { durationDaysString = it },
                            label = { Text("Listing Duration (Days)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Careflux Proximity & Demand matching report
                        val matches = viewModel.calculateRedistributionOpportunities(selectedItemForPost!!.name, selectedItemForPost!!.category)
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Hub, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                                    Text(
                                        "Routing redistributed opportunity mapping",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("EVENT: Expiry Proximity Risk", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("ACTION: Redistribution Mapped", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                
                                Text(
                                    "Dynamic Routing Engine: The system scans registered cooperative nodes and historical prescription/sale volumes to identify proximate, high-capacity partners designed to liquidate this item rapidly with zero transit friction.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                    lineHeight = 15.sp
                                )
                                
                                if (matches.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "No active partner nodes synced in pool.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Button(
                                            onClick = { viewModel.seedSimulationNodesAndSales() },
                                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            Text("Seed Demo Partners List", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Text(
                                        "Matches Found Meeting Criteria (${matches.size}):",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    
                                    matches.forEach { match ->
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(match.pharmacyName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        "Score: ${match.score}%",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (match.score >= 50) TealPrimary else MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Text(
                                                    "Device: ${match.deviceModel} — Location: ${match.lga} LGA, ${match.state} State",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val tierLabel = when (match.escalationTier) {
                                                        1 -> "Immediate routing (Now)"
                                                        2 -> "Tier 2: Escalated routing (24 hours)"
                                                        else -> "Tier 3: Open pool (48 hours)"
                                                    }
                                                    Text(
                                                        tierLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                                
                                                if (match.reasons.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    match.reasons.forEach { r ->
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically, 
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Check, 
                                                                contentDescription = null, 
                                                                modifier = Modifier.size(10.dp), 
                                                                tint = TealPrimary
                                                            )
                                                            Text(r, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

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
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Post To Cooperative Pool", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))

                    val myOffers = listings.filter { it.ownerDeviceId == currentDeviceId }
                    Text(
                        text = "My Posted Offers (${myOffers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )

                    if (myOffers.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
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
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                            "Available" -> TealPrimary to TealPrimary.copy(alpha = 0.12f)
                                            "Accepted" -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
                                            else -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.12f)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(containerColor, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = offer.status,
                                                fontSize = 11.sp,
                                                color = statusColor,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Column {
                                            Text("Remaining Qty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${offer.quantity} Units", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("Liquidation Offer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(formatRescuePrice(offer.sellingPrice), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("Commission set", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${offer.commissionPercentage}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
                                                Toast.makeText(context, "Listing retracted successfully!", Toast.LENGTH_LONG).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Retract & Remove Listing", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            title = { Text("Log Liquidated Transction", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Liquidation Product: ${activeListing.productName}")
                    Text("Available custody: $maxAvailable units")

                    OutlinedTextField(
                        value = quantityToSellString,
                        onValueChange = { quantityToSellString = it },
                        label = { Text("Quantity Sold") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Customer demographic mapping dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isCustomerDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectedCustomerForRescueSale?.name ?: "Assign Customer (Demographics)")
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Total Revenue: ${formatRescuePrice(rev)}", fontWeight = FontWeight.Bold)
                            Text("Your Commission: ${formatRescuePrice(resCo)} (${activeListing.commissionPercentage}%)")
                            Text("Owner Remittance Split: ${formatRescuePrice(ownRe)}")
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
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black)
                ) {
                    Text("Complete Rescue Sale", fontWeight = FontWeight.Bold)
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
