package com.example.liftrix.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.ui.profile.components.CollapsibleSection
import com.example.liftrix.ui.profile.components.MultiSelectChipGroup
import com.example.liftrix.ui.profile.components.ProfileCompletionIndicator
import com.example.liftrix.ui.profile.components.calculateProfileCompletion
import com.example.liftrix.ui.theme.LiftrixSpacing
import timber.log.Timber

/**
 * Redesigned ProfileEditScreen - Modern UX with improved visual hierarchy
 *
 * UX Improvements:
 * - Collapsible sections with accordion pattern to reduce scrolling
 * - Multi-select chip UI instead of checkbox lists for better scanning
 * - Profile completion indicator to motivate users
 * - Sticky save/cancel buttons at bottom
 * - Change detection to enable/disable save button
 * - Better visual hierarchy with card-based sections
 * - Helper text and improved spacing
 * - Clear section summaries when collapsed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreenRedesigned(
    onNavigateBack: () -> Unit,
    onNavigateToImageCrop: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Edit state
    var displayName by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var isPublic by rememberSaveable { mutableStateOf(true) }
    var hasDraftEdits by rememberSaveable { mutableStateOf(false) }
    var initializedProfileUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGoalNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedEquipmentNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var otherEquipment by rememberSaveable { mutableStateOf("") }
    val selectedGoals = remember(selectedGoalNames) {
        FitnessGoal.values().filter { it.name in selectedGoalNames }.toSet()
    }
    val selectedEquipment = remember(selectedEquipmentNames) {
        Equipment.values().filter { it.name in selectedEquipmentNames }.toSet()
    }

    // Track original values for change detection
    var originalProfile by remember { mutableStateOf<ProfileFormData?>(null) }

    // Validation state
    var displayNameError by remember { mutableStateOf<String?>(null) }
    var bioError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }
    var weightError by remember { mutableStateOf<String?>(null) }

    // Initialize fields when profile loads
    LaunchedEffect(uiState.profile) {
        uiState.profile?.let { profile ->
            if (hasDraftEdits && initializedProfileUserId == profile.userId) {
                return@LaunchedEffect
            }
            displayName = profile.displayName
            bio = profile.bio ?: ""
            age = profile.age?.toString() ?: ""
            weight = profile.weight?.value?.toString() ?: ""
            isPublic = profile.isPublic
            selectedGoalNames = profile.fitnessGoals.map { it.name }
            selectedEquipmentNames = profile.availableEquipment.map { it.name }
            otherEquipment = profile.otherEquipment ?: ""
            initializedProfileUserId = profile.userId

            // Store original values
            originalProfile = ProfileFormData(
                userId = profile.userId,
                displayName = displayName,
                bio = bio,
                age = age,
                weight = weight,
                isPublic = isPublic,
                selectedGoals = selectedGoals,
                selectedEquipment = selectedEquipment,
                otherEquipment = otherEquipment
            )
            hasDraftEdits = false
        }
    }

    // Handle success message and navigate back
    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            Timber.i("Profile save successful, navigating back")
            onNavigateBack()
        }
    }

    // Detect changes
    val hasChanges = remember(
        displayName, bio, age, weight, isPublic,
        selectedGoals, selectedEquipment, otherEquipment,
        originalProfile
    ) {
        originalProfile?.let { original ->
            displayName != original.displayName ||
            bio != original.bio ||
            age != original.age ||
            weight != original.weight ||
            isPublic != original.isPublic ||
            selectedGoals != original.selectedGoals ||
            selectedEquipment != original.selectedEquipment ||
            otherEquipment != original.otherEquipment
        } ?: false
    }

    // Calculate profile completion
    val completionPercentage = remember(
        displayName, bio, age, weight, selectedGoals, selectedEquipment
    ) {
        calculateProfileCompletion(
            displayName = displayName,
            bio = bio,
            age = age,
            weight = weight,
            selectedGoals = selectedGoals,
            selectedEquipment = selectedEquipment
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Profile",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Sticky bottom action bar
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LiftrixSpacing.large),
                    horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                ) {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(LiftrixSpacing.small))
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (validateFields(
                                displayName = displayName,
                                bio = bio,
                                age = age,
                                weight = weight,
                                onDisplayNameError = { displayNameError = it },
                                onBioError = { bioError = it },
                                onAgeError = { ageError = it },
                                onWeightError = { weightError = it }
                            )) {
                                val currentProfile = uiState.profile
                                if (currentProfile != null) {
                                    val updatedProfile = currentProfile.copy(
                                        displayName = displayName.trim(),
                                        bio = bio.trim().takeIf { it.isNotEmpty() },
                                        age = age.toIntOrNull(),
                                        weight = weight.toDoubleOrNull()?.let { Weight.fromKilograms(it) },
                                        isPublic = isPublic,
                                        fitnessGoals = selectedGoals.toList(),
                                        availableEquipment = selectedEquipment.toList(),
                                        otherEquipment = otherEquipment.trim().takeIf { it.isNotEmpty() }
                                    )

                                    viewModel.handleEvent(ProfileEvent.SaveProfile(updatedProfile))
                                    Timber.i("Saving profile changes for user: ${updatedProfile.userId}")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isLoading && hasChanges
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(LiftrixSpacing.small))
                        Text("Save Changes")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = LiftrixSpacing.large)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.large)
        ) {
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))

            // Profile Completion Indicator
            ProfileCompletionIndicator(
                completionPercentage = completionPercentage
            )

            // Basic Information Section
            CollapsibleSection(
                title = "Profile Basics",
                summary = if (displayName.isNotBlank()) displayName else "Add your name and details",
                icon = Icons.Default.Person,
                initiallyExpanded = true
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                ) {
                    Text(
                        text = "Your basic information helps others recognize you",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {
                            hasDraftEdits = true
                            displayName = it
                            displayNameError = null
                        },
                        label = { Text("Display Name") },
                        supportingText = displayNameError?.let { { Text(it) } },
                        isError = displayNameError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = bio,
                        onValueChange = {
                            hasDraftEdits = true
                            bio = it
                            bioError = null
                        },
                        label = { Text("Bio") },
                        supportingText = if (bioError != null) {
                            { Text(bioError!!) }
                        } else {
                            { Text("${bio.length}/200 characters") }
                        },
                        isError = bioError != null,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                    ) {
                        OutlinedTextField(
                            value = age,
                            onValueChange = {
                                hasDraftEdits = true
                                age = it
                                ageError = null
                            },
                            label = { Text("Age") },
                            supportingText = ageError?.let { { Text(it) } },
                            isError = ageError != null,
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = weight,
                            onValueChange = {
                                hasDraftEdits = true
                                weight = it
                                weightError = null
                            },
                            label = { Text("Weight (lbs)") },
                            supportingText = weightError?.let { { Text(it) } },
                            isError = weightError != null,
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Fitness Goals Section
            CollapsibleSection(
                title = "Fitness Goals",
                summary = "${selectedGoals.size} goal${if (selectedGoals.size != 1) "s" else ""} selected",
                icon = Icons.Default.Star,
                initiallyExpanded = false
            ) {
                MultiSelectChipGroup(
                    items = FitnessGoal.values().toList(),
                    selectedItems = selectedGoals,
                    onSelectionChange = {
                        hasDraftEdits = true
                        selectedGoalNames = it.map { goal -> goal.name }
                    },
                    itemLabel = { it.displayName },
                    helperText = "Select one or more fitness goals to personalize your experience"
                )
            }

            // Equipment Access Section
            CollapsibleSection(
                title = "Available Equipment",
                summary = "${selectedEquipment.size} item${if (selectedEquipment.size != 1) "s" else ""} selected",
                icon = Icons.Default.FitnessCenter,
                initiallyExpanded = false
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                ) {
                    MultiSelectChipGroup(
                        items = Equipment.values().toList(),
                        selectedItems = selectedEquipment,
                        onSelectionChange = {
                            hasDraftEdits = true
                            selectedEquipmentNames = it.map { equipment -> equipment.name }
                        },
                        itemLabel = { it.displayName },
                        helperText = "Select the equipment you have access to for better workout recommendations"
                    )

                    OutlinedTextField(
                        value = otherEquipment,
                        onValueChange = {
                            hasDraftEdits = true
                            otherEquipment = it
                        },
                        label = { Text("Other Equipment") },
                        supportingText = { Text("${otherEquipment.length}/200 characters") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Privacy Settings Section
            CollapsibleSection(
                title = "Privacy",
                summary = if (isPublic) "Profile is public" else "Profile is private",
                icon = Icons.Default.Lock,
                initiallyExpanded = false
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Public Profile",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isPublic)
                                    "Others can find and view your profile"
                                else
                                    "Your profile is hidden from other users",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPublic,
                            onCheckedChange = {
                                hasDraftEdits = true
                                isPublic = it
                            }
                        )
                    }

                    if (isPublic) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(LiftrixSpacing.medium),
                                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Public profiles appear in search results and allow others to view your workout statistics.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Bottom spacing to ensure content isn't hidden behind bottom bar
            Spacer(modifier = Modifier.height(LiftrixSpacing.large))
        }
    }
}

/**
 * Data class to track original profile values for change detection
 */
private data class ProfileFormData(
    val userId: String,
    val displayName: String,
    val bio: String,
    val age: String,
    val weight: String,
    val isPublic: Boolean,
    val selectedGoals: Set<FitnessGoal>,
    val selectedEquipment: Set<Equipment>,
    val otherEquipment: String
)

/**
 * Validation function for profile fields
 */
private fun validateFields(
    displayName: String,
    bio: String,
    age: String,
    weight: String,
    onDisplayNameError: (String?) -> Unit,
    onBioError: (String?) -> Unit,
    onAgeError: (String?) -> Unit,
    onWeightError: (String?) -> Unit
): Boolean {
    var isValid = true

    // Validate display name
    when {
        displayName.isBlank() -> {
            onDisplayNameError("Display name is required")
            isValid = false
        }
        displayName.length < 2 -> {
            onDisplayNameError("Display name must be at least 2 characters")
            isValid = false
        }
        displayName.length > 50 -> {
            onDisplayNameError("Display name cannot exceed 50 characters")
            isValid = false
        }
        else -> onDisplayNameError(null)
    }

    // Validate bio
    when {
        bio.isNotBlank() && bio.length < 10 -> {
            onBioError("Bio must be at least 10 characters or left empty")
            isValid = false
        }
        bio.length > 200 -> {
            onBioError("Bio cannot exceed 200 characters")
            isValid = false
        }
        else -> onBioError(null)
    }

    // Validate age
    if (age.isNotBlank()) {
        val ageInt = age.toIntOrNull()
        when {
            ageInt == null -> {
                onAgeError("Please enter a valid age")
                isValid = false
            }
            ageInt < 13 -> {
                onAgeError("Age must be at least 13")
                isValid = false
            }
            ageInt > 120 -> {
                onAgeError("Age cannot exceed 120")
                isValid = false
            }
            else -> onAgeError(null)
        }
    } else {
        onAgeError(null)
    }

    // Validate weight
    if (weight.isNotBlank()) {
        val weightDouble = weight.toDoubleOrNull()
        when {
            weightDouble == null -> {
                onWeightError("Please enter a valid weight")
                isValid = false
            }
            weightDouble < 50 -> {
                onWeightError("Weight must be at least 50 lbs")
                isValid = false
            }
            weightDouble > 1000 -> {
                onWeightError("Weight cannot exceed 1000 lbs")
                isValid = false
            }
            else -> onWeightError(null)
        }
    } else {
        onWeightError(null)
    }

    return isValid
}
