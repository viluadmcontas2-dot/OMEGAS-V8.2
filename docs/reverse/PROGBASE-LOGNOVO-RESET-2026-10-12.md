# ProgBase × ECU: o que o log real mostra sobre reset (2026-10-12)

Fonte: `PortmonLOGNOVO.LOG` (150 MB, 2 631 711 linhas, 39 524 escritas seriais do ProgBase.exe; Drive do dono,
SHA-256 do zip `6879fa2a…`). ProgBase (3).exe = C++Builder/Embarcadero 14, 12,6 MB (SHA-256 `8a2d297c…`).
Análise com REA 6.4.0 + Ghidra 12.1.4 (instalados pelo `tools/rea/install.sh`) e leitura direta do log.

## Fatos provados no log

| Fato | Evidência |
|---|---|
| O ProgBase **nunca** manda reset seletivo (`02 24 04 01` gasolina, `02 24 04 02` GNV) | 0 ocorrências em 39 524 escritas |
| O ProgBase manda **1** `RESET_ALL` (`02 24 04 04 2E`) | linha 69944 |
| A ECU responde ACK (`53`) e o ProgBase relê os vetores de aquisição (`29 62 01`, `29 5C 01`, …) | linhas 69944–70608 |
| Contadores de GNV (`NUM_BUF_UPD_GAS`, 0x015C) **antes** do reset | `[10×16, 0, 0]` (linha 68492) |
| Contadores de GNV **depois** do reset | `[0×18]` (linha 70608) |
| A ECU zera na hora (não "no próximo ciclo") | o primeiro `29 5C 01` após o reset já vem zerado |

## Consequências para o OMEGAS

- Bug do carro (2026-10-12, "Reaprender GNV" mostrando GNV 12/18 depois): corrigido em `f0b3aa30` — um reset manual
  deixa o combustível "aguardando zero" e só um grupo com os contadores dele zerados confirma a nova aquisição. O
  log real confirma que "zerado" é o único pós-estado observado.
- O reset seletivo `0x02` (GNV) e `0x01` (gasolina) **não têm efeito provado** na ECU: o ProgBase original não os
  usa. Precisa de um teste no carro com recibo (antes/depois dos contadores) antes de prometer seletividade.
  Teste: AutoCal → "Reaprender GNV" → Ferramentas → Log do sistema → procurar `autocal_native_action` com os
  campos `before`/`after` de `NUM_BUF_UPD_GAS`.
- Campos que o app ainda não lê e o ProgBase conhece: `MUL_PREV_EE`, `MUL_UPD_CALL_CNTR_EE`, `GAS_POINT_2DELETE`,
  `AUTO_CALIBR_BYTE_ARRAY`, `AUTO_CALIBR_WORD_ARRAY(D)`, `PETR_INJ_TBUFL`, `MNFLD_PRESS_BUF_PETR_EE`.
- Classes do ProgBase relevantes para o AutoCal: `TAutoCalDM`, `TAutoCalDM_EE`, `TAutoCalUI`, `TAutoCalUIX`,
  `TAutoCalSettings`, `TFormRifAutocal`, `TFormCalibra`, `TFormCorrCalibra`, `TFormModificaMappa`,
  `TStrategiaPandaDM`, `TStrategiaTempiMortiDM`.

## Famílias de comando do ProgBase no log (escritas, por frequência)

`48 01` (20 456, telemetria), `00 14` (4 693), `29 1D` (3 668), `48 08` (3 334), `09 21` (732), `29 5B…29 63`
(~440 cada: vetores de aquisição/MUL_ACT), `48 0B` (434, status nativo), `29 70` (325), `29 8D/8E` (~220),
`14 54` e `0A 1B` (144).

## Pendente

- Descompilação do `TAutoCalDM` (como o ProgBase decide o AutoMatch e a curva) — Ghidra em execução sobre 6,3 MB
  de código; resultado vai para este diretório quando sair.
- Inteligência global × local na tela do AutoCal (dono, 2026-10-12): o AutoCal nativo não guarda RPM por ponto;
  a separação por rotação vem da telemetria do Refino (`EquivalenceAttribution`). Falta mostrá-la no AutoCal e
  tratar Mapa K perto de 255 (renormalizar o mapa e deixar a curva absorver o ganho).
