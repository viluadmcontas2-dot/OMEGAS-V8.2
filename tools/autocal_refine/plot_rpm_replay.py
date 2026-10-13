#!/usr/bin/env python3
"""Create reproducible plots from native and residual replay CSV receipts."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib.colors import Normalize


def read_csv(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def native_overview(rows: list[dict], output: Path) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(13, 5), constrained_layout=True)
    rpm_values = [float(row["rpm"]) for row in rows]
    rpm_norm = Normalize(vmin=min(rpm_values), vmax=max(rpm_values))
    plotted = None
    for axis, fuel in zip(axes, ("GASOLINA", "GNV")):
        selected = [row for row in rows if row["fuel"] == fuel]
        for source, marker, label in (
            ("INFERRED_INTERVAL_MATCH", "x", "intervalo causal"),
            ("NATIVE_CORRELATION", "o", "correlação nativa"),
        ):
            part = [row for row in selected if row["source"] == source]
            if not part:
                continue
            plotted = axis.scatter(
                [float(row["map_bar"]) for row in part],
                [float(row["petrol_ms"]) for row in part],
                c=[float(row["rpm"]) for row in part], cmap="turbo", norm=rpm_norm,
                s=30 if marker == "o" else 24, marker=marker,
                alpha=.78, label=label,
            )
        axis.set(title=f"AutoCal nativo — {fuel}", xlabel="MAP (bar)", ylabel="Tpet observado (ms)")
        axis.grid(alpha=.2)
        axis.legend(fontsize=8)
    if plotted is not None:
        fig.colorbar(plotted, ax=axes, label="RPM")
    fig.suptitle("Sessaoutil: a curva MAP × ms contém aquisições de regimes RPM distintos")
    fig.savefig(output, dpi=180)
    plt.close(fig)


def model_comparison(payload: dict, output: Path) -> None:
    names = ["INCUMBENT", "RPM_BIN_MEDIAN", "CONTINUOUS_RPM", "RIVER_ARF_SHADOW"]
    summary = payload["summary"]
    metrics = ["median_abs_log_percent", "mean_abs_log_percent", "p90_abs_log_percent"]
    labels = ["mediana", "média", "p90"]
    x = range(len(names))
    fig, axis = plt.subplots(figsize=(11, 5), constrained_layout=True)
    width = .24
    for index, (metric, label) in enumerate(zip(metrics, labels)):
        offset = (index - 1) * width
        axis.bar([value + offset for value in x], [summary[name][metric] for name in names], width, label=label)
    axis.set_xticks(list(x), [name.replace("_", "\n") for name in names])
    axis.set_ylabel("erro absoluto em log (%) — menor é melhor")
    axis.set_title("Validação por sessão física: River reduz a cauda, mas não vence de forma uniforme")
    axis.grid(axis="y", alpha=.2)
    axis.legend()
    fig.savefig(output, dpi=180)
    plt.close(fig)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--native-csv", type=Path, required=True)
    parser.add_argument("--models-json", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    native_overview(read_csv(args.native_csv), args.output / "native-curves-by-rpm.png")
    model_comparison(json.loads(args.models_json.read_text(encoding="utf-8")), args.output / "model-comparison.png")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
