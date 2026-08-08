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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage

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
        promoTheme: PromoThemeStyle = PromoThemeStyle.MIDNIGHT_CYAN,
        state: PromoStudioState = PromoStudioState()
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
                        promoTheme = promoTheme,
                        state = state
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

fun parseHexColor(hex: String, defaultColor: Color): Color {
    return try {
        val cleanHex = hex.trim().removePrefix("#")
        val colorInt = android.graphics.Color.parseColor("#$cleanHex")
        Color(colorInt)
    } catch (e: Exception) {
        defaultColor
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
    promoTheme: PromoThemeStyle = PromoThemeStyle.MIDNIGHT_CYAN,
    state: PromoStudioState = PromoStudioState()
) {
    when (state.templateStyle) {
        FlyerTemplateStyle.CAREFLUX_MASTER -> {
            CarefluxMasterTemplateLayout(state = state)
        }
        FlyerTemplateStyle.VIBRANT_3D_BLAST -> {
            Vibrant3DBlastTemplateLayout(selectedItems, priceOverrides, nameOverrides, resolvedBitmaps, state)
        }
        FlyerTemplateStyle.CYAN_GOLD_GLOSSY -> {
            CyanGoldGlossyTemplateLayout(selectedItems, priceOverrides, nameOverrides, resolvedBitmaps, state)
        }
        FlyerTemplateStyle.PRO_MEDICAL_GRID -> {
            ProMedicalGridTemplateLayout(selectedItems, priceOverrides, nameOverrides, resolvedBitmaps, state)
        }
        FlyerTemplateStyle.ECO_ORGANIC_CLEAN -> {
            EcoOrganicTemplateLayout(selectedItems, priceOverrides, nameOverrides, resolvedBitmaps, state)
        }
        FlyerTemplateStyle.MEDICAL_OUTREACH -> {
            MedicalOutreachTemplateLayout(selectedItems, priceOverrides, nameOverrides, resolvedBitmaps, state)
        }
    }
}

@Composable
fun CarefluxMasterTemplateLayout(
    state: PromoStudioState,
    modifier: Modifier = Modifier,
    onSectionClick: ((String) -> Unit)? = null
) {
    val bgColor = parseHexColor(state.bgColorHex, Color(0xFFF8F7F0))

    Box(
        modifier = modifier
            .size(360.dp)
            .background(bgColor)
            .padding(10.dp)
    ) {
        // Decorative Leaves Overlay
        if (state.showLeaves && state.leavesResId != 0) {
            val leafAlpha = state.leavesOpacity
            // Top Right Leaf Branch
            Image(
                painter = painterResource(id = state.leavesResId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(90.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 22.dp, y = (-18).dp)
                    .graphicsLayer(alpha = leafAlpha, rotationZ = 120f)
            )
            // Bottom Right Leaf Branch
            Image(
                painter = painterResource(id = state.leavesResId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 18.dp, y = 12.dp)
                    .graphicsLayer(alpha = leafAlpha, rotationZ = 45f)
            )
            // Bottom Left Leaf Branch
            Image(
                painter = painterResource(id = state.leavesResId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-18).dp, y = 12.dp)
                    .graphicsLayer(alpha = leafAlpha, rotationZ = (-45f))
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. HEADER SECTION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onSectionClick != null) Modifier.clickable { onSectionClick("header") } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pharmacy Logo Emblem
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalPharmacy,
                        contentDescription = null,
                        tint = Color(0xFF203D2E),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = state.pharmacyName,
                            color = Color(0xFF0D1B2A),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.5.sp
                        )
                        if (state.pharmacySubtitle.isNotEmpty()) {
                            Text(
                                text = state.pharmacySubtitle,
                                color = Color(0xFF203D2E),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tagline with Left and Right horizontal lines
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(Color(0xFFD0C8B0))
                    )
                    Text(
                        text = state.pharmacySlogan,
                        color = Color(0xFF555555),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(Color(0xFFD0C8B0))
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. PRODUCT GRID (2x2)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val products = state.products
                val row1 = products.take(2)
                val row2 = products.drop(2).take(2)

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for ((idx, prod) in row1.withIndex()) {
                        CarefluxProductCardItem(
                            product = prod,
                            index = idx,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onSectionClick?.invoke("product_$idx") }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for ((idx, prod) in row2.withIndex()) {
                        val realIndex = idx + 2
                        CarefluxProductCardItem(
                            product = prod,
                            index = realIndex,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onSectionClick?.invoke("product_$realIndex") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. BOTTOM FEATURE BAR
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onSectionClick != null) Modifier.clickable { onSectionClick("features") } else Modifier)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeatureBarItem(
                        title = state.trustBadge1Title,
                        subtitle = state.trustBadge1Sub,
                        icon = Icons.Filled.Shield,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFE5E7EB)))
                    FeatureBarItem(
                        title = state.trustBadge2Title,
                        subtitle = state.trustBadge2Sub,
                        icon = Icons.Filled.SupportAgent,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFE5E7EB)))
                    FeatureBarItem(
                        title = state.trustBadge3Title,
                        subtitle = state.trustBadge3Sub,
                        icon = Icons.Filled.LocalShipping,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFE5E7EB)))
                    FeatureBarItem(
                        title = state.trustBadge4Title,
                        subtitle = state.trustBadge4Sub,
                        icon = Icons.Filled.Chat,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 4. BOTTOM SIGNATURE
            Text(
                text = state.footerTagline,
                color = Color(0xFF2E5A3C),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .then(if (onSectionClick != null) Modifier.clickable { onSectionClick("header") } else Modifier)
            )
        }
    }
}

@Composable
fun CarefluxProductCardItem(
    product: PromoProductConfig,
    index: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgTint = parseHexColor(product.bgTintHex, Color(0xFFE8ECE0))
    val priceColor = parseHexColor(product.priceColorHex, Color(0xFF1E4D2B))
    val badgeBg = parseHexColor(product.badgeBgHex, Color(0xFFA1C19C))

    val badgeVector = when (product.badgeIcon) {
        "droplet" -> Icons.Filled.WaterDrop
        "moon" -> Icons.Filled.NightsStay
        "lightning" -> Icons.Filled.Bolt
        "leaf" -> Icons.Filled.Eco
        "shield" -> Icons.Filled.Shield
        else -> Icons.Filled.MedicalServices
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Image Box
            Box(
                modifier = Modifier
                    .width(62.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgTint)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!product.imageUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = product.imageUri,
                        contentDescription = product.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (product.drawableResId != null) {
                    Image(
                        painter = painterResource(id = product.drawableResId),
                        contentDescription = product.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MedicalServices,
                        contentDescription = null,
                        tint = priceColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right Product Info
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 2.dp, top = 2.dp, bottom = 2.dp)
            ) {
                // Floating Badge Icon (Top Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeVector,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = product.name,
                            color = Color(0xFF0D1B2A),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (product.subtitle.isNotEmpty()) {
                            Text(
                                text = product.subtitle,
                                color = Color(0xFF6B7280),
                                fontSize = 7.5.sp,
                                lineHeight = 9.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Price with Curved Brush Underline
                    Column(modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            text = "${product.currency}${product.price}",
                            color = priceColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black
                        )
                        // Smooth curved brush line
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(3.dp)
                        ) {
                            val path = Path().apply {
                                moveTo(0f, size.height * 0.8f)
                                quadraticTo(
                                    size.width / 2f, size.height * 0.1f,
                                    size.width, size.height * 0.9f
                                )
                            }
                            drawPath(
                                path = path,
                                color = priceColor,
                                style = Stroke(
                                    width = 2.5f,
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureBarItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFF203D2E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(9.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            color = Color(0xFF0D1B2A),
            fontSize = 6.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 7.5.sp
        )
        Text(
            text = subtitle,
            color = Color(0xFF6B7280),
            fontSize = 5.5.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
fun Vibrant3DBlastTemplateLayout(
    selectedItems: List<InventoryItem>,
    priceOverrides: Map<Int, String>,
    nameOverrides: Map<Int, String>,
    resolvedBitmaps: Map<Int, Bitmap>,
    state: PromoStudioState
) {
    val items = selectedItems.ifEmpty { dummyInventorySample() }
    
    Box(
        modifier = Modifier
            .size(360.dp)
            .background(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color(0xFF005F5A), Color(0xFF003835), Color(0xFF001210))
                )
            )
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF00E5FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.LocalPharmacy, contentDescription = null, tint = Color(0xFF003835), modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(state.pharmacyName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(state.pharmacySlogan, color = Color(0xFF80DFD5), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 3D Starburst Gold Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF6F00))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(state.badgeEmblemText, color = Color(0xFF1B5E20), fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }

            // 3D Title Banner Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF003D39))
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(10.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFD54F))
                            .padding(horizontal = 12.dp, vertical = 1.dp)
                    ) {
                        Text("TODAY'S", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    }
                    Text(
                        text = "SPECIAL OFFERS",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "& PROMOTIONS",
                        color = Color(0xFF80DEEA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }

            // Product Items Container
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val stripeColors = listOf(Color(0xFF00897B), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFE65100))

                if (items.size == 1) {
                    val item = items[0]
                    val pName = state.nameOverrides[item.id] ?: item.name
                    val pPrice = formatNairaPrice(state.priceOverrides[item.id], item.price)
                    val pDosage = state.dosageOverrides[item.id] ?: item.dosage
                    val bmp = resolvedBitmaps[item.id]
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(90.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE0F2F1)), contentAlignment = Alignment.Center) {
                                if (bmp != null) {
                                    androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = pName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                } else {
                                    Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = Color(0xFF00897B), modifier = Modifier.size(44.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(pName, color = Color(0xFF1F2937), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                    Text(pDosage, color = Color(0xFF6B7280), fontSize = 9.sp, maxLines = 1)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFFFF3D00)).padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("ONLY $pPrice", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                } else {
                    val rowCount = if (items.size <= 2) 1 else 2
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (r in 0 until rowCount) {
                            Row(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val colCount = if (items.size == 2) 2 else if (r == 1 && items.size == 3) 1 else 2
                                for (c in 0 until colCount) {
                                    val idx = if (r == 1 && items.size == 3) 2 else r * 2 + c
                                    if (idx < items.size) {
                                        val item = items[idx]
                                        val pName = state.nameOverrides[item.id] ?: item.name
                                        val pPrice = formatNairaPrice(state.priceOverrides[item.id], item.price)
                                        val pDosage = state.dosageOverrides[item.id] ?: item.dosage
                                        val bmp = resolvedBitmaps[item.id]
                                        val stripeColor = stripeColors[idx % stripeColors.size]

                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        ) {
                                            Row(modifier = Modifier.fillMaxSize()) {
                                                Box(
                                                    modifier = Modifier.fillMaxHeight().width(56.dp).background(stripeColor.copy(alpha = 0.15f)).padding(4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (bmp != null) {
                                                        androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = pName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                                    } else {
                                                        Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = stripeColor, modifier = Modifier.size(28.dp))
                                                    }
                                                }
                                                Column(
                                                    modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
                                                    verticalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text(pName, color = Color(0xFF1F2937), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                        Text(pDosage, color = Color(0xFF6B7280), fontSize = 8.sp, maxLines = 1)
                                                    }
                                                    Box(
                                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(stripeColor).padding(vertical = 3.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("ONLY $pPrice", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
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

            // Bottom Trust Badges Bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = Color(0xFF80DEEA), modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(state.trustBadge1Title, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = Color(0xFF80DEEA), modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(state.trustBadge2Title, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Color(0xFF80DEEA), modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(state.trustBadge3Title, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFFD54F), Color(0xFFFFB300))
                            )
                        )
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Chat, contentDescription = null, tint = Color(0xFF1B5E20), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ORDER NOW | ${state.footerTagline}", color = Color(0xFF1B5E20), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun CyanGoldGlossyTemplateLayout(
    selectedItems: List<InventoryItem>,
    priceOverrides: Map<Int, String>,
    nameOverrides: Map<Int, String>,
    resolvedBitmaps: Map<Int, Bitmap>,
    state: PromoStudioState
) {
    val items = selectedItems.ifEmpty { dummyInventorySample() }

    Box(
        modifier = Modifier
            .size(360.dp)
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color(0xFF00796B), Color(0xFF004D40))
                )
            )
            .padding(6.dp)
    ) {
        // Inner Glossy White Frame
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalPharmacy, contentDescription = null, tint = Color(0xFF00796B), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(state.pharmacyName, color = Color(0xFF004D40), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFFFB300), Color(0xFFFF6F00))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(state.badgeEmblemText, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Glossy Title Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF00695C))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFD54F))
                                .padding(horizontal = 10.dp, vertical = 1.dp)
                        ) {
                            Text("TODAY'S", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                        Text("SPECIAL OFFERS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text("& PROMOTIONS", color = Color(0xFF80DEEA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Grid Items
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val cardBorders = listOf(Color(0xFF00796B), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFE65100))
                    val badgeIcons = listOf(Icons.Filled.WaterDrop, Icons.Filled.NightsStay, Icons.Filled.Bolt, Icons.Filled.Male)

                    val rowCount = if (items.size <= 2) 1 else 2
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (r in 0 until rowCount) {
                            Row(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val colCount = if (items.size == 2) 2 else if (r == 1 && items.size == 3) 1 else 2
                                for (c in 0 until colCount) {
                                    val idx = if (r == 1 && items.size == 3) 2 else r * 2 + c
                                    if (idx < items.size) {
                                        val item = items[idx]
                                        val pName = state.nameOverrides[item.id] ?: item.name
                                        val pPrice = formatNairaPrice(state.priceOverrides[item.id], item.price)
                                        val pDosage = state.dosageOverrides[item.id] ?: item.dosage
                                        val bmp = resolvedBitmaps[item.id]
                                        val borderColor = cardBorders[idx % cardBorders.size]
                                        val icon = badgeIcons[idx % badgeIcons.size]

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                                .background(Color(0xFFF9FAFB))
                                                .padding(4.dp)
                                        ) {
                                            // Floating Badge Icon
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(20.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(borderColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(52.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (bmp != null) {
                                                        androidx.compose.foundation.Image(
                                                            bitmap = bmp.asImageBitmap(),
                                                            contentDescription = pName,
                                                            contentScale = ContentScale.Fit,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = borderColor, modifier = Modifier.size(28.dp))
                                                    }
                                                }

                                                Column(
                                                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                                                    verticalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(pName, color = Color(0xFF111827), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                    Text(pDosage, color = Color(0xFF6B7280), fontSize = 8.sp, maxLines = 1)
                                                    Spacer(modifier = Modifier.height(2.dp))

                                                    // CTA Price Tag Pill
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0xFFD32F2F))
                                                            .padding(vertical = 2.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("NOW $pPrice", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
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

                // Bottom Footer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF004D40))
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.footerTagline, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EcoOrganicTemplateLayout(
    selectedItems: List<InventoryItem>,
    priceOverrides: Map<Int, String>,
    nameOverrides: Map<Int, String>,
    resolvedBitmaps: Map<Int, Bitmap>,
    state: PromoStudioState
) {
    val items = selectedItems.ifEmpty { dummyInventorySample() }

    Box(
        modifier = Modifier
            .size(360.dp)
            .background(Color(0xFFF9F8F5))
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.LocalPharmacy, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                Text(state.pharmacyName, color = Color(0xFF1B4332), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(state.pharmacySlogan, color = Color(0xFF666666), fontSize = 9.sp)
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth(0.6f)
                        .height(1.dp)
                        .background(Color(0xFFCCCCCC))
                )
            }

            // Grid Cards
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                val cardTints = listOf(Color(0xFFEFF7F2), Color(0xFFEEF5FC), Color(0xFFFFF9E6), Color(0xFFF0F8F3))
                val underlineColors = listOf(Color(0xFF2E7D32), Color(0xFF1565C0), Color(0xFFF57F17), Color(0xFF2E7D32))
                val badgeIcons = listOf(Icons.Filled.WaterDrop, Icons.Filled.NightsStay, Icons.Filled.Bolt, Icons.Filled.Eco)

                val rowCount = if (items.size <= 2) 1 else 2
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (r in 0 until rowCount) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val colCount = if (items.size == 2) 2 else if (r == 1 && items.size == 3) 1 else 2
                            for (c in 0 until colCount) {
                                val idx = if (r == 1 && items.size == 3) 2 else r * 2 + c
                                if (idx < items.size) {
                                    val item = items[idx]
                                    val pName = state.nameOverrides[item.id] ?: item.name
                                    val pPrice = formatNairaPrice(state.priceOverrides[item.id], item.price)
                                    val pDosage = state.dosageOverrides[item.id] ?: item.dosage
                                    val bmp = resolvedBitmaps[item.id]
                                    val bgTint = cardTints[idx % cardTints.size]
                                    val underlineColor = underlineColors[idx % underlineColors.size]
                                    val icon = badgeIcons[idx % badgeIcons.size]

                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = bgTint),
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize().padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier.size(54.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (bmp != null) {
                                                    androidx.compose.foundation.Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = pName,
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = underlineColor, modifier = Modifier.size(30.dp))
                                                }
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f).padding(start = 6.dp),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(pName, color = Color(0xFF1B4332), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                        Text(pDosage, color = Color(0xFF666666), fontSize = 8.sp, maxLines = 1)
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(RoundedCornerShape(9.dp))
                                                            .background(underlineColor.copy(alpha = 0.2f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(icon, contentDescription = null, tint = underlineColor, modifier = Modifier.size(10.dp))
                                                    }
                                                }

                                                Column(modifier = Modifier.padding(top = 2.dp)) {
                                                    Text(pPrice, color = Color(0xFF111827), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(0.8f)
                                                            .height(2.dp)
                                                            .background(underlineColor)
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

            // Footer Trust Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Verified, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(state.trustBadge1Title, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(state.trustBadge2Title, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(state.trustBadge3Title, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = state.footerTagline,
                color = Color(0xFF1B4332),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun ProMedicalGridTemplateLayout(
    selectedItems: List<InventoryItem>,
    priceOverrides: Map<Int, String>,
    nameOverrides: Map<Int, String>,
    resolvedBitmaps: Map<Int, Bitmap>,
    state: PromoStudioState
) {
    val items = selectedItems.ifEmpty { dummyInventorySample() }

    Box(
        modifier = Modifier
            .size(360.dp)
            .background(Color(0xFFF4F6F8))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalPharmacy, contentDescription = null, tint = Color(0xFF00695C), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(state.pharmacyName, color = Color(0xFF004D40), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(state.topTrustText, color = Color(0xFF1B5E20), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Cards Grid
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val cardBorders = listOf(Color(0xFF00695C), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFE65100))

                val rowCount = if (items.size <= 2) 1 else 2
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (r in 0 until rowCount) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val colCount = if (items.size == 2) 2 else if (r == 1 && items.size == 3) 1 else 2
                            for (c in 0 until colCount) {
                                val idx = if (r == 1 && items.size == 3) 2 else r * 2 + c
                                if (idx < items.size) {
                                    val item = items[idx]
                                    val pName = state.nameOverrides[item.id] ?: item.name
                                    val pPrice = formatNairaPrice(state.priceOverrides[item.id], item.price)
                                    val pDosage = state.dosageOverrides[item.id] ?: item.dosage
                                    val bmp = resolvedBitmaps[item.id]
                                    val borderColor = cardBorders[idx % cardBorders.size]

                                    val bullet1 = state.featureBullet1Overrides[item.id] ?: "Accurate & Reliable"
                                    val bullet2 = state.featureBullet2Overrides[item.id] ?: "Easy Self-Testing"

                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, borderColor),
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize().padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier.size(52.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (bmp != null) {
                                                    androidx.compose.foundation.Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = pName,
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = borderColor, modifier = Modifier.size(28.dp))
                                                }
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f).padding(start = 4.dp),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text(pName, color = Color(0xFF111827), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                    Text(pDosage, color = Color(0xFF6B7280), fontSize = 7.sp, maxLines = 1)
                                                }

                                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = borderColor, modifier = Modifier.size(8.dp))
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(bullet1, fontSize = 7.sp, color = Color(0xFF374151), maxLines = 1)
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = borderColor, modifier = Modifier.size(8.dp))
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(bullet2, fontSize = 7.sp, color = Color(0xFF374151), maxLines = 1)
                                                    }
                                                }

                                                // Price Tag Pill
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(borderColor)
                                                        .padding(vertical = 2.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(pPrice, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
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

            // Footer Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF004D40))
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(state.trustBadge1Title, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    Text(state.trustBadge2Title, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    Text(state.trustBadge3Title, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    Text(state.trustBadge4Title, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = state.footerTagline,
                color = Color(0xFF004D40),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 1.dp)
            )
        }
    }
}

@Composable
fun MedicalOutreachTemplateLayout(
    selectedItems: List<InventoryItem>,
    priceOverrides: Map<Int, String>,
    nameOverrides: Map<Int, String>,
    resolvedBitmaps: Map<Int, Bitmap>,
    state: PromoStudioState
) {
    Box(
        modifier = Modifier
            .size(360.dp)
            .background(Color(0xFFF7F2FA))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalPharmacy, contentDescription = null, tint = Color(0xFF4A148C), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(state.pharmacyName, color = Color(0xFF4A148C), fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Text(state.pharmacySlogan, color = Color(0xFF388E3C), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF4A148C))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(state.outreachOccasion, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Headline Callout
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.outreachSubhead, color = Color(0xFF4A148C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(state.outreachTitle, color = Color(0xFF388E3C), fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }

            // Message Block
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Text(
                    text = state.outreachMessage,
                    color = Color(0xFF374151),
                    fontSize = 8.sp,
                    modifier = Modifier.padding(6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // Free Health Services Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEDE7F6))
                    .padding(6.dp)
            ) {
                Column {
                    Text("FREE HEALTH SERVICES", color = Color(0xFF4A148C), fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFF4A148C), modifier = Modifier.size(14.dp))
                            Text(state.outreachService1, fontSize = 6.sp, color = Color(0xFF4A148C), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = Color(0xFF4A148C), modifier = Modifier.size(14.dp))
                            Text(state.outreachService2, fontSize = 6.sp, color = Color(0xFF4A148C), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = Color(0xFF4A148C), modifier = Modifier.size(14.dp))
                            Text(state.outreachService3, fontSize = 6.sp, color = Color(0xFF4A148C), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Verified, contentDescription = null, tint = Color(0xFF4A148C), modifier = Modifier.size(14.dp))
                            Text(state.outreachService4, fontSize = 6.sp, color = Color(0xFF4A148C), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }

            // Discounts Box
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(state.outreachDiscount1, color = Color(0xFF1B5E20), fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(state.outreachDiscount2, color = Color(0xFF1B5E20), fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }

            // Event Details Schedule
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Event, contentDescription = null, tint = Color(0xFF4A148C), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(state.outreachDate, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFF4A148C), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(state.outreachTime, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFF4A148C), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(state.outreachLocation, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            // Bottom Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF4A148C))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Thank you for being part of our journey! ♥", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FourItemsGrid(
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
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (i in 0..1) {
                if (i < selectedItems.size) {
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
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        promoTheme = promoTheme
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (i in 2..3) {
                if (i < selectedItems.size) {
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
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        promoTheme = promoTheme
                    )
                }
            }
        }
    }
}

private fun dummyInventorySample(): List<InventoryItem> {
    return listOf(
        InventoryItem(id = 101, name = "Accu-Chek Test Strips", dosage = "1 Strip", stockQuantity = 50, minRequiredStock = 5, category = "Diabetic Care", price = 20000.0),
        InventoryItem(id = 102, name = "Advil PM", dosage = "200mg + 38mg", stockQuantity = 30, minRequiredStock = 5, category = "Pain Relief", price = 13000.0),
        InventoryItem(id = 103, name = "Maca 500mg", dosage = "60 Capsules", stockQuantity = 40, minRequiredStock = 5, category = "Supplements", price = 23000.0),
        InventoryItem(id = 104, name = "Saw Palmetto 500mg", dosage = "100 Capsules", stockQuantity = 25, minRequiredStock = 5, category = "Supplements", price = 20000.0)
    )
}
