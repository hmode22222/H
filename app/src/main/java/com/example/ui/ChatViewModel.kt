package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.CodingAgentMode
import com.example.data.api.GeminiModelRegistry
import com.example.data.local.ChatDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val currentConversationId: Long? = null,
    val isLoading: Boolean = false,
    val agentPhaseText: String? = null,
    val selectedModelId: String = GeminiModelRegistry.FLAGSHIP_CODE_MODEL,
    val selectedAgentMode: CodingAgentMode = CodingAgentMode.FULL_STACK_ARCHITECT,
    val temperature: Float = 0.2f,
    val isApiKeyDialogOpen: Boolean = false,
    val isModelSelectorOpen: Boolean = false,
    val isArtifactsOpen: Boolean = false,
    val isClearConfirmDialogOpen: Boolean = false,
    val customApiKey: String = "",
    val effectiveApiKey: String = "",
    val snackbarMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository
    private val prefs = application.getSharedPreferences("gemini_coding_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>>
    val messages: StateFlow<List<ChatMessageEntity>>

    init {
        val database = ChatDatabase.getInstance(application)
        repository = ChatRepository(database.chatDao())

        // Load custom or default API key
        val savedKey = prefs.getString("custom_gemini_api_key", "") ?: ""
        val effective = if (savedKey.isNotBlank()) savedKey else BuildConfig.GEMINI_API_KEY

        val savedModel = prefs.getString("selected_gemini_model", GeminiModelRegistry.FLAGSHIP_CODE_MODEL)
            ?: GeminiModelRegistry.FLAGSHIP_CODE_MODEL
        val savedModeStr = prefs.getString("selected_agent_mode", CodingAgentMode.FULL_STACK_ARCHITECT.name)
        val savedMode = try {
            CodingAgentMode.valueOf(savedModeStr ?: CodingAgentMode.FULL_STACK_ARCHITECT.name)
        } catch (_: Exception) {
            CodingAgentMode.FULL_STACK_ARCHITECT
        }
        val savedTemp = prefs.getFloat("coding_temperature", 0.2f)

        _uiState.value = _uiState.value.copy(
            customApiKey = savedKey,
            effectiveApiKey = effective,
            selectedModelId = savedModel,
            selectedAgentMode = savedMode,
            temperature = savedTemp
        )

        conversations = repository.allConversations.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        messages = _uiState.flatMapLatest { state ->
            val convId = state.currentConversationId
            if (convId != null) {
                repository.getMessagesForConversation(convId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Ensure at least one conversation exists or auto-select latest
        viewModelScope.launch {
            conversations.collect { convList ->
                if (_uiState.value.currentConversationId == null) {
                    if (convList.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(currentConversationId = convList.first().id)
                    } else {
                        val newId = repository.createNewConversation("مشروع Manus جديد")
                        _uiState.value = _uiState.value.copy(currentConversationId = newId)
                    }
                }
            }
        }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            val newId = repository.createNewConversation("مشروع Manus جديد")
            _uiState.value = _uiState.value.copy(currentConversationId = newId)
        }
    }

    fun selectConversation(id: Long) {
        _uiState.value = _uiState.value.copy(currentConversationId = id)
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_uiState.value.currentConversationId == id) {
                val remaining = conversations.value.filter { it.id != id }
                if (remaining.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(currentConversationId = remaining.first().id)
                } else {
                    val newId = repository.createNewConversation("مشروع Manus جديد")
                    _uiState.value = _uiState.value.copy(currentConversationId = newId)
                }
            }
        }
    }

    fun clearCurrentMessages() {
        val convId = _uiState.value.currentConversationId ?: return
        viewModelScope.launch {
            repository.clearMessages(convId)
            _uiState.value = _uiState.value.copy(isClearConfirmDialogOpen = false)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun selectModel(modelId: String) {
        prefs.edit().putString("selected_gemini_model", modelId).apply()
        _uiState.value = _uiState.value.copy(
            selectedModelId = modelId,
            snackbarMessage = "تم اختيار نموذج: ${GeminiModelRegistry.getModelById(modelId).displayName}"
        )
    }

    fun selectAgentMode(mode: CodingAgentMode) {
        prefs.edit().putString("selected_agent_mode", mode.name).apply()
        _uiState.value = _uiState.value.copy(
            selectedAgentMode = mode,
            snackbarMessage = "تم تفعيل نمط: ${mode.titleAr}"
        )
    }

    fun setTemperature(temp: Float) {
        prefs.edit().putFloat("coding_temperature", temp).apply()
        _uiState.value = _uiState.value.copy(temperature = temp)
    }

    fun setModelSelectorOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isModelSelectorOpen = open)
    }

    fun setArtifactsOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isArtifactsOpen = open)
    }

    fun sendMessage(text: String) {
        val prompt = text.trim()
        if (prompt.isBlank()) return

        val convId = _uiState.value.currentConversationId ?: return
        val key = _uiState.value.effectiveApiKey.ifBlank { BuildConfig.GEMINI_API_KEY }

        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            _uiState.value = _uiState.value.copy(
                isApiKeyDialogOpen = true,
                snackbarMessage = "يرجى إدخال مفتاح Gemini API للمتابعة"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                agentPhaseText = "🧠 [Manus] تحليل المتطلبات وتخطيط المعمارية..."
            )

            // Autonomous phase progress animation
            val phaseJob = launch {
                delay(1800)
                if (_uiState.value.isLoading) {
                    _uiState.value = _uiState.value.copy(
                        agentPhaseText = "💻 [Manus] كتابة وتدقيق الأكواد البرمجية..."
                    )
                }
                delay(2800)
                if (_uiState.value.isLoading) {
                    _uiState.value = _uiState.value.copy(
                        agentPhaseText = "🔍 [Manus] فحص الأداء وحالات الخطأ والتوثيق..."
                    )
                }
            }

            try {
                repository.sendMessage(
                    conversationId = convId,
                    userPrompt = prompt,
                    apiKey = key,
                    modelId = _uiState.value.selectedModelId,
                    agentMode = _uiState.value.selectedAgentMode,
                    temperature = _uiState.value.temperature
                )
            } finally {
                phaseJob.cancel()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    agentPhaseText = null
                )
            }
        }
    }

    fun setApiKeyDialogOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isApiKeyDialogOpen = open)
    }

    fun setClearConfirmDialogOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isClearConfirmDialogOpen = open)
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("custom_gemini_api_key", trimmed).apply()
        val effective = if (trimmed.isNotBlank()) trimmed else BuildConfig.GEMINI_API_KEY
        _uiState.value = _uiState.value.copy(
            customApiKey = trimmed,
            effectiveApiKey = effective,
            isApiKeyDialogOpen = false,
            snackbarMessage = "تم حفظ مفتاح API بنجاح"
        )
    }

    fun resetApiKey() {
        prefs.edit().remove("custom_gemini_api_key").apply()
        _uiState.value = _uiState.value.copy(
            customApiKey = "",
            effectiveApiKey = BuildConfig.GEMINI_API_KEY,
            isApiKeyDialogOpen = false,
            snackbarMessage = "تمت استعادة الإعدادات الافتراضية"
        )
    }

    fun clearSnackbarMessage() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }
}
