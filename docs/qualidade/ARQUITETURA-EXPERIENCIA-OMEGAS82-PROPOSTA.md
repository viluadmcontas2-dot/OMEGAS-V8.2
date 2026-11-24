# OMEGAS 8.2 — opções de arquitetura de experiência (OP-18 / gate visual)

> Proposta de 2026-11-18. NÃO é mockup aprovado, screenshot do app nem autorização para reformar AutoCal/Refino/Mapa K/Curva K. Base: DESIGN.md/UX-CONTRACT.md, jornadas OP-17 e capturas CI #2053. Objetivo: reduzir decisão operacional, não retirar capacidade comprovada.

## Três caminhos comparados, sem scores fabricados

| Alternativa | Como começa a tarefa | Vantagem observável/proposta | Risco e gate |
|---|---|---|---|
| A — cinco áreas atuais (baseline) | AutoCal, Refino, Ajuste local, Ajuste global, Ferramentas | Mantém atalhos especializados, estrutura funcional já coberta por testes; nenhuma migração de rota | Usuário iniciante precisa escolher entre nomes técnicos; rótulo Ferramentas quebra em DPR3 em quatro telas técnicas |
| B — início guiado por intenção (exploração recomendada, NÃO aprovada) | “Entender meu carro” / “Ver leituras” / “Analisar sessão” / “Ajustar manualmente” antes de acessar áreas existentes | Reduz decisão por subsistema, mantém ação final explícita e reaproveita engine/bridge | Exigiria projeto completo de cinco rotas, mapa de fluxo, revisão de acessibilidade e OK do dono ANTES de mudar telas protegidas |
| C — painel unificado de diagnóstico e ajustes (pesquisa) | Uma tela concentra motor, aprendizado e sugestões | Menos troca entre rotas em desktop | Alta densidade em 360×640 e 800×480; maior risco de confundir leitura com writer, de scroll aninhado e de esconder recuperação |

**Direção de trabalho independente permitida agora:** manter A executável sem reforma nas quatro telas protegidas, melhorar contratos de honestidade e testes de Ferramentas; desenhar B como hipótese a submeter à aprovação no pacote completo. Não implementar B/C nesta execução. A escolha depende de provas de tarefa real e aceitação do dono, não de tendências.

## Mapa de fluxo funcional (estado atual, não mockup)
- Entrada / AutoCal: ler status → verificar aquisição → analisar zonas → proposta local (não grava) → ações manuais já autorizadas → resultado/recuperação; bloqueio RESET_ALL público persistente.
- Refino: visualizar séries e recomendações do OMEGAS → distinguir análise do app da central → prévia → escrita somente em fluxo existente com intenção.
- Ajuste local: ler grade K da sessão → selecionar e comparar → revisar alteração → confirmar comando manual existente → status com contrato histórico ACK/readback ainda BLOQUEADO para mudança.
- Ajuste global: ler Curva K → comparar proposta → revisão → gravação manual existente → status e recuperação.
- Ferramentas: Motor/combustível (anti-apagão e leitura) ↔ App/arquivos (Sessões / Registro do app / permissões / Car Info) → exportação local; aviso baseado em retorno real.

## Adaptação proposta, sem alteração implementada

| Janela | Prioridade | Prova/gap |
|---|---|---|
| 360×640 DPR3 e 360×800 / 390×844 | Legibilidade e nav sem quebra; gráfico e ação não podem se ocultar | Screenshot CI #2053 mostrou quebra de Ferramentas nas quatro telas técnicas; reforma bloqueada |
| 768×1024 e 1080×1920 CSS | Alvos tocáveis, uma leitura por vez e scroll contextual | Capturas de Chromium; toque/rotação Android NE |
| 800×480 e 1024×600 | Leitura a distância, poucos cartões por linha, não depender de teclado | Ferramentas compactas observadas no CI #2053; controle individual em bancada |
| 1280×720 / 1920×1080 / 2560×1080 | Contexto, gráfico e ação lado a lado; limitar altura vazia em Ferramentas | Capturas visualmente revisadas; não extrapolar para WebView físico |

## Critérios para o pacote de mockups e aprovação (OP-19 bloqueado)
1. Conjunto completo de cinco áreas + Sessões e Diagnóstico, não uma tela isolada; estados sem cabo, lendo, pendente, êxito, NAK, parcial e recuperação.
2. Rótulo/caminho e efeito previsível para um leigo e para mecânico experiente, sem jargão desnecessário; unidade e proveniência visíveis.
3. Variantes 390×844, 1280×720, 800×480, 1024×600, tablet e ultrawide; contraste, foco, toque ≥56px onde contrato exige e sem cortes.
4. Fluxo Orientar→Reconhecer→Prever→Agir→Confirmar→Continuar, com decisão e reversibilidade explícitas; nenhum rascunho perdido.
5. Apresentar pacote ao dono e obter OK explícito ANTES de qualquer reforma em AutoCal/Refino/Mapa K/Curva K. Se recusado, não alterar rotas protegidas.

**Estado OP-18: PARTIAL — comparação e mapa documental, sem mockups aprovados nem mudança visual.**
