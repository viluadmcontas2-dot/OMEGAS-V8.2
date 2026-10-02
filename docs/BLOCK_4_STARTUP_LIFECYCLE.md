# Bloco 04 — Startup, lifecycle e restauração (031–040)

Base remota investigada: `work/v8.2-clean` em `8eeb1ce25d3269ed12691aafd9a4b1804f1f092f`.

## Evidência de implementação existente preservada

- `DeferredLiveOnlyLearningStore` restaura Learning em executor dedicado, publica `LEARNING_RESTORING`, `LEARNING_READY` e `LEARNING_RESTORE_FAILED` e mantém telemetria independente durante restore/falha.
- Ajuste confirmado durante restore fica pendente e é aplicado antes de expor o Learning como READY, evitando reutilizar evidência GNV antiga.
- `MainActivity` inicia/binda o `TelemetryForegroundService`, carrega a WebView e não realiza leitura completa de histórico nem checkpoint pesado em `onCreate`, `onResume` ou `onDestroy`.
- `TelemetryForegroundService` mantém o runtime fora da Activity; destruição da Activity apenas desfaz bind/bridges/WebView, sem encerrar automaticamente o serviço.

## Delta deste bloco

`StartupLifecyclePolicy.kt` centraliza os contratos que faltavam para 031–040:

- budgets explícitos: cold start 1500 ms, warm start 750 ms, reopen 1000 ms, primeira telemetria válida 3000 ms;
- Learning fora do critical path;
- telemetria independente de RESTORING/READY/FAILED;
- nenhuma leitura integral de histórico na primeira tela;
- nenhum checkpoint pesado no primeiro frame, troca de tela ou `Activity.onDestroy`;
- custo de abertura proibido de crescer com a contagem histórica acumulada.

`StartupLifecyclePolicyTest.kt` cobre essas invariantes e inclui restore corrompido como cenário fail-open apenas para telemetria, sem promover Learning inválido.

## Estado de prova

Código e testes foram adicionados remotamente. GitHub Actions não foi executado. APK não foi gerado. Portanto os budgets temporais são contrato/teste preparado, mas medição física cold/warm/reopen em dispositivo continua pendente para a fase de validação física prevista no Programa Mestre.
