package com.example.babycare.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Vaccine(
    val id: Int,
    val name: String,
    val description: String,
    val monthAge: Int, // Độ tuổi cần tiêm (tính theo tháng)
    val isMandatory: Boolean = true
)
