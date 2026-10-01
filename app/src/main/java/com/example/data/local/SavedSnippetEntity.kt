package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_snippets")
data class SavedSnippetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val language: String,
    val code: String,
    val tags: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
