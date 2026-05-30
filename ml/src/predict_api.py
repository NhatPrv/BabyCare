from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

# Ensure ml/src is on sys.path when running from repo root
ROOT = Path(__file__).resolve().parents[2]
SRC = Path(__file__).resolve().parents[1]
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from feature_builder import build_features, FEATURE_COLUMNS


DEFAULT_MODEL = ROOT / "ml" / "models" / "growth_model_lgbm_optuna.joblib"


def load_model(path: str | Path = None):
    model_path = Path(path) if path is not None else DEFAULT_MODEL
    if not model_path.exists():
        raise FileNotFoundError(f"Model not found at {model_path}")
    return joblib.load(model_path)


def make_input_frame(kwargs: dict) -> pd.DataFrame:
    # Accept common measurement keys and build features using existing builder
    df = pd.DataFrame([kwargs])
    features = build_features(df)
    # Ensure all FEATURE_COLUMNS present
    for col in FEATURE_COLUMNS:
        if col not in features.columns:
            features[col] = np.nan
    feat = features[FEATURE_COLUMNS].astype(float).fillna(0.0)
    return feat


def predict(model, input_frame: pd.DataFrame) -> dict:
    X = input_frame.values
    if hasattr(model, "predict_proba"):
        probs = model.predict_proba(X)
        classes = model.classes_.tolist()
        top_idx = np.argmax(probs, axis=1)[0]
        return {
            "classes": classes,
            "probabilities": probs[0].tolist(),
            "predicted": classes[int(top_idx)],
        }
    else:
        pred = model.predict(X)
        return {"predicted": pred[0].tolist() if hasattr(pred[0], "tolist") else pred[0]}


def cli():
    parser = argparse.ArgumentParser(description="Predict child growth class using trained model")
    parser.add_argument("--model", help="Path to joblib model", default=str(DEFAULT_MODEL))
    parser.add_argument("--sex", help="Sex (male/female)")
    parser.add_argument("--age_months", type=float, help="Age in months")
    parser.add_argument("--age_days", type=float, help="Age in days")
    parser.add_argument("--weight_kg", type=float, help="Weight in kg")
    parser.add_argument("--height_cm", type=float, help="Height in cm")
    parser.add_argument("--head_circumference_cm", type=float, help="Head circumference in cm")
    parser.add_argument("--input_json", help="Path to JSON file with input fields")
    args = parser.parse_args()

    if args.input_json:
        with open(args.input_json, "r", encoding="utf8") as f:
            payload = json.load(f)
    else:
        payload = {
            "sex": args.sex,
            "age_months": args.age_months,
            "age_days": args.age_days,
            "weight_kg": args.weight_kg,
            "height_cm": args.height_cm,
            "head_circumference_cm": args.head_circumference_cm,
        }

    model = load_model(args.model)
    input_frame = make_input_frame(payload)
    result = predict(model, input_frame)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    cli()
