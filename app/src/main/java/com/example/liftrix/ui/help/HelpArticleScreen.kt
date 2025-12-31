package com.example.liftrix.ui.help

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.ui.help.HelpViewModel
import com.example.liftrix.ui.help.HelpEvent
import com.example.liftrix.ui.common.state.UiState

/**
 * Screen for displaying individual help articles with content and feedback
 * 
 * Features:
 * - Article content display with rich formatting
 * - User feedback options (helpful/not helpful)
 * - Article sharing functionality
 * - View tracking for analytics
 * - Scroll position restoration
 * - Loading states and error handling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpArticleScreen(
    articleId: String,
    onNavigateBack: () -> Unit,
    viewModel: HelpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    LaunchedEffect(articleId) {
        viewModel.handleEvent(HelpEvent.LoadArticle(articleId))
        viewModel.handleEvent(HelpEvent.RecordArticleView(articleId))
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Help Article") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // Only show share button when article is loaded
                val currentState = uiState
                if (currentState is UiState.Success && currentState.data.selectedArticle != null) {
                    val article = currentState.data.selectedArticle
                    IconButton(
                        onClick = {
                            // Create share intent with article details
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Liftrix Help: ${article.title}")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    buildString {
                                        appendLine("Liftrix Help Article")
                                        appendLine()
                                        appendLine("${article.title}")
                                        appendLine()
                                        appendLine(article.content.take(200))
                                        if (article.content.length > 200) {
                                            appendLine("...")
                                        }
                                        appendLine()
                                        appendLine("Read more in the Liftrix app!")
                                    }
                                )
                            }
                            
                            // Launch share chooser
                            val chooserIntent = Intent.createChooser(shareIntent, "Share Help Article")
                            context.startActivity(chooserIntent)
                            
                            // Track sharing event
                            viewModel.handleEvent(HelpEvent.ShareArticle(articleId))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            }
        )
        
        when (val state = uiState) {
            UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Failed to load article",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = state.error.message ?: "An error occurred",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            viewModel.handleEvent(HelpEvent.LoadArticle(articleId))
                        }
                    ) {
                        Text("Retry")
                    }
                }
            }
            
            is UiState.Success -> {
                val article = state.data.selectedArticle
                
                if (article != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Article title
                        Text(
                            text = article.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Article metadata
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = article.category,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Text(
                                text = "Updated recently",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Article content
                        Text(
                            text = article.content,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Feedback section
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Was this article helpful?",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.handleEvent(
                                                HelpEvent.MarkArticleHelpful(articleId, true)
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.ThumbUp,
                                            contentDescription = "Helpful",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Yes")
                                    }
                                    
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.handleEvent(
                                                HelpEvent.MarkArticleHelpful(articleId, false)
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.ThumbDown,
                                            contentDescription = "Not helpful",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("No")
                                    }
                                }
                                
                                // Show feedback stats
                                if (article.helpfulCount > 0 || article.notHelpfulCount > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    val totalVotes = article.helpfulCount + article.notHelpfulCount
                                    val helpfulPercentage = if (totalVotes > 0) {
                                        (article.helpfulCount * 100) / totalVotes
                                    } else 0
                                    
                                    Text(
                                        text = "$helpfulPercentage% found this helpful ($totalVotes votes)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    // Article not found
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Article not found",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "The requested article could not be found or may have been removed.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                }
            }
            
            is UiState.Empty -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No articles available",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
            
            else -> {
                // Fallback for any unexpected states
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
