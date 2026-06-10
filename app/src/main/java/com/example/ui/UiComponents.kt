package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.service.EmergencyService
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.data.AlertEntity
import com.example.data.SafeZoneEntity
import com.example.data.UserEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainAppContainer(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val activeEmergency by viewModel.activeEmergencyAlert.collectAsState()

    var currentScreen by remember { mutableStateOf("splash") }

    // Navigation trigger from Splash
    LaunchedEffect(currentScreen) {
        if (currentScreen == "splash") {
            kotlinx.coroutines.delay(2000)
            currentScreen = if (currentUser != null) "dashboard" else "login"
        }
    }

    // Sync screen with login/logout action
    LaunchedEffect(currentUser) {
        if (currentScreen != "splash") {
            currentScreen = if (currentUser != null) "dashboard" else "login"
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                "splash" -> SplashScreen()
                "login" -> LoginScreen(
                    viewModel = viewModel,
                    onNavigateToRegister = { currentScreen = "register" }
                )
                "register" -> RegisterScreen(
                    viewModel = viewModel,
                    onNavigateToLogin = { currentScreen = "login" }
                )
                "dashboard" -> DashboardRouter(
                    viewModel = viewModel,
                    onLogout = { currentScreen = "login" }
                )
            }

            // FULL-SCREEN EMERGENCY ALARM OVERLAY (HIGHEST Z-INDEX ON SCREEN ON TOP OF ALL SCREENS)
            activeEmergency?.let { alert ->
                if (currentUser?.role == "citizen") {
                    EmergencyOverlayScreen(
                        alert = alert,
                        viewModel = viewModel,
                        onAcknowledge = { viewModel.acknowledgeAlert(alert.alertId) }
                    )
                }
            }
        }
    }
}

// 1. SPLASH SCREEN (RU)
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB71C1C)),
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(140.dp * scale)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = "Сирена",
                    tint = Color.White,
                    modifier = Modifier.size(80.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "SOS ОПОВЕЩЕНИЕ",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Система экстренного информирования населения",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(64.dp))
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
        }
    }
}

// 2. LOGIN SCREEN (RU)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val authError by viewModel.authError.collectAsState()

    LaunchedEffect(Unit) { viewModel.clearMessages() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = "Лого",
                tint = Color(0xFFD50000),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Ogoh-Alert",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = "Авторизация в системе оповещения",
                fontSize = 14.sp,
                color = Color(0xFF475569),
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                textAlign = TextAlign.Center
            )

            // Input Fields
            val inputColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFF0F172A),
                unfocusedTextColor = Color(0xFF0F172A),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFFD50000),
                unfocusedBorderColor = Color(0xFFCBD5E1),
                focusedLabelColor = Color(0xFFD50000),
                unfocusedLabelColor = Color(0xFF64748B),
                focusedLeadingIconColor = Color(0xFFD50000),
                unfocusedLeadingIconColor = Color(0xFF64748B)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Электронная почта") },
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = "Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = inputColors
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль (мин. 6 символов)") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = inputColors
            )

            authError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = Color(0xFFD50000),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.login(email, password, {}) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ВОЙТИ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Быстрый вход для тестирования:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        email = "citizen@sos.ru"
                        password = "password"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Гражданин", color = Color.Black, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        email = "admin@sos.ru"
                        password = "password"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Админ", color = Color.Black, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            TextButton(onClick = onNavigateToRegister) {
                Text("У вас нет аккаунта? Зарегистрироваться", color = Color(0xFFD50000))
            }
        }
    }
}

// 3. REGISTER SCREEN (RU)
@Composable
fun RegisterScreen(
    viewModel: MainViewModel,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("citizen") } // Default citizen
    val authError by viewModel.authError.collectAsState()

    LaunchedEffect(Unit) { viewModel.clearMessages() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.PersonAdd,
                contentDescription = "Регистрация",
                tint = Color(0xFFD50000),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Регистрация гражданина",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = "Создайте личный кабинет системы безопасности",
                fontSize = 13.sp,
                color = Color(0xFF475569),
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                textAlign = TextAlign.Center
            )

            // Input Fields
            val inputColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFF0F172A),
                unfocusedTextColor = Color(0xFF0F172A),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFFD50000),
                unfocusedBorderColor = Color(0xFFCBD5E1),
                focusedLabelColor = Color(0xFFD50000),
                unfocusedLabelColor = Color(0xFF64748B),
                focusedLeadingIconColor = Color(0xFFD50000),
                unfocusedLeadingIconColor = Color(0xFF64748B)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Электронная почта") },
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = "Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = inputColors
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль (обязательно 6+ знаков)") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = inputColors
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Выберите роль в системе:",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Role selections
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = role == "citizen",
                        onClick = { role = "citizen" },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD50000))
                    )
                    Text("Гражданин", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = role == "admin",
                        onClick = { role = "admin" },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD50000))
                    )
                    Text("Администратор", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            authError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = Color(0xFFD50000),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.register(email, password, role, {}) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ЗАРЕГИСТРИРОВАТЬСЯ", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onNavigateToLogin) {
                Text("Уже есть аккаунт? Войти", color = Color(0xFFD50000))
            }
        }
    }
}

// 4. MAIN DASHBOARD WITH INTERNAL ROUTING (CITIZEN, OPERATOR, ADMIN PANELS)
@Composable
fun DashboardRouter(
    viewModel: MainViewModel,
    onLogout: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    var currentTab by remember { mutableStateOf("home") } // tab selection inside Dashboard: "home", "zones", "profile"

    val isElevatedRole = currentUser?.role == "admin"

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == "home",
                    onClick = { currentTab = "home" },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Главная") },
                    label = { Text("Главная", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD50000),
                        selectedTextColor = Color(0xFFD50000)
                    )
                )

                // If user is Admin, show "Alerts panel"
                if (isElevatedRole) {
                    NavigationBarItem(
                        selected = currentTab == "admin_panel",
                        onClick = { currentTab = "admin_panel" },
                        icon = { Icon(Icons.Filled.Notifications, contentDescription = "Панель МЧС") },
                        label = { Text("Оповестить", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFD50000),
                            selectedTextColor = Color(0xFFD50000)
                        )
                    )
                }

                NavigationBarItem(
                    selected = currentTab == "zones",
                    onClick = { currentTab = "zones" },
                    icon = { Icon(Icons.Filled.LocationOn, contentDescription = "Безопасные зоны") },
                    label = { Text("Укрытия", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD50000),
                        selectedTextColor = Color(0xFFD50000)
                    )
                )

                NavigationBarItem(
                    selected = currentTab == "profile",
                    onClick = { currentTab = "profile" },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Профиль") },
                    label = { Text("Профиль", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD50000),
                        selectedTextColor = Color(0xFFD50000)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                "home" -> CitizenDashboardScreen(viewModel)
                "admin_panel" -> AdminDashboardScreen(viewModel)
                "zones" -> SafeZoneScreen(viewModel)
                "profile" -> ProfileScreen(viewModel, onLogout)
            }
        }
    }
}

// 5. CITIZEN DASHBOARD SCREEN (RU)
@Composable
fun CitizenDashboardScreen(viewModel: MainViewModel) {
    val alerts by viewModel.alerts.collectAsState()
    val nearestShelterResult by viewModel.nearestSafeZoneResult.collectAsState()
    val userCoord by viewModel.userLocation.collectAsState()
    val acknowledgedAlertIds by viewModel.acknowledgedAlertIds.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val context = LocalContext.current

    // Trigger updates
    LaunchedEffect(Unit) {
        viewModel.fetchUserLocation()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warning Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Pulse circle layout
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(color = Color(0xFF4CAF50))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ВАША БЕЗОПАСНОСТЬ: В НОРМЕ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "В случае поступления сигнала тревоги, сирена включится автоматически. Содержите телефон заряженным.",
                        fontSize = 11.sp,
                        color = Color(0xFF475569),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Live Nearest Safe Zone Section
        item {
            Text(
                text = "📍 БЛИЖАЙШЕЕ УКРЫТИЕ (РЕАЛЬНОЕ ВРЕМЯ)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), // Reddish white M3 surface
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (nearestShelterResult != null) {
                        val result = nearestShelterResult!!
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFD50000), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = "Шелтер",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = result.safeZone.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF263238)
                                )
                                Text(
                                    text = result.safeZone.description,
                                    fontSize = 12.sp,
                                    color = Color.DarkGray
                                )
                            }
                        }

                        // Split Grid for Distance and Time
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("РАССТОЯНИЕ", fontSize = 10.sp, color = Color(0xFF475569), fontWeight = FontWeight.Bold)
                                Text(
                                    text = String.format(Locale.getDefault(), "%.2f км", result.distanceKm),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD50000)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ВРЕМЯ ПУТИ ПЕШКОМ", fontSize = 10.sp, color = Color(0xFF475569), fontWeight = FontWeight.Bold)
                                Text(
                                    text = "~ ${result.estMinutes} мин",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                // Google Maps free URL navigation scheme
                                val mapsUrl = "https://www.google.com/maps/dir/?api=1&destination=${result.safeZone.latitude},${result.safeZone.longitude}"
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("Dashboard", "Ошибка запуска карт", e)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Navigation, contentDescription = "Go", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ПРОЛОЖИТЬ МАРШРУТ (КАРТЫ)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                    } else {
                        // Empty states
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color(0xFFD50000))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Определяем ближайшее укрытие...",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            }
        }

        // Action Coordinate Simulator
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Симулятор вашего GPS-местоположения:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Button(
                            onClick = { viewModel.fetchUserLocation() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Filled.CompassCalibration, contentDescription = "Auto", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Авто GPS", fontSize = 9.sp)
                        }
                        Button(
                            onClick = {
                                // Simulate coordinates right next to Shelter #12
                                viewModel.acknowledgeAll() // ensure siren stops first
                                val intent = Intent(context, EmergencyService::class.java).apply {
                                    action = EmergencyService.ACTION_STOP_ALARM
                                }
                                context.startService(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Filled.VolumeOff, contentDescription = "Ex", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Шумоподавление сирены", fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // Recent Notifications Title
        item {
            Text(
                text = "🚨 ТЕКУЩИЕ ЭКСТРЕННЫЕ СИГНАЛЫ (АКТИВНЫЕ)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD50000),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        val activeThreshold = 2 * 60 * 60 * 1000L // 2 hours in ms
        val currentTime = System.currentTimeMillis()
        val activeAlerts = alerts.filter { 
            !acknowledgedAlertIds.contains(it.alertId) && (currentTime - it.timestamp < activeThreshold)
        }
        val pastAlerts = alerts.filter { 
            acknowledgedAlertIds.contains(it.alertId) || (currentTime - it.timestamp >= activeThreshold)
        }
        val isAdmin = currentUser?.role == "admin"

        if (activeAlerts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Активных угроз не обнаружено. Локация в полной безопасности.",
                            fontSize = 13.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(activeAlerts) { alert ->
                AlertHistoryCard(
                    alert = alert,
                    isAcknowledgedByUser = false,
                    showDelete = isAdmin,
                    onDelete = {
                        viewModel.deleteAlert(alert.alertId)
                    }
                )
            }
        }

        // Past alerts partition (for resolved or acknowledged ones)
        if (pastAlerts.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "📁 АРХИВ ОПОВЕЩЕНИЙ (ПРИНЯТО)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            items(pastAlerts) { alert ->
                AlertHistoryCard(
                    alert = alert,
                    isAcknowledgedByUser = true,
                    showDelete = isAdmin,
                    onDelete = {
                        viewModel.deleteAlert(alert.alertId)
                    }
                )
            }
        }
    }
}

// History Alert Cell Component (RU)
@Composable
fun AlertHistoryCard(
    alert: AlertEntity,
    isAcknowledgedByUser: Boolean,
    showDelete: Boolean,
    onDelete: () -> Unit
) {
    val dateString = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp))

    val isCritical = alert.priority == "CRITICAL"
    val isHigh = alert.priority == "HIGH"

    val cardBg = if (isAcknowledgedByUser) {
        Color(0xFFF1F5F9) // Slate-100 very safe gray color
    } else {
        if (isCritical) Color(0xFFFFEBEE) else if (isHigh) Color(0xFFFFF3E0) else Color.White
    }

    val borderCol = if (isAcknowledgedByUser) {
        Color(0xFFCBD5E1)
    } else {
        if (isCritical) Color(0xFFD50000) else if (isHigh) Color(0xFFFF9800) else Color.LightGray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isAcknowledgedByUser) {
                    Modifier.border(2.dp, borderCol, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isAcknowledgedByUser) 1.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = if (isAcknowledgedByUser) {
                            Color(0xFF64748B) // Calm Slate Gray
                        } else {
                            if (isCritical) Color(0xFFB71C1C) else if (isHigh) Color(0xFFE65100) else Color.Gray
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "ПРИОРИТЕТ: ${alert.priority}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = if (isAcknowledgedByUser) Color(0xFF475569) else Color(0xFFD50000),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isAcknowledgedByUser) "✓ СИГНАЛ ПРИНЯТ" else "🚨 АКТИВНЫЙ СИГНАЛ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (showDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = alert.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAcknowledgedByUser) Color(0xFF475569) else Color.Black,
                textDecoration = if (isAcknowledgedByUser) TextDecoration.LineThrough else TextDecoration.None
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = alert.instructions,
                fontSize = 13.sp,
                color = if (isAcknowledgedByUser) Color(0xFF64748B) else Color.DarkGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = borderCol.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Время публикации: $dateString",
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

// 6. SAFE ZONE LIST VIEW AND ADD FOR ADM (RU)
@Composable
fun SafeZoneScreen(viewModel: MainViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val zones by viewModel.safeZones.collectAsState()

    val isAdmin = currentUser?.role == "admin"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "🛡️ СПИСК БЕЗОПАСНЫХ УКРЫТИЙ",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }

        if (isAdmin) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "🧩 ДОБАВИТЬ НОВОЕ УКРЫТИЕ (АДМИН)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFD50000)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        var name by remember { mutableStateOf("") }
                        var lat by remember { mutableStateOf("") }
                        var lon by remember { mutableStateOf("") }
                        var desc by remember { mutableStateOf("") }

                        val inputColors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFFD50000),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedLabelColor = Color(0xFFD50000),
                            unfocusedLabelColor = Color(0xFF64748B)
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Название укрытия", fontSize = 12.sp) },
                            singleLine = true,
                            colors = inputColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // INTERACTIVE MAP FOR SELECTING COORDINATES
                        Text(
                            text = "🗺️ Интерактивная карта Ташкента (Нажмите для выбора координат):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF1F5F9))
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val minLat = 41.2500
                                        val maxLat = 41.3700
                                        val minLon = 69.1800
                                        val maxLon = 69.3400

                                        val tappedLon = minLon + (offset.x / size.width) * (maxLon - minLon)
                                        val tappedLat = maxLat - (offset.y / size.height) * (maxLat - minLat)

                                        lat = String.format(Locale.US, "%.5f", tappedLat)
                                        lon = String.format(Locale.US, "%.5f", tappedLon)
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val minLat = 41.2500
                                val maxLat = 41.3700
                                val minLon = 69.1800
                                val maxLon = 69.3400

                                val mapWidth = size.width
                                val mapHeight = size.height

                                fun getX(l: Double): Float = ((l - minLon) / (maxLon - minLon) * mapWidth).toFloat()
                                fun getY(l: Double): Float = ((maxLat - l) / (maxLat - minLat) * mapHeight).toFloat()

                                // 1. Grids
                                val gridColor = Color(0xFFE2E8F0)
                                for (i in 1..4) {
                                    val x = mapWidth * (i / 5f)
                                    drawLine(color = gridColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, mapHeight), strokeWidth = 1f)
                                    val y = mapHeight * (i / 5f)
                                    drawLine(color = gridColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(mapWidth, y), strokeWidth = 1f)
                                }

                                // 2. Draw Anhor river path
                                val riverPath = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(getX(69.2100), getY(41.3700))
                                    quadraticTo(
                                        getX(69.2500), getY(41.3100),
                                        getX(69.2600), getY(41.2800)
                                    )
                                    quadraticTo(
                                        getX(69.2400), getY(41.2600),
                                        getX(69.2200), getY(41.2500)
                                    )
                                }
                                drawPath(
                                    path = riverPath,
                                    color = Color(0xFFBAE6FD),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )

                                // 3. Draw key roads
                                val roadColor = Color(0xFFE2E8F0)
                                drawCircle(
                                    color = roadColor,
                                    center = androidx.compose.ui.geometry.Offset(getX(69.2797), getY(41.3111)),
                                    radius = mapWidth * 0.35f,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                )
                                drawLine(roadColor, androidx.compose.ui.geometry.Offset(getX(69.1800), getY(41.3111)), androidx.compose.ui.geometry.Offset(getX(69.3400), getY(41.3111)), strokeWidth = 4.dp.toPx())
                                drawLine(roadColor, androidx.compose.ui.geometry.Offset(getX(69.2797), getY(41.2500)), androidx.compose.ui.geometry.Offset(getX(69.2797), getY(41.3700)), strokeWidth = 4.dp.toPx())

                                // 4. Draw existing shelters as small green circles
                                zones.forEach { zone ->
                                    val zx = getX(zone.longitude)
                                    val zy = getY(zone.latitude)
                                    if (zx in 0f..mapWidth && zy in 0f..mapHeight) {
                                        drawCircle(color = Color(0xFF16A34A), radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(zx, zy))
                                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(zx, zy))
                                    }
                                }

                                // 5. Draw currently selected pin coordinate
                                val parsedLat = lat.toDoubleOrNull()
                                val parsedLon = lon.toDoubleOrNull()
                                if (parsedLat != null && parsedLon != null && parsedLat in minLat..maxLat && parsedLon in minLon..maxLon) {
                                    val px = getX(parsedLon)
                                    val py = getY(parsedLat)
                                    drawCircle(
                                        color = Color(0xFFD50000).copy(alpha = 0.3f),
                                        radius = 14.dp.toPx(),
                                        center = androidx.compose.ui.geometry.Offset(px, py)
                                    )
                                    drawCircle(
                                        color = Color(0xFFD50000),
                                        radius = 6.dp.toPx(),
                                        center = androidx.compose.ui.geometry.Offset(px, py)
                                    )
                                }
                            }

                            // Watermarks / Labels for city landmarks
                            Box(modifier = Modifier.fillMaxSize()) {
                                Text("р. Анхор", fontSize = 9.sp, color = Color(0xFF0284C7).copy(alpha = 0.5f), modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp, top = 32.dp))
                                Text("Сквер А. Темура", fontSize = 9.sp, color = Color(0xFF475569).copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center).padding(start = 32.dp, top = 12.dp))
                                Text("Чорсу", fontSize = 9.sp, color = Color(0xFF475569).copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp, bottom = 42.dp))
                                Text("Телебашня", fontSize = 9.sp, color = Color(0xFF475569).copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopEnd).padding(end = 40.dp, top = 20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = lat,
                                onValueChange = { lat = it },
                                label = { Text("Широта (Lat)", fontSize = 12.sp) },
                                singleLine = true,
                                colors = inputColors,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedTextField(
                                value = lon,
                                onValueChange = { lon = it },
                                label = { Text("Долгота (Lng)", fontSize = 12.sp) },
                                singleLine = true,
                                colors = inputColors,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = desc,
                            onValueChange = { desc = it },
                            label = { Text("Описание и ориентиры", fontSize = 12.sp) },
                            colors = inputColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val dLat = lat.toDoubleOrNull() ?: 0.0
                                val dLon = lon.toDoubleOrNull() ?: 0.0
                                if (name.isNotBlank() && dLat != 0.0 && dLon != 0.0) {
                                    viewModel.addSafeZone(name, dLat, dLon, desc)
                                    name = ""
                                    lat = ""
                                    lon = ""
                                    desc = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("СОХРАНИТЬ В FIRESTORE")
                        }
                    }
                }
            }
        }

        if (zones.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Список сохраненных зон укрытий пуст.", color = Color.Gray)
                    }
                }
            }
        } else {
            items(zones) { zone ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFE8F5E9), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = "Zone", tint = Color(0xFF2E7D32))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(zone.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                            Text(zone.description, fontSize = 12.sp, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Координаты: ${zone.latitude}, ${zone.longitude}",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isAdmin) {
                            IconButton(onClick = { viewModel.deleteSafeZone(zone.zoneId) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFD50000))
                            }
                        }
                    }
                }
            }
        }
    }
}

// 7. ADMIN EMERGENCY TRIGGER SCREEN (RU)
@Composable
fun AdminDashboardScreen(viewModel: MainViewModel) {
    var title by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("CRITICAL") } // "CRITICAL", "HIGH", "NORMAL"

    val successMessage by viewModel.successMessage.collectAsState()
    val authError by viewModel.authError.collectAsState()

    val firebaseStatus by com.example.data.FirebaseSyncManager.connectionStatus.collectAsState()
    val firebaseError by com.example.data.FirebaseSyncManager.lastError.collectAsState()

    LaunchedEffect(Unit) { viewModel.clearMessages() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // -----------------------------------------------------
            // FIREBASE CONNECTION DIAGNOSTICS & BASE URL MANAGEMENT
            // -----------------------------------------------------
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ДИАГНОСТИКА ПОДКЛЮЧЕНИЯ И СЕРВЕРА:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Connection status badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val isConnected = firebaseStatus.contains("ПОДКЛЮЧЕНО")
                        Canvas(modifier = Modifier.size(10.dp)) {
                            drawCircle(color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFD50000))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Статус Firebase: $firebaseStatus",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                        )
                    }

                    firebaseError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Ошибка БД: $err",
                                fontSize = 11.sp,
                                color = Color(0xFFB71C1C),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Базовый URL облака Firebase RTDB:",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )

                    val context = LocalContext.current
                    val prefs = remember { context.getSharedPreferences("sos_alert_prefs", Context.MODE_PRIVATE) }
                    var dbUrlInput by remember {
                        mutableStateOf(prefs.getString("firebase_db_url", "https://sosalert-f9a99-default-rtdb.firebaseio.com") ?: "https://sosalert-f9a99-default-rtdb.firebaseio.com")
                    }
                    var isEditingUrl by remember { mutableStateOf(false) }

                    if (isEditingUrl) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = dbUrlInput,
                            onValueChange = { dbUrlInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                isEditingUrl = false
                                dbUrlInput = prefs.getString("firebase_db_url", "https://sosalert-f9a99-default-rtdb.firebaseio.com") ?: "https://sosalert-f9a99-default-rtdb.firebaseio.com"
                            }) {
                                Text("ОТМЕНА", fontSize = 12.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (dbUrlInput.isNotBlank()) {
                                        com.example.data.FirebaseSyncManager.updateDatabaseUrl(context, dbUrlInput)
                                        isEditingUrl = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                            ) {
                                Text("ПРИМЕНИТЬ", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    } else {
                        Text(
                            text = dbUrlInput,
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Color.Black,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = { isEditingUrl = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("ИЗМЕНИТЬ URL СЕРВЕРА ⚙️", fontSize = 11.sp, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = "Alert Trigger",
                        tint = Color(0xFFD50000),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ОБЪЯВЛЕНИЕ ЭКСТРЕННОЙ ТРЕВОГИ",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "Сигнал мгновенно поступит на все устройства граждан в виде сирены и полноэкранного сообщения",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Priority selectors
                    Text(
                        text = "Уровень приоритета тревоги:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = priority == "CRITICAL",
                                onClick = { priority = "CRITICAL" },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFB71C1C))
                            )
                            Text("КРИТИЧЕСКИЙ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = priority == "HIGH",
                                onClick = { priority = "HIGH" },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF9800))
                            )
                            Text("ВЫСОКИЙ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fast Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                title = "ВОЗДУШНАЯ УГРОЗА"
                                instructions = "Внимание! Воздушная тревога. Срочно пройдите в оборудованное укрытие гражданской обороны."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Воздушная", color = Color(0xFFB71C1C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                title = "ТЕХНОГЕННАЯ АВАРИЯ"
                                instructions = "Внимание! Произошел прорыв дамбы / утечка химикатов газа. Соблюдайте дистанцию и закройте все окна влажной тканью."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF3E0)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Авария", color = Color(0xFFE65100), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val inputColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF0F172A),
                        unfocusedTextColor = Color(0xFF0F172A),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFFD50000),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedLabelColor = Color(0xFFD50000),
                        unfocusedLabelColor = Color(0xFF64748B)
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Заголовок тревоги (например: ВОЗДУШНАЯ ТРЕВОГА)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = inputColors
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        label = { Text("Инструкции безопасности для граждан") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = inputColors
                    )

                    successMessage?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = it,
                            color = Color(0xFF2E7D32),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    authError?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = it,
                            color = Color(0xFFB71C1C),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() && instructions.isNotBlank()) {
                                viewModel.triggerAlert(title, instructions, priority)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.FlashOn, contentDescription = "Send", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ОТПРАВИТЬ СИГНАЛ (БЕЗЗВУЧНЫЙ / С СИРЕНОЙ)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

// 8. PROFILE SCRÈEN (RU)
@Composable
fun ProfileScreen(viewModel: MainViewModel, onLogout: () -> Unit) {
    val currentUser by viewModel.currentUser.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Color(0xFFFFEBEE), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "Profile",
                tint = Color(0xFFD50000),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = currentUser?.email ?: "неизвестный пользователь",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            color = Color(0xFFD50000).copy(alpha = 0.12f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = "РОЛЬ: ${(currentUser?.role ?: "").uppercase()}",
                color = Color(0xFFD50000),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                viewModel.logout(onLogout)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.ExitToApp, contentDescription = "Logout")
            Spacer(modifier = Modifier.width(8.dp))
            Text("ВЫЙТИ ИЗ СИСТЕМЫ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

// 9. HIGH-PRIORITY CIVIL WARNING EMERGENCY SCREEN (RU)
@Composable
fun EmergencyOverlayScreen(
    alert: AlertEntity,
    viewModel: MainViewModel,
    onAcknowledge: () -> Unit
) {
    val context = LocalContext.current
    val nearestShelterResult by viewModel.nearestSafeZoneResult.collectAsState()

    // Blinking flash red background loop animation
    val infiniteTransition = rememberInfiniteTransition(label = "flash")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flash_alpha"
    )

    val warningScale by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "warning_scale"
    )

    val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alert.timestamp))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB71C1C).copy(alpha = alphaAnim))
            .clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // 1. Status Bar Mimic
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedTime,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LTE",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    // Custom battery symbol
                    Box(
                        modifier = Modifier
                            .size(width = 20.dp, height = 11.dp)
                            .background(Color.Transparent)
                            .clip(RoundedCornerShape(2.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Border
                            drawRect(
                                color = Color.White.copy(alpha = 0.8f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                            )
                            // Inner fill
                            drawRect(
                                color = Color.White.copy(alpha = 0.8f),
                                size = androidx.compose.ui.geometry.Size(width = size.width * 0.75f, height = size.height - 4.dp.toPx()),
                                topLeft = androidx.compose.ui.geometry.Offset(x = 2.dp.toPx(), y = 2.dp.toPx())
                            )
                        }
                    }
                }
            }

            // Scrollable Content Region
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                // Pulse warning ring icon
                Box(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .size(90.dp * warningScale)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                        .padding(12.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚠️",
                        fontSize = 38.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // КРИТИЧЕСКИЙ УРОВЕНЬ dynamic badge
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = if (alert.priority == "CRITICAL") "КРИТИЧЕСКИЙ УРОВЕНЬ" else "ВЫСОКИЙ ПРИОРИТЕТ",
                        color = Color(0xFFB71C1C),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // Alert Title (e.g. Воздушная Тревога)
                Text(
                    text = alert.title,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Subtitle description snippet
                Text(
                    text = "Внимание! Зафиксирована непосредственная опасность.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 24.dp)
                )

                // Detailed semi-transparent Instructions card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = "ИНСТРУКЦИИ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = alert.instructions,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White,
                            lineHeight = 24.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Proximity Location Section
                val shelterName = nearestShelterResult?.safeZone?.name ?: "Убежище №12 (Подземное)"
                val shelterAddress = nearestShelterResult?.safeZone?.description ?: "Откройте карту для выбора ближайшего"
                val distanceKm = nearestShelterResult?.distanceKm
                val estMinutes = nearestShelterResult?.estMinutes ?: 6

                val distanceStr = if (distanceKm != null) {
                    if (distanceKm < 1.0) {
                        "${(distanceKm * 1000).toInt()} метров"
                    } else {
                        String.format(Locale.getDefault(), "%.1f км", distanceKm)
                    }
                } else {
                    "Поиск..."
                }

                val targetLat = nearestShelterResult?.safeZone?.latitude ?: 41.3111
                val targetLon = nearestShelterResult?.safeZone?.longitude ?: 69.2797

                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Circular/rounded icon box
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFFFFF1F1), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🏢", fontSize = 22.sp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = shelterName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = shelterAddress,
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 2,
                                    lineHeight = 17.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Info Row for distance and duration
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("📍", fontSize = 12.sp)
                                        Text(
                                            text = distanceStr,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("⏱️", fontSize = 12.sp)
                                        Text(
                                            text = "~ $estMinutes минут",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF16A34A)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Built-in Navigation Action Button (black slate theme)
                        Button(
                            onClick = {
                                val mapsUrl = "https://www.google.com/maps/dir/?api=1&destination=$targetLat,$targetLon"
                                try {
                                    val mapsIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(mapsUrl))
                                    context.startActivity(mapsIntent)
                                } catch (e: Exception) {
                                    Log.e("EmergencySiren", "Cannot open maps intent", e)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "ПОСТРОИТЬ МАРШРУТ",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "↗️", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 4. Bottom Acknowledge Action & Footer Text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp, top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onAcknowledge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(32.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Text(
                        text = "ПОНЯТНО",
                        color = Color(0xFFB71C1C),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "СИРЕНА БУДЕТ ОТКЛЮЧЕНА ПОСЛЕ ПОДТВЕРЖДЕНИЯ",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
