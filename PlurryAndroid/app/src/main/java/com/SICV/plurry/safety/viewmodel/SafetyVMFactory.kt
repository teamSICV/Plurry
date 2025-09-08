package com.SICV.plurry.safety.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.SICV.plurry.safety.SafetyRepo

class SafetyVMFactory(
    private val repo: SafetyRepo
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SafetyViewModel(repo) as T
    }
}
