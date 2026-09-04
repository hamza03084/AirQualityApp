package com.example.airqualityapp.ui.admin

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airqualityapp.data.repository.AdminRepository
import com.example.airqualityapp.data.repository.AqiThreshold
import com.example.airqualityapp.util.AqiConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminThresholdsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val adminRepository = remember { AdminRepository() }
    val scope = rememberCoroutineScope()

    var thresholds by remember { mutableStateOf(AqiThreshold()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var goodMax by remember { mutableStateOf("50") }
    var moderateMax by remember { mutableStateOf("100") }
    var unhealthySensitiveMax by remember { mutableStateOf("150") }
    var unhealthyMax by remember { mutableStateOf("200") }
    var veryUnhealthyMax by remember { mutableStateOf("300") }
    var alertThreshold by remember { mutableStateOf("150") }
    var validationError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val t = adminRepository.getThresholds()
        thresholds = t
        goodMax = t.goodMax.toString()
        moderateMax = t.moderateMax.toString()
        unhealthySensitiveMax = t.unhealthySensitiveMax.toString()
        unhealthyMax = t.unhealthyMax.toString()
        veryUnhealthyMax = t.veryUnhealthyMax.toString()
        alertThreshold = t.alertThreshold.toString()
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AQI Thresholds") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    "Configure AQI Classification Thresholds",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    "Set the maximum AQI value for each category",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(20.dp))

                ThresholdField("🟢 Good (Max)", goodMax) { goodMax = it }
                ThresholdField("🟡 Moderate (Max)", moderateMax) { moderateMax = it }
                ThresholdField("🟠 Unhealthy for Sensitive (Max)", unhealthySensitiveMax) { unhealthySensitiveMax = it }
                ThresholdField("🔴 Unhealthy (Max)", unhealthyMax) { unhealthyMax = it }
                ThresholdField("🟣 Very Unhealthy (Max)", veryUnhealthyMax) { veryUnhealthyMax = it }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "🔔 Alert Threshold",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    "Citizens receive push notifications when AQI exceeds this value",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = alertThreshold,
                    onValueChange = { alertThreshold = it },
                    label = { Text("Alert Threshold") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "These values define the AQI bands shown to citizens and the alert level used for notifications. " +
                        "Each maximum must be greater than the previous one.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )

                if (validationError.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(validationError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val values = listOf(goodMax, moderateMax, unhealthySensitiveMax, unhealthyMax, veryUnhealthyMax, alertThreshold)
                        if (values.any { it.trim().toIntOrNull() == null }) {
                            validationError = "All thresholds must be whole numbers."
                            return@Button
                        }
                        val updated = AqiThreshold(
                            goodMax = goodMax.trim().toInt(),
                            moderateMax = moderateMax.trim().toInt(),
                            unhealthySensitiveMax = unhealthySensitiveMax.trim().toInt(),
                            unhealthyMax = unhealthyMax.trim().toInt(),
                            veryUnhealthyMax = veryUnhealthyMax.trim().toInt(),
                            alertThreshold = alertThreshold.trim().toInt()
                        )
                        if (!AqiConfig.isValid(updated)) {
                            validationError = "Values must be positive and strictly increasing: Good < Moderate < Sensitive < Unhealthy < Very Unhealthy."
                            return@Button
                        }
                        validationError = ""
                        scope.launch {
                            isSaving = true
                            try {
                                adminRepository.updateThresholds(updated)
                                Toast.makeText(context, "Thresholds saved and applied to all users", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                            isSaving = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Thresholds", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThresholdField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

