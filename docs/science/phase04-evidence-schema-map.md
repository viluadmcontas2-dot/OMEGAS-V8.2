# Fase 04 — mapa de schema científico (owner 069)

Status: auditoria estrutural do contrato `producer → field → consumer`. Este documento descreve o fluxo existente; não cria segunda Store, Router, scheduler, serial ou writer.

## Regra de autoridade

- Aquisição física: `ResponseDrivenEcuEngine` / `NativeRuntimeManager`.
- Observação tipada: `RuntimeTelemetryFrame` / `CanonicalEvidence`.
- Elegibilidade: `MotorSampleAnalyzer` + `SampleDecision`.
- Memória científica: `SignalLearningStore` / `MotorLearningMemory`.
- Referência gasolina: `PetrolReferenceSelector`.
- Geometria de célula: `LearningGridProjection` somente com geometry current conhecida.
- Identidade material GNV: `LearningCalibrationBinding` / `LearningCalibrationAuthority`.
- UI é projeção; não é autoridade científica.

## Producer → field → consumer

| Producer | Campo / identidade | Consumidor principal | Papel / provenance | Lacuna ou owner futuro |
|---|---|---|---|---|
| `RuntimeTelemetryFrame` | `sequence`, `usbSessionId`, `capturedAtElapsedMs` | State, Learning, Adaptive Shadow | identidade da mesma observação física | freshness científica formalizada em 072 |
| `RuntimeTelemetryFrame` / `Mp48Telemetry` | `rpm`, `petrolMs`, `mapBar` | `MotorSampleAnalyzer`, `PetrolReferenceSelector`, `LearningGridProjection` | sinais físicos atuais | nenhum zero pode representar UNKNOWN |
| `RuntimeTelemetryFrame` / `Mp48Telemetry` | `waterC` | analyzer + reference context | contexto ambiental | knownness/freshness em 074/077 |
| `RuntimeTelemetryFrame` / `Mp48Telemetry` | `gasC` | evidence/context | contexto OMEGAS, não gate nativo provado | 075/078/079 |
| `RuntimeTelemetryFrame` / `Mp48Telemetry` | `pressureDiffBar`, pressão abs/MAP | evidence/context | pressão/MAP possui evidência nativa mais forte que gas-temp | 076/078/079 |
| `SampleDecision` | `learningEligible`, `reasonCode`, `classification`, `sample` | `SignalLearningStore` | decisão de ingest explícita | `EvidencePolicyEngine` em 071A |
| `EvidenceProvenance` | frame range, new/reused counts, novelty ratio | `SignalLearningStore` | evita dupla contagem silenciosa | posterior/marginal gain em 082A/083A |
| `ContinuousWindowNovelty` | `newFrames`, `fraction` | `SignalLearningStore` | baseline de overlap bounded | `FULLY_NEW_FRACTION=0.75` é `LEGACY_BASELINE`, não lei |
| `LearningRegion` | rpm/MAP/petrol/quality/weight/sampleCount | `PetrolReferenceSelector`, Advisor | memória agregada de condição física | sample-count deixa de ser pedágio em 073A/083A/086A |
| `BoundedRobustPetrolSummary` | median/MAD/IQR + totalObserved | gasoline region/reference | resumo robusto bounded | quantis são baseline explícito no registry |
| `LearningRegion` | visit/session counts + retained IDs | confidence/persistence | contagem exata separada de provenance compactada | budgets são `RESOURCE_BUDGET` |
| `PetrolReferenceSelector.Region` | id/rpm/MAP/water/petrol/confidence/sampleCount/updatedAt/environment | selector | candidato gasolina | 073–079 aprofundam contexto |
| `PetrolReferenceSelector.Result` | availability/reason/target/spread/quality/regionIds/stage | `MotorLearningMemory` | referência selecionada | 089 deve preservar IDs, denominador, timestamps e contexto na comparação |
| `PetrolReferenceSelector.Result` | `selectedRegionContexts`, `requestEnvironment` | diagnóstico/089 | provenance ambiental e timestamps das regiões | não descartar ao formar comparação 089 |
| `VisitComparisonAccumulator` | bounded weight, mean error, variance, independent/correlated windows | confidence/advisor | uma visita física não vira votos ilimitados | posterior/ESS em 083A/086A |
| `LearningCalibrationBinding` | calibration fingerprint/generation/geometry fingerprint/usb session | GNV evidence | impede misturar calibrações A/B | 086 já exige binding; revalidar no fluxo final |
| `LearningGridProjection` | geometry known, row/column/weights | comparison/UI projection | célula física somente com geometry current | geometry UNKNOWN preserva raw evidence em 087 |
| `FuelComparison` | referenceRegionId, petrolTargetMs, petrolOnCngMs, difference/error, context | evidence/advisor/UI | comparação atual | cálculo legado ainda contém `0.05 ms`; 070/089 são owners da correção |
| `ComparisonEvidence` | consensus/MAD/environment spans/effective samples/stage | suggestion path | actionability atual | thresholds ficam explícitos; Fast-to-Zero substitui pedágios fixos gradualmente |
| `ScientificConstantRegistry` | symbol/value/unit/source/consumer/falsifier/owner/revision/class | todos os owners científicos | provenance causal de números materiais | nenhuma `UNKNOWN` permitida silenciosamente |

## Constantes e conflitos encontrados em 069

1. `PetrolReferenceSelector` continha vários literais materiais além dos sete constants já catalogados: piso `0.05 ms`, pesos, quality floors, dominância, decay e fator de extrapolação. Foram classificados como `LEGACY_BASELINE`, sem mudar valor.
2. `VisitConfidence` contém a tabela adaptativa `3/5/7/10` e thresholds de consensus/repeatability. O Programa Fast-to-Zero diz que esses números são baselines, não pedágios universais; o registry agora deixa isso explícito.
3. `MotorLearningMemory` ainda possui caminhos legados de sugestão (`0.35`, `±5%`, preview `50..255`) e cálculo legado de erro com denominador `0.05 ms`. Eles estão registrados como dívida dos owners `070/089/F8`; 069 não altera matemática nem writer.
4. `LearningEvidenceBudget` e `LearningMemoryBudget` afetam retenção/proveniência e agora são explicitamente `RESOURCE_BUDGET`.
5. `BoundedRobustPetrolSummary` usa mediana/IQR como escolha de estimador; os quantis estão nomeados como baseline científico, sem alegação OEM.

## Regra de não promoção

`LEGACY_BASELINE` e `UNKNOWN` nunca significam verdade física, fórmula OEM ou owner-hard-bound. Um owner posterior pode manter um baseline como fixture/default experimental, calibrá-lo com evidência ou substituí-lo. O registry documenta a dependência; não concede actionability por si só.

## Falsificadores do 069

- qualquer campo de `LearningTolerancePolicy` sem entrada no registry;
- qualquer constant material conhecido do selector/budgets sem símbolo;
- símbolo duplicado;
- instância `UNKNOWN` no registry V2;
- metadata vazia (`unit/source/consumer/falsifier/owner/revision`);
- nova literal material em um decision path auditado sem entrada explícita no mapa de teste;
- schema map sem `CalibrationIdentity`, provenance, ambiente, geometry ou reference IDs.
