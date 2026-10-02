"""Make the Temporal pure gate tests part of the existing canonical Python gate."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.temporal_omegas.test_gates import GateTests  # noqa: E402,F401

if __name__ == "__main__":
    unittest.main()
