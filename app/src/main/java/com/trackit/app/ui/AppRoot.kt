package com.trackit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.trackit.app.ui.profile.ChangePasswordScreen
import com.trackit.app.ui.profile.DeleteAccountScreen
import com.trackit.app.ui.profile.EditProfileScreen
import com.trackit.app.ui.profile.PrivacySecurityScreen
import com.trackit.app.ui.profile.ProfileScreen
import com.trackit.app.ui.track.TrackScreen
import com.trackit.app.ui.viewmodel.AccountViewModel
import com.trackit.app.ui.viewmodel.AppViewModel
import com.trackit.app.ui.viewmodel.AuthViewModel
import com.trackit.app.ui.viewmodel.CalendarViewModel
import com.trackit.app.ui.viewmodel.ExportViewModel
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
    Tab("track", "Track", Icons.Rounded.AccountBalanceWallet),
    Tab("calendar", "Calendar", Icons.Rounded.CalendarMonth),
    Tab("profile", "Profile", Icons.Rounded.Person),
)

@Composable
private fun TrackitBottomBar(current: String?, onSelect: (Tab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                // Profile stays highlighted while inside its sub-screens.
                val selected = current == tab.route ||
                    (tab.route == "profile" && current?.startsWith("profile/") == true)
                val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(26.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = tint,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(app: AppViewModel, factory: ViewModelProvider.Factory) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = current == null || tabs.any { it.route == current }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                TrackitBottomBar(current = current) { tab ->
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
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
            composable("profile") {
                val exportVm: ExportViewModel = viewModel(factory = factory)
                ProfileScreen(
                    vm = app,
                    exportVm = exportVm,
                    onEditProfile = { nav.navigate("profile/edit") },
                    onPrivacy = { nav.navigate("profile/security") },
                )
            }
            composable("profile/edit") {
                val vm: AccountViewModel = viewModel(factory = factory)
                EditProfileScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("profile/security") {
                val vm: AccountViewModel = viewModel(factory = factory)
                PrivacySecurityScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onChangePassword = { nav.navigate("profile/password") },
                    onDeleteAccount = { nav.navigate("profile/delete") },
                    onSignOut = { app.logout() },
                )
            }
            composable("profile/password") {
                val vm: AccountViewModel = viewModel(factory = factory)
                ChangePasswordScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("profile/delete") {
                val vm: AccountViewModel = viewModel(factory = factory)
                DeleteAccountScreen(vm, onBack = { nav.popBackStack() })
            }
        }
    }
}
