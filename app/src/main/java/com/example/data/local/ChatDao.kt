package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: Long): ConversationEntity?

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesSync(conversationId: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Long)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("UPDATE chat_messages SET content = :content, isError = :isError WHERE id = :id")
    suspend fun updateMessage(id: Long, content: String, isError: Boolean)

    // Saved Snippets
    @Query("SELECT * FROM saved_snippets ORDER BY timestamp DESC")
    fun getAllSnippets(): Flow<List<SavedSnippetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: SavedSnippetEntity): Long

    @Query("DELETE FROM saved_snippets WHERE id = :id")
    suspend fun deleteSnippetById(id: Long)

    @Query("SELECT * FROM saved_snippets WHERE title LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%' OR language LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchSnippets(query: String): Flow<List<SavedSnippetEntity>>
}
