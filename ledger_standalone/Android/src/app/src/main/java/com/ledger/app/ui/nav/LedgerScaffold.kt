package com.ledger.app.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ledger.app.common.HapticManager
import com.ledger.app.data.Model
import com.ledger.app.ui.history.HistoryScreen
import com.ledger.app.ui.inventory.InventoryScreen
import com.ledger.app.ui.ledger.LedgerMainScreen
import com.ledger.app.ui.ledger.LedgerTools
import com.ledger.app.ui.settings.SettingsScreen
import com.ledger.app.ui.theme.LocalPrivacyMode
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

private data class NavItem(
  val route: Any,
  val label: String,
  val icon: ImageVector,
)

private val NAV_ITEMS = listOf(
  NavItem(HomeRoute, "Home", Icons.Rounded.Home),
  NavItem(HistoryRoute, "History", Icons.Rounded.History),
  NavItem(InventoryRoute, "Inventory", Icons.Rounded.Inventory2),
  NavItem(SettingsRoute, "Settings", Icons.Rounded.Settings),
)

@Composable
fun LedgerScaffold(
  model: Model,
  stateFlow: Flow<Unit>,
  ledgerTools: LedgerTools,
  bottomPadding: Dp = 0.dp,
) {
  val navController = rememberNavController()
  val navBackStack by navController.currentBackStackEntryAsState()
  val privacyMode = remember { mutableStateOf(false) }
  val context = androidx.compose.ui.platform.LocalContext.current

  CompositionLocalProvider(LocalPrivacyMode provides privacyMode) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      bottomBar = {
        NavigationBar {
          NAV_ITEMS.forEach { item ->
            val selected = navBackStack?.destination?.hasRoute(item.route::class) == true
            NavigationBarItem(
              selected = selected,
              onClick = {
                navController.navigate(item.route) {
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = { Icon(item.icon, contentDescription = item.label) },
              label = { Text(item.label) },
            )
          }
        }
      },
    ) { innerPadding ->
      NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = Modifier.padding(innerPadding).fillMaxSize(),
        enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 20 } },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 20 } },
      ) {
        composable<HomeRoute> {
          LedgerMainScreen(
            model = model,
            stateFlow = stateFlow,
            ledgerTools = ledgerTools,
            bottomPadding = 0.dp,
          )
        }
        composable<HistoryRoute> {
          HistoryScreen(ledgerTools = ledgerTools)
        }
        composable<InventoryRoute> {
          InventoryScreen()
        }
        composable<SettingsRoute> {
          SettingsScreen()
        }
      }
    }
  }
}
