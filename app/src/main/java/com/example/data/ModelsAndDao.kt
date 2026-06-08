package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val email: String,
    val role: String // "citizen", "operator", "admin"
)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val alertId: String,
    val title: String,
    val instructions: String,
    val priority: String, // "CRITICAL", "HIGH", "NORMAL"
    val timestamp: Long,
    val isAcknowledged: Boolean = false
)

@Entity(tableName = "safe_zones")
data class SafeZoneEntity(
    @PrimaryKey val zoneId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val description: String
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun getUserById(uid: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE alertId = :alertId LIMIT 1")
    suspend fun getAlertById(alertId: String): AlertEntity?

    @Query("SELECT * FROM alerts WHERE isAcknowledged = 0 ORDER BY timestamp DESC LIMIT 1")
    fun getLatestUnacknowledgedAlert(): Flow<AlertEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Query("UPDATE alerts SET isAcknowledged = 1 WHERE alertId = :alertId")
    suspend fun acknowledgeAlert(alertId: String)

    @Query("UPDATE alerts SET isAcknowledged = 1")
    suspend fun acknowledgeAllAlerts()

    @Query("DELETE FROM alerts WHERE alertId = :alertId")
    suspend fun deleteAlert(alertId: String)
}

@Dao
interface SafeZoneDao {
    @Query("SELECT * FROM safe_zones")
    fun getAllSafeZones(): Flow<List<SafeZoneEntity>>

    @Query("SELECT * FROM safe_zones")
    suspend fun getAllSafeZonesList(): List<SafeZoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSafeZone(safeZone: SafeZoneEntity)

    @Query("DELETE FROM safe_zones WHERE zoneId = :zoneId")
    suspend fun deleteSafeZone(zoneId: String)
}

@Database(entities = [UserEntity::class, AlertEntity::class, SafeZoneEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract val userDao: UserDao
    abstract val alertDao: AlertDao
    abstract val safeZoneDao: SafeZoneDao
}
