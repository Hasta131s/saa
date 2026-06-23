package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HitDao {
    @Query("SELECT * FROM hits ORDER BY timestamp DESC")
    fun getAllHitsFlow(): Flow<List<Hit>>

    @Query("SELECT * FROM hits ORDER BY timestamp DESC")
    suspend fun getAllHits(): List<Hit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHit(hit: Hit)

    @Query("DELETE FROM hits WHERE id = :id")
    suspend fun deleteHitById(id: Int)

    @Query("DELETE FROM hits")
    suspend fun deleteAllHits()
}
