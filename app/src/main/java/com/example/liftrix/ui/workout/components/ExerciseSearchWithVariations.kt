package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.usecase.exercise.ExerciseGroup
import com.example.liftrix.domain.usecase.exercise.SearchableExercise

/**
 * Component that displays search results grouped by exercise variations
 * 
 * @param searchResults List of exercise groups with variations
 * @param onVariationSelected Callback when an exercise variation is selected
 * @param modifier Optional modifier for styling
 */
@Composable
fun ExerciseSearchWithVariations(
    searchResults: List<ExerciseGroup>,
    onVariationSelected: (SearchableExercise) -> Unit,
    modifier: Modifier = Modifier
) {
    if (searchResults.isEmpty()) {
        EmptyVariationsContent(modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .semantics { 
                    contentDescription = "Exercise search results with variations" 
                },
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(searchResults) { exerciseGroup: ExerciseGroup ->
                ExerciseVariationList(
                    exerciseGroup = exerciseGroup,
                    onExerciseSelected = onVariationSelected
                )
            }
        }
    }
}

/**
 * Content displayed when no exercise variations are found
 */
@Composable
private fun EmptyVariationsContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No exercise variations found",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Try a different search term or create a custom exercise",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

 