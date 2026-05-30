package com.example.babycare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.babycare.ui.theme.*
import com.example.babycare.viewmodel.BabyViewModel
import com.example.babycare.data.model.VaccinationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccinationScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit
) {
    var selectedAge by remember { mutableStateOf("Tất cả") }
    val ages = listOf("Tất cả", "Sơ sinh", "2 tháng", "4 tháng", "6 tháng")
    
    val vaccineSchedule by viewModel.vaccineSchedule.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Lịch tiêm chủng", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Add */ }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (vaccineSchedule.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Chưa có lịch tiêm", fontSize = 16.sp, color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(SecondaryBlue.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(24.dp)
            ) {
                // 1. Progress Overview Card
                item {
                    val completedCount = vaccineSchedule.count { it.status == VaccinationStatus.COMPLETED }
                    val dueCount = vaccineSchedule.count { it.status == VaccinationStatus.OVERDUE }
                    val upcomingCount = vaccineSchedule.count { it.status == VaccinationStatus.UPCOMING }
                    val totalCount = vaccineSchedule.size
                    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Tiến độ tiêm chủng", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Hoàn thành", color = TextSecondary, fontSize = 14.sp)
                                Text("${(progress * 100).toInt()}%", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = PrimaryBlue,
                                trackColor = SecondaryBlue
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatBox(modifier = Modifier.weight(1f), count = completedCount.toString(), label = "ĐÃ TIÊM", color = SuccessGreen)
                                StatBox(modifier = Modifier.weight(1f), count = dueCount.toString(), label = "QUÁ HẠN", color = PrimaryBlue)
                                StatBox(modifier = Modifier.weight(1f), count = upcomingCount.toString(), label = "SẮP TỚI", color = WarningOrange)
                            }
                        }
                    }
                }

                // 2. Age Filter Chips
                item {
                    LazyRow(
                        modifier = Modifier.padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ages) { age ->
                            FilterChip(
                                selected = selectedAge == age,
                                onClick = { selectedAge = age },
                                label = { Text(age) },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color.White
                                )
                            )
                        }
                    }
                }

                // 3. Vaccine Items from ViewModel
                items(vaccineSchedule) { scheduleItem ->
                    val statusColor = when (scheduleItem.status) {
                        VaccinationStatus.COMPLETED -> SuccessGreen
                        VaccinationStatus.OVERDUE -> ErrorRed
                        VaccinationStatus.UPCOMING -> WarningOrange
                    }
                    val statusText = when (scheduleItem.status) {
                        VaccinationStatus.COMPLETED -> "ĐÃ TIÊM"
                        VaccinationStatus.OVERDUE -> "QUÁ HẠN"
                        VaccinationStatus.UPCOMING -> "SẮP TỚI"
                    }
                    
                    if (scheduleItem.status == VaccinationStatus.COMPLETED) {
                        VaccineItem(
                            name = scheduleItem.vaccine.name,
                            dose = "${scheduleItem.vaccine.monthAge} tháng tuổi",
                            date = scheduleItem.dueDate,
                            status = statusText,
                            color = statusColor
                        )
                    } else {
                        VaccineDetailCard(
                            name = scheduleItem.vaccine.name,
                            dose = "${scheduleItem.vaccine.monthAge} tháng tuổi",
                            targetDate = scheduleItem.dueDate,
                            status = statusText,
                            statusColor = statusColor,
                            onMarkDone = { viewModel.markVaccinationAsCompleted(scheduleItem.vaccine.id) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun StatBox(modifier: Modifier, count: String, label: String, color: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun TimelineHeader(title: String, isCompleted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(if (isCompleted) SuccessGreen else Color.White, CircleShape)
                .border(2.dp, if (isCompleted) SuccessGreen else PrimaryBlue, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
    }
}

@Composable
fun VaccineItem(name: String, dose: String, date: String, status: String, color: Color) {
    Row(modifier = Modifier.padding(start = 12.dp)) {
        // Connector line
        Box(modifier = Modifier.width(2.dp).height(100.dp).background(PrimaryBlue.copy(alpha = 0.2f)))
        
        Card(
            modifier = Modifier.padding(start = 28.dp, bottom = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(dose, color = TextSecondary, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tiêm ngày: $date", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text(status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun VaccineDetailCard(name: String, dose: String, targetDate: String, status: String, statusColor: Color, onMarkDone: () -> Unit = {}) {
    Row(modifier = Modifier.padding(start = 12.dp)) {
        Box(modifier = Modifier.width(2.dp).height(240.dp).background(PrimaryBlue.copy(alpha = 0.2f)))
        
        Card(
            modifier = Modifier.padding(start = 28.dp, bottom = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Surface(color = SecondaryBlue, shape = RoundedCornerShape(4.dp)) {
                        Text(status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(dose, color = TextSecondary, fontSize = 13.sp)
                
                Surface(
                    modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                    color = BackgroundLight,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ngày dự kiến: $targetDate", fontSize = 13.sp)
                    }
                }
                
                Text("TÁC DỤNG PHỤ THƯỜNG GẶP", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TextSecondary)
                Text("Đau đỏ, sốt nhẹ, quấy khóc tại chỗ tiêm. Thường hết sau 1-2 ngày.", fontSize = 12.sp, color = TextSecondary)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onMarkDone,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đã tiêm", fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = { /* TODO */ },
                        modifier = Modifier.background(SecondaryBlue, RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = PrimaryBlue)
                    }
                }
            }
        }
    }
}
