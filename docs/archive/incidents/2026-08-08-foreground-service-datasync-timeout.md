# Incidente — Foreground service classificado como `dataSync`

Data: 2026-08-08  
Status técnico: correção aplicada na branch `feature/ux-didactic-expansion`; validação física ainda pendente.

## Sintoma e impacto

Foi observado em Android um `ForegroundServiceDidNotStopInTimeException` associado ao `TelemetryForegroundService` e ao tipo `dataSync`.

Efeito prático: mesmo com o OMEGAS projetado para manter a comunicação MP48/OBD fora da tela principal, o Android podia tratar o serviço como uma sincronização de dados temporária, sujeita a regras de timeout que não representam a função real do aplicativo. Isso reduz a previsibilidade do monitoramento em segundo plano e pode encerrar o serviço.

## Causa imediata

`TelemetryForegroundService.startForegroundCompat()` adicionava `FOREGROUND_SERVICE_TYPE_DATA_SYNC` em Android 14+ independentemente da atividade real do serviço.

O manifesto também declarava `dataSync` junto de `connectedDevice|location`.

## Causa estrutural

O serviço agregou historicamente responsabilidades de telemetria, persistência, GPS e sincronização. A classificação de foreground foi ampliada por soma de capacidades, em vez de representar a atividade contínua dominante.

A função dominante do serviço é comunicação persistente com hardware conectado por USB/Bluetooth. Persistir telemetria e sessões é consequência dessa comunicação e não transforma o serviço em uma operação `dataSync` de longa duração.

## Por que os testes anteriores não detectaram

Os contratos anteriores verificavam presença de foreground service, `START_STICKY`, `stopWithTask=false`, WakeLock e recuperação da engine, mas não protegiam a semântica dos tipos declarados no manifesto e no `startForeground()`.

Além disso, CI verde não exercitava a política temporal do Android com o aplicativo realmente fora de foco por longos períodos.

## Correção

1. removido `FOREGROUND_SERVICE_DATA_SYNC` do manifesto;
2. removido `dataSync` de `android:foregroundServiceType`;
3. `connectedDevice` passa a ser o tipo permanente da sessão de monitoramento;
4. `location` é acrescentado somente quando GPS está efetivamente ativo;
5. `CHANGE_NETWORK_STATE` é declarado como pré-requisito normal compatível com `connectedDevice`, coerente também com as capacidades de rede local do aplicativo;
6. ao ligar/desligar GPS, o serviço atualiza a declaração runtime dos tipos de foreground.

Nenhuma regra de ECU, USB, aprendizado, Mapa K, Curva K ou writer foi alterada.

## Teste de regressão

`tests/test_ux_didactic_expansion_contract.py` protege que:

- `dataSync` e `FOREGROUND_SERVICE_DATA_SYNC` não voltem ao manifesto;
- `FOREGROUND_SERVICE_TYPE_DATA_SYNC` não volte ao serviço;
- `connectedDevice` permaneça declarado;
- `START_STICKY` e `stopWithTask=false` permaneçam;
- a expansão continue usando a única autoridade visual e o único scheduler.

O Quality Gate da branch foi ajustado para comparar o estado acumulado contra `main`, exigindo JVM + lint enquanto existir qualquer mudança Android acumulada, mesmo que o último commit altere apenas testes ou documentação.

## Evidência

Fonte atual na branch:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt`
- `tests/test_ux_didactic_expansion_contract.py`
- `.github/workflows/quality-gate.yml`

Referência normativa consultada durante o diagnóstico: documentação Android vigente sobre tipos de foreground service e limites de `dataSync` em Android 15+.

## Risco residual

A correção de classificação pode ser validada por JVM/lint, mas comportamento real em segundo plano depende também de:

- versão Android do aparelho;
- política do fabricante para bateria/processos;
- permissões USB/Bluetooth/GPS;
- estado de otimização de bateria;
- comportamento após tela apagada, app removido da lista recente e longos períodos sem Activity visível.

Portanto, até teste físico, o estado correto é **AGUARDANDO CELULAR**. Não declarar segundo plano resolvido no S23/multimídia apenas com CI.

## Validação física necessária

1. conectar MP48 e confirmar telemetria válida;
2. iniciar/confirmar aprendizado;
3. apagar a tela e manter o app em segundo plano;
4. reabrir após janelas crescentes e verificar continuidade da sessão/evidência;
5. repetir com OBD Bluetooth conectado;
6. testar retorno após perda e reconexão USB;
7. verificar notificação persistente e ausência de exceção de timeout;
8. repetir com otimização de bateria padrão e, se houver falha, diagnosticar a política OEM antes de pedir qualquer exceção ao usuário.
