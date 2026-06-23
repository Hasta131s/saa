package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hits")
data class Hit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val email: String,
    val password: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "valid",
    val apiResponse: String = ""
)
