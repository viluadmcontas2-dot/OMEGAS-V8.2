# Incidente — sugestão global da Curva K era apenas informativa

## Sintoma e impacto
O assessor Kotlin calculava tendência global, delta, confiança e incerteza, mas abrir uma sugestão na Curva K apenas mostrava o contexto. O operador precisava redigitar manualmente o fator, quebrando o fluxo assistido.

## Causa
`suggestion-model.js` não preservava o índice do ponto e `curve.js` possuía somente `renderSuggestionFocus`, sem caminho para transformar a sugestão normalizada em proposta editável.

## Por que os testes não detectaram
O contrato anterior exigia explicitamente que o painel não preenchesse `this.proposals`; portanto o teste protegia um fluxo incompleto.

## Correção
- preservar `index` e `petrolMs` na sugestão normalizada;
- esperar a leitura real da Curva K;
- ação contextual `Preparar sugestão`;
- calcular apenas o candidato a partir do delta normalizado e enviá-lo à prévia Kotlin existente;
- armazenar a prévia como proposta antes/depois;
- manter revisão e confirmação humana separadas;
- nenhuma chamada de writer no preparo.

## Teste de regressão
`tests/test_block3_suggestion_ui_contract.py` e `tests/ui/suggestion-model.test.cjs` provam índice, destino, preparo via preview e ausência de writer.

## Risco residual
Fluxo visual/touch e resultado real na Curva K continuam **AGUARDANDO CELULAR/VEÍCULO**.