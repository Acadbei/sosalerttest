package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

object FirebaseSyncManager {
    private var database: FirebaseDatabase? = null
    private var alertsRef: DatabaseReference? = null
    private var zonesRef: DatabaseReference? = null
    private var usersRef: DatabaseReference? = null

    private val scope = CoroutineScope(Dispatchers.IO)
    private var isSyncing = false
    private var localDatabase: AppDatabase? = null

    private val _connectionStatus = MutableStateFlow("Ожидание запуска...")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun init(context: Context) {
        if (database != null) return
        try {
            _connectionStatus.value = "Инициализация..."
            // Programmatic configuration to bypass standard google-services.json requirements
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:1252f45fdc07400f8:android:59bc73fdb89a912c")
                .setProjectId("sosalert-f9a99")
                .setDatabaseUrl("https://sosalert-f9a99-default-rtdb.firebaseio.com/")
                .build()

            val app = if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context, options)
            } else {
                FirebaseApp.getInstance()
            }

            val prefs = context.getSharedPreferences("sos_alert_prefs", Context.MODE_PRIVATE)
            val customUrl = prefs.getString("firebase_db_url", "https://sosalert-f9a99-default-rtdb.firebaseio.com/") ?: "https://sosalert-f9a99-default-rtdb.firebaseio.com/"
            Log.d("FirebaseSyncManager", "Initializing Firebase Realtime Database with URL: $customUrl")

            database = FirebaseDatabase.getInstance(app, customUrl).apply {
                try {
                    setPersistenceEnabled(true) // Build real offline-first behavior
                } catch (pe: Exception) {
                    Log.w("FirebaseSyncManager", "setPersistenceEnabled already called or not supported", pe)
                }
            }

            alertsRef = database?.getReference("alerts")
            zonesRef = database?.getReference("safe_zones")
            usersRef = database?.getReference("users")

            // Monitor connection status
            database?.getReference(".info/connected")?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    _connectionStatus.value = if (connected) "ПОДКЛЮЧЕНО ✅" else "ОТКЛЮЧЕНО (Поиск сети / Проверьте URL) ❌"
                }

                override fun onCancelled(error: DatabaseError) {
                    _connectionStatus.value = "ОШИБКА ⚠️"
                    _lastError.value = "Ошибка подключения: ${error.message} (${error.details})"
                }
            })

            Log.d("FirebaseSyncManager", "Firebase Realtime Database initialized successfully.")
        } catch (e: Exception) {
            _connectionStatus.value = "СБОЙ ИНИЦИАЛИЗАЦИИ ❌"
            _lastError.value = "Критическая ошибка: ${e.localizedMessage}"
            Log.e("FirebaseSyncManager", "Failed to initialize Firebase Realtime Database", e)
        }
    }

    fun startSyncing(databaseInstance: AppDatabase) {
        localDatabase = databaseInstance
        if (isSyncing) return
        isSyncing = true

        Log.d("FirebaseSyncManager", "Starting real-time synchronization with Firebase...")

        // 1. Sync Alerts Child Events
        alertsRef?.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch {
                    val alert = mapSnapshotToAlert(snapshot) ?: return@launch
                    databaseInstance.alertDao.insertAlert(alert)
                    Log.d("FirebaseSyncManager", "Alert added locally from Firebase: ${alert.title}")
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch {
                    val alert = mapSnapshotToAlert(snapshot) ?: return@launch
                    databaseInstance.alertDao.insertAlert(alert)
                    Log.d("FirebaseSyncManager", "Alert changed locally from Firebase: ${alert.title}")
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                scope.launch {
                    val alertId = snapshot.key ?: return@launch
                    databaseInstance.alertDao.deleteAlert(alertId)
                    Log.d("FirebaseSyncManager", "Alert deleted locally from Firebase: $alertId")
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                _lastError.value = "Ошибка Alerts: ${error.message}"
                Log.e("FirebaseSyncManager", "Alert sync cancelled: ${error.message}")
            }
        })

        // 2. Sync Safe Zones Child Events
        zonesRef?.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch {
                    val zone = mapSnapshotToSafeZone(snapshot) ?: return@launch
                    databaseInstance.safeZoneDao.insertSafeZone(zone)
                    Log.d("FirebaseSyncManager", "Safe zone added locally from Firebase: ${zone.name}")
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch {
                    val zone = mapSnapshotToSafeZone(snapshot) ?: return@launch
                    databaseInstance.safeZoneDao.insertSafeZone(zone)
                    Log.d("FirebaseSyncManager", "Safe zone changed locally from Firebase: ${zone.name}")
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                scope.launch {
                    val zoneId = snapshot.key ?: return@launch
                    databaseInstance.safeZoneDao.deleteSafeZone(zoneId)
                    Log.d("FirebaseSyncManager", "Safe zone deleted locally from Firebase: $zoneId")
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                _lastError.value = "Ошибка SafeZones: ${error.message}"
                Log.e("FirebaseSyncManager", "SafeZone sync cancelled: ${error.message}")
            }
        })

        // 3. Sync Users Child Events
        usersRef?.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch {
                    val user = mapSnapshotToUser(snapshot) ?: return@launch
                    databaseInstance.userDao.insertUser(user)
                    Log.d("FirebaseSyncManager", "Credentials sync: Added user ${user.email}")
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch {
                    val user = mapSnapshotToUser(snapshot) ?: return@launch
                    databaseInstance.userDao.insertUser(user)
                    Log.d("FirebaseSyncManager", "Credentials sync: Updated user ${user.email}")
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                _lastError.value = "Ошибка Users: ${error.message}"
                Log.e("FirebaseSyncManager", "Users sync cancelled: ${error.message}")
            }
        })
    }

    fun updateDatabaseUrl(context: Context, newUrl: String) {
        val prefs = context.getSharedPreferences("sos_alert_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("firebase_db_url", newUrl).apply()
        
        val currentDb = localDatabase
        
        database = null
        isSyncing = false
        _connectionStatus.value = "Переподключение..."
        _lastError.value = null
        
        init(context)
        if (currentDb != null) {
            startSyncing(currentDb)
        }
    }

    fun publishAlert(alert: AlertEntity) {
        scope.launch {
            alertsRef?.child(alert.alertId)?.setValue(mapOf(
                "alertId" to alert.alertId,
                "title" to alert.title,
                "instructions" to alert.instructions,
                "priority" to alert.priority,
                "timestamp" to alert.timestamp
            ))?.addOnFailureListener {
                Log.e("FirebaseSyncManager", "Failed to write alert to Firebase", it)
            }
        }
    }

    fun deleteAlert(alertId: String) {
        scope.launch {
            alertsRef?.child(alertId)?.removeValue()?.addOnFailureListener {
                Log.e("FirebaseSyncManager", "Failed to delete alert from Firebase", it)
            }
        }
    }

    fun publishSafeZone(zone: SafeZoneEntity) {
        scope.launch {
            zonesRef?.child(zone.zoneId)?.setValue(mapOf(
                "zoneId" to zone.zoneId,
                "name" to zone.name,
                "latitude" to zone.latitude,
                "longitude" to zone.longitude,
                "description" to zone.description
            ))?.addOnFailureListener {
                Log.e("FirebaseSyncManager", "Failed to write safe zone to Firebase", it)
            }
        }
    }

    fun deleteSafeZone(zoneId: String) {
        scope.launch {
            zonesRef?.child(zoneId)?.removeValue()?.addOnFailureListener {
                Log.e("FirebaseSyncManager", "Failed to delete safe zone from Firebase", it)
            }
        }
    }

    fun publishUser(user: UserEntity) {
        scope.launch {
            usersRef?.child(user.uid)?.setValue(mapOf(
                "uid" to user.uid,
                "email" to user.email,
                "role" to user.role
            ))?.addOnFailureListener {
                Log.e("FirebaseSyncManager", "Failed to write user to Firebase", it)
            }
        }
    }

    private fun mapSnapshotToAlert(snapshot: DataSnapshot): AlertEntity? {
        val alertId = snapshot.child("alertId").getValue(String::class.java) ?: snapshot.key ?: return null
        val title = snapshot.child("title").getValue(String::class.java) ?: ""
        val instructions = snapshot.child("instructions").getValue(String::class.java) ?: ""
        val priority = snapshot.child("priority").getValue(String::class.java) ?: "CRITICAL"
        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
        return AlertEntity(alertId, title, instructions, priority, timestamp)
    }

    private fun mapSnapshotToSafeZone(snapshot: DataSnapshot): SafeZoneEntity? {
        val zoneId = snapshot.child("zoneId").getValue(String::class.java) ?: snapshot.key ?: return null
        val name = snapshot.child("name").getValue(String::class.java) ?: ""
        val latitude = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
        val longitude = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
        val description = snapshot.child("description").getValue(String::class.java) ?: ""
        return SafeZoneEntity(zoneId, name, latitude, longitude, description)
    }

    private fun mapSnapshotToUser(snapshot: DataSnapshot): UserEntity? {
        val uid = snapshot.child("uid").getValue(String::class.java) ?: snapshot.key ?: return null
        val email = snapshot.child("email").getValue(String::class.java) ?: ""
        val role = snapshot.child("role").getValue(String::class.java) ?: "citizen"
        return UserEntity(uid, email, role)
    }
}
