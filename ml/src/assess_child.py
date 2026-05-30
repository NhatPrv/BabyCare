"""Assess a single child's growth status using the trained model.

Usage examples:
  ./.venv/Scripts/python.exe ml/src/assess_child.py --sex male --age_months 36 --weight_kg 15 --height_cm 100
  ./.venv/Scripts/python.exe ml/src/assess_child.py --sex female --dob 2022-05-01 --measurement_date 2024-05-01 --weight_kg 9 --height_cm 85
"""
from __future__ import annotations

import argparse
import io
from pathlib import Path
import json
from contextlib import redirect_stdout, redirect_stderr

import joblib
import numpy as np
import pandas as pd

from feature_builder import build_features

import sys


RECOMMENDATIONS = {
    "obese": "Bé có dấu hiệu thừa cân/béo phì. Khuyến nghị: kiểm tra chế độ ăn và vận động; theo dõi định kỳ; khi nghi ngờ, tham vấn chuyên gia dinh dưỡng/bs nhi.",
    "overweight": "Bé hơi thừa cân. Khuyến nghị: cân bằng dinh dưỡng, tăng hoạt động thể chất, theo dõi.",
    "normal": "Bé có cân nặng phù hợp theo mô hình WHO. Duy trì chế độ dinh dưỡng hợp lý và theo dõi định kỳ.",
    "thin": "Bé có dấu hiệu thiếu cân. Khuyến nghị: kiểm tra dinh dưỡng, đánh giá tăng trưởng, tham vấn bs nếu cần.",
    "severe_thin": "Bé nghiêm trọng thiếu cân. Cần đánh giá y tế ngay và can thiệp dinh dưỡng chuyên sâu.",
}


def _json_safe(value):
    if isinstance(value, dict):
        return {str(key): _json_safe(val) for key, val in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_safe(item) for item in value]
    if isinstance(value, (np.integer, np.int64, np.int32)):
        return int(value)
    if isinstance(value, (np.floating, np.float64, np.float32)):
        return float(value)
    if isinstance(value, np.ndarray):
        return value.tolist()
    return value


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Assess a single child's growth status using the trained model.")
    default_model = Path(__file__).resolve().parents[2] / "ml" / "models" / "growth_model_lgbm_optuna.joblib"
    p.add_argument("--model", default=str(default_model), help="Path to .joblib model bundle")
    p.add_argument("--sex", required=True, help="male/female or equivalent")
    p.add_argument("--age_months", type=float, help="Age in months (optional if dob+measurement_date provided)")
    p.add_argument("--dob", help="Date of birth YYYY-MM-DD (optional)")
    p.add_argument("--measurement_date", help="Measurement date YYYY-MM-DD (optional)")
    p.add_argument("--weight_kg", type=float, required=True)
    p.add_argument("--height_cm", type=float, required=True)
    p.add_argument("--json", action="store_true", help="Output JSON")
    return p.parse_args()


def load_model(path: str | Path):
    bundle = joblib.load(path)
    return bundle


def make_row(args: argparse.Namespace) -> pd.DataFrame:
    row = {
        "sex": args.sex,
        "weight_kg": args.weight_kg,
        "height_cm": args.height_cm,
    }
    if args.age_months is not None:
        row["age_months"] = args.age_months
    if args.dob:
        row["dob"] = args.dob
    if args.measurement_date:
        row["measurement_date"] = args.measurement_date
    return pd.DataFrame([row])


def assess(args: argparse.Namespace) -> dict:
    bundle = load_model(args.model)
    pipeline = bundle["model"]
    label_encoder = bundle.get("label_encoder")
    classes = bundle.get("classes") or pipeline.classes_.tolist()
    if label_encoder is not None and hasattr(label_encoder, "classes_"):
        classes = label_encoder.inverse_transform(np.arange(len(classes))).tolist()

    df = make_row(args)
    features = build_features(df)
    X = features[bundle["feature_columns"]]

    probs = pipeline.predict_proba(X)[0]
    pred_index = int(pipeline.predict(X)[0])
    pred = classes[pred_index] if pred_index < len(classes) else str(pred_index)

    prob_by_class = {str(c): float(p) for c, p in zip(classes, probs)}
    top_prob = float(probs.max())

    rec = RECOMMENDATIONS.get(pred, "Không có khuyến nghị cụ thể.")

    return {
        "prediction": str(pred),
        "top_probability": top_prob,
        "probabilities": prob_by_class,
        "recommendation": rec,
    }


def main():
    args = parse_args()
    # try to ensure UTF-8 output for terminals on Windows
    try:
        if hasattr(sys.stdout, "reconfigure"):
            sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
        result = assess(args)
    if args.json:
        print(json.dumps(_json_safe(result), ensure_ascii=False, indent=2))
    else:
        print(f"Prediction: {result['prediction']} (prob={result['top_probability']:.3f})")
        print("Probabilities:")
        for k, v in sorted(result['probabilities'].items(), key=lambda x: -x[1]):
            print(f" - {k}: {v:.3f}")
        print("\nRecommendation:")
        print(result['recommendation'])


if __name__ == '__main__':
    main()
