package com.chexo.scan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocalProductDao {
    @Insert
    suspend fun insert(product: LocalProduct)

    @Query("SELECT * FROM local_products WHERE username = :username ORDER BY timestamp DESC")
    suspend fun getAllForUser(username: String): List<LocalProduct>

    @Query("DELETE FROM local_products WHERE id = :id")
    suspend fun deleteById(id: Long)
}
