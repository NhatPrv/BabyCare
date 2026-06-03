package com.example.babycare.data.repository

import com.example.babycare.data.model.Vaccine

class VaccineRepository {
    fun getRecommendedVaccines(): List<Vaccine> {
        return listOf(
            Vaccine(1, "Viêm gan B (Mũi sơ sinh)", "Tiêm trong vòng 24 giờ đầu sau sinh", 0),
            Vaccine(2, "Lao (BCG)", "Phòng bệnh lao, tiêm trong tháng đầu sau sinh", 0),
            Vaccine(3, "Bạch hầu, Ho gà, Uốn ván, Bại liệt, Hib, VGB (6 trong 1 - Mũi 1)", "Phòng 6 bệnh truyền nhiễm nguy hiểm, tiêm khi trẻ 2 tháng tuổi", 2),
            Vaccine(4, "Phế cầu (Mũi 1)", "Phòng viêm phổi, viêm màng não, viêm tai giữa do phế cầu khuẩn", 2),
            Vaccine(5, "Rota virus (Liều 1)", "Vaccine uống phòng bệnh tiêu chảy cấp do Rota virus", 2),
            Vaccine(6, "Bạch hầu, Ho gà, Uốn ván, Bại liệt, Hib, VGB (6 trong 1 - Mũi 2)", "Tiêm cách mũi 1 ít nhất 1 tháng", 3),
            Vaccine(7, "Phế cầu (Mũi 2)", "Tiêm cách mũi 1 ít nhất 1 tháng", 3),
            Vaccine(8, "Rota virus (Liều 2)", "Uống cách liều 1 ít nhất 1 tháng", 3),
            Vaccine(9, "Bạch hầu, Ho gà, Uốn ván, Bại liệt, Hib, VGB (6 trong 1 - Mũi 3)", "Tiêm cách mũi 2 ít nhất 1 tháng", 4),
            Vaccine(10, "Phế cầu (Mũi 3)", "Tiêm cách mũi 2 ít nhất 1 tháng", 4),
            Vaccine(11, "Bại liệt uống/tiêm (IPV - Mũi nhắc)", "Tăng cường miễn dịch bại liệt", 5),
            Vaccine(12, "Cúm (Mũi 1)", "Phòng cúm mùa, tiêm khi trẻ từ 6 tháng tuổi", 6),
            Vaccine(13, "Cúm (Mũi 2)", "Tiêm cách mũi 1 ít nhất 1 tháng", 7),
            Vaccine(14, "Não mô cầu B+C (Mũi 1)", "Phòng viêm màng não do não mô cầu khuẩn nhóm B, C", 6),
            Vaccine(15, "Não mô cầu B+C (Mũi 2)", "Tiêm cách mũi 1 khoảng 6-8 tuần", 8),
            Vaccine(16, "Sởi đơn (Mũi 1)", "Phòng bệnh sởi, tiêm khi trẻ đủ 9 tháng tuổi", 9),
            Vaccine(17, "Sởi - Quai bị - Rubella (MMR - Mũi 1)", "Phòng 3 bệnh sởi, quai bị, rubella", 12),
            Vaccine(18, "Thủy đậu (Mũi 1)", "Phòng bệnh thủy đậu (trái rạ)", 12),
            Vaccine(19, "Viêm não Nhật Bản (Mũi 1)", "Phòng bệnh viêm não Nhật Bản", 12),
            Vaccine(20, "Viêm gan A (Mũi 1)", "Phòng bệnh viêm gan A", 12),
            Vaccine(21, "Bạch hầu, Ho gà, Uốn ván, Bại liệt, Hib (Mũi 4 - Nhắc lại)", "Tiêm nhắc lại lúc 18 tháng tuổi", 18),
            Vaccine(22, "Sởi - Quai bị - Rubella (MMR - Mũi 2)", "Tiêm nhắc lại hoặc tiêm mũi 2", 18),
            Vaccine(23, "Viêm gan A (Mũi 2)", "Tiêm cách mũi 1 từ 6 - 12 tháng", 18)
        )
    }
}
