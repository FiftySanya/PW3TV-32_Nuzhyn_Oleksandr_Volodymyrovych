package com.example.energymonitoringapp.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
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
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class EnergyMeterApiModel(
    @SerializedName("id") val id: String,
    @SerializedName("facility_name") val facilityName: String,
    @SerializedName("current_power_kw") val currentPowerKw: Double,
    @SerializedName("voltage_v") val voltageV: Double,
    @SerializedName("frequency_hz") val frequencyHz: Double,
    @SerializedName("status") val status: String,
    @SerializedName("daily_kwh") val dailyKwh: Double
)

data class EnergySummaryApiModel(
    @SerializedName("total_load_kw") val totalLoadKw: Double,
    @SerializedName("active_meters_count") val activeMetersCount: Int,
    @SerializedName("forecast_daily_kwh") val forecastDailyKwh: Double
)

interface EnergyApiService {
    @GET("api/v1/energy/meters")
    suspend fun getMeters(): List<EnergyMeterApiModel>

    @GET("api/v1/energy/summary")
    suspend fun getSummary(): EnergySummaryApiModel
}

class MockRestApiInterceptor : Interceptor {
    var simulateOffline: Boolean = false

    private val gson = Gson()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.encodedPath

        if (simulateOffline) {

            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("Service Unavailable - Network Offline")
                .body("{}".toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        Thread.sleep(400)

        val (statusCode, json) = when {
            url.endsWith("/meters") -> {
                200 to gson.toJson(
                    listOf(
                        EnergyMeterApiModel("M-101", "Підстанція 110/10 кВ «Північна»", 168.4, 382.0, 50.02, "OVERLOAD", 3200.5),
                        EnergyMeterApiModel("M-102", "Трансформатор Т-2 (Промзона)", 112.0, 379.5, 49.98, "NORMAL", 2450.0),
                        EnergyMeterApiModel("M-103", "Розподільчий пункт РП-5 (Центр)", 64.8, 221.4, 50.01, "NORMAL", 1180.2),
                        EnergyMeterApiModel("M-104", "Фідер №4 (Житловий масив)", 91.2, 219.0, 49.97, "NORMAL", 1890.6),
                        EnergyMeterApiModel("M-105", "Котельня №2 (Резервна лінія)", 34.5, 380.0, 50.00, "NORMAL", 710.0)
                    )
                )
            }
            url.endsWith("/summary") -> {
                200 to gson.toJson(
                    EnergySummaryApiModel(
                        totalLoadKw = 470.9,
                        activeMetersCount = 5,
                        forecastDailyKwh = 9431.3
                    )
                )
            }
            else -> 404 to "{\"error\":\"Not Found\"}"
        }

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(statusCode)
            .message("OK")
            .body(json.toResponseBody("application/json".toMediaTypeOrNull()))
            .build()
    }
}

object RetrofitEnergyClient {
    val mockInterceptor = MockRestApiInterceptor()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(mockInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.energymonitor.local/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: EnergyApiService = retrofit.create(EnergyApiService::class.java)
}

data class RestApiUiState(
    val isLoading: Boolean = false,
    val isOfflineMode: Boolean = false,
    val isDataFromCache: Boolean = false,
    val meters: List<EnergyMeterApiModel> = emptyList(),
    val summary: EnergySummaryApiModel? = null,
    val errorMessage: String? = null
)

class Lesson10ViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RestApiUiState())
    val uiState: StateFlow<RestApiUiState> = _uiState.asStateFlow()

    private var cachedMeters: List<EnergyMeterApiModel> = emptyList()
    private var cachedSummary: EnergySummaryApiModel? = null

    init {
        loadData()
    }

    fun toggleOfflineMode() {
        val newOffline = !_uiState.value.isOfflineMode
        RetrofitEnergyClient.mockInterceptor.simulateOffline = newOffline
        _uiState.value = _uiState.value.copy(isOfflineMode = newOffline)
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {

                val meters = RetrofitEnergyClient.apiService.getMeters()
                val summary = RetrofitEnergyClient.apiService.getSummary()

                cachedMeters = meters
                cachedSummary = summary

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isDataFromCache = false,
                    meters = meters,
                    summary = summary
                )
            } catch (e: Exception) {

                if (cachedMeters.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isDataFromCache = true,
                        meters = cachedMeters,
                        summary = cachedSummary,
                        errorMessage = "Мережа недоступна. Відображено дані з локального кешу"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Помилка завантаження: ${e.localizedMessage ?: "Сервер недоступний"}"
                    )
                }
            }
        }
    }
}

@Composable
fun RestApiScreen(viewModel: Lesson10ViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var filterOverloadOnly by remember { mutableStateOf(false) }

    val displayedMeters = remember(state.meters, filterOverloadOnly) {
        if (filterOverloadOnly) state.meters.filter { it.status == "OVERLOAD" } else state.meters
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "REST API: Інтеграція та JSON",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Retrofit2, Gson, офлайн-кешування та агрегація",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.isOfflineMode)
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (state.isOfflineMode) Icons.Default.CloudOff else Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = if (state.isOfflineMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isOfflineMode) "Режим: Офлайн (збої мережі)" else "Режим: Онлайн (REST API)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = !state.isOfflineMode,
                    onCheckedChange = { viewModel.toggleOfflineMode() },
                    modifier = Modifier.scale(0.85f)
                )
            }
        }

        if (state.errorMessage != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = state.errorMessage ?: "",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        state.summary?.let { summary ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Зведення енергомережі (REST)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (state.isDataFromCache) {
                            Text("● Кеш", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Загальне навантаження", fontSize = 11.sp)
                            Text("%.1f кВт".format(summary.totalLoadKw), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Column {
                            Text("Активні лічильники", fontSize = 11.sp)
                            Text("${summary.activeMetersCount} шт.", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Column {
                            Text("Прогноз / доба", fontSize = 11.sp)
                            Text("%.0f кВт·г".format(summary.forecastDailyKwh), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilterChip(
                selected = filterOverloadOnly,
                onClick = { filterOverloadOnly = !filterOverloadOnly },
                label = { Text(if (filterOverloadOnly) "Лише перевантаження" else "Всі лічильники", fontSize = 11.sp) }
            )

            Button(
                onClick = { viewModel.loadData() },
                enabled = !state.isLoading,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Оновити API", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayedMeters, key = { it.id }) { meter ->
                val isOverload = meter.status == "OVERLOAD"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOverload)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = meter.facilityName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isOverload) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = if (isOverload) "ПЕРЕВАНТАЖЕННЯ" else "НОРМА",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Потужність: ${meter.currentPowerKw} кВт", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("Напруга: ${meter.voltageV} В", fontSize = 12.sp)
                            Text("Частота: ${meter.frequencyHz} Гц", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Добове споживання: ${meter.dailyKwh} кВт·год (ID: ${meter.id})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this
