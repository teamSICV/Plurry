//UI에서 안전도 쉽게 쓸 수 있게 중간 다리용
package com.SICV.plurry.safety.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.SICV.plurry.safety.SafetyRepo
import com.SICV.plurry.safety.model.SafetyDetail
import kotlinx.coroutines.launch

class SafetyViewModel(
    private val repo: SafetyRepo
) : ViewModel() {

    private val _safety = MutableLiveData<SafetyDetail?>()
    val safety: LiveData<SafetyDetail?> = _safety

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /** 안전도 평가 실행 */
    fun evaluate(lat: Double, lon: Double) {
        _loading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val detail = repo.getSafety(lat, lon)     // suspend
                _safety.value = detail
            } catch (t: Throwable) {
                _error.value = t.message ?: "unknown error"
                // 안전한 폴백 값
                _safety.value = SafetyDetail(
                    score = 50,
                    level = SafetyDetail.Level.CAUTION,
                    convCount = 0,
                    publicCount = 0,
                    subwayCount = 0,
                    cctvCount = 0,
                    streetLightCount = 0,
                    reasons = listOf("fallback")
                )
            } finally {
                _loading.value = false
            }
        }
    }
}
