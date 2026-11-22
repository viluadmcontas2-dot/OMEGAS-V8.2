"""OMEGAS lab — evidence-gated investigator, without ECU writes.

No vehicle access, no model API, no background execution. Only locally verified
capture reports may close descriptive questions; an LLM can propose read-only steps.
"""
from __future__ import annotations
from dataclasses import dataclass
from enum import Enum
from hashlib import sha256
from pathlib import Path
from typing import Any
import json
import re
import zipfile

SCHEMA = "omegas.investigation.v1"
ALLOWED_STEPS = frozenset({
    "analyze_saved_capture", "compare_independent_sources",
    "read_protocol_evidence", "replay_decoder", "compare_historical_sessions",
    "request_passive_measurement", "validate_sensor_configuration",
})
FORBIDDEN_TEXT = re.compile(
    r"\b(write|flash|reset|resetar|erase|program|set\s*k|apply\s*k|clear\s*dtc|"
    r"gravar|escrever|apagar|reprogramar|calibrar|desativar|reiniciar\s+ecu)\b",
    re.IGNORECASE,
)


class Resolution(str, Enum):
    MEASURED = "OBSERVACAO_CONFIRMADA"
    REJECTED = "REFUTADA_PELA_CAPTURA"
    OPEN = "LACUNA_ABERTA"
    UNTESTABLE = "AGUARDANDO_DADO_EXTERNO"


@dataclass(frozen=True)
class Record:
    evidence_id: str
    claim_id: str
    verdict: str
    origin: str
    class_of_evidence: str
    artifact_sha256: str | None
    explanation: str
    verified: bool

    def __post_init__(self):
        if self.verdict not in {"supports", "contradicts", "context"}:
            raise ValueError("invalid verdict")
        if self.class_of_evidence not in {"measurement", "source_code", "document", "physical_test"}:
            raise ValueError("invalid evidence class")
        if not self.evidence_id or not self.claim_id or not self.origin or not self.explanation:
            raise ValueError("incomplete evidence")
        if self.artifact_sha256 and not re.fullmatch(r"[0-9a-f]{64}", self.artifact_sha256):
            raise ValueError("invalid artifact hash")


@dataclass(frozen=True)
class Question:
    claim_id: str
    title: str
    criterion: str
    severity: int
    next_read_only_step: str
    missing_proof: str

    def __post_init__(self):
        if self.next_read_only_step not in ALLOWED_STEPS:
            raise ValueError("unsafe next step")
        if self.severity not in range(1, 6):
            raise ValueError("severity must be 1..5")
        if self.criterion not in {"descriptive_capture", "physical_identity", "causation", "calibration"}:
            raise ValueError("invalid criterion")


def verify_archive(path: str | Path, expected_sha256: str | None = None) -> dict[str, Any]:
    """Verify whole ZIP before accepting data. Does not modify it."""
    path = Path(path)
    h = sha256()
    with path.open("rb") as f:
        while block := f.read(1024 * 1024):
            h.update(block)
    digest = h.hexdigest()
    if expected_sha256 is not None and digest != expected_sha256:
        raise ValueError("capture integrity failure: SHA-256 mismatch")
    with zipfile.ZipFile(path) as z:
        if z.testzip() is not None:
            raise ValueError("capture integrity failure: bad ZIP entry")
        if z.namelist() != ["dyno-vault.json"]:
            raise ValueError("unexpected ZIP entries")
        if z.getinfo("dyno-vault.json").file_size > 100_000_000:
            raise ValueError("capture too large")
        data = json.loads(z.read("dyno-vault.json"))
    if data.get("schemaVersion") != 1 or not isinstance(data.get("observations"), list):
        raise ValueError("unsupported capture schema")
    for i, obs in enumerate(data["observations"]):
        if not isinstance(obs, dict) or obs.get("source") not in {"obd", "mp48"}:
            raise ValueError(f"bad source in observation {i}")
        if not isinstance(obs.get("mono_ns"), int):
            raise ValueError(f"missing monotonic timestamp in observation {i}")
    return {
        "sha256": digest, "schemaVersion": data["schemaVersion"],
        "observation_count": len(data["observations"]),
        "source_counts": {source: sum(o["source"] == source for o in data["observations"])
                          for source in ("obd", "mp48")},
        "data": data,
    }


def status(question: Question, records: list[Record]) -> tuple[Resolution, str]:
    """Only evidence gates (not language-model assertions) assign state."""
    direct = [r for r in records if r.claim_id == question.claim_id and r.verified
              and r.class_of_evidence in {"measurement", "physical_test"}]
    supportive = [r for r in direct if r.verdict == "supports"]
    opposed = [r for r in direct if r.verdict == "contradicts"]
    independent = {r.origin for r in supportive}
    if question.criterion == "descriptive_capture":
        if supportive and opposed:
            return Resolution.OPEN, "Evidências diretas contraditórias exigem revisão"
        if opposed:
            return Resolution.REJECTED, "Observação direta contraria a afirmação descritiva"
        if supportive:
            return Resolution.MEASURED, "Observação direta verificada; válido apenas para esta captura"
    # Physical identity and cause require independent tests and original reference.
    if question.criterion in {"physical_identity", "causation", "calibration"}:
        external = [r for r in supportive if r.class_of_evidence == "physical_test" and r.artifact_sha256]
        external_contradiction = [r for r in opposed if r.class_of_evidence == "physical_test" and r.artifact_sha256]
        if external and external_contradiction:
            return Resolution.OPEN, "Testes físicos contraditórios"
        if external_contradiction and not external:
            return Resolution.REJECTED, "Teste físico independente refutou a afirmação"
        supported_docs = [r for r in records if r.claim_id == question.claim_id and r.verified
                          and r.verdict == "supports" and r.class_of_evidence in {"source_code", "document"}
                          and r.artifact_sha256]
        if (len({r.origin for r in external}) >= 2 and len(independent) >= 2
                and supported_docs and not opposed):
            return Resolution.MEASURED, "Dois testes físicos independentes + fonte original; válido somente no escopo verificado"
        return Resolution.UNTESTABLE, "Falta validação física independente, não autorizar conclusão"
    return Resolution.OPEN, "Falta observação direta verificada"


def evaluate(questions: list[Question], records: list[Record]) -> dict[str, Any]:
    known_ids = {q.claim_id for q in questions}
    if len(known_ids) != len(questions):
        raise ValueError("duplicated claim id")
    evid_ids = set()
    for r in records:
        if r.claim_id not in known_ids:
            raise ValueError("unknown claim referenced by evidence")
        if r.evidence_id in evid_ids:
            raise ValueError("duplicate evidence ID")
        evid_ids.add(r.evidence_id)
    results = []
    for q in questions:
        resolution, reason = status(q, records)
        results.append({
            "claim_id": q.claim_id, "question": q.title,
            "state": resolution.value, "why": reason, "severity": q.severity,
            "missing_proof": None if resolution is Resolution.MEASURED else q.missing_proof,
            "next_step": None if resolution is Resolution.MEASURED else q.next_read_only_step,
            "evidence_ids": [r.evidence_id for r in records if r.claim_id == q.claim_id and r.verified],
        })
    results.sort(key=lambda r: (-r["severity"], r["claim_id"]))
    return {
        "schema": SCHEMA, "questions": results,
        "closed_this_capture": sum(x["state"] == Resolution.MEASURED.value for x in results),
        "open_or_blocked": sum(x["state"] != Resolution.MEASURED.value for x in results),
        "all_steps_read_only": True,
    }


def validate_llm_proposal(proposal: dict[str, Any], results: dict[str, Any]) -> dict[str, Any]:
    """LLM suggests read-only plan. Validation never executes or closes a claim."""
    if set(proposal) != {"claim_id", "step", "reason", "evidence_ids"}:
        raise ValueError("proposal has missing or unexpected keys")
    claim = next((q for q in results["questions"] if q["claim_id"] == proposal["claim_id"]), None)
    if claim is None or claim["state"] == Resolution.MEASURED.value:
        raise ValueError("no open investigation with this identifier")
    if proposal["step"] not in ALLOWED_STEPS or proposal["step"] != claim["next_step"]:
        raise ValueError("unauthorized/irrelevant action")
    if not isinstance(proposal["reason"], str) or FORBIDDEN_TEXT.search(proposal["reason"]):
        raise ValueError("write-like instruction in proposal")
    evidence_ids = proposal["evidence_ids"]
    if (not isinstance(evidence_ids, list) or
        set(evidence_ids) - set(claim["evidence_ids"])):
        raise ValueError("unverifiable evidence references")
    return {
        "claim_id": claim["claim_id"], "proposed_read_only_step": proposal["step"],
        "reason": proposal["reason"], "state": "PLANEJAMENTO_NAO_EXECUTADO",
        "allowed_to_write_to_vehicle": False,
    }


def first_s20_case() -> tuple[list[Question], list[Record]]:
    """Historical ledger seeded from the separately verified first S20 export.

    A GitHub runner cannot re-verify private S20 bytes; analyze_verified_capture
    does verify them locally when the original ZIP is available.
    """
    digest = "0c926a353cdacd5e5686cca5ab364aa927306ceab8d03b9b066e4ea37efda0b2"
    questions = [
        Question("q01-rpm-agreement", "OBD e MP48 exibiram RPM coerentes nesta sessão?",
                 "descriptive_capture", 4, "compare_historical_sessions",
                 "Nenhuma comparação verificável desta sessão"),
        Question("q02-map-agreement", "OBD e MP48 exibiram MAP coerente nesta sessão?",
                 "descriptive_capture", 4, "compare_historical_sessions",
                 "Nenhuma comparação verificável desta sessão"),
        Question("q03-water-sensor", "O byte 12 da MP48 mede a mesma temperatura física do ECT OBD?",
                 "physical_identity", 5, "validate_sensor_configuration",
                 "Configuração e localização dos dois sensores, mais testes físicos independentes"),
        Question("q04-gnv-gap-event", "Ocorreu uma janela com ambos os comandos GNV zerados perto da marcha lenta?",
                 "descriptive_capture", 5, "compare_historical_sessions",
                 "Captura bruta e detector com continuidade do relógio"),
        Question("q05-gnv-gap-cause", "Qual mecanismo causou a ausência de pulsos GNV na marcha lenta?",
                 "causation", 5, "compare_historical_sessions",
                 "Repetição, estados/erros da central e medição independente dos pulsos"),
        Question("q06-voltage-byte19", "Byte 19 da MP48 é a tensão física dos injetores GNV em V=raw/16?",
                 "physical_identity", 3, "validate_sensor_configuration",
                 "Leitura física independente de tensão e semântica no protocolo original"),
        Question("q07-fuel-wait", "O estado 0x88 ocorreu sem pulso GNV antes do estado 0x90 nesta sessão?",
                 "descriptive_capture", 4, "compare_historical_sessions",
                 "Quadros de ambos os estados com contagem de pulsos"),
        Question("q08-fuel-wait-meaning", "0x88 significa logicamente aguardando entrada de GNV em qualquer ECU?",
                 "physical_identity", 3, "read_protocol_evidence",
                 "Comprovação semântica em ProgBase/protocolo original e outra ECU"),
    ]
    records = [
        Record("S20-20261121-RPM", "q01-rpm-agreement", "supports", "S20-OBD-MP48", "measurement", digest,
               "145 pares até 200ms, erro absoluto mediano 6 RPM", True),
        Record("S20-20261121-MAP", "q02-map-agreement", "supports", "S20-OBD-MP48", "measurement", digest,
               "146 pares até 200ms, erro absoluto mediano 1,4kPa", True),
        Record("S20-20261121-TEMP", "q03-water-sensor", "context", "S20-OBD-MP48", "measurement", digest,
               "147 pares, curva correlacionada r=0,992 mas diferença mediana -48C; identidade não comprovada", True),
        Record("S20-20261121-GAP", "q04-gnv-gap-event", "supports", "S20-MP48", "measurement", digest,
               "12 quadros, 0,495s, ambos os bancos GNV sem pulso em estado 0x90 e RPM 758..925", True),
        Record("S20-20261121-GAP-CAUSE", "q05-gnv-gap-cause", "context", "S20-MP48", "measurement", digest,
               "Ausência observada, mas causa física não medida; motor não chegou a 0 RPM", True),
        Record("S20-20261121-B19", "q06-voltage-byte19", "context", "S20-MP48", "measurement", digest,
               "202..225 em estado 0x90; escalado por 16 seria 12,63..14,06V, não confirmado", True),
        Record("S20-20261121-FUEL", "q07-fuel-wait", "supports", "S20-MP48", "measurement", digest,
               "2113 quadros 0x88 sem pulso, depois 661 quadros 0x90, 636 com pulso", True),
        Record("S20-20261121-FUEL-SEM", "q08-fuel-wait-meaning", "context", "S20-MP48", "measurement", digest,
               "Associação observada, significado físico/protocolo permanece inferido", True),
    ]
    return questions, records


def analyze_verified_capture(path: str | Path, expected_sha256: str) -> dict[str, Any]:
    """Recompute evidence only from a locally verified archive, not LLM text."""
    import importlib.util
    base = Path(__file__).resolve().parent.parent / "tools"

    def load(module_name: str):
        source = base / (module_name + ".py")
        if not source.exists():
            raise FileNotFoundError(f"Required checked-in analyzer absent: {source}")
        spec = importlib.util.spec_from_file_location(module_name, source)
        if spec is None or spec.loader is None:
            raise RuntimeError("Cannot load the offline analyzer")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    archive = verify_archive(path, expected_sha256)
    summary = load("analyze_dual_capture").analyze(path)
    windows = load("detect_gnv_pulse_gaps").detect(archive["data"]["observations"])
    questions, _ = first_s20_case()
    records: list[Record] = []

    def add(claim: str, verdict: str, observation: str):
        records.append(Record(
            evidence_id=f"CAPTURE-{archive['sha256'][:12]}-{claim}",
            claim_id=claim, verdict=verdict, origin="S20-OBD-MP48",
            class_of_evidence="measurement", artifact_sha256=archive["sha256"],
            explanation=observation, verified=True,
        ))

    comparisons = summary.get("cross_source_comparisons", {})
    for field, claim_id, maximum_difference in [
        ("rpm", "q01-rpm-agreement", 10.0),
        ("mapBar", "q02-map-agreement", 5.0),
    ]:
        d = comparisons.get(field, {})
        count = d.get("pairs", 0)
        diff = d.get("absolute_difference_median")
        if count >= 20 and isinstance(diff, (float, int)):
            add(claim_id, "supports" if diff <= maximum_difference else "contradicts",
                f"{count} pairs within ±200ms, absolute median difference {diff} {d.get('units')}")
    if comparisons.get("waterCandidate", {}).get("pairs", 0) >= 20:
        add("q03-water-sensor", "context",
            "Temperatures co-varied or diverged; neither alone validates identical sensor location/physical scale")

    candidate = [w for w in windows.get("windows", [])
                 if w.get("interpretation") == "REVISAR_INTERRUPCAO_GNV_PROXIMA_DA_MARCHA_LENTA"
                 and w.get("frames_both_gnv_banks_zero", 0) >= 5]
    if candidate:
        add("q04-gnv-gap-event", "supports",
            f"{len(candidate)} window(s) with consecutive GNV 1+2 zero pulses near idle")
        add("q05-gnv-gap-cause", "context", "Pulse loss observed, cause not measured")

    flags = summary.get("fuel_states", {})
    changes = summary.get("fuel_state_changes", [])
    if (flags.get("0x88", {}).get("frames", 0) > 0
            and flags.get("0x88", {}).get("gas_pulse_frames") == 0
            and flags.get("0x90", {}).get("gas_pulse_frames", 0) > 0
            and any(x.get("from") == "0x88" and x.get("to") == "0x90" for x in changes)):
        add("q07-fuel-wait", "supports", "0x88 without pulses, followed by 0x90 with pulses")
        add("q08-fuel-wait-meaning", "context", "Association cannot establish universal protocol semantics")

    if summary.get("byte19_by_fuel_state"):
        add("q06-voltage-byte19", "context",
            "Byte 19 changes by fuel state, but no independent voltmeter/OBD voltage reference")
    report = evaluate(questions, records)
    report["verified_artifact_sha256"] = archive["sha256"]
    report["observations"] = archive["observation_count"]
    report["source_counts"] = archive["source_counts"]
    report["inspection"] = {"rpm": comparisons.get("rpm"), "map": comparisons.get("mapBar"),
                            "water": comparisons.get("waterCandidate"),
                            "fuel_states": flags, "gnv_pulse_windows": windows.get("windows", [])}
    return report


def main() -> None:
    questions, records = first_s20_case()
    print(json.dumps(evaluate(questions, records), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
