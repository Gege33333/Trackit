package com.trackit.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trackit.app.ui.auth.LoginScreen
import com.trackit.app.ui.auth.RegisterScreen
import com.trackit.app.ui.calendar.CalendarTab
import com.trackit.app.ui.profile.ProfileScreen
import com.trackit.app.ui.track.TrackScreen
import com.trackit.app.ui.viewmodel.AppViewModel
import com.trackit.app.ui.viewmodel.AuthViewModel
import com.trackit.app.ui.viewmodel.CalendarViewModel
import com.trackit.app.ui.viewmodel.TrackViewModel

@Composable
fun AppRoot(app: AppViewModel, factory: ViewModelProvider.Factory) {
    val user by app.user.collectAsStateWithLifecycle()
    if (user == null) AuthFlow(factory) else MainScaffold(app, factory)
}

@Composable
private fun AuthFlow(factory: ViewModelProvider.Factory) {
    val nav = rememberNavController()
    val vm: AuthViewModel = viewModel(factory = factory)
    NavHost(navController = nav, startDestination = "login") {
        composable("login") {
            LoginScreen(vm, onGoRegister = { vm.clearError(); nav.navigate("register") })
        }
        composable("register") {
            RegisterScreen(vm, onGoLogin = { vm.clearError(); nav.popBackStack() })
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("track", "Track", Icons.Filled.Add),
    Tab("calendar", "Calendar", Icons.Filled.DateRange),
    Tab("profile", "Profile", Icons.Filled.Person),
)

@Composable
private fun MainScaffold(app: AppViewModel, factory: ViewModelProvider.Factory) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = "track", modifier = Modifier.padding(padding)) {
            composable("track") {
                val vm: TrackViewModel = viewModel(factory = factory)
                TrackScreen(vm)
            }
            composable("calendar") {
                val vm: CalendarViewModel = viewModel(factory = factory)
                CalendarTab(vm)
            }
            composable("profile") { ProfileScreen(app) }
        }
    }
}
