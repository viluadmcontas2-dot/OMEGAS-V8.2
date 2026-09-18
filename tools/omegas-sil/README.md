# OMEGAS SIL

Headless Software-in-the-Loop laboratory for OMEGAS Verde.

## Authority

- Production science: Kotlin classes from the active Verde source tree.
- Corpus reconstruction/orchestration: Python under this directory.
- Source logs: read-only `G:\Meu Drive\OMEGAS` on AgentRed.
- Android Emulator, APK, UI and ADB are intentionally outside this workflow.

## Practical success criterion

Reference error is operationally acceptable when:

```
abs(predicted_ms - observed_ms) / observed_ms <= 0.05
```

The lab still reports MAE/P90/P99, but generalization, coverage and resistance
to stale/frozen/transient evidence outrank optimizing errors already within 5%.

## Corpus layer

`corpus.py` reconstructs MP48 transport evidence.

For native OMEGAS session ZIPs it consumes only `usb_raw` events and rebuilds
TX/RX transactions. For Portmon archives it reuses the repository's canonical
`scripts/omegas/portmon_parser.py`.

The accepted response contract is the physical MP48 envelope already used by
`UsbSerialManager`:

```
request echo | status | length | payload | checksum
```

Telemetry is request `48 01 49`, ACK `53`, length `22` hex, payload 34
bytes. Checksum is the low byte of the sum from status through payload.

Exact duplicate sessions are collapsed by SHA-256 over the ordered telemetry
payload stream while every filename remains attached as provenance/alias.

## Tests

```powershell
python tools\omegas-sil\test_corpus.py
```

The later replay runner will feed reconstructed transactions into the real
`ResponseDrivenEcuEngine`, real decoder/analyzer and real learning memory.
Python does not reproduce or replace the scientific algorithm.
