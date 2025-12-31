package com.example.liftrix.ui.settings.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.BuildConfig
import com.example.liftrix.R
import com.example.liftrix.ui.common.ContextualColorOverlay
import com.example.liftrix.ui.common.ColorContext
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
import com.example.liftrix.ui.settings.components.SettingsNavigationItem
import com.example.liftrix.ui.theme.LiftrixTheme
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * About screen displaying app information, version details, and credits
 * 
 * Features:
 * - App logo and branding
 * - Version information with build details
 * - Legal document navigation (Privacy Policy, Terms of Service)
 * - Open source licenses information
 * - Developer credits and acknowledgments
 * - App statistics and build information
 * - Social links and contact information
 * - Accessibility support throughout
 * 
 * @param onNavigateBack Callback to navigate back to previous screen
 * @param onNavigateToPrivacy Callback to navigate to privacy policy
 * @param onNavigateToTerms Callback to navigate to terms of service
 * @param onNavigateToLicenses Callback to navigate to open source licenses
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToLicenses: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // App branding section
            item {
                AppBrandingSection()
            }
            
            // Version information
            item {
                VersionInformationCard()
            }
            
            // Legal documents section
            item {
                LegalDocumentsSection(
                    onNavigateToPrivacy = onNavigateToPrivacy,
                    onNavigateToTerms = onNavigateToTerms,
                    onNavigateToLicenses = onNavigateToLicenses
                )
            }
            
            // App statistics
            item {
                AppStatisticsCard()
            }
            
            // Social and contact links
            item {
                SocialLinksSection(
                    onLinkClicked = { url ->
                        try {
                            uriHandler.openUri(url)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open URL: $url")
                        }
                    }
                )
            }
            
            // Developer credits
            item {
                DeveloperCreditsCard()
            }
            
            // Build information for debug
            if (BuildConfig.DEBUG) {
                item {
                    DebugInformationCard()
                }
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * App branding section with logo and name
 */
@Composable
private fun AppBrandingSection(
    modifier: Modifier = Modifier
) {
    ContextualColorOverlay(
        context = ColorContext.UserPreference(
            preferredColor = MaterialTheme.colorScheme.primary,
            intensity = 0.3f
        ),
        modifier = modifier
    ) {
        LiftrixCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // App logo placeholder - replace with actual logo
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = "Liftrix logo",
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Liftrix",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "Your Personal Fitness Companion",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Track workouts, monitor progress, and achieve your fitness goals with our comprehensive fitness tracking application.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            }
        }
    }
}

/**
 * Version information card
 */
@Composable
private fun VersionInformationCard(
    modifier: Modifier = Modifier
) {
    ElevatedLiftrixCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Version Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            VersionInfoRow(
                label = "Version",
                value = BuildConfig.VERSION_NAME
            )
            
            VersionInfoRow(
                label = "Build",
                value = BuildConfig.VERSION_CODE.toString()
            )
            
            VersionInfoRow(
                label = "Build Type",
                value = BuildConfig.BUILD_TYPE.capitalize()
            )
            
            VersionInfoRow(
                label = "Build Date",
                value = getBuildDate()
            )
            
            if (BuildConfig.DEBUG) {
                VersionInfoRow(
                    label = "Package",
                    value = BuildConfig.APPLICATION_ID
                )
            }
        }
    }
}

/**
 * Individual version info row
 */
@Composable
private fun VersionInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Legal documents section
 */
@Composable
private fun LegalDocumentsSection(
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Legal & Licenses",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        ElevatedLiftrixCard {
            Column {
                SettingsNavigationItem(
                    title = "Privacy Policy",
                    subtitle = "How we handle your data and privacy",
                    icon = Icons.Default.Security,
                    onClick = onNavigateToPrivacy,
                    showDivider = true
                )
                
                SettingsNavigationItem(
                    title = "Terms of Service",
                    subtitle = "Terms and conditions of use",
                    icon = Icons.Default.Description,
                    onClick = onNavigateToTerms,
                    showDivider = true
                )
                
                SettingsNavigationItem(
                    title = "Open Source Licenses",
                    subtitle = "Third-party software acknowledgments",
                    icon = Icons.Default.Code,
                    onClick = onNavigateToLicenses,
                    showDivider = false
                )
            }
        }
    }
}

/**
 * App statistics card
 */
@Composable
private fun AppStatisticsCard(
    modifier: Modifier = Modifier
) {
    ElevatedLiftrixCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "App Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "This version of Liftrix includes:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            StatisticItem(
                icon = Icons.Default.FitnessCenter,
                text = "85+ Use Cases for comprehensive functionality"
            )
            
            StatisticItem(
                icon = Icons.Default.Storage,
                text = "27 Database entities with 45 migrations"
            )
            
            StatisticItem(
                icon = Icons.Default.Dashboard,
                text = "15 Analytics widgets with modern charts"
            )
            
            StatisticItem(
                icon = Icons.Default.Group,
                text = "Social features with gym buddy system"
            )
            
            StatisticItem(
                icon = Icons.Default.Security,
                text = "User-scoped data with privacy controls"
            )
        }
    }
}

/**
 * Individual statistic item
 */
@Composable
private fun StatisticItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Social links section
 */
@Composable
private fun SocialLinksSection(
    onLinkClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Connect & Support",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        ElevatedLiftrixCard {
            Column {
                SocialLinkItem(
                    icon = Icons.Default.Web,
                    title = "Official Website",
                    subtitle = "Visit liftrix.com for more information",
                    url = "https://liftrix.com",
                    onLinkClicked = onLinkClicked,
                    showDivider = true
                )
                
                SocialLinkItem(
                    icon = Icons.Default.Email,
                    title = "Contact Support",
                    subtitle = "Get help via valijianu98@gmail.com",
                    url = "mailto:valijianu98@gmail.com",
                    onLinkClicked = onLinkClicked,
                    showDivider = true
                )
                
                SocialLinkItem(
                    icon = Icons.Default.Star,
                    title = "Rate the App",
                    subtitle = "Leave a review on the Play Store",
                    url = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}",
                    onLinkClicked = onLinkClicked,
                    showDivider = true
                )
                
                SocialLinkItem(
                    icon = Icons.Default.BugReport,
                    title = "Report Issues",
                    subtitle = "Help us improve by reporting bugs",
                    url = "https://github.com/liftrix/issues",
                    onLinkClicked = onLinkClicked,
                    showDivider = false
                )
            }
        }
    }
}

/**
 * Individual social link item
 */
@Composable
private fun SocialLinkItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    url: String,
    onLinkClicked: (String) -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLinkClicked(url) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open link",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        }
    }
}

/**
 * Developer credits card
 */
@Composable
private fun DeveloperCreditsCard(
    modifier: Modifier = Modifier
) {
    ElevatedLiftrixCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Credits",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "Developed with ❤️ by Valentin",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Special thanks to:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            CreditItem(text = "• Android Jetpack for Compose UI framework")
            CreditItem(text = "• Material Design for design guidelines")
            CreditItem(text = "• Open source contributors for libraries used")
            CreditItem(text = "• Beta testers for valuable feedback")
            CreditItem(text = "• Fitness community for inspiration and support")
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "© 2025 Liftrix. All rights reserved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Individual credit item
 */
@Composable
private fun CreditItem(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp)
    )
}

/**
 * Debug information card (only shown in debug builds)
 */
@Composable
private fun DebugInformationCard(
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ElevatedLiftrixCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Debug Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show")
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse debug info" else "Expand debug info"
                    )
                }
            }
            
            if (expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DebugInfoRow("Package ID", BuildConfig.APPLICATION_ID)
                    DebugInfoRow("Build Type", BuildConfig.BUILD_TYPE)
                    DebugInfoRow("Flavor", "release")
                    DebugInfoRow("Debug Mode", BuildConfig.DEBUG.toString())
                    DebugInfoRow("Compile SDK", android.os.Build.VERSION.SDK_INT.toString())
                    DebugInfoRow("Min SDK", "24")
                    DebugInfoRow("Target SDK", "34")
                }
            }
        }
    }
}

/**
 * Debug info row
 */
@Composable
private fun DebugInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

/**
 * Helper function to get build date
 */
private fun getBuildDate(): String {
    return try {
        // Use a reasonable default since BUILD_TIME might not be available
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        dateFormat.format(Date()) // Current date as fallback
    } catch (e: Exception) {
        "Unknown"
    }
}

/**
 * Preview for about screen
 */
@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    LiftrixTheme {
        AboutScreen(
            onNavigateBack = { },
            onNavigateToPrivacy = { },
            onNavigateToTerms = { },
            onNavigateToLicenses = { }
        )
    }
}
