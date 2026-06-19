package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.InventoryItem
import com.example.ui.theme.TealPrimary
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun ProcurementTabContent(
    inventory: List<InventoryItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // AI intelligent filtering: identifying items that are completely out of stock or critically below minimum.
    // Suggesting procurement amounts so that stock becomes 3x the minimum required, rounded up.
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

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Procurement List", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("AI suggested restock quantities", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Button(
                onClick = { 
                    exportProcurementList(context, procurementList) 
                },
                enabled = procurementList.isNotEmpty()
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Export")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export CSV")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

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
                        tint = TealPrimary.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Inventories Fully Stocked",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "All medical inventory items remain comfortably above their set threshold/safety stocks. No procurement re-orders required.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(procurementList) { (item, suggestedAmount) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Current Stock: ${item.stockQuantity} | Min Required: ${item.minRequiredStock}", style = MaterialTheme.typography.bodySmall)
                                if (item.brand.isNotBlank()) {
                                    Text("Brand: ${item.brand}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Need to order", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = "$suggestedAmount",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
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
