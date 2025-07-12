package com.example.liftrix.ui.components.layouts

import androidx.compose.foundation.layout.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Test suite for grid system layout components
 * Tests grid calculations, responsive behavior, and spacing consistency
 */
class GridSystemTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    /**
     * Test SpacingValues object provides correct 8pt grid values
     */
    @Test
    fun spacingValues_followsEightPointGrid() {
        // Verify 8pt grid system compliance
        assertEquals(4.dp, SpacingValues.grid4pt)
        assertEquals(8.dp, SpacingValues.grid8pt)
        assertEquals(12.dp, SpacingValues.grid12pt)
        assertEquals(16.dp, SpacingValues.grid16pt)
        assertEquals(24.dp, SpacingValues.grid24pt)
        assertEquals(32.dp, SpacingValues.grid32pt)
        assertEquals(40.dp, SpacingValues.grid40pt)
        
        // Verify aliases match grid values
        assertEquals(SpacingValues.grid4pt, SpacingValues.xxs)
        assertEquals(SpacingValues.grid8pt, SpacingValues.xs)
        assertEquals(SpacingValues.grid12pt, SpacingValues.s)
        assertEquals(SpacingValues.grid16pt, SpacingValues.m)
        assertEquals(SpacingValues.grid24pt, SpacingValues.l)
        assertEquals(SpacingValues.grid32pt, SpacingValues.xl)
        assertEquals(SpacingValues.grid40pt, SpacingValues.xxl)
    }
    
    /**
     * Test SpacingValues content padding defaults
     */
    @Test
    fun spacingValues_providesContentPaddingDefaults() {
        assertEquals(SpacingValues.grid16pt, SpacingValues.contentPadding)
        assertEquals(SpacingValues.grid12pt, SpacingValues.compactPadding)
        assertEquals(SpacingValues.grid24pt, SpacingValues.spaciousPadding)
    }
    
    /**
     * Test responsive breakpoints provide correct values
     */
    @Test
    fun responsiveBreakpoints_provideCorrectValues() {
        // Verify window width breakpoints
        assertEquals(600.dp, ResponsiveBreakpoints.compactMaxWidth)
        assertEquals(840.dp, ResponsiveBreakpoints.mediumMaxWidth)
        assertEquals(841.dp, ResponsiveBreakpoints.expandedMinWidth)
        
        // Verify column configurations
        assertEquals(1, ResponsiveBreakpoints.compactColumns)
        assertEquals(2, ResponsiveBreakpoints.mediumColumns)
        assertEquals(3, ResponsiveBreakpoints.expandedColumns)
        
        // Verify adaptive spacing values
        assertEquals(SpacingValues.grid8pt, ResponsiveBreakpoints.compactSpacing)
        assertEquals(SpacingValues.grid12pt, ResponsiveBreakpoints.mediumSpacing)
        assertEquals(SpacingValues.grid16pt, ResponsiveBreakpoints.expandedSpacing)
        
        // Verify adaptive padding values
        assertEquals(SpacingValues.grid12pt, ResponsiveBreakpoints.compactPadding)
        assertEquals(SpacingValues.grid16pt, ResponsiveBreakpoints.mediumPadding)
        assertEquals(SpacingValues.grid24pt, ResponsiveBreakpoints.expandedPadding)
    }
    
    /**
     * Test GridContainer renders with correct semantics
     */
    @Test
    fun gridContainer_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                GridContainer(
                    columns = 2,
                    spacing = SpacingValues.grid8pt,
                    modifier = Modifier.testTag("grid-container")
                ) {
                    items(4) { index ->
                        androidx.compose.material3.Text(
                            text = "Item ${index + 1}",
                            modifier = Modifier.testTag("grid-item-$index")
                        )
                    }
                }
            }
        }
        
        // Verify grid container exists
        composeTestRule
            .onNodeWithTag("grid-container")
            .assertExists()
        
        // Verify grid items exist
        for (i in 0 until 4) {
            composeTestRule
                .onNodeWithTag("grid-item-$i")
                .assertExists()
                .assertIsDisplayed()
        }
    }
} 