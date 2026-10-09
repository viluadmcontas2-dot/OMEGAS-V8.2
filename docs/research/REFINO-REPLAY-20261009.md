# Refino: replay completo e validação da evidência madura — 2026-10-09

Escopo: OMEGASCINZA, implementação Kotlin do `EquivalenceRuntime`, `EquivalencePhases` e `RefinoState`; fixtures reais de 06/10. Nenhuma gravação na ECU. O fluxo pedido pelo dono separa Refino da Curva K de proposta local do Mapa K, esta última condicionada à identificação causal do efeito do mapa.

## O que foi executado

- Replay cronológico dos 93 snapshots e 8.937 quadros da pista, e dos 15 snapshots e 4.303 quadros da sessão util.
- Dois cenários de referência por sessão: sem salvar; salvando explicitamente a primeira referência nativa disponível. 216 avaliações no total.
- Estado observado sem contador e contrafactual com AutoMatch 3/3, apresentados separadamente. Os recortes JSONL não contêm esse contador nem o eixo PETR_INJ_TBP; o replay acrescenta o eixo de fábrica utilizado no teste já existente. O contrafactual não prova conclusão física do AutoCal.
- A Curva K lida alinha a época do livro; mudanças descartam GNV da curva anterior. Não se inventou telemetria de condução.
- Ganho por histórico de gravações não é reproduzido neste ensaio (`gainScale = null`); não houve gravação hipotética nem simulação de resposta física posterior.

## Resultado e correção

Com referência salva, a pista produzia APPLY em 47/93 snapshots. Dez dessas propostas melhoravam o critério de todos os alvos, mas pioravam o erro das faixas maduras usado no diagnóstico: por exemplo, snapshot de índice 40 (41ª leitura), erro 5,663% → 5,898%.

A guarda conferia apenas todos os alvos; o diagnóstico calculava um subconjunto diferente, maduro. Agora ambos são conferidos antes da liberação, conservando REGRESSION_EPS = 0,0001, limites, filtro de condução e histerese existentes. Uma reprovação mantém os valores atuais e informa `regressionBlocked`.

Após a correção, a pista ainda produz APPLY em 37/93 snapshots; as dez regressões são barradas. Passo máximo entre as propostas restantes: 10,94%. Com contador 3/3 hipotético, 36 propostas ficam liberadas pelo estado da tela; uma fica bloqueada pelo prazo de coleta. Sem o contador, nenhuma fica autorizada para gravação. Isso é insuficiência do recorte de evidência, não demonstração de falha de leitura no veículo.

A sessão util produz dois ajustes iniciais baseados na aquisição **nativa** existente, sem usar pares próprios de condução (`telemetryPairsUsed = 0`); depois pede coleta. O relatório anterior amostrava só os snapshots de índices 9 e 14 e, por isso, mostrava zero propostas. Não se deve generalizar essa amostra para os quinze snapshots.

Sem salvar a referência, a pista pede `FREEZE_REFERENCE` nas 93 leituras. Esse é um passo manual explícito, não uma ausência de capacidade matemática de propor.

## Prova de regressão

O novo teste executa todos os 108 snapshots e exige que as propostas APPLY não piorem o erro maduro além da tolerância numérica. Também exige que a pista continue produzindo alguma proposta. Antes da correção falhou no snapshot de índice 40; depois passou. Não basta testar somente os snapshots amostrados.

Os testes de ciclo de vida verificam recuperação automática depois de falha de leitura e evidência nova, sem exigir reset do usuário. São ensaios sintéticos de estados; distinguem-se do replay de medições reais.

## Limites e partes do fluxo ainda abertas

- Replays reais exercitam o cálculo e as decisões; não constituem teste conectado à ECU nem provam melhora física após aplicar uma proposta.
- O ramo causal de Mapa K está no laboratório offline (`MAP-K-CAUSAL-20261009.md`, `tools/map_k/`), não integrado como gerador de propostas locais no serviço. Estes recortes não registram intervenção Map K confirmada que permita identificar seu ganho.
- Validar fisicamente sem piorar exige gravação manual, readback e novas medições comparáveis nas regiões afetadas. Nada neste ensaio executou essa gravação.
- A existência de uma proposta não prova que todos os regimes já estão medidos, nem autoriza alterar regiões não observadas.
