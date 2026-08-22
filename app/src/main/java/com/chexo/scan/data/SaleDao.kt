package com.chexo.scan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SaleDao {
    @Insert
    suspend fun insert(sale: Sale)

    @Insert
    suspend fun insertAll(sales: List<Sale>)

    // Today's total sales amount
    @Query("SELECT COALESCE(SUM(totalAmount), 0) FROM sales WHERE username = :username AND soldAt >= :startOfDay")
    suspend fun getTodaySales(username: String, startOfDay: Long): Double

    // Today's profit
    @Query("SELECT COALESCE(SUM((unitPrice - ourPrice) * quantitySold), 0) FROM sales WHERE username = :username AND soldAt >= :startOfDay")
    suspend fun getTodayProfit(username: String, startOfDay: Long): Double

    // Total products sold today
    @Query("SELECT COALESCE(SUM(quantitySold), 0) FROM sales WHERE username = :username AND soldAt >= :startOfDay")
    suspend fun getTodayProductsSold(username: String, startOfDay: Long): Int

    // All sales for a date range
    @Query("SELECT * FROM sales WHERE username = :username AND soldAt BETWEEN :from AND :to ORDER BY soldAt DESC")
    suspend fun getSalesBetween(username: String, from: Long, to: Long): List<Sale>

    // All sales for a specific product
    @Query("SELECT * FROM sales WHERE username = :username AND productName = :productName AND soldAt BETWEEN :from AND :to ORDER BY soldAt DESC")
    suspend fun getSalesByProductBetween(username: String, productName: String, from: Long, to: Long): List<Sale>

    // All sales for username
    @Query("SELECT * FROM sales WHERE username = :username ORDER BY soldAt DESC")
    suspend fun getAllForUser(username: String): List<Sale>

    @Query("DELETE FROM sales WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Distinct product names sold by user
    @Query("SELECT DISTINCT productName FROM sales WHERE username = :username ORDER BY productName")
    suspend fun getDistinctProductNames(username: String): List<String>
}
