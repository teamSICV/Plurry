package com.SICV.plurry.safety.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.SICV.plurry.safety.SafetyRepo
import kotlinx.coroutines.launch

class SafetyViewModel(
    private val repo: SafetyRepo
) : ViewModel() {

    private val _safety = MutableLiveData<SafetyRepo.Detail?>()
    val safety: LiveData<SafetyRepo.Detail?> = _safety

    fun evaluate(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val detail = repo.getSafety(lat, lon)
                _safety.value = detail
            } catch (e: Exception) {
                _safety.value = SafetyRepo.Detail(
                    score = 60, // 기본값: 보통 수준
                    level = SafetyRepo.Level.CAUTION,
                    convCount = 0, busStopCount = 0,
                    cctvCount = 0, streetLightLevel = 0,
                    reasons = listOf("네트워크 오류: ${e.message}")
                )
            }
        }
    }
}
