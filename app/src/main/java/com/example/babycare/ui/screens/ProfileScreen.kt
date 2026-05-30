package com.example.babycare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.babycare.R
import com.example.babycare.ui.theme.BackgroundLight
import com.example.babycare.ui.theme.PrimaryBlue
import com.example.babycare.ui.theme.SecondaryBlue
import com.example.babycare.ui.theme.SuccessGreen
import com.example.babycare.ui.theme.TextDark
import com.example.babycare.ui.theme.TextSecondary
import com.example.babycare.viewmodel.BabyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit
) {
    val baby by viewModel.babyState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Hồ sơ", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
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
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.avatar_parent),
                            contentDescription = "Avatar phụ huynh",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Đặng Nhật", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Tài khoản phụ huynh", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        Text(
                            text = baby?.let { "Quản lý ${it.name} • ${calculateBabyAge(it.dob)}" }
                                ?: "Chưa có thông tin bé",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ProfileStatCard(modifier = Modifier.weight(1f), value = "2", label = "Con")
                ProfileStatCard(modifier = Modifier.weight(1f), value = "14", label = "Lịch hẹn")
                ProfileStatCard(modifier = Modifier.weight(1f), value = "9", label = "Tiêm chủng")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Cài đặt",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(modifier = Modifier.height(12.dp))

            ProfileMenuCard(
                title = "Thông tin tài khoản",
                subtitle = "Cập nhật họ tên, số điện thoại",
                icon = Icons.Default.Person
            )
            ProfileMenuCard(
                title = "Thông báo",
                subtitle = "Tiêm chủng, lịch hẹn và nhắc nhở",
                icon = Icons.Default.Notifications,
                hasSwitch = true
            )
            ProfileMenuCard(
                title = "BabyCare AI",
                subtitle = "Quản lý trợ lý thông minh",
                icon = Icons.Default.SmartToy
            )
            ProfileMenuCard(
                title = "Quyền riêng tư & bảo mật",
                subtitle = "Quyền ứng dụng và dữ liệu",
                icon = Icons.Default.Shield
            )
            ProfileMenuCard(
                title = "Trợ giúp & hỗ trợ",
                subtitle = "Câu hỏi thường gặp và liên hệ",
                icon = Icons.AutoMirrored.Filled.HelpOutline
            )

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* TODO */ }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Đăng xuất", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                        Text("Thoát khỏi thiết bị này", fontSize = 12.sp, color = TextSecondary)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ProfileStatCard(modifier: Modifier, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 18.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Text(label, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun ProfileMenuCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    hasSwitch: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SecondaryBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryBlue)
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = TextDark)
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
            if (hasSwitch) {
                Switch(
                    checked = true,
                    onCheckedChange = {},
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SuccessGreen
                    )
                )
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
            }
        }
    }
}

