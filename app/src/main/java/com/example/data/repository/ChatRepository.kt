package com.example.data.repository

import com.example.data.local.dao.ChatMessageDao
import com.example.data.local.dao.ConversationDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageRole
import com.example.data.local.entity.MessageStatus
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserSettings
import com.example.data.remote.OpenAiApiClient
import com.example.data.remote.OpenAiMessageDto
import com.example.data.remote.RkChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val chatMessageDao: ChatMessageDao,
    private val appPreferences: AppPreferences,
    private val apiClient: OpenAiApiClient
) {

    val allConversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()

    val userSettings: Flow<UserSettings> = appPreferences.userSettings

    val themeMode: Flow<ThemeMode> = appPreferences.themeMode

    fun getConversation(id: String): Flow<ConversationEntity?> =
        conversationDao.getConversationById(id)

    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>> =
        chatMessageDao.getMessagesForConversation(conversationId)

    fun searchConversations(query: String): Flow<List<ConversationEntity>> =
        conversationDao.searchConversations(query)

    suspend fun createNewConversation(
        title: String = "New Chat",
        model: String = "llama3.2",
        systemPrompt: String? = null
    ): ConversationEntity {
        val newConv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            model = model,
            systemPrompt = systemPrompt
        )
        conversationDao.insertConversation(newConv)
        appPreferences.saveLastConversationId(newConv.id)
        return newConv
    }

    suspend fun renameConversation(id: String, newTitle: String) {
        conversationDao.updateConversationTitle(id, newTitle.trim())
    }

    suspend fun deleteConversation(id: String) {
        chatMessageDao.deleteMessagesForConversation(id)
        conversationDao.deleteConversationById(id)
    }

    suspend fun deleteAllConversations() {
        conversationDao.deleteAllConversations()
    }

    suspend fun saveApiKey(apiKey: String) {
        appPreferences.saveApiKey(apiKey)
    }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        appPreferences.saveThemeMode(themeMode)
    }

    suspend fun saveDefaultModel(model: String) {
        appPreferences.saveDefaultModel(model)
    }

    suspend fun updateSettings(
        apiKey: String,
        themeMode: ThemeMode,
        model: String,
        systemPrompt: String,
        temperature: Float
    ) {
        appPreferences.updateAllSettings(apiKey, themeMode, model, systemPrompt, temperature)
    }

    suspend fun sendMessage(
        conversationId: String,
        userText: String,
        onMessageCreated: ((String) -> Unit)? = null
    ): Result<String> {
        val trimmedText = userText.trim()
        if (trimmedText.isEmpty()) return Result.failure(IllegalArgumentException("Message cannot be empty"))

        val settings = appPreferences.userSettings.first()
        val conv = conversationDao.getConversationByIdOnce(conversationId)
            ?: createNewConversation(model = settings.defaultModel)

        // 1. Insert user message
        val userMessage = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conv.id,
            role = MessageRole.USER.value,
            content = trimmedText,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SUCCESS
        )
        chatMessageDao.insertMessage(userMessage)

        // 2. Update conversation title if needed (e.g. initial message)
        val messageCount = chatMessageDao.getMessageCount(conv.id)
        if (messageCount <= 1 || conv.title == "New Chat" || conv.title.isBlank()) {
            val autoTitle = generateSmartTitle(trimmedText)
            conversationDao.updateConversationTitle(conv.id, autoTitle)
        } else {
            conversationDao.updateConversationTimestamp(conv.id)
        }

        // 3. Create placeholder assistant message
        val assistantMessageId = UUID.randomUUID().toString()
        val placeholderMessage = ChatMessageEntity(
            id = assistantMessageId,
            conversationId = conv.id,
            role = MessageRole.ASSISTANT.value,
            content = "",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING
        )
        chatMessageDao.insertMessage(placeholderMessage)
        onMessageCreated?.invoke(assistantMessageId)

        // 4. Build message payload
        val history = chatMessageDao.getMessagesForConversationOnce(conv.id)
        val rkMessages = mutableListOf<RkChatMessage>()

        // Include system prompt only if user explicitly customized it
        val effectiveSystemPrompt = conv.systemPrompt?.ifBlank { null } ?: settings.systemPrompt
        val isDefaultPrompt = effectiveSystemPrompt.contains("helpful, knowledgeable AI assistant")
        if (effectiveSystemPrompt.isNotBlank() && !isDefaultPrompt) {
            rkMessages.add(RkChatMessage(role = "system", content = effectiveSystemPrompt))
        }

        // Add recent history excluding the pending placeholder (last 4 messages to preserve speed)
        val filteredHistory = history.filter { it.id != assistantMessageId && it.status != MessageStatus.ERROR }
        filteredHistory.takeLast(4).forEach { msg ->
            rkMessages.add(RkChatMessage(role = msg.role, content = msg.content))
        }

        // 5. Call Rk Smart Chat API (https://rkchat-ai.duckdns.org/chat_ai)
        var lastDbUpdateMillis = 0L
        val result = apiClient.sendRkChatCompletion(
            prompt = trimmedText,
            model = conv.model.ifBlank { "llama3.2" },
            endpoint = settings.customEndpoint.ifBlank { "https://rkchat-ai.duckdns.org/chat_ai" },
            messages = rkMessages,
            onChunk = { streamedText ->
                val now = System.currentTimeMillis()
                if (now - lastDbUpdateMillis > 150L) {
                    lastDbUpdateMillis = now
                    chatMessageDao.updateMessageContentAndStatus(
                        id = assistantMessageId,
                        content = streamedText,
                        status = MessageStatus.SENDING
                    )
                }
            }
        )

        result.onSuccess { responseText ->
            chatMessageDao.updateMessageContentAndStatus(
                id = assistantMessageId,
                content = responseText,
                status = MessageStatus.SUCCESS,
                errorMessage = null
            )
            conversationDao.updateConversationTimestamp(conv.id)
        }.onFailure { error ->
            chatMessageDao.updateMessageContentAndStatus(
                id = assistantMessageId,
                content = "Could not generate response.",
                status = MessageStatus.ERROR,
                errorMessage = error.localizedMessage ?: "Unknown error"
            )
        }

        return result
    }

    suspend fun retryMessage(conversationId: String, failedMessageId: String): Result<String> {
        val settings = appPreferences.userSettings.first()
        val conv = conversationDao.getConversationByIdOnce(conversationId)
            ?: return Result.failure(IllegalStateException("Conversation not found"))

        // Set status to SENDING
        chatMessageDao.updateMessageContentAndStatus(
            id = failedMessageId,
            content = "",
            status = MessageStatus.SENDING,
            errorMessage = null
        )

        val history = chatMessageDao.getMessagesForConversationOnce(conv.id)
        val rkMessages = mutableListOf<RkChatMessage>()

        val effectiveSystemPrompt = conv.systemPrompt?.ifBlank { null } ?: settings.systemPrompt
        val isDefaultPrompt = effectiveSystemPrompt.contains("helpful, knowledgeable AI assistant")
        if (effectiveSystemPrompt.isNotBlank() && !isDefaultPrompt) {
            rkMessages.add(RkChatMessage(role = "system", content = effectiveSystemPrompt))
        }

        val filteredHistory = history.filter { it.id != failedMessageId && it.status != MessageStatus.ERROR }
        filteredHistory.takeLast(4).forEach { msg ->
            rkMessages.add(RkChatMessage(role = msg.role, content = msg.content))
        }

        val lastUserText = rkMessages.lastOrNull { it.role == "user" }?.content ?: ""

        var lastDbUpdateMillis = 0L
        val result = apiClient.sendRkChatCompletion(
            prompt = lastUserText,
            model = conv.model.ifBlank { "llama3.2" },
            endpoint = settings.customEndpoint.ifBlank { "https://rkchat-ai.duckdns.org/chat_ai" },
            messages = rkMessages,
            onChunk = { streamedText ->
                val now = System.currentTimeMillis()
                if (now - lastDbUpdateMillis > 150L) {
                    lastDbUpdateMillis = now
                    chatMessageDao.updateMessageContentAndStatus(
                        id = failedMessageId,
                        content = streamedText,
                        status = MessageStatus.SENDING
                    )
                }
            }
        )

        result.onSuccess { responseText ->
            chatMessageDao.updateMessageContentAndStatus(
                id = failedMessageId,
                content = responseText,
                status = MessageStatus.SUCCESS,
                errorMessage = null
            )
            conversationDao.updateConversationTimestamp(conv.id)
        }.onFailure { error ->
            chatMessageDao.updateMessageContentAndStatus(
                id = failedMessageId,
                content = "Could not generate response.",
                status = MessageStatus.ERROR,
                errorMessage = error.localizedMessage ?: "Unknown error"
            )
        }

        return result
    }

    suspend fun stopGenerating(conversationId: String, assistantMessageId: String?) {
        val targetId = assistantMessageId ?: run {
            chatMessageDao.getMessagesForConversationOnce(conversationId)
                .lastOrNull { it.status == MessageStatus.SENDING }?.id
        }

        if (targetId != null) {
            val message = chatMessageDao.getMessageById(targetId)
            val finalContent = if (message != null && message.content.isNotBlank()) {
                message.content
            } else {
                "Generation stopped."
            }
            chatMessageDao.updateMessageContentAndStatus(
                id = targetId,
                content = finalContent,
                status = MessageStatus.SUCCESS,
                errorMessage = null
            )
            conversationDao.updateConversationTimestamp(conversationId)
        }
    }

    private fun generateSmartTitle(firstMessage: String): String {
        val clean = firstMessage.lines().firstOrNull()?.trim() ?: firstMessage.trim()
        return if (clean.length > 36) {
            clean.take(34) + "…"
        } else {
            clean.ifEmpty { "New Chat" }
        }
    }
}
