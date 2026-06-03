"""Convert NHANES XPT raw files into a combined processed CSV for age<24 months.

Reads pairs of DEMO_*.XPT and BMX_*.XPT from the raw folder, merges them on SEQN,
normalizes column names to `age_months`, `sex`, `weight_kg`, `height_cm`, and
writes `ml/data/external/nhanes/processed/nhanes_under24_combined.csv`.

Usage:
  python ml/src/preprocess_nhanes_raw.py --raw ml/data/external/nhanes/raw --out ml/data/external/nhanes/processed
"""
from __future__ import annotations

import argparse
from pathlib import Path
import sys
import warnings

try:
    import pyreadstat
    read_xport = lambda p: pyreadstat.read_xport(p)
except Exception:
    import pandas as pd
    def read_xport(p):
        # pandas read_sas supports xport for many installs
        return (pd.read_sas(p, format='xport'), None)

import pandas as pd


def detect_column(cols, patterns):
    uc = [c.upper() for c in cols]
    for pat in patterns:
        for i, c in enumerate(uc):
            if pat in c:
                return cols[i]
    return None


def normalize_demo(df):
    # detect SEQN
    seq = detect_column(df.columns, ['SEQN'])
    if seq is None:
        raise RuntimeError('No SEQN in DEMO file')
    df = df.rename(columns={seq: 'SEQN'})

    # age in months if available
    age_m_col = detect_column(df.columns, ['RIDAGEMN', 'RIDAGE_MN', 'RIDAGEM'])
    age_y_col = detect_column(df.columns, ['RIDAGEYR', 'RIDAGEY'])
    if age_m_col:
        df['age_months'] = pd.to_numeric(df[age_m_col], errors='coerce')
    elif age_y_col:
        df['age_months'] = pd.to_numeric(df[age_y_col], errors='coerce') * 12
    else:
        df['age_months'] = pd.NA

    # sex
    sex_col = detect_column(df.columns, ['RIAGENDR', 'SEX', 'RIAGEND'])
    if sex_col:
        df['sex'] = df[sex_col].map({1: 'male', 2: 'female'}).fillna(df[sex_col].astype(str))
    else:
        df['sex'] = pd.NA

    return df[['SEQN', 'age_months', 'sex']]


def normalize_bmx(df):
    seq = detect_column(df.columns, ['SEQN'])
    if seq is None:
        raise RuntimeError('No SEQN in BMX file')
    df = df.rename(columns={seq: 'SEQN'})

    weight_col = detect_column(df.columns, ['BMXWT', 'WT', 'WEIGHT', 'WTKG'])
    height_col = detect_column(df.columns, ['BMXHT', 'HT', 'HEIGHT', 'LENGTH'])

    if weight_col:
        df['weight_kg'] = pd.to_numeric(df[weight_col], errors='coerce')
    else:
        df['weight_kg'] = pd.NA

    if height_col:
        df['height_cm'] = pd.to_numeric(df[height_col], errors='coerce')
    else:
        df['height_cm'] = pd.NA

    return df[['SEQN', 'weight_kg', 'height_cm']]


def process_cycle(demo_path: Path, bmx_path: Path):
    try:
        demo_df, _ = read_xport(str(demo_path))
    except Exception as e:
        warnings.warn(f'Failed to read DEMO {demo_path}: {e}')
        return pd.DataFrame()
    try:
        bmx_df, _ = read_xport(str(bmx_path))
    except Exception as e:
        warnings.warn(f'Failed to read BMX {bmx_path}: {e}')
        return pd.DataFrame()

    demo = normalize_demo(demo_df)
    bmx = normalize_bmx(bmx_df)

    merged = pd.merge(demo, bmx, on='SEQN', how='inner')
    # drop rows without weight/height
    merged = merged.dropna(subset=['weight_kg', 'height_cm'])
    # coerce numeric
    merged['age_months'] = pd.to_numeric(merged['age_months'], errors='coerce')
    merged['weight_kg'] = pd.to_numeric(merged['weight_kg'], errors='coerce')
    merged['height_cm'] = pd.to_numeric(merged['height_cm'], errors='coerce')

    return merged


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--raw', default='ml/data/external/nhanes/raw')
    p.add_argument('--out', default='ml/data/external/nhanes/processed')
    args = p.parse_args()

    raw = Path(args.raw)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    demo_files = sorted(raw.glob('*DEMO*.XPT'))
    bmx_files = sorted(raw.glob('*BMX*.XPT'))

    if not demo_files or not bmx_files:
        print('No DEMO or BMX XPT files found in', raw)
        return 1

    combined = []
    # pair files by filename prefix (e.g., '19992000')
    for demo in demo_files:
        prefix = demo.stem.split('_')[0]
        match = None
        for b in bmx_files:
            if b.stem.split('_')[0] == prefix:
                match = b
                break
        if not match:
            warnings.warn(f'No BMX match for {demo.name}, skipping')
            continue
        print('Processing', demo.name, 'with', match.name)
        df = process_cycle(demo, match)
        if not df.empty:
            combined.append(df)

    if not combined:
        print('No data processed from cycles')
        return 1

    all_df = pd.concat(combined, ignore_index=True)
    # filter under 24 months
    all_df = all_df[all_df['age_months'].notna()]
    try:
        under24 = all_df[all_df['age_months'] < 24]
    except Exception:
        under24 = all_df[all_df['age_months'].astype(float) < 24]

    out_file = out / 'nhanes_under24_combined.csv'
    under24.to_csv(out_file, index=False)
    print('Wrote', out_file, 'rows:', len(under24))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
