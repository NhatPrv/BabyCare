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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

@Composable
fun HomeScreen(
    viewModel: BabyViewModel,
    onNavigateToVaccination: () -> Unit,
    onNavigateToChatbot: () -> Unit,
    onNavigateToBooking: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAssessment: () -> Unit
) {
    val baby by viewModel.babyState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BabyStatusInfoCard(
                                modifier = Modifier.weight(1f),
                                title = "TIÊM KẾP TỚI",
                                value = "Xem lịch tiêm",
                                time = "Xem chi tiết",
                                icon = Icons.Default.Edit
                            )
                            BabyStatusInfoCard(
                                modifier = Modifier.weight(1f),
                                title = "LỊCH HẸN TỚI",
                                value = "Đặt lịch khám",
                                time = "Đặt ngay",
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
                        subtitle = "Xem lịch tiêm",
                        icon = Icons.Default.Security,
                        containerColor = Color.White,
                        onClick = onNavigateToVaccination
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
                        title = "Thêm bé",
                        subtitle = "Hồ sơ mới",
                        icon = Icons.Default.Add,
                        containerColor = Color.White,
                        onClick = { /* TODO */ }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                QuickActionCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Đánh giá phát triển",
                    subtitle = "Đánh giá nhanh",
                    icon = Icons.Default.MonitorWeight,
                    containerColor = Color.White,
                    onClick = onNavigateToAssessment
                )
            }

            // 4. Recent Updates
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cập nhật gần đây", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Text("Xem tất cả", color = PrimaryBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UpdateItem(
                    title = "Nhắc nhở tiêm chủng",
                    description = "Long sắp đến hạn tiêm mũi DTaP trong 12 ngày nữa. Nhấn để xem chi tiết.",
                    time = "2 giờ trước",
                    icon = Icons.Default.Edit // Replace with syringe
                )
                UpdateItem(
                    title = "Cập nhật tăng trưởng",
                    description = "Đã đến lúc cập nhật cân nặng và chiều cao của Long cho tháng này.",
                    time = "Hôm qua",
                    icon = Icons.Default.MonitorWeight
                )
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
fun UpdateItem(title: String, description: String, time: String, icon: ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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

        var months = today.get(Calendar.MONTH) - birthCalendar.get(Calendar.MONTH)
        var years = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)

        if (months < 0) {
            years--
            months += 12
        }

        val days = today.get(Calendar.DAY_OF_MONTH) - birthCalendar.get(Calendar.DAY_OF_MONTH)

        when {
            years > 0 -> "$years tuổi $months tháng"
            months > 0 -> "$months tháng ${if (days >= 0) days else 0} ngày"
            else -> "${if (days >= 0) days else 0} ngày tuổi"
        }
    } catch (_: Exception) {
        "Không rõ tuổi"
    }
}
