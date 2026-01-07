package com.example.morp_prj.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.morp_prj.data.model.User

@Dao
interface UserDao {
    // Lấy user để kiểm tra pass cũ
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): User?

    // 🟢 ĐÂY LÀ HÀM BẠN ĐANG THIẾU
    @Query("UPDATE users SET hashedPassword = :newPasswordHash WHERE id = :userId")
    suspend fun updatePassword(userId: String, newPasswordHash: String)

    // Lưu user khi login thành công
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)
}