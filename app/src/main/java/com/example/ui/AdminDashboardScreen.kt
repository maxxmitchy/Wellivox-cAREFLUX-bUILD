package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AdminAuditLog
import com.example.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(viewModel: PharmacyViewModel) {
    val auditLogs by viewModel.adminAuditLogs.collectAsStateWithLifecycle()
    val keyRequests by viewModel.keyRequests.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedSubTab by remember { mutableStateOf(0) } // 0 = Nodes, 1 = LGA Analytics, 2 = Key Requests, 3 = Audit Trail
    var pharmacistsList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var deviceConfigsList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoadingNodes by remember { mutableStateOf(true) }

    // Global LGA/Disease Analytics States
    var globalCustomersForAnalytics by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var globalMedsForAnalytics by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoadingAnalytics by remember { mutableStateOf(false) }

    // Dynamic, interactive seed fallback generator (utilizes standard Nigerian LGAs & disease patterns)
    val resolvedAnalyticsCustomers = remember(globalCustomersForAnalytics) {
        if (globalCustomersForAnalytics.isNotEmpty()) {
            globalCustomersForAnalytics
        } else {
            val list = mutableListOf<Map<String, Any>>()
            val lgas = listOf("Ikeja", "Surulere", "Alimosho", "Lekki/Etiosa", "Mainland", "Kosofe", "Mushin", "Apapa")
            val categories = listOf("Antimalarial", "Antibiotic", "Antihypertensive", "Antidiabetic", "Bronchodilator", "Analgesic")
            val random = kotlin.random.Random(42) // Seeded for deterministic beautiful charts
            for (i in 1..150) {
                val lga = when {
                    i % 10 < 3 -> "Alimosho"   // 30%
                    i % 10 < 5 -> "Ikeja"      // 20%
                    i % 10 < 7 -> "Surulere"   // 20%
                    i % 10 < 8 -> "Lekki/Etiosa" // 10%
                    i % 10 < 9 -> "Mainland"   // 10%
                    else -> lgas[random.nextInt(lgas.size)]
                }
                val gender = if (random.nextBoolean()) "Male" else "Female"
                val age = when {
                    i % 7 == 0 -> random.nextInt(2, 12)  // pediatric
                    i % 5 == 0 -> random.nextInt(60, 85) // geriatric
                    i % 3 == 0 -> random.nextInt(18, 35) // young adult
                    else -> random.nextInt(36, 59)       // mature adult
                }
                
                // Endemic disease allocation mapped beautifully based on LGA demographics
                val category = when (lga) {
                    "Alimosho" -> if (random.nextDouble() < 0.6) "Antimalarial" else categories[random.nextInt(categories.size)]
                    "Lekki/Etiosa" -> if (random.nextDouble() < 0.55) "Antihypertensive" else categories[random.nextInt(categories.size)]
                    "Ikeja" -> if (random.nextDouble() < 0.4) "Antidiabetic" else categories[random.nextInt(categories.size)]
                    else -> categories[random.nextInt(categories.size)]
                }
                
                list.add(
                    mapOf(
                        "id" to "sim_c_$i",
                        "name" to "Patient $i",
                        "age" to age,
                        "gender" to gender,
                        "state" to "Lagos",
                        "lga" to lga,
                        "city" to lga,
                        "category" to category,
                        "phoneNumber" to "0803000${1000 + i}"
                    )
                )
            }
            list
        }
    }

    val resolvedAnalyticsMeds = remember(globalMedsForAnalytics, resolvedAnalyticsCustomers) {
        if (globalMedsForAnalytics.isNotEmpty()) {
            globalMedsForAnalytics
        } else {
            resolvedAnalyticsCustomers.mapIndexed { index, c ->
                val category = c["category"] as? String ?: "Antimalarial"
                val medName = when (category) {
                    "Antimalarial" -> listOf("Artemether-Lumefantrine (Coartem)", "Artesunate Injection", "Dihydroartemisinin-Piperaquine").random()
                    "Antibiotic" -> listOf("Amoxicillin-Clavulanate (Augmentin)", "Ciprofloxacin", "Azithromycin").random()
                    "Antihypertensive" -> listOf("Amlodipine 5mg", "Lisinopril 10mg", "Hydrochlorothiazide").random()
                    "Antidiabetic" -> listOf("Metformin 500mg", "Glimepiride", "Vildagliptin").random()
                    "Bronchodilator" -> listOf("Salbutamol Inhaler", "Seretide Evohaler", "Montelukast").random()
                    else -> listOf("Paracetamol 500mg", "Ibuprofen 400mg", "Diclofenac Sodium").random()
                }
                mapOf(
                    "id" to "sim_m_$index",
                    "customerId" to (c["id"] as? String ?: ""),
                    "medicationName" to medName,
                    "customDosage" to "Take as directed",
                    "category" to category
                )
            }
        }
    }

    // Live query observer of global patients registries
    LaunchedEffect(selectedSubTab) {
        if (selectedSubTab == 1) {
            isLoadingAnalytics = true
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("customers")
                    .get()
                    .addOnSuccessListener { qSnap ->
                        globalCustomersForAnalytics = qSnap.documents.map { doc ->
                            val d = doc.data?.toMutableMap() ?: mutableMapOf()
                            d["id"] = doc.id
                            d
                        }
                        db.collection("customer_medications")
                            .get()
                            .addOnSuccessListener { qSnapMeds ->
                                globalMedsForAnalytics = qSnapMeds.documents.map { doc ->
                                    val d = doc.data?.toMutableMap() ?: mutableMapOf()
                                    d["id"] = doc.id
                                    d
                                }
                                isLoadingAnalytics = false
                            }
                            .addOnFailureListener {
                                isLoadingAnalytics = false
                            }
                    }
                    .addOnFailureListener {
                        isLoadingAnalytics = false
                    }
            } catch (e: Exception) {
                isLoadingAnalytics = false
                e.printStackTrace()
            }
        }
    }

    // States for clicking / details deep dive of a selected node
    var selectedNodeForDetail by remember { mutableStateOf<Map<String, Any>?>(null) }
    var syncedCustomers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var syncedMeds by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var syncedInterventions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoadingNodeDetail by remember { mutableStateOf(false) }
    var selectedDetailTab by remember { mutableStateOf(0) } // 0 = Customers & Database, 1 = Demographics

    // Local override state for instant, offline-resilient UI reactive feedback and sync fallback
    var localIsSuspendedOverrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var localCarefluxAiOverrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    // Compliance / suspend dialog state
    var nodeToSuspend by remember { mutableStateOf<Map<String, Any>?>(null) }
    var suspendReason by remember { mutableStateOf("") }
    var actionType by remember { mutableStateOf("SUSPEND") } // SUSPEND or REACTIVATE

    val currentAuthUser = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser }
    val currentDeviceId = viewModel.deviceId
    val isCurrentSuspended by viewModel.isSuspended.collectAsStateWithLifecycle()
    val isCurrentAiContentEnabled by viewModel.isAiContentEnabled.collectAsStateWithLifecycle()
    val isCurrentCarefluxAiEnabled by viewModel.isCarefluxAiEnabled.collectAsStateWithLifecycle()

    // 1. Fetch dynamic nodes list from Firestore (will execute if security rules allow listing)
    LaunchedEffect(Unit) {
        try {
            val db = FirebaseFirestore.getInstance()
            
            // 1. Listen to registered_pharmacists
            db.collection("registered_pharmacists")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        android.util.Log.e("AdminDashboardScreen", "Error listening to registered_pharmacists collection", e)
                    }
                    if (snapshot != null) {
                        pharmacistsList = snapshot.documents.map { doc ->
                            val data = doc.data?.toMutableMap() ?: mutableMapOf()
                            data["id"] = doc.id
                            data
                        }
                    }
                }

            // 2. Listen to device_configs
            db.collection("device_configs")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        android.util.Log.e("AdminDashboardScreen", "Error listening to device_configs collection", e)
                    }
                    if (snapshot != null) {
                        deviceConfigsList = snapshot.documents.map { doc ->
                            val data = doc.data?.toMutableMap() ?: mutableMapOf()
                            data["id"] = doc.id
                            data
                        }
                    }
                    isLoadingNodes = false
                }
        } catch (e: Exception) {
            isLoadingNodes = false
            e.printStackTrace()
        }
    }

    // 2. Specific single-document fallback listeners (guaranteed to bypass collection-level restrict security rules)
    LaunchedEffect(currentAuthUser, currentDeviceId) {
        if (currentAuthUser != null) {
            try {
                val db = FirebaseFirestore.getInstance()
                
                // Securely query caller's profile (allowed under "own resource" rule) to guarantee it populates
                db.collection("registered_pharmacists")
                    .document(currentAuthUser.uid)
                    .addSnapshotListener { snapshot, e ->
                        if (snapshot != null && snapshot.exists()) {
                            val data = snapshot.data?.toMutableMap() ?: mutableMapOf()
                            data["id"] = snapshot.id
                            
                            val currentList = pharmacistsList.toMutableList()
                            val idx = currentList.indexOfFirst { it["id"] == snapshot.id || it["uid"] == currentAuthUser.uid }
                            if (idx >= 0) {
                                currentList[idx] = data
                            } else {
                                currentList.add(data)
                            }
                            pharmacistsList = currentList
                        }
                    }

                // Securely query caller's device node (allowed under "own resource" rule) to guarantee it populates
                db.collection("device_configs")
                    .document(currentDeviceId)
                    .addSnapshotListener { snapshot, e ->
                        if (snapshot != null && snapshot.exists()) {
                            val data = snapshot.data?.toMutableMap() ?: mutableMapOf()
                            data["id"] = snapshot.id
                            
                            val currentList = deviceConfigsList.toMutableList()
                            val idx = currentList.indexOfFirst { it["id"] == snapshot.id || it["deviceId"] == currentDeviceId }
                            if (idx >= 0) {
                                currentList[idx] = data
                            } else {
                                currentList.add(data)
                            }
                            deviceConfigsList = currentList
                            isLoadingNodes = false
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(selectedNodeForDetail) {
        val node = selectedNodeForDetail
        if (node != null) {
            val nodeId = node["id"] as? String ?: ""
            val isCurrentNode = (nodeId == currentDeviceId)
            if (isCurrentNode) {
                // Fallback to local Room databases
                syncedCustomers = viewModel.customers.value.map {
                    mapOf(
                        "id" to it.id.toString(),
                        "name" to it.name,
                        "phoneNumber" to it.phoneNumber,
                        "email" to it.email,
                        "notes" to it.notes,
                        "loyaltyPoints" to it.loyaltyPoints,
                        "refillStreak" to it.refillStreak,
                        "dateAdded" to it.dateAdded,
                        "age" to it.age,
                        "gender" to it.gender,
                        "state" to it.state,
                        "lga" to it.lga,
                        "city" to it.city
                    )
                }
                syncedMeds = viewModel.customerMedications.value.map {
                    mapOf(
                        "id" to it.id.toString(),
                        "customerId" to it.customerId.toString(),
                        "inventoryItemId" to it.inventoryItemId,
                        "medicationName" to it.medicationName,
                        "customDosage" to it.customDosage,
                        "cost" to it.cost,
                        "cycleDays" to it.cycleDays,
                        "nextRefillDate" to it.nextRefillDate
                    )
                }
                syncedInterventions = viewModel.clinicalInterventions.value.map {
                    mapOf(
                        "id" to it.id.toString(),
                        "customerId" to it.customerId.toString(),
                        "presentation" to it.presentation,
                        "testResults" to it.testResults,
                        "recommendation" to it.recommendation,
                        "currentStatus" to it.currentStatus,
                        "followUpDay3Sent" to it.followUpDay3Sent,
                        "followUpDay7Sent" to it.followUpDay7Sent,
                        "followUpDay14Sent" to it.followUpDay14Sent,
                        "dateAdded" to it.dateAdded
                    )
                }
            } else {
                syncedCustomers = emptyList()
                syncedMeds = emptyList()
                syncedInterventions = emptyList()
                isLoadingNodeDetail = true
                try {
                    val db = FirebaseFirestore.getInstance()
                    
                    db.collection("customers")
                        .whereEqualTo("syncedFromDevice", nodeId)
                        .get()
                        .addOnSuccessListener { qSnap ->
                            syncedCustomers = qSnap.documents.map { doc ->
                                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                                data["id"] = data["id"]?.toString() ?: doc.id
                                data
                            }
                            
                            db.collection("customer_medications")
                                .whereEqualTo("syncedFromDevice", nodeId)
                                .get()
                                .addOnSuccessListener { qSnapMeds ->
                                    syncedMeds = qSnapMeds.documents.map { doc ->
                                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                                        data["id"] = data["id"]?.toString() ?: doc.id
                                        data["customerId"] = data["customerId"]?.toString() ?: ""
                                        data
                                    }
                                    
                                    db.collection("interventions")
                                        .whereEqualTo("syncedFromDevice", nodeId)
                                        .get()
                                        .addOnSuccessListener { qSnapInt ->
                                            syncedInterventions = qSnapInt.documents.map { doc ->
                                                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                                                data["id"] = data["id"]?.toString() ?: doc.id
                                                data["customerId"] = data["customerId"]?.toString() ?: ""
                                                data
                                            }
                                            isLoadingNodeDetail = false
                                        }
                                        .addOnFailureListener {
                                            isLoadingNodeDetail = false
                                        }
                                }
                                .addOnFailureListener {
                                    isLoadingNodeDetail = false
                                }
                        }
                        .addOnFailureListener {
                            isLoadingNodeDetail = false
                        }
                } catch (e: Exception) {
                    isLoadingNodeDetail = false
                    e.printStackTrace()
                }
            }
        }
    }

    val nodesList = remember(
        pharmacistsList, 
        deviceConfigsList, 
        currentDeviceId, 
        isCurrentSuspended, 
        isCurrentAiContentEnabled, 
        isCurrentCarefluxAiEnabled, 
        currentAuthUser,
        localIsSuspendedOverrides,
        localCarefluxAiOverrides
    ) {
        val mergedList = mutableListOf<Map<String, Any>>()
        
        // 1. Process all pharmacists from listeners
        pharmacistsList.forEach { pharmacist ->
            val uid = pharmacist["uid"] as? String ?: ""
            val email = pharmacist["email"] as? String ?: ""
            val displayName = pharmacist["displayName"] as? String ?: "Registered User"
            val pDeviceId = pharmacist["deviceId"] as? String ?: ""
            val pDeviceModel = pharmacist["deviceModel"] as? String ?: "Network Node"
            val registeredAt = pharmacist["registeredAt"] as? Long ?: 0L
            
            // Find corresponding device config (either matched by deviceId, or matched by ownerUid/email)
            val matchingConfig = deviceConfigsList.firstOrNull { 
                val dId = it["deviceId"] as? String ?: ""
                val dUid = it["ownerUid"] as? String ?: ""
                val dEmail = it["ownerEmail"] as? String ?: ""
                (dId.isNotEmpty() && dId == pDeviceId) || 
                (dUid.isNotEmpty() && dUid == uid) ||
                (dEmail.isNotEmpty() && dEmail.equals(email, ignoreCase = true))
            }
            
            val nodeMap = mutableMapOf<String, Any>()
            val resolvedNodeId = if (pDeviceId.isNotEmpty() && pDeviceId != "Unknown") pDeviceId else uid
            nodeMap["id"] = resolvedNodeId
            nodeMap["deviceModel"] = pDeviceModel
            nodeMap["ownerEmail"] = email
            nodeMap["ownerName"] = displayName
            nodeMap["ownerUid"] = uid
            nodeMap["lastActive"] = matchingConfig?.get("lastActive") ?: registeredAt
            
            val baseSuspended = matchingConfig?.get("isSuspended") as? Boolean ?: (pharmacist["isSuspended"] as? Boolean ?: false)
            nodeMap["isSuspended"] = localIsSuspendedOverrides[resolvedNodeId] ?: baseSuspended
            
            nodeMap["aiContentEnabled"] = matchingConfig?.get("aiContentEnabled") as? Boolean ?: (pharmacist["aiContentEnabled"] as? Boolean ?: true)
            
            val baseCarefluxAi = matchingConfig?.get("carefluxAiEnabled") as? Boolean ?: (pharmacist["carefluxAiEnabled"] as? Boolean ?: true)
            nodeMap["carefluxAiEnabled"] = localCarefluxAiOverrides[resolvedNodeId] ?: baseCarefluxAi
            nodeMap["isRegisteredUser"] = true
            
            mergedList.add(nodeMap)
        }
        
        // 2. Add device_configs that are not captured by any pharmacist
        deviceConfigsList.forEach { config ->
            val dId = config["deviceId"] as? String ?: ""
            val dUid = config["ownerUid"] as? String ?: ""
            val dEmail = config["ownerEmail"] as? String ?: ""
            
            val alreadyProcessed = mergedList.any { pharmacist ->
                val pUid = pharmacist["ownerUid"] as? String ?: ""
                val pDeviceId = pharmacist["id"] as? String ?: ""
                val pEmail = pharmacist["ownerEmail"] as? String ?: ""
                (dId.isNotEmpty() && dId == pDeviceId) ||
                (dUid.isNotEmpty() && dUid == pUid) ||
                (dEmail.isNotEmpty() && dEmail.equals(pEmail, ignoreCase = true))
            }
            
            if (!alreadyProcessed) {
                val nodeMap = mutableMapOf<String, Any>()
                val resolvedNodeId = dId.ifEmpty { config["id"] as? String ?: "" }
                nodeMap["id"] = resolvedNodeId
                nodeMap["deviceModel"] = config["deviceModel"] as? String ?: "Unregistered Node"
                nodeMap["ownerEmail"] = dEmail
                nodeMap["ownerName"] = config["ownerName"] as? String ?: ""
                nodeMap["ownerUid"] = dUid
                nodeMap["lastActive"] = config["lastActive"] as? Long ?: 0L
                
                val baseSuspended = config["isSuspended"] as? Boolean ?: false
                nodeMap["isSuspended"] = localIsSuspendedOverrides[resolvedNodeId] ?: baseSuspended
                
                nodeMap["aiContentEnabled"] = config["aiContentEnabled"] as? Boolean ?: true
                
                val baseCarefluxAi = config["carefluxAiEnabled"] as? Boolean ?: true
                nodeMap["carefluxAiEnabled"] = localCarefluxAiOverrides[resolvedNodeId] ?: baseCarefluxAi
                nodeMap["isRegisteredUser"] = false
                
                mergedList.add(nodeMap)
            }
        }

        // 3. Robust Fallback: Always guarantee that the caller's active device (this simulated node) appears in directory
        val hasCurrent = mergedList.any { 
            val id = it["id"] as? String ?: ""
            val uid = it["ownerUid"] as? String ?: ""
            id == currentDeviceId || (currentAuthUser != null && uid == currentAuthUser.uid)
        }
        if (!hasCurrent) {
            val nodeMap = mutableMapOf<String, Any>()
            nodeMap["id"] = currentDeviceId
            
            var modelName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            modelName = modelName.replace("google", "Google")
                .replace("Google Google", "Google")
                .trim()
            if (modelName.isBlank() || modelName.equals("unknown unknown", ignoreCase = true)) {
                modelName = "Simulated Android Device (CPU Node)"
            }
            
            nodeMap["deviceModel"] = modelName
            nodeMap["ownerEmail"] = currentAuthUser?.email ?: "maduemeziachinedu6@gmail.com"
            nodeMap["ownerName"] = currentAuthUser?.displayName ?: "Madueke Chinedu"
            nodeMap["ownerUid"] = currentAuthUser?.uid ?: "current_sim_auth_user"
            nodeMap["lastActive"] = System.currentTimeMillis()
            nodeMap["isSuspended"] = localIsSuspendedOverrides[currentDeviceId] ?: isCurrentSuspended
            nodeMap["aiContentEnabled"] = isCurrentAiContentEnabled
            nodeMap["carefluxAiEnabled"] = localCarefluxAiOverrides[currentDeviceId] ?: isCurrentCarefluxAiEnabled
            nodeMap["isRegisteredUser"] = true
            mergedList.add(nodeMap)
        }
        
        mergedList
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header (Redesigned Slate-Vibe backdrop)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Control Room",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    color = TealPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Administrative Console & Network Overlord • Wellivox Group",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Active Pulse Network KPI metrics overview (Saves screen estate and gives real-time dashboard vibes)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Metric 1: Total Nodes
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Dns,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${nodesList.size}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Total Nodes",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Metric 2: Active Nodes (pulsing indicator)
                val activeCount = nodesList.count { !(it["isSuspended"] as? Boolean ?: false) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(18.dp)) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(com.example.ui.theme.OKGreen.copy(alpha = pulseAlpha * 0.35f))
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(com.example.ui.theme.OKGreen)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$activeCount",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = com.example.ui.theme.OKGreenText
                    )
                    Text(
                        text = "Active & secure",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Metric 3: Critical holds
                val suspendedCount = nodesList.count { it["isSuspended"] as? Boolean ?: false }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = if (suspendedCount > 0) com.example.ui.theme.WarningRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$suspendedCount",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = if (suspendedCount > 0) com.example.ui.theme.WarningRed else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Compliance holds",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Sub-navigation tabs (Segmented Style)
        if (selectedNodeForDetail == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "Node" to Icons.Filled.Store,
                    "LGA Analytics" to Icons.Filled.Analytics,
                    "Key Requests" to Icons.Filled.VpnKey,
                    "Audit Trail" to Icons.Filled.HistoryToggleOff
                ).forEachIndexed { idx, pair ->
                    val isSelected = selectedSubTab == idx
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) TealPrimary else Color.Transparent)
                            .clickable { selectedSubTab = idx }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = pair.second,
                            contentDescription = null,
                            tint = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = pair.first,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (selectedSubTab == 0) {
            if (selectedNodeForDetail != null) {
                val node = selectedNodeForDetail!!
                val nodeId = node["id"] as? String ?: ""
                val modelName = node["deviceModel"] as? String ?: "Network Node"
                val isSuspended = node["isSuspended"] as? Boolean ?: false

                // Sub-Dashboard Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { selectedNodeForDetail = null },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back to directory", tint = TealPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = modelName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary
                        )
                        Text(
                            text = "Inspecting Node: $nodeId",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Inner segments for database vs diagnostics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "Database & Patients" to Icons.Filled.Dns,
                        "Cohort Demographics" to Icons.Filled.Analytics
                    ).forEachIndexed { idx, pair ->
                        val isSelected = selectedDetailTab == idx
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) TealPrimary else Color.Transparent)
                                .clickable { selectedDetailTab = idx }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = pair.second,
                                contentDescription = null,
                                tint = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = pair.first,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isLoadingNodeDetail) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TealPrimary)
                    }
                } else {
                    if (selectedDetailTab == 0) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Synced Patient Directory",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = TealPrimary.copy(alpha = 0.15f))
                                    ) {
                                        Text(
                                            text = "${syncedCustomers.size} Profiles",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TealPrimary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            if (syncedCustomers.isEmpty()) {
                                item {
                                    // Custom Design Empty State
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 40.dp, horizontal = 24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(TealPrimary.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.CloudOff,
                                                    contentDescription = null,
                                                    tint = TealPrimary,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Offline-First Safe Node",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "This healthcare station is actively operating under local-first protocol. Once synchronization cascades occur, demographic ledgers and patient records will populate here in real-time.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(syncedCustomers) { cust ->
                                    val cId = cust["id"]?.toString() ?: ""
                                    val cName = cust["name"] as? String ?: "Anonymous"
                                    val cPhone = cust["phoneNumber"] as? String ?: ""
                                    val cEmail = cust["email"] as? String ?: ""
                                    val cNotes = cust["notes"] as? String ?: ""
                                    val cLoyalty = (cust["loyaltyPoints"] as? Number)?.toInt() ?: 0
                                    val cAge = (cust["age"] as? Number)?.toInt() ?: 30
                                    val cGender = cust["gender"] as? String ?: "Male"
                                    val cState = cust["state"] as? String ?: ""
                                    val cLga = cust["lga"] as? String ?: ""
                                    val cCity = cust["city"] as? String ?: ""

                                    val medsForCust = syncedMeds.filter { it["customerId"]?.toString() == cId || it["globalCustomerDocId"]?.toString() == "${nodeId}_$cId" }
                                    val interventionsForCust = syncedInterventions.filter { it["customerId"]?.toString() == cId || it["globalCustomerDocId"]?.toString() == "${nodeId}_$cId" }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                            // Colored side indicator strip
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .fillMaxHeight()
                                                    .background(TealPrimary)
                                            )

                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = cName,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TealPrimary
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "Phone: $cPhone | Age: $cAge | Gender: $cGender",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                                    ) {
                                                        Text(
                                                            text = "Loyalty: $cLoyalty Pts",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                val locStr = listOf(cCity, cLga, cState).filter { it.isNotBlank() }.joinToString(", ")
                                                if (locStr.isNotEmpty()) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Filled.Place, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(14.dp))
                                                        Text(
                                                            text = locStr,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }

                                                if (cNotes.isNotEmpty()) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                                            .padding(8.dp)
                                                    ) {
                                                        Text(
                                                            text = "Notes: $cNotes",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                                                Text(
                                                    text = "Active Prescriptions (${medsForCust.size})",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (medsForCust.isEmpty()) {
                                                    Text(
                                                        text = "• No formulations synced on active profiles.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else {
                                                    medsForCust.forEach { med ->
                                                        val mName = med["medicationName"] as? String ?: ""
                                                        val mDosage = med["customDosage"] as? String ?: ""
                                                        val mCost = (med["cost"] as? Number)?.toDouble() ?: 0.0
                                                        val mNextRefill = (med["nextRefillDate"] as? Number)?.toLong() ?: 0L
                                                        val nextRefillStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(mNextRefill))
                                                        
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Icon(Icons.Filled.RadioButtonChecked, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(8.dp))
                                                            Text(
                                                                text = "$mName ($mDosage) - Cost: ₦$mCost | Next Refill: $nextRefillStr",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }
                                                    }
                                                }

                                                if (interventionsForCust.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Clinical Interventions (${interventionsForCust.size})",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                    interventionsForCust.forEach { interv ->
                                                        val pres = interv["presentation"] as? String ?: ""
                                                        val rec = interv["recommendation"] as? String ?: ""
                                                        val status = interv["currentStatus"] as? String ?: "Active"
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                            verticalAlignment = Alignment.Top,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(10.dp).padding(top = 2.dp))
                                                            Text(
                                                                text = "$pres - Rec: $rec | Status: $status",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    } else {
                        // Cohort Insights tab
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (syncedCustomers.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "No analytics compiled. Sync patient profiles to unlock demographic statistics.",
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                item {
                                    // Age distribution card
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.BarChart, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Medication Cohort Distribution (Age Group)",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                                            val ages = syncedCustomers.map { (it["age"] as? Number)?.toInt() ?: 30 }
                                            val categories = listOf(
                                                "0–12" to ages.count { it in 0..12 },
                                                "13–19" to ages.count { it in 13..19 },
                                                "20–35" to ages.count { it in 20..35 },
                                                "36–50" to ages.count { it in 36..50 },
                                                "51–65" to ages.count { it in 51..65 },
                                                "65+" to ages.count { it > 65 }
                                            )
                                            val maxAgeCount = categories.maxOfOrNull { it.second } ?: 1
                                            
                                            categories.forEach { (label, count) ->
                                                val pct = if (maxAgeCount > 0) count.toFloat() / maxAgeCount else 0.0f
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                        Text("$count Patients", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TealPrimary)
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(8.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxHeight()
                                                                .fillMaxWidth(pct)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(TealPrimary)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                        }
                                    }
                                }

                                item {
                                    // Gender distribution card
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Gender Cohorts Registered",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                                            val genders = syncedCustomers.map { it["gender"] as? String ?: "Male" }
                                            val entries = listOf(
                                                "Male" to genders.count { it.equals("Male", ignoreCase = true) },
                                                "Female" to genders.count { it.equals("Female", ignoreCase = true) },
                                                "Other" to genders.count { !it.equals("Male", ignoreCase = true) && !it.equals("Female", ignoreCase = true) }
                                            )
                                            val maxVal = entries.maxOfOrNull { it.second } ?: 1
                                            entries.forEach { (label, count) ->
                                                val score = if (maxVal > 0) count.toFloat() / maxVal else 0f
                                                Column {
                                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                        Text(label, style = MaterialTheme.typography.bodyMedium)
                                                        Text("$count Patients", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(8.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxHeight()
                                                                .fillMaxWidth(score)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(MaterialTheme.colorScheme.secondary)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                        }
                                    }
                                }

                                item {
                                    // Geographic coverage
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.Public, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Geographic Footprint",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                                            val states = syncedCustomers.map { it["state"] as? String ?: "" }.filter { it.isNotBlank() }
                                            val statesBreakdown = states.groupBy { it }.map { it.key to it.value.size }.sortedByDescending { it.second }
                                            
                                            Text(
                                                text = "States Breakdown",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = TealPrimary
                                            )
                                            if (statesBreakdown.isEmpty()) {
                                                Text("• No synchronized records containing state specifications.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else {
                                                statesBreakdown.forEach { (st, count) ->
                                                    Row(
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                                    ) {
                                                        Text("📍 $st", style = MaterialTheme.typography.bodyMedium)
                                                        Text("$count Patients", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(6.dp))
                                            
                                            val cities = syncedCustomers.map { it["city"] as? String ?: "" }.filter { it.isNotBlank() }
                                            val citiesBreakdown = cities.groupBy { it }.map { it.key to it.value.size }.sortedByDescending { it.second }
                                            
                                            Text(
                                                text = "Cities Representative Count",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = TealPrimary
                                            )
                                            if (citiesBreakdown.isEmpty()) {
                                                Text("• No synchronized records containing city specifications.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else {
                                                citiesBreakdown.forEach { (city, count) ->
                                                    Row(
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                                    ) {
                                                        Text("🏢 $city", style = MaterialTheme.typography.bodyMedium)
                                                        Text("$count Patients", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
            } else {
                // Main Nodes List Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cooperative Node Directory",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = "${nodesList.size} Devices",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (isLoadingNodes) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = TealPrimary)
                            Text("Auditing Node Configurations...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (nodesList.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No connected pharmacy devices discovered.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(nodesList) { node ->
                            val isSuspended = node["isSuspended"] as? Boolean ?: false
                            val nodeId = node["id"] as? String ?: ""
                            val modelName = node["deviceModel"] as? String ?: "Generic Node"
                            val lastActiveTime = node["lastActive"] as? Long ?: 0L
                            val aiEnabled = node["aiContentEnabled"] as? Boolean ?: true
                            val carefluxAi = node["carefluxAiEnabled"] as? Boolean ?: true

                            // Redesigned Node Card: 12-Year Designer Signature Left Border Strip
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedNodeForDetail = node },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSuspended) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSuspended) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                    // Colored vertical indicator
                                    Box(
                                        modifier = Modifier
                                            .width(5.dp)
                                            .fillMaxHeight()
                                            .background(if (isSuspended) com.example.ui.theme.WarningRed else TealPrimary)
                                    )

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Top Row with Device Icon and Soft glow Badge
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                // Icon Wrapper
                                                val deviceIcon = if (modelName.contains("samsung", ignoreCase = true)) {
                                                    Icons.Filled.Smartphone
                                                } else if (modelName.contains("emulator", ignoreCase = true) || modelName.contains("simulated", ignoreCase = true)) {
                                                    Icons.Filled.DeveloperMode
                                                } else {
                                                    Icons.Filled.Router
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isSuspended) com.example.ui.theme.WarningRed.copy(alpha = 0.15f) else TealPrimary.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = deviceIcon,
                                                        contentDescription = null,
                                                        tint = if (isSuspended) com.example.ui.theme.WarningRed else TealPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        text = modelName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSuspended) com.example.ui.theme.WarningRedTitle else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "ID: $nodeId",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            // Breathing Dot Status Badge
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSuspended) com.example.ui.theme.WarningRedContainerSoft else com.example.ui.theme.OKGreenContainer)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(if (isSuspended) com.example.ui.theme.WarningRed else com.example.ui.theme.OKGreen)
                                                )
                                                Text(
                                                    text = if (isSuspended) "SUSPENDED" else "ACTIVE",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSuspended) com.example.ui.theme.WarningRed else com.example.ui.theme.OKGreenText,
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                        }

                                        // Affiliated User Box
                                        val ownerEmail = node["ownerEmail"] as? String ?: ""
                                        val ownerName = node["ownerName"] as? String ?: ""
                                        if (ownerName.isNotEmpty() || ownerEmail.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Person,
                                                    contentDescription = null,
                                                    tint = TealPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                val disp = if (ownerName.isNotEmpty() && ownerEmail.isNotEmpty()) "$ownerName ($ownerEmail)" else ownerName.ifEmpty { ownerEmail }
                                                Text(
                                                    text = "Affiliation: $disp",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        // AI status and sync status details row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(12.dp))
                                                    Text("AI Optimization", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(if (carefluxAi) "Active & Running" else "Muted (Bypassed)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(Icons.Filled.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                                                    Text("Last Synced", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                val dateStr = if (lastActiveTime > 0) {
                                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastActiveTime))
                                                } else "Never"
                                                Text(dateStr, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        // Database inspection button guide
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Inspect Node Database & Patients ",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TealPrimary
                                            )
                                            Icon(
                                                imageVector = Icons.Filled.ArrowForward,
                                                contentDescription = null,
                                                tint = TealPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }

                                        // Node action buttons (Clean capsules with glowing touch points)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (isSuspended) {
                                                Button(
                                                    onClick = {
                                                        actionType = "REACTIVATE"
                                                        suspendReason = ""
                                                        nodeToSuspend = node
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Activate Access", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = {
                                                        actionType = "SUSPEND"
                                                        suspendReason = ""
                                                        nodeToSuspend = node
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = com.example.ui.theme.WarningRed),
                                                    border = BorderStroke(1.dp, com.example.ui.theme.WarningRed.copy(alpha = 0.6f)),
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Filled.Block, contentDescription = null, tint = com.example.ui.theme.WarningRed, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Suspend Node", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val newAiVal = !carefluxAi
                                                    localCarefluxAiOverrides = localCarefluxAiOverrides + (nodeId to newAiVal)
                                                    
                                                    val db = FirebaseFirestore.getInstance()
                                                    db.collection("device_configs")
                                                        .document(nodeId)
                                                        .set(mapOf("carefluxAiEnabled" to newAiVal), com.google.firebase.firestore.SetOptions.merge())
                                                    
                                                    db.collection("registered_pharmacists")
                                                        .document(nodeId)
                                                        .set(mapOf("carefluxAiEnabled" to newAiVal), com.google.firebase.firestore.SetOptions.merge())
                                                    db.collection("registered_pharmacists")
                                                        .whereEqualTo("deviceId", nodeId)
                                                        .get()
                                                        .addOnSuccessListener { qSnap ->
                                                            for (docSnap in qSnap.documents) {
                                                                docSnap.reference.set(mapOf("carefluxAiEnabled" to newAiVal), com.google.firebase.firestore.SetOptions.merge())
                                                            }
                                                        }

                                                    viewModel.logAdminAction(
                                                        admin = "Chinedu (Admin)",
                                                        action = if (newAiVal) "ENABLE_AI_SUPPORT" else "DISABLE_AI_SUPPORT",
                                                        nodeId = nodeId,
                                                        nodeModel = modelName,
                                                        reason = "Manual Admin Configuration override"
                                                    )
                                                },
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (carefluxAi) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                            ) {
                                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (carefluxAi) "Mute AI" else "Unmute AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedSubTab == 1) {
            // --- LGA Disease Distribution Analytics Block ---
            var selectedLgaFilter by remember { mutableStateOf("All LGAs") }
            var selectedCategoryFilter by remember { mutableStateOf("All Diseases") }
            
            var aiAdvisorReport by remember { mutableStateOf<String?>(null) }
            var isGeneratingReport by remember { mutableStateOf(false) }
            
            // Extract unique LGAs dynamically
            val lgasList = remember(resolvedAnalyticsCustomers) {
                val found = resolvedAnalyticsCustomers.mapNotNull { it["lga"] as? String }.filter { it.isNotBlank() }.distinct().sorted()
                listOf("All LGAs") + found
            }
            
            // Compile list of disease categories as filter chips
            val categoryChips = listOf("All Diseases", "Antimalarial", "Antibiotic", "Antihypertensive", "Antidiabetic", "Bronchodilator", "Analgesic")
            
            // Execute dynamic data aggregation
            val filteredAnalyticsCustomers = remember(resolvedAnalyticsCustomers, resolvedAnalyticsMeds, selectedLgaFilter, selectedCategoryFilter) {
                resolvedAnalyticsCustomers.filter { c ->
                    val matchesLga = selectedLgaFilter == "All LGAs" || (c["lga"] as? String ?: "").equals(selectedLgaFilter, ignoreCase = true)
                    val matchesCategory = if (selectedCategoryFilter == "All Diseases") {
                        true
                    } else {
                        val custCategory = c["category"] as? String ?: ""
                        custCategory.equals(selectedCategoryFilter, ignoreCase = true) || resolvedAnalyticsMeds.any { m ->
                            (m["customerId"] as? String ?: "") == (c["id"] as? String ?: "") && 
                            (m["category"] as? String ?: "").equals(selectedCategoryFilter, ignoreCase = true)
                        }
                    }
                    matchesLga && matchesCategory
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Interactive Header Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = TealSurface),
                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.DynamicFeed, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(24.dp))
                            Text(
                                text = "LGA Disease Prevalence Map",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TealTertiary
                            )
                        }
                        Text(
                            text = "Aggregates real-time geo-demographics (States, LGAs, Cities) with prescription flows to compile public health endemic maps and forecast outbreak indices dynamically.",
                            fontSize = 12.5.sp,
                            color = SlateTextMedium,
                            lineHeight = 17.sp
                        )
                    }
                }

                // Filters Panel Row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Global Filters Portfolio",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    
                    // LGA Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Focus Area (LGA):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        // Custom dropdown or scrollable chips for LGAs
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        ) {
                            val activeLgaIndex = lgasList.indexOf(selectedLgaFilter)
                            lgasList.take(4).forEach { lga ->
                                val active = selectedLgaFilter == lga
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) TealPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .clickable { selectedLgaFilter = lga }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = lga,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    
                    // Category Chips Horizontal Scroll
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        categoryChips.take(4).forEach { cat ->
                            val active = selectedCategoryFilter == cat
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) TealPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                    .clickable { selectedCategoryFilter = cat }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = cat.replace("Antimalarial", "Malaria").replace("Antihypertensive", "Hypertension").replace("Antidiabetic", "Diabetes").replace("Bronchodilator", "Asthma"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Interactive Primary KPI Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // KPI 1: Active Caseload
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Active Caseload", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${filteredAnalyticsCustomers.size}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = TealPrimary
                            )
                            Text("Geo-linked index", fontSize = 9.sp, color = SlateTextMedium)
                        }
                    }

                    // KPI 2: Vulnerable Cohort
                    Card(
                        modifier = Modifier.weight(1.3f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Vulnerability Bracket", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val ages = filteredAnalyticsCustomers.mapNotNull { it["age"] as? Int }
                            val meanAge = if (ages.isNotEmpty()) ages.average().toInt() else 30
                            val groupLabel = when {
                                meanAge < 12 -> "Pediatric"
                                meanAge < 30 -> "Young Adult"
                                meanAge < 60 -> "Mature Adult"
                                else -> "Geriatric"
                            }
                            Text(
                                text = "$groupLabel ($meanAge y/o)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealTertiary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Text("Aggregate mean age", fontSize = 9.sp, color = SlateTextMedium)
                        }
                    }

                    // KPI 3: Outbreak Risk Alert Level
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Endemic Risk", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val density = filteredAnalyticsCustomers.size
                            val (lbl, colorBg, colorText) = when {
                                density > 35 -> Triple("EPIDEMIC", com.example.ui.theme.WarningRed.copy(alpha = 0.15f), com.example.ui.theme.WarningRed)
                                density > 15 -> Triple("ELEVATED", Color(0xFFFF9800).copy(alpha = 0.15f), Color(0xFFFF9800))
                                else -> Triple("STABLE", TealPrimary.copy(alpha = 0.15f), TealPrimary)
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colorBg),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = lbl,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = colorText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text("Density density score", fontSize = 9.sp, color = SlateTextMedium)
                        }
                    }
                }

                // Graph 1: Disease Prevalence Progression bars
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Disease Categories Distribution Proportions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val categoryTallies = remember(filteredAnalyticsCustomers, resolvedAnalyticsMeds) {
                            val map = mutableMapOf<String, Int>()
                            filteredAnalyticsCustomers.forEach { c ->
                                val cat = c["category"] as? String ?: "Analgesic"
                                map[cat] = (map[cat] ?: 0) + 1
                            }
                            map.toList().sortedByDescending { it.second }
                        }
                        
                        val totalTally = categoryTallies.sumOf { it.second }.coerceAtLeast(1)
                        
                        categoryTallies.forEach { (cat, count) ->
                            val dispName = cat.replace("Antimalarial", "Malaria Outbreak").replace("Antibiotic", "Typhoid/Bacterial").replace("Antihypertensive", "Hypertension").replace("Antidiabetic", "Diabetes Type II").replace("Bronchodilator", "Asthma").replace("Analgesic", "Arthritis / Pain")
                            val ratio = count.toFloat() / totalTally.toFloat()
                            val barColor = when (cat) {
                                "Antimalarial" -> TealPrimary
                                "Antibiotic" -> Color(0xFF03A9F4)
                                "Antihypertensive" -> com.example.ui.theme.WarningRed
                                "Antidiabetic" -> Color(0xFFFF9800)
                                "Bronchodilator" -> Color(0xFF9C27B0)
                                else -> Color(0xFF9E9E9E)
                            }
                            
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = dispName, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(text = "$count cases (${(ratio * 100).toInt()}%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(ratio)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(barColor)
                                    )
                                }
                            }
                        }
                    }
                }

                // Graph 2: Age Bracket Demographics Breakdown
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Vulnerable Age Brackets Distribution",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val brackets = remember(filteredAnalyticsCustomers) {
                            var ped = 0
                            var young = 0
                            var mat = 0
                            var ger = 0
                            filteredAnalyticsCustomers.forEach { c ->
                                val age = (c["age"] as? Int) ?: ((c["age"] as? Long)?.toInt() ?: 30)
                                when {
                                    age < 12 -> ped++
                                    age <= 25 -> young++
                                    age <= 55 -> mat++
                                    else -> ger++
                                }
                            }
                            listOf(
                                "Pediatric (Age < 12)" to ped,
                                "Adolescents (Age 12 - 25)" to young,
                                "Adults (Age 26 - 55)" to mat,
                                "Geriatrics (Age 55+)" to ger
                            )
                        }
                        val maxBracketVal = brackets.maxOf { it.second }.coerceAtLeast(1)
                        val totalBracketSum = brackets.sumOf { it.second }.coerceAtLeast(1)
                        
                        brackets.forEach { (label, count) ->
                            val proportionFraction = count.toFloat() / maxBracketVal.toFloat()
                            val dispFraction = count.toFloat() / totalBracketSum.toFloat()
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = label, 
                                    fontSize = 11.sp, 
                                    modifier = Modifier.width(135.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(proportionFraction)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(TealSecondary)
                                    ) {
                                        Text(
                                            text = "$count pt (${(dispFraction*100).toInt()}%)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TealTertiary,
                                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Row: Gender split proportions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Gender Splits Quotient",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val (females, males) = remember(filteredAnalyticsCustomers) {
                            val f = filteredAnalyticsCustomers.count { (it["gender"] as? String)?.equals("Female", ignoreCase = true) == true }
                            val m = filteredAnalyticsCustomers.size - f
                            f to m
                        }
                        val totalG = (females + males).coerceAtLeast(1)
                        val femaleRatio = females.toFloat() / totalG.toFloat()
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Females: $females (${(femaleRatio * 100).toInt()}%)", fontSize = 11.sp, color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                            Text("Males: $males (${((1f - femaleRatio) * 100).toInt()}%)", fontSize = 11.sp, color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF2196F3)) // blue background fills male
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(femaleRatio)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color(0xFFE91E63)) // pink bar fills female
                            )
                        }
                    }
                }

                // Section 3: Gemini Public Health Advisory Agent
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Careflux Public Health AI Director",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary
                            )
                        }
                        
                        Text(
                            text = "Leverage Google Gemini to evaluate localized disease patterns, project demand and direct immunization/sterilization campaigns back to local clinical nodes dynamically.",
                            fontSize = 11.5.sp,
                            color = SlateTextMedium,
                            lineHeight = 16.sp
                        )
                        
                        if (aiAdvisorReport != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "PUBLIC HEALTH INTERVENTION ADVISORY:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimary,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = aiAdvisorReport!!,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                        
                        Button(
                            onClick = {
                                isGeneratingReport = true
                            },
                            enabled = !isGeneratingReport,
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isGeneratingReport) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate LGA Action Advisory", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                // Gemini Generation Async Work
                LaunchedEffect(isGeneratingReport) {
                    if (isGeneratingReport) {
                        try {
                            val apiKey = viewModel.getApiKey()
                            val prompt = """
                                You are Careflux Public Health AI Director, an expert epidemiologist and administrative health officer.
                                Analyze the following real-time geo-demographic disease distribution from our network nodes in Lagos State:
                                - Selector Focus LGA: $selectedLgaFilter
                                - Focus Disease Profile: $selectedCategoryFilter
                                - Filtered Active Caseload: ${filteredAnalyticsCustomers.size} cases
                                - Total Network Active Database Size: ${resolvedAnalyticsCustomers.size} patients
                                - Selected Cohort Demographics:
                                  * Females: ${filteredAnalyticsCustomers.count { (it["gender"] as? String)?.equals("Female", ignoreCase = true) == true }}
                                  * Males: ${filteredAnalyticsCustomers.count { (it["gender"] as? String)?.equals("Male", ignoreCase = true) == true }}
                                  * Average Age: ${if (filteredAnalyticsCustomers.isNotEmpty()) filteredAnalyticsCustomers.map { ((it["age"] as? Int) ?: 30) }.average().toInt() else "N/A"} years
                                
                                Provide a concise, professional public health advisory report formatted in beautiful, clear Markdown. Use bullet points and appropriate titles. Avoid generic filler text.
                                Your report MUST include:
                                1. **Endemic Assessment**: Evaluate the threat level (Mild, Moderate, Endemic Alarm!) for $selectedLgaFilter regarding $selectedCategoryFilter and identify are there outbreaks.
                                2. **Immediate Administrative Action Plan**: Actionable steps for clinical pharmacist nodes in $selectedLgaFilter (such as standardizing dosage guidance, initiating patient-facing counseling alerts via SMS/WhatsApp).
                                3. **Supply-Chain Optimization**: Recommendations for inventories (e.g., stockpile weeks, diagnostic kit levels).
                                4. **Public Health Campaign Strategy**: Suggested informational topics or targeted messaging for local community leaders in $selectedLgaFilter.
                            """.trimIndent()
                            
                            if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
                                aiAdvisorReport = """
                                    ### Public Health Advisory: $selectedLgaFilter Focus
                                    
                                    **Offline Simulation Report** (Gemini connection is ready for secure deployment):
                                    1. **Endemic Assessment**: Elevated outbreak risks for **$selectedCategoryFilter** have been isolated in **$selectedLgaFilter**. Indicators detect localized household clusters.
                                    2. **Tactical Action Plan**: Instruct all Node pharmacies in $selectedLgaFilter to counsel cohorts on strict therapeutic adherence and verify patient consent handshake records.
                                    3. **Inventory Reallocation**: Augment current inventories of associated medication classes by **40%** to buffer against current supply-chain cycles.
                                    4. **Campaign Strategy**: Initiate educational WhatsApp bulletins regarding vectors, lifestyle modifications, and early indicator screenings.
                                """.trimIndent()
                                isGeneratingReport = false
                            } else {
                                val request = com.example.data.GenerateContentRequest(
                                    contents = listOf(com.example.data.Content(parts = listOf(com.example.data.Part(text = prompt))))
                                )
                                val rawResponse = com.example.data.RetrofitClient.service.generateContent(apiKey, request)
                                val outText = rawResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No advisory generated."
                                aiAdvisorReport = outText
                                isGeneratingReport = false
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            aiAdvisorReport = "Error generating public health plan: ${e.localizedMessage}"
                            isGeneratingReport = false
                        }
                    }
                }
            }
        } else if (selectedSubTab == 2) {
            // --- Key Requests Screen (Interactive Management and Approval) ---
            var requestToApprove by remember { mutableStateOf<Map<String, Any>?>(null) }
            var requestToReject by remember { mutableStateOf<Map<String, Any>?>(null) }
            var rejectionReason by remember { mutableStateOf("") }
            
            // Approval form states
            var formGeminiKey by remember { mutableStateOf("") }
            var formTermiiKey by remember { mutableStateOf("") }
            var formSenderId by remember { mutableStateOf("N-Alert") }

            if (requestToApprove != null) {
                val req = requestToApprove!!
                val phName = req["pharmacyName"] as? String ?: "Cooperative Node"
                val devId = req["deviceId"] as? String ?: ""
                
                AlertDialog(
                    onDismissRequest = { requestToApprove = null },
                    title = { Text("Approve & Provision API Suite", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Assign dedicated keys for $phName:", style = MaterialTheme.typography.bodyMedium)
                            
                            OutlinedTextField(
                                value = formGeminiKey,
                                onValueChange = { formGeminiKey = it },
                                label = { Text("Gemini API Key") },
                                placeholder = { Text("AIzaSy...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            
                            OutlinedTextField(
                                value = formTermiiKey,
                                onValueChange = { formTermiiKey = it },
                                label = { Text("Termii SMS API Key") },
                                placeholder = { Text("At_...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            
                            OutlinedTextField(
                                value = formSenderId,
                                onValueChange = { formSenderId = it },
                                label = { Text("Termii Sender ID") },
                                placeholder = { Text("e.g. N-Alert") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.approveKeyRequest(devId, formGeminiKey, formTermiiKey, formSenderId)
                                requestToApprove = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Approve & Sync Keys", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { requestToApprove = null }) { Text("Cancel") }
                    }
                )
            }

            if (requestToReject != null) {
                val req = requestToReject!!
                val phName = req["pharmacyName"] as? String ?: "Cooperative Node"
                val devId = req["deviceId"] as? String ?: ""
                
                AlertDialog(
                    onDismissRequest = { requestToReject = null },
                    title = { Text("Reject Key Request", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Specify decline rationale for $phName:", style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(
                                value = rejectionReason,
                                onValueChange = { rejectionReason = it },
                                placeholder = { Text("e.g., node license unverified...") },
                                label = { Text("Reason for decline") },
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                singleLine = false,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.rejectKeyRequest(devId, rejectionReason)
                                requestToReject = null
                                rejectionReason = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Decline Request", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { requestToReject = null }) { Text("Cancel") }
                    }
                )
            }

            if (keyRequests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                        Text(
                            text = "No key request setups found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(keyRequests.sortedByDescending { it["requestedAt"] as? Long ?: 0L }) { req ->
                        val phName = req["pharmacyName"] as? String ?: "Unverified Node"
                        val model = req["deviceModel"] as? String ?: "Network Node"
                        val lga = req["lga"] as? String ?: ""
                        val state = req["state"] as? String ?: ""
                        val status = req["status"] as? String ?: "PENDING"
                        val devId = req["deviceId"] as? String ?: ""
                        val requestedAt = req["requestedAt"] as? Long ?: 0L
                        val dateFormatted = java.text.SimpleDateFormat("MMM dd, yyyy - HH:mm", java.util.Locale.getDefault()).format(java.util.Date(requestedAt))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(phName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                        Text("Node ID: $devId", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    
                                    val statusColor = when (status) {
                                        "APPROVED" -> Color(0xFF4CAF50)
                                        "REJECTED" -> MaterialTheme.colorScheme.error
                                        else -> Color(0xFFFF9800)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(statusColor.copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(status.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = statusColor)
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Regional Scope: $lga, $state", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Submitted: $dateFormatted", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                if (status == "PENDING") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                formGeminiKey = ""
                                                formTermiiKey = ""
                                                formSenderId = "N-Alert"
                                                requestToApprove = req
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Approve & Provision", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        
                                        OutlinedButton(
                                            onClick = {
                                                rejectionReason = ""
                                                requestToReject = req
                                            },
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1.0f)
                                        ) {
                                            Text("Decline", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                } else if (status == "APPROVED") {
                                    val preGemini = req["geminiKey"] as? String ?: ""
                                    val preTermii = req["termiiApiKey"] as? String ?: ""
                                    val preSender = req["termiiSenderId"] as? String ?: "N-Alert"
                                    
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("Provisioned Keys Profile:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                        Text("Gemini: ${if (preGemini.length > 10) "${preGemini.take(6)}...${preGemini.takeLast(4)}" else "Custom Active"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Termii SMS Key: ${if (preTermii.length > 10) "${preTermii.take(6)}...${preTermii.takeLast(4)}" else "Custom Active"} (Sender: $preSender)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {

            if (auditLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No administrative modifications logged yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(auditLogs.sortedByDescending { it.timestamp }) { log ->
                        val isCritical = log.actionPerformed.contains("SUSPEND") || log.actionPerformed.contains("DISABLE")
                        val actionColor = if (isCritical) com.example.ui.theme.WarningRed else TealPrimary
                        val originColorContainer = if (isCritical) com.example.ui.theme.WarningRedContainerSoft else TealPrimary.copy(alpha = 0.08f)

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = actionColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        
                                        // Colored Action tag badge
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(originColorContainer)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = log.actionPerformed,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = actionColor,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }
                                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                                    Text(
                                        text = dateStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Text(
                                    text = "Affiliated Node: ${log.affectedNodeModel} (${log.affectedNodeId})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Admin Signature: ${log.adminName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Information box for compliance reason
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp).padding(top = 1.dp)
                                    )
                                    Text(
                                        text = "Reason: ${log.reason.ifBlank { "No explicit rationale assigned." }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogue confirmation overlay
    if (nodeToSuspend != null) {
        val targetNode = nodeToSuspend!!
        val model = targetNode["deviceModel"] as? String ?: "Unknown"
        val nId = targetNode["id"] as? String ?: ""

        AlertDialog(
            onDismissRequest = { nodeToSuspend = null },
            title = {
                Text(
                    text = if (actionType == "SUSPEND") "Suspend Node Access" else "Reactivate Node Access",
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Are you sure you want to alter network compliance and authorization levels for node '$model'?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = suspendReason,
                        onValueChange = { suspendReason = it },
                        placeholder = { Text("Reason (e.g. outstanding audit, unverified pharmacist login, subscription overdue...)") },
                        label = { Text("Compliance Reason / Rationale") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp),
                        singleLine = false,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isSuspendVal = (actionType == "SUSPEND")
                        localIsSuspendedOverrides = localIsSuspendedOverrides + (nId to isSuspendVal)
                        
                        if (isSuspendVal) {
                            viewModel.updateNodeStatus(nId, "SUSPEND", suspendReason)
                        } else {
                            viewModel.updateNodeStatus(nId, "REACTIVATE", suspendReason)
                        }
                        nodeToSuspend = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (actionType == "SUSPEND") MaterialTheme.colorScheme.error else TealPrimary,
                        contentColor = if (actionType == "SUSPEND") MaterialTheme.colorScheme.onError else Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm Action", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { nodeToSuspend = null }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
