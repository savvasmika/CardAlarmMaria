package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ShiftDay
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object για τα ωράρια εργασίας της Μαρίας.
 */
@Dao
interface ShiftDao {

    @Query("SELECT * FROM shift_days ORDER BY dateKey ASC")
    fun getAllShiftsFlow(): Flow<List<ShiftDay>>

    @Query("SELECT * FROM shift_days WHERE monthKey = :monthKey ORDER BY dateKey ASC")
    fun getShiftsForMonthFlow(monthKey: String): Flow<List<ShiftDay>>

    @Query("SELECT DISTINCT monthKey FROM shift_days ORDER BY monthKey ASC")
    fun getAllMonthKeysFlow(): Flow<List<String>>

    @Query("SELECT * FROM shift_days ORDER BY dateKey ASC")
    suspend fun getAllShifts(): List<ShiftDay>

    @Query("SELECT * FROM shift_days WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getShiftForDate(dateKey: String): ShiftDay?

    @Query("SELECT * FROM shift_days WHERE dateKey >= :currentDate ORDER BY dateKey ASC LIMIT :limit")
    suspend fun getUpcomingShifts(currentDate: String, limit: Int = 10): List<ShiftDay>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShifts(shifts: List<ShiftDay>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateShift(shift: ShiftDay)

    @Update
    suspend fun updateShift(shift: ShiftDay)

    @Query("DELETE FROM shift_days WHERE dateKey = :dateKey")
    suspend fun deleteShift(dateKey: String)

    @Query("DELETE FROM shift_days")
    suspend fun deleteAllShifts()
}
