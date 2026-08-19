package com.example.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import com.example.data.AICarousel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun copyUriToInternalStorage(context: Context, uri: android.net.Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = java.io.File(context.filesDir, "slide_image_${System.currentTimeMillis()}.png")
        val outputStream = java.io.FileOutputStream(file)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

val MinimalistTheme = SlideTheme("Minimalist", Color(0xFFFAFAFA), Color(0xFF121212), Color(0xFF000000), Color(0xFFEEEEEE), true)
val BoldHealthTheme = SlideTheme("Bold Health", Color(0xFF0D47A1), Color.White, Color(0xFF64B5F6), Color(0xFF1565C0), false)
val SoftWellnessTheme = SlideTheme("Soft Wellness", Color(0xFFF1F8E9), Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFFC8E6C9), true)
val SunsetCareTheme = SlideTheme("Sunset Care", Color(0xFFFFF3E0), Color(0xFFE65100), Color(0xFFFF9800), Color(0xFFFFE0B2), true)
val DarkModePro = SlideTheme("Dark Mode Pro", Color(0xFF121212), Color(0xFFE0E0E0), Color(0xFFBB86FC), Color(0xFF1F1F1F), false)

val AllThemes = listOf(MinimalistTheme, BoldHealthTheme, SoftWellnessTheme, SunsetCareTheme, DarkModePro)

enum class SlideLayoutTheme(val displayName: String) {
    PHARMA_EDITORIAL("Pharma Editorial"),
    ASYMMETRIC_CARDS("Asymmetric Cards"),
    TECH_INFOGRAPHIC("Tech Infographic"),
    CLASSIC_MINIMAL("Classic Minimal")
}

data class SlideTheme(
    val name: String,
    val cardBg: Color,
    val textColor: Color,
    val accentColor: Color,
    val recommendedBg: Color,
    val isLight: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIContentEngineTab(viewModel: AIContentEngineViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    val promoState by viewModel.promoState.collectAsStateWithLifecycle()
    val inventoryItems by viewModel.inventoryItems.collectAsStateWithLifecycle()
    
    var activeSubTab by remember { mutableStateOf("carousel") } // "carousel" or "promo"
    
    var selectedTheme by remember { mutableStateOf(MinimalistTheme) }
    var isStoryFormat by remember { mutableStateOf(false) }
    var selectedLayoutTheme by remember { mutableStateOf(SlideLayoutTheme.PHARMA_EDITORIAL) }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (activeSubTab == "promo") "Promo Studio" else "AI Content Engine",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TealPrimary
                )
                Text(
                    text = if (activeSubTab == "promo") "Design high-converting multi-product grid layouts" else "Generate daily educational carousels automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMedium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (activeSubTab == "carousel") {
                if (uiState !is ContentEngineState.Idle && uiState !is ContentEngineState.Error) {
                    TextButton(
                        onClick = { viewModel.setIdle() },
                        colors = ButtonDefaults.textButtonColors(contentColor = TealPrimary)
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("History", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { viewModel.startSelection() },
                        enabled = uiState !is ContentEngineState.Generating && uiState !is ContentEngineState.SelectStrategy && uiState !is ContentEngineState.SelectSpecificProducts,
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Generate", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Content", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            } else {
                if (promoState.uiMode != PromoUiMode.Selection) {
                    TextButton(
                        onClick = { viewModel.clearPromoStudioState() },
                        colors = ButtonDefaults.textButtonColors(contentColor = TealPrimary)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Design", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Executive Pill Segmented Tab Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SlateBackgroundLight)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "carousel" to "Educational Carousel",
                "promo" to "Promo Studio (Grid)"
            ).forEach { (tabId, label) ->
                val isSelected = activeSubTab == tabId
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) TealPrimary else Color.Transparent)
                        .clickable { activeSubTab = tabId }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isSelected) Color.Black else SlateTextMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (activeSubTab == "carousel") {
            when (val state = uiState) {
            is ContentEngineState.Idle -> {
                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TealSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(20.dp).fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(TealPrimary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = "AI",
                                        tint = TealPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No content generated yet.",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Generate and queue high-conversions educational topics for your customers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.startSelection() },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Generate Fresh Content", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "History & Queue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = TealPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${history.size} Carousels",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(history) { carousel ->
                            Card(
                                onClick = { viewModel.viewHistoryItem(carousel) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = TealSurface),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(TealPrimary.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Dashboard,
                                                contentDescription = null,
                                                tint = TealPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = carousel.topicTitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val date = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(carousel.createdAt))
                                            Text(
                                                text = "Generated $date",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SlateTextMedium
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteCarousel(carousel) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = WarningRed.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is ContentEngineState.SelectStrategy -> {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text("Select Visual Theme:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        AllThemes.forEach { theme ->
                            FilterChip(
                                selected = selectedTheme == theme,
                                onClick = { selectedTheme = theme },
                                label = { Text(theme.name) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("What should we focus on?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    ElevatedCard(
                        onClick = { viewModel.generateCarouselWithStrategy("HighStock", selectedTheme.name) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("High Stock Items", fontWeight = FontWeight.Bold)
                                Text("Focus on products with high inventory", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }

                    ElevatedCard(
                        onClick = { viewModel.generateCarouselWithStrategy("Random", selectedTheme.name) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🎲", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Surprise Me", fontWeight = FontWeight.Bold)
                                Text("Pick 15 random products", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }

                    ElevatedCard(
                        onClick = { viewModel.startSpecificSelection() },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📦", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("+ Select Specific Products", fontWeight = FontWeight.Bold)
                                Text("Manually choose up to 15 items", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }

                    ElevatedCard(
                        onClick = { viewModel.startManualCreation(selectedTheme.name) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✍️", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Create Manually", fontWeight = FontWeight.Bold)
                                Text("Design a custom educational carousel slide-by-slide manually", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }
            
            is ContentEngineState.SelectSpecificProducts -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("Select Visual Theme:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        AllThemes.forEach { theme ->
                            FilterChip(
                                selected = selectedTheme == theme,
                                onClick = { selectedTheme = theme },
                                label = { Text(theme.name) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Select up to 15 items (${state.selectedIds.size}/15)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.inventory) { item ->
                            val isSelected = state.selectedIds.contains(item.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleProductSelection(item.id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleProductSelection(item.id) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(item.name, fontWeight = FontWeight.Bold)
                                    Text("${item.stockQuantity} in stock • ${item.category}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.generateCarouselFromSelection(selectedTheme.name) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.selectedIds.isNotEmpty()
                    ) {
                        Text("Continue")
                    }
                }
            }

            is ContentEngineState.Generating -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(state.message, color = Color.Gray)
                    }
                }
            }
            is ContentEngineState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.message == "GEMINI_429_RATE_LIMIT") {
                            Icon(
                                imageVector = Icons.Filled.CloudOff,
                                contentDescription = "Rate limit active",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "AI Generation Limit Reached",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "The background AI engine is temporarily rate-limited. No worries! Our CareFlux system enables you to bypass this instantly by designing, phrasing, and customizing your educational slides manually offline.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(28.dp))
                            Button(
                                onClick = { viewModel.startManualCreation(selectedTheme.name) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create Carousel Manually")
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.setIdle() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Back to Drafts & History")
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "Error",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "An Error Occurred",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Red,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.setIdle() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Go Back")
                            }
                        }
                    }
                }
            }
            is ContentEngineState.ManualCreation -> {
                ManualCarouselCreation(state = state, viewModel = viewModel)
            }
            is ContentEngineState.Success -> {
                val carousel = state.carousel
                val theme = AllThemes.find { it.name == state.theme } ?: MinimalistTheme
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    OutlinedTextField(
                        value = carousel.topicTitle,
                        onValueChange = { viewModel.updateCarouselMeta(it, carousel.caption) },
                        label = { Text("Carousel Main Topic") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = carousel.caption,
                        onValueChange = { viewModel.updateCarouselMeta(carousel.topicTitle, it) },
                        label = { Text("Carousel Caption/Subheading") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Format:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = !isStoryFormat,
                            onClick = { isStoryFormat = false },
                            label = { Text("Feed (4:5)") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = isStoryFormat,
                            onClick = { isStoryFormat = true },
                            label = { Text("Story (9:16)") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Design Style:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SlideLayoutTheme.values().forEach { style ->
                            FilterChip(
                                selected = selectedLayoutTheme == style,
                                onClick = { selectedLayoutTheme = style },
                                label = { Text(style.displayName) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        itemsIndexed(carousel.slides) { index, slide ->
                            SlidePreview(slide, index, carousel.slides.size, isStoryFormat, theme, selectedLayoutTheme, context, viewModel)
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(400.dp)
                                    .clickable { viewModel.addSlide() },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Filled.Add, contentDescription = "Add Slide", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Add Slide", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val carouselCoroutineScope = rememberCoroutineScope()

                    MusicAndVideoExportSection(
                        viewModel = viewModel,
                        promoState = promoState,
                        context = context,
                        coroutineScope = carouselCoroutineScope,
                        getFrames = {
                            val frames = mutableListOf<android.graphics.Bitmap>()
                            carousel.slides.forEachIndexed { idx, slide ->
                                val bmp = renderSlideToBitmap(context, slide, idx, carousel.slides.size, theme)
                                frames.add(bmp)
                            }
                            frames
                        }
                    )
                }
            }
        }
    } else {
        PromoStudioContent(
            viewModel = viewModel,
            state = promoState,
            inventory = inventoryItems,
            context = context
        )
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideEditDialog(
    slide: CarouselSlide,
    onDismissRequest: () -> Unit,
    onSave: (CarouselSlide) -> Unit
) {
    val context = LocalContext.current
    var heading by remember { mutableStateOf(slide.heading) }
    var text by remember { mutableStateOf(slide.text) }
    var imageUri by remember { mutableStateOf(slide.imageUri) }
    val keyPoints = remember { mutableStateListOf<String>().apply { addAll(slide.keyPoints) } }
    val recommendedProducts = remember { mutableStateListOf<String>().apply { addAll(slide.recommendedProducts ?: emptyList()) } }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val savedPath = copyUriToInternalStorage(context, uri)
                if (savedPath != null) {
                    imageUri = savedPath
                } else {
                    Toast.makeText(context, "Error processing picked image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Slide #${slide.slideNumber} Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = heading,
                    onValueChange = { heading = it },
                    label = { Text("Heading / Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Body Paragraph / Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Slide Image (Optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (!imageUri.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            coil.compose.AsyncImage(
                                model = imageUri,
                                contentDescription = "Picked Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            
                            IconButton(
                                onClick = { imageUri = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove Image", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = "Choose Image")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload / Choose Image")
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bullet Key Points", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        IconButton(
                            onClick = { keyPoints.add("New key point text") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Key Point", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    if (keyPoints.isEmpty()) {
                        Text("No key points added. Click + to add one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        keyPoints.forEachIndexed { idx, point ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = point,
                                    onValueChange = { keyPoints[idx] = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { keyPoints.removeAt(idx) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recommended Products", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        IconButton(
                            onClick = { recommendedProducts.add("Recommended Product Name") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Recommended Product", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (recommendedProducts.isEmpty()) {
                        Text("No products recommended on this slide. Click + to add.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        recommendedProducts.forEachIndexed { idx, prod ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = prod,
                                    onValueChange = { recommendedProducts[idx] = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { recommendedProducts.removeAt(idx) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
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
                    val updatedSlide = slide.copy(
                        heading = heading,
                        text = text,
                        keyPoints = keyPoints.toList(),
                        recommendedProducts = if (recommendedProducts.isEmpty()) null else recommendedProducts.toList(),
                        imageUri = imageUri
                    )
                    onSave(updatedSlide)
                    onDismissRequest()
                }
            ) {
                Text("Apply & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SlidePreview(
    slide: CarouselSlide, 
    index: Int, 
    totalSlides: Int,
    isStoryFormat: Boolean, 
    theme: SlideTheme, 
    layoutTheme: SlideLayoutTheme,
    context: Context, 
    viewModel: AIContentEngineViewModel
) {
    val aspectRatio = if (isStoryFormat) 9f / 16f else 4f / 5f
    val height = 400.dp 
    val width = height * aspectRatio
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        SlideEditDialog(
            slide = slide,
            onDismissRequest = { showEditDialog = false },
            onSave = { updatedSlide ->
                viewModel.updateSlide(index, updatedSlide)
            }
        )
    }

    Column(modifier = Modifier.width(width)) {
        Card(
            modifier = Modifier.fillMaxWidth().height(height),
            colors = CardDefaults.cardColors(containerColor = theme.cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            SlideContent(
                slide = slide,
                theme = theme,
                layoutTheme = layoutTheme,
                totalSlides = totalSlides,
                onHeadingChange = { newHeading ->
                    viewModel.updateSlide(index, slide.copy(heading = newHeading))
                },
                onTextChange = { newText ->
                    viewModel.updateSlide(index, slide.copy(text = newText))
                }
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.moveSlide(index, -1) },
                enabled = index > 0,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Move Left", modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { viewModel.duplicateSlide(index) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { viewModel.deleteSlide(index) },
                enabled = totalSlides > 1,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            }
            IconButton(
                onClick = { viewModel.moveSlide(index, 1) },
                enabled = index < totalSlides - 1,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "Move Right", modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showEditDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = theme.textColor
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, theme.textColor.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit Elements", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit Details", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = {
                    val density = context.resources.displayMetrics.density
                    val aspectRatioVal = if (isStoryFormat) 9f / 16f else 4f / 5f
                    val exportWidthPx = 1080
                    val exportHeightPx = (exportWidthPx / aspectRatioVal).toInt()
                    
                    try {
                        val result = SlideImageGenerator.generateSlide(context, slide, theme, isStoryFormat, layoutTheme, totalSlides)
                        val uri = result.first
                        
                        if (uri != null) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "image/png")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "View Slide"))
                        } else {
                            Toast.makeText(context, "Failed to capture slide.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.weight(1.2f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.accentColor,
                    contentColor = if (theme.isLight) Color.White else Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = "View", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("View Image", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SlideContent(
    slide: CarouselSlide, 
    theme: SlideTheme,
    layoutTheme: SlideLayoutTheme,
    totalSlides: Int,
    onHeadingChange: (String) -> Unit,
    onTextChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.cardBg)
            .drawBehind {
                // Background watermarks & decorations depending on layout
                when (layoutTheme) {
                    SlideLayoutTheme.PHARMA_EDITORIAL -> {
                        // Background watermark based on slide purpose
                        val decorativeColor = theme.accentColor
                        if (slide.slideNumber == 1 || slide.heading.contains("Water", true) || slide.heading.contains("Dehydration", true)) {
                            // Draw Glass of Water watermark
                            val left = size.width * 0.7f
                            val right = size.width * 0.95f
                            val top = size.height * 0.45f
                            val bottom = size.height * 0.75f
                            val glassPath = Path().apply {
                                moveTo(left, top)
                                lineTo(left + 15.dp.toPx(), bottom)
                                quadraticTo(size.width * 0.825f, bottom + 8.dp.toPx(), right - 15.dp.toPx(), bottom)
                                lineTo(right, top)
                            }
                            drawPath(glassPath, decorativeColor.copy(alpha = 0.08f), style = Stroke(width = 2.dp.toPx()))
                            
                            val waterLevel = top + 25.dp.toPx()
                            val waterPath = Path().apply {
                                moveTo(left + 4.dp.toPx(), waterLevel)
                                lineTo(left + 15.dp.toPx(), bottom)
                                quadraticTo(size.width * 0.825f, bottom + 8.dp.toPx(), right - 15.dp.toPx(), bottom)
                                lineTo(right - 4.dp.toPx(), waterLevel)
                                quadraticTo(size.width * 0.825f, waterLevel - 4.dp.toPx(), left + 4.dp.toPx(), waterLevel)
                            }
                            drawPath(waterPath, decorativeColor.copy(alpha = 0.05f))
                        } else if (slide.slideNumber == 2 || slide.heading.contains("Cause", true) || slide.heading.contains("diarrhea", true)) {
                            // Draw stylized Intestinal gut loop watermark
                            val centerX = size.width * 0.82f
                            val centerY = size.height * 0.6f
                            val loopPath = Path().apply {
                                moveTo(centerX - 35.dp.toPx(), centerY - 45.dp.toPx())
                                cubicTo(centerX + 35.dp.toPx(), centerY - 38.dp.toPx(), centerX - 42.dp.toPx(), centerY - 8.dp.toPx(), centerX + 8.dp.toPx(), centerY)
                                cubicTo(centerX + 50.dp.toPx(), centerY + 8.dp.toPx(), centerX - 35.dp.toPx(), centerY + 38.dp.toPx(), centerX, centerY + 45.dp.toPx())
                            }
                            drawPath(loopPath, decorativeColor.copy(alpha = 0.1f), style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        } else {
                            // Medical shield watermark
                            val centerX = size.width * 0.82f
                            val centerY = size.height * 0.6f
                            val sizePx = 45.dp.toPx()
                            val shieldPath = Path().apply {
                                moveTo(centerX, centerY - sizePx)
                                quadraticTo(centerX + sizePx, centerY - sizePx, centerX + sizePx, centerY)
                                quadraticTo(centerX + sizePx, centerY + sizePx * 0.7f, centerX, centerY + sizePx)
                                quadraticTo(centerX - sizePx, centerY + sizePx * 0.7f, centerX - sizePx, centerY)
                                quadraticTo(centerX - sizePx, centerY - sizePx, centerX, centerY - sizePx)
                            }
                            drawPath(shieldPath, decorativeColor.copy(alpha = 0.06f))
                            drawPath(shieldPath, decorativeColor.copy(alpha = 0.08f), style = Stroke(width = 1.dp.toPx()))
                        }
                    }
                    SlideLayoutTheme.ASYMMETRIC_CARDS -> {
                        // Draw soft concentric background circles
                        val tint = theme.accentColor.copy(alpha = 0.04f)
                        drawCircle(tint, radius = 100.dp.toPx(), center = Offset(size.width * 0.9f, size.height * 0.2f))
                        drawCircle(tint, radius = 160.dp.toPx(), center = Offset(size.width * 0.1f, size.height * 0.8f))
                    }
                    SlideLayoutTheme.TECH_INFOGRAPHIC -> {
                        // Draw a technical background grid
                        val gridColor = theme.textColor.copy(alpha = 0.03f)
                        val step = 30.dp.toPx()
                        var x = 0f
                        while (x < size.width) {
                            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            x += step
                        }
                        var y = 0f
                        while (y < size.height) {
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            y += step
                        }
                    }
                    SlideLayoutTheme.CLASSIC_MINIMAL -> {
                        // Fine editorial top/bottom horizontal layout borders
                        drawLine(
                            color = theme.textColor.copy(alpha = 0.1f),
                            start = Offset(24.dp.toPx(), 48.dp.toPx()),
                            end = Offset(size.width - 24.dp.toPx(), 48.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = theme.textColor.copy(alpha = 0.1f),
                            start = Offset(24.dp.toPx(), size.height - 48.dp.toPx()),
                            end = Offset(size.width - 24.dp.toPx(), size.height - 48.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            
            // 1. Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo brand
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                color = if (theme.isLight) theme.accentColor else Color(0xFFFFB300),
                                shape = RoundedCornerShape(3.dp)
                            )
                            .drawBehind {
                                val sizePx = size.width
                                val thick = 4.dp.toPx()
                                val len = sizePx * 0.7f
                                // horizontal line of cross
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset((sizePx - len) / 2f, (sizePx - thick) / 2f),
                                    size = Size(len, thick)
                                )
                                // vertical line of cross
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset((sizePx - thick) / 2f, (sizePx - len) / 2f),
                                    size = Size(thick, len)
                                )
                            }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CAREFLUX",
                        color = theme.textColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp
                    )
                }
                
                // Progress Circular Fraction
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            color = if (theme.isLight) Color(0xFF111111) else theme.accentColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${slide.slideNumber}/$totalSlides",
                        color = if (theme.isLight) Color.White else Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(18.dp))
            
            // 2. Headings With Custom Underlines / Highlights!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (layoutTheme == SlideLayoutTheme.PHARMA_EDITORIAL) {
                            // Draw beautiful hand-drawn wavy curve stroke beneath the heading words
                            val y = size.height - 1.dp.toPx()
                            val strokeWidthVal = 3.dp.toPx()
                            // Hand-drawn wavy brush using curve equations
                            val wavePath = Path().apply {
                                moveTo(0f, y)
                                val segments = 5
                                val segWidth = size.width / segments
                                val waveHeight = 3.dp.toPx()
                                for (i in 0 until segments) {
                                    val startX = i * segWidth
                                    val endX = (i + 1) * segWidth
                                    val midX = startX + segWidth / 2f
                                    val ctrlY = if (i % 2 == 0) y + waveHeight else y - waveHeight
                                    quadraticTo(midX, ctrlY, endX, y)
                                }
                            }
                            drawPath(
                                path = wavePath,
                                color = Color(0xFFFFB300), // Solid beautiful gold highlighted color
                                style = Stroke(width = strokeWidthVal, cap = StrokeCap.Round)
                            )
                        } else if (layoutTheme == SlideLayoutTheme.TECH_INFOGRAPHIC) {
                            // Bold blocky side line
                            drawLine(
                                color = theme.accentColor,
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }
                    .padding(
                        start = if (layoutTheme == SlideLayoutTheme.TECH_INFOGRAPHIC) 12.dp else 0.dp,
                        bottom = if (layoutTheme == SlideLayoutTheme.PHARMA_EDITORIAL) 8.dp else 0.dp
                    )
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = slide.heading,
                    onValueChange = onHeadingChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = theme.textColor, 
                        fontSize = if (layoutTheme == SlideLayoutTheme.PHARMA_EDITORIAL) 23.sp else 20.sp, 
                        fontWeight = FontWeight.Black, 
                        lineHeight = 28.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // 3. Body Text Statement
            androidx.compose.foundation.text.BasicTextField(
                value = slide.text,
                onValueChange = onTextChange,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = theme.textColor.copy(alpha = 0.85f), 
                    fontSize = 13.5.sp,
                    lineHeight = 18.2.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (!slide.imageUri.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, theme.textColor.copy(alpha = 0.15f))
                ) {
                    coil.compose.AsyncImage(
                        model = slide.imageUri,
                        contentDescription = "Slide Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // 4. Keypoints List (Or Asymmetric Cards / Sharp Tech Infographics!)
            if (slide.keyPoints.isNotEmpty()) {
                when (layoutTheme) {
                    SlideLayoutTheme.PHARMA_EDITORIAL, SlideLayoutTheme.CLASSIC_MINIMAL -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slide.keyPoints.forEach { point ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = theme.accentColor,
                                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = point,
                                        color = theme.textColor,
                                        fontSize = 12.5.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                    SlideLayoutTheme.ASYMMETRIC_CARDS -> {
                        // Display beautiful staggered transparent cards with index numbers!
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slide.keyPoints.forEachIndexed { i, point ->
                                val cardOffset = if (i % 2 == 1) 6.dp else 0.dp
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(x = cardOffset),
                                    colors = CardDefaults.cardColors(
                                        containerColor = theme.accentColor.copy(alpha = 0.08f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .background(theme.accentColor.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "0${i + 1}",
                                                color = theme.textColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = point,
                                            color = theme.textColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                    SlideLayoutTheme.TECH_INFOGRAPHIC -> {
                        // Clean bold items with border outlines
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slide.keyPoints.forEach { point ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(theme.textColor.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                        .border(1.dp, theme.textColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = null,
                                            tint = theme.accentColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = point,
                                            color = theme.textColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 5. Recommended products
            if (!slide.recommendedProducts.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = theme.recommendedBg,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, theme.accentColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.ShoppingCart,
                                contentDescription = null,
                                tint = theme.accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Available on CareFlux:",
                                fontWeight = FontWeight.Bold,
                                color = theme.accentColor,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        slide.recommendedProducts.forEach { prod ->
                            Text(
                                text = "✔ $prod", 
                                color = theme.textColor.copy(alpha = 0.9f), 
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // 6. Action swipe tip
            if (slide.slideNumber < totalSlides) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Swipe to learn more ->",
                        color = theme.textColor.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✔ Expert Recommended",
                        color = theme.accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "✔ Trusted Safety",
                        color = theme.accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromoStudioContent(
    viewModel: AIContentEngineViewModel,
    state: PromoStudioState,
    inventory: List<com.example.data.InventoryItem>,
    context: android.content.Context
) {
    val persistentCoroutineScope = rememberCoroutineScope()
    when (state.uiMode) {
        PromoUiMode.Selection -> {
            PromoSelectionScreen(
                viewModel = viewModel,
                state = state,
                inventory = inventory
            )
        }
        PromoUiMode.Configuration -> {
            CarefluxLiveStudioEditor(
                viewModel = viewModel,
                state = state,
                context = context,
                coroutineScope = persistentCoroutineScope
            )
        }
        PromoUiMode.Generating -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Rendering High-Res 1080x1080 Promotional Grid...",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Preparing synchronous bitmap caches to prevent lag.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
        PromoUiMode.Success -> {
            PromoSuccessScreen(
                viewModel = viewModel,
                state = state,
                context = context
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromoSelectionScreen(
    viewModel: AIContentEngineViewModel,
    state: PromoStudioState,
    inventory: List<com.example.data.InventoryItem>
) {
    var searchQuery by remember { mutableStateOf("") }
    var showHelpInfo by remember { mutableStateOf(false) }
    
    val filteredItems = remember(inventory, searchQuery) {
        if (searchQuery.isBlank()) {
            inventory
        } else {
            inventory.filter { item ->
                item.name.contains(searchQuery, ignoreCase = true) ||
                        item.category.contains(searchQuery, ignoreCase = true) ||
                        item.brand.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Promo Grid Selection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showHelpInfo = !showHelpInfo },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Help Info",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (state.selectedItems.size == 4) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "${state.selectedItems.size} / 4 Selected",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (state.selectedItems.size == 4) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (showHelpInfo) {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Select 1 to 4 products from your inventory to compose a beautiful promotional grid graphic with custom discount prices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (state.selectedItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    state.selectedItems.forEach { item ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.togglePromoProductSelection(item) },
                            label = { 
                                Text(
                                    text = item.name, 
                                    maxLines = 1, 
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, 
                                    fontSize = 11.sp, 
                                    modifier = Modifier.widthIn(max = 100.dp)
                                ) 
                            },
                            trailingIcon = { 
                                Icon(
                                    imageVector = Icons.Filled.Cancel, 
                                    contentDescription = "Remove", 
                                    modifier = Modifier.size(14.dp)
                                ) 
                            }
                        )
                    }
                }
            }
        }

        // Search bar (Using BasicTextField to prevent vertical clipping)
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
                .height(40.dp),
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
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "SearchIcon",
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
                                text = "Search inventory...",
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
                                contentDescription = "ClearSearch",
                                tint = SlateTextMedium,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // List of items
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (filteredItems.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No matching items found.", color = Color.Gray)
                    }
                }
            } else {
                items(filteredItems) { item ->
                    val isChecked = state.selectedItems.any { it.id == item.id }
                    
                    ElevatedCard(
                        onClick = { viewModel.togglePromoProductSelection(item) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.togglePromoProductSelection(item) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Small Thumbnail representation or Initials
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!item.imageUri.isNullOrEmpty()) {
                                    coil.compose.AsyncImage(
                                        model = item.imageUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text(item.name.firstOrNull()?.toString()?.uppercase() ?: "P", fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("${item.brand.ifEmpty { "Generic" }} • ₦${String.format("%,.2f", item.price)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { viewModel.setPromoUiMode(PromoUiMode.Configuration) },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            enabled = state.selectedItems.isNotEmpty(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Configure Layout & Offers (${state.selectedItems.size} Selected)")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromoConfigurationScreen(
    viewModel: AIContentEngineViewModel,
    state: PromoStudioState,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            IconButton(onClick = { viewModel.setPromoUiMode(PromoUiMode.Selection) }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Configure Grid Offers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Text(
            text = "Customize promotional details below without changing your master database. Prices will render with elegant currency styling.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Select Professional Graphic Design Template:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(FlyerTemplateStyle.values()) { tStyle ->
                val isSelected = tStyle == state.templateStyle
                Card(
                    onClick = { viewModel.updateFlyerTemplateStyle(tStyle) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .width(160.dp)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = tStyle.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tStyle.subtitle,
                            fontSize = 10.sp,
                            color = Color.Gray,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Text(
            text = "Pharmacy & Branding Header (Editable):",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                OutlinedTextField(
                    value = state.pharmacyName,
                    onValueChange = { viewModel.updateFlyerHeaderDetails(pharmacyName = it) },
                    label = { Text("Pharmacy Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.pharmacySlogan,
                    onValueChange = { viewModel.updateFlyerHeaderDetails(pharmacySlogan = it) },
                    label = { Text("Pharmacy Slogan / Slogan Line") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.badgeEmblemText,
                    onValueChange = { viewModel.updateFlyerHeaderDetails(badgeEmblemText = it) },
                    label = { Text("3D Starburst Emblem Text") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        state.selectedItems.forEachIndexed { index, item ->
            val customPriceStr = state.priceOverrides[item.id] ?: ""
            val customNameStr = state.nameOverrides[item.id] ?: ""
            val customDosageStr = state.dosageOverrides[item.id] ?: item.dosage
            val customBullet1 = state.featureBullet1Overrides[item.id] ?: "Accurate & Reliable"
            val customBullet2 = state.featureBullet2Overrides[item.id] ?: "Easy Self-Testing"

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!item.imageUri.isNullOrEmpty()) {
                                coil.compose.AsyncImage(
                                    model = item.imageUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Text(item.name.firstOrNull()?.toString()?.uppercase() ?: "P", fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Product #${index + 1}: ${item.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customNameStr,
                        onValueChange = { viewModel.updatePromoNameOverride(item.id, it) },
                        label = { Text("Display Product Name") },
                        placeholder = { Text(item.name) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customPriceStr,
                        onValueChange = { viewModel.updatePromoPriceOverride(item.id, it) },
                        label = { Text("Promotional Price (₦)") },
                        placeholder = { Text(String.format("%,.2f", item.price)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customDosageStr,
                        onValueChange = { viewModel.updatePromoDosageOverride(item.id, it) },
                        label = { Text("Dosage / Size Specs") },
                        placeholder = { Text(item.dosage) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.templateStyle == FlyerTemplateStyle.PRO_MEDICAL_GRID) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customBullet1,
                            onValueChange = { viewModel.updatePromoFeatureBullet1(item.id, it) },
                            label = { Text("Feature Bullet #1") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customBullet2,
                            onValueChange = { viewModel.updatePromoFeatureBullet2(item.id, it) },
                            label = { Text("Feature Bullet #2") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (state.templateStyle == FlyerTemplateStyle.MEDICAL_OUTREACH) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Medical Outreach Event Details (Editable):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = state.outreachOccasion,
                        onValueChange = { viewModel.updateOutreachDetails(occasion = it) },
                        label = { Text("Occasion / Ribbon Badge") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.outreachTitle,
                        onValueChange = { viewModel.updateOutreachDetails(title = it) },
                        label = { Text("Main Outreach Title") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.outreachMessage,
                        onValueChange = { viewModel.updateOutreachDetails(message = it) },
                        label = { Text("Thank You Message Text") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.outreachDate,
                        onValueChange = { viewModel.updateOutreachDetails(date = it) },
                        label = { Text("Event Date") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.outreachTime,
                        onValueChange = { viewModel.updateOutreachDetails(time = it) },
                        label = { Text("Event Time") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.outreachLocation,
                        onValueChange = { viewModel.updateOutreachDetails(location = it) },
                        label = { Text("Event Location Address") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Footer Tagline & Sign-off:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = state.footerTagline,
            onValueChange = { viewModel.updateFlyerHeaderDetails(footerTagline = it) },
            label = { Text("Footer Signature / Slogan Line") },
            placeholder = { Text("Your health, our priority. ♡") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Choose Design Color Theme:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(PromoThemeStyle.values()) { theme ->
                val isSelected = theme == state.promoTheme
                val displayBg = if (theme.isDark) theme.backgroundColor else theme.backgroundColor.copy(alpha = 0.9f)
                val displayBorder = if (isSelected) theme.accentColor else theme.cardBorderColor

                Card(
                    onClick = { viewModel.updatePromoTheme(theme) },
                    colors = CardDefaults.cardColors(
                        containerColor = displayBg
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .width(130.dp)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = displayBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(theme.accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (theme.isDark) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = theme.displayName,
                            color = theme.headerTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (theme.isDark) "Dark Theme" else "Light Theme",
                            color = theme.productMutedColor,
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.setPromoUiMode(PromoUiMode.Generating)
                coroutineScope.launch {
                    try {
                        // 1. Pre-resolve bitmaps on a background thread in parallel first to avoid any latency
                        val resolvedBitmaps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            kotlinx.coroutines.coroutineScope {
                                val map = mutableMapOf<Int, android.graphics.Bitmap>()
                                val deferreds = state.selectedItems.map { item ->
                                    item to async {
                                        PromoImageGenerator.loadUriAsBitmap(context, item.imageUri)
                                    }
                                }
                                deferreds.forEach { (item, deferred) ->
                                    try {
                                        val bmp = deferred.await()
                                        if (bmp != null) {
                                            map[item.id] = bmp
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                map
                            }
                        }

                        // 2. Generate the promotional grid layout synchronously on the Main thread (already in this coroutine)
                        val result = PromoImageGenerator.generatePromoGrid(
                            context = context,
                            selectedItems = state.selectedItems,
                            priceOverrides = state.priceOverrides,
                            nameOverrides = state.nameOverrides,
                            isOfferBanner = state.isOfferBanner,
                            resolvedBitmaps = resolvedBitmaps,
                            subheader = state.subheader,
                            promoTheme = state.promoTheme,
                            state = state
                        )

                        if (result.first != null) {
                            viewModel.setPromoGeneratedUri(result.first)
                            viewModel.setPromoUiMode(PromoUiMode.Success)
                        } else {
                            viewModel.setPromoUiMode(PromoUiMode.Configuration)
                            Toast.makeText(context, "Error compiling layout bounds.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        viewModel.setPromoUiMode(PromoUiMode.Configuration)
                        Toast.makeText(context, "Rendering failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate High-Res Grid Graphic", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarefluxLiveStudioEditor(
    viewModel: AIContentEngineViewModel,
    state: PromoStudioState,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val productImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = copyUriToInternalStorage(context, uri)
            if (savedPath != null) {
                val activeIdx = when (state.activeEditSection) {
                    "product_0" -> 0
                    "product_1" -> 1
                    "product_2" -> 2
                    "product_3" -> 3
                    else -> 0
                }
                viewModel.updateMasterProductImageUri(activeIdx, savedPath)
                Toast.makeText(context, "Product image updated!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not process selected image. Please try another.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = { viewModel.setPromoUiMode(PromoUiMode.Selection) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.padding(end = 6.dp)) {
                    Text(
                        "Pixel-Perfect Live Editor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        "Tap elements on flyer preview below to edit live",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // Export PNG Button
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            Toast.makeText(context, "Generating high-resolution PNG...", Toast.LENGTH_SHORT).show()
                            val resolvedBitmaps = withContext(Dispatchers.IO) {
                                val map = mutableMapOf<Int, android.graphics.Bitmap>()
                                state.selectedItems.forEachIndexed { index, item ->
                                    val prodSlot = state.products.getOrNull(index)
                                    val targetUri = item.imageUri?.ifEmpty { null } ?: prodSlot?.imageUri?.ifEmpty { null }
                                    var bmp: android.graphics.Bitmap? = null
                                    if (!targetUri.isNullOrEmpty()) {
                                        bmp = PromoImageGenerator.loadUriAsBitmap(context, targetUri)
                                    } else if (prodSlot?.drawableResId != null) {
                                        bmp = PromoImageGenerator.loadDrawableAsBitmap(context, prodSlot.drawableResId)
                                    }
                                    if (bmp != null) {
                                        map[item.id] = bmp
                                        if (prodSlot != null) {
                                            map[prodSlot.id] = bmp
                                        }
                                    }
                                }
                                state.products.forEach { prod ->
                                    if (!map.containsKey(prod.id)) {
                                        var bmp: android.graphics.Bitmap? = null
                                        if (!prod.imageUri.isNullOrEmpty()) {
                                            bmp = PromoImageGenerator.loadUriAsBitmap(context, prod.imageUri)
                                        } else if (prod.drawableResId != null) {
                                            bmp = PromoImageGenerator.loadDrawableAsBitmap(context, prod.drawableResId)
                                        }
                                        if (bmp != null) map[prod.id] = bmp
                                    }
                                }
                                map
                            }

                            val result = PromoImageGenerator.generatePromoGrid(
                                context = context,
                                selectedItems = state.selectedItems,
                                priceOverrides = state.priceOverrides,
                                nameOverrides = state.nameOverrides,
                                isOfferBanner = state.isOfferBanner,
                                resolvedBitmaps = resolvedBitmaps,
                                subheader = state.subheader,
                                promoTheme = state.promoTheme,
                                state = state
                            )
                            if (result.first != null) {
                                viewModel.setPromoGeneratedUri(result.first)
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(android.content.Intent.EXTRA_STREAM, result.first)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Studio Design PNG"))
                                Toast.makeText(context, "PNG Export Ready!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Error rendering flyer.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Rendering error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export PNG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Live Canvas Interactive Preview
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                CarefluxMasterTemplateLayout(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onSectionClick = { section ->
                        viewModel.updateActiveEditSection(section)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Section Selector Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val sections = listOf(
                "product_0" to "Prod #1",
                "product_1" to "Prod #2",
                "product_2" to "Prod #3",
                "product_3" to "Prod #4",
                "header" to "Header",
                "features" to "Features",
                "background" to "Canvas"
            )

            sections.forEach { (secKey, secLabel) ->
                val isSelected = state.activeEditSection == secKey
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.updateActiveEditSection(secKey) },
                    label = { Text(secLabel, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TealPrimary,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Editor Form Panel based on activeEditSection
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when {
                    state.activeEditSection.startsWith("product_") -> {
                        val pIdx = state.activeEditSection.removePrefix("product_").toIntOrNull() ?: 0
                        val prod = state.products.getOrElse(pIdx) { state.products[0] }

                        Text(
                            text = "Editing Product #${pIdx + 1}: ${prod.name.replace("\n", " ")}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Custom Image Upload Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Product Image:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Button(
                                onClick = { productImagePickerLauncher.launch("image/*") },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload Custom Image", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = prod.name,
                            onValueChange = { viewModel.updateMasterProductName(pIdx, it) },
                            label = { Text("Product Display Name") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = prod.subtitle,
                            onValueChange = { viewModel.updateMasterProductSubtitle(pIdx, it) },
                            label = { Text("Product Subtitle / Size / Specs") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = prod.price,
                            onValueChange = { viewModel.updateMasterProductPrice(pIdx, it) },
                            label = { Text("Promotional Price (₦)") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Card Background Tint Selector
                        Text("Card Container Tint Color:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        val bgTints = listOf(
                            "#E8ECE0" to "Olive",
                            "#E3EAF7" to "Periwinkle",
                            "#FAF2D8" to "Soft Yellow",
                            "#E3EDE2" to "Mint",
                            "#FFFFFF" to "Pure White",
                            "#FFEEEA" to "Coral",
                            "#FAF8F2" to "Cream"
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(bgTints) { (hex, name) ->
                                val color = parseHexColor(hex, Color.White)
                                val isSel = prod.bgTintHex.equals(hex, ignoreCase = true)
                                FilterChip(
                                    selected = isSel,
                                    onClick = { viewModel.updateMasterProductBgTint(pIdx, hex) },
                                    label = { Text(name, fontSize = 10.sp) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(1.dp, Color.Gray, CircleShape)
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Badge Icon Selector
                        Text("Badge Emblem Icon:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        val badgeIcons = listOf(
                            "droplet" to "Droplet 💧",
                            "moon" to "Moon 🌙",
                            "lightning" to "Lightning ⚡",
                            "leaf" to "Leaf 🍃",
                            "shield" to "Shield 🛡️",
                            "medical" to "Medical 🏥"
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(badgeIcons) { (iconKey, label) ->
                                val isSel = prod.badgeIcon == iconKey
                                FilterChip(
                                    selected = isSel,
                                    onClick = { viewModel.updateMasterProductBadgeIcon(pIdx, iconKey) },
                                    label = { Text(label, fontSize = 10.sp) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    state.activeEditSection == "header" -> {
                        Text(
                            text = "Editing Business Header & Signature",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = state.pharmacyName,
                            onValueChange = { viewModel.updatePharmacyHeader(it, state.pharmacySubtitle, state.pharmacySlogan) },
                            label = { Text("Business Name") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = state.pharmacySubtitle,
                            onValueChange = { viewModel.updatePharmacyHeader(state.pharmacyName, it, state.pharmacySlogan) },
                            label = { Text("Business Subtitle") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = state.pharmacySlogan,
                            onValueChange = { viewModel.updatePharmacyHeader(state.pharmacyName, state.pharmacySubtitle, it) },
                            label = { Text("Tagline / Slogan Line") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = state.footerTagline,
                            onValueChange = { viewModel.updateFlyerHeaderDetails(footerTagline = it) },
                            label = { Text("Bottom Cursive Signature Text") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    state.activeEditSection == "features" -> {
                        Text(
                            text = "Editing Bottom Feature Trust Bar (4 Items)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = state.trustBadge1Title,
                            onValueChange = { viewModel.updateTrustBadges(it, state.trustBadge1Sub, state.trustBadge2Title, state.trustBadge2Sub, state.trustBadge3Title, state.trustBadge3Sub, state.trustBadge4Title, state.trustBadge4Sub) },
                            label = { Text("Feature 1 Title") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = state.trustBadge1Sub,
                            onValueChange = { viewModel.updateTrustBadges(state.trustBadge1Title, it, state.trustBadge2Title, state.trustBadge2Sub, state.trustBadge3Title, state.trustBadge3Sub, state.trustBadge4Title, state.trustBadge4Sub) },
                            label = { Text("Feature 1 Subtitle") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = state.trustBadge2Title,
                            onValueChange = { viewModel.updateTrustBadges(state.trustBadge1Title, state.trustBadge1Sub, it, state.trustBadge2Sub, state.trustBadge3Title, state.trustBadge3Sub, state.trustBadge4Title, state.trustBadge4Sub) },
                            label = { Text("Feature 2 Title") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = state.trustBadge2Sub,
                            onValueChange = { viewModel.updateTrustBadges(state.trustBadge1Title, state.trustBadge1Sub, state.trustBadge2Title, it, state.trustBadge3Title, state.trustBadge3Sub, state.trustBadge4Title, state.trustBadge4Sub) },
                            label = { Text("Feature 2 Subtitle") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = state.trustBadge3Title,
                            onValueChange = { viewModel.updateTrustBadges(state.trustBadge1Title, state.trustBadge1Sub, state.trustBadge2Title, state.trustBadge2Sub, it, state.trustBadge3Sub, state.trustBadge4Title, state.trustBadge4Sub) },
                            label = { Text("Feature 3 Title") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = state.trustBadge3Sub,
                            onValueChange = { viewModel.updateTrustBadges(state.trustBadge1Title, state.trustBadge1Sub, state.trustBadge2Title, state.trustBadge2Sub, state.trustBadge3Title, it, state.trustBadge4Title, state.trustBadge4Sub) },
                            label = { Text("Feature 3 Subtitle") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = state.trustBadge4Title,
                            onValueChange = { viewModel.updateTrustBadges(state.trustBadge1Title, state.trustBadge1Sub, state.trustBadge2Title, state.trustBadge2Sub, state.trustBadge3Title, state.trustBadge3Sub, it, state.trustBadge4Sub) },
                            label = { Text("Feature 4 Title") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = state.trustBadge4Sub,
                            onValueChange = { viewModel.updateTrustBadges(state.trustBadge1Title, state.trustBadge1Sub, state.trustBadge2Title, state.trustBadge2Sub, state.trustBadge3Title, state.trustBadge3Sub, state.trustBadge4Title, it) },
                            label = { Text("Feature 4 Subtitle") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    state.activeEditSection == "background" -> {
                        Text(
                            text = "Editing Canvas Background & Leaf Accents",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Background Color Options
                        Text("Canvas Background Tone:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        val bgColors = listOf(
                            "#F8F7F0" to "Warm Cream",
                            "#FFFFFF" to "Pure White",
                            "#F0FDF4" to "Soft Sage",
                            "#F0F9FF" to "Soft Ice Blue",
                            "#FEFCE8" to "Pastel Yellow"
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(bgColors) { (hex, label) ->
                                val isSel = state.bgColorHex.equals(hex, ignoreCase = true)
                                FilterChip(
                                    selected = isSel,
                                    onClick = { viewModel.updateBackgroundConfig(hex, state.showLeaves, state.leavesOpacity) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Show Corner Decorative Leaves", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Switch(
                                checked = state.showLeaves,
                                onCheckedChange = { viewModel.updateBackgroundConfig(state.bgColorHex, it, state.leavesOpacity) }
                            )
                        }

                        if (state.showLeaves) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Leaves Opacity: ${(state.leavesOpacity * 100).toInt()}%", fontSize = 12.sp)
                            Slider(
                                value = state.leavesOpacity,
                                onValueChange = { viewModel.updateBackgroundConfig(state.bgColorHex, state.showLeaves, it) },
                                valueRange = 0.1f..1.0f
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Music & Video Export Section
        MusicAndVideoExportSection(
            viewModel = viewModel,
            promoState = state,
            context = context,
            coroutineScope = coroutineScope,
            getFrames = {
                val resolvedBitmaps = withContext(Dispatchers.IO) {
                    val map = mutableMapOf<Int, android.graphics.Bitmap>()
                    val deferreds = state.selectedItems.map { item ->
                        item to async {
                            PromoImageGenerator.loadUriAsBitmap(context, item.imageUri)
                        }
                    }
                    deferreds.forEach { (item, deferred) ->
                        try {
                            val bmp = deferred.await()
                            if (bmp != null) {
                                map[item.id] = bmp
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    map
                }
                val framePair = PromoImageGenerator.generatePromoGrid(
                    context = context,
                    selectedItems = state.selectedItems,
                    priceOverrides = state.priceOverrides,
                    nameOverrides = state.nameOverrides,
                    isOfferBanner = state.isOfferBanner,
                    resolvedBitmaps = resolvedBitmaps,
                    subheader = state.subheader,
                    promoTheme = state.promoTheme,
                    state = state
                )
                val frameBitmap = framePair.second
                if (frameBitmap != null) listOf(frameBitmap) else emptyList()
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

fun getUriFileName(context: Context, uri: Uri?): String {
    if (uri == null) return "Custom Audio Track"
    var name = "Custom Audio Track"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return name
}

fun renderSlideToBitmap(
    context: Context,
    slide: CarouselSlide,
    index: Int,
    total: Int,
    theme: SlideTheme
): android.graphics.Bitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(1080, 1080, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Fill background
    val bgPaint = android.graphics.Paint().apply {
        color = theme.cardBg.toArgb()
    }
    canvas.drawRect(0f, 0f, 1080f, 1080f, bgPaint)

    // Draw header banner / title
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.textColor.toArgb()
        textSize = 44f
        isFakeBoldText = true
    }
    canvas.drawText("SLIDE ${index + 1} OF $total", 70f, 120f, textPaint)

    val headingPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.accentColor.toArgb()
        textSize = 58f
        isFakeBoldText = true
    }
    
    var headY = 220f
    slide.heading.chunked(25).forEach { hLine ->
        canvas.drawText(hLine, 70f, headY, headingPaint)
        headY += 68f
    }

    val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.textColor.toArgb()
        textSize = 36f
    }
    
    var y = headY + 30f
    slide.text.chunked(38).forEach { line ->
        canvas.drawText(line, 70f, y, bodyPaint)
        y += 48f
    }

    y += 30f
    val bulletPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.accentColor.toArgb()
        textSize = 32f
    }
    slide.keyPoints.take(4).forEach { point ->
        canvas.drawText("• $point", 90f, y, bulletPaint)
        y += 44f
    }

    // Footer brand sign-off
    val footerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.GRAY
        textSize = 28f
    }
    canvas.drawText("CAREFLUX PHARMACY • HEALTH EDUCATION", 70f, 1000f, footerPaint)

    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicAndVideoExportSection(
    viewModel: AIContentEngineViewModel,
    promoState: PromoStudioState,
    context: Context,
    coroutineScope: CoroutineScope,
    getFrames: suspend () -> List<android.graphics.Bitmap>
) {
    var isPlayingPreview by remember { mutableStateOf(false) }
    var activePreviewOption by remember { mutableStateOf<AudioTrackOption?>(null) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.updateCustomAudioUri(uri)
            Toast.makeText(context, "Custom audio file added!", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Add Music & Export MP4 Video", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Create animated MP4 slideshow with audio track", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("1. Select Royalty-Free Background Music:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AudioTrackOption.values()) { track ->
                    val isSelected = track == promoState.musicTrack
                    val isTrackPlaying = isPlayingPreview && activePreviewOption == track

                    Card(
                        onClick = {
                            viewModel.updateMusicTrack(track)
                            if (track == AudioTrackOption.CUSTOM_FILE && promoState.customAudioUri == null) {
                                audioPickerLauncher.launch("audio/*")
                            }
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(155.dp)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    track.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                if (track != AudioTrackOption.NONE) {
                                    val canPlay = track != AudioTrackOption.CUSTOM_FILE || promoState.customAudioUri != null
                                    if (canPlay) {
                                        IconButton(
                                            onClick = {
                                                if (isTrackPlaying) {
                                                    AudioEngine.stopPreview()
                                                    isPlayingPreview = false
                                                    activePreviewOption = null
                                                } else {
                                                    AudioEngine.playPreview(context, track, promoState.customAudioUri)
                                                    isPlayingPreview = true
                                                    activePreviewOption = track
                                                }
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                if (isTrackPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                                                contentDescription = "Preview Audio",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (track == AudioTrackOption.CUSTOM_FILE && promoState.customAudioUri != null)
                                    getUriFileName(context, promoState.customAudioUri)
                                else track.subtitle,
                                fontSize = 9.sp,
                                color = Color.Gray,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            if (promoState.musicTrack == AudioTrackOption.CUSTOM_FILE) {
                Spacer(modifier = Modifier.height(12.dp))
                if (promoState.customAudioUri != null) {
                    val isCustomPlaying = isPlayingPreview && activePreviewOption == AudioTrackOption.CUSTOM_FILE
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        Icons.Filled.AudioFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            getUriFileName(context, promoState.customAudioUri),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1
                                        )
                                        Text("Custom Audio Loaded", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        if (isCustomPlaying) {
                                            AudioEngine.stopPreview()
                                            isPlayingPreview = false
                                            activePreviewOption = null
                                        } else {
                                            AudioEngine.playPreview(context, AudioTrackOption.CUSTOM_FILE, promoState.customAudioUri)
                                            isPlayingPreview = true
                                            activePreviewOption = AudioTrackOption.CUSTOM_FILE
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (isCustomPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                                        contentDescription = "Preview Custom Track",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { audioPickerLauncher.launch("audio/*") },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Replace Audio", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        AudioEngine.stopPreview()
                                        isPlayingPreview = false
                                        activePreviewOption = null
                                        viewModel.updateCustomAudioUri(null)
                                        viewModel.updateMusicTrack(AudioTrackOption.UPBEAT_RETAIL)
                                    },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Remove Audio", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { audioPickerLauncher.launch("audio/*") },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Audio File (MP3, WAV, AAC, M4A)", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("2. Video Format & Aspect Ratio:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                VideoAspectRatio.values().forEach { aspect ->
                    val isSel = aspect == promoState.videoAspectRatio
                    FilterChip(
                        selected = isSel,
                        onClick = { viewModel.updateVideoAspectRatio(aspect) },
                        label = { Text(aspect.label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (promoState.isVideoEncoding) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { promoState.videoEncodingProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Encoding HD MP4 Video... ${(promoState.videoEncodingProgress * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.setVideoEncodingState(true, 0.05f)
                            try {
                                val frames = getFrames()
                                if (frames.isNotEmpty()) {
                                    val videoFile = Mp4VideoEncoder.encodeBitmapsToMp4(
                                        context = context,
                                        bitmaps = frames,
                                        aspectRatio = promoState.videoAspectRatio,
                                        durationPerSlideSeconds = promoState.slideDurationSeconds,
                                        musicOption = promoState.musicTrack,
                                        customAudioUri = promoState.customAudioUri,
                                        onProgress = { p ->
                                            viewModel.setVideoEncodingState(true, p)
                                        }
                                    )
                                    if (videoFile != null) {
                                        val uri = Mp4VideoEncoder.getFileProviderUri(context, videoFile)
                                        viewModel.setVideoEncodingState(false, 1.0f, uri)
                                        Toast.makeText(context, "MP4 Video Created Successfully!", Toast.LENGTH_LONG).show()
                                    } else {
                                        viewModel.setVideoEncodingState(false)
                                        Toast.makeText(context, "Video encoding failed.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    viewModel.setVideoEncodingState(false)
                                    Toast.makeText(context, "No bitmap frames ready to encode.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                viewModel.setVideoEncodingState(false)
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.Movie, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export MP4 Video with Music", fontWeight = FontWeight.Bold)
                }
            }

            if (promoState.generatedVideoUri != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("MP4 Video Animated Post Ready!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(android.content.Intent.EXTRA_STREAM, promoState.generatedVideoUri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share MP4 Video Post"))
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share MP4 Video", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PromoSuccessScreen(
    viewModel: AIContentEngineViewModel,
    state: PromoStudioState,
    context: android.content.Context
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Text("Promo Graphic Ready!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Text(
            text = "Your high-resolution 1080x1080 promotional grid has been successfully generated offscreen. Share it straight to WhatsApp and social media!",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier
                .size(310.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.generatedUri != null) {
                    coil.compose.AsyncImage(
                        model = state.generatedUri,
                        contentDescription = "Generated Promo Grid",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    Text("Preview unavailable", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        val coroutineScope = rememberCoroutineScope()

        MusicAndVideoExportSection(
            viewModel = viewModel,
            promoState = state,
            context = context,
            coroutineScope = coroutineScope,
            getFrames = {
                val frames = mutableListOf<android.graphics.Bitmap>()
                if (state.generatedUri != null) {
                    val mainBmp = PromoImageGenerator.loadUriAsBitmap(context, state.generatedUri?.toString())
                    if (mainBmp != null) {
                        frames.add(mainBmp)
                    }
                }
                if (frames.isEmpty()) {
                    val resolvedBitmaps = withContext(Dispatchers.IO) {
                        val map = mutableMapOf<Int, android.graphics.Bitmap>()
                        state.selectedItems.forEachIndexed { index, item ->
                            val prodSlot = state.products.getOrNull(index)
                            val targetUri = item.imageUri?.ifEmpty { null } ?: prodSlot?.imageUri?.ifEmpty { null }
                            var bmp: android.graphics.Bitmap? = null
                            if (!targetUri.isNullOrEmpty()) {
                                bmp = PromoImageGenerator.loadUriAsBitmap(context, targetUri)
                            } else if (prodSlot?.drawableResId != null) {
                                bmp = PromoImageGenerator.loadDrawableAsBitmap(context, prodSlot.drawableResId)
                            }
                            if (bmp != null) {
                                map[item.id] = bmp
                                if (prodSlot != null) {
                                    map[prodSlot.id] = bmp
                                }
                            }
                        }
                        state.products.forEach { prod ->
                            if (!map.containsKey(prod.id)) {
                                var bmp: android.graphics.Bitmap? = null
                                if (!prod.imageUri.isNullOrEmpty()) {
                                    bmp = PromoImageGenerator.loadUriAsBitmap(context, prod.imageUri)
                                } else if (prod.drawableResId != null) {
                                    bmp = PromoImageGenerator.loadDrawableAsBitmap(context, prod.drawableResId)
                                }
                                if (bmp != null) map[prod.id] = bmp
                            }
                        }
                        map
                    }
                    val framePair = PromoImageGenerator.generatePromoGrid(
                        context = context,
                        selectedItems = state.selectedItems,
                        priceOverrides = state.priceOverrides,
                        nameOverrides = state.nameOverrides,
                        isOfferBanner = state.isOfferBanner,
                        resolvedBitmaps = resolvedBitmaps,
                        subheader = state.subheader,
                        promoTheme = state.promoTheme,
                        state = state
                    )
                    val frameBmp = framePair.second
                    if (frameBmp != null) frames.add(frameBmp)
                }
                frames
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (state.generatedUri != null) {
                    try {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(android.content.Intent.EXTRA_STREAM, state.generatedUri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Promo Grid Image"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Error opening Share sheet: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "Nothing to share yet.", Toast.LENGTH_SHORT).show()
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share PNG Image Grid to WhatsApp", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                viewModel.clearPromoStudioState()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create New Promotion", fontWeight = FontWeight.SemiBold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ManualCarouselCreation(
    state: ContentEngineState.ManualCreation,
    viewModel: AIContentEngineViewModel
) {
    val context = LocalContext.current
    var activeImagePickIndex by remember { mutableStateOf<Int?>(null) }
    
    val manualPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            val idx = activeImagePickIndex
            if (uri != null && idx != null) {
                val savedPath = copyUriToInternalStorage(context, uri)
                if (savedPath != null) {
                    val newSlides = state.slides.toMutableList()
                    if (idx < newSlides.size) {
                        newSlides[idx] = newSlides[idx].copy(imageUri = savedPath)
                        viewModel.updateManualCreationFields(state.topicTitle, state.caption, newSlides)
                    }
                } else {
                    Toast.makeText(context, "Error processing picked image", Toast.LENGTH_SHORT).show()
                }
            }
            activeImagePickIndex = null
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Manual Carousel Creator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { viewModel.setIdle() }) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Configure your custom slide deck and content structure manually. This does not require an active internet connection.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Topic Title
        OutlinedTextField(
            value = state.topicTitle,
            onValueChange = { 
                viewModel.updateManualCreationFields(it, state.caption, state.slides)
            },
            label = { Text("Topic / Title of Carousel") },
            placeholder = { Text("e.g., Guide to Managing Blood Pressure") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Caption
        OutlinedTextField(
            value = state.caption,
            onValueChange = { 
                viewModel.updateManualCreationFields(state.topicTitle, it, state.slides)
            },
            label = { Text("Carousel Social Caption & Hashtags") },
            placeholder = { Text("e.g. Health education slide deck on lower body blood flow. #CardioCare #HealthTips") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(20.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Slide Deck Cards (${state.slides.size} slides)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            TextButton(
                onClick = {
                    val nextNum = state.slides.size + 1
                    val newSlides = state.slides + CarouselSlide(nextNum, "", "", emptyList(), emptyList())
                    viewModel.updateManualCreationFields(state.topicTitle, state.caption, newSlides)
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Slide")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Slide")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        state.slides.forEachIndexed { index, slide ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Slide #${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        
                        if (state.slides.size > 1) {
                            IconButton(
                                onClick = {
                                    val newSlides = state.slides.toMutableList()
                                    newSlides.removeAt(index)
                                    // Re-number slides
                                    val updatedSlides = newSlides.mapIndexed { idx, s -> s.copy(slideNumber = idx + 1) }
                                    viewModel.updateManualCreationFields(state.topicTitle, state.caption, updatedSlides)
                                }
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete Slide", tint = Color.Red.copy(alpha = 0.7f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = slide.heading,
                        onValueChange = { newVal ->
                            val newSlides = state.slides.toMutableList()
                            newSlides[index] = slide.copy(heading = newVal)
                            viewModel.updateManualCreationFields(state.topicTitle, state.caption, newSlides)
                        },
                        label = { Text("Slide Heading") },
                        placeholder = { Text("e.g. What is Hypertension?") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = slide.text,
                        onValueChange = { newVal ->
                            val newSlides = state.slides.toMutableList()
                            newSlides[index] = slide.copy(text = newVal)
                            viewModel.updateManualCreationFields(state.topicTitle, state.caption, newSlides)
                        },
                        label = { Text("Slide Subtext / Core Explanation") },
                        placeholder = { Text("e.g. Hypertension occurs when the force of... is too high.") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val rawPoints = remember(slide.keyPoints) { slide.keyPoints.joinToString("\n") }
                    OutlinedTextField(
                        value = rawPoints,
                        onValueChange = { newVal ->
                            val newSlides = state.slides.toMutableList()
                            val parsedPoints = newVal.split("\n").filter { it.isNotBlank() }
                            newSlides[index] = slide.copy(keyPoints = parsedPoints)
                            viewModel.updateManualCreationFields(state.topicTitle, state.caption, newSlides)
                        },
                        label = { Text("Bullet Points (one per line)") },
                        placeholder = { Text("e.g. Fast symptoms guide\nReduces heart loading") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val rawProducts = remember(slide.recommendedProducts) { slide.recommendedProducts?.joinToString(", ") ?: "" }
                    OutlinedTextField(
                        value = rawProducts,
                        onValueChange = { newVal ->
                            val newSlides = state.slides.toMutableList()
                            val parsedProducts = newVal.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            newSlides[index] = slide.copy(recommendedProducts = parsedProducts)
                            viewModel.updateManualCreationFields(state.topicTitle, state.caption, newSlides)
                        },
                        label = { Text("Recommended Products (comma-separated, optional)") },
                        placeholder = { Text("e.g. Daflon 500mg, Amino Pep Forte") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Slide Image (Optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (!slide.imageUri.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            coil.compose.AsyncImage(
                                model = slide.imageUri,
                                contentDescription = "Picked Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            
                            IconButton(
                                onClick = {
                                    val newSlides = state.slides.toMutableList()
                                    newSlides[index] = slide.copy(imageUri = null)
                                    viewModel.updateManualCreationFields(state.topicTitle, state.caption, newSlides)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove Image", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                activeImagePickIndex = index
                                manualPhotoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = "Choose Image")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload / Choose Image")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { viewModel.saveManualCarousel() },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.topicTitle.isNotBlank() && state.slides.any { it.heading.isNotBlank() }
        ) {
            Icon(Icons.Filled.Check, contentDescription = "Save")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save & Live Preview Carousel")
        }
    }
}

