package com.netlock.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainDao {

    @Query("SELECT * FROM domains WHERE listType = :type ORDER BY domain ASC")
    fun observeByType(type: ListType): Flow<List<DomainEntity>>

    @Query("SELECT domain FROM domains WHERE listType = :type")
    suspend fun getDomainsByType(type: ListType): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DomainEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<DomainEntity>)

    @Delete
    suspend fun delete(entity: DomainEntity)

    @Query("DELETE FROM domains WHERE listType = :type")
    suspend fun clearType(type: ListType)
}
