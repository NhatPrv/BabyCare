from __future__ import annotations

import argparse
from pathlib import Path

import joblib

from feature_builder import FEATURE_COLUMNS, build_features, load_growth_csv


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Predict child growth risk labels from a CSV file.")
    parser.add_argument("--model", required=True, help="Path to the trained .joblib model.")
    parser.add_argument("--input", required=True, help="CSV file with measurement rows.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    bundle = joblib.load(Path(args.model))
    pipeline = bundle["model"]

    raw = load_growth_csv(args.input)
    features = build_features(raw)
    missing = [column for column in FEATURE_COLUMNS if column not in features.columns]
    if missing:
        raise ValueError(f"Missing feature columns: {', '.join(missing)}")

    X = features[FEATURE_COLUMNS]
    predictions = pipeline.predict(X)

    print("Predictions:")
    for row_index, label in enumerate(predictions, start=1):
        print(f"- Row {row_index}: {label}")

    if hasattr(pipeline, "predict_proba"):
        proba = pipeline.predict_proba(X)
        classes = pipeline.named_steps["model"].classes_
        print("\nTop probability per row:")
        for row_index, row in enumerate(proba, start=1):
            best_index = int(row.argmax())
            print(f"- Row {row_index}: {classes[best_index]} ({row[best_index]:.3f})")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
