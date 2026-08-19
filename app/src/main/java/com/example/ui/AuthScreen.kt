package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppThemeManager
import com.example.ui.theme.TealPrimary
import com.example.R
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthUser
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: (AuthUser) -> Unit,
    onShowOnboarding: (() -> Unit)? = null,
    authRepository: AuthRepository = remember { AuthRepository() },
    remoteDataSource: com.example.data.remote.RemoteDataSource = remember { com.example.data.remote.FirestoreRemoteDataSourceImpl() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Branch registration and joining states
    var registerNewBranch by remember { mutableStateOf(false) }
    var signUpBranchCode by remember { mutableStateOf("") }
    var signUpBranchName by remember { mutableStateOf("") }
    var signUpBranchLga by remember { mutableStateOf("") }
    var signUpBranchState by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("Pharmacist") } // "Pharmacist", "Intern Pharmacist", "Technician"
    
    // Manage email verification screen if not verified
    var pendingVerificationUser by remember { mutableStateOf<AuthUser?>(null) }
    
    // Check initially if currentUser needs verification
    LaunchedEffect(Unit) {
        val currentUser = authRepository.getCurrentUser()
        if (currentUser != null) {
            if (currentUser.isEmailVerified || isGoogleProvider(currentUser)) {
                onAuthSuccess(currentUser)
            } else {
                pendingVerificationUser = currentUser
            }
        }
    }

    // Google Sign-In setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("214615316254-k3rj9rk3q6v5ach7vhc5r2l46hipjuvv.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data != null) {
            isLoading = true
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    scope.launch {
                        val authResult = authRepository.signInWithGoogleIdToken(idToken)
                        isLoading = false
                        authResult.fold(
                            onSuccess = { user ->
                                Toast.makeText(context, "Welcome ${user.displayName ?: "User"}", Toast.LENGTH_SHORT).show()
                                onAuthSuccess(user)
                            },
                            onFailure = { ex ->
                                Toast.makeText(context, "Google Sign-In failed: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                } else {
                    isLoading = false
                    Toast.makeText(context, "Failed to get Google ID Token", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                isLoading = false
                if (e is com.google.android.gms.common.api.ApiException) {
                    val explanation = when(e.statusCode) {
                        10 -> "DEVELOPER_ERROR (Code 10). This indicates the debug app's SHA-1 fingerprint signature is not registered in your Firebase Console. Please register this specific build's SHA-1."
                        12500 -> "Sign-in Failed (Code 12500). Please configure Google Sign-In in your Firebase Auth dashboard."
                        else -> "Google Sign-In Error Code ${e.statusCode}: ${e.localizedMessage}"
                    }
                    Toast.makeText(context, explanation, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Google Sign-In Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (pendingVerificationUser != null) {
        val user = pendingVerificationUser!!
        EmailVerificationScreen(
            user = user,
            authRepository = authRepository,
            onVerified = {
                onAuthSuccess(user)
            },
            onCancel = {
                authRepository.signOut()
                pendingVerificationUser = null
            }
        )
        return
    }

    val isDark = AppThemeManager.isDark
    
    // Core background theme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9))
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            // 2. Beautiful branding title
            Spacer(modifier = Modifier.height(32.dp))
            Image(
                painter = painterResource(id = R.drawable.ic_careflux_logo),
                contentDescription = "Careflux Logo (Tap to view app tour)",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable(enabled = onShowOnboarding != null) {
                        onShowOnboarding?.invoke()
                    }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "CarefluxRx",
                fontWeight = FontWeight.Black,
                fontSize = 32.sp,
                color = if (isDark) Color.White else Color(0xFF1E293B),
                letterSpacing = 1.sp
            )
            Text(
                text = "A registered product of Wellivox",
                fontSize = 13.sp,
                color = (if (isDark) Color.White else Color(0xFF1E293B)).copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 3. Welcome Sign In Content card matching styling in image
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp, 
                        if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0), 
                        RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E293B) else Color.White
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    
                    Text(
                        text = if (isSignUp) "Create Account" else "Welcome Back",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = if (isDark) Color.White else Color(0xFF0F172A)
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = if (isSignUp) "Join CarefluxRx today" else "Sign in to your account",
                        fontSize = 14.sp,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )
                    
                    Spacer(modifier = Modifier.height(28.dp))
                    
                    if (isSignUp) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_name_field"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Phone Number") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_phone_field"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Branch selection controls (Join Branch vs Register Branch)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { registerNewBranch = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!registerNewBranch) TealPrimary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Join Branch",
                                    color = if (!registerNewBranch) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = { registerNewBranch = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (registerNewBranch) TealPrimary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Register Branch",
                                    color = if (registerNewBranch) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        if (!registerNewBranch) {
                            // Join Existing Branch Inputs
                            OutlinedTextField(
                                value = signUpBranchCode,
                                onValueChange = { signUpBranchCode = it.trim().uppercase() },
                                label = { Text("Branch Code") },
                                placeholder = { Text("e.g. CF-123456") },
                                leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = "Branch Code") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_branch_code_field"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Select Your Professional Role:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val roles = listOf("Pharmacist", "Intern Pharmacist", "Technician")
                                roles.forEach { role ->
                                    val isSelected = selectedRole == role
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) TealPrimary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { selectedRole = role }
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) TealPrimary else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = role,
                                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        } else {
                            // Register New Branch Inputs
                            OutlinedTextField(
                                value = signUpBranchName,
                                onValueChange = { signUpBranchName = it },
                                label = { Text("New Branch Name") },
                                placeholder = { Text("e.g. WELLIVOX HQ") },
                                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = "Branch Name") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_branch_name_field"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = signUpBranchLga,
                                    onValueChange = { signUpBranchLga = it },
                                    label = { Text("LGA") },
                                    placeholder = { Text("e.g. Ikeja") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("auth_branch_lga_field"),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = signUpBranchState,
                                    onValueChange = { signUpBranchState = it },
                                    label = { Text("State") },
                                    placeholder = { Text("e.g. Lagos") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("auth_branch_state_field"),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = TealPrimary.copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = "Role info", tint = TealPrimary)
                                    Column {
                                        Text(
                                            text = "Role: Branch Manager",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isDark) Color.White else Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "As the registerer, you are designated the Branch Manager and approved automatically.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_field"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showPassword) "Hide Password" else "Show Password"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_field"),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank() || (isSignUp && (name.isBlank() || phoneNumber.isBlank()))) {
                                Toast.makeText(context, "Please fill in all details", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            isLoading = true
                            if (isSignUp) {
                                val normPhone = phoneNumber.trim().replace(Regex("[^+\\d]"), "")
                                if (normPhone.isBlank()) {
                                    Toast.makeText(context, "Please enter your phone number", Toast.LENGTH_SHORT).show()
                                    isLoading = false
                                    return@Button
                                }
                                
                                val remoteDataSource = com.example.data.remote.FirestoreRemoteDataSourceImpl()
                                scope.launch {
                                    val phoneCheck = remoteDataSource.getDocumentsWhereEquals("registered_pharmacists", "phoneNumber", normPhone)
                                    if (phoneCheck.isSuccess && phoneCheck.getOrNull()?.isNotEmpty() == true) {
                                        isLoading = false
                                        Toast.makeText(context, "Sign Up Failed: This phone number is already registered by another pharmacist!", Toast.LENGTH_LONG).show()
                                    } else {
                                        if (!registerNewBranch) {
                                            // Join Branch Verification
                                            val cleanCode = signUpBranchCode.trim().uppercase()
                                            if (cleanCode.isBlank()) {
                                                Toast.makeText(context, "Please enter a valid Branch Code to join", Toast.LENGTH_SHORT).show()
                                                isLoading = false
                                                return@launch
                                            }
                                            val branchDoc = remoteDataSource.getDocument("branches", cleanCode).getOrNull()
                                            if (branchDoc != null) {
                                                val bName = branchDoc["name"] as? String ?: "Careflux Pharmacy"
                                                
                                                // Proceed with creation
                                                val createRes = authRepository.createUserWithEmailAndPassword(email, password)
                                                createRes.fold(
                                                    onSuccess = { user ->
                                                        authRepository.updateDisplayName(name.trim())
                                                        val devId = context.getSharedPreferences("careflux_prefs", Context.MODE_PRIVATE)
                                                            .getString("device_uuid", "Unknown") ?: "Unknown"
                                                        val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                                                        
                                                        val pharmacistMap = hashMapOf<String, Any?>(
                                                            "uid" to user.uid,
                                                            "email" to (user.email ?: ""),
                                                            "displayName" to name.trim(),
                                                            "phoneNumber" to normPhone,
                                                            "deviceId" to devId,
                                                            "deviceModel" to deviceModel,
                                                            "registeredAt" to System.currentTimeMillis(),
                                                            "lastLoginAt" to System.currentTimeMillis(),
                                                            "branchId" to cleanCode,
                                                            "branchName" to bName,
                                                            "role" to selectedRole,
                                                            "isApproved" to true
                                                        )
                                                        scope.launch {
                                                            remoteDataSource.upsertDocument("registered_pharmacists", user.uid, pharmacistMap)
                                                        }
                                                        
                                                        scope.launch {
                                                            val verifRes = authRepository.sendEmailVerification()
                                                            isLoading = false
                                                            if (verifRes.isSuccess) {
                                                                Toast.makeText(context, "Verification email sent. Please check your inbox.", Toast.LENGTH_LONG).show()
                                                            } else {
                                                                Toast.makeText(context, "Created successfully but failed to send email verification.", Toast.LENGTH_SHORT).show()
                                                            }
                                                            pendingVerificationUser = user
                                                        }
                                                    },
                                                    onFailure = { ex ->
                                                        isLoading = false
                                                        Toast.makeText(context, "Sign Up Failed: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            } else {
                                                isLoading = false
                                                Toast.makeText(context, "Branch Code '$cleanCode' not found. Please contact your Branch Manager.", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            // Register New Branch
                                            val cleanBranchName = signUpBranchName.trim()
                                            val cleanLga = signUpBranchLga.trim().ifBlank { "Ikeja" }
                                            val cleanState = signUpBranchState.trim().ifBlank { "Lagos" }
                                            if (cleanBranchName.isBlank()) {
                                                Toast.makeText(context, "Please enter a name for the new branch", Toast.LENGTH_SHORT).show()
                                                isLoading = false
                                                return@launch
                                            }
                                            
                                            val randomCode = "CF-" + (100000..999999).random().toString()
                                            
                                            val createRes = authRepository.createUserWithEmailAndPassword(email, password)
                                            createRes.fold(
                                                onSuccess = { user ->
                                                    authRepository.updateDisplayName(name.trim())
                                                    val devId = context.getSharedPreferences("careflux_prefs", Context.MODE_PRIVATE)
                                                        .getString("device_uuid", "Unknown") ?: "Unknown"
                                                    val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                                                    
                                                    // Create Branch document first
                                                    val branchMap = hashMapOf<String, Any?>(
                                                        "id" to randomCode,
                                                        "name" to cleanBranchName,
                                                        "lga" to cleanLga,
                                                        "state" to cleanState,
                                                        "createdBy" to user.uid,
                                                        "createdAt" to System.currentTimeMillis()
                                                    )
                                                    scope.launch {
                                                        remoteDataSource.upsertDocument("branches", randomCode, branchMap)
                                                    }
                                                    
                                                    // Then create Pharmacist document as Branch Manager
                                                    val pharmacistMap = hashMapOf<String, Any?>(
                                                        "uid" to user.uid,
                                                        "email" to (user.email ?: ""),
                                                        "displayName" to name.trim(),
                                                        "phoneNumber" to normPhone,
                                                        "deviceId" to devId,
                                                        "deviceModel" to deviceModel,
                                                        "registeredAt" to System.currentTimeMillis(),
                                                        "lastLoginAt" to System.currentTimeMillis(),
                                                        "branchId" to randomCode,
                                                        "branchName" to cleanBranchName,
                                                        "role" to "Branch Manager",
                                                        "isApproved" to true
                                                    )
                                                    scope.launch {
                                                        remoteDataSource.upsertDocument("registered_pharmacists", user.uid, pharmacistMap)
                                                    }
                                                    
                                                    scope.launch {
                                                        val verifRes = authRepository.sendEmailVerification()
                                                        isLoading = false
                                                        if (verifRes.isSuccess) {
                                                            Toast.makeText(context, "Verification email sent. Your generated Branch Code is $randomCode . Please check your inbox.", Toast.LENGTH_LONG).show()
                                                        } else {
                                                            Toast.makeText(context, "Created successfully but failed to send email verification. Branch Code: $randomCode", Toast.LENGTH_SHORT).show()
                                                        }
                                                        pendingVerificationUser = user
                                                    }
                                                },
                                                onFailure = { ex ->
                                                    isLoading = false
                                                    Toast.makeText(context, "Sign Up Failed: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Real Auth Sign In with Email Verification Enforcement
                                scope.launch {
                                    val loginRes = authRepository.signInWithEmailAndPassword(email, password)
                                    isLoading = false
                                    loginRes.fold(
                                        onSuccess = { user ->
                                            if (user.isEmailVerified) {
                                                // Save pharmacist details/login stats
                                                val devId = context.getSharedPreferences("careflux_prefs", Context.MODE_PRIVATE)
                                                    .getString("device_uuid", "Unknown") ?: "Unknown"
                                                val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                                                
                                                val updateMap = hashMapOf<String, Any?>(
                                                    "uid" to user.uid,
                                                    "email" to (user.email ?: ""),
                                                    "deviceId" to devId,
                                                    "deviceModel" to deviceModel,
                                                    "lastLoginAt" to System.currentTimeMillis()
                                                )
                                                scope.launch {
                                                    try {
                                                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                            .collection("registered_pharmacists")
                                                            .document(user.uid)
                                                            .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
                                                    } catch (e: Exception) {
                                                        // Non-blocking telemetry sync
                                                    }
                                                }

                                                Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show()
                                                onAuthSuccess(user)
                                            } else {
                                                Toast.makeText(context, "Please verify your email address before logging in.", Toast.LENGTH_LONG).show()
                                                pendingVerificationUser = user
                                            }
                                        },
                                        onFailure = { ex ->
                                            Toast.makeText(context, "Log In Failed: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B82F6) // Matches the sleek blue button in the image
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("auth_action_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = if (isSignUp) "Sign Up" else "Sign In",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(18.dp))
                    
                    // Simple "OR" separator matching the clean layout in image
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                        Text(
                            text = "OR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                    }
                    
                    Spacer(modifier = Modifier.height(18.dp))
                    
                    // Google Sign-In Button with colorful Google G icon
                    OutlinedButton(
                        onClick = {
                            val intent = googleSignInClient.signInIntent
                            googleSignInLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.dp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("google_auth_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Render a nice Google icon matching the image structure
                            Icon(
                                imageVector = Icons.Default.AccountCircle, // Elegant alternative vector if drawable is absent
                                tint = Color(0xFFDB4437),
                                contentDescription = "Google Icon",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Continue with Google",
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                fontSize = 15.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(28.dp))
                    
                    // Link to toggle registration modes matching the shared image
                    Text(
                        text = if (isSignUp) "Already have an account? Sign In" else "Don't have an account? Sign up",
                        color = Color(0xFF3B82F6),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable {
                                isSignUp = !isSignUp
                            }
                            .testTag("toggle_auth_mode")
                    )
                }
            }
            
            // Allow user to toggle light/dark theme dynamically on the login screen too!
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDark) Color(0xFF1E293B) else Color.White)
                    .clickable { 
                        AppThemeManager.isDark = !AppThemeManager.isDark 
                        // Persist theme choice immediately
                        context.getSharedPreferences("careflux_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("theme_dark", AppThemeManager.isDark)
                            .apply()
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = if (isDark) Color.Yellow else Color(0xFF0F172A),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Careflux is a product under Wellivox, a registered company.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = (if (isDark) Color.White else Color(0xFF1E293B)).copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "All healthcare operations on CarefluxRx™ are securely documented.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = (if (isDark) Color.White else Color(0xFF1E293B)).copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun EmailVerificationScreen(
    user: AuthUser,
    authRepository: AuthRepository = remember { AuthRepository() },
    onVerified: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = AppThemeManager.isDark
    var isChecking by remember { mutableStateOf(false) }
    var resendCooldown by remember { mutableStateOf(0) }
    
    // Auto check verification status every 5 seconds until verified
    LaunchedEffect(Unit) {
        while (true) {
            val reloadRes = authRepository.reloadUser()
            reloadRes.getOrNull()?.let { updatedUser ->
                if (updatedUser.isEmailVerified) {
                    onVerified()
                }
            }
            delay(5000)
        }
    }

    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown--
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp, 
                    if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0), 
                    RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1E293B) else Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.MarkEmailRead,
                    contentDescription = "Verify Email",
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(72.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Verify Your Email",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "We sent a verification link to:\n${user.email}\n\nPlease click the link in your inbox to proceed.",
                    fontSize = 14.sp,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            isChecking = true
                            val reloadRes = authRepository.reloadUser()
                            isChecking = false
                            reloadRes.fold(
                                onSuccess = { updatedUser ->
                                    if (updatedUser.isEmailVerified) {
                                        Toast.makeText(context, "Email Verified Successfully!", Toast.LENGTH_SHORT).show()
                                        onVerified()
                                    } else {
                                        Toast.makeText(context, "Verification email still check pending. Please wait or reload.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                onFailure = { ex ->
                                    Toast.makeText(context, "Error reloading user data: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("I've Clicked the Link", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = {
                        if (resendCooldown == 0) {
                            scope.launch {
                                val sendRes = authRepository.sendEmailVerification()
                                sendRes.fold(
                                    onSuccess = {
                                        Toast.makeText(context, "Verification email sent!", Toast.LENGTH_SHORT).show()
                                        resendCooldown = 60
                                    },
                                    onFailure = { ex ->
                                        Toast.makeText(context, "Failed to send link: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    },
                    enabled = resendCooldown == 0,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = if (resendCooldown > 0) "Resend in ${resendCooldown}s" else "Resend Verification Email",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Sign Out & Cancel",
                    color = Color.Red.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { onCancel() }
                        .padding(8.dp)
                )
            }
        }
    }
}

// Utility function to detect Google Auth providers so we do not enforce email verification screen on standard google logins
fun isGoogleProvider(user: AuthUser): Boolean {
    return user.providerIds.contains("google.com")
}
