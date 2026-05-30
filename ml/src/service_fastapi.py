from __future__ import annotations

from pathlib import Path
from typing import Optional

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from feature_builder import build_features


MODEL_PATH = Path(__file__).resolve().parents[2] / "ml" / "models" / "growth_model_lgbm_optuna.joblib"


class PredictRequest(BaseModel):
    sex: Optional[str] = None
    age_months: Optional[float] = None
    age_days: Optional[float] = None
    weight_kg: Optional[float] = None
    height_cm: Optional[float] = None
    head_circumference_cm: Optional[float] = None


app = FastAPI(title="Growth Model Service")


def load_bundle(path: Path = MODEL_PATH):
    if not path.exists():
        raise FileNotFoundError(f"Model bundle not found at {path}")
    bundle = joblib.load(path)
    return bundle


@app.on_event("startup")
def startup():
    global BUNDLE, PIPELINE, IMPUTER, FEATURE_COLUMNS, LABEL_ENCODER
    BUNDLE = load_bundle(MODEL_PATH)
    PIPELINE = BUNDLE.get("model")
    IMPUTER = BUNDLE.get("imputer")
    FEATURE_COLUMNS = BUNDLE.get("feature_columns")
    LABEL_ENCODER = BUNDLE.get("label_encoder")


@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": bool(PIPELINE)}


@app.post("/predict")
def predict(req: PredictRequest):
    payload = req.dict()
    try:
        df = pd.DataFrame([payload])
        features = build_features(df)
        X = features[FEATURE_COLUMNS].copy()

        # fill all-NA columns
        for c in X.columns:
            if X[c].isna().all():
                X[c] = 0

        if IMPUTER is not None:
            X_imp = IMPUTER.transform(X)
        else:
            X_imp = X.values

        # model may be stored under 'model' key as a fitted estimator
        model = PIPELINE
        if hasattr(model, "predict_proba"):
            probs = model.predict_proba(X_imp)[0]
            if LABEL_ENCODER is not None:
                classes = LABEL_ENCODER.inverse_transform(np.arange(len(probs))).tolist()
            else:
                classes = model.classes_.tolist()
            top_idx = int(np.argmax(probs))
            predicted = classes[top_idx]
            prob_by_class = {str(c): float(p) for c, p in zip(classes, probs)}
        else:
            pred = model.predict(X_imp)[0]
            predicted = str(pred)
            prob_by_class = {predicted: 1.0}

        # also include WHO features computed by feature_builder
        who_class = features.get("who_class").iloc[0] if "who_class" in features.columns else None
        who_zscore = features.get("who_zscore").iloc[0] if "who_zscore" in features.columns else None
        bmi = features.get("bmi").iloc[0] if "bmi" in features.columns else None

        return {
            "prediction": predicted,
            "probabilities": prob_by_class,
            "who_class": str(who_class) if pd.notna(who_class) else None,
            "who_zscore": float(who_zscore) if pd.notna(who_zscore) else None,
            "bmi": float(bmi) if pd.notna(bmi) else None,
        }
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
