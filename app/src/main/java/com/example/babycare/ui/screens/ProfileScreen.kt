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
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToChildren: () -> Unit,
    onNavigateToAppointments: () -> Unit,
    onNavigateToVaccinations: () -> Unit
) {
    val baby by viewModel.babyState.collectAsStateWithLifecycle()
    val parentStats by viewModel.parentStats.collectAsStateWithLifecycle()
    val parentProfile by viewModel.parentProfile.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // Dialog state for editing account info
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }

    // Edit Account Info Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Thông tin tài khoản", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it; editError = null },
                        label = { Text("Họ và tên") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it; editError = null },
                        label = { Text("Số điện thoại") },
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (editError != null) {
                        Text(editError!!, color = Color(0xFFEF4444), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isBlank()) {
                        editError = "Vui lòng nhập họ và tên"
                        return@TextButton
                    }
                    viewModel.updateParentProfile(
                        fullName = editName,
                        phone = editPhone,
                        onSuccess = { showEditDialog = false },
                        onError = { editError = it }
                    )
                }) {
                    Text("Lưu", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

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
                    IconButton(onClick = {
                        editName = parentProfile?.fullName ?: ""
                        editPhone = parentProfile?.phone ?: ""
                        editError = null
                        showEditDialog = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = androidx.compose.ui.Modifier
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
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .size(72.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.avatar_parent),
                            contentDescription = "Avatar phụ huynh",
                            contentScale = ContentScale.Crop,
                            modifier = androidx.compose.ui.Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                    Spacer(modifier = androidx.compose.ui.Modifier.size(16.dp))
                    Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                        Text(
                            text = parentProfile?.fullName?.takeIf { it.isNotBlank() } ?: "Phụ huynh",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = parentProfile?.phone?.takeIf { it.isNotBlank() }?.let { "📞 $it" } ?: "Tài khoản phụ huynh",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                        Text(
                            text = baby?.let { "Quản lý ${it.name} • ${calculateBabyAge(it.dob)}" }
                                ?: "Chưa có thông tin bé",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = androidx.compose.ui.Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                ProfileStatCard(
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                    value = parentStats?.childrenCount?.toString() ?: "-",
                    label = "Con",
                    onClick = onNavigateToChildren
                )
                ProfileStatCard(
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                    value = parentStats?.appointmentsCount?.toString() ?: "-",
                    label = "Lịch hẹn",
                    onClick = onNavigateToAppointments
                )
                ProfileStatCard(
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                    value = parentStats?.completedVaccinationsCount?.toString() ?: "-",
                    label = "Tiêm chủng",
                    onClick = onNavigateToVaccinations
                )
            }

            Spacer(modifier = androidx.compose.ui.Modifier.height(24.dp))

            Text(
                text = "Cài đặt",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))

            ProfileMenuCard(
                title = "Thông tin tài khoản",
                subtitle = parentProfile?.let { p ->
                    buildString {
                        if (p.fullName.isNotBlank()) append(p.fullName)
                        if (p.phone.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(p.phone)
                        }
                    }.takeIf { it.isNotBlank() }
                } ?: "Cập nhật họ tên, số điện thoại",
                icon = Icons.Default.Person,
                onClick = {
                    editName = parentProfile?.fullName ?: ""
                    editPhone = parentProfile?.phone ?: ""
                    editError = null
                    showEditDialog = true
                }
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

            Spacer(modifier = androidx.compose.ui.Modifier.height(20.dp))

            Surface(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,

                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.logout()
                            onLogout()
                        }
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
private fun ProfileStatCard(
    modifier: Modifier,
    value: String,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
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
    hasSwitch: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .then(if (onClick != null) androidx.compose.ui.Modifier.clickable { onClick() } else androidx.compose.ui.Modifier),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = androidx.compose.ui.Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SecondaryBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryBlue)
            }
            Spacer(modifier = androidx.compose.ui.Modifier.size(12.dp))
            Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
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

