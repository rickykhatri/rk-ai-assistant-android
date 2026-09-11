package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ChatTopBar
import com.example.ui.components.ConversationDrawer
import com.example.ui.components.MessageInputBar
import com.example.ui.components.MessageItem
import com.example.ui.components.RenameDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.mvi.ChatIntent
import com.example.ui.mvi.ChatSideEffect
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var renamingConversationId by remember { mutableStateOf<String?>(null) }
    var renamingInitialTitle by remember { mutableStateOf("") }

    // Sync drawer state with viewmodel
    LaunchedEffect(state.isSidebarOpen) {
        if (state.isSidebarOpen && drawerState.isClosed) {
            drawerState.open()
        } else if (!state.isSidebarOpen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen != state.isSidebarOpen) {
            viewModel.processIntent(ChatIntent.SetSidebarOpen(drawerState.isOpen))
        }
    }

    // Handle MVI Side Effects
    LaunchedEffect(Unit) {
        viewModel.sideEffects.collectLatest { effect ->
            when (effect) {
                is ChatSideEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is ChatSideEffect.CopyToClipboard -> {
                    clipboardManager.setText(AnnotatedString(effect.text))
                }
                is ChatSideEffect.ScrollToBottom -> {
                    if (state.messages.isNotEmpty()) {
                        listState.animateScrollToItem(state.messages.size - 1)
                    }
                }
            }
        }
    }

    // Handle Snackbar messages from state
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = "OK",
                duration = SnackbarDuration.Short
            )
            viewModel.processIntent(ChatIntent.DismissSnackbar)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                conversations = state.conversations,
                currentConversationId = state.currentConversationId,
                searchQuery = state.searchQuery,
                hasApiKey = state.settings.apiKey.isNotBlank(),
                themeMode = state.settings.themeMode,
                onSelectConversation = { convId ->
                    viewModel.processIntent(ChatIntent.SelectConversation(convId))
                    coroutineScope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.processIntent(ChatIntent.CreateNewConversation)
                    coroutineScope.launch { drawerState.close() }
                },
                onSearchChange = { viewModel.processIntent(ChatIntent.UpdateSearchQuery(it)) },
                onRenameConversation = { convId ->
                    val conv = state.conversations.firstOrNull { it.id == convId }
                    renamingConversationId = convId
                    renamingInitialTitle = conv?.title ?: ""
                },
                onDeleteConversation = { convId ->
                    viewModel.processIntent(ChatIntent.DeleteConversation(convId))
                },
                onClearAll = {
                    viewModel.processIntent(ChatIntent.ClearAllConversations)
                },
                onOpenSettings = {
                    viewModel.processIntent(ChatIntent.SetSettingsOpen(true))
                    coroutineScope.launch { drawerState.close() }
                },
                onToggleTheme = { newTheme ->
                    viewModel.processIntent(ChatIntent.UpdateThemeMode(newTheme))
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .testTag("chat_scaffold"),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                ChatTopBar(
                    currentConversation = state.currentConversation,
                    themeMode = state.settings.themeMode,
                    onMenuClick = {
                        coroutineScope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    },
                    onNewChatClick = {
                        viewModel.processIntent(ChatIntent.CreateNewConversation)
                    },
                    onSettingsClick = {
                        viewModel.processIntent(ChatIntent.SetSettingsOpen(true))
                    },
                    onToggleTheme = { nextTheme ->
                        viewModel.processIntent(ChatIntent.UpdateThemeMode(nextTheme))
                    },
                    onRenameClick = { convId ->
                        val conv = state.conversations.firstOrNull { it.id == convId }
                        renamingConversationId = convId
                        renamingInitialTitle = conv?.title ?: ""
                    },
                    onDeleteClick = { convId ->
                        viewModel.processIntent(ChatIntent.DeleteConversation(convId))
                    }
                )
            },
            bottomBar = {
                MessageInputBar(
                    inputText = state.inputText,
                    isGenerating = state.isGenerating,
                    showSuggestions = state.messages.isEmpty(),
                    onInputChange = { viewModel.processIntent(ChatIntent.UpdateInputText(it)) },
                    onSendMessage = { viewModel.processIntent(ChatIntent.SendCurrentMessage) },
                    onStopGeneration = { viewModel.processIntent(ChatIntent.StopGeneration) },
                    onSelectSuggestion = { prompt ->
                        viewModel.processIntent(ChatIntent.SendCustomMessage(prompt))
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (state.messages.isEmpty()) {
                    // Empty State / Welcome Screen
                    EmptyChatWelcomeView(
                        hasApiKey = state.settings.apiKey.isNotBlank(),
                        modelName = state.currentConversation?.model ?: state.settings.defaultModel,
                        onOpenSettings = { viewModel.processIntent(ChatIntent.SetSettingsOpen(true)) },
                        onSelectPrompt = { prompt ->
                            viewModel.processIntent(ChatIntent.SendCustomMessage(prompt))
                        }
                    )
                } else {
                    // Message Stream
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("chat_messages_list"),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(
                            items = state.messages,
                            key = { it.id }
                        ) { message ->
                            MessageItem(
                                message = message,
                                onCopy = { text ->
                                    viewModel.processIntent(ChatIntent.CopyMessageContent(text))
                                },
                                onRetry = { msgId ->
                                    viewModel.processIntent(ChatIntent.RetryMessage(msgId))
                                },
                                onStop = {
                                    viewModel.processIntent(ChatIntent.StopGeneration)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Settings Modal Dialog
    if (state.isSettingsOpen) {
        SettingsDialog(
            currentSettings = state.settings,
            onDismiss = { viewModel.processIntent(ChatIntent.SetSettingsOpen(false)) },
            onSave = { apiKey, theme, model, prompt, temp ->
                viewModel.processIntent(
                    ChatIntent.SaveAllSettings(
                        apiKey = apiKey,
                        themeMode = theme,
                        defaultModel = model,
                        systemPrompt = prompt,
                        temperature = temp
                    )
                )
            }
        )
    }

    // Rename Conversation Dialog
    renamingConversationId?.let { convId ->
        RenameDialog(
            initialTitle = renamingInitialTitle,
            onDismiss = { renamingConversationId = null },
            onConfirm = { newTitle ->
                viewModel.processIntent(ChatIntent.RenameConversation(convId, newTitle))
                renamingConversationId = null
            }
        )
    }
}

@Composable
fun EmptyChatWelcomeView(
    hasApiKey: Boolean = true,
    modelName: String,
    onOpenSettings: () -> Unit,
    onSelectPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("empty_chat_welcome_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "What can I help with today?",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        val displayModel = if (modelName.contains("llama", ignoreCase = true)) "RK AI" else modelName
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
            Text(
                text = "RK AI Assist • $displayModel",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Suggested Prompts",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        val samplePrompts = listOf(
            "Explain quantum computing simply",
            "Write a Python script for file download",
            "Give me 5 creative weekend project ideas",
            "Draft a professional email for a project update"
        )

        Column(
            modifier = Modifier.fillMaxWidth(0.92f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            samplePrompts.forEach { prompt ->
                Surface(
                    onClick = { onSelectPrompt(prompt) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("suggested_prompt_${prompt.take(10)}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = prompt,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
