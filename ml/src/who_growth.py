from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
from math import erf, sqrt
from pathlib import Path

import numpy as np
import pandas as pd


DATA_DIR = Path(__file__).resolve().parents[1] / "data"


@dataclass(frozen=True)
class WHOReference:
    kind: str
    sex: str
    x_column: str
    table: pd.DataFrame


def percentile_from_zscore(zscore: float | int | None) -> float | pd.NA:
    if zscore is None or pd.isna(zscore):
        return pd.NA
    z_value = float(zscore)
    return 100.0 * 0.5 * (1.0 + erf(z_value / sqrt(2.0)))


def zscore_from_lms(measurement: float, l_value: float, m_value: float, s_value: float) -> float | pd.NA:
    if measurement is None or pd.isna(measurement):
        return pd.NA
    if m_value is None or pd.isna(m_value) or s_value is None or pd.isna(s_value):
        return pd.NA

    measurement_value = float(measurement)
    median = float(m_value)
    scale = float(s_value)
    shape = float(l_value) if l_value is not None and not pd.isna(l_value) else 0.0

    if measurement_value <= 0 or median <= 0 or scale <= 0:
        return pd.NA

    if abs(shape) < 1e-8:
        return float(np.log(measurement_value / median) / scale)
    return float((((measurement_value / median) ** shape) - 1.0) / (shape * scale))


def classification_from_zscore(zscore: float | int | None) -> str | pd.NA:
    if zscore is None or pd.isna(zscore):
        return pd.NA

    value = float(zscore)
    if value < -3:
        return "severe_thin"
    if value < -2:
        return "thin"
    if value <= 1:
        return "normal"
    if value <= 2:
        return "overweight"
    return "obese"


def _pick_workbook(folder_name: str, sex: str, token: str) -> Path:
    folder = DATA_DIR / folder_name
    if not folder.exists():
        raise FileNotFoundError(f"WHO folder not found: {folder}")

    matches = [
        candidate
        for candidate in folder.rglob("*.xlsx")
        if sex in candidate.name.lower() and token in candidate.name.lower() and "zscore" in candidate.name.lower()
    ]
    if not matches:
        raise FileNotFoundError(f"No WHO workbook found for {folder_name} / {sex} / {token}")
    return sorted(matches)[0]


@lru_cache(maxsize=16)
def load_reference(kind: str, sex: str) -> WHOReference:
    normalized_kind = kind.lower().strip()
    normalized_sex = sex.lower().strip()

    if normalized_kind == "wfl":
        workbook = _pick_workbook("Weight-for-length_height", normalized_sex, "wfl")
    elif normalized_kind == "bfa":
        workbook = _pick_workbook("Body mass index-for-age (BMI-for-age)", normalized_sex, "acfa")
    elif normalized_kind == "wfa":
        workbook = _pick_workbook("Weight-for-age", normalized_sex, "wfa")
    elif normalized_kind == "lfa":
        workbook = _pick_workbook("Length-height-for-age", normalized_sex, "lhfa")
    else:
        raise ValueError(f"Unsupported WHO kind: {kind}")

    table = pd.read_excel(workbook, sheet_name=0)
    table.columns = [str(column).strip() for column in table.columns]

    x_column = next(
        (column for column in table.columns if column.lower() in {"day", "age", "height", "length"}),
        None,
    )
    if x_column is None:
        raise ValueError(f"Cannot identify x-axis column in workbook: {workbook}")

    numeric_columns = [x_column, "L", "M", "S"]
    for column in numeric_columns:
        table[column] = pd.to_numeric(table[column], errors="coerce")
    table = table.dropna(subset=[x_column, "L", "M", "S"]).sort_values(x_column).reset_index(drop=True)

    return WHOReference(kind=normalized_kind, sex=normalized_sex, x_column=x_column, table=table)


def interpolate_reference(reference: WHOReference, x_value: float) -> pd.Series:
    table = reference.table
    x_series = table[reference.x_column].astype(float)

    if x_value <= float(x_series.iloc[0]):
        return table.iloc[0]
    if x_value >= float(x_series.iloc[-1]):
        return table.iloc[-1]

    interpolated = {
        column: float(np.interp(x_value, x_series, table[column].astype(float)))
        for column in ["L", "M", "S"]
    }
    return pd.Series(interpolated)


def compute_who_score(kind: str, sex: str, x_value: float, measurement: float) -> tuple[float | pd.NA, float | pd.NA]:
    reference = load_reference(kind, sex)
    row = interpolate_reference(reference, float(x_value))
    zscore = zscore_from_lms(measurement, row["L"], row["M"], row["S"])
    percentile = percentile_from_zscore(zscore)
    return zscore, percentile


def classify_row(row: pd.Series) -> pd.Series:
    sex_value = str(row.get("gender_code", row.get("sex", ""))).strip().lower()
    if sex_value in {"1", "male", "boy", "m", "nam"}:
        sex = "boys"
    else:
        sex = "girls"

    age_months = pd.to_numeric(row.get("age_months"), errors="coerce")
    age_days = pd.to_numeric(row.get("age_days"), errors="coerce")
    weight_kg = pd.to_numeric(row.get("weight_kg"), errors="coerce")
    height_cm = pd.to_numeric(row.get("height_cm"), errors="coerce")
    bmi = pd.to_numeric(row.get("bmi"), errors="coerce")

    if pd.notna(age_days) and pd.notna(weight_kg):
        try:
            zscore, percentile = compute_who_score("wfa", sex, float(age_days), float(weight_kg))
            return pd.Series({"who_metric": "wfa", "who_zscore": zscore, "who_percentile": percentile, "who_class": classification_from_zscore(zscore)})
        except Exception:
            pass

    if pd.notna(age_days) and pd.notna(height_cm):
        try:
            zscore, percentile = compute_who_score("lfa", sex, float(age_days), float(height_cm))
            return pd.Series({"who_metric": "lfa", "who_zscore": zscore, "who_percentile": percentile, "who_class": classification_from_zscore(zscore)})
        except Exception:
            pass

    if pd.notna(height_cm) and pd.notna(weight_kg):
        try:
            if pd.notna(age_months) and float(age_months) < 24:
                zscore, percentile = compute_who_score("wfl", sex, float(height_cm), float(weight_kg))
                return pd.Series({"who_metric": "wfl", "who_zscore": zscore, "who_percentile": percentile, "who_class": classification_from_zscore(zscore)})
            if pd.notna(age_months) and float(age_months) <= 60 and pd.notna(bmi):
                zscore, percentile = compute_who_score("bfa", sex, float(age_days if pd.notna(age_days) else age_months * 30.4375), float(bmi))
                return pd.Series({"who_metric": "bfa", "who_zscore": zscore, "who_percentile": percentile, "who_class": classification_from_zscore(zscore)})
        except Exception:
            pass

    return pd.Series({"who_metric": pd.NA, "who_zscore": pd.NA, "who_percentile": pd.NA, "who_class": pd.NA})


def enrich_with_who_reference(frame: pd.DataFrame) -> pd.DataFrame:
    enriched = frame.copy()
    who_columns = enriched.apply(classify_row, axis=1)
    for column in who_columns.columns:
        if column not in enriched.columns:
            enriched[column] = who_columns[column]
        else:
            enriched[column] = enriched[column].where(enriched[column].notna(), who_columns[column])
    return enriched
