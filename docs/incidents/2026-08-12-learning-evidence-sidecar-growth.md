# Incidente — sidecar de evidência do Learning crescia sem orçamento

Data: 2026-08-12
Estado: correção preparada localmente; publicação remota pendente
Branch alvo: `work/v8.2-clean`

## Sintoma e impacto

O arquivo `learning_v6_evidence.json` representava uma fotografia substituível, mas duas estruturas internas podiam crescer sem limite global:

- `nativeEvidence`, indexado por `snapshotId:band`;
- `visitAccumulators`, indexado por `visitId:regionId`.

Isso permitia que o passado aumentasse continuamente memória ocupada, custo de serialização e custo de restauração. A escrita em disco já era coalescida, porém o JSON completo era montado **antes** da coalescência; várias solicitações podiam portanto repetir o trabalho caro mesmo quando somente a fotografia mais nova chegaria ao disco.

## Causa imediata

`SignalLearningStore.persistEvidenceState()` criava `JSONObject` + `JSONArray` integralmente no chamador e só depois enviava a `String` pronta para `CoalescedSnapshotWriter.submit()`.

Ao mesmo tempo:

- `nativeEvidence` não possuía limite por quantidade de snapshots;
- `visitAccumulators` não possuía limite por quantidade de entradas;
- somente `provenanceHistory` já era limitado a 64 elementos;
- não havia teto explícito por bytes para o sidecar persistido.

## Causa estrutural

A arquitetura tratava um estado derivado/diagnóstico como se pudesse crescer indefinidamente junto com o histórico, embora ele participe do estado quente do Learning e seja relido na restauração.

Coalescer somente o I/O era insuficiente: o custo de construir snapshots antigos continuava no caminho de processamento do Learning.

## Por que os testes anteriores não detectaram

O contrato de backpressure verificava que o arquivo era gravado por `CoalescedSnapshotWriter`, mas exigia literalmente `submit(payload.toString())`. Assim, o próprio teste cristalizava a coalescência **depois** da serialização e não media o custo de construção do payload.

Também não existiam testes para retenção máxima de snapshots nativos, acumuladores de visita ou teto de bytes do sidecar.

## Correção preparada

1. `CoalescedSnapshotWriter` ganhou `request { ... }`: o provider do payload mais recente é substituível e só é executado na thread de persistência quando uma gravação realmente será feita.
2. `SignalLearningStore` captura apenas uma fotografia imutável e barata dos objetos e deixa `JSONObject`/`JSONArray` para a thread de persistência.
3. Novo `LearningEvidenceBudget` define:
   - até 16 snapshots nativos completos;
   - até 256 acumuladores de visita mais recentes por atividade;
   - até 64 entradas de proveniência;
   - teto persistido de 256 KiB.
4. Sob pressão excepcional de bytes, a fotografia persistida reduz primeiro derivados mais antigos até caber no orçamento. O núcleo científico principal de `MotorLearningMemory` não é reescrito por esta política.
5. A resposta de telemetria rápida (`includeAdvisor=false`) deixa de montar `native_ecu_evidence` por frame. Evidência pesada permanece disponível em status/export completos.
6. O schema do sidecar passa para `omegas-learning-evidence-v6-v2`, mantendo leitura compatível do v1 existente.

## Teste de regressão

- `tests/test_learning_evidence_budget_contract.py` compila e executa o `LearningEvidenceBudget.kt` real com `kotlinc` e prova retenção dos snapshots/visitas mais recentes.
- `CoalescedSnapshotWriterTest` ganha cenário que envia 100 providers e exige que a maioria nem seja executada quando estados intermediários são coalescidos.
- `SignalLearningStoreTest` ganha cenário de 32 snapshots nativos e exige somente os 16 mais recentes completos, além do teto do arquivo persistido.
- `tests/test_multimedia_telemetry_backpressure_contract.py` foi corrigido para proibir o contrato antigo `submit(payload.toString())` e exigir coalescência antes da construção do payload.
- `python -B tools/run_checks.py` -> `QUALITY_GATE_FAST=PASS` após a atualização.

## Evidência

Base remota revalidada antes do bloco: `work/v8.2-clean@40aa1769460c771f36d7bf7feca25893a051483c`, tree `003cb85e86fd40ad526defbe7781e4afe071c903`.

Checkpoints Notion relacionados: CP-087, CP-089 e CP-090. O trabalho permanece fora do GitHub até formar publicação relevante.

## Risco residual

- Gradle/JUnit Android completo continua pendente porque a distribuição/dependências não estão disponíveis nesta bancada offline.
- Os limites são de estado derivado; é necessário validar no celular que a confiança adaptativa continua estável em sessões longas e que o sidecar permanece abaixo do orçamento real.
- O núcleo `MotorLearningMemory` já possui limites por quantidade de regiões/comparações/sessões, mas ainda não possui orçamento integral por bytes; essa frente deve ser medida separadamente antes de afirmar estabilidade de horas/dias.
- Nenhuma validação física de duração foi feita ainda.