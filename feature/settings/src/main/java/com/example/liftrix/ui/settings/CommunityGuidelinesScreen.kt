package com.example.liftrix.ui.settings

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Community Guidelines screen displaying community standards and acceptable behavior
 *
 * Features:
 * - WebView display of community guidelines HTML content
 * - Local asset loading for offline access
 * - No JavaScript execution for security
 * - Simple back navigation
 * - Material 3 design system integration
 *
 * Security:
 * - JavaScript disabled in WebView
 * - Only loads from local assets (file:///android_asset/)
 * - No external URL loading
 *
 * @param onNavigateBack Callback to navigate back to previous screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityGuidelinesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Community Guidelines",
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
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            factory = { context ->
                WebView(context).apply {
                    // Security: Disable JavaScript as HTML content is static
                    settings.javaScriptEnabled = false

                    // Load community guidelines from assets
                    loadUrl("file:///android_asset/community-guidelines.html")
                }
            }
        )
    }
}

/**
 * Preview for CommunityGuidelinesScreen
 */
@Preview(showBackground = true)
@Composable
private fun CommunityGuidelinesScreenPreview() {
    LiftrixTheme {
        CommunityGuidelinesScreen(
            onNavigateBack = {}
        )
    }
}
