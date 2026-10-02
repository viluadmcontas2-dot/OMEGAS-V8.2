"""Shared setup for the CodSpeed benchmark suite.

The benchmarked modules are standalone scripts (not an installed package), so
their directories are added to ``sys.path`` here.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

for relative in ("scripts/omegas", "lab/contracts", "tests"):
    path = str(ROOT / relative)
    if path not in sys.path:
        sys.path.insert(0, path)
