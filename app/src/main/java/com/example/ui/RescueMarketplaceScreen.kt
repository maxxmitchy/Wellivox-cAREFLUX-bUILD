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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.example.data.InventoryItem
import com.example.data.RescueListing
import com.example.ui.theme.*
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
    
    // Verified Pharmacy Network search & filter states
    var networkSearchQuery by remember { mutableStateOf("") }
    var networkFilterType by remember { mutableStateOf("All") } // All, Independent, Medplus
    var networkFilterState by remember { mutableStateOf("All") } // All, Lagos, Abuja, Rivers
    var showAddContactDialog by remember { mutableStateOf(false) }

    val sharedPrefs = remember { context.getSharedPreferences("verified_network_prefs", android.content.Context.MODE_PRIVATE) }
    
    // Check if initialized
    val isInitialized = remember { sharedPrefs.getBoolean("is_initialized_v2", false) }
    var pharmaciesJson by remember {
        mutableStateOf(
            if (!isInitialized) {
                // To keep backward compatibility and integrate custom contacts if any already existed:
                val existingCustomJson = sharedPrefs.getString("custom_contacts", "[]") ?: "[]"
                val existingCustom = try {
                    Json.decodeFromString<List<VerifiedPharmacy>>(existingCustomJson)
                } catch(e: Exception) {
                    emptyList<VerifiedPharmacy>()
                }
                val initialList = staticPharmacies + existingCustom
                val json = Json.encodeToString(initialList)
                sharedPrefs.edit()
                    .putString("all_pharmacies_v2", json)
                    .putBoolean("is_initialized_v2", true)
                    .apply()
                json
            } else {
                sharedPrefs.getString("all_pharmacies_v2", null) ?: "[]"
            }
        )
    }
    
    val pharmaciesList = remember(pharmaciesJson) {
        try {
            Json.decodeFromString<List<VerifiedPharmacy>>(pharmaciesJson)
        } catch(e: Exception) {
            staticPharmacies
        }
    }

    var contactToEdit by remember { mutableStateOf<VerifiedPharmacy?>(null) }
    var contactToDelete by remember { mutableStateOf<VerifiedPharmacy?>(null) }

    // Stock Query Broadcaster states (Pre-populated for the customer scenario: Maxi Vision eye supplements in Ikeja)
    var showBroadcasterDialog by remember { mutableStateOf(false) }
    var broadcastProduct by remember { mutableStateOf("Maxi Vision Eye Supplements") }
    var broadcastAddress by remember { mutableStateOf("Road 1 Ikeja") }
    var broadcastState by remember { mutableStateOf("Lagos") }
    var broadcastLga by remember { mutableStateOf("Ikeja") }
    var broadcastResponses by remember { mutableStateOf(mapOf<String, String>()) } // Key: phone, Value: "Pending" | "Yes" | "No"
    
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // Highly polished, space-efficient executive Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = "Rescue Marketplace",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TealPrimary
                )
                Text(
                    text = "Cooperative zero-waste near-expiry medical pool routing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMedium
                )
            }
            // Quick count badge
            val activeDealsCount = listings.count { it.status == "Available" }
            Surface(
                color = TealPrimary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "$activeDealsCount Active",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TealPrimary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Standardized Pill Segmented Tab Control with small icons & text labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SlateBackgroundLight)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                Triple("Feed", Icons.Filled.Group, 0),
                Triple("Stock", Icons.Filled.VerifiedUser, 1),
                Triple("Post", Icons.Filled.PostAdd, 2),
                Triple("Network", Icons.Filled.Hub, 3)
            ).forEach { (label, icon, index) ->
                val isSelected = selectedSection == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) TealPrimary else Color.Transparent)
                        .clickable { selectedSection = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) Color.Black else SlateTextMedium,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (isSelected) Color.Black else SlateTextMedium
                        )
                    }
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

                            // Dynamic Intelligent Routing Suggestions Engine
                            val recommendations = remember(inventory, visibleFeedDeals, currentState) {
                                val list = mutableListOf<String>()
                                val regionalPharmacies = staticPharmacies.filter { it.state.equals(currentState, ignoreCase = true) }
                                val regionalWholesalers = regionalPharmacies.filter { it.category.contains("Wholesaler", ignoreCase = true) }
                                val regionalRetailers = regionalPharmacies.filter { it.category.contains("Retailer", ignoreCase = true) || it.category.contains("Node", ignoreCase = true) }

                                // 1. Identify near-expiry items in user's inventory
                                val nearExpiryItem = inventory.firstOrNull { 
                                    val days = ((it.expiryDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                                    days in 1..90
                                }
                                
                                if (nearExpiryItem != null && regionalRetailers.isNotEmpty()) {
                                    val target = regionalRetailers.randomOrNull() ?: regionalRetailers[0]
                                    list.add("💡 To offload near-expiry ${nearExpiryItem.name}: High-success retail match suggestion: ${target.name} in $currentState (${target.phone})")
                                }

                                // 2. Identify community deals
                                val communityDeal = visibleFeedDeals.firstOrNull()
                                if (communityDeal != null && regionalWholesalers.isNotEmpty()) {
                                    val target = regionalWholesalers.randomOrNull() ?: regionalWholesalers[0]
                                    list.add("💡 Need backup stock for ${communityDeal.productName}? Fast-fulfillment wholesaler: ${target.name} in $currentState (${target.phone})")
                                }

                                // 3. Fallback general routing suggestion
                                if (list.isEmpty()) {
                                    if (regionalWholesalers.isNotEmpty()) {
                                        val w = regionalWholesalers.first()
                                        list.add("💡 Looking to buy/ask? Try Wholesaler ${w.name} (${w.phone}) in $currentState for direct stock routing.")
                                    } else {
                                        list.add("💡 Sourcing query? Connect with Medplus chain nodes for instant cooperative routing.")
                                    }
                                }
                                list
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "INTELLIGENT ROUTING RECOMMENDATIONS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = TealPrimary,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    recommendations.forEach { recommendation ->
                                        Text(
                                            text = recommendation,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 15.sp
                                        )
                                    }
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
                    Column {
                        Text(
                            text = "Browse Community Deals",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Distributed Network • Capacity: up to 2,000+ Listings",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = SlateTextMedium
                        )
                    }
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
                                                viewModel.acceptRescueListing(deal) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
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

            3 -> {
                // Verified Pharmacy & Wholesaler Network Directory
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {


                    // MODAL DIALOG CONTAINER FOR PROXIMITY BROADCASTER
                    if (showBroadcasterDialog) {
                        Dialog(
                            onDismissRequest = { showBroadcasterDialog = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .fillMaxHeight(0.92f)
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (AppThemeManager.isDark) Color(0xFF1E293B) else Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Hub,
                                                contentDescription = null,
                                                tint = TealPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "LGA Proximity Stock Broadcaster",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (AppThemeManager.isDark) Color.White else Color(0xFF0F172A)
                                                )
                                                Text(
                                                    text = "Query & route inquiries outward by proximity tiers",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SlateTextMedium
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { showBroadcasterDialog = false },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Close",
                                                tint = SlateTextMedium,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                                    // Content Scroll State
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Enter requested medication and customer delivery address below. The system automatically partitions and ranks cooperative network nodes into Proximity Escalation Tiers.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateTextMedium,
                                            lineHeight = 15.sp
                                        )

                                        // 1. INPUT FIELDS (Proper spacing, responsive layout, no hardcoded heights)
                                        OutlinedTextField(
                                            value = broadcastProduct,
                                            onValueChange = { broadcastProduct = it },
                                            label = { Text("Product Requested") },
                                            placeholder = { Text("e.g., Maxi Vision Eye Supplements") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = TealPrimary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = broadcastAddress,
                                            onValueChange = { broadcastAddress = it },
                                            label = { Text("Customer Delivery Address") },
                                            placeholder = { Text("e.g., Road 1 Ikeja") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = TealPrimary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = broadcastLga,
                                                onValueChange = { broadcastLga = it },
                                                label = { Text("Target LGA") },
                                                placeholder = { Text("e.g., Ikeja") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = TealPrimary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )

                                            OutlinedTextField(
                                                value = broadcastState,
                                                onValueChange = { broadcastState = it },
                                                label = { Text("Target State") },
                                                placeholder = { Text("e.g., Lagos") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = TealPrimary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Proximity Escalation Tiers Computation
                                        val tier1Local = remember(pharmaciesList, broadcastState, broadcastLga) {
                                            pharmaciesList.filter { 
                                                it.state.trim().equals(broadcastState.trim(), ignoreCase = true) && 
                                                it.lga.trim().equals(broadcastLga.trim(), ignoreCase = true)
                                            }
                                        }
                                        val tier2State = remember(pharmaciesList, broadcastState, broadcastLga) {
                                            pharmaciesList.filter { 
                                                it.state.trim().equals(broadcastState.trim(), ignoreCase = true) && 
                                                !it.lga.trim().equals(broadcastLga.trim(), ignoreCase = true)
                                            }
                                        }
                                        val tier3National = remember(pharmaciesList, broadcastState) {
                                            pharmaciesList.filter { 
                                                !it.state.trim().equals(broadcastState.trim(), ignoreCase = true)
                                            }
                                        }

                                        Text(
                                            text = "PROXIMITY ESCALATION TIERS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TealPrimary
                                        )

                                        var selectedTierTab by remember { mutableStateOf(1) } // 1 = Local, 2 = State, 3 = National

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(TealBackground, RoundedCornerShape(6.dp))
                                                .padding(2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf(
                                                Triple(1, "Tier 1: Local LGA (${tier1Local.size})", TealPrimary),
                                                Triple(2, "Tier 2: State (${tier2State.size})", Color(0xFFFF9800)),
                                                Triple(3, "Tier 3: National (${tier3National.size})", Color.LightGray)
                                            ).forEach { (tierNum, label, accentColor) ->
                                                val isSelected = selectedTierTab == tierNum
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                                        .clickable { selectedTierTab = tierNum }
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) accentColor else SlateTextMedium
                                                    )
                                                }
                                            }
                                        }

                                        val activeTierNodes = when (selectedTierTab) {
                                            1 -> tier1Local
                                            2 -> tier2State
                                            else -> tier3National
                                        }

                                        if (activeTierNodes.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(20.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "No cooperative nodes registered in this proximity tier.",
                                                    fontSize = 11.sp,
                                                    color = SlateTextMedium
                                                )
                                            }
                                        } else {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                activeTierNodes.forEach { ph ->
                                                    val responseStatus = broadcastResponses[ph.phone] ?: "Pending"
                                                    val cardBorderColor = when (responseStatus) {
                                                        "Yes" -> Color(0xFF4CAF50)
                                                        "No" -> Color(0xFFF44336).copy(alpha = 0.4f)
                                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                                    }
                                                    val cardBg = when (responseStatus) {
                                                        "Yes" -> Color(0xFF4CAF50).copy(alpha = 0.08f)
                                                        "No" -> Color(0xFFF44336).copy(alpha = 0.02f)
                                                        else -> if (AppThemeManager.isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
                                                    }

                                                    Card(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = cardBg),
                                                        border = BorderStroke(1.dp, cardBorderColor)
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(8.dp),
                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = ph.name,
                                                                            fontSize = 12.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = if (AppThemeManager.isDark) Color.White else Color(0xFF0F172A)
                                                                        )
                                                                        if (responseStatus == "Yes") {
                                                                            Surface(
                                                                                color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                                                                                shape = RoundedCornerShape(4.dp)
                                                                            ) {
                                                                                Text(
                                                                                    text = "STOCK CONFIRMED",
                                                                                    fontSize = 8.sp,
                                                                                    fontWeight = FontWeight.Bold,
                                                                                    color = Color(0xFF4CAF50),
                                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                    Text(
                                                                        text = "${ph.lga}, ${ph.state} • ${ph.phone}",
                                                                        fontSize = 10.sp,
                                                                        color = SlateTextMedium
                                                                    )
                                                                }

                                                                IconButton(
                                                                    onClick = {
                                                                        try {
                                                                            val rawPhone = ph.phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
                                                                            val cleanPhone = if (rawPhone.startsWith("0")) {
                                                                                "234" + rawPhone.substring(1)
                                                                            } else if (rawPhone.startsWith("+")) {
                                                                                rawPhone.substring(1)
                                                                            } else {
                                                                                rawPhone
                                                                            }
                                                                            val textMessage = "Hello ${ph.name}! Do you have *${broadcastProduct}* in stock? We have a client at *${broadcastAddress}* asking for it. Please reply YES or NO. Thank you!"
                                                                            val encodedText = java.net.URLEncoder.encode(textMessage, "UTF-8")
                                                                            val intent = android.content.Intent(
                                                                                android.content.Intent.ACTION_VIEW,
                                                                                android.net.Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")
                                                                            )
                                                                            context.startActivity(intent)
                                                                        } catch (e: Exception) {
                                                                            Toast.makeText(context, "Could not open WhatsApp.", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    },
                                                                    modifier = Modifier.size(28.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Filled.Chat,
                                                                        contentDescription = "WhatsApp Query",
                                                                        tint = Color(0xFF25D366),
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }

                                                            // Response Status Selector
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(text = "Response Status:", fontSize = 9.sp, color = SlateTextMedium)
                                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    listOf(
                                                                        "Pending" to Color.Gray,
                                                                        "Yes" to Color(0xFF4CAF50),
                                                                        "No" to Color(0xFFF44336)
                                                                    ).forEach { (status, color) ->
                                                                        val isCurrent = responseStatus == status
                                                                        Surface(
                                                                            color = if (isCurrent) color.copy(alpha = 0.2f) else Color.Transparent,
                                                                            border = BorderStroke(1.dp, if (isCurrent) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                                                                            shape = RoundedCornerShape(4.dp),
                                                                            modifier = Modifier
                                                                                .clickable {
                                                                                    val updatedResponses = broadcastResponses.toMutableMap()
                                                                                    updatedResponses[ph.phone] = status
                                                                                    broadcastResponses = updatedResponses
                                                                                }
                                                                        ) {
                                                                            Text(
                                                                                text = status,
                                                                                fontSize = 8.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = if (isCurrent) color else SlateTextMedium,
                                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Strategy recommendation text
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = TealBackground),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(14.dp))
                                                Text(
                                                    text = "ESCALATION TIP: Open chats for Tier 1 first. If all reply NO or fail to reply in 5 mins, select Tier 2 tab above to query other Lagos State nodes, followed by Tier 3.",
                                                    fontSize = 8.sp,
                                                    lineHeight = 10.sp,
                                                    color = SlateTextMedium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Search & Filter Panel (Compact & Polish)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateBackgroundLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Search bar
                            OutlinedTextField(
                                value = networkSearchQuery,
                                onValueChange = { networkSearchQuery = it },
                                placeholder = { Text("Search pharmacy, LGA, address...", fontSize = 12.sp, color = SlateTextMedium) },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = SlateTextMedium) },
                                trailingIcon = {
                                    if (networkSearchQuery.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { networkSearchQuery = "" },
                                            tint = SlateTextMedium
                                        )
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TealPrimary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            )

                            // Horizontal layout for Type & State quick filters (Very high-density)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Type Filters
                                listOf("All", "Wholesalers", "Retailers").forEach { type ->
                                    val isSelected = networkFilterType == type
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) TealPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable { networkFilterType = type }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = type,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(16.dp)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // State Filters (Scrollable Row - dynamically computed to support custom added states)
                                val dynamicStates = remember(pharmaciesList) {
                                    val defaultStates = listOf("Lagos", "Abuja", "Rivers", "Enugu", "Anambra", "Cross River", "Imo", "Akwa Ibom")
                                    val actualStates = pharmaciesList.map { it.state.trim() }.filter { it.isNotBlank() }
                                    val uniqueStates = (defaultStates + actualStates).distinct().sortedBy { it.lowercase() }
                                    listOf("All") + uniqueStates
                                }

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    dynamicStates.forEach { stateName ->
                                        val displayLabel = if (stateName == "All") "All States" else stateName
                                        val isSelected = networkFilterState == stateName
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSelected) TealPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                .clickable { networkFilterState = stateName }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = displayLabel,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Using consolidated list which supports additions, edits, and deletions
                    val allPharmacies = pharmaciesList

                    // Network Stats bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cooperative Nodes Directory",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { showAddContactDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp).testTag("add_contact_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Contact", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(TealPrimary)
                            )
                            Text(
                                text = "${allPharmacies.size} Active Nodes",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateTextMedium,
                                fontSize = 10.sp
                            )
                        }
                    }

                    val filteredPharmacies = remember(allPharmacies, networkSearchQuery, networkFilterType, networkFilterState) {
                        allPharmacies.filter { ph ->
                            val matchesType = when (networkFilterType) {
                                "All" -> true
                                "Wholesalers" -> ph.category.contains("Wholesaler", ignoreCase = true)
                                "Retailers" -> ph.category.contains("Retailer", ignoreCase = true) || ph.category.contains("Node", ignoreCase = true)
                                else -> true
                            }
                            val matchesState = when (networkFilterState) {
                                "All" -> true
                                else -> ph.state.equals(networkFilterState, ignoreCase = true)
                            }
                            val matchesSearch = if (networkSearchQuery.isBlank()) true else {
                                ph.name.contains(networkSearchQuery, ignoreCase = true) ||
                                ph.address.contains(networkSearchQuery, ignoreCase = true) ||
                                ph.lga.contains(networkSearchQuery, ignoreCase = true) ||
                                ph.phone.contains(networkSearchQuery)
                            }
                            matchesType && matchesState && matchesSearch
                        }
                    }

                    if (filteredPharmacies.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(32.dp), tint = SlateTextMedium)
                                Text("No verified pharmacies found matching criteria.", fontSize = 12.sp, color = SlateTextMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(filteredPharmacies) { ph ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = SlateBackgroundLight),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = if (ph.category.contains("Medplus", ignoreCase = true)) Icons.Filled.Verified else Icons.Filled.Store,
                                                    contentDescription = null,
                                                    tint = if (ph.category.contains("Medplus", ignoreCase = true)) TealPrimary else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = ph.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (AppThemeManager.isDark) Color.White else Color(0xFF0F172A),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.width(4.dp))

                                            // Badges
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            if (ph.category.contains("Medplus", ignoreCase = true)) TealPrimary.copy(alpha = 0.15f)
                                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (ph.category.contains("Medplus", ignoreCase = true)) "MEDPLUS" else "INDEPENDENT",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = if (ph.category.contains("Medplus", ignoreCase = true)) TealPrimary else MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                val isWholesaler = ph.category.contains("Wholesaler", ignoreCase = true)
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            if (isWholesaler) Color(0xFFFF9800).copy(alpha = 0.15f)
                                                            else Color(0xFF9E9E9E).copy(alpha = 0.15f)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (isWholesaler) "WHOLESALER" else "RETAILER",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = if (isWholesaler) Color(0xFFFF9800) else (if (AppThemeManager.isDark) Color.LightGray else Color(0xFF475569))
                                                     )
                                                }
                                            }
                                        }

                                        Text(
                                            text = ph.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateTextMedium,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp
                                        )

                                        // Row 3: Location and Route
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Location Chip
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.LocationOn,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = SlateTextMedium
                                                )
                                                Text(
                                                    text = "${ph.lga}, ${ph.state}",
                                                    fontSize = 11.sp,
                                                    color = SlateTextMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            // Route transit score simulation
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.LocalShipping,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = TealPrimary
                                                )
                                                Text(
                                                    text = "Route",
                                                    fontSize = 11.sp,
                                                    color = TealPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )

                                        // Row 4: Phone & Quick Actions
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = ph.phone,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TealPrimary,
                                                modifier = Modifier.clickable {
                                                    try {
                                                        val intent = android.content.Intent(
                                                            android.content.Intent.ACTION_DIAL,
                                                            android.net.Uri.parse("tel:${ph.phone}")
                                                        )
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Could not open dialer.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        try {
                                                            val intent = android.content.Intent(
                                                                android.content.Intent.ACTION_DIAL,
                                                                android.net.Uri.parse("tel:${ph.phone}")
                                                            )
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Could not open dialer.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp).testTag("call_node_${ph.phone}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Phone,
                                                        contentDescription = "Call Node",
                                                        tint = TealPrimary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        try {
                                                            val rawPhone = ph.phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
                                                            val cleanPhone = if (rawPhone.startsWith("0")) {
                                                                "234" + rawPhone.substring(1)
                                                            } else if (rawPhone.startsWith("+")) {
                                                                rawPhone.substring(1)
                                                            } else {
                                                                rawPhone
                                                            }
                                                            val intent = android.content.Intent(
                                                                android.content.Intent.ACTION_VIEW,
                                                                android.net.Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone")
                                                            )
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Could not open WhatsApp.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp).testTag("whatsapp_node_${ph.phone}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Chat,
                                                        contentDescription = "WhatsApp Node",
                                                        tint = Color(0xFF25D366),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { contactToEdit = ph },
                                                    modifier = Modifier.size(28.dp).testTag("edit_node_${ph.name}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Edit,
                                                        contentDescription = "Edit Node",
                                                        tint = SlateTextMedium,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { contactToDelete = ph },
                                                    modifier = Modifier.size(28.dp).testTag("delete_node_${ph.name}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Delete,
                                                        contentDescription = "Delete Node",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(13.dp)
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
    }

    // MODAL DIALOG CONTAINER FOR PROXIMITY BROADCASTER
    if (showBroadcasterDialog) {
        Dialog(
            onDismissRequest = { showBroadcasterDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.92f)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (AppThemeManager.isDark) Color(0xFF1E293B) else Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Hub,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "LGA Proximity Stock Broadcaster",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (AppThemeManager.isDark) Color.White else Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Query & route inquiries outward by proximity tiers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextMedium
                                )
                            }
                        }
                        IconButton(
                            onClick = { showBroadcasterDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = SlateTextMedium,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // Content Scroll State
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Enter requested medication and customer delivery address below. The system automatically partitions and ranks cooperative network nodes into Proximity Escalation Tiers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextMedium,
                            lineHeight = 15.sp
                        )

                        // 1. INPUT FIELDS (Proper spacing, responsive layout, no hardcoded heights)
                        OutlinedTextField(
                            value = broadcastProduct,
                            onValueChange = { broadcastProduct = it },
                            label = { Text("Product Requested") },
                            placeholder = { Text("e.g., Maxi Vision Eye Supplements") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = broadcastAddress,
                            onValueChange = { broadcastAddress = it },
                            label = { Text("Customer Delivery Address") },
                            placeholder = { Text("e.g., Road 1 Ikeja") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = broadcastLga,
                                onValueChange = { broadcastLga = it },
                                label = { Text("Target LGA") },
                                placeholder = { Text("e.g., Ikeja") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TealPrimary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = broadcastState,
                                onValueChange = { broadcastState = it },
                                label = { Text("Target State") },
                                placeholder = { Text("e.g., Lagos") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TealPrimary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Proximity Escalation Tiers Computation
                        val tier1Local = remember(pharmaciesList, broadcastState, broadcastLga) {
                            pharmaciesList.filter { 
                                it.state.trim().equals(broadcastState.trim(), ignoreCase = true) && 
                                it.lga.trim().equals(broadcastLga.trim(), ignoreCase = true)
                            }
                        }
                        val tier2State = remember(pharmaciesList, broadcastState, broadcastLga) {
                            pharmaciesList.filter { 
                                it.state.trim().equals(broadcastState.trim(), ignoreCase = true) && 
                                !it.lga.trim().equals(broadcastLga.trim(), ignoreCase = true)
                            }
                        }
                        val tier3National = remember(pharmaciesList, broadcastState) {
                            pharmaciesList.filter { 
                                !it.state.trim().equals(broadcastState.trim(), ignoreCase = true)
                            }
                        }

                        Text(
                            text = "PROXIMITY ESCALATION TIERS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary
                        )

                        var selectedTierTab by remember { mutableStateOf(1) } // 1 = Local, 2 = State, 3 = National

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TealBackground, RoundedCornerShape(6.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                Triple(1, "Tier 1: Local LGA (${tier1Local.size})", TealPrimary),
                                Triple(2, "Tier 2: State (${tier2State.size})", Color(0xFFFF9800)),
                                Triple(3, "Tier 3: National (${tier3National.size})", Color.LightGray)
                            ).forEach { (tierNum, label, accentColor) ->
                                val isSelected = selectedTierTab == tierNum
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                        .clickable { selectedTierTab = tierNum }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) accentColor else SlateTextMedium
                                    )
                                }
                            }
                        }

                        val activeTierNodes = when (selectedTierTab) {
                            1 -> tier1Local
                            2 -> tier2State
                            else -> tier3National
                        }

                        if (activeTierNodes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No cooperative nodes registered in this proximity tier.",
                                    fontSize = 11.sp,
                                    color = SlateTextMedium
                                )
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                activeTierNodes.forEach { ph ->
                                    val responseStatus = broadcastResponses[ph.phone] ?: "Pending"
                                    val cardBorderColor = when (responseStatus) {
                                        "Yes" -> Color(0xFF4CAF50)
                                        "No" -> Color(0xFFF44336).copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                    }
                                    val cardBg = when (responseStatus) {
                                        "Yes" -> Color(0xFF4CAF50).copy(alpha = 0.08f)
                                        "No" -> Color(0xFFF44336).copy(alpha = 0.02f)
                                        else -> if (AppThemeManager.isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardBg),
                                        border = BorderStroke(1.dp, cardBorderColor)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            text = ph.name,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (AppThemeManager.isDark) Color.White else Color(0xFF0F172A)
                                                        )
                                                        if (responseStatus == "Yes") {
                                                            Surface(
                                                                color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "STOCK CONFIRMED",
                                                                    fontSize = 8.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF4CAF50),
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = "${ph.lga}, ${ph.state} • ${ph.phone}",
                                                        fontSize = 10.sp,
                                                        color = SlateTextMedium
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        try {
                                                            val rawPhone = ph.phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
                                                            val cleanPhone = if (rawPhone.startsWith("0")) {
                                                                "234" + rawPhone.substring(1)
                                                            } else if (rawPhone.startsWith("+")) {
                                                                rawPhone.substring(1)
                                                            } else {
                                                                rawPhone
                                                            }
                                                            val textMessage = "Hello ${ph.name}! Do you have *${broadcastProduct}* in stock? We have a client at *${broadcastAddress}* asking for it. Please reply YES or NO. Thank you!"
                                                            val encodedText = java.net.URLEncoder.encode(textMessage, "UTF-8")
                                                            val intent = android.content.Intent(
                                                                android.content.Intent.ACTION_VIEW,
                                                                android.net.Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")
                                                            )
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Could not open WhatsApp.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Chat,
                                                        contentDescription = "WhatsApp Query",
                                                        tint = Color(0xFF25D366),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            // Response Status Selector
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = "Response Status:", fontSize = 9.sp, color = SlateTextMedium)
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    listOf(
                                                        "Pending" to Color.Gray,
                                                        "Yes" to Color(0xFF4CAF50),
                                                        "No" to Color(0xFFF44336)
                                                    ).forEach { (status, color) ->
                                                        val isCurrent = responseStatus == status
                                                        Surface(
                                                            color = if (isCurrent) color.copy(alpha = 0.2f) else Color.Transparent,
                                                            border = BorderStroke(1.dp, if (isCurrent) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                                                            shape = RoundedCornerShape(4.dp),
                                                            modifier = Modifier
                                                                .clickable {
                                                                    val updatedResponses = broadcastResponses.toMutableMap()
                                                                    updatedResponses[ph.phone] = status
                                                                    broadcastResponses = updatedResponses
                                                                }
                                                        ) {
                                                            Text(
                                                                text = status,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isCurrent) color else SlateTextMedium,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

                        Spacer(modifier = Modifier.height(4.dp))

                        // Strategy recommendation text
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TealBackground),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "ESCALATION TIP: Open chats for Tier 1 first. If all reply NO or fail to reply in 5 mins, select Tier 2 tab above to query other Lagos State nodes, followed by Tier 3.",
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp,
                                    color = SlateTextMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Extended Floating Action Button for LGA Proximity Stock Broadcaster (instantly launch from any tab)
    ExtendedFloatingActionButton(
        onClick = { showBroadcasterDialog = true },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = 24.dp, end = 16.dp)
            .testTag("proximity_broadcaster_fab"),
        containerColor = TealPrimary,
        contentColor = Color.Black,
        icon = {
            Icon(
                imageVector = Icons.Filled.Hub,
                contentDescription = "Stock Broadcaster",
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        },
        text = {
            Text(
                text = "Stock Broadcaster",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.Black
            )
        }
    )
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

    if (showAddContactDialog) {
        var nameInput by remember { mutableStateOf("") }
        var phoneInput by remember { mutableStateOf("") }
        var addressInput by remember { mutableStateOf("") }
        var categoryInput by remember { mutableStateOf("Independent Retailer") } // "Independent Wholesaler", "Independent Retailer", or "Medplus Chain Node"
        var stateInput by remember { mutableStateOf("Lagos") }
        var lgaInput by remember { mutableStateOf("") }
        var isCategoryDropdownExpanded by remember { mutableStateOf(false) }
        var isStateDropdownExpanded by remember { mutableStateOf(false) }

        val defaultStates = listOf("Lagos", "Abuja", "Rivers", "Enugu", "Anambra", "Cross River", "Imo", "Akwa Ibom")
        var isCustomState by remember { mutableStateOf(false) }
        var customStateInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = { Text("Add Cooperative Node Contact", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Node Name") },
                        placeholder = { Text("e.g., St. Catherine Pharmacy") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_contact_name_input")
                    )

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("e.g., 0803 123 4567") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_contact_phone_input")
                    )

                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        label = { Text("Full Address") },
                        placeholder = { Text("e.g., 12 Townhall Street, Ikeja") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_contact_address_input")
                    )

                    // Category Selector Dropdown
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Category", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { isCategoryDropdownExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(categoryInput)
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(
                                expanded = isCategoryDropdownExpanded,
                                onDismissRequest = { isCategoryDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Independent Wholesaler") },
                                    onClick = {
                                        categoryInput = "Independent Wholesaler"
                                        isCategoryDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Independent Retailer") },
                                    onClick = {
                                        categoryInput = "Independent Retailer"
                                        isCategoryDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Medplus Chain Node") },
                                    onClick = {
                                        categoryInput = "Medplus Chain Node"
                                        isCategoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // State Selector Dropdown
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("State", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { isStateDropdownExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (isCustomState) "Other (Custom State)..." else stateInput)
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(
                                expanded = isStateDropdownExpanded,
                                onDismissRequest = { isStateDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Other (Custom State)...") },
                                    onClick = {
                                        isCustomState = true
                                        isStateDropdownExpanded = false
                                    }
                                )
                                defaultStates.forEach { stateName ->
                                    DropdownMenuItem(
                                        text = { Text(stateName) },
                                        onClick = {
                                            stateInput = stateName
                                            isCustomState = false
                                            isStateDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (isCustomState) {
                        OutlinedTextField(
                            value = customStateInput,
                            onValueChange = { customStateInput = it },
                            label = { Text("Custom State Name") },
                            placeholder = { Text("e.g., Oyo") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("add_contact_custom_state_input")
                        )
                    }

                    OutlinedTextField(
                        value = lgaInput,
                        onValueChange = { lgaInput = it },
                        label = { Text("Local Government Area (LGA)") },
                        placeholder = { Text("e.g., Ikeja") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_contact_lga_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalState = if (isCustomState) customStateInput.trim() else stateInput
                        if (nameInput.isBlank() || phoneInput.isBlank() || addressInput.isBlank() || lgaInput.isBlank() || finalState.isBlank()) {
                            Toast.makeText(context, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val newContact = VerifiedPharmacy(
                            name = nameInput.trim(),
                            phone = phoneInput.trim(),
                            address = addressInput.trim(),
                            category = categoryInput,
                            state = finalState,
                            lga = lgaInput.trim()
                        )

                        // Save using local helper
                        val updatedList = pharmaciesList + newContact
                        try {
                            val json = Json.encodeToString(updatedList)
                            sharedPrefs.edit().putString("all_pharmacies_v2", json).apply()
                            pharmaciesJson = json
                            Toast.makeText(context, "Node added to network directory!", Toast.LENGTH_SHORT).show()
                            showAddContactDialog = false
                        } catch(e: Exception) {
                            Toast.makeText(context, "Error saving contact: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                    modifier = Modifier.testTag("add_contact_confirm_button")
                ) {
                    Text("Add Node", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddContactDialog = false },
                    modifier = Modifier.testTag("add_contact_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (contactToEdit != null) {
        val original = contactToEdit!!
        var nameInput by remember { mutableStateOf(original.name) }
        var phoneInput by remember { mutableStateOf(original.phone) }
        var addressInput by remember { mutableStateOf(original.address) }
        var categoryInput by remember { mutableStateOf(original.category) }
        var stateInput by remember { mutableStateOf(original.state) }
        var lgaInput by remember { mutableStateOf(original.lga) }
        var isCategoryDropdownExpanded by remember { mutableStateOf(false) }
        var isStateDropdownExpanded by remember { mutableStateOf(false) }

        val defaultStates = listOf("Lagos", "Abuja", "Rivers", "Enugu", "Anambra", "Cross River", "Imo", "Akwa Ibom")
        var isCustomState by remember { mutableStateOf(!defaultStates.contains(original.state)) }
        var customStateInput by remember { mutableStateOf(if (!defaultStates.contains(original.state)) original.state else "") }

        AlertDialog(
            onDismissRequest = { contactToEdit = null },
            title = { Text("Edit Cooperative Node Contact", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Node Name") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_contact_name_input")
                    )

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_contact_phone_input")
                    )

                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        label = { Text("Full Address") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_contact_address_input")
                    )

                    // Category Selector Dropdown
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Category", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { isCategoryDropdownExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(categoryInput)
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(
                                expanded = isCategoryDropdownExpanded,
                                onDismissRequest = { isCategoryDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Independent Wholesaler") },
                                    onClick = {
                                        categoryInput = "Independent Wholesaler"
                                        isCategoryDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Independent Retailer") },
                                    onClick = {
                                        categoryInput = "Independent Retailer"
                                        isCategoryDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Medplus Chain Node") },
                                    onClick = {
                                        categoryInput = "Medplus Chain Node"
                                        isCategoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // State Selector Dropdown
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("State", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { isStateDropdownExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (isCustomState) "Other (Custom State)..." else stateInput)
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(
                                expanded = isStateDropdownExpanded,
                                onDismissRequest = { isStateDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Other (Custom State)...") },
                                    onClick = {
                                        isCustomState = true
                                        isStateDropdownExpanded = false
                                    }
                                )
                                defaultStates.forEach { stateName ->
                                    DropdownMenuItem(
                                        text = { Text(stateName) },
                                        onClick = {
                                            stateInput = stateName
                                            isCustomState = false
                                            isStateDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (isCustomState) {
                        OutlinedTextField(
                            value = customStateInput,
                            onValueChange = { customStateInput = it },
                            label = { Text("Custom State Name") },
                            placeholder = { Text("e.g., Oyo") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("edit_contact_custom_state_input")
                        )
                    }

                    OutlinedTextField(
                        value = lgaInput,
                        onValueChange = { lgaInput = it },
                        label = { Text("Local Government Area (LGA)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_contact_lga_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalState = if (isCustomState) customStateInput.trim() else stateInput
                        if (nameInput.isBlank() || phoneInput.isBlank() || addressInput.isBlank() || lgaInput.isBlank() || finalState.isBlank()) {
                            Toast.makeText(context, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val editedContact = VerifiedPharmacy(
                            name = nameInput.trim(),
                            phone = phoneInput.trim(),
                            address = addressInput.trim(),
                            category = categoryInput,
                            state = finalState,
                            lga = lgaInput.trim()
                        )

                        val updatedList = pharmaciesList.map {
                            if (it.name == original.name && it.phone == original.phone) {
                                editedContact
                            } else {
                                it
                            }
                        }
                        try {
                            val json = Json.encodeToString(updatedList)
                            sharedPrefs.edit().putString("all_pharmacies_v2", json).apply()
                            pharmaciesJson = json
                            Toast.makeText(context, "Node updated successfully!", Toast.LENGTH_SHORT).show()
                            contactToEdit = null
                        } catch(e: Exception) {
                            Toast.makeText(context, "Error saving changes: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                    modifier = Modifier.testTag("edit_contact_confirm_button")
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { contactToEdit = null },
                    modifier = Modifier.testTag("edit_contact_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (contactToDelete != null) {
        val target = contactToDelete!!
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Delete Network Entry?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove ${target.name} from the directory? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedList = pharmaciesList.filterNot { it.name == target.name && it.phone == target.phone }
                        try {
                            val json = Json.encodeToString(updatedList)
                            sharedPrefs.edit().putString("all_pharmacies_v2", json).apply()
                            pharmaciesJson = json
                            Toast.makeText(context, "Node removed from directory.", Toast.LENGTH_SHORT).show()
                            contactToDelete = null
                        } catch(e: Exception) {
                            Toast.makeText(context, "Error deleting: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("delete_contact_confirm_button")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { contactToDelete = null },
                    modifier = Modifier.testTag("delete_contact_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Static pharmacies list moved to StaticPharmacies.kt for performance and clean architecture

private fun formatRescuePrice(price: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "NG"))
    format.maximumFractionDigits = 0
    return format.format(price).replace("NGN", "₦").replace("NG", "₦").replace("¤", "₦")
}

@Serializable
internal data class VerifiedPharmacy(
    val name: String,
    val phone: String,
    val address: String,
    val category: String,
    val state: String,
    val lga: String
)
