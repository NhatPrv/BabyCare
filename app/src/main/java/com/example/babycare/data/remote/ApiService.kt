package com.example.babycare.data.remote

import com.example.babycare.data.model.Appointment
import com.example.babycare.data.model.Baby
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.DELETE

@Serializable
data class VaccinationRequest(
    val babyName: String,
    val vaccineId: Int
)

@Serializable
data class AuthRequest(
    val username: String,
    val password: String,
    val fullName: String? = null
)

@Serializable
data class AuthResponse(
    val token: String? = null,
    val success: Boolean = false,
    val error: String? = null
)

@Serializable
data class ChatSessionDto(
    val id: String,
    val title: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class ChatMessageDto(
    val id: String,
    val role: String,
    val content: String,
    val model: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class CreateChatSessionRequest(
    val title: String? = null
)

@Serializable
data class SendChatMessageRequest(
    val message: String
)

@Serializable
data class SendChatMessageResponse(
    val userMessage: ChatMessageDto,
    val assistantMessage: ChatMessageDto
)

@Serializable
data class ChildUpsertRequest(
    val name: String,
    val dob: String,
    val weight: Double,
    val height: Double,
    val gender: String,
    val blood_type: String? = null,
    val note: String? = null
)

@Serializable
data class GrowthAssessmentRequest(
    val childId: String? = null,
    val sex: String? = null,
    val age_months: Double? = null,
    val age_days: Double? = null,
    val weight_kg: Double? = null,
    val height_cm: Double? = null,
    val head_circumference_cm: Double? = null,
    val dob: String? = null,
    val measurement_date: String? = null
)

@Serializable
data class GrowthAssessmentResponse(
    val prediction: String? = null,
    val top_probability: Double? = null,
    val recommendation: String? = null,
    val who_class: String? = null,
    val who_zscore: Double? = null,
    val bmi: Double? = null,
    val probabilities: Map<String, Double> = emptyMap(),
    val model_result: JsonElement? = null
)

@Serializable
data class ChildMeasurementDto(
    val id: String,
    @SerialName("childId") val childId: String? = null,
    @SerialName("measuredAt") val measuredAt: String = "",
    @SerialName("measuredAtKey") val measuredAtKey: String = "",
    val weight: Double? = null,
    val height: Double? = null,
    @SerialName("headCircumference") val headCircumference: Double? = null,
    val note: String? = null,
    @SerialName("createdAt") val createdAt: String = "",
    val assessment: GrowthAssessmentRecordDto? = null
)

@Serializable
data class GrowthAssessmentRecordDto(
    val id: String,
    @SerialName("childId") val childId: String? = null,
    @SerialName("measurementId") val measurementId: String? = null,
    @SerialName("modelVersion") val modelVersion: String? = null,
    val bmi: Double? = null,
    @SerialName("weightForAgeZ") val weightForAgeZ: Double? = null,
    @SerialName("heightForAgeZ") val heightForAgeZ: Double? = null,
    @SerialName("bmiForAgeZ") val bmiForAgeZ: Double? = null,
    val classification: String? = null,
    @SerialName("riskLevel") val riskLevel: String? = null,
    val summary: String? = null,
    val recommendations: JsonElement? = null,
    val recommendation: String? = null,
    @SerialName("createdAt") val createdAt: String = ""
)

@Serializable
data class ChildProfileResponse(
    val child: Baby,
    @SerialName("latestAssessment") val latestAssessment: GrowthAssessmentRecordDto? = null,
    @SerialName("measurementHistory") val measurementHistory: List<ChildMeasurementDto> = emptyList()
)

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body request: AuthRequest): AuthResponse

    @GET("baby")
    suspend fun getBabyInfo(@Header("Authorization") authorization: String): Baby

    @POST("baby")
    suspend fun updateBabyInfo(
        @Header("Authorization") authorization: String,
        @Body baby: Baby
    )

    @GET("children")
    suspend fun getChildren(@Header("Authorization") authorization: String): List<Baby>

    @GET("children/{childId}")
    suspend fun getChildById(
        @Header("Authorization") authorization: String,
        @Path("childId") childId: String
    ): Baby

    @GET("children/{childId}/profile")
    suspend fun getChildProfile(
        @Header("Authorization") authorization: String,
        @Path("childId") childId: String
    ): ChildProfileResponse

    @POST("children")
    suspend fun createChild(
        @Header("Authorization") authorization: String,
        @Body request: ChildUpsertRequest
    ): Baby

    @PUT("children/{childId}")
    suspend fun updateChild(
        @Header("Authorization") authorization: String,
        @Path("childId") childId: String,
        @Body request: ChildUpsertRequest
    ): Baby

    @GET("appointments")
    suspend fun getAppointments(@Header("Authorization") authorization: String): List<Appointment>

    @POST("appointments")
    suspend fun addAppointment(
        @Header("Authorization") authorization: String,
        @Body appointment: Appointment
    )

    @GET("vaccinations/{babyName}")
    suspend fun getVaccinations(
        @Header("Authorization") authorization: String,
        @Path("babyName") babyName: String
    ): List<Int>

    @POST("vaccinations")
    suspend fun markVaccinationCompleted(
        @Header("Authorization") authorization: String,
        @Body request: VaccinationRequest
    )

    @GET("chat/sessions")
    suspend fun getChatSessions(
        @Header("Authorization") authorization: String
    ): List<ChatSessionDto>

    @POST("chat/sessions")
    suspend fun createChatSession(
        @Header("Authorization") authorization: String,
        @Body request: CreateChatSessionRequest
    ): ChatSessionDto

    @GET("chat/sessions/{sessionId}/messages")
    suspend fun getChatMessages(
        @Header("Authorization") authorization: String,
        @Path("sessionId") sessionId: String
    ): List<ChatMessageDto>

    @POST("chat/sessions/{sessionId}/messages")
    suspend fun sendChatMessage(
        @Header("Authorization") authorization: String,
        @Path("sessionId") sessionId: String,
        @Body request: SendChatMessageRequest
    ): SendChatMessageResponse

    @PUT("chat/sessions/{sessionId}")
    suspend fun updateChatSessionTitle(
        @Header("Authorization") authorization: String,
        @Path("sessionId") sessionId: String,
        @Body update: Map<String, String>
    )

    @DELETE("chat/sessions/{sessionId}")
    suspend fun deleteChatSession(
        @Header("Authorization") authorization: String,
        @Path("sessionId") sessionId: String
    )

    @POST("api/growth/assess")
    suspend fun assessGrowth(
        @Header("Authorization") authorization: String,
        @Body request: GrowthAssessmentRequest
    ): GrowthAssessmentResponse
}
