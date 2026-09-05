package com.myra.assistant.data.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a persistent memory entry remembered by MYRA.
 * E.g., Phone numbers, Instagram IDs, favorite songs, family facts, passwords/pins, social handles.
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyName: String,          // e.g., "Instagram ID", "Mom's Phone", "Favorite Song"
    val content: String,          // The stored factual data
    val category: String,         // "Social", "Contact", "Music", "Personal", "Secure Vault", "General"
    val isSecureVault: Boolean = false, // Protected memory requiring PIN/authentication
    val timestamp: Long = System.currentTimeMillis()
)
