package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AlarmRecord
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object για την καταγραφή και διαχείριση των ενεργών alarms.
 */
@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarm_records ORDER BY triggerTimestamp ASC")
    fun getAllAlarmsFlow(): Flow<List<AlarmRecord>>

    @Query("SELECT * FROM alarm_records WHERE triggerTimestamp >= :fromTimestamp ORDER BY triggerTimestamp ASC")
    fun getPendingAlarmsFlow(fromTimestamp: Long): Flow<List<AlarmRecord>>

    @Query("SELECT * FROM alarm_records WHERE triggerTimestamp >= :fromTimestamp ORDER BY triggerTimestamp ASC")
    suspend fun getPendingAlarms(fromTimestamp: Long): List<AlarmRecord>

    @Query("SELECT * FROM alarm_records ORDER BY triggerTimestamp ASC")
    suspend fun getAllAlarms(): List<AlarmRecord>

    @Query("SELECT * FROM alarm_records WHERE dateKey = :dateKey")
    suspend fun getAlarmsForDate(dateKey: String): List<AlarmRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarms(alarms: List<AlarmRecord>): List<Long>

    @Update
    suspend fun updateAlarm(alarm: AlarmRecord)

    @Query("UPDATE alarm_records SET isFired = 1 WHERE id = :id")
    suspend fun markAlarmFired(id: Int)

    @Query("DELETE FROM alarm_records WHERE dateKey = :dateKey")
    suspend fun deleteAlarmsForDate(dateKey: String)

    @Query("DELETE FROM alarm_records WHERE id = :id")
    suspend fun deleteAlarmById(id: Int)

    @Query("DELETE FROM alarm_records")
    suspend fun deleteAllAlarms()
}
