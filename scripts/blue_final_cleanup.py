#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def replace(rel: str, old: str, new: str) -> None:
    target = ROOT / rel
    text = target.read_text(encoding="utf-8")
    if old in text:
        target.write_text(text.replace(old, new), encoding="utf-8")


def regex(rel: str, pattern: str, replacement: str) -> None:
    target = ROOT / rel
    text = target.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, text, flags=re.S)
    if count != 1:
        raise SystemExit(f"expected one replacement in {rel}, got {count}: {pattern[:80]}")
    target.write_text(updated, encoding="utf-8")


native = "app/src/main/assets/ui/core/native-api.js"

# Demo represents physical evidence only. It must not recreate retired browser
# calibration/advisor/predictor semantics.
regex(
    native,
    r"  function demoLearning\(\) \{.*?\n  \}\n\n  function demoObdMaps",
    r'''  function demoLearning() {
    const cells = [];
    for (let row = 0; row < 12; row += 1) {
      for (let column = 0; column < 12; column += 1) {
        if ((row + column) % 3 !== 0) continue;
        cells.push({
          row, column, key: `${row}:${column}`,
          samples: 12 + ((row * 7 + column * 5) % 55),
          visits: 2 + ((row + column) % 4),
          confidence: 0.55 + (((row + column) % 5) * 0.09),
          stage: 'ACCEPTED',
        });
      }
    }
    const petrol = cells.map(item => ({ ...item, fuel: 'PETROL', petrolMs: 3.8 + item.row * 0.17 }));
    const cng = cells.map(item => ({ ...item, fuel: 'CNG', petrolMs: 3.84 + item.row * 0.17 }));
    const comparisons = cells.map((item, index) => ({
      ...item,
      errorPercent: ((index % 9) - 4) * 0.35,
      quality: item.confidence,
    }));
    return {
      ok: true,
      demo: true,
      decisionAuthority: 'BLUE_CAUSAL_ENGINE',
      uiPipeline: 'PHYSICAL_EVIDENCE_ONLY',
      grid: { rows: 12, columns: 12, petrolBins: PETROL_BINS, rpmBins: RPM_BINS },
      cells,
      petrol,
      cng,
      comparisons,
      current: { fuel: 'GNV', rpm: 2100, petrolMs: 4.2, mapBar: 0.56, cell: { row: 4, column: 3 } },
    };
  }

  function demoObdMaps''',
)

replace(
    native,
    "    sessionStatus() { return this.demo ? { recording: false, events: 0, megabytes: 0, settings: { autoStartOnUsb: true, telemetryEveryMs: 500, captureRawUsb: false, maxSessionMb: 64, keepSessions: 10 } } : invoke(this.native, 'getSessionRecorderStatus', [], {}); }",
    "    sessionStatus() { return this.demo ? { recording: false, events: 0, megabytes: 0, settings: { autoStartOnUsb: true, telemetryEveryMs: 500, captureRawUsb: false, maxSessionMb: 64, keepSessions: 30 } } : invoke(this.native, 'getSessionRecorderStatus', [], {}); }",
)
regex(
    native,
    r"    setSessionSettings\(settings\) \{\n      const s = settings \|\| \{\};\n      if \(this\.demo\) return \{ ok: true, settings: s, demo: true \};\n      return invoke\(this\.native, 'setSessionRecorderSettings', \[Number\(s\.telemetryEveryMs\) \|\| 500, Number\(s\.maxSessionMb\) \|\| 64, Number\(s\.keepSessions\) \|\| 10, s\.autoStartOnUsb !== false, s\.captureRawUsb === true\], \{ ok: false \}\);\n    \}",
    r'''    setSessionSettings(settings) {
      const s = settings || {};
      const normalized = {
        telemetryEveryMs: Number(s.telemetryEveryMs) || 500,
        maxSessionMb: Number(s.maxSessionMb) || 64,
        keepSessions: Math.max(20, Math.min(100, Number(s.keepSessions) || 30)),
        autoStartOnUsb: s.autoStartOnUsb !== false,
        captureRawUsb: s.captureRawUsb === true,
      };
      if (this.demo) return { ok: true, settings: normalized, demo: true };
      return invoke(this.native, 'setSessionRecorderSettings', [normalized.telemetryEveryMs, normalized.maxSessionMb, normalized.keepSessions, normalized.autoStartOnUsb, normalized.captureRawUsb], { ok: false });
    }''',
)

source = (ROOT / native).read_text(encoding="utf-8")
for token in ["assistedCalibration", "kFactorSuggestions"]:
    if token in source:
        raise SystemExit(f"retired native-api browser authority remains: {token}")
