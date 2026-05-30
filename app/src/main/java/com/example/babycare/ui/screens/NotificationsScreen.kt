package com.example.babycare.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.babycare.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onNavigateBack: () -> Unit) {
    var selectedCategory by remember { mutableStateOf("Tất cả") }
    val categories = listOf("Tất cả", "Tiêm chủng", "Lịch hẹn", "Nhắc nhở")

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Thông báo", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Mark all as read */ }) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, tint = TextSecondary)
                    }
                    Box(modifier = Modifier.padding(end = 16.dp).size(32.dp).clip(CircleShape).background(Color.LightGray))
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
            LazyRow(
                modifier = Modifier.padding(vertical = 16.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ModeNight, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Giờ yên tĩnh", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Tắt thông báo từ 22:00 - 07:00", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                            Switch(
                                checked = true,
                                onCheckedChange = {},
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.White.copy(alpha = 0.4f))
                            )
                        }
                    }
                }

                item { SectionTitle("Hôm nay", "2 mới") }

                item {
                    NotificationItem(
                        title = "Nhắc nhở tiêm chủng",
                        time = "10:30 SA",
                        description = "Long đến hạn tiêm mũi DTaP 6 tháng hôm nay. Nhấn để xem chi tiết.",
                        status = "Long • DTaP",
                        containerColor = Color(0xFF10B981).copy(alpha = 0.1f),
                        accentColor = Color(0xFF10B981)
                    )
                }

                item {
                    NotificationItem(
                        title = "Lịch khám sắp tới",
                        time = "8:15 SA",
                        description = "Khám nhi khoa ngày mai lúc 14:00 tại Bệnh viện Đa khoa Thành phố.",
                        status = "Ánh",
                        containerColor = Color.White,
                        accentColor = WarningOrange,
                        showDetails = true
                    )
                }

                item { SectionTitle("Tuần này", "") }

                item {
                    NotificationItem(
                        title = "Thông tin sức khỏe hàng tuần",
                        time = "Hôm qua",
                        description = "Ánh sắp đến mốc 24 tháng. Tôi đã chuẩn bị tóm tắt những điều cần chú ý.",
                        status = "Hỏi trợ lý AI",
                        containerColor = Color(0xFFCFFAFE).copy(alpha = 0.5f),
                        accentColor = PrimaryBlue,
                        isAI = true
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, badge: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
        }
        if (badge.isNotEmpty()) {
            Surface(color = SecondaryBlue, shape = RoundedCornerShape(4.dp)) {
                Text(badge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp, color = PrimaryBlue)
            }
        }
    }
}

@Composable
fun NotificationItem(
    title: String,
    time: String,
    description: String,
    status: String,
    containerColor: Color,
    accentColor: Color,
    showDetails: Boolean = false,
    isAI: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        shadowElevation = if (containerColor == Color.White) 1.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                Text(time, fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(description, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = accentColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isAI) Icon(Icons.Default.Face, contentDescription = null, tint = accentColor, modifier = Modifier.size(12.dp))
                        else Box(modifier = Modifier.size(6.dp).background(accentColor, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(status, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (showDetails) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Xem chi tiết", color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
