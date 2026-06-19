package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Receipt
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun ReceiptsTab(receipts: List<Receipt>, context: Context, onDeleteReceipt: (Receipt) -> Unit, onUpdateReceiptStatus: (Receipt, String) -> Unit) {
    val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
    var receiptToDelete by remember { mutableStateOf<Receipt?>(null) }
    var statusMenuFor by remember { mutableStateOf<Receipt?>(null) }
    val statuses = listOf("Pending", "Paid", "Rejected", "Cancelled")

    if (receiptToDelete != null) {
        AlertDialog(
            onDismissRequest = { receiptToDelete = null },
            title = { Text("Delete Receipt") },
            text = { Text("Are you sure you want to delete this receipt? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    receiptToDelete?.let { onDeleteReceipt(it) }
                    receiptToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { receiptToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        Text(
            text = "Receipts & Invoices History",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (receipts.isEmpty()) {
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
                        imageVector = Icons.Filled.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Transaction History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Generate custom digital invoices and print-ready receipts in the Shopping Cart layout.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(receipts.sortedByDescending { it.timestamp }) { receipt ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { showReceiptImage(context, receipt.imageFileName) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(receipt.customerName, fontWeight = FontWeight.Bold)
                                Text("₦${"%,.2f".format(receipt.totalAmount)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                val typeLabel = if (receipt.isInvoice) "Invoice" else "Receipt"
                                Text("$typeLabel • ${sdf.format(Date(receipt.timestamp))}", style = MaterialTheme.typography.bodySmall)
                            }

                            Box {
                                val statusBgColor = when (receipt.paymentStatus) {
                                    "Paid" -> if (com.example.ui.theme.AppThemeManager.isDark) Color(0xFF1B5E20) else Color(0xFFE8F5E9)
                                    "Pending" -> if (com.example.ui.theme.AppThemeManager.isDark) Color(0xFFE65100) else Color(0xFFFFF3E0)
                                    "Cancelled" -> if (com.example.ui.theme.AppThemeManager.isDark) Color(0xFFB71C1C) else Color(0xFFFFEBEE)
                                    else -> MaterialTheme.colorScheme.surface
                                }
                                val statusTextColor = when (receipt.paymentStatus) {
                                    "Paid" -> if (com.example.ui.theme.AppThemeManager.isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                                    "Pending" -> if (com.example.ui.theme.AppThemeManager.isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
                                    "Cancelled" -> if (com.example.ui.theme.AppThemeManager.isDark) Color(0xFFE57373) else Color(0xFFC62828)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }

                                SuggestionChip(
                                    onClick = { statusMenuFor = receipt },
                                    label = { Text(receipt.paymentStatus, color = statusTextColor, fontWeight = FontWeight.Bold) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = statusBgColor
                                    )
                                )
                                DropdownMenu(
                                    expanded = statusMenuFor == receipt,
                                    onDismissRequest = { statusMenuFor = null }
                                ) {
                                    statuses.forEach { st ->
                                        DropdownMenuItem(
                                            text = { Text(st) },
                                            onClick = {
                                                onUpdateReceiptStatus(receipt, st)
                                                statusMenuFor = null
                                            }
                                        )
                                    }
                                }
                            }
                            
                            IconButton(onClick = { receiptToDelete = receipt }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete Receipt", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun showReceiptImage(context: Context, fileName: String) {
    try {
        val file = File(context.filesDir, fileName)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/png")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Use createChooser so users can pick standard gallery/photo viewer if not default
            context.startActivity(Intent.createChooser(intent, "View Receipt"))
        } else {
            android.widget.Toast.makeText(context, "Receipt file not found", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Error opening receipt", android.widget.Toast.LENGTH_SHORT).show()
    }
}
