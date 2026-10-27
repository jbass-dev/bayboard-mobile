package com.jbassdev.bayboard.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jbassdev.bayboard.ui.board.BoardScreen
import com.jbassdev.bayboard.ui.board.BoardViewModel
import com.jbassdev.bayboard.ui.board.NewTicketDialog
import com.jbassdev.bayboard.ui.components.CenteredMessage
import com.jbassdev.bayboard.ui.inventory.InventoryScreen
import com.jbassdev.bayboard.ui.login.AuthState
import com.jbassdev.bayboard.ui.login.AuthViewModel
import com.jbassdev.bayboard.ui.login.LoginScreen

private object Routes {
    const val BOARD = "board"
    const val INVENTORY = "inventory"
}

@Composable
fun BayBoardApp(authVm: AuthViewModel = viewModel()) {
    val auth by authVm.authState.collectAsStateWithLifecycle()
    when (val state = auth) {
        AuthState.Loading -> CenteredMessage { CircularProgressIndicator() }
        AuthState.SignedOut -> LoginScreen(authVm)
        is AuthState.SignedIn -> SignedInApp(isGuest = state.isGuest, onSignOut = authVm::signOut)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInApp(
    isGuest: Boolean,
    onSignOut: () -> Unit,
    boardVm: BoardViewModel = viewModel(),
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.BOARD
    val snackbar = remember { SnackbarHostState() }
    var showNewTicket by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(boardVm) {
        boardVm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (route == Routes.INVENTORY) "Inventory" else "Bay Board") },
                actions = {
                    if (isGuest) {
                        Text("Guest", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = route == Routes.BOARD,
                    onClick = { nav.navigateTab(Routes.BOARD) },
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                    label = { Text("Board") },
                )
                NavigationBarItem(
                    selected = route == Routes.INVENTORY,
                    onClick = { nav.navigateTab(Routes.INVENTORY) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Inventory") },
                )
            }
        },
        floatingActionButton = {
            if (route == Routes.BOARD) {
                ExtendedFloatingActionButton(
                    onClick = { showNewTicket = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New ticket") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(nav, startDestination = Routes.BOARD, modifier = Modifier.padding(padding)) {
            composable(Routes.BOARD) { BoardScreen(boardVm) }
            composable(Routes.INVENTORY) { InventoryScreen(boardVm) }
        }
    }

    if (showNewTicket) {
        NewTicketDialog(
            onDismiss = { showNewTicket = false },
            onCreate = { vehicle, service, notes, customer ->
                boardVm.createTicket(vehicle, service, notes, customer)
                showNewTicket = false
            },
        )
    }
}

private fun NavController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
