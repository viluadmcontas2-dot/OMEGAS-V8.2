#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").read_text(encoding="utf-8")

match = re.search(
    r"fun importSnapshotIntoLearning\(snapshotJson: String\): String = try \{(?P<body>[\s\S]*?)\n    \} catch",
    SOURCE,
)
assert match, "importSnapshotIntoLearning body not found"
body = match.group("body")
count = body.count("blueIngestLearningSnapshot(snapshotJson)")
assert count == 1, (
    "one imported native snapshot must enter the Blue evidence boundary exactly once; "
    f"found {count} ingests"
)
assert SOURCE.count("import com.omegas.prohub.service.blueProposalJson") == 1

print("BLUE_AUTOCAL_SINGLE_INGEST_CONTRACT=PASS")
