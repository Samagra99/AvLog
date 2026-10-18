package com.samtech.avlog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.samtech.avlog.duty.DutyScreen
import com.samtech.avlog.duty.DutyViewModel
import com.samtech.avlog.licenses.ExpiryNotifications
import com.samtech.avlog.licenses.LicenseScreen
import com.samtech.avlog.licenses.LicenseViewModel
import com.samtech.avlog.records.RecordsScreen
import com.samtech.avlog.records.RecordsViewModel
import com.samtech.avlog.ui.theme.AvLogTheme

class MainActivity : ComponentActivity() {
    /** Destination requested by a notification tap (consumed once). */
    private var pendingDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingDestination = intent.destination()
        setContent {
            AvLogTheme {
                MainApp(
                    pendingDestination = pendingDestination,
                    onDestinationHandled = { pendingDestination = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDestination = intent.destination()
    }

    private fun Intent.destination(): String? = getStringExtra(ExpiryNotifications.EXTRA_DESTINATION)
}

enum class Screen(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Duty("duty", R.string.tab_flight, Icons.Default.Timer),
    Records("records", R.string.tab_records, Icons.Default.History),
    Licenses("licenses", R.string.tab_licenses, Icons.Default.Badge)
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun MainApp(pendingDestination: String? = null, onDestinationHandled: () -> Unit = {}) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Hoisted to activity scope so tab switches don't recreate them.
    val dutyViewModel: DutyViewModel = viewModel(factory = AvLogViewModels.Factory)
    val recordsViewModel: RecordsViewModel = viewModel(factory = AvLogViewModels.Factory)
    val licenseViewModel: LicenseViewModel = viewModel(factory = AvLogViewModels.Factory)

    // P5.5: ask for POST_NOTIFICATIONS once, in context (after the first license is saved).
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) ExpiryNotifications.checkNow(context)
    }
    val onLicenseSaved: () -> Unit = {
        ExpiryNotifications.checkNow(context)
        val settings = context.appContainer.settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !settings.askedNotificationPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            settings.askedNotificationPermission = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(pendingDestination) {
        if (pendingDestination == ExpiryNotifications.DESTINATION_LICENSES) {
            navController.navigateToTab(Screen.Licenses.route)
            onDestinationHandled()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.label)) },
                        selected = currentRoute == screen.route,
                        onClick = { navController.navigateToTab(screen.route) }
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController,
            Screen.Duty.route,
            Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable(Screen.Duty.route) { DutyScreen(dutyViewModel, snackbarHostState) }
            composable(Screen.Records.route) { RecordsScreen(recordsViewModel, snackbarHostState) }
            composable(Screen.Licenses.route) { LicenseScreen(licenseViewModel, snackbarHostState, onLicenseSaved) }
        }
    }
}
