package com.example.data

object ClinicalDdiEngine {
    data class InteractionRule(
        val drugA: String, // generic keyword, in lowercase
        val drugB: String, // generic keyword, in lowercase
        val alertSeverity: String = "Severe Warning",
        val messageDescription: String
    )

    private val rules = listOf(
        InteractionRule(
            "sildenafil", "nitroglycerin",
            messageDescription = "Dispensing Sildenafil alongside Nitroglycerin carries a severe hypotensive risk."
        ),
        InteractionRule(
            "sildenafil", "isosorbide",
            messageDescription = "Dispensing Sildenafil alongside Isosorbide carries a severe hypotensive risk."
        ),
        InteractionRule(
            "tadalafil", "nitroglycerin",
            messageDescription = "Dispensing Tadalafil alongside Nitroglycerin carries a severe hypotensive risk."
        ),
        InteractionRule(
            "tadalafil", "isosorbide",
            messageDescription = "Dispensing Tadalafil alongside Isosorbide dinitrate/mononitrate carries a severe hypotensive risk."
        ),
        InteractionRule(
            "warfarin", "aspirin",
            messageDescription = "Combining Warfarin with Aspirin significantly increases the risk of serious gastrointestinal or internal bleeding."
        ),
        InteractionRule(
            "warfarin", "ibuprofen",
            messageDescription = "Combining Warfarin with NSAIDs like Ibuprofen significantly increases the risk of serious bleeding hazards."
        ),
        InteractionRule(
            "lisinopril", "spironolactone",
            messageDescription = "Combining ACE inhibitors like Lisinopril with potassium-sparing diuretics (Spironolactone) leads to severe risk of Hyperkalemia (high potassium) and cardiac risks."
        ),
        InteractionRule(
            "lisinopril", "potassium",
            messageDescription = "Combining ACE inhibitors like Lisinopril with Potassium supplements leads to severe risk of Hyperkalemia."
        ),
        InteractionRule(
            "ramipril", "spironolactone",
            messageDescription = "Combining ACE inhibitors like Ramipril with potassium-sparing diuretics (Spironolactone) leads to severe risk of Hyperkalemia."
        ),
        InteractionRule(
            "ramipril", "potassium",
            messageDescription = "Combining ACE inhibitors like Ramipril with Potassium supplements leads to severe risk of Hyperkalemia."
        ),
        InteractionRule(
            "tramadol", "fluoxetine",
            messageDescription = "Co-dispensing Tramadol with SSRIs like Fluoxetine can trigger life-threatening Serotonin Syndrome."
        ),
        InteractionRule(
            "tramadol", "sertraline",
            messageDescription = "Co-dispensing Tramadol with SSRIs like Sertraline can trigger life-threatening Serotonin Syndrome."
        ),
        InteractionRule(
            "simvastatin", "clarithromycin",
            messageDescription = "Co-dispensing statins (Simvastatin) with macrolide antibiotics (Clarithromycin) is contra-indicated due to extreme risk of drug-induced Rhabdomyolysis."
        ),
        InteractionRule(
            "atorvastatin", "clarithromycin",
            messageDescription = "Co-dispensing statins (Atorvastatin) with macrolide antibiotics (Clarithromycin) is contra-indicated due to increased risk of drug-induced muscle damage / myopathy."
        ),
        InteractionRule(
            "clopidogrel", "omeprazole",
            messageDescription = "Omeprazole reduces the antiplatelet therapeutical effect of Clopidogrel, making it less effective at preventing stroke or myocardial infarction."
        ),
        InteractionRule(
            "ciprofloxacin", "calcium",
            messageDescription = "Calcium elements significantly inhibit the absorption of oral Ciprofloxacin when co-administered."
        ),
        InteractionRule(
            "ciprofloxacin", "iron",
            messageDescription = "Iron supplements significantly inhibit the absorption of oral Ciprofloxacin when co-administered."
        ),
        InteractionRule(
            "methotrexate", "ibuprofen",
            messageDescription = "NSAIDs like Ibuprofen reduce renal clearance of Methotrexate, creating severe risk of methotrexate toxic build-up."
        ),
        InteractionRule(
            "digoxin", "amiodarone",
            messageDescription = "Amiodarone drastically elevates Digoxin blood levels leading to potentially fatal digoxin cardiovascular toxicity."
        )
    )

    fun checkInteractions(medicationNames: List<String>): List<String> {
        if (medicationNames.size < 2) return emptyList()
        val normalizedMeds = medicationNames.map { it.lowercase().trim() }
        val triggeredWarnings = mutableListOf<String>()

        for (i in 0 until normalizedMeds.size) {
            val med1 = normalizedMeds[i]
            for (j in i + 1 until normalizedMeds.size) {
                val med2 = normalizedMeds[j]

                for (rule in rules) {
                    val containsA1 = med1.contains(rule.drugA)
                    val containsB2 = med2.contains(rule.drugB)
                    
                    val containsA2 = med2.contains(rule.drugA)
                    val containsB1 = med1.contains(rule.drugB)

                    if ((containsA1 && containsB2) || (containsA2 && containsB1)) {
                        val originalName1 = medicationNames[i]
                        val originalName2 = medicationNames[j]
                        triggeredWarnings.add("Warning: Dispensing $originalName1 alongside $originalName2 carries a severe risk - ${rule.messageDescription}")
                    }
                }
            }
        }
        return triggeredWarnings.distinct()
    }
}
