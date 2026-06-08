package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class AuthRepository(private val context: Context, private val userDao: UserDao) {
    private val prefs = context.getSharedPreferences("sos_alert_prefs", Context.MODE_PRIVATE)

    suspend fun login(email: String, password: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val normalizedEmail = email.trim().lowercase()
            // Check pre-populated static logins to make testing extremely accessible and functional
            val matchedUser = when (normalizedEmail) {
                "admin@sos.ru" -> {
                    if (password == "password") UserEntity("admin-uid", "admin@sos.ru", "admin") else null
                }
                "citizen@sos.ru" -> {
                    if (password == "password") UserEntity("citizen-uid", "citizen@sos.ru", "citizen") else null
                }
                else -> {
                    // Query database for custom registered users
                    val dbUser = userDao.getUserByEmail(normalizedEmail)
                    if (dbUser != null && password.length >= 6) {
                        dbUser
                    } else null
                }
            }

            if (matchedUser != null) {
                // Save session in database to ensure it persists
                userDao.insertUser(matchedUser)
                saveSession(matchedUser.uid, matchedUser.email, matchedUser.role)
                Result.success(matchedUser)
            } else {
                Result.failure(Exception("Неверный email или пароль. Пароль должен быть не менее 6 символов."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String, role: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val normalizedEmail = email.trim().lowercase()
            if (password.length < 6) {
                return@withContext Result.failure(Exception("Пароль должен содержать минимум 6 символов."))
            }
            if (userDao.getUserByEmail(normalizedEmail) != null || 
                normalizedEmail == "admin@sos.ru" || normalizedEmail == "citizen@sos.ru") {
                return@withContext Result.failure(Exception("Пользователь с таким email уже зарегистрирован."))
            }

            val uid = UUID.randomUUID().toString()
            val newUser = UserEntity(uid, normalizedEmail, role)
            userDao.insertUser(newUser)
            FirebaseSyncManager.publishUser(newUser)
            saveSession(uid, normalizedEmail, role)
            Result.success(newUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getActiveSession(): UserEntity? {
        val uid = prefs.getString("session_uid", null) ?: return null
        val email = prefs.getString("session_email", "") ?: ""
        val role = prefs.getString("session_role", "citizen") ?: "citizen"
        return UserEntity(uid, email, role)
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    private fun saveSession(uid: String, email: String, role: String) {
        prefs.edit()
            .putString("session_uid", uid)
            .putString("session_email", email)
            .putString("session_role", role)
            .apply()
    }
}

class AlertRepository(private val alertDao: AlertDao) {
    val allAlerts: Flow<List<AlertEntity>> = alertDao.getAllAlerts()
    val latestUnacknowledgedAlert: Flow<AlertEntity?> = alertDao.getLatestUnacknowledgedAlert()

    suspend fun createAlert(title: String, instructions: String, priority: String): AlertEntity = withContext(Dispatchers.IO) {
        val alert = AlertEntity(
            alertId = UUID.randomUUID().toString(),
            title = title,
            instructions = instructions,
            priority = priority,
            timestamp = System.currentTimeMillis()
        )
        alertDao.insertAlert(alert)
        FirebaseSyncManager.publishAlert(alert)
        Log.d("AlertRepository", "Создано экстренное оповещение: $title")
        alert
    }

    suspend fun acknowledge(alertId: String) = withContext(Dispatchers.IO) {
        alertDao.acknowledgeAlert(alertId)
    }

    suspend fun acknowledgeAll() = withContext(Dispatchers.IO) {
        alertDao.acknowledgeAllAlerts()
    }

    suspend fun delete(alertId: String) = withContext(Dispatchers.IO) {
        alertDao.deleteAlert(alertId)
        FirebaseSyncManager.deleteAlert(alertId)
    }
}

data class ShelterDistanceResult(
    val safeZone: SafeZoneEntity,
    val distanceKm: Double,
    val estMinutes: Int
)

class SafeZoneRepository(private val safeZoneDao: SafeZoneDao) {
    val allSafeZones: Flow<List<SafeZoneEntity>> = safeZoneDao.getAllSafeZones()

    suspend fun insert(name: String, latitude: Double, longitude: Double, description: String) = withContext(Dispatchers.IO) {
        val zone = SafeZoneEntity(
            zoneId = UUID.randomUUID().toString(),
            name = name,
            latitude = latitude,
            longitude = longitude,
            description = description
        )
        safeZoneDao.insertSafeZone(zone)
        FirebaseSyncManager.publishSafeZone(zone)
    }

    suspend fun delete(zoneId: String) = withContext(Dispatchers.IO) {
        safeZoneDao.deleteSafeZone(zoneId)
        FirebaseSyncManager.deleteSafeZone(zoneId)
    }

    suspend fun getNearestSafeZone(userLat: Double, userLon: Double): ShelterDistanceResult? = withContext(Dispatchers.IO) {
        val zones = safeZoneDao.getAllSafeZonesList()
        if (zones.isEmpty()) return@withContext null

        var nearest: SafeZoneEntity? = null
        var minDistance = Double.MAX_VALUE

        for (zone in zones) {
            val dist = calculateHaversineDistance(userLat, userLon, zone.latitude, zone.longitude)
            if (dist < minDistance) {
                minDistance = dist
                nearest = zone
            }
        }

        if (nearest != null) {
            // Assume walking speed of 5 km/h -> time = distance / 5 hours * 60 minutes
            val estMinutes = Math.max(1, (minDistance / 5.0 * 60.0).toInt())
            ShelterDistanceResult(nearest, minDistance, estMinutes)
        } else {
            null
        }
    }

    // Haversine formula
    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
