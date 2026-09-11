package com.example.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload data classes for Rk Smart Chat API (https://rkchat-ai.duckdns.org/api/chat)
 */
@Serializable
data class RkChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class RkChatOptions(
    @SerialName("num_ctx")
    val numCtx: Int = 4096
)

@Serializable
data class RkChatRequest(
    val model: String = "llama3.2",
    val messages: List<RkChatMessage>,
    val stream: Boolean = true,
    val options: RkChatOptions = RkChatOptions(numCtx = 4096)
)

@Serializable
data class OpenAiMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<OpenAiMessageDto>,
    val temperature: Float = 0.7f,
    val stream: Boolean = false
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChoiceDto> = emptyList(),
    val usage: UsageDto? = null
)

@Serializable
data class ChoiceDto(
    val index: Int = 0,
    val message: OpenAiMessageDto? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0
)

@Serializable
data class OpenAiErrorResponse(
    val error: OpenAiErrorDetail? = null
)

@Serializable
data class OpenAiErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val param: String? = null,
    val code: String? = null
)
