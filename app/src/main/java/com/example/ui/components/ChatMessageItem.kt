package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.ChatMessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatMessageItem(
    message: ChatMessageEntity,
    onDelete: () -> Unit,
    onSaveSnippet: ((title: String, lang: String, code: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(message.timestamp))
    var isThinkingExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(if (isUser) "user_message_item_${message.id}" else "ai_message_item_${message.id}"),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            // Manus AI Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (message.isError) MaterialTheme.colorScheme.errorContainer
                        else Color(0xFF1E1F30)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (message.isError) Icons.Default.ErrorOutline else Icons.Default.Terminal,
                    contentDescription = "Manus AI",
                    modifier = Modifier.size(20.dp),
                    tint = if (message.isError) MaterialTheme.colorScheme.error
                    else Color(0xFF60A5FA)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 580.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Header for AI
            if (!isUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = "Manus Code Agent",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }

            // Message Bubble Card
            val bubbleColor = when {
                isUser -> MaterialTheme.colorScheme.primary
                message.isError -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
            val contentColor = when {
                isUser -> MaterialTheme.colorScheme.onPrimary
                message.isError -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSurface
            }

            val bubbleShape = if (isUser) {
                RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 4.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                )
            } else {
                RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                )
            }

            Card(
                shape = bubbleShape,
                colors = CardDefaults.cardColors(
                    containerColor = bubbleColor,
                    contentColor = contentColor
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 1.dp else 0.5.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Image attachment badge if user attached an image
                    if (isUser && message.imageUri != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "تم إرفاق صورة/مخطط للتحليل",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    if (isUser) {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 22.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    } else {
                        // AI Model response: Render formatted text and syntax-highlighted code blocks
                        val parsedBlocks = remember(message.content) {
                            MarkdownCodeParser.parse(message.content)
                        }

                        parsedBlocks.forEach { block ->
                            when (block) {
                                is FormattedContentBlock.TextBlock -> {
                                    SelectionContainer {
                                        Text(
                                            text = block.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = 22.sp,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                                is FormattedContentBlock.CodeBlock -> {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    CodeBlockView(
                                        language = block.language,
                                        code = block.code,
                                        filename = block.filename,
                                        onSaveToLibrary = onSaveSnippet
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Action row below message (Copy & Delete)
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.content))
                        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("copy_button_${message.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("delete_button_${message.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // User Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
