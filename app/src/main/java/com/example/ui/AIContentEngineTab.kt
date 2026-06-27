package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AICarousel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    Column(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (activeSubTab == "promo") "Promo Studio" else "AI Content Engine",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (activeSubTab == "promo") "Design high-converting multi-product grid layouts" else "Generate daily educational carousels automatically",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            if (activeSubTab == "carousel") {
                if (uiState !is ContentEngineState.Idle && uiState !is ContentEngineState.Error) {
                    TextButton(onClick = { viewModel.setIdle() }) {
                        Text("Back to History")
                    }
                } else {
                    Button(
                        onClick = { viewModel.startSelection() },
                        enabled = uiState !is ContentEngineState.Generating && uiState !is ContentEngineState.SelectStrategy && uiState !is ContentEngineState.SelectSpecificProducts
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Generate")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Content")
                    }
                }
            } else {
                if (promoState.uiMode != PromoUiMode.Selection) {
                    TextButton(onClick = { viewModel.clearPromoStudioState() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Design")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Premium Material 3 Sub-Tab selector
        TabRow(
            selectedTabIndex = if (activeSubTab == "carousel") 0 else 1,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[if (activeSubTab == "carousel") 0 else 1]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Tab(
                selected = activeSubTab == "carousel",
                onClick = { activeSubTab = "carousel" },
                text = { Text("Educational Carousel", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
            Tab(
                selected = activeSubTab == "promo",
                onClick = { activeSubTab = "promo" },
                text = { Text("Promo Studio (Grid)", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (activeSubTab == "carousel") {
            when (val state = uiState) {
            is ContentEngineState.Idle -> {
                if (history.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "AI", modifier = Modifier.size(64.dp), tint = Color.LightGray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No content generated yet.", color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.startSelection() }) {
                                Text("Generate Fresh Content")
                            }
                        }
                    }
                } else {
                    Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history) { carousel ->
                            ElevatedCard(
                                onClick = { viewModel.viewHistoryItem(carousel) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(carousel.topicTitle, fontWeight = FontWeight.Bold)
                                        val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(carousel.createdAt))
                                        Text("Generated on $date", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                    IconButton(onClick = { viewModel.deleteCarousel(carousel) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f))
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
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text(carousel.topicTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(carousel.caption, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(carousel.slides) { index, slide ->
                            SlidePreview(slide, index, carousel.slides.size, isStoryFormat, theme, selectedLayoutTheme, context, viewModel)
                        }
                    }
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
        
        Spacer(modifier = Modifier.height(8.dp))
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
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.accentColor,
                contentColor = if (theme.isLight) Color.White else Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Share, contentDescription = "View", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("View Image", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            
            // 5. Recommended products or Warnings (or Pro tips like the image!)
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
            } else {
                // If there are no products, display a super premium callout warning
                val tipText = if (slide.heading.contains("diarrhea", true) || slide.heading.contains("Dehydration", true)) {
                    "Consult medical staff immediately if vomiting or severe lethargy persists."
                } else {
                    "Always prioritize fresh, clean hydration and sanitary fluid supplies."
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (theme.isLight) Color(0xFFFFFAEB) else Color(0xFF292212),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (theme.isLight) Color(0xFFFEE8BD) else Color(0xFF534120),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Tip",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(15.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tipText,
                            color = if (theme.isLight) Color(0xFF664D03) else Color(0xFFFFD56B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 14.sp
                        )
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
            PromoConfigurationScreen(
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
    var selectedCategory by remember { mutableStateOf("All") }
    var showHelpInfo by remember { mutableStateOf(false) }
    
    val categories = remember(inventory) {
        listOf("All") + inventory.map { it.category }.distinct().sorted()
    }
    
    val filteredItems = remember(inventory, searchQuery, selectedCategory) {
        inventory.filter { item ->
            val matchesSearch = item.name.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true) ||
                    item.brand.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "All" || item.category == selectedCategory
            matchesSearch && matchesCategory
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
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
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Promo Grid Selection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showHelpInfo = !showHelpInfo },
                        modifier = Modifier.size(32.dp)
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (state.selectedItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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

        // Search and category selectors
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search inventory...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // List of items
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredItems.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
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
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.togglePromoProductSelection(item) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Small Thumbnail representation or Initials
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("${item.brand.ifEmpty { "Generic" }} • ₦${String.format("%,.2f", item.price)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { viewModel.setPromoUiMode(PromoUiMode.Configuration) },
            modifier = Modifier.fillMaxWidth(),
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

        state.selectedItems.forEachIndexed { index, item ->
            val customPriceStr = state.priceOverrides[item.id] ?: ""
            val customNameStr = state.nameOverrides[item.id] ?: ""

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
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Promo Graphic Sub-header Text:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = state.subheader,
            onValueChange = { viewModel.updatePromoSubheader(it) },
            label = { Text("Sub-header Promotions Banner Text") },
            placeholder = { Text("TODAY'S SPECIAL OFFERS & PROMOTIONS") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose Graphic Template Style:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                onClick = { viewModel.setPromoTemplateMode(false) },
                colors = CardDefaults.cardColors(
                    containerColor = if (!state.isOfferBanner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).border(
                    width = if (!state.isOfferBanner) 2.dp else 0.dp,
                    color = if (!state.isOfferBanner) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (!state.isOfferBanner) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Plain Grid", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Clean edge-to-edge layout", fontSize = 10.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }

            Card(
                onClick = { viewModel.setPromoTemplateMode(true) },
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isOfferBanner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).border(
                    width = if (state.isOfferBanner) 2.dp else 0.dp,
                    color = if (state.isOfferBanner) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Campaign,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (state.isOfferBanner) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Offer Banner", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Brand badge + trust seals", fontSize = 10.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }

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
                            promoTheme = state.promoTheme
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

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (state.generatedUri != null) {
                    try {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(android.content.Intent.EXTRA_STREAM, state.generatedUri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Promo Grid"))
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
            Text("Share Grid to WhatsApp", fontWeight = FontWeight.Bold)
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

