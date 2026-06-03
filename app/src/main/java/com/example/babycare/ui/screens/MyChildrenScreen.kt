package com.example.babycare.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.babycare.R
import com.example.babycare.data.model.Baby
import com.example.babycare.data.remote.ChildProfileResponse
import com.example.babycare.data.remote.ChildMeasurementDto
import com.example.babycare.data.remote.GrowthAssessmentRecordDto
import com.example.babycare.ui.theme.BackgroundLight
import com.example.babycare.ui.theme.ErrorRed
import com.example.babycare.ui.theme.PrimaryBlue
import com.example.babycare.ui.theme.SecondaryBlue
import com.example.babycare.ui.theme.SuccessGreen
import com.example.babycare.ui.theme.TextDark
import com.example.babycare.ui.theme.TextSecondary
import com.example.babycare.ui.theme.WarningOrange
import com.example.babycare.viewmodel.BabyViewModel
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyChildrenScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onAddChild: () -> Unit
) {
    val scrollState = rememberScrollState()
    val children by viewModel.childrenState.collectAsStateWithLifecycle()
    val selectedBaby by viewModel.babyState.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.childProfile.collectAsStateWithLifecycle()
    val selectedChildId by viewModel.selectedChildId.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var isEditing by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var draftName by remember(selectedBaby?.id) { mutableStateOf(selectedBaby?.name ?: "") }
    var draftDob by remember(selectedBaby?.id) { mutableStateOf(selectedBaby?.dob ?: "") }
    var draftWeight by remember(selectedBaby?.id) { mutableStateOf(selectedBaby?.weight?.toString() ?: "") }
    var draftHeight by remember(selectedBaby?.id) { mutableStateOf(selectedBaby?.height?.toString() ?: "") }
    var draftGender by remember(selectedBaby?.id) { mutableStateOf(selectedBaby?.gender ?: "Nam") }

    val currentChild = selectedProfile?.child ?: selectedBaby
    val currentAssessment = selectedProfile?.latestAssessment
        ?: selectedProfile?.measurementHistory?.lastOrNull()?.assessment
    val measurementHistory = selectedProfile?.measurementHistory.orEmpty()

    LaunchedEffect(children, selectedChildId) {
        if (children.isNotEmpty() && selectedChildId == null) {
            viewModel.selectChild(children.first().id)
        } else if (selectedChildId != null && children.none { it.id == selectedChildId }) {
            viewModel.selectChild(children.firstOrNull()?.id)
        }
    }

    LaunchedEffect(currentChild?.id) {
        if (currentChild != null) {
            isEditing = false
            draftName = currentChild.name
            draftDob = currentChild.dob
            draftWeight = currentChild.weight.takeIf { it > 0 }?.toString() ?: ""
            draftHeight = currentChild.height.takeIf { it > 0 }?.toString() ?: ""
            draftGender = currentChild.gender.ifBlank { "Nam" }
        }
    }

    val assessmentUi = remember(currentAssessment?.classification ?: currentAssessment?.recommendation) {
        buildAssessmentUi(currentAssessment?.classification ?: currentAssessment?.recommendation)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Hồ sơ các bé", fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                .verticalScroll(scrollState)
        ) {
            LazyRow(
                modifier = Modifier.padding(vertical = 16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(children) { child ->
                    ChildAvatarItem(
                        child = child,
                        isSelected = selectedChildId == child.id,
                        onClick = {
                            isEditing = false
                            viewModel.selectChild(child.id)
                        }
                    )
                }
                item {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                            .clickable(onClick = onAddChild),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(SecondaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryBlue)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (currentChild == null) {
                EmptyChildrenState(onAddChild = onAddChild)
            } else {
                SelectedChildDashboard(
                    child = currentChild,
                    isEditing = isEditing,
                    onEditModeChanged = { isEditing = it },
                    name = draftName,
                    dob = draftDob,
                    weight = draftWeight,
                    height = draftHeight,
                    gender = draftGender,
                    onNameChange = { draftName = it },
                    onDobChange = { draftDob = it },
                    onWeightChange = { draftWeight = it },
                    onHeightChange = { draftHeight = it },
                    onGenderChange = { draftGender = it },
                    onBook = onNavigateToHome,
                    onStartEdit = {
                        isEditing = true
                        draftName = currentChild.name
                        draftDob = currentChild.dob
                        draftWeight = currentChild.weight.takeIf { it > 0 }?.toString() ?: ""
                        draftHeight = currentChild.height.takeIf { it > 0 }?.toString() ?: ""
                        draftGender = currentChild.gender.ifBlank { "Nam" }
                    },
                    onCancelEdit = {
                        isEditing = false
                        draftName = currentChild.name
                        draftDob = currentChild.dob
                        draftWeight = currentChild.weight.takeIf { it > 0 }?.toString() ?: ""
                        draftHeight = currentChild.height.takeIf { it > 0 }?.toString() ?: ""
                        draftGender = currentChild.gender.ifBlank { "Nam" }
                    },
                    onUpdate = { showConfirmDialog = true },
                    isSaving = isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                AssessmentCard(
                    assessment = currentAssessment,
                    ui = assessmentUi
                )

                Spacer(modifier = Modifier.height(16.dp))

                ClassificationHistoryCard(
                    title = "Lịch sử thể trạng",
                    subtitle = "Mỗi cột thể hiện phân loại thể trạng tại lần cập nhật",
                    history = measurementHistory
                )

                
            }
        }
    }

    if (showConfirmDialog && currentChild != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Xác nhận cập nhật") },
            text = { Text("Bạn có muốn cập nhật thông tin bé và đánh giá lại tình trạng phát triển ngay bây giờ không?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.updateBabyInfo(
                            Baby(
                                id = currentChild.id,
                                name = draftName,
                                dob = draftDob,
                                weight = draftWeight.toDoubleOrNull() ?: 0.0,
                                height = draftHeight.toDoubleOrNull() ?: 0.0,
                                gender = draftGender
                            ),
                            onSuccess = { isEditing = false }
                        )
                    }
                ) {
                    Text("Đồng ý")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun EmptyChildrenState(onAddChild: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Chưa có hồ sơ bé nào", fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Hãy thêm hồ sơ đầu tiên để theo dõi thể trạng và đánh giá phát triển.", color = TextSecondary)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddChild, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                Text("Thêm bé")
            }
        }
    }
}

@Composable
private fun SelectedChildDashboard(
    child: Baby,
    isEditing: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    name: String,
    dob: String,
    weight: String,
    height: String,
    gender: String,
    onNameChange: (String) -> Unit,
    onDobChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onGenderChange: (String) -> Unit,
    onBook: () -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onUpdate: () -> Unit,
    isSaving: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(child.name, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isBoy(child.gender)) Icons.Default.Male else Icons.Default.Female,
                            contentDescription = null,
                            tint = if (isBoy(child.gender)) PrimaryBlue else Color(0xFFEC4899),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(child.gender, color = if (isBoy(child.gender)) PrimaryBlue else Color(0xFFEC4899), fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(child.dob, color = TextSecondary, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    if (isEditing) {
                        EditableField("Tên của bé", name, onNameChange)
                        EditableField("Ngày sinh (DD/MM/YYYY)", dob, onDobChange)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            EditableField("Cân nặng (kg)", weight, onWeightChange, modifier = Modifier.weight(1f), keyboardType = KeyboardType.NumberPassword)
                            EditableField("Chiều cao (cm)", height, onHeightChange, modifier = Modifier.weight(1f), keyboardType = KeyboardType.NumberPassword)
                        }
                        GenderSelector(gender = gender, onGenderChange = onGenderChange)
                    } else {
                        InfoGrid(
                            items = listOf(
                                InfoChip("Cân nặng", if (child.weight > 0) String.format("%.1f kg", child.weight) else "Chưa có"),
                                InfoChip("Chiều cao", if (child.height > 0) String.format("%.1f cm", child.height) else "Chưa có"),
                                InfoChip("Ngày sinh", child.dob),
                                InfoChip("Giới tính", child.gender)
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SecondaryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = if (isBoy(child.gender)) R.drawable.avatar_boy else R.drawable.avatar_girl),
                        contentDescription = child.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                    )
                }
            }

            if (!isEditing) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onBook,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, PrimaryBlue),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue, containerColor = Color.White)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đặt lịch", color = PrimaryBlue)
                    }
                    Button(
                        onClick = onStartEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Chỉnh sửa", color = Color.White)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(18.dp))
                if (isSaving) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PrimaryBlue)
                        Text("Đang lưu và đánh giá lại...", color = TextSecondary)
                    }
                } else {
                    // khi đang edit: hiển thị nút Hủy / Cập nhật ngay trong card
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onCancelEdit,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
                        ) {
                            Text("Hủy")
                        }
                        Button(
                            onClick = onUpdate,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = Color.White)
                        ) {
                            Text("Cập nhật", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssessmentCard(
    assessment: GrowthAssessmentRecordDto?,
    ui: AssessmentUi
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Đánh giá phát triển hiện tại", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
            Spacer(modifier = Modifier.height(14.dp))
            if (assessment == null) {
                Text(
                    "Bé mới nhập lần đầu nên chưa có đánh giá phát triển. Khi cập nhật thông tin thể trạng, hệ thống sẽ tự tạo đánh giá mới.",
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            } else {
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
                InfoGrid(
                    items = listOf(
                            InfoChip("Thể trạng", classificationToVietnamese(assessment.classification)),
                            InfoChip("Rủi ro", riskToVietnamese(assessment.riskLevel)),
                        )
                )
                assessment.recommendation?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Khuyến nghị", fontWeight = FontWeight.SemiBold, color = TextDark)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = TextSecondary, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun ClassificationHistoryCard(
    title: String,
    subtitle: String,
    history: List<ChildMeasurementDto>
) {
    var selected by remember { mutableStateOf<ChildMeasurementDto?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))

            if (history.isEmpty()) {
                Text("Chưa có dữ liệu lịch sử.", color = TextSecondary)
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val availableWidth = maxWidth
                    val spacing = 8.dp
                    val columnWidth = (availableWidth - (spacing * 4)) / 5

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        history.forEach { m ->
                            val cls = m.assessment?.classification
                            val color = classificationToColor(cls)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(columnWidth)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(80.dp)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(color)
                                        .clickable { selected = m }
                                ) {}
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = m.measuredAtKey.ifBlank { m.measuredAt },
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (selected != null) {
        val m = selected!!
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Chi tiết cập nhật") },
            text = {
                Column {
                    Text("Ngày: ${m.measuredAt}")
                    Text("Cân nặng: ${m.weight?.let { String.format("%.1f kg", it) } ?: "-"}")
                    Text("Chiều cao: ${m.height?.let { String.format("%.1f cm", it) } ?: "-"}")
                    Text("Thể trạng: ${classificationToVietnamese(m.assessment?.classification)}")
                    Text("Rủi ro: ${riskToVietnamese(m.assessment?.riskLevel)}")
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text("Đóng") }
            }
        )
    }
}

private fun classificationToVietnamese(raw: String?): String {
    return when (raw?.trim()?.lowercase()) {
        "normal" -> "Sức khỏe tốt"
        "overweight" -> "Nguy cơ thừa cân"
        "obese" -> "Nguy cơ béo phì"
        "thin" -> "Nguy cơ suy dinh dưỡng"
        "severe_thin" -> "Nguy cơ suy dinh dưỡng nặng"
        else -> "Chưa rõ"
    }
}

private fun riskToVietnamese(raw: String?): String {
    return when (raw?.trim()?.lowercase()) {
        "low", "normal" -> "Thấp"
        "medium" -> "Trung bình"
        "high" -> "Cao"
        else -> raw ?: "Chưa rõ"
    }
}

private fun classificationToColor(raw: String?): Color {
    return when (raw?.trim()?.lowercase()) {
        "normal" -> SuccessGreen.copy(alpha = 0.9f)
        "overweight", "obese" -> WarningOrange.copy(alpha = 0.9f)
        "thin", "severe_thin" -> ErrorRed.copy(alpha = 0.9f)
        else -> Color(0xFFF1F5F9)
    }
}

@Composable
private fun GrowthHistoryCard(
    title: String,
    subtitle: String,
    history: List<ChildMeasurementDto>,
    seriesSelector: (ChildMeasurementDto) -> Double?,
    lineColor: Color
) {
    val series = history.mapNotNull { measurement ->
        val value = seriesSelector(measurement)
        if (value != null) ChartPoint(measurement.measuredAtKey.ifBlank { measurement.measuredAt }, value) else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
            if (series.size < 2) {
                Text("Chưa đủ dữ liệu để vẽ biểu đồ.", color = TextSecondary)
            } else {
                LineChart(series = series, lineColor = lineColor)
            }
        }
    }
}

@Composable
private fun LineChart(
    series: List<ChartPoint>,
    lineColor: Color
) {
    val minValue = series.minOf { it.value }
    val maxValue = series.maxOf { it.value }
    val delta = max(abs(maxValue - minValue), 0.0001)

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val chartHeight = size.height * 0.78f
            val chartTop = size.height * 0.08f
            val chartBottom = chartTop + chartHeight
            val stepX = if (series.size > 1) size.width / (series.size - 1) else 0f

            val points = series.mapIndexed { index, point ->
                val x = stepX * index
                val normalized = ((point.value - minValue) / delta).toFloat()
                val y = chartBottom - (normalized * chartHeight)
                Offset(x, y)
            }

            val path = Path().apply {
                points.firstOrNull()?.let { moveTo(it.x, it.y) }
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }

            drawLine(
                color = Color(0xFFE2E8F0),
                start = Offset(0f, chartBottom),
                end = Offset(size.width, chartBottom),
                strokeWidth = 2f
            )
            drawLine(
                color = Color(0xFFE2E8F0),
                start = Offset(0f, chartTop),
                end = Offset(size.width, chartTop),
                strokeWidth = 1f
            )

            drawPath(
                path = path,
                color = lineColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            points.forEach { point ->
                drawCircle(color = Color.White, radius = 8f, center = point)
                drawCircle(color = lineColor, radius = 5f, center = point)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(series.first().label, color = TextSecondary, fontSize = 12.sp)
            Text(String.format("%.1f", series.last().value), color = lineColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(series.last().label, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun InfoGrid(items: List<InfoChip>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = BackgroundLight
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.label, fontSize = 12.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(item.value, fontWeight = FontWeight.SemiBold, color = TextDark)
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EditableField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        enabled = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType)
    )
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun GenderSelector(gender: String, onGenderChange: (String) -> Unit) {
    Text("Giới tính", fontWeight = FontWeight.SemiBold, color = TextDark)
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.RadioButton(selected = gender == "Nam", onClick = { onGenderChange("Nam") })
        Text("Nam")
        Spacer(modifier = Modifier.width(20.dp))
        androidx.compose.material3.RadioButton(selected = gender == "Nữ", onClick = { onGenderChange("Nữ") })
        Text("Nữ")
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ChildAvatarItem(child: Baby, isSelected: Boolean, onClick: () -> Unit) {
    val isBoy = isBoy(child.gender)
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

private data class InfoChip(val label: String, val value: String)

private data class ChartPoint(val label: String, val value: Double)

private data class AssessmentUi(
    val title: String,
    val subtitle: String,
    val color: Color,
    val icon: ImageVector
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

private fun isBoy(gender: String?): Boolean {
    val normalized = gender?.trim()?.lowercase() ?: return true
    return normalized in setOf("nam", "male", "boy", "m")
}
