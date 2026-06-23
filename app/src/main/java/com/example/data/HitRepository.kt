package com.example.data

import kotlinx.coroutines.flow.Flow

class HitRepository(private val hitDao: HitDao) {
    val allHitsFlow: Flow<List<Hit>> = hitDao.getAllHitsFlow()

    suspend fun getAllHits(): List<Hit> = hitDao.getAllHits()

    suspend fun insert(hit: Hit) = hitDao.insertHit(hit)

    suspend fun deleteById(id: Int) = hitDao.deleteHitById(id)

    suspend fun deleteAll() = hitDao.deleteAllHits()
}
