# OMEGAS 8.2 — Programa Mestre de Fidelidade e Experiência Premium Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Em este projeto, respeitar GitHub remoto como autoridade; nunca gerar APK automaticamente.

**Goal:** Fechar 100% das lacunas **do escopo funcional comprovado e autorizado** do OMEGAS 8.2, com comunicação segura e fiel à central MP48, UX intuitiva premium e interface para qualquer proporção suportada, evidenciada de ponta a ponta.

**Architecture:** Preservar Kotlin/serviço/USB/scheduler como únicos proprietários de protocolo e ciência; WebView JS/CSS só projeta estado e captura intenção humana. Construir uma matriz auditável de ações e capacidade por perfil real da central; permitir no produto somente funções demonstradas, sem inventar escritores. Executar ondas pequenas (uma falha/contrato por PR revisável), manter PR #195 isolado e reconciliar concorrência antes de editar.

**Tech Stack:** Android Kotlin, WebView HTML/CSS/JS, Node Test + Playwright/Chromium, testes JVM/Gradle, Python/Unittest, GitHub Actions, graphify, Ghidra/Unicorn/Portmon e emulação offline **somente autorizada**, corpus versionado sem binário privado.

**Spec:** `docs/superpowers/specs/2026-11-17-omegas-v82-design.md`

## Global Constraints

- Branch canônica: `OMEGASCANONICO`; ver HEAD remoto antes de **cada** escrita, sem merge automático. Autorização deste plano = desenhar e investigar; mudar UI existente apenas após aprovação do conjunto dos mockups.
- Cinco áreas: AutoCal, Refino, Ajuste local, Ajuste global, Ferramentas; Sessões/Diagnóstico em Ferramentas. Barra viva sempre visível; vocabulário leigo; 1 toque = intenção, sem “confirmar de novo” desnecessariamente.
- Nenhuma escrita de mapa/curva automática; ACK não é readback; “Gravado” somente com leitura de volta igual; erro de transporte ≠ erro da central; desconhecido = —.
- Teto da curva K em escrita manual 1,8, regras de mapa/curva e resets conforme `AGENTS.md` e `docs/REGRAS-DO-DONO.md`; não reabrir limites comprovados. Never add RESET_ALL público, flash, SetByAddress, SetDuty, firmware ou diagnóstico de atuadores.
- Decisões científicas por evidência/eventos, não pelo relógio; nível de gás RAW até calibração física; AutoCal sem antes×depois, sem recriar AutoMatch escritor.
- Testes pesados somente CI remoto; sem apressar APK. Não chamar de PASS um teste SKIP, mock sem fronteira real ou relatório antigo. Não operar central física sem o dono.
- Traço da origem: OBS-FIO/OBS-COD/DERIVADO/INFERIDO/DESCONHECIDO; “original tinha botão” **não prova** que a ECU do perfil o aceita.
- Todos os resultados de análise, lacuna, caso de teste, decisão e bloqueio ganham ligação no grafo de PRODUÇÃO e, quando cabível, índice de PESQUISA; não regenerar catálogos editando JSON gerado à mão.
- Ao encontrar evento crítico não recuperável (ação irreversível, falha física, sem evidência), bloquear a capacidade e registrar bloqueio; não substituir por comportamento especulativo.

## Review Focus — cinco condições que os testes devem simular

1. USB desconecta ou sessão muda após o ACK mas antes do readback: operação deve ficar “resultado incerto”, sem gravar novamente nem reusar estado da geração anterior (Tarefas 5 e 7).
2. NAK CA com máscara 0x10 e código não idêntico a 0x10, busy 0x96 e resposta de tamanho inválido: classificação, retry e proibição de escrita corretos (Tarefa 6).
3. Operador vê uma recomendação sem suporte físico comprovado: CTA de escrita deve faltar; mensagem diferencia “não demonstrado” de “não suportado” e de “cabo desconectado” (Tarefas 4 e 11).
4. Girar de 16:9 para 9:16 durante gráfico com proposta não gravada: manter seleção, campos, ações visíveis e estado, sem scroll horizontal global (Tarefas 2 e 14).
5. Ferramentas carrega com ponte auxiliar ausente, bateria fraca, Android volta do seletor de exportação ou WebView recria: nunca tela vazia, feedback verdadeiro e continuidade da sessão (Tarefas 1, 15 e 17).

## Estratégia de execução

A ordem abaixo é contratual: **C0 estabilizar → C1 definir capacidades → C2 protocolo e writer → C3 prova de original → C4 UX aprovada → C5 inteligência → C6 realidade física/release**. P0 bloqueia qualquer nova escrita; trabalho de pesquisa read-only e planejamento visual pode ocorrer em paralelo apenas com fontes e arquivos disjuntos.

Cada tarefa: formular hipótese a partir de dados → escrever teste RED que a detecta → corrigir mínimo possível → GREEN focado → revisão de diff → CI remota do SHA → recibos/evidência → commit pequeno. Se o teste não for executável, a tarefa é `BLOCKED: razão, dependência e evidência necessária`, não concluída.

---

## Fase C0 — Baseline e CI honesto

### Task 1: Eliminar regressão de Ferramentas no PR #195

**Files**
- Modify: `app/src/main/assets/ui/app.js`
- Test: `tests/ui/ferramentas-webview-regression.test.cjs`
- Evidence: run #1905, `docs/handoff/PENDENTE.md` (não editar ledger sem reconciliar)

**Interfaces:** usa DOM `document.querySelector` em runtime real, mas mantém compatibilidade com mock antigo que não implementa esse método. Nenhuma alteração de rota, API ou ECU.

- [ ] **Step 1:** capturar falha RED de CI #1905: três cenários de Ferramentas com `TypeError: document.querySelector is not a function` em `app.js:15`.
- [ ] **Step 2:** assegurar no teste que Ferramentas desenha cartão de fallback com ponte auxiliar falha e que a execução sem `document.querySelector` não cai.
- [ ] **Step 3:** tornar reposicionamento do CSS adaptativo seguro sem DOM completo; preservar carregamento ordenado de estilos no WebView.
- [ ] **Step 4:** executar `node --test tests/ui/ferramentas-webview-regression.test.cjs` no CI e depois `JavaScript e navegador`; esperar **0 falhas e 0 skips relevantes**.
- [ ] **Step 5:** revisar logs, salvar hash do SHA, commit específico, não merge.

### Task 2: Prova de baseline UI responsiva sem cortes e sem falso “PASS”

**Files**
- Modify: `tests/ui/adaptive-premium-layout.test.cjs`
- Check: `app/src/main/assets/ui/{index.html,styles-adaptive.css,styles-autocal-cockpit.css,styles-calibration-obd.css,styles-ux-leigo.css}`
- Evidence: `docs/evidence/img/ux-adaptive-*` como artefato CI; `tools/ci/sem_rolagem_abas.cjs`

**Interfaces:** cinco rotas mantidas, CSS sem reescrever handlers, estado da navegação e dados intactos.

- [ ] **Step 1:** acrescentar RED: screenshot em cada rota, 8 formatos (360×800, 390×844, 768×1024, 800×480, 1024×600, 1280×720, 1920×1080, 2560×1080) com central sem USB, emulada e ação em andamento.
- [ ] **Step 2:** verificar `scrollWidth` de página e de cada painel crítico, retângulo dos botões, interseção/oclusão, legenda e recuperação de foco; testar rotação com proposta não gravada.
- [ ] **Step 3:** capturar métricas e PNG com nome de tela, estado, viewport, SHA; validar manualmente todas as capturas, não só sua existência.
- [ ] **Step 4:** não marcar concluído se Chromium indisponível, screenshot vazia, rota oculta, console.error ou gráfico cortado; registrar por rota.

### Task 3: Congelar matriz fonte→ação→efeito antes de ampliar escopo

**Files**
- Create: `docs/qualidade/MATRIZ-FUNCIONAL-OMEGAS82.md` (tabela humana) e, se necessário, `docs/qualidade/matriz-funcional.json` (machine-readable; não fonte duplicada)
- Read: `docs/progbase/evidencias/INDEX.json`, `docs/progbase/protocolo/{comandos,vetores,backlog,matriz-comando-resposta}.json`, `docs/produto/lacunas-por-tela.md`
- Test: `tests/test_grafo_producao.py` e novo `tests/test_matriz_funcional.py`

**Interfaces:** cada ID de função referencia evidência original, capability ECU, estado de UI, ponte, writer/reader e teste; não deduz “usado” só por palavra no código.

- [ ] **Step 1:** inventariar cada CTA que realmente existe na WebView e cada método exposto das bridges (inclusive escondidos), identificar se lê, escreve ou apenas atua no app.
- [ ] **Step 2:** adicionar teste RED para ausência de referência, proveniência inválida, ação da ECU sem confirmação de suporte e função fora do original vendida como original.
- [ ] **Step 3:** preencher por função: `ORIGINAL_OBSERVADA`, `ORIGINAL_SOMENTE_CODIGO`, `APP_LOCAL`, `INVESTIGAR` ou `PROIBIDA`; classificar evidência separadamente de suporte no perfil concreto da central.
- [ ] **Step 4:** rodar teste, `python3 -B tools/grafo_producao.py --check`, atualização correta dos geradores e revisão; reportar contagens comprovadas e desconhecidas, não uma % artificial.
- [ ] **Step 5:** adicionar linha/issue para toda pendência; manter fonte primária sem duplicar tabelas geradas.

## Fase C1 — Gate de capacidades e verdade da escrita

### Task 4: Perfil de capacidades da central, fail-closed

**Files**
- Create (após contrato aprovado): `app/src/main/java/com/omegas/prohub/ecu/EcuCapabilityProfile.kt`
- Modify: `app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt` e `app/src/main/assets/ui/core/native-api.js`
- Test: `app/src/test/java/com/omegas/prohub/ecu/EcuCapabilityProfileTest.kt`, `tests/ui/ecu-capabilities.test.cjs`

**Interfaces:** `EcuCapabilityProfile` mapeia por geração de conexão/fingerprint validado uma capacidade a estado de **leitura**, **escrita**, origem e motivo; endpoint **read-only** de projeção. Formato a congelar em teste antes de integrar UI, evitando novos autores de estado.

- [ ] **Step 1:** RED: NAK de função não suportada, firmware desconhecido, perfis múltiplos e nova sessão USB → nenhum writer ganha permissão implícita.
- [ ] **Step 2:** criar perfil derivado **apenas** de respostas/presenças comprovadas, sem probing mutável ou suposições pelo nome da função.
- [ ] **Step 3:** expor ao JS somente a capacidade projetada, com motivo “sem comprovação nesta central”, “não suportada” ou “desconectada”.
- [ ] **Step 4:** verificar que botões desconhecidos ficam sem CTA de escrita; revisar não alteração dos comandos já contratados.
- [ ] **Step 5:** CI Kotlin + UI + auditoria; commit próprio.

### Task 5: Resolver “ACK ≠ gravado” e regravação automática do Mapa K

**Files**
- Modify: `app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt`, `CalibrationWriterReadBackV7.kt` e consumidores de status necessários
- Test: novos `app/src/test/java/com/omegas/prohub/calibration/KWriteResultContractTest.kt` e `tests/ui/mapa-k-ack-vs-readback.test.cjs`

**Interfaces:** estados explícitos `REQUESTED → ACKED → VERIFYING → VERIFIED / DIVERGED / UNKNOWN`; a UI exibe “Gravado” só em VERIFIED e Desfazer só se o protocolo permitir.

- [ ] **Step 1:** RED para ACK recebido e readback diferente, ACK com desconexão, sessão trocada, writer ocupado e erro na saída K insertion.
- [ ] **Step 2:** separar aceite do comando de valor efetivamente lido da central; não liberar recibo VERIFIED ao receber apenas ACK.
- [ ] **Step 3:** auditar a “correção autônoma” após readback divergente; não fazer novo lote sem comando humano explicitamente autorizado, preservando recuperação/estado incerto.
- [ ] **Step 4:** garantir trava persistida em K insertion incerto e não usar cache de arquivo como leitura da central atual; validar com teste de retomada.
- [ ] **Step 5:** CI remoto Kotlin + WebView + contratos; sem teste em carro nenhum efeito físico é considerado provado.

### Task 6: Matriz de respostas e conversões MP48

**Files**
- Modify somente após RED: `app/src/main/java/com/omegas/prohub/usb/UsbProtocolReply.kt`, `ecu/ResponseDrivenEcuEngine.kt`, `ecu/Mp48Protocol.kt`
- Test: `app/src/test/java/com/omegas/prohub/usb/UsbProtocolReplyTest.kt`, `tests/test_progbase_protocolo.py`, novos fixtures em `tests/fixtures`

**Interfaces:** respeitar eco, checksum, tamanho e forma de ACK/NAK/busy conforme versão; distinguir transporte de recusa da central; endereço/valor little-endian conforme o comando, sem suposição genérica.

- [ ] **Step 1:** RED para NAK cujo campo contém bit 0x10 mas não é exatamente 0x10, 0x96 busy, checksum ruim, eco parcial e n inválido, usando oráculo original.
- [ ] **Step 2:** verificar classificação do protocolo real v2 contra corpus; corrigir diferenças demonstradas, não copiar retries cegos sobre writes não idempotentes.
- [ ] **Step 3:** exigir que evento/resultado de falha preserve comando, geração da sessão, tipo da resposta, motivo e próximos passos, sem mostrar números da central quando desconhecidos.
- [ ] **Step 4:** CI remota, testes de fuzz determinístico de framing com limites e receipts; sem laboratório de escrita arbitrária.

### Task 7: Corrida de sessão, serial e recuperação

**Files**
- Check/Modify conforme falhas: `app/src/main/java/com/omegas/prohub/ecu/Mp48SerialScheduler.kt`, `ResponseDrivenEcuEngine.kt`, `service/TelemetryForegroundService.kt`
- Test: `tests/test_mp48_serial_scheduler_behavior.py`, `app/src/test/...` de USB e nova prova de geração

- [ ] **Step 1:** RED para USB desconecta durante leitura, readback de outro carro, stale telemetry e reset de conexão sob fila com prioridade de leitura.
- [ ] **Step 2:** manter uma única autoridade serial; cancelar ações vencidas por geração, limpar capability antiga e conservar recibo de estado incerto.
- [ ] **Step 3:** verificar que erros de transporte não se tornam NAK nem botão de “tentar gravar” automático.
- [ ] **Step 4:** CI com replay concorrente e restart da WebView; inspecionar logs de serial.

## Fase C2 — Original, endereços, formulários e efeitos

### Task 8: Triagem forense por função útil, não “portar tudo”

**Files**
- Read/update via geradores: `docs/progbase/COBERTURA.md`, `docs/progbase/decomp/COBERTURA.md`, `docs/progbase/protocolo/*`, `docs/progbase/evidencias/INDEX.json`
- Test: `tests/test_progbase_protocolo.py`, `tests/test_grafo_producao.py` e oráculos opcionais sob entrada autorizada

- [ ] **Step 1:** consolidar F1/F2/F3/F4/F6/F7/F8/F9, AutoCal, Mapa K, Curva K, sessões e funções da central em tabela **priorizada por risco e utilidade**; não criar UI para 7.132 funções sem nome.
- [ ] **Step 2:** para cada operação relevante, cruzar tela/controle DFM, rotina, endereço/SerialCode, unidade/escala, pedido, resposta, falhas e permissões de versão.
- [ ] **Step 3:** resolver primeiro lacunas P0 existentes: 4 extras anti-apagão, marcha lenta, retorno por pressão baixa, bins de tempo, diagnósticos, nível do gás e ttNone; congelar DESCONHECIDO quando não houver prova.
- [ ] **Step 4:** reexecutar parsers e oráculos apenas com fontes autorizadas; reconciliar 163 casos ttNone divergentes com CPU original (não ajustar fórmula por adivinhação).
- [ ] **Step 5:** gerar artefatos de cobertura, grafo e links EV; registrar diffs e origens. Exe/licença/log bruto continuam privados.

### Task 9: Replay comparativo do original e mapa de comandos

**Files**
- Verify/extend: `tools/emulacao/ecu_emulator.py`, `tools/progbase/protocolo_matriz.py`, `docs/progbase/protocolo/matriz-comando-resposta.json`
- Test: `tests/test_progbase_protocolo.py` e cenários Kotlin reais de decoder/bridges

- [ ] **Step 1:** estabelecer oráculo congelado LN/AC e log novo permitido, com hash e perfil; testar replay de pedido→eco→ACK/NAK→conversão→resultado sem adulterar captura.
- [ ] **Step 2:** produzir matriz para comandos **usados no produto**: telas, escala, eixo, índice, resultado e comportamento sem suporte; separar FIO real de emulação.
- [ ] **Step 3:** executar RED por comando divergente; corrigir exclusivamente a rotina comprovada e anexar casos de regressão; sem habilitar novos writes por isso.
- [ ] **Step 4:** checksum, fragmentação, versão, busy, NAK e falhas de desconexão passam; marcar gaps que precisam do carro.

### Task 10: Encerrar os fluxos P0 de motor, sem mecânicas inventadas

**Files**
- Read/Modify só com prova: `autocal/AntiStalloFlagCore.kt`, `AntiStalloParamsCore.kt`, `VoltaGasolinaCore.kt`, `VoltaGasolinaWriter.kt`, `diagnostics/SessionGuide.kt`
- UI: `app/src/main/assets/ui/core/cartoes-motor.js`, `components/advanced.js`
- Test: `tests/ui/anti-apagao-*.test.cjs` e testes Kotlin do writer

- [ ] **Step 1:** tela Anti-apagão no padrão real do original: **interruptor + um número comprovado**; quatro extras sem semântica provada ficam ocultos/recolhidos como investigação, **sem escrita**.
- [ ] **Step 2:** tratar “o motor apagou” versus “motor desligado normalmente” como fatos separados: teste com logs ambíguos, não inventar causa/velocidade.
- [ ] **Step 3:** volta por pressão baixa, opções de troca, marcha lenta: mostrar leitura/sugestão se já comprovada; writer só após captura de comando/resposta, prova do perfil, teste de partial/Desfazer e decisão do dono.
- [ ] **Step 4:** teste negativo para parâmetros sem escala, não suportados e idade de dados; nenhuma correção automática por heurística.
- [ ] **Step 5:** validação física bloqueante para declarar funcionamento do anti-apagão, marcha lenta ou parâmetros no carro.

### Task 11: AutoCal, mapa e curva — paridade real sem duplicar ciência

**Files**
- Read/Modify conforme teste: `autocal/NativeAutoCalMonitor.kt`, `AutoCalJavascriptBridge.kt`, `autocal/RefinementAutopilot.kt`, `ecu/AutoCalProtocol.kt`, `calibration/KFactorManager.kt`, `calibration/KWriteManager.kt`
- UI (somente depois do OK): `screens/autocal-cockpit.js`, `screens/refino.js`, `screens/map.js`, `screens/curve.js`
- Test: `tests/ui/autocal-original-parity.test.cjs`, `tests/ui/refino-mesmo-grafico.test.cjs`, `tests/ui/curva-k-para-mapa-k.test.cjs` e JUnit correspondentes

- [ ] **Step 1:** mapear comportamento atual: zonas, #n/d, epoch, botão de AutoMatch manual, reler da central, fazer proposta sem gravar, curva K e Mapa K com índices físicos corretos.
- [ ] **Step 2:** RED para dados sem origem, reset destrutivo indevido, K fora dos limites, mapa/célula cache desatualizado e write status ambíguo.
- [ ] **Step 3:** corrigir apenas o contrato comprovado, preservar o mesmo gráfico AutoCal/Refino e evitar “antes×depois” proibido.
- [ ] **Step 4:** separar pendências de protocolo dos ajustes visuais; nenhuma nova ação de referência, reset ou 0x37 sem evidência, aprovação e teste físico.

## Fase C3 — UI/UX de 2026 com continuidade, sem luxo decorativo

### Task 12: Entregar a aprovação visual completa (GATE)

**Files**
- Create: `docs/produto/mockups/v3/` PNG e arquivo fonte por tela, `docs/produto/mockups/v3/MAPA-FLUXO.md`
- Read: `docs/ux/mapa-de-telas.md`, `docs/REGRAS-DO-DONO.md`, `docs/produto/lacunas-por-tela.md`

- [ ] **Step 1:** desenhar juntas as cinco rotas e subfluxos de Ferramentas/Sessões/Diagnóstico: conectado, desconectado, coletando, proposta, alterando, falha, sucesso e estado incerto.
- [ ] **Step 2:** incluir pelo menos 16:9 1280×720 e 9:16 390×844, além de mapa de adaptação para 800×480, 1024×600, tablet e ultrawide.
- [ ] **Step 3:** review Intuitive Software Loop (Orientar→Reconhecer→Prever→Agir→Confirmar→Continuar) e recuperação para ações consequenciais; foco, contraste, alvo de toque e hierarquia.
- [ ] **Step 4:** enviar **TODO** o conjunto no chat, não somente links; obter OK explícito **antes** de editar visuais existentes.

### Task 13: Design system adaptativo e controles acessíveis

**Files**
- Modify somente após gate: `app/src/main/assets/ui/{styles.css,styles-ux-leigo.css,styles-autocal-cockpit.css,styles-calibration-obd.css,styles-adaptive.css,index.html}`
- Test: `tests/ui/adaptive-premium-layout.test.cjs` e `tools/ci/sem_rolagem_abas.cjs`

- [ ] **Step 1:** RED por clipe em cada rota/estado em 8 viewports; testar se um controle crítico fica inacessível sem scroll de pane legítimo.
- [ ] **Step 2:** manter layout automotivo com leitura a distância; retrato com gráfico acima do painel, navegação persistente, área segura do dispositivo e contraste acessível; técnicas de container queries quando a área exigir.
- [ ] **Step 3:** tooltips nunca bloqueiam interação; teclado/touch/foco e reduced motion; sem animação contínua.
- [ ] **Step 4:** screenshots DOM + teste manual de toque + CI; validação em WebView Android real.

### Task 14: Microinterações modernas baseadas em resultado real

**Files**
- Modify após gate: `app/src/main/assets/ui/app.js`, `components/live-bar.js`, `components/advanced.js`, `components/drawers.js`
- Test: `tests/ui/linguagem-leiga.test.cjs`, `tests/ui/copy-sem-ambiguidade.test.cjs`, `tests/ui/global-live-bar.test.cjs` e fluxos Playwright

- [ ] **Step 1:** RED para erro auxiliar Ferramentas vazia, primeiro uso sem cabo, “Já está assim” modal redundante, perda de trabalho ao navegar e exportar sem mensagem.
- [ ] **Step 2:** cada ação mostra contexto, restrições conhecidas, ocupado, aceito, verificando, confirmado, parcial, falha, recuperação e consequência (“o que mudou”).
- [ ] **Step 3:** implementar feedback local honesto sem polling duplicado: JS recebe projeção do estado Kotlin; affordance simples e estado persistente no cartão.
- [ ] **Step 4:** caminhar com usuário sem informar o nome do botão; validar descoberta da próxima ação e recuperação sem conhecer jargão interno.
- [ ] **Step 5:** fluxo de navegação tem no máximo cinco botões principais, sem ocultar recursos seguros existentes.

## Fase C4 — Inteligência útil, sem “IA gravando na central”

### Task 15: Conselheiro explicável por evidências de sessão

**Files**
- Modify conforme aprovação de escopo: `app/src/main/java/com/omegas/prohub/autocal/AntiStallSessionAnalysis.kt`, `RefinementAutopilot.kt`, `diagnostics/SessionGuide.kt`
- UI: `app/src/main/assets/ui/core/cartoes-motor.js` e cartões informativos
- Test: `tests/ui/anti-apagao-sugestao.test.cjs`, fixtures de sessões reais anonimizadas e testes Kotlin da análise

- [ ] **Step 1:** RED para sessões com apagão ambíguo, falta de velocidade, antes/depois não comparáveis, fonte recente/stale e janela sem amostra suficiente.
- [ ] **Step 2:** recomendar uma única próxima ação **como proposta**, com motivo, origem, limites, confiança e o que não se sabe; sem numero fixo tirado de hipótese.
- [ ] **Step 3:** vincular sugestão a evento do motor/sessão e revisão de estado, não a timer; nenhuma nova rota/writer/autoajuste de K.
- [ ] **Step 4:** validar precisão e taxa de “não sei” com corpus; observar no teste se usuário entende que nada foi gravado.

### Task 16: Diagnóstico e leitura progressivos, só do que existe

**Files**
- Read: `docs/progbase/protocolo/vetores.json`, `docs/produto/lacunas-por-tela.md`
- Modify após prova e gate: `app/src/main/assets/ui/core/ferramentas-grupos.js`, `components/advanced.js`; consumidores Kotlin somente de leitura, se faltarem

- [ ] **Step 1:** selecionar painel de diagnóstico, sensores/pressões/temperaturas, nível do gás e parâmetros F1/F9 que sejam capturados/aceitos na ECU específica.
- [ ] **Step 2:** não expor opções do ProgBase não encontradas no carro; agrupar “informação da central”, “análise do OMEGAS” e “não disponível”, sem botões falsos.
- [ ] **Step 3:** teste de indisponível, NAK, valor RAW e explicação de unidade não provada; uma medida não calibrada não ganha porcentagem.
- [ ] **Step 4:** conferir com screenshots/usuário que o diagnóstico explica ação e próximo passo; liberar apenas depois do gate de prova.

## Fase C5 — Release de verdade, acessibilidade, privacidade e campo

### Task 17: Continuidade, exportação, privacidade e desempenho em hardware fraco

**Files**
- Read/Modify conforme necessidade: `MainActivity.kt`, `diagnostics/PrivateSessionArchive.kt`, `diagnostics/DocumentsSessionMirror.kt`, `components/drawers.js`, `service/TelemetryForegroundService.kt`
- Test: Playwright exportação/retorno, tests Android/Java de permissão/privacidade, `tools/ci/estresse_interface.cjs`

- [ ] **Step 1:** RED para seletor de exportação cancelado/falhou, retorno à tela, sessão salva sem mensagem, pasta pública sem aviso, app reiniciado e usuário sem USB.
- [ ] **Step 2:** apresentar somente resultado real do armazenamento; manter dados privados por padrão quando possível, sem inventar sincronização entre dispositivos.
- [ ] **Step 3:** aferir performance em cold/warm start, frames/memória e custo de update visual no hardware autorizado; eliminar recomputações inúteis sem alterar ciência.
- [ ] **Step 4:** teste de regressão para Android lifecycle, carregamento tardio e sem tela branca.

### Task 18: Gate de aceitação integrado por função e por tela

**Files**
- Modify: `.github/workflows/ci.yml` com gates quando estiverem testados
- Test: `node --test tests/ui/*.test.cjs`, `python3 -B -m unittest discover -s tests -p 'test_*.py'`, `./gradlew testDebugUnitTest lintDebug`, `python3 -B tools/run_checks.py`
- Evidence: `docs/qualidade/RELATORIO-ACEITE-OMEGAS82.md` e capturas anexas com SHA exato

- [ ] **Step 1:** verificar gráfico de PRODUÇÃO, matriz funcional completa, corpus replay, Kotlin USB/writers, JS tela/rotas, Android render de cada formato e ausência de consoles inválidos.
- [ ] **Step 2:** rodar CI integral **no mesmo commit**, sem mascarar skips, timeout ou cancelamento como verde.
- [ ] **Step 3:** fazer revisão independente por subárea segurança/protocolo/UX; integrar dúvidas e resultados no relatório, citar falhas reais.
- [ ] **Step 4:** para cada função sem teste físico, escrever VALIDACAO_FISICA_PENDENTE, manter writer inativo se não provado e registrar próxima evidência necessária.

### Task 19: Confirmação real e release controlado

**Files**
- Read: `docs/handoff/PENDENTE.md` e `STATUS.md`; atualizar relatório de aceitação, plano de rollout e rollback
- Evidence: imagens/recibos do Android WebView, sessão USB, leitura da central e SHA do APK quando aprovado

- [ ] **Step 1:** propor roteiro observacional e seguro de validação com dono, sem testes de escrita agressivos enquanto o carro está em movimento.
- [ ] **Step 2:** validar aplicação instalada, conexão, estabilidade, leitura, cada ação autorizada, ACK+readback, recuperação, temperatura de uso e duração relevante de sessão.
- [ ] **Step 3:** comparar resultados com matriz de original e estado esperado; registrar prova física ou bloqueio. Nenhum “funciona no carro” por emulador.
- [ ] **Step 4:** somente com todas as aprovações e CI verde do SHA final, solicitar instrução explícita para build APK, integração na canônica e publicação.
- [ ] **Step 5:** declarar escopo final completo só quando as capacidades expostas e cenários obrigatórios estiverem provados; continuar backlog de pesquisa separado, sem acrescentar capacidades não demonstradas.

---

## Pacotes e critérios de passagem

| Marco | Dependências | Critério mínimo para passar |
|---|---|---|
| C0 Base limpa | 1–3 | CI não falha por regressão de shell; matriz rastreada; screenshots de 5 rotas sem falso PASS |
| C1 Segurança | C0 → 4–7 | Nenhuma escrita fora da capacidade; resultados ACK/readback coerentes; USB recoverável |
| C2 Original | C1 → 8–11 | Operações expostas mapeadas a fonte, endereço/escala, resposta e limite, desconhecidos não expostos |
| C3 Experiência | C0 + mapa de capacidades → 12–14 | Conjunto de telas aprovado; todos os formatos e estados testados visualmente; fluxo intuitivo |
| C4 Inteligência | C1 + C2 + C3 → 15–16 | Sugestão explicável nunca grava; diagnóstico só de capacidades verdadeiras |
| C5 Produto | Todos → 17–19 | CI completo, matriz de evidências, usabilidade e dispositivo físico; bloqueios físicos honestos |

## Execução recorrente da tarefa programada

Em cada ciclo: (1) ler este plano, especificação, `AGENTS.md`, `PENDENTE.md`, diff e HEAD do PR #195; (2) buscar primeira tarefa não concluída com dependência satisfeita; (3) executar **um** próximo passo testável dentro de escopo/autorização, de preferência read-only ou bugfix isolado com teste; (4) publicar evidência no repositório; (5) atualizar esta checklist ou relatório de progresso e informar somente avanço/risco/bloqueio relevante; (6) não fazer merge, build APK ou escrever na central sem autorização. Se o passo exigir visual aprovação ou ECU física, marcar BLOCKED e escolher uma tarefa independente admissível. Não fabricar “100%” para cumprir cronograma.

O período recorrente de uma hora é o mínimo suportado pela automação. Este plano não garante execução contínua nem que uma ferramenta inativa consiga usar a central ou gerar artefatos; essas condições são explicitamente monitoradas.

## Self-Review

- Escopo cobre protocolo, ciência, ações originais, UX 5 rotas, múltiplas telas, inteligência de sessão, privacidade, performance, Android e campo.
- Nenhum comando não confirmado é promovido a função acessível; conteúdo “fora da ECU” só é apresentado como análise do OMEGAS.
- Dependência central: gates de segurança e visual antes de mudança de writers/telas.
- Critérios independentes: código correto ≠ protocolo original ≠ ECU real ≠ interface intuitiva.
- Bloqueio real é resultado documentado, não desaparece do ledger; sem correr atrás de cobrir bibliotecas Delphi não relacionadas à finalidade do produto.
