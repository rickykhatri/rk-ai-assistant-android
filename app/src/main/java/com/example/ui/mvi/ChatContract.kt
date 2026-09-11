package com.example.ui.mvi

import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserSettings

data class ConversationItem(
    val conversation: ConversationEntity,
    val lastMessagePreview: String = "",
    val messageCount: Int = 0
)

data class ChatUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val currentConversationId: String? = null,
    val currentConversation: ConversationEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val isSidebarOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val searchQuery: String = "",
    val settings: UserSettings = UserSettings(
        apiKey = "",
        themeMode = ThemeMode.SYSTEM,
        defaultModel = "llama3.2",
        systemPrompt = "You are a helpful and knowledgeable AI assistant.",
        temperature = 0.7f,
        customEndpoint = "https://rkchat-ai.duckdns.org/chat_ai"
    ),
    val snackbarMessage: String? = null
)

sealed interface ChatIntent {
    data class UpdateInputText(val text: String) : ChatIntent
    data object SendCurrentMessage : ChatIntent
    data class SendCustomMessage(val text: String) : ChatIntent
    data class SelectConversation(val conversationId: String) : ChatIntent
    data object CreateNewConversation : ChatIntent
    data class DeleteConversation(val conversationId: String) : ChatIntent
    data class RenameConversation(val conversationId: String, val newTitle: String) : ChatIntent
    data object ClearAllConversations : ChatIntent
    data class UpdateSearchQuery(val query: String) : ChatIntent
    data class SetSidebarOpen(val isOpen: Boolean) : ChatIntent
    data class SetSettingsOpen(val isOpen: Boolean) : ChatIntent
    data class UpdateThemeMode(val themeMode: ThemeMode) : ChatIntent
    data class SaveAllSettings(
        val apiKey: String,
        val themeMode: ThemeMode,
        val defaultModel: String,
        val systemPrompt: String,
        val temperature: Float
    ) : ChatIntent
    data class RetryMessage(val messageId: String) : ChatIntent
    data object StopGeneration : ChatIntent
    data class CopyMessageContent(val content: String) : ChatIntent
    data object DismissSnackbar : ChatIntent
}

sealed interface ChatSideEffect {
    data class ShowToast(val message: String) : ChatSideEffect
    data class CopyToClipboard(val text: String) : ChatSideEffect
    data object ScrollToBottom : ChatSideEffect
}
