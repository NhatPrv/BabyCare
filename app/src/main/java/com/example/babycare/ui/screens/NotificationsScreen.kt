package com.example.babycare.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.babycare.R
import com.example.babycare.data.model.Appointment
import com.example.babycare.data.model.VaccinationStatus
import com.example.babycare.data.model.VaccineSchedule
import com.example.babycare.ui.theme.*
import com.example.babycare.viewmodel.BabyViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ──────────────────────────────────────────────
// Data model dùng nội bộ cho danh sách thông báo
// ──────────────────────────────────────────────
data class NotifItem(
    val id: String,
    val title: String,
    val description: String,
    val time: String,
    val tag: String,
    val tagColor: Color,
    val bgColor: Color,
    val icon: ImageVector,
    val category: String,   // "Tiêm chủng" | "Lịch hẹn" | "Nhắc nhở"
    val isRead: Boolean = false,
    val sortKey: Int = 0,    // số lớn hơn = mới hơn, hiển thị trên đầu
    val timestamp: Long = 0L
)

fun parseDateToMillis(dateStr: String): Long {
    val formats = listOf(
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()),
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.US)
    )
    val cleaned = dateStr.trim()
    for (format in formats) {
        try {
            val parsed = format.parse(cleaned)
            if (parsed != null) return parsed.time
        } catch (_: Exception) {}
    }
    return System.currentTimeMillis()
}

fun parseDateTimeToMillis(dateTimeStr: String, fallbackDateStr: String): Long {
    val formats = listOf(
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()),
        SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
        SimpleDateFormat("EEE, dd MMM yyyy hh:mm a", Locale.getDefault()),
        SimpleDateFormat("EEE, dd MMM yyyy hh:mm a", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    )
    val cleaned = dateTimeStr.trim()
    for (format in formats) {
        try {
            val parsed = format.parse(cleaned)
            if (parsed != null) return parsed.time
        } catch (_: Exception) {}
    }
    return parseDateToMillis(fallbackDateStr)
}

fun isWithin24Hours(timestamp: Long): Boolean {
    val current = System.currentTimeMillis()
    val diff = abs(current - timestamp)
    return diff <= 24 * 60 * 60 * 1000L
}

// ──────────────────────────────────────────────
// Chuyển dữ liệu thật thành NotifItem
// ──────────────────────────────────────────────
fun appointmentsToNotifs(appointments: List<Appointment>, children: List<com.example.babycare.data.model.Baby>): List<NotifItem> =
    appointments.map { a ->
        val isVaccine = a.serviceType.contains("Tiêm chủng", ignoreCase = true)
        val tagColor = when (a.status) {
            "Đã xác nhận" -> Color(0xFF10B981)
            "Từ chối", "Đã hủy" -> Color(0xFFEF4444)
            "Đã tiêm" -> Color(0xFF6366F1)
            else -> Color(0xFFF59E0B)
        }
        val bgColor = when (a.status) {
            "Đã xác nhận" -> Color(0xFF10B981).copy(alpha = 0.07f)
            "Từ chối", "Đã hủy" -> Color(0xFFEF4444).copy(alpha = 0.07f)
            "Đã tiêm" -> Color(0xFF6366F1).copy(alpha = 0.07f)
            else -> Color.White
        }
        val statusLabel = when (a.status) {
            "Chờ xác nhận" -> "⏳ Đang chờ xác nhận"
            "Đã xác nhận" -> "✅ Đã được xác nhận"
            "Từ chối" -> "❌ Bị từ chối${a.rejectionReason?.let { " – $it" } ?: ""}"
            "Đã hủy" -> "🚫 Đã hủy${a.rejectionReason?.let { " – $it" } ?: ""}"
            "Đã tiêm" -> "💉 Đã tiêm xong"
            else -> a.status
        }
        val desc = buildString {
            append("${a.serviceType} tại ${a.hospitalName}")
            append(", ${a.date} lúc ${a.time}.")
            append(" $statusLabel")
            a.note?.let { append("\nGhi chú: $it") }
        }
        val ts = parseDateTimeToMillis("${a.date} ${a.time}", a.date)
        val childName = children.firstOrNull { it.id == a.childId }?.name
        val tag = childName ?: "Lịch hẹn"
        NotifItem(
            id = "appt_${a.id}",
            title = if (isVaccine) "Lịch tiêm chủng" else "Lịch khám / tư vấn",
            description = desc,
            time = a.date,
            tag = tag,
            tagColor = tagColor,
            bgColor = bgColor,
            icon = if (isVaccine) Icons.Default.Vaccines else Icons.Default.CalendarMonth,
            category = if (isVaccine) "Tiêm chủng" else "Lịch hẹn",
            timestamp = ts
        )
    }

fun vaccineScheduleToNotifs(schedules: List<VaccineSchedule>): List<NotifItem> =
    schedules
        .filter { it.status == VaccinationStatus.UPCOMING || it.status == VaccinationStatus.OVERDUE }
        .map { vs ->
            val isOverdue = vs.status == VaccinationStatus.OVERDUE
            val tagColor = if (isOverdue) Color(0xFFEF4444) else Color(0xFFF59E0B)
            val bgColor = if (isOverdue) Color(0xFFEF4444).copy(alpha = 0.07f) else Color(0xFFFEF3C7).copy(alpha = 0.6f)
            val ts = parseDateToMillis(vs.dueDate)
            NotifItem(
                id = "vacc_${vs.vaccine.id}_${vs.dueDate}",
                title = if (isOverdue) "⚠️ Quá hạn tiêm!" else "🔔 Nhắc nhở tiêm chủng",
                description = "${vs.vaccine.name} – hạn tiêm: ${vs.dueDate}. ${vs.vaccine.description}",
                time = vs.dueDate,
                tag = if (isOverdue) "Quá hạn" else "Sắp đến",
                tagColor = tagColor,
                bgColor = bgColor,
                icon = Icons.Default.Vaccines,
                category = "Tiêm chủng",
                timestamp = ts
            )
        }


// ──────────────────────────────────────────────
// Screen chính
// ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Tất cả") }
    val categories = listOf("Tất cả", "Tiêm chủng", "Lịch hẹn", "Nhắc nhở")

    // Scroll state + coroutine để scroll lên đầu khi đổi filter
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Giờ yên tĩnh — hình thức toggle
    var quietHoursEnabled by remember { mutableStateOf(true) }

    // Lấy dữ liệu từ ViewModel
    val appointments by viewModel.appointments.collectAsStateWithLifecycle()
    val vaccineSchedule by viewModel.vaccineSchedule.collectAsStateWithLifecycle()
    val readNotificationIds by viewModel.readNotificationIds.collectAsStateWithLifecycle()
    val children by viewModel.childrenState.collectAsStateWithLifecycle()

    // Gộp tất cả thông báo — mới nhất lên đầu (dùng index đảo ngược)
    val allNotifs = remember(appointments, vaccineSchedule, readNotificationIds, children) {
        val apptNotifs = appointmentsToNotifs(appointments, children)
            .mapIndexed { i, n ->
                val isRead = readNotificationIds.contains(n.id)
                n.copy(
                    isRead = isRead,
                    sortKey = appointments.size - i,
                    bgColor = if (isRead) Color.White else Color(0xFFF1F5F9)
                )
            }
        val vaccineNotifs = vaccineScheduleToNotifs(vaccineSchedule)
            .mapIndexed { i, n ->
                val isRead = readNotificationIds.contains(n.id)
                n.copy(
                    isRead = isRead,
                    sortKey = -i,
                    bgColor = if (isRead) Color.White else Color(0xFFFEF3C7).copy(alpha = 0.6f)
                )
            }
        (apptNotifs + vaccineNotifs).sortedByDescending { it.sortKey }
    }

    // Lọc theo category
    val filtered = remember(allNotifs, selectedCategory) {
        if (selectedCategory == "Tất cả") allNotifs
        else if (selectedCategory == "Nhắc nhở") allNotifs.filter { it.id.startsWith("vacc_") }
        else allNotifs.filter { it.category == selectedCategory }
    }

    // Scroll lên đầu khi đổi filter
    LaunchedEffect(selectedCategory) {
        coroutineScope.launch { listState.animateScrollToItem(0) }
    }

    // Nhóm theo "Sắp tới" và "Lịch sử"
    val upcoming = filtered.filter { n ->
        appointments.any { a ->
            (a.status == "Chờ xác nhận" || a.status == "Đã xác nhận") &&
                    n.description.contains(a.date)
        } || vaccineSchedule.any { vs ->
            (vs.status == VaccinationStatus.UPCOMING || vs.status == VaccinationStatus.OVERDUE) &&
                    n.description.contains(vs.dueDate)
        }
    }
    val history = filtered - upcoming.toSet()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Thông báo", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.markAllNotificationsAsRead(allNotifs.map { it.id })
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Xem tất cả",
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
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
            // ── Bộ lọc category ──
            LazyRow(
                modifier = Modifier.padding(vertical = 14.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (selectedCategory == category) Color.Transparent else Color(0xFFE2E8F0),
                            enabled = true,
                            selected = selectedCategory == category
                        )
                    )
                }
            }

            if (filtered.isEmpty()) {
                // Empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Không có thông báo nào", color = TextSecondary, fontSize = 15.sp)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Banner giờ yên tĩnh ──
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.ModeNight, contentDescription = null, tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Giờ yên tĩnh", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Tắt thông báo từ 22:00 – 07:00", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                                Switch(
                                    checked = quietHoursEnabled,
                                    onCheckedChange = { quietHoursEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color.White.copy(alpha = 0.4f)
                                    )
                                )
                            }
                        }
                    }

                    // ── Section: Sắp tới / đang chờ ──
                    if (upcoming.isNotEmpty()) {
                        item {
                            SectionTitle(
                                title = "Sắp tới",
                                badge = if (upcoming.size > 0) "${upcoming.size} mới" else ""
                            )
                        }
                        items(upcoming) { notif ->
                            NotificationItem(notif, onClick = {
                                if (!notif.isRead) {
                                    viewModel.markNotificationAsRead(notif.id)
                                }
                            })
                        }
                    }

                    // ── Section: Lịch sử ──
                    if (history.isNotEmpty()) {
                        item { SectionTitle(title = "Lịch sử", badge = "") }
                        items(history) { notif ->
                            NotificationItem(notif, onClick = {
                                if (!notif.isRead) {
                                    viewModel.markNotificationAsRead(notif.id)
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Composables phụ
// ──────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String, badge: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
        }
        if (badge.isNotEmpty()) {
            Surface(color = SecondaryBlue, shape = RoundedCornerShape(6.dp)) {
                Text(
                    badge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 12.sp,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NotificationItem(notif: NotifItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = notif.bgColor,
        shadowElevation = if (notif.bgColor == Color.White) 1.dp else 0.dp
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            // Icon category
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(notif.tagColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(notif.icon, contentDescription = null, tint = notif.tagColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(notif.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                        if (!notif.isRead) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(PrimaryBlue, CircleShape)
                            )
                        }
                    }
                    Text(notif.time, fontSize = 11.sp, color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(notif.description, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = notif.tagColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(notif.tagColor, CircleShape))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(notif.tag, color = notif.tagColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

