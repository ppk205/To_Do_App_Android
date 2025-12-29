package com.example.morp_prj.data

import com.example.morp_prj.data.db.TaskDao
import com.example.morp_prj.data.db.TaskEntity
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val dao: TaskDao) {

    fun observeAllByUser(userId: String): Flow<List<TaskEntity>> = dao.observeAllByUser(userId)

    fun observeAllLocal(guestUserId: String): Flow<List<TaskEntity>> = dao.observeAllLocal(guestUserId)

    fun observeByDateRange(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>> =
        dao.observeByDateRange(startOfDay, endOfDay)

    fun observeByDateRangeForUser(userId: String, startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>> =
        dao.observeByDateRangeForUser(userId, startOfDay, endOfDay)

    fun observeByStatus(status: String): Flow<List<TaskEntity>> = dao.observeByStatus(status)

    fun observeByStatusForUser(userId: String, status: String): Flow<List<TaskEntity>> =
        dao.observeByStatusForUser(userId, status)

    fun observeCountByStatus(status: String): Flow<Int> = dao.observeCountByStatus(status)

    fun observeCountByStatusForUser(userId: String, status: String): Flow<Int> =
        dao.observeCountByStatusForUser(userId, status)

    suspend fun insert(entity: TaskEntity): Long = dao.insert(entity)

    suspend fun update(entity: TaskEntity) = dao.update(entity)

    suspend fun updateStatus(id: Long, status: String): Int = dao.updateStatus(id, status)

    suspend fun deleteById(id: Long): Int = dao.deleteById(id)

    suspend fun deleteByIds(ids: List<Long>): Int = dao.deleteByIds(ids)

    suspend fun deleteByUserId(userId: String): Int = dao.deleteByUserId(userId)

    suspend fun deleteAll(): Int = dao.deleteAll()
}
