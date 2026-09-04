package com.example.airqualityapp.ui.help

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airqualityapp.util.AqiClassifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var feedbackText by remember { mutableStateOf("") }
    var feedbackSubject by remember { mutableStateOf("") }
    var isSubmittingFeedback by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // AQI Guide
            Text("📚 Understanding AQI", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "The Air Quality Index (AQI) is a standardized indicator for reporting daily air quality. " +
                        "It tells you how clean or polluted your air is and what associated health effects might be of concern.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // AQI Categories
                    AqiClassifier.getAllCategories().forEach { (range, _, category) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = category.color.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("●", color = category.color, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${category.label} ($range)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Text(category.healthImpact, fontSize = 12.sp, color = Color.Gray)
                                Text("💡 ${category.recommendation}", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // How to Use This App
            Text("🧭 How to Use This App", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TutorialStep("1", "Sign in", "Register or log in with email/password or Google.")
                    TutorialStep("2", "View live AQI", "The Home screen auto-fetches AQI for your selected city every 60 minutes.")
                    TutorialStep("3", "Manual prediction", "Tap Manual Input to enter PM2.5, PM10, temperature, humidity and wind speed — the on-device ML model returns an instant AQI even offline.")
                    TutorialStep("4", "Review history", "Open History to browse Today / Week / Month trends with a visual chart.")
                    TutorialStep("5", "Configure alerts", "In Settings, set your Preferred City and alert threshold. You'll get a push notification when AQI exceeds it.")
                    TutorialStep("6", "Help & feedback", "Read the AQI guide and FAQs below, or send feedback if you hit an issue.")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // FAQs
            Text("❓ Frequently Asked Questions", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))

            val faqs = listOf(
                "What is AQI?" to "AQI stands for Air Quality Index. It's a number used by government agencies to communicate how polluted the air currently is or how polluted it is forecast to become.",
                "How is AQI calculated?" to "AQI is calculated based on concentrations of pollutants like PM2.5, PM10, Ozone, NO2, SO2, and CO. The highest individual pollutant AQI becomes the overall AQI.",
                "What are PM2.5 and PM10?" to "PM2.5 refers to fine particulate matter with diameter less than 2.5 micrometers. PM10 refers to particles with diameter less than 10 micrometers. Both can penetrate the lungs and affect health.",
                "How accurate are the predictions?" to "Our AI model uses machine learning trained on historical data. While predictions provide good estimates, actual conditions may vary. Always check real-time data for critical decisions.",
                "Does the app work offline?" to "Yes! You can view the last fetched AQI data and submit manual readings offline. Data automatically syncs when you reconnect to the internet.",
                "How do I get alerts?" to "Alerts are sent automatically when AQI in your area crosses unsafe thresholds. You can configure the threshold in Settings.",
                "Who should be extra cautious?" to "Children, elderly people, pregnant women, and those with respiratory conditions (asthma, COPD) or heart disease are more sensitive to air pollution."
            )

            faqs.forEach { (question, answer) ->
                FaqItem(question = question, answer = answer)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Health Guide
            Text("🏥 Health Protection Guide", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    HealthTip("🏠", "Stay indoors when AQI is above 150")
                    HealthTip("😷", "Wear N95 masks in polluted areas")
                    HealthTip("🪟", "Keep windows closed during high pollution")
                    HealthTip("🌬️", "Use air purifiers if available")
                    HealthTip("💧", "Stay hydrated - drink plenty of water")
                    HealthTip("🏃", "Avoid outdoor exercise during poor AQI")
                    HealthTip("🌱", "Keep indoor plants for natural air purification")
                    HealthTip("👶", "Keep children indoors during high pollution")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Feedback Form
            Text("📝 Send Feedback", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = feedbackSubject,
                        onValueChange = { feedbackSubject = it },
                        label = { Text("Subject") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        label = { Text("Your Feedback / Report Issue") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (feedbackText.isBlank()) {
                                Toast.makeText(context, "Please enter feedback", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSubmittingFeedback = true
                            scope.launch {
                                try {
                                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
                                    FirebaseFirestore.getInstance().collection("feedback")
                                        .add(mapOf(
                                            "subject" to feedbackSubject,
                                            "message" to feedbackText,
                                            "userId" to uid,
                                            "email" to (FirebaseAuth.getInstance().currentUser?.email ?: ""),
                                            "timestamp" to System.currentTimeMillis()
                                        )).await()

                                    feedbackSubject = ""
                                    feedbackText = ""
                                    Toast.makeText(context, "Feedback submitted! Thank you.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to submit: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                isSubmittingFeedback = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmittingFeedback,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSubmittingFeedback) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submit Feedback")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quran verse
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📖 Inspiration", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "\"And the movement of the winds, and the submissive clouds between heaven and earth; " +
                        "certainly, in all these are signs (to recognize Allah) for the intelligent ones.\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "— The Holy Quran: Para 2, Surah 2 Al-Baqarah, Verse 164",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    question,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(answer, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun TutorialStep(number: String, title: String, description: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun HealthTip(emoji: String, tip: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(tip, style = MaterialTheme.typography.bodyMedium)
    }
}

