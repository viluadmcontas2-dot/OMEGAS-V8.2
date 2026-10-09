#!/usr/bin/env python3
"""Contracts for an OFFLINE Map K causal experiment. Never sends ECU commands."""
import json
import math
import sys
import unittest
from io import StringIO
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools" / "map_k"))
from map_k_probe import audit_events, proportional_target_percent, controlled_gain


def sample(seq, fuel="GNV", rpm=1750, state="SAMPLE_ACCEPTED"):
    return {"type": "telemetry", "sequence": seq, "data": {
        "fuel": fuel, "rpm": rpm, "sample_state": state,
        "petrol_ms": 4.8, "load_bar": 0.43, "water_c": 85}}


def confirmed(seq=100, readback=True):
    return {"sequence": seq, "type": "k_batch_confirmed", "data": {
        "calibrationType": "MAP_K", "humanConfirmed": True,
        "readbackValid": readback, "cells": [
            {"row": 4, "column": 0, "current": 160, "target": 171}]}}


class EvidenceGateTests(unittest.TestCase):
    def test_requires_petrol_and_gnv_in_both_windows(self):
        events = [sample(i) for i in range(20)]
        events.append(confirmed())
        events.extend(sample(i, f) for i in range(101, 141) for f in ("GNV", "GASOLINA"))
        report = audit_events(events)
        self.assertEqual(report["interventions"], 1)
        self.assertIn("SEM_GASOLINA_ANTES", report["blockers"])
        self.assertEqual(report["status"], "NAO_IDENTIFICADO")

    def test_invalid_readback_not_admitted(self):
        report = audit_events([confirmed(readback=False)])
        self.assertEqual(report["interventions"], 0)
        self.assertIn("SEM_MAP_K_CONFIRMADO", report["blockers"])

    def test_cannot_mix_burn_modes_or_cutoff_into_experiment(self):
        events = [sample(i, "GASOLINA", 890) for i in range(10)]
        events.extend(sample(i + 10, "CUTOFF") for i in range(10))
        events.extend(sample(i + 20, "GNV", 1700, "SAMPLE_REJECTED") for i in range(10))
        events.append(confirmed(40))
        report = audit_events(events)
        self.assertEqual(report["before"], {"GNV": 0, "GASOLINA": 0})

    def test_intervening_curve_change_contaminates_followup(self):
        events = [sample(i, f) for i in range(30) for f in ("GNV", "GASOLINA")]
        events.append(confirmed(100))
        events += [sample(i, f) for i in range(101, 160) for f in ("GNV", "GASOLINA")]
        events.append({"sequence": 161, "type": "k_factor_batch_confirmed", "data": {"readbackValid": True}})
        report = audit_events(events)
        self.assertTrue(report["curve_changed_after_map"])
        self.assertEqual(report["after"], {"GNV": 59, "GASOLINA": 59})

    def test_no_percent_without_independent_sensitivity(self):
        self.assertIsNone(proportional_target_percent(1.09, None))
        self.assertIsNone(proportional_target_percent(1.09, controlled_gain(0.9, sessions=1)))
        self.assertIsNone(proportional_target_percent(1.09, controlled_gain(0.0, sessions=4)))
        self.assertIsNone(proportional_target_percent(1.09, controlled_gain(0.9, sessions=4, consistent=False)))

    def test_proportional_direction_gain_and_limit(self):
        model = controlled_gain(1.0, sessions=4)
        p = proportional_target_percent(1.10, model, damping=0.5)
        self.assertAlmostEqual(p, 100 * (math.exp(-math.log(1.1) * 0.5) - 1), places=8)
        self.assertGreater(proportional_target_percent(0.90, model), 0.0)
        self.assertGreater(proportional_target_percent(1.10, controlled_gain(-1.0, sessions=4)), 0)
        self.assertAlmostEqual(proportional_target_percent(2.0, model), -5.0)

    def test_nonfinite_or_unphysical_ratio_abstains(self):
        model = controlled_gain(1.0, sessions=4)
        for bad in (float("nan"), float("inf"), -1, 0):
            self.assertIsNone(proportional_target_percent(bad, model))


if __name__ == "__main__":
    unittest.main()
