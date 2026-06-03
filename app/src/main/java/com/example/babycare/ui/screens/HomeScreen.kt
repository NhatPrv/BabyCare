package com.example.babycare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.babycare.R
import com.example.babycare.ui.theme.*
import com.example.babycare.viewmodel.BabyViewModel
import java.text.SimpleDateFormat
import java.util.*

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: BabyViewModel,
    onNavigateToVaccination: () -> Unit,
    onNavigateToChatbot: () -> Unit,
    onNavigateToBooking: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToGeneralVaccines: () -> Unit,
    onNavigateToUpcomingVaccines: () -> Unit,
    onAddChild: () -> Unit
) {
    val baby by viewModel.babyState.collectAsStateWithLifecycle()
    val children by viewModel.childrenState.collectAsStateWithLifecycle()
    val appointments by viewModel.appointments.collectAsStateWithLifecycle()
    val vaccineSchedule by viewModel.vaccineSchedule.collectAsStateWithLifecycle()
    val readNotificationIds by viewModel.readNotificationIds.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val newNotifications = remember(appointments, vaccineSchedule, readNotificationIds, children) {
        val apptNotifs = appointmentsToNotifs(appointments, children)
        val vaccineNotifs = vaccineScheduleToNotifs(vaccineSchedule)
        (apptNotifs + vaccineNotifs)
            .filter { !readNotificationIds.contains(it.id) }
            .filter { isWithin24Hours(it.timestamp) }
            .sortedByDescending { it.timestamp }
    }


    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.fetchDataFromServer()
            while (true) {
                delay(5000)
                viewModel.fetchDataFromServer()
            }
        }
    }

    // Find the closest upcoming vaccine appointment for this baby
    val closestVaccineAppt = remember(appointments, baby) {
        if (baby == null) null
        else {
            appointments
                .filter { it.childId == baby!!.id }
                .filter { it.serviceType.contains("Tiêm chủng", ignoreCase = true) }
                .filter { it.status == "Chờ xác nhận" || it.status == "Đã xác nhận" }
                .sortedBy { appt ->
                    val formats = listOf(
                        SimpleDateFormat("EEE, dd MMM yyyy hh:mm a", Locale.getDefault()),
                        SimpleDateFormat("EEE, dd MMM yyyy hh:mm a", Locale.US),
                        SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()),
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    )
                    val combined = "${appt.date} ${appt.time}"
                    var parsedDate: Date? = null
                    for (format in formats) {
                        try {
                            parsedDate = format.parse(combined)
                            if (parsedDate != null) break
                        } catch (_: Exception) {}
                    }
                    parsedDate ?: Date(Long.MAX_VALUE)
                }
                .firstOrNull()
        }
    }

    val displayVaccineName = remember(closestVaccineAppt) {
        if (closestVaccineAppt == null) {
            "Chưa có lịch"
        } else {
            val serviceType = closestVaccineAppt.serviceType
            val prefix = "Tiêm chủng ("
            if (serviceType.startsWith(prefix) && serviceType.endsWith(")")) {
                serviceType.substring(prefix.length, serviceType.length - 1)
            } else {
                serviceType.replace("Tiêm chủng", "").replace("(", "").replace(")", "").trim().takeIf { it.isNotEmpty() } ?: "Vắc-xin"
            }
        }
    }

    val displayVaccineTime = remember(closestVaccineAppt) {
        if (closestVaccineAppt != null) {
            "Ngày tiêm: ${closestVaccineAppt.date}"
        } else {
            "Đặt lịch ngay"
        }
    }

    // Get greeting text based on time of day
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Chào buổi sáng"
            in 12..17 -> "Chào buổi chiều"
            else -> "Chào buổi tối"
        }
    }

    Column(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(scrollState)
    ) {
            // 1. Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onNavigateToProfile() }
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.avatar_parent),
                            contentDescription = "Avatar phụ huynh",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("$greeting,", fontSize = 14.sp, color = TextSecondary)
                        Text("Đặng Nhật", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    }
                }
                
                Box {
                    IconButton(
                        onClick = onNavigateToNotifications,
                        modifier = Modifier.background(Color.White, CircleShape)
                    ) {
                        Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = TextDark)
                    }
                    // Red Dot Badge
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(ErrorRed, CircleShape)
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                    )
                }
            }

            // 2. Baby Highlight Card
            if (baby != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        var showBabyMenu by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBabyMenu = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.ChildCare, contentDescription = null, tint = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(baby!!.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Text(calculateBabyAge(baby!!.dob), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                                    }
                                }
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                            }

                            DropdownMenu(
                                expanded = showBabyMenu,
                                onDismissRequest = { showBabyMenu = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                children.forEach { child ->
                                    DropdownMenuItem(
                                        text = { Text(child.name, fontWeight = FontWeight.SemiBold, color = TextDark) },
                                        onClick = {
                                            showBabyMenu = false
                                            viewModel.selectChild(child.id)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BabyStatusInfoCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        viewModel.setInitialVaccineFilter("Tất cả")
                                        onNavigateToVaccination()
                                    },
                                title = "XEM LỊCH TIÊM",
                                value = "Lịch sơ sinh",
                                time = "Xem chi tiết",
                                icon = Icons.Default.Edit
                            )
                            BabyStatusInfoCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (closestVaccineAppt == null) {
                                            onNavigateToBooking()
                                        } else {
                                            onNavigateToUpcomingVaccines()
                                        }
                                    },
                                title = "MŨI TIÊM TỚI",
                                value = displayVaccineName,
                                time = displayVaccineTime,
                                icon = Icons.Default.CalendarToday
                            )
                        }
                    }
                }
            } else {
                // Hiển thị khi chưa có thông tin bé
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Chưa có thông tin bé", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Thêm thông tin để bắt đầu", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    }
                }
            }

            // 3. Quick Actions
                SectionHeader(title = "Hành động nhanh", modifier = Modifier.padding(top = 24.dp))
            
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Tiêm chủng",
                        subtitle = "Danh sách mũi tiêm",
                        icon = Icons.Default.Security,
                        containerColor = Color.White,
                        onClick = onNavigateToGeneralVaccines
                    )
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Trợ lý AI",
                        subtitle = "Đặt câu hỏi",
                        icon = Icons.Default.SmartToy,
                        containerColor = Color.White,
                        onClick = onNavigateToChatbot
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Đặt lịch khám",
                        subtitle = "Lên lịch bác sĩ",
                        icon = Icons.Default.CalendarMonth,
                        containerColor = Color.White,
                        onClick = onNavigateToBooking
                    )
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Thêm hồ sơ bé",
                        subtitle = "Hồ sơ mới",
                        icon = Icons.Default.Add,
                        containerColor = Color.White,
                        onClick = onAddChild
                    )
                }
            }

            // 4. Recent Updates
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Thông báo mới", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Text(
                    text = "Xem tất cả",
                    color = PrimaryBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateToNotifications() }
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (newNotifications.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White
                    ) {
                        Box(
                            modifier = Modifier.padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Không có thông báo chưa xem trong 24h qua.", color = TextSecondary, fontSize = 14.sp)
                        }
                    }
                } else {
                    newNotifications.forEach { notif ->
                        UpdateItem(
                            title = notif.title,
                            description = notif.description,
                            time = notif.time,
                            icon = notif.icon,
                            onClick = {
                                viewModel.markNotificationAsRead(notif.id)
                                onNavigateToNotifications()
                            }
                        )
                    }
                }
            }

            // Keep only a small breathing room at the end of the list.
            Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun BabyStatusInfoCard(modifier: Modifier, title: String, value: String, time: String, icon: ImageVector) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.15f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(time, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextDark,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

@Composable
fun QuickActionCard(modifier: Modifier, title: String, subtitle: String, icon: ImageVector, containerColor: Color, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SecondaryBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
fun UpdateItem(title: String, description: String, time: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = Color.White
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SecondaryBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                    Text(time, fontSize = 12.sp, color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
            }
        }
    }
}

// Hàm tính tuổi bé
fun calculateBabyAge(dobString: String): String {
    return try {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dob = dateFormat.parse(dobString) ?: return "Không rõ tuổi"
        val today = Calendar.getInstance()
        val birthCalendar = Calendar.getInstance()
        birthCalendar.time = dob

        var years = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
        var months = today.get(Calendar.MONTH) - birthCalendar.get(Calendar.MONTH)
        var days = today.get(Calendar.DAY_OF_MONTH) - birthCalendar.get(Calendar.DAY_OF_MONTH)

        if (days < 0) {
            months--
            val prevMonth = (today.get(Calendar.MONTH) - 1 + 12) % 12
            val tempCal = Calendar.getInstance().apply {
                set(Calendar.MONTH, prevMonth)
                set(Calendar.YEAR, today.get(Calendar.YEAR))
            }
            days += tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }

        if (months < 0) {
            years--
            months += 12
        }

        when {
            years > 0 -> "$years tuổi $months tháng"
            months > 0 -> "$months tháng $days ngày"
            else -> "$days ngày tuổi"
        }
    } catch (_: Exception) {
        "Không rõ tuổi"
    }
}
