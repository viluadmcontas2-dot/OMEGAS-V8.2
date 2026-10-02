# Prompt de execução — OMEGAS WU-006 (fechamento: refino + UI/UX + levels + APK)

Cole tudo abaixo no executor (ChatGPT/Codex com acesso ao GitHub e ao Notion).

---

Você é o executor do OMEGAS V8.2. É um app Android para a ECU MP48 / Omega Platinum (GNV). Repositório: `viluadmcontas2-dot/OMEGAS-V8.2`. Branch única: `claude/brave-darwin-wuliyo`. Ponto de partida: HEAD atual dessa branch (inclui o commit `8d6ae63f`, "ciclo fechado do refino"). Não crie outra branch, não abra PR e não apague branches remotas.

## 0. Leitura obrigatória antes de tocar em código

Leia nesta ordem:
1. `AGENTS.md`
2. `PROJECT.md`
3. `STATUS.md`
4. `docs/workunits/OMEGAS-WU-006.md`
5. `docs/evidence/WU-006-refined-replay.md`
6. `docs/reference/progbase/` (README, FORMULAS, ACHADOS-DIRIGIDOS-20261002, LACUNAS)

Abra no Notion os blueprints **CUSTOMROM** e **Omega Dev** (Omega Dev 4.0). Eles são a lei de UI/UX deste trabalho: o dono já pediu várias vezes que sejam seguidos. Antes de mudar UI, extraia deles um checklist de tokens, componentes, hierarquia, estados e linguagem. Grave esse checklist em `docs/product/UX-BLUEPRINT-CHECKLIST.md` e use-o como critério de aceite.

## 1. Regras invioláveis

- Nenhuma escrita automática na ECU. Toda escrita é manual: preparar → revisar → confirmar → ACK → readback. Falha ou divergência nunca é sucesso.
- Não mexer na matemática do motor refinado (`AutoMatchRefinedEngine`, `EquivalenceLedger`, `RefinementJournal`, `RefinementAutopilot`) nem em `tools/autocal_refine/*`. Exceção: bug comprovado por teste que falha antes da correção e passa depois. As constantes vêm de validação cruzada e teste cego, então nada de "achismo".
- Predictor e OBD já foram removidos e não voltam.
- Nunca alegar validação física. Declarar sempre `PHYSICAL_VALIDATION_CLAIMED=false`.
- Gasto monetário zero. GitHub Actions só para a prova final do APK, com o workflow `omegas-preapk-build.yml` em disparo manual. Não disparar build por commit de documentação.
- Não commitar binários proprietários do ProgBase.
- Ordem de prova: gate rápido (`python3 -B tools/run_checks.py`) → testes afetados → suíte Android → lint → APK.

## 2. O que já está pronto (não refazer)

O algoritmo que acompanha o automático e refina a curva:

1. **ECU no automático.** O AutoCal nativo faz o automático 1, 2, 3. O OMEGAS só observa. Em paralelo, o `EquivalenceLedger` junta **pontos próprios**: leituras estáveis de gasolina e GNV no mesmo RPM×MAP, muito mais densas que as 18 bandas da ECU.
2. **ECU parou.** O `RefinementAutopilot` detecta a parada por uma destas condições:
   - `MAX_AUTOMATCH` atingido;
   - AutoCal desligado;
   - aquisição 18/18 sem novo automático.
   A partir daí entra o refino: a curva refinada usa os pontos da ECU mais os nossos (`telemetryTargets`).
3. **Proposta pronta.** O motorista recebe um aviso por notificação, revisa e grava manualmente.
4. **Verificação.** O `RefinementJournal` mede faixa por faixa se o GNV chegou na gasolina e dá um veredito: CONFIRMADA, PASSOU, CURTA ou PIOROU. Ele aprende o ganho por faixa e oferece "Restaurar trecho que piorou" e "Desfazer última gravação".
5. **Estável.** Todas as faixas ficam dentro de ±3%. Aparece "Pode desconectar".

Onde isso aparece hoje:
- UI: `assets/ui/screens/autocal-refine.js` (faixa do piloto e diário), `screens/dashboard.js` (cartão "Calibração") e `core/autocal-api.js` (`equivalence()`).
- Provas locais: gate PASS, paridade Kotlin↔Python OK, JVM 690 OK e `tests/ui/autocal-refine.test.cjs` 9/9.

## 3. Tarefas (nesta ordem, um commit por bloco, cada um com teste)

### 3.1 UI/UX segundo os blueprints CUSTOMROM / Omega Dev

O congelamento de UI está suspenso pelo dono para esta WU. Refinar sem mudar a lógica:

- **Navegação por intenção:** Agora · AutoCal · Ajuste global (Curva K) · Ajuste local (Mapa K) · Sugestões · Ferramentas. O alvo físico é a multimídia landscape 1280×720 com side-nav; também precisa funcionar em 900 px.
- **Linguagem humana no nível 1; números brutos no nível 2** ("Detalhes técnicos"). Traduções obrigatórias:

  | Termo técnico | Na tela |
  |---|---|
  | MUL_ACT | Curva de equivalência |
  | AutoMatch | Automático da ECU |
  | SUPPORTED / BLENDED / HELD | Medido / Transição / Mantido |
  | RESET_GAS | Recoletar GNV |

- **Normalidade compacta, problema ganha espaço:** quando algo falha, a tela diz o que houve, o impacto e o próximo passo.
- **Uma ação primária por tela ou passo.** Semântica de cor:
  - verde: ler/analisar;
  - amarelo: rascunho/recoleta;
  - vermelho: gravar na ECU, sempre com confirmação e readback.
- **Estados obrigatórios em toda tela:** sem ECU, lendo, coletando, dados insuficientes, pronto, gravando, verificado, divergente.
- **Piloto do refino como "linha do tempo" principal do AutoCal.** O motorista deve entender em 2 segundos, sem ler números:
  - em que fase está: ECU no automático → nossos pontos → refino → verificação → estável;
  - o que fazer agora.
  - O cartão "Calibração" do Agora deve ter o mesmo vocabulário.
- **Visual:** aplicar tokens e componentes do blueprint (tipografia, espaçamento, raios, cores, ícones) em `styles*.css`. Remover CSS órfão.
- **Sem porcentagem inventada e sem animação decorativa.** Feedback imediato a cada toque.
- **Testes:**
  - atualizar ou criar testes Node em `tests/ui/` para cada tela tocada;
  - capturar screenshots 1280×720 com Playwright (Chromium do executor), de cada tela, em todos os estados acima, usando o bridge falso;
  - salvar as capturas em `docs/evidence/ui-wu006/` e linkar no WU-006.

### 3.2 Levels (nível do cilindro) e consumo

Fatos (ver `docs/reference/progbase/ACHADOS-DIRIGIDOS-20261002.md` §4):
- Hoje `Mp48TelemetryScale.levelPercentage` faz `(255-raw)*100/255`, igual para qualquer sensor. Isso está errado como "volume".
- O dono relata que o indicador "rola de 50 em 50" em vez de 1 em 1 como no app antigo.
- A ECU tem os parâmetros abaixo, todos em leitura somente:

  | Parâmetro | SC |
  |---|---|
  | TIPO_SENSORE | 36 |
  | RIF_SENSORE (reserva, 1/4, 2/4, 3/4) | 37 |
  | Filtros (rápido 1,0 / lento 0,015) | 276 |
  | LEDs `[3, 12, 37, 62, 87]` | 300 |
  | TANK_VOL (só o índice 0) | 313 |

Fazer:
1. Ler SC 36, 37, 276, 300 e 313[0] pela via read-only existente (mesmo padrão de `AutoCalProtocol`/snapshot). Mostrar em Ferramentas → "Sensor de nível".
2. Novo `LevelEstimator` (Kotlin puro, com testes):
   - aplicar o filtro lento/rápido da ECU (SC 276) sobre `level_raw`;
   - mapear raw → degraus usando as referências RIF_SENSORE do próprio carro (interpolação entre reserva, 1/4, 2/4, 3/4 e cheio), não a fórmula linear fixa;
   - exibir em **degraus da ECU (LEDs)** e numa barra contínua filtrada que anda de 1 em 1;
   - quando as referências não forem lidas, mostrar "proxy" explícito e nunca litros.
3. **Consumo por calibração.** O `EquivalenceLedger` já calcula `gasPerAir` (gás útil por ar admitido, independente do trânsito).
   - Criar o placar por versão de curva: cada gravação confirmada de Curva K ou Mapa K abre uma época com `gasPerAir`, km rodados (GPS), queda de nível filtrada e km por degrau.
   - Mostrar em Agora/Ferramentas: "esta calibração vs a anterior".
   - É só observação, sem nenhuma ação automática.
4. Testes JVM para `LevelEstimator` e para o placar. Testes Node para a tela.

### 3.3 Fechamento

1. `python3 -B tools/run_checks.py` → `QUALITY_GATE_FAST=PASS`.
2. Disparar **uma vez** o workflow `omegas-preapk-build.yml` na ref `claude/brave-darwin-wuliyo` (`testDebugUnitTest lintDebug assembleDebug`). Se falhar, corrigir a causa raiz e disparar de novo. Nunca pular, desabilitar nem colocar teste em quarentena.
3. Registrar SHA, run id, nome do artifact e digest em `STATUS.md` e `docs/evidence/`, com `PHYSICAL_VALIDATION_CLAIMED=false`.
4. Atualizar `docs/workunits/OMEGAS-WU-006.md`, com o que foi feito e o que ficou pendente.
5. Responder ao dono em português simples, no máximo 10 linhas:
   - o que mudou na tela;
   - como o level passou a funcionar;
   - link do APK;
   - o que só a rodagem real vai provar.

## 4. Critério de pronto

- O motorista conecta o cabo e dirige. O app diz sozinho em que fase está, avisa quando a curva refinada está pronta, verifica depois de gravar e diz "pode desconectar" quando GNV ≈ gasolina (±3%).
- Telas conferidas contra o checklist do blueprint, com screenshots.
- O level anda de 1 em 1, filtrado, com degraus iguais aos LEDs da ECU.
- O placar de consumo compara calibrações.
- APK verde com evidência registrada.
