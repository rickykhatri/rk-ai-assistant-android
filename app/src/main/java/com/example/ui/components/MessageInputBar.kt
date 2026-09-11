package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SophisticatedDarkSurfaceElevated

data class SuggestionPrompt(
    val title: String,
    val prompt: String,
    val iconEmoji: String = "💡"
)

val DEFAULT_PROMPTS = listOf(
    SuggestionPrompt("Explain Concept", "Explain quantum computing in simple terms with analogies."),
    SuggestionPrompt("Write Code", "Write a clean Kotlin coroutine flow pattern for Android with error handling."),
    SuggestionPrompt("Brainstorm Ideas", "Brainstorm 5 innovative startup ideas in AI automation."),
    SuggestionPrompt("Draft Email", "Draft a professional follow-up email after a job interview."),
    SuggestionPrompt("Summarize", "Summarize the key differences between MVI and MVVM architectures.")
)

@Composable
fun MessageInputBar(
    inputText: String,
    isGenerating: Boolean,
    showSuggestions: Boolean,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit = {},
    onSelectSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Suggested prompt chips on empty conversation
        if (showSuggestions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DEFAULT_PROMPTS.forEach { item ->
                    FilterChip(
                        selected = false,
                        onClick = { onSelectSuggestion(item.prompt) },
                        label = {
                            Text(
                                text = "${item.iconEmoji} ${item.title}",
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("suggestion_chip_${item.title}")
                    )
                }
            }
        }

        val isLightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        val containerColor = if (isLightTheme) MaterialTheme.colorScheme.surfaceVariant else SophisticatedDarkSurfaceElevated
        val containerBorderColor = when {
            inputText.isNotBlank() -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            isLightTheme -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        }

        // Input Container
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            border = BorderStroke(1.dp, containerBorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val inputTextColor = if (isLightTheme) Color.Black else MaterialTheme.colorScheme.onSurface
                val placeholderTextColor = if (isLightTheme) Color.Black.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                val cursorColor = if (isLightTheme) Color.Black else MaterialTheme.colorScheme.primary

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = inputTextColor,
                        fontSize = 15.sp
                    ),
                    placeholder = {
                        Text(
                            text = if (isGenerating) "Generating… tap ■ to stop" else "Ask anything…",
                            fontSize = 15.sp,
                            color = placeholderTextColor
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("message_input_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = inputTextColor,
                        unfocusedTextColor = inputTextColor,
                        cursorColor = cursorColor,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    )
                )

                // Clear button if text exists
                AnimatedVisibility(
                    visible = inputText.isNotBlank() && !isGenerating,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(
                        onClick = { onInputChange("") },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("clear_input_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear input",
                            tint = if (isLightTheme) Color.Black.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Send / Stop Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isGenerating -> MaterialTheme.colorScheme.errorContainer
                                inputText.isNotBlank() -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGenerating) {
                        IconButton(
                            onClick = onStopGeneration,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("stop_streaming_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop generation",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onSendMessage,
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message",
                                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

