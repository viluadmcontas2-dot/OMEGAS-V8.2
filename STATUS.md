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
- #81 epic e #84 global real-log E2E permanecem **CLOSED / COMPLETED**; o refinamento posterior não reabre esses resultados.
- #98 P0 de reset permanece aberto somente até a revalidação final same-SHA desta correção de segurança/UX.
- Fixture real MP48: `tests/fixtures/portmon-autocal-cycle-v1.json`, origem #68 / Portmon real.
- ProgBase original: `G:\Meu Drive\OMEGAS\Copy of ProgBase (3).exe`.
- ProgBase SHA-256: `8A2D297C8C21FF3B4F7A47F7FE64593B0FEC9014DD938BD91022DC0C68AC36F4`.
- Forensic fan-out #13 (`35646095776`) no SHA `873550b1b13e594c9ffaba7229688f243de3ecaf`: **256 PASS / 0 RED / 0 BROKEN**.
- Canonical CI #202 (`35647103096`) no SHA `28c119fbe34eec8ef6a2695e172cd4c9583b91a7`: **PASS**.
- Fast contracts #133 (`35647103061`) no mesmo SHA: **PASS**.

### Evidência recente inspecionada

- Provenance audit: `docs/evidence/2026-09-22-autocal-fixture-provenance.md`.
- Android render #61 (`35809110846`) no SHA `aee50900b907c44de2cb8c562ed998b8a5d39314`: **13/13 cenários PASS**, incluindo o novo `autocal-sparse-zone-map`; receipts e screenshot 1280×720 inspecionados.
- O cenário de zonas preserva identidade espacial: gasolina `Z1/Z2 OK, Z3/Z4 FALTA`; GNV `Z1/Z3 OK, Z2/Z4 FALTA`; `AGORA` usa CurrentBand e o fixture é explicitamente `SYNTHETIC_NON_SCIENTIFIC / VISUAL_ONLY_NON_SCIENTIFIC` sobre geometria ORIGINAL_DERIVED.
- Baseline imediatamente anterior ao gate final: Fast #197 (`35808579759`), CI #308 (`35808579735`), Forensic #50 (`35808579730`) e Global #72 (`35808579729`) **PASS** no SHA `b083153a59c478c2fae5d13de5592eee69746bd1`.
- CI #309 (`35809110817`) também **PASS** no SHA do render #61.
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

Ações AutoCal recuperadas do ProgBase 4.2.0.6 original:
- `ActionAutoCalRifExecute` = Modify map refs, modo `0x08`, frame `02 24 04 08 32`;
- `ActionAutoMatchExecute` = Manual AutoMatch, modo `0x01`, frame `02 24 04 01 2B`;
- `ActionResetPetrolExecute` = Reset petrol point, modo `0x02`, frame `02 24 04 02 2C`;
- `ActionResetGasExecute` = Reset gas point, modo `0x04`, frame `02 24 04 04 2E`;
- `ActionResetAllExecute` usa rota separada; sequência wire exata permanece não resolvida.
- No Lognovo original, `Reset gas point` teve efeito amplo: zerou estado de aquisição gasolina/GNV, curvas de referência e `MUL_ACT`. OMEGAS não promete seletividade e mantém os resets destrutivos intertravados.

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
- Reset gasolina/GNV continua bloqueado na HMI operacional; nenhuma ação destrutiva pode sair antes de backup pré-mutação completo, persistido e revalidado.
- Ferramentas expõe exportação de backup completo; isso é proteção operacional, não autorização para escrever dados de volta sem protocolo original comprovado.
- Zonas `0x016F/0x0170` preservam identidade Z1..Z4 na tela: `OK`, `FALTA` e marcador `AGORA`; não são mais reduzidas apenas a N/4.

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
- Android render com os 13 cenários verdes em 1280×720, incluindo o mapa esparso de zonas;
- receipts e screenshots inspecionados, não apenas badge;
- provenance ORIGINAL_DERIVED preservada para AutoCal/Curve K e `SYNTHETIC_NON_SCIENTIFIC` explícita no cenário visual shifted.

Os run IDs do SHA final são registrados nos issues #84/#81 depois que Actions termina; não se cria outro commit apenas para registrar o próprio run ID.

## NON-GOAL

Nenhum port/cherry-pick/cópia SIL/CIU nesta WorkUnit.
