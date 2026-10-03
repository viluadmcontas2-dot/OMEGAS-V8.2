# Incidente — estado de sessão implícito na interface

## Sintoma e impacto
A tela exibia conexão e telemetria, mas não distinguia dados atuais de dados atrasados, observação de modo oficina, condução provável, segundo plano e reconexão. O usuário poderia interpretar números antigos como atuais e chegar ao fluxo de escrita sem uma autorização explícita de modo oficina.

## Causa imediata
A UI derivava seu estado apenas de `usbConnected` e valores instantâneos.

## Causa estrutural
Não existia um modelo puro e testável de sessão. Estado, segurança e mensagens eram tratados como detalhes dispersos da renderização.

## Por que os testes não detectaram
Os testes anteriores comprovavam mapa, navegação e ausência de escrita automática, mas não cruzavam idade da telemetria, RPM, serviço Android, visibilidade e modo de operação.

## Correção
Foi criada uma autoridade determinística de sessão em `session-state.js`, consumida pelo shell principal, sem timer adicional. O modo oficina passou a ser manual e suspenso por desconexão, telemetria insegura, comunicação travada ou condução provável.

## Teste de regressão
- matriz determinística de estados;
- 500 cenários de propriedades;
- runtime da WebView simulada;
- reconexão;
- segundo plano;
- bloqueio do writer fora do modo oficina.

## Evidência pendente
CI Android, emulador, celular, multimídia e veículo.

## Risco residual
RPM não prova movimento do veículo. A classificação é preventiva; velocidade real só poderá ser incorporada quando houver fonte confiável e contrato de produto aprovado.
