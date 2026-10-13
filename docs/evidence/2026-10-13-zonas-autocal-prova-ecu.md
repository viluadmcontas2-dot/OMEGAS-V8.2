# Zonas do AutoCal: prova no fio (fronteiras THD[5]/THD[9]/THD[13]) e byte 0x94 — 2026-10-13

Auditoria do achado 1 e 2 de `2026-10-13-autocal-original-vs-omegas.md` com evidência da **ECU**, não da
captura de tela. Fonte: `PortmonLOGNOVO.LOG` (sha256 `43a63272…`, 2 631 711 linhas), parser próprio
`tools/evidence/zonas_autocal_portmon.py` (reproduz o doc 18 do PROTOCOLO-LANDI: byte 11 = `0x94` em 181 quadros,
`0x90` 13 508, `0x80` 6 041, `0x88` 375).

## Método

Para cada virada 0→1 de uma bandeira de zona (`29 6F 01` gasolina / `29 70 01` GNV, lidas a cada ~2 s), o MAP da
telemetria (`48 01`, s16 no offset 17, /1000) nos 3 s anteriores. Se o MAP nesse intervalo nunca entrou na faixa
que um mapeamento atribui à zona, esse mapeamento está excluído.

## Viradas decisivas

| t (s) | linha | comb. | zona (1-4) | MAP min–max nos 3 s | 6/10/14 (0,50/0,70/0,90) | 5/9/13 (0,45/0,65/0,85) |
|---|---|---|---|---|---|---|
| 923,2 | 1 649 102 | GNV | 3 | 0,668–0,698 | ✗ (zona 3 exigiria ≥ 0,70) | ✓ (≥ 0,65) |
| 738,4 | 1 305 484 | GNV | 4 | 0,227–0,874 | ✗ (exigiria ≥ 0,90) | ✓ (≥ 0,85) |
| 990,4 | 1 774 032 | GNV | 4 | 0,756–0,874 | ✗ | ✓ |
| 1140,7 | 2 055 606 | GNV | 2 | 0,462–0,521 | ambíguo | ✓ |
| 1132,9 | 2 040 962 | GNV | 1 | 0,374–0,423 | ✓ | ✓ |

As demais 13 viradas são compatíveis com os dois mapeamentos. Nenhuma contradiz 5/9/13. Veredito: zonas =
bandas **0–4 | 5–8 | 9–12 | 13–16**, como as linhas pontilhadas do ProgBase (docs 18/20 do PROTOCOLO-LANDI).

## Achado novo: a bandeira de zona não é "bandas cheias"

Em t=886,7 s (linha 1 581 586) a zona 4 do GNV vira 1 com os 18 contadores `NUM_BUF_UPD_GAS` em 0; em t=1066,6 s
com só quatro bandas em 1. A bandeira marca que o motor **passou** pela zona (a ECU registrou presença), não que
as bandas estejam maduras. O texto da tela "Zona N confirmada pela ECU" continua verdadeiro; "zona completa" não
seria.

## Byte 0x94

181 quadros em LN (1 096 em AC): injeção gasolina 0 e GNV 0 em todos, rpm 1306–4264, MAP 0,158–0,570 (mediana
0,168). O ProgBase lê as zonas GNV com ele (doc 17). No OMEGAS, 174 já caíam na heurística `physicalCutoff`;
7 (MAP 0,39–0,57) viravam `UNKNOWN` → `TRANSITION` no `FuelStateResolver`. Sem amostra útil perdida (injeção 0);
corrigido por semântica: `0x94` → `CUTOFF` em `Mp48Protocol.kt`.

## O que mudou no código

- `AutoCalAcquisition.zone()` e `zoneBoundariesInclusive` → `[4, 8, 12]`.
- `autocal-cockpit.js`: `zoneForBand` e `edges = [0, 5, 9, 13, 17]`.
- `Mp48Protocol.kt`: `0x94` → `CUTOFF`.
- Testes: `AutoCalAcquisitionTest` (novo caso banda 5 → zona 2), `Mp48TelemetryScaleTest` (0x94 fora da
  heurística), `tests/ui/autocal-didactic-cockpit.test.cjs` (limites e `currentZone`), helper do
  `RefinementAutopilotTest`.

Não mudou: bytes de comando, `LOW_BAND_MAX_INDEX = 5` (seletor de limiar de maturidade gasLow/gasNormal, provado
pelo DUMP; é outra coisa, não a zona — fica como pergunta aberta se também deveria cortar em 4).
