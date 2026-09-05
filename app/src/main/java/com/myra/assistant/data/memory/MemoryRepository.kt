package com.myra.assistant.data.memory

import android.content.Context
import kotlinx.coroutines.flow.Flow

class MemoryRepository(context: Context) {

    private val memoryDao: MemoryDao = AppDatabase.getDatabase(context).memoryDao()

    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> = memoryDao.getAllMemoriesFlow()

    fun getMemoriesByCategoryFlow(category: String): Flow<List<MemoryEntity>> =
        memoryDao.getMemoriesByCategoryFlow(category)

    suspend fun getPublicMemories(): List<MemoryEntity> = memoryDao.getPublicMemories()

    suspend fun getAllMemoriesList(): List<MemoryEntity> = memoryDao.getAllMemoriesList()

    suspend fun saveMemory(
        keyName: String,
        content: String,
        category: String = "General",
        isSecure: Boolean = false
    ): Long {
        return memoryDao.insertMemory(
            MemoryEntity(
                keyName = keyName.trim(),
                content = content.trim(),
                category = category.trim(),
                isSecureVault = isSecure
            )
        )
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteById(id)
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> {
        return memoryDao.searchMemories(query)
    }
}
