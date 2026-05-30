package com.example.babycare.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Appointment(
    val id: String = "",
    val serviceType: String = "", // Khám bệnh, Tiêm vaccine, Tư vấn
    val hospitalName: String = "",
    val date: String = "",
    val time: String = "",
    val status: String = "Chờ xác nhận" // Chờ xác nhận, Đã xác nhận, Đã từ chối
)
