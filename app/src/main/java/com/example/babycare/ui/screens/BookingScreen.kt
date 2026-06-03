package com.example.babycare.ui.screens
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.example.babycare.R
import com.example.babycare.data.model.Appointment
import com.example.babycare.data.model.Baby
import com.example.babycare.ui.theme.BackgroundLight
import com.example.babycare.ui.theme.PrimaryBlue
import com.example.babycare.ui.theme.SecondaryBlue
import com.example.babycare.ui.theme.TextDark
import com.example.babycare.ui.theme.TextSecondary
import com.example.babycare.viewmodel.BabyViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

private data class AppointmentTypeOption(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val children by viewModel.childrenState.collectAsStateWithLifecycle()
    val parentProfile by viewModel.parentProfile.collectAsStateWithLifecycle()
    // Chỉ hiện lỗi sau khi user đã thao tác (không hiện lỗi kết nối khi mới vào)
    var hasUserAction by remember { mutableStateOf(false) }
    val appointmentTypes = remember {
        listOf(
            AppointmentTypeOption("Tiêm chủng", "Tiêm định kỳ", Icons.Default.Vaccines),
            AppointmentTypeOption("Khám tổng quát", "Khám sức khỏe & theo dõi phát triển", Icons.Default.Search),
            AppointmentTypeOption("Khám nhi", "Vấn đề sức khỏe cụ thể", Icons.Default.ChildCare)
        )
    }
    val clinics = remember {
        listOf(
            "Bác sĩ Khoa nhi - Nguyễn Văn A",
            "Bác sĩ Khoa nhi - Trần Thị B",
            "Bác sĩ Khoa nhi - Lê Hoàng C",
            "Bác sĩ Khoa nhi - Phạm Minh D"
        )
    }
    val dateFormatter = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    var selectedChildId by remember { mutableStateOf<String?>(viewModel.selectedChildId.value) }
    var selectedType by remember { mutableStateOf(appointmentTypes.first().title) }
    var selectedClinic by remember { mutableStateOf(clinics.first()) }
    var selectedDate by remember {
        mutableStateOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) })
    }
    var selectedTime by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        })
    }
    val selectedDateText = remember(selectedDate.timeInMillis) { dateFormatter.format(selectedDate.time) }
    val selectedTimeText = remember(selectedTime) {
        timeFormatter.format(selectedTime.time)
    }
    var notes by remember { mutableStateOf("") }
    var clinicExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val vaccineSchedule by viewModel.vaccineSchedule.collectAsStateWithLifecycle()
    val pendingVaccines = remember(vaccineSchedule) {
        vaccineSchedule.filter { it.status != com.example.babycare.data.model.VaccinationStatus.COMPLETED }.map { it.vaccine }
    }
    var selectedVaccine by remember { mutableStateOf<String?>(null) }
    var vaccineExpanded by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showVaccineAlert by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.fetchDataFromServer()
        }
    }

    LaunchedEffect(selectedChildId) {
        selectedVaccine = null
        if (!selectedChildId.isNullOrBlank()) {
            viewModel.selectChild(selectedChildId)
        }
    }

    LaunchedEffect(children) {
        if (selectedChildId == null || children.none { it.id == selectedChildId }) {
            selectedChildId = viewModel.selectedChildId.value ?: children.firstOrNull()?.id
        }
    }

    val selectedChild = children.firstOrNull { it.id == selectedChildId } ?: children.firstOrNull()
    val selectedChildName = selectedChild?.name?.ifBlank { "Chưa chọn bé" } ?: "Chưa chọn bé"
    val selectedChildAge = selectedChild?.dob?.takeIf { it.isNotBlank() }?.let { calculateBabyAge(it) } ?: ""
    Scaffold(
        containerColor = BackgroundLight,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Đặt lịch khám", fontWeight = FontWeight.Bold, color = PrimaryBlue) },
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
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Chỉ hiện lỗi khi user đã thao tác (không phải lỗi kết nối lúc khởi động)
            if (error != null && hasUserAction) {
                Surface(
                    color = Color(0xFFFFF1F2),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error ?: "",
                        color = Color(0xFFB91C1C),
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            SectionHeader(number = "1.", title = "Chọn bé")
            Spacer(modifier = Modifier.height(12.dp))
            if (children.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(children) { child ->
                        val isBoy = child.gender.trim().lowercase() in setOf("nam", "male", "boy", "m")
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { selectedChildId = child.id }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedChildId == child.id) PrimaryBlue.copy(alpha = 0.1f) else Color.Transparent)
                                    .border(if (selectedChildId == child.id) 2.dp else 0.dp, PrimaryBlue, CircleShape),
                                contentAlignment = Alignment.Center
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
                                fontWeight = if (selectedChildId == child.id) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedChildId == child.id) PrimaryBlue else TextSecondary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            } else {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Chưa có bé nào trong hồ sơ. Vui lòng thêm thông tin bé trước khi đặt lịch.",
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(number = "2.", title = "Loại dịch vụ")
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                appointmentTypes.forEach { option ->
                    AppointmentTypeCard(
                        option = option,
                        selected = selectedType == option.title,
                        onClick = { selectedType = option.title }
                    )
                }
            }
            if (selectedType == "Tiêm chủng") {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Chọn mũi tiêm cho bé", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedVaccine ?: "Chọn vắc-xin cần tiêm",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = PrimaryBlue.copy(alpha = 0.45f),
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(enabled = !isLoading) { vaccineExpanded = !vaccineExpanded }
                    )
                    DropdownMenu(
                        expanded = vaccineExpanded,
                        onDismissRequest = { vaccineExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        if (pendingVaccines.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Không có mũi tiêm chưa tiêm") },
                                onClick = { vaccineExpanded = false }
                            )
                        } else {
                            pendingVaccines.forEach { vaccine ->
                                DropdownMenuItem(
                                    text = { Text("${vaccine.name} (Khuyên dùng: ${vaccine.monthAge} tháng)") },
                                    onClick = {
                                        selectedVaccine = vaccine.name
                                        vaccineExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(number = "3.", title = "Chọn cơ sở / Bác sĩ")
                Text("Tuỳ chọn", color = TextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedClinic,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryBlue.copy(alpha = 0.45f),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    )
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(enabled = !isLoading) { clinicExpanded = !clinicExpanded }
                )
                DropdownMenu(
                    expanded = clinicExpanded,
                    onDismissRequest = { clinicExpanded = false }
                ) {
                    clinics.forEach { clinic ->
                        DropdownMenuItem(
                            text = { Text(clinic) },
                            onClick = {
                                selectedClinic = clinic
                                clinicExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(number = "4.", title = "Ngày & Giờ")
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedDateText,
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Chọn ngày") },
                            leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = PrimaryBlue.copy(alpha = 0.45f),
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(enabled = !isLoading) {
                                    val now = Calendar.getInstance()
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            selectedDate = (selectedDate.clone() as Calendar).apply {
                                                set(Calendar.YEAR, year)
                                                set(Calendar.MONTH, month)
                                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                            }
                                        },
                                        selectedDate.get(Calendar.YEAR),
                                        selectedDate.get(Calendar.MONTH),
                                        selectedDate.get(Calendar.DAY_OF_MONTH)
                                    ).apply {
                                        datePicker.minDate = now.timeInMillis
                                    }.show()
                                }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedTimeText,
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Chọn giờ") },
                            leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = PrimaryBlue.copy(alpha = 0.45f),
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(enabled = !isLoading) {
                                    showTimePicker = !showTimePicker
                                }
                        )
                    }
                    if (showTimePicker) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
                                .border(1.dp, PrimaryBlue.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Giờ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                ScrollSpinner(
                                    items = (1..12).toList(),
                                    selectedValue = if (selectedTime.get(Calendar.HOUR) == 0) 12 else selectedTime.get(Calendar.HOUR),
                                    onValueChange = { hour ->
                                        selectedTime = (selectedTime.clone() as Calendar).apply {
                                            set(Calendar.HOUR, if (hour == 12) 0 else hour)
                                        }
                                    },
                                    labelFormatter = { String.format("%02d", it) }
                                )
                            }
                            Text(
                                text = ":",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Phút", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                ScrollSpinner(
                                    items = listOf(0, 15, 30, 45),
                                    selectedValue = selectedTime.get(Calendar.MINUTE),
                                    onValueChange = { minute ->
                                        selectedTime = (selectedTime.clone() as Calendar).apply {
                                            set(Calendar.MINUTE, minute)
                                        }
                                    },
                                    labelFormatter = { String.format("%02d", it) }
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("AM/PM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                ScrollSpinner(
                                    items = listOf("AM", "PM"),
                                    selectedValue = if (selectedTime.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM",
                                    onValueChange = { amPm ->
                                        selectedTime = (selectedTime.clone() as Calendar).apply {
                                            set(Calendar.AM_PM, if (amPm == "AM") Calendar.AM else Calendar.PM)
                                        }
                                    },
                                    labelFormatter = { it },
                                    modifier = Modifier.width(80.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Ghi chú (Tuỳ chọn)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("Nhập ghi chú nếu cần") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = PrimaryBlue.copy(alpha = 0.45f),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lịch hẹn đã chọn", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = "$selectedDateText • $selectedTimeText",
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue,
                            fontSize = 16.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SecondaryBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ChildCare, contentDescription = null, tint = PrimaryBlue)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(selectedChildName, fontWeight = FontWeight.SemiBold, color = TextDark)
                        if (selectedChildAge.isNotBlank()) {
                            Text(selectedChildAge, fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    hasUserAction = true
                    if (selectedType == "Tiêm chủng" && selectedVaccine == null) {
                        showVaccineAlert = true
                        return@Button
                    }
                    val finalServiceType = if (selectedType == "Tiêm chủng" && selectedVaccine != null) {
                        "Tiêm chủng ($selectedVaccine)"
                    } else {
                        selectedType
                    }
                    viewModel.addAppointment(
                        Appointment(
                            serviceType = finalServiceType,
                            hospitalName = selectedClinic,
                            date = selectedDateText,
                            time = selectedTimeText,
                            childId = selectedChildId,
                            note = notes.takeIf { it.isNotBlank() },
                            parentName = parentProfile?.fullName?.takeIf { it.isNotBlank() },
                            parentPhone = parentProfile?.phone?.takeIf { it.isNotBlank() }
                        ),
                        onSuccess = {
                            showSuccessDialog = true
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Xác nhận lịch hẹn", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onNavigateBack()
            },
            title = {
                Text(
                    text = "Đặt lịch thành công",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryBlue
                )
            },
            text = {
                Text(
                    text = "Lịch hẹn khám của bé đã được gửi thành công. Vui lòng chờ bác sĩ xác nhận.",
                    fontSize = 14.sp,
                    color = TextDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Đồng ý", color = Color.White)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showVaccineAlert) {
        AlertDialog(
            onDismissRequest = { showVaccineAlert = false },
            title = {
                Text(
                    text = "Chưa chọn vắc-xin",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryBlue
                )
            },
            text = {
                Text(
                    text = "Vui lòng chọn loại vắc-xin cần tiêm cho bé trước khi tiếp tục.",
                    fontSize = 14.sp,
                    color = TextDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVaccineAlert = false
                        coroutineScope.launch {
                            // Cuộn đến vùng chọn vắc-xin ở giữa màn hình (offset khoảng 350.dp)
                            scrollState.animateScrollTo(350)
                            // Tự động mở rộng danh sách vắc-xin
                            vaccineExpanded = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Chọn vắc-xin", color = Color.White)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
@Composable
private fun SectionHeader(number: String, title: String) {
    Text(
        text = "$number $title",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = PrimaryBlue
    )
}
@Composable
private fun ChildOptionCard(
    child: Baby,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    BookingChildCard(child = child, selected = selected, modifier = modifier, onClick = onClick)
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
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(child.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = if (selected) PrimaryBlue else TextSecondary)
        Text(child.dob.takeIf { it.isNotBlank() }?.let { calculateBabyAge(it) } ?: "", fontSize = 12.sp, color = TextSecondary)
    }
}
@Composable
private fun AppointmentTypeCard(
    option: AppointmentTypeOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (selected) PrimaryBlue.copy(alpha = 0.45f) else Color(0xFFE2E8F0)),
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SecondaryBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(option.icon, contentDescription = null, tint = PrimaryBlue)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(option.title, fontWeight = FontWeight.Bold, color = TextDark)
                Text(option.subtitle, fontSize = 12.sp, color = TextSecondary)
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
            )
        }
    }
}

@Composable
private fun <T> ScrollSpinner(
    items: List<T>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    labelFormatter: (T) -> String = { it.toString() },
    modifier: Modifier = Modifier
) {
    val selectedIndex = items.indexOf(selectedValue).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    LaunchedEffect(selectedValue) {
        val index = items.indexOf(selectedValue)
        if (index >= 0 && index != listState.firstVisibleItemIndex) {
            listState.animateScrollToItem(index)
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val firstIndex = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 100
            val index = if (offset > itemHeight / 2) firstIndex + 1 else firstIndex
            val coercedIndex = index.coerceIn(0, items.lastIndex)
            if (coercedIndex != listState.firstVisibleItemIndex || offset != 0) {
                listState.animateScrollToItem(coercedIndex)
            }
            onValueChange(items[coercedIndex])
        }
    }

    Box(
        modifier = modifier
            .height(110.dp)
            .width(60.dp),
        contentAlignment = Alignment.Center
    ) {
        // Highlight middle selected row
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(PrimaryBlue.copy(alpha = 0.05f))
                .align(Alignment.Center)
                .border(BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.2f)), RoundedCornerShape(4.dp))
        )
        
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(items.size) { index ->
                val item = items[index]
                val isSelected = item == selectedValue
                Text(
                    text = labelFormatter(item),
                    fontSize = if (isSelected) 20.sp else 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) PrimaryBlue else TextSecondary,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clickable {
                            onValueChange(item)
                        }
                )
            }
        }
    }
}
