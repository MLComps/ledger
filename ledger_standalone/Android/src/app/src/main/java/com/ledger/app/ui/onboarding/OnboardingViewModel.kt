package com.ledger.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.DataStoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
  private val dataStoreRepository: DataStoreRepository,
) : ViewModel() {

  private val _hasSeenOnboarding = MutableStateFlow<Boolean?>(null)
  val hasSeenOnboarding = _hasSeenOnboarding.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      _hasSeenOnboarding.value = dataStoreRepository.readHasSeenOnboarding()
    }
  }

  fun markSeen() {
    viewModelScope.launch(Dispatchers.IO) {
      dataStoreRepository.saveHasSeenOnboarding(true)
    }
    _hasSeenOnboarding.value = true
  }
}
