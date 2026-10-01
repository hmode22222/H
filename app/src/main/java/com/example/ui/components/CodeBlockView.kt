package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class FormattedContentBlock {
    data class TextBlock(val text: String) : FormattedContentBlock()
    data class CodeBlock(val language: String, val code: String, val filename: String? = null) : FormattedContentBlock()
}

/**
 * Parses markdown into sequential Text and Code blocks.
 */
object MarkdownCodeParser {
    fun parse(content: String): List<FormattedContentBlock> {
        val blocks = mutableListOf<FormattedContentBlock>()
        val codeFenceRegex = Regex("```([a-zA-Z0-9_-]*)\\s*\\n?([\\s\\S]*?)```")
        var lastIndex = 0

        codeFenceRegex.findAll(content).forEach { matchResult ->
            val startIndex = matchResult.range.first
            if (startIndex > lastIndex) {
                val textPart = content.substring(lastIndex, startIndex).trim()
                if (textPart.isNotBlank()) {
                    blocks.add(FormattedContentBlock.TextBlock(textPart))
                }
            }

            val lang = matchResult.groupValues[1].ifBlank { "code" }
            val rawCode = matchResult.groupValues[2].trimEnd()

            // Try to extract filename if first line has // File: or # File:
            var detectedFileName: String? = null
            val firstLine = rawCode.lines().firstOrNull()?.trim() ?: ""
            if (firstLine.startsWith("// File:") || firstLine.startsWith("# File:") || firstLine.startsWith("// filename:")) {
                detectedFileName = firstLine.substringAfter(":").trim()
            }

            blocks.add(
                FormattedContentBlock.CodeBlock(
                    language = lang.uppercase(),
                    code = rawCode,
                    filename = detectedFileName
                )
            )

            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < content.length) {
            val trailingText = content.substring(lastIndex).trim()
            if (trailingText.isNotBlank()) {
                blocks.add(FormattedContentBlock.TextBlock(trailingText))
            }
        }

        if (blocks.isEmpty() && content.isNotBlank()) {
            blocks.add(FormattedContentBlock.TextBlock(content))
        }

        return blocks
    }
}

/**
 * Developer IDE style Code Block with Line Numbers, Language Badge, and Copy Button.
 */
@Composable
fun CodeBlockView(
    language: String,
    code: String,
    filename: String?,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val codeBgColor = Color(0xFF13151F)
    val headerBgColor = Color(0xFF1C1E2D)
    val lineNumberColor = Color(0xFF5B627A)
    val codeTextColor = Color(0xFFE2E8F0)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .testTag("code_block_${language.lowercase()}"),
        color = codeBgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBgColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = language.ifBlank { "CODE" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (!filename.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = filename,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Copy Button
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        isCopied = true
                        scope.launch {
                            delay(2000)
                            isCopied = false
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("copy_code_button")
                ) {
                    if (isCopied) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "تم النسخ",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "نسخ الكود",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Code Content with Line Numbers
            val lines = remember(code) { code.lines() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Line Numbers
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    lines.indices.forEach { index ->
                        Text(
                            text = "${index + 1}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = lineNumberColor
                        )
                    }
                }

                // Code lines
                Column {
                    lines.forEach { line ->
                        Text(
                            text = line.ifEmpty { " " },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = codeTextColor
                        )
                    }
                }
            }
        }
    }
}
