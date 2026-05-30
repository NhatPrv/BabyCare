"""Run multiple training runs with different random seeds and summarize results.

Usage:
  .venv\Scripts\python.exe ml/src/run_multiple_trains.py --seeds 1 2 3
"""
from __future__ import annotations

import argparse
import subprocess
from pathlib import Path
import json
import csv


def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--seeds", nargs="+", type=int, required=True)
    p.add_argument("--input", default="ml/data/combined_24m_plus.csv")
    p.add_argument("--output-dir", default="ml/models/reports")
    p.add_argument("--target-column", default="who_class")
    return p.parse_args()


def run_train(seed: int, input_path: str, output_model: str, target_column: str):
    cmd = [
        ".venv\\Scripts\\python.exe",
        "ml\\src\\train_growth_model.py",
        "--input",
        input_path,
        "--output",
        output_model,
        "--target-column",
        target_column,
        "--random-seed",
        str(seed),
    ]
    print("Running:", " ".join(cmd))
    proc = subprocess.run(cmd, capture_output=True, text=True)
    return proc


def main():
    args = parse_args()
    outdir = Path(args.output_dir)
    outdir.mkdir(parents=True, exist_ok=True)
    summary_path = outdir / "multiple_runs_summary.csv"

    rows = []
    for seed in args.seeds:
        model_out = f"ml/models/growth_model_seed_{seed}.joblib"
        proc = run_train(seed, args.input, model_out, args.target_column)
        print(proc.stdout)
        if proc.returncode != 0:
            print(f"Train failed for seed {seed}", proc.stderr)
            continue

        # find latest metrics JSON in reports
        import glob
        from datetime import datetime

        metrics_files = sorted(glob.glob(str(outdir / "growth_metrics_*.json")))
        metrics = None
        if metrics_files:
            latest = metrics_files[-1]
            with open(latest, "r", encoding="utf-8") as fh:
                metrics = json.load(fh)

        rows.append({
            "seed": seed,
            "model": model_out,
            "metrics_file": latest if metrics else "",
            "accuracy": metrics.get("accuracy") if metrics else None,
            "dataset_rows": metrics.get("dataset_rows") if metrics else None,
        })

    # write summary CSV
    if rows:
        with open(summary_path, "w", newline='', encoding="utf-8") as csvfile:
            writer = csv.DictWriter(csvfile, fieldnames=["seed", "model", "metrics_file", "accuracy", "dataset_rows"])
            writer.writeheader()
            for r in rows:
                writer.writerow(r)
        print("Wrote summary:", summary_path)


if __name__ == "__main__":
    main()
