# OMEGAS Verde — Status

## Active control surface

- Spec Kit: `OMEGAS-SK-001`
- WorkUnit: `OMEGAS-WU-006`
- Epic: #81
- Branch: `OmegasVerde`
- Estado: `FINAL GATE — SAME-SHA REVALIDATION`

Sempre resolver o HEAD remoto antes de agir. GitHub remoto é autoridade; este arquivo registra evidência e direção, não fixa o HEAD.

## Evidência já fechada

- #82 ProgBase byte/consumer map: **CLOSED / COMPLETED**.
- #83 OMEGAS parity matrix: **CLOSED / COMPLETED**.
- #86 sessão canônica: **CLOSED / MATCH** — AutoCal usa o mesmo `SessionRecorder`, o mesmo diretório de sessão e o mesmo ZIP canônico.
- #85 Dashboard LEVELS RAW: **CLOSED / GREEN** — fresh `177`, invalid/stale `—`, sem conversão física inventada.
- #95 AutoCal parity/repair/render: **CLOSED / GREEN** no escopo determinístico; validação física em veículo continua separada.
- Fixture real MP48: `tests/fixtures/portmon-autocal-cycle-v1.json`, origem #68 / Portmon real.
- ProgBase original: `G:\Meu Drive\OMEGAS\Copy of ProgBase (3).exe`.
- ProgBase SHA-256: `8A2D297C8C21FF3B4F7A47F7FE64593B0FEC9014DD938BD91022DC0C68AC36F4`.
- Forensic fan-out #13 (`35646095776`) no SHA `873550b1b13e594c9ffaba7229688f243de3ecaf`: **256 PASS / 0 RED / 0 BROKEN**.
- Canonical CI #202 (`35647103096`) no SHA `28c119fbe34eec8ef6a2695e172cd4c9583b91a7`: **PASS**.
- Fast contracts #133 (`35647103061`) no mesmo SHA: **PASS**.

### Evidência recente inspecionada

- Provenance audit: `docs/evidence/2026-09-22-autocal-fixture-provenance.md`.
- Android render #45 (`35755193960`) no SHA de produto `87a4ffbd91da1c01c2452b98dc83077eb83b093c`: **12/12 cenários PASS**, receipts e screenshots inspecionados em 1280×720.
- Global reality fan-out #36 (`35755194066`) no mesmo SHA: **159 lanes PASS / 0 RED / 0 BROKEN**; plan + aggregate também verdes.
- Fast contracts #164 (`35755193972`) e CI canônica #263 (`35755194006`) no mesmo SHA: **PASS**.
- RED real preservado: render #44 (`35754584631`) provou que Curve K offline ficava presa em “Lendo 30 pontos diretamente da ECU”; `87a4ffbd...` corrigiu o settle para “Curva não confirmada”.
- Os commits posteriores ao SHA de produto alteraram apenas o workflow autorizado de APK; por isso o fechamento exige uma nova rodada CI + render + fan-out no HEAD atual.

## ProgBase/original confirmado

Estruturas VCL observadas:
- `TAutoCalUI`, `TAutoCalDM`, `TFormRifAutocal`;
- `ChartData`, `PetrolCurve`, `GasCurve`;
- `RunPoint`, `CurrentBand`, `PollingPetrol`, `PollingGas`.

Portmon:
- `48 01 49`: mediana ~46,57 ms;
- família `0x015B..0x0163`: ~2,01 s;
- família `0x018D/0x018E`: ~4,05 s;
- leituras secundárias intercaladas com telemetria viva.

Semântica live recuperada:
- Petrol Injection raw: payload offset 8;
- LEVELS RAW: payload offset 13;
- MAP raw: payload offset 17, **S16LE**;
- nenhuma conversão física LEVELS→%/litros/m³ está autorizada.

## OMEGAS atual — correções provadas

- LEVELS do AutoCal usa telemetria live fresca.
- Dashboard/Agora contém `LEVELS RAW`; emissão de `level_percentage` não calibrado foi removida.
- `CurrentBand` reproduz o helper original: threshold[i] < MAP <= threshold[i+1], fora do domínio não seleciona faixa.
- Refresh operacional ~2 s usa a autoridade serial existente e renova a unidade consumida por `AutoCalAcquisition`:
  - gasolina: tempo + MAP + contador;
  - GNV atual: tempo + MAP + contador;
  - GNV anterior: tempo + MAP;
  - zonas gasolina/GNV.
- Refresh de referência ~4 s renova atomicamente `PETR_INJ_TBP`, `MNFLD_PRESS_THD`, `MUL_ACT`, petrol RV e gas RV.
- `MUL_ACT` permanece no grupo coerente de referência, não no grupo operacional.
- MAP live foi alinhado ao S16LE do oracle; fronteira `0xFFFF -> -1` fica implausível/fail-closed.
- Nenhum segundo serial owner/thread foi criado; `telemetryAfter` permanece preservado.
- Escrita automática na ECU continua proibida.

## Sessão

AutoCal não possui um segundo sistema de sessão:
- snapshots nativos, snapshots manuais, epochs e ações confirmadas entram no `SessionRecorder` canônico;
- aliases AutoCal de listar/exportar delegam para a sessão/ZIP canônicos;
- `autocal_native_receipts.json` permanece apenas como compatibilidade/readback e não deve ser apagado sem provar readers/writers.

## Execução paralela

### Fan-out forense
- workflow: `.github/workflows/verde-forensic-fanout.yml`;
- matriz máxima: **256 lanes**;
- 243 lanes = uma por transação Portmon;
- 13 lanes meta = oracle, same-ECU JVM, UI, arquitetura, segurança, mutações, MAP, LEVELS, aquisição e revisão.

### Fan-out global
- workflow: `.github/workflows/verde-global-reality-fanout.yml`;
- descobre e isola contratos de Dashboard, Learning, Map, Curve, OBD, sessão, reconnect, telemetry/runtime/bridges;
- uma dependência JVM→Node do Learning foi tornada explícita; não era RED de produto.

### Render Android real
Pipeline vinculante:
`fixture real -> decoder/runtime/bridge reais -> MainActivity/WebView real -> 1280x720 -> DOM/assertions -> screenshot + receipt`.

O emulator, build e instalação dos APKs já foram provados no run #9; a falha desse run foi apenas sintaxe do wrapper shell. O run seguinte usa `tools/ci/run_android_render_evidence.sh` para executar os cenários dentro de Bash real.

## Gate de fechamento

Para fechar #84 e reconciliar #81, o HEAD corrente deve produzir, no mesmo SHA:

- CI canônica verde;
- fast contracts verdes;
- global reality fan-out sem RED/BROKEN;
- Android render com os 12 cenários verdes em 1280×720;
- receipts e screenshots inspecionados, não apenas badge;
- provenance ORIGINAL_DERIVED preservada para AutoCal/Curve K e `SYNTHETIC_NON_SCIENTIFIC` explícita no cenário visual shifted.

Os run IDs do SHA final são registrados nos issues #84/#81 depois que Actions termina; não se cria outro commit apenas para registrar o próprio run ID.

## NON-GOAL

Nenhum port/cherry-pick/cópia SIL/CIU nesta WorkUnit.
