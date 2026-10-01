package com.example.ui.components

import android.content.Intent
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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

            // Extract file path from first comment line if available
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
 * Syntax Highlighting Engine for CodeBlocks.
 */
object SyntaxHighlighter {
    private val KEYWORDS = setOf(
        "val", "var", "fun", "class", "interface", "object", "enum", "data", "sealed",
        "import", "package", "return", "if", "else", "when", "for", "while", "do",
        "try", "catch", "finally", "throw", "null", "true", "false", "this", "super",
        "public", "private", "protected", "internal", "override", "abstract", "final",
        "suspend", "async", "await", "def", "const", "let", "function", "type", "from",
        "SELECT", "FROM", "WHERE", "JOIN", "INSERT", "UPDATE", "DELETE", "GROUP", "BY",
        "ORDER", "LIMIT", "TABLE", "CREATE", "ALTER", "DROP", "fn", "mut", "pub", "impl"
    )

    private val TYPES = setOf(
        "String", "Int", "Long", "Double", "Float", "Boolean", "List", "Map", "Set",
        "Flow", "StateFlow", "SharedFlow", "CoroutineScope", "ViewModel", "Composable",
        "Modifier", "Unit", "Any", "Result", "Option", "Promise", "Future"
    )

    fun highlight(line: String, isDiff: Boolean): AnnotatedString {
        if (isDiff) {
            val trimmed = line.trimStart()
            return when {
                trimmed.startsWith("+") -> buildAnnotatedString {
                    append(line)
                    addStyle(SpanStyle(color = Color(0xFF34D399), fontWeight = FontWeight.SemiBold), 0, line.length)
                }
                trimmed.startsWith("-") -> buildAnnotatedString {
                    append(line)
                    addStyle(SpanStyle(color = Color(0xFFF87171), fontWeight = FontWeight.SemiBold), 0, line.length)
                }
                trimmed.startsWith("@@") -> buildAnnotatedString {
                    append(line)
                    addStyle(SpanStyle(color = Color(0xFF818CF8)), 0, line.length)
                }
                else -> AnnotatedString(line)
            }
        }

        // Standard Code syntax highlighting
        val trimmed = line.trimStart()
        if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
            return buildAnnotatedString {
                append(line)
                addStyle(SpanStyle(color = Color(0xFF64748B)), 0, line.length)
            }
        }

        return buildAnnotatedString {
            append(line)

            // Tokenize words
            val regex = Regex("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b")
            regex.findAll(line).forEach { match ->
                val word = match.value
                val start = match.range.first
                val end = match.range.last + 1
                if (word in KEYWORDS) {
                    addStyle(SpanStyle(color = Color(0xFF818CF8), fontWeight = FontWeight.SemiBold), start, end)
                } else if (word in TYPES || (word.startsWith("@") || word.first().isUpperCase())) {
                    addStyle(SpanStyle(color = Color(0xFF38BDF8)), start, end)
                }
            }

            // String highlighting
            val stringRegex = Regex("\"[^\"]*\"|'[^']*'")
            stringRegex.findAll(line).forEach { match ->
                addStyle(SpanStyle(color = Color(0xFF34D399)), match.range.first, match.range.last + 1)
            }

            // Number highlighting
            val numberRegex = Regex("\\b\\d+(\\.\\d+)?f?\\b")
            numberRegex.findAll(line).forEach { match ->
                addStyle(SpanStyle(color = Color(0xFFFBBF24)), match.range.first, match.range.last + 1)
            }
        }
    }
}

/**
 * Developer IDE style Code Block with Syntax Highlighting, Line Numbers, Copy, Share, and Save to Library.
 */
@Composable
fun CodeBlockView(
    language: String,
    code: String,
    filename: String?,
    onSaveToLibrary: ((title: String, lang: String, code: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isDiff = language.equals("DIFF", ignoreCase = true)
    val codeBgColor = Color(0xFF0F111A)
    val headerBgColor = Color(0xFF181B26)
    val lineNumberColor = Color(0xFF475569)

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
                            .background(
                                if (isDiff) Color(0xFF10B981).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = language.ifBlank { "CODE" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDiff) Color(0xFF34D399) else MaterialTheme.colorScheme.primary,
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

                // Actions: Save to Library, Share, Copy
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Save to Library Button
                    if (onSaveToLibrary != null) {
                        IconButton(
                            onClick = {
                                val title = filename ?: "Snippet (${language.uppercase()})"
                                onSaveToLibrary(title, language, code)
                                isSaved = true
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("save_snippet_button")
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "حفظ في المكتبة",
                                tint = if (isSaved) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Share Button
                    IconButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, code)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "مشاركة الكود"))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "مشاركة",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(15.dp)
                        )
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
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "تم النسخ",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
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
            }

            // Code Content with Line Numbers & Syntax Highlighting
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

                // Highlighted Code lines
                Column {
                    lines.forEach { rawLine ->
                        val highlightedLine = remember(rawLine) {
                            SyntaxHighlighter.highlight(rawLine, isDiff)
                        }
                        Text(
                            text = if (highlightedLine.isEmpty()) AnnotatedString(" ") else highlightedLine,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }
    }
}
