package com.example.babycare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.babycare.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.babycare.ui.theme.*
import com.example.babycare.viewmodel.BabyViewModel
import com.example.babycare.data.model.VaccineSchedule
import com.example.babycare.data.model.VaccinationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccinationScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit
) {
    val initialFilter by viewModel.initialVaccineFilter.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf("Tất cả") }
    
    LaunchedEffect(initialFilter) {
        selectedFilter = initialFilter
    }

    val filters = listOf("Tất cả", "Đã tiêm", "Chưa tới", "Bị trễ")
    
    val vaccineSchedule by viewModel.vaccineSchedule.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val children by viewModel.childrenState.collectAsStateWithLifecycle()
    val selectedChildId by viewModel.selectedChildId.collectAsStateWithLifecycle()

    val filteredSchedule = remember(vaccineSchedule, selectedFilter) {
        when (selectedFilter) {
            "Đã tiêm" -> vaccineSchedule.filter { it.status == VaccinationStatus.COMPLETED }
            "Chưa tới" -> vaccineSchedule.filter { it.status == VaccinationStatus.UPCOMING }
            "Bị trễ" -> vaccineSchedule.filter { it.status == VaccinationStatus.OVERDUE }
            else -> vaccineSchedule
        }
    }

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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BackgroundLight)
        ) {
            // Child Selector LazyRow
            if (children.isNotEmpty()) {
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(
                            text = "Chọn bé để xem lịch tiêm",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 24.dp)
                        ) {
                            items(children) { child ->
                                val isBoy = child.gender.trim().lowercase() in setOf("nam", "male", "boy", "m")
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { viewModel.selectChild(child.id) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(if (selectedChildId == child.id) PrimaryBlue.copy(alpha = 0.1f) else Color.Transparent)
                                            .border(if (selectedChildId == child.id) 2.dp else 0.dp, PrimaryBlue, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.foundation.Image(
                                            painter = painterResource(id = if (isBoy) R.drawable.avatar_boy else R.drawable.avatar_girl),
                                            contentDescription = child.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    }
                                    Text(
                                        text = child.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedChildId == child.id) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedChildId == child.id) PrimaryBlue else TextSecondary,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (vaccineSchedule.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Chưa có lịch tiêm", fontSize = 16.sp, color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
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
                                    StatBox(modifier = Modifier.weight(1f), count = dueCount.toString(), label = "BỊ TRỄ", color = ErrorRed)
                                    StatBox(modifier = Modifier.weight(1f), count = upcomingCount.toString(), label = "CHƯA TỚI", color = WarningOrange)
                                }
                            }
                        }
                    }

                    // 2. Status Filter Chips
                    item {
                        LazyRow(
                            modifier = Modifier.padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filters) { filter ->
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text(filter) },
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

                    // 3. Vaccine Items
                    if (filteredSchedule.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val emptyMessage = when (selectedFilter) {
                                    "Đã tiêm" -> "Chưa có mũi tiêm nào hoàn thành"
                                    "Chưa tới" -> "Không có mũi tiêm nào chưa tới tuổi"
                                    "Bị trễ" -> "Không có mũi tiêm nào bị trễ"
                                    else -> "Không có mũi tiêm nào"
                                }
                                Text(emptyMessage, fontSize = 14.sp, color = TextSecondary)
                            }
                        }
                    } else {
                        items(filteredSchedule) { scheduleItem ->
                            VaccineCard(
                                scheduleItem = scheduleItem,
                                onToggleStatus = { isCompleted ->
                                    viewModel.toggleVaccinationStatus(scheduleItem.vaccine.id, isCompleted)
                                }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
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
fun VaccineCard(
    scheduleItem: VaccineSchedule,
    onToggleStatus: (Boolean) -> Unit
) {
    val isCompleted = scheduleItem.status == VaccinationStatus.COMPLETED
    val statusColor = when (scheduleItem.status) {
        VaccinationStatus.COMPLETED -> SuccessGreen
        VaccinationStatus.OVERDUE -> ErrorRed
        VaccinationStatus.UPCOMING -> WarningOrange
    }
    val statusText = when (scheduleItem.status) {
        VaccinationStatus.COMPLETED -> "Đã tiêm"
        VaccinationStatus.OVERDUE -> "Chưa tiêm (Bị trễ)"
        VaccinationStatus.UPCOMING -> "Chưa tiêm (Chưa đủ tháng)"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scheduleItem.vaccine.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Độ tuổi khuyên dùng: ${scheduleItem.vaccine.monthAge} tháng tuổi",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ngày dự kiến: ${scheduleItem.dueDate}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))

            Checkbox(
                checked = isCompleted,
                onCheckedChange = { onToggleStatus(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = SuccessGreen,
                    uncheckedColor = TextSecondary
                ),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
