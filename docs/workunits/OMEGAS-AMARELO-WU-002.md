# OMEGAS-AMARELO-WU-002 — State machine e consumer graph do ProgBase AutoCAL

Issue: #74  
Estado: **CLOSED / PROVEN — consumer graph e lifecycle host-observável fechados**

## Resultado observável

Grafo auditável:

`intenção humana -> handler -> dispatcher/objeto nativo -> serial/ECU -> readback -> publisher tipado -> consumer/UI`

Cada afirmação é classificada como PROVEN / SUPPORTED / UNKNOWN.  
Machine-readable authority:

`docs/omegas-amarelo/evidence/WU-002-consumer-graph-v1.json`

## O que o ProgBase realmente faz

### Scheduler, não state machine física

`PostActionRefresh@0x5162F8` usa labels:
- `state_0..state_5`
- `state_acquire_petrol_line`
- `state_acquire_gas_line`
- `state_draw_gas_petrol_curve`

Esses labels pertencem a um **scheduler de refresh do host**, não a uma state machine física da ECU.

Estrutura provada:
- selector `0x51AE20`
- matcher `0x51ABA8`
- advance `0x51ADE8`
- reset `0x51AE10`
- table pointer `DM+0x4BC`
- count `DM+0x4C0`
- cursor `DM+0x4C4`

Qualquer state machine interna da ECU permanece fora dos artefatos atuais.

### Refresh roles

- `state_0` → PetrolPoint + petrol maturity/polling
- `state_1` → GasPointPrev
- `state_2` → GasPoint current + gas maturity/polling
- `state_3` → KLine = `PETR_INJ_TBP × MUL_ACT`
- `state_4` → acquired-zone areas
- `state_5` → axes/run geometry
- acquire petrol line → `PETR_MNFLD_PRESS_RV`
- acquire gas line → `GAS_MNFLD_PRESS_RV`
- draw gas/petrol curve → redraw host-side de vetores já lidos

**Redraw nunca é evidência de nova mutação ECU.**

## Curvas e pontos nativos

Referências:
- PetrolCurve = `PETR_INJ_TBP × PETR_MNFLD_PRESS_RV`
- GasCurve = `PETR_INJ_TBP × GAS_MNFLD_PRESS_RV`
- KLine = `PETR_INJ_TBP × MUL_ACT`

Pontos:
- `0x0162/0x0163` → PetrolPoint
- `0x015F/0x0160` → GasPoint current
- `0x015D/0x015E` → GasPointPrev

GasPointPrev é o **snapshot nativo do epoch AutoMatch anterior**, não o poll anterior.

## Quatro regiões MAP

A rotina `0x517254` usa `MNFLD_PRESS_THD` e constrói quatro regiões low→high MAP:

- R1: 0.000–0.461 bar
- R2: 0.461–0.666 bar
- R3: 0.666–0.870 bar
- R4: 0.870–1.126 bar

Cortes internos: índices 5, 9, 13; endpoint 17.

As quatro flags `ACQUIRED_ZONES_*`:
- vêm da ECU;
- não são porcentagem;
- não são progresso monotônico;
- não devem ser reconstruídas por MAP, dwell, point presence ou maturity.

O guard exato é firmware-interno e possui stop condition documentado.

## 18 posições de aquisição ≠ 4 regiões

Os 18 buffers de aquisição e as quatro regiões MAP são conceitos distintos.

Regra correta:
- index split 0..5 / 6..17 é usado apenas para escolher threshold de maturidade;
- R1..R4 é derivado do MAP físico do ponto contra `MNFLD_PRESS_THD`;
- nenhum consumer de produção deve inferir região pelo número do buffer.

## Maturity layer

`0x516F64` usa counters e thresholds nativos.

A comparação `counter >= threshold` governa a camada de polling/maturidade do ProgBase.  
Não governa `ACQUIRED_ZONES`.

O valor observado 3 no LOGNOVO é capture-specific e nunca é hardcoded no produto.

## AutoMatch epoch

Lifecycle observável nas duas capturas:
1. contador AutoMatch muda;
2. `MUL_ACT` muda;
3. `GasPointPrev` é substituído em bloco;
4. counters GNV resetam/reseed;
5. acquired zones podem limpar;
6. novas leituras confirmam o epoch.

LOGNOVO fecha associação bidirecional entre quatro mudanças não-baseline de `NUM_AUTOMATCH_EXECUTED` e quatro bulk replacements de `GasPointPrev`.

A ordem acima é **readback observável**, não ordem de instrução firmware.

## Publisher → consumer: autoridade única

Caminho científico atual:

`NativeAutoCalMonitor`
→ `AutoCalUiProjection`
→ `AutoCalInstrumentProjection`
→ `projection.instrument`
→ `AutoCalUxModel`
→ cockpit.

Regras já implementadas:
- `instrument.zones` vence fontes legacy/raw;
- `instrument.acquisition.gasCounter` vence snapshot cru;
- `instrument.epoch.transition` é a única autoridade para um novo epoch científico na UI;
- counter change sem typed event = aguardar confirmação nativa;
- JS não compara Curve K para inventar epoch;
- previous K points locais são baseline visual only.

## Native AutoCAL ≠ predictor inferido

`AutoMatchSnapshotAnalysis / AUTOMATCH_INFERIDO_V2` é um motor inferido separado:
- `nativeFirmwareExact=false`
- `automatic=false`
- `manualOnly=true`

Ele **não é executado dentro de AutoCalUiProjection** e não fornece ciência à tela AutoCAL nativa.

Contrato:
**AutoCAL nativo = ECU/monitor truth. Predictor = ferramenta inferida separada.**

## Previous epoch sem maturidade falsa

Não existe objeto comprovado `NUM_BUF_UPD_GAS_PREV`.

Portanto `GNV_ANTERIOR`:
- mantém tempo de injeção, MAP, região e identidade de epoch anterior;
- não herda `NUM_BUF_UPD_GAS` atual;
- publica `maturityApplicable=false`;
- não recebe threshold/state de maturidade do epoch atual.

## UX semantics

- gráfico central: X = Petrol Inj. [ms], Y = MAP [bar];
- Petrol reference, Gas reference, current GNV points, previous AutoMatch epoch, live AGORA;
- quatro regiões MAP como contexto discreto;
- nenhuma barra de progresso;
- nenhuma porcentagem derivada de flags;
- `GNV · época anterior` é diferente de `Comparar referência anterior`;
- LEVELS não pertence ao AutoCAL.

Auto Calibration é uma ação humana única de enable/disable. O componente visual pode ser botão; o original não é template visual.

## Bounded UNKNOWN

Permanecem explicitamente fora do que os artefatos podem provar:
- state machine física interna da ECU, se existir separada do scheduler host;
- fórmula interna que atualiza GasPoint;
- guard interno de ACQUIRED_ZONES;
- instrução/instante exato do copy Current→Prev;
- efeito firmware exato do Finish após write.

Esses UNKNOWNs **não autorizam algoritmo host paralelo**.

## DoD

| Critério | Estado |
| --- | --- |
| consumer graph machine-readable | PROVEN |
| relatório humano reconciliado | PROVEN |
| ações → dispatcher → nativo → refresh | PROVEN dentro da fronteira host observável |
| curves/points/bands/labels consumers | PROVEN |
| host write vs ECU mutation | PROVEN nas superfícies usadas pelo produto |
| publisher/consumer tipado | IMPLEMENTED |
| native epoch single authority | IMPLEMENTED |
| predictor separado do native AutoCAL | IMPLEMENTED |
| UNKNOWNs explicitamente marcados | PROVEN |
| receipt integrado do fechamento | PROVEN — fast `35766226922` + render `35766226859` no SHA `6e669ce9159f9baf1f6f467427e00ecca96cc5f6` |
| WU-001 fechado | PROVEN — issue #73 CLOSED / COMPLETED |

## Closure receipt — 2026-09-22

WU-002 está **CLOSED / PROVEN** para a fronteira host-observável e para o caminho de produção tipado.

Pré-requisito:
- WU-001 / issue #73: **CLOSED / COMPLETED**.

Receipts integrados do código:
- `OMEGAS Amarelo fast contracts` — run `35766226922` — SHA `6e669ce9159f9baf1f6f467427e00ecca96cc5f6` — **SUCCESS**;
- `OMEGAS Amarelo Android render evidence` — run `35766226859` — mesmo SHA — **SUCCESS**.

Receipt documental posterior:
- fast contracts run `35766739562` — SHA `ddd5ac3181ef4d85c27cf0fa58c28e3a19d5a2c2` — **SUCCESS**.

Fechamento significa:
- scheduler host separado de qualquer state machine física interna;
- consumers e publishers nativos mapeados;
- 4 regiões MAP e 18 posições mantidas como conceitos distintos;
- maturity/counters separados de ACQUIRED_ZONES;
- GasPoint current/previous e epochs AutoMatch tratados pela autoridade nativa;
- predictor inferido separado do AutoCAL nativo;
- counter-only jamais promovido a novo epoch científico;
- confirmação de epoch protegida contra polling assíncrono e replay do mesmo evento;
- UNKNOWNs firmware-internos preservados como limites explícitos, sem algoritmo host paralelo.

Nenhum desses UNKNOWNs reabre WU-002 sem novo artefato capaz de fornecer evidência direta.


## Polling-safe native epoch confirmation — 2026-09-22

A previous monitor implementation could miss a real native epoch if `NUM_AUTOMATCH_EXECUTED`
changed in one probe but the changed `MUL_ACT` became visible only in a later full readback.

The runtime now treats the counter transition as a **signal**, not as scientific confirmation:

1. counter transition opens a pending native epoch;
2. the pre-transition `MUL_ACT` payload is preserved as baseline;
3. full snapshot readback checks for a changed native `MUL_ACT`;
4. only a changed K readback emits `nativeAutoMatchEpochEvent`;
5. up to 3 heavy snapshots are actively requested (initial + two follow-ups);
6. if K is still unchanged after that budget, forced heavy polling stops but the transition remains passively pending;
7. any later natural full snapshot may still confirm the pending epoch if native K finally changes;
8. session change/disconnect clears pending state;
9. an explicit manual app action clears pending native attribution before its own readback;
10. overlapping unresolved counter transitions fail closed rather than attributing a K change to the wrong epoch.

The 3-snapshot budget limits only **active retries**. It is an operational anti-race/anti-loop policy, **not ECU science**, and it does not erase unresolved evidence.

Typed HMI projection now carries:
- `epoch.pending` -> human state **Verificando ajuste da ECU**;
- `epoch.unconfirmed` -> **Mudança ainda não confirmada**;
- `epoch.transition` only after native K readback confirmation.

Counter-only change remains insufficient to create a scientific epoch in the UI.


### Native epoch edge identity — 2026-09-22

The typed native event is treated as an edge, not as a latched state that can replay forever.

A UI epoch transition is accepted only when:
- typed `before` equals the previous projection AutoMatch counter;
- typed `after` equals the current projection AutoMatch counter;
- `oldHash` and `newHash` are both present and differ;
- readback is valid;
- ECU-native observation is true;
- no app write produced the event.

Therefore:
- repeating the same typed event after the counter is already at its `after` value does **not** create another epoch;
- a stale typed event whose before/after do not match the actual projection edge fails closed;
- counter-only change remains pending/unconfirmed and never becomes a scientific epoch by itself.

Fast-contract receipt for monitor + projection + consumer logic:
- SHA `6e669ce9159f9baf1f6f467427e00ecca96cc5f6`
- run `35766226922`
- conclusion: **SUCCESS**.
