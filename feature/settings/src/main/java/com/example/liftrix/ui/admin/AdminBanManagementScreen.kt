package com.example.liftrix.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.admin.AdminBanInfo
import com.example.liftrix.domain.model.admin.AdminUserInfo
import com.example.liftrix.domain.model.admin.BanSeverity
import com.example.liftrix.ui.common.components.LoadingDialog
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Admin screen for managing user bans and moderation actions.
 * 
 * ★ Insight ─────────────────────────────────────
 * - Implements secure admin controls with Firebase Admin SDK integration
 * - Provides comprehensive ban management including search, ban history, and severity controls  
 * - Includes audit trails and confirmation dialogs to prevent accidental bans
 * ─────────────────────────────────────────────────
 * 
 * Features:
 * - User search and ban status lookup
 * - Ban/unban actions with reason tracking
 * - Ban history and admin action logs
 * - Severity-based ban classification
 * - Admin permissions verification
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBanManagementScreen(
    onNavigateBack: () -> Unit,
    showTopBar: Boolean = true,
    viewModel: AdminBanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var showBanDialog by remember { mutableStateOf<AdminUserInfo?>(null) }
    var showUnbanDialog by remember { mutableStateOf<AdminUserInfo?>(null) }
    var selectedTab by remember { mutableStateOf(AdminTab.SEARCH) }
    
    LaunchedEffect(Unit) {
        viewModel.handleEvent(AdminBanEvent.LoadBannedUsers)
        viewModel.checkAdminPermissions()
    }
    
    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                title = { Text("User Ban Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            when (selectedTab) {
                                AdminTab.SEARCH -> viewModel.handleEvent(AdminBanEvent.RefreshSearch)
                                AdminTab.BANNED_USERS -> viewModel.handleEvent(AdminBanEvent.LoadBannedUsers)
                                AdminTab.ADMIN_LOGS -> viewModel.handleEvent(AdminBanEvent.LoadAdminLogs)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Admin permissions check
            if (!uiState.isAdmin) {
                AdminPermissionDeniedCard()
                return@Column
            }
            
            // Tab navigation
            AdminTabRow(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    selectedTab = tab
                    when (tab) {
                        AdminTab.SEARCH -> Unit
                        AdminTab.BANNED_USERS -> viewModel.handleEvent(AdminBanEvent.LoadBannedUsers)
                        AdminTab.ADMIN_LOGS -> viewModel.handleEvent(AdminBanEvent.LoadAdminLogs)
                    }
                }
            )
            
            // Content based on selected tab
            when (selectedTab) {
                AdminTab.SEARCH -> {
                    SearchUsersContent(
                        searchQuery = searchQuery,
                        onSearchQueryChanged = { searchQuery = it },
                        searchResults = uiState.searchResults,
                        isSearching = uiState.isLoading,
                        onSearchUsers = { 
                            viewModel.handleEvent(AdminBanEvent.SearchUsers(searchQuery))
                        },
                        onBanUser = { showBanDialog = it },
                        onUnbanUser = { showUnbanDialog = it },
                        onViewUserDetails = { userId ->
                            viewModel.handleEvent(AdminBanEvent.GetUserBanInfo(userId))
                        }
                    )
                }
                AdminTab.BANNED_USERS -> {
                    BannedUsersContent(
                        bannedUsers = uiState.bannedUsers,
                        isLoading = uiState.isLoading,
                        onUnbanUser = { showUnbanDialog = it },
                        onViewUserDetails = { userId ->
                            viewModel.handleEvent(AdminBanEvent.GetUserBanInfo(userId))
                        }
                    )
                }
                AdminTab.ADMIN_LOGS -> {
                    AdminLogsContent(
                        adminLogs = uiState.adminLogs,
                        isLoading = uiState.isLoading
                    )
                }
            }
        }
    }
    
    // Ban confirmation dialog
    showBanDialog?.let { user ->
        BanUserDialog(
            user = user,
            onBanConfirmed = { reason, severity, duration ->
                viewModel.handleEvent(
                    AdminBanEvent.BanUser(
                        userId = user.uid,
                        reason = reason,
                        severity = severity,
                        banDuration = duration
                    )
                )
                showBanDialog = null
            },
            onDismiss = { showBanDialog = null }
        )
    }
    
    // Unban confirmation dialog
    showUnbanDialog?.let { user ->
        UnbanUserDialog(
            user = user,
            onUnbanConfirmed = { reason ->
                viewModel.handleEvent(
                    AdminBanEvent.UnbanUser(
                        userId = user.uid,
                        reason = reason
                    )
                )
                showUnbanDialog = null
            },
            onDismiss = { showUnbanDialog = null }
        )
    }
    
    // Loading states
    if (uiState.isLoading) {
        LoadingDialog(message = "Processing request...")
    }
    
    // Error handling
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error snackbar or dialog
            delay(5000)
            viewModel.handleEvent(AdminBanEvent.ClearError)
        }
    }
}

@Composable
private fun AdminPermissionDeniedCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LiftrixSpacing.medium),
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.Dark.Error.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "Access denied",
                tint = LiftrixColorsV2.Dark.Error,
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            
            Text(
                text = "Access Denied",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = LiftrixColorsV2.Dark.Error
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            Text(
                text = "You don't have admin permissions to access this feature.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AdminTabRow(
    selectedTab: AdminTab,
    onTabSelected: (AdminTab) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = LiftrixColorsV2.surface,
        contentColor = LiftrixColorsV2.primary
    ) {
        AdminTab.values().forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = { Text(tab.title) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title
                    )
                }
            )
        }
    }
}

@Composable
private fun SearchUsersContent(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    searchResults: List<AdminUserInfo>,
    isSearching: Boolean,
    onSearchUsers: () -> Unit,
    onBanUser: (AdminUserInfo) -> Unit,
    onUnbanUser: (AdminUserInfo) -> Unit,
    onViewUserDetails: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LiftrixSpacing.medium)
    ) {
        // Search input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Search users by email or name...") },
                modifier = Modifier.weight(1f),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }
            )
            
            Spacer(modifier = Modifier.width(LiftrixSpacing.small))
            
            Button(
                onClick = onSearchUsers,
                enabled = searchQuery.length >= 3 && !isSearching
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Search")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
        
        // Search results
        if (searchResults.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
            ) {
                items(searchResults) { user ->
                    UserCard(
                        user = user,
                        onBanUser = onBanUser,
                        onUnbanUser = onUnbanUser,
                        onViewDetails = onViewUserDetails
                    )
                }
            }
        } else if (searchQuery.isNotEmpty() && !isSearching) {
            EmptySearchResults()
        }
    }
}

@Composable
private fun UserCard(
    user: AdminUserInfo,
    onBanUser: (AdminUserInfo) -> Unit,
    onUnbanUser: (AdminUserInfo) -> Unit,
    onViewDetails: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (user.currentlyBanned) {
                LiftrixColorsV2.Dark.Error.copy(alpha = 0.1f)
            } else {
                LiftrixColorsV2.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName ?: "Unknown User",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    user.email?.let { email ->
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Text(
                        text = "UID: ${user.uid.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (user.currentlyBanned) {
                        Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                        
                        Text(
                            text = "🚫 BANNED",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = LiftrixColorsV2.Dark.Error
                        )
                    }
                }
                
                // Action buttons
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    if (user.currentlyBanned) {
                        Button(
                            onClick = { onUnbanUser(user) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LiftrixColorsV2.Dark.Success
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Unban",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Unban")
                        }
                    } else {
                        Button(
                            onClick = { onBanUser(user) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LiftrixColorsV2.Dark.Error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Ban",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ban")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                    
                    TextButton(
                        onClick = { onViewDetails(user.uid) }
                    ) {
                        Text("View Details")
                    }
                }
            }
        }
    }
}

@Composable
private fun BanUserDialog(
    user: AdminUserInfo,
    onBanConfirmed: (reason: String, severity: BanSeverity, duration: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(BanSeverity.MODERATE) }
    var duration by remember { mutableStateOf<String?>(null) }
    var isPermanent by remember { mutableStateOf(true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Ban User",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "You are about to ban user:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = LiftrixColorsV2.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(LiftrixSpacing.medium)
                    ) {
                        Text(
                            text = user.displayName ?: "Unknown User",
                            fontWeight = FontWeight.Bold
                        )
                        user.email?.let { email ->
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
                
                // Reason input
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Ban Reason *") },
                    placeholder = { Text("Enter reason for ban...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
                
                // Severity selection
                Text(
                    text = "Severity",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                
                BanSeverity.values().forEach { sev ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = severity == sev,
                            onClick = { severity = sev }
                        )
                        Text(
                            text = sev.displayName,
                            modifier = Modifier.padding(start = LiftrixSpacing.small)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
                
                // Duration selection
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isPermanent,
                        onCheckedChange = { 
                            isPermanent = it
                            if (it) duration = null
                        }
                    )
                    Text(
                        text = "Permanent Ban",
                        modifier = Modifier.padding(start = LiftrixSpacing.small)
                    )
                }
                
                if (!isPermanent) {
                    OutlinedTextField(
                        value = duration ?: "",
                        onValueChange = { duration = it },
                        label = { Text("Duration") },
                        placeholder = { Text("e.g., 7d, 30d, 1y") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onBanConfirmed(reason, severity, if (isPermanent) null else duration)
                },
                enabled = reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiftrixColorsV2.Dark.Error
                )
            ) {
                Text("Ban User")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun UnbanUserDialog(
    user: AdminUserInfo,
    onUnbanConfirmed: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Unban User",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("You are about to unban user: ${user.displayName ?: user.email}")
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
                
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Unban Reason") },
                    placeholder = { Text("Appeal approved, mistake, etc.") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onUnbanConfirmed(reason) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiftrixColorsV2.Dark.Success
                )
            ) {
                Text("Unban User")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BannedUsersContent(
    bannedUsers: List<AdminBanInfo>,
    isLoading: Boolean,
    onUnbanUser: (AdminUserInfo) -> Unit,
    onViewUserDetails: (String) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (bannedUsers.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(LiftrixSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
        ) {
            items(bannedUsers) { banInfo ->
                BannedUserCard(
                    banInfo = banInfo,
                    onUnbanUser = onUnbanUser,
                    onViewDetails = onViewUserDetails
                )
            }
        }
    } else {
        EmptyBannedUsers()
    }
}

@Composable
private fun BannedUserCard(
    banInfo: AdminBanInfo,
    onUnbanUser: (AdminUserInfo) -> Unit,
    onViewDetails: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.Dark.Error.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = banInfo.userDisplayName ?: "Unknown User",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    banInfo.userEmail?.let { email ->
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                    
                    Text(
                        text = "Reason: ${banInfo.reason}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Text(
                        text = "Severity: ${banInfo.severity.displayName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Text(
                        text = "Banned: ${formatDate(banInfo.bannedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Button(
                        onClick = {
                            onUnbanUser(
                                AdminUserInfo(
                                    uid = banInfo.userId,
                                    email = banInfo.userEmail,
                                    displayName = banInfo.userDisplayName,
                                    accountStatus = "banned",
                                    createdAt = null,
                                    lastActive = null,
                                    currentlyBanned = true
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LiftrixColorsV2.Dark.Success
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Unban",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Unban")
                    }
                    
                    Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                    
                    TextButton(
                        onClick = { onViewDetails(banInfo.userId) }
                    ) {
                        Text("Details")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminLogsContent(
    adminLogs: List<AdminBanInfo>,
    isLoading: Boolean
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(LiftrixSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
        ) {
            items(adminLogs) { log ->
                AdminLogCard(log = log)
            }
        }
    }
}

@Composable
private fun AdminLogCard(log: AdminBanInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (log.status == "active") Icons.Default.Block else Icons.Default.CheckCircle,
                    contentDescription = if (log.status == "active") "User banned" else "User unbanned",
                    tint = if (log.status == "active") LiftrixColorsV2.Dark.Error else LiftrixColorsV2.Dark.Success,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(LiftrixSpacing.small))
                
                Text(
                    text = if (log.status == "active") "User Banned" else "User Unbanned",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = formatDate(log.bannedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            Text(
                text = "User: ${log.userDisplayName ?: log.userEmail ?: "Unknown"}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "Reason: ${log.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptySearchResults() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "No users found",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            
            Text(
                text = "No users found",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Try a different search term",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyBannedUsers() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "No banned users",
                modifier = Modifier.size(64.dp),
                tint = LiftrixColorsV2.Dark.Success
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            
            Text(
                text = "No banned users",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "All users are currently active",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Helper function to format dates
private fun formatDate(timestamp: Any?): String {
    return try {
        val date = when (timestamp) {
            is String -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(timestamp)
            is Date -> timestamp
            else -> Date()
        }
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(date ?: Date())
    } catch (e: Exception) {
        "Unknown"
    }
}

// Enums and data classes
enum class AdminTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SEARCH("Search Users", Icons.Default.Search),
    BANNED_USERS("Banned Users", Icons.Default.Block),
    ADMIN_LOGS("Admin Logs", Icons.Default.History)
}
