package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @param:Json(name = "contents") val contents: List<ContentItem>,
    @param:Json(name = "systemInstruction") val systemInstruction: SystemInstruction? = null,
    @param:Json(name = "generationConfig") val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class SystemInstruction(
    @param:Json(name = "parts") val parts: List<PartItem>
)

@JsonClass(generateAdapter = true)
data class ContentItem(
    @param:Json(name = "role") val role: String? = null,
    @param:Json(name = "parts") val parts: List<PartItem>
)

@JsonClass(generateAdapter = true)
data class PartItem(
    @param:Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @param:Json(name = "temperature") val temperature: Float? = 0.2f,
    @param:Json(name = "topP") val topP: Float? = 0.95f,
    @param:Json(name = "topK") val topK: Int? = 40,
    @param:Json(name = "thinkingConfig") val thinkingConfig: ThinkingConfig? = null
)

@JsonClass(generateAdapter = true)
data class ThinkingConfig(
    @param:Json(name = "thinkingLevel") val thinkingLevel: String = "low"
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @param:Json(name = "candidates") val candidates: List<CandidateItem>? = null,
    @param:Json(name = "error") val error: GeminiApiError? = null
)

@JsonClass(generateAdapter = true)
data class CandidateItem(
    @param:Json(name = "content") val content: ContentItem? = null,
    @param:Json(name = "finishReason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiApiError(
    @param:Json(name = "code") val code: Int? = null,
    @param:Json(name = "message") val message: String? = null,
    @param:Json(name = "status") val status: String? = null
)

/**
 * All supported Gemini models adhering strictly to the official gemini-api skill.
 */
data class GeminiModelInfo(
    val id: String,
    val displayName: String,
    val tag: String,
    val description: String,
    val isDefault: Boolean = false,
    val isFlagshipCoding: Boolean = false,
    val supportsVision: Boolean = false,
    val badgeColor: Long = 0xFF6366F1
)

object GeminiModelRegistry {
    val FLAGSHIP_CODE_MODEL = "gemini-3.1-pro-preview"

    val AVAILABLE_MODELS = listOf(
        GeminiModelInfo(
            id = "gemini-3.1-pro-preview",
            displayName = "Gemini 3.1 Pro",
            tag = "Manus Flagship Engine",
            description = "أقوى نموذج للاستدلال العميق، وهندسة البرمجيات المعقدة، وحل الخوارزميات وتصحيح الأخطاء الصعبة.",
            isFlagshipCoding = true,
            isDefault = true,
            badgeColor = 0xFF6366F1
        ),
        GeminiModelInfo(
            id = "gemini-3.5-flash",
            displayName = "Gemini 3.5 Flash",
            tag = "Fast & Balanced",
            description = "نموذج سريع ومتوازن، مثالي لكتابة الأكواد اليومية، وتعديل الدوال، والردود الفورية.",
            isDefault = false,
            badgeColor = 0xFF10B981
        ),
        GeminiModelInfo(
            id = "gemini-flash-latest",
            displayName = "Gemini Flash Latest",
            tag = "Latest Flash",
            description = "أحدث إصدار من جيل Flash لتطوير التطبيقات والبرمجة العامة بسرعة استجابة عالية.",
            badgeColor = 0xFF06B6D4
        ),
        GeminiModelInfo(
            id = "gemini-3.1-flash-lite-preview",
            displayName = "Gemini 3.1 Flash Lite",
            tag = "Ultra-Fast / Low Latency",
            description = "أخف وأسرع نموذج، مخصص لشرح المصطلحات البرمجية، وتوليد مقتطفات التعليمات البرمجية الصغيرة.",
            badgeColor = 0xFFF59E0B
        ),
        GeminiModelInfo(
            id = "gemini-2.5-flash-image",
            displayName = "Gemini 2.5 Flash Vision",
            tag = "Vision & UI Analysis",
            description = "مخصص لقراءة واجهات المستخدم وتصاميم Wireframes والمخططات الهندسية وتحويلها إلى كود.",
            supportsVision = true,
            badgeColor = 0xFFEC4899
        ),
        GeminiModelInfo(
            id = "gemini-3.1-flash-image-preview",
            displayName = "Gemini 3.1 Flash Image Preview",
            tag = "High-Res Vision",
            description = "تحليل صور عالي الدقة لفحص تفاصيل التصاميم وتخطيطات الشاشات الاحترافية.",
            supportsVision = true,
            badgeColor = 0xFF8B5CF6
        )
    )

    fun getModelById(id: String): GeminiModelInfo {
        return AVAILABLE_MODELS.find { it.id == id } ?: GeminiModelInfo(
            id = id,
            displayName = id,
            tag = "Custom Model",
            description = "نموذج مخصص من المستخدم",
            badgeColor = 0xFF64748B
        )
    }
}

/**
 * Manus autonomous programming modes.
 */
enum class CodingAgentMode(
    val title: String,
    val titleAr: String,
    val icon: String,
    val descriptionAr: String,
    val systemPromptDirective: String
) {
    FULL_STACK_ARCHITECT(
        title = "Architect",
        titleAr = "مهندس معماري شامل",
        icon = "🏗️",
        descriptionAr = "تخطيط وبناء مشاريع برمجية متكاملة، هيكلة الملفات، والمكتبات المطلوبة.",
        systemPromptDirective = "Role: Full-Stack System Architect. Provide comprehensive project scaffolds, file structures, dependencies, and clean architecture implementation."
    ),
    ROOT_CAUSE_DEBUGGER(
        title = "Debugger",
        titleAr = "محلل ومصحح أخطاء",
        icon = "🐞",
        descriptionAr = "تحليل مكدس الأخطاء (Stack Traces)، تتبع السبب الجذري، وتقديم حلول وترقيعات دقيقة.",
        systemPromptDirective = "Role: Deep Debugging Specialist. Analyze error stack traces, explain root causes with surgical precision, and provide the exact bug fixes."
    ),
    CLEAN_CODE_OPTIMIZER(
        title = "Optimizer",
        titleAr = "مُحسّن ومُعاد الصياغة",
        icon = "⚡",
        descriptionAr = "إعادة صياغة الأكواد وفق مبادئ SOLID، وتحسين الأداء وBig-O وتعزيز القابلية للقراءة.",
        systemPromptDirective = "Role: Senior Refactoring & Optimization Engineer. Focus on clean code, performance benchmarks, Big-O complexity reduction, and idiomatic modern syntax."
    ),
    QA_TEST_ENGINEER(
        title = "QA & Tests",
        titleAr = "مهندس اختبارات الجودة",
        icon = "🧪",
        descriptionAr = "كتابة اختبارات شاملة: Unit tests وIntegration tests وتغطية الحالات الشاذة (Edge Cases).",
        systemPromptDirective = "Role: QA Automation & Test Architect. Write unit tests, mock dependencies, test boundary/edge conditions, and enforce robust test coverage."
    ),
    API_DATABASE_DESIGNER(
        title = "API & DB",
        titleAr = "مصمم واجهات وقواعد بيانات",
        icon = "🔌",
        descriptionAr = "تصميم واجهات REST/GraphQL، وهياكل قواعد البيانات SQL/NoSQL وRoom Entities.",
        systemPromptDirective = "Role: API & Database Architect. Design schemas, database relations, migrations, query optimizations, and RESTful API endpoints."
    )
}
