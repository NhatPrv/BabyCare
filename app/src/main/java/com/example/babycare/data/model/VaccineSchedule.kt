package com.example.babycare.data.model

import kotlinx.serialization.Serializable

@Serializable
data class VaccineSchedule(
    val vaccine: Vaccine,
    val dueDate: String,
    val status: VaccinationStatus = VaccinationStatus.UPCOMING
)

@Serializable
enum class VaccinationStatus {
    UPCOMING,
    OVERDUE,
    COMPLETED
}
