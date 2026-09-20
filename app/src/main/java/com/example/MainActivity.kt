package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flock.data.FarmRegistryEntity
import com.example.flock.ui.AppScreen
import com.example.flock.ui.FlockViewModel
import com.example.flock.ui.MainFlockScreen
import com.example.flock.ui.components.AccountSheet
import com.example.flock.ui.components.FarmSettingsDialog
import com.example.flock.ui.components.RecycleBinDialog
import com.example.flock.ui.components.ShareFarmDialog
import com.example.flock.ui.screens.FarmsScreen
import com.example.flock.ui.screens.FlocksScreen
import com.example.flock.ui.screens.SignInScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: FlockViewModel = viewModel()
                FlockAppRoot(viewModel)
            }
        }
    }
}

@Composable
fun FlockAppRoot(viewModel: FlockViewModel) {
    val authState by viewModel.authState.collectAsState()
    val appScreen by viewModel.appScreen.collectAsState()
    val farms by viewModel.farms.collectAsState()
    val flocks by viewModel.flocks.collectAsState()
    val farm by viewModel.farm.collectAsState()
    val config by viewModel.config.collectAsState()
    val feedTypes by viewModel.feedTypes.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val deletedFarms by viewModel.deletedFarms.collectAsState()
    val deletedFlocks by viewModel.deletedFlocks.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var sharingFarm by remember { mutableStateOf<FarmRegistryEntity?>(null) }
    var showAccount by remember { mutableStateOf(false) }
    var showRecycleBin by remember { mutableStateOf(false) }

    // System back: Dashboard → Flocks → Farms (instead of exiting the app)
    BackHandler(enabled = authState.isSignedIn && appScreen != AppScreen.FARMS) {
        when (appScreen) {
            AppScreen.DASHBOARD -> viewModel.goToFlocks()
            AppScreen.FLOCKS -> viewModel.goToFarms()
            AppScreen.FARMS -> {}
        }
    }

    // Non-blocking Toast (a Scaffold Snackbar sat over the bottom buttons and blocked taps).
    val context = LocalContext.current
    LaunchedEffect(userMessage) {
        userMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            if (account != null) viewModel.handleSignInResult(account)
            else viewModel.handleSignInError("Google sign-in returned no account.")
        } catch (e: ApiException) {
            viewModel.handleSignInError(
                "Google sign-in unavailable (code ${e.statusCode}). Configure an OAuth client in Google Cloud, or use offline mode."
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Screens without their own Scaffold get the system-bar insets here; the dashboard
        // (which has its own Scaffold + bottom nav) handles insets itself to avoid a double gap.
        val content = Modifier.fillMaxSize().systemBarsPadding()
        if (!authState.isSignedIn) {
            SignInScreen(
                onSignInGoogle = { signInLauncher.launch(viewModel.googleSignInClient().signInIntent) },
                onOffline = { viewModel.enableDemoMode() },
                modifier = content
            )
        } else {
            when (appScreen) {
                AppScreen.FARMS -> FarmsScreen(
                    farms = farms,
                    userEmail = authState.email,
                    isDemoMode = authState.isDemoMode,
                    onOpenFarm = { viewModel.openFarm(it) },
                    onCreateFarm = { name -> viewModel.createFarm(name) {} },
                    onOpenSharedFarm = { viewModel.openSharedFarm(it) },
                    onShareFarm = { sharingFarm = it },
                    onDeleteFarm = { viewModel.deleteFarm(it.spreadsheetId) },
                    onToggleLock = { viewModel.toggleFarmLock(it.spreadsheetId, !it.locked) },
                    onOpenAccount = { showAccount = true },
                    modifier = content
                )
                AppScreen.FLOCKS -> FlocksScreen(
                    farmName = farm.farmName,
                    timeZone = farm.timeZone,
                    flocks = flocks,
                    onBack = { viewModel.goToFarms() },
                    onOpenFlock = { viewModel.openFlock(it) },
                    onCreateFlock = { name, breed, startDate, startTime, placed, transitMort, harvestAge ->
                        viewModel.createFlock(name, breed, startDate, placed, transitMort, 3200.0, harvestAge, "Monsoon", startTime)
                    },
                    onDeleteFlock = { viewModel.deleteFlock(it) },
                    onToggleLock = { flockId, locked -> viewModel.toggleFlockLock(flockId, locked) },
                    onOpenSettings = { showSettings = true },
                    modifier = content
                )
                AppScreen.DASHBOARD -> MainFlockScreen(
                    viewModel = viewModel,
                    onNavFarms = { viewModel.goToFarms() },
                    onNavFlocks = { viewModel.goToFlocks() },
                    onOpenSettings = { showSettings = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showSettings) {
        FarmSettingsDialog(
            farm = farm,
            config = config,
            feedTypes = feedTypes,
            onDismiss = { showSettings = false },
            onSaveFarmSettings = { f, c -> viewModel.updateFarmSettings(f, c) },
            onSaveFeedType = { ft -> viewModel.saveFeedType(ft) },
            onDeleteFeedType = { code -> viewModel.deleteFeedType(code) }
        )
    }
    sharingFarm?.let { f ->
        ShareFarmDialog(
            farmName = f.farmName,
            spreadsheetId = f.spreadsheetId,
            onDismiss = { sharingFarm = null },
            onShare = { email, isEditor -> viewModel.shareFarm(f.spreadsheetId, email, isEditor) { _, _ -> } }
        )
    }
    if (showAccount) {
        AccountSheet(
            displayName = authState.displayName,
            email = authState.email,
            isDemo = authState.isDemoMode,
            appVersion = BuildConfig.VERSION_NAME,
            onOpenRecycleBin = { showRecycleBin = true },
            onSignOut = { viewModel.signOut() },
            onDismiss = { showAccount = false }
        )
    }
    if (showRecycleBin) {
        RecycleBinDialog(
            deletedFarms = deletedFarms,
            deletedFlocks = deletedFlocks,
            onRestoreFarm = { viewModel.restoreFarm(it) },
            onPurgeFarm = { viewModel.purgeFarm(it) },
            onRestoreFlock = { s, f -> viewModel.restoreFlock(s, f) },
            onPurgeFlock = { s, f -> viewModel.purgeFlock(s, f) },
            onDismiss = { showRecycleBin = false }
        )
    }
}
