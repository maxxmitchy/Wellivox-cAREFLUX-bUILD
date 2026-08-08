package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

object CarefluxAIEngine {
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val aiResponseAdapter = moshi.adapter(AIResponse::class.java)

    private var lastRequestTime = 0L
    private var lastCachedInsights: AIResponse? = null

    suspend fun generateInsights(
        apiKey: String,
        inventory: List<InventoryItem>,
        customers: List<Customer>,
        medications: List<CustomerMedication>,
        prescriptions: List<DailyPrescriptionVolume>,
        forceRefresh: Boolean = false
    ): AIResponse? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && lastCachedInsights != null && (now - lastRequestTime) < 120_000) {
            Log.d("CarefluxAIEngine", "Returning cached insights (throttled).")
            return@withContext lastCachedInsights
        }

        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
            Log.e("CarefluxAIEngine", "Gemini API Key is missing.")
            val fallback = getFallbackHeuristicResponse(inventory, customers)
            lastCachedInsights = fallback
            lastRequestTime = now
            return@withContext fallback
        }

        // We serialize small summaries so we don't blow up context sizes.
        val stateSummary = """
        [Current Pharmacy State]
        Total Inventory Items: ${inventory.size}
        Low Stock items: ${inventory.count { it.isLowStock }}
        Customers Count: ${customers.size}
        Active Customer Meds: ${medications.size}
        
        Details (USE ONLY THIS DATA, DO NOT MAKE UP ANY OTHER ITEMS OR CUSTOMERS):
        Inventory: ${inventory.map { "${it.name} (Qty: ${it.stockQuantity})" }}
        Customers: ${customers.map { "${it.name} - ${it.notes}" }}
        Customer Prescriptions: ${medications.mapNotNull { med -> 
            val customerName = customers.find { it.id == med.customerId }?.name
            if (customerName != null) {
                "$customerName takes ${med.medicationName} (Next refill: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(med.nextRefillDate))})"
            } else null
        }.joinToString("\n")}
        """.trimIndent()

        val isMorningWindow = com.example.util.RefillNotificationSchedule.isMorningRefillWindow()
        val isEveningWindow = com.example.util.RefillNotificationSchedule.isEveningRefillWindow()
        val isAfternoonRestockWindow = com.example.util.RefillNotificationSchedule.isAfternoonRestockWindow()
        val priorityNotice = when {
            isMorningWindow -> "\n4. REFILL WINDOW PRIORITY: Current time is in the MORNING PEAK REFILL WINDOW (9:00 AM - 11:00 AM). You MUST prioritize prescription/medication refill reminders and chronic patient refill follow-ups at the TOP of highPriorityTasks list."
            isEveningWindow -> "\n4. REFILL WINDOW PRIORITY: Current time is in the EVENING PEAK REFILL WINDOW (8:00 PM - 11:00 PM). You MUST prioritize prescription/medication refill reminders and evening refill follow-ups at the TOP of highPriorityTasks list."
            isAfternoonRestockWindow -> "\n4. RESTOCK CUTOFF PRIORITY: Current time is in the AFTERNOON RESTOCK CUTOFF WINDOW (3:00 PM - 6:00 PM). You MUST prioritize low-stock items and procurement order cutoffs at the TOP of highPriorityTasks list."
            else -> ""
        }

        val promptText = """
        You are the AI operations assistant for Careflux, a Nigerian pharmacy and patient engagement platform.
        Your role is to intelligently monitor pharmacy operations daily and automatically generate actionable tasks, reminders, alerts, operational recommendations, patient engagement prompts, and performance goals.
        
        STRICT RULES:
        1. DO NOT HALLUCINATE OR MAKE UP NAMES: You must ONLY use the exact customer names provided in the Customer List and Customer Prescriptions list. Do not invent any new patients.
        2. DO NOT HALLUCINATE MEDICATIONS: You must ONLY reference medications that exist in the Inventory or Customer Prescriptions list. Do not suggest following up on random medications like "Lisinopril" unless a specific customer in the list is taking it.
        3. DO NOT INVENT ARBITRARY SCENARIOS: Do not invent scenarios like "power fluctuations", "system outtages", or "fridge broken". Base all tasks STRICTLY on the actual numbers and states provided in the input. If stock is low, mention low stock. If refill is soon, mention refill.$priorityNotice
        
        $stateSummary
        
        Analyze the provided real pharmacy data summaries.
        Generate tasks prioritizing: Patient safety, Medication availability, Chronic patient follow-up, Inventory accuracy.
        
        Return the result strictly as a raw JSON payload matching this Kotlin schema structure:
        {
          "highPriorityTasks": [ {"title": "string", "description": "string", "urgency": "High|Medium"} ],
          "inventoryAlerts": [ {"title": "string", "description": "string", "urgency": "string"} ],
          "patientFollowUps": [ {"title": "string", "description": "string", "urgency": "string"} ],
          "businessOpportunities": [ {"recommendation": "string", "potentialImpact": "string"} ],
          "riskAlerts": [ {"title": "string", "severity": "High|Medium|Low", "details": "string"} ],
          "staffPerformanceGoals": [ {"goal": "string", "target": "string"} ],
          "suggestedWhatsAppMessages": [ {"to": "Customer Name or Title", "message": "string"} ]
        }
        
        Ensure output is valid JSON without markdown wrapping (no ```json ... ``` prefixes).
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = promptText)))
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json"
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            Log.d("CarefluxAIEngine", "Gemini Output: ${"$"}{jsonText}")
            
            // In case model wraps in markdown
            val cleanJson = jsonText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsedResult = aiResponseAdapter.fromJson(cleanJson)
            if (parsedResult != null) {
                lastCachedInsights = parsedResult
                lastRequestTime = now
            }
            return@withContext parsedResult ?: getFallbackHeuristicResponse(inventory, customers).also {
                lastCachedInsights = it
                lastRequestTime = now
            }
        } catch (e: Exception) {
            Log.e("CarefluxAIEngine", "Error calling Gemini", e)
            val fallback = getFallbackHeuristicResponse(inventory, customers)
            lastCachedInsights = fallback
            lastRequestTime = now
            return@withContext fallback
        }
    }

    private fun getFallbackHeuristicResponse(inventory: List<InventoryItem>, customers: List<Customer>): AIResponse {
        val lowStock = inventory.filter { it.isLowStock }
        val alerts = lowStock.map { AITask(it.name + " is running low", "Qty: ${it.stockQuantity}", "High") }
        
        return AIResponse(
            highPriorityTasks = listOf(AITask("Review Restocks", "There are ${lowStock.size} low stock items.", "High")),
            inventoryAlerts = alerts.ifEmpty { listOf(AITask("Stock levels OK", "All main items have adequate inventory.", "Low")) },
            patientFollowUps = customers.take(3).map { AITask("Follow up with ${it.name}", "Check up on recent refill.", "Medium") },
            businessOpportunities = listOf(Opportunity("Promote BP Monitor", "Increase sales by offering BP checks.")),
            riskAlerts = emptyList(),
            staffPerformanceGoals = listOf(Goal("Complete 10 follow-ups", "Target: 10")),
            suggestedWhatsAppMessages = listOf(WhatsAppMessage("All Patients", "Hello from Careflux! Please remember to check your meds."))
        )
    }

    suspend fun generateExpiryStrategy(
        apiKey: String,
        item: InventoryItem
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
            return@withContext getFallbackExpiryStrategy(item)
        }

        val prompt = """
            You are an expert clinical pharmacist, professional training coordinator, and senior pharmacy commercial director.
            We have a medication line in our premium Nigerian pharmacy "Careflux Pharmacy" that is near expiry.
            We need an authoritative, 100% verified, and medically accurate "PHARMACIST INTELLIGENCE BRIEF" to educate our staff fully about this product, helping them sell/deplete this stock first (FEFO - First-Expiry, First-Out) ethically and confidently before it expires.
            
            Medication details:
            - Name: ${item.name} (${item.brand.ifEmpty { "Generic" }})
            - Category: ${item.category}
            - Dosage: ${item.dosage}
            - Form: ${item.unitForm.ifEmpty { "Tablet/Syrup/Capsule" }}
            - Current Stock Qty: ${item.stockQuantity} units
            - Price: ₦${"%,.2f".format(item.price)}

            CRITICAL GOODRX-GRADE CLINICAL FACTUALITY & RELEVANCE MANDATE:
            1. NO GENERIC PLACEHOLDERS OR GUESSES: You must act as a peer-reviewed, authoritative clinical drug database equivalent to GoodRx, Medscape, and the British National Formulary (BNF). Do NOT write lazy cop-outs such as "take as prescribed by your doctor" or "consult a physician". You MUST provide the actual, specific clinical guidelines, precise numerical dosing intervals, and exact duration limits.
            2. SPECIFIC CHEMICAL TAILORING: You must dynamically research and identify the actual, true active pharmaceutical ingredients (APIs) of "${item.name}". 
               - If it is Quinine Sulphate (such as Malacide), talk strictly about Quinine Sulphate, its cinchonism side effect profile, G6PD precautions, blood schizonticide kinetics, and a 7-day course. Do NOT talk about Artemether/Lumefantrine or Coartem/Lonart!
               - If it is Artemether/Lumefantrine (such as Coartem, Lonart, or Amatem), talk strictly about Artemether/Lumefantrine, their synergistic kinetics, taking with a fatty meal, and standard 3-day (6-dose) regimens.
               - If it is Amoxicillin/Clavulanate (such as Augmentin), write strictly about Amoxicillin/Clavulanate, beta-lactamase resistance, and 5-to-10-day courses.
               - If it is Amino Pep Forte, write in detail about L-amino acids, peptone, and clinical iron/vitamin content.
               - Tailor every single word to the exact pharmacological profile, active ingredients, and mechanisms of this specific drug. Never generalize or substitute one antimalarial or antibiotic for another!
            3. REAL CLINICAL EDUCATION: Give the exact physiological mechanism of action (e.g., how the drug is absorbed, how it binds to receptors or cell walls, metabolic clearance).
            4. ETHICAL ADHERENCE: Prioritize patient safety, food interactions, critical contraindications, and genuine clinical need.
            
            Generate a highly detailed, education-focused, and highly practical guide following this EXACT markdown structure. Avoid any conversational greeting or trailing text. Proceed directly to the brief:
            
            # PRODUCT SNAPSHOT
            - **Brand Name**: [Specific Brand Name or "${item.brand.ifEmpty { "Generic" }}"]
            - **Generic Ingredients**: [List the exact active chemical ingredients and strengths, e.g. Artemether 20mg / Lumefantrine 120mg, or Amoxicillin 500mg / Clavulanate 125mg]
            - **Pharmacological Class**: [The exact therapeutic/pharmacological class of the API, e.g., Artemisinin-based Combination Therapy, Beta-lactam antibiotic, etc.]
            - **Main Clinical Uses**: [Detailed, specific indications, e.g. acute uncomplicated Plasmodium falciparum malaria, lower respiratory tract infections]
            - **Adult Dosage**: [Provide the exact standard clinical dosage, e.g., "1 tablet twice daily for 5 days" - be precise and numerical]
            - **Pediatric Dosage (if applicable)**: [Provide standard pediatric dosage parameters based on weight or age bands, else specify "Not typical for pediatric use"]
            - **Duration of Use**: [Exact standard therapeutic course, e.g., "5 to 7 days depending on clinical severity"]
            - **Important Counselling Points**: [At least 3 highly specific, practical clinical counselling tips, e.g., fat intake for absorption, complete full course to prevent resistance, check urine color]
            - **Major Contraindications**: [Specific medical conditions, drug-drug combinations, or allergies where use is strictly contraindicated]

            # QUICK PHARMACIST EDUCATION
            Explain clearly in simple yet highly professional pharmacist language:
            - The exact biochemical mechanism of action of the drug inside the body.
            - What physiological health problem it solves.
            - Key clinical benefits of this form or brand.
            - Local prescribing patterns and context in Nigerian clinical practice.

            # IDEAL CUSTOMER PROFILES
            Detail the precise customer types likely to benefit, giving a clinical "WHY" explanation for each:
            - [Profile 1 (e.g., Malaria recovery patient, Elderly patient with weakness, Post-surgery patient, etc.)] - Why: [Clinical context]
            - [Profile 2] - Why: [Clinical context]
            - [Profile 3] - Why: [Clinical context]
            - [Profile 4] - Why: [Clinical context]

            # COUNTER TRIGGERS
            Identify products a customer may already be purchasing that should make a pharmacist think of recommending/offering this near-expiry product. Explain why the near-expiry product becomes clinically relevant to add on:
            - If customer buys [Product/Group A]: Why: [Clinical link]
            - If customer buys [Product/Group B]: Why: [Clinical link]
            - If customer buys [Product/Group C]: Why: [Clinical link]

            # CROSS-SELL MATRIX
            Create a clean Markdown table with exactly 3 columns and at least 10 highly relevant clinical examples:
            | Customer Buying | Why Relevant | Suggested Upsell |
            | :--- | :--- | :--- |
            | [Example 1] | [Example 1 Why] | [Example 1 Upsell] |
            | [Example 2] | [Example 2 Why] | [Example 2 Upsell] |
            ...and so on, up to at least 10 columns. Keep it specific to Nigerian clinical pharmacy and ₦${"%,.2f".format(item.price)} price points.

            # SALES OPPORTUNITY SCORE
            Excellently rate each category out of 10 and write a 1-2 sentence clinical/retail explanation of why:
            - **Walk-in Potential**: [Score]/10 - [Explanation]
            - **Chronic User Potential**: [Score]/10 - [Explanation]
            - **Family Purchase Potential**: [Score]/10 - [Explanation]
            - **Corporate/Institution Potential**: [Score]/10 - [Explanation]

            # READY-TO-USE COUNSELLING SCRIPT
            Write 3 natural, professional, and empathetic conversation snippets (including pharmacist words and patient replies) in Nigerian community-pharmacy conversational style (polite, polished, professional, warm):
            1. **Counter Recommendation**: [Empathetic dialogue suggesting the product based on a presenting complaint]
            2. **Add-on Sale**: [Conversation introducing this product to support an ongoing therapy purchase]
            3. **Chronic Refill Follow-Up**: [Conversation following up on an ongoing replenishment or refill cycle]

            # EXPIRY RECOVERY PLAN
            List practical, actionable retail pharmacy strategies to clear this specific stock (FEFO) before it expires:
            - **WhatsApp Campaigns**: [Specific messaging and targets, e.g. broadcast to wellness groups, elder care contacts]
            - **Existing Patient Lists**: [Targeted database queries, chronic refill targets]
            - **Chronic Refill Reminders**: [Automated or manual adherence follow-ups]
            - **Bundle Opportunities**: [Bundle with fast-moving goods at a minor discount]
            - **Shelf Placement Strategy**: [Eye-level, checkout displays, dispensary prioritization]

            # CLINICAL CAUTIONS
            - Explain situations where recommending this product is clinically inappropriate or risky.
            - Explicitly prioritize patient safety, active clinical consultations, and ethical guidelines over just clearing inventory.

            Make the brief detailed, authentic, highly professional, and culturally tailored for a practicing clinical pharmacist in Nigeria. Use ₦ symbols for pricing where relevant.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            tools = listOf(Tool(googleSearch = GoogleSearch()))
        )

        try {
            // Try standard Pro model first with search grounding for maximum clinical reasoning quality
            val response = RetrofitClient.service.generateContentPro(apiKey, request)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: getFallbackExpiryStrategy(item)
        } catch (e: java.lang.Exception) {
            Log.e("CarefluxAIEngine", "Pro model failed, calling Flash model", e)
            try {
                // Fallback to Flash model if Pro is blocked or rate-limited
                val response = RetrofitClient.service.generateContent(apiKey, request)
                response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: getFallbackExpiryStrategy(item)
            } catch (e2: java.lang.Exception) {
                Log.e("CarefluxAIEngine", "Both Pro and Flash models failed. Using clinical local database fallback.", e2)
                getFallbackExpiryStrategy(item)
            }
        }
    }

    fun getFallbackExpiryStrategy(item: InventoryItem): String {
        val nameLower = (item.name + " " + item.brand).lowercase()
        val priceStr = "₦%,.2f".format(item.price)

        // 1. DAFLON / DIOSMIN / VESSEL SUPPORT
        if (nameLower.contains("daflon") || nameLower.contains("diosmin") || nameLower.contains("hesperidin")) {
            return """
                # PRODUCT SNAPSHOT
                - **Brand Name**: Daflon 500mg
                - **Generic Ingredients**: Micronized Purified Flavonoid Fraction (MPFF) 500mg: Diosmin 450mg (90%), Hesperidin 50mg (10%)
                - **Pharmacological Class**: Phlebotonic / Venotonic and Vascular Protecting Flavonoid
                - **Main Clinical Uses**: Chronic Venous Insufficiency (CVI) services (varicose veins, severe heavy leg feelings, leg pain, night cramps, edema, trophic ulcers) and Treatment of Acute/Chronic Hemorrhoidal Attacks (Piles)
                - **Adult Dosage**: For Varicose Veins/CVI: 2 tablets daily (1 tablet midday and 1 in the evening with food). For Acute Hemorrhoidal Attacks: 6 tablets daily for the first 4 days (3 tablets twice daily), followed by 4 tablets daily for the next 3 days (2 tablets twice daily), then maintenance of 2 tablets daily.
                - **Pediatric Dosage (if applicable)**: Not typical or safety-validated for pediatric use.
                - **Duration of Use**: Chronic CVI treatment typically continues for 2 to 3 months. Acute hemorrhoidal attacks are treated with a rapid 7-day course.
                - **Important Counselling Points**:
                  1. Take tablets with or immediately after meals to minimize any potential mild gastric upset.
                  2. For acute piles, if symptoms do not improve within 15 days, consult a specialist physician/proctologist.
                  3. Accompany therapy with lifestyle measures: avoid prolonged standing/sitting, elevate legs, avoid excess heat/hot baths, and walk regularly.
                - **Major Contraindications**: Known hypersensitivity to diosmin, hesperidin, or its excipients. Breastfeeding is strictly contraindicated (excretion data is lacking).

                # QUICK PHARMACIST EDUCATION
                Daflon is a micronized purified flavonoid fraction (MPFF) phlebotonic. 
                Inside the wall of veins, it prolongs the vasoconstrictor effect of norepinephrine, thereby raising venous tone, reducing venous capacity and distensibility. In microcirculation, it decreases capillary hyperpermeability and capillary resistance to prevent edema.
                It solves circulation problems by treating chronic venous disorders (varicose veins) and severe painful hemorrhoidal plexus swellings.
                Its key clinical benefit is "micronization" (micro-particles under 2 micrometers), which increases GI absorption and clinical efficacy by up to 2x compared to standard flavonoids. Highly prescribed in Nigerian clinical practice.

                # IDEAL CUSTOMER PROFILES
                - **Chronic Varicose Veins Patient** - Why: Older adults complaining of leg swelling, aching veins, Night cramps, or ankle edema. MPFF increases venous tone and prevents fluid retention in lower extremities.
                - **Acute Hemorrhoids / Piles Patient** - Why: Customer requesting rapid relief for painful local anorectal inflammation, itching, or rectal bleeding.
                - **Post-Operative Pelvic Convalescent** - Why: Relieves pelvic and genital circulatory stagnation following complex pelvic surgical interventions.
                - **Sedentary Worker / Desk Professional** - Why: Corporate office workers sitting for 8+ hours a day are at high risk of hemorrhoidal stagnation due to constant gravity pressure.

                # COUNTER TRIGGERS
                Recommend considering Daflon 500mg when customer buys:
                - **Hemorrhoidal ointments (e.g., Anusol, Sheriproct)**: Combines immediate local symptom relief with systemic venous strengthening for complete vascular healing from within.
                - **Stool Softeners & Laxatives (e.g., Dulcolax, Liquid Paraffin)**: Relieves constipation and straining, allowing swollen hemorrhoidal tissue to heal effectively without ongoing mechanical trauma.
                - **Compression Stockings / Socks**: Combined mechanical pressure and active pharmacological venotonic output provides the gold-standard treatment for chronic stasis.

                # CROSS-SELL MATRIX
                | Customer Buying | Why Relevant | Suggested Upsell |
                | :--- | :--- | :--- |
                | Sheriproct / Anusol Ointment | Dual action: Ointment treats local pain/itching, Daflon repairs underlying vascular bleeding. | Daflon 500mg |
                | Dulcolax / Laxatives | Straining during defecation tears hemorrhoidal veins; Daflon heals the veins while laxative softens stool. | Daflon 500mg |
                | Compression Stockings | Stockings give passive external compression; Daflon actively increases venous wall tone from within. | Daflon 500mg |
                | Ibuprofen 400mg / Painkillers | Relieves acute rectal pain while Daflon repairs inflammatory tissue swelling. | Daflon 500mg |
                | Vitamin C 1000mg | Vitamin C strengthens vascular collagen, synergizing with Daflon's anti-permeability effect. | Daflon 500mg |
                | Antimalarials (e.g., Coartem) | Restorative recovery support if a sedentary malaria state provoked temporary lower leg pooling. | Daflon 500mg |
                | Zinc / Wound Healing supplements | Speeds tissue repair in patients suffering from venous stasis ulcers. | Daflon 500mg |
                | Chronic Multivitamins | Support overall general circulatory health for sedentary elder care. | Daflon 500mg |
                | Fiber supplements / Psyllium | Promotes regular stool passing to alleviate ongoing hemorrhoidal pressures. | Daflon 500mg |
                | Warm Salt / Sitz Bath Accessories | Combines local heat hygiene with systemic venous restoration. | Daflon 500mg |

                # SALES OPPORTUNITY SCORE
                - **Walk-in Potential**: 9/10 - Extremely high, as patients often ask for "something for piles" or "something for varicose veins".
                - **Chronic User Potential**: 8/10 - Excellent, as venotonic relief of venous insufficiency requires continuous 2-3 month dosage maintenance.
                - **Family Purchase Potential**: 8/10 - Often purchased by working adults for their elderly parents who suffer from painful lower leg swelling.
                - **Corporate/Institution Potential**: 6/10 - Solid fit for desk-bound corporate teams sitting for 8+ hours a day.

                # READY-TO-USE COUNSELLING SCRIPT
                ### 1. Counter Recommendation
                - **Pharmacist**: "Hello sir! I can get this Sheriproct ointment for your hemorrhoids. Are you using any systemic medication to strengthen the blood vessels as well?"
                - **Patient**: "No, I thought the cream alone was enough."
                - **Pharmacist**: "The cream works on the surface, but Daflon 500mg repairs the internal blood vessels to stop the swelling and prevent future bleeding. Recommending a 5-day acute course for a complete solution."

                ### 2. Add-on Sale
                - **Pharmacist**: "I see you are purchasing compression stockings for your varicose veins. Adding Daflon 500mg ($priceStr) will actively restore your vein elasticity from the inside while the stockings squeeze from the outside."
                - **Patient**: "Ah, that makes perfect sense! Let's add one pack."

                ### 3. Chronic Refill Follow-Up
                - **Pharmacist**: "Hello Ma, it's time for your regular Daflon refill for your chronic venous legs. To support your continuous adherence, we are offering a 10% loyalty discount today!"
                - **Patient**: "Excellent, it has really helped with my night cramps. Prepare two packs."

                # EXPIRY RECOVERY PLAN
                - **WhatsApp Campaigns**: Broadcast specialized circulatory tips targeting elderly patient lists or desk-bound office professionals, introducing Daflon's venotonic properties.
                - **Existing Patient Lists**: Pull databases of customers who previously bought compression socks, hemorrhoid creams, or venous drugs, and reach out to them.
                - **Chronic Refill Reminders**: Proactively alert existing users that their venotonic cycles are due for replenishment.
                - **Bundle Opportunities**: Create a "Severe Piles Relief Bundle" combining Daflon 500mg, Sheriproct/Anusol ointment, and a fiber supplement at a unified discount.
                - **Shelf Placement Strategy**: Position prominently at the cash counter next to counseling pamphlets containing varicose veins or hemorrhoidal warnings.

                # CLINICAL CAUTIONS
                - Never promote for pregnant or lactating mothers without obstetrician prescription endorsement.
                - Always prioritize professional proctologist referral if rectal bleeding persists longer than 15 days despite treatment.
            """.trimIndent()
        }

        // 2. AMINO PEP FORTE
        if (nameLower.contains("amino pep") || nameLower.contains("aminopep") || nameLower.contains("forte")) {
            return """
                # PRODUCT SNAPSHOT
                - **Brand Name**: Amino Pep Forte
                - **Generic Ingredients**: L-Lysine HCl, L-Leucine, L-Phenylalanine, L-Treonine, L-Methionine, L-Isoleucine, L-Valine, Peptone, Iron (Ammonium Citrate), Zinc Sulfate, Nicotinamide, Vitamin B1, B2, B6, B12, Vitamin C
                - **Pharmacological Class**: Essential Amino Acids and Restorative Multivitamin supplement
                - **Main Clinical Uses**: Post-illness recovery (convalescence after malaria, typhoid, surgery), extreme physical weakness, prolonged fatigue, poor appetite, geriatric nutritional decline
                - **Adult Dosage**: 10–15 mL (two to three teaspoonfuls) two times daily, taken after meals.
                - **Pediatric Dosage (if applicable)**: Children (6–12 years): 5 mL (one teaspoonful) two times daily, or as directed by a healthcare professional. Not typical for children under 5 years without direct pediatric instruction.
                - **Duration of Use**: Typically taken for 2 to 4 weeks to restore active nutrient reserves.
                - **Important Counselling Points**:
                  1. Always take after meals to optimize iron/mineral absorption and minimize any minor stomach irritability.
                  2. Shake the bottle excellently well before usage to ensure even distribution of constituents.
                  3. Expected black stools might occur during therapy due to the therapeutical iron content; this is completely harmless.
                - **Major Contraindications**: Known hypersensitivity to any amino acid components, systemic iron storage disorders, hemochromatosis.

                # QUICK PHARMACIST EDUCATION
                Amino Pep Forte is a high-grade regenerative supplement containing free-form crystalline amino acids, vitamins, and minerals.
                Free-form amino acids do not require stomach energy for enzymatic digestion; they are rapidly absorbed directly in the duodenum to catalyze rapid tissue protein synthesis, build hemoglobin, and replenish depleted enzymatic cofactors.
                It solves post-malaria or post-surgical metabolic deficits and weak state complaints.
                Key benefits include its comprehensive B-Complex synergy to unlock metabolic energy (ATP) paired with highly bioavailable iron to treat micro-anemia. It is highly valued in Nigerian community practice for restoring patients complaining of body weakness.

                # IDEAL CUSTOMER PROFILES
                - **Malaria/Typhoid Recovery Patient** - Why: Recently treated for infection but still experiences exhaustion, low appetite, and body pain. Replenishes amino storage.
                - **Post-Surgical Convalescent** - Why: Rebuilding skin tissue, fascia, and muscular wounds requires a rich pool of essential amino acids like L-Lysine and zinc.
                - **Geriatric Patient with Low Appetite** - Why: Frail elderly complaining of low nutritional intake and loss of motor strength.
                - **Exhausted Corporate Worker / Student** - Why: High brain/physical stress drains active neurotransmitters; L-Phenylalanine and B-vitamins restore neurological baselines.

                # COUNTER TRIGGERS
                Recommend considering Amino Pep Forte when customer buys:
                - **Antimalarials (e.g., Coartem, Lonart, Amatem)**: Restores strength and appetite during the acute exhaustion recovery phase.
                - **Broad-spectrum Antibiotics (e.g., Augmentin, Cipro, Zithromax)**: Repairs the bodily metabolic depletion caused by fighting persistent systemic bacterial infections.
                - **Orally Rehydrating Fluids / Hydration Supplements**: Standard dehydration therapy indicates severe mineral depletion; supplements optimize metabolic recovery.

                # CROSS-SELL MATRIX
                | Customer Buying | Why Relevant | Suggested Upsell |
                | :--- | :--- | :--- |
                | Coartem (Malaria treatment) | Antimalarial treats the parasite; Amino Pep Forte treats the post-malaria weakness and restores appetite. | Amino Pep Forte |
                | Augmentin (Antibiotics) | Chronic infections cause systemic protein catabolism; amino acids speed tissue protein healing. | Amino Pep Forte |
                | Lonart (Malaria therapy) | Rebuilds red blood cells and supplies vital iron lost during malaria red cell hemolysis. | Amino Pep Forte |
                | Vitamin C 1000mg | Vitamin C acts as an antioxidant, while Amino Pep supplies amino building blocks for cellular renewal. | Amino Pep Forte |
                | ORS (Oral Rehydration) | Restores muscle vitality and protein building after wasting diarrheal illness. | Amino Pep Forte |
                | Neurobion (Nerve vitamins) | Combines localized nerve repair with global metabolic and physical strength recovery. | Amino Pep Forte |
                | Blood Tonics / Syrups | Enhances hematopoiesis (blood production) by combining amino acids with blood-building cofactors. | Amino Pep Forte |
                | Cold & Flu caplets | Supports immediate immune response and shortens recovery timeframe of cold/flu exhaustion. | Amino Pep Forte |
                | Calcium / Ostamin tablets | Nourishes baseline bone and muscle synthesis, highly important in older patients. | Amino Pep Forte |
                | Sleep support herbal capsules | Regulates rest while Amino Pep rebuilds neurotransmitters (serotonin from Phenylalanine) during sleep. | Amino Pep Forte |

                # SALES OPPORTUNITY SCORE
                - **Walk-in Potential**: 9/10 - Very high, as patients treat infections daily and complain of lingering weakness.
                - **Chronic User Potential**: 6/10 - Decent, particularly among elderly clients and bodybuilders requiring constant protein building blocks.
                - **Family Purchase Potential**: 8/10 - Very common for family heads to buy recovery supplements for children or grandparents who just completed antibiotic/antimalarial therapies.
                - **Corporate/Institution Potential**: 5/10 - Moderate for corporate medical box refills.

                # READY-TO-USE COUNSELLING SCRIPT
                ### 1. Counter Recommendation
                - **Pharmacist**: "Welcome to Careflux, sir. I see you look a bit tired. How are you feeling today?"
                - **Patient**: "I just finished my malaria dose yesterday, but my body is still broken and I can't eat."
                - **Pharmacist**: "The malaria drugs killed the parasites, but your body lost vital proteins and iron during the fight. I suggest Amino Pep Forte; it holds 8 essential free amino acids that go straight into your blood to restore your strength and bring back your appetite."

                ### 2. Add-on Sale
                - **Pharmacist**: "Here is your antibiotic syrup for the child. Many children lose weight and refuse to eat after a fever. Adding Amino Pep Forte ($priceStr) will help repair their system and stimulate a healthy appetite."
                - **Patient**: "That's very true, let's add it to make sure they recover fast."

                ### 3. Chronic Refill Follow-Up
                - **Pharmacist**: "Hello Chief! I see you are purchasing your monthly diabetes medications. To support your general stamina, keeping Amino Pep Forte on hand helps prevent muscle fatigue associated with chronic wellness regimes."
                - **Patient**: "Ah, yes, my legs often feel heavy. I'll take a bottle to try it."

                # EXPIRY RECOVERY PLAN
                - **WhatsApp Campaigns**: Broadcast restorative recovery advice to database contacts who treated malaria or severe bugs in the last 14 days, showcasing Amino Pep's recovery properties.
                - **Existing Patient Lists**: Target patients who recently completed surgical, acute infection, or malaria regimens.
                - **Chronic Refill Reminders**: Suggest to geriatric customers that their restorative tone syrup is due for active replenishment.
                - **Bundle Opportunities**: Create a "Post-Malaria Recover Kit" containing the ACT drug, Vitamin C, and Amino Pep Forte.
                - **Shelf Placement Strategy**: Eye-level, immediately behind the dispensing counter to easily trigger active physical/stamina counselling.

                # CLINICAL CAUTIONS
                - Never administer in massive quantities to patients with chronic liver failure or advanced renal disease.
                - Always prioritize professional consultation for pregnant women to check baseline iron volumes beforehand.
            """.trimIndent()
        }

        // 2b. MALACIDE / QUININE SULPHATE
        if (nameLower.contains("malacide") || nameLower.contains("quinine") || nameLower.contains("sulphate") || nameLower.contains("sulfate")) {
            return """
                # PRODUCT SNAPSHOT
                - **Brand Name**: Malacide (Quinine Sulphate) 300mg
                - **Generic Ingredients**: Quinine Sulphate 300mg
                - **Pharmacological Class**: Cinchona Alkaloid / Antimalarial (Blood Schizonticide)
                - **Main Clinical Uses**: Treatment of chloroquine-resistant Plasmodium falciparum malaria and nocturnal muscle cramps (restricted use).
                - **Adult Dosage**: 600mg (2 tablets of 300mg) every 8 hours for 7 days (usually combined with doxycycline or clindamycin for malaria).
                - **Pediatric Dosage (if applicable)**: 10mg/kg of body weight salt every 8 hours for 7 days under pediatric medical supervision.
                - **Duration of Use**: Exactly 7 days.
                - **Important Counselling Points**:
                  1. Always take with meals or a full glass of milk to reduce gastrointestinal irritation.
                  2. Ringing in the ears (tinnitus), headache, or mild vision impairment (cinchonism) are possible side effects; report severe symptoms immediately.
                  3. Complete the entire 7-day course even if you feel better after a few days to prevent malaria relapse.
                - **Major Contraindications**: Myasthenia gravis, optic neuritis, history of Blackwater fever, G6PD deficiency, clinically prolonged QT interval.

                # QUICK PHARMACIST EDUCATION
                Quinine is a cinchona alkaloid that acts as a fast-acting blood schizonticide.
                In the malaria parasite’s food vacuole, it binds to DNA and inhibits hemozoin crystallization. This leads to an intracellular toxic accumulation of free heme inside the parasitized erythrocyte, killing the schizont.
                It solves acute, chloroquine-resistant Plasmodium falciparum respiratory and hematological malaria infections when fast-acting first-line ACTs are unavailable or contraindicated.
                In local Nigerian clinical pharmacy practice, it is traditionally preferred in complicated malarial cases or persistent fevers resistant to modern artemisinins.

                # IDEAL CUSTOMER PROFILES
                - **Resistant/Recurrent Malaria Patient** - Why: Has completed artemisinin therapies but continues to experience parasite relapse. Quinine targets parasites through a completely non-artemisinin pathway.
                - **Nocturnal Leg Cramps Sufferer** - Why: Safe, low-dose short courses help relax skeletal muscle parameters under strict physician orders.
                - **Malaria Patient allergic to Artemisinins** - Why: Excellent alternative antimalarial line for those experiencing severe hypersensitivity to ACTs.
                - **High Parasitemia Post-Assessment Patient** - Why: Fast blood clearing action makes it ideal for managing high parasite counts under clinical cover.

                # COUNTER TRIGGERS
                Recommend considering Malacide (Quinine Sulphate) 300mg when customer buys:
                - **Doxycycline 100mg or Clindamycin**: Synergistic antibiotics commonly combined with Quinine to treat resistant malaria strains.
                - **Paracetamol / Analgesics**: Over-the-counter fever symptom relief signals an active infection requiring Quinine's root-parasite eradication.
                - **Sitz Bath accessories / Compression stockings**: Lower limb cramping complaints may map to nocturnal cramps suitable for Quinine relief.

                # CROSS-SELL MATRIX
                | Customer Buying | Why Relevant | Suggested Upsell |
                | :--- | :--- | :--- |
                | Doxycycline 100mg | Gold-standard synergy: Combining Quinine with Doxycycline elevates treatment cure rate for resistant malaria to virtually 100%. | Malacide 300mg |
                | Paracetamol (Panadol) | Masks malaria fever symptoms while Quinine attacks and kills the erythrocytic schizonts. | Malacide 300mg |
                | Vitamin C 1000mg | High-dose antioxidant supports cellular recovery and helps reduce drug-induced oxidative stress. | Malacide 300mg |
                | Probiotics (Lactobacillus) | Restores healthy gut flora following high-dose cinchona alkaloid-induced GI stress. | Malacide 300mg |
                | Blood Tonics / Hematinics | Promotes erythropoiesis to restore red blood cells destroyed by deep malarial hemolysis. | Malacide 300mg |
                | Antiemetic (e.g., Vomilast) | Prevents vomiting, ensuring complete systemic absorption of the critical 7-day course. | Malacide 300mg |
                | Multivitamins (B-Complex) | Restores metabolic energy pathways depleted during prolonged malarial physical wasting. | Malacide 300mg |
                | Coartem (with treatment failure) | If artemisinins failed to clear malaria, Quinine serves as the crucial secondary non-resistant rescue agent. | Malacide 300mg |
                | Oral rehydration salts | Prevents dehydration associated with high-temperature malarial sweating and fever spikes. | Malacide 300mg |
                | Throat lozenges / Cough syrups | Restores localized symptom defense lines during general respiratory recovery periods. | Malacide 300mg |

                # SALES OPPORTUNITY SCORE
                - **Walk-in Potential**: 7/10 - Solid, though increasingly viewed as a targeted secondary antimalarial line rather than everyday first-line.
                - **Chronic User Potential**: 2/10 - Extremely low, restricted strictly to short acute 7-day courses.
                - **Family Purchase Potential**: 8/10 - Often purchased by family heads keeping rescue therapies for resistant malaria spikes.
                - **Corporate/Institution Potential**: 6/10 - Regularly included in professional corporate first aid supplies in high-end tropical work setups.

                # READY-TO-USE COUNSELLING SCRIPT
                ### 1. Counter Recommendation
                - **Pharmacist**: "Welcome back, sir. I understand you treated your malaria with standard Coartem last week, but you are still shivering with a high fever today?"
                - **Patient**: "Yes! The fever keeps spiking. I don't know what to do."
                - **Pharmacist**: "This might be a resistant malarial strain. I suggest Malacide (Quinine Sulphate) 300mg for 7 days (${priceStr}). It uses a completely different chemical pathway to kill the resistant malaria parasites in your blood."

                ### 2. Add-on Sale
                - **Pharmacist**: "Here is your Malacide, sir. When taking Quinine, you must finish the entire 7-day course even if you feel fully well on day 3. Also, are you taking this with Doxycycline? Combining them guarantees the parasites are completely cleared."
                - **Patient**: "Ah, let's add Doxycycline to make sure this fever is gone for good!"

                ### 3. Chronic Refill Follow-Up
                - **Pharmacist**: "Hello Chief! While checking your regular prescriptions, since your farmhands work outdoors in high mosquito zones, keeping a pack or two of Malacide (${priceStr}) ensures they have access to immediate rescue therapy if standard antimalarials fail."
                - **Patient**: "You are very right, farm mosquitos are persistent. Add two boxes of Malacide."

                # EXPIRY RECOVERY PLAN
                - **WhatsApp Campaigns**: Distribute educational info highlighting the medical reality of artemisinin resistance and the correct place of Quinine as a rescue therapy.
                - **Existing Patient Lists**: Directly contact clinics or diagnostic center operators about placing Quinine in their emergency kits.
                - **Chronic Refill Reminders**: Suggest updating high-risk workplace health caches with fresh FEFO rescue antimalarials.
                - **Bundle Opportunities**: Connect Malacide (Quinine) with Vitamin C and Paracetamol in a "Resistant Malaria Rescue Pack."
                - **Shelf Placement Strategy**: Place next to malaria diagnostic check strips to emphasize clinical precision.

                # CLINICAL CAUTIONS
                - Strictly screen for G6PD deficiency and prior cardiotoxicity or QT prolonged intervals.
                - Never administer Quinine concurrently with other QT-prolonging agents (like halofantrine).
            """.trimIndent()
        }

        // 3. COARTEM / LONART / ANTIMALARIALS
        if (nameLower.contains("coartem") || nameLower.contains("lonart") || nameLower.contains("artemether") || nameLower.contains("lumefantrine") || nameLower.contains("amatem")) {
            return """
                # PRODUCT SNAPSHOT
                - **Brand Name**: Coartem (or Lonart)
                - **Generic Ingredients**: Artemether 20mg, Lumefantrine 120mg
                - **Pharmacological Class**: Artemisinin-based Combination Therapy (ACT) Antimalarial
                - **Main Clinical Uses**: Treatment of acute, uncomplicated malaria infections caused by Plasmodium falciparum
                - **Adult Dosage**: Standard 3-day course (6 doses total): 
                  - Day 1: Take 4 tablets at diagnosis, then another 4 tablets exactly 8 hours later.
                  - Day 2: Take 4 tablets in the morning and 4 tablets in the evening (12 hours apart).
                  - Day 3: Take 4 tablets in the morning and 4 tablets in the evening (12 hours apart).
                  - Total = 24 tablets.
                - **Pediatric Dosage (if applicable)**: Based strictly on weight bands:
                  - 5 to 15 kg: 1 tablet per dose (6 doses total).
                  - 15 to 25 kg: 2 tablets per dose (6 doses total).
                  - 25 to 35 kg: 3 tablets per dose (6 doses total).
                  - >35 kg: Full adult dose of 4 tablets per dose.
                - **Duration of Use**: Exactly 3 days (6 doses total).
                - **Important Counselling Points**:
                  1. You MUST take each dose with a fatty meal, milk, or fatty food (this increases therapeutic Lumefantrine absorption up to 16-fold!).
                  2. You must finish the entire 6-dose course over 3 days, even if fever stops, to prevent malaria relapse and drug resistance.
                  3. If you vomit within 1 hour of taking a dose, you must repeat the full dose.
                - **Major Contraindications**: Known hypersensitivity to artemether or lumefantrine. Clinically severe/complicated malaria (patients who are unconscious, convulsing, or vomiting everything). Clinically prolonged QT interval. First trimester of pregnancy (unless alternative therapies are completely unavailable).

                # QUICK PHARMACIST EDUCATION
                Coartem utilizes a highly synergistic two-pronged artemisinin-based combination (ACT) mechanism.
                Inside the parasite, artemether's peroxide bridge reacts with intraparasitic iron to release toxic free radicals, destroying membrane proteins and killing 95% of active parasites rapidly. Lumefantrine is slow-acting with a long half-life, systematically clearing any remaining parasites to prevent recrudescence.
                It solves acute uncomplicated malarial fevers and parasitic depletion.
                Its key benefit is rapid clearance of fever and parasite load. It is the gold-standard protocol in local Nigerian clinical practice for malaria.

                # IDEAL CUSTOMER PROFILES
                - **Acute Malarial Fever Patient** - Why: Patient presenting with high fever, chills, night sweats, headache, and positive malaria RDT test.
                - **Relapsing Malaria Sufferer** - Why: Patient whose previous single-therapy antimalarial failed due to parasite resistance.
                - **Frequent Outdoors Worker** - Why: High mosquito exposure profiles.
                - **High-Temperature Pediatric Child** - Why: Suitable weight-based pediatric forms cure malaria fast.

                # COUNTER TRIGGERS
                Recommend considering Coartem when customer buys:
                - **Paracetamol / NSAIDs**: Treating symptoms of malaria (fever, headache) mandates executing ACT to kill the root parasite.
                - **Insecticide / Mosquito nets**: Proves active mosquito threats, highlighting a direct therapeutic vulnerability.
                - **Malaria Testing Kits / RDTs**: Customer already seeking objective proof of diagnosis.

                # CROSS-SELL MATRIX
                | Customer Buying | Why Relevant | Suggested Upsell |
                | :--- | :--- | :--- |
                | Paracetamol (Panadol) | Painkillers mask the malaria fever; ACT kills the parasites causing the fever in the first place. | Coartem / Lonart |
                | Post-Malaria restorative tonic | Restores hemoglobin index and body strength drained during acute hemolysis. | Amino Pep Forte |
                | RDT Malaria Test Strip | Confirms diagnostic accuracy before starting standard 3-day ACT therapy. | Coartem / Lonart |
                | Insecticide spray | Clears immediate mosquitoes to prevent reinfection during recovery. | Coartem / Lonart |
                | Immune Booster (Vitamin C) | Speeds up physical recovery of immune barriers. | Coartem / Lonart |
                | Orally rehydrating fluids | Prevents dehydration from malaria-associated high fevers. | Coartem / Lonart |
                | Antiemetics (e.g., Vomilast) | Prevents vomiting to ensure complete absorption of the 6-dose course. | Coartem / Lonart |
                | Chronic Multivitamins | Supports dynamic health during the convalescence stage. | Coartem / Lonart |
                | General wellness capsules | Builds long-term cellular health against recurring malaria fatigue. | Coartem / Lonart |
                | Mosquito repellent cream | Protects skin from immediate insect vector bites. | Coartem / Lonart |

                # SALES OPPORTUNITY SCORE
                - **Walk-in Potential**: 10/10 - Extremely high; malaria is the most frequently presented self-medicated ailment in Nigeria.
                - **Chronic User Potential**: 3/10 - Low, as it is only used for acute infection clearance, not chronic therapy.
                - **Family Purchase Potential**: 9/10 - Parents aggressively maintain stock for their children and spouses.
                - **Corporate/Institution Potential**: 7/10 - Essential replenishment for site first-aid kits in tropical areas.

                # READY-TO-USE COUNSELLING SCRIPT
                ### 1. Counter Recommendation
                - **Pharmacist**: "Hello sir, I see you are buying a lot of Panadol for fever. Are you also experiencing headache, joint pain, or cold chills?"
                - **Patient**: "Yes! I've been shivering all night. I thought it was just stress."
                - **Pharmacist**: "In our tropical region, those symptoms points strongly to malaria. Masking it with Panadol won't clear the parasite. I suggest this premium Coartem (Artemether/Lumefantrine); it is a 3-day course that targets and eradicates the parasites completely."

                ### 2. Add-on Sale
                - **Pharmacist**: "Here is your Coartem. Remember, Lumefantrine is a fat-soluble chemical. To work effectively, you must take these tablets with a glass of milk, egg, or any fatty food."
                - **Patient**: "Oh, I didn't know that. I usually take it with plain water on an empty stomach."
                - **Pharmacist**: "Without fat, your body will only absorb a tiny fraction of the drug, leading to treatment failure. Always take it with food."

                ### 3. Chronic Refill Follow-Up
                - **Pharmacist**: "Hello sir! While packing your monthly diabetes medicines, has anyone in the family had an malaria fever recently? Having Coartem ($priceStr) in your first aid box ensures immediate treatment if a fever breaks out during the night."
                - **Patient**: "That's smart. Give me one adult pack to keep at home just in case."

                # EXPIRY RECOVERY PLAN
                - **WhatsApp Campaigns**: Send malaria awareness posts and RDT testing reminders to community patient groups.
                - **Existing Patient Lists**: Offer quick diagnostics to patients who bought insect repellents or anti-fever drugs recently.
                - **Chronic Refill Reminders**: Suggest maintaining emergency tropical medication packages.
                - **Bundle Opportunities**: Create a "Complete Malaria Relief Kit" joining Coartem, a paracetamol pack, and a restorative tonic at a combined price.
                - **Shelf Placement Strategy**: Display prominently at the eye-level dispensary shelves and cashier counters during rainy seasons.

                # CLINICAL CAUTIONS
                - Never treat severe complicated malaria with oral tablets; refer immediately for intravenous artesunate.
                - Screen carefully for cardiac arrhythmias or patients on QT-prolonging medicines.
            """.trimIndent()
        }

        // 4. AUGMENTIN / AMOXICILLIN-CLAVULANATE
        if (nameLower.contains("augmentin") || nameLower.contains("amoxicillin") || nameLower.contains("clavulanat") || nameLower.contains("clavulanic")) {
            return """
                # PRODUCT SNAPSHOT
                - **Brand Name**: Augmentin 625mg (or 1g)
                - **Generic Ingredients**: Amoxicillin 500mg, Clavulanate Potassium 125mg (for 625mg tablet); Amoxicillin 875mg, Clavulanate Potassium 125mg (for 1g tablet)
                - **Pharmacological Class**: Beta-lactam Penicillin Antibiotic with Beta-lactamase Inhibitor
                - **Main Clinical Uses**: Treatment of bacterial infections of the lower/upper respiratory tract (bronchitis, pneumonia, sinusitis, otitis media), urinary tract infections (UTIs), skin/soft tissue infections, and dental abscesses
                - **Adult Dosage**: 
                  - Mild to Moderate Infections: 625mg tablet orally every 12 hours (twice daily) for 5 to 7 days.
                  - Severe Infections: 1g (1000mg) tablet orally every 12 hours (twice daily) for 7 to 10 days.
                - **Pediatric Dosage (if applicable)**: Based on weight under amoxicillin guidelines (typically 25–45 mg/kg/day in divided doses every 12 hours of suspension form). Not typical for adult tablets.
                - **Duration of Use**: Exactly 5, 7, or 10 days depending on infection severity.
                - **Important Counselling Points**:
                  1. Take immediately at the start of a meal to significantly reduce stomach cramping and diarrhea, and optimize intestinal absorption.
                  2. You MUST complete the full 5 to 10-day prescribed course even if symptoms disappear early; stopping early breeds highly resistant superbugs.
                  3. Space the doses strictly/evenly (e.g., exactly 12 hours apart) to maintain stable bactericidal serum concentrations.
                - **Major Contraindications**: Known history of serious allergic reaction/anaphylaxis to penicillin, cephalosporins, or beta-lactam drugs. History of cholestatic jaundice or hepatic dysfunction during previous amoxicillin/clavulanate therapy.

                # QUICK PHARMACIST EDUCATION
                Augmentin is a bactericidal powerhouse uniting Amoxicillin and Clavulanate.
                Amoxicillin binds to and deactivates penicillin-binding proteins (PBPs) involved in bacterial cell wall synthesis, causing lysis. However, many pathogens produce beta-lactamase enzymes that hydrolyze amoxicillin. Clavulanate is a "suicide inhibitor" that irreversibly binds to beta-lactamase, shielding amoxicillin and restoring its full bactericidal spectrum.
                It solves severe resistant bacterial throat, chest, skin, and pelvic infections.
                Its key benefit is bypassing bacterial penicillinase resistance. It is the leading premium antibiotic in Nigerian clinical practice.

                # IDEAL CUSTOMER PROFILES
                - **Severe Respiratory Infection Patient** - Why: Complaining of green/yellow sputum, chest pain, and severe fever. Excellent lung tissue penetration.
                - **Urinary Tract Infection Patient** - Why: Experiencing painful urination (dysuria), frequent urges, and pelvic weight. Clears renal pathogens.
                - **Severe Dental Abscess Customer** - Why: Swollen dental gums and root canal inflammation. Penicillin excels in oral flora.
                - **Skin & Soft Tissue Injury Patient** - Why: Infected skin burns, diabetic foot ulcers, or post-operative wound protection.

                # COUNTER TRIGGERS
                Recommend considering Augmentin when customer buys:
                - **Cough syrups / Lozenges**: Indicates a deep bronchitis/pulmonary infection that may require antimicrobial clearance.
                - **Cranberry capsules / Urinary Alkalizers**: Highlights an ongoing bladder or renal infection.
                - **Dental pain gels / Ibuprofen 600mg**: Prompts examining internal tooth nerve abscesses requiring antibiotic cover.

                # CROSS-SELL MATRIX
                | Customer Buying | Why Relevant | Suggested Upsell |
                | :--- | :--- | :--- |
                | Cough syrup (Bronchial) | Syrup relieves the cough reflex; Augmentin kills the bacterial pathogens causing lung inflammation. | Augmentin 625mg/1g |
                | Probiotics (Lactobacillus) | Antibiotics disrupt gut flora causing diarrhea; probiotics restore intestinal balance during course. | Augmentin 625mg/1g |
                | Ural / Urinary alkalizer | Alkalizers ease urination pain; Augmentin clears the bacterial UTI colonizing the bladder. | Augmentin 625mg/1g |
                | Strepsils / Throat Lozenges | Throat lozenges soothe soreness; Augmentin eradicates streptococcal throat infection. | Augmentin 625mg/1g |
                | Restorative Post-Infection Tonic | Nutrient repair during antibiotic catabolic recovery. | Amino Pep Forte |
                | Multivitamins (B-Complex) | B-vitamins assist bodily metabolism while antibiotic clears systemic bacterial load. | Augmentin 625mg/1g |
                | Wound Dressings / Gauze | Combines topical wound protection with strong oral systemic prophylactic antibiotic coverage. | Augmentin 625mg/1g |
                | Oral painkiller (Ibuprofen) | Eases severe toothache while antibiotic clears the root dental canal infection. | Augmentin 625mg/1g |
                | Antipyretic (Paracetamol) | Controls high fever while the antibiotic performs bactericidal clearance. | Augmentin 625mg/1g |
                | Antacids (taken separately) | Prevents severe antibiotic-induced heartburn or gastric irritation. | Augmentin 625mg/1g |

                # SALES OPPORTUNITY SCORE
                - **Walk-in Potential**: 8/10 - High, though community regulations mandate verifying a valid medical prescription before dispensing.
                - **Chronic User Potential**: 2/10 - Low; antibiotics are only used for brief acute courses, never chronic treatment.
                - **Family Purchase Potential**: 8/10 - Parents actively buy chest syrup/tablet antibiotics to protect their household.
                - **Corporate/Institution Potential**: 6/10 - Standard inventory item for corporate clinic boxes.

                # READY-TO-USE COUNSELLING SCRIPT
                ### 1. Counter Recommendation
                - **Pharmacist**: "I can see this severe cough syrup is for your husband. Does he have a persistent high fever or green phlegm?"
                - **Patient**: "Yes! He is shivering and coughing deep green colored mucus. The throat sweets aren't helping."
                - **Pharmacist**: "That points strongly to a lower bacterial respiratory infection. The syrup only covers the symptoms. He likely needs this professional Augmentin course to kill the bacteria in his chest."

                ### 2. Add-on Sale
                - **Pharmacist**: "Here is your Augmentin 1g. To prevent severe diarrhea or stomach upset that penicillin can cause, always swallow these tablets at the start of your meals, not on an empty stomach."
                - **Patient**: "Oh thank you, I usually suffer from stomach running when taking antibiotics."
                - **Pharmacist**: "Taking it with the first bite of food will shield your gut and also help your body absorb the drug much better."

                ### 3. Chronic Refill Follow-Up
                - **Pharmacist**: "Hello Ma, as we refill your chronic prescriptions, keeping your home first aid box stocked with a fresh pack of Augmentin ($priceStr) ensures you have rapid access to high-grade therapy the moment an acute infection peaks."
                - **Patient**: "Yes, tooth infections often attack me unexpectedly. Put one pack in the bag."

                # EXPIRY RECOVERY PLAN
                - **WhatsApp Campaigns**: Distribute tips regarding the dangers of antibiotic resistance and proper usage timelines.
                - **Existing Patient Lists**: Consult patients with recurring dental issues or chronic bronchitis records who are due for medical checkups.
                - **Chronic Refill Reminders**: Ensure emergency medicine supplies are updated with valid FEFO batches.
                - **Bundle Opportunities**: Pair Augmentin with high-quality probiotics at a minor loyalty discount.
                - **Shelf Placement Strategy**: Dispensary shelf priority displays to remind pharmacists to recommend this premium brand for prescription orders.

                # CLINICAL CAUTIONS
                - Never dispense without a valid clinician prescription.
                - Strictly screen for prior penicillin anaphylactic reactions; penicillin allergy can be fatal.
            """.trimIndent()
        }

        // 5. BASELINE GENERAL MEDICINE FALLBACK
        return """
            # PRODUCT SNAPSHOT
            - **Brand Name**: ${item.brand.ifEmpty { "Generic Brand" }}
            - **Generic Ingredients**: Active therapeutic components configured for ${item.category} (Dosage: ${item.dosage})
            - **Pharmacological Class**: ${item.category} Specific Agent
            - **Main Clinical Uses**: Symptoms and conditions pertaining as indicated under ${item.category}
            - **Adult Dosage**: ${item.dosage} taken according to precise clinician instructions or standard therapeutic protocols
            - **Pediatric Dosage (if applicable)**: Consult primary care pediatrician or use specific pediatric formulation parameters
            - **Duration of Use**: Typically 5 to 10 days depending on precise clinical presentation
            - **Important Counselling Points**: 
              1. Swallow whole with water; do not crush unless advised.
              2. Complete the full course of treatment even if symptoms improve.
              3. Store in a cool, dry place away from direct sunlight.
            - **Major Contraindications**: Known hypersensitivity to ingredients or related chemical forms.

            # QUICK PHARMACIST EDUCATION
            ${item.name} supplies essential therapeutic support tailored for ${item.category}. 
            Patients pick this up to relieve symptoms associated with their diagnosis. This product addresses physiological distress, solves active distress pathways, and delivers key benefits like enhanced recovery and symptom management. It is usually prescribed during standard clinical presentation or recommended as part of supportive therapy.

            # IDEAL CUSTOMER PROFILES
            - **Post-Acute Recovery Patient** - Why: Rebuilding strength or continuing secondary care after treating primary bacterial or parasitic infection.
            - **Elderly Patient struggling with symptoms** - Why: Requires supportive relief for chronic or age-related complaints in this clinical category.
            - **Corporate Worker with active fatigue/tension** - Why: Seeks high-performance symptom control with minimum sedative side-effects.
            - **Chronic Care Refiller** - Why: Regularly uses medication to sustain physiological baseline state.

            # COUNTER TRIGGERS
            Recommend considering ${item.name} when customer buys:
            - **Antimalarials**: Post-treatment restorative recovery.
            - **Antibiotics**: Supportive clinical therapy to maintain system stability.
            - **Vitamin C & Multivitamins**: General wellness optimization.

            # CROSS-SELL MATRIX
            | Customer Buying | Why Relevant | Suggested Upsell |
            | :--- | :--- | :--- |
            | Antimalarials (e.g., Coartem) | Accelerates total recovery from post-illness fatigue | ${item.name} (${item.dosage}) |
            | Broad-spectrum Antibiotics | Replenishes vital bodily resilience after systemic shock | ${item.name} (${item.dosage}) |
            | Vitamin C (e.g., C-1000) | Amplifies immune benefits with deep cellular repair | ${item.name} (${item.dosage}) |
            | Orally rehydrating fluids | Replaces metabolic blocks and boosts overall absorption | ${item.name} (${item.dosage}) |
            | Cardiovascular support medications | Addresses underlying weakness while managing base disease | ${item.name} (${item.dosage}) |
            | Blood Tonics / Syrups | Combines hematinic benefits with direct category symptom relief | ${item.name} (${item.dosage}) |
            | Muscle Relaxants / Analgesics | Addresses localized pain in conjunction with global recovery | ${item.name} (${item.dosage}) |
            | Immune booster capsules | Synergizes with antioxidant status for fast convalescence | ${item.name} (${item.dosage}) |
            | Essential amino acids | Supplies protein blocks along with active physiological support | ${item.name} (${item.dosage}) |
            | Sleep support supplements | Standardizes rest parameters while drug regulates symptoms | ${item.name} (${item.dosage}) |

            # SALES OPPORTUNITY SCORE
            - **Walk-in Potential**: 8/10 - Extremely high as customers frequently present complaints that map directly back to ${item.category}.
            - **Chronic User Potential**: 6/10 - Good repeat refill potential depending on standard prescription cycles.
            - **Family Purchase Potential**: 7/10 - Highly purchasable for family heads looking after elderly parents or convalescing children.
            - **Corporate/Institution Potential**: 5/10 - Moderate fit for workplace first-aid replenish runs.

            # READY-TO-USE COUNSELLING SCRIPT
            ### 1. Counter Recommendation
            - **Pharmacist**: "Welcome to Careflux, sir. How are you feeling today?"
            - **Patient**: "I'm feeling very weak after treating a bad bout of typhoid."
            - **Pharmacist**: "I understand completely. Rebuilding your tissue and energy is crucial. Recommending ${item.name} (${item.dosage}) to support your nutrient profile and restore your full energy levels."

            ### 2. Add-on Sale
            - **Pharmacist**: "Here is your prescription medication. Since you are completing an intensive course, adding ${item.name} ($priceStr) will supply the critical systemic reinforcement you need to prevent post-therapy fatigue."
            - **Patient**: "Oh, sounds logical. Let's add that to prevent crashing later!"

            ### 3. Chronic Refill Follow-Up
            - **Pharmacist**: "Hello Chief! I see it's time for your refill. We are doing a FEFO health drive; we can process your refill of ${item.name} today at a 10% loyalty discount to support your ongoing cellular health."
            - **Patient**: "That's fantastic. Go ahead and add it to my cart."

            # EXPIRY RECOVERY PLAN
            - **WhatsApp Campaigns**: Broadcast targeted clinical wellness tips to our elderly patient lists, offering ${item.name} as a nutritional/clinical package.
            - **Existing Patient Lists**: Filter active customers who bought similar therapeutic categories in the last 60 days and message them.
            - **Chronic Refill Reminders**: Proactively call patients on chronic schedules to book early refills at a small discount.
            - **Bundle Opportunities**: Create an "Infection Recovery Bundle" combining this item with fast-moving Vitamin C or immune boosters.
            - **Shelf Placement Strategy**: Place near the cash counter or customer consultation desk with highlighted counseling placards.

            # CLINICAL CAUTIONS
            - Always screen carefully for prior drug-allergy profiles.
            - Do not recommend if customer is pregnant, nursing, or has renal/renal-associated hepatic impairment unless strictly authorized by their physician.
            - Always prioritize patient health over clearing inventory.
        """.trimIndent()
    }
}
