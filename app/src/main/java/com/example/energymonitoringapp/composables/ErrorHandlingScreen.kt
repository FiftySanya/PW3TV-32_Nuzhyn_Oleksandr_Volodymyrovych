package com.example.energymonitoringapp.composables

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

data class ErrorLogRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val errorCode: String,
    val description: String,
    val severity: String
)

object NetworkErrorHandler {
    private const val TAG = "EnergyErrorHandler"

    fun handleHttpCode(statusCode: Int): Pair<String, String> {
        val userMessage = when (statusCode) {
            400 -> "Помилка 400: Некоректні параметри запиту показників лічильника"
            401 -> "Помилка 401: Неавторизований доступ. Термін сесії вичерпано"
            403 -> "Помилка 403: Доступ до диспетчерського керування заборонено"
            404 -> "Помилка 404: Лічильник або вимірювальну лінію не знайдено на сервері"
            500 -> "Помилка 500: Внутрішній збій центрального сервера телеметрії"
            503 -> "Помилка 503: Сервер перевантажений або на технічному обслуговуванні"
            else -> "HTTP Помилка $statusCode: Непередбачена відповідь сервера"
        }
        val severity = if (statusCode >= 500) "ERROR" else "WARNING"
        Log.e(TAG, "HTTP error intercepted: code=$statusCode, msg=$userMessage")
        return userMessage to severity
    }

    fun handleException(throwable: Throwable): Pair<String, String> {
        val userMessage = when (throwable) {
            is IOException -> "Мережевий збій: Перевірте підключення до мережі або доступність шлюзу"
            else -> "Невідома помилка виконання запиту: ${throwable.localizedMessage ?: "Збій системи"}"
        }
        Log.e(TAG, "Exception intercepted: ${throwable.javaClass.simpleName}, msg=$userMessage", throwable)
        return userMessage to "ERROR"
    }
}

data class ErrorHandlingUiState(
    val isLoading: Boolean = false,
    val retryAttempt: Int = 0,
    val currentStatusMessage: String? = null,
    val isSuccess: Boolean = false,
    val errorLogs: List<ErrorLogRecord> = emptyList()
)

class Lesson12ViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ErrorHandlingUiState())
    val uiState: StateFlow<ErrorHandlingUiState> = _uiState.asStateFlow()

    fun simulateRequest(scenario: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                retryAttempt = 0,
                currentStatusMessage = "Виконання мережевого запиту...",
                isSuccess = false
            )
            delay(500)

            when (scenario) {
                "SUCCESS_200" -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true,
                        currentStatusMessage = "Успіх 200 OK: Показники споживання успішно завантажено"
                    )
                    addLog("200 OK", "Успішне зчитування телеметрії лічильника", "INFO")
                }
                "ERROR_404" -> {
                    val (msg, severity) = NetworkErrorHandler.handleHttpCode(404)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = false,
                        currentStatusMessage = msg
                    )
                    addLog("HTTP 404", msg, severity)
                }
                "ERROR_500" -> {
                    val (msg, severity) = NetworkErrorHandler.handleHttpCode(500)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = false,
                        currentStatusMessage = msg
                    )
                    addLog("HTTP 500", msg, severity)
                }
                "TIMEOUT_RETRY" -> {

                    executeWithRetry(maxRetries = 3)
                }
            }
        }
    }

    private suspend fun executeWithRetry(maxRetries: Int) {
        for (attempt in 1..maxRetries) {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                retryAttempt = attempt,
                currentStatusMessage = "Спроба $attempt з $maxRetries: Підключення до сервера..."
            )
            delay(600)

            if (attempt < maxRetries) {
                val failMsg = "Таймаут сокета на спробі $attempt. Очікування повтору..."
                addLog("TIMEOUT", failMsg, "WARNING")
                delay(400)
            } else {

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSuccess = true,
                    currentStatusMessage = "Зв'язок відновлено на спробі $attempt! Дані отримано."
                )
                addLog("RETRY OK", "Успішне підключення після автоматичних повторів ($maxRetries спроби)", "INFO")
                return
            }
        }
    }

    private fun addLog(code: String, description: String, severity: String) {
        val newEntry = ErrorLogRecord(
            errorCode = code,
            description = description,
            severity = severity
        )
        _uiState.value = _uiState.value.copy(
            errorLogs = listOf(newEntry) + _uiState.value.errorLogs
        )
    }

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(errorLogs = emptyList())
    }
}

@Composable
fun ErrorHandlingScreen(viewModel: Lesson12ViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(state.currentStatusMessage) {
        state.currentStatusMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Обробка мережевих помилок",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "HTTP коди (404, 500), Retry-логіка, Snackbar та журнал",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Симуляція сценаріїв запитів до API:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.simulateRequest("SUCCESS_200") },
                            enabled = !state.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("200 OK", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { viewModel.simulateRequest("ERROR_404") },
                            enabled = !state.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("404 Not Found", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.simulateRequest("ERROR_500") },
                            enabled = !state.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("500 Server Error", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { viewModel.simulateRequest("TIMEOUT_RETRY") },
                            enabled = !state.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retry (3 спроби)", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        state.isLoading -> MaterialTheme.colorScheme.secondaryContainer
                        state.isSuccess -> Color(0xFFE8F5E9)
                        state.currentStatusMessage != null -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = state.currentStatusMessage ?: "Завантаження...",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    } else if (state.isSuccess) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.currentStatusMessage ?: "Готово",
                            color = Color(0xFF1B5E20),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    } else if (state.currentStatusMessage != null) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.currentStatusMessage ?: "Помилка",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    } else {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Оберіть дію вище для перевірки обробника",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Журнал реєстрації помилок (${state.errorLogs.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (state.errorLogs.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Очистити", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (state.errorLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Журнал помилок порожній", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.errorLogs, key = { it.id }) { log ->
                        val badgeColor = when (log.severity) {
                            "ERROR" -> MaterialTheme.colorScheme.error
                            "WARNING" -> Color(0xFFF57C00)
                            else -> Color(0xFF388E3C)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = badgeColor
                                        ) {
                                            Text(
                                                text = log.errorCode,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = timeFormatter.format(Date(log.timestamp)),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = log.description,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
