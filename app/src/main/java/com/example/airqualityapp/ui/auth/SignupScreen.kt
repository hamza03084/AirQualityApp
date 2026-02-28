package com.example.airqualityapp.ui.auth

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airqualityapp.data.repository.UserProfile
import com.example.airqualityapp.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onSignupSuccess: (role: String) -> Unit,
    onLoginClick: () -> Unit
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val userRepository = remember { UserRepository() }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("citizen") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

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
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Join Air Quality Monitor",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Full Name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

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
                        contentDescription = null
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Confirm Password
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Role Selection
        Text(
            text = "Select Role",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FilterChip(
                selected = selectedRole == "citizen",
                onClick = { selectedRole = "citizen" },
                label = { Text("👤 Citizen") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedRole == "admin",
                onClick = { selectedRole = "admin" },
                label = { Text("🛡️ Admin") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Error message
        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Sign Up Button
        Button(
            onClick = {
                when {
                    name.isBlank() || email.isBlank() || password.isBlank() -> {
                        errorMessage = "Please fill all fields"
                        return@Button
                    }
                    password != confirmPassword -> {
                        errorMessage = "Passwords do not match"
                        return@Button
                    }
                    password.length < 6 -> {
                        errorMessage = "Password must be at least 6 characters"
                        return@Button
                    }
                }

                loading = true
                errorMessage = ""

                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { authResult ->
                        scope.launch {
                            try {
                                val uid = authResult.user?.uid ?: ""
                                val profile = UserProfile(
                                    uid = uid,
                                    email = email,
                                    name = name,
                                    role = selectedRole
                                )
                                userRepository.createUserProfile(profile)
                                loading = false
                                Toast.makeText(context, "Account Created!", Toast.LENGTH_SHORT).show()
                                onSignupSuccess(selectedRole)
                            } catch (e: Exception) {
                                loading = false
                                errorMessage = e.message ?: "Failed to create profile"
                            }
                        }
                    }
                    .addOnFailureListener {
                        loading = false
                        errorMessage = it.message ?: "Sign up failed"
                    }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            enabled = !loading
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text("Sign Up", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Login Link
        TextButton(
            onClick = onLoginClick,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Already have an account? Login")
        }
    }
}

