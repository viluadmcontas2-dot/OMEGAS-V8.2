# OMEGAS-WU-006 — AutoCal: Equivalência Refinada GNV = gasolina

- Objetivo humano: fazer o tempo de injeção no GNV igualar o da gasolina, a partir da calibração automática nativa da ECU, com curvas sem anomalias (sem dente de serra, sem trancos).
- Branch: `claude/brave-darwin-wuliyo`
- Decisões do proprietário (2026-10-02):
  - o AutoCal é o centro do produto;
  - o Predictor sai da navegação (o código fica);
  - o Ajuste local continua;
  - a interface pode evoluir para uma navegação por intenção, seguindo os blueprints CUSTOMROM e Omega Dev;
  - o ganho de correção é proporcional à evidência, com limite de ±15% por execução;
  - a aplicação pode ser "meio automática", com um toque, desde que a revisão e a confirmação humanas continuem existindo.
- Política de custo: zero monetário. A prova Android/APK fica para um CI seletivo.
- Limite físico: `SIMULATED_ECU_ONLY=true`, `PHYSICAL_VALIDATION_CLAIMED=false`.

## Evidência que definiu o problema

Fixtures em `fixtures/autocal/real/*.json.gz`, extraídas das sessões do Drive por `tools/autocal_refine/extract_session.py`:

| Fixture | Sessão (local, UTC-3) | Papel |
|---|---|---|
| `ref_2026-10-01_1719` | 01/10 17:19 | Curva "boa" do proprietário (melhor consumo), mas com trancos no GNV |
| `automatch_2026-10-01_1301` | 01/10 13:01 | AutoMatch nativo executado às 16:10:32Z sobre MUL_ACT=1,0 |
| `gnv_only_2026-09-30_0931` | 30/09 09:31 | Duas aquisições de GNV independentes, antes e depois do RESET_GAS |

1. **O AutoMatch nativo é ganho total, ponto a ponto, sem suavização.**
   - O resultado da ECU às 16:10:32Z bate com `clamp(T_gnv(P_gas(t))/t, 0,75; 1,20)` calculado pelas curvas RV, com diferença de no máximo 10 LSB nos índices 4–29 (`native_automatch_replica`).
   - Esse cálculo usou bandas com 1 ou 2 amostras. Isso derruba a hipótese anterior do app (ganho 1/3, limite de ±5%, suavização de 3 pontos), que ficou registrada como inferida em `AutoMatchV5Engine`.
   - A cabeça da curva (índices 0–3, valor 0,80) segue uma regra ainda não identificada.
2. **O tranco é um ciclo-limite do laço fechado.**
   - Na sessão de referência, 28 de 35 pares estáveis entre 8,0 e 8,5 ms alternam entre 8,0 e 8,9 ms a cada leitura.
   - A razão gás/gasolina oscila entre 2,2 e 2,6. Esse fator de 1,18 é o mesmo do degrau de K entre os índices 15 e 17.
   - A elasticidade |d ln K / d ln t| chega a 1,92 nesse trecho.
3. **Uma banda ruim vira buraco na curva.**
   - Na banda 9, a razão gás/gasolina é 0,824, contra cerca de 1,02 a 1,10 nas vizinhas. O K no índice 9 (5,0 ms) cai para 0,865.

## Solução

`AutoMatchRefinedEngine` (Kotlin puro) tem paridade comprovada com `tools/autocal_refine/refined_oracle.py`. O processo:

1. Evidência por banda: buffers nativos com peso `min(n, 6)/6`; banda madura tem n ≥ 3, como no `CALIBRATION_VAL_1`.
2. Ajuste isotônico robusto de T(MAP) para cada combustível. Uma banda que viola a monotonicidade e foge das vizinhas é descartada.
3. Equivalência exata: `K_alvo(T_p) = K_atual(T_g) · T_g / T_p`.
4. Ajuste Whittaker robusto (Tukey/IRLS) em ln K sobre u = ln t, com λ = 0,1 escolhido por varredura. O peso do K atual cai à medida que a evidência aumenta, o que dá o ganho proporcional sem degraus.
5. Trava de coerência por projeções alternadas:
   - passo de no máximo ±15% por execução;
   - |Δ ln K / Δ ln t| ≤ 0,35.
   Se a curva atual estiver serrilhada demais para caber nos dois limites, o limite de inclinação é relaxado até o mínimo viável e a análise marca `needsAnotherPass`.
6. Pontos sem evidência e sem anomalia (HELD) mantêm exatamente o valor gravado.
7. Falha fechada: com menos de 4 faixas comuns maduras, o modo é POLISH, que só corrige coerência e nunca inventa equivalência.

**Simulador de laço fechado** (`tools/autocal_refine/closed_loop_sim.py`):
- com a curva de referência, reproduz o ciclo-limite (8,05↔8,61 ms);
- com a curva refinada, não há oscilação em toda a faixa de 1,5 a 12 ms, com margem de ganho de cerca de 2×, nos modelos sem atraso e com 1 frame de atraso.

**Produto:**
- A aba **AutoCal** passa a ser de primeiro nível, com um fluxo guiado: Gasolina → GNV → Revisar → Gravar.
- Um gráfico mostra a curva atual e a refinada, com a origem de cada ponto.
- Indicadores: risco de tranco, serrilhado, maior degrau e evidência.
- O botão único "Revisar e aplicar" pré-seleciona os pontos justificados. A gravação usa o caminho existente: relê a curva, confere que nada mudou, grava, faz ACK e readback.
- Se a curva da ECU mudou desde o snapshot, a gravação é abortada.
- Depois de gravar, aparecem "Restaurar curva anterior" e a recomendação de recoletar o GNV.

## Provas locais

- `python3 -B tools/run_checks.py` → `QUALITY_GATE_FAST=PASS`. Inclui o oráculo, o teste de paridade (pulado quando não há kotlinc) e `tests/ui/autocal-refine.test.cjs`.
- `KOTLINC=… python3 -B tests/test_refined_autocal_kotlin_parity.py` → OK. Os 30 snapshots reais dão diferença de no máximo 1 LSB, com modo e origens idênticos.
- `AutoMatchRefinedEngineTest` (JUnit 4) → 4/4 OK, compilado com kotlinc 2.0.21 e org.json 20240303 fora do Gradle. O Android SDK não está disponível neste executor porque o proxy bloqueia dl.google.com.
- Replay completo: `docs/evidence/WU-006-refined-replay.md`.

## Pendente

- Prova Android (`testDebugUnitTest lintDebug assembleDebug`) num único CI seletivo, para compilar `AutoCalJavascriptBridge` e a suíte inteira.
- Validação física: gravar a curva refinada, recoletar o GNV e medir o resíduo e a ausência de trancos.
- `tests/ui/autocal-cockpit.test.cjs` já falhava na `main` antes desta WU e não faz parte do gate.
