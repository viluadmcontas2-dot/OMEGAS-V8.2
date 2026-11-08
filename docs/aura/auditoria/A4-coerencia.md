# A4 — Coerência das sugestões (Diário + Coerência)

Auditoria só de leitura. Base: `docs/aura/PLANO.md` seção Inteligência. Tudo abaixo com caminho:linha; "inferido" = não verificado em execução.
Caminho base Kotlin: `app/src/main/java/com/omegas/prohub/` (abrevio `P/`).

## 1. O que já existe de histórico (achado-mãe: já há um "diário", mas só da Curva K)

| Peça | Guarda | Onde/formato | Até quando | Quem lê |
|---|---|---|---|---|
| `RefinementJournal` (`P/autocal/RefinementJournal.kt:42`) | Cada gravação da Curva K: antes/depois, índice medido antes, faixas "donas", veredito por faixa (CONFIRMADA/PASSOU/CURTA/PIOROU), bloqueios, `bandScale` (ganho aprendido), `expectedRaw` | `runtimeRoot/refinement_journal.json` (`TelemetryForegroundService.kt:191`), JSON atômico + `.bak`, `FORMAT omegas-refinement-journal-v1` (`:43`) | Só os últimos 40 experimentos (`:74`, corte em `:267`); `bandScale` persiste sempre | `RefinementCycle`, `RefinementAutopilot`, tela Refino |
| `EquivalenceLedger` (`P/autocal/EquivalenceLedger.kt:31`) | Leituras gasolina/GNV (t,rpm,map,ms), impressão digital da curva, época do GNV e motivo, referência da ECU, `localLatch` | `runtimeRoot/equivalence_ledger.json` (`:190`), salva a cada 60 s ou forçado (`:54`, `:617`) | **Descartável por desenho**: `resetGas`/`invalidateGas` apagam o GNV (`:314`, `:333`) | Journal (índice por faixa), Attribution, MapSuggestionPolicy |
| `StallWatch` (`P/autocal/StallWatch.kt:22`) | Apagões e tropeços (Petrol Inj., MAP, RPM antes, corte) | `stall_watch.json` (`TelemetryForegroundService.kt:194`) | Últimos 50 (`:28`); **nunca zerado** | `AutoCalJavascriptBridge.kt:344,388` → `ui/core/stall-region.js` → Refino |
| `RefinementAutopilot` (`:1-70`) | Fase, histerese por faixa, estado do ticker | `FORMAT omegas-refinement-autopilot-v1` (`:~48`) | persistido; sem trilha de decisões | UI |
| `RefinementCycle` (`P/autocal/RefinementCycle.kt`) | Nada próprio; cola Journal+Ledger+Autopilot. `mapKWritten()` (`:134`) e `nativeCurveChanged()` (`:~141`) só **interrompem** | — | — | Service |
| `LocalMapPlanner` / `EquivalenceAttribution` / `MapSuggestionPolicy` | **Nada** (funções puras; `LocalMapPlanner.kt:20-73`, `MapSuggestionPolicy.kt:19`) | só `localLatch` no Ledger (`EquivalenceLedger.kt:154,440`) | — | Refino/Mapa K |
| `PreAutoMatchState` (`:10`) | Foto das bandas ≤1 s antes do AutoMatch da ECU | evento `autocal_pre_automatch_state` na sessão (`TelemetryForegroundService.kt:362`) | vida da sessão gravada | só auditoria humana |
| `d1_decision` | O que o D1 SOMBRA "teria gravado" após cada AutoMatch (`idealFactorsRaw`, `liveFactorsRaw`, pontos, `mapSuggestion`) (`:1046,1080`) | evento de sessão (`SessionRecorder.kt:828`) | vida da sessão; exportável (`SessionRecorder.kt:531 exportSession`) | ninguém no app (nenhuma tela lê) — inferido por grep |
| `V7SessionFileStore` (`P/../v7/runtime/V7SessionFileStore.kt:8`) | Arquivos `.omegas7` nomeados (estado V7) | pasta de sessões, escrita atômica (`:19-37`) | até apagar | legado V7; **não tem relação** com sugestões |

**Lacunas (verificadas):** (a) não há registro de **sugestão** (só de gravação); (b) não há registro de **Mapa K gravado** nem do que o AutoCal nativo fez (só um `interrupt`); (c) nenhum registro liga "quem" a "o quê" por região; (d) o que existe em `d1_decision` nunca volta para a decisão.

## 2. Por que as sugestões mudam e o AutoCal diverge

Três cérebros calculam "a curva certa" com entradas diferentes: AutoCal nativo da central, D1 (`AutoRefinePolicy`, `idealFactorsRaw` do `engineReview` sobre o pré-AutoMatch) e Refino (`RefinementCycle.proposal`, `:~170-200`, por equivalência GNV×gasolina). Só o Refino tem memória (Journal). Hipóteses:

1. **AutoMatch reaplica sobre a curva viva (verificado).** `docs/evidence/2026-10-16-evidencia-arrancada-e-automatch.md:6` (Q1, n=1, forte): a ECU aplica o AutoMatch sobre a curva **viva**, então o que o app gravou entra como base do próximo passo da central e é multiplicado de novo. Efeito: o Refino sobe, o AutoCal ajusta em cima, a proposta seguinte já parte de outra curva. No app: `onNativeCalibrationObserved` → `refinement.nativeCurveChanged()` (`TelemetryForegroundService.kt:~349`) apaga o GNV e interrompe verificações (`RefinementCycle.kt:~141`), mas **não registra quem mexeu em que ponto**.
2. **Ledger descarta o GNV a cada mudança (verificado), não re-escala.** `invalidateGas` por faixa quando o app grava (`RefinementCycle.kt:~100`), `resetGas` total quando a curva muda por fora (`EquivalenceLedger.kt:357-364`, `381-400`). Consequência: toda mudança zera a evidência; a proposta some e depois volta com outra amostra (poucos pares: `MIN_BAND_SAMPLES`, histerese 4%/5%/6% em `RefinementAutopilot.kt:45-60`). Proposta oscilante vem de **evidência curta + histerese só por faixa**, não de bug. Sinal `PROPOSAL_EXIT_EMPTY_TICKS=3` (`:54`) amortece só o piscar.
3. **Buffers adquiridos sob a curva antiga (verificado em parte).** O Journal trata "leitura atrasada" com `STALE_READS_MAX`/`preWriteCapture` (`RefinementJournal.kt:305-330`), mas o D1 usa `pre_automatch` (≤1 s antes, `PreAutoMatchState.kt:5`) e o AutoCal lê buffers `PETR_INJ_TBUF_GAS` adquiridos sob a curva anterior (inferido: o corpus não tem a ordem de aquisição). Não existe um "selo de curva" comum aos três cérebros.
4. **Tropeços nunca zeram (verificado).** `StallWatch.json()` sempre devolve os últimos 50 e `topRegion` (`stall-region.js:~20-60`, `MIN_EVENTS=3`, passo +5%/+10%) escolhe a faixa com mais eventos. `mapKWritten()` (`RefinementCycle.kt:134`) **não toca** o StallWatch e `stall-region.js` não tem noção de "já apliquei aqui" (grep `applied|since` vazio). Resultado: depois de gravar +5% no Mapa K a mesma faixa continua topo e é sugerida de novo. Também mistura eventos de antes e depois da mudança.
5. **Mesma região sugerida por dois caminhos (verificado).** Curva (Refino, faixas `BANDS` 3-12 ms, `EquivalenceLedger.kt:53`) e Mapa K (grupo coluna×faixa, `EquivalenceAttribution` + `LocalMapPlanner`) usam o mesmo par; um grupo entra "local" acima de 5% e sai em 4% (`EquivalenceAttribution.kt:~23`). Se o grupo cruza o limiar entre leituras, a mesma região troca de dono (curva ↔ Mapa K) e a sugestão "muda de curva". Existe `localLatch` (persistido) para amenizar; sem registro de qual caminho já foi usado.
6. **Dados reais (corpus `tests/fixtures/sessoes-uteis`)**: em `eventos-calibracao.jsonl.gz` há 31 `k_factor_batch_confirmed` (dos 468 pontos: 299 "Reset ProgBase", 109 "Ajuste manual", 60 "Restaurar backup"); 464 snapshots, 202 com MUL_ACT válido, só **11 mudanças da curva entre snapshots vizinhos** (2 sessões com 2 cada: `024_RICA…`, `040_RICA…`). Sinais "OSCILA" achados em `028_RICA_2026-09-27` são reset/restauro (±8192), não Refino: **não prova nada**. Conclusão honesta: o corpus (set–out/2026, pré-Journal) **não tem** trilha de sugestão nem de Mapa K; a oscilação real do dono ainda não está gravada em lugar nenhum — essa é a razão de existir o Diário.

## 3. Modelo de dados mínimo do Diário

Arquivo novo `runtimeRoot/diario.json` (JSON-lines em memória, array em disco), `FORMAT omegas-diario-v1`, só acrescenta. Por evento:

```
{ "seq": 17,              // contador, é a ordem (nunca relógio)
  "leituras": 41230,      // contagem de leituras GNV vistas até aqui (a "idade" do evento)
  "quem": "VOCE|REFINO|AUTOCAL_ECU|D1_SOMBRA|ANTIAPAGAO",
  "oque": "SUGERIU|GRAVOU|DESFEZ|RESTAURAR|ECU_MUDOU|DESFECHO|CONGELOU|LIBEROU",
  "alvo": "CURVA_K|MAPA_K",
  "regiao": { "banda": 2, "coluna": 4, "ponto": 13 },  // qualquer um pode ser null (desconhecido = null, nunca 0)
  "antes": 19654, "depois": 22929,  // raw da curva, ou % no Mapa K; null se não sabe
  "curva": "ab12cd",      // fingerprint (EquivalenceLedger.fingerprint, :57) da curva viva no momento
  "desfecho": { "veredito": "CONFIRMADA|PASSOU|CURTA|PIOROU|SEM_BASE|null", "erroPct": -3.2, "leiturasNovas": 120 },
  "ref": 16 }             // seq do evento que este fecha (sugestão→gravação→desfecho)
```
Regras de dados: limite 2000 eventos (descarta os mais antigos, mantém o último desfecho por região); `.bak` atômico por `AtomicJsonFile` (`util/AtomicJsonFile.kt:20`); carregar com `loadError` como as outras classes; sem texto livre.

## 4. Regras de coerência (determinísticas, iniciais; todas sobre `leituras`, não sobre minutos)

| Regra | Condição | Limiar inicial | Ação (só aviso/pausa, nunca grava) |
|---|---|---|---|
| R1 Oscilação | na mesma região, 3 deslocamentos (GRAVOU/ECU_MUDOU) com sinal alternado | janela de 2000 leituras; passo ≥ 1,5% cada | CONGELAR região por 1500 leituras novas; texto "Esta região foi para cima e para baixo; vou esperar" |
| R2 AutoCal desfez o Refino | ECU_MUDOU no ponto em ≤ 600 leituras após GRAVOU no mesmo ponto, com sinal oposto e |Δ| ≥ 50% do passo gravado | 1 ocorrência | marca região "ECU manda"; Refino para de propor a **mesma direção** até medir 300 leituras novas; avisa "O AutoCal baixou o que o Refino subiu aqui" |
| R3 Sugestão repetida sem aplicar | mesmo `(alvo,região,sinal)` SUGERIU ≥ 3 vezes sem GRAVOU em seguida | 3 sugestões / 1000 leituras | esconder a repetida; mostrar 1 vez com "sugerida N vezes, não aplicada" |
| R4 Tropeço já tratado | região de StallWatch com GRAVOU Mapa K depois do último evento da região | exige ≥ 3 tropeços **novos** depois do GRAVOU (pareia `MIN_EVENTS`, `stall-region.js:16`) | não sugerir de novo antes disso |
| R5 Mesmo local, dois caminhos | região recebe SUGERIU em CURVA_K e MAPA_K em ≤ 1000 leituras | 1 ocorrência | manter só o caminho que o `localLatch` indica; o outro vira "aguardando" |
| R6 Congelar por contagem | região congelada libera quando `leituras_atual − leituras_do_congelamento ≥ N` **e** ≥ 3 regiões RPM×MAP lidas (mesma cobertura da regra do "piorou", `RefinementJournal.kt:23`) | N = 1500 | evento LIBEROU |

Os números são **chutes de partida** (inferido): calibrar pelo replay abaixo antes de ligar. Congelar não impede o dono: o botão "Aplicar mesmo assim" continua (1 toque, regra "sem atrito").

### Validação por replay
- Script novo `tools/sessoes/replay_diario.py` (alimenta `diario.js` com eventos extraídos pelo `tools/sessoes/corpus.py:eventos`) usando: `k_factor_batch_confirmed` como GRAVOU/DESFEZ (`reason` separa Ajuste/Restaurar/Reset); mudança de `MUL_ACT` entre snapshots sem batch correspondente como ECU_MUDOU (as 11 acima); `telemetria.csv.gz` só para contar `leituras`.
- Estender `tests/replay_sugestao_mapa.py` (que já passa o corpus pelo Ledger/MapSuggestionPolicy real) para registrar cada SUGERIU e medir R3/R5.
- Para R1/R2 há pouco dado real: criar fixtures sintéticos em `tests/fixtures/diario/*.json` construídos a partir dos pares reais (ex.: ponto 13 da `002_RICA`, +3275 raw) e **declarar** que são sintéticos.
- Critério: nenhuma regra dispara no baseline "sessão boa" (sem falso positivo) e dispara em todos os fixtures de oscilação; duas passagens dão saída idêntica (determinismo, como `replay_sugestao_mapa.py:6`).
- Pendente fora do repo: capturar 1-2 dias de uso real com o Diário só gravando (sem agir) para ajustar os limiares.

## 5. Onde encaixar sem tocar classes protegidas

- **Lógica em JS puro**: `app/src/main/assets/ui/core/diario.js` (modelo, append, consulta) e `coerencia.js` (R1-R6 como funções puras). Mesmo padrão UMD de `stall-region.js:1-5`, testável em `node --test`.
- **Persistência nativa (recomendado)**: nova classe `P/autocal/DiarioStore.kt` (parecida com `StallWatch.save/load`, `StallWatch.kt:163-192`, usando `AtomicJsonFile`), criada ao lado de `stallWatch` em `TelemetryForegroundService.kt:190-194` e exposta por **método novo** no `AutoCalJavascriptBridge` (`:344,388` mostram o padrão `service.stallWatch.json()`). Motivo: WebView `localStorage` (usado só em `router.js:19`, `autocal-preferencias.js:75`) some em limpeza do navegador e não entra na exportação nem sobrevive com a UI trocada; o nativo é o que já sobrevive a reinício do app.
- **Quem alimenta**: ganchos já existentes, sem editar as protegidas: `RefinementCycle.noteCurveWrite/noteCurveOperation` (`:~60-130`) → GRAVOU; `nativeCurveChanged()` (`:~141`) → ECU_MUDOU (precisa do diff da curva: `journal.expectedCurve()` vs leitura nova, `RefinementJournal.kt:344`); `mapKWritten()` (`:134`) → GRAVOU MAPA_K; `RefinementJournal.close` (`:432`) → DESFECHO; `runAutoRefine` (`TelemetryForegroundService.kt:~1020`) → D1_SOMBRA; `RefinementCycle.proposal` (`:~170`) → SUGERIU. Nenhum desses é `UsbSerialManager`/`ResponseDrivenEcuEngine`/`AutoCalProtocol`/`KFactorManager`/`KWriteManager`/`AutoCalNativeActionManager`.
- **Exportação**: incluir `diario.json` no zip (`SessionRecorder.exportSession`, `:531`) e acrescentar um bloco de texto "Copiar resumo" (item 4 do PLANO).
- **Ponto de decisão**: `coerencia.js` filtra as propostas **depois** de `RefinementCycle.proposal` e **antes** de `refino.js` desenhar (propostas em `autopilot.json`), de modo que congelar nunca mexe em cálculo de Kotlin.

## 6. Testes a escrever primeiro (RED), `node --test` em `tests/ui/`

1. `diario-modelo.test.cjs`: evento sem `leituras`/`quem` é rejeitado; desconhecido fica `null`, nunca 0; ordem por `seq`; corte em 2000 mantendo o último desfecho por região.
2. `diario-sem-relogio.test.cjs` (junto de `tests/test_sem_tempo_como_gatilho.py`): nenhuma regra lê `Date`/`performance.now`; liberar congelamento só por `leituras`.
3. `coerencia-oscilacao.test.cjs`: +,−,+ na mesma região em 2000 leituras → congela; +,+,+ → não congela; região diferente → não.
4. `coerencia-autocal-desfaz.test.cjs`: GRAVOU +3% e ECU_MUDOU −2% em 300 leituras → marca "ECU manda"; −0,5% → ignora.
5. `coerencia-sugestao-repetida.test.cjs`: 3 SUGERIU sem GRAVOU → 1 aviso; com GRAVOU no meio → zera a contagem.
6. `coerencia-tropeco-aplicado.test.cjs`: região do `stall-region.js` com GRAVOU depois → não propõe; 3 tropeços novos → propõe de novo.
7. `coerencia-dois-caminhos.test.cjs`: curva e Mapa K na mesma região → mantém só o do `localLatch`.
8. `diario-replay-corpus.test.cjs`: carrega fixtures e confere saída idêntica em duas passagens e zero disparo no baseline.
9. Kotlin (depois): `DiarioStoreTest.kt` (carregar `.bak`, formato desconhecido gera `loadError` sem apagar) copiando `StallWatchTest.kt`.
10. `coerencia-nao-grava.test.cjs`: módulo nunca chama ponte de escrita (invariante "o app só sugere").
