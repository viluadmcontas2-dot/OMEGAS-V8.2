#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def remove_file(rel: str) -> None:
    target = ROOT / rel
    if target.is_file():
        target.unlink()


def replace(rel: str, old: str, new: str) -> None:
    target = ROOT / rel
    text = target.read_text(encoding="utf-8")
    if old in text:
        target.write_text(text.replace(old, new), encoding="utf-8")


# One-time remote migration: browser suggestion decision math is retired.
remove_file("app/src/main/assets/ui/suggestion-model.js")
remove_file("tests/ui/suggestion-model.test.cjs")
replace(
    "app/src/main/assets/ui/index.html",
    '  <script src="suggestion-model.js" defer></script>\n',
    "",
)

# Fail closed if the old browser authority is still wired anywhere in the UI shell.
for rel in [
    "app/src/main/assets/ui/index.html",
    "app/src/main/assets/ui/components/drawers.js",
    "app/src/main/assets/ui/app.js",
]:
    source = (ROOT / rel).read_text(encoding="utf-8")
    for token in [
        "OmegasSuggestionModel",
        "kFactorSuggestions",
        "mapResidualPredictions",
        "mapResidualSuggestions",
    ]:
        if token in source:
            raise SystemExit(f"legacy browser suggestion authority remains: {rel}: {token}")
