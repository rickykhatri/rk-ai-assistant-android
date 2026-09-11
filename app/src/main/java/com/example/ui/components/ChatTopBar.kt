package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ConversationEntity
import com.example.data.preferences.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    currentConversation: ConversationEntity?,
    themeMode: ThemeMode,
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleTheme: (ThemeMode) -> Unit,
    onRenameClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier.testTag("chat_top_bar"),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        navigationIcon = {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.testTag("open_drawer_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open conversation history drawer"
                )
            }
        },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentConversation?.title?.ifBlank { "RK AI Assist" } ?: "RK AI Assist",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Model Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val rawModel = currentConversation?.model ?: "RK AI"
                            val displayModel = if (rawModel.contains("llama", ignoreCase = true)) "RK AI" else rawModel.removePrefix("gpt-").uppercase()
                            Text(
                                text = displayModel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        },
        actions = {
            // New Chat Button
            IconButton(
                onClick = onNewChatClick,
                modifier = Modifier.testTag("new_chat_top_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Start new chat",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Quick Theme Switch Button
            IconButton(
                onClick = {
                    val nextTheme = when (themeMode) {
                        ThemeMode.DARK -> ThemeMode.LIGHT
                        ThemeMode.LIGHT -> ThemeMode.SYSTEM
                        ThemeMode.SYSTEM -> ThemeMode.DARK
                    }
                    onToggleTheme(nextTheme)
                },
                modifier = Modifier.testTag("toggle_theme_button")
            ) {
                Icon(
                    imageVector = when (themeMode) {
                        ThemeMode.DARK -> Icons.Default.DarkMode
                        ThemeMode.LIGHT -> Icons.Default.LightMode
                        ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                    },
                    contentDescription = "Toggle theme mode: ${themeMode.title}"
                )
            }

            // More Options / Context Menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("more_options_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More chat options"
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (currentConversation != null) {
                        DropdownMenuItem(
                            text = { Text("Rename Chat") },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRenameClick(currentConversation.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Chat", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick(currentConversation.id)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Settings & API Key") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onSettingsClick()
                        }
                    )
                }
            }
        }
    )
}
