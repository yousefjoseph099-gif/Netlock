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

    // ---- Blocked-domain diagnostic log ----

    @Query("SELECT * FROM blocked_log ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecentBlocked(limit: Int = 100): Flow<List<BlockedLogEntity>>

    @Insert
    suspend fun insertBlockedLog(entry: BlockedLogEntity)

    /** Keeps the log from growing forever - called after each insert. */
    @Query(
        "DELETE FROM blocked_log WHERE id NOT IN " +
        "(SELECT id FROM blocked_log ORDER BY timestampMillis DESC LIMIT :keep)"
    )
    suspend fun trimBlockedLog(keep: Int = 200)

    @Query("DELETE FROM blocked_log")
    suspend fun clearBlockedLog()
}
