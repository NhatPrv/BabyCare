from pathlib import Path
import sys

p = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(r"D:\MyDATA\SEMESTER6\Chuyende\Final\ml\data\external\nhanes\raw\19992000_DEMO_19992000.XPT")
if not p.exists():
    print('File not found:', p)
    raise SystemExit(2)
with open(p, 'rb') as f:
    head = f.read(512)
print('LEN', len(head))
print('START_BYTES', head[:64])
try:
    print('AS_TEXT:', head[:256].decode('utf-8', errors='replace'))
except Exception:
    pass
print('HEX:', head[:64].hex())
