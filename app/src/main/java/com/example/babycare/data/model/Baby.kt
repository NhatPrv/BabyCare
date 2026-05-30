package com.example.babycare.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Baby(
    val id: String? = null,
    val name: String = "",
    val dob: String = "",
    val weight: Double = 0.0,
    val height: Double = 0.0,
    val gender: String = "Nam"
)
