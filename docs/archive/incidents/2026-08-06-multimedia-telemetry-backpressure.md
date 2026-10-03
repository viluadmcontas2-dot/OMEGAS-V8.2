# Incidente — backpressure da telemetria na multimídia 9"

## Sintoma e impacto

Na multimídia principal do produto, a telemetria MP48 apresentava aviso recorrente de intervalo anormal em torno de `1110 ms`, atraso visual e frames de interface lentos. O mesmo APK era informado como normal no Galaxy S23+.

Impacto prático:

- a consulta seguinte à MP48 podia começar tarde;
- a multimídia mostrava dados defasados;
- o aplicativo dependia excessivamente da força do aparelho;
- o Galaxy S23+ mascarava o defeito por possuir CPU, armazenamento e WebView muito superiores.

## Evidência observada

Coleta ADB informada para a multimídia:

- processo OMEGAS com uso elevado de CPU;
- todos os frames gráficos medidos lentos;
- pausas longas de renderização;
- memória total sem pressão suficiente para explicar sozinha o defeito;
- ocorrência adicional e separada de falha USB `get_status`.

Código confirmado no `OMEGAS-V7`, branch `rebuild/ux-9in-dual-layout`:

- `ResponseDrivenEcuEngine` só inicia a próxima transação depois do retorno de `onTelemetry`;
- `NativeRuntimeManager.consumeTelemetry` executava aprendizado completo antes de retornar;
- a entrega ao serviço também ocorria na mesma chamada;
- o aprendizado pode persistir evidências e montar estruturas JSON extensas.

## Causa imediata

`NativeRuntimeManager.consumeTelemetry` chamava `learning.ingest(...)` e `onTelemetryEvent(...)` de forma síncrona dentro do callback da thread única da ECU.

Enquanto aprendizado, persistência, gravação de sessão, atualização de estado e serialização terminavam, o loop MP48 não iniciava a consulta seguinte. O intervalo medido entre quadros válidos incorporava esse trabalho pós-quadro.

## Causa estrutural

A arquitetura não separava três responsabilidades com prioridades diferentes:

1. receber e decodificar a resposta MP48;
2. publicar telemetria leve para observação;
3. processar aprendizado, persistência e diagnóstico.

A implementação funcionava em aparelho potente, mas criava backpressure em hardware automotivo mais lento.

## Por que os testes anteriores não detectaram

- os testes provavam contratos funcionais, não tempo de retorno do callback da ECU;
- não existia teste que bloqueasse artificialmente o trabalho secundário e verificasse que `submit` continuava imediato;
- não havia métrica pública de fila, atraso de fila ou tempo de processamento secundário;
- a validação anterior ocorreu no celular, sem teste prolongado na multimídia principal.

## Correção aplicada na branch de trabalho

Branch: `fix/multimedia-telemetry-backpressure`.

- adicionada fila assíncrona ordenada genérica;
- entrega ao serviço retirada da thread ECU;
- aprendizado e persistência retirados da thread ECU;
- ordem preservada por worker único em cada pipeline;
- barreiras de `flush` adicionadas para troca de sessão, exportação, ajuste confirmado e encerramento;
- métricas de pendência, atraso da fila, tempo de processamento e falhas expostas;
- quadro rápido passou a carregar somente resumo leve do aprendizado;
- estado completo continua disponível pelas APIs próprias;
- sequência assíncrona reiniciada a cada nova conexão física.

## Teste de regressão

- `OrderedBackgroundPipelineTest` prova retorno sem esperar trabalho bloqueado, preservação de ordem, drenagem e continuidade após falha;
- `test_multimedia_telemetry_backpressure_contract.py` impede:
  - `learning.ingest` síncrono antes do retorno da ECU;
  - entrega síncrona ao serviço;
  - cópia do estado completo do aprendizado no quadro rápido;
  - inversão entre entrega leve e aprendizado;
  - reutilização da sequência da conexão anterior;
  - remoção das barreiras de drenagem.

## O que permaneceu intocado

- comandos e protocolo MP48;
- baud rate, checksum, parser e escalas;
- matemática de aprendizado;
- referência de gasolina;
- Curva K e Mapa K;
- writers, checkpoint, ACK e readback;
- OBD observacional;
- proibição de escrita automática.

## Estado da validação

- código e testes adicionados remotamente;
- CI do commit final ainda sem execução consultável no momento do registro;
- APK novo ainda não identificado por SHA-256;
- comportamento ainda não validado na multimídia, celular ou veículo.

Resultado atual: `PARTIAL`.

## Risco residual

- a fila de aprendizado é ordenada e não descarta tarefas, portanto uma carga permanentemente maior que a capacidade do aparelho pode acumular pendências; as novas métricas devem revelar isso;
- persistência continua custando CPU e armazenamento, porém fora do caminho crítico da ECU;
- a falha USB `get_status` é um problema separado e pode ainda causar desconexões reais;
- somente teste prolongado na multimídia confirmará a redução de CPU, GC, frames lentos e intervalos anormais.

## Próxima prova necessária

Gerar APK do commit final, registrar SHA-256 e repetir uma sessão de pelo menos 30 minutos na multimídia, comparando:

- `lastIntervalMs`;
- pendência e atraso das duas filas;
- CPU do processo;
- eventos de coleta de memória;
- frames lentos;
- falhas USB reais.
