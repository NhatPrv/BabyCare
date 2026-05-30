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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
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
import com.example.babycare.ui.theme.ErrorRed
import com.example.babycare.ui.theme.PrimaryBlue
import com.example.babycare.ui.theme.SecondaryBlue
import com.example.babycare.ui.theme.SuccessGreen
import com.example.babycare.ui.theme.TextDark
import com.example.babycare.ui.theme.TextSecondary
import com.example.babycare.ui.theme.WarningOrange
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
    val assessmentUi = remember(assessment) { assessment?.let { buildAssessmentUi(it.prediction ?: it.who_class) } }

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
            Text(
                text = "Thông tin của Bé",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                modifier = Modifier.padding(vertical = 20.dp)
            )

            error?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2))
                ) {
                    Text(
                        text = it,
                        color = ErrorRed,
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
                        shape = RoundedCornerShape(18.dp)
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
                        shape = RoundedCornerShape(18.dp)
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
                            shape = RoundedCornerShape(18.dp)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        OutlinedTextField(
                            value = height,
                            onValueChange = { height = it },
                            label = { Text("Chiều cao (cm)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !isLoading,
                            shape = RoundedCornerShape(18.dp)
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
                        RadioButton(selected = gender == "Nam", onClick = { gender = "Nam" }, enabled = !isLoading)
                        Text("Nam", color = TextDark)
                        Spacer(modifier = Modifier.width(24.dp))
                        RadioButton(selected = gender == "Nữ", onClick = { gender = "Nữ" }, enabled = !isLoading)
                        Text("Nữ", color = TextDark)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SecondaryBlue),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Đánh giá phát triển", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bấm đánh giá để xem bé đang phát triển ở mức nào theo model.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val currentBaby = Baby(
                                id = babyState?.id,
                                name = name,
                                dob = dob,
                                weight = weight.toDoubleOrNull() ?: 0.0,
                                height = height.toDoubleOrNull() ?: 0.0,
                                gender = gender
                            )
                            viewModel.assessGrowth(currentBaby)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = name.isNotBlank() && dob.isNotBlank() && !isLoading,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Đánh giá phát triển", modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }

            assessment?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        val ui = assessmentUi ?: buildAssessmentUi(result.prediction ?: result.who_class)
                        Text("Kết quả model", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = ui.icon,
                                contentDescription = null,
                                tint = ui.color,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ui.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                                Text(ui.subtitle, color = TextSecondary, fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        result.who_zscore?.let {
                            ResultRow(label = "WHO z-score", value = "${"%.2f".format(it)}")
                        }
                        result.bmi?.let {
                            ResultRow(label = "BMI", value = "${"%.1f".format(it)}")
                        }

                        result.recommendation?.takeIf { it.isNotBlank() }?.let {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Khuyến nghị", fontWeight = FontWeight.SemiBold, color = TextDark)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(it, color = TextSecondary, lineHeight = 20.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

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
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(text = "Lưu và Tiếp tục", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
            ) {
                Text("Quay lại")
            }
        }
    }
}

private data class AssessmentUi(
    val title: String,
    val subtitle: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun buildAssessmentUi(rawLabel: String?): AssessmentUi {
    return when (rawLabel?.trim()?.lowercase()) {
        "normal" -> AssessmentUi(
            title = "Bé khỏe mạnh",
            subtitle = "Chỉ số hiện tại của bé đang ở mức phù hợp.",
            color = SuccessGreen,
            icon = Icons.Default.CheckCircle
        )
        "overweight" -> AssessmentUi(
            title = "Bé có nguy cơ thừa cân",
            subtitle = "Nên theo dõi chế độ ăn và vận động của bé.",
            color = WarningOrange,
            icon = Icons.Default.WarningAmber
        )
        "obese" -> AssessmentUi(
            title = "Bé có nguy cơ thừa cân/béo phì",
            subtitle = "Cần điều chỉnh dinh dưỡng và hoạt động thể chất.",
            color = WarningOrange,
            icon = Icons.Default.WarningAmber
        )
        "thin" -> AssessmentUi(
            title = "Bé có nguy cơ suy dinh dưỡng",
            subtitle = "Hãy theo dõi dinh dưỡng và tăng trưởng của bé.",
            color = ErrorRed,
            icon = Icons.Default.WarningAmber
        )
        "severe_thin" -> AssessmentUi(
            title = "Bé có nguy cơ suy dinh dưỡng nặng",
            subtitle = "Nên đưa bé đi khám để được đánh giá sớm.",
            color = ErrorRed,
            icon = Icons.Default.WarningAmber
        )
        else -> AssessmentUi(
            title = "Chưa xác định rõ",
            subtitle = "Hãy kiểm tra lại thông tin đầu vào và thử đánh giá lại.",
            color = TextSecondary,
            icon = Icons.Default.WarningAmber
        )
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary)
        Text(value, fontWeight = FontWeight.SemiBold, color = TextDark)
    }
}
