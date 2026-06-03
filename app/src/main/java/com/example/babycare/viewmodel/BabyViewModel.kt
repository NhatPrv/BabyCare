package com.example.babycare.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.babycare.data.model.Appointment
import com.example.babycare.data.model.Baby
import com.example.babycare.data.model.VaccineSchedule
import com.example.babycare.data.model.VaccinationStatus
import com.example.babycare.data.remote.AuthRequest
import com.example.babycare.data.remote.ChatMessageDto
import com.example.babycare.data.remote.ChatSessionDto
import com.example.babycare.data.remote.ChildUpsertRequest
import com.example.babycare.data.remote.ChildProfileResponse
import com.example.babycare.data.remote.CreateChatSessionRequest
import com.example.babycare.data.remote.GrowthAssessmentRequest
import com.example.babycare.data.remote.GrowthAssessmentResponse
import com.example.babycare.data.remote.ParentProfileDto
import com.example.babycare.data.remote.UpdateParentProfileRequest
import com.example.babycare.data.remote.RetrofitClient
import com.example.babycare.data.remote.SendChatMessageRequest
import com.example.babycare.data.remote.VaccinationRequest
import com.example.babycare.data.repository.VaccineRepository
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class BabyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaccineRepository()
    private val apiService = RetrofitClient.apiService
    private val TAG = "BabyViewModel"
    private val appDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val sharedPrefs = application.getSharedPreferences("babycare_prefs", Context.MODE_PRIVATE)

    private val _babyState = MutableStateFlow<Baby?>(null)
    val babyState: StateFlow<Baby?> = _babyState.asStateFlow()

    private val _childrenState = MutableStateFlow<List<Baby>>(emptyList())
    val childrenState: StateFlow<List<Baby>> = _childrenState.asStateFlow()

    private val _selectedChildId = MutableStateFlow<String?>(null)
    val selectedChildId: StateFlow<String?> = _selectedChildId.asStateFlow()

    private val _childProfile = MutableStateFlow<ChildProfileResponse?>(null)
    val childProfile: StateFlow<ChildProfileResponse?> = _childProfile.asStateFlow()

    private val _growthAssessment = MutableStateFlow<GrowthAssessmentResponse?>(null)
    val growthAssessment: StateFlow<GrowthAssessmentResponse?> = _growthAssessment.asStateFlow()

    private val _vaccineSchedule = MutableStateFlow<List<VaccineSchedule>>(emptyList())
    val vaccineSchedule: StateFlow<List<VaccineSchedule>> = _vaccineSchedule.asStateFlow()

    private val _aiSuggestions = MutableStateFlow<List<String>>(emptyList())
    val aiSuggestions: StateFlow<List<String>> = _aiSuggestions.asStateFlow()

    private val _appointments = MutableStateFlow<List<Appointment>>(emptyList())
    val appointments: StateFlow<List<Appointment>> = _appointments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private val _chatSessions = MutableStateFlow<List<ChatSessionDto>>(emptyList())
    val chatSessions: StateFlow<List<ChatSessionDto>> = _chatSessions.asStateFlow()

    private val _parentStats = MutableStateFlow<com.example.babycare.data.remote.ParentStatsResponse?>(null)
    val parentStats: StateFlow<com.example.babycare.data.remote.ParentStatsResponse?> = _parentStats.asStateFlow()

    private val _parentProfile = MutableStateFlow<ParentProfileDto?>(null)
    val parentProfile: StateFlow<ParentProfileDto?> = _parentProfile.asStateFlow()

    private val _selectedChatSessionId = MutableStateFlow<String?>(null)
    val selectedChatSessionId: StateFlow<String?> = _selectedChatSessionId.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessageDto>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageDto>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _initialVaccineFilter = MutableStateFlow<String>("Tất cả")
    val initialVaccineFilter: StateFlow<String> = _initialVaccineFilter.asStateFlow()

    fun setInitialVaccineFilter(filter: String) {
        _initialVaccineFilter.value = filter
    }

    init {
        val savedToken = sharedPrefs.getString("auth_token", null)
        if (!savedToken.isNullOrBlank()) {
            _authToken.value = savedToken
            fetchDataFromServer()
        }
    }

    private fun authHeader(): String? = _authToken.value?.let { "Bearer $it" }

    fun fetchDataFromServer() {
        val authorization = authHeader() ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                try {
                    val stats = apiService.getParentStats(authorization)
                    _parentStats.value = stats
                } catch (se: Exception) {
                    Log.e(TAG, "Lỗi khi lấy thống kê phụ huynh: ${se.message}")
                }

                try {
                    val profile = apiService.getParentProfile(authorization)
                    _parentProfile.value = profile
                } catch (pe: Exception) {
                    Log.e(TAG, "Lỗi khi lấy hồ sơ phụ huynh: ${pe.message}")
                }

                val children = apiService.getChildren(authorization)
                _childrenState.value = children

                val selectedChild = when (val selectedId = _selectedChildId.value) {
                    null -> children.firstOrNull()
                    else -> children.firstOrNull { it.id == selectedId } ?: children.firstOrNull()
                }
                if (selectedChild?.id != null) {
                    _selectedChildId.value = selectedChild.id
                    val profile = apiService.getChildProfile(authorization, selectedChild.id)
                    _babyState.value = profile.child
                    _childProfile.value = profile
                } else {
                    _babyState.value = null
                    _childProfile.value = null
                }
                
                val appts = apiService.getAppointments(authorization)
                _appointments.value = appts

                if (selectedChild?.name?.isNotBlank() == true) {
                    val completedVaccineIds = apiService.getVaccinations(authorization, selectedChild.name)
                    generateSchedule(selectedChild.dob, completedVaccineIds)
                } else {
                    _vaccineSchedule.value = emptyList()
                    _aiSuggestions.value = listOf("Hãy thêm thông tin bé để xem lịch tiêm và gợi ý AI.")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Không thể kết nối máy chủ: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateBabyInfo(baby: Baby, onSuccess: () -> Unit = {}) {
        val authorization = authHeader()
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val payload = ChildUpsertRequest(
                    name = baby.name,
                    dob = baby.dob,
                    weight = baby.weight,
                    height = baby.height,
                    gender = baby.gender
                )
                val saved = if (authorization == null) {
                    // No auth: save locally in-memory so the UI flow works (use a generated id)
                    val localId = java.util.UUID.randomUUID().toString()
                    Baby(
                        id = localId,
                        name = payload.name,
                        dob = payload.dob,
                        weight = payload.weight,
                        height = payload.height,
                        gender = payload.gender
                    )
                } else {
                    if (_selectedChildId.value.isNullOrBlank()) {
                        apiService.createChild(authorization, payload)
                    } else {
                        apiService.updateChild(authorization, _selectedChildId.value!!, payload)
                    }
                }

                _selectedChildId.value = saved.id
                _babyState.value = saved

                // Refresh other data (appointments, schedule) if authorized
                if (authorization != null) fetchDataFromServer()

                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Lỗi khi lưu thông tin: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addAppointment(appointment: Appointment, onSuccess: (() -> Unit)? = null) {
        val authorization = authHeader() ?: return
        val newAppt = appointment.copy(id = UUID.randomUUID().toString())
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                apiService.addAppointment(authorization, newAppt)
                fetchDataFromServer()
                onSuccess?.invoke()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Lỗi khi đặt lịch: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelAppointment(appointmentId: String) {
        val authorization = authHeader() ?: return
        viewModelScope.launch {
            try {
                apiService.cancelAppointment(authorization, appointmentId)
                fetchDataFromServer()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Lỗi khi hủy lịch hẹn: ${e.message}"
            }
        }
    }


    fun login(
        username: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = apiService.login(AuthRequest(username = username.trim(), password = password))
                val token = response.token
                if (!token.isNullOrBlank()) {
                    _authToken.value = token
                    sharedPrefs.edit().putString("auth_token", token).apply()
                    fetchDataFromServer()
                    onSuccess()
                } else {
                    onError(response.error ?: "Đăng nhập thất bại")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onError(e.message ?: "Không thể đăng nhập")
            }
        }
    }

    fun register(
        username: String,
        password: String,
        fullName: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = apiService.register(
                    AuthRequest(
                        username = username.trim(),
                        password = password,
                        fullName = fullName?.trim()?.takeIf { it.isNotEmpty() }
                    )
                )
                val token = response.token
                if (response.success || !token.isNullOrBlank()) {
                    if (!token.isNullOrBlank()) {
                        _authToken.value = token
                        sharedPrefs.edit().putString("auth_token", token).apply()
                        fetchDataFromServer()
                    }
                    onSuccess()
                } else {
                    onError(response.error ?: "Đăng ký thất bại")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onError(e.message ?: "Không thể đăng ký")
            }
        }
    }

    fun logout() {
        _authToken.value = null
        sharedPrefs.edit().remove("auth_token").apply()
        _parentStats.value = null
        _parentProfile.value = null
        _babyState.value = null
        _childrenState.value = emptyList()
        _selectedChildId.value = null
        _childProfile.value = null
        _growthAssessment.value = null
        _appointments.value = emptyList()
        _chatSessions.value = emptyList()
        _chatMessages.value = emptyList()
    }

    fun updateParentProfile(
        fullName: String,
        phone: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val authorization = authHeader() ?: return
        viewModelScope.launch {
            try {
                val updated = apiService.updateParentProfile(
                    authorization,
                    UpdateParentProfileRequest(fullName = fullName.trim(), phone = phone.trim())
                )
                _parentProfile.value = updated
                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onError("Lỗi khi cập nhật thông tin: ${e.message}")
            }
        }
    }

    fun toggleVaccinationStatus(vaccineId: Int, isCompleted: Boolean) {
        val authorization = authHeader() ?: return
        val babyName = _babyState.value?.name ?: return
        viewModelScope.launch {
            try {
                val statusString = if (isCompleted) "completed" else "upcoming"
                apiService.markVaccinationCompleted(
                    authorization,
                    VaccinationRequest(babyName, vaccineId, statusString)
                )
                fetchDataFromServer()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Lỗi khi cập nhật tiêm chủng: ${e.message}"
            }
        }
    }

    fun loadChildForEdit(childId: String?) {
        selectChild(childId)
    }

    fun selectChild(childId: String?) {
        val authorization = authHeader() ?: return
        viewModelScope.launch {
            try {
                _growthAssessment.value = null
                if (childId.isNullOrBlank()) {
                    _selectedChildId.value = null
                    _babyState.value = null
                    _childProfile.value = null
                    _vaccineSchedule.value = emptyList()
                    return@launch
                }
                _selectedChildId.value = childId
                val profile = apiService.getChildProfile(authorization, childId)
                _babyState.value = profile.child
                _childProfile.value = profile
                
                if (profile.child.name.isNotBlank()) {
                    val completedVaccineIds = apiService.getVaccinations(authorization, profile.child.name)
                    generateSchedule(profile.child.dob, completedVaccineIds)
                } else {
                    _vaccineSchedule.value = emptyList()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Không thể tải thông tin bé: ${e.message}"
            }
        }
    }

    fun saveMeasurement(childId: String, measuredAt: String, weight: Double?, height: Double?, headCircumference: Double? = null, note: String? = null, onSuccess: (() -> Unit)? = null) {
        val authorization = authHeader() ?: return
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val req = com.example.babycare.data.remote.ChildMeasurementCreateRequest(
                    measuredAt = measuredAt,
                    weight = weight,
                    height = height,
                    headCircumference = headCircumference,
                    note = note
                )
                apiService.createChildMeasurement(authorization, childId, req)
                // Refresh profile to include new history
                val profile = apiService.getChildProfile(authorization, childId)
                _babyState.value = profile.child
                _childProfile.value = profile
                onSuccess?.invoke()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Không thể lưu lịch sử đo: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSelectedChild() {
        _selectedChildId.value = null
        _babyState.value = null
        _childProfile.value = null
        _growthAssessment.value = null
    }

    fun assessGrowth(baby: Baby, onSuccess: (() -> Unit)? = null) {
        val authorization = authHeader() ?: return
        val ageDays = computeAgeDays(baby.dob)
        val ageMonths = ageDays?.toDouble()?.div(30.4375)
        val sex = when (baby.gender.lowercase(Locale.getDefault())) {
            "nữ", "nu", "female", "girl", "f" -> "female"
            else -> "male"
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                val result = apiService.assessGrowth(
                    authorization,
                    GrowthAssessmentRequest(
                        childId = _selectedChildId.value,
                        sex = sex,
                        age_months = ageMonths,
                        age_days = ageDays?.toDouble(),
                        weight_kg = baby.weight,
                        height_cm = baby.height,
                        dob = baby.dob
                    )
                )
                _growthAssessment.value = result
                onSuccess?.invoke()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Không thể đánh giá phát triển: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadChatSessions(selectLatest: Boolean = true) {
        val authorization = authHeader() ?: return
        viewModelScope.launch {
            try {
                val sessions = apiService.getChatSessions(authorization)
                Log.d(TAG, "Loaded ${sessions.size} chat sessions")
                _chatSessions.value = sessions
                val selectedId = _selectedChatSessionId.value
                when {
                    selectedId != null && sessions.any { it.id == selectedId } -> {
                        selectChatSession(selectedId)
                    }
                    selectLatest && sessions.isNotEmpty() -> {
                        selectChatSession(sessions.first().id)
                    }
                    sessions.isEmpty() -> {
                        _selectedChatSessionId.value = null
                        _chatMessages.value = emptyList()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Không thể tải lịch sử chat: ${e.message}"
            }
        }
    }

    fun createNewChatSession() {
        val authorization = authHeader() ?: return
        viewModelScope.launch {
            try {
                val session = apiService.createChatSession(
                    authorization,
                    CreateChatSessionRequest(title = "Đoạn chat mới")
                )
                _selectedChatSessionId.value = session.id
                _chatMessages.value = emptyList()
                _chatSessions.value = listOf(session) + _chatSessions.value.filterNot { it.id == session.id }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Không thể tạo đoạn chat mới: ${e.message}"
            }
        }
    }

    fun selectChatSession(sessionId: String) {
        val authorization = authHeader() ?: return
        _selectedChatSessionId.value = sessionId
        viewModelScope.launch {
            try {
                _isChatLoading.value = true
                _chatMessages.value = apiService.getChatMessages(authorization, sessionId)
                Log.d(TAG, "selectChatSession=$sessionId loaded ${_chatMessages.value.size} messages")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Không thể tải nội dung chat: ${e.message}"
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun sendChatMessage(message: String) {
        val authorization = authHeader() ?: return
        val cleaned = message.trim()
        if (cleaned.isBlank()) return

        viewModelScope.launch {
            try {
                _isChatLoading.value = true
                val sessionId = _selectedChatSessionId.value ?: run {
                    val session = apiService.createChatSession(
                        authorization,
                        CreateChatSessionRequest(title = cleaned.take(80))
                    )
                    _selectedChatSessionId.value = session.id
                    _chatSessions.value = listOf(session) + _chatSessions.value.filterNot { it.id == session.id }
                    session.id
                }

                val response = apiService.sendChatMessage(
                    authorization,
                    sessionId,
                    SendChatMessageRequest(cleaned)
                )
                Log.d(TAG, "sendChatMessage response user='${response.userMessage.content}' assistant='${response.assistantMessage.content}'")
                _chatMessages.value = _chatMessages.value + response.userMessage + response.assistantMessage
                selectChatSession(sessionId)
                loadChatSessions(selectLatest = false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Không thể gửi tin nhắn AI: ${e.message}"
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun updateChatSessionTitle(sessionId: String, newTitle: String) {
        val authorization = authHeader() ?: return
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        
        viewModelScope.launch {
            try {
                apiService.updateChatSessionTitle(authorization, sessionId, mapOf("title" to trimmed))
                _chatSessions.value = _chatSessions.value.map {
                    if (it.id == sessionId) it.copy(title = trimmed) else it
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Không thể đổi tên: ${e.message}"
            }
        }
    }

    fun deleteChatSession(sessionId: String) {
        val authorization = authHeader() ?: return
        viewModelScope.launch {
            try {
                apiService.deleteChatSession(authorization, sessionId)
                _chatSessions.value = _chatSessions.value.filterNot { it.id == sessionId }
                if (_selectedChatSessionId.value == sessionId) {
                    _selectedChatSessionId.value = null
                    _chatMessages.value = emptyList()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Không thể xóa đoạn chat: ${e.message}"
            }
        }
    }

    private fun generateSchedule(dobString: String, completedIds: List<Int> = emptyList()) {
        val vaccines = repository.getRecommendedVaccines()
        
        try {
            val dob = appDateFormat.parse(dobString) ?: return
            val today = Calendar.getInstance().time

            val schedule = vaccines.map { vaccine ->
                val vaccineCal = Calendar.getInstance()
                vaccineCal.time = dob
                vaccineCal.add(Calendar.MONTH, vaccine.monthAge)
                val dueDate = vaccineCal.time
                
                val status = when {
                    completedIds.contains(vaccine.id) -> VaccinationStatus.COMPLETED
                    dueDate.before(today) -> VaccinationStatus.OVERDUE
                    else -> VaccinationStatus.UPCOMING
                }

                VaccineSchedule(
                    vaccine = vaccine,
                    dueDate = appDateFormat.format(dueDate),
                    status = status
                )
            }
            _vaccineSchedule.value = schedule
            generateAISuggestions(schedule)
        } catch (e: Exception) {
            _vaccineSchedule.value = emptyList()
        }
    }

    private fun computeAgeDays(dobString: String): Int? {
        return try {
            val dob = appDateFormat.parse(dobString) ?: return null
            val now = Calendar.getInstance().time
            val diff = now.time - dob.time
            if (diff < 0) 0 else (diff / (1000L * 60L * 60L * 24L)).toInt()
        } catch (_: ParseException) {
            null
        }
    }

    private fun generateAISuggestions(schedule: List<VaccineSchedule>) {
        val suggestions = mutableListOf<String>()
        val upcomingSoon = schedule.firstOrNull { it.status == VaccinationStatus.UPCOMING }

        // Removed loud overdue warning from AI suggestions to avoid occupying
        // the top of chat UI. Overdue info can still be shown in Notifications/Home.
        upcomingSoon?.let {
            suggestions.add("Gợi ý: Mũi tiêm tiếp theo của bé là '${it.vaccine.name}' vào ngày ${it.dueDate}.")
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Bé đang có sức khỏe tốt. Hãy tiếp tục theo dõi lịch tiêm chủng nhé!")
        }

        _aiSuggestions.value = suggestions
    }

    fun clearError() {
        _error.value = null
    }
}
