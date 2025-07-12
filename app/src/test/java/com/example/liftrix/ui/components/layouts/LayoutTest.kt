package com.example.liftrix.ui.components.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Rule
import org.junit.Test

class LayoutTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun gridSystem_providesCorrectSpacingValues() {
        // Test that spacing values follow 8pt grid system
        assert(GridSystem.spacing1.value == 4f) { "spacing1 should be 4dp" }
        assert(GridSystem.spacing2.value == 8f) { "spacing2 should be 8dp" }
        assert(GridSystem.spacing3.value == 16f) { "spacing3 should be 16dp" }
        assert(GridSystem.spacing4.value == 24f) { "spacing4 should be 24dp" }
        assert(GridSystem.spacing5.value == 32f) { "spacing5 should be 32dp" }
        assert(GridSystem.spacing6.value == 48f) { "spacing6 should be 48dp" }
    }
    
    @Test
    fun gridSystem_spacingFunction_calculatesCorrectValues() {
        // Test spacing function with multipliers
        assert(GridSystem.spacing(0.5f).value == 4f) { "spacing(0.5) should be 4dp" }
        assert(GridSystem.spacing(1f).value == 8f) { "spacing(1) should be 8dp" }
        assert(GridSystem.spacing(2f).value == 16f) { "spacing(2) should be 16dp" }
        assert(GridSystem.spacing(3f).value == 24f) { "spacing(3) should be 24dp" }
    }
    
    @Test
    fun gridSystem_isGridAligned_validatesCorrectly() {
        // Test grid alignment validation
        assert(GridSystem.isGridAligned(4.dp)) { "4dp should be grid aligned" }
        assert(GridSystem.isGridAligned(8.dp)) { "8dp should be grid aligned" }
        assert(GridSystem.isGridAligned(16.dp)) { "16dp should be grid aligned" }
        assert(!GridSystem.isGridAligned(5.dp)) { "5dp should not be grid aligned" }
        assert(!GridSystem.isGridAligned(10.dp)) { "10dp should not be grid aligned" }
    }
    
    @Test
    fun breakpoints_classifyScreenSizesCorrectly() {
        // Test breakpoint classification
        assert(Breakpoints.isCompact(400.dp)) { "400dp should be compact" }
        assert(Breakpoints.isMedium(700.dp)) { "700dp should be medium" }
        assert(Breakpoints.isExpanded(1000.dp)) { "1000dp should be expanded" }
        
        assert(!Breakpoints.isCompact(700.dp)) { "700dp should not be compact" }
        assert(!Breakpoints.isMedium(400.dp)) { "400dp should not be medium" }
        assert(!Breakpoints.isExpanded(400.dp)) { "400dp should not be expanded" }
    }
    
    @Test
    fun responsiveContainer_displaysContent() {
        composeTestRule.setContent {
            LiftrixTheme {
                ResponsiveContainer(
                    modifier = Modifier.testTag("responsive_container")
                ) { screenSize ->
                    Text(
                        text = "Test Content - ${screenSize.name}",
                        modifier = Modifier.testTag("test_content")
                    )
                }
            }
        }
        
        composeTestRule
            .onNodeWithTag("responsive_container")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithTag("test_content")
            .assertIsDisplayed()
    }
    
    @Test
    fun asymmetricalLayout_displaysAllContent() {
        composeTestRule.setContent {
            LiftrixTheme {
                AsymmetricalLayout(
                    modifier = Modifier.testTag("asymmetrical_layout"),
                    primaryContent = {
                        Text(
                            text = "Primary Content",
                            modifier = Modifier.testTag("primary_content")
                        )
                    },
                    secondaryContent = {
                        Text(
                            text = "Secondary Content",
                            modifier = Modifier.testTag("secondary_content")
                        )
                    },
                    tertiaryContent = {
                        Text(
                            text = "Tertiary Content",
                            modifier = Modifier.testTag("tertiary_content")
                        )
                    }
                )
            }
        }
        
        composeTestRule
            .onNodeWithTag("asymmetrical_layout")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Primary Content")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Secondary Content")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Tertiary Content")
            .assertIsDisplayed()
    }
    
    @Test
    fun adaptiveGrid_displaysCorrectContent() {
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveGrid(
                    modifier = Modifier.testTag("adaptive_grid"),
                    compactColumns = 1,
                    mediumColumns = 2,
                    expandedColumns = 3
                ) { columns, screenSize ->
                    Text(
                        text = "Grid - $columns columns (${screenSize.name})",
                        modifier = Modifier.testTag("grid_info")
                    )
                }
            }
        }
        
        composeTestRule
            .onNodeWithTag("adaptive_grid")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithTag("grid_info")
            .assertIsDisplayed()
    }
    
    @Test
    fun screenSize_enum_hasCorrectValues() {
        // Test screen size enum values
        val compact = ScreenSize.Compact
        val medium = ScreenSize.Medium
        val expanded = ScreenSize.Expanded
        
        assert(compact.name == "Compact") { "Compact screen size name should be 'Compact'" }
        assert(medium.name == "Medium") { "Medium screen size name should be 'Medium'" }
        assert(expanded.name == "Expanded") { "Expanded screen size name should be 'Expanded'" }
    }
    
    @Test
    fun layoutConstants_provideCorrectValues() {
        // Test layout constants
        assert(LayoutConstants.appBarHeight.value == 64f) { "App bar height should be 64dp" }
        assert(LayoutConstants.bottomNavigationHeight.value == 80f) { "Bottom navigation height should be 80dp" }
        assert(LayoutConstants.cardMinHeight.value == 120f) { "Card min height should be 120dp" }
        assert(LayoutConstants.fabSize.value == 56f) { "FAB size should be 56dp" }
        assert(LayoutConstants.touchTargetMinimum.value == 48f) { "Touch target minimum should be 48dp" }
    }
    
    @Test
    fun layoutConstants_iconSizes_followMaterialDesign() {
        // Test icon sizes follow Material Design guidelines
        assert(LayoutConstants.iconSmall.value == 16f) { "Small icon should be 16dp" }
        assert(LayoutConstants.iconMedium.value == 24f) { "Medium icon should be 24dp" }
        assert(LayoutConstants.iconLarge.value == 32f) { "Large icon should be 32dp" }
        assert(LayoutConstants.iconXLarge.value == 48f) { "XLarge icon should be 48dp" }
    }
    
    @Test
    fun layoutConstants_cornerRadius_providesConsistentValues() {
        // Test corner radius values
        assert(LayoutConstants.cornerRadiusSmall.value == 4f) { "Small corner radius should be 4dp" }
        assert(LayoutConstants.cornerRadiusMedium.value == 8f) { "Medium corner radius should be 8dp" }
        assert(LayoutConstants.cornerRadiusLarge.value == 16f) { "Large corner radius should be 16dp" }
        assert(LayoutConstants.cornerRadiusXLarge.value == 24f) { "XLarge corner radius should be 24dp" }
    }
    
    @Test
    fun layoutConstants_elevation_providesConsistentValues() {
        // Test elevation values
        assert(LayoutConstants.elevationNone.value == 0f) { "No elevation should be 0dp" }
        assert(LayoutConstants.elevationSmall.value == 2f) { "Small elevation should be 2dp" }
        assert(LayoutConstants.elevationMedium.value == 4f) { "Medium elevation should be 4dp" }
        assert(LayoutConstants.elevationLarge.value == 8f) { "Large elevation should be 8dp" }
        assert(LayoutConstants.elevationXLarge.value == 16f) { "XLarge elevation should be 16dp" }
    }
    
    @Test
    fun semanticSpacing_providesCorrectValues() {
        // Test semantic spacing values
        assert(GridSystem.paddingSmall == GridSystem.spacing2) { "Small padding should equal spacing2" }
        assert(GridSystem.paddingMedium == GridSystem.spacing3) { "Medium padding should equal spacing3" }
        assert(GridSystem.paddingLarge == GridSystem.spacing4) { "Large padding should equal spacing4" }
        
        assert(GridSystem.marginSmall == GridSystem.spacing2) { "Small margin should equal spacing2" }
        assert(GridSystem.marginMedium == GridSystem.spacing3) { "Medium margin should equal spacing3" }
        assert(GridSystem.marginLarge == GridSystem.spacing4) { "Large margin should equal spacing4" }
        
        assert(GridSystem.gapSmall == GridSystem.spacing2) { "Small gap should equal spacing2" }
        assert(GridSystem.gapMedium == GridSystem.spacing3) { "Medium gap should equal spacing3" }
        assert(GridSystem.gapLarge == GridSystem.spacing4) { "Large gap should equal spacing4" }
    }
    
    @Test
    fun componentSpecificSpacing_providesCorrectValues() {
        // Test component-specific spacing
        assert(GridSystem.cardPadding == GridSystem.spacing3) { "Card padding should equal spacing3" }
        assert(GridSystem.cardMargin == GridSystem.spacing3) { "Card margin should equal spacing3" }
        assert(GridSystem.cardGap == GridSystem.spacing3) { "Card gap should equal spacing3" }
        
        assert(GridSystem.buttonPadding == GridSystem.spacing3) { "Button padding should equal spacing3" }
        assert(GridSystem.buttonMargin == GridSystem.spacing2) { "Button margin should equal spacing2" }
        assert(GridSystem.buttonGap == GridSystem.spacing2) { "Button gap should equal spacing2" }
        
        assert(GridSystem.screenPadding == GridSystem.spacing3) { "Screen padding should equal spacing3" }
        assert(GridSystem.screenMargin == GridSystem.spacing3) { "Screen margin should equal spacing3" }
    }
} 