package com.example.liftrix

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.di.IoDispatcher
import com.example.liftrix.monitoring.PerformanceMonitor
import com.google.firebase.perf.metrics.Trace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import com.example.liftrix.ui.branding.LiftrixLaunchAnimation
import com.example.liftrix.ui.navigation.UnifiedNavigationContainer
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.theme.ThemeManager
import com.example.liftrix.ui.theme.ProvideWeightUnitManager
import com.example.liftrix.domain.service.WeightUnitManager
import com.example.liftrix.data.service.FirebaseStorageUrlResolver
import com.example.liftrix.ui.common.LocalFirebaseStorageUrlResolver
import com.example.liftrix.ui.common.LocalProfileImageCache
import com.example.liftrix.service.ProfileImageCache
import com.example.liftrix.widget.LiftrixWidgetActions
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var weightUnitManager: WeightUnitManager

    @Inject
    lateinit var firebaseStorageUrlResolver: FirebaseStorageUrlResolver

    @Inject
    lateinit var profileImageCache: ProfileImageCache

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var performanceMonitor: PerformanceMonitor

    private var mainActivityCreateTrace: Trace? = null
    private var firstAuthenticatedContentTrace: Trace? = null
    private var fullyDrawnReported = false
    private var pendingWidgetWorkoutNavigation by mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.getInstance(this).applyCurrentThemeToPlatform()
        installSplashScreen()
        super.onCreate(savedInstanceState)
        mainActivityCreateTrace = performanceMonitor.startCustomTrace("liftrix_main_activity_on_create")
        captureWidgetNavigationRequest(intent)
        
        // Initialize WeightUnitManager
        lifecycleScope.launch(ioDispatcher) {
            try {
                weightUnitManager.initialize()
                Timber.d("WeightUnitManager initialized successfully in MainActivity")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize WeightUnitManager in MainActivity")
            }
        }
        
        setContent {
            val themeManager = remember { ThemeManager.getInstance(this@MainActivity) }
            
            LiftrixTheme(
                themeManager = themeManager
            ) {
                ProvideWeightUnitManager(weightUnitManager = weightUnitManager) {
                    CompositionLocalProvider(
                        LocalFirebaseStorageUrlResolver provides firebaseStorageUrlResolver,
                        LocalProfileImageCache provides profileImageCache
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            MainContent(
                                pendingWidgetWorkoutNavigation = pendingWidgetWorkoutNavigation,
                                onWidgetWorkoutNavigationConsumed = {
                                    pendingWidgetWorkoutNavigation = false
                                },
                                onFirstAuthenticatedContent = { userId ->
                                    traceFirstAuthenticatedContent()
                                    reportStartupFullyDrawn()
                                    (application as? LiftrixApp)?.onFirstContentReady(userId)
                                },
                                onNavigateToAuth = {
                                    navigateToAuthActivity()
                                }
                            )
                        }
                    }
                }
            }
        }
        mainActivityCreateTrace?.let { performanceMonitor.stopCustomTrace(it) }
        mainActivityCreateTrace = null
    }

    private fun traceFirstAuthenticatedContent() {
        val trace = firstAuthenticatedContentTrace
            ?: performanceMonitor.startCustomTrace("liftrix_first_authenticated_content").also {
                firstAuthenticatedContentTrace = it
            }
        trace?.let { performanceMonitor.stopCustomTrace(it) }
        firstAuthenticatedContentTrace = null
    }

    private fun reportStartupFullyDrawn() {
        if (fullyDrawnReported) return
        fullyDrawnReported = true

        val trace = performanceMonitor.startCustomTrace("liftrix_report_fully_drawn")
        reportFullyDrawn()
        trace?.let { performanceMonitor.stopCustomTrace(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureWidgetNavigationRequest(intent)
    }

    private fun captureWidgetNavigationRequest(intent: Intent?) {
        if (
            intent?.action == LiftrixWidgetActions.ACTION_OPEN_WORKOUT ||
            intent?.hasExtra(LiftrixWidgetActions.EXTRA_WIDGET_SOURCE) == true
        ) {
            pendingWidgetWorkoutNavigation = true
        }
    }
    
    private fun navigateToAuthActivity() {
        val intent = Intent(this, AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
@Composable
fun MainContent(
    pendingWidgetWorkoutNavigation: Boolean = false,
    onWidgetWorkoutNavigationConsumed: () -> Unit = {},
    onFirstAuthenticatedContent: (String) -> Unit = {},
    onNavigateToAuth: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    
    // Set explicit auth flow to false when MainActivity is active
    // This allows MainViewModel to handle auth state normally
    LaunchedEffect(Unit) {
        viewModel.setExplicitAuthFlow(false)
    }
    
    when (val currentState = authState) {
        is MainViewModel.AuthenticationState.Loading -> {
            LoadingScreen()
        }
        is MainViewModel.AuthenticationState.Unauthenticated -> {
            LaunchedEffect(Unit) {
                onNavigateToAuth()
            }
        }
        is MainViewModel.AuthenticationState.Authenticated -> {
            AuthenticatedContent(
                user = currentState.user,
                pendingWidgetWorkoutNavigation = pendingWidgetWorkoutNavigation,
                onWidgetWorkoutNavigationConsumed = onWidgetWorkoutNavigationConsumed,
                onFirstAuthenticatedContent = onFirstAuthenticatedContent
            )
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LiftrixLaunchAnimation()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading Liftrix...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun AuthenticatedContent(
    user: com.example.liftrix.domain.model.User,
    pendingWidgetWorkoutNavigation: Boolean = false,
    onWidgetWorkoutNavigationConsumed: () -> Unit = {},
    onFirstAuthenticatedContent: (String) -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val profileReadiness by viewModel.profileReadiness.collectAsState()
    val profileReady = profileReadiness is MainViewModel.ProfileReadiness.Ready &&
        (profileReadiness as MainViewModel.ProfileReadiness.Ready).userId == user.uid

    // Show loading while verifying profile existence
    if (!profileReady) {
        LoadingScreen()
    } else {
        LaunchedEffect(user.uid) {
            Timber.d("User authenticated in MainActivity")
            onFirstAuthenticatedContent(user.uid)
        }

        // Main navigation with type-safe navigation system
        UnifiedNavigationContainer(
            pendingWidgetWorkoutNavigation = pendingWidgetWorkoutNavigation,
            onWidgetWorkoutNavigationConsumed = onWidgetWorkoutNavigationConsumed
        )
    }
}
