package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TealTertiary
import com.example.ui.theme.AppThemeManager
import androidx.compose.foundation.BorderStroke
import com.example.data.OperationTask

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BranchTeamTab(
    viewModel: com.example.ui.PharmacyViewModel,
    initialSubTab: String? = null,
    highlightTaskId: Long? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isProfileLoading by viewModel.isProfileLoading.collectAsStateWithLifecycle()
    val branchId by viewModel.currentPharmacistBranchId.collectAsStateWithLifecycle()
    val branchName by viewModel.currentPharmacistBranchName.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentPharmacistRole.collectAsStateWithLifecycle()
    val currentName by viewModel.currentPharmacistName.collectAsStateWithLifecycle()
    val currentPhone by viewModel.currentPharmacistPhone.collectAsStateWithLifecycle()
    val staffList by viewModel.branchStaffList.collectAsStateWithLifecycle()
    val operationTasks by viewModel.operationTasks.collectAsStateWithLifecycle()
    val claimingTaskIds by viewModel.claimingTaskIds.collectAsStateWithLifecycle()
    val activeHighlightTaskId by viewModel.activeHighlightTaskId.collectAsStateWithLifecycle()
    val reconciledRatio by viewModel.reconciled14DaysRatio.collectAsStateWithLifecycle()
    val unreconciledCount by viewModel.unreconciled14DaysCount.collectAsStateWithLifecycle()
    val totalInventory by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val allBranches by viewModel.allBranches.collectAsStateWithLifecycle()

    val effectiveHighlightTaskId = activeHighlightTaskId ?: highlightTaskId

    var showRoleDialogForStaff by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showSwitchBranchDialog by remember { mutableStateOf(false) }
    var taskForComplianceVerification by remember { mutableStateOf<OperationTask?>(null) }
    var taskForManagerApproval by remember { mutableStateOf<OperationTask?>(null) }
    var showColdChainDialog by remember { mutableStateOf(false) }
    var lastLoggedFridgeTemp by remember { mutableFloatStateOf(4.2f) }
    var lastLoggedFridgeUnit by remember { mutableStateOf("Main Refrigerator") }
    var lastLoggedTimeMs by remember { mutableLongStateOf(System.currentTimeMillis() - 2L * 3600L * 1000L) }
    var lastLoggedBy by remember { mutableStateOf("Staff Pharmacist") }
    var selectedTab by remember { mutableStateOf(if (initialSubTab == "ops_task_board" || (effectiveHighlightTaskId != null && effectiveHighlightTaskId > 0L)) 1 else 0) } // 0 = Staff Roster, 1 = Ops Delegation Center
    var opsTaskFilter by remember { mutableStateOf(2) } // 0 = All, 1 = My Tasks, 2 = Unassigned Pool, 3 = Resolved
    val sharedPrefs = remember { context.getSharedPreferences("pharmacy_app_prefs", Context.MODE_PRIVATE) }
    // Sort Modes: 0 = "Latest First" (createdAt DESC), 1 = "Urgency" (High > Med > Low, then createdAt DESC), 2 = "Due / SLA"
    var opsTaskSortMode by remember { mutableIntStateOf(sharedPrefs.getInt("ops_task_sort_mode", 0)) }
    val activeStaffName = currentName?.ifBlank { "Staff Pharmacist" } ?: "Staff Pharmacist"

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    LaunchedEffect(initialSubTab, effectiveHighlightTaskId) {
        if (initialSubTab == "ops_task_board" || (effectiveHighlightTaskId != null && effectiveHighlightTaskId > 0L)) {
            selectedTab = 1
            if (effectiveHighlightTaskId != null && effectiveHighlightTaskId > 0L) {
                opsTaskFilter = 0
            } else {
                opsTaskFilter = 2
            }
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Precision Deep Linking & Zero-Lag Scroll to target task
    LaunchedEffect(effectiveHighlightTaskId, selectedTab, operationTasks) {
        if (effectiveHighlightTaskId != null && effectiveHighlightTaskId > 0L) {
            selectedTab = 1 // Switch to Ops Task Board
            opsTaskFilter = 0 // Ensure "All Tasks" filter is active so target task is visible
            
            val filteredTasks = operationTasks.filter { task ->
                val taskAssignee = task.assignedToName?.trim()
                val isUnassigned = taskAssignee.isNullOrBlank() || taskAssignee.equals("All Staff", ignoreCase = true)
                when (opsTaskFilter) {
                    1 -> !task.isCompleted && taskAssignee?.equals(activeStaffName, ignoreCase = true) == true
                    2 -> !task.isCompleted && isUnassigned
                    3 -> task.isCompleted
                    else -> true
                }
            }.sortedByDescending { it.createdAt }

            val targetIndex = filteredTasks.indexOfFirst { it.id.toLong() == effectiveHighlightTaskId || it.id == effectiveHighlightTaskId.toInt() }
            if (targetIndex != -1) {
                val isManagerRole = currentRole == "Branch Manager" || currentRole == "Admin" || viewModel.isCurrentUserAdmin()
                val headerOffset = if (isManagerRole) 5 else 4
                val targetItemIndex = (headerOffset + targetIndex).coerceAtLeast(0)
                
                // Zero-lag immediate positioning followed by fluid animation
                try {
                    listState.scrollToItem(targetItemIndex)
                } catch (e: Exception) {}
                
                kotlinx.coroutines.delay(100)
                try {
                    listState.animateScrollToItem(targetItemIndex)
                } catch (e: Exception) {}
            }
        }
    }

    // Smooth loading state to avoid flickering before profile/branch sync completes
    if (isProfileLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = TealPrimary,
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 3.dp
                )
                Text(
                    text = "Verifying Terminal Node Credentials...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Synchronizing branch profile state with Careflux network",
                    fontSize = 11.sp,
                    color = AppThemeManager.slateTextMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else if (branchId.isNullOrBlank()) {
        // --- Branch Enrollment & INITIALIZATION Wizard ---
        var enrollmentTab by remember { mutableStateOf(0) } // 0 = Join Exist, 1 = Register New

        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Branch Enrollment Protocol",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Initialize or enroll into an authorized Careflux terminal node",
                                fontSize = 11.sp,
                                color = AppThemeManager.slateTextMedium
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppThemeManager.slateBackgroundLight
                    )
                )
            },
            containerColor = AppThemeManager.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(top = 16.dp, bottom = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {


                // Modern Choice segment selector
                TabRow(
                    selectedTabIndex = enrollmentTab,
                    containerColor = AppThemeManager.secondary,
                    contentColor = TealPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, AppThemeManager.slateBorderLight, RoundedCornerShape(12.dp))
                ) {
                    val isTab0 = enrollmentTab == 0
                    Tab(
                        selected = isTab0,
                        onClick = { enrollmentTab = 0 },
                        text = {
                            Text(
                                text = "Join Branch via Code",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTab0) TealPrimary else AppThemeManager.slateTextMedium
                            )
                        }
                    )
                    val isTab1 = enrollmentTab == 1
                    Tab(
                        selected = isTab1,
                        onClick = { enrollmentTab = 1 },
                        text = {
                            Text(
                                text = "Register New Branch",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTab1) TealPrimary else AppThemeManager.slateTextMedium
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (enrollmentTab == 0) {
                    // TAB 0: JOIN BRANCH VIA CODE
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = AppThemeManager.surface
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, AppThemeManager.slateBorderLight)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "ENTER AUTHORIZED NODE CODE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary
                            )

                            var inputCode by remember { mutableStateOf("") }
                            var isJoining by remember { mutableStateOf(false) }

                            OutlinedTextField(
                                value = inputCode,
                                onValueChange = { inputCode = it },
                                placeholder = { Text("e.g. CF-123456", color = AppThemeManager.slateTextMedium) },
                                leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = TealPrimary) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TealPrimary,
                                    unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder,
                                    focusedLabelColor = TealPrimary,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            Text(
                                text = "Ask your branch manager to share the authorized code (CF-XXXXXX) listed on their workspace control deck.",
                                fontSize = 11.sp,
                                color = AppThemeManager.slateTextMedium
                            )

                            val btnTextColor = if (AppThemeManager.isDark) Color(0xFF0B0F19) else Color.White
                            Button(
                                onClick = {
                                    if (inputCode.trim().isBlank()) {
                                        Toast.makeText(context, "Please enter a branch code.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isJoining = true
                                    viewModel.joinBranch(inputCode) { success, msg ->
                                        isJoining = false
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                enabled = !isJoining,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealPrimary,
                                    contentColor = btnTextColor
                                )
                            ) {
                                if (isJoining) {
                                    CircularProgressIndicator(color = btnTextColor, modifier = Modifier.size(24.dp))
                                } else {
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = btnTextColor)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Authorize & Join Node", color = btnTextColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // TAB 1: REGISTER NEW BRANCH
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = AppThemeManager.surface
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, AppThemeManager.slateBorderLight)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "INITIALIZE NEW CORPORATE NODE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary
                            )

                            var storeName by remember { mutableStateOf("") }
                            var storeLga by remember { mutableStateOf("") }
                            var storeState by remember { mutableStateOf("") }
                            var isRegistering by remember { mutableStateOf(false) }

                            OutlinedTextField(
                                value = storeName,
                                onValueChange = { storeName = it },
                                label = { Text("Pharmacy / Store Name") },
                                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, tint = TealPrimary) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TealPrimary,
                                    unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder,
                                    focusedLabelColor = TealPrimary,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = storeLga,
                                    onValueChange = { storeLga = it },
                                    label = { Text("Local Gov Area (LGA)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TealPrimary,
                                        unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder,
                                        focusedLabelColor = TealPrimary,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )

                                OutlinedTextField(
                                    value = storeState,
                                    onValueChange = { storeState = it },
                                    label = { Text("State") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TealPrimary,
                                        unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder,
                                        focusedLabelColor = TealPrimary,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            Text(
                                text = "By initializing this terminal, you'll be assigned automatically as the designated Branch Manager for this node, allowing you to configure local member accounts, security levels, and manage staff operations.",
                                fontSize = 10.sp,
                                color = AppThemeManager.slateTextMedium
                            )

                            val btnTextColor = if (AppThemeManager.isDark) Color(0xFF0B0F19) else Color.White
                            Button(
                                onClick = {
                                    if (storeName.trim().isBlank()) {
                                        Toast.makeText(context, "Please enter a valid branch/store name.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isRegistering = true
                                    viewModel.registerBranch(storeName, storeLga, storeState) { success, msg ->
                                        isRegistering = false
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                enabled = !isRegistering,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealPrimary,
                                    contentColor = btnTextColor
                                )
                            ) {
                                if (isRegistering) {
                                    CircularProgressIndicator(color = btnTextColor, modifier = Modifier.size(24.dp))
                                } else {
                                    Icon(Icons.Default.AddHome, contentDescription = null, tint = btnTextColor)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Register & Activate Node", color = btnTextColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // --- LOGGED IN USER ALREADY BELONGS TO ACTIVE BRANCH ---
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Branch Control Deck",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Manage staff synchronization, role configurations, and operational task delegation",
                                fontSize = 10.sp,
                                color = AppThemeManager.slateTextMedium
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppThemeManager.slateBackgroundLight
                    )
                )
            },
            containerColor = AppThemeManager.background
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Space for Top Bar offset
                item { Spacer(modifier = Modifier.height(4.dp)) }

                // --- 1. Branch Details Hero Card ---
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = AppThemeManager.surface
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, AppThemeManager.slateBorderLight)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: Branch details + Authorization Code Pill
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(TealPrimary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Storefront,
                                            contentDescription = "Branch",
                                            tint = TealPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = branchName ?: "Standard Pharmacy Branch",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Compact Tap-to-Copy Code Pill
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppThemeManager.secondary)
                                        .border(1.dp, TealPrimary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            val clip = ClipData.newPlainText("Careflux Branch Code", branchId ?: "")
                                            clipboardManager.setPrimaryClip(clip)
                                            Toast.makeText(context, "Branch Code copied!", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = branchId ?: "N/A",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        letterSpacing = 0.5.sp
                                    )
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = TealPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            // Row 2: Logged-in Operator Info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(TealPrimary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = TealPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = currentName ?: "Active Staff",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(TealPrimary.copy(alpha = 0.15f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = currentRole ?: "Pharmacist",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimary,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Row 3: Action Buttons (Switch Branch & Edit Profile)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { showSwitchBranchDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = TealPrimary.copy(alpha = 0.08f),
                                        contentColor = TealPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Switch Branch",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Switch Branch",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showEditProfileDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = TealPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Profile",
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Edit Profile",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 2. Custom Modern Segment Tabs ( Roster vs Ops Tasks ) ---
                item {
                    val isSubTab0 = selectedTab == 0
                    val isSubTab1 = selectedTab == 1
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = AppThemeManager.secondary,
                        contentColor = TealPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, AppThemeManager.slateBorderLight, RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = isSubTab0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.People,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSubTab0) TealPrimary else AppThemeManager.slateTextMedium
                                    )
                                    Text(
                                        text = "Roster & Roles",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSubTab0) TealPrimary else AppThemeManager.slateTextMedium
                                    )
                                }
                            }
                        )
                        Tab(
                            selected = isSubTab1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Assignment,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSubTab1) TealPrimary else AppThemeManager.slateTextMedium
                                    )
                                    Text(
                                        text = "Ops Task Board",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSubTab1) TealPrimary else AppThemeManager.slateTextMedium
                                    )
                                }
                            }
                        )
                    }
                }

                // --- TAB CONTENT ---
                if (selectedTab == 0) {
                    // --- SUB-TAB 0: STAFF ROSTER & MANAGEMENT ---
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Synchronized Colleagues (${staffList.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(TealPrimary.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(TealPrimary)
                                )
                                Text(
                                    text = "Live",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimary
                                )
                            }
                        }
                    }

                    if (staffList.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AppThemeManager.secondary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.PeopleOutline, contentDescription = null, tint = AppThemeManager.slateTextMedium, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Polling registered colleagues...", fontSize = 12.sp, color = AppThemeManager.slateTextMedium)
                                }
                            }
                        }
                    } else {
                        items(staffList) { staff ->
                            val uid = staff["uid"]?.toString() ?: ""
                            val dispName = staff["displayName"]?.toString() ?: "Operational Pharmacist"
                            val email = staff["email"]?.toString() ?: "staff@careflux.org"
                            val role = staff["role"]?.toString() ?: "Pharmacist"
                            val isApproved = staff["isApproved"] as? Boolean ?: true
                            val deviceModel = staff["deviceModel"]?.toString() ?: "Workspace Node"

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isApproved) AppThemeManager.surface else AppThemeManager.warningRedContainerSoft
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isApproved) AppThemeManager.slateBorderLight else AppThemeManager.warningRed.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isApproved) MaterialTheme.colorScheme.primaryContainer 
                                                else AppThemeManager.warningRedContainer
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (role == "Branch Manager") Icons.Default.Shield else Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (isApproved) MaterialTheme.colorScheme.onPrimaryContainer else AppThemeManager.warningRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(dispName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(TealSecondary)
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(role, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TealTertiary)
                                            }
                                        }
                                        Text(email, fontSize = 11.sp, color = AppThemeManager.slateTextMedium)
                                        Text("Terminal: $deviceModel", fontSize = 9.sp, color = AppThemeManager.slateTextMedium.copy(alpha = 0.8f))
                                    }

                                    // Manager exclusive configuration
                                    if ((currentRole == "Branch Manager" || viewModel.isCurrentUserAdmin()) && role != "Branch Manager") {
                                        IconButton(onClick = { showRoleDialogForStaff = staff }) {
                                            Icon(Icons.Default.Settings, contentDescription = "Edit permissions", tint = TealPrimary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // --- SUB-TAB 1: OPERATIONAL TASKS & DELEGATION BOARD ---
                    val isManager = currentRole == "Branch Manager" || viewModel.isCurrentUserAdmin()

                    item {
                        // Task Summary Metrics Row & Dispatch Control
                        val completedCount = operationTasks.count { it.isCompleted }
                        val myTasksCount = operationTasks.count { !it.isCompleted && (it.assignedToName?.trim() == activeStaffName) }
                        val unassignedCount = operationTasks.count { !it.isCompleted && (it.assignedToName.isNullOrBlank() || it.assignedToName == "All Staff") }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppThemeManager.surface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            border = BorderStroke(1.dp, AppThemeManager.slateBorderLight)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f, fill = false),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Verified, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(14.dp))
                                        Text("OPS VERIFICATION BOARD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            viewModel.dispatchAutomatedVerificationTasks { count ->
                                                val msg = if (count > 0) "Dispatched $count automated shelf verification checks!" else "All inventory & expiry verification checks up-to-date."
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Black)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("⚡ Dispatch Checks", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("TOTAL", fontSize = 8.sp, color = AppThemeManager.slateTextMedium, fontWeight = FontWeight.Bold)
                                        Text("${operationTasks.size}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(20.dp).background(AppThemeManager.slateBorderLight))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("MY TASKS", fontSize = 8.sp, color = TealPrimary, fontWeight = FontWeight.Bold)
                                        Text("$myTasksCount", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TealPrimary)
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(20.dp).background(AppThemeManager.slateBorderLight))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("UNASSIGNED POOL", fontSize = 8.sp, color = AppThemeManager.pendingOrange, fontWeight = FontWeight.Bold)
                                        Text("$unassignedCount", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = AppThemeManager.pendingOrange)
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(20.dp).background(AppThemeManager.slateBorderLight))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("RESOLVED", fontSize = 8.sp, color = AppThemeManager.okGreen, fontWeight = FontWeight.Bold)
                                        Text("$completedCount", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = AppThemeManager.okGreen)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Compact Segmented Filter Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val filterTabs = listOf(
                                        "All Tasks (${operationTasks.size})",
                                        "My Tasks ($myTasksCount)",
                                        "Unassigned Pool ($unassignedCount)",
                                        "Resolved ($completedCount)"
                                    )
                                    filterTabs.forEachIndexed { index, label ->
                                        val selected = opsTaskFilter == index
                                        FilterChip(
                                            selected = selected,
                                            onClick = { opsTaskFilter = index },
                                            label = { Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = TealPrimary,
                                                selectedLabelColor = Color.Black
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = selected,
                                                borderColor = AppThemeManager.slateBorderLight
                                            ),
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Minimalist & Seamless Sort Mode Bar (Option C: Latest First by default)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(end = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sort,
                                            contentDescription = "Sort order",
                                            tint = AppThemeManager.slateTextMedium,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            "Sort:",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AppThemeManager.slateTextMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    val sortOptions = listOf(
                                        Triple(0, "⏱️ Latest First", "Newest items on top"),
                                        Triple(1, "🚨 Urgency Priority", "High > Medium > Low"),
                                        Triple(2, "⏳ Near Due / SLA", "Action-required first")
                                    )

                                    sortOptions.forEach { (modeId, label, _) ->
                                        val isSelected = opsTaskSortMode == modeId
                                        Surface(
                                            onClick = {
                                                opsTaskSortMode = modeId
                                                sharedPrefs.edit().putInt("ops_task_sort_mode", modeId).apply()
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) TealPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) TealPrimary else AppThemeManager.slateBorderLight.copy(alpha = 0.7f)
                                            )
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(TealPrimary)
                                                    )
                                                }
                                                Text(
                                                    text = label,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                    color = if (isSelected) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // REFRIGERATED STORAGE & QUALITY CONTROL DASHBOARD CARD
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppThemeManager.surface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            border = BorderStroke(1.dp, AppThemeManager.slateBorderLight)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AcUnit,
                                            contentDescription = null,
                                            tint = TealPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "REFRIGERATED STORAGE & COLD-CHAIN",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    val isTempSafe = lastLoggedFridgeTemp in 2.0f..8.0f
                                    val statusColor = if (isTempSafe) AppThemeManager.okGreen else AppThemeManager.warningRed
                                    Surface(
                                        color = statusColor.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(statusColor)
                                            )
                                            Text(
                                                text = "${String.format("%.1f", lastLoggedFridgeTemp)}°C • ${if (isTempSafe) "SAFE" else "EXCURSION"}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = statusColor,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Storage Equipment Quick Status Grid
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Card 1: Main Refrigerator
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = TealPrimary.copy(alpha = 0.05f)),
                                        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Main Fridge", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Text("${String.format("%.1f", lastLoggedFridgeTemp)}°C", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = TealPrimary)
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Target: 2.0°C – 8.0°C", fontSize = 8.sp, color = AppThemeManager.slateTextMedium)
                                            Text("Insulin • Vaccines • Serums", fontSize = 7.5.sp, color = AppThemeManager.slateTextMedium)
                                        }
                                    }

                                    // Card 2: Vaccine Storage Unit #2
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = AppThemeManager.okGreen.copy(alpha = 0.05f)),
                                        border = BorderStroke(1.dp, AppThemeManager.okGreen.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Vaccine Vault", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Text("3.8°C", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = AppThemeManager.okGreen)
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Target: 2.0°C – 8.0°C", fontSize = 8.sp, color = AppThemeManager.slateTextMedium)
                                            Text("Childhood Vaccines", fontSize = 7.5.sp, color = AppThemeManager.slateTextMedium)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Action Row & Last Logged Timestamp with spacious margins
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val timeAgo = android.text.format.DateUtils.getRelativeTimeSpanString(
                                        lastLoggedTimeMs,
                                        System.currentTimeMillis(),
                                        android.text.format.DateUtils.MINUTE_IN_MILLIS
                                    )
                                    Text(
                                        text = "Last audit: $timeAgo by $lastLoggedBy",
                                        fontSize = 8.5.sp,
                                        color = AppThemeManager.slateTextMedium,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Button(
                                        onClick = { showColdChainDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = TealPrimary,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Thermostat,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "1-Tap Temp Log",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isManager) {
                        // Manager's Live Task Assignment Form
                        item {
                            var showCreatorForm by remember { mutableStateOf(false) }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = TealPrimary.copy(alpha = 0.08f)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showCreatorForm = !showCreatorForm },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.AddCircle, contentDescription = null, tint = TealPrimary)
                                            Text(
                                                text = "Delegate Operational Task",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Icon(
                                            imageVector = if (showCreatorForm) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = TealPrimary
                                        )
                                    }

                                    if (showCreatorForm) {
                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Task Category Chooser
                                        var selectedCategory by remember { mutableStateOf("Patient Engagement") }
                                        var selectedTemplate by remember { mutableStateOf("Select Task Theme Action") }
                                        var selectedAssigneeName by remember { mutableStateOf("All Staff") }
                                        var selectedAssigneeUid by remember { mutableStateOf("") }
                                        var customInstructions by remember { mutableStateOf("") }
                                        var urgencyLevel by remember { mutableStateOf("Medium") }

                                        var catDropdownExp by remember { mutableStateOf(false) }
                                        var templateDropdownExp by remember { mutableStateOf(false) }
                                        var staffDropdownExp by remember { mutableStateOf(false) }
                                        var urgencyDropdownExp by remember { mutableStateOf(false) }

                                        val categoriesAndTemplates = mapOf(
                                            "Patient Engagement" to listOf(
                                                "Register New Patient",
                                                "Send Welcome Message",
                                                "48-Hour Follow-Up",
                                                "7-Day Follow-Up",
                                                "Confirm Medication Adherence",
                                                "Contact Inactive Patient",
                                                "Confirm Symptom Improvement"
                                            ),
                                            "Revenue & Retention" to listOf(
                                                "Send Refill Reminder",
                                                "Recover Missed Refill Patient",
                                                "Product Availability Follow-Up"
                                            ),
                                            "Clinical Intelligence" to listOf(
                                                "Record BP Reading",
                                                "Record Blood Sugar Reading",
                                                "Verify Prescription Completion",
                                                "Record Disease Condition",
                                                "Upload Patient Outcome",
                                                "Report Adverse Drug Reaction"
                                            ),
                                            "Growth" to listOf(
                                                "Register Referral",
                                                "Collect Testimonial",
                                                "Community Enrollment",
                                                "Collect Patient Feedback"
                                            )
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            // Category Picker
                                            Box {
                                                OutlinedButton(
                                                    onClick = { catDropdownExp = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.onSurface
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Category: $selectedCategory", fontSize = 12.sp)
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TealPrimary)
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = catDropdownExp,
                                                    onDismissRequest = { catDropdownExp = false }
                                                ) {
                                                    categoriesAndTemplates.keys.forEach { cat ->
                                                        DropdownMenuItem(
                                                            text = { Text(cat, fontSize = 13.sp) },
                                                            onClick = {
                                                                selectedCategory = cat
                                                                selectedTemplate = "Select Task Theme Action"
                                                                catDropdownExp = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            // Task Action Template Picker
                                            Box {
                                                OutlinedButton(
                                                    onClick = { templateDropdownExp = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.onSurface
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Action: $selectedTemplate", fontSize = 12.sp)
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TealPrimary)
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = templateDropdownExp,
                                                    onDismissRequest = { templateDropdownExp = false }
                                                ) {
                                                    categoriesAndTemplates[selectedCategory]?.forEach { action ->
                                                        DropdownMenuItem(
                                                            text = { Text(action, fontSize = 13.sp) },
                                                            onClick = {
                                                                selectedTemplate = action
                                                                templateDropdownExp = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            // Assignee Staff Member Selector
                                            Box {
                                                OutlinedButton(
                                                    onClick = { staffDropdownExp = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.onSurface
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Assign To: $selectedAssigneeName", fontSize = 12.sp)
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TealPrimary)
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = staffDropdownExp,
                                                    onDismissRequest = { staffDropdownExp = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("All Staff / Broadcast", fontSize = 13.sp) },
                                                        onClick = {
                                                            selectedAssigneeName = "All Staff"
                                                            selectedAssigneeUid = ""
                                                            staffDropdownExp = false
                                                        }
                                                    )
                                                    staffList.forEach { s ->
                                                        val nameStr = s["displayName"]?.toString() ?: "Staff Pharmacist"
                                                        val uIdStr = s["uid"]?.toString() ?: ""
                                                        val roleStr = s["role"]?.toString() ?: "Staff"
                                                        DropdownMenuItem(
                                                            text = { Text("$nameStr ($roleStr)", fontSize = 13.sp) },
                                                            onClick = {
                                                                selectedAssigneeName = nameStr
                                                                selectedAssigneeUid = uIdStr
                                                                staffDropdownExp = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            // Urgency Level
                                            Box {
                                                OutlinedButton(
                                                    onClick = { urgencyDropdownExp = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.onSurface
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Urgency: $urgencyLevel", fontSize = 12.sp)
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TealPrimary)
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = urgencyDropdownExp,
                                                    onDismissRequest = { urgencyDropdownExp = false }
                                                ) {
                                                    listOf("High", "Medium", "Low").forEach { urg ->
                                                        DropdownMenuItem(
                                                            text = { Text(urg, fontSize = 13.sp) },
                                                            onClick = {
                                                                urgencyLevel = urg
                                                                urgencyDropdownExp = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            // Description input
                                            OutlinedTextField(
                                                value = customInstructions,
                                                onValueChange = { customInstructions = it },
                                                label = { Text("Instructions / Comments") },
                                                placeholder = { Text("Special requirements or context details...", color = AppThemeManager.slateTextMedium) },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 2,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = TealPrimary,
                                                    unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder,
                                                    focusedLabelColor = TealPrimary,
                                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                    unfocusedLabelColor = AppThemeManager.slateTextMedium
                                                )
                                            )

                                            Button(
                                                onClick = {
                                                    if (selectedTemplate == "Select Task Theme Action") {
                                                        Toast.makeText(context, "Please select an operational action template.", Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }
                                                    // Format payload string that is retro-compatible with Room schema
                                                    val formattedDesc = "Assignee: $selectedAssigneeName | instructions: ${customInstructions.trim().ifBlank { "Standard operational protocol deployment." }}"
                                                    viewModel.addOperationTask(
                                                        title = selectedTemplate,
                                                        description = formattedDesc,
                                                        urgency = urgencyLevel,
                                                        category = selectedCategory,
                                                        assignedToName = selectedAssigneeName,
                                                        assignedToUid = selectedAssigneeUid
                                                    )
                                                    Toast.makeText(context, "Task assigned in real-time!", Toast.LENGTH_SHORT).show()
                                                    // reset
                                                    selectedTemplate = "Select Task Theme Action"
                                                    customInstructions = ""
                                                    showCreatorForm = false
                                                },
                                                modifier = Modifier.align(Alignment.End),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = TealPrimary,
                                                    contentColor = Color.Black
                                                )
                                            ) {
                                                Icon(Icons.Default.SendToMobile, contentDescription = null, tint = Color.Black)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Dispatch Task", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Rolling 14-Day Cycle Count Health Card
                    item {
                        val ratioPct = (reconciledRatio * 100).toInt()
                        val ratioFormatted = if (reconciledRatio > 0f && (reconciledRatio * 100f) < 1.0f) {
                            String.format(java.util.Locale.US, "%.1f%%", reconciledRatio * 100f)
                        } else {
                            "$ratioPct%"
                        }
                        val isTargetMet = reconciledRatio >= 0.80f
                        val progressColor = if (isTargetMet) AppThemeManager.okGreen else Color(0xFFF59E0B)

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (AppThemeManager.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(progressColor.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isTargetMet) Icons.Default.CheckCircle else Icons.Default.Assessment,
                                                contentDescription = null,
                                                tint = progressColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Rolling 14-Day Cycle Count",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isTargetMet) "Target Met (≥80% Reconciled)" else "Action Needed (<80% Target)",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = progressColor
                                            )
                                        }
                                    }
                                    Surface(
                                        color = progressColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "$ratioFormatted / 80%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = progressColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { reconciledRatio.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = progressColor,
                                    trackColor = progressColor.copy(alpha = 0.2f)
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${totalInventory.size - unreconciledCount} of ${totalInventory.size} items count-verified in last 14 days",
                                        fontSize = 10.sp,
                                        color = AppThemeManager.slateTextMedium
                                    )
                                    if (unreconciledCount > 0) {
                                        TextButton(
                                            onClick = { viewModel.dispatchAutomatedVerificationTasks { } },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(
                                                text = "Generate Audit Tasks",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TealPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Task List Displaying
                    val filteredTasks = operationTasks.filter { task ->
                        val taskAssignee = task.assignedToName?.trim()
                        val isUnassigned = taskAssignee.isNullOrBlank() || taskAssignee.equals("All Staff", ignoreCase = true)
                        when (opsTaskFilter) {
                            1 -> !task.isCompleted && taskAssignee?.equals(activeStaffName, ignoreCase = true) == true
                            2 -> !task.isCompleted && isUnassigned
                            3 -> task.isCompleted
                            else -> true
                        }
                    }.let { list ->
                        val urgencyWeight = { urgency: String ->
                            when (urgency.trim().lowercase()) {
                                "critical" -> 0
                                "high" -> 1
                                "medium" -> 2
                                "low" -> 3
                                else -> 4
                            }
                        }
                        if (opsTaskFilter == 3) {
                            // In resolved/completed tab, sort by completion time descending (most recently resolved first)
                            list.sortedByDescending { it.verifiedAt ?: it.createdAt }
                        } else {
                            when (opsTaskSortMode) {
                                1 -> {
                                    // Urgency Priority: High > Medium > Low, then latest createdAt
                                    list.sortedWith(
                                        compareBy<com.example.data.OperationTask> { urgencyWeight(it.urgency) }
                                            .thenByDescending { it.createdAt }
                                    )
                                }
                                2 -> {
                                    // Near Due / SLA: Tasks requiring urgent action / oldest uncompleted vs SLA
                                    list.sortedWith(
                                        compareBy<com.example.data.OperationTask> { urgencyWeight(it.urgency) }
                                            .thenBy { it.createdAt }
                                    )
                                }
                                else -> {
                                    // 0 = Default Option C: Latest First (createdAt DESC)
                                    list.sortedByDescending { it.createdAt }
                                }
                            }
                        }
                    }

                    if (filteredTasks.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AppThemeManager.secondary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.PlaylistAddCheck, contentDescription = null, tint = AppThemeManager.slateTextMedium, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("No operational tasks active for this view filter.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Switch filters or use the delegation panel to dispatch follow-ups and verification assignments.", fontSize = 10.sp, color = AppThemeManager.slateTextMedium, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    } else {
                        items(filteredTasks) { task ->
                            // Custom Parsing of internal retro-compatible payload string
                            val descriptionText = task.description
                            val hasAssignee = descriptionText.startsWith("Assignee:")
                            val parts = if (hasAssignee) descriptionText.split(" | instructions: ", limit = 2) else null
                            val legacyAssignee = if (parts != null && parts.isNotEmpty()) parts[0].substringAfter("Assignee:") else "All Staff"
                            val rawInstructions = if (parts != null && parts.size > 1) parts[1] else descriptionText
                            val actualInstructions = rawInstructions
                                .let { inst ->
                                    if (inst.contains("expiring within 30 days", ignoreCase = true)) {
                                        inst.replace("expiring within 30 days", "expiring batch - perform physical count & FEFO audit", ignoreCase = true)
                                    } else inst
                                }

                            val displayAssignee = task.assignedToName?.ifBlank { legacyAssignee } ?: legacyAssignee
                            val isUnassigned = displayAssignee.isBlank() || displayAssignee == "All Staff"

                            val isDark = AppThemeManager.isDark
                            // Category themed color mapping to modern primary/secondary/tertiary colors
                            val categoryColor = when (task.category) {
                                "Patient Engagement", "Patient Care" -> if (isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9) // Lavender Purple
                                "Revenue & Retention" -> if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309) // Gold Amber
                                "Clinical Intelligence" -> if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8) // Ice Blue
                                "Growth", "AI Priority" -> if (isDark) Color(0xFF34D399) else Color(0xFF047857) // Emerald Mint
                                else -> TealPrimary
                            }

                            val isHighlightedTask = effectiveHighlightTaskId != null && effectiveHighlightTaskId > 0L && (task.id.toLong() == effectiveHighlightTaskId || task.id == effectiveHighlightTaskId.toInt())

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isHighlightedTask) {
                                        TealPrimary.copy(alpha = 0.12f)
                                    } else if (task.isCompleted) {
                                        AppThemeManager.secondary.copy(alpha = 0.5f)
                                    } else {
                                        AppThemeManager.surface
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(
                                    width = if (isHighlightedTask) 2.5.dp else 1.dp,
                                    color = if (isHighlightedTask) TealPrimary else if (task.isCompleted) AppThemeManager.slateBorderLight else categoryColor.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (isHighlightedTask) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(TealPrimary)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.NotificationsActive,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "NOTIFICATION TARGET TASK",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.Black,
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear Highlight",
                                                tint = Color.Black,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { viewModel.clearHighlightTaskId() }
                                            )
                                        }
                                    }
                                    // Top Header Row: Title & Action Buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (task.isCompleted) AppThemeManager.slateTextMedium else categoryColor)
                                            )
                                            Text(
                                                text = task.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = if (task.isCompleted) AppThemeManager.slateTextMedium else MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        // Status Toggle & One-Tap Actions
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(start = 4.dp).horizontalScroll(rememberScrollState())
                                        ) {
                                            if (isUnassigned && !task.isCompleted) {
                                                val isTaskBeingClaimed = task.id in claimingTaskIds
                                                Button(
                                                    onClick = { viewModel.claimOperationTask(task, activeStaffName) },
                                                    enabled = !isTaskBeingClaimed,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = TealPrimary,
                                                        contentColor = Color.White,
                                                        disabledContainerColor = TealPrimary.copy(alpha = 0.7f),
                                                        disabledContentColor = Color.White
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    shape = RoundedCornerShape(16.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    if (isTaskBeingClaimed) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(12.dp),
                                                            color = Color.White,
                                                            strokeWidth = 1.5.dp
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Claiming...", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                                                    } else {
                                                        Icon(Icons.Default.Handshake, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Claim Task", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                                                    }
                                                }
                                            }

                                            if (!task.isCompleted) {
                                                Button(
                                                    onClick = { taskForComplianceVerification = task },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = AppThemeManager.okGreen,
                                                        contentColor = Color.White
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    shape = RoundedCornerShape(16.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Complete Task", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                                                }
                                            }

                                            // Managers can permanently revoke tasks
                                            if (isManager) {
                                                IconButton(
                                                    onClick = { viewModel.deleteOperationTask(task) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Revoke task", tint = AppThemeManager.slateTextMedium.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Category Tag, Urgency Tag & Assignee Badge (Full width FlowRow so pills stay on same line and wrap only when necessary)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(categoryColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = task.category.uppercase(),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = categoryColor,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }

                                        val urgencyColor = when(task.urgency) {
                                            "High" -> AppThemeManager.warningRed
                                            "Medium" -> AppThemeManager.pendingOrange
                                            else -> AppThemeManager.okGreen
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(urgencyColor.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = task.urgency.uppercase(),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = urgencyColor,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isUnassigned) AppThemeManager.pendingOrange.copy(alpha = 0.15f) else TealPrimary.copy(alpha = 0.15f))
                                                .border(0.5.dp, if (isUnassigned) AppThemeManager.pendingOrange.copy(alpha = 0.4f) else TealPrimary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isUnassigned) Icons.Default.SupervisedUserCircle else Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = if (isUnassigned) AppThemeManager.pendingOrange else TealPrimary,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Text(
                                                    text = if (isUnassigned) "UNASSIGNED POOL" else "ASSIGNED: $displayAssignee",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isUnassigned) AppThemeManager.pendingOrange else TealPrimary,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }

                                     Spacer(modifier = Modifier.height(6.dp))

                                     // Display actual instructions
                                     Text(
                                         text = actualInstructions,
                                         fontSize = 11.sp,
                                         color = if (task.isCompleted) AppThemeManager.slateTextMedium else MaterialTheme.colorScheme.onSurface
                                     )

                                     if (task.isCompleted) {
                                         Spacer(modifier = Modifier.height(6.dp))
                                         Box(
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .clip(RoundedCornerShape(8.dp))
                                                 .background(AppThemeManager.okGreen.copy(alpha = 0.07f))
                                                 .border(BorderStroke(1.dp, AppThemeManager.okGreen.copy(alpha = 0.22f)), RoundedCornerShape(8.dp))
                                                 .padding(10.dp)
                                         ) {
                                             Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                 // Top Header Row: Pill Badge & Compact Timestamp
                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     verticalAlignment = Alignment.CenterVertically,
                                                     horizontalArrangement = Arrangement.SpaceBetween
                                                 ) {
                                                     Row(
                                                         verticalAlignment = Alignment.CenterVertically,
                                                         horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                         modifier = Modifier
                                                             .clip(RoundedCornerShape(12.dp))
                                                             .background(AppThemeManager.okGreen.copy(alpha = 0.15f))
                                                             .padding(horizontal = 7.dp, vertical = 3.dp)
                                                     ) {
                                                         Icon(
                                                             imageVector = Icons.Default.VerifiedUser,
                                                             contentDescription = "Verified",
                                                             tint = AppThemeManager.okGreen,
                                                             modifier = Modifier.size(12.dp)
                                                         )
                                                         Text(
                                                             text = "COMPLIANCE VERIFIED",
                                                             fontSize = 9.sp,
                                                             fontWeight = FontWeight.Bold,
                                                             color = AppThemeManager.okGreen,
                                                             letterSpacing = 0.3.sp
                                                         )
                                                     }
                                                     if (task.verifiedAt != null) {
                                                         Text(
                                                             text = android.text.format.DateFormat.format("MMM d, yyyy • h:mm a", task.verifiedAt).toString(),
                                                             fontSize = 8.5.sp,
                                                             fontWeight = FontWeight.Medium,
                                                             color = AppThemeManager.slateTextMedium
                                                         )
                                                     }
                                                 }

                                                 // Metadata Row: Auditor, Channel, Patient Profile (FlowRow to prevent clipping)
                                                 FlowRow(
                                                     horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                     verticalArrangement = Arrangement.spacedBy(4.dp),
                                                     modifier = Modifier.fillMaxWidth()
                                                 ) {
                                                     Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                         Text("Auditor:", fontSize = 8.5.sp, color = AppThemeManager.slateTextMedium)
                                                         Text(task.verifiedBy ?: "System Operator", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                     }
                                                     Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                         Text("Channel:", fontSize = 8.5.sp, color = AppThemeManager.slateTextMedium)
                                                         Text(task.verificationChannel ?: "Direct", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                     }
                                                     if (!task.verificationCustomerName.isNullOrBlank()) {
                                                         Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                             Icon(
                                                                 imageVector = Icons.Default.Person,
                                                                 contentDescription = null,
                                                                 tint = categoryColor,
                                                                 modifier = Modifier.size(10.dp)
                                                             )
                                                             Text(task.verificationCustomerName, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = categoryColor)
                                                         }
                                                     }
                                                 }

                                                 // Audit Notes Quote Box
                                                 if (!task.verificationNotes.isNullOrBlank()) {
                                                     Column(
                                                         modifier = Modifier
                                                             .fillMaxWidth()
                                                             .clip(RoundedCornerShape(6.dp))
                                                             .background(AppThemeManager.okGreen.copy(alpha = 0.08f))
                                                             .padding(horizontal = 8.dp, vertical = 6.dp),
                                                         verticalArrangement = Arrangement.spacedBy(2.dp)
                                                     ) {
                                                         Text(
                                                             text = "Audit Notes:",
                                                             fontSize = 8.5.sp,
                                                             fontWeight = FontWeight.Bold,
                                                             color = AppThemeManager.slateTextMedium
                                                         )
                                                         Text(
                                                             text = "“${task.verificationNotes}”",
                                                             fontSize = 9.5.sp,
                                                             fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                             color = MaterialTheme.colorScheme.onSurface,
                                                             maxLines = 3,
                                                             overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                         )
                                                     }
                                                 }

                                                 // Manager Sign-Off / Approval Row
                                                 if (task.isApproved) {
                                                     Row(
                                                         modifier = Modifier
                                                             .fillMaxWidth()
                                                             .clip(RoundedCornerShape(6.dp))
                                                             .background(TealPrimary.copy(alpha = 0.08f))
                                                             .padding(horizontal = 8.dp, vertical = 5.dp),
                                                         verticalAlignment = Alignment.CenterVertically,
                                                         horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                     ) {
                                                         Icon(
                                                             imageVector = Icons.Default.TaskAlt,
                                                             contentDescription = "Approved",
                                                             tint = TealPrimary,
                                                             modifier = Modifier.size(13.dp)
                                                         )
                                                         Text(
                                                             text = "APPROVED BY ${task.approvedBy?.uppercase() ?: "MANAGER"}",
                                                             fontSize = 9.sp,
                                                             fontWeight = FontWeight.Bold,
                                                             color = TealPrimary
                                                         )
                                                         if (!task.approvalNotes.isNullOrBlank()) {
                                                             Text(
                                                                 text = "• “${task.approvalNotes}”",
                                                                 fontSize = 8.5.sp,
                                                                 color = AppThemeManager.slateTextMedium,
                                                                 maxLines = 1,
                                                                 overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                                 modifier = Modifier.weight(1f, fill = false)
                                                             )
                                                         }
                                                     }
                                                 } else {
                                                     Row(
                                                         modifier = Modifier
                                                             .fillMaxWidth()
                                                             .clip(RoundedCornerShape(6.dp))
                                                             .background(AppThemeManager.pendingOrange.copy(alpha = 0.08f))
                                                             .padding(horizontal = 8.dp, vertical = 5.dp),
                                                         verticalAlignment = Alignment.CenterVertically,
                                                         horizontalArrangement = Arrangement.SpaceBetween
                                                     ) {
                                                         Row(
                                                             verticalAlignment = Alignment.CenterVertically,
                                                             horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                             modifier = Modifier.weight(1f)
                                                         ) {
                                                             Icon(
                                                                 imageVector = Icons.Default.HourglassEmpty,
                                                                 contentDescription = "Pending Sign-Off",
                                                                 tint = AppThemeManager.pendingOrange,
                                                                 modifier = Modifier.size(13.dp)
                                                             )
                                                             Text(
                                                                 text = "Awaiting Manager Sign-Off",
                                                                 fontSize = 9.5.sp,
                                                                 fontWeight = FontWeight.Bold,
                                                                 color = AppThemeManager.pendingOrange,
                                                                 maxLines = 1,
                                                                 overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                             )
                                                         }
                                                         if (isManager) {
                                                             Button(
                                                                 onClick = { taskForManagerApproval = task },
                                                                 shape = RoundedCornerShape(6.dp),
                                                                 colors = ButtonDefaults.buttonColors(
                                                                     containerColor = TealPrimary,
                                                                     contentColor = Color.White
                                                                 ),
                                                                 contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                 modifier = Modifier.height(24.dp)
                                                             ) {
                                                                 Icon(
                                                                     imageVector = Icons.Default.FactCheck,
                                                                     contentDescription = null,
                                                                     modifier = Modifier.size(11.dp),
                                                                     tint = Color.White
                                                                 )
                                                                 Spacer(modifier = Modifier.width(3.dp))
                                                                 Text(
                                                                     text = "Approve",
                                                                     fontSize = 9.sp,
                                                                     fontWeight = FontWeight.Bold,
                                                                     color = Color.White,
                                                                     maxLines = 1,
                                                                     softWrap = false
                                                                 )
                                                             }
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                     } else {
                                         // Highlight that action requires audit verification
                                         Spacer(modifier = Modifier.height(10.dp))
                                         Button(
                                             onClick = { taskForComplianceVerification = task },
                                             shape = RoundedCornerShape(8.dp),
                                             colors = ButtonDefaults.buttonColors(
                                                 containerColor = categoryColor.copy(alpha = 0.12f),
                                                 contentColor = categoryColor
                                             ),
                                             contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                             modifier = Modifier.fillMaxWidth().height(32.dp)
                                         ) {
                                             Icon(
                                                 imageVector = Icons.Default.FactCheck,
                                                 contentDescription = null,
                                                 modifier = Modifier.size(14.dp)
                                             )
                                             Spacer(modifier = Modifier.width(6.dp))
                                             Text(
                                                 text = "Resolve with Compliance Audit",
                                                 fontSize = 11.sp,
                                                 fontWeight = FontWeight.Bold
                                             )
                                         }
                                     }

                                     Spacer(modifier = Modifier.height(10.dp))
                                     HorizontalDivider(color = AppThemeManager.slateBorderLight)
                                     Spacer(modifier = Modifier.height(6.dp))

                                    // Assignee metadata line
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(
                                                Icons.Default.AssignmentInd,
                                                contentDescription = null,
                                                tint = categoryColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "Assigned To: ",
                                                fontSize = 10.sp,
                                                color = AppThemeManager.slateTextMedium
                                            )
                                            Text(
                                                text = displayAssignee,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (task.isCompleted) AppThemeManager.slateTextMedium else categoryColor
                                            )
                                        }

                                        Text(
                                            text = android.text.format.DateFormat.format("MMM dd, hh:mm a", task.createdAt).toString(),
                                            fontSize = 9.sp,
                                            color = AppThemeManager.slateTextMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Spacing at end
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    // Interactive credential assignment bottom sheet dialog
    if (showRoleDialogForStaff != null) {
        val staff = showRoleDialogForStaff!!
        val uid = staff["uid"]?.toString() ?: staff["id"]?.toString() ?: ""
        val email = staff["email"]?.toString()
        val dispName = staff["displayName"]?.toString() ?: "Staff Member"
        var selectedRoleState by remember { mutableStateOf(staff["role"]?.toString() ?: "Pharmacist") }
        var isApprovedState by remember { mutableStateOf(staff["isApproved"] as? Boolean ?: true) }

        AlertDialog(
            onDismissRequest = { showRoleDialogForStaff = null },
            title = {
                Text(
                    text = "Configure Staff: $dispName",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Fine-tune local workspace authorizations and functional permissions below:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Role selection options
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "DESIGNATED ROLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary
                        )
                        val roles = listOf("Pharmacist", "Intern Pharmacist", "Technician")
                        roles.forEach { r ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selectedRoleState == r) TealPrimary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { selectedRoleState = r }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RadioButton(
                                    selected = selectedRoleState == r,
                                    onClick = { selectedRoleState = r },
                                    colors = RadioButtonDefaults.colors(selectedColor = TealPrimary)
                                )
                                Text(
                                    text = r,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedRoleState == r) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedRoleState == r) TealPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Approved active state toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WORKSPACE APPROVAL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isApprovedState) TealPrimary else Color.Red
                            )
                            Text(
                                text = if (isApprovedState) "Active (Access Allowed)" else "Suspended (No Access)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = isApprovedState,
                            onCheckedChange = { isApprovedState = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = TealPrimary)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateStaffRoleOrApproval(uid, selectedRoleState, isApprovedState, email)
                        showRoleDialogForStaff = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("Apply Parameters", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRoleDialogForStaff = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditProfileDialog) {
        var tempName by remember { mutableStateOf(currentName ?: "") }
        var tempPhone by remember { mutableStateOf(currentPhone ?: "") }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showEditProfileDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Edit Pharmacist Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Modify your system registration details. Changes will synchronize in real-time across high-security nodes and audit trails.",
                        fontSize = 11.sp,
                        color = AppThemeManager.slateTextMedium
                    )

                    // Full Name Input
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Full Name", fontSize = 12.sp) },
                        placeholder = { Text("e.g. John Doe") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            focusedLabelColor = TealPrimary,
                            unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Phone Number Input
                    OutlinedTextField(
                        value = tempPhone,
                        onValueChange = { tempPhone = it },
                        label = { Text("Phone Number", fontSize = 12.sp) },
                        placeholder = { Text("e.g. +2348000000000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            focusedLabelColor = TealPrimary,
                            unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempName.trim().isBlank() || tempPhone.trim().isBlank()) {
                            Toast.makeText(context, "Full Name and Phone Number are required", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSaving = true
                        viewModel.updatePharmacistProfile(tempName, tempPhone) { success, msg ->
                            isSaving = false
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) {
                                showEditProfileDialog = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save Changes", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditProfileDialog = false },
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSwitchBranchDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchBranchDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = TealPrimary
                    )
                    Text("Switch Pharmacy Branch Node", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    Text(
                        text = "Select an active branch. Real-time inventory, sales, delegation task boards, and audit logs will instantly adapt to your selected store node.",
                        fontSize = 12.sp,
                        color = AppThemeManager.slateTextMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (allBranches.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No other registered pharmacy branches found in network.",
                                fontSize = 12.sp,
                                color = AppThemeManager.slateTextMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(allBranches) { branchMap ->
                                val bId = branchMap["id"] as? String ?: branchMap["code"] as? String ?: ""
                                val bName = branchMap["name"] as? String ?: "Pharmacy Branch"
                                val lga = branchMap["lga"] as? String ?: ""
                                val state = branchMap["state"] as? String ?: ""
                                val isCurrent = bId == branchId
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.switchActiveBranch(bId, bName) { _, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                            showSwitchBranchDialog = false
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrent) TealPrimary.copy(alpha = 0.12f) else AppThemeManager.secondary
                                    ),
                                    border = BorderStroke(1.dp, if (isCurrent) TealPrimary else AppThemeManager.slateBorderLight)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(bName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            if (lga.isNotBlank() || state.isNotBlank()) {
                                                Text("$lga, $state", fontSize = 10.sp, color = AppThemeManager.slateTextMedium)
                                            }
                                            Text("Store Code: $bId", fontSize = 10.sp, color = TealPrimary, fontWeight = FontWeight.SemiBold)
                                        }
                                        if (isCurrent) {
                                            Box(
                                                modifier = Modifier
                                                    .background(TealPrimary, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Active Node", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = AppThemeManager.slateTextMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSwitchBranchDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val currentVerifyTask = taskForComplianceVerification
    if (currentVerifyTask != null) {
        val task = currentVerifyTask
        var complianceNotes by remember { mutableStateOf("") }
        var selectedChannel by remember { mutableStateOf("Phone Call") }
        var linkedCustomerName by remember { mutableStateOf("") }
        var isSavingCompliance by remember { mutableStateOf(false) }
        var auditCountedQuantityString by remember { mutableStateOf("") }
        var showCustomNoteInput by remember { mutableStateOf(false) }

        val channels = listOf("Phone Call", "WhatsApp", "In-Person", "SMS")

        val isStockTransfer = task.category == "Stock Transfer"
        val isExpiryTask = task.title.contains("Expiry", ignoreCase = true) || task.category.contains("Expiry", ignoreCase = true)
        val isInventoryTask = !isExpiryTask && (task.title.contains("Inventory", ignoreCase = true) || task.title.contains("Reconcile", ignoreCase = true) || task.title.contains("Stock", ignoreCase = true) || task.category.contains("Inventory", ignoreCase = true))
        val isPatientTask = !isStockTransfer && !isExpiryTask && !isInventoryTask && (task.title.contains("Refill", ignoreCase = true) || task.title.contains("Patient", ignoreCase = true) || task.category.contains("Patient", ignoreCase = true))

        val presetVerificationNotes = when {
            isStockTransfer -> listOf(
                "Received intact, count verified, batch logged in shelf.",
                "Stock transfer verified and accepted.",
                "Stock verified with minor packaging damage, items intact."
            )
            isExpiryTask || isInventoryTask -> listOf(
                "Physical count verified with shelf stock.",
                "Count verified. Applied FEFO sticker for quick depletion.",
                "Stock counted & verified intact.",
                "Discrepancy resolved after shelf recount."
            )
            isPatientTask -> listOf(
                "Contacted patient via phone, refill confirmed.",
                "Patient contacted via WhatsApp, appointment scheduled.",
                "Patient confirmed prescription pick-up for tomorrow."
            )
            else -> listOf(
                "Task completed according to branch guidelines.",
                "Operational check passed and verified.",
                "Maintenance and inspection completed."
            )
        }

        val auditActionChannel = if (isExpiryTask) "Expiry Audit" else "Shelf Audit"

        val descriptionText = task.description
        val itemName = if (isStockTransfer && descriptionText.contains("ITEM: ")) {
            descriptionText.substringAfter("ITEM: ").substringBefore(" | DOSAGE: ").trim()
        } else ""
        val itemDosage = if (isStockTransfer && descriptionText.contains("DOSAGE: ")) {
            descriptionText.substringAfter("DOSAGE: ").substringBefore(" | QTY: ").trim()
        } else ""
        val itemQty = if (isStockTransfer && descriptionText.contains("QTY: ")) {
            descriptionText.substringAfter("QTY: ").substringBefore(" | FROM: ").trim().toIntOrNull() ?: 0
        } else 0
        val fromBranch = if (isStockTransfer && descriptionText.contains("FROM: ")) {
            descriptionText.substringAfter("FROM: ").substringBefore(" | REASON: ").trim()
        } else ""
        val reasonText = if (isStockTransfer && descriptionText.contains("REASON: ")) {
            descriptionText.substringAfter("REASON: ").trim()
        } else ""

        val dialogTitleText = when {
            isStockTransfer -> "Stock Transfer Receipt"
            isExpiryTask -> "Expiry Shelf Audit Verification"
            isInventoryTask -> "Inventory & Stock Audit Verification"
            isPatientTask -> "Patient Follow-up & Clinical Log"
            else -> "Operations Task Verification"
        }

        AlertDialog(
            onDismissRequest = { if (!isSavingCompliance) taskForComplianceVerification = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isStockTransfer -> Icons.Default.Transform
                            isExpiryTask -> Icons.Default.Warning
                            isInventoryTask -> Icons.Default.Inventory
                            else -> Icons.Default.FactCheck
                        },
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = dialogTitleText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                if (isStockTransfer) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppThemeManager.secondary.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "INCOMING STOCK TRANSFER DETAILS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Item: $itemName ($itemDosage)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "Transfer Quantity: $itemQty units", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "Originating Branch: $fromBranch", fontSize = 11.sp, color = AppThemeManager.slateTextMedium)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "Reason: $reasonText", fontSize = 11.sp, color = AppThemeManager.slateTextMedium)
                            }
                        }

                        Text(
                            text = "Please verify that the physical stock received matches the transfer request. Confirming this receipt will automatically adjust your local branch inventory.",
                            fontSize = 11.sp,
                            color = AppThemeManager.slateTextMedium
                        )

                        // --- Preset Verification Notes & Custom Type Toggle ---
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Select Verification Note Preset",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppThemeManager.slateTextMedium
                                )
                                TextButton(
                                    onClick = { showCustomNoteInput = !showCustomNoteInput },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = null,
                                        tint = TealPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (showCustomNoteInput) "Hide Text Area" else "Type / Edit Note",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presetVerificationNotes.forEach { preset ->
                                    val isSelected = complianceNotes == preset
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { complianceNotes = preset },
                                        label = { Text(preset, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TealPrimary.copy(alpha = 0.2f),
                                            selectedLabelColor = TealPrimary,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = AppThemeManager.unfocusedTextFieldBorder.copy(alpha = 0.4f),
                                            selectedBorderColor = TealPrimary
                                        )
                                    )
                                }
                            }

                            if (showCustomNoteInput || complianceNotes.isBlank() || presetVerificationNotes.none { it == complianceNotes }) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = complianceNotes,
                                    onValueChange = { complianceNotes = it },
                                    label = { Text("Verification / Condition Notes", fontSize = 12.sp) },
                                    placeholder = { Text("Type custom verification notes...") },
                                    minLines = 2,
                                    maxLines = 4,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TealPrimary,
                                        focusedLabelColor = TealPrimary,
                                        unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("transfer_receipt_notes_input")
                                )
                            }
                        }
                        
                        Text(
                            text = "Note: A minimum of 5 characters verification notes is required.",
                            fontSize = 10.sp,
                            color = if (complianceNotes.trim().length >= 5) AppThemeManager.okGreen else AppThemeManager.warningRed,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                } else if (isExpiryTask || isInventoryTask) {
                    // --- Inventory / Expiry Shelf Audit Dialog ---
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppThemeManager.secondary.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isExpiryTask) "EXPIRY AUDIT NODE" else "INVENTORY RECONCILIATION NODE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = task.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(4.dp))
                                val instructionsText = if (task.description.startsWith("Assignee:")) {
                                    task.description.substringAfter(" | instructions: ")
                                } else {
                                    task.description
                                }
                                Text(text = instructionsText, fontSize = 11.sp, color = AppThemeManager.slateTextMedium)
                            }
                        }

                        Text(
                            text = "Conduct a physical shelf audit. Enter counted physical quantity to update inventory directly, select the audit action, and select or type a verification note.",
                            fontSize = 11.sp,
                            color = AppThemeManager.slateTextMedium
                        )

                        // Counted Physical Quantity Input Field
                        OutlinedTextField(
                            value = auditCountedQuantityString,
                            onValueChange = { auditCountedQuantityString = it.filter { c -> c.isDigit() } },
                            label = { Text("Counted Physical Stock Quantity (Units) *", fontSize = 12.sp) },
                            placeholder = { Text("e.g. 45") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                focusedLabelColor = TealPrimary,
                                unfocusedBorderColor = if (auditCountedQuantityString.isBlank()) AppThemeManager.warningRed else AppThemeManager.unfocusedTextFieldBorder
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("audit_counted_qty_input")
                        )
                        if (auditCountedQuantityString.isNotBlank()) {
                            Text(
                                text = "⚡ Entered count '${auditCountedQuantityString.trim()}' will automatically update inventory stock upon verification.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary
                            )
                        } else {
                            Text(
                                text = "⚠️ Counted physical stock quantity is required to resolve this audit task.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppThemeManager.warningRed
                            )
                        }

                        // Preset Verification Notes for Inventory/Expiry
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Preset Audit Resolution Notes",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppThemeManager.slateTextMedium
                                )
                                TextButton(
                                    onClick = { showCustomNoteInput = !showCustomNoteInput },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = null,
                                        tint = TealPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (showCustomNoteInput) "Hide Text Area" else "Type / Edit Note",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presetVerificationNotes.forEach { preset ->
                                    val isSelected = complianceNotes == preset
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { complianceNotes = preset },
                                        label = { Text(preset, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TealPrimary.copy(alpha = 0.2f),
                                            selectedLabelColor = TealPrimary,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = AppThemeManager.unfocusedTextFieldBorder.copy(alpha = 0.4f),
                                            selectedBorderColor = TealPrimary
                                        )
                                    )
                                }
                            }

                            if (showCustomNoteInput || complianceNotes.isBlank() || presetVerificationNotes.none { it == complianceNotes }) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = complianceNotes,
                                    onValueChange = { complianceNotes = it },
                                    label = { Text("Audit Resolution & Verification Notes", fontSize = 12.sp) },
                                    placeholder = { Text("e.g. Physical count confirmed 45 units...") },
                                    minLines = 2,
                                    maxLines = 4,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TealPrimary,
                                        focusedLabelColor = TealPrimary,
                                        unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("audit_notes_input")
                                )
                            }
                        }
                        
                        Text(
                            text = "Note: A minimum of 5 characters resolution note is required.",
                            fontSize = 10.sp,
                            color = if (complianceNotes.trim().length >= 5) AppThemeManager.okGreen else AppThemeManager.warningRed,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                } else if (isPatientTask) {
                    // --- Patient Care & Refill Task Dialog ---
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppThemeManager.secondary.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "PATIENT CARE FOLLOW-UP", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = task.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(2.dp))
                                val instructionsText = if (task.description.startsWith("Assignee:")) {
                                    task.description.substringAfter(" | instructions: ")
                                } else {
                                    task.description
                                }
                                Text(text = instructionsText, fontSize = 11.sp, color = AppThemeManager.slateTextMedium)
                            }
                        }

                        Text(
                            text = "Document patient outreach evidence and outcome before archiving this clinical care task.",
                            fontSize = 11.sp,
                            color = AppThemeManager.slateTextMedium
                        )

                        // Linked Patient Name
                        OutlinedTextField(
                            value = linkedCustomerName,
                            onValueChange = { linkedCustomerName = it },
                            label = { Text("Patient / Customer Name", fontSize = 12.sp) },
                            placeholder = { Text("e.g. John Doe") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                focusedLabelColor = TealPrimary,
                                unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Engagement Channel Select
                        Column {
                            Text(text = "Select Engagement Channel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppThemeManager.slateTextMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                channels.forEach { channel ->
                                    val isSelected = selectedChannel == channel
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) TealPrimary.copy(alpha = 0.15f) else Color.Transparent)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) TealPrimary else AppThemeManager.unfocusedTextFieldBorder.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { selectedChannel = channel }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = channel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) TealPrimary else AppThemeManager.slateTextMedium
                                        )
                                    }
                                }
                            }
                        }

                        // Clinical interaction preset notes
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Select Outreach Preset Note",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppThemeManager.slateTextMedium
                                )
                                TextButton(
                                    onClick = { showCustomNoteInput = !showCustomNoteInput },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = null,
                                        tint = TealPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (showCustomNoteInput) "Hide Text Area" else "Type / Edit Note",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presetVerificationNotes.forEach { preset ->
                                    val isSelected = complianceNotes == preset
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { complianceNotes = preset },
                                        label = { Text(preset, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TealPrimary.copy(alpha = 0.2f),
                                            selectedLabelColor = TealPrimary,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = AppThemeManager.unfocusedTextFieldBorder.copy(alpha = 0.4f),
                                            selectedBorderColor = TealPrimary
                                        )
                                    )
                                }
                            }

                            if (showCustomNoteInput || complianceNotes.isBlank() || presetVerificationNotes.none { it == complianceNotes }) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = complianceNotes,
                                    onValueChange = { complianceNotes = it },
                                    label = { Text("Clinical Outreach Notes", fontSize = 12.sp) },
                                    placeholder = { Text("e.g. Contacted patient via WhatsApp, confirmed refill...") },
                                    minLines = 2,
                                    maxLines = 4,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TealPrimary,
                                        focusedLabelColor = TealPrimary,
                                        unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("clinical_notes_input")
                                )
                            }
                        }
                        
                        Text(
                            text = "Note: Patient Name & minimum 8 characters notes required.",
                            fontSize = 10.sp,
                            color = if (linkedCustomerName.trim().isNotEmpty() && complianceNotes.trim().length >= 8) AppThemeManager.okGreen else AppThemeManager.warningRed,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                } else {
                    // --- General Operations Task Dialog ---
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppThemeManager.secondary.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "BRANCH OPERATIONS TASK", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = task.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(2.dp))
                                val instructionsText = if (task.description.startsWith("Assignee:")) {
                                    task.description.substringAfter(" | instructions: ")
                                } else {
                                    task.description
                                }
                                Text(text = instructionsText, fontSize = 11.sp, color = AppThemeManager.slateTextMedium)
                            }
                        }

                        Text(
                            text = "Document completion notes to verify execution of this branch operational task.",
                            fontSize = 11.sp,
                            color = AppThemeManager.slateTextMedium
                        )

                        // Preset Notes for General Ops
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Select Completion Preset Note",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppThemeManager.slateTextMedium
                                )
                                TextButton(
                                    onClick = { showCustomNoteInput = !showCustomNoteInput },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = null,
                                        tint = TealPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (showCustomNoteInput) "Hide Text Area" else "Type / Edit Note",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presetVerificationNotes.forEach { preset ->
                                    val isSelected = complianceNotes == preset
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { complianceNotes = preset },
                                        label = { Text(preset, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TealPrimary.copy(alpha = 0.2f),
                                            selectedLabelColor = TealPrimary,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = AppThemeManager.unfocusedTextFieldBorder.copy(alpha = 0.4f),
                                            selectedBorderColor = TealPrimary
                                        )
                                    )
                                }
                            }

                            if (showCustomNoteInput || complianceNotes.isBlank() || presetVerificationNotes.none { it == complianceNotes }) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = complianceNotes,
                                    onValueChange = { complianceNotes = it },
                                    label = { Text("Completion & Resolution Notes", fontSize = 12.sp) },
                                    placeholder = { Text("Provide details on how this task was completed...") },
                                    minLines = 2,
                                    maxLines = 4,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TealPrimary,
                                        focusedLabelColor = TealPrimary,
                                        unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("general_notes_input")
                                )
                            }
                        }
                        
                        Text(
                            text = "Note: A minimum of 5 characters resolution note is required.",
                            fontSize = 10.sp,
                            color = if (complianceNotes.trim().length >= 5) AppThemeManager.okGreen else AppThemeManager.warningRed,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanNotes = complianceNotes.trim()
                        if (isStockTransfer) {
                            if (cleanNotes.length < 5) {
                                Toast.makeText(context, "Verification notes must be at least 5 characters long", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            taskForComplianceVerification = null
                            Toast.makeText(context, "Stock transfer verified and received.", Toast.LENGTH_SHORT).show()
                            viewModel.verifyAndReceiveStockTransfer(task, cleanNotes) { _, _ -> }
                        } else if (isExpiryTask || isInventoryTask) {
                            val countedQty = auditCountedQuantityString.trim().toIntOrNull()
                            if (countedQty == null) {
                                Toast.makeText(context, "Please enter the counted physical stock quantity to resolve this audit task", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (cleanNotes.length < 5) {
                                Toast.makeText(context, "Resolution notes must be at least 5 characters long", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            taskForComplianceVerification = null
                            Toast.makeText(context, "Task completed and inventory reconciled.", Toast.LENGTH_SHORT).show()
                            viewModel.verifiablyCompleteOperationTask(
                                task = task,
                                notes = cleanNotes,
                                channel = auditActionChannel,
                                patientName = "Internal Stock Audit",
                                countedQuantity = countedQty
                            ) { _, _ -> }
                        } else if (isPatientTask) {
                            val cleanPatient = linkedCustomerName.trim()
                            if (cleanPatient.isEmpty()) {
                                Toast.makeText(context, "Patient Name is required for follow-up audit", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (cleanNotes.length < 8) {
                                Toast.makeText(context, "Outreach notes must be at least 8 characters long to satisfy policy", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            taskForComplianceVerification = null
                            Toast.makeText(context, "Clinical outreach logged and task completed.", Toast.LENGTH_SHORT).show()
                            viewModel.verifiablyCompleteOperationTask(
                                task = task,
                                notes = cleanNotes,
                                channel = selectedChannel,
                                patientName = cleanPatient
                            ) { _, _ -> }
                        } else {
                            if (cleanNotes.length < 5) {
                                Toast.makeText(context, "Completion notes must be at least 5 characters long", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            taskForComplianceVerification = null
                            Toast.makeText(context, "Task completed.", Toast.LENGTH_SHORT).show()
                            viewModel.verifiablyCompleteOperationTask(
                                task = task,
                                notes = cleanNotes,
                                channel = "System/Other",
                                patientName = "N/A - General Ops"
                            ) { _, _ -> }
                        }
                    },
                    enabled = (
                        (isStockTransfer && complianceNotes.trim().length >= 5) ||
                        ((isExpiryTask || isInventoryTask) && auditCountedQuantityString.trim().toIntOrNull() != null && complianceNotes.trim().length >= 5) ||
                        (isPatientTask && linkedCustomerName.trim().isNotEmpty() && complianceNotes.trim().length >= 8) ||
                        (!isStockTransfer && !isExpiryTask && !isInventoryTask && !isPatientTask && complianceNotes.trim().length >= 5)
                    ),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    val confirmText = when {
                        isStockTransfer -> "Verify & Receive"
                        isExpiryTask || isInventoryTask -> "Confirm & Resolve Audit"
                        isPatientTask -> "Archive Clinical Log"
                        else -> "Complete Operational Task"
                    }
                    Text(confirmText, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { taskForComplianceVerification = null },
                    enabled = !isSavingCompliance
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val currentApprovalTask = taskForManagerApproval
    if (currentApprovalTask != null) {
        val task = currentApprovalTask
        var approvalNotes by remember { mutableStateOf("") }
        var isSavingApproval by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSavingApproval) taskForManagerApproval = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Manager Sign-Off",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppThemeManager.secondary.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = "TASK TO APPROVE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = task.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(2.dp))
                            val instructionsText = if (task.description.startsWith("Assignee:")) {
                                task.description.substringAfter(" | instructions: ")
                            } else {
                                task.description
                            }
                            Text(text = instructionsText, fontSize = 11.sp, color = AppThemeManager.slateTextMedium)
                        }
                    }

                    Text(
                        text = "As the designated Branch Manager, your signature confirms compliance audit verification. Enter any administrative audit comments or sign-off notes below:",
                        fontSize = 11.sp,
                        color = AppThemeManager.slateTextMedium
                    )

                    OutlinedTextField(
                        value = approvalNotes,
                        onValueChange = { approvalNotes = it },
                        label = { Text("Manager Review / Sign-off Notes", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Verified prescription volume records match. Approved.") },
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            focusedLabelColor = TealPrimary,
                            unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanNotes = approvalNotes.trim()
                        isSavingApproval = true
                        viewModel.approveOperationTask(
                            task = task,
                            notes = cleanNotes
                        ) { success, msg ->
                            isSavingApproval = false
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) {
                                taskForManagerApproval = null
                            }
                        }
                    },
                    enabled = !isSavingApproval,
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    if (isSavingApproval) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Sign-off & Approve", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { taskForManagerApproval = null },
                    enabled = !isSavingApproval
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showColdChainDialog) {
        var selectedUnit by remember { mutableStateOf(lastLoggedFridgeUnit) }
        var tempVal by remember { mutableFloatStateOf(lastLoggedFridgeTemp) }
        var notesText by remember { mutableStateOf("") }
        var doorSealChecked by remember { mutableStateOf(true) }
        var powerChecked by remember { mutableStateOf(true) }
        var isSubmitting by remember { mutableStateOf(false) }

        val isTempSafe = tempVal in 2.0f..8.0f
        val isTempFreezing = tempVal < 2.0f
        val isTempExcursion = tempVal > 8.0f

        val tempStatusColor = when {
            isTempSafe -> AppThemeManager.okGreen
            isTempFreezing -> AppThemeManager.pendingOrange
            else -> AppThemeManager.warningRed
        }

        val tempStatusLabel = when {
            isTempSafe -> "OPTIMAL COLD-CHAIN RANGE (2°C – 8°C)"
            isTempFreezing -> "FREEZING RISK (< 2.0°C) - RISK OF SPOILAGE"
            else -> "TEMPERATURE EXCURSION (> 8.0°C) - BREACH ALERT"
        }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showColdChainDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AcUnit,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "1-Tap Cold-Chain Temp Log",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Refrigerated Storage & Quality Control",
                            fontSize = 10.sp,
                            color = AppThemeManager.slateTextMedium
                        )
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp)
                ) {
                    // Unit Selection
                    Text("SELECT STORAGE UNIT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AppThemeManager.slateTextMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val units = listOf("Main Refrigerator", "Vaccine Storage #2", "Insulin Vault")
                        units.forEach { unit ->
                            val sel = selectedUnit == unit
                            FilterChip(
                                selected = sel,
                                onClick = { selectedUnit = unit },
                                label = { Text(unit, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TealPrimary,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }

                    // Temperature Live Value Display Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = tempStatusColor.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, tempStatusColor.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tempStatusLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = tempStatusColor
                            )
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = String.format("%.1f", tempVal),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = tempStatusColor
                                )
                                Text(
                                    text = "°C",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tempStatusColor,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            
                            Slider(
                                value = tempVal,
                                onValueChange = { tempVal = (kotlin.math.round(it * 10)) / 10.0f },
                                valueRange = 0.0f..15.0f,
                                steps = 149,
                                colors = SliderDefaults.colors(
                                    thumbColor = tempStatusColor,
                                    activeTrackColor = tempStatusColor,
                                    inactiveTrackColor = AppThemeManager.slateBorderLight
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("0°C", fontSize = 9.sp, color = AppThemeManager.slateTextMedium)
                                Text("Ideal: 2°C–8°C", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AppThemeManager.okGreen)
                                Text("15°C", fontSize = 9.sp, color = AppThemeManager.slateTextMedium)
                            }
                        }
                    }

                    // Equipment Checkboxes
                    Text("SAFETY & COMPLIANCE CHECKS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AppThemeManager.slateTextMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = doorSealChecked,
                            onCheckedChange = { doorSealChecked = it },
                            colors = CheckboxDefaults.colors(checkedColor = TealPrimary)
                        )
                        Text("Door Gasket & Magnetic Seal Tight", fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = powerChecked,
                            onCheckedChange = { powerChecked = it },
                            colors = CheckboxDefaults.colors(checkedColor = TealPrimary)
                        )
                        Text("Mains Power & Backup Inverter Online", fontSize = 11.sp)
                    }

                    // Audit Notes
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Quality Control Notes (Optional)", fontSize = 11.sp) },
                        placeholder = { Text("e.g. Digital probe reading verified, zero power outages.", fontSize = 10.sp) },
                        minLines = 2,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            focusedLabelColor = TealPrimary,
                            unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmitting = true
                        val now = System.currentTimeMillis()
                        lastLoggedFridgeTemp = tempVal
                        lastLoggedFridgeUnit = selectedUnit
                        lastLoggedTimeMs = now
                        lastLoggedBy = activeStaffName

                        // Automatically resolve any cold chain task on board
                        val coldChainTask = operationTasks.find {
                            !it.isCompleted && (it.title.contains("Cold Chain", ignoreCase = true) || it.title.contains("Fridge", ignoreCase = true))
                        }
                        val userNotes = notesText.ifBlank { "Routine opening cold chain check verified with digital probe." }
                        val fullNotes = "Logged ${String.format("%.1f", tempVal)}°C for $selectedUnit. Door Seal: ${if (doorSealChecked) "OK" else "Issue"}, Power: ${if (powerChecked) "OK" else "Alert"}. $userNotes"

                        if (coldChainTask != null) {
                            viewModel.verifiablyCompleteOperationTask(
                                task = coldChainTask,
                                notes = fullNotes,
                                channel = "Digital Calibrated Sensor",
                                patientName = selectedUnit,
                                onFinished = { _, _ -> }
                            )
                        }

                        Toast.makeText(context, "✅ Cold-Chain Logged: ${String.format("%.1f", tempVal)}°C saved for $selectedUnit!", Toast.LENGTH_SHORT).show()
                        showColdChainDialog = false
                        isSubmitting = false
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("Save Cold-Chain Audit Log", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showColdChainDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
