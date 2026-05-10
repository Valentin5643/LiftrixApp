package com.example.liftrix.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.liftrix.domain.model.ExternalShare
import com.example.liftrix.domain.model.ShareableContent
import com.example.liftrix.domain.model.ShareableContentType
import com.example.liftrix.domain.model.SocialPlatform
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Share workflow screen for sharing workouts to external platforms.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareWorkoutScreen(
    workoutContent: ShareableContent,
    shareUrl: String,
    onNavigateBack: () -> Unit,
    onShareToPlatform: (SocialPlatform, String?) -> Unit,
    onGenerateQRCode: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true
) {
    var customMessage by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf<SocialPlatform?>(null) }
    
    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Share Workout") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (selectedPlatform != null) {
                FloatingActionButton(
                    onClick = {
                        selectedPlatform?.let { platform ->
                            onShareToPlatform(platform, customMessage.takeIf { it.isNotBlank() })
                        }
                    },
                    containerColor = LiftrixColorsV2.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White
                    )
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(LiftrixSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.large)
        ) {
            item {
                WorkoutPreviewCard(
                    content = workoutContent
                )
            }
            
            item {
                ShareLinkCard(
                    shareUrl = shareUrl,
                    onGenerateQRCode = onGenerateQRCode
                )
            }
            
            item {
                PlatformSelectionSection(
                    selectedPlatform = selectedPlatform,
                    onPlatformSelected = { selectedPlatform = it }
                )
            }
            
            item {
                CustomMessageSection(
                    message = customMessage,
                    onMessageChanged = { customMessage = it },
                    selectedPlatform = selectedPlatform
                )
            }
            
            if (selectedPlatform != null) {
                item {
                    SharePreviewCard(
                        platform = selectedPlatform!!,
                        content = workoutContent,
                        customMessage = customMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutPreviewCard(
    content: ShareableContent
) {
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
            Text(
                text = "Workout Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            
            if (content.imageUrl != null) {
                AsyncImage(
                    model = content.imageUrl,
                    contentDescription = "Workout image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            }
            
            Text(
                text = content.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            content.subtitle?.let { subtitle ->
                Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (content.stats.isNotEmpty()) {
                Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
                WorkoutStatsRow(stats = content.stats)
            }
        }
    }
}

@Composable
private fun WorkoutStatsRow(
    stats: Map<String, Any>
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        items(stats.entries.toList()) { (key, value) ->
            StatItem(
                label = key.replace("_", " ").capitalize(),
                value = value.toString()
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.primary.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.small),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = LiftrixColorsV2.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShareLinkCard(
    shareUrl: String,
    onGenerateQRCode: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    
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
            Text(
                text = "Share Link",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = shareUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiftrixColorsV2.primary,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(shareUrl))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy link"
                    )
                }
                
                IconButton(onClick = onGenerateQRCode) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Generate QR Code"
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformSelectionSection(
    selectedPlatform: SocialPlatform?,
    onPlatformSelected: (SocialPlatform) -> Unit
) {
    Column {
        Text(
            text = "Choose Platform",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
        ) {
            items(getSupportedPlatforms()) { platform ->
                PlatformItem(
                    platform = platform,
                    isSelected = selectedPlatform == platform,
                    onClick = { onPlatformSelected(platform) }
                )
            }
        }
    }
}

@Composable
private fun PlatformItem(
    platform: SocialPlatform,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val platformData = getPlatformData(platform)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box {
            Card(
                modifier = Modifier.size(64.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        LiftrixColorsV2.primary
                    } else {
                        platformData.color.copy(alpha = 0.1f)
                    }
                ),
                shape = CircleShape
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = platformData.icon,
                        contentDescription = platform.name,
                        tint = if (isSelected) Color.White else platformData.color,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(
                            color = LiftrixColorsV2.Dark.Success,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.small))
        
        Text(
            text = platformData.displayName,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CustomMessageSection(
    message: String,
    onMessageChanged: (String) -> Unit,
    selectedPlatform: SocialPlatform?
) {
    Column {
        Text(
            text = "Custom Message (Optional)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
        
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChanged,
            placeholder = {
                Text(
                    text = getMessagePlaceholder(selectedPlatform),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )
        
        selectedPlatform?.let { platform ->
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            Text(
                text = getPlatformTip(platform),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SharePreviewCard(
    platform: SocialPlatform,
    content: ShareableContent,
    customMessage: String
) {
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
            Text(
                text = "Share Preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            
            // Platform-specific preview
            when (platform) {
                SocialPlatform.INSTAGRAM -> InstagramPreview(content, customMessage)
                SocialPlatform.WHATSAPP -> WhatsAppPreview(content, customMessage)
                SocialPlatform.TWITTER -> TwitterPreview(content, customMessage)
                SocialPlatform.FACEBOOK -> FacebookPreview(content, customMessage)
                else -> DefaultSharePreview(content, customMessage)
            }
        }
    }
}

@Composable
private fun InstagramPreview(
    content: ShareableContent,
    customMessage: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.medium)
        ) {
            Text(
                text = "Instagram Story Preview",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Story preview would be generated here",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WhatsAppPreview(
    content: ShareableContent,
    customMessage: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF128C7E)
        )
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.medium)
        ) {
            Text(
                text = "WhatsApp Preview",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            Text(
                text = customMessage.ifBlank { "Check out my workout! ${content.title}" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun TwitterPreview(
    content: ShareableContent,
    customMessage: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1DA1F2)
        )
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.medium)
        ) {
            Text(
                text = "Twitter Preview",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            Text(
                text = customMessage.ifBlank { "Just crushed this workout! 💪 ${content.title}" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun FacebookPreview(
    content: ShareableContent,
    customMessage: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1877F2)
        )
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.medium)
        ) {
            Text(
                text = "Facebook Preview",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            Text(
                text = customMessage.ifBlank { content.title },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun DefaultSharePreview(
    content: ShareableContent,
    customMessage: String
) {
    Text(
        text = customMessage.ifBlank { content.title },
        style = MaterialTheme.typography.bodyMedium
    )
}

// Helper functions and data
private fun getSupportedPlatforms(): List<SocialPlatform> {
    return listOf(
        SocialPlatform.INSTAGRAM,
        SocialPlatform.WHATSAPP,
        SocialPlatform.TWITTER,
        SocialPlatform.FACEBOOK,
        SocialPlatform.TELEGRAM
    )
}

private data class PlatformData(
    val displayName: String,
    val icon: ImageVector,
    val color: Color
)

private fun getPlatformData(platform: SocialPlatform): PlatformData {
    return when (platform) {
        SocialPlatform.INSTAGRAM -> PlatformData(
            "Instagram",
            Icons.Default.Share, // Would use actual Instagram icon
            Color(0xFFE4405F)
        )
        SocialPlatform.WHATSAPP -> PlatformData(
            "WhatsApp",
            Icons.Default.Share, // Would use actual WhatsApp icon
            Color(0xFF25D366)
        )
        SocialPlatform.TWITTER -> PlatformData(
            "Twitter",
            Icons.Default.Share, // Would use actual Twitter icon
            Color(0xFF1DA1F2)
        )
        SocialPlatform.FACEBOOK -> PlatformData(
            "Facebook",
            Icons.Default.Share, // Would use actual Facebook icon
            Color(0xFF1877F2)
        )
        SocialPlatform.TELEGRAM -> PlatformData(
            "Telegram",
            Icons.Default.Share, // Would use actual Telegram icon
            Color(0xFF0088CC)
        )
        else -> PlatformData(
            platform.name,
            Icons.Default.Share,
            LiftrixColorsV2.primary
        )
    }
}

private fun getMessagePlaceholder(platform: SocialPlatform?): String {
    return when (platform) {
        SocialPlatform.INSTAGRAM -> "Add a caption for your story..."
        SocialPlatform.WHATSAPP -> "Add a message to share with your workout..."
        SocialPlatform.TWITTER -> "What's happening? (280 characters)"
        SocialPlatform.FACEBOOK -> "What's on your mind?"
        SocialPlatform.TELEGRAM -> "Share your workout with friends..."
        else -> "Add a custom message..."
    }
}

private fun getPlatformTip(platform: SocialPlatform): String {
    return when (platform) {
        SocialPlatform.INSTAGRAM -> "Perfect for sharing your fitness journey with followers"
        SocialPlatform.WHATSAPP -> "Great for sharing with workout buddies and groups"
        SocialPlatform.TWITTER -> "Keep it under 280 characters for maximum engagement"
        SocialPlatform.FACEBOOK -> "Share your achievements with friends and family"
        SocialPlatform.TELEGRAM -> "Ideal for fitness communities and groups"
        else -> "Choose the platform that works best for you"
    }
}
