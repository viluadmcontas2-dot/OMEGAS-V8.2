# Export das sessões (v1 compatível + v2)

Objetivo do dono: gerar MUITO mais dado e inteligência fora do app (Drive + Graphify + Python): replay milimétrico, benchmarking de mapas/curvas e "qual tanque rendeu mais". O app só CAPTURA e valida; a ciência fica fora e pode mudar sem novo APK. Regras: desconhecido é vazio/null, nunca 0; o app nunca escreve na ECU/CAN por aqui; o formato `omegas-session-log-v1` (`events_*.jsonl`) segue igual (só ganhou campos novos).

## 1. O que o app exportava (levantamento, 2026-10-23)
- `events_NNNN.jsonl` (v1): 1 linha por evento; telemetria só a cada >= 250 ms (**18,7 % dos quadros** da ECU; a ECU manda ~15/s). 59 % de cada linha de telemetria era estado interno de aprendizado. Segmento até 64 MiB; sessão até 256 MB.
- ZIP único ao fechar a sessão (até dezenas de MB; o download pelo Drive/MCP quebra acima de ~8 MB) ou partes `_parte_NNNN`. ID da sessão = data da parede + aparelho (frágil: relógio de 2025, salto de 343 dias, cópias `(12)`, `.json.json`).
- 91 de 105 sessões reais terminaram SEM `session_stopped` (corte de energia); `level_raw` sem escala documentada; velocidade sempre 0 (GPS desligado).

## 2. Camadas do v2
| Camada | Arquivos | Quem escreve |
|---|---|---|
| BRUTA (fonte da verdade) | `raw/frames_48_01.NNNN.tsv`, `raw/tx.NNNN.tsv`, `raw/mm.NNNN.tsv`, `raw/heartbeat.NNNN.tsv` + `events_*.jsonl` (v1) | app |
| DERIVADA leve no aparelho | `resumo/session_summary.json` (`omegas-session-summary-v1`), `resumo/series_1hz.csv`, `resumo/manifest.json` | app (a cada 60 s de tempo dos dados e no fechamento) |
| DERIVADA completa (offline) | `derived/series_1hz.csv`, `series_5hz.csv`, `segments.csv`, `level_steps.csv`, `tank_cycles.csv`, `stalls.csv`, `writes.csv`, `cell_yield.csv`, `cell_compare.csv`, `kpis.json`, `quality.json` | `tools/sessoes/export_v2.py derivar` |
| ÍNDICE | `index/ingest_log.csv`, `conflicts.csv`, `tank_ledger.json` (livro-caixa no aparelho) | app / Python |

Os `session_summary.json` e `manifest.json` da RAIZ da sessão são o resumo semântico antigo e o manifesto do log v1 (não mudam); os novos ficam em `resumo/`.

## 3. Bruta: formato
- Texto colunar, append-only, um arquivo por fluxo e segmento (16 MiB brutos no máximo), cabeçalho `#omegas-raw-v2 stream= cols= session= boot= segment= base_e=`.
- `frames_48_01`: `e_ms<TAB>hex` com os **34 bytes crus** de CADA quadro `48 01` válido (inclui bytes que o app não decodifica). `e_ms` = elapsedRealtime relativo ao início (monotônico). ≈ **11 B/quadro** em gzip (medido, 20 mil quadros sintéticos); 106 min a 15 Hz ≈ 1 MB.
- `tx`: TODA transação serial que não é telemetria (leituras de AutoCal, parâmetros, nível, mapa; escritas manuais): `e_ms, class, status, ok, ms, req_hex, resp_hex, reason, error` (resposta inteira, sem truncar).
- `mm`: cada MUDANÇA do painel da multimídia (`car_info`) e de cada uma das 17 transações (`tx6`, `tx17`, ... com hex inteiro) mais `tx17_props` (todas as propriedades `propId/areaId=valor`, sem o teto de 64 do cartão); `e_ms` e `wall_ms`.
- `heartbeat`: a cada 75 quadros (≈ 5 s, por contagem): `e_ms, wall_ms, engine_frames, engine_failures, recorded_frames, dropped_lines` (acha salto de relógio, corte de energia e descarte).
- Eventos v1 agora levam também `capturedAtMs` (parede na entrada da fila; `recordedAtMs` já atrasou 109 s) e `elapsedMs` (monotônico).

## 4. Tempo e identidade
- `sessionKey = S_<aparelho 8>_<boot 8>_<início monotônico em s>`; `sessionUuid` = UUID determinístico da chave. Não nasce da parede. O `boot` vem de `/proc/sys/kernel/random/boot_id`; sem leitura, deriva de `parede − monotônico` (prefixo `d`, marcado).
- Qualidade do relógio no resumo: `clock.clockSuspect` (`WALL_BEFORE_2026`, `WALL_JUMP`), `wallMinusElapsedFirst/LastMs`, `driftMs`, `gpsReferenceOffsetMs` (hora do GPS menos a parede: somar a `startMs` corrige o relógio). Séries e derivadas usam só o monotônico.
- Identidade do app e da central: `build.{appVersion, commit, buildType}` (o commit REAL; a versão era a mesma em 104 sessões), `ecu.moduleVersion` (do AutoCal; serial e firmware ainda NÃO são lidos: null), `context.{carProfile, vin}` (null quando desconhecidos), hash do mapa/curva (`mapCurve`: `hashAtStart` do cache do app, `hashFirstSeen/Last` lidos da ECU, `changes`).

## 5. Durabilidade e partes
- fsync a cada 75 quadros (≈ 5 s) nos fluxos brutos e a cada 20 telemetrias nos eventos: perda máxima ≈ 5 s. Sessão que não fechou é fechada na próxima abertura: manifesto `recording:false`, `stopReason "sem stop (...)"`, `orphan:true`, hora da última prova de vida (batimento, senão último evento; sem prova = null).
- Partes publicadas: **até 16 MiB brutos por parte** (o JSON/TSV comprime >= 8x; ZIP bem abaixo de 4 MB), ZIP com membros comprimidos (deflate, equivalente a gzip e abre em qualquer lugar), `parte.json` com `sessionKey`, `sessionUuid`, `startMs`, `partIndex`, `isLast`, `totalParts`, `rawBytes`, `contentSha256` e SHA-256 de cada membro. Caminhos preservados (`raw/...`, `resumo/...`).
- Ao lado das partes sai `<sessão>_resumo.zip` (resumo + série + manifesto + livro-caixa, ~100–300 KB): 95% das análises só precisam dele.
- Manifesto `resumo/manifest.json`: constantes (`rawFormat`, colunas de cada fluxo), arquivos com bytes e SHA-256, `streams`, `rawFiles`, `packageSha256`.

## 6. Nível do cilindro (escala medida no campo)
- `level_raw` = byte 13 do `48 01`. **255 = vazio**; recém abastecido ≈ 31–78; escada ≈ **75 / 125 / 177 / 253**; 19–22 = sensor sem leitura (central sem motor), nunca é nível (<= 25 inválido). A ECU filtra o sinal (subida suave, τ ≈ 7 min medido): o degrau chega com atraso.
- `level_calibration` (evento, a cada conexão/reconexão, SÓ LEITURA, mesmos pedidos do original conferidos no fio): tipo do sensor (SC 0x24, ex. 0x81), 4 referências (SC 0x25, ex. 39/93/143/219), filtro FAST/SLOW (SC 0x114, raw/32768), histerese e 4 LEDs (SC 0x12C). Zonas = entre as referências (<39 cheio, 39–93 ¾, 93–143 ½, 143–219 ¼, >=219 reserva).
- `level_step` (nível firme = 15 quadros com dispersão <= 3; mudança >= 20) e `refuel_detected` (queda >= 40 contra o último nível conhecido, `source` BETWEEN_SESSIONS via livro-caixa ou IN_SESSION). Tanque = ciclo entre abastecimentos; "km por unidade de nível" e "minutos de injetor por 100 de nível" são **PROXY** e só saem com >= 20 unidades consumidas.
- Corrigido nesta rodada: a barra do topo (`live-bar.js`) mostrava tanque CHEIO como reserva (sentido e cortes ao contrário dos dados). Agora byte alto = vazio e as zonas são as referências da central.
- Lacuna registrada (sem prova no app, NÃO implementado): `STATO_DIAGNOSI` (`29 1D 00`) e `DIAGNOSI_INJ_BENZ` (`09 21 00`); `TANK_VOL`/consumômetro (NAK nesta central).

## 7. Eventos novos (control plane)
`level_calibration`, `level_step`, `refuel_detected`, `fuel_change` (GNV/GASOLINA/TRANSICAO/MOTOR_OFF, 3 quadros de confirmação), `lifecycle` e `lifecycle_backlog` (serviço, USB, motor; os 64 últimos guardados mesmo sem gravação), `user_action` (abastecimento registrado, interruptores, operações V8, com resultado). Nada decide por relógio (docs/handoff/EVENTOS-SEM-TEMPO.md).

## 8. Resumo `omegas-session-summary-v1` (camada leve)
Identidade e relógio; durações (telemetria coberta, GNV, gasolina); movimento por fonte (`MM` multimídia > `GPS` > `RPM_PROXY` rpm > 1100; trapézio, buraco > 2 s [GPS 5 s] não integra; fontes nunca somadas; proxy não tem km); odômetro da multimídia; rpm; minutos de injetor GNV (`Σ gas_ms × rpm/120 × 4 × dt`); nível início/fim/mín/máx + abastecimento; `tankSegments`; mapa/curva ativos; estatística da multimídia e canais crus; lacunas com início/fim; qualidade (% válidos). Série de 1 Hz (`series_1hz.csv`, <= 1 MB/h): `t_s, rpm, map_bar, speed_kmh, speed_src, moving, moving_src, gnv_level_raw, gas_ms, petrol_ms, fuel, mm_*`.

## 9. Offline (Python, `python3 -I`)
- `tools/sessoes/export_v2.py ingerir|converter|derivar|grafo`: ingestão idempotente por (sessão, arquivo, byte inicial, sha256) (cópia duplicada, zip renomeado, `(12)`, ordem trocada não duplicam; mesmo trecho com outro conteúdo = conflito, nunca sobrescreve); conversor v1 → v2 (bruta parcial, `raw_complete=false`, chave estável); derivação; grafo para o Graphify (nós Session, TankCycle, MapVersion, CurveVersion, EcuProfile, WriteAction, Stall; arestas `usa_mapa`, `usa_curva`, `abasteceu_depois_de`, `escreveu_antes_de`, `produziu`, `pertence_a`, `tem_perfil_ecu`).
- `tools/sessoes/analisar_resumos.py`: tabelas/CSV/grafo só dos resumos leves; compara mapas só com cobertura comparável.
- Rendimento por célula (rpm × MAP) só é comparado entre versões de mapa quando as DUAS têm >= 10 s na célula; senão fica sem delta.

## 9b. Sessão, tanque e mapa: sem suposição rígida (dono, 2026-10-23)
- **Sessão = qualquer intervalo de conexão** (cabo ligado → desligado). Curta, longa, sem nível, sem abastecimento, sem parada, sem ignição: tudo válido. O dono pode conectar mil vezes no dia. Nenhum campo obrigatório depende de evento raro.
- **Tanque/ciclo/abastecimento é camada DERIVADA**, costurada ENTRE sessões pelo livro-caixa (`tank_ledger.json`) com eventos detectados (`refuel_detected`, salto do nível) quando existirem. Sem evento = o ciclo continua aberto; uma sessão nunca fecha por causa disso.
- Cada sessão é autocontida; ligações (tanque, mapa, curva) são referências por ID (`sessionId`, `tankIndex`, hashes), tolerantes a ausência, ordem trocada, duplicatas e lacunas. A ingestão é idempotente e não depende da ordem. Teste: `TankLedgerTest` (3 sessões curtas sem abastecimento + 1 com abastecimento, em 3 ordens e com duplicata → mesmo ciclo).
- `MAPA-DO-CARRO`, `COMPARATIVO` e `LINHA-DO-TEMPO`: sem leitura do mapa, o mapa é `known:false` (nunca 0); sem tanque comparável o veredito é INCONCLUSIVO com os motivos.

## 10. Riscos e limites (honesto)
- **CPU/I/O a taxa cheia no rk3326 é INCERTO** (nunca medido no aparelho). Por isso: a thread da ECU só enfileira (fila de 16.384 linhas, nunca espera); fila cheia = DESCARTA e CONTA (`dropped_lines` no batimento, visível no export); escrita em thread própria de prioridade baixa; fsync por contagem (não por quadro). Se faltar CPU a degradação é perder linhas contadas, nunca atrasar a serial.
- Não foi medido no carro: a ordem de grandeza (≈ 0,6–0,7 MB/h de quadros) vem de medição em dados sintéticos e do corpus real.
- Serial/firmware da ECU não são lidos (null). Significado do tipo de sensor 0x81 é desconhecido (hipótese: bit 7 = invertido). `boot_id` pode ser derivado (marcado) se o Android recusar a leitura.
- Escala do byte 19 (÷16 V) segue provisória; unidade de velocidade/odômetro da multimídia assumida km/h e km (não confirmadas).
