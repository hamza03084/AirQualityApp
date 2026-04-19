package com.example.airqualityapp.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Signup : Screen("signup")
    object CitizenHome : Screen("citizen_home")
    object AdminDashboard : Screen("admin_dashboard")
    object ManualInput : Screen("manual_input")
    object History : Screen("history")
    object Help : Screen("help")
    object Settings : Screen("settings")
    object CitizenProfile : Screen("citizen_profile")
    object AqiDetail : Screen("aqi_detail")
    object AdminUsers : Screen("admin_users")
    object AdminThresholds : Screen("admin_thresholds")
    object AdminRecommendations : Screen("admin_recommendations")
    object AdminDatasets : Screen("admin_datasets")
    object AdminFeedback : Screen("admin_feedback")
    object AdminActivity : Screen("admin_activity")
}

