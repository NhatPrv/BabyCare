import json
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))

import joblib
import numpy as np
import pandas as pd
from sklearn.impute import SimpleImputer
from sklearn.pipeline import Pipeline
from sklearn.compose import ColumnTransformer
from sklearn.model_selection import cross_val_score, StratifiedKFold
from sklearn.preprocessing import LabelEncoder

from feature_builder import FEATURE_COLUMNS, build_features


def main():
    data_path = Path('ml/data/external/nhanes/processed/nhanes_under24_combined_v2_features.csv')
    params_path = Path('ml/models/optuna/best_params.json')
    out_model = Path('ml/models/growth_model_lgbm_optuna.joblib')
    report_dir = Path('ml/models/reports')
    report_dir.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(data_path)
    df = df.dropna(subset=['who_class']).copy()

    # ensure features present
    features = build_features(df)
    X = features[FEATURE_COLUMNS].copy()

    for c in X.columns:
        if X[c].isna().all():
            X[c] = 0

    imp = SimpleImputer(strategy='median')
    X_imp = imp.fit_transform(X)

    y_raw = df['who_class'].astype(str).str.strip()
    le = LabelEncoder()
    y = le.fit_transform(y_raw)

    # load best params
    if not params_path.exists():
        raise SystemExit('Best params file not found: ' + str(params_path))
    params = json.loads(params_path.read_text(encoding='utf8'))['best_params']
    params = {k: v for k, v in params.items() if k not in ('num_class',)}

    try:
        import lightgbm as lgb
        ModelCls = lgb.LGBMClassifier
    except Exception:
        from sklearn.ensemble import HistGradientBoostingClassifier
        ModelCls = lambda **kw: HistGradientBoostingClassifier()

    model = ModelCls(**params)

    # cross-validate
    skf = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    scores = cross_val_score(model, X_imp, y, cv=skf, scoring='f1_weighted', n_jobs=-1)

    # fit on full data
    model.fit(X_imp, y)

    bundle = {
        'model': model,
        'imputer': imp,
        'feature_columns': FEATURE_COLUMNS,
        'label_encoder': le,
        'trained_at_utc': datetime.now(timezone.utc).isoformat(),
        'dataset_rows': int(len(df)),
        'optuna_best_params': params,
    }
    out_model.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(bundle, out_model)

    metrics = {
        'f1_weighted_cv_mean': float(scores.mean()),
        'f1_weighted_cv_std': float(scores.std()),
        'n_rows': int(len(df)),
        'trained_at_utc': bundle['trained_at_utc'],
    }
    metrics_path = report_dir / f"growth_metrics_lgbm_optuna_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    metrics_path.write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding='utf8')

    print(f"Saved final model to {out_model}")
    print(f"CV f1_weighted: {scores.mean():.4f} ± {scores.std():.4f}")


if __name__ == '__main__':
    main()
