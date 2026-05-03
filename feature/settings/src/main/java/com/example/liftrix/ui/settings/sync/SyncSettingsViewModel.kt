package com.example.liftrix.ui.settings.sync

import androidx.lifecycle.ViewModel
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for sync settings screen
 * 
 * Provides access to authentication services and sync configuration
 * while maintaining separation of concerns with dedicated use cases.
 */
@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    val authQueryUseCase: AuthQueryUseCase
) : ViewModel()