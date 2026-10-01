package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class QuickCodingPrompt(
    val title: String,
    val icon: String,
    val promptTemplate: String
)

val CODING_QUICK_ACTIONS = listOf(
    QuickCodingPrompt(
        title = "تصحيح خطأ",
        icon = "🐞",
        promptTemplate = "إليك الخطأ التالي في تطبيقي، قم بتحليل السبب الجذري واقتراح الحل والترقيع:\n```\n[ضع الخطأ أو StackTrace هنا]\n```"
    ),
    QuickCodingPrompt(
        title = "تحسين وإعادة صياغة",
        icon = "⚡",
        promptTemplate = "قم بإعادة صياغة الكود التالي لتحسين الأداء وتقليل تعقيد Big-O وتطبيق مبادئ Clean Code:\n```\n[ضع الكود هنا]\n```"
    ),
    QuickCodingPrompt(
        title = "كتابة اختبارات",
        icon = "🧪",
        promptTemplate = "اكتب مجموعة اختبارات unit tests واختبارات edge cases شاملة للكود التالي:\n```\n[ضع الكود هنا]\n```"
    ),
    QuickCodingPrompt(
        title = "هندسة مشروع متكامل",
        icon = "🏗️",
        promptTemplate = "صمم معمارية تطبيق متكامل (Clean Architecture / MVVM) لـ [اسم الفكرة] مع ذكر هيكل الملفات والمكتبات وكود المكونات الأساسية."
    ),
    QuickCodingPrompt(
        title = "تصميم Schema وقاعدة بيانات",
        icon = "🔌",
        promptTemplate = "صمم مخطط قاعدة بيانات Room و SQL متقدم مع الـ Entities والـ Relations والـ DAOs لـ [اسم النظام]."
    ),
    QuickCodingPrompt(
        title = "واجهة Jetpack Compose",
        icon = "📱",
        promptTemplate = "اكتب واجهة مستخدم كاملة وحديثة باستخدام Jetpack Compose و Material 3 لـ [وصف الشاشة]."
    )
)

@Composable
fun CodingActionBar(
    onPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CODING_QUICK_ACTIONS.forEach { action ->
            FilterChip(
                selected = false,
                onClick = { onPromptSelected(action.promptTemplate) },
                label = {
                    Text(
                        text = "${action.icon} ${action.title}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("quick_action_${action.title}")
            )
        }
    }
}
