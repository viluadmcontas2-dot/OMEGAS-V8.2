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

| Tema | Arquivo | Estado | Commit |
|---|---|---|---|
| 1. Transporte e protocolo | `protocolo.md` | concluído (serial, quadro, gramática, sessão, `CA 01 10`, `FLAG_CONF1`, tempos) | 1 |
| 2. Telemetria ao vivo (`48 01`) | `telemetria.md` | concluído (34 bytes, offsets, escalas, byte 11, validação com valores reais) | 2 |
| 3. Parâmetros (SC) que o OMEGAS lê/grava | `parametros.md` | concluído (identidade, Mapa K, AutoCal, nível, tempos mortos, não suportados, ignorados) | 3 |
| 4. AutoCal nativo | `autocal.md` | concluído (objetos 0x014A–0x018E, maturidade, ações, AutoMatch, gráfico) | 4 |
| 5. Curva K e Mapa K | `curvas-mapas.md` | concluído (eixos SC 55/61, bloco de 144 escritas, readback ≠ escrito, backup `.lec`) | 5 |
| 6. Level (nível do cilindro) | `level.md` | concluído (canal 0x0E, SC 36/37/276/300, rotina min/max, conversão) | 6 |
| Lacunas | `lacunas.md` | concluído (L-01…L-11, cada uma com a prova que fecharia) | 7 |
| Registro de máquina | `registry.json` | concluído (126 entradas: 74 parâmetros, 23 comandos, 18 campos, 11 ações) | 7 |

Selos no `registry.json`: 110 `PROVADO`, 11 `INFERIDO`, 5 `DESCONHECIDO`.

## Pontos para contrastar com o OMEGAS (só a lista, por risco decrescente)

O contraste em si fica para outra WorkUnit. Cada ponto aponta a seção onde a evidência original está.

1. **Mapa K: o valor lido não é o valor escrito** (`100 → 149 → 166` sem escrita intermediária). O OMEGAS valida a escrita comparando readback com o enviado (`curvas-mapas.md` 5.2, L-07).
2. **Valor neutro do Mapa K**: OMEGAS usa 128; a evidência original aponta 100 (`INFERIDO`) e nada sustenta 128 (`curvas-mapas.md` 5.3).
3. **`FLAG_CONF1` gravado com bytes fixos deste carro**; o original lê o valor e só alterna o bit 0x0800 (`protocolo.md` 1.6).
4. **Eixos do Mapa K fixos em arquivo de lock**; o original lê `TEMPI_PER_K` (SC 55) e `GIRI_PER_K` (SC 61) da ECU e permite editá-los (`curvas-mapas.md` 5.1).
5. **Escala do tempo de injeção fixa (0,00256 ms)** em vez de derivada de `BASE_TEMPI_GLOBALE` (SC 121 = 2560) lido da ECU (`telemetria.md` 2.3).
6. **`CA 01 10` tratado como falha de sessão**; o original continua e segue para o próximo pedido (`protocolo.md` 1.5).
7. **Desconexão sem `01 12 00`** após escritas; o original envia `01 12 00 13` antes de `00 01 01` (`protocolo.md` 1.4).
8. **Escrita do Mapa K**: original grava 144 células em bloco contínuo, sem telemetria no meio e sem readback na sessão (`curvas-mapas.md` 5.2).
9. **Escalas da telemetria rápida** (pressão `/800`, água `109−raw`, temp. gás `raw−20`) continuam `INFERIDO`; `pressureDiffBar` depende delas (`telemetria.md` 2.2, L-04).
10. **Tempos mortos e compensações** (SC 125/126 e família de pressão) existem na ECU e o OMEGAS usa constantes (`parametros.md` 3.5).
11. **Timeouts e purge**: original 1200 ms até o status e 200 ms depois, purge antes de cada escrita; OMEGAS 1800/900 ms e purge por transação (`protocolo.md` 1.7–1.8).
12. **Offsets 2–3 e 19 da telemetria** têm identidade provável (MAP bruto; tensão dos injetores de gás) e hoje são ignorados (`telemetria.md` 2.2).
13. **Level**: tipo de sensor `0x81` e referências SC 37 são lidos pelo original; a conversão raw→nível exibido e o filtro continuam parcialmente `DESCONHECIDO` (`level.md`, L-08).

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
- `lec-208-pegeot-pos-cali.md`: chave e algoritmo do `.lec` (MLTP) e o prefixo decifrado do backup real, cruzado com a fiação.
