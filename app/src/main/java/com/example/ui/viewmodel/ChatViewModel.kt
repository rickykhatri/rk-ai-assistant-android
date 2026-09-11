package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageStatus
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserSettings
import com.example.data.remote.OpenAiApiClient
import com.example.data.repository.ChatRepository
import com.example.ui.mvi.ChatIntent
import com.example.ui.mvi.ChatSideEffect
import com.example.ui.mvi.ChatUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _sideEffects = MutableSharedFlow<ChatSideEffect>()
    val sideEffects: SharedFlow<ChatSideEffect> = _sideEffects.asSharedFlow()

    private var messageCollectionJob: Job? = null
    private var conversationDetailsJob: Job? = null
    private var activeGenerationJob: Job? = null
    private var activeAssistantMessageId: String? = null

    init {
        observeSettings()
        observeConversations()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            repository.userSettings.collectLatest { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    private fun observeConversations() {
        viewModelScope.launch {
            repository.allConversations.collectLatest { convList ->
                _uiState.update { state ->
                    val updatedCurrentId = state.currentConversationId ?: convList.firstOrNull()?.id
                    state.copy(
                        conversations = convList,
                        currentConversationId = updatedCurrentId
                    )
                }

                // If current conversation changed or set for first time, load its messages
                _uiState.value.currentConversationId?.let { convId ->
                    bindActiveConversation(convId)
                }
            }
        }
    }

    private fun bindActiveConversation(convId: String) {
        messageCollectionJob?.cancel()
        conversationDetailsJob?.cancel()

        conversationDetailsJob = viewModelScope.launch {
            repository.getConversation(convId).collectLatest { conv ->
                _uiState.update { it.copy(currentConversation = conv) }
            }
        }

        messageCollectionJob = viewModelScope.launch {
            repository.getMessagesForConversation(convId).collectLatest { msgs ->
                val hasSending = msgs.any { it.status == MessageStatus.SENDING }
                _uiState.update {
                    it.copy(
                        messages = msgs,
                        isGenerating = hasSending
                    )
                }
                _sideEffects.emit(ChatSideEffect.ScrollToBottom)
            }
        }
    }

    fun processIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.UpdateInputText -> {
                _uiState.update { it.copy(inputText = intent.text) }
            }

            is ChatIntent.SendCurrentMessage -> {
                val currentText = _uiState.value.inputText.trim()
                if (currentText.isNotEmpty()) {
                    sendMessage(currentText)
                    _uiState.update { it.copy(inputText = "") }
                }
            }

            is ChatIntent.SendCustomMessage -> {
                val text = intent.text.trim()
                if (text.isNotEmpty()) {
                    sendMessage(text)
                }
            }

            is ChatIntent.SelectConversation -> {
                _uiState.update {
                    it.copy(
                        currentConversationId = intent.conversationId,
                        isSidebarOpen = false
                    )
                }
                bindActiveConversation(intent.conversationId)
            }

            is ChatIntent.CreateNewConversation -> {
                viewModelScope.launch {
                    val settings = _uiState.value.settings
                    val newConv = repository.createNewConversation(
                        title = "New Chat",
                        model = settings.defaultModel,
                        systemPrompt = settings.systemPrompt
                    )
                    _uiState.update {
                        it.copy(
                            currentConversationId = newConv.id,
                            isSidebarOpen = false,
                            inputText = ""
                        )
                    }
                    bindActiveConversation(newConv.id)
                }
            }

            is ChatIntent.DeleteConversation -> {
                viewModelScope.launch {
                    val targetId = intent.conversationId
                    repository.deleteConversation(targetId)
                    if (_uiState.value.currentConversationId == targetId) {
                        val remaining = _uiState.value.conversations.filter { it.id != targetId }
                        val nextId = remaining.firstOrNull()?.id
                        _uiState.update { it.copy(currentConversationId = nextId) }
                        if (nextId != null) {
                            bindActiveConversation(nextId)
                        } else {
                            _uiState.update {
                                it.copy(
                                    currentConversation = null,
                                    messages = emptyList()
                                )
                            }
                        }
                    }
                    _sideEffects.emit(ChatSideEffect.ShowToast("Conversation deleted"))
                }
            }

            is ChatIntent.RenameConversation -> {
                viewModelScope.launch {
                    if (intent.newTitle.isNotBlank()) {
                        repository.renameConversation(intent.conversationId, intent.newTitle)
                    }
                }
            }

            is ChatIntent.ClearAllConversations -> {
                viewModelScope.launch {
                    repository.deleteAllConversations()
                    _uiState.update {
                        it.copy(
                            currentConversationId = null,
                            currentConversation = null,
                            messages = emptyList(),
                            isSidebarOpen = false
                        )
                    }
                    _sideEffects.emit(ChatSideEffect.ShowToast("All conversations cleared"))
                }
            }

            is ChatIntent.UpdateSearchQuery -> {
                _uiState.update { it.copy(searchQuery = intent.query) }
            }

            is ChatIntent.SetSidebarOpen -> {
                _uiState.update { it.copy(isSidebarOpen = intent.isOpen) }
            }

            is ChatIntent.SetSettingsOpen -> {
                _uiState.update { it.copy(isSettingsOpen = intent.isOpen) }
            }

            is ChatIntent.UpdateThemeMode -> {
                viewModelScope.launch {
                    repository.saveThemeMode(intent.themeMode)
                }
            }

            is ChatIntent.SaveAllSettings -> {
                viewModelScope.launch {
                    repository.updateSettings(
                        apiKey = intent.apiKey,
                        themeMode = intent.themeMode,
                        model = intent.defaultModel,
                        systemPrompt = intent.systemPrompt,
                        temperature = intent.temperature
                    )
                    _uiState.update { it.copy(isSettingsOpen = false) }
                    _sideEffects.emit(ChatSideEffect.ShowToast("Settings updated"))
                }
            }

            is ChatIntent.RetryMessage -> {
                val convId = _uiState.value.currentConversationId ?: return
                activeGenerationJob?.cancel()
                activeAssistantMessageId = intent.messageId
                activeGenerationJob = viewModelScope.launch {
                    _uiState.update { it.copy(isGenerating = true) }
                    try {
                        val result = repository.retryMessage(convId, intent.messageId)
                        result.onFailure { err ->
                            if (err !is CancellationException) {
                                _uiState.update { it.copy(snackbarMessage = err.localizedMessage) }
                            }
                        }
                    } catch (e: CancellationException) {
                        // User stopped generation
                    } finally {
                        _uiState.update { it.copy(isGenerating = false) }
                        activeAssistantMessageId = null
                    }
                }
            }

            is ChatIntent.StopGeneration -> {
                val currentId = _uiState.value.currentConversationId
                val messageIdToStop = activeAssistantMessageId
                activeGenerationJob?.cancel()
                activeGenerationJob = null
                _uiState.update { it.copy(isGenerating = false) }
                if (currentId != null) {
                    viewModelScope.launch(NonCancellable) {
                        repository.stopGenerating(currentId, messageIdToStop)
                    }
                }
            }

            is ChatIntent.CopyMessageContent -> {
                viewModelScope.launch {
                    _sideEffects.emit(ChatSideEffect.CopyToClipboard(intent.content))
                    _sideEffects.emit(ChatSideEffect.ShowToast("Copied to clipboard"))
                }
            }

            is ChatIntent.DismissSnackbar -> {
                _uiState.update { it.copy(snackbarMessage = null) }
            }
        }
    }

    private fun sendMessage(text: String) {
        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            val state = _uiState.value
            val currentId = state.currentConversationId ?: run {
                val newConv = repository.createNewConversation(model = state.settings.defaultModel)
                _uiState.update { it.copy(currentConversationId = newConv.id) }
                bindActiveConversation(newConv.id)
                newConv.id
            }

            _uiState.update { it.copy(isGenerating = true) }
            try {
                val result = repository.sendMessage(currentId, text) { msgId ->
                    activeAssistantMessageId = msgId
                }
                result.onFailure { error ->
                    if (error !is CancellationException) {
                        _uiState.update { it.copy(snackbarMessage = error.localizedMessage) }
                    }
                }
            } catch (e: CancellationException) {
                // User stopped generation
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
                activeAssistantMessageId = null
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getInstance(context)
            val preferences = AppPreferences(context)
            val apiClient = OpenAiApiClient()
            val repository = ChatRepository(
                conversationDao = db.conversationDao(),
                chatMessageDao = db.chatMessageDao(),
                appPreferences = preferences,
                apiClient = apiClient
            )
            return ChatViewModel(repository) as T
        }
    }
}
