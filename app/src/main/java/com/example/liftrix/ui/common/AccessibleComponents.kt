package com.example.liftrix.ui.common

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.buttons.LiftrixButton
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.AccessibilityColors.luminance
import com.example.liftrix.ui.common.AccessibilityUtils.ensureMinimumTouchTarget

/**
 * Accessibility-enhanced component wrappers ensuring WCAG 2.1 AA compliance.
 * These components provide proper semantic structure, contrast validation, and TalkBack support.
 */

/**
 * Accessibility-enhanced LiftrixCard with proper semantic structure and contrast validation.
 */
@Composable
fun AccessibleLiftrixCard(
    onClick: (() -> Unit)? = null,
    heading: String? = null,
    contentDescription: String,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    isHeading: Boolean = false,
    headingLevel: Int = 2,
    content: @Composable ColumnScope.() -> Unit
) {
    val systemAccessibility = rememberSystemAccessibilityState()
    val cardContext = LocalContext.current
    
    // Validate contrast ratio for accessibility
    val containerColor = colors.containerColor
    val contentColor = colors.contentColor
    val contrastRatio = AccessibilityUtils.checkContrastRatio(contentColor, containerColor)
    
    // Use high contrast colors if needed
    val accessibleColors = if (systemAccessibility.isHighContrastEnabled && 
        contrastRatio < AccessibilityUtils.ContrastRatios.NORMAL_TEXT_AA) {
        CardDefaults.cardColors(
            containerColor = if (containerColor.luminance() > 0.5f) Color.White else Color.Black,
            contentColor = if (containerColor.luminance() > 0.5f) Color.Black else Color.White
        )
    } else {
        colors
    }
    
    LiftrixCard(
        onClick = onClick,
        modifier = modifier
            .ensureMinimumTouchTarget()
            .semantics {
                this.contentDescription = contentDescription
                onClick?.let { this.role = Role.Button }
                if (isHeading) {
                    this.heading()
                }
                this.liveRegion = LiveRegionMode.Polite
            },
        elevation = elevation
    ) {
        Column {
            content()
        }
    }
}

/**
 * Accessibility-enhanced button with proper contrast, touch targets, and semantic roles.
 */
@Composable
fun AccessibleLiftrixButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentDescription: String? = null,
    announcement: String? = null
) {
    val systemAccessibility = rememberSystemAccessibilityState()
    val buttonContext = LocalContext.current
    
    // Validate contrast ratio
    val containerColor = colors.containerColor
    val contentColor = colors.contentColor
    val contrastRatio = AccessibilityUtils.checkContrastRatio(contentColor, containerColor)
    
    // Use high contrast colors if needed
    val accessibleColors = if (systemAccessibility.isHighContrastEnabled && 
        contrastRatio < AccessibilityUtils.ContrastRatios.NORMAL_TEXT_AA) {
        ButtonDefaults.buttonColors(
            containerColor = LiftrixColorsV2.Teal,
            contentColor = Color.White
        )
    } else {
        colors
    }
    
    val accessibilityManager = remember { 
        buttonContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager 
    }
    
    Button(
        onClick = {
            onClick()
            // Announce action completion if specified
            announcement?.let { announcementText ->
                // Announce to screen reader using AccessibilityEvent
                if (accessibilityManager?.isEnabled == true) {
                    val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                        this.text.add(announcementText)
                        className = "com.example.liftrix"
                        packageName = buttonContext.packageName
                    }
                    accessibilityManager.sendAccessibilityEvent(event)
                }
            }
        },
        modifier = modifier
            .ensureMinimumTouchTarget()
            .semantics {
                this.contentDescription = contentDescription ?: text
                this.role = Role.Button
                if (!enabled) this.disabled()
                this.stateDescription = if (enabled) "Enabled" else "Disabled"
            },
        enabled = enabled,
        colors = accessibleColors
    ) {
        Text(
            text = text,
            fontSize = if (systemAccessibility.isLargeTextEnabled) 18.sp else 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Accessibility-enhanced text with proper contrast and scaling support.
 */
@Composable
fun AccessibleText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    isHeading: Boolean = false,
    headingLevel: Int = 2,
    contentDescription: String? = null
) {
    val systemAccessibility = rememberSystemAccessibilityState()
    val backgroundColor = MaterialTheme.colorScheme.surface
    
    // Validate and adjust color for contrast
    val accessibleColor = if (systemAccessibility.isHighContrastEnabled) {
        AccessibilityUtils.getHighContrastColor(color, backgroundColor, style.fontSize.value > 18f)
    } else {
        color
    }
    
    // Adjust font size for accessibility
    val accessibleStyle = if (systemAccessibility.isLargeTextEnabled) {
        style.copy(fontSize = style.fontSize * systemAccessibility.fontScale)
    } else {
        style
    }
    
    Text(
        text = text,
        modifier = modifier.semantics {
            this.contentDescription = contentDescription ?: text
            if (isHeading) {
                this.heading()
            }
        },
        color = accessibleColor,
        style = accessibleStyle
    )
}

/**
 * Accessibility-enhanced heading with proper semantic structure.
 */
@Composable
fun AccessibleHeading(
    text: String,
    level: Int = 1,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign = TextAlign.Start
) {
    val systemAccessibility = rememberSystemAccessibilityState()
    val backgroundColor = MaterialTheme.colorScheme.surface
    
    // Get appropriate text style based on heading level
    val baseStyle = when (level) {
        1 -> MaterialTheme.typography.displayLarge
        2 -> MaterialTheme.typography.displayMedium
        3 -> MaterialTheme.typography.displaySmall
        4 -> MaterialTheme.typography.headlineLarge
        5 -> MaterialTheme.typography.headlineMedium
        6 -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.headlineMedium
    }
    
    // Validate and adjust color for contrast
    val accessibleColor = if (systemAccessibility.isHighContrastEnabled) {
        AccessibilityUtils.getHighContrastColor(color, backgroundColor, true)
    } else {
        color
    }
    
    // Adjust font size for accessibility
    val accessibleStyle = if (systemAccessibility.isLargeTextEnabled) {
        baseStyle.copy(fontSize = baseStyle.fontSize * systemAccessibility.fontScale)
    } else {
        baseStyle
    }
    
    Text(
        text = text,
        modifier = modifier.semantics {
            this.contentDescription = text
            this.heading()
        },
        color = accessibleColor,
        style = accessibleStyle,
        textAlign = textAlign
    )
}

/**
 * Accessibility-enhanced radio button group with proper semantics.
 * 
 * @param options List of options
 * @param selectedOption Currently selected option
 * @param onOptionSelected Callback when option is selected
 * @param modifier Modifier for styling
 * @param groupLabel Label for the radio group
 */
@Composable
fun AccessibleRadioGroup(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    groupLabel: String
) {
    val systemAccessibility = rememberSystemAccessibilityState()
    
    Column(
        modifier = modifier
            .selectableGroup()
            .semantics {
                this.contentDescription = groupLabel
            }
    ) {
        AccessibleHeading(
            text = groupLabel,
            level = 3,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .ensureMinimumTouchTarget()
                    .selectable(
                        selected = (option == selectedOption),
                        onClick = { onOptionSelected(option) }
                    )
                    .semantics {
                        this.contentDescription = option
                        this.role = Role.RadioButton
                        this.stateDescription = 
                            if (option == selectedOption) "Selected" else "Not selected"
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (option == selectedOption),
                    onClick = null // Handled by row click
                )
                Spacer(modifier = Modifier.width(8.dp))
                AccessibleText(
                    text = option,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = if (systemAccessibility.isLargeTextEnabled) 18.sp else 16.sp
                    )
                )
            }
        }
    }
}

/**
 * Accessibility-enhanced switch with proper semantics and announcements.
 * 
 * @param checked Whether switch is checked
 * @param onCheckedChange Callback when switch state changes
 * @param label Label for the switch
 * @param modifier Modifier for styling
 * @param enabled Whether switch is enabled
 */
@Composable
fun AccessibleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val switchContext = LocalContext.current
    
    Row(
        modifier = modifier
            .ensureMinimumTouchTarget()
            .semantics(mergeDescendants = true) {
                this.contentDescription = label
                this.role = Role.Switch
                this.stateDescription = 
                    if (checked) "On" else "Off"
                if (!enabled) this.disabled()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val accessibilityManager = remember { 
            switchContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager 
        }
        
        Switch(
            checked = checked,
            onCheckedChange = { newValue ->
                onCheckedChange(newValue)
                // Announce state change to screen reader
                val announcement = "$label ${if (newValue) "enabled" else "disabled"}"
                if (accessibilityManager?.isEnabled == true) {
                    val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                        this.text.add(announcement)
                        className = "com.example.liftrix"
                        packageName = switchContext.packageName
                    }
                    accessibilityManager.sendAccessibilityEvent(event)
                }
            },
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(8.dp))
        AccessibleText(
            text = label,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Accessibility-enhanced progress indicator with proper announcements.
 * 
 * @param progress Current progress (0.0 to 1.0)
 * @param modifier Modifier for styling
 * @param label Label for the progress indicator
 * @param showPercentage Whether to show percentage text
 */
@Composable
fun AccessibleProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String,
    showPercentage: Boolean = true
) {
    val progressContext = LocalContext.current
    val percentage = (progress * 100).toInt()
    
    Column(
        modifier = modifier.semantics {
            this.contentDescription = "$label: $percentage percent complete"
            this.liveRegion = LiveRegionMode.Polite
        }
    ) {
        if (showPercentage) {
            AccessibleText(
                text = "$label: $percentage%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
    }
}




/**
 * Accessibility-enhanced icon with proper content description and touch targets.
 * 
 * @param imageVector Icon to display
 * @param contentDescription Description for screen readers
 * @param modifier Modifier for styling
 * @param tint Color tint for the icon
 * @param iconSize Size of the icon
 */
@Composable
fun AccessibleIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 24.dp
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier
            .size(iconSize)
            .semantics {
                contentDescription?.let { this.contentDescription = it }
            },
        tint = tint
    )
}

/**
 * Accessibility-enhanced icon button with proper content description and touch targets.
 * 
 * @param onClick Click handler
 * @param imageVector Icon to display
 * @param contentDescription Description for screen readers
 * @param modifier Modifier for styling
 * @param enabled Whether the button is enabled
 * @param iconSize Size of the icon
 */
@Composable
fun AccessibleIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .ensureMinimumTouchTarget()
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
                if (!enabled) this.disabled()
            },
        enabled = enabled
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null, // Handled by button semantics
            modifier = Modifier.size(iconSize)
        )
    }
} 