# CHECKPOINTS — diário retomável

Regra: depois de cada commit, este arquivo é atualizado com **feito / falta / próxima ação exata**. Outra sessão deve conseguir retomar lendo só isto e o `README.md`.

## Commit 0 — esqueleto e mapa do que já está provado

**Feito**
- Branch `evidence/progbase-original` criada a partir de `origin/OmegasPlatina` (`b185e80`).
- Lidas e cruzadas as fontes: `docs/reference/progbase/*` (branch `claude/brave-darwin-wuliyo`), `docs/evidence/2026-09-2*` e fixtures `ORIGINAL_DERIVED` (Platina/Verde), código do protocolo em `app/src/main/java/com/omegas/prohub/ecu/`, DUMP no Drive (`TAUTOCALDM`, `TFORMVISUALIZZA` em texto, `README_RELATORIO.md`), capturas Portmon pequenas (`1.LOG`, `2.LOG`, `3.LOG`).
- Esqueleto da pasta e arquivos-fonte preservados em `fontes/`.

**Mapa inicial: já provado antes desta branch (fontes versionadas em Platina)**
- Quadro do protocolo (eco + `53` + len + payload + checksum soma mod 256): `evidence/portmon/full-corpus-manifest.json` (36.457 de 36.463 transações válidas).
- Telemetria `48 01 49`, payload 34 bytes, offsets 0/6/8/11/13/14/16/17/24/28 e consumidores: `tests/fixtures/progbase-autocal-consumer-map-v1.json`.
- Família AutoCal SC 0x014A–0x018E: comandos, cadências, consumidores, ações `02 24 04 xx`, Reset K, Finish, maturidade: fixtures `progbase-autocal-*.json`, `portmon-lognovo-*.json`, `docs/evidence/2026-09-21-*`, `docs/platinum/evidence/*`.
- Escalas DFM dos vetores AutoCal (`/512`, `/1024`, `/16384`): `tests/fixtures/progbase-autocal-scale-dfm-v1.json`.
- Nível: canal `0x0E`, min/max, regra `step = |max-min|·0,2`: `progbase-autocal-consumer-map-v1.json#levels`.

**Lacunas identificadas para cavar nesta branch**
- Sequência de conexão/desconexão do ProgBase na fiação (`00 02`, `01 00 3A`, `00 25`, `00 01`): só corroborada pelo código do OMEGAS.
- Escrita do Mapa K no original (bloco de 144 escritas, flag de inserção em `FLAG_CONF1` SC 3): citado em incidente, sem bytes versionados.
- Escalas da telemetria rápida (pressão `/800`, água `109-raw`, temp. gás `raw-20`, injeção `0,00256 ms`): constantes do app, origem no original ainda não fechada.
- Eixos do Mapa K (`GIRI_PER_K` SC 61, `TEMPI_PER_K` SC 55): o OMEGAS não os lê da ECU.
- Level: tabela por tipo de sensor e conversão raw→nível exibido.

**Próxima ação exata (na época)**
- Escrever `protocolo.md` (tema 1) e fazer o commit 1. Em paralelo aguardam três subagentes: parse do `PortmonLOGNOVO.zip` (6 MB), parse de `1/2/3.LOG`, decifração dos `.lec` do carro.

> Nota de processo: os commits 1–7 foram feitos em sequência sem atualizar este diário a cada um (violação da regra). As entradas abaixo foram escritas retroativamente, a partir dos próprios commits, antes do commit 8.

## Commit 1 — `0b486e3` — tema 1 protocolo

**Feito**
- `PortmonLOGNOVO.zip` (6 MB, LOG de 63 MB, 20.288 transações) parseado; `protocolo.md` escrito com fatos de fiação: CP210x 9600 8N1 com sonda em 38400, purge antes de cada escrita, `COMMTIMEOUTS` 1200/200 ms, quadro eco+`53`+len+payload+checksum, gramática `09/0A/29/2A/12-14/31+n/37`, sessão `00 02 02` → `01 00 3A 3B` → `00 25 25` → dump seq 31–410 → telemetria; desconexão `01 12 00 13` + `00 01 01`; `CA 01 10` = não suportado, sessão continua; `FLAG_CONF1` SC 3 alternando bit 0x0800.
- `fontes/lec-208-pegeot-pos-cali.md`: chave MLTP e prefixo decifrado do `.lec` real (só 3.546 B chegaram do Drive; registrado como limitação).
- `INDICE-FONTES.md` com a entrada do Lognovo de 63 MB (SHA do zip e do LOG).

**Falta**: temas 2–6, lacunas, registry.

## Commit 2 — `ba73fff` — tema 2 telemetria

**Feito**: `telemetria.md`: 34 bytes de `48 01`, tabela offset→campo→escala→exibição, byte 11 como bitfield de combustível, escala de injeção = `BASE_TEMPI_GLOBALE` (SC 121 = 2560 → 0,00256 ms/contagem), validação com quadros reais (`1.LOG`/`3.LOG`/LN: MAP 992 motor desligado, água, pressão, rpm).
**Falta**: temas 3–6, lacunas, registry.

## Commit 3 — `ae06172` — tema 3 parâmetros

**Feito**: `parametros.md`: SC de identidade/sessão, Mapa K e eixos, AutoCal, nível, tempos mortos e compensações (SC 125/126 e família de pressão), SC não suportados (`CA 01 10` a toda leitura: 313–318, MGLEV), lista "ignorado de propósito". Valores reais desta ECU vindos do dump de conexão (LN seq 31–410).
**Falta**: temas 4–6, lacunas, registry.

## Commit 4 — `8bba0b2` — tema 4 AutoCal nativo

**Feito**: `autocal.md`: objetos 0x014A–0x018E com escalas (`/512`, `/1024`, `/16384`, Q14 ×30), maturidade e zonas (desmontagem `0x00516F64` + DFM), ações `02 24 04 xx` e `01 24 05` com bytes exatos, épocas de AutoMatch nativo e Reset All na fiação, camadas do `ChartData`.
**Falta**: temas 5–6, lacunas, registry.

## Commit 5 — `df95523` — tema 5 Curva K e Mapa K

**Feito**: `curvas-mapas.md`: `MAP_K` SC 84 U8 13×12, leitura `2A 54 00 rr`, escrita de 144 células `14 54 00 rr cc vv` em bloco (LN seq 1054–1197) sem telemetria nem readback, eixos SC 55/61 lidos e editados (`14 37 00`/`14 3D 00`), 13ª linha lida e nunca escrita, readback ≠ escrito (`100 → 149 → 166`), neutro 100 `INFERIDO`, `K_MAPPA_NEUTRO` SC 188 = 85, Curva K `MUL_ACT`, backup `.lec`.
**Falta**: tema 6, lacunas, registry.

## Commit 6 — `6aea1e7` — tema 6 level

**Feito**: `level.md`: canal 0x0E da telemetria, `TIPO_SENSORE` SC 36 = 0x81, `RIF_SENSORE` SC 37 = 39/93/143/219, filtros SC 276 (1,0/0,15), SC 300 `3 10 37 62 90`, SC 313 não suportado, rotina min/max/find (`step = |max−min|·0,2`), conversão raw→nível exibido até onde o DUMP mostra.
**Falta**: lacunas, registry.

## Commit 7 — `5d1a203` — lacunas e registry

**Feito**: `lacunas.md` (L-01…L-11 com a prova que fecharia cada uma + lista do que foi encerrado nesta branch); `registry.json` (schema `omegas.evidence.progbase-original.registry.v1`, 126 entradas: 74 parâmetros, 23 comandos, 18 campos de telemetria, 11 ações; 110 `PROVADO`, 11 `INFERIDO`, 5 `DESCONHECIDO`), gerado por script a partir dos temas e validado como JSON.
**Falta**: README com estados finais e lista de contraste; este diário; push.

## Commit 8 — fechamento

**Feito**
- `README.md`: tabela de temas com estado final e commit; contagem de selos; lista de 13 "pontos para contrastar com o OMEGAS" ordenada por risco (só lista, sem contraste).
- `CHECKPOINTS.md`: entradas retroativas dos commits 1–7 e esta.
- `level.md` 6.5: referência corrigida para L-08.
- Branch `evidence/progbase-original` publicada em `origin`.

**Estado final**
- Os seis temas, `lacunas.md` e `registry.json` estão concluídos. Nenhum arquivo do app foi tocado. Nenhum binário proprietário entrou.
- Fontes usadas de fato: DUMP (DFM/RTTI/desmontagem via estudos preservados) + Portmon (`PortmonLOGNOVO.zip` 63 MB parseado aqui; `1/2/3.LOG` lidos direto; `PortmonAUTOCAL` e Lognovo de 149,9 MB só via fixtures já versionadas, ver L-11).

**Próxima ação exata (fora desta branch)**
- Abrir WorkUnit de contraste OMEGAS × ProgBase a partir da lista do `README.md`, começando pelos pontos 1–5.
- Para fechar lacunas: L-07 (uma célula do Mapa K escrita e relida na mesma sessão) e L-04 (desmontar `TFormVisualizza.Timer1Timer`) são as de maior retorno.

## Commit 9 — restrição de fontes a DUMP + `PortmonLOGNOVO (1).zip` (parte 1, intermediário)

**Decisão do owner (2026-10-02):** só a pasta DUMP e a captura `PortmonLOGNOVO (1).zip` valem como evidência. Nada do OMEGAS (sessões, fixtures, código, docs de outras branches), nenhum `.lec`, nenhuma outra captura Portmon.

**Feito**
- Verificado que o LOG de 63 MB já parseado é o **prefixo exato** do LOG de 149,9 MB do zip indicado (mesmas seq/idx); toda citação `LN seq/idx` já está na numeração do arquivo completo.
- `protocolo.md`, `telemetria.md`, `parametros.md`, `autocal.md`, `curvas-mapas.md`, `level.md`, `lacunas.md`, `registry.json`, `fontes/INDICE-FONTES.md`: todas as citações a `1/2/3.LOG`, `PortmonAUTOCAL`, fixtures `tests/fixtures/*`, código Kotlin, `.lec`, `autocalcfg.ini`, docs de evidência do OMEGAS e DFM em texto de `reverse_report` foram substituídas por LN ou DUMP, ou rebaixadas (hipóteses de escala em `telemetria.md`; VAs de desmontagem marcados L-12).
- Removidos de `fontes/`: `lec-208-pegeot-pos-cali.md` e os quatro `brave-darwin-*.md` (ficam no histórico do git).
- Valores de SC 42/43 reencontrados no LN (seq 129–130).
- Novas lacunas L-11 (zip de 14 MB excede o limite da ferramenta do Drive; HTTP bloqueado) e L-12 (`Seção_0_.text.bin` idem; VAs não reexecutados).

**Falta (commit 10)**
- Decodificar os DFM binários do DUMP (download em curso), regenerar `fontes/parametros-dfm-inventario.json` a partir deles, preencher SHA-256 em `INDICE-FONTES.md`, conferir offsets citados, atualizar `README.md`.
