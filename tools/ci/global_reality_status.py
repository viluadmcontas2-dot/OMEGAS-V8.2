#!/usr/bin/env python3
"""Classify isolated fan-out command outcomes without confusing harness failure with product RED."""

INFRA_FAILURE_MARKERS = (
    "Could not resolve all files for configuration",
    "Could not resolve all dependencies for configuration",
)

def classify_failure(exit_code: int, output: str) -> str:
    if exit_code == 0:
        return "PASS"
    if any(marker in output for marker in INFRA_FAILURE_MARKERS):
        return "BROKEN"
    return "RED"
