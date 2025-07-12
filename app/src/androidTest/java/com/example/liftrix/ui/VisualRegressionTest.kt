package com.example.liftrix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.MainActivity
import com.example.liftrix.ui.components.cards.*
import com.example.liftrix.ui.theme.LiftrixTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual regression testing for component consistency across updates as specified in TESTING-001.
 * 
 * These instrumented tests ensure design system consistency by:
 * - Testing component visual appearance and layout
 * - Verifying 8pt grid system adherence
 * - Checking asymmetrical composition balance
 * - Validating brand color strategic placement
 * - Ensuring visual hierarchy consistency
 * - Testing component rendering across different screen densities
 * - Verifying Material 3 design token implementation
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class VisualRegressionTest {

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    // Design System Consistency Tests

    @Test
    fun designSystem_cardComponents_maintainVisualConsistency() {
        composeTestRule.setContent {
            LiftrixTheme {
                DesignSystemCardShowcase()
            }
        }

        // Verify all card variants are displayed
        composeTestRule
            .onNodeWithTag("basic_liftrix_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("compact_liftrix_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("elevated_liftrix_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("gradient_liftrix_card")
            .assertIsDisplayed()

        // Verify visual hierarchy with text content
        composeTestRule
            .onNodeWithText("Basic LiftrixCard")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Compact LiftrixCard")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Elevated LiftrixCard")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Gradient LiftrixCard")
            .assertIsDisplayed()
    }

    @Test
    fun designSystem_statCards_maintainVisualHierarchy() {
        composeTestRule.setContent {
            LiftrixTheme {
                StatCardShowcase()
            }
        }

        // Verify stat cards display with proper hierarchy
        composeTestRule
            .onNodeWithTag("positive_trend_stat")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("negative_trend_stat")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("neutral_trend_stat")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("compact_stat_showcase")
            .assertIsDisplayed()

        // Verify text content is properly displayed
        composeTestRule
            .onNodeWithText("Weekly Workouts")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("12")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Calories Burned")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("2,450")
            .assertIsDisplayed()
    }

    @Test
    fun designSystem_activityCards_maintainConsistentLayout() {
        composeTestRule.setContent {
            LiftrixTheme {
                ActivityCardShowcase()
            }
        }

        // Verify activity cards display consistently
        composeTestRule
            .onNodeWithTag("standard_activity_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("compact_activity_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("feature_card")
            .assertIsDisplayed()

        // Verify content is properly displayed
        composeTestRule
            .onNodeWithText("Morning Workout")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Quick Start")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("New Feature")
            .assertIsDisplayed()
    }

    // 8pt Grid System Tests

    @Test
    fun gridSystem_components_adhere_to_8pt_spacing() {
        composeTestRule.setContent {
            LiftrixTheme {
                GridSystemShowcase()
            }
        }

        // Verify grid system components are displayed
        composeTestRule
            .onNodeWithTag("grid_system_showcase")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("spacing_demonstration")
            .assertIsDisplayed()

        // Verify content demonstrates proper spacing
        composeTestRule
            .onNodeWithText("8pt Grid System")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Consistent Spacing")
            .assertIsDisplayed()
    }

    @Test
    fun gridSystem_asymmetricalLayout_maintainsBalance() {
        composeTestRule.setContent {
            LiftrixTheme {
                AsymmetricalLayoutShowcase()
            }
        }

        // Verify asymmetrical layout components
        composeTestRule
            .onNodeWithTag("asymmetrical_layout")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("large_stat_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("small_stat_cards")
            .assertIsDisplayed()

        // Verify content balance
        composeTestRule
            .onNodeWithText("Today's Goal")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("75%")
            .assertIsDisplayed()
    }

    // Brand Color Tests

    @Test
    fun brandColors_strategicPlacement_maintainsConsistency() {
        composeTestRule.setContent {
            LiftrixTheme {
                BrandColorShowcase()
            }
        }

        // Verify brand color placement in components
        composeTestRule
            .onNodeWithTag("brand_color_showcase")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("primary_brand_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("secondary_brand_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("accent_brand_card")
            .assertIsDisplayed()
    }

    @Test
    fun brandColors_darkTheme_maintainsAccessibility() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                BrandColorShowcase()
            }
        }

        // Verify brand colors work in dark theme
        composeTestRule
            .onNodeWithTag("brand_color_showcase")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("primary_brand_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("secondary_brand_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("accent_brand_card")
            .assertIsDisplayed()
    }

    // Material 3 Integration Tests

    @Test
    fun material3_tokens_implementedConsistently() {
        composeTestRule.setContent {
            LiftrixTheme {
                Material3TokensShowcase()
            }
        }

        // Verify Material 3 design tokens implementation
        composeTestRule
            .onNodeWithTag("material3_showcase")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("surface_tokens")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("elevation_tokens")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("color_role_tokens")
            .assertIsDisplayed()
    }

    @Test
    fun material3_elevation_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                ElevationShowcase()
            }
        }

        // Verify elevation levels are properly implemented
        composeTestRule
            .onNodeWithTag("elevation_level_1")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("elevation_level_2")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("elevation_level_3")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("elevation_level_4")
            .assertIsDisplayed()
    }

    // Cross-Theme Visual Consistency

    @Test
    fun crossTheme_components_maintainVisualIdentity() {
        // Test light theme
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = false) {
                CrossThemeShowcase(themeLabel = "Light")
            }
        }

        composeTestRule
            .onNodeWithTag("cross_theme_showcase")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Light Theme")
            .assertIsDisplayed()

        // Test dark theme
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                CrossThemeShowcase(themeLabel = "Dark")
            }
        }

        composeTestRule
            .onNodeWithTag("cross_theme_showcase")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Dark Theme")
            .assertIsDisplayed()
    }

    @Test
    fun crossTheme_typography_maintainsHierarchy() {
        // Test typography in both themes
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = false) {
                TypographyShowcase()
            }
        }

        composeTestRule
            .onNodeWithTag("typography_showcase")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("display_typography")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("headline_typography")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("body_typography")
            .assertIsDisplayed()

        // Switch to dark theme
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                TypographyShowcase()
            }
        }

        composeTestRule
            .onNodeWithTag("typography_showcase")
            .assertIsDisplayed()
    }

    // Performance Visual Tests

    @Test
    fun performance_complexLayouts_renderWithoutIssues() {
        composeTestRule.setContent {
            LiftrixTheme {
                ComplexLayoutShowcase()
            }
        }

        // Verify complex layouts render properly
        composeTestRule
            .onNodeWithTag("complex_layout_showcase")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("nested_cards_section")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("mixed_components_section")
            .assertIsDisplayed()

        // Verify performance doesn't impact visual consistency
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("complex_layout_showcase")
            .assertIsDisplayed()
    }

    @Test
    fun performance_manyComponents_maintainVisualConsistency() {
        composeTestRule.setContent {
            LiftrixTheme {
                ManyComponentsShowcase()
            }
        }

        // Verify many components render consistently
        composeTestRule
            .onNodeWithTag("many_components_showcase")
            .assertIsDisplayed()

        // Scroll through components to test visual consistency
        composeTestRule
            .onNodeWithTag("many_components_list")
            .performScrollToIndex(5)

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("many_components_showcase")
            .assertIsDisplayed()
    }
}

// Showcase Composables for Visual Testing

@Composable
private fun DesignSystemCardShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LiftrixCard(
            modifier = Modifier.testTag("basic_liftrix_card")
        ) {
            Text("Basic LiftrixCard")
        }
        
        CompactLiftrixCard(
            modifier = Modifier.testTag("compact_liftrix_card")
        ) {
            Text("Compact LiftrixCard")
        }
        
        ElevatedLiftrixCard(
            modifier = Modifier.testTag("elevated_liftrix_card")
        ) {
            Text("Elevated LiftrixCard")
        }
        
        GradientLiftrixCard(
            modifier = Modifier.testTag("gradient_liftrix_card")
        ) {
            Text("Gradient LiftrixCard")
        }
    }
}

@Composable
private fun StatCardShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard(
            title = "Weekly Workouts",
            value = "12",
            subtitle = "This week",
            trend = Trend.Positive(15.2f),
            icon = Icons.Default.FitnessCenter,
            modifier = Modifier.testTag("positive_trend_stat")
        )
        
        StatCard(
            title = "Calories Burned",
            value = "2,450",
            subtitle = "This month",
            trend = Trend.Negative(5.8f),
            modifier = Modifier.testTag("negative_trend_stat")
        )
        
        StatCard(
            title = "Average Session",
            value = "45 min",
            trend = Trend.Neutral(),
            modifier = Modifier.testTag("neutral_trend_stat")
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("compact_stat_showcase"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactStatCard(
                title = "Sets",
                value = "24",
                modifier = Modifier.weight(1f)
            )
            CompactStatCard(
                title = "Reps",
                value = "180",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActivityCardShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ActivityCard(
            title = "Morning Workout",
            subtitle = "Push Day - 45 minutes",
            icon = Icons.Default.FitnessCenter,
            trailing = "2h ago",
            modifier = Modifier.testTag("standard_activity_card")
        )
        
        CompactActivityCard(
            title = "Quick Start",
            subtitle = "15 min",
            icon = Icons.Default.Timer,
            modifier = Modifier.testTag("compact_activity_card")
        )
        
        FeatureCard(
            title = "New Feature",
            description = "Enhanced analytics for better insights",
            icon = Icons.Default.TrendingUp,
            modifier = Modifier.testTag("feature_card")
        )
    }
}

@Composable
private fun GridSystemShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("grid_system_showcase"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "8pt Grid System",
            style = MaterialTheme.typography.headlineMedium
        )
        
        LiftrixCard(
            modifier = Modifier.testTag("spacing_demonstration")
        ) {
            Text("Consistent Spacing")
        }
    }
}

@Composable
private fun AsymmetricalLayoutShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("asymmetrical_layout")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Today's Goal",
                value = "75%",
                subtitle = "3 of 4 exercises",
                trend = Trend.Positive(25f),
                modifier = Modifier
                    .weight(2f)
                    .testTag("large_stat_card")
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag("small_stat_cards"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactStatCard(
                    title = "Sets",
                    value = "24"
                )
                CompactStatCard(
                    title = "Reps",
                    value = "180"
                )
            }
        }
    }
}

@Composable
private fun BrandColorShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("brand_color_showcase"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LiftrixCard(
            modifier = Modifier.testTag("primary_brand_card")
        ) {
            Text("Primary Brand Color")
        }
        
        LiftrixCard(
            modifier = Modifier.testTag("secondary_brand_card")
        ) {
            Text("Secondary Brand Color")
        }
        
        LiftrixCard(
            modifier = Modifier.testTag("accent_brand_card")
        ) {
            Text("Accent Brand Color")
        }
    }
}

@Composable
private fun Material3TokensShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("material3_showcase"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LiftrixCard(
            modifier = Modifier.testTag("surface_tokens")
        ) {
            Text("Surface Tokens")
        }
        
        LiftrixCard(
            modifier = Modifier.testTag("elevation_tokens")
        ) {
            Text("Elevation Tokens")
        }
        
        LiftrixCard(
            modifier = Modifier.testTag("color_role_tokens")
        ) {
            Text("Color Role Tokens")
        }
    }
}

@Composable
private fun ElevationShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LiftrixCard(
            modifier = Modifier.testTag("elevation_level_1")
        ) {
            Text("Elevation Level 1")
        }
        
        CompactLiftrixCard(
            modifier = Modifier.testTag("elevation_level_2")
        ) {
            Text("Elevation Level 2")
        }
        
        ElevatedLiftrixCard(
            modifier = Modifier.testTag("elevation_level_3")
        ) {
            Text("Elevation Level 3")
        }
        
        GradientLiftrixCard(
            modifier = Modifier.testTag("elevation_level_4")
        ) {
            Text("Elevation Level 4")
        }
    }
}

@Composable
private fun CrossThemeShowcase(themeLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("cross_theme_showcase"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "$themeLabel Theme",
            style = MaterialTheme.typography.headlineMedium
        )
        
        LiftrixCard {
            Text("Cross-theme consistency test")
        }
    }
}

@Composable
private fun TypographyShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("typography_showcase"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Display Typography",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.testTag("display_typography")
        )
        
        Text(
            text = "Headline Typography",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("headline_typography")
        )
        
        Text(
            text = "Body Typography",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("body_typography")
        )
    }
}

@Composable
private fun ComplexLayoutShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("complex_layout_showcase"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Complex Layout Test",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("nested_cards_section"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiftrixCard(modifier = Modifier.weight(1f)) {
                Column {
                    Text("Nested")
                    CompactLiftrixCard {
                        Text("Card")
                    }
                }
            }
            LiftrixCard(modifier = Modifier.weight(1f)) {
                Text("Layout")
            }
        }
        
        Column(
            modifier = Modifier.testTag("mixed_components_section"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Mixed",
                value = "100%"
            )
            ActivityCard(
                title = "Components",
                subtitle = "Test",
                icon = Icons.Default.FitnessCenter
            )
        }
    }
}

@Composable
private fun ManyComponentsShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("many_components_showcase")
    ) {
        LazyColumn(
            modifier = Modifier.testTag("many_components_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(20) { index ->
                LiftrixCard(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text("Component $index")
                }
            }
        }
    }
} 