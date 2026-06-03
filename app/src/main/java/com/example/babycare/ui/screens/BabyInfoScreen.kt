package com.example.babycare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.babycare.data.model.Baby
import com.example.babycare.ui.theme.BackgroundLight
import com.example.babycare.ui.theme.PrimaryBlue
import com.example.babycare.ui.theme.SecondaryBlue
import com.example.babycare.ui.theme.TextDark
import com.example.babycare.ui.theme.TextSecondary
import com.example.babycare.viewmodel.BabyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyInfoScreen(
    viewModel: BabyViewModel,
    childId: String? = null,
    onNavigateAfterSave: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val babyState by viewModel.babyState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val assessment by viewModel.growthAssessment.collectAsStateWithLifecycle()

    LaunchedEffect(childId) {
        if (childId.isNullOrBlank()) {
            viewModel.clearSelectedChild()
        } else {
            viewModel.loadChildForEdit(childId)
        }
    }

    var name by remember(babyState) { mutableStateOf(babyState?.name ?: "") }
    var dob by remember(babyState) { mutableStateOf(babyState?.dob ?: "") }
    var weight by remember(babyState) { mutableStateOf(babyState?.weight?.toString() ?: "") }
    var height by remember(babyState) { mutableStateOf(babyState?.height?.toString() ?: "") }
    var gender by remember(babyState) { mutableStateOf(babyState?.gender ?: "Nam") }

    val scrollState = rememberScrollState()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (childId.isNullOrBlank()) "Thêm thông tin bé" else "Chỉnh sửa thông tin bé",
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BackgroundLight)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            error?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2))
                ) {
                    Text(
                        text = it,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Thông tin cơ bản", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Tên của bé") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = dob,
                        onValueChange = { dob = it },
                        label = { Text("Ngày sinh (DD/MM/YYYY)") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        placeholder = { Text("Ví dụ: 20/05/2024") },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = weight,
                            onValueChange = { weight = it },
                            label = { Text("Cân nặng (kg)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !isLoading,
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors()
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        OutlinedTextField(
                            value = height,
                            onValueChange = { height = it },
                            label = { Text("Chiều cao (cm)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !isLoading,
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Giới tính",
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = gender == "Nam",
                            onClick = { gender = "Nam" },
                            enabled = !isLoading,
                            colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue, unselectedColor = Color(0xFF94A3B8))
                        )
                        Text("Nam", color = TextDark)
                        Spacer(modifier = Modifier.width(24.dp))
                        RadioButton(
                            selected = gender == "Nữ",
                            onClick = { gender = "Nữ" },
                            enabled = !isLoading,
                            colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue, unselectedColor = Color(0xFF94A3B8))
                        )
                        Text("Nữ", color = TextDark)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val baby = Baby(
                        id = babyState?.id,
                        name = name,
                        dob = dob,
                        weight = weight.toDoubleOrNull() ?: 0.0,
                        height = height.toDoubleOrNull() ?: 0.0,
                        gender = gender
                    )
                    viewModel.updateBabyInfo(baby, onSuccess = onNavigateAfterSave)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && dob.isNotBlank() && !isLoading,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(text = "Lưu và Tiếp tục", fontSize = 18.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryBlue,
    unfocusedBorderColor = Color(0xFFCBD5E1),
    focusedLabelColor = PrimaryBlue,
    unfocusedLabelColor = TextSecondary,
    cursorColor = PrimaryBlue,
    focusedTextColor = TextDark,
    unfocusedTextColor = TextDark,
    focusedTrailingIconColor = PrimaryBlue,
    unfocusedTrailingIconColor = TextSecondary,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White
)
