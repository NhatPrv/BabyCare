# BabyCare Growth Model Training

Folder này tách riêng để bạn train model mà không ảnh hưởng backend/app chính.

## Nguồn dữ liệu nên dùng

Lấy từ database các bảng sau:

- `children`: `dob`, `gender`
- `child_measurements`: `measured_at`, `weight`, `height`, `head_circumference`
- `growth_assessments`: `classification` nếu bạn đã có nhãn từ rule engine hoặc bác sĩ
- `vaccination_records`: có thể dùng sau này làm feature phụ

## Cách làm thực tế

1. Xuất dữ liệu từ DB ra CSV.
2. Điền cột `label` bằng rule WHO/CDC hoặc bác sĩ xác nhận; nếu có `zscore` thì script sẽ tự suy ra `percentile` và nhãn WHO (`who_class`).
3. Train model trên bất kỳ máy nào có Python.
4. Copy file model `.joblib` về máy dev chính nếu muốn chạy inference.

## Form dữ liệu để mang sang máy khác

File template:

- `ml/forms/growth_input_template.csv`

Chỉ cần mở CSV đó bằng Excel/Google Sheets rồi điền dữ liệu.

## Cài dependencies

```powershell
cd d:\MyDATA\SEMESTER6\Chuyende\Final
.venv\Scripts\python.exe -m pip install -r ml\requirements.txt
```

## Train model

```powershell
cd d:\MyDATA\SEMESTER6\Chuyende\Final\ml
..\.venv\Scripts\python.exe src\train_growth_model.py --input data\training_data_2to19.csv --output models\growth_model.joblib --target-column nutritional_status
```

## Predict thử

```powershell
cd d:\MyDATA\SEMESTER6\Chuyende\Final\ml
..\.venv\Scripts\python.exe src\predict_growth.py --model models\growth_model.joblib --input data\sample_growth_records.csv
```

## Kết luận về RAM máy bạn

Với máy có 12 GB RAM như ảnh, train model tabular bằng `RandomForest` hoặc `LogisticRegression` thường là nhẹ:

- dataset nhỏ đến vừa: thường chỉ vài trăm MB hoặc ít hơn
- vẫn có thể mở backend và làm tính năng khác song song
- nếu dataset lớn hoặc train nhiều cây hơn, nên train trong terminal riêng hoặc máy khác

Không nên dùng deep learning cho bài toán này nếu dữ liệu còn ít.
