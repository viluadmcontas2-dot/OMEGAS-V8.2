# Evidência definitiva do ProgBase original (Landi Renzo Omegas / MP48)

**Branch de evidência:** `evidence/progbase-original` (criada a partir de `origin/OmegasPlatina` `b185e80ab68e203e65a79806983bf4210310582c`).
**Missão:** `fontes/PROMPT-EVIDENCIA-PROGBASE.md`. Este levantamento é feito **uma única vez**; daqui em diante qualquer trabalho no OMEGAS consulta esta pasta em vez de reescavar o DUMP, os logs Portmon ou as branches antigas.

Nenhum código do app foi alterado nesta branch. Nenhuma escrita em ECU foi feita. Nenhum binário proprietário entrou no repositório: só trechos mínimos em hex, sempre com a fonte.

## Como usar

1. Procure o tema em um dos seis arquivos abaixo. Cada item traz um **selo**:
   - `PROVADO`: visto na fiação (captura Portmon do ProgBase real) ou no DUMP sem ambiguidade (DFM, RTTI ou desmontagem com endereço).
   - `INFERIDO`: dedução forte, com o raciocínio em uma linha.
   - `DESCONHECIDO`: lacuna registrada, com o que provaria.
2. Cada fato existe em **um** lugar; os demais arquivos apontam para ele por âncora. `registry.json` é a versão de máquina (uma entrada por comando, campo, parâmetro ou ação).
3. Toda afirmação tem fonte rastreável: arquivo do Drive (nome + id) com offset ou linha, sessão/sequência Portmon, VA de desmontagem, ou fixture `ORIGINAL_DERIVED` já versionada (caminho no repositório). O índice completo de fontes está em `fontes/INDICE-FONTES.md`.
4. O que o OMEGAS nunca vai tocar aparece só por nome na lista "ignorado de propósito" de `parametros.md`.
5. Para retomar o trabalho, leia `CHECKPOINTS.md` (feito / falta / próxima ação).

## Mapa dos seis temas

| Tema | Arquivo | Estado |
|---|---|---|
| 1. Transporte e protocolo | `protocolo.md` | em elaboração |
| 2. Telemetria ao vivo (`48 01`) | `telemetria.md` | em elaboração |
| 3. Parâmetros (SC) que o OMEGAS lê/grava | `parametros.md` | em elaboração |
| 4. AutoCal nativo | `autocal.md` | em elaboração |
| 5. Curva K e Mapa K | `curvas-mapas.md` | em elaboração |
| 6. Level (nível do cilindro) | `level.md` | em elaboração |
| Lacunas | `lacunas.md` | em elaboração |
| Registro de máquina | `registry.json` | em elaboração |

## Hierarquia de fontes usada

1. Capturas Portmon do ProgBase 4.2.0.6 real falando com a MP48 do dono (`PortmonAUTOCAL.LOG`, `PortmonLOGNOVO.LOG`, `1.LOG`/`2.LOG`/`3.LOG`). Provam o comando na fiação.
2. DUMP do `ProgBase.exe` 4.2.0.6 (SHA-256 `8a2d297c…36f4`): recursos DFM (`RT_RCDATA`), RTTI, desmontagem com VA.
3. Fixtures `ORIGINAL_DERIVED` já versionadas nas branches `OmegasPlatina`/`OmegasVerde` (`tests/fixtures/progbase-*`, `tests/fixtures/portmon-*`). São extrações diretas das fontes 1 e 2 e ficam citadas pelo caminho, não copiadas.
4. Código Kotlin do OMEGAS (`origin/OmegasPlatina`): serve só para dizer **o que o OMEGAS usa**; nunca é prova do original.

## O que entrou e o que ficou fora

Entra só o que muda como o OMEGAS conversa com a ECU, lê telemetria, interpreta curvas/mapas, grava ou lê nível. Ficam fora: estética, textos de tela, licença, atualização de firmware, OBD, Landi Connect e parâmetros que o OMEGAS nunca vai tocar.

## Arquivos-fonte preservados em `fontes/`

- `PROMPT-EVIDENCIA-PROGBASE.md`: a missão.
- `INDICE-FONTES.md`: todas as fontes (Drive ids, SHA-256, caminhos de fixtures).
- `parametros-dfm-inventario.json`: inventário de 344 componentes `TAeb*` extraídos dos DFM (SC, forma, offset). Vinha da branch `claude/brave-darwin-wuliyo` e seria perdido com ela.
- `brave-darwin-*.md`: os quatro estudos de 2026-10-02 da mesma branch, preservados tal qual (contêm offsets e VAs de desmontagem). O conteúdo útil deles já está destilado nos temas; ficam como registro de origem.
