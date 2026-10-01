package com.example.energymonitoringapp.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class EnergyConsumptionRecord(
    val id: String = UUID.randomUUID().toString(),
    val facilityName: String,
    val category: String,
    val powerKw: Double,
    val voltageV: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class EnergyStats(
    val totalPowerKw: Double = 0.0,
    val averagePowerKw: Double = 0.0,
    val peakFacility: String = "-",
    val peakPowerKw: Double = 0.0
)

class LocalEnergyDataSource {
    private val records = mutableListOf(
        EnergyConsumptionRecord(facilityName = "Головний цех №1", category = "Промисловий", powerKw = 145.8, voltageV = 380.0),
        EnergyConsumptionRecord(facilityName = "Офісний центр", category = "Комерційний", powerKw = 42.3, voltageV = 220.0),
        EnergyConsumptionRecord(facilityName = "Житловий комплекс «Затишок»", category = "Побутовий", powerKw = 78.5, voltageV = 220.0)
    )

    fun getCachedRecords(): List<EnergyConsumptionRecord> = records.toList()

    fun saveRecords(newRecords: List<EnergyConsumptionRecord>) {
        records.clear()
        records.addAll(newRecords)
    }

    fun insertRecord(record: EnergyConsumptionRecord) {
        records.add(0, record)
    }
}

class RemoteEnergyDataSource {
    suspend fun fetchRemoteRecords(): List<EnergyConsumptionRecord> {
        delay(600)
        return listOf(
            EnergyConsumptionRecord(facilityName = "Головний цех №1", category = "Промисловий", powerKw = 152.4, voltageV = 381.2),
            EnergyConsumptionRecord(facilityName = "Офісний центр", category = "Комерційний", powerKw = 48.7, voltageV = 219.5),
            EnergyConsumptionRecord(facilityName = "Житловий комплекс «Затишок»", category = "Побутовий", powerKw = 82.1, voltageV = 221.0),
            EnergyConsumptionRecord(facilityName = "Насосна станція №3", category = "Промисловий", powerKw = 95.0, voltageV = 380.0),
            EnergyConsumptionRecord(facilityName = "Торговельний комплекс", category = "Комерційний", powerKw = 63.4, voltageV = 220.5)
        )
    }
}

class EnergyMonitoringRepository(
    private val localSource: LocalEnergyDataSource = LocalEnergyDataSource(),
    private val remoteSource: RemoteEnergyDataSource = RemoteEnergyDataSource()
) {
    private val _recordsFlow = MutableStateFlow<List<EnergyConsumptionRecord>>(localSource.getCachedRecords())
    val recordsFlow: StateFlow<List<EnergyConsumptionRecord>> = _recordsFlow.asStateFlow()

    suspend fun refreshData() {

        val remoteData = remoteSource.fetchRemoteRecords()
        localSource.saveRecords(remoteData)
        _recordsFlow.value = localSource.getCachedRecords()
    }

    fun addManualRecord(record: EnergyConsumptionRecord) {
        localSource.insertRecord(record)
        _recordsFlow.value = localSource.getCachedRecords()
    }

    fun calculateStats(records: List<EnergyConsumptionRecord>): EnergyStats {
        if (records.isEmpty()) return EnergyStats()
        val total = records.sumOf { it.powerKw }
        val avg = total / records.size
        val peak = records.maxByOrNull { it.powerKw }
        return EnergyStats(
            totalPowerKw = total,
            averagePowerKw = avg,
            peakFacility = peak?.facilityName ?: "-",
            peakPowerKw = peak?.powerKw ?: 0.0
        )
    }
}

class Lesson9ViewModel(
    private val repository: EnergyMonitoringRepository = EnergyMonitoringRepository()
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("Всі")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _sortByPowerDesc = MutableStateFlow(true)
    val sortByPowerDesc: StateFlow<Boolean> = _sortByPowerDesc.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val records: StateFlow<List<EnergyConsumptionRecord>> = repository.recordsFlow

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun toggleSort() {
        _sortByPowerDesc.value = !_sortByPowerDesc.value
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.refreshData()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addRecord(name: String, category: String, power: Double, voltage: Double) {
        repository.addManualRecord(
            EnergyConsumptionRecord(
                facilityName = name,
                category = category,
                powerKw = power,
                voltageV = voltage
            )
        )
    }

    fun getStats(): EnergyStats = repository.calculateStats(records.value)
}

@Composable
fun RepositoryScreen(viewModel: Lesson9ViewModel = viewModel()) {
    val allRecords by viewModel.records.collectAsState()
    val selectedCat by viewModel.selectedCategory.collectAsState()
    val sortByPower by viewModel.sortByPowerDesc.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    val categories = listOf("Всі", "Промисловий", "Комерційний", "Побутовий")

    val filteredRecords = remember(allRecords, selectedCat, sortByPower) {
        val list = if (selectedCat == "Всі") allRecords else allRecords.filter { it.category == selectedCat }
        if (sortByPower) list.sortedByDescending { it.powerKw } else list.sortedBy { it.facilityName }
    }

    val stats = remember(allRecords) { viewModel.getStats() }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Додати", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Моніторинг: Repository & Data Source",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Агрегація джерел, кешування та розрахунок показників",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Сумарно", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            text = "%.1f кВт".format(stats.totalPowerKw),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Середнє", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            text = "%.1f кВт".format(stats.averagePowerKw),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Пік", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(
                            text = "%.1f кВт".format(stats.peakPowerKw),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCat == cat,
                            onClick = { viewModel.setCategory(cat) },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { viewModel.toggleSort() }) {
                    Text(if (sortByPower) "Сортування: Потужність ↓" else "Сортування: Назва A-Z", fontSize = 12.sp)
                }

                Button(
                    onClick = { viewModel.refresh() },
                    enabled = !isLoading,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Оновити з API", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.facilityName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = record.category,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "${record.voltageV} В",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "%.1f кВт".format(record.powerKw),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (record.powerKw > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = timeFormatter.format(Date(record.timestamp)),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Промисловий") }
        var powerStr by remember { mutableStateOf("") }
        var voltageStr by remember { mutableStateOf("220.0") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Новий замір енергоспоживання") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Назва об'єкта") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = powerStr,
                        onValueChange = { powerStr = it },
                        label = { Text("Потужність (кВт)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = voltageStr,
                        onValueChange = { voltageStr = it },
                        label = { Text("Напруга (В)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = powerStr.toDoubleOrNull() ?: 10.0
                        val v = voltageStr.toDoubleOrNull() ?: 220.0
                        if (name.isNotBlank()) {
                            viewModel.addRecord(name, category, p, v)
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Зберегти")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Скасувати")
                }
            }
        )
    }
}
