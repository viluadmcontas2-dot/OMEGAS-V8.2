"""Contrato do alinhamento com o ProgBase original (2026-10-07, autorizado pelo dono).

Prova de classe 1: texto-fonte. Não substitui o teste unitário Kotlin nem o carro (classe 5).
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KOT = ROOT / "app/src/main/java/com/omegas/prohub"
axes = (KOT / "calibration/KMapEcuAxes.kt").read_text(encoding="utf-8")
writer = (KOT / "calibration/KWriteManager.kt").read_text(encoding="utf-8")
protocol = (KOT / "ecu/AutoCalProtocol.kt").read_text(encoding="utf-8")
usb = (KOT / "usb/UsbSerialManager.kt").read_text(encoding="utf-8")
policy = (KOT / "usb/SerialPurgePolicy.kt").read_text(encoding="utf-8")

# (3) Eixos do Mapa K: somente leitura (29 37 00 / 29 3D 00), com volta ao eixo fixo.
assert "byteArrayOf(0x29, 0x37, 0x00)" in axes and "byteArrayOf(0x29, 0x3D, 0x00)" in axes
for write_opcode in ("0x14,", "0x35,", "0x37, 0x6", "0x12,", "0x13,"):
    assert write_opcode not in axes, f"KMapEcuAxes não pode conter escrita: {write_opcode}"
assert "KMapEcuAxes.READ_RPM" in writer and "KMapEcuAxes.READ_TIME" in writer
assert "readEcuAxes(expectedSessionId)" in writer
assert "KMapPhysicalAxes.rpmBins()" in axes  # fallback fixo preservado
assert "Mp48TelemetryScale.injectionMs" in axes  # escala 0,00256 ms/contagem do log

# (4) 29 64 01 fica fora da varredura completa; a leitura em si não mudou.
reads = protocol.split("val READ_ONLY_FIELDS: List<Field> = listOf(", 1)[1].split("\n    )", 1)[0]
assert "VECT_AUTOCAL_EE" not in reads
assert "OPTIONAL_FIELDS" in protocol
assert "VECT_AUTOCAL_U8_0," in reads and "MODULE_VERSION," in reads  # 09 65 01 / 09 73 01 mantidos

# (5) Purge antes de toda transação, sempre antes da escrita e com desligamento automático.
assert "else preTransactionPurge(current, reason)" in usb
assert usb.index("preTransactionPurge(current, reason)") < usb.index("current.write(request, timeoutMs)")
assert "purgePolicy.reset()" in usb
assert "slowStrikesToDisable" in policy and "hardwareEnabled" in policy
assert "purgeBefore = true" not in (KOT / "autocal/NativeAutoCalMonitor.kt").read_text(encoding="utf-8")

print("ALINHAR_ORIGINAL_CONTRACT=PASS")
