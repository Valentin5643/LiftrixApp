package com.example.liftrix.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.chat.Language

/**
 * Chat input bar component for composing and sending messages.
 * 
 * Features:
 * - Multi-line text input with automatic expansion
 * - Send button with proper state management
 * - Keyboard action handling for send on enter
 * - Language-aware placeholder text
 * - Accessibility support with proper content descriptions
 * - Voice input support (future enhancement)
 * 
 * @param text Current input text value
 * @param onTextChange Callback for text changes
 * @param onSend Callback for sending message
 * @param enabled Whether input is enabled (respects usage limits)
 * @param currentLanguage Current interface language for localization
 * @param modifier Modifier for the component
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    enabled: Boolean,
    currentLanguage: Language,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    
    Surface(
        color = LiftrixColorsV2.surface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium),
            verticalAlignment = Alignment.Bottom
        ) {
            ChatTextField(
                text = text,
                onTextChange = onTextChange,
                onSend = { if (text.isNotBlank()) onSend(text) },
                enabled = enabled,
                currentLanguage = currentLanguage,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(LiftrixSpacing.small))
            
            SendButton(
                onClick = { 
                    if (text.isNotBlank()) {
                        onSend(text)
                    }
                },
                enabled = enabled && text.isNotBlank(),
                currentLanguage = currentLanguage
            )
        }
    }
}

@Composable
private fun ChatTextField(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    currentLanguage: Language,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { 
            Text(
                text = getPlaceholderText(currentLanguage),
                color = LiftrixColorsV2.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        enabled = enabled,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Send,
            capitalization = KeyboardCapitalization.Sentences
        ),
        keyboardActions = KeyboardActions(
            onSend = { onSend() }
        ),
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LiftrixColorsV2.primary,
            unfocusedBorderColor = LiftrixColorsV2.outline,
            disabledBorderColor = LiftrixColorsV2.outline.copy(alpha = 0.5f)
        ),
        modifier = modifier.focusRequester(focusRequester)
    )
}

@Composable
private fun SendButton(
    onClick: () -> Unit,
    enabled: Boolean,
    currentLanguage: Language
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = LiftrixColorsV2.primary,
            contentColor = LiftrixColorsV2.onPrimary,
            disabledContainerColor = LiftrixColorsV2.outline,
            disabledContentColor = LiftrixColorsV2.onSurfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Icon(
            Icons.Default.Send,
            contentDescription = getSendButtonContentDescription(currentLanguage)
        )
    }
}

/**
 * Enhanced chat input bar with voice input support (future enhancement).
 */
@Composable
fun EnhancedChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onVoiceInput: () -> Unit = {},
    enabled: Boolean,
    isListening: Boolean = false,
    currentLanguage: Language,
    modifier: Modifier = Modifier
) {
    Surface(
        color = LiftrixColorsV2.surface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium),
            verticalAlignment = Alignment.Bottom
        ) {
            ChatTextField(
                text = text,
                onTextChange = onTextChange,
                onSend = { if (text.isNotBlank()) onSend(text) },
                enabled = enabled,
                currentLanguage = currentLanguage,
                focusRequester = remember { FocusRequester() },
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(LiftrixSpacing.small))
            
            // Voice input button (when text is empty)
            if (text.isBlank() && enabled) {
                VoiceInputButton(
                    onClick = onVoiceInput,
                    isListening = isListening,
                    currentLanguage = currentLanguage
                )
                
                Spacer(modifier = Modifier.width(LiftrixSpacing.small))
            }
            
            SendButton(
                onClick = { 
                    if (text.isNotBlank()) {
                        onSend(text)
                    }
                },
                enabled = enabled && text.isNotBlank(),
                currentLanguage = currentLanguage
            )
        }
    }
}

@Composable
private fun VoiceInputButton(
    onClick: () -> Unit,
    isListening: Boolean,
    currentLanguage: Language
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (isListening) 
                LiftrixColorsV2.primary.copy(alpha = 0.1f) 
            else 
                LiftrixColorsV2.surface
        )
    ) {
        Icon(
            Icons.Default.MicNone,
            contentDescription = getVoiceInputContentDescription(currentLanguage),
            tint = if (isListening) 
                LiftrixColorsV2.primary 
            else 
                LiftrixColorsV2.onSurfaceVariant
        )
    }
}

/**
 * Input limit indicator showing character/word count.
 */
@Composable
fun InputLimitIndicator(
    currentLength: Int,
    maxLength: Int,
    showWarning: Boolean = false,
    modifier: Modifier = Modifier
) {
    val progress = currentLength.toFloat() / maxLength
    val color = when {
        progress >= 1.0f -> MaterialTheme.colorScheme.error
        progress >= 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> LiftrixColorsV2.onSurfaceVariant.copy(alpha = 0.6f)
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LinearProgressIndicator(
            progress = progress.coerceAtMost(1.0f),
            color = color,
            trackColor = color.copy(alpha = 0.3f),
            modifier = Modifier
                .width(40.dp)
                .height(2.dp)
        )
        
        Text(
            text = "$currentLength/$maxLength",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * Localization helpers for different languages.
 */
private fun getPlaceholderText(language: Language): String {
    return when (language) {
        Language.ROMANIAN -> "Întreabă despre antrenamentul tău..."
        Language.ENGLISH -> "Ask about your workout..."
    }
}

private fun getSendButtonContentDescription(language: Language): String {
    return when (language) {
        Language.ROMANIAN -> "Trimite mesaj"
        Language.ENGLISH -> "Send message"
    }
}

private fun getVoiceInputContentDescription(language: Language): String {
    return when (language) {
        Language.ROMANIAN -> "Introducere vocală"
        Language.ENGLISH -> "Voice input"
    }
}