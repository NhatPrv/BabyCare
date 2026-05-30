import argparse
import json
import os
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

from sklearn.model_selection import RepeatedStratifiedKFold
from sklearn.preprocessing import LabelEncoder
from sklearn.impute import SimpleImputer
from sklearn.metrics import classification_report, accuracy_score


def get_model():
    try:
        import lightgbm as lgb

        return lgb.LGBMClassifier
    except Exception:
        from sklearn.ensemble import HistGradientBoostingClassifier

        return lambda **kw: HistGradientBoostingClassifier(**{k.replace('n_estimators','max_iter'):v for k,v in kw.items()})


def main():
    p = argparse.ArgumentParser(description='Train LightGBM with repeated stratified CV')
    p.add_argument('--input', required=True)
    p.add_argument('--out-dir', required=True)
    p.add_argument('--target-column', default='who_class')
    p.add_argument('--folds', type=int, default=5)
    p.add_argument('--repeats', type=int, default=3)
    p.add_argument('--random-seed', type=int, default=42)
    p.add_argument('--n-estimators', type=int, default=100)
    p.add_argument('--early-stopping-rounds', type=int, default=10)
    p.add_argument('--test-size', type=float, default=0.0, help='fraction to hold out as final test (0 disables)')
    args = p.parse_args()

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(args.input)
    if args.target_column not in df.columns:
        raise SystemExit(f"Target column '{args.target_column}' not found in {args.input}")

    # Keep index to map OOF preds back
    idx = df.index.values

    y_raw = df[args.target_column].values
    if y_raw.dtype.kind in 'OUS':
        le = LabelEncoder()
        y = le.fit_transform(y_raw.astype(str))
        classes = le.classes_.tolist()
    else:
        le = None
        y = y_raw
        classes = np.unique(y).tolist()

    # Select numeric features only, drop ID-like columns
    drop_cols = {args.target_column, 'SEQN', 'seqn', 'sample_weight'} & set(df.columns)
    X = df.drop(columns=list(drop_cols | set([args.target_column])), errors='ignore')
    X = X.select_dtypes(include=[np.number])

    # Handle columns with all-missing values (SimpleImputer will drop them)
    all_na_cols = [c for c in X.columns if X[c].isna().all()]
    if all_na_cols:
        # fill all-NA numeric cols with 0 (feature absent across dataset)
        X[all_na_cols] = X[all_na_cols].fillna(0)

    # Impute missing
    imp = SimpleImputer(strategy='median')
    X_imputed = pd.DataFrame(imp.fit_transform(X), columns=X.columns, index=X.index)

    n_classes = len(np.unique(y))
    oof_proba = np.zeros((len(df), n_classes), dtype=float)

    rkf = RepeatedStratifiedKFold(n_splits=args.folds, n_repeats=args.repeats, random_state=args.random_seed)
    ModelCls = get_model()

    fold = 0
    per_fold_metrics = []

    for fold_idx, (train_idx, val_idx) in enumerate(rkf.split(X_imputed, y)):
        fold += 1
        print(f"Training fold {fold} ({len(train_idx)} train / {len(val_idx)} val)")

        model_kw = {'n_estimators': args.n_estimators, 'random_state': args.random_seed + fold_idx}
        model = ModelCls(**model_kw)

        # LightGBM supports early stopping via eval_set
        try:
            model.fit(X_imputed.iloc[train_idx], y[train_idx], eval_set=[(X_imputed.iloc[val_idx], y[val_idx])], early_stopping_rounds=args.early_stopping_rounds, verbose=False)
        except TypeError:
            # fallback if model does not support early stopping param names
            model.fit(X_imputed.iloc[train_idx], y[train_idx])

        # Save model
        model_path = out_dir / f"lgbm_fold_{fold_idx+1}.joblib"
        joblib.dump(model, model_path)

        # Predict proba
        if hasattr(model, 'predict_proba'):
            proba = model.predict_proba(X_imputed.iloc[val_idx])
        else:
            preds = model.predict(X_imputed.iloc[val_idx])
            proba = np.zeros((len(preds), n_classes))
            for i, c in enumerate(np.unique(preds)):
                proba[:, int(c)] = (preds == c).astype(float)

        oof_proba[val_idx, :] = proba

        # per-fold metrics
        y_pred = np.argmax(proba, axis=1)
        report = classification_report(y[val_idx], y_pred, output_dict=True)
        acc = accuracy_score(y[val_idx], y_pred)
        per_fold_metrics.append({'fold': fold_idx + 1, 'accuracy': acc, 'report': report})

    # Save OOF preds
    oof_df = pd.DataFrame(oof_proba, columns=[f'prob_class_{i}' for i in range(n_classes)], index=df.index)
    oof_df[args.target_column] = y_raw
    oof_path = out_dir / 'oof_predictions.csv'
    oof_df.to_csv(oof_path, index=True)

    # Aggregate metrics
    y_oof_pred = np.argmax(oof_proba, axis=1)
    agg_report = classification_report(y, y_oof_pred, output_dict=True)
    agg_acc = accuracy_score(y, y_oof_pred)

    metrics = {'aggregate_accuracy': float(agg_acc), 'aggregate_report': agg_report, 'per_fold': per_fold_metrics, 'classes': classes}
    metrics_path = out_dir / 'lgbm_cv_metrics.json'
    with open(metrics_path, 'w', encoding='utf8') as fh:
        json.dump(metrics, fh, indent=2, ensure_ascii=False)

    print(f"Saved models and OOF preds to {out_dir} — aggregate accuracy: {agg_acc:.4f}")


if __name__ == '__main__':
    main()
