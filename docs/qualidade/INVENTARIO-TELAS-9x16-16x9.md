# OMEGAS 8.2 — inventário enxuto de telas, 9:16 e 16:9

> Origem visual: **capturas reais do Chromium automatizado**, CI #1969, SHA `b2ccc9ab9da21e1f7eecdbea745964b9b1da6ece`; [artefato Github Actions](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44093640227/artifacts/14332498594). As alterações posteriores até `17b2a79` não mudaram código/UI/CSS do app. Screenshot **não é** Android WebView, toque real ou homologação da central. Classificação de evidência do código = OBS-COD; não houve OBS-FIO novo.

| Tela | Retrato 9:16: 360×640 CSS DPR3 (1080×1920 físicos) e 1080×1920 CSS | Paisagem 16:9: 1280×720 e 1920×1080 |
|---|---|---|
| **AutoCal** | **PARCIAL:** gráfico e ações observados; nav `Ferramentas` quebra em duas linhas no DPR3 | **VISUAL OBSERVADO:** gráfico e ações presentes; interações não homologadas |
| **Refino** | **PARCIAL:** curvas vistas, mas nav `Ferramentas` quebra no DPR3 | **VISUAL OBSERVADO:** gráfico e controles presentes |
| **Ajuste local — Mapa K** | **PARCIAL:** grade e scroll local presentes; nav quebra no DPR3; toque das 144 células não validado no aparelho | **VISUAL OBSERVADO:** grade e ações aparecem |
| **Ajuste global — Curva K** | **PARCIAL:** curva visível; nav quebra no DPR3; arrasto e writer não homologados | **VISUAL OBSERVADO:** curva e controles presentes |
| **Ferramentas** | **VISUAL OBSERVADO:** nav `Ferramentas` inteira na aba ativa, cartões empilhados | **VISUAL OBSERVADO:** cartões compactos e controles visíveis |

**Decisão por experiência (Intuitive Software Design — IMPROVE / User-Task Walkthrough / Decision-Support):** UI: inconsistência de quebra do rótulo em 4 rotas técnicas na janela DPR3; Flow: capturas não demonstram perda de estado, mas também não validam toque/rotação; Feel/confiança: diferenciar estado da central, exportação local, resposta e leitura posterior. Não inventar scores ou estudos de usuário. Fricção a resolver: navegação legível em todas as rotas, sem modificar as quatro telas protegidas até aprovação expressa com mockups de conjunto e mapa de fluxo.

**Outros viewports na suíte:** 360×800, 390×844, 768×1024, 800×480, 1024×600, 2560×1080; aceitação visual/Android por estado ainda parcial. Resposta a “funciona em Full HD?” = testes de viewport e imagens do Chromium, **não homologação física**.

**Segurança P0 reconciliada (OBS-COD, 2026-11-17):** a frase anterior sobre `RESET_ALL` alcançável estava desatualizada. O controle foi removido da UI e os métodos de preparação/execução da bridge passaram a negar `RESET_ALL`; a ação interna do original permanece como referência, não como acesso da WebView. CI #2009 validou a correção, e o HEAD `6122e0f909c79b7011fc5d215ae268d6be8a8627` teve CI #2017 (run 44152452981) com seis jobs `success`. Isso **não** equivale a teste na ECU física nem elimina os demais bloqueios de release. Artefato de capturas do CI #2017: [14359818313](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44152452981/artifacts/14359818313); as PNGs desse SHA **ainda não foram inspecionadas manualmente**, portanto as classificações visuais acima são históricas (CI #1969), não PASSOU no HEAD. Próxima etapa OP-13: baixar e revisar as cinco rotas em retrato/paisagem, incluindo DPR3, sem alterar telas protegidas.


## Verificação remota adicional — CI #2021 (2026-11-17)

- **OBS-COD / CI:** HEAD `24919d196c060496ec6b75008de767c4ccbf1b73`, workflow run [#2021](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44166547478), seis jobs `success` (Python, grafo, Android lint, Kotlin/JVM, JavaScript/navegador e integração). O job JavaScript executou a etapa de capturas, mas isso **não** comprova revisão visual individual de PNGs nem teste em Android/USB/ECU.
- **Evidência visual anterior disponível:** artefato CI #2017, SHA `6122e0f909c79b7011fc5d215ae268d6be8a8627`, com imagens de Ferramentas 1080×1920 CSS, Ferramentas e AutoCal 360×640 CSS DPR3 e Ferramentas 1280×720 já apresentadas ao dono no chat. Foi observada a quebra de `Ferramentas` na navegação da rota AutoCal DPR3, apesar da correção scoped da rota Ferramentas. **FALHA VISUAL HISTÓRICA**, não reprovação automática do HEAD #2021; mudança das quatro telas técnicas continua bloqueada até aprovação do conjunto de mockups e mapa de fluxo.
- **GATE DE ENTREGA:** ainda NÃO PRONTO. CI verde é condição necessária, não suficiente. Bloqueios físicos, matriz funcional incompleta, conflito ACK/readback e revisão de screenshots do HEAD persistem. Não gerar nem disponibilizar APK intermediário. A menção histórica ao link de APK no Centro de Comando é **risco de exposição de entrega incompleta**: a interface pública deve ocultar/desabilitar esse link até liberação final, mediante mudança autorizada no projeto AppDeploy, não por alterar escritores ou o PR Android.
- **OP-13:** PARTIAL, próximo aceite = baixar artefato do SHA #2021, inspecionar as cinco rotas nas orientações e viewports exigidos, registrar falhas por screenshot/rota/viewport e distinguir correção autorizada em Ferramentas de mudança protegida. Nenhuma tela recebe PASSOU neste SHA sem inspeção da captura correspondente.


## Revisão VISUAL automatizada OP-8/OP-13 — CI #2053 (2026-11-18)

- **Artefato efetivamente baixado e inspecionado:** [GitHub Actions — capturas do CI #2053, artefato 14468157741](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44391621134/artifacts/14468157741), SHA `6a8fa2ba99cd5cb757f0adac362aef924cced6c4`, 50 PNGs `ux-adaptive-{viewport}-{route}.png` das cinco rotas e dez cenários de dimensões. Capturas de **Chromium com ponte emulada**, não Android.
- Revisão com grade visual de 40 capturas e aberturas individuais de AutoCal/Ferramentas em 360×640 CSS (DPR3, PNG 1080×1920), Mapa em 390×844 e Ferramentas em 800×480/1024×600. Dos 50 arquivos, 46 são idênticos em bytes aos equivalentes da suíte CI #2051; quatro diferem (map em 800×480/1024×600/1280×720 e curve em 1920×1080), sem concluir causa apenas pela diferença de arquivo.

| Tela | 9:16 retrato — 360×640 DPR3 / 390×844; 1080×1920 CSS distinto | 16:9 paisagem — 1280×720 / 1920×1080 (800×480 e 1024×600 também capturados) |
|---|---|---|
| AutoCal | **FALHOU VISUAL:** rótulo “Ferramentas” quebra dentro da navegação DPR3; demais estados não homologados | **PASSOU VISUAL na captura:** painel/gráfico e comandos visíveis |
| Refino | **FALHOU VISUAL:** rótulo “Ferramentas” quebra na barra das telas técnicas DPR3 | **PASSOU VISUAL na captura:** gráfico e ações visíveis |
| Ajuste local — Mapa K | **FALHOU VISUAL:** rótulo quebra no DPR3; grade com scroll interno intencional, toque de célula no Android não homologado | **PASSOU VISUAL na captura:** grade e ações visíveis |
| Ajuste global — Curva K | **FALHOU VISUAL:** rótulo quebra no DPR3; gravação e arrasto não homologados | **PASSOU VISUAL na captura:** curva e painel lateral visíveis |
| Ferramentas (Sessões/Diagnóstico são subáreas) | **PASSOU VISUAL na captura:** “Ferramentas” inteira na rota ativa, cartões legíveis; conteúdo abaixo da dobra exige continuidade | **PASSOU VISUAL na captura:** cartões compactos em 800×480/1024×600 e 16:9; feedback real de exportação não foi provado |

**Classificação:** PASSOU/FALHOU é apenas inspeção de layout de screenshots desse SHA; **Android/WebView/USB/ECU físicos: NÃO VERIFICADO em todas as cinco telas**. Os 10 cenários incluem 360×640 DPR3 (1080×1920 físicos) e 1080×1920 CSS pixels como casos distintos, além de 360×800, 390×844, 768×1024, 800×480, 1024×600, 1280×720, 1920×1080 e 2560×1080. O CI completo do SHA #2053 passou seis jobs; a inspeção não substitui teste de toque/legibilidade com o dono. Não corrigir a navegação das quatro telas protegidas sem mockups completos e OK explícito.


**Nota de repetibilidade das capturas:** comparação pixel a pixel entre #2051 e #2053: 46/50 arquivos `ux-adaptive` iguais em bytes; quatro imagens têm somente diferenças de cor de até 10 níveis RGB (sem pixel acima desse limiar). Não deduzir causa nem evolução de UI desta diferença. Evidência visível de quebra do rótulo nas quatro rotas técnicas permanece.


## Inspeção visual pontual de Ferramentas / subárea App e arquivos — CI #2073
**Origem:** [artefato GitHub Actions #2073, id 14484313982](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44426621923/artifacts/14484313982), SHA `cadf71b4142df89d765b9d1986627969e50a0210`. Capturas Chromium automatizadas, não Android. A imagem `ux-800x480-9-ferramentas-app-e-arquivos.png` é de **800×480 CSS, DPR2 = 1600×960 pixels** e `ux-1280x720-9-ferramentas-app-e-arquivos.png` de **1280×720 CSS, DPR2 = 2560×1440 pixels**.

| Subárea | 800×480 | 1280×720 | Limite |
|---|---|---|---|
| Ferramentas / App e arquivos | **PASSOU VISUAL:** cartão Bateria e Balão mostram “Sem leitura”, textos completos e sem botão falso de permissão de balão | **PASSOU VISUAL:** cartões legíveis/compactos, navegação Ferramentas inteira | Nenhuma interação nativa ou autorização física comprovada |

**Diferença observável #2069→#2073:** só os pixels do cartão Balão mudaram nestas duas capturas, trocando pedido “Permitir balão” por “Sem leitura” porque o fallback `overlayStatus.ok=false` não é permissão acessível. A tela de Ferramentas ativa mostra nome inteiro. **AutoCal/Refino/Mapa/Curva retrato DPR3 mantêm a falha conhecida de quebra da navegação; não promover PASS dessas telas por esta revisão.** Android físico em todas as áreas continua NÃO VERIFICADO.


## Revisão visual — CI #2081, 19/11/2026
**Artefato real baixado e inspecionado:** [Actions #2081 — screenshots (id 14492858377)](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44445329165/artifacts/14492858377), SHA `d78a82131f9899663c01c3673f06de9bc5dba597`, 6 jobs SUCCESS. ZIP com 131 entradas, incluindo 50 `ux-adaptive` de 5 rotas × 10 cenários; inspeção em painéis visuais para 360×640 CSS DPR3 (PNG 1080×1920 físico), 390×844, 1080×1920 CSS, 800×480, 1024×600 e 1280×720. 1920×1080 e 2560×1080 também presentes. Capturas de **Chromium emulado**, não WebView ou Android físico.

| Tela | Retrato 9:16 (360×640 DPR3) | Paisagem 16:9 (1280×720 / 800×480) | Dispositivo |
|---|---|---|---|
| AutoCal | **FALHOU VISUAL:** “Ferramentas” quebra no rodapé | **PASSOU VISUAL:** painel/ações visíveis | NÃO VERIFICADO |
| Refino | **FALHOU VISUAL:** mesma quebra | **PASSOU VISUAL:** curvas/controles visíveis | NÃO VERIFICADO |
| Ajuste local — Mapa K | **FALHOU VISUAL:** mesma quebra, grade em janela estreita | **PASSOU VISUAL:** grade/painel visíveis | NÃO VERIFICADO |
| Ajuste global — Curva K | **FALHOU VISUAL:** mesma quebra | **PASSOU VISUAL:** curva/controles visíveis | NÃO VERIFICADO |
| Ferramentas (Sessões/Diagnóstico) | **PASSOU VISUAL:** navegação inteira | **PASSOU VISUAL:** cartões em 800×480 e 1280×720 | NÃO VERIFICADO |

**1080×1920 CSS ≠ 360×640 DPR3**, embora ambas as capturas tenham 1080×1920 pixels. Sem autorização para redesenhar quatro telas protegidas. Capturas históricas não conferem PASS ao novo SHA, não comprovam toque/USB/ECU.
