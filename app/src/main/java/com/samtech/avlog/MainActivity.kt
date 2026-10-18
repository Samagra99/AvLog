package com.samtech.avlog

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.samtech.avlog.ui.theme.AvLogTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AvLogTheme {
                MainApp()
            }
        }
    }
}

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    Duty("duty", "Flight", Icons.Default.Timer),
    Records("records", "Records", Icons.Default.History),
    Licenses("licenses", "Licenses", Icons.Default.Badge)
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val dutyDao = database.dutyDao()
    val licenseDao = database.licenseDao()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scheduleExpiryCheck(context)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scheduleExpiryCheck(context)
        }
    }

    val dutyViewModel: DutyViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DutyViewModel(dutyDao, context.applicationContext) as T
            }
        }
    )

    val licenseViewModel: LicenseViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LicenseViewModel(licenseDao) as T
            }
        }
    )

    var showAddLicenseDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                listOf(Screen.Duty, Screen.Records, Screen.Licenses).forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                        selected = currentRoute == screen.route,
                        onClick = { navController.navigate(screen.route) }
                    )
                }
            }
        },
        floatingActionButton = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            if (currentRoute == Screen.Licenses.route) {
                FloatingActionButton(onClick = { showAddLicenseDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add License")
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, Screen.Duty.route, Modifier.padding(innerPadding)) {
            composable(Screen.Duty.route) { DutyScreen(dutyViewModel) }
            composable(Screen.Records.route) {
                val recordsViewModel: RecordsViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return RecordsViewModel(dutyDao) as T
                        }
                    }
                )
                RecordsScreen(recordsViewModel)
            }
            composable(Screen.Licenses.route) {
                LicenseScreen(licenseViewModel)
            }
        }

        if (showAddLicenseDialog) {
            AddLicenseDialog(
                onDismiss = { showAddLicenseDialog = false },
                onConfirm = { name, expiry ->
                    licenseViewModel.addLicense(name, expiry)
                    showAddLicenseDialog = false
                },
            )
        }
    }
}

fun scheduleExpiryCheck(context: Context) {
    val request = PeriodicWorkRequestBuilder<ExpiryWorker>(24, TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "LicenseCheck",
        ExistingPeriodicWorkPolicy.KEEP, // Don't restart the 24h timer if app opens again
        request
    )
}
