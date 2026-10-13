# Auditoria tela por tela (modo demonstração, 1280×720), 2026-10-13

Método: Chromium sobre o app real (`index.html`), 8 abas, capturas em `docs/evidence/img/previas-2026-10-13b/`. Limite: demonstração não tem ECU; estados reais (reset, zona nova, automatch) só no aparelho ou nas sessões gravadas.

| Aba | Achado | Estado |
|---|---|---|
| 01 Agora | Coerente (injeção, RPM, MAP, combustível). Eyebrow ciano ("AGORA") difere do cinza das outras abas. | cosmético, aberto |
| 02 Aprender | Grade com azul/laranja (sinal da diferença, explicado na legenda). "comparando" repetido em toda célula pesa. | cosmético, aberto |
| 03 Ajuste local (Mapa K) | Proposta Curva→Mapa curta; botão "Gravar N alterações" (singular corrigido). | ok |
| 04 Ajuste global (Curva K) | Linha atual azul (= curva K do AutoCal), proposta âmbar; arrasto sem redesenho. | corrigido |
| 05 AutoCal | Gráfico mantém o último estado válido (não reconstrói em reset/zona nova). | corrigido, falta ver no aparelho |
| 06 Refino | Cores de combustível iguais ao AutoCal (vermelho/verde); texto de ajuda corrigido. Em demonstração mostra "ponte AutoCal ausente". | corrigido |
| 07 Sugestões | Estado vazio colado no canto superior esquerdo de uma tela grande. | cosmético, aberto |
| 08 Ferramentas | Coerente; sem erros de console em nenhuma aba. | ok |

Erros de página: 0 nas 8 abas. Layout sem rolagem: `tools/ci/sem_rolagem_8_abas.cjs` PASS.
Pendências reais (não cosméticas) estão no checkpoint: Refino usar tropeços para sugerir; validação no aparelho.
