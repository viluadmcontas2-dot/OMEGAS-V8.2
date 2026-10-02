# Incidente — mapa antigo permanecia utilizável após falha de releitura

Data: 2026-08-04  
Branch: `rebuild/ux-9in-dual-layout`

## Sintoma e impacto

A abertura do Mapa K já iniciava leitura automática. Porém, se uma releitura falhasse ao iniciar ou terminasse com erro, o editor ainda podia manter em memória o mapa confirmado anteriormente.

O impacto potencial era permitir que uma célula antiga voltasse a ser selecionada depois de uma leitura não confirmada, contrariando a regra de que a edição deve partir da leitura atual da ECU.

## Causa imediata

O estado visual mudava para falha, mas o modelo `MapEditor` não possuía uma operação explícita de invalidação completa. As linhas, eixos, hash e seleção anteriores não eram removidos em todos os caminhos de erro.

## Causa estrutural

Os testes do shell verificavam principalmente presença de funções e seletores. Eles não executavam o ciclo real de abrir, reler, falhar, reconectar e confirmar que nenhuma escrita foi chamada.

## Correção

- `MapEditor.reset()` invalida linhas, eixos, linha técnica, hash e seleção;
- toda leitura aceita pela ECU invalida o mapa anterior antes de aguardar a resposta;
- falha ao iniciar ou concluir a leitura mantém o mapa inválido;
- reabrir Ajustar ou tocar novamente em Mapa K solicita uma nova leitura;
- reabertura durante leitura fica enfileirada e começa após a operação atual;
- nenhum desses caminhos chama o writer.

## Teste de regressão

Os testes comportamentais executam o shell com adaptador MP48 simulado e comprovam:

- leitura em cada abertura e reabertura;
- espera desconectada e leitura após reconexão;
- segunda leitura enfileirada durante uma leitura ativa;
- invalidação do mapa após falha de início e falha de conclusão;
- zero chamadas `startKBatchWrite` nesses caminhos;
- um único agendador periódico;
- contratos responsivos para multimídia horizontal, central baixa e celular vertical.

## Risco residual

A evidência automatizada não substitui WebView real, USB, ECU, suspensão/retomada, sessão prolongada, ACK, readback e validação no veículo.
