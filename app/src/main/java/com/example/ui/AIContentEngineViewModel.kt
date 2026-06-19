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
    val recommendedProducts: List<String>? = null
)

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
    val isOfferBanner: Boolean = true,
    val subheader: String = "TODAY'S SPECIAL OFFERS & PROMOTIONS",
    val promoTheme: PromoThemeStyle = PromoThemeStyle.MIDNIGHT_CYAN,
    val generatedUri: Uri? = null
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
        val current = _promoState.value
        _promoState.value = current.copy(uiMode = mode)
    }

    fun setPromoGeneratedUri(uri: Uri?) {
        val current = _promoState.value
        _promoState.value = current.copy(generatedUri = uri)
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
        _uiState.value = ContentEngineState.Success(response, carousel.visualTheme)
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
                    repository.insertAICarousel(newCarousel)
                    _uiState.value = ContentEngineState.Success(carouselResponse, theme)
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
                    repository.insertAICarousel(newCarousel)
                    _uiState.value = ContentEngineState.Success(
                        CarouselResponse(
                            topicTitle = newCarousel.topicTitle,
                            caption = newCarousel.caption,
                            slides = current.slides
                        ),
                        current.theme
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
    data class Success(val carousel: CarouselResponse, val theme: String) : ContentEngineState()
    data class Error(val message: String) : ContentEngineState()
    data class ManualCreation(
        val topicTitle: String = "",
        val caption: String = "",
        val slides: List<CarouselSlide> = emptyList(),
        val theme: String = "Minimalist"
    ) : ContentEngineState()
}
