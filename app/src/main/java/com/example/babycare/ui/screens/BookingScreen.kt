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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Vaccines
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.example.babycare.data.model.Appointment
import com.example.babycare.ui.theme.BackgroundLight
import com.example.babycare.ui.theme.PrimaryBlue
import com.example.babycare.ui.theme.SecondaryBlue
import com.example.babycare.ui.theme.TextDark
import com.example.babycare.ui.theme.TextSecondary
import com.example.babycare.viewmodel.BabyViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
private data class BookingChild(
    val name: String,
    val ageText: String
)
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
    val baby by viewModel.babyState.collectAsStateWithLifecycle()
    // Chỉ hiện lỗi sau khi user đã thao tác (không hiện lỗi kết nối khi mới vào)
    var hasUserAction by remember { mutableStateOf(false) }
    val children = remember(baby) {
        listOf(
            BookingChild(
                name = baby?.name?.ifBlank { "Đặng Long" } ?: "Đặng Long",
                ageText = baby?.dob?.takeIf { it.isNotBlank() }?.let { calculateBabyAge(it) } ?: "8 Tháng"
            ),
            BookingChild("Đặng Ánh", "2 Tuổi")
        )
    }
    val appointmentTypes = remember {
        listOf(
            AppointmentTypeOption("Tiêm chủng", "Tiêm định kỳ", Icons.Default.Vaccines),
            AppointmentTypeOption("Khám tổng quát", "Khám sức khỏe & theo dõi phát triển", Icons.Default.Search),
            AppointmentTypeOption("Khám nhi", "Vấn đề sức khỏe cụ thể", Icons.Default.ChildCare)
        )
    }
    val clinics = remember {
        listOf("Đầu theo yêu cầu", "Bệnh viện Đa khoa Thành phố", "Trung tâm Tiêm chủng VNVC", "Phòng khám Nhi Gia đình")
    }
    val dateFormatter = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    var selectedChild by remember { mutableStateOf(children.first().name) }
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
    val selectedTimeText = remember(selectedTime.get(Calendar.HOUR_OF_DAY), selectedTime.get(Calendar.MINUTE)) {
        timeFormatter.format(selectedTime.time)
    }
    var notes by remember { mutableStateOf("") }
    var clinicExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
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
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                children.forEach { child ->
                    ChildOptionCard(
                        child = child,
                        selected = selectedChild == child.name,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedChild = child.name }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLoading) { clinicExpanded = !clinicExpanded },
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryBlue.copy(alpha = 0.45f),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    )
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
                    OutlinedTextField(
                        value = selectedDateText,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
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
                            },
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
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = selectedTimeText,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isLoading) {
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        selectedTime = (selectedTime.clone() as Calendar).apply {
                                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                                            set(Calendar.MINUTE, minute)
                                        }
                                    },
                                    selectedTime.get(Calendar.HOUR_OF_DAY),
                                    selectedTime.get(Calendar.MINUTE),
                                    false
                                ).show()
                            },
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
                    Text(selectedChild, fontWeight = FontWeight.SemiBold, color = TextDark)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    hasUserAction = true
                    viewModel.addAppointment(
                        Appointment(
                            serviceType = selectedType,
                            hospitalName = selectedClinic,
                            date = selectedDateText,
                            time = selectedTimeText
                        )
                    )
                    onNavigateBack()
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
    child: BookingChild,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, if (selected) PrimaryBlue else Color(0xFFE2E8F0), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(SecondaryBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ChildCare, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
            }
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
        Text(child.name, fontWeight = FontWeight.SemiBold, color = if (selected) PrimaryBlue else TextSecondary)
        Text(child.ageText, fontSize = 12.sp, color = TextSecondary)
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
