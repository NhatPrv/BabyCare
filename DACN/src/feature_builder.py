from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

from who_growth import enrich_with_who_reference, percentile_from_zscore, classification_from_zscore


FEATURE_COLUMNS = [
    "age_days",
    "age_months",
    "gender_code",
    "weight_kg",
    "height_cm",
    "head_circumference_cm",
    "bmi",
    "who_zscore",
    "who_percentile",
    "weight_delta_30d",
    "height_delta_30d",
    "measurement_interval_days",
]


GENDER_MAP = {
    "nam": 1,
    "male": 1,
    "boy": 1,
    "m": 1,
    "nu": 0,
    "nữ": 0,
    "female": 0,
    "girl": 0,
    "f": 0,
}


def _normalize_gender(value: object) -> str:
    if pd.isna(value):
        return "unknown"
    return str(value).strip().lower()


def load_growth_csv(path: str | Path) -> pd.DataFrame:
    return pd.read_csv(path)


def build_features(df: pd.DataFrame) -> pd.DataFrame:
    frame = df.copy()

    if "measurement_date" in frame.columns:
        frame["measurement_date"] = pd.to_datetime(frame["measurement_date"], errors="coerce")
    if "dob" in frame.columns:
        frame["dob"] = pd.to_datetime(frame["dob"], errors="coerce", dayfirst=True)

    if "age_days" not in frame.columns:
        frame["age_days"] = pd.NA
    if "age_months" not in frame.columns:
        frame["age_months"] = pd.NA

    if "measurement_date" in frame.columns and "dob" in frame.columns:
        valid_dates = frame["measurement_date"].notna() & frame["dob"].notna()
        frame.loc[valid_dates, "age_days"] = (frame.loc[valid_dates, "measurement_date"] - frame.loc[valid_dates, "dob"]).dt.days.clip(lower=0)

    frame["age_days"] = pd.to_numeric(frame["age_days"], errors="coerce")
    frame["age_months"] = pd.to_numeric(frame["age_months"], errors="coerce")
    frame.loc[frame["age_days"].notna() & frame["age_months"].isna(), "age_months"] = frame.loc[frame["age_days"].notna() & frame["age_months"].isna(), "age_days"] / 30.4375
    frame.loc[frame["age_months"].notna() & frame["age_days"].isna(), "age_days"] = frame.loc[frame["age_months"].notna() & frame["age_days"].isna(), "age_months"] * 30.4375

    if "gender_code" not in frame.columns:
        frame["gender_code"] = pd.NA
    if "gender" in frame.columns:
        frame["gender_code"] = frame["gender"].map(lambda value: GENDER_MAP.get(_normalize_gender(value), -1))
    elif "sex" in frame.columns:
        frame["gender_code"] = frame["sex"].map(lambda value: GENDER_MAP.get(_normalize_gender(value), -1))

    frame["gender_code"] = pd.to_numeric(frame["gender_code"], errors="coerce")

    frame["weight_kg"] = pd.to_numeric(frame["weight_kg"], errors="coerce")
    frame["height_cm"] = pd.to_numeric(frame["height_cm"], errors="coerce")
    if "head_circumference_cm" in frame.columns:
        frame["head_circumference_cm"] = pd.to_numeric(frame["head_circumference_cm"], errors="coerce")
    else:
        frame["head_circumference_cm"] = pd.NA

    height_m = frame["height_cm"] / 100.0
    frame["bmi"] = frame["weight_kg"] / (height_m ** 2)
    frame.loc[~height_m.gt(0), "bmi"] = pd.NA

    if "who_zscore" not in frame.columns:
        frame["who_zscore"] = pd.NA
    if "who_percentile" not in frame.columns:
        frame["who_percentile"] = pd.NA

    if "zscore" in frame.columns:
        frame["who_zscore"] = pd.to_numeric(frame["zscore"], errors="coerce").where(frame["who_zscore"].isna(), frame["who_zscore"])
    if "percentile" in frame.columns:
        frame["who_percentile"] = pd.to_numeric(frame["percentile"], errors="coerce").where(frame["who_percentile"].isna(), frame["who_percentile"])

    frame = enrich_with_who_reference(frame)
    frame["who_zscore"] = pd.to_numeric(frame["who_zscore"], errors="coerce")
    frame["who_percentile"] = pd.to_numeric(frame["who_percentile"], errors="coerce")

    missing_percentile = frame["who_percentile"].isna() & frame["who_zscore"].notna()
    frame.loc[missing_percentile, "who_percentile"] = frame.loc[missing_percentile, "who_zscore"].map(percentile_from_zscore)

    if "who_class" not in frame.columns:
        frame["who_class"] = pd.NA
    frame["who_class"] = frame["who_class"].where(frame["who_class"].notna(), frame["who_zscore"].map(classification_from_zscore))

    if "child_id" in frame.columns and "measurement_date" in frame.columns:
        frame = frame.sort_values(["child_id", "measurement_date"]).copy()
        grouped = frame.groupby("child_id", dropna=False)

        prev_weight = grouped["weight_kg"].shift(1)
        prev_height = grouped["height_cm"].shift(1)
        prev_date = grouped["measurement_date"].shift(1)

        interval_days = (frame["measurement_date"] - prev_date).dt.days
        frame["measurement_interval_days"] = interval_days.fillna(0).clip(lower=0)

        safe_interval = interval_days.where(interval_days > 0)
        frame["weight_delta_30d"] = ((frame["weight_kg"] - prev_weight) * 30 / safe_interval).replace([pd.NA, pd.NaT], pd.NA)
        frame["height_delta_30d"] = ((frame["height_cm"] - prev_height) * 30 / safe_interval).replace([pd.NA, pd.NaT], pd.NA)

        frame["weight_delta_30d"] = pd.to_numeric(frame["weight_delta_30d"], errors="coerce").fillna(0)
        frame["height_delta_30d"] = pd.to_numeric(frame["height_delta_30d"], errors="coerce").fillna(0)
    else:
        frame["measurement_interval_days"] = 0
        frame["weight_delta_30d"] = 0
        frame["height_delta_30d"] = 0

    if "metric" in frame.columns:
        frame["metric"] = pd.to_numeric(frame["metric"], errors="coerce")

    # Ensure feature columns are numeric and use numpy.nan instead of pandas.NA
    for col in FEATURE_COLUMNS:
        if col in frame.columns:
            frame[col] = pd.to_numeric(frame[col], errors="coerce")
            frame[col] = frame[col].where(frame[col].notna(), np.nan)

    return frame


def get_feature_frame(df: pd.DataFrame) -> pd.DataFrame:
    features = build_features(df)
    for column in FEATURE_COLUMNS:
        if column not in features.columns:
            features[column] = pd.NA
    return features[FEATURE_COLUMNS]
