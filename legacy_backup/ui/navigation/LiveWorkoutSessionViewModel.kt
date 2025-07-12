package com.example.liftrix.ui.navigation

import androidx.lifecycle.ViewModel
import com.example.liftrix.service.LiveWorkoutSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel wrapper for LiveWorkoutSessionManager to enable proper Hilt injection in Compose
 */
@HiltViewModel
class LiveWorkoutSessionViewModel @Inject constructor(
    val liveWorkoutSessionManager: LiveWorkoutSessionManager
) : ViewModel()