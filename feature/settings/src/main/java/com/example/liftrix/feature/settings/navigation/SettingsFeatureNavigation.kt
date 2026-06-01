package com.example.liftrix.feature.settings.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.ui.admin.AdminBanManagementScreen
import com.example.liftrix.ui.common.sync.SettingsSyncIntegration
import com.example.liftrix.ui.help.HelpArticleScreen
import com.example.liftrix.ui.help.HelpScreen
import com.example.liftrix.ui.navigation.LiftrixRoute
import com.example.liftrix.ui.settings.DashboardCustomizationScreen
import com.example.liftrix.ui.settings.NotificationSettingsScreen
import com.example.liftrix.ui.settings.PrivacySettingsScreen
import com.example.liftrix.ui.settings.SettingsScreen
import com.example.liftrix.ui.settings.WidgetSettingsScreen
import com.example.liftrix.ui.settings.about.AboutScreen
import com.example.liftrix.ui.settings.account.AccountDeletionFlow
import com.example.liftrix.ui.settings.account.EmailChangeScreen
import com.example.liftrix.ui.settings.account.PasswordChangeScreen
import com.example.liftrix.ui.settings.account.UsernameChangeScreen
import com.example.liftrix.ui.settings.data.DataPortabilityScreen
import com.example.liftrix.ui.settings.data.ProgressReportExportScreen
import com.example.liftrix.ui.settings.legal.AIDisclaimerScreen
import com.example.liftrix.ui.settings.legal.CommunityGuidelinesScreen
import com.example.liftrix.ui.settings.legal.ContentModerationPolicyScreen
import com.example.liftrix.ui.settings.legal.PrivacyPolicyScreen
import com.example.liftrix.ui.settings.legal.RefundSubscriptionPolicyScreen
import com.example.liftrix.ui.settings.legal.TermsOfServiceScreen
import com.example.liftrix.ui.settings.upgrade.UpgradeToPremiumScreen
import com.example.liftrix.ui.settings.sync.SyncSettingsViewModel
import com.example.liftrix.ui.support.ContactSupportScreen
import com.example.liftrix.ui.support.SupportTicketScreen

@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToAnomalyDetection: () -> Unit,
    onNavigateToAnomalyDashboard: () -> Unit,
    onNavigateToWidgetSettings: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToEmailChange: () -> Unit,
    onNavigateToPasswordChange: () -> Unit,
    onNavigateToUsernameChange: () -> Unit,
    onNavigateToAccountDeletion: () -> Unit,
    onNavigateToHelpCenter: () -> Unit,
    onNavigateToContactSupport: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPrivacySettings: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToTermsOfService: () -> Unit,
    onNavigateToAIDisclaimer: () -> Unit,
    onNavigateToCommunityGuidelines: () -> Unit,
    onNavigateToContentModerationPolicy: () -> Unit,
    onNavigateToRefundSubscriptionPolicy: () -> Unit,
    onNavigateToDataPortability: () -> Unit,
    onNavigateToExportProgressReport: () -> Unit,
    onNavigateToAIChatSettings: () -> Unit,
    onNavigateToAdminBanManagement: () -> Unit,
    onNavigateToUpgradeToPremium: () -> Unit
) {
    SettingsScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToAuth = onNavigateToAuth,
        onNavigateToAnomalyDetection = onNavigateToAnomalyDetection,
        onNavigateToAnomalyDashboard = onNavigateToAnomalyDashboard,
        onNavigateToWidgetSettings = onNavigateToWidgetSettings,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToEmailChange = onNavigateToEmailChange,
        onNavigateToPasswordChange = onNavigateToPasswordChange,
        onNavigateToUsernameChange = onNavigateToUsernameChange,
        onNavigateToAccountDeletion = onNavigateToAccountDeletion,
        onNavigateToHelpCenter = onNavigateToHelpCenter,
        onNavigateToContactSupport = onNavigateToContactSupport,
        onNavigateToAbout = onNavigateToAbout,
        onNavigateToPrivacySettings = onNavigateToPrivacySettings,
        onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy,
        onNavigateToTermsOfService = onNavigateToTermsOfService,
        onNavigateToAIDisclaimer = onNavigateToAIDisclaimer,
        onNavigateToCommunityGuidelines = onNavigateToCommunityGuidelines,
        onNavigateToContentModerationPolicy = onNavigateToContentModerationPolicy,
        onNavigateToRefundSubscriptionPolicy = onNavigateToRefundSubscriptionPolicy,
        onNavigateToDataPortability = onNavigateToDataPortability,
        onNavigateToExportProgressReport = onNavigateToExportProgressReport,
        onNavigateToAIChatSettings = onNavigateToAIChatSettings,
        onNavigateToAdminBanManagement = onNavigateToAdminBanManagement,
        onNavigateToUpgradeToPremium = onNavigateToUpgradeToPremium
    )
}

@Composable
fun WidgetSettingsRoute(onNavigateBack: () -> Unit) {
    WidgetSettingsScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun NotificationSettingsRoute(onNavigateBack: () -> Unit) {
    NotificationSettingsScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun DashboardCustomizationRoute(onNavigateBack: () -> Unit) {
    DashboardCustomizationScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun PrivacySettingsRoute(onNavigateBack: () -> Unit) {
    PrivacySettingsScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun EmailChangeRoute(onNavigateBack: () -> Unit) {
    EmailChangeScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun PasswordChangeRoute(onNavigateBack: () -> Unit) {
    PasswordChangeScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun UsernameChangeRoute(onNavigateBack: () -> Unit) {
    UsernameChangeScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun AccountDeletionRoute(
    onNavigateBack: () -> Unit,
    onDeletionCompleted: () -> Unit
) {
    AccountDeletionFlow(
        onNavigateBack = onNavigateBack,
        onDeletionCompleted = onDeletionCompleted,
        showTopBar = false
    )
}

@Composable
fun HelpCenterRoute(
    onNavigateBack: () -> Unit,
    onNavigateToArticle: (String) -> Unit,
    onNavigateToSupport: () -> Unit
) {
    HelpScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToArticle = onNavigateToArticle,
        onNavigateToSupport = onNavigateToSupport,
        showTopBar = false
    )
}

@Composable
fun HelpArticleRoute(
    articleId: String,
    onNavigateBack: () -> Unit
) {
    HelpArticleScreen(
        articleId = articleId,
        onNavigateBack = onNavigateBack,
        showTopBar = false
    )
}

@Composable
fun ContactSupportRoute(
    onNavigateBack: () -> Unit,
    onNavigateToTicket: (String) -> Unit
) {
    ContactSupportScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToTicket = onNavigateToTicket,
        showTopBar = false
    )
}

@Composable
fun SupportTicketRoute(
    ticketId: String,
    onNavigateBack: () -> Unit
) {
    SupportTicketScreen(
        ticketId = ticketId,
        onNavigateBack = onNavigateBack,
        showTopBar = false
    )
}

@Composable
fun AboutRoute(
    onNavigateBack: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToLicenses: () -> Unit
) {
    AboutScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToPrivacy = onNavigateToPrivacy,
        onNavigateToTerms = onNavigateToTerms,
        onNavigateToLicenses = onNavigateToLicenses,
        showTopBar = false
    )
}

@Composable
fun PrivacyPolicyRoute(onNavigateBack: () -> Unit) {
    PrivacyPolicyScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun TermsOfServiceRoute(onNavigateBack: () -> Unit) {
    TermsOfServiceScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun AIDisclaimerRoute(onNavigateBack: () -> Unit) {
    AIDisclaimerScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun CommunityGuidelinesRoute(onNavigateBack: () -> Unit) {
    CommunityGuidelinesScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun ContentModerationPolicyRoute(onNavigateBack: () -> Unit) {
    ContentModerationPolicyScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun RefundSubscriptionPolicyRoute(onNavigateBack: () -> Unit) {
    RefundSubscriptionPolicyScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun DataPortabilityRoute(onNavigateBack: () -> Unit) {
    DataPortabilityScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun ExportProgressReportRoute(onNavigateBack: () -> Unit) {
    ProgressReportExportScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun AdminBanManagementRoute(onNavigateBack: () -> Unit) {
    AdminBanManagementScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

@Composable
fun UpgradeToPremiumRoute(
    onNavigateBack: () -> Unit,
    onContactSupport: () -> Unit
) {
    UpgradeToPremiumScreen(
        onNavigateBack = onNavigateBack,
        onContactSupport = onContactSupport,
        showTopBar = false
    )
}

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    composable<LiftrixRoute.Settings> {
        SettingsRoute(
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToProfile = { navController.navigate(LiftrixRoute.ProfileEdit) },
            onNavigateToAuth = {
                navController.clearBackStackAndNavigate(LiftrixRoute.Home)
            },
            onNavigateToAnomalyDetection = {
                navController.navigate(LiftrixRoute.AnomalySettings)
            },
            onNavigateToAnomalyDashboard = {
                navController.navigate(LiftrixRoute.AnomalyDashboard)
            },
            onNavigateToWidgetSettings = {
                navController.navigate(LiftrixRoute.WidgetSettings)
            },
            onNavigateToNotifications = {
                navController.navigate(LiftrixRoute.NotificationSettings)
            },
            onNavigateToEmailChange = {
                navController.navigate(LiftrixRoute.EmailChange)
            },
            onNavigateToPasswordChange = {
                navController.navigate(LiftrixRoute.PasswordChange)
            },
            onNavigateToUsernameChange = {
                navController.navigate(LiftrixRoute.UsernameChange)
            },
            onNavigateToAccountDeletion = {
                navController.navigate(LiftrixRoute.AccountDeletion)
            },
            onNavigateToHelpCenter = {
                navController.navigate(LiftrixRoute.HelpCenter)
            },
            onNavigateToContactSupport = {
                navController.navigate(LiftrixRoute.ContactSupport)
            },
            onNavigateToAbout = {
                navController.navigate(LiftrixRoute.About)
            },
            onNavigateToPrivacySettings = {
                navController.navigate(LiftrixRoute.PrivacySettings)
            },
            onNavigateToPrivacyPolicy = {
                navController.navigate(LiftrixRoute.PrivacyPolicy)
            },
            onNavigateToTermsOfService = {
                navController.navigate(LiftrixRoute.TermsOfService)
            },
            onNavigateToAIDisclaimer = {
                navController.navigate(LiftrixRoute.AIDisclaimer)
            },
            onNavigateToCommunityGuidelines = {
                navController.navigate(LiftrixRoute.CommunityGuidelines)
            },
            onNavigateToContentModerationPolicy = {
                navController.navigate(LiftrixRoute.ContentModerationPolicy)
            },
            onNavigateToRefundSubscriptionPolicy = {
                navController.navigate(LiftrixRoute.RefundSubscriptionPolicy)
            },
            onNavigateToDataPortability = {
                navController.navigate(LiftrixRoute.DataPortability)
            },
            onNavigateToExportProgressReport = {
                navController.navigate(LiftrixRoute.ExportProgressReport)
            },
            onNavigateToAIChatSettings = {
                navController.navigate(LiftrixRoute.AIChatSettings)
            },
            onNavigateToAdminBanManagement = {
                navController.navigate(LiftrixRoute.AdminBanManagement)
            },
            onNavigateToUpgradeToPremium = {
                navController.navigate(LiftrixRoute.UpgradeToPremium)
            }
        )
    }

    composable<LiftrixRoute.SyncSettings> {
        val authQueryUseCase: AuthQueryUseCase =
            hiltViewModel<SyncSettingsViewModel>().authQueryUseCase
        var currentUserId by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            val result = authQueryUseCase(waitForAuth = false)
            currentUserId = result.fold(
                onSuccess = { it.value },
                onFailure = { null }
            )
            isLoading = false
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            currentUserId != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    TopAppBar(
                        title = { Text("Sync Settings") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStackSafely() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )

                    SettingsSyncIntegration(userId = currentUserId!!)
                }
            }
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Authentication required")
                }
            }
        }
    }

    composable<LiftrixRoute.WidgetSettings> {
        WidgetSettingsRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.EmailChange> {
        EmailChangeRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.PasswordChange> {
        PasswordChangeRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.UsernameChange> {
        UsernameChangeRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.AccountDeletion> {
        AccountDeletionRoute(
            onNavigateBack = { navController.popBackStackSafely() },
            onDeletionCompleted = {
                navController.clearBackStackAndNavigate(LiftrixRoute.AuthSignIn)
            }
        )
    }

    composable<LiftrixRoute.HelpCenter> {
        HelpCenterRoute(
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToArticle = { articleId ->
                navController.navigate(LiftrixRoute.HelpArticle(articleId))
            },
            onNavigateToSupport = {
                navController.navigate(LiftrixRoute.ContactSupport)
            }
        )
    }

    composable<LiftrixRoute.HelpArticle> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.HelpArticle>()
        HelpArticleRoute(
            articleId = route.articleId,
            onNavigateBack = { navController.popBackStackSafely() }
        )
    }

    composable<LiftrixRoute.ContactSupport> {
        ContactSupportRoute(
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToTicket = { ticketId ->
                navController.navigate(LiftrixRoute.SupportTicket(ticketId))
            }
        )
    }

    composable<LiftrixRoute.SupportTicket> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.SupportTicket>()
        SupportTicketRoute(
            ticketId = route.ticketId,
            onNavigateBack = { navController.popBackStackSafely() }
        )
    }

    composable<LiftrixRoute.About> {
        AboutRoute(
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToPrivacy = {
                navController.navigate(LiftrixRoute.PrivacyPolicy)
            },
            onNavigateToTerms = {
                navController.navigate(LiftrixRoute.TermsOfService)
            },
            onNavigateToLicenses = {
                navController.navigate(LiftrixRoute.HelpCenter)
            }
        )
    }

    composable<LiftrixRoute.PrivacyPolicy> {
        PrivacyPolicyRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.TermsOfService> {
        TermsOfServiceRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.AIDisclaimer> {
        AIDisclaimerRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.CommunityGuidelines> {
        CommunityGuidelinesRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.ContentModerationPolicy> {
        ContentModerationPolicyRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.RefundSubscriptionPolicy> {
        RefundSubscriptionPolicyRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.DataPortability> {
        DataPortabilityRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.ExportProgressReport> {
        ExportProgressReportRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.NotificationSettings> {
        NotificationSettingsRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.DashboardCustomization> {
        DashboardCustomizationRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.PrivacySettings> {
        PrivacySettingsRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.AdminBanManagement> {
        AdminBanManagementRoute(onNavigateBack = { navController.popBackStackSafely() })
    }

    composable<LiftrixRoute.UpgradeToPremium> {
        UpgradeToPremiumRoute(
            onNavigateBack = { navController.popBackStackSafely() },
            onContactSupport = {
                navController.navigate(LiftrixRoute.ContactSupport)
            }
        )
    }
}

private fun NavController.popBackStackSafely(): Boolean {
    return if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        false
    }
}

private fun NavController.clearBackStackAndNavigate(route: LiftrixRoute) {
    navigate(route) {
        popUpTo(0) {
            inclusive = true
        }
        launchSingleTop = true
    }
}
