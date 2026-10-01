package com.example.data.repository

import com.example.data.api.CodingAgentMode
import com.example.data.api.ContentItem
import com.example.data.api.GeminiApiClient
import com.example.data.api.GeminiModelRegistry
import com.example.data.api.GeminiRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.InlineData
import com.example.data.api.PartItem
import com.example.data.api.SystemInstruction
import com.example.data.api.TechStack
import com.example.data.api.ThinkingConfig
import com.example.data.local.ChatDao
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.SavedSnippetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(private val chatDao: ChatDao) {

    val allConversations: Flow<List<ConversationEntity>> = chatDao.getAllConversations()
    val allSnippets: Flow<List<SavedSnippetEntity>> = chatDao.getAllSnippets()

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

    // Snippets
    suspend fun saveSnippet(title: String, language: String, code: String, tags: String = ""): Long = withContext(Dispatchers.IO) {
        chatDao.insertSnippet(
            SavedSnippetEntity(
                title = title,
                language = language,
                code = code,
                tags = tags,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteSnippet(id: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteSnippetById(id)
    }

    fun searchSnippets(query: String): Flow<List<SavedSnippetEntity>> {
        return chatDao.searchSnippets(query)
    }

    suspend fun sendMessage(
        conversationId: Long,
        userPrompt: String,
        apiKey: String,
        modelId: String = GeminiModelRegistry.FLAGSHIP_CODE_MODEL,
        agentMode: CodingAgentMode = CodingAgentMode.FULL_STACK_ARCHITECT,
        techStack: TechStack = TechStack.ANDROID_COMPOSE,
        imageBase64: String? = null,
        imageMimeType: String = "image/jpeg",
        temperature: Float = 0.2f
    ): Result<String> = withContext(Dispatchers.IO) {
        // 1. Save user message to database
        val userMessage = ChatMessageEntity(
            conversationId = conversationId,
            role = "user",
            content = userPrompt.trim(),
            timestamp = System.currentTimeMillis(),
            isError = false,
            imageUri = if (imageBase64 != null) "attached_image" else null
        )
        chatDao.insertMessage(userMessage)

        // Update conversation title if first message
        val currentConv = chatDao.getConversationById(conversationId)
        if (currentConv != null) {
            val updatedTitle = if (currentConv.title == "مشروع برمجي جديد" || currentConv.title == "مشروع Manus جديد" || currentConv.title == "محادثة جديدة" || currentConv.title.isBlank()) {
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

        // 2. Build multi-turn context (fetch recent history)
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

        // Prepare current turn parts (including image if present)
        val currentParts = mutableListOf<PartItem>()
        currentParts.add(PartItem(text = userPrompt))
        if (!imageBase64.isNullOrBlank()) {
            currentParts.add(
                PartItem(
                    inlineData = InlineData(
                        mimeType = imageMimeType,
                        data = imageBase64
                    )
                )
            )
        }

        // Replace or add current turn
        if (contentsList.isNotEmpty() && contentsList.last().role == "user") {
            contentsList[contentsList.lastIndex] = ContentItem(role = "user", parts = currentParts)
        } else {
            contentsList.add(ContentItem(role = "user", parts = currentParts))
        }

        // Manus Programming Agent System Directive
        val manusSystemPrompt = buildString {
            appendLine("You are Manus Code Agent, an autonomous senior software engineering and system architecture AI, powered by Google Gemini.")
            appendLine("Mission: Produce complete, production-grade, bug-free, idiomatic code solutions.")
            appendLine("Target Technology Stack: ${techStack.title} (${techStack.promptContext})")
            appendLine("Specialization Mode: ${agentMode.systemPromptDirective}")
            appendLine()
            appendLine("Manus Autonomous Engineering Protocol:")
            appendLine("1. [PLAN / ROADMAP]: Step-by-step technical plan and architectural breakdown.")
            appendLine("2. [CODE / IMPLEMENTATION]:")
            appendLine("   - Every file MUST be enclosed in fenced markdown blocks with language identifiers (e.g. ```kotlin, ```python, ```typescript, ```sql, ```bash).")
            appendLine("   - Place a file header at the top of each block: `// File: [path/filename]` or `# File: [path/filename]`.")
            appendLine("   - Implement full logic; NEVER leave placeholders like `// TODO: implement later`.")
            appendLine("   - When demonstrating bug fixes or refactoring, you may use ```diff blocks showing `-` removals and `+` additions.")
            appendLine("3. [VERIFICATION & SPECS]:")
            appendLine("   - State Time & Space Complexity (Big-O).")
            appendLine("   - Detail edge cases handled (nullability, concurrency, network errors).")
            appendLine("   - Provide runnable test commands or instructions.")
            appendLine("Answer fluently in Arabic or English according to the user's prompt language.")
        }

        val systemInstruction = SystemInstruction(
            parts = listOf(PartItem(text = manusSystemPrompt))
        )

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
            val textResponse = candidate?.content?.parts?.firstOrNull { it.text != null }?.text
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
