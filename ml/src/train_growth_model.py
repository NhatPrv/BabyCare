from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

import joblib
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.metrics import accuracy_score, classification_report
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.compose import ColumnTransformer

from feature_builder import FEATURE_COLUMNS, build_features, load_growth_csv


def make_pipeline() -> Pipeline:
    numeric_columns = FEATURE_COLUMNS
    preprocessor = ColumnTransformer(
        transformers=[
            ("num", Pipeline([("imputer", SimpleImputer(strategy="median"))]), numeric_columns)
        ],
        remainder="drop",
    )

    model = RandomForestClassifier(
        n_estimators=300,
        random_state=42,
        class_weight="balanced",
        min_samples_leaf=1,
    )

    return Pipeline([
        ("preprocess", preprocessor),
        ("model", model),
    ])


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train a child growth classification model.")
    parser.add_argument("--input", required=True, help="CSV file with measurements and a label column.")
    parser.add_argument("--output", required=True, help="Path to save the trained joblib model.")
    parser.add_argument("--report-dir", default="models/reports", help="Directory for metrics JSON.")
    parser.add_argument("--target-column", default="", help="Override the label column to train on.")
    parser.add_argument("--random-seed", type=int, default=42, help="Random seed for model and data split.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    input_path = Path(args.input)
    output_path = Path(args.output)
    report_dir = Path(args.report_dir)
    report_dir.mkdir(parents=True, exist_ok=True)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    raw = load_growth_csv(input_path)

    prepared = build_features(raw)
    target_candidates = [c for c in [args.target_column, "label", "nutritional_status", "who_class", "severity"] if c]

    target_column = None
    for candidate in target_candidates:
        if candidate in raw.columns:
            target_column = candidate
            break
        if candidate in prepared.columns:
            target_column = candidate
            break

    if target_column is None:
        raise ValueError("Input CSV must contain one of: label, nutritional_status, who_class, severity.")

    prepared["label"] = raw[target_column] if target_column in raw.columns else prepared[target_column]
    if target_column == "who_class" and "who_class" in prepared.columns:
        prepared["label"] = prepared["who_class"]
    # normalize and drop empty/NA labels
    prepared["label"] = prepared["label"].astype(object)
    prepared.loc[prepared["label"].isna(), "label"] = pd.NA
    prepared["label"] = prepared["label"].astype(str).str.strip()
    prepared = prepared[prepared["label"].ne("") & prepared["label"].ne("<NA>")].copy()
    prepared = prepared.dropna(subset=["label"]).copy()

    if prepared.empty:
        raise ValueError("No usable rows found after preprocessing.")

    X = prepared[FEATURE_COLUMNS]
    y = prepared["label"]

    class_counts = y.value_counts()
    stratify = y if len(class_counts) > 1 and class_counts.min() >= 2 else None
    test_size = 0.25 if len(prepared) >= 8 else 0.5

    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y,
        test_size=test_size,
        random_state=42,
        stratify=stratify,
    )

    pipeline = make_pipeline()
    # set random_state for reproducibility if pipeline contains RandomForest
    seed = int(args.random_seed) if hasattr(args, 'random_seed') else 42
    try:
        pipeline.named_steps["model"].set_params(random_state=seed)
    except Exception:
        pass

    pipeline.fit(X_train, y_train)

    predictions = pipeline.predict(X_test)
    accuracy = accuracy_score(y_test, predictions)
    report = classification_report(y_test, predictions, zero_division=0, output_dict=True)

    bundle = {
        "model": pipeline,
        "feature_columns": FEATURE_COLUMNS,
        "classes": sorted(y.unique().tolist()),
        "trained_at_utc": datetime.now(timezone.utc).isoformat(),
        "dataset_rows": int(len(prepared)),
        "accuracy": float(accuracy),
        "random_seed": seed,
    }
    joblib.dump(bundle, output_path)

    metrics = {
        "accuracy": float(accuracy),
        "report": report,
        "dataset_rows": int(len(prepared)),
        "class_counts": class_counts.to_dict(),
        "target_column": target_column,
        "random_seed": seed,
    }
    metrics_path = report_dir / f"growth_metrics_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    metrics_path.write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Saved model to: {output_path}")
    print(f"Rows used: {len(prepared)}")
    print(f"Accuracy: {accuracy:.4f}")
    print(f"Metrics: {metrics_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
