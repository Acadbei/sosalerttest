package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.*
import com.example.service.EmergencyService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Database & Repositories init
    private val database: AppDatabase = Room.databaseBuilder(
        application.applicationContext,
        AppDatabase::class.java,
        "sos_alert_database.db"
    ).fallbackToDestructiveMigration().build()

    private val authRepository = AuthRepository(application, database.userDao)
    private val alertRepository = AlertRepository(database.alertDao)
    private val safeZoneRepository = SafeZoneRepository(database.safeZoneDao)

    private val fusedLocationClient = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        LocationServices.getFusedLocationProviderClient(application.createAttributionContext("location_attribution"))
    } else {
        LocationServices.getFusedLocationProviderClient(application)
    }

    // State Flows
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // Database flow streams
    val alerts: StateFlow<List<AlertEntity>> = alertRepository.allAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val safeZones: StateFlow<List<SafeZoneEntity>> = safeZoneRepository.allSafeZones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _acknowledgedAlertIds = MutableStateFlow<Set<String>>(emptySet())
    val acknowledgedAlertIds: StateFlow<Set<String>> = _acknowledgedAlertIds.asStateFlow()

    val latestUnacknowledgedAlert: StateFlow<AlertEntity?> = combine(alerts, _acknowledgedAlertIds) { alertList, ackSet ->
        val currentTime = System.currentTimeMillis()
        // Auto-siren overlay triggers only for alerts created in the last 2 hours (120 minutes)
        // to prevent loud alarm audio on fresh logins with historical alert databases.
        alertList.firstOrNull { alert -> 
            !ackSet.contains(alert.alertId) && (currentTime - alert.timestamp < 120 * 60 * 1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Location States
    private val _userLocation = MutableStateFlow<Pair<Double, Double>>(Pair(41.3111, 69.2797)) // Default coordinate: center
    val userLocation: StateFlow<Pair<Double, Double>> = _userLocation.asStateFlow()

    // Nearest Safe Zone Calculation State
    private val _nearestSafeZoneResult = MutableStateFlow<ShelterDistanceResult?>(null)
    val nearestSafeZoneResult: StateFlow<ShelterDistanceResult?> = _nearestSafeZoneResult.asStateFlow()

    // Active alert being modeled on Critical Alert overlay screen
    private val _activeEmergencyAlert = MutableStateFlow<AlertEntity?>(null)
    val activeEmergencyAlert: StateFlow<AlertEntity?> = _activeEmergencyAlert.asStateFlow()

    init {
        // Initialize Firebase Sync
        FirebaseSyncManager.init(application)
        FirebaseSyncManager.startSyncing(database)

        // Load active login session
        val activeSession = authRepository.getActiveSession()
        if (activeSession != null) {
            _currentUser.value = activeSession
            val prefs = application.getSharedPreferences("sos_alert_prefs", Context.MODE_PRIVATE)
            _acknowledgedAlertIds.value = prefs.getStringSet("acknowledged_${activeSession.uid}", emptySet()) ?: emptySet()
        }

        // Listen for current user updates to reload user's specific acknowledged alert set
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    val prefs = application.getSharedPreferences("sos_alert_prefs", Context.MODE_PRIVATE)
                    _acknowledgedAlertIds.value = prefs.getStringSet("acknowledged_${user.uid}", emptySet()) ?: emptySet()
                } else {
                    _acknowledgedAlertIds.value = emptySet()
                }
            }
        }

        // Initialize prepopulation in database so there are default zones and accounts
        prepopulateDatabaseIfNeeded()

        // Sync nearest safe zone whenever coordinates or safe zone lists change
        viewModelScope.launch {
            combine(_userLocation, safeZones) { location, zones ->
                location to zones
            }.collect { (location, _) ->
                recalculateNearestSafeZone(location.first, location.second)
            }
        }

        // Listen for new incoming unacknowledged notifications
        viewModelScope.launch {
            combine(latestUnacknowledgedAlert, currentUser) { alert, user ->
                alert to user
            }.collect { (alert, user) ->
                if (alert != null && user?.role == "citizen") {
                    _activeEmergencyAlert.value = alert
                    // If service not running, trigger the Foreground Siren Service!
                    triggerForegroundService(alert)
                } else {
                    _activeEmergencyAlert.value = null
                    // Stop service if roles changed or alert is gone
                    if (user?.role != "citizen" || alert == null) {
                        stopForegroundService()
                    }
                }
            }
        }
    }

    private fun prepopulateDatabaseIfNeeded() {
        viewModelScope.launch {
            try {
                // Prepopulate 4 beautiful safe zones if db is blank
                val currentZones = database.safeZoneDao.getAllSafeZonesList()
                if (currentZones.isEmpty()) {
                    safeZoneRepository.insert("Убежище №12 (Подземное)", 41.3111, 69.2797, "Капитальный бетонный бункер гражданской обороны с фильтрацией воздуха")
                    safeZoneRepository.insert("Метро Станция Амир Тимур", 41.3144, 69.2811, "Станция глубокого заложения. Используется как бомбоубежище")
                    safeZoneRepository.insert("Укрытие Школа №110", 41.3020, 69.2680, "Подвальное помещение повышенной прочности, запасы воды и медикаментов")
                    safeZoneRepository.insert("Безопасный пункт Парк ЦУМ", 41.3101, 69.2715, "Открытая площадка, свободная от угрозы обрушения зданий")
                    Log.d("MainViewModel", "База данных успешно предзаполнена укрытиями")
                }

                // Verify pre-populated admin/citizens accounts are inserted
                // This ensures accounts are ready in users table even offline
                database.userDao.insertUser(UserEntity("admin-uid", "admin@sos.ru", "admin"))
                database.userDao.insertUser(UserEntity("citizen-uid", "citizen@sos.ru", "citizen"))
            } catch (e: Exception) {
                Log.e("MainViewModel", "Ошибка заполнения укрытий", e)
            }
        }
    }

    fun clearMessages() {
        _authError.value = null
        _successMessage.value = null
    }

    // AUTH ACTIONS
    fun login(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _authError.value = null
            authRepository.login(email, password)
                .onSuccess { user ->
                    _currentUser.value = user
                    onSuccess()
                }
                .onFailure { exception ->
                    _authError.value = exception.localizedMessage ?: "Ошибка авторизации"
                }
        }
    }

    fun register(email: String, password: String, role: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _authError.value = null
            authRepository.register(email, password, role)
                .onSuccess { user ->
                    _currentUser.value = user
                    onSuccess()
                }
                .onFailure { exception ->
                    _authError.value = exception.localizedMessage ?: "Ошибка регистрации"
                }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            _currentUser.value = null
            onSuccess()
        }
    }

    // ACTIONS FOR OPERATOR / ADMIN
    fun triggerAlert(title: String, instructions: String, priority: String) {
        viewModelScope.launch {
            _successMessage.value = null
            try {
                // Saves to alerts DB. The flow observer will catch it and activate full-screen alert automatically immediately!
                val alert = alertRepository.createAlert(title, instructions, priority)
                _successMessage.value = "Сигал тревоги \"${alert.title}\" успешно отправлен гражданам!"
            } catch (e: Exception) {
                _authError.value = "Ошибка при отправке сигнала: ${e.localizedMessage}"
            }
        }
    }

    fun deleteAlert(alertId: String) {
        viewModelScope.launch {
            try {
                alertRepository.delete(alertId)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Ошибка удаления сигнала", e)
            }
        }
    }

    fun addSafeZone(name: String, latitude: Double, longitude: Double, description: String) {
        viewModelScope.launch {
            _successMessage.value = null
            try {
                safeZoneRepository.insert(name, latitude, longitude, description)
                _successMessage.value = "Убежище \"$name\" добавлено в систему"
            } catch (e: Exception) {
                _authError.value = "Ошибка при создании зоны: ${e.localizedMessage}"
            }
        }
    }

    fun deleteSafeZone(zoneId: String) {
        viewModelScope.launch {
            try {
                safeZoneRepository.delete(zoneId)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Ошибка удаления зоны", e)
            }
        }
    }

    // ACTION FOR CITIZENS
    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            try {
                val user = _currentUser.value
                if (user != null) {
                    val prefs = getApplication<Application>().getSharedPreferences("sos_alert_prefs", Context.MODE_PRIVATE)
                    val newSet = _acknowledgedAlertIds.value.toMutableSet().apply { add(alertId) }
                    prefs.edit().putStringSet("acknowledged_${user.uid}", newSet).apply()
                    _acknowledgedAlertIds.value = newSet
                }

                alertRepository.acknowledge(alertId)
                // Stop the siren sound service!
                val intent = Intent(getApplication(), EmergencyService::class.java).apply {
                    action = EmergencyService.ACTION_STOP_ALARM
                }
                getApplication<Application>().startService(intent)
                _activeEmergencyAlert.value = null
                Log.d("MainViewModel", "Сигнал $alertId успешно подтвержден пользователем")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Ошибка подтверждения сигнала", e)
            }
        }
    }

    fun acknowledgeAll() {
        viewModelScope.launch {
            try {
                val user = _currentUser.value
                if (user != null) {
                    val prefs = getApplication<Application>().getSharedPreferences("sos_alert_prefs", Context.MODE_PRIVATE)
                    val allIds = alerts.value.map { it.alertId }.toSet()
                    val newSet = _acknowledgedAlertIds.value.toMutableSet().apply { addAll(allIds) }
                    prefs.edit().putStringSet("acknowledged_${user.uid}", newSet).apply()
                    _acknowledgedAlertIds.value = newSet
                }

                alertRepository.acknowledgeAll()
                val intent = Intent(getApplication(), EmergencyService::class.java).apply {
                    action = EmergencyService.ACTION_STOP_ALARM
                }
                getApplication<Application>().startService(intent)
                _activeEmergencyAlert.value = null
            } catch (e: Exception) {
                Log.e("MainViewModel", "Ошибка подтверждения всех сигналов", e)
            }
        }
    }

    // LOCATION & DISTANCE RE-TRACKING
    fun fetchUserLocation() {
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    _userLocation.value = Pair(location.latitude, location.longitude)
                    recalculateNearestSafeZone(location.latitude, location.longitude)
                    Log.d("MainViewModel", "Координаты получены: ${location.latitude}, ${location.longitude}")
                } else {
                    Log.d("MainViewModel", "Не удалось получить точные координаты. Используются координаты по умолчанию.")
                }
            }.addOnFailureListener { e ->
                Log.e("MainViewModel", "Ошибка получения геолокации", e)
            }
        } catch (e: SecurityException) {
            Log.e("MainViewModel", "Разрешения на геолокацию отсутствуют", e)
        }
    }

    private fun recalculateNearestSafeZone(lat: Double, lon: Double) {
        viewModelScope.launch {
            val result = safeZoneRepository.getNearestSafeZone(lat, lon)
            _nearestSafeZoneResult.value = result
        }
    }

    // FORWARD SERVICE LAUNCHER
    private fun triggerForegroundService(alert: AlertEntity) {
        try {
            val context = getApplication<Application>()
            val serviceIntent = Intent(context, EmergencyService::class.java).apply {
                putExtra(EmergencyService.EXTRA_TITLE, alert.title)
                putExtra(EmergencyService.EXTRA_INSTRUCTIONS, alert.instructions)
                putExtra(EmergencyService.EXTRA_PRIORITY, alert.priority)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d("MainViewModel", "Foreground служба сирены запущена успешно")
        } catch (e: Exception) {
            Log.e("MainViewModel", "Не удалось запустить Foreground службу сирены", e)
        }
    }

    fun stopForegroundService() {
        try {
            val intent = Intent(getApplication(), EmergencyService::class.java).apply {
                action = EmergencyService.ACTION_STOP_ALARM
            }
            getApplication<Application>().startService(intent)
            Log.d("MainViewModel", "Служба сирены остановлена")
        } catch (e: Exception) {
            Log.e("MainViewModel", "Ошибка при остановке службы сирены", e)
        }
    }
}
