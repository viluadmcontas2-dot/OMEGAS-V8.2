# Grafo do REFINO — fluxo real, lacunas e plano (2026-10-20)

Fonte: leitura do código (graphify query + leitura direta). Somente leitura; nada foi alterado.
Caminhos curtos: `K/` = `app/src/main/java/com/omegas/prohub/autocal/`, `UI/` = `app/src/main/assets/ui/`, `SVC` = `app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt`.

## 1. Mapa real do fluxo hoje

```
telemetria MP48 (SVC) ──► EquivalenceLedger.accept ──► filtros ──► pares GNV×gasolina
        │                                                         │
        └─► StallWatch.accept (apagou/quase) ── só aviso ─────────┤ (não limpa pares)
                                                                  ▼
             RefinementCycle.observe ─► proposta Curva K (AutoMatchRefinedEngine)
                                    └─► LocalMapPlanner.plan(EquivalenceAttribution) ─► proposta Mapa K
                                                                  ▼
             RefinementAutopilot.observe ─► fase + 1 botão ─► refino.js (tela)
                                                                  ▼
             gravação (operador toca) Curva K / Mapa K (map-fix.js) ─► ACK + readback
                                                                  ▼
             RefinementJournal.evaluate ─► MELHOROU / PIOROU / MEDINDO ─► restaurar
```

| Nó | O que faz | Onde |
|---|---|---|
| Entrada | Cada quadro de telemetria vira `EquivalenceLedger.Frame` (combustível, RPM, MAP, t_gasolina, t_GNV, motor quente). **Não vem da tela AutoCal**: vem direto do serviço. | `SVC:1189-1203` |
| Entrada (apagão) | O mesmo quadro alimenta `StallWatch`; evento vira log e `engine_stall` na sessão. | `SVC:1205-1215`, `K/StallWatch.kt:60-99` |
| Filtro 1 | Descarta combustível ≠ GASOLINA/GNV, RPM ≤ 0, MAP ≤ 0, t_gasolina < 1 ms. | `K/EquivalenceLedger.kt:275` |
| Filtro 2 (transiente) | Janela de 3 quadros em ≤ 1,2 s com variação ≤ 150 rpm e ≤ 0,03 bar; troca de combustível zera a janela. | `EquivalenceLedger.kt:34-36, 283, 301-311` |
| Filtro 3 (frio) | Gasolina com motor frio não vira referência; célula de gasolina congela após 8 leituras. | `EquivalenceLedger.kt:67, 289`; temperatura em `SVC:1198` |
| Teto por célula | 30 leituras por célula 150 rpm × 0,02 bar. | `EquivalenceLedger.kt:41` |
| Pareamento | GNV × gasolina no mesmo RPM×MAP (±150 rpm, ±0,02 bar). | `EquivalenceLedger.kt:37-38, 455+` |
| Global × local | Agrupa por coluna de RPM do Mapa K × faixa de t_gasolina; LOCAL só com ≥8 pares, ≥3 visitas, outra coluna no mesmo t, visitas estáveis. | `K/EquivalenceAttribution.kt:32-43, 83-90`; `EquivalenceLedger.kt:419-437` |
| Proposta Mapa K | Resíduo local em %, no máx ±15% por gravação. | `K/LocalMapPlanner.kt:22-47` |
| Proposta Curva K | Motor `AutoMatchRefinedEngine` sobre os pares; clamp `MIN_FACTOR 0,60..MAX_FACTOR` (≈4,0). | `K/RefinementCycle.kt:180-193`; `K/AutoMatchRefinedEngine.kt:39-40, 234` |
| Decisão de fase | SEM_ECU → ECU_TRABALHANDO → RESTAURAR_TRECHO → PROPOSTA_PRONTA → VERIFICANDO → ESTAVEL / COLETANDO_NOSSOS; faixas com histerese 5%/4%, ≥8 leituras por faixa, ≥3 faixas medidas. | `K/RefinementAutopilot.kt:58-59, 160-230` |
| "ECU terminou" | Só evidência: AutoCal desligado, MAX_AUTOMATCH, ou ≥4 zonas adquiridas em cada combustível. | `RefinementAutopilot.kt:111-140` |
| Tela | Só desenha o que o piloto manda; Mapa K: apagão primeiro (stall-region), depois diferença por RPM. | `UI/screens/refino.js:12, 371-400, 598-599` |
| Sugestão de apagão | ≥3 eventos, passo 5% (10% com ≥6), máx 15%, máx 3 colunas. | `UI/core/stall-region.js:17-22, 67-83` |
| Gravação Mapa K | `MapFix` lê mapa, monta prévia; porta de gravação bloqueia com a ECU no automático. | `UI/core/map-fix.js`, `refino.js:237, 358, 373, 408` |
| Pós-gravação | Pares GNV do trecho afetado são invalidados (curva) ou zerados (Mapa K / AutoMatch nativo). | `RefinementCycle.kt:85-141`; `EquivalenceLedger.kt:314-362` |
| Prova | Diário julga cada ponto (MELHOROU/PIOROU) por quantidade de leituras (100 parcial, 500 desiste); PIOROU vira "Restaurar". | `K/RefinementJournal.kt:46-84, 358-531` |
| Teto 1,8 | Garantido **na escrita** (`KFactorManager.kt:297, 345`), não no motor de proposta. | `ecu/KFactorProtocol.kt:19-20` |

## 2. Lacunas: defesas do AutoCal que o Refino NÃO tem

| Defesa | AutoCal | Refino hoje | Prova |
|---|---|---|---|
| RPM mínimo para aprender (`minRpmLearn`, padrão 1000, ajustável 0–3000) | Sim: `UI/core/low-rpm-filter.js:6-44` + `autocal-cockpit.js:635-636, 1047-1051` | **Não.** Só exige RPM > 0. Marcha lenta entra como par. | `EquivalenceLedger.kt:275` (só `rpm <= 0`) |
| Carro parado (lenta com marcha/ar ligado) | Coberto pelo RPM mínimo | **Não.** Não há velocidade nem RPM mínimo; o teste `RefinementCycleTest:700` até confirma que "a lenta entra na curva". | `EquivalenceLedger.kt:52` (`IDLE_DIAGNOSTIC_RPM` só para diagnóstico) |
| Transiente | Contador da ECU | **Sim, parcial**: janela de 3 quadros. Falta olhar o t_gasolina (pode variar com RPM/MAP estáveis, ex.: corte/enriquecimento). | `EquivalenceLedger.kt:301-311` (não mede espalhamento de `petrolMs`) |
| Quase-apagão / apagão | `AntiStallSessionAnalysis` (conselheiro: queda ≥175 rpm em 2 leituras, sem recuperar) | **Só aviso.** `StallWatch` detecta e manda ao Mapa K, mas **não remove** os pares GNV dos ~4 s anteriores (os mais "ruins"). | `SVC:1205-1215` não chama `invalidateGas`; `StallWatch.kt:30` (LOOKBACK 4 s já guardado) |
| Pós-queda / troca de combustível | — | **Parcial.** Troca zera só a janela de 3 quadros; os primeiros quadros GNV após a troca (purga, tubo cheio) podem virar par. | `EquivalenceLedger.kt:283` |
| GNV frio | Temperatura mínima do AutoCal | **Não para GNV**: `engineHot` só filtra gasolina. | `EquivalenceLedger.kt:289` |
| Teto 1,8 na proposta | `AutoMatchKFactorDraft`/`V5Engine` usam `MAX_WRITE_FACTOR` | **Não.** O motor do Refino propõe até ≈4,0 e a escrita recusa depois → proposta que nunca grava. | `AutoMatchRefinedEngine.kt:40, 234` × `AutoMatchV5Engine.kt:28` |

## 3. Melhorias priorizadas (todas reaproveitam código existente)

1. **RPM mínimo no Ledger (mesmo `minRpmLearn` do AutoCal)**
   - Efeito: tira a marcha lenta (onde o GNV oscila e o apagão mora) dos pares; curva mais limpa, menos propostas falsas no começo da curva.
   - Tocar: `EquivalenceLedger.accept` (linha 275, condição extra com o valor), `SVC:1189` passa o valor; ler de `AutoCalPreferenceCore` (mesma preferência, um único lugar).
   - Prova: teste Kotlin em `EquivalenceLedgerTest` (quadros a 800 rpm não viram `Obs`; 1200 sim; 0 = desligado) + ajustar `RefinementCycleTest:700` (hoje afirma o contrário: é decisão do dono, não remendo).
   - Risco: médio. Lenta some da curva → os pontos baixos da curva ficam "—" (correto, desconhecido). Valor 0 mantém o comportamento antigo.

2. **Apagão/quase-apagão invalida os pares anteriores**
   - Efeito: ponto que precede um apagão deixa de "ensinar" a curva; evita propor o mesmo erro que causou o apagão.
   - Tocar: no `SVC:1205-1215`, quando `stallWatch.accept` devolve evento, chamar o existente `equivalence.invalidateGas(motivo, faixa t_gasolina do evento)` (`EquivalenceLedger.kt:333`), com faixa vinda de `StallWatch.record` (`petrolMs`, lookback já existe).
   - Prova: `StallWatchTest` + teste de ciclo: injeta GNV estável, depois queda <300 rpm → pares da faixa somem; fora da faixa ficam.
   - Risco: baixo (só remove evidência). Cuidado: não usar tempo como decisão — a janela de 4 s é só "quais quadros", igual ao StallWatch hoje.

3. **Teto 1,8 no motor de proposta**
   - Efeito: nenhuma proposta "impossível"; o operador nunca vê ponto que a escrita recusaria. Coerência com o invariante.
   - Tocar: `AutoMatchRefinedEngine.kt:40` → `KFactorProtocol.MAX_WRITE_FACTOR` (mesmo padrão de `AutoMatchV5Engine.kt:28`).
   - Prova: teste do motor com pares que pedem 2,2 → proposta 1,8 marcada `limited`.
   - Risco: baixo. Atenção: `AutoMatchRefinedEngine` não está na lista de classes de comando proibidas, mas é ciência crítica — RED/GREEN obrigatório.

4. **Transiente também pelo t_gasolina**
   - Efeito: descarta janelas onde a gasolina varia >X% (corte, enriquecimento de aceleração), mesmo com RPM/MAP parados.
   - Tocar: `EquivalenceLedger.stableObservation` (`:301-311`), mais uma condição de espalhamento.
   - Prova: teste com 3 quadros iguais em RPM/MAP e `petrolMs` 3,0/3,0/4,5 → sem `Obs`.
   - Risco: baixo-médio (menos pares; calibrar X com `tests/fixtures/sessoes-uteis/` pelo `tools/sessoes/corpus.py` antes de fixar).

5. **Descarte pós-troca de combustível (por leituras, não por relógio)**
   - Efeito: ignora os N primeiros quadros GNV após troca/corte; evita par com tubo vazio.
   - Tocar: `EquivalenceLedger.accept` (`:283`), contador de quadros desde a troca.
   - Prova: teste com troca gasolina→GNV: os N primeiros não geram `Obs`.
   - Risco: baixo. N por evidência do corpus, nunca em segundos (regra "só evidência").

6. **Conselheiro anti-apagão no cartão do Refino**
   - Efeito: o Refino passa a dizer "na sua sessão, X quedas fortes sem recuperar em RPM Y" antes de propor Mapa K, igual ao AutoCal; prioriza a região certa.
   - Tocar: expor o resultado de `AntiStallSessionAnalysis` (`K/AntiStallSessionAnalysis.kt:93-124`, hoje só no `SessionRecorder.kt:157-686`) no JSON que `refino.js:375-381` já lê para `stall-region`.
   - Prova: `tests/ui/` com `eq.stalls` + tabela → frase aparece; nada grava.
   - Risco: baixo (só informação). Não duplicar texto: reutilizar `describeStalls` (`refino.js:64`).

7. **Priorizar a próxima coleta pelas faixas faltantes ("aonde dirigir")**
   - Efeito: acelera a calibração: o piloto já calcula `bandsMissing` (`RefinementAutopilot.kt:155-190`); a tela pode mostrar "falta 2,5–3,5 ms (subida leve)" em vez de "coletando".
   - Tocar: `nextStep` (`RefinementAutopilot.kt:410+`) usando `missing` que já recebe.
   - Prova: `RefinementAutopilotTest` (fase COLETANDO_NOSSOS com faixa faltante → frase cita a faixa).
   - Risco: baixo.

## 4. O que NÃO vale fazer

- **Gravar sozinho** quando a proposta estiver "pronta" (fere o invariante; D1 só sugere).
- **Prazo em minutos** para "verificou" ou "estável" — o diário já usa contagem (100/500 leituras); não regredir.
- **Copiar o `LowRpmFilter` JS para o Refino**: ele apaga contadores da ECU; no Refino o filtro tem de ficar no Kotlin (Ledger), onde nascem os pares. Uma regra, um valor, dois executores.
- **Afrouxar `MIN_BAND_SAMPLES`/histerese para "andar mais rápido"**: acelera propostas ruins e gera ciclo grava/restaura.
- **Usar velocidade do GPS como "carro parado"**: dado ausente em muito uso; o RPM mínimo já resolve com dado que sempre existe.
- **Passo maior que 15% no Mapa K** ou Curva K perto de 1,8: o próprio dono resolveu correção grande indo ao Mapa K (~220).

## 5. Ordem recomendada

1. **(1ª) Item 3 — teto 1,8 no motor**: uma linha, risco mínimo, fecha uma violação real do invariante.
2. Item 1 — RPM mínimo no Ledger (maior ganho de qualidade).
3. Item 2 — apagão invalida pares.
4. Itens 4 e 5 — transiente pelo t_gasolina e pós-troca (calibrar no corpus real antes).
5. Item 7 — "aonde dirigir" (velocidade de coleta).
6. Item 6 — conselheiro no cartão.

Cada item: RED → GREEN → `node --test tests/ui/*.test.cjs` + `sh ./gradlew testDebugUnitTest` dos relacionados → replay do corpus `sessoes-uteis` para medir quantos pares saem e se a proposta muda.
