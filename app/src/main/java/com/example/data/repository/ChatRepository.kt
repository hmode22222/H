package com.example.data.repository

import com.example.data.api.ContentItem
import com.example.data.api.GeminiApiClient
import com.example.data.api.GeminiRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.PartItem
import com.example.data.api.SystemInstruction
import com.example.data.local.ChatDao
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(private val chatDao: ChatDao) {

    val allConversations: Flow<List<ConversationEntity>> = chatDao.getAllConversations()

    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesForConversation(conversationId)
    }

    suspend fun createNewConversation(title: String = "محادثة جديدة"): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val conv = ConversationEntity(
            title = title,
            createdAt = now,
            updatedAt = now
        )
        chatDao.insertConversation(conv)
    }

    suspend fun deleteConversation(conversationId: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteConversationById(conversationId)
    }

    suspend fun clearMessages(conversationId: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteMessagesForConversation(conversationId)
    }

    suspend fun deleteMessage(messageId: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteMessageById(messageId)
    }

    suspend fun sendMessage(
        conversationId: Long,
        userPrompt: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        // 1. Save user message to database
        val userMessage = ChatMessageEntity(
            conversationId = conversationId,
            role = "user",
            content = userPrompt.trim(),
            timestamp = System.currentTimeMillis(),
            isError = false
        )
        chatDao.insertMessage(userMessage)

        // Update conversation title if first message
        val currentConv = chatDao.getConversationById(conversationId)
        if (currentConv != null) {
            val updatedTitle = if (currentConv.title == "محادثة جديدة" || currentConv.title.isBlank()) {
                val preview = userPrompt.trim().take(35)
                if (userPrompt.length > 35) "$preview..." else preview
            } else {
                currentConv.title
            }
            chatDao.updateConversation(
                currentConv.copy(
                    title = updatedTitle,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // 2. Build multi-turn context (fetch recent history up to last 10 messages)
        val history = chatDao.getMessagesSync(conversationId)
        val contentsList = mutableListOf<ContentItem>()

        // Take last 8-10 turns to stay responsive and well within context limits
        val recentHistory = history.takeLast(10)
        for (msg in recentHistory) {
            if (!msg.isError && msg.content.isNotBlank()) {
                val apiRole = if (msg.role == "user") "user" else "model"
                contentsList.add(
                    ContentItem(
                        role = apiRole,
                        parts = listOf(PartItem(text = msg.content))
                    )
                )
            }
        }

        // Ensure the last content is the current user prompt if not already present
        if (contentsList.isEmpty() || contentsList.last().role != "user") {
            contentsList.add(
                ContentItem(
                    role = "user",
                    parts = listOf(PartItem(text = userPrompt))
                )
            )
        }

        val systemInstruction = SystemInstruction(
            parts = listOf(
                PartItem(
                    text = "You are a helpful, fast, and knowledgeable AI assistant powered by Google Gemini. " +
                            "Support both Arabic and English seamlessly. Answer in the language the user speaks. " +
                            "Keep answers clear, well-structured, easy to read, and polite. " +
                            "Use formatting like bullet points and code blocks where helpful."
                )
            )
        )

        val request = GeminiRequest(
            contents = contentsList,
            systemInstruction = systemInstruction,
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                topP = 0.95f
            )
        )

        try {
            val response = GeminiApiClient.apiService.generateContent(
                apiKey = apiKey,
                request = request
            )

            if (response.error != null) {
                val errorMessage = response.error.message ?: "خطأ من خادم Gemini"
                chatDao.insertMessage(
                    ChatMessageEntity(
                        conversationId = conversationId,
                        role = "model",
                        content = "خطأ: $errorMessage",
                        timestamp = System.currentTimeMillis(),
                        isError = true
                    )
                )
                return@withContext Result.failure(Exception(errorMessage))
            }

            val candidate = response.candidates?.firstOrNull()
            val textResponse = candidate?.content?.parts?.firstOrNull()?.text
                ?: "لم يتم استلام أي نص من النموذج."

            // Save model response in database
            chatDao.insertMessage(
                ChatMessageEntity(
                    conversationId = conversationId,
                    role = "model",
                    content = textResponse,
                    timestamp = System.currentTimeMillis(),
                    isError = false
                )
            )

            Result.success(textResponse)
        } catch (e: Exception) {
            val errorText = e.localizedMessage ?: "فشل الاتصال بالإنترنت أو تعذر الوصول إلى Gemini API"
            chatDao.insertMessage(
                ChatMessageEntity(
                    conversationId = conversationId,
                    role = "model",
                    content = "خطأ في الاتصال: $errorText\n\nتأكد من صحة مفتاح Gemini API والاتصال بالإنترنت.",
                    timestamp = System.currentTimeMillis(),
                    isError = true
                )
            )
            Result.failure(e)
        }
    }
}
