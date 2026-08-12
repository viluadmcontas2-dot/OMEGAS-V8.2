from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REQUIRED = [
    "AGENTS.md",
    "START_HERE.md",
    "SKILLS.md",
    "TESTING_RULES.md",
    "LEARNING_RULES.md",
    "PROJECT_STATE.md",
    "DECISIONS.md",
    "CAPABILITY_MATRIX.md",
]


def main() -> int:
    missing = [path for path in REQUIRED if not (ROOT / path).is_file()]
    agents = (ROOT / "AGENTS.md").read_text(encoding="utf-8") if (ROOT / "AGENTS.md").is_file() else ""
    required_phrases = [
        "nenhuma escrita automática na ECU",
        "checkpoint, ACK e readback",
        "multimídia/tablet 9\"",
        "toda branch futura",
    ]
    missing_phrases = [phrase for phrase in required_phrases if phrase not in agents]

    if missing or missing_phrases:
        if missing:
            print("Arquivos obrigatórios ausentes:", ", ".join(missing))
        if missing_phrases:
            print("Contratos globais ausentes no AGENTS.md:", ", ".join(missing_phrases))
        return 1

    print("Governança global presente e contratos essenciais preservados.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
