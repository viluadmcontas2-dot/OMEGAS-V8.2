# Prompt — Levar o refino OMEGAS para a OmegasPlatina (substitui WU-007 e anteriores)

## Por que este prompt existe

O trabalho das WU-006 e WU-007 foi feito na branch `claude/brave-darwin-wuliyo`, que **não descende da `OmegasPlatina`**:
- a base comum é `305bb4ed`, de 12/08;
- a Platina tem 1.146 commits que essa branch não tem.

O APK gerado ali saiu com uma interface antiga, sem didática e com a rolagem horizontal quebrada. **A referência do produto é a `OmegasPlatina`.** A interface dela é a que o dono aprova.

## Missão

Partir da `OmegasPlatina` e **acrescentar** a ela o "cérebro" do refino, mais **uma** aba nova no estilo visual da própria Platina. **Não redesenhe a Platina.**

- Repositório: `viluadmcontas2-dot/OMEGAS-V8.2`.
- Crie a branch **`work/platina-refino`** a partir do HEAD atual de `origin/OmegasPlatina` (`b185e80a` ou mais novo).
- Trabalhe só nela. Não mexa na `OmegasPlatina` nem em `claude/brave-darwin-wuliyo`, e não abra PR.
- A branch `claude/brave-darwin-wuliyo` serve **só como fonte** dos arquivos a portar (§2). **Não traga a UI dela.**

## Postura

Aja como o CEO técnico do produto: profundidade, criatividade e rigor.

1. **Antes de portar, leia a Platina fio a fio:**
   - `AGENTS.md`, `PROJECT.md`, `STATUS.md`;
   - `app/src/main/assets/ui/core/router.js` (ROUTES = dashboard, learning, predictor, map, curve, autocal, obd, suggestions, tools), `index.html`, `app.js`;
   - `screens/autocal-cockpit.js`, o CSS;
   - `autocal/AutoCalJavascriptBridge.kt`, `NativeAutoCalMonitor.kt`, `AutoMatchSnapshotAnalysis.kt`, `AutoMatchKFactorDraft.kt`;
   - `service/TelemetryForegroundService.kt`, `ecu/AutoCalProtocol.kt`;
   - `tools/run_checks.py` e os workflows da Platina (`.github/workflows/`).

   Registre em `docs/workunits/PLATINA-REFINO.md` o mapa de cada ponto de integração: arquivo, função, thread e frequência.
2. **Antes de copiar, compare.** Cada arquivo da lista §2 depende de APIs (`AutoCalScale`, `AutoCalFieldStatus`, `AutoCalProtocol`, `AutoCalSnapshotBuilder`, os campos do snapshot). Verifique se essas APIs existem **iguais** na Platina. Onde forem diferentes, **adapte o arquivo portado à Platina**, nunca o contrário.
3. **Causa raiz, testes e honestidade.** Não declare pronto sem teste. Separe o que está provado, o que é hipótese e o que só o carro prova.

## 1. Foco e regras invioláveis

- **Foco:** igualar o tempo de injeção do GNV ao da gasolina.
  - A base é MAP × Tpet, igual à ECU (18 bandas).
  - O refino do refino é RPM × MAP.
  - Depois vem o ciclo fechado.
  - O resto é bônus.
- **ECU:** nunca gravar automaticamente. Use o fluxo de escrita **da Platina**: preparar → revisar → confirmar → ACK → readback.
- **Matemática congelada:** não altere constantes nem fórmulas dos arquivos portados.
- **Interface da Platina preservada:** não mude navegação, telas, CSS nem textos existentes, a não ser o mínimo para registrar a aba nova. Predictor e OBD **continuam** como estão na Platina.
- **Desempenho na multimídia:**
  - nada de polling por tela (use o Scheduler/Store da Platina);
  - nada O(n²);
  - nada pesado na thread de UI ou no bridge síncrono.
- **Notificações:** checar `POST_NOTIFICATIONS` (Android 13+) antes de notificar, senão o lint falha.
- **Custo zero:** CI só no fechamento, com o workflow de APK que a Platina já usa (provavelmente `verde-apk-now.yml`; confira).
- **Parar é parar:** se o dono ou o Claude mandar parar, não faça mais push.

## 2. O que portar de `claude/brave-darwin-wuliyo`

Use `git show origin/claude/brave-darwin-wuliyo:<caminho>`.

**Kotlin puro (o cérebro):**
- `app/src/main/java/com/omegas/prohub/autocal/AutoMatchRefinedEngine.kt`: motor da curva refinada (isotônica, Whittaker robusto, trava ±15% e |Δln K/Δln t| ≤ 0,35, falha fechada).
- `app/src/main/java/com/omegas/prohub/autocal/EquivalenceLedger.kt`: os nossos pontos.
  - Leitura estável de 3 quadros; pares RPM × MAP com busca em grade O(n).
  - Índice por faixa; `gasPerAir`.
  - Bandas densas MAP × Tpet (`denseBandsJson`), se existir.
- `app/src/main/java/com/omegas/prohub/autocal/RefinementJournal.kt`: diário com veredito por faixa, ganho aprendido e `restorePoints`.
- `app/src/main/java/com/omegas/prohub/autocal/RefinementAutopilot.kt`: as fases do piloto (ECU no automático → nossos pontos → proposta → verificação → estável).

**Testes (portar e fazer passar na Platina):**
- `AutoMatchRefinedEngineTest.kt`, `EquivalenceLedgerTest.kt`, `RefinementJournalTest.kt`, `RefinementAutopilotTest.kt`;
- `tools/autocal_refine/*`, `tests/test_refined_autocal_oracle.py`, `tests/test_refined_autocal_kotlin_parity.py`;
- registrar os novos testes no `tools/run_checks.py` da Platina.

**Integrações (refazer na Platina, olhando a branch fonte só como referência):**
- `AutoMatchSnapshotAnalysis.analyzeRefined(snapshot, telemetryPairs, pointGainScale)`.
- `AutoMatchKFactorDraft.createRefined(analysis)`: prefixo `AMR`.
- **Bridge:** `getRefinedAnalysis()` (memoizado), `createRefinedDraft()`, `getEquivalence()`. Os contratos de campos estão no §2.3 de `docs/handoff/PROMPT-GPT-WU-006-FINAL.md` na branch fonte.
- **Serviço:**
  - alimentar `equivalence.accept(Frame)` a cada quadro de telemetria;
  - Mapa K confirmado → `resetGas` + `journal.interrupt`;
  - Curva K confirmada → `journal.recordCurveWrite` + `resetGas` + `adoptCurve`;
  - AutoMatch nativo → `resetGas` + `interrupt`;
  - no health tick: `journal.evaluate(index)` + `autopilot.observe(...)` + notificação com permissão;
  - `onDestroy` → `flush`.
- `NativeAutoCalMonitor.autoMatchProgressJson()`: contador vivo, `maxAutomatch` e `autoCalEnabled`.

**Não portar:**
- nenhuma tela, CSS ou JS de UI da branch fonte;
- level, placar ou ZIP de sessão. Isso fica para depois, quando o refino estiver aprovado na Platina.

## 3. A aba nova: "Refino", no estilo da Platina

1. **Visual:** é um destino novo na navegação lateral da Platina, logo abaixo de AutoCal. Use **os mesmos componentes, cores, fontes, espaçamentos e padrões** da tela AutoCal da Platina (o cockpit com o gráfico "Curva de aquisição · Gasolina × GNV").
2. **Conteúdo, numa tela 1280×720 sem rolagem vertical e com rolagem horizontal funcionando onde já houver:**
   - **linha do tempo do piloto:** ECU no automático → Nossos pontos → Refino → Verificação → Estável, com uma frase grande e o próximo passo;
   - **gráfico "Nossa curva":** **o mesmo gráfico do AutoCal da Platina** (X = Petrol Inj. ms, Y = MAP bar, gasolina e GNV, os pontos da ECU), com as nossas bandas densas por cima em tamanho menor. Reaproveite o componente de gráfico da Platina; não crie outro;
   - **uma ação principal** conforme a fase:

     | Fase | Ação |
     |---|---|
     | Proposta pronta | "Revisar e gravar" |
     | Trecho piorou | "Restaurar trecho que piorou" |
     | Estável | "✓ Estável · pode desconectar" |

     A revisão mostra a Curva K atual × refinada, ponto a ponto, e grava pelo fluxo de escrita da Platina (Ajuste global / Curva K), com stale check e readback;
   - "Resultado da última gravação" e "Detalhes técnicos" em painéis fechados.
3. **Linguagem:** didática e humana (blueprints Notion CUSTOMROM / Omega Dev 4.0 / Car Info Next). Sem jargão no primeiro nível.
4. **O resto da Platina não muda.** No máximo, uma linha no Agora com a fase do piloto, se couber sem quebrar nada.

## 4. Verificação e entrega

1. **Gate:** o gate rápido da Platina (`python3 -B tools/run_checks.py`) tem que passar, com os testes novos.
2. **Testes:** JVM dos 4 arquivos portados e paridade Kotlin↔Python, se houver kotlinc.
3. **Screenshots:** capture 1280×720 da aba Refino em cada fase e da tela AutoCal da Platina **sem alteração visual**. Salve em `docs/evidence/platina-refino/`.
4. **APK:** **um** build pelo workflow de APK da Platina na branch `work/platina-refino`. Mesmo erro duas vezes → pare e reporte.
5. **Registro:** registre SHA, run, artifact e digest em `STATUS.md`.
6. **Resposta ao dono** em português simples, no máximo 10 linhas:
   - o que entrou;
   - o que ficou igual à Platina;
   - o link do APK;
   - o que só o carro prova.

## 5. Onde você pode falhar

- **Interface da branch fonte:** trazer qualquer pedaço da UI de `claude/brave-darwin-wuliyo`. **Proibido**; foi isso que deixou o app horrível.
- **Arquivos inteiros:** copiar `AutoCalJavascriptBridge.kt`, `TelemetryForegroundService.kt` ou `NativeAutoCalMonitor.kt` inteiros da branch fonte. **Proibido**: os da Platina são diferentes (o monitor da Platina é bem maior). Acrescente **só** os métodos e ganchos necessários.
- **Nomes de campo:** um nome de campo diferente entre o JSON do bridge e o JS. Use os contratos do §2.3 citado.
- **Branch base:** esquecer de partir da `OmegasPlatina`. Confira com `git merge-base --is-ancestor origin/OmegasPlatina HEAD` antes do primeiro push e antes do build.
