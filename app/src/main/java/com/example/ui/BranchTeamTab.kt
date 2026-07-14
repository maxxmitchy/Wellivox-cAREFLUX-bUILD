package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TealTertiary
import com.example.ui.theme.AppThemeManager
import androidx.compose.foundation.BorderStroke
import com.example.data.OperationTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchTeamTab(
    viewModel: com.example.ui.PharmacyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val branchId by viewModel.currentPharmacistBranchId.collectAsStateWithLifecycle()
    val branchName by viewModel.currentPharmacistBranchName.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentPharmacistRole.collectAsStateWithLifecycle()
    val currentName by viewModel.currentPharmacistName.collectAsStateWithLifecycle()
    val currentPhone by viewModel.currentPharmacistPhone.collectAsStateWithLifecycle()
    val staffList by viewModel.branchStaffList.collectAsStateWithLifecycle()
    val operationTasks by viewModel.operationTasks.collectAsStateWithLifecycle()

    var showRoleDialogForStaff by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var taskForComplianceVerification by remember { mutableStateOf<OperationTask?>(null) }
    var taskForManagerApproval by remember { mutableStateOf<OperationTask?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Staff Roster, 1 = Ops Delegation Center

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // Checking if user has an assigned branch
    if (branchId.isNullOrBlank()) {
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
                // Header Banner Logo
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(TealPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Text(
                    text = "Welcome to Careflux Workspace",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Your account is not linked to any active branch node. To access pharmacy operations, real-time inventory, sales, and task systems, choose one of the options below to proceed.",
                    fontSize = 12.sp,
                    color = AppThemeManager.slateTextMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(TealPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Storefront,
                                        contentDescription = "Branch",
                                        tint = TealPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = branchName ?: "Standard Pharmacy Branch",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Active Pharmacy Node Workspace",
                                        fontSize = 11.sp,
                                        color = AppThemeManager.slateTextMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Authorization Code Field
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AppThemeManager.secondary.copy(alpha = 0.6f))
                                    .border(1.dp, TealPrimary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "BRANCH AUTHORIZATION CODE (TAP TO COPY)",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimary
                                    )
                                    Text(
                                        text = branchId ?: "",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        letterSpacing = 1.sp
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clip = ClipData.newPlainText("Careflux Branch Code", branchId ?: "")
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "Branch Code copied!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = TealPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Elegant Divider Separating Node Code from Personal Operator Details
                            Divider(
                                color = AppThemeManager.slateBorderLight.copy(alpha = 0.5f),
                                thickness = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Logged-in Operator Info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    // Elegant avatar placeholder container
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(TealPrimary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = TealPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = currentName ?: "Active Staff",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Professional high-visibility role badge
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(TealPrimary.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = currentRole ?: "Pharmacist",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TealPrimary,
                                                    maxLines = 1
                                                )
                                            }

                                            Text(
                                                text = "• Active Session",
                                                fontSize = 10.sp,
                                                color = AppThemeManager.slateTextMedium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                // Premium action chip for editing profile
                                OutlinedButton(
                                    onClick = { showEditProfileDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = TealPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Profile",
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Edit Profile",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
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
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Synced live",
                                tint = TealPrimary,
                                modifier = Modifier.size(14.dp)
                              )
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
                        // Task Summary Metrics Row
                        val completedCount = operationTasks.count { it.isCompleted && it.isApproved }
                        val pendingCount = operationTasks.size - completedCount

                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppThemeManager.surface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            border = BorderStroke(1.dp, AppThemeManager.slateBorderLight)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("TOTAL OPERATIONS", fontSize = 9.sp, color = AppThemeManager.slateTextMedium, fontWeight = FontWeight.Bold)
                                    Text("${operationTasks.size}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(AppThemeManager.slateBorderLight))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("RESOLVED", fontSize = 9.sp, color = AppThemeManager.okGreen, fontWeight = FontWeight.Bold)
                                    Text("$completedCount", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = AppThemeManager.okGreen)
                                }
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(AppThemeManager.slateBorderLight))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("PENDING TASK", fontSize = 9.sp, color = AppThemeManager.pendingOrange, fontWeight = FontWeight.Bold)
                                    Text("$pendingCount", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = AppThemeManager.pendingOrange)
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

                    // Task List Displaying
                    if (operationTasks.isEmpty()) {
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
                                    Text("No operational tasks active for this node.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Use the delegation panel to dispatch follow-ups, engagement targets, or retention assignments.", fontSize = 10.sp, color = AppThemeManager.slateTextMedium, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    } else {
                        items(operationTasks.sortedByDescending { it.createdAt }) { task ->
                            // Custom Parsing of internal retro-compatible payload string
                            val descriptionText = task.description
                            val hasAssignee = descriptionText.startsWith("Assignee:")
                            val parts = if (hasAssignee) descriptionText.split(" | instructions: ", limit = 2) else null
                            val assigneeName = if (parts != null && parts.isNotEmpty()) parts[0].substringAfter("Assignee:") else "All Staff"
                            val actualInstructions = if (parts != null && parts.size > 1) parts[1] else descriptionText

                            val isDark = AppThemeManager.isDark
                            // Category themed color mapping to modern primary/secondary/tertiary colors
                            val categoryColor = when (task.category) {
                                "Patient Engagement", "Patient Care" -> if (isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9) // Lavender Purple
                                "Revenue & Retention" -> if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309) // Gold Amber
                                "Clinical Intelligence" -> if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8) // Ice Blue
                                "Growth", "AI Priority" -> if (isDark) Color(0xFF34D399) else Color(0xFF047857) // Emerald Mint
                                else -> TealPrimary
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (task.isCompleted) {
                                        AppThemeManager.secondary.copy(alpha = 0.5f)
                                    } else {
                                        AppThemeManager.surface
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (task.isCompleted) AppThemeManager.slateBorderLight else categoryColor.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                                    fontSize = 14.sp,
                                                    color = if (task.isCompleted) AppThemeManager.slateTextMedium else MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // Category Tag
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                                        color = categoryColor
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
                                                        color = urgencyColor
                                                    )
                                                }
                                            }
                                        }

                                         // Status Toggle with Compliance Guard
                                         Row(verticalAlignment = Alignment.CenterVertically) {
                                             Checkbox(
                                                 checked = task.isCompleted,
                                                 onCheckedChange = { isChecked ->
                                                     if (isChecked) {
                                                         taskForComplianceVerification = task
                                                     } else {
                                                         viewModel.toggleOperationTask(task)
                                                     }
                                                 },
                                                 enabled = !task.isCompleted,
                                                 colors = CheckboxDefaults.colors(
                                                     checkedColor = AppThemeManager.okGreen,
                                                     checkmarkColor = if (isDark) Color.Black else Color.White
                                                 )
                                             )

                                             // Managers can permanently revoke tasks
                                             if (isManager) {
                                                 IconButton(onClick = { viewModel.deleteOperationTask(task) }) {
                                                     Icon(Icons.Default.Delete, contentDescription = "Revoke task", tint = AppThemeManager.slateTextMedium.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                                 }
                                             }
                                         }
                                     }

                                     Spacer(modifier = Modifier.height(8.dp))

                                     // Display actual instructions
                                     Text(
                                         text = actualInstructions,
                                         fontSize = 12.sp,
                                         color = if (task.isCompleted) AppThemeManager.slateTextMedium else MaterialTheme.colorScheme.onSurface
                                     )

                                     if (task.isCompleted) {
                                         Spacer(modifier = Modifier.height(10.dp))
                                         Box(
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .clip(RoundedCornerShape(8.dp))
                                                 .background(AppThemeManager.okGreen.copy(alpha = 0.08f))
                                                 .border(BorderStroke(1.dp, AppThemeManager.okGreen.copy(alpha = 0.2f)), RoundedCornerShape(8.dp))
                                                 .padding(10.dp)
                                         ) {
                                             Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                 Row(
                                                     verticalAlignment = Alignment.CenterVertically,
                                                     horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                 ) {
                                                     Icon(
                                                         imageVector = Icons.Default.VerifiedUser,
                                                         contentDescription = "Compliance Audited",
                                                         tint = AppThemeManager.okGreen,
                                                         modifier = Modifier.size(14.dp)
                                                     )
                                                     Text(
                                                         text = "CLINICAL COMPLIANCE VERIFIED",
                                                         fontSize = 9.sp,
                                                         fontWeight = FontWeight.Bold,
                                                         color = AppThemeManager.okGreen,
                                                         letterSpacing = 0.5.sp
                                                     )
                                                 }
                                                 
                                                 Spacer(modifier = Modifier.height(2.dp))
                                                 
                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                 ) {
                                                     Column(modifier = Modifier.weight(1f)) {
                                                         Text("Auditor/Staff", fontSize = 8.sp, color = AppThemeManager.slateTextMedium)
                                                         Text(task.verifiedBy ?: "System Operator", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                     }
                                                     Column(modifier = Modifier.weight(1f)) {
                                                         Text("Engagement Channel", fontSize = 8.sp, color = AppThemeManager.slateTextMedium)
                                                         Text(task.verificationChannel ?: "Not specified", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                     }
                                                 }
                                                 
                                                 if (!task.verificationCustomerName.isNullOrBlank()) {
                                                     Spacer(modifier = Modifier.height(4.dp))
                                                     Text("Linked Patient Engagement Profile", fontSize = 8.sp, color = AppThemeManager.slateTextMedium)
                                                     Row(
                                                         verticalAlignment = Alignment.CenterVertically,
                                                         horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                     ) {
                                                         Icon(
                                                             imageVector = Icons.Default.Person,
                                                             contentDescription = null,
                                                             tint = categoryColor,
                                                             modifier = Modifier.size(10.dp)
                                                         )
                                                         Text(task.verificationCustomerName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = categoryColor)
                                                     }
                                                 }
                                                 
                                                 if (!task.verificationNotes.isNullOrBlank()) {
                                                     Spacer(modifier = Modifier.height(6.dp))
                                                     HorizontalDivider(color = AppThemeManager.okGreen.copy(alpha = 0.15f))
                                                     Spacer(modifier = Modifier.height(4.dp))
                                                     Text("Clinical resolution interaction transcript:", fontSize = 8.sp, color = AppThemeManager.slateTextMedium)
                                                     Text(
                                                         text = "“${task.verificationNotes}”",
                                                         fontSize = 11.sp,
                                                         fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                         color = MaterialTheme.colorScheme.onSurface
                                                     )
                                                 }
                                                 
                                                 if (task.verifiedAt != null) {
                                                     Spacer(modifier = Modifier.height(4.dp))
                                                     Text(
                                                         text = "Audit archived on ${android.text.format.DateFormat.format("MMM dd, yyyy 'at' hh:mm a", task.verifiedAt).toString()}",
                                                         fontSize = 8.sp,
                                                         color = AppThemeManager.slateTextMedium,
                                                         modifier = Modifier.align(Alignment.End)
                                                     )
                                                 }

                                                 // --- MANAGER SIGN-OFF & APPROVAL SECURED WORKFLOW ---
                                                 if (task.isApproved) {
                                                     Spacer(modifier = Modifier.height(8.dp))
                                                     HorizontalDivider(color = TealPrimary.copy(alpha = 0.2f))
                                                     Spacer(modifier = Modifier.height(6.dp))
                                                     Row(
                                                         verticalAlignment = Alignment.CenterVertically,
                                                         horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                     ) {
                                                         Icon(
                                                             imageVector = Icons.Default.TaskAlt,
                                                             contentDescription = "Approved",
                                                             tint = TealPrimary,
                                                             modifier = Modifier.size(14.dp)
                                                         )
                                                         Text(
                                                             text = "MANAGER APPROVAL & SIGN-OFF SECURED",
                                                             fontSize = 9.sp,
                                                             fontWeight = FontWeight.Bold,
                                                             color = TealPrimary,
                                                             letterSpacing = 0.5.sp
                                                         )
                                                     }
                                                     
                                                     Spacer(modifier = Modifier.height(4.dp))
                                                     Text("Approved By: ${task.approvedBy ?: "Branch Manager"}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                     
                                                     if (!task.approvalNotes.isNullOrBlank()) {
                                                         Spacer(modifier = Modifier.height(2.dp))
                                                         Text("Approval Remarks: “${task.approvalNotes}”", fontSize = 10.sp, color = AppThemeManager.slateTextMedium)
                                                     }
                                                     
                                                     if (task.approvedAt != null && task.approvedAt > 0L) {
                                                         Spacer(modifier = Modifier.height(2.dp))
                                                         Text(
                                                             text = "Signed off on ${android.text.format.DateFormat.format("MMM dd, yyyy 'at' hh:mm a", task.approvedAt).toString()}",
                                                             fontSize = 8.sp,
                                                             color = AppThemeManager.slateTextMedium,
                                                             modifier = Modifier.align(Alignment.End)
                                                         )
                                                     }
                                                 } else {
                                                     if (isManager) {
                                                         Spacer(modifier = Modifier.height(10.dp))
                                                         Button(
                                                             onClick = { taskForManagerApproval = task },
                                                             shape = RoundedCornerShape(8.dp),
                                                             colors = ButtonDefaults.buttonColors(
                                                                 containerColor = TealPrimary,
                                                                 contentColor = Color.Black
                                                             ),
                                                             contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                             modifier = Modifier.fillMaxWidth().height(32.dp)
                                                         ) {
                                                             Icon(
                                                                 imageVector = Icons.Default.FactCheck,
                                                                 contentDescription = null,
                                                                 modifier = Modifier.size(14.dp),
                                                                 tint = Color.Black
                                                             )
                                                             Spacer(modifier = Modifier.width(6.dp))
                                                             Text(
                                                                 text = "Review & Approve Claim",
                                                                 fontSize = 11.sp,
                                                                 fontWeight = FontWeight.Bold,
                                                                 color = Color.Black
                                                             )
                                                         }
                                                     } else {
                                                         Spacer(modifier = Modifier.height(8.dp))
                                                         HorizontalDivider(color = AppThemeManager.slateBorderLight.copy(alpha = 0.5f))
                                                         Spacer(modifier = Modifier.height(6.dp))
                                                         Row(
                                                             verticalAlignment = Alignment.CenterVertically,
                                                             horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                         ) {
                                                             Icon(
                                                                 imageVector = Icons.Default.HourglassEmpty,
                                                                 contentDescription = "Awaiting Approval",
                                                                 tint = AppThemeManager.pendingOrange,
                                                                 modifier = Modifier.size(12.dp)
                                                             )
                                                             Text(
                                                                 text = "AWAITING BRANCH MANAGER SIGN-OFF & APPROVAL",
                                                                 fontSize = 9.sp,
                                                                 fontWeight = FontWeight.Bold,
                                                                 color = AppThemeManager.pendingOrange
                                                             )
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
                                                text = assigneeName,
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
        val uid = staff["uid"]?.toString() ?: ""
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
                        viewModel.updateStaffRoleOrApproval(uid, selectedRoleState, isApprovedState)
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

    val currentVerifyTask = taskForComplianceVerification
    if (currentVerifyTask != null) {
        val task = currentVerifyTask
        var complianceNotes by remember { mutableStateOf("") }
        var selectedChannel by remember { mutableStateOf("Phone Call") }
        var linkedCustomerName by remember { mutableStateOf("") }
        var isSavingCompliance by remember { mutableStateOf(false) }

        val channels = listOf("Phone Call", "WhatsApp", "In-Person", "System/Other")

        val isStockTransfer = task.category == "Stock Transfer"
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

        AlertDialog(
            onDismissRequest = { if (!isSavingCompliance) taskForComplianceVerification = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isStockTransfer) Icons.Default.Transform else Icons.Default.FactCheck,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (isStockTransfer) "Stock Transfer Receipt" else "Clinical Task Verification",
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

                        OutlinedTextField(
                            value = complianceNotes,
                            onValueChange = { complianceNotes = it },
                            label = { Text("Verification / Condition Notes", fontSize = 12.sp) },
                            placeholder = { Text("e.g. Received intact, count verified, batch logged in shelf.") },
                            minLines = 3,
                            maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                focusedLabelColor = TealPrimary,
                                unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("transfer_receipt_notes_input")
                        )
                        
                        Text(
                            text = "Note: A minimum of 5 characters verification notes is required.",
                            fontSize = 10.sp,
                            color = if (complianceNotes.trim().length >= 5) AppThemeManager.okGreen else AppThemeManager.warningRed,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                } else {
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
                                Text(text = "TASK TO RESOLVE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
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
                            text = "To maintain strict clinical safety and operational tracing, you must document interaction evidence before closing this node assignment.",
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

                        // Clinical resolution interaction transcript
                        OutlinedTextField(
                            value = complianceNotes,
                            onValueChange = { complianceNotes = it },
                            label = { Text("Clinical Interaction Transcript / Proof Notes", fontSize = 12.sp) },
                            placeholder = { Text("Provide details of the follow-up, patient feedback, clinical adjustments, or resolution outcomes...") },
                            minLines = 3,
                            maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                focusedLabelColor = TealPrimary,
                                unfocusedBorderColor = AppThemeManager.unfocusedTextFieldBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Text(
                            text = "Note: A minimum of 10 characters descriptive proof is required to satisfy clinical audit policies.",
                            fontSize = 10.sp,
                            color = if (complianceNotes.trim().length >= 10) AppThemeManager.okGreen else AppThemeManager.warningRed,
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
                            isSavingCompliance = true
                            viewModel.verifyAndReceiveStockTransfer(task, cleanNotes) { success, msg ->
                                isSavingCompliance = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) {
                                    taskForComplianceVerification = null
                                }
                            }
                        } else {
                            val cleanPatient = linkedCustomerName.trim()
                            if (cleanPatient.isEmpty()) {
                                Toast.makeText(context, "Patient Name is required for follow-up audit", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (cleanNotes.length < 10) {
                                Toast.makeText(context, "Interaction notes must be at least 10 characters long to satisfy policy", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSavingCompliance = true
                            viewModel.verifiablyCompleteOperationTask(
                                task = task,
                                notes = cleanNotes,
                                channel = selectedChannel,
                                patientName = cleanPatient
                            ) { success, msg ->
                                isSavingCompliance = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) {
                                    taskForComplianceVerification = null
                                }
                            }
                        }
                    },
                    enabled = !isSavingCompliance && (
                        (isStockTransfer && complianceNotes.trim().length >= 5) ||
                        (!isStockTransfer && linkedCustomerName.trim().isNotEmpty() && complianceNotes.trim().length >= 10)
                    ),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    if (isSavingCompliance) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (isStockTransfer) "Verify & Receive" else "Archive Proof", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
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
}
