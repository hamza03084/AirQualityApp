package com.example.airqualityapp.ui.auth

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.airqualityapp.R
import com.example.airqualityapp.data.repository.UserProfile
import com.example.airqualityapp.data.repository.UserRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (role: String) -> Unit,
    onSignupClick: () -> Unit
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val userRepository = remember { UserRepository() }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val webClientId = stringResource(R.string.default_web_client_id)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Logo/Icon
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = "Air Quality",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Air Quality Monitor",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Smart AQI Prediction & Alert System",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Show error message
        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Login Button
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Please fill all fields"
                    return@Button
                }

                loading = true
                errorMessage = ""

                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        scope.launch {
                            val profile = userRepository.getCurrentUserProfile()
                            loading = false
                            val role = profile?.role ?: "citizen"
                            Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                            onLoginSuccess(role)
                        }
                    }
                    .addOnFailureListener {
                        loading = false
                        errorMessage = it.message ?: "Login failed"
                    }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            enabled = !loading
        ) {
            if (loading)
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            else
                Text("Login", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Google Login Button
        OutlinedButton(
            onClick = {
                scope.launch {
                    loading = true
                    doGoogleLogin(context, auth, userRepository, webClientId,
                        onSuccess = { role ->
                            loading = false
                            onLoginSuccess(role)
                        },
                        onError = { error ->
                            loading = false
                            errorMessage = error
                        }
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            enabled = !loading
        ) {
            Text("Login with Google")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sign Up Link
        TextButton(
            onClick = onSignupClick,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Don't have an account? Sign Up")
        }
    }
}

private suspend fun doGoogleLogin(
    context: Context,
    auth: FirebaseAuth,
    userRepository: UserRepository,
    webClientId: String,
    onSuccess: (role: String) -> Unit,
    onError: (String) -> Unit
) {
    val credentialManager = CredentialManager.create(context)

    val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    try {
        val result = credentialManager.getCredential(
            context = context,
            request = request
        )
        val credential = result.credential

        if (credential is GoogleIdTokenCredential) {
            val googleIdToken = credential.idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            auth.signInWithCredential(firebaseCredential)
                .addOnSuccessListener { authResult ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        val uid = authResult.user?.uid ?: ""
                        var profile = userRepository.getUserProfile(uid)
                        if (profile == null) {
                            // First time Google login - create profile as citizen
                            profile = UserProfile(
                                uid = uid,
                                email = authResult.user?.email ?: "",
                                name = authResult.user?.displayName ?: "",
                                role = "citizen"
                            )
                            userRepository.createUserProfile(profile)
                        }
                        onSuccess(profile.role)
                    }
                }
                .addOnFailureListener {
                    onError(it.message ?: "Firebase authentication failed")
                }
        }
    } catch (e: GetCredentialException) {
        onError(e.message ?: "Google login failed")
    } catch (e: Exception) {
        onError(e.message ?: "An unexpected error occurred")
    }
}

