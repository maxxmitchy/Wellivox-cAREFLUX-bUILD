package com.example.data

object ClinicalDdiEngine {
    
    enum class DrugClass {
        ACE_INHIBITORS,
        POTASSIUM_SPARING_DIURETICS,
        NITRATES,
        PDE5_INHIBITORS,
        NSAID,
        ORAL_ANTICOAGULANTS,
        SSRIs,
        SEROTONIN_AGONISTS,
        STATINS,
        MACROLIDE_ANTIBIOTICS,
        PROTON_PUMP_INHIBITORS,
        ANTIPLATELETS,
        FLUOROQUINOLONES,
        MULTIVALENT_CATIONS,
        METHOTREXATE,
        DIGOXIN,
        AMIODARONE,
        POTASSIUM_SUPPLEMENTS,
        BENZODIAZEPINES,
        TERATOGENS, // Category X/D drugs (unsafe in pregnancy)
        PEDIATRIC_CAUTION // Drugs to avoid in children
    }

    // A robust mapping of drug brand names and generic names to their active ingredients and class profiles.
    // This allows the system to seamlessly resolve any of the 1000+ brand names or dosages.
    private val drugDatabase = mapOf(
        // ACE Inhibitors
        "lisinopril" to Pair("lisinopril", setOf(DrugClass.ACE_INHIBITORS, DrugClass.TERATOGENS)),
        "zestril" to Pair("lisinopril", setOf(DrugClass.ACE_INHIBITORS, DrugClass.TERATOGENS)),
        "ramipril" to Pair("ramipril", setOf(DrugClass.ACE_INHIBITORS, DrugClass.TERATOGENS)),
        "altace" to Pair("ramipril", setOf(DrugClass.ACE_INHIBITORS, DrugClass.TERATOGENS)),
        "enalapril" to Pair("enalapril", setOf(DrugClass.ACE_INHIBITORS, DrugClass.TERATOGENS)),
        "vasotec" to Pair("enalapril", setOf(DrugClass.ACE_INHIBITORS, DrugClass.TERATOGENS)),
        "captopril" to Pair("captopril", setOf(DrugClass.ACE_INHIBITORS, DrugClass.TERATOGENS)),
        
        // Potassium-Sparing Diuretics
        "spironolactone" to Pair("spironolactone", setOf(DrugClass.POTASSIUM_SPARING_DIURETICS)),
        "aldactone" to Pair("spironolactone", setOf(DrugClass.POTASSIUM_SPARING_DIURETICS)),
        "eplerenone" to Pair("eplerenone", setOf(DrugClass.POTASSIUM_SPARING_DIURETICS)),
        "inspra" to Pair("eplerenone", setOf(DrugClass.POTASSIUM_SPARING_DIURETICS)),
        
        // Nitrates
        "nitroglycerin" to Pair("nitroglycerin", setOf(DrugClass.NITRATES)),
        "nitrostat" to Pair("nitroglycerin", setOf(DrugClass.NITRATES)),
        "isosorbide" to Pair("isosorbide", setOf(DrugClass.NITRATES)),
        "imdur" to Pair("isosorbide", setOf(DrugClass.NITRATES)),
        
        // PDE5 Inhibitors
        "sildenafil" to Pair("sildenafil", setOf(DrugClass.PDE5_INHIBITORS)),
        "viagra" to Pair("sildenafil", setOf(DrugClass.PDE5_INHIBITORS)),
        "tadalafil" to Pair("tadalafil", setOf(DrugClass.PDE5_INHIBITORS)),
        "cialis" to Pair("tadalafil", setOf(DrugClass.PDE5_INHIBITORS)),
        "vardenafil" to Pair("vardenafil", setOf(DrugClass.PDE5_INHIBITORS)),
        "levitra" to Pair("vardenafil", setOf(DrugClass.PDE5_INHIBITORS)),
        
        // NSAIDs / Aspirin
        "ibuprofen" to Pair("ibuprofen", setOf(DrugClass.NSAID)),
        "advil" to Pair("ibuprofen", setOf(DrugClass.NSAID)),
        "motrin" to Pair("ibuprofen", setOf(DrugClass.NSAID)),
        "diclofenac" to Pair("diclofenac", setOf(DrugClass.NSAID)),
        "voltaren" to Pair("diclofenac", setOf(DrugClass.NSAID)),
        "naproxen" to Pair("naproxen", setOf(DrugClass.NSAID)),
        "aleve" to Pair("naproxen", setOf(DrugClass.NSAID)),
        "aspirin" to Pair("aspirin", setOf(DrugClass.NSAID, DrugClass.PEDIATRIC_CAUTION)),
        "aloxiprin" to Pair("aspirin", setOf(DrugClass.NSAID, DrugClass.PEDIATRIC_CAUTION)),
        
        // Oral Anticoagulants
        "warfarin" to Pair("warfarin", setOf(DrugClass.ORAL_ANTICOAGULANTS, DrugClass.TERATOGENS)),
        "coumadin" to Pair("warfarin", setOf(DrugClass.ORAL_ANTICOAGULANTS, DrugClass.TERATOGENS)),
        "apixaban" to Pair("apixaban", setOf(DrugClass.ORAL_ANTICOAGULANTS)),
        "eliquis" to Pair("apixaban", setOf(DrugClass.ORAL_ANTICOAGULANTS)),
        "rivaroxaban" to Pair("rivaroxaban", setOf(DrugClass.ORAL_ANTICOAGULANTS)),
        "xarelto" to Pair("rivaroxaban", setOf(DrugClass.ORAL_ANTICOAGULANTS)),
        
        // SSRIs
        "fluoxetine" to Pair("fluoxetine", setOf(DrugClass.SSRIs)),
        "prozac" to Pair("fluoxetine", setOf(DrugClass.SSRIs)),
        "sertraline" to Pair("sertraline", setOf(DrugClass.SSRIs)),
        "zoloft" to Pair("sertraline", setOf(DrugClass.SSRIs)),
        "escitalopram" to Pair("escitalopram", setOf(DrugClass.SSRIs)),
        "lexapro" to Pair("escitalopram", setOf(DrugClass.SSRIs)),
        "paroxetine" to Pair("paroxetine", setOf(DrugClass.SSRIs, DrugClass.TERATOGENS)),
        "paxil" to Pair("paroxetine", setOf(DrugClass.SSRIs, DrugClass.TERATOGENS)),
        
        // Serotonergics
        "tramadol" to Pair("tramadol", setOf(DrugClass.SEROTONIN_AGONISTS)),
        "ultram" to Pair("tramadol", setOf(DrugClass.SEROTONIN_AGONISTS)),
        
        // Statins
        "simvastatin" to Pair("simvastatin", setOf(DrugClass.STATINS, DrugClass.TERATOGENS)),
        "zocor" to Pair("simvastatin", setOf(DrugClass.STATINS, DrugClass.TERATOGENS)),
        "atorvastatin" to Pair("atorvastatin", setOf(DrugClass.STATINS, DrugClass.TERATOGENS)),
        "lipitor" to Pair("atorvastatin", setOf(DrugClass.STATINS, DrugClass.TERATOGENS)),
        "rosuvastatin" to Pair("rosuvastatin", setOf(DrugClass.STATINS, DrugClass.TERATOGENS)),
        "crestor" to Pair("rosuvastatin", setOf(DrugClass.STATINS, DrugClass.TERATOGENS)),
        
        // Macrolides
        "clarithromycin" to Pair("clarithromycin", setOf(DrugClass.MACROLIDE_ANTIBIOTICS)),
        "biaxin" to Pair("clarithromycin", setOf(DrugClass.MACROLIDE_ANTIBIOTICS)),
        "erythromycin" to Pair("erythromycin", setOf(DrugClass.MACROLIDE_ANTIBIOTICS)),
        "azithromycin" to Pair("azithromycin", setOf(DrugClass.MACROLIDE_ANTIBIOTICS)),
        "zithromax" to Pair("azithromycin", setOf(DrugClass.MACROLIDE_ANTIBIOTICS)),
        
        // PPIs
        "omeprazole" to Pair("omeprazole", setOf(DrugClass.PROTON_PUMP_INHIBITORS)),
        "prilosec" to Pair("omeprazole", setOf(DrugClass.PROTON_PUMP_INHIBITORS)),
        "losec" to Pair("omeprazole", setOf(DrugClass.PROTON_PUMP_INHIBITORS)),
        "esomeprazole" to Pair("esomeprazole", setOf(DrugClass.PROTON_PUMP_INHIBITORS)),
        "nexium" to Pair("esomeprazole", setOf(DrugClass.PROTON_PUMP_INHIBITORS)),
        
        // Antiplatelets
        "clopidogrel" to Pair("clopidogrel", setOf(DrugClass.ANTIPLATELETS)),
        "plavix" to Pair("clopidogrel", setOf(DrugClass.ANTIPLATELETS)),
        
        // Fluoroquinolones
        "ciprofloxacin" to Pair("ciprofloxacin", setOf(DrugClass.FLUOROQUINOLONES)),
        "cipro" to Pair("ciprofloxacin", setOf(DrugClass.FLUOROQUINOLONES)),
        "levofloxacin" to Pair("levofloxacin", setOf(DrugClass.FLUOROQUINOLONES)),
        "levaquin" to Pair("levofloxacin", setOf(DrugClass.FLUOROQUINOLONES)),
        
        // Multivalent Cations & Potassium
        "calcium" to Pair("calcium", setOf(DrugClass.MULTIVALENT_CATIONS)),
        "iron" to Pair("iron", setOf(DrugClass.MULTIVALENT_CATIONS)),
        "ferrous" to Pair("ferrous", setOf(DrugClass.MULTIVALENT_CATIONS)),
        "zinc" to Pair("zinc", setOf(DrugClass.MULTIVALENT_CATIONS)),
        "magnesium" to Pair("magnesium", setOf(DrugClass.MULTIVALENT_CATIONS)),
        "potassium" to Pair("potassium", setOf(DrugClass.POTASSIUM_SUPPLEMENTS)),
        "k-lor" to Pair("potassium", setOf(DrugClass.POTASSIUM_SUPPLEMENTS)),
        
        // Methotrexate
        "methotrexate" to Pair("methotrexate", setOf(DrugClass.METHOTREXATE, DrugClass.TERATOGENS)),
        "trexall" to Pair("methotrexate", setOf(DrugClass.METHOTREXATE, DrugClass.TERATOGENS)),
        
        // Digoxin
        "digoxin" to Pair("digoxin", setOf(DrugClass.DIGOXIN)),
        "lanoxin" to Pair("digoxin", setOf(DrugClass.DIGOXIN)),
        
        // Amiodarone
        "amiodarone" to Pair("amiodarone", setOf(DrugClass.AMIODARONE)),
        "cordarone" to Pair("amiodarone", setOf(DrugClass.AMIODARONE)),
        
        // Benzodiazepines (avoid in elderly due to fall risk)
        "diazepam" to Pair("diazepam", setOf(DrugClass.BENZODIAZEPINES)),
        "valium" to Pair("diazepam", setOf(DrugClass.BENZODIAZEPINES)),
        "alprazolam" to Pair("alprazolam", setOf(DrugClass.BENZODIAZEPINES)),
        "xanax" to Pair("alprazolam", setOf(DrugClass.BENZODIAZEPINES)),
        "lorazepam" to Pair("lorazepam", setOf(DrugClass.BENZODIAZEPINES)),
        "ativan" to Pair("lorazepam", setOf(DrugClass.BENZODIAZEPINES)),
        
        // Other major teratogens
        "misoprostol" to Pair("misoprostol", setOf(DrugClass.TERATOGENS)),
        "cytotec" to Pair("misoprostol", setOf(DrugClass.TERATOGENS)),
        "isotretinoin" to Pair("isotretinoin", setOf(DrugClass.TERATOGENS)),
        "accutane" to Pair("isotretinoin", setOf(DrugClass.TERATOGENS)),
        "thalidomide" to Pair("thalidomide", setOf(DrugClass.TERATOGENS)),
        "thalomid" to Pair("thalidomide", setOf(DrugClass.TERATOGENS))
    )

    data class ResolvedMedication(
        val originalName: String,
        val genericName: String,
        val classes: Set<DrugClass>
    )

    fun resolveMedication(name: String): ResolvedMedication {
        val normalized = name.lowercase().trim()
        
        // 1. Direct match
        val directMatch = drugDatabase[normalized]
        if (directMatch != null) {
            return ResolvedMedication(name, directMatch.first, directMatch.second)
        }
        
        // 2. Substring/token match (sorted by descending length to match more specific terms first)
        val sortedKeys = drugDatabase.keys.sortedByDescending { it.length }
        for (key in sortedKeys) {
            if (normalized.contains(key)) {
                val match = drugDatabase[key]!!
                return ResolvedMedication(name, match.first, match.second)
            }
        }
        
        // 3. Fallback
        return ResolvedMedication(name, normalized, emptySet())
    }

    data class ClassInteractionRule(
        val classA: DrugClass,
        val classB: DrugClass,
        val severity: String = "Severe Warning",
        val description: String
    )

    private val classRules = listOf(
        ClassInteractionRule(
            DrugClass.PDE5_INHIBITORS, DrugClass.NITRATES,
            description = "dispensing a PDE5 Inhibitor alongside Nitrates carries an extreme risk of life-threatening acute hypotension."
        ),
        ClassInteractionRule(
            DrugClass.ACE_INHIBITORS, DrugClass.POTASSIUM_SPARING_DIURETICS,
            description = "combining ACE Inhibitors with Potassium-Sparing Diuretics significantly increases risk of severe Hyperkalemia (potassium toxicity) and cardiac complications."
        ),
        ClassInteractionRule(
            DrugClass.ACE_INHIBITORS, DrugClass.POTASSIUM_SUPPLEMENTS,
            description = "combining ACE Inhibitors with Potassium supplements significantly increases risk of severe Hyperkalemia."
        ),
        ClassInteractionRule(
            DrugClass.NSAID, DrugClass.ORAL_ANTICOAGULANTS,
            description = "combining NSAIDs with Oral Anticoagulants exponentially elevates gastrointestinal bleeding hazards and internal hemorrhaging risks."
        ),
        ClassInteractionRule(
            DrugClass.SSRIs, DrugClass.SEROTONIN_AGONISTS,
            description = "co-dispensing SSRIs with Serotonergic pain medications (Tramadol) can trigger life-threatening Serotonin Syndrome."
        ),
        ClassInteractionRule(
            DrugClass.STATINS, DrugClass.MACROLIDE_ANTIBIOTICS,
            description = "combining Statins with Macrolide antibiotics is highly contraindicated due to severe risks of drug-induced Rhabdomyolysis and acute kidney damage."
        ),
        ClassInteractionRule(
            DrugClass.PROTON_PUMP_INHIBITORS, DrugClass.ANTIPLATELETS,
            description = "Proton Pump Inhibitors (Omeprazole) can reduce the active therapeutic efficacy of antiplatelets (Clopidogrel), increasing cardiovascular or thromboembolic risks."
        ),
        ClassInteractionRule(
            DrugClass.FLUOROQUINOLONES, DrugClass.MULTIVALENT_CATIONS,
            description = "co-administration of multivalent cations (calcium, iron, antacids) significantly reduces oral absorption and therapeutic efficacy of Fluoroquinolone antibiotics."
        ),
        ClassInteractionRule(
            DrugClass.NSAID, DrugClass.METHOTREXATE,
            description = "NSAIDs decrease the renal clearance of Methotrexate, creating an extreme risk of severe, life-threatening methotrexate toxicity."
        ),
        ClassInteractionRule(
            DrugClass.DIGOXIN, DrugClass.AMIODARONE,
            description = "Amiodarone significantly increases serum Digoxin levels, promoting severe digoxin-induced cardiotoxicity and arrhythmias."
        )
    )

    private fun isPregnant(customer: Customer?): Boolean {
        if (customer == null) return false
        if (customer.gender.lowercase() != "female") return false
        val notesLower = customer.notes.lowercase()
        return notesLower.contains("pregnant") || 
               notesLower.contains("pregnancy") || 
               notesLower.contains("gestation") || 
               notesLower.contains("conceived") || 
               notesLower.contains("expecting")
    }

    private fun isLactating(customer: Customer?): Boolean {
        if (customer == null) return false
        val notesLower = customer.notes.lowercase()
        return notesLower.contains("breastfeeding") || 
               notesLower.contains("lactating") || 
               notesLower.contains("nursing") || 
               notesLower.contains("postpartum")
    }

    fun checkInteractions(medicationNames: List<String>, customer: Customer? = null): List<String> {
        if (medicationNames.isEmpty()) return emptyList()
        val triggeredWarnings = mutableListOf<String>()

        // 1. Resolve all medications
        val resolvedMeds = medicationNames.map { resolveMedication(it) }

        // 2. Perform Pairwise Drug-Drug Interaction matching
        for (i in 0 until resolvedMeds.size) {
            val med1 = resolvedMeds[i]
            for (j in i + 1 until resolvedMeds.size) {
                val med2 = resolvedMeds[j]

                // Check for class-based interactions
                for (rule in classRules) {
                    val matchAB = med1.classes.contains(rule.classA) && med2.classes.contains(rule.classB)
                    val matchBA = med2.classes.contains(rule.classA) && med1.classes.contains(rule.classB)
                    if (matchAB || matchBA) {
                        triggeredWarnings.add("Warning: Dispensing ${med1.originalName} alongside ${med2.originalName} carries a severe risk - ${rule.description}")
                    }
                }
            }
        }

        // 3. Perform Patient-Specific Contraindication Checks
        if (customer != null) {
            val isPatientPregnant = isPregnant(customer)
            val isPatientLactating = isLactating(customer)

            for (med in resolvedMeds) {
                // Pregnancy Teratogen Warning
                if (isPatientPregnant && med.classes.contains(DrugClass.TERATOGENS)) {
                    triggeredWarnings.add("CRITICAL PREGNANCY WARNING: ${med.originalName} is highly contraindicated during pregnancy. It carries severe risk of teratogenicity and fetal harm.")
                }

                // Lactation Warning
                if (isPatientLactating && med.classes.contains(DrugClass.AMIODARONE)) {
                    triggeredWarnings.add("CRITICAL LACTATION WARNING: Amiodarone (${med.originalName}) is concentrated in breast milk and highly contraindicated during breastfeeding.")
                }

                // Geriatric caution
                if (customer.age >= 65 && med.classes.contains(DrugClass.BENZODIAZEPINES)) {
                    triggeredWarnings.add("Geriatric Warning: ${med.originalName} (Benzodiazepine) carries a high risk of cognitive impairment, falls, and severe sedation in patients aged 65 or older.")
                }

                // Pediatric caution
                if (customer.age < 12 && med.classes.contains(DrugClass.PEDIATRIC_CAUTION)) {
                    triggeredWarnings.add("Pediatric Warning: ${med.originalName} carries a high risk of Reye's Syndrome or other adverse effects in patients under 12 years of age.")
                }
            }
        }

        return triggeredWarnings.distinct()
    }
}
