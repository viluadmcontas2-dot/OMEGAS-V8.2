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

**Bloqueio de release adicional:** `RESET_ALL` permanece como ação alcançável no menu avançado AutoCal; não gerar APK com exposição não autorizada. Sem alterações à UI nesta auditoria.
