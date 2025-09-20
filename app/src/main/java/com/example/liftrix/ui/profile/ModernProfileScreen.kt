package com.example.liftrix.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.ui.profile.components.ProfileImageDisplay
import com.example.liftrix.ui.theme.LiftrixColorsV2
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Modern Profile Screen with premium fitness app experience
 * Features:
 * - 80px avatar with gradient background
 * - 2x2 grid stats layout with card system
 * - Elevated dark card surfaces (#2D2D2D)
 * - Consistent 16px border radius
 * - 24px major section gaps
 * - Teal accent color (#00BCD4)
 */
@Composable
fun ModernProfileScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Use LiftrixColorsV2 design system
    val backgroundColor = LiftrixColorsV2.Dark.BackgroundPrimary
    val cardBackgroundColor = LiftrixColorsV2.Dark.BackgroundSecondary
    val primaryTeal = LiftrixColorsV2.Teal
    val textPrimary = LiftrixColorsV2.Dark.TextPrimary
    val textSecondary = LiftrixColorsV2.Dark.TextSecondary
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Top App Bar
            ModernProfileTopBar(
                onNavigateBack = onNavigateBack,
                onNavigateToSettings = onNavigateToSettings
            )
            
            when {
                uiState.isLoading && uiState.profile == null -> {
                    ModernLoadingState()
                }
                uiState.profile != null -> {
                    ModernProfileContent(
                        profile = uiState.profile!!,
                        profileImageUrl = uiState.effectiveProfileImageUrl
                    )
                }
                else -> {
                    ModernEmptyState()
                }
            }
        }
        
        // Floating Action Button
        FloatingActionButton(
            onClick = { /* Handle action */ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(56.dp),
            containerColor = primaryTeal,
            contentColor = backgroundColor,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernProfileTopBar(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    TopAppBar(
        title = { 
            Text(
                "Profile",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = LiftrixColorsV2.Dark.TextPrimary
                )
            }
        },
        actions = {
            IconButton(onClick = { /* Grid view */ }) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "Grid View",
                    tint = LiftrixColorsV2.Dark.TextPrimary
                )
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = LiftrixColorsV2.Dark.TextPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = LiftrixColorsV2.Dark.BackgroundPrimary
        )
    )
}

@Composable
private fun ModernProfileContent(
    profile: UserProfile,
    profileImageUrl: String?
) {
    val cardBackgroundColor = LiftrixColorsV2.Dark.BackgroundSecondary
    val primaryTeal = LiftrixColorsV2.Teal
    val textPrimary = LiftrixColorsV2.Dark.TextPrimary
    val textSecondary = LiftrixColorsV2.Dark.TextSecondary
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Profile Header Section with Gradient Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            LiftrixColorsV2.Teal.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar - 80px diameter with consistent image handling
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(primaryTeal),
                    contentAlignment = Alignment.Center
                ) {
                    val hasValidImageUrl = !profileImageUrl.isNullOrBlank()
                    timber.log.Timber.d("Own profile avatar for ${profile.displayName}: imageUrl='$profileImageUrl', hasValid=$hasValidImageUrl")
                    
                    if (hasValidImageUrl) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(profileImageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Your profile picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onError = { error ->
                                val httpCode = if (error.result.throwable?.message?.contains("403") == true) "HTTP 403" else "Unknown"
                                timber.log.Timber.e("Failed to load own profile image: $profileImageUrl | Error=$httpCode | ${error.result.throwable?.message}")
                            }
                        )
                    } else {
                        Text(
                            text = profile.displayName.take(2).uppercase(),
                            color = LiftrixColorsV2.Dark.BackgroundPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Profile Name
                Text(
                    text = profile.displayName,
                    color = textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Level Badge (smaller)
                Surface(
                    modifier = Modifier.padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = primaryTeal
                ) {
                    Text(
                        text = "Beginner",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = LiftrixColorsV2.Dark.BackgroundPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Stats Summary Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModernStatItem(
                        icon = Icons.Default.FitnessCenter,
                        value = profile.totalWorkouts.toString(),
                        label = "Workouts",
                        inline = true
                    )
                    
                    Spacer(modifier = Modifier.width(32.dp))
                    
                    ModernStatItem(
                        icon = Icons.Default.CalendarToday,
                        value = formatMemberDate(profile.memberSince),
                        label = "Member Since",
                        inline = true
                    )
                }
            }
        }
        
        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Primary Connect Button
            Button(
                onClick = { /* Handle connect */ },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryTeal,
                    contentColor = LiftrixColorsV2.Dark.BackgroundPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Connect",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Secondary Message Button
            OutlinedButton(
                onClick = { /* Handle message */ },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = primaryTeal
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(primaryTeal, primaryTeal))
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Message,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Message",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // About Section Card
        if (!profile.bio.isNullOrBlank()) {
            ModernCard(title = "About") {
                Text(
                    text = profile.bio,
                    color = textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
        
        // Fitness Overview - 2x2 Grid
        ModernCard(title = "Fitness Overview") {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ModernStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Timer,
                        value = "0h 0m",
                        label = "TOTAL TIME",
                        iconColor = primaryTeal
                    )
                    ModernStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AvTimer,
                        value = "0min",
                        label = "AVG SESSION",
                        iconColor = primaryTeal
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ModernStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.TrendingUp,
                        value = "${profile.currentStreak} days",
                        label = "CURRENT STREAK",
                        iconColor = primaryTeal
                    )
                    ModernStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.EmojiEvents,
                        value = "${profile.longestStreak} days",
                        label = "BEST STREAK",
                        iconColor = primaryTeal
                    )
                }
            }
        }
        
        // Add bottom spacing for FAB
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun ModernCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                spotColor = Color.Black.copy(alpha = 0.1f),
                ambientColor = Color.Black.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.Dark.BackgroundSecondary
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = LiftrixColorsV2.Dark.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun ModernStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    iconColor: Color
) {
    Card(
        modifier = modifier
            .aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.Dark.BackgroundPrimary
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = LiftrixColorsV2.Dark.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = LiftrixColorsV2.Dark.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModernStatItem(
    icon: ImageVector,
    value: String,
    label: String,
    inline: Boolean = false
) {
    if (inline) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = LiftrixColorsV2.Teal
            )
            Column {
                Text(
                    text = value,
                    color = LiftrixColorsV2.Dark.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = label,
                    color = LiftrixColorsV2.Dark.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ModernLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = LiftrixColorsV2.Teal
        )
    }
}

@Composable
private fun ModernEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "No profile data available",
                color = LiftrixColorsV2.Dark.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Complete your profile to get started",
                color = LiftrixColorsV2.Dark.TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

// Helper function - safe date formatting to prevent null crashes
private fun formatMemberDate(memberSince: LocalDateTime?): String {
    return try {
        memberSince?.format(DateTimeFormatter.ofPattern("MMM yyyy")) ?: "Unknown"
    } catch (e: Exception) {
        "Unknown"
    }
}