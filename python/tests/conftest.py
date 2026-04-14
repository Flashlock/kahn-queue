import sys
from pathlib import Path

# Ensure `python/src` is importable when running tests from `python/`.
ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
sys.path.insert(0, str(SRC))

