"""Download and preprocess NHANES BMX/exam files for specified cycles.

Dry-run by default: lists URLs to fetch. Use --download to actually download and process.

Example:
  python ml/src/fetch_nhanes_cycles.py --download --out ml/data/external/nhanes/raw
"""
from __future__ import annotations

import argparse
from pathlib import Path
import sys
import textwrap

NHANES_BASE = "https://wwwn.cdc.gov/Nchs/Nhanes/"

# Cycles to include: NHANES III (nhanes3) and continuous cycles 1999-2018
CONTINUOUS_CYCLES = [
    "1999-2000","2001-2002","2003-2004","2005-2006","2007-2008",
    "2009-2010","2011-2012","2013-2014","2015-2016","2017-2018"
]

# CDC DataFiles use letter codes for continuous cycles: A=1999-2000, B=2001-2002, ... J=2017-2018
CONTINUOUS_CODES = ['A','B','C','D','E','F','G','H','I','J']

def build_urls():
    urls = []
    # NHANES III datasets live under different structure; skip automatic NHANES III for now
    for cyc, code in zip(CONTINUOUS_CYCLES, CONTINUOUS_CODES):
        # Prefer CDC DataFiles path with letter codes (DEMO_I.XPT, BMX_I.XPT, etc.)
        base = f"https://wwwn.cdc.gov/Nchs/Data/Nhanes/Public/{cyc}/DataFiles/"
        urls.append((cyc, base + f"BMX_{code}.XPT"))
        urls.append((cyc, base + f"DEMO_{code}.XPT"))
    return urls


def parse_args():
    p = argparse.ArgumentParser(description="NHANES cycle fetcher (dry-run by default)")
    p.add_argument("--download", action="store_true", help="Actually download files")
    p.add_argument("--out", default="ml/data/external/nhanes/raw", help="Output folder for raw files")
    return p.parse_args()


def main():
    args = parse_args()
    out = Path(args.out)
    urls = build_urls()
    print("Planned NHANES files to fetch (dry-run):")
    for cyc, url in urls:
        print(f"- {cyc}: {url}")

    if not args.download:
        print(textwrap.dedent("""
            Dry-run complete. To actually download and preprocess, re-run with --download.
            Note: NHANES file names and paths can vary; this script attempts common patterns but
            may need manual adjustments per cycle. Downloading all cycles may be hundreds of MB.
        """))
        return 0

    # If download requested, attempt to fetch files and save into out folder.
    import requests
    out.mkdir(parents=True, exist_ok=True)
    for cyc, url in urls:
        fname = out / f"{cyc.replace('-', '')}_{Path(url).name}"
        print(f"Downloading {url} -> {fname} ...")
        try:
            r = requests.get(url, timeout=60)
            r.raise_for_status()
            with open(fname, 'wb') as fh:
                fh.write(r.content)
            print("Saved", fname)
        except Exception as e:
            print("Failed to download", url, e)

    print("Download pass complete. You may need to run preprocessing to convert XPT -> CSV and merge DEM/BMX files.")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
