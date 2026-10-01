package com.example.energymonitoringapp.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class UserRole {
    GUEST,
    USER,
    ADMIN
}

data class AuthToken(
    val tokenValue: String,
    val username: String,
    val role: UserRole,
    val expiresAt: Long
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
}

data class SecureEnergyData(
    val mainSubstationPowerKw: Double,
    val backupGeneratorStatus: String,
    val gridPowerLimitKw: Double,
    val isEmergencyShutdown: Boolean
)

data class AuthUiState(
    val currentToken: AuthToken? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val secureData: SecureEnergyData? = null,
    val adminAuditLog: List<String> = emptyList()
)

class Lesson11ViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(username: String, pass: String) {
        if (username.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Помилка: логін та пароль не можуть бути порожніми")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            kotlinx.coroutines.delay(400)

            val role = when {
                username == "admin" && pass == "admin123" -> UserRole.ADMIN
                username == "user" && pass == "user123" -> UserRole.USER
                else -> null
            }

            if (role != null) {

                val token = AuthToken(
                    tokenValue = "mock-jwt-" + UUID.randomUUID().toString().take(12),
                    username = username,
                    role = role,
                    expiresAt = System.currentTimeMillis() + 15 * 60 * 1000
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentToken = token,
                    errorMessage = null,
                    adminAuditLog = listOf("Успішний вхід користувача $username (${role.name})")
                )

                fetchSecureResource()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Невірний логін або пароль! (Спробуйте admin/admin123 або user/user123)"
                )
            }
        }
    }

    fun fetchSecureResource() {
        val token = _uiState.value.currentToken
        if (token == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "401 Unauthorized: Токен відсутній. Доступ до захищеного ресурсу заборонено!",
                secureData = null
            )
            return
        }

        if (token.isExpired) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "401 Unauthorized: Термін дії токена вичерпано! Необхідно виконати повторний вхід.",
                secureData = null
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            secureData = SecureEnergyData(
                mainSubstationPowerKw = 340.5,
                backupGeneratorStatus = "Готовий до запуску (Standby)",
                gridPowerLimitKw = 500.0,
                isEmergencyShutdown = false
            ),
            errorMessage = null
        )
    }

    fun updatePowerLimit(newLimit: Double) {
        if (_uiState.value.currentToken?.role != UserRole.ADMIN) return
        val current = _uiState.value.secureData ?: return
        _uiState.value = _uiState.value.copy(
            secureData = current.copy(gridPowerLimitKw = newLimit),
            adminAuditLog = _uiState.value.adminAuditLog + "Зміна ліміту навантаження на $newLimit кВт"
        )
    }

    fun toggleEmergencyShutdown() {
        if (_uiState.value.currentToken?.role != UserRole.ADMIN) return
        val current = _uiState.value.secureData ?: return
        val newState = !current.isEmergencyShutdown
        _uiState.value = _uiState.value.copy(
            secureData = current.copy(isEmergencyShutdown = newState),
            adminAuditLog = _uiState.value.adminAuditLog + (if (newState) "УВАГА: Аварійне відключення активовано!" else "Аварійне відключення скасовано")
        )
    }

    fun simulateExpireToken() {
        val current = _uiState.value.currentToken ?: return
        _uiState.value = _uiState.value.copy(
            currentToken = current.copy(expiresAt = System.currentTimeMillis() - 1000)
        )
        fetchSecureResource()
    }

    fun logout() {
        _uiState.value = AuthUiState()
    }
}

@Composable
fun AuthScreen(viewModel: Lesson11ViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var usernameInput by remember { mutableStateOf("admin") }
    var passwordInput by remember { mutableStateOf("admin123") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Автентифікація: Токени та Ролі",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Mocked Token, захищені ендпоїнти, перевірка терміну та ролі",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (state.currentToken == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Вхід до системи моніторингу",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "admin / admin123 (Адміністратор) або user / user123 (Оператор)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = usernameInput == "admin",
                            onClick = {
                                usernameInput = "admin"
                                passwordInput = "admin123"
                            },
                            label = { Text("Адміністратор (admin)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = usernameInput == "user",
                            onClick = {
                                usernameInput = "user"
                                passwordInput = "user123"
                            },
                            label = { Text("Користувач (user)", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text("Логін") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Пароль") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.login(usernameInput, passwordInput) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text("Увійти через Mocked API")
                        }
                    }
                }
            }
        } else {

            val token = state.currentToken!!
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (token.role == UserRole.ADMIN)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (token.role == UserRole.ADMIN) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Користувач: ${token.username}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "РОЛЬ: ${token.role.name}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Токен: ${token.tokenValue}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.simulateExpireToken() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Прострочити токен", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { viewModel.logout() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Вийти", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            state.secureData?.let { secure ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Захищений ресурс (Secure Endpoint)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Навантаження підстанції: ${secure.mainSubstationPowerKw} кВт", fontSize = 13.sp)
                        Text("Генератор резерву: ${secure.backupGeneratorStatus}", fontSize = 13.sp)
                        Text("Встановлений ліміт потужності: ${secure.gridPowerLimitKw} кВт", fontSize = 13.sp)
                        Text(
                            text = if (secure.isEmergencyShutdown) "СТАН: АВАРІЙНЕ ВІДКЛЮЧЕННЯ" else "СТАН: МЕРЕЖА ПРАЦЮЄ ШТАТНО",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (secure.isEmergencyShutdown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (token.role == UserRole.ADMIN) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Панель керування адміністратора (Admin Only)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.updatePowerLimit(600.0) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Ліміт 600 кВт", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.updatePowerLimit(450.0) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Ліміт 450 кВт", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = { viewModel.toggleEmergencyShutdown() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.secureData?.isEmergencyShutdown == true)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (state.secureData?.isEmergencyShutdown == true)
                                    "ВІДНОВИТИ ЖИВЛЕННЯ"
                                else
                                    "АВАРІЙНЕ ВІДКЛЮЧЕННЯ ЕНЕРГОМЕРЕЖІ"
                            )
                        }
                    }
                }
            }

            if (state.adminAuditLog.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Журнал автентифікації та аудиту:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        state.adminAuditLog.takeLast(4).forEach { log ->
                            Text("• $log", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        if (state.errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = state.errorMessage ?: "",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
