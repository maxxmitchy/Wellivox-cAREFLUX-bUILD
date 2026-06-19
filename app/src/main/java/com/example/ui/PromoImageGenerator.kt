package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.view.View.MeasureSpec
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.InventoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class PromoThemeStyle(
    val id: String,
    val displayName: String,
    val backgroundColor: Color,
    val cardBackgroundColor: Color,
    val cardBorderColor: Color,
    val accentColor: Color,
    val headerTextColor: Color,
    val productTextColor: Color,
    val productMutedColor: Color,
    val priceBgColor: Color,
    val priceBorderColor: Color,
    val priceTextColor: Color,
    val secondaryAccentColor: Color,
    val isDark: Boolean
) {
    MIDNIGHT_CYAN(
        id = "MIDNIGHT_CYAN",
        displayName = "Midnight Cyan",
        backgroundColor = Color(0xFF0B0F19),
        cardBackgroundColor = Color(0xFF131B2A),
        cardBorderColor = Color(0xFF1E293B),
        accentColor = Color(0xFF00E5FF),
        headerTextColor = Color.White,
        productTextColor = Color.White,
        productMutedColor = Color(0xFF94A3B8),
        priceBgColor = Color(0xFFFFB74D).copy(alpha = 0.15f),
        priceBorderColor = Color(0xFFFFB74D),
        priceTextColor = Color(0xFFFFB74D),
        secondaryAccentColor = Color(0xFF00FA9A),
        isDark = true
    ),
    EMERALD_HERB(
        id = "EMERALD_HERB",
        displayName = "Emerald Herb",
        backgroundColor = Color(0xFFF0FDF4),
        cardBackgroundColor = Color.White,
        cardBorderColor = Color(0xFFBBF7D0),
        accentColor = Color(0xFF15803D),
        headerTextColor = Color(0xFF14532D),
        productTextColor = Color(0xFF1F2937),
        productMutedColor = Color(0xFF6B7280),
        priceBgColor = Color(0xFFDCFCE7),
        priceBorderColor = Color(0xFF15803D),
        priceTextColor = Color(0xFF15803D),
        secondaryAccentColor = Color(0xFF16A34A),
        isDark = false
    ),
    SOFT_LAVENDER(
        id = "SOFT_LAVENDER",
        displayName = "Soft Lavender",
        backgroundColor = Color(0xFFFAF5FF),
        cardBackgroundColor = Color.White,
        cardBorderColor = Color(0xFFE9D5FF),
        accentColor = Color(0xFF7E22CE),
        headerTextColor = Color(0xFF581C87),
        productTextColor = Color(0xFF1F2937),
        productMutedColor = Color(0xFF6B7280),
        priceBgColor = Color(0xFFF3E8FF),
        priceBorderColor = Color(0xFF7E22CE),
        priceTextColor = Color(0xFF7E22CE),
        secondaryAccentColor = Color(0xFF9333EA),
        isDark = false
    ),
    GOLDEN_AMBER(
        id = "GOLDEN_AMBER",
        displayName = "Golden Sunset",
        backgroundColor = Color(0xFF1E1B4B),
        cardBackgroundColor = Color(0xFF312E81),
        cardBorderColor = Color(0xFF4338CA),
        accentColor = Color(0xFFF59E0B),
        headerTextColor = Color.White,
        productTextColor = Color.White,
        productMutedColor = Color(0xFFC7D2FE),
        priceBgColor = Color(0xFFFFE4E6).copy(alpha = 0.15f),
        priceBorderColor = Color(0xFFF59E0B),
        priceTextColor = Color(0xFFF59E0B),
        secondaryAccentColor = Color(0xFFEF4444),
        isDark = true
    ),
    CLINICAL_BLUE(
        id = "CLINICAL_BLUE",
        displayName = "Clinical Blue",
        backgroundColor = Color(0xFFF0F9FF),
        cardBackgroundColor = Color.White,
        cardBorderColor = Color(0xFFBAE6FD),
        accentColor = Color(0xFF0369A1),
        headerTextColor = Color(0xFF0C4A6E),
        productTextColor = Color(0xFF1F2937),
        productMutedColor = Color(0xFF6B7280),
        priceBgColor = Color(0xFFE0F2FE),
        priceBorderColor = Color(0xFF0369A1),
        priceTextColor = Color(0xFF0369A1),
        secondaryAccentColor = Color(0xFF0284C7),
        isDark = false
    )
}

object PromoImageGenerator {

    private fun findActivity(context: Context): ComponentActivity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is ComponentActivity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Loads a URI or local path/content/network URI into a Bitmap.
     * Uses fast direct BitmapFactory decoding for local content/file URIs to avoid permission hangs,
     * and uses a singleton Coil.imageLoader with strict timeouts for fallback and remote images.
     */
    suspend fun loadUriAsBitmap(context: Context, uriString: String?, maxDimension: Int = 600): Bitmap? {
        if (uriString.isNullOrEmpty()) return null
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                // 1. Direct local file loading
                val cleanPath = when {
                    uriString.startsWith("file://") -> uriString.substring("file://".length)
                    uriString.startsWith("/") -> uriString
                    else -> null
                }
                if (cleanPath != null) {
                    try {
                        val file = java.io.File(cleanPath)
                        if (file.exists() && file.isFile) {
                            val options = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeFile(file.absolutePath, options)
                            
                            val height = options.outHeight
                            val width = options.outWidth
                            var inSampleSize = 1
                            if (height > maxDimension || width > maxDimension) {
                                val halfHeight = height / 2
                                val halfWidth = width / 2
                                while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
                                    inSampleSize *= 2
                                }
                            }
                            
                            val decodeOptions = BitmapFactory.Options().apply {
                                this.inSampleSize = inSampleSize
                            }
                            val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                            if (decoded != null) {
                                return@withContext decoded
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 2. Content Resolver loading for content:// URIs
                if (uriString.startsWith("content://")) {
                    try {
                        val uri = Uri.parse(uriString)
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val options = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeStream(stream, null, options)
                            
                            val height = options.outHeight
                            val width = options.outWidth
                            var inSampleSize = 1
                            if (height > maxDimension || width > maxDimension) {
                                val halfHeight = height / 2
                                val halfWidth = width / 2
                                while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
                                    inSampleSize *= 2
                                }
                            }
                            
                            context.contentResolver.openInputStream(uri)?.use { finalStream ->
                                val decodeOptions = BitmapFactory.Options().apply {
                                    this.inSampleSize = inSampleSize
                                }
                                val decoded = BitmapFactory.decodeStream(finalStream, null, decodeOptions)
                                if (decoded != null) return@withContext decoded
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 3. Fallback to Coil
                kotlinx.coroutines.withTimeoutOrNull(2500) {
                    val imageLoader = coil.Coil.imageLoader(context)
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(uriString)
                        .size(maxDimension)
                        .allowHardware(false)
                        .dispatcher(Dispatchers.IO)
                        .build()
                    
                    val result = imageLoader.execute(request)
                    if (result is coil.request.SuccessResult) {
                        val drawable = result.drawable
                        if (drawable is android.graphics.drawable.BitmapDrawable) {
                            drawable.bitmap
                        } else {
                            val width = drawable.intrinsicWidth.coerceAtLeast(1)
                            val height = drawable.intrinsicHeight.coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            drawable.setBounds(0, 0, width, height)
                            drawable.draw(canvas)
                            bitmap
                        }
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Generates a 1080x1080 marketing promotional grid from selected items
     * and exports it to a shareable FileProvider cache URI.
     * Must be called on the Main thread.
     */
    fun generatePromoGrid(
        context: Context,
        selectedItems: List<InventoryItem>,
        priceOverrides: Map<Int, String>,
        nameOverrides: Map<Int, String>,
        isOfferBanner: Boolean,
        resolvedBitmaps: Map<Int, Bitmap>,
        subheader: String = "TODAY'S SPECIAL OFFERS & PROMOTIONS",
        promoTheme: PromoThemeStyle = PromoThemeStyle.MIDNIGHT_CYAN
    ): Pair<Uri?, Bitmap?> {
        val exportWidthPx = 1080
        val exportHeightPx = 1080

        val activity = findActivity(context) ?: return Pair(null, null)
        val rootView = activity.findViewById<android.view.ViewGroup>(android.R.id.content)

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                val customDensity = Density(
                    density = exportWidthPx.toFloat() / 360f,
                    fontScale = 1.0f
                )
                CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides customDensity
                ) {
                    PromoCompositionLayout(
                        selectedItems = selectedItems,
                        priceOverrides = priceOverrides,
                        nameOverrides = nameOverrides,
                        isOfferBanner = isOfferBanner,
                        resolvedBitmaps = resolvedBitmaps,
                        subheader = subheader,
                        promoTheme = promoTheme
                    )
                }
            }
        }

        rootView.addView(composeView)
        val bitmap = Bitmap.createBitmap(exportWidthPx, exportHeightPx, Bitmap.Config.ARGB_8888)

        try {
            composeView.measure(
                MeasureSpec.makeMeasureSpec(exportWidthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(exportHeightPx, MeasureSpec.EXACTLY)
            )
            composeView.layout(0, 0, exportWidthPx, exportHeightPx)

            val canvas = Canvas(bitmap)
            composeView.draw(canvas)
        } finally {
            rootView.removeView(composeView)
        }

        val cachePath = File(context.cacheDir, "promos")
        cachePath.mkdirs()
        val file = File(cachePath, "promo_${System.currentTimeMillis()}.png")
        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        return Pair(uri, bitmap)
    }
}

@Composable
fun PromoCompositionLayout(
    selectedItems: List<InventoryItem>,
    priceOverrides: Map<Int, String>,
    nameOverrides: Map<Int, String>,
    isOfferBanner: Boolean,
    resolvedBitmaps: Map<Int, Bitmap>,
    subheader: String = "TODAY'S SPECIAL OFFERS & PROMOTIONS",
    promoTheme: PromoThemeStyle = PromoThemeStyle.MIDNIGHT_CYAN
) {
    Column(
        modifier = Modifier
            .size(360.dp)
            .background(promoTheme.backgroundColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PromoHeaderBanner(subheader = subheader, promoTheme = promoTheme)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            when (selectedItems.size) {
                1 -> SingleItemPromoView(selectedItems, priceOverrides, nameOverrides, resolvedBitmaps, promoTheme)
                2 -> TwinItemsRow(selectedItems, priceOverrides, nameOverrides, resolvedBitmaps, promoTheme)
                3 -> TripleItemsGrid(selectedItems, priceOverrides, nameOverrides, resolvedBitmaps, promoTheme)
                else -> QuadItemsGrid(selectedItems, priceOverrides, nameOverrides, resolvedBitmaps, promoTheme)
            }
        }

        PromoFooterBanner(promoTheme = promoTheme)
    }
}

@Composable
fun PromoHeaderBanner(subheader: String = "TODAY'S SPECIAL OFFERS & PROMOTIONS", promoTheme: PromoThemeStyle = PromoThemeStyle.MIDNIGHT_CYAN) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.LocalPharmacy,
                contentDescription = null,
                tint = promoTheme.accentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "CAREFLUX PHARMACY",
                color = promoTheme.headerTextColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )
        }
        Text(
            text = subheader,
            color = promoTheme.accentColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth(0.85f)
                .height(2.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, promoTheme.accentColor, Color.Transparent)
                    )
                )
        )
    }
}

@Composable
fun PromoFooterBanner(promoTheme: PromoThemeStyle = PromoThemeStyle.MIDNIGHT_CYAN) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 6.dp)
                .fillMaxWidth(0.85f)
                .height(1.dp)
                .background(if (promoTheme.isDark) Color(0xFF1E293B) else Color(0xFFD1D5DB))
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    tint = promoTheme.secondaryAccentColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "100% Genuine Care",
                    color = promoTheme.productMutedColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "Ask our pharmacist for details",
                color = promoTheme.accentColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocalShipping,
                    contentDescription = null,
                    tint = promoTheme.secondaryAccentColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Free Home Delivery",
                    color = promoTheme.productMutedColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ProductGridCard(
    item: InventoryItem,
    name: String,
    price: String,
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    promoTheme: PromoThemeStyle = PromoThemeStyle.MIDNIGHT_CYAN
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = promoTheme.cardBackgroundColor
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, promoTheme.cardBorderColor),
        modifier = modifier.padding(if (isCompact) 2.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 4.dp else 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(if (isCompact) 54.dp else 84.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (promoTheme.isDark) Color(0xFF0F172A) else Color(0xFFF3F4F6)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MedicalServices,
                        contentDescription = null,
                        tint = promoTheme.cardBorderColor,
                        modifier = Modifier.size(if (isCompact) 24.dp else 40.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = name,
                    color = promoTheme.productTextColor,
                    fontSize = if (isCompact) 9.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = item.dosage,
                    color = promoTheme.productMutedColor,
                    fontSize = if (isCompact) 7.sp else 8.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(promoTheme.priceBgColor)
                    .border(1.dp, promoTheme.priceBorderColor, RoundedCornerShape(6.dp))
                    .padding(vertical = if (isCompact) 1.dp else 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = price,
                    color = promoTheme.priceTextColor,
                    fontSize = if (isCompact) 9.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

private fun formatNairaPrice(rawPrice: String?, defaultVal: Double): String {
    if (rawPrice.isNullOrBlank()) {
        return "₦${"%,.2f".format(defaultVal)}"
    }
    val clean = rawPrice.trim().removePrefix("₦").removePrefix("$").trim()
    val parsed = clean.toDoubleOrNull()
    return if (parsed != null) {
        "₦${"%,.2f".format(parsed)}"
    } else {
        if (rawPrice.startsWith("₦") || rawPrice.startsWith("$")) {
            "₦" + rawPrice.drop(1).trim()
        } else {
            "₦$rawPrice"
        }
    }
}

@Composable
fun SingleItemPromoView(
    selectedItems: List<InventoryItem>,
    priceOverrides: Map<Int, String>,
    nameOverrides: Map<Int, String>,
    resolvedBitmaps: Map<Int, Bitmap>,
    promoTheme: PromoThemeStyle = PromoThemeStyle.MIDNIGHT_CYAN
) {
    val item = selectedItems.first()
    val name = nameOverrides[item.id] ?: item.name
    val price = formatNairaPrice(priceOverrides[item.id], item.price)
    val bmp = resolvedBitmaps[item.id]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProductGridCard(
            item = item,
            name = name,
            price = price,
            bitmap = bmp,
            modifier = Modifier.fillMaxSize(),
            promoTheme = promoTheme
        )
    }
}

@Composable
fun TwinItemsRow(
    selectedItems: List<InventoryItem>,
    priceOverrides: Map<Int, String>,
    nameOverrides: Map<Int, String>,
    resolvedBitmaps: Map<Int, Bitmap>,
    promoTheme: PromoThemeStyle = PromoThemeStyle.MIDNIGHT_CYAN
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        selectedItems.forEach { item ->
            val name = nameOverrides[item.id] ?: item.name
            val price = formatNairaPrice(priceOverrides[item.id], item.price)
            val bmp = resolvedBitmaps[item.id]
            ProductGridCard(
                item = item,
                name = name,
                price = price,
                bitmap = bmp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                promoTheme = promoTheme
            )
        }
    }
}

@Composable
fun TripleItemsGrid(
    selectedItems: List<InventoryItem>,
    priceOverrides: Map<Int, String>,
    nameOverrides: Map<Int, String>,
    resolvedBitmaps: Map<Int, Bitmap>,
    promoTheme: PromoThemeStyle = PromoThemeStyle.MIDNIGHT_CYAN
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (i in 0..1) {
                val item = selectedItems[i]
                val name = nameOverrides[item.id] ?: item.name
                val price = formatNairaPrice(priceOverrides[item.id], item.price)
                val bmp = resolvedBitmaps[item.id]
                ProductGridCard(
                    item = item,
                    name = name,
                    price = price,
                    bitmap = bmp,
                    isCompact = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    promoTheme = promoTheme
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.Center
        ) {
            val item = selectedItems[2]
            val name = nameOverrides[item.id] ?: item.name
            val price = formatNairaPrice(priceOverrides[item.id], item.price)
            val bmp = resolvedBitmaps[item.id]
            ProductGridCard(
                item = item,
                name = name,
                price = price,
                bitmap = bmp,
                isCompact = true,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f),
                promoTheme = promoTheme
            )
        }
    }
}

@Composable
fun QuadItemsGrid(
    selectedItems: List<InventoryItem>,
    priceOverrides: Map<Int, String>,
    nameOverrides: Map<Int, String>,
    resolvedBitmaps: Map<Int, Bitmap>,
    promoTheme: PromoThemeStyle = PromoThemeStyle.MIDNIGHT_CYAN
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (i in 0..1) {
                val item = selectedItems[i]
                val name = nameOverrides[item.id] ?: item.name
                val price = formatNairaPrice(priceOverrides[item.id], item.price)
                val bmp = resolvedBitmaps[item.id]
                ProductGridCard(
                    item = item,
                    name = name,
                    price = price,
                    bitmap = bmp,
                    isCompact = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    promoTheme = promoTheme
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (i in 2..3) {
                val item = selectedItems[i]
                val name = nameOverrides[item.id] ?: item.name
                val price = formatNairaPrice(priceOverrides[item.id], item.price)
                val bmp = resolvedBitmaps[item.id]
                ProductGridCard(
                    item = item,
                    name = name,
                    price = price,
                    bitmap = bmp,
                    isCompact = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    promoTheme = promoTheme
                )
            }
        }
    }
}
