import sys
import glob
import json
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))

import joblib
import numpy as np
import pandas as pd
from sklearn.impute import SimpleImputer
from sklearn.metrics import accuracy_score, classification_report
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder

from feature_builder import FEATURE_COLUMNS


def main():
    data_path = Path('ml/data/external/nhanes/processed/nhanes_under24_combined_v2_features.csv')
    out_dir = Path('ml/models/lgbm_runs')
    out_dir.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(data_path)
    if 'who_class' not in df.columns:
        raise SystemExit('who_class not present in features file; run feature builder first')

    df = df.dropna(subset=['who_class']).copy()
    y_raw = df['who_class'].astype(str).str.strip()
    le = LabelEncoder()
    y = le.fit_transform(y_raw)

    X = df[FEATURE_COLUMNS].copy()
    # fill all-NA numeric cols
    for c in X.columns:
        if X[c].isna().all():
            X[c] = 0

    imp = SimpleImputer(strategy='median')
    X_imp = pd.DataFrame(imp.fit_transform(X), columns=X.columns, index=X.index)

    X_train, X_hold, y_train, y_hold = train_test_split(X_imp, y, test_size=0.15, random_state=42, stratify=y)

    # load saved fold models
    model_paths = sorted(glob.glob(str(out_dir / 'lgbm_fold_*.joblib')))
    if not model_paths:
        raise SystemExit('No fold models found in ml/models/lgbm_runs/')

    probs = []
    # ensure correct column order and use numpy array for prediction
    X_hold_arr = X_hold[FEATURE_COLUMNS].to_numpy()
    for mp in model_paths:
        m = joblib.load(mp)
        if hasattr(m, 'predict_proba'):
            p = m.predict_proba(X_hold_arr)
        else:
            preds = m.predict(X_hold_arr)
            n_classes = len(le.classes_)
            p = np.zeros((len(preds), n_classes))
            for i, val in enumerate(preds):
                p[i, int(val)] = 1.0
        probs.append(p)

    avg_proba = np.mean(probs, axis=0)
    y_pred = np.argmax(avg_proba, axis=1)

    acc = accuracy_score(y_hold, y_pred)
    report = classification_report(y_hold, y_pred, target_names=le.classes_, output_dict=True, zero_division=0)

    out = {
        'holdout_accuracy': float(acc),
        'n_holdout': int(len(y_hold)),
        'classes': le.classes_.tolist(),
        'report': report,
        'models_used': model_paths,
    }

    with open(out_dir / 'holdout_evaluation.json', 'w', encoding='utf8') as fh:
        json.dump(out, fh, indent=2, ensure_ascii=False)

    print(f"Hold-out accuracy: {acc:.4f} (n={len(y_hold)})")


if __name__ == '__main__':
    main()
