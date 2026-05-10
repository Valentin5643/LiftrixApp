package com.example.liftrix.domain.usecase.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.ui.theme.ThemeManager
import com.example.liftrix.ui.theme.ThemeMode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InitializeUserThemeUseCaseTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var themeManager: ThemeManager
    private lateinit var useCase: InitializeUserThemeUseCase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("liftrix_theme_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        settingsRepository = mockk()
        themeManager = ThemeManager.getInstance(context)
        themeManager.switchTheme(ThemeMode.SYSTEM)
        useCase = InitializeUserThemeUseCase(settingsRepository, context)
    }

    @After
    fun tearDown() {
        themeManager.switchTheme(ThemeMode.SYSTEM)
    }

    @Test
    fun preservesExplicitLightThemeWhenUserSettingsAreMissingDuringLogin() = runTest {
        every { settingsRepository.getUserSettings(USER_ID) } returns flowOf(null)
        themeManager.switchTheme(ThemeMode.LIGHT)

        val result = useCase(USER_ID)

        assertTrue(result.isSuccess)
        assertEquals(ThemeMode.LIGHT, themeManager.themeMode.value)
        assertFalse(themeManager.getEffectiveThemeState(isSystemInDarkTheme = true))
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun preservesExplicitLightThemeWhenSettingsReadFailsDuringLogin() = runTest {
        every { settingsRepository.getUserSettings(USER_ID) } returns flow {
            throw IllegalStateException("settings unavailable")
        }
        themeManager.switchTheme(ThemeMode.LIGHT)

        val result = useCase(USER_ID)

        assertTrue(result.isFailure)
        assertEquals(ThemeMode.LIGHT, themeManager.themeMode.value)
        assertFalse(themeManager.getEffectiveThemeState(isSystemInDarkTheme = true))
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun appliesPersistedLightUserSettingFromSystemMode() = runTest {
        every { settingsRepository.getUserSettings(USER_ID) } returns flowOf(
            UserSettings(
                userId = USER_ID,
                darkMode = false,
                weightUnit = WeightUnit.KILOGRAMS
            )
        )
        themeManager.switchTheme(ThemeMode.SYSTEM)

        val result = useCase(USER_ID)

        assertTrue(result.isSuccess)
        assertEquals(ThemeMode.LIGHT, themeManager.themeMode.value)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun appliesPersistedDarkUserSettingFromSystemMode() = runTest {
        every { settingsRepository.getUserSettings(USER_ID) } returns flowOf(
            UserSettings(
                userId = USER_ID,
                darkMode = true,
                weightUnit = WeightUnit.KILOGRAMS
            )
        )
        themeManager.switchTheme(ThemeMode.SYSTEM)

        val result = useCase(USER_ID)

        assertTrue(result.isSuccess)
        assertEquals(ThemeMode.DARK, themeManager.themeMode.value)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
    }

    private companion object {
        const val USER_ID = "user-123"
    }
}
