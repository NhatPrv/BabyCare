import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))

import numpy as np
import pandas as pd
from sklearn.impute import SimpleImputer
from sklearn.model_selection import StratifiedKFold, cross_val_score
from sklearn.preprocessing import LabelEncoder

try:
    import lightgbm as lgb
except Exception:
    lgb = None

try:
    import optuna
except Exception:
    optuna = None

from feature_builder import FEATURE_COLUMNS


def objective(trial, X, y):
    if optuna is None:
        raise RuntimeError('optuna not installed')

    param = {
        'objective': 'multiclass',
        'num_class': len(np.unique(y)),
        'learning_rate': trial.suggest_loguniform('learning_rate', 1e-3, 0.3),
        'num_leaves': trial.suggest_int('num_leaves', 8, 256),
        'feature_fraction': trial.suggest_float('feature_fraction', 0.5, 1.0),
        'bagging_fraction': trial.suggest_float('bagging_fraction', 0.5, 1.0),
        'bagging_freq': trial.suggest_int('bagging_freq', 0, 10),
        'min_child_samples': trial.suggest_int('min_child_samples', 5, 100),
        'lambda_l1': trial.suggest_float('lambda_l1', 0.0, 10.0),
        'lambda_l2': trial.suggest_float('lambda_l2', 0.0, 10.0),
        'verbosity': -1,
        'seed': 42,
        'n_estimators': 200,
    }

    model = lgb.LGBMClassifier(**param)
    # 3-fold stratified CV
    skf = StratifiedKFold(n_splits=3, shuffle=True, random_state=42)
    scores = cross_val_score(model, X, y, cv=skf, scoring='f1_weighted', n_jobs=1)
    return float(scores.mean())


def main(trials=20):
    if optuna is None:
        raise SystemExit('Optuna not installed in environment.')
    if lgb is None:
        raise SystemExit('LightGBM not installed in environment.')

    data_path = Path('ml/data/external/nhanes/processed/nhanes_under24_combined_v2_features.csv')
    df = pd.read_csv(data_path)
    df = df.dropna(subset=['who_class']).copy()
    y_raw = df['who_class'].astype(str).str.strip()
    le = LabelEncoder()
    y = le.fit_transform(y_raw)

    X = df[FEATURE_COLUMNS].copy()
    for c in X.columns:
        if X[c].isna().all():
            X[c] = 0

    imp = SimpleImputer(strategy='median')
    X_imp = imp.fit_transform(X)

    study = optuna.create_study(direction='maximize')
    func = lambda trial: objective(trial, X_imp, y)
    study.optimize(func, n_trials=trials, show_progress_bar=True)

    best = study.best_params
    best['n_estimators'] = 400
    best['learning_rate'] = float(best.get('learning_rate', 0.05))
    best['num_class'] = len(np.unique(y))

    out_dir = Path('ml/models/optuna')
    out_dir.mkdir(parents=True, exist_ok=True)
    with open(out_dir / 'best_params.json', 'w', encoding='utf8') as fh:
        json.dump({'best_params': best, 'best_value': study.best_value}, fh, indent=2, ensure_ascii=False)

    print('Best score:', study.best_value)
    print('Best params saved to', out_dir / 'best_params.json')


if __name__ == '__main__':
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument('--trials', type=int, default=20)
    args = parser.parse_args()
    main(trials=args.trials)
