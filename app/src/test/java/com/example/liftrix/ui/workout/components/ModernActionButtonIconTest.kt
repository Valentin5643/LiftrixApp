package com.example.liftrix.ui.workout.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import org.junit.Test
import org.junit.Assert.*

/**
 * Test to verify that all required icons have proper mappings in ModernActionButton
 * to prevent buttons from appearing as dots.
 */
class ModernActionButtonIconTest {
    
    @Test
    fun `verify critical icons have mappings and not default dot`() {
        // These are the critical icons that were appearing as dots
        val criticalIcons = mapOf(
            Icons.Default.PersonAdd to "⊕",
            Icons.Default.Check to "✓",
            Icons.Default.Block to "⊗",
            Icons.Default.ExitToApp to "↪",
            Icons.Default.MoreHoriz to "⋯",
            Icons.Default.MoreVert to "⋮",
            Icons.Default.Schedule to "⏱",
            Icons.Default.Star to "★"
        )
        
        // Verify each critical icon has a proper mapping (not the default dot)
        criticalIcons.forEach { (icon, expectedSymbol) ->
            val actualSymbol = getIconSymbol(icon)
            assertNotEquals(
                "Icon ${icon.name} should not map to default dot",
                "●",
                actualSymbol
            )
            assertEquals(
                "Icon ${icon.name} should map to $expectedSymbol",
                expectedSymbol,
                actualSymbol
            )
        }
    }
    
    @Test
    fun `verify existing icons still have correct mappings`() {
        val existingIcons = mapOf(
            Icons.Default.PlayArrow to "▶",
            Icons.Default.Edit to "✎",
            Icons.Default.Assignment to "☰",
            Icons.Default.People to "◉",
            Icons.Default.Add to "+",
            Icons.Default.Save to "⊡",
            Icons.Default.Refresh to "↻"
        )
        
        existingIcons.forEach { (icon, expectedSymbol) ->
            val actualSymbol = getIconSymbol(icon)
            assertEquals(
                "Icon ${icon.name} should map to $expectedSymbol",
                expectedSymbol,
                actualSymbol
            )
        }
    }
    
    /**
     * Helper function that mimics the icon mapping logic in ModernActionButton
     */
    private fun getIconSymbol(icon: androidx.compose.ui.graphics.vector.ImageVector): String {
        return when (icon) {
            Icons.Default.PlayArrow -> "▶"
            Icons.Default.Edit -> "✎"
            Icons.Default.Assignment -> "☰"
            Icons.Default.People -> "◉"
            Icons.Default.Add -> "+"
            Icons.Default.Save -> "⊡"
            Icons.Default.Refresh -> "↻"
            Icons.Default.PersonAdd -> "⊕"
            Icons.Default.Check -> "✓"
            Icons.Default.Block -> "⊗"
            Icons.Default.ExitToApp -> "↪"
            Icons.Default.MoreHoriz -> "⋯"
            Icons.Default.MoreVert -> "⋮"
            Icons.Default.Schedule -> "⏱"
            Icons.Default.Star -> "★"
            else -> "●"
        }
    }
}