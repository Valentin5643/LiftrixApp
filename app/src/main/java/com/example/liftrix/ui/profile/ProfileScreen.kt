package com.example.liftrix.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.R
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.ui.animations.ScreenLoadingSkeleton
import com.example.liftrix.ui.error.ErrorHandlingExtensions.LiftrixErrorState
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.profile.components.AchievementDisplay
import com.example.liftrix.ui.profile.components.ProfileImageDisplay
import com.example.liftrix.ui.profile.components.ImagePickerDialog
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.TertiaryActionButton
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.theme.LiftrixSpacing
import timber.log.Timber

/**
 * ProfileScreen - Main profile display screen
 * 
 * Comprehensive profile interface showing user information, achievements, 
 * bio, privacy controls, and profile completion status.
 * 
 * Features:
 * - Profile image display and management integration
 * - Achievement section with badge display  
 * - Privacy controls toggle
 * - Bio display and editing capability
 * - Profile completion percentage with suggestions
 * - Real-time updates from enhanced ProfileViewModel
 * - Material 3 design with accessibility compliance
 * - UnifiedWorkoutCard layout pattern for consistency
 * 
 * Design System Compliance:
 * - Uses UnifiedWorkoutCard for consistent layout
 * - Follows ModernActionButton hierarchy (Primary/Secondary/Tertiary)
 * - Implements LiftrixSpacing semantic tokens
 * - WCAG 2.1 AA accessibility compliance
 * - Persian Green/Tiffany Blue color system
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToEdit: () -> Unit,
    onNavigateToImageCrop: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Image picker dialog state
    var showImagePickerDialog by remember { mutableStateOf(false) }
    
    Timber.d("ProfileScreen: Composing with state - loading: ${uiState.isLoading}, profile: ${uiState.profile?.displayName}")
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(LiftrixSpacing.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
    ) {
        when {
            uiState.isLoading && uiState.profile == null -> {
                ProfileLoadingState()
            }
            uiState.error != null -> {
                ProfileErrorState(
                    error = uiState.error!!,
                    onRetry = { viewModel.handleEvent(ProfileEvent.RetryLastOperation) },
                    onClearError = { viewModel.handleEvent(ProfileEvent.ClearError) }
                )
            }
            uiState.profile != null -> {
                ProfileContent(
                    profile = uiState.profile!!,
                    profileImageUrl = uiState.profile!!.profileImageUrl,
                    imageUploadState = uiState.imageUploadState,
                    onNavigateToEdit = onNavigateToEdit,
                    onNavigateToImageCrop = onNavigateToImageCrop,
                    onNavigateToSettings = onNavigateToSettings,
                    onUploadImage = { uri, cropRect -> 
                        viewModel.handleEvent(ProfileEvent.UploadImage(uri, cropRect))
                    },
                    onDeleteImage = { 
                        viewModel.handleEvent(ProfileEvent.DeleteImage)
                    },
                    onUpdatePrivacy = { isPublic ->
                        viewModel.handleEvent(ProfileEvent.UpdatePrivacy(isPublic))
                    },
                    showImagePickerDialog = showImagePickerDialog,
                    onShowImagePicker = { showImagePickerDialog = true },
                    onImagePickerDialogDismiss = { showImagePickerDialog = false }
                )
            }
            else -> {
                ProfileEmptyState(
                    onNavigateToEdit = onNavigateToEdit
                )
            }
        }
        
        // Success message display
        uiState.successMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(LiftrixSpacing.cardPadding),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ProfileContent(
    profile: UserProfile,
    profileImageUrl: String?,
    imageUploadState: ImageUploadState,
    onNavigateToEdit: () -> Unit,
    onNavigateToImageCrop: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onUploadImage: (android.net.Uri, android.graphics.Rect?) -> Unit,
    onDeleteImage: () -> Unit,
    onUpdatePrivacy: (Boolean) -> Unit,
    showImagePickerDialog: Boolean,
    onShowImagePicker: () -> Unit,
    onImagePickerDialogDismiss: () -> Unit
) {
    // Profile Header Card
    UnifiedWorkoutCard(
        title = profile.displayName,
        subtitle = generateProfileSubtitle(profile),
        leadingIcon = Icons.Default.Person,
        actions = {
            SecondaryActionButton(
                text = "Edit",
                onClick = onNavigateToEdit,
                leadingIcon = Icons.Default.Edit
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
            verticalAlignment = Alignment.Top
        ) {
            // Profile Image Section
            ProfileImageDisplay(
                imageUrl = profileImageUrl,
                displayName = profile.displayName,
                userId = profile.userId,
                size = 80.dp,
                onClick = { 
                    onShowImagePicker()
                },
                modifier = Modifier
            )
            
            // Profile Stats Section
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
            ) {
                ProfileStatRow("Workouts", profile.totalWorkouts.toString())
                ProfileStatRow("Current Streak", "${profile.currentStreak} days")
                ProfileStatRow("Best Streak", "${profile.longestStreak} days")
                ProfileStatRow("Member Since", formatMemberSince(profile.memberSince))
            }
        }
        
        // Profile Completion Section
        if (profile.profileCompletionPercentage < 100) {
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing))
            ProfileCompletionSection(
                completionPercentage = profile.profileCompletionPercentage,
                onNavigateToEdit = onNavigateToEdit
            )
        }
    }
    
    // Bio Section
    if (!profile.bio.isNullOrBlank()) {
        UnifiedWorkoutCard(
            title = "About",
            subtitle = null
        ) {
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    
    // Achievements Section
    if (profile.achievements.isNotEmpty()) {
        UnifiedWorkoutCard(
            title = "Achievements",
            subtitle = "${profile.achievements.size} unlocked",
            leadingIcon = Icons.Default.Star
        ) {
            AchievementDisplay(
                achievements = profile.achievements,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    
    // Privacy & Settings Section
    UnifiedWorkoutCard(
        title = "Privacy & Settings",
        subtitle = if (profile.isPublic) "Profile is public" else "Profile is private",
        leadingIcon = Icons.Default.Settings,
        actions = {
            SecondaryActionButton(
                text = "Settings",
                onClick = onNavigateToSettings,
                leadingIcon = Icons.Default.Settings
            )
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Public Profile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = profile.isPublic,
                    onCheckedChange = { isPublic ->
                        onUpdatePrivacy(isPublic)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
            if (profile.isPublic) {
                Text(
                    text = "Your profile is visible to other users and can be found through search",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Your profile is private and cannot be found by other users",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    // Image Picker Dialog
    ImagePickerDialog(
        isVisible = showImagePickerDialog,
        onDismiss = onImagePickerDialogDismiss,
        onImageSelected = { uri ->
            onUploadImage(uri, null) // No crop rect for now, can be enhanced later
        },
        onError = { error ->
            Timber.e("Image picker error: $error")
            // Error handling can be enhanced with proper error display
        }
    )
}

@Composable
private fun ProfileStatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ProfileCompletionSection(
    completionPercentage: Int,
    onNavigateToEdit: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile Completion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$completionPercentage%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        LinearProgressIndicator(
            progress = { completionPercentage / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer
        )
        
        Text(
            text = "Complete your profile to unlock all features",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        TertiaryActionButton(
            text = "Complete Profile",
            onClick = onNavigateToEdit
        )
    }
}

@Composable
private fun ProfileLoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
    ) {
        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(LiftrixSpacing.cardPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileErrorState(
    error: ProfileError,
    onRetry: () -> Unit,
    onClearError: () -> Unit
) {
    UnifiedWorkoutCard(
        title = "Error Loading Profile",
        subtitle = error.message
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
        ) {
            Text(
                text = "We couldn't load your profile information. Please try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.buttonSpacing)
            ) {
                if (error.isRecoverable) {
                    PrimaryActionButton(
                        text = "Retry",
                        onClick = onRetry
                    )
                }
                SecondaryActionButton(
                    text = "Dismiss",
                    onClick = onClearError
                )
            }
        }
    }
}

@Composable
private fun ProfileEmptyState(
    onNavigateToEdit: () -> Unit
) {
    UnifiedWorkoutCard(
        title = "Welcome to Liftrix",
        subtitle = "Set up your profile to get started"
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Create your profile to track workouts, earn achievements, and connect with other fitness enthusiasts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            PrimaryActionButton(
                text = "Set Up Profile",
                onClick = onNavigateToEdit
            )
        }
    }
}

// Helper functions
private fun generateProfileSubtitle(profile: UserProfile): String {
    return buildString {
        if (profile.age != null) {
            append("${profile.age} years old")
        }
        if (profile.fitnessGoals.isNotEmpty()) {
            if (isNotEmpty()) append(" • ")
            append("${profile.fitnessGoals.size} goals")
        }
        if (isEmpty()) {
            append("Fitness enthusiast")
        }
    }
}

private fun formatMemberSince(memberSince: java.time.LocalDateTime): String {
    val now = java.time.LocalDateTime.now()
    val period = java.time.Period.between(memberSince.toLocalDate(), now.toLocalDate())
    
    return when {
        period.years > 0 -> "${period.years} year${if (period.years > 1) "s" else ""}"
        period.months > 0 -> "${period.months} month${if (period.months > 1) "s" else ""}"
        period.days > 7 -> "${period.days / 7} week${if (period.days / 7 > 1) "s" else ""}"
        period.days > 0 -> "${period.days} day${if (period.days > 1) "s" else ""}"
        else -> "New member"
    }
}