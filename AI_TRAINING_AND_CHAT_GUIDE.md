# BabyCare AI: hướng dẫn A-Z

## 1. Chia đúng 3 bài toán AI

### A. Đánh giá tăng trưởng/thể trạng

Không nên bắt đầu bằng LLM hoặc model tự train để kết luận bé mập/gầy. Cách đúng là:

1. Lưu số đo theo thời gian: cân nặng, chiều cao, vòng đầu, ngày đo.
2. Tính tuổi theo ngày/tháng và giới tính.
3. Tính các chỉ số chuẩn: BMI, weight-for-age z-score, height-for-age z-score, BMI-for-age z-score.
4. Phân loại theo z-score/percentile.
5. Model ML chỉ dùng để cá nhân hóa gợi ý ăn uống, phát hiện xu hướng tăng/giảm bất thường, hoặc ưu tiên cảnh báo.

Lý do: WHO/CDC đã có chuẩn tăng trưởng theo tuổi và giới tính. Với bài toán sức khỏe, chuẩn thống kê y khoa đáng tin hơn model tự train từ dữ liệu ít.

### B. Gợi ý/cảnh báo lịch tiêm

Bài toán này không cần train model lúc đầu. Nên dùng rule engine:

1. Dựa vào `children.dob`.
2. Join `vaccine_schedule`.
3. Tính `due_date = dob + recommended_month_age`.
4. Nếu còn 0-14 ngày: `due_soon`.
5. Nếu quá ngày mà chưa có `vaccination_records.status = completed`: `missed`.
6. Lưu cảnh báo vào `vaccine_ai_alerts`.

Sau này có thể train model để dự đoán phụ huynh nào dễ bỏ lỡ lịch, nhưng MVP không cần.

### C. AI chat

Chat không cần train model riêng. Dùng API LLM như Gemini API, gửi kèm ngữ cảnh bé và lịch sử hội thoại. Backend lưu:

1. `chat_sessions`: mỗi đoạn chat ở thanh trái.
2. `chat_messages`: nội dung user/assistant.
3. Không gọi API AI trực tiếp từ Android để tránh lộ API key.

## 2. Database đã thêm

Migration nằm ở:

```text
backend/sql/03_ai_growth_and_chat_schema.sql
```

Bảng tăng trưởng:

```text
child_measurements
growth_assessments
```

Bảng cảnh báo tiêm chủng:

```text
vaccine_ai_alerts
```

Bảng chat:

```text
chat_sessions
chat_messages
```

Chạy migration:

```powershell
$env:PGPASSWORD='123456'
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' `
  -U postgres -h localhost -p 5432 `
  -d babycare `
  -f 'd:\MyDATA\SEMESTER6\Chuyende\Final\backend\sql\03_ai_growth_and_chat_schema.sql'
```

Backend cũng đã có `CREATE TABLE IF NOT EXISTS`, nên khi chạy `node backend\index.js` nó cũng tự tạo bảng nếu DB chưa có.

## 3. Pipeline train model tăng trưởng

### Bước 1: Thu thập dữ liệu

Nguồn dữ liệu nội bộ:

```text
children: dob, gender
child_measurements: measured_at, weight, height, head_circumference
vaccination_records: lịch sử tiêm
appointments: lịch hẹn
```

Nguồn chuẩn:

```text
WHO growth standards: trẻ dưới 5 tuổi
CDC growth charts: trẻ lớn hơn, nếu app mở rộng sau này
```

### Bước 2: Tạo feature

Mỗi dòng training tương ứng một lần đo:

```text
age_days
age_months
gender
weight
height
bmi
weight_delta_30d
height_delta_30d
bmi_delta_30d
weight_for_age_z
height_for_age_z
bmi_for_age_z
missed_vaccine_count
```

### Bước 3: Tạo label

MVP nên dùng label rule-based:

```text
severe_thin
thin
normal
risk_overweight
overweight
obese
growth_slow
growth_fast
```

Sau này nếu có bác sĩ/nhãn chuyên môn thì label có thể là:

```text
needs_nutrition_review
needs_pediatric_visit
normal_follow_up
```

### Bước 4: Chọn model

MVP:

```text
Rule-based z-score classifier
```

Nếu bắt buộc có train model:

```text
RandomForestClassifier
XGBoost/LightGBM
LogisticRegression baseline
```

Không nên dùng deep learning khi dữ liệu ít.

### Bước 5: Train

Thư mục đề xuất:

```text
ml/
  data/
  notebooks/
  src/
    train_growth_model.py
    evaluate_growth_model.py
    predict_growth.py
  models/
    growth_model.pkl
```

Pseudo-code:

```python
df = load_measurements_from_postgres()
df = add_age_features(df)
df = add_bmi(df)
df = add_who_z_scores(df)
df["label"] = classify_from_z_scores(df)

X_train, X_test, y_train, y_test = train_test_split(...)
model = RandomForestClassifier(...)
model.fit(X_train, y_train)
evaluate(model, X_test, y_test)
joblib.dump(model, "ml/models/growth_model.pkl")
```

### Bước 6: Deploy model

Có 2 hướng:

1. Node.js gọi Python script: dễ làm cho đồ án.
2. Tách FastAPI service `/predict-growth`: sạch hơn nếu muốn mở rộng.

Khuyến nghị đồ án:

```text
Backend Node.js chính
Python FastAPI riêng cho ML
PostgreSQL dùng chung
```

## 4. AI chat bằng Gemini API

Backend đã thêm các endpoint:

```text
GET  /chat/sessions
POST /chat/sessions
GET  /chat/sessions/:sessionId/messages
POST /chat/sessions/:sessionId/messages
```

Thêm vào `backend/.env`:

```env
GEMINI_API_KEY=AIza...
GEMINI_MODEL=gemini-2.5-flash
```

Không có `GEMINI_API_KEY` thì backend vẫn lưu chat, nhưng trả lời fallback để nhắc bạn cấu hình key.

Luồng app:

1. User mở Chatbot.
2. App gọi `GET /chat/sessions` để hiện thanh trái.
3. Bấm “đoạn chat mới” thì gọi `POST /chat/sessions`.
4. Gửi tin nhắn thì gọi `POST /chat/sessions/:id/messages`.
5. Backend lưu user message, gọi Gemini `generateContent`, lưu assistant message, trả về cả hai.

## 5. Prompt an toàn cho chat

System prompt nên có:

```text
Bạn là trợ lý BabyCare cho phụ huynh Việt Nam.
Không chẩn đoán bệnh.
Không thay thế bác sĩ.
Luôn khuyên gặp bác sĩ nhi khi có dấu hiệu nguy hiểm.
Khi nói về tăng trưởng và tiêm chủng, chỉ hỗ trợ tham khảo.
```

Không nên để AI tự bịa phác đồ điều trị, liều thuốc, hoặc kết luận bệnh.

## 6. Thứ tự triển khai khuyến nghị

1. Hoàn thiện DB và backend chat history.
2. Gắn Android ChatbotScreen với API chat.
3. Làm rule engine đánh giá lịch tiêm.
4. Làm rule engine z-score tăng trưởng.
5. Sau khi có dữ liệu mẫu, train model ML để cá nhân hóa gợi ý.
6. Thêm màn biểu đồ tăng trưởng và cảnh báo.

## 7. Nội dung có thể viết trong báo cáo

Tên module:

```text
AI-based Growth and Vaccination Advisory Module
```

Mô tả:

```text
Hệ thống kết hợp chuẩn tăng trưởng WHO/CDC, rule engine lịch tiêm chủng và mô hình học máy để hỗ trợ phụ huynh theo dõi thể trạng, tốc độ phát triển và các mũi tiêm cần thiết của trẻ. AI chat dùng mô hình ngôn ngữ lớn Gemini qua backend để tư vấn thông tin phổ thông, đồng thời lưu lịch sử hội thoại theo từng tài khoản phụ huynh.
```

Giới hạn:

```text
Kết quả chỉ có tính chất tham khảo, không thay thế chẩn đoán hoặc chỉ định của bác sĩ.
```
