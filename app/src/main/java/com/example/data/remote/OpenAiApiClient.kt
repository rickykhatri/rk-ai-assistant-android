package com.example.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

class OpenAiApiClient {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val client: HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(60, TimeUnit.SECONDS)
                readTimeout(300, TimeUnit.SECONDS)
                writeTimeout(60, TimeUnit.SECONDS)
                callTimeout(300, TimeUnit.SECONDS)
                retryOnConnectionFailure(true)
            }
        }
        install(ContentNegotiation) {
            json(jsonConfig)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 300_000
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("OpenAiApiClient", message)
                }
            }
            level = LogLevel.INFO
        }
    }

    /**
     * Rk Smart Chat API Call
     * Endpoint: https://rkchat-ai.duckdns.org/chat_ai/{prompt}
     * GET (chunked plain-text stream)
     */
    suspend fun sendRkChatCompletion(
        prompt: String,
        model: String = "llama3.2",
        endpoint: String = "https://rkchat-ai.duckdns.org/chat_ai",
        messages: List<RkChatMessage>? = null,
        onChunk: (suspend (String) -> Unit)? = null
    ): Result<String> {
        // Filter out default boilerplate system prompt to keep prompt evaluation fast on server
        val relevantMessages = messages?.filter {
            it.role != "system" || (!it.content.contains("helpful, knowledgeable AI assistant") && it.content.isNotBlank())
        } ?: emptyList()

        val rawPrompt = if (relevantMessages.size > 1) {
            // Keep at most the last 3 recent messages to prevent latency explosion on self-hosted Llama
            val recent = relevantMessages.takeLast(3)
            val sb = StringBuilder()
            recent.forEach { msg ->
                val truncatedContent = if (msg.content.length > 250) msg.content.take(250) + "..." else msg.content
                when (msg.role) {
                    "system" -> sb.append("${truncatedContent}\n\n")
                    "user" -> sb.append("User: ${truncatedContent}\n")
                    "assistant" -> sb.append("Assistant: ${truncatedContent}\n")
                    else -> sb.append("${msg.role}: ${truncatedContent}\n")
                }
            }
            sb.append("Assistant:")
            sb.toString()
        } else {
            prompt.ifBlank { relevantMessages.lastOrNull()?.content ?: "Hello" }.trim()
        }

        val effectivePrompt = rawPrompt.ifBlank { "Hello" }.replace("/", " ∕ ")
        val encodedPrompt = java.net.URLEncoder.encode(effectivePrompt, "UTF-8").replace("+", "%20")

        val effectiveEndpoint = endpoint.ifBlank { "https://rkchat-ai.duckdns.org/chat_ai" }.trimEnd('/')
        val targetUrl = when {
            effectiveEndpoint.contains("/chat_ai") -> "$effectiveEndpoint/$encodedPrompt"
            effectiveEndpoint.contains("/api/chat") -> effectiveEndpoint.replace("/api/chat", "/chat_ai") + "/$encodedPrompt"
            else -> "$effectiveEndpoint/chat_ai/$encodedPrompt"
        }

        return try {
            val accumulated = StringBuilder()
            var streamSuccess = false

            // 1. Attempt streaming GET execution for real-time response rendering
            try {
                client.prepareGet(targetUrl) {
                    header("Accept", "text/plain, */*")
                    timeout {
                        requestTimeoutMillis = 300_000
                        socketTimeoutMillis = 300_000
                        connectTimeoutMillis = 60_000
                    }
                }.execute { response ->
                    if (response.status.isSuccess()) {
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(2048)
                        while (!channel.isClosedForRead) {
                            val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                            if (bytesRead <= 0) break
                            val chunk = String(buffer, 0, bytesRead, Charsets.UTF_8)
                            accumulated.append(chunk)
                            onChunk?.invoke(accumulated.toString())
                        }
                        streamSuccess = accumulated.isNotBlank()
                    } else {
                        val errBody = response.bodyAsText()
                        throw Exception(extractErrorDetail(errBody, response.status.value))
                    }
                }
            } catch (e: Exception) {
                if (accumulated.isBlank()) {
                    Log.w("OpenAiApiClient", "Streaming GET channel failed, falling back to direct GET: ${e.message}")
                } else {
                    throw e
                }
            }

            // 2. Fallback to standard GET request if streaming channel didn't yield text
            if (!streamSuccess && accumulated.isBlank()) {
                val response: HttpResponse = client.get(targetUrl) {
                    header("Accept", "text/plain, */*")
                    timeout {
                        requestTimeoutMillis = 300_000
                        socketTimeoutMillis = 300_000
                        connectTimeoutMillis = 60_000
                    }
                }

                val responseText = response.bodyAsText()
                if (response.status.isSuccess()) {
                    accumulated.append(responseText)
                    onChunk?.invoke(responseText)
                } else {
                    val errorDetail = extractErrorDetail(responseText, response.status.value)
                    return Result.failure(Exception(errorDetail))
                }
            }

            if (accumulated.isNotBlank()) {
                val finalResult = accumulated.toString().trim()
                onChunk?.invoke(finalResult)
                Result.success(finalResult)
            } else {
                Result.failure(Exception("Received empty response from Rk Smart Chat API."))
            }
        } catch (e: Exception) {
            Log.e("OpenAiApiClient", "Error in sendRkChatCompletion", e)
            val friendlyMsg = when {
                e is java.net.SocketTimeoutException || e.message?.contains("timed out", ignoreCase = true) == true || e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Request timed out while waiting for llama3.2. Tap Retry to try again."
                e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                    "Unable to resolve host. Please check your internet connection."
                else -> e.localizedMessage ?: "Failed to connect to chat API."
            }
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    private fun extractContentFromJsonLine(line: String): String? {
        return try {
            val elem = jsonConfig.parseToJsonElement(line)
            if (elem is JsonObject) {
                // 1. Ollama chat stream: {"message": {"content": "..."}}
                val msg = elem["message"]
                if (msg is JsonObject) {
                    val c = msg["content"]?.jsonPrimitive?.contentOrNull
                    if (!c.isNullOrEmpty()) return c
                }
                // 2. Ollama generate stream: {"response": "..."}
                val resp = elem["response"]?.jsonPrimitive?.contentOrNull
                if (!resp.isNullOrEmpty()) return resp

                // 3. Direct content field: {"content": "..."}
                val content = elem["content"]?.jsonPrimitive?.contentOrNull
                if (!content.isNullOrEmpty()) return content

                // 4. OpenAI stream delta: {"choices":[{"delta":{"content":"..."}}]}
                val choices = elem["choices"] as? JsonArray
                val firstChoice = choices?.firstOrNull() as? JsonObject
                val delta = firstChoice?.get("delta") as? JsonObject
                val deltaContent = delta?.get("content")?.jsonPrimitive?.contentOrNull
                if (!deltaContent.isNullOrEmpty()) return deltaContent

                // 5. OpenAI choice message: {"choices":[{"message":{"content":"..."}}]}
                val choiceMsg = firstChoice?.get("message") as? JsonObject
                val choiceContent = choiceMsg?.get("content")?.jsonPrimitive?.contentOrNull
                if (!choiceContent.isNullOrEmpty()) return choiceContent
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractContentFromFullResponse(raw: String): String {
        return try {
            val elem = jsonConfig.parseToJsonElement(raw.trim())
            if (elem is JsonObject) {
                val msg = elem["message"]
                if (msg is JsonObject) {
                    msg["content"]?.jsonPrimitive?.contentOrNull?.let { return it }
                }
                elem["response"]?.jsonPrimitive?.contentOrNull?.let { return it }
                elem["content"]?.jsonPrimitive?.contentOrNull?.let { return it }
                val choices = elem["choices"] as? JsonArray
                val first = choices?.firstOrNull() as? JsonObject
                val choiceMsg = first?.get("message") as? JsonObject
                choiceMsg?.get("content")?.jsonPrimitive?.contentOrNull?.let { return it }
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractErrorDetail(responseBody: String, statusCode: Int): String {
        return try {
            val elem = jsonConfig.parseToJsonElement(responseBody.trim())
            val detail = (elem as? JsonObject)?.get("detail")?.jsonPrimitive?.contentOrNull
                ?: (elem as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                ?: (elem as? JsonObject)?.get("error")?.jsonPrimitive?.contentOrNull
            if (!detail.isNullOrBlank()) {
                "API Error ($statusCode): $detail"
            } else {
                "API Error ($statusCode): $responseBody"
            }
        } catch (_: Exception) {
            "API Error ($statusCode): ${responseBody.ifBlank { "No details provided" }}"
        }
    }

    /*
     * Previous Gen-AI / OpenAI API implementation
     * (Commented out as requested by user to switch to Rk Chat API)
     *
    suspend fun sendChatCompletion(
        apiKey: String,
        endpoint: String = "https://api.openai.com/v1/chat/completions",
        model: String,
        messages: List<OpenAiMessageDto>,
        temperature: Float = 0.7f
    ): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(
                IllegalStateException("Please configure your OpenAI API Key in Settings to start chatting.")
            )
        }

        return try {
            val requestBody = ChatCompletionRequest(
                model = model,
                messages = messages,
                temperature = temperature,
                stream = false
            )

            val effectiveEndpoint = endpoint.ifBlank { "https://api.openai.com/v1/chat/completions" }

            val response: HttpResponse = client.post(effectiveEndpoint) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                setBody(requestBody)
            }

            val responseText = response.bodyAsText()

            if (response.status.isSuccess()) {
                val completion: ChatCompletionResponse = jsonConfig.decodeFromString(responseText)
                val assistantMessage = completion.choices.firstOrNull()?.message?.content
                if (!assistantMessage.isNullOrBlank()) {
                    Result.success(assistantMessage.trim())
                } else {
                    Result.failure(Exception("Received empty response from OpenAI."))
                }
            } else {
                // Try parsing structured error response
                val errorMessage = try {
                    val errorObj = jsonConfig.decodeFromString<OpenAiErrorResponse>(responseText)
                    errorObj.error?.message ?: "HTTP ${response.status.value}: ${response.status.description}"
                } catch (e: Exception) {
                    "HTTP ${response.status.value}: $responseText"
                }

                val formattedError = when {
                    response.status.value == 401 -> "Invalid OpenAI API Key (401). Please check your key in Settings."
                    response.status.value == 429 -> "Rate limit or quota exceeded (429). Please check your OpenAI account billing: $errorMessage"
                    response.status.value == 404 -> "Model '$model' not found or invalid endpoint: $errorMessage"
                    else -> "OpenAI Error (${response.status.value}): $errorMessage"
                }
                Result.failure(Exception(formattedError))
            }
        } catch (e: Exception) {
            Log.e("OpenAiApiClient", "Network exception during chat completion", e)
            val friendlyMsg = when {
                e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                    "No internet connection. Please check your network and try again."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Request timed out. OpenAI took too long to respond. Please try again."
                else -> e.localizedMessage ?: "Failed to connect to OpenAI API."
            }
            Result.failure(Exception(friendlyMsg, e))
        }
    }
    */
}
