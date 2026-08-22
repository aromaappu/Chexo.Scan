package com.chexo.scan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface LocalProductDao {
    @Insert
    suspend fun insert(product: LocalProduct)

    @Update
    suspend fun update(product: LocalProduct)

    @Query("SELECT * FROM local_products WHERE username = :username ORDER BY timestamp DESC")
    suspend fun getAllForUser(username: String): List<LocalProduct>

    @Query("SELECT * FROM local_products WHERE id = :id")
    suspend fun getById(id: Long): LocalProduct?

    @Query("SELECT * FROM local_products WHERE barcode = :barcode AND username = :username LIMIT 1")
    suspend fun getByBarcodeForUser(barcode: String, username: String): LocalProduct?

    @Query("DELETE FROM local_products WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM local_products WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
