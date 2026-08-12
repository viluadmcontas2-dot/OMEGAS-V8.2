# Incidente — aprendizado atrasado por backlog quente acumulativo

Data: 2026-08-09  
Estado: correção automatizada em validação; validação física pendente.

## Sintoma e impacto

Na multimídia real, a telemetria do Dashboard permanecia fluida, porém o aprendizado de gasolina praticamente não aparecia e o GNV evoluía muito lentamente. Sessões físicas exportadas mostraram que o analisador reconhecia amostras válidas no tempo correto, mas a memória de aprendizado só as refletia dezenas de minutos depois.

Evidência física analisada antes da correção:

- sessão `stels`: 50.861 trabalhos submetidos ao pipeline, 21.096 concluídos e 29.765 pendentes; atraso observado na ordem de dezenas de minutos;
- sessão `lauro`: 83.166 trabalhos submetidos, 24.244 concluídos e 58.922 pendentes; fila próxima de 56 minutos;
- uma amostra forte de gasolina aceita por volta de 23:06:56 só apareceu na memória por volta de 00:01:13, praticamente o mesmo atraso informado pelo pipeline;
- a sessão seguinte iniciou ainda carregando dezenas de milhares de trabalhos quentes da sessão anterior quando o flush não conseguiu drenar a fila.

Os números acima vêm dos pacotes físicos analisados durante o incidente. Os arquivos brutos não são incorporados ao repositório.

## Causa imediata

`NativeRuntimeManager` encaminhava todo quadro de telemetria a um `OrderedBackgroundPipeline` de aprendizado, independente de o quadro conter uma nova amostra útil. O pipeline era:

- single-thread;
- `Thread.MIN_PRIORITY`;
- sem limite explícito de backlog;
- estritamente FIFO;
- drenado por flush em fronteiras de sessão, sem descarte de trabalho obsoleto.

Quando a taxa de chegada superou a taxa de processamento, a RAM virou histórico de execução. O aprendizado continuava correto em ordem, porém correto para uma condição física antiga demais para ser útil na calibração atual.

## Causa estrutural

O analisador usa janelas móveis e pode reavaliar uma janela fortemente sobreposta a cada quadro novo depois do mínimo. A arquitetura tratava cada uma dessas decisões como se precisasse de uma tarefa durável independente em RAM. Isso confundia três responsabilidades distintas:

1. telemetria ao vivo;
2. buffer curto para absorver picos de processamento;
3. histórico durável da sessão para auditoria/exportação.

A sessão gravada já existe para a terceira responsabilidade. Manter dezenas de milhares de closures/tarefas antigas em RAM duplicava esse papel e aumentava a idade da decisão.

## Por que os testes não detectaram

O contrato anterior verificava que o aprendizado era retirado da thread da ECU e preservava ordem, mas não exercitava carga sustentada numa multimídia lenta. Em particular, faltavam provas de:

- capacidade máxima do backlog quente;
- latência máxima sob sobrecarga;
- geração de sessão USB;
- purge de trabalho quente obsoleto;
- recuperação após pico;
- política explícita para janelas correlacionadas sobrepostas.

O gate verde anterior, portanto, provava isolamento da thread ECU, mas não provava aprendizado em tempo real.

## Correção

A branch `fix/learning-realtime-curve-obd` substitui o pipeline de aprendizado por `RealtimeLearningBuffer`:

- geração vinculada à sessão USB;
- buffer de evidência quente com limite duro de três janelas pendentes;
- quando saturado, a janela pendente sobreposta mais antiga é substituída pela mais recente para limitar a idade do cálculo;
- decisões transitórias são coalescidas para somente a mais recente;
- worker roda acima de `MIN_PRIORITY`;
- troca/desconexão USB dá uma janela curta de drenagem e depois purga o trabalho quente restante da geração encerrada;
- trabalho de geração antiga não pode publicar estado na sessão nova;
- a sessão gravada permanece como histórico frio/durável e não é apagada pelo purge quente;
- persistência substituível continua assíncrona/coalescida;
- critérios de RPM, MAP, temperatura, pressão e equivalência não são afrouxados.

## Testes de regressão

Foram adicionados/atualizados contratos para exigir:

- 5.000 quadros transitórios gerarem no máximo um transitório pendente;
- limite duro de três evidências quentes mesmo se o chamador pedir capacidade maior;
- sobrecarga manter as janelas pendentes mais recentes e contabilizar `supersededImportant`;
- nova geração USB purgar fila antiga e rejeitar submissão stale;
- fila permanecer pequena e observável por métricas;
- persistência continuar coalescida;
- aprendizado continuar fora da thread ECU.

O gate Android também executa `RealtimeLearningBufferTest` via testes JVM.

## Evidência necessária para fechar o incidente

A correção só pode ser considerada fisicamente fechada depois de uma sessão real na multimídia demonstrar simultaneamente:

- gasolina refletida na memória em segundos, não minutos;
- GNV refletido em segundos;
- `pending` permanecer pequeno e retornar para perto de zero após picos;
- `lastQueueDelayMs` não crescer continuamente;
- desconectar/reconectar não transportar backlog quente da sessão anterior;
- nenhuma regressão na telemetria da ECU;
- memória aprendida e sugestões coerentes com a condição física observada.

## Risco residual

Sob CPU extremamente saturada, janelas móveis correlacionadas podem ser substituídas no buffer quente antes do processamento. Isso é deliberadamente observável em `supersededImportant`, evita acumular minutos de atraso e mantém o histórico bruto na sessão. A validação física deve confirmar que a taxa efetivamente processada é suficiente para cobertura de calibração; se não for, o próximo passo deve otimizar o custo interno de `MotorLearningMemory`/assessor, não aumentar novamente a fila em RAM.
