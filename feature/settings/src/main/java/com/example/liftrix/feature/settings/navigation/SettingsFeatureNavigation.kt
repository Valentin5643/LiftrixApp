package com.example.liftrix.feature.settings.navigation

import androidx.compose.runtime.Composable
import com.example.liftrix.ui.admin.AdminBanManagementScreen
import com.example.liftrix.ui.help.HelpArticleScreen
import com.example.liftrix.ui.help.HelpScreen
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
import com.example.liftrix.ui.settings.legal.AIDisclaimerScreen
import com.example.liftrix.ui.settings.legal.CommunityGuidelinesScreen
import com.example.liftrix.ui.settings.legal.ContentModerationPolicyScreen
import com.example.liftrix.ui.settings.legal.PrivacyPolicyScreen
import com.example.liftrix.ui.settings.legal.RefundSubscriptionPolicyScreen
import com.example.liftrix.ui.settings.legal.TermsOfServiceScreen
import com.example.liftrix.ui.settings.upgrade.UpgradeToPremiumScreen
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
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToTermsOfService: () -> Unit,
    onNavigateToAIDisclaimer: () -> Unit,
    onNavigateToCommunityGuidelines: () -> Unit,
    onNavigateToContentModerationPolicy: () -> Unit,
    onNavigateToRefundSubscriptionPolicy: () -> Unit,
    onNavigateToDataPortability: () -> Unit,
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
        onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy,
        onNavigateToTermsOfService = onNavigateToTermsOfService,
        onNavigateToAIDisclaimer = onNavigateToAIDisclaimer,
        onNavigateToCommunityGuidelines = onNavigateToCommunityGuidelines,
        onNavigateToContentModerationPolicy = onNavigateToContentModerationPolicy,
        onNavigateToRefundSubscriptionPolicy = onNavigateToRefundSubscriptionPolicy,
        onNavigateToDataPortability = onNavigateToDataPortability,
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
