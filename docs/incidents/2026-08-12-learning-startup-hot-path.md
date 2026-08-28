# Incidente — Learning pesado no caminho crítico de startup

Data: 2026-08-12
Estado: correção preparada localmente; publicação remota pendente
Branch alvo: `work/v8.2-clean`

## Sintoma e impacto

Após uso prolongado, o aplicativo pode ficar progressivamente mais caro para reabrir. O relato físico do proprietário inclui sessões em que, depois de cerca de 30 minutos ou mais de uso/learning, o app deixa de abrir normalmente até limpar os dados.

A auditoria atual não atribui todo o sintoma a uma única exceção física ainda não capturada, mas confirma uma causa estrutural capaz de aumentar o custo de abertura: a inicialização do serviço cria o runtime e, durante o construtor do Learning, lê/reconstrói memória persistida, sidecar de evidência e Advisor antes de o Learning ficar disponível.

## Causa imediata

O caminho observado era:

`MainActivity.onCreate -> startHubService -> TelemetryForegroundService.onCreate -> NativeRuntimeManager -> LiveOnlyLearningStore -> SignalLearningStore -> MotorLearningMemory`

Nesse caminho síncrono:

- `MotorLearningMemory.init` executa `load()` e `rebuildVisualStatusFromMemory()`;
- `readValidState()` lê JSON integral e valida digest;
- regiões, comparações e sessões são materializadas;
- `SignalLearningStore` inicializa `advisor = analyzeCurrentMemory()`;
- `analyzeCurrentMemory()` exporta a memória e roda `AssistedCalibrationAdvisor`;
- `loadEvidenceState()` lê `learning_v6_evidence.json` integralmente.

## Causa estrutural

O presente dependia do tamanho e do custo do passado para ficar operacional. Telemetria, serviço e UI podiam ter sua abertura atrasada por restauração científica/histórica que não é necessária para começar a observar a ECU.

A mesma investigação confirmou uma segunda causa acoplada: `nativeEvidence` e `visitAccumulators` do sidecar não possuíam orçamento global e o JSON era construído antes da coalescência. Essa frente foi incorporada ao mesmo bloco de estabilização e está documentada em `2026-08-12-learning-evidence-sidecar-growth.md`.

## Por que os testes anteriores não detectaram

Os testes existentes cobriam backpressure em telemetria/Learning durante execução e coalescência de persistência, mas não existia contrato específico exigindo que o construtor do runtime permanecesse independente da restauração persistida.

CI verde, portanto, não provava startup com base grande ou restaurador lento.

## Correção preparada

Foi introduzido `DeferredLiveOnlyLearningStore` como fronteira de restauração:

- migração + leitura + reconstrução + Advisor ficam em `omegas-learning-restore`, thread daemon dedicada;
- runtime nasce imediatamente com estado explícito `LEARNING_RESTORING`;
- telemetria continua independente;
- frames que chegarem antes do READY não entram na memória científica e são contabilizados como `skippedFramesWhileRestoring`;
- export, merge, snapshot contextual e preview manual recusam rapidamente enquanto o Learning não está pronto, sem bloquear;
- uma escrita K já confirmada por humano + ACK/readback durante a restauração guarda o ajuste mais recente e aplica a invalidação da evidência GNV antes de expor o Learning como READY;
- falha de restauração mantém telemetria disponível e não apaga memória automaticamente.

A matemática de aprendizado, USB, protocolo, writers, Mapa K, Curva K e OBD não foram alterados neste bloco.

## Teste de regressão

Adicionados:

- `tests/test_startup_learning_restore_contract.py` no gate rápido;
- `DeferredLiveOnlyLearningStoreTest.kt` para comportamento de construtor não bloqueante e ajuste confirmado diferido.

Provas locais executadas:

1. `python -B tools/run_checks.py` -> `QUALITY_GATE_FAST=PASS`;
2. compilação do arquivo Kotlin real `DeferredLiveOnlyLearningStore.kt` com `kotlinc` e backend controlado/lento;
3. harness comportamental -> `DEFERRED_LEARNING_BEHAVIOR=PASS`, construtor em aproximadamente 10 ms no ensaio.

O Gradle/JUnit Android completo não foi executado porque o ambiente local não consegue resolver `services.gradle.org` para baixar a distribuição Gradle 8.9. Isso não foi substituído por GitHub Actions, seguindo a regra de economia de Actions.

## Evidência

Código remoto auditado antes da alteração: `work/v8.2-clean@40aa1769460c771f36d7bf7feca25893a051483c`, tree `003cb85e86fd40ad526defbe7781e4afe071c903`.

Checkpoints Notion relacionados: CP-086, CP-087 e o checkpoint de implementação local deste bloco.

## Risco residual

- Ainda falta publicar e compilar o projeto Android completo em ambiente com Gradle disponível.
- Ainda falta teste no celular/multimídia com base Learning grande real.
- Frames científicos recebidos enquanto a restauração ainda está pendente não são absorvidos pelo Learning; a telemetria bruta permanece no SessionRecorder. Essa política evita backlog e deve ser medida fisicamente.
- O sidecar agora possui orçamento local preparado, mas ainda precisa validação Android/física de longa duração.
- A primeira telemetria pode tentar checkpoint enquanto o Learning restaura; atualmente essa tentativa falha rápido e não bloqueia. A política definitiva desse checkpoint continua frente separada.
