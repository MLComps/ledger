package com.ledger.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledger.app.data.DataStoreRepository
import com.ledger.app.data.Model
import com.ledger.app.db.LedgerRepository
import com.ledger.app.ui.ledger.LedgerTools
import com.ledger.app.ui.modelsetup.ModelSetupScreen
import com.ledger.app.ui.nav.LedgerScaffold
import com.ledger.app.ui.onboarding.OnboardingSheet
import com.ledger.app.ui.onboarding.OnboardingViewModel
import com.ledger.app.ui.theme.LedgerTheme
import com.ledger.app.worker.DailyDigestScheduler
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject lateinit var ledgerRepository: LedgerRepository
  @Inject lateinit var dataStoreRepository: DataStoreRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(null)

    @OptIn(ExperimentalApi::class)
    ExperimentalFlags.enableBenchmark = false

    DailyDigestScheduler.schedule(this)

    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    setContent {
      val themeMode by dataStoreRepository.themeModeFlow().collectAsState(initial = dataStoreRepository.readThemeMode())
      val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
      }
      LedgerTheme(darkTheme = darkTheme) {
        var showSplash by remember { mutableStateOf(true) }
        
        if (showSplash) {
          com.ledger.app.ui.common.LedgerSplashScreen(onAnimationFinished = { showSplash = false })
        } else {
          Surface(modifier = Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            var selectedModel by remember { mutableStateOf<Model?>(null) }
            val stateFlow = remember { MutableSharedFlow<Unit>(replay = 1) }
            val ledgerTools = remember {
              LedgerTools(
                onStateChanged = { stateFlow.tryEmit(Unit) },
                ledgerRepository = ledgerRepository,
                coroutineScope = scope,
              )
            }

            if (selectedModel == null) {
              ModelSetupScreen(
                onModelReady = { model -> selectedModel = model }
              )
            } else {
              LedgerScaffold(
                model = selectedModel!!,
                stateFlow = stateFlow,
                ledgerTools = ledgerTools,
              )
            }

            val onboardingVm: OnboardingViewModel = hiltViewModel()
            val hasSeenOnboarding by onboardingVm.hasSeenOnboarding.collectAsState()
            if (hasSeenOnboarding == false) {
              OnboardingSheet(onDismiss = { onboardingVm.markSeen() })
            }
          }
        }
      }
    }
  }
}
