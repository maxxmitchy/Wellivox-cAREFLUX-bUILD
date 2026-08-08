package com.example.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@JsonClass(generateAdapter = true)
data class CarouselResponse(
    val topicTitle: String,
    val caption: String,
    val slides: List<CarouselSlide>
)

@JsonClass(generateAdapter = true)
data class CarouselSlide(
    val slideNumber: Int,
    val heading: String,
    val text: String,
    val keyPoints: List<String>,
    val recommendedProducts: List<String>? = null,
    val imageUri: String? = null
)

@JsonClass(generateAdapter = true)
data class PromoProductConfig(
    val id: Int,
    val name: String,
    val subtitle: String,
    val price: String,
    val currency: String = "₦",
    val imageUri: String? = null,
    val drawableResId: Int? = null,
    val bgTintHex: String = "#E8ECE0",
    val priceColorHex: String = "#1E4D2B",
    val textColorHex: String = "#0D1B2A",
    val badgeIcon: String = "droplet",
    val badgeBgHex: String = "#A1C19C",
    val isVisible: Boolean = true
)

enum class FlyerTemplateStyle(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val isDark: Boolean
) {
    CAREFLUX_MASTER("CAREFLUX_MASTER", "Careflux Master Flyer (Pixel-Perfect)", "Exact replica of Careflux Pharmacy 4-product promo flyer with live editor", false),
    VIBRANT_3D_BLAST("VIBRANT_3D_BLAST", "Vibrant 3D Promo Blast", "Dark radiant gradient, bold 3D headers & discount tags", true),
    CYAN_GOLD_GLOSSY("CYAN_GOLD_GLOSSY", "Cyan & Gold Glossy 3D", "Teal background, glossy frame, metallic gold emblem", true),
    PRO_MEDICAL_GRID("PRO_MEDICAL_GRID", "Pro Medical Grid", "Feature bullet points, side-by-side cards & price pills", false),
    ECO_ORGANIC_CLEAN("ECO_ORGANIC_CLEAN", "Eco-Organic Clean", "Soft pastel cards with leaf accents & signature", false),
    MEDICAL_OUTREACH("MEDICAL_OUTREACH", "Medical Outreach Event", "Anniversary & health event poster with service schedule", false)
}

enum class PromoUiMode {
    Selection,
    Configuration,
    Generating,
    Success
}

data class PromoStudioState(
    val uiMode: PromoUiMode = PromoUiMode.Selection,
    val selectedItems: List<InventoryItem> = emptyList(),
    val priceOverrides: Map<Int, String> = emptyMap(),
    val nameOverrides: Map<Int, String> = emptyMap(),
    val dosageOverrides: Map<Int, String> = emptyMap(),
    val featureBullet1Overrides: Map<Int, String> = emptyMap(),
    val featureBullet2Overrides: Map<Int, String> = emptyMap(),
    val badgeIconOverrides: Map<Int, String> = emptyMap(),
    
    val templateStyle: FlyerTemplateStyle = FlyerTemplateStyle.CAREFLUX_MASTER,
    val isOfferBanner: Boolean = true,
    
    // Careflux Master Products (100% Editable)
    val products: List<PromoProductConfig> = listOf(
        PromoProductConfig(
            id = 1,
            name = "ACCU-CHEK\nTest Strips",
            subtitle = "1 Strip",
            price = "20,000",
            currency = "₦",
            drawableResId = com.example.R.drawable.accu_chek_strips_1785078662332,
            bgTintHex = "#E8ECE0",
            priceColorHex = "#1E4D2B",
            textColorHex = "#0D1B2A",
            badgeIcon = "droplet",
            badgeBgHex = "#A1C19C"
        ),
        PromoProductConfig(
            id = 2,
            name = "ADVIL PM",
            subtitle = "Ibuprofen +\nDiphenhydramine\n200mg + 38mg",
            price = "13,000",
            currency = "₦",
            drawableResId = com.example.R.drawable.advil_pm_box_1785078677350,
            bgTintHex = "#E3EAF7",
            priceColorHex = "#3B62AD",
            textColorHex = "#0D1B2A",
            badgeIcon = "moon",
            badgeBgHex = "#A6BEE0"
        ),
        PromoProductConfig(
            id = 3,
            name = "MACA",
            subtitle = "500mg",
            price = "23,000",
            currency = "₦",
            drawableResId = com.example.R.drawable.maca_bottle_1785078692587,
            bgTintHex = "#FAF2D8",
            priceColorHex = "#D98A11",
            textColorHex = "#0D1B2A",
            badgeIcon = "lightning",
            badgeBgHex = "#F3DC82"
        ),
        PromoProductConfig(
            id = 4,
            name = "SAW\nPALMETTO",
            subtitle = "500mg",
            price = "20,000",
            currency = "₦",
            drawableResId = com.example.R.drawable.saw_palmetto_bottle_1785078705069,
            bgTintHex = "#E3EDE2",
            priceColorHex = "#276B2D",
            textColorHex = "#0D1B2A",
            badgeIcon = "leaf",
            badgeBgHex = "#AFD1A9"
        )
    ),

    // Active Section for Live Editor Drawer/Panel
    val activeEditSection: String = "grid", // "grid", "product_0", "product_1", "product_2", "product_3", "header", "features", "background"
    val bgColorHex: String = "#F8F7F0",
    val showLeaves: Boolean = true,
    val leavesOpacity: Float = 0.9f,
    val leavesResId: Int = com.example.R.drawable.decorative_leaves_1785078719121,

    // Header & Taglines (Editable)
    val pharmacyName: String = "CAREFLUX",
    val pharmacySubtitle: String = "PHARMACY",
    val pharmacySlogan: String = "Quality products. Trusted care.",
    val headerTitle: String = "TODAY'S SPECIAL OFFERS",
    val headerTitleSuffix: String = "& PROMOTIONS",
    val subheader: String = "TODAY'S SPECIAL OFFERS & PROMOTIONS",
    val topTrustText: String = "QUALITY YOU CAN TRUST, CARE YOU CAN COUNT ON.",
    val badgeEmblemText: String = "SAVE MORE LIVE BETTER ★★★",
    val footerTagline: String = "Your health, our priority. ♡",
    
    // Footer Trust Badges (Editable)
    val trustBadge1Title: String = "100% GENUINE",
    val trustBadge1Sub: String = "Quality you can trust",
    val trustBadge2Title: String = "ASK OUR PHARMACIST",
    val trustBadge2Sub: String = "We're here to help",
    val trustBadge3Title: String = "FAST & RELIABLE\nDELIVERY",
    val trustBadge3Sub: String = "To your doorstep",
    val trustBadge4Title: String = "ORDER & CHAT",
    val trustBadge4Sub: String = "Easy on WhatsApp",
    
    // Medical Outreach Fields (Editable)
    val outreachTitle: String = "MEDICAL OUTREACH",
    val outreachSubhead: String = "Join us for a",
    val outreachOccasion: String = "1ST ANNIVERSARY",
    val outreachMessage: String = "As we celebrate our 1st Anniversary, we want to say THANK YOU to our amazing customers for your trust and support.",
    val outreachBannerRight: String = "Let's keep building a healthier community together!",
    val outreachService1: String = "BLOOD PRESSURE & PULSE CHECKS",
    val outreachService2: String = "FREE SUGAR TESTS",
    val outreachService3: String = "FREE HIV TESTING",
    val outreachService4: String = "HEPATITIS B TEST (First 100)",
    val outreachDiscount1: String = "5-10% DISCOUNT on all purchases",
    val outreachDiscount2: String = "FREE WORM MEDICINE & SUPPLEMENTS",
    val outreachDate: String = "THURSDAY, 6TH AUGUST, 2026",
    val outreachTime: String = "10:00 AM",
    val outreachLocation: String = "CAREFLUX PHARMACY, Main Station Road, Port-Harcourt",

    val promoTheme: PromoThemeStyle = PromoThemeStyle.MIDNIGHT_CYAN,
    val generatedUri: Uri? = null,

    // Music & MP4 Export
    val musicTrack: AudioTrackOption = AudioTrackOption.UPBEAT_RETAIL,
    val customAudioUri: Uri? = null,
    val videoAspectRatio: VideoAspectRatio = VideoAspectRatio.SQUARE_1_1,
    val slideDurationSeconds: Int = 4,
    val isVideoEncoding: Boolean = false,
    val videoEncodingProgress: Float = 0f,
    val generatedVideoUri: Uri? = null
)

class AIContentEngineViewModel(private val repository: PharmacyRepository) : ViewModel() {

    private val moshi = Moshi.Builder().build()
    private val carouselAdapter: JsonAdapter<CarouselResponse> = moshi.adapter(CarouselResponse::class.java)
    private val slidesAdapter: JsonAdapter<List<CarouselSlide>> = moshi.adapter(Types.newParameterizedType(List::class.java, CarouselSlide::class.java))

    val history: StateFlow<List<AICarousel>> = repository.allAICarousels.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val inventoryItems: StateFlow<List<InventoryItem>> = repository.allInventoryItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _promoState = MutableStateFlow(PromoStudioState())
    val promoState: StateFlow<PromoStudioState> = _promoState

    fun syncProductsFromSelectedItems() {
        val current = _promoState.value
        if (current.selectedItems.isEmpty()) return

        val newProducts = current.products.toMutableList()
        val bgTints = listOf("#E8ECE0", "#E3EAF7", "#FAF2D8", "#E3EDE2")
        val priceColors = listOf("#1E4D2B", "#3B62AD", "#D98A11", "#276B2D")
        val badgeIcons = listOf("droplet", "moon", "lightning", "leaf")
        val badgeBgs = listOf("#A1C19C", "#A6BEE0", "#F3DC82", "#AFD1A9")

        current.selectedItems.take(4).forEachIndexed { index, item ->
            val formattedPrice = try {
                if (item.price % 1.0 == 0.0) {
                    String.format("%,.0f", item.price)
                } else {
                    String.format("%,.2f", item.price)
                }
            } catch (e: Exception) {
                item.price.toString()
            }

            val subtitleStr = if (item.brand.isNotBlank() && item.category.isNotBlank()) {
                "${item.brand} • ${item.category}"
            } else if (item.brand.isNotBlank()) {
                item.brand
            } else if (item.category.isNotBlank()) {
                item.category
            } else {
                "Health & Wellness"
            }

            val defaultProd = current.products.getOrElse(index) { current.products[0] }

            newProducts[index] = PromoProductConfig(
                id = item.id,
                name = item.name,
                subtitle = subtitleStr,
                price = current.priceOverrides[item.id] ?: formattedPrice,
                currency = "₦",
                imageUri = item.imageUri,
                drawableResId = if (item.imageUri.isNullOrEmpty()) defaultProd.drawableResId else null,
                bgTintHex = bgTints.getOrElse(index) { "#E8ECE0" },
                priceColorHex = priceColors.getOrElse(index) { "#1E4D2B" },
                textColorHex = "#0D1B2A",
                badgeIcon = badgeIcons.getOrElse(index) { "droplet" },
                badgeBgHex = badgeBgs.getOrElse(index) { "#A1C19C" }
            )
        }

        val selectedCount = current.selectedItems.size
        if (selectedCount in 1..3) {
            for (i in selectedCount..3) {
                val sourceProd = newProducts[i % selectedCount]
                newProducts[i] = sourceProd.copy(
                    id = i + 100,
                    bgTintHex = bgTints.getOrElse(i) { "#E8ECE0" },
                    priceColorHex = priceColors.getOrElse(i) { "#1E4D2B" },
                    badgeIcon = badgeIcons.getOrElse(i) { "droplet" },
                    badgeBgHex = badgeBgs.getOrElse(i) { "#A1C19C" }
                )
            }
        }

        _promoState.value = current.copy(products = newProducts)
    }

    fun togglePromoProductSelection(item: InventoryItem) {
        val current = _promoState.value
        val list = current.selectedItems.toMutableList()
        val exists = list.any { it.id == item.id }
        if (exists) {
            list.removeAll { it.id == item.id }
        } else {
            if (list.size < 4) {
                list.add(item)
            }
        }
        _promoState.value = current.copy(selectedItems = list)
        syncProductsFromSelectedItems()
    }

    fun updatePromoPriceOverride(itemId: Int, priceStr: String) {
        val current = _promoState.value
        val overrides = current.priceOverrides.toMutableMap()
        overrides[itemId] = priceStr
        _promoState.value = current.copy(priceOverrides = overrides)
    }

    fun updatePromoNameOverride(itemId: Int, nameStr: String) {
        val current = _promoState.value
        val overrides = current.nameOverrides.toMutableMap()
        overrides[itemId] = nameStr
        _promoState.value = current.copy(nameOverrides = overrides)
    }

    fun updateFlyerTemplateStyle(style: FlyerTemplateStyle) {
        val current = _promoState.value
        _promoState.value = current.copy(templateStyle = style)
    }

    fun updatePromoDosageOverride(itemId: Int, dosageStr: String) {
        val current = _promoState.value
        val map = current.dosageOverrides.toMutableMap()
        map[itemId] = dosageStr
        _promoState.value = current.copy(dosageOverrides = map)
    }

    fun updatePromoFeatureBullet1(itemId: Int, bulletStr: String) {
        val current = _promoState.value
        val map = current.featureBullet1Overrides.toMutableMap()
        map[itemId] = bulletStr
        _promoState.value = current.copy(featureBullet1Overrides = map)
    }

    fun updatePromoFeatureBullet2(itemId: Int, bulletStr: String) {
        val current = _promoState.value
        val map = current.featureBullet2Overrides.toMutableMap()
        map[itemId] = bulletStr
        _promoState.value = current.copy(featureBullet2Overrides = map)
    }

    fun updatePromoBadgeIcon(itemId: Int, badgeStr: String) {
        val current = _promoState.value
        val map = current.badgeIconOverrides.toMutableMap()
        map[itemId] = badgeStr
        _promoState.value = current.copy(badgeIconOverrides = map)
    }

    fun updateMasterProductConfig(index: Int, newConfig: PromoProductConfig) {
        val current = _promoState.value
        val list = current.products.toMutableList()
        if (index in list.indices) {
            list[index] = newConfig
            _promoState.value = current.copy(products = list)
        }
    }

    fun updateMasterProductName(index: Int, name: String) {
        val current = _promoState.value
        val list = current.products.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(name = name)
            _promoState.value = current.copy(products = list)
        }
    }

    fun updateMasterProductSubtitle(index: Int, subtitle: String) {
        val current = _promoState.value
        val list = current.products.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(subtitle = subtitle)
            _promoState.value = current.copy(products = list)
        }
    }

    fun updateMasterProductPrice(index: Int, price: String) {
        val current = _promoState.value
        val list = current.products.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(price = price)
            _promoState.value = current.copy(products = list)
        }
    }

    fun updateMasterProductImageUri(index: Int, uriStr: String?) {
        val current = _promoState.value
        val list = current.products.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(imageUri = uriStr)
            _promoState.value = current.copy(products = list)
        }
    }

    fun updateMasterProductBgTint(index: Int, bgHex: String) {
        val current = _promoState.value
        val list = current.products.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(bgTintHex = bgHex)
            _promoState.value = current.copy(products = list)
        }
    }

    fun updateMasterProductPriceColor(index: Int, priceHex: String) {
        val current = _promoState.value
        val list = current.products.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(priceColorHex = priceHex)
            _promoState.value = current.copy(products = list)
        }
    }

    fun updateMasterProductBadgeIcon(index: Int, badgeIcon: String) {
        val current = _promoState.value
        val list = current.products.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(badgeIcon = badgeIcon)
            _promoState.value = current.copy(products = list)
        }
    }

    fun updatePharmacyHeader(name: String, subtitle: String, slogan: String) {
        val current = _promoState.value
        _promoState.value = current.copy(
            pharmacyName = name,
            pharmacySubtitle = subtitle,
            pharmacySlogan = slogan
        )
    }

    fun updateTrustBadges(b1Title: String, b1Sub: String, b2Title: String, b2Sub: String, b3Title: String, b3Sub: String, b4Title: String, b4Sub: String) {
        val current = _promoState.value
        _promoState.value = current.copy(
            trustBadge1Title = b1Title,
            trustBadge1Sub = b1Sub,
            trustBadge2Title = b2Title,
            trustBadge2Sub = b2Sub,
            trustBadge3Title = b3Title,
            trustBadge3Sub = b3Sub,
            trustBadge4Title = b4Title,
            trustBadge4Sub = b4Sub
        )
    }

    fun updateBackgroundConfig(bgColorHex: String, showLeaves: Boolean, leavesOpacity: Float) {
        val current = _promoState.value
        _promoState.value = current.copy(
            bgColorHex = bgColorHex,
            showLeaves = showLeaves,
            leavesOpacity = leavesOpacity
        )
    }

    fun updateActiveEditSection(section: String) {
        val current = _promoState.value
        _promoState.value = current.copy(activeEditSection = section)
    }

    fun updateFlyerHeaderDetails(
        pharmacyName: String? = null,
        pharmacySlogan: String? = null,
        headerTitle: String? = null,
        headerTitleSuffix: String? = null,
        subheader: String? = null,
        topTrustText: String? = null,
        badgeEmblemText: String? = null,
        footerTagline: String? = null
    ) {
        val cur = _promoState.value
        _promoState.value = cur.copy(
            pharmacyName = pharmacyName ?: cur.pharmacyName,
            pharmacySlogan = pharmacySlogan ?: cur.pharmacySlogan,
            headerTitle = headerTitle ?: cur.headerTitle,
            headerTitleSuffix = headerTitleSuffix ?: cur.headerTitleSuffix,
            subheader = subheader ?: cur.subheader,
            topTrustText = topTrustText ?: cur.topTrustText,
            badgeEmblemText = badgeEmblemText ?: cur.badgeEmblemText,
            footerTagline = footerTagline ?: cur.footerTagline
        )
    }

    fun updateOutreachDetails(
        title: String? = null,
        subhead: String? = null,
        occasion: String? = null,
        message: String? = null,
        rightBanner: String? = null,
        service1: String? = null,
        service2: String? = null,
        service3: String? = null,
        service4: String? = null,
        discount1: String? = null,
        discount2: String? = null,
        date: String? = null,
        time: String? = null,
        location: String? = null
    ) {
        val cur = _promoState.value
        _promoState.value = cur.copy(
            outreachTitle = title ?: cur.outreachTitle,
            outreachSubhead = subhead ?: cur.outreachSubhead,
            outreachOccasion = occasion ?: cur.outreachOccasion,
            outreachMessage = message ?: cur.outreachMessage,
            outreachBannerRight = rightBanner ?: cur.outreachBannerRight,
            outreachService1 = service1 ?: cur.outreachService1,
            outreachService2 = service2 ?: cur.outreachService2,
            outreachService3 = service3 ?: cur.outreachService3,
            outreachService4 = service4 ?: cur.outreachService4,
            outreachDiscount1 = discount1 ?: cur.outreachDiscount1,
            outreachDiscount2 = discount2 ?: cur.outreachDiscount2,
            outreachDate = date ?: cur.outreachDate,
            outreachTime = time ?: cur.outreachTime,
            outreachLocation = location ?: cur.outreachLocation
        )
    }

    fun setPromoTemplateMode(isOfferBanner: Boolean) {
        val current = _promoState.value
        _promoState.value = current.copy(isOfferBanner = isOfferBanner)
    }

    fun updatePromoSubheader(subheader: String) {
        val current = _promoState.value
        _promoState.value = current.copy(subheader = subheader)
    }

    fun updatePromoTheme(theme: PromoThemeStyle) {
        val current = _promoState.value
        _promoState.value = current.copy(promoTheme = theme)
    }

    fun setPromoUiMode(mode: PromoUiMode) {
        if (mode == PromoUiMode.Configuration) {
            syncProductsFromSelectedItems()
        }
        val current = _promoState.value
        _promoState.value = current.copy(uiMode = mode)
    }

    fun setPromoGeneratedUri(uri: Uri?) {
        val current = _promoState.value
        _promoState.value = current.copy(generatedUri = uri)
    }

    fun updateMusicTrack(track: AudioTrackOption) {
        _promoState.value = _promoState.value.copy(musicTrack = track)
    }

    fun updateCustomAudioUri(uri: Uri?) {
        _promoState.value = _promoState.value.copy(
            customAudioUri = uri,
            musicTrack = AudioTrackOption.CUSTOM_FILE
        )
    }

    fun updateVideoAspectRatio(ratio: VideoAspectRatio) {
        _promoState.value = _promoState.value.copy(videoAspectRatio = ratio)
    }

    fun updateSlideDurationSeconds(seconds: Int) {
        _promoState.value = _promoState.value.copy(slideDurationSeconds = seconds)
    }

    fun setVideoEncodingState(isEncoding: Boolean, progress: Float = 0f, videoUri: Uri? = null) {
        _promoState.value = _promoState.value.copy(
            isVideoEncoding = isEncoding,
            videoEncodingProgress = progress,
            generatedVideoUri = videoUri ?: if (isEncoding) null else _promoState.value.generatedVideoUri
        )
    }

    fun clearPromoStudioState() {
        _promoState.value = PromoStudioState()
    }

    private val _uiState = MutableStateFlow<ContentEngineState>(ContentEngineState.Idle)
    val uiState: StateFlow<ContentEngineState> = _uiState

    fun parseSlides(json: String): List<CarouselSlide> {
        return try {
            slidesAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun viewHistoryItem(carousel: AICarousel) {
        val slides = parseSlides(carousel.slidesJson)
        val response = CarouselResponse(carousel.topicTitle, carousel.caption, slides)
        _uiState.value = ContentEngineState.Success(response, carousel.visualTheme, carousel.id)
    }

    fun deleteCarousel(carousel: AICarousel) {
        viewModelScope.launch {
            repository.deleteAICarousel(carousel)
            if (_uiState.value is ContentEngineState.Success) {
                _uiState.value = ContentEngineState.Idle
            }
        }
    }

    fun updateSlide(slideIndex: Int, updatedSlide: CarouselSlide) {
        val currentState = _uiState.value
        if (currentState is ContentEngineState.Success) {
            val newSlides = currentState.carousel.slides.toMutableList()
            if (slideIndex in newSlides.indices) {
                newSlides[slideIndex] = updatedSlide
                val updatedCarousel = currentState.carousel.copy(slides = newSlides)
                _uiState.value = currentState.copy(carousel = updatedCarousel)
                
                val carouselId = currentState.carouselId
                if (carouselId != null) {
                    viewModelScope.launch {
                        try {
                            val slidesJson = slidesAdapter.toJson(newSlides)
                            val updatedCarouselDb = AICarousel(
                                id = carouselId,
                                topicTitle = currentState.carousel.topicTitle,
                                caption = currentState.carousel.caption,
                                slidesJson = slidesJson,
                                visualTheme = currentState.theme
                            )
                            repository.insertAICarousel(updatedCarouselDb)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun updateCarouselMeta(newTitle: String, newCaption: String) {
        val currentState = _uiState.value
        if (currentState is ContentEngineState.Success) {
            val updatedCarousel = currentState.carousel.copy(topicTitle = newTitle, caption = newCaption)
            _uiState.value = currentState.copy(carousel = updatedCarousel)
            
            val carouselId = currentState.carouselId
            if (carouselId != null) {
                viewModelScope.launch {
                    try {
                        val slidesJson = slidesAdapter.toJson(updatedCarousel.slides)
                        val updatedCarouselDb = AICarousel(
                            id = carouselId,
                            topicTitle = newTitle,
                            caption = newCaption,
                            slidesJson = slidesJson,
                            visualTheme = currentState.theme
                        )
                        repository.insertAICarousel(updatedCarouselDb)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun moveSlide(slideIndex: Int, direction: Int) {
        val currentState = _uiState.value
        if (currentState is ContentEngineState.Success) {
            val newSlides = currentState.carousel.slides.toMutableList()
            val targetIndex = slideIndex + direction
            if (slideIndex in newSlides.indices && targetIndex in newSlides.indices) {
                val slide = newSlides.removeAt(slideIndex)
                newSlides.add(targetIndex, slide)
                val adjustedSlides = newSlides.mapIndexed { i, s -> s.copy(slideNumber = i + 1) }
                val updatedCarousel = currentState.carousel.copy(slides = adjustedSlides)
                _uiState.value = currentState.copy(carousel = updatedCarousel)
                
                val carouselId = currentState.carouselId
                if (carouselId != null) {
                    viewModelScope.launch {
                        try {
                            val slidesJson = slidesAdapter.toJson(adjustedSlides)
                            val updatedCarouselDb = AICarousel(
                                id = carouselId,
                                topicTitle = currentState.carousel.topicTitle,
                                caption = currentState.carousel.caption,
                                slidesJson = slidesJson,
                                visualTheme = currentState.theme
                            )
                            repository.insertAICarousel(updatedCarouselDb)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun addSlide() {
        val currentState = _uiState.value
        if (currentState is ContentEngineState.Success) {
            val newSlides = currentState.carousel.slides.toMutableList()
            val newSlideNumber = newSlides.size + 1
            val newSlide = CarouselSlide(
                slideNumber = newSlideNumber,
                heading = "New Slide Title",
                text = "Add details or explanations here.",
                keyPoints = listOf("Key point 1"),
                recommendedProducts = emptyList()
            )
            newSlides.add(newSlide)
            val updatedCarousel = currentState.carousel.copy(slides = newSlides)
            _uiState.value = currentState.copy(carousel = updatedCarousel)
            
            val carouselId = currentState.carouselId
            if (carouselId != null) {
                viewModelScope.launch {
                    try {
                        val slidesJson = slidesAdapter.toJson(newSlides)
                        val updatedCarouselDb = AICarousel(
                            id = carouselId,
                            topicTitle = currentState.carousel.topicTitle,
                            caption = currentState.carousel.caption,
                            slidesJson = slidesJson,
                            visualTheme = currentState.theme
                        )
                        repository.insertAICarousel(updatedCarouselDb)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun deleteSlide(slideIndex: Int) {
        val currentState = _uiState.value
        if (currentState is ContentEngineState.Success) {
            val newSlides = currentState.carousel.slides.toMutableList()
            if (slideIndex in newSlides.indices) {
                newSlides.removeAt(slideIndex)
                val adjustedSlides = newSlides.mapIndexed { i, s -> s.copy(slideNumber = i + 1) }
                val updatedCarousel = currentState.carousel.copy(slides = adjustedSlides)
                _uiState.value = currentState.copy(carousel = updatedCarousel)
                
                val carouselId = currentState.carouselId
                if (carouselId != null) {
                    viewModelScope.launch {
                        try {
                            val slidesJson = slidesAdapter.toJson(adjustedSlides)
                            val updatedCarouselDb = AICarousel(
                                id = carouselId,
                                topicTitle = currentState.carousel.topicTitle,
                                caption = currentState.carousel.caption,
                                slidesJson = slidesJson,
                                visualTheme = currentState.theme
                            )
                            repository.insertAICarousel(updatedCarouselDb)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun duplicateSlide(slideIndex: Int) {
        val currentState = _uiState.value
        if (currentState is ContentEngineState.Success) {
            val newSlides = currentState.carousel.slides.toMutableList()
            if (slideIndex in newSlides.indices) {
                val original = newSlides[slideIndex]
                val copy = original.copy(heading = "${original.heading} (Copy)")
                newSlides.add(slideIndex + 1, copy)
                val adjustedSlides = newSlides.mapIndexed { i, s -> s.copy(slideNumber = i + 1) }
                val updatedCarousel = currentState.carousel.copy(slides = adjustedSlides)
                _uiState.value = currentState.copy(carousel = updatedCarousel)
                
                val carouselId = currentState.carouselId
                if (carouselId != null) {
                    viewModelScope.launch {
                        try {
                            val slidesJson = slidesAdapter.toJson(adjustedSlides)
                            val updatedCarouselDb = AICarousel(
                                id = carouselId,
                                topicTitle = currentState.carousel.topicTitle,
                                caption = currentState.carousel.caption,
                                slidesJson = slidesJson,
                                visualTheme = currentState.theme
                            )
                            repository.insertAICarousel(updatedCarouselDb)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun setIdle() {
        _uiState.value = ContentEngineState.Idle
    }

    fun startSelection() {
        _uiState.value = ContentEngineState.SelectStrategy
    }

    fun startSpecificSelection() {
        viewModelScope.launch {
            val inventory = repository.allInventoryItems.first().filter { it.stockQuantity > 0 }
            _uiState.value = ContentEngineState.SelectSpecificProducts(inventory, emptySet())
        }
    }

    fun toggleProductSelection(productId: Int) {
        val state = _uiState.value
        if (state is ContentEngineState.SelectSpecificProducts) {
             val newSet = state.selectedIds.toMutableSet()
             if (newSet.contains(productId)) {
                 newSet.remove(productId)
             } else if (newSet.size < 15) {
                 newSet.add(productId)
             }
             _uiState.value = state.copy(selectedIds = newSet)
        }
    }

    fun generateCarouselFromSelection(theme: String) {
        val state = _uiState.value
        if (state is ContentEngineState.SelectSpecificProducts) {
            _uiState.value = ContentEngineState.Generating("Gathering data for your content...")
            val selectedItems = state.inventory.filter { state.selectedIds.contains(it.id) }
            proceedToGenerateCarousel(selectedItems, theme)
        }
    }

    fun generateCarouselWithStrategy(strategy: String, theme: String) {
        if (_uiState.value is ContentEngineState.Generating) return
        _uiState.value = ContentEngineState.Generating("Gathering data for your content...")
        viewModelScope.launch {
            val inventory = repository.allInventoryItems.first().filter { it.stockQuantity > 0 }
            val selectedItems = when (strategy) {
                "HighStock" -> inventory.sortedByDescending { it.stockQuantity }.take(15)
                "Random" -> inventory.shuffled().take(15)
                else -> emptyList()
            }
            proceedToGenerateCarousel(selectedItems, theme)
        }
    }

    private fun proceedToGenerateCarousel(items: List<com.example.data.InventoryItem>, theme: String) {
        viewModelScope.launch {
            _uiState.value = ContentEngineState.Generating("Designing an educational carousel based on your inventory...")
            try {
                val availableProducts = items.map { "${it.name} (${it.category})" }
                
                if (availableProducts.isEmpty()) {
                    _uiState.value = ContentEngineState.Error("No products selected or available.")
                    return@launch
                }

                val prompt = """
                    You are an expert AI Content Engine for a healthcare app called CareFlux.
                    We have the following products in our inventory: ${availableProducts.joinToString(", ")}.
                    
                    TASK: Create an engaging, highly-shareable educational social-media carousel post (5 to 6 slides).
                    First, select a single compelling health/wellness topic that heavily relates to the provided inventory.
                    Then, generate the slides for that topic.
                    Prioritize education over promotion (80% education, 20% product mention).
                    Mention products ONLY in the final slides when naturally relevant to the topic.
                    The tone should be professional, empathetic, and easy to understand.
                    
                    Respond strictly in JSON format matching this schema without any markdown wrapping (no ```json):
                    {
                        "topicTitle": "Topic title",
                        "caption": "Short caption for the post with hashtags",
                        "slides": [
                            {
                                "slideNumber": 1,
                                "heading": "Slide heading",
                                "text": "Slide subtext or paragraph",
                                "keyPoints": ["list", "of", "key points"],
                                "recommendedProducts": ["Product Name 1", "Product Name 2"]
                            }
                        ]
                    }
                        
                    NOTE: For recommendedProducts, only list actual inventory items, and only do this on the last slide if relevant.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(responseMimeType = "application/json")
                )
                val response = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                
                var responseText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                responseText = responseText.replace("```json", "").replace("```", "").trim()
                
                val carouselResponse = carouselAdapter.fromJson(responseText)
                
                if (carouselResponse != null) {
                    val slidesJson = slidesAdapter.toJson(carouselResponse.slides)
                    val newCarousel = AICarousel(
                        topicTitle = carouselResponse.topicTitle,
                        caption = carouselResponse.caption,
                        slidesJson = slidesJson,
                        visualTheme = theme
                    )
                    val insertedId = repository.insertAICarousel(newCarousel).toInt()
                    _uiState.value = ContentEngineState.Success(carouselResponse, theme, insertedId)
                } else {
                    _uiState.value = ContentEngineState.Error("Failed to parse AI response.")
                }
            } catch (e: Exception) {
                val is429 = e.message?.contains("429") == true || (e is retrofit2.HttpException && e.code() == 429)
                if (is429) {
                    _uiState.value = ContentEngineState.Error("GEMINI_429_RATE_LIMIT")
                } else {
                    _uiState.value = ContentEngineState.Error(e.message ?: "An unknown error occurred.")
                }
            }
        }
    }

    fun startManualCreation(theme: String) {
        _uiState.value = ContentEngineState.ManualCreation(
            topicTitle = "",
            caption = "",
            slides = listOf(
                CarouselSlide(
                    slideNumber = 1,
                    heading = "",
                    text = "",
                    keyPoints = emptyList(),
                    recommendedProducts = emptyList()
                )
            ),
            theme = theme
        )
    }

    fun updateManualCreationFields(
        topicTitle: String,
        caption: String,
        slides: List<CarouselSlide>
    ) {
        val current = _uiState.value
        if (current is ContentEngineState.ManualCreation) {
            _uiState.value = current.copy(
                topicTitle = topicTitle,
                caption = caption,
                slides = slides
            )
        }
    }

    fun saveManualCarousel() {
        val current = _uiState.value
        if (current is ContentEngineState.ManualCreation) {
            viewModelScope.launch {
                try {
                    val slidesJson = slidesAdapter.toJson(current.slides)
                    val newCarousel = AICarousel(
                        topicTitle = current.topicTitle.ifBlank { "Untitled Topic" },
                        caption = current.caption,
                        slidesJson = slidesJson,
                        visualTheme = current.theme
                    )
                    val insertedId = repository.insertAICarousel(newCarousel).toInt()
                    _uiState.value = ContentEngineState.Success(
                        CarouselResponse(
                            topicTitle = newCarousel.topicTitle,
                            caption = newCarousel.caption,
                            slides = current.slides
                        ),
                        current.theme,
                        insertedId
                    )
                } catch (e: Exception) {
                    _uiState.value = ContentEngineState.Error("Failed to save custom carousel: ${e.message}")
                }
            }
        }
    }
}

sealed class ContentEngineState {
    object Idle : ContentEngineState()
    object SelectStrategy : ContentEngineState()
    data class SelectSpecificProducts(val inventory: List<com.example.data.InventoryItem>, val selectedIds: Set<Int>) : ContentEngineState()
    data class Generating(val message: String) : ContentEngineState()
    data class Success(val carousel: CarouselResponse, val theme: String, val carouselId: Int? = null) : ContentEngineState()
    data class Error(val message: String) : ContentEngineState()
    data class ManualCreation(
        val topicTitle: String = "",
        val caption: String = "",
        val slides: List<CarouselSlide> = emptyList(),
        val theme: String = "Minimalist"
    ) : ContentEngineState()
}
