# DACN Colab Folder

Đây là folder riêng để bạn upload lên Colab và train lại model cho phần trình bày học máy.

Nên upload nguyên folder này, không cần chọn lẻ từng file.

## File cần có
- `requirements.txt`
- `src/feature_builder.py`
- `src/who_growth.py`
- `src/preprocess_nhanes_raw.py`
- `src/train_lgbm_optuna.py`
- `src/train_lgbm_final.py`
- Các file hỗ trợ trong `src/`
- `sql/export_growth_dataset.sql` nếu bạn muốn export dữ liệu từ PostgreSQL

## Data lấy ở đâu
Bạn có 2 cách:
1. Cách dễ nhất: export CSV từ database bằng `sql/export_growth_dataset.sql`, rồi train trực tiếp trên file CSV đó.
2. Cách đầy đủ hơn: tải raw NHANES `.XPT` vào Colab, chạy `preprocess_nhanes_raw.py` để tạo CSV xử lý trước, rồi mới train.

## Luồng chạy trên Colab
1. Upload nguyên folder `DACN` lên Google Drive hoặc workspace Colab.
2. Cài dependencies từ `requirements.txt`.
3. Chuẩn bị data theo một trong hai cách ở trên.
4. Chạy `train_lgbm_optuna.py` để tối ưu tham số.
5. Chạy `train_lgbm_final.py` để tạo model cuối cùng.

## Đường dẫn đầu ra
- Raw NHANES input: `ml/data/external/nhanes/raw`
- Processed output: `ml/data/external/nhanes/processed`
- Final model output: `ml/models/growth_model_lgbm_optuna.joblib`

Nếu mục tiêu là demo nhanh để trình bày, dùng luôn CSV export từ database là ngắn nhất.
