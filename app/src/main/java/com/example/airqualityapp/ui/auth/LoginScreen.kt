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
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.airqualityapp.R
import com.example.airqualityapp.data.repository.UserProfile
import com.example.airqualityapp.data.repository.UserRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

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
    var googleLoading by remember { mutableStateOf(false) }
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

                scope.launch {
                    try {
                        withTimeout(15_000) {
                            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
                            // Force-refresh ID token so Firestore SDK picks up fresh auth state
                            // after a prior logout in the same process — otherwise the next
                            // Firestore read can hang indefinitely.
                            result.user?.getIdToken(true)?.await()
                            val profile = userRepository.getOrCreateCurrentUserProfile()
                                ?: error("Signed in, but Firebase session is not ready yet.")
                            val role = profile.role.ifBlank { "citizen" }
                            Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                            onLoginSuccess(role)
                        }
                    } catch (_: TimeoutCancellationException) {
                        errorMessage = "Login timed out. Please check your internet and try again."
                    } catch (e: Exception) {
                        errorMessage = e.toAuthErrorMessage("Login failed")
                    } finally {
                        loading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            enabled = !loading && !googleLoading
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
                    googleLoading = true
                    errorMessage = ""
                    try {
                        withTimeout(30_000) {
                            doGoogleLogin(context, auth, userRepository, webClientId,
                                onSuccess = { role ->
                                    onLoginSuccess(role)
                                },
                                onError = { error ->
                                    errorMessage = error
                                }
                            )
                        }
                    } catch (_: TimeoutCancellationException) {
                        errorMessage = "Google login timed out. Please try again."
                    } finally {
                        googleLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            enabled = !loading && !googleLoading
        ) {
            if (googleLoading)
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp)
                )
            else
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
        .setAutoSelectEnabled(false)
        .setNonce(null)
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

        // Extract Google ID token from credential
        val googleIdToken: String? = when {
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            }
            credential is GoogleIdTokenCredential -> {
                credential.idToken
            }
            else -> null
        }

        if (googleIdToken == null) {
            onError("Unexpected credential type: ${credential.type}")
            return
        }

        // Sign in to Firebase using suspend/await (runs on caller's coroutine context)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        val authResult = auth.signInWithCredential(firebaseCredential).await()
        // Force-refresh so Firestore SDK picks up fresh auth state (avoids hang after
        // prior logout in same process).
        authResult.user?.getIdToken(true)?.await()

        val profile = userRepository.getOrCreateCurrentUserProfile(defaultRole = "citizen")
            ?: error("Signed in, but Firebase session is not ready yet.")

        // onSuccess is now called on the main thread (caller's coroutine scope)
        onSuccess(profile.role)

    } catch (e: NoCredentialException) {
        onError("No Google account was found on this device. Add a Google account in your device settings, then try again.")
    } catch (e: GetCredentialException) {
        onError(e.message ?: "Google login failed")
    } catch (e: FirebaseAuthUserCollisionException) {
        // This email was already registered with email/password (or another provider).
        // Firebase blocks Google sign-in for it unless the providers are linked.
        onError("This email is already registered with a password. Please log in with your email and password instead.")
    } catch (e: Exception) {
        onError(e.toAuthErrorMessage("An unexpected error occurred"))
    }
}

private fun Throwable.toAuthErrorMessage(defaultMessage: String): String {
    val rawMessage = message ?: return defaultMessage
    return when {
        rawMessage.contains("PERMISSION_DENIED", ignoreCase = true) ||
            rawMessage.contains("insufficient permissions", ignoreCase = true) ->
            "Firebase denied access to your profile. Publish the latest Firestore rules, then try again."
        else -> rawMessage
    }
}

