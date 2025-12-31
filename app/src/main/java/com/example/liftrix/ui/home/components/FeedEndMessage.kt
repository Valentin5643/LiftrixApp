package com.example.liftrix.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * End-of-feed message component displayed when user reaches the workout feed limit.
 * 
 * Shows a friendly message indicating the user has seen all available workout updates,
 * typically appearing after 30-40 workout items have been displayed. Features:
 * - Centered layout with checkmark icon
 * - Primary message with encouraging subtitle
 * - Material 3 design with proper accessibility support
 * 
 * Used by WorkoutFeedSection to provide clear feed completion feedback and encourage
 * users to check back later for more content.
 * 
 * @param modifier Modifier for styling the end message container
 */
@Composable
fun FeedEndMessage(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp)
            .semantics {
                contentDescription = "End of workout feed message indicating all updates have been shown"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = "You're all caught up",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(48.dp)
                .padding(bottom = 16.dp)
        )
        
        Text(
            text = "You've seen everything for now",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Check back later for more workout updates",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedEndMessagePreview() {
    LiftrixTheme {
        FeedEndMessage()
    }
}
