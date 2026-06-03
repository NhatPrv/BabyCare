import pandas as pd
from pathlib import Path

src = Path("ml/data/external/nhanes/processed/nhanes_children_24m_plus.csv")
if not src.exists():
    print(f"Source not found: {src}")
    raise SystemExit(1)

df = pd.read_csv(src)
changed = False
# Map common alternate names to expected names
if "length_height_cm" in df.columns and "height_cm" not in df.columns:
    df["height_cm"] = df["length_height_cm"]
    changed = True
if "length_cm" in df.columns and "height_cm" not in df.columns:
    df["height_cm"] = df["length_cm"]
    changed = True
if "height" in df.columns and "height_cm" not in df.columns:
    df["height_cm"] = df["height"]
    changed = True

out = src.with_name(src.stem + "_fix" + src.suffix)
if changed:
    df.to_csv(out, index=False)
    print(f"Wrote fixed CSV: {out}")
else:
    # still write a copy to keep pipeline stable
    df.to_csv(out, index=False)
    print(f"No column rename needed; wrote copy: {out}")
