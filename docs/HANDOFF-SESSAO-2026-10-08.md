# Passagem de sessão — 2026-10-08

## Estado
- Branch de integração: `work/platina-final` (merge de: `work/platina-correcoes` [PR #174], `work/platina-sessao-ficticia`, `work/platina-refino-final`, `work/platina-salvar-manual`, `work/platina-teia-autocal`). Base: `OmegasDiamante`. Rodar `ci.yml` (workflow_dispatch) e conferir `build_and_test`.
- APK anterior (sem estas correções): run 37669637823, commit 188a7b6, SHA-256 2b6371ad93187143004ffa0f3e4717e80b8a4185bc930ab830e23936d39b3c3e. NÃO foi enviado ao Drive (Codex exige aprovação para `upload_file`; permitir a regra para `codex exec --approve-for-me` ou arrastar o APK à pasta OMEGAS manualmente).
- Trabalho local guardado: `work/platina-sessao-local-0810` (correção da "dobra natural" do apagamento automático, quadro vazio do gráfico, StageTimer) — NÃO integrado; revisar e decidir.

## Ordens do dono (vigentes)
- AutoCal é a tela principal: gráfico focado/largo, sem linha de estado, zonas Z1–Z4 por combustível, contador "AutoMatch N de M" na legenda; barra de topo única é BLOQUEANTE; "Selecionar junto" FICA; AutoMatch manual, detalhes técnicos demais, aba Sugestões e rastro ao vivo NÃO voltam.
- Leitura anterior NUNCA aparece (regra 16). Reset nunca pausa o aprendizado (regra 14). Curva K só salva ao clicar em Salvar, em Downloads/OMEGAS/Curva (regra 15).
- Refino simples e funcional: curva nativa da ECU como base, pontos próprios (até ~50) refinam; rejeitar parado/outliers. Pendente: "mais pontos = mais perto" ainda NÃO provado (ver `RELATORIO-REFINO.md`).
- Ideia em espera (mockup aprovado só para ver): aba "Curva→Mapa" (Curva K vira Mapa K por faixa de injeção e a Curva zera). Regra 11 muda só com pedido explícito (o dono já deu).

## Não provado (só o dono no carro)
Que a ECU pausa após Reset e que o religar vence; que o arquivo da curva aparece em Download/Omegas/Curva; que as sugestões melhoram a curva; instalação física do APK.

## Itens pendentes
1. CI verde em `work/platina-final` → um PR para `OmegasDiamante` → APK final (`verde-apk-now.yml`) com SHA-256.
2. Apagar branches: `main`, `work/platina-*` mescladas (o proxy da sessão não consegue; fazer pela página de Branches).
3. Bancada ProgBase: repositório privado `omegas-bancada` (nunca subir exe/licença para repo público).
