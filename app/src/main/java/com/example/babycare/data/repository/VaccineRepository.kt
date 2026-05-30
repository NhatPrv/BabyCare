package com.example.babycare.data.repository

import com.example.babycare.data.model.Vaccine

class VaccineRepository {
    fun getRecommendedVaccines(): List<Vaccine> {
        return listOf(
            Vaccine(1, "Viêm gan B (Mũi sơ sinh)", "Tiêm trong vòng 24h sau sinh", 0),
            Vaccine(2, "Lao (BCG)", "Tiêm một lần cho trẻ sơ sinh", 0),
            Vaccine(3, "Bạch hầu, Ho gà, Uốn ván, Bại liệt, Hib (Mũi 1)", "Vaccine 5 trong 1 hoặc 6 trong 1", 2),
            Vaccine(4, "Phế cầu (Mũi 1)", "Phòng viêm phổi, viêm màng não do phế cầu", 2),
            Vaccine(5, "Rota virus (Liều 1)", "Vaccine uống phòng tiêu chảy", 2),
            Vaccine(6, "Bạch hầu, Ho gà, Uốn ván, Bại liệt, Hib (Mũi 2)", "Tiêm cách mũi 1 ít nhất 1 tháng", 3),
            Vaccine(7, "Phế cầu (Mũi 2)", "Tiêm cách mũi 1 ít nhất 1 tháng", 3),
            Vaccine(8, "Rota virus (Liều 2)", "Uống cách liều 1 ít nhất 1 tháng", 3),
            Vaccine(9, "Bạch hầu, Ho gà, Uốn ván, Bại liệt, Hib (Mũi 3)", "Tiêm cách mũi 2 ít nhất 1 tháng", 4),
            Vaccine(10, "Phế cầu (Mũi 3)", "Tiêm cách mũi 2 ít nhất 1 tháng", 4),
            Vaccine(11, "Sởi (Mũi 1)", "Tiêm khi trẻ đủ 9 tháng tuổi", 9),
            Vaccine(12, "Viêm não Nhật Bản", "Tiêm khi trẻ đủ 12 tháng tuổi", 12)
        )
    }
}
