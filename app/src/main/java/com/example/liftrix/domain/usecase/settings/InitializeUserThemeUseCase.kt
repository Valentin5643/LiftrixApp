package com.example.liftrix.domain.usecase.settings

import android.content.Context
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.ui.theme.ThemeManager
import com.example.liftrix.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

/**
 * Use case for initializing user theme preferences when they log in.
 * Ensures that the app's theme matches the user's saved preferences immediately.
 * 
 * This resolves the issue where users see white theme briefly before Settings screen
 * loads and applies their saved theme preferences.
 */
@Singleton
class InitializeUserThemeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {
    
    /**
     * Initialize theme for the authenticated user.
     * Loads user's saved dark mode preference and applies it to ThemeManager immediately.
     * 
     * @param userId The authenticated user's ID
     * @return LiftrixResult indicating success or failure of theme initialization
     */
    suspend operator fun invoke(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "THEME_INITIALIZATION_FAILED",
                errorMessage = "Failed to initialize user theme preferences: ${throwable.message}",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "operation" to "INITIALIZE_THEME"
                )
            )
        }
    ) {
        Timber.d("InitializeUserThemeUseCase: Initializing theme for user $userId")
        
        // Get ThemeManager instance
        val themeManager = ThemeManager.getInstance(context)
        
        try {
            // Load user settings to get their preferred dark mode setting
            val userSettings = settingsRepository.getUserSettings(userId).first()
            
            if (userSettings != null) {
                val themeMode = if (userSettings.darkMode) ThemeMode.DARK else ThemeMode.LIGHT
                
                // Apply theme immediately
                themeManager.switchTheme(themeMode)
                
                Timber.i("InitializeUserThemeUseCase: Theme initialized successfully for user $userId, darkMode: ${userSettings.darkMode}")
            } else {
                // User might not have settings yet (new account), use system default
                Timber.d("InitializeUserThemeUseCase: No saved settings for user $userId, using system default")
                themeManager.switchTheme(ThemeMode.SYSTEM)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "InitializeUserThemeUseCase: Exception while initializing theme for user $userId")
            // Fallback to system theme if there's any error
            themeManager.switchTheme(ThemeMode.SYSTEM)
            throw e
        }
    }
}