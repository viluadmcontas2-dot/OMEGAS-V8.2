#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--states", type=Path, required=True)
    ap.add_argument("--report", type=Path, required=True)
    ap.add_argument("--closure", type=Path, required=True)
    args = ap.parse_args()

    states = []
    for path in args.states.rglob("state.json"):
        try:
            states.append(json.loads(path.read_text(encoding="utf-8")))
        except Exception:
            pass
    if not states:
        raise SystemExit("no battle state artifacts")

    state = max(states, key=lambda x: int(x.get("wave", 0)))
    targets = list(state.get("targets", {}).values())
    counts = {}
    kinds = {}
    for target in targets:
        counts[target["status"]] = counts.get(target["status"], 0) + 1
        kinds[target["kind"]] = kinds.get(target["kind"], 0) + 1

    coverage = state.get("coverage", {})
    open_count = sum(1 for target in targets if target["status"] not in {"PROVEN", "REFUTED"})
    semantic_complete = bool(state.get("semantic_closure", False))
    no_collector_truncation = (
        not bool(coverage.get("function_cap_hit", False))
        and not bool(coverage.get("decompile_cap_hit", False))
    )
    has_next = bool(state.get("has_next", False))
    stalled = int(coverage.get("stalled_targets", 1))
    broken = int(coverage.get("broken", 1))
    unseen = int(coverage.get("graph_functions_unseen", 1))

    complete = (
        open_count == 0
        and unseen == 0
        and broken == 0
        and stalled == 0
        and semantic_complete
        and no_collector_truncation
        and not has_next
    )

    lines = [
        "# OMEGAS Atlas — Battle Royale status",
        "",
        f"Last reconciled wave: **{state.get('wave')}**",
        f"Targets tracked: **{len(targets)}**",
        f"By kind: **{json.dumps(kinds, sort_keys=True)}**",
        f"By state: **{json.dumps(counts, sort_keys=True)}**",
        f"Native graph functions: **{coverage.get('graph_functions_total', '?')}**",
        f"Native graph functions not yet admitted to battle: **{unseen}**",
        f"Stalled ESCALATE targets: **{stalled}**",
        f"BROKEN: **{broken}**",
        f"Semantic closure: **{semantic_complete}**",
        f"Function collector truncated: **{bool(coverage.get('function_cap_hit', False))}**",
        f"Decompiler corpus truncated: **{bool(coverage.get('decompile_cap_hit', False))}**",
        "",
        f"Closure gate: **{'PASS' if complete else 'OPEN'}**",
        "",
        "No UNKNOWN state is used. Any unresolved item remains ESCALATE and the Atlas must not be declared complete.",
    ]

    args.report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    args.closure.write_text(
        json.dumps(
            {
                "complete": complete,
                "has_next": has_next,
                "open_targets": open_count,
                "wave": state.get("wave"),
                "state_artifact": f"atlas-state-w{state.get('wave')}",
                "semantic_closure": semantic_complete,
                "collector_complete": no_collector_truncation,
                "coverage": coverage,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print("\n".join(lines))


if __name__ == "__main__":
    main()
