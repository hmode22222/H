package com.example.data.repository

import com.example.data.api.CodingAgentMode
import com.example.data.api.ContentItem
import com.example.data.api.GeminiApiClient
import com.example.data.api.GeminiModelRegistry
import com.example.data.api.GeminiRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.PartItem
import com.example.data.api.SystemInstruction
import com.example.data.api.ThinkingConfig
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

    suspend fun createNewConversation(title: String = "مشروع برمجي جديد"): Long = withContext(Dispatchers.IO) {
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
        apiKey: String,
        modelId: String = GeminiModelRegistry.FLAGSHIP_CODE_MODEL,
        agentMode: CodingAgentMode = CodingAgentMode.FULL_STACK_ARCHITECT,
        temperature: Float = 0.2f
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
            val updatedTitle = if (currentConv.title == "مشروع برمجي جديد" || currentConv.title == "محادثة جديدة" || currentConv.title.isBlank()) {
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

        // Manus Programming Agent System Directive
        val manusSystemPrompt = buildString {
            appendLine("You are Manus Code Agent, an autonomous software engineering AI expert and technical architect, powered by Google Gemini.")
            appendLine("Your mission: Deliver complete, production-grade, bug-free software solutions.")
            appendLine("Active Specialization Directive: ${agentMode.systemPromptDirective}")
            appendLine()
            appendLine("Follow the Manus Engineering Standard for every answer:")
            appendLine("1. [PLAN]: Briefly state your architectural roadmap or diagnosis before code.")
            appendLine("2. [CODE / IMPLEMENTATION]: Write modular, robust, modern, production-grade code.")
            appendLine("   - Always enclose code in fenced markdown blocks with explicit language tags (e.g., ```kotlin, ```python, ```typescript, ```sql, ```bash).")
            appendLine("   - Include file path comments at the top of each code block (e.g. `// File: app/src/...`).")
            appendLine("   - Avoid placeholders like `// TODO: write code here`. Implement the actual required logic.")
            appendLine("3. [VERIFICATION / SUMMARY]: Outline edge-cases handled, time/space complexity, or how to run and test.")
            appendLine("Answer fluently in Arabic or English according to the user's prompt language.")
        }

        val systemInstruction = SystemInstruction(
            parts = listOf(PartItem(text = manusSystemPrompt))
        )

        // Thinking config is supported on reasoning models like gemini-3.1-pro-preview
        val thinkingConfig = if (modelId.contains("pro") || modelId == GeminiModelRegistry.FLAGSHIP_CODE_MODEL) {
            ThinkingConfig(thinkingLevel = "low")
        } else {
            null
        }

        val request = GeminiRequest(
            contents = contentsList,
            systemInstruction = systemInstruction,
            generationConfig = GenerationConfig(
                temperature = temperature,
                topP = 0.95f,
                topK = 40,
                thinkingConfig = thinkingConfig
            )
        )

        try {
            val response = GeminiApiClient.apiService.generateContent(
                model = modelId,
                apiKey = apiKey,
                request = request
            )

            if (response.error != null) {
                val errorMessage = response.error.message ?: "خطأ من خادم Gemini"
                chatDao.insertMessage(
                    ChatMessageEntity(
                        conversationId = conversationId,
                        role = "model",
                        content = "❌ خطأ في المعالجة البرمجية:\n$errorMessage",
                        timestamp = System.currentTimeMillis(),
                        isError = true
                    )
                )
                return@withContext Result.failure(Exception(errorMessage))
            }

            val candidate = response.candidates?.firstOrNull()
            val textResponse = candidate?.content?.parts?.firstOrNull()?.text
                ?: "لم يتم استلام أي كود أو رد من النموذج."

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
                    content = "❌ خطأ في الاتصال بنموذج $modelId:\n$errorText\n\nتأكد من صحة مفتاح Gemini API والاتصال بالإنترنت.",
                    timestamp = System.currentTimeMillis(),
                    isError = true
                )
            )
            Result.failure(e)
        }
    }
}
