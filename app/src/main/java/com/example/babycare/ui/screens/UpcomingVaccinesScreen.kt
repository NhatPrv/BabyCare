package com.example.babycare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Vaccines
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
import com.example.babycare.data.model.Appointment
import com.example.babycare.data.model.Baby
import com.example.babycare.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingVaccinesScreen(
    viewModel: com.example.babycare.viewmodel.BabyViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBooking: () -> Unit
) {
    val appointments by viewModel.appointments.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val children by viewModel.childrenState.collectAsStateWithLifecycle()

    var selectedChildId by remember { mutableStateOf<String?>(viewModel.selectedChildId.value) }
    val filters = remember { listOf("Sắp tới", "Trễ hẹn", "Đã tiêm") }
    var selectedFilter by remember { mutableStateOf("Sắp tới") }

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

    LaunchedEffect(children) {
        if (selectedChildId == null || children.none { it.id == selectedChildId }) {
            selectedChildId = viewModel.selectedChildId.value ?: children.firstOrNull()?.id
        }
    }

    LaunchedEffect(selectedChildId) {
        if (!selectedChildId.isNullOrBlank()) {
            viewModel.selectChild(selectedChildId)
        }
    }

    // Filter and sort vaccine appointments for the selected baby
    val vaccineAppointments = remember(appointments, selectedChildId, selectedFilter) {
        if (selectedChildId == null) emptyList()
        else {
            val childAppts = appointments
                .filter { it.childId == selectedChildId }
                .filter { it.serviceType.contains("Tiêm chủng", ignoreCase = true) }

            val calToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            childAppts.filter { appt ->
                val apptDate = parseAppointmentDateTime(appt.date, appt.time)
                val calAppt = Calendar.getInstance().apply { time = apptDate }
                val isUpcomingOrToday = !calAppt.before(calToday)
                val isPast = calAppt.before(calToday)

                val cleanedStatus = appt.status.trim().lowercase()
                val isCompletedStatus = cleanedStatus == "đã tiêm" || cleanedStatus == "completed" || cleanedStatus == "thành công"
                val isCancelledStatus = cleanedStatus == "đã hủy" || cleanedStatus == "cancelled" || cleanedStatus == "từ chối"

                // Exclude cancelled/rejected appointments from standard filter tabs to avoid noise
                if (isCancelledStatus) return@filter false

                when (selectedFilter) {
                    "Sắp tới" -> !isCompletedStatus && isUpcomingOrToday
                    "Trễ hẹn" -> !isCompletedStatus && isPast
                    "Đã tiêm" -> isCompletedStatus
                    else -> true
                }
            }.sortedBy { parseAppointmentDateTime(it.date, it.time) }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Lịch hẹn tiêm chủng", fontWeight = FontWeight.Bold, color = PrimaryBlue) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !isLoading) {
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
            // 1. Child selector (Same style as BookingChildCard)
            if (children.isNotEmpty()) {
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Text(
                            text = "Chọn bé để xem lịch tiêm",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            children.forEach { child ->
                                BookingChildCard(
                                    child = child,
                                    selected = selectedChildId == child.id,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedChildId = child.id }
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Chưa có thông tin bé trong hồ sơ.",
                        color = TextSecondary,
                        modifier = Modifier.padding(24.dp),
                        fontSize = 13.sp
                    )
                }
            }

            // 2. Filter tabs (Modern Capsule Chips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val chipBgColor = if (isSelected) PrimaryBlue else Color.White
                    val chipTextColor = if (isSelected) Color.White else TextSecondary
                    val chipBorderColor = if (isSelected) PrimaryBlue else Color(0xFFE2E8F0)

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedFilter = filter },
                        shape = RoundedCornerShape(12.dp),
                        color = chipBgColor,
                        border = BorderStroke(1.dp, chipBorderColor)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter,
                                color = chipTextColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 3. Appointments list
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading && vaccineAppointments.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PrimaryBlue
                    )
                } else if (vaccineAppointments.isEmpty()) {
                    val filterEmptyMsg = when (selectedFilter) {
                        "Sắp tới" -> "Không có lịch hẹn tiêm sắp tới."
                        "Trễ hẹn" -> "Không có lịch hẹn trễ."
                        "Đã tiêm" -> "Chưa có lịch hẹn tiêm nào hoàn thành."
                        else -> "Không có lịch hẹn tiêm chủng nào."
                    }
                    EmptyVaccinesState(
                        msg = filterEmptyMsg,
                        onNavigateToBooking = onNavigateToBooking
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(vaccineAppointments) { appointment ->
                            VaccineAppointmentCard(
                                appointment = appointment,
                                onCancelClick = {
                                    viewModel.cancelAppointment(appointment.id)
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingChildCard(
    child: Baby,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isBoy = child.gender.trim().lowercase() in setOf("nam", "male", "boy", "m")
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(if (selected) PrimaryBlue.copy(alpha = 0.08f) else Color.Transparent)
                .border(if (selected) 2.dp else 1.dp, if (selected) PrimaryBlue else Color(0xFFE2E8F0), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = if (isBoy) R.drawable.avatar_boy else R.drawable.avatar_girl),
                contentDescription = child.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = child.name,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) PrimaryBlue else TextSecondary,
            fontSize = 13.sp
        )
        Text(
            text = child.dob.takeIf { it.isNotBlank() }?.let { calculateBabyAge(it) } ?: "",
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun VaccineAppointmentCard(
    appointment: Appointment,
    onCancelClick: (() -> Unit)? = null
) {
    val vaccineName = remember(appointment.serviceType) {
        extractVaccineName(appointment.serviceType)
    }

    val statusColor = when (appointment.status.trim().lowercase()) {
        "đã xác nhận", "thành công", "approved", "confirmed" -> SuccessGreen
        "đã tiêm", "vaccinated" -> PrimaryBlue
        "chờ xác nhận", "đang xử lý", "pending" -> WarningOrange
        "đã hủy", "cancelled", "từ chối" -> ErrorRed
        else -> TextSecondary
    }

    val statusBgColor = statusColor.copy(alpha = 0.1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Vaccine Icon + Vaccine Name + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = SecondaryBlue
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Vaccines,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = vaccineName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextDark,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = statusBgColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = appointment.status,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(14.dp))

            // Details rows
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailItem(
                    icon = Icons.Default.CalendarMonth,
                    text = appointment.date,
                    iconTint = PrimaryBlue
                )
                DetailItem(
                    icon = Icons.Default.AccessTime,
                    text = appointment.time,
                    iconTint = PrimaryBlue
                )
                DetailItem(
                    icon = Icons.Default.Person,
                    text = appointment.hospitalName,
                    iconTint = PrimaryBlue
                )
                if (!appointment.note.isNullOrBlank()) {
                    DetailItem(
                        icon = Icons.Default.Edit,
                        text = "Ghi chú: ${appointment.note}",
                        iconTint = PrimaryBlue
                    )
                }
                val isRejectedOrCancelled = remember(appointment.status) {
                    val st = appointment.status.trim().lowercase()
                    st == "từ chối" || st == "rejected" || st == "đã hủy" || st == "cancelled"
                }
                if (isRejectedOrCancelled && !appointment.rejectionReason.isNullOrBlank()) {
                    DetailItem(
                        icon = Icons.Default.Info,
                        text = "Lý do của bác sĩ: ${appointment.rejectionReason}",
                        iconTint = ErrorRed
                    )
                }
            }

            val isCancellable = remember(appointment.status) {
                val st = appointment.status.trim().lowercase()
                st == "chờ xác nhận" || st == "đang xử lý" || st == "pending" ||
                st == "đã xác nhận" || st == "approved" || st == "confirmed"
            }

            if (isCancellable && onCancelClick != null) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    var showDialog by remember { mutableStateOf(false) }

                    OutlinedButton(
                        onClick = { showDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = ErrorRed
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Hủy lịch",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed
                        )
                    }

                    if (showDialog) {
                        AlertDialog(
                            onDismissRequest = { showDialog = false },
                            title = { Text(text = "Hủy lịch hẹn", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark) },
                            text = { Text(text = "Bạn có chắc chắn muốn hủy lịch hẹn tiêm vắc xin này không?", fontSize = 14.sp, color = TextDark) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showDialog = false
                                        onCancelClick()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                                ) {
                                    Text("Đồng ý", color = Color.White)
                                }
                            },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = { showDialog = false }
                                ) {
                                    Text("Bỏ qua", color = TextDark)
                                }
                            },
                            containerColor = Color.White,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(icon: ImageVector, text: String, iconTint: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = TextDark
        )
    }
}

@Composable
private fun EmptyVaccinesState(msg: String, onNavigateToBooking: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(24.dp),
            color = SecondaryBlue
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MedicalServices,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = msg,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = TextDark,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Bạn có thể đăng ký đặt lịch tiêm chủng mới với bác sĩ.",
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNavigateToBooking,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("Đặt lịch tiêm ngay", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

private fun parseAppointmentDateTime(dateStr: String, timeStr: String): Date {
    val formats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy hh:mm a", Locale.getDefault()),
        SimpleDateFormat("EEE, dd MMM yyyy hh:mm a", Locale.US),
        SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    )
    val combined = "$dateStr $timeStr"
    for (format in formats) {
        try {
            return format.parse(combined) ?: continue
        } catch (_: Exception) {}
    }
    return Date(0)
}

private fun extractVaccineName(serviceType: String): String {
    val prefix = "Tiêm chủng ("
    if (serviceType.startsWith(prefix, ignoreCase = true) && serviceType.endsWith(")")) {
        return serviceType.substring(prefix.length, serviceType.length - 1)
    }
    return serviceType.replace("Tiêm chủng", "", ignoreCase = true)
        .replace("(", "")
        .replace(")", "")
        .trim()
        .ifBlank { "Vắc-xin" }
}
