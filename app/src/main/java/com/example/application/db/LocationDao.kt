package com.example.application.db

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface LocationDao {
    @Insert
    suspend fun insert(location: LocationEntity)
}