package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object CheckerState {
    // Basic stat counts
    val totalCount = MutableStateFlow(0)
    val checkedCount = MutableStateFlow(0)
    val successCount = MutableStateFlow(0)
    val failureCount = MutableStateFlow(0)
    
    // Testing state
    val isChecking = MutableStateFlow(false)
    
    // Current testing account
    val currentEmail = MutableStateFlow("")
    val currentPassword = MutableStateFlow("")
    
    // Loaded list of pairs (Email, Password)
    var combosList: List<Pair<String, String>> = emptyList()
    
    // Custom endpoint URL
    val currentCustomUrl = MutableStateFlow("https://bosforlab.online/vapi.php")
    
    // Voice / sound alert settings
    val soundEnabled = MutableStateFlow(true)
    
    // Simulation / Safe-demonstration mode
    val simulationMode = MutableStateFlow(false)

    // Reset stats to zero
    fun reset(total: Int, list: List<Pair<String, String>>) {
        totalCount.value = total
        checkedCount.value = 0
        successCount.value = 0
        failureCount.value = 0
        currentEmail.value = ""
        currentPassword.value = ""
        combosList = list
    }

    // Stop and clear
    fun stop() {
        isChecking.value = false
    }
}
