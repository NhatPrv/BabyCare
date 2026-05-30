package com.example.babycare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.babycare.data.model.Baby
import com.example.babycare.R
import com.example.babycare.ui.theme.*
import com.example.babycare.viewmodel.BabyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyChildrenScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onOpenChild: (String) -> Unit,
    onAddChild: () -> Unit
) {
    val scrollState = rememberScrollState()
    val children by viewModel.childrenState.collectAsStateWithLifecycle()
    val selectedBaby by viewModel.babyState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Hồ sơ các bé", fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onAddChild,
                        modifier = Modifier.padding(end = 8.dp).background(SecondaryBlue, CircleShape).size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = PrimaryBlue, modifier = Modifier.size(20.dp))
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
                .background(Color.White)
                .verticalScroll(scrollState)
        ) {
            // Horizontal Children List
            LazyRow(
                modifier = Modifier.padding(vertical = 16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(children) { child ->
                    ChildAvatarItem(
                        child = child,
                        isSelected = selectedBaby?.id == child.id,
                        onClick = { child.id?.let(onOpenChild) }
                    )
                }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .border(1.dp, Color(0xFFE2E8F0), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = TextSecondary)
                        }
                        Text("Add", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Children Cards
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                children.forEach { child ->
                    ChildDetailCard(
                        child = child,
                        onEdit = { child.id?.let(onOpenChild) },
                        onBook = onNavigateToHome
                    )
                }

                // Add Another Child Dotted Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f)),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onAddChild)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Thêm bé khác", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ChildAvatarItem(child: Baby, isSelected: Boolean, onClick: () -> Unit) {
    val isBoy = child.gender.lowercase() in setOf("nam", "male", "boy", "m")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else Color.Transparent)
                .border(if (isSelected) 2.dp else 0.dp, PrimaryBlue, CircleShape)
                .clickable(onClick = onClick)
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
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) PrimaryBlue else TextSecondary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun ChildDetailCard(child: Baby, onEdit: () -> Unit, onBook: () -> Unit) {
    val isBoy = child.gender.lowercase() in setOf("nam", "male", "boy", "m")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 2.dp,
        color = Color.White
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = if (isBoy) R.drawable.avatar_boy else R.drawable.avatar_girl),
                        contentDescription = child.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(child.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextDark)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isBoy) Icons.Default.Male else Icons.Default.Female,
                            contentDescription = null,
                            tint = if (isBoy) PrimaryBlue else Color(0xFFEC4899),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(child.gender, color = if (isBoy) PrimaryBlue else Color(0xFFEC4899), fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(child.dob, color = TextSecondary, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // dob đã ẩn theo yêu cầu
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onBook,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đặt lịch", fontSize = 14.sp)
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = TextDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Chỉnh sửa", color = TextDark, fontSize = 14.sp)
                }
            }
        }
    }
}
