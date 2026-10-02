# OMEGAS VERDE — Scientific Coverage Matrix

Baseline científico de consolidação: `d51b26d0f0886c24ad71a69e9e4801b8a4b26181`  
Tracker: #50  
Status: **runtime scientific coverage closed; final CI revalidation pending on this documentation HEAD**

## Matriz final

| Descoberta | Requisito runtime | Seam principal | Evidência | Status final |
|---|---|---|---|---|
| F2 é prior congelado | Coeficientes exatos; nenhum refit em runtime | `AdaptivePetrolReference.f2` | `test_verde_scientific_runtime_contract.py`, unit suite | **IMPLEMENTADO + TESTADO** |
| S carry-forward | S aceito sobrevive à sessão seguinte | `AdaptivePetrolScaleState`, `MotorLearningMemory` | `MotorLearningScaleCarryForwardTest` | **IMPLEMENTADO + TESTADO** |
| S atualiza cautelosamente | Janela/sessão enganosa não reseta S agressivamente | `promoteScale` | prior `1.065612`, coerente `1.066815`, enganoso `1.10058`; limite 0,5%/sessão | **IMPLEMENTADO + TESTADO** |
| Residual local exige curvatura | Quadrático anisotrópico com gasolina real | `AdaptivePetrolReference` | 240 rpm / 0,060 bar / max 60 / ridge 0,001 + unit suite | **IMPLEMENTADO + TESTADO** |
| Referência gasolina contínua | GNV pode receber referência sem par discreto | `AdaptivePetrolReference`, reconciler e live memory | `MotorLearningAdaptiveReferenceTest`, `AssistedCalibrationReconciliationEndToEndTest` | **IMPLEMENTADO + TESTADO** |
| Predição != evidência | Referência estimada não cria visita/amostra física | reconciler/memory | regressões #48 + consolidation suite | **IMPLEMENTADO + TESTADO** |
| Gasolina física posterior vence previsão | `PRIOR_PLUS_RESIDUAL` pode ser promovido para referência física sem voto duplicado | `LearningSnapshotReconciler` | `AdaptivePetrolReferenceRefreshTest` | **IMPLEMENTADO + TESTADO** |
| Geometria gasolina = RPM×MAP | Referência não casa gasolina por Petrol Inj | `PetrolReferenceSelector`, adaptive reference | equivalence/reference tests | **IMPLEMENTADO + TESTADO** |
| Geometria Mapa K = RPM×Petrol Inj | Residual local vai aos pontos físicos com bilinear | `ContinuousLearningMath`, advisor | `ContinuousLearningMathTest`, advisor tests | **IMPLEMENTADO + TESTADO** |
| Curva global antes do Mapa residual | Remover tendência global antes do residual local | `AssistedCalibrationAdvisor` | ordem `globalCurve → residualMap`; scientific runtime gate | **IMPLEMENTADO + TESTADO** |
| Estado científico é Curve+Map material | Mesmos valores materiais => mesma identidade física | `CalibrationStateV7.materialFingerprint()` | `CalibrationCausalTransitionV7Test` | **IMPLEMENTADO + TESTADO** |
| GNV pertence ao estado que o produziu | Pós-intervenção não pode confirmar com evidência da revisão anterior | `calibrationTransitions` | filtro `revision == after.revision` + causal tests | **IMPLEMENTADO + TESTADO** |
| Readback define intervenção concluída | Causalidade nasce somente depois de write/readback aplicado | V7 runtime/checkpoints/transitions | runtime + readback lifecycle tests | **IMPLEMENTADO + TESTADO** |
| Primeiro passo ≈ 0,75 | Sem escalada por quantidade de visitas | `AssistedCalibrationAdvisor` | todas as fractions base = 0,75; advisor tests | **IMPLEMENTADO + TESTADO** |
| ~0,90 só após resposta causal real | Última transição do mesmo alvo físico precisa ser `CONFIRMED` | `AdvisorSuggestionAdapterV7` + coordinator | `AdvisorSuggestionAdapterV7CausalStepTest`, wiring contract | **IMPLEMENTADO + TESTADO** |
| Confirmação velha não vence contradição nova | Autoridade causal é a transição mais recente do mesmo alvo | adapter | `contradictedOrUnrelatedTransitionCannotUnlock090` | **IMPLEMENTADO + TESTADO** |
| Não reaprender ganho do zero | Usar prior de resposta transparente, sem learner opaco | advisor/runtime | código + scientific runtime gate | **PRESERVADO + TESTADO** |
| TRUST falho não é autoridade de escrita | Confidence é metadata/readiness; escrita continua humana | advisor/writer/UI | manual-review contracts + writer tests | **PRESERVADO + TESTADO** |
| Sem auto-write | Review humano + ACK/readback obrigatórios | Map/Curve screens → Native API → Kotlin writer | clean UI contracts + writer/readback tests | **IMPLEMENTADO + TESTADO** |
| Gasolina coletável a qualquer momento | Antes/depois de GNV, sugestão e readback | learning stores/memory | regressões #48 + unit suite | **IMPLEMENTADO + TESTADO** |
| Sem hardcode MAP≈0,775 | Nenhuma spline/regra especial de produção | adaptive/runtime | scientific runtime gate proíbe `0.775`/`.775` | **REJEITADO COMO ESPECIAL-CASE; PRESERVADO** |
| Sem ML opaco | Matemática auditável e determinística | learning/runtime | code audit + runtime gate | **PRESERVADO** |
| Valor primário da célula deve ser robusto | Uma visita isolada não pode substituir verdade consolidada | `LearningStabilityV7`, `learning.js` | `LearningStabilityV7Test`, learning UI tests | **IMPLEMENTADO + TESTADO** |
| REVALIDATING preserva verdade consolidada | Tendência nova aparece separada até repetir | stability/runtime/UI | isolated-outlier + repeated-change tests | **IMPLEMENTADO + TESTADO** |
| Visitas independentes > frames brutos | Bilinear não transforma 1 visita em 4; ESS/visitas sustentam promoção | `LearningStabilityV7` | `LearningStabilityV7Test` | **IMPLEMENTADO + TESTADO** |
| Gaussian espacial extra não é autorizado | Não duplicar smoothing sobre a bilinear sem falsificação | `ContinuousLearningMath` | runtime gate exige helper existente porém não conectado | **REJEITADO/PRESERVADO** |
| Prior espacial histórico não vira verdade hardcoded | Fraqueza histórica pode informar investigação, não regra fixa | adaptive/stability | ausência de hardcodes e TRUST automático | **REJEITADO COMO HARDCODE; PRESERVADO** |

## Volatilidade — diagnóstico e fechamento

O problema observado na UI era real: durante `LEARNING`, a projeção podia cair para uma comparação crua individual apesar de `LearningStabilityV7` já possuir centro robusto. Isso permitia sequências visuais equivalentes a `+1,7 → +15 → -10` sem que o estado científico consolidado tivesse realmente mudado.

O fluxo final usa:

- `LEARNING` → centro robusto recente (`recentErrorPercent`);
- `CONSOLIDATED` → `consolidatedErrorPercent`;
- `REVALIDATING` → mantém o consolidado como valor principal e expõe a tendência recente separadamente;
- nova geração só nasce quando a mudança contrária se torna repetível.

Probe/regressão: a série crua usada para falsificação produziu salto máximo de aproximadamente **25 pontos percentuais**. Uma observação contrária isolada colocou o estado em `REVALIDATING` sem mover o consolidado; evidência contrária repetida promoveu corretamente uma nova geração. Não foi adicionado EMA cosmético nem Gaussian adicional.

## Decisão sobre `gaussianSpatialKernel`

O helper existe em `ContinuousLearningMath`, mas permanece deliberadamente **não conectado**. A hipótese de que as células eram independentes foi falsificada pelo código: cada observação já se distribui aos pontos físicos vizinhos por interpolação bilinear RPM×Petrol Inj. Conectar outro Gaussian sem nova evidência seria dupla suavização e poderia apagar curvatura/inversão física real.

## Gates de consolidação

Os seguintes gates são permanentes no repo:

- `tests/test_causal_step_wiring_contract.py`
- `tests/test_verde_scientific_runtime_contract.py`
- `tests/test_clean_ui_contract.py`
- `tests/test_ux_didactic_expansion_contract.py`
- Kotlin unit tests de AdaptiveReference, S carry-forward, estabilidade, causal transition, advisor e writer/readback.

Última evidência anterior a esta atualização documental:

- HEAD funcional: `4eb89a0d58b99d2ea56c73bcd367f70d29015bca`
- Fast contracts: **SUCCESS** (`35035432656`)
- Main CI: **SUCCESS** (`35035432629`)
- `testDebugUnitTest`: **SUCCESS**
- `assembleDebug`: **SUCCESS**
- Hash APK: **SUCCESS**
- Publish artifact: **SUCCESS**
- Artifact: `omegas-verde-debug-4eb89a0d58b99d2ea56c73bcd367f70d29015bca`
- Artifact archive digest: `sha256:603aa169b8ce798b07adc2a919f792a3ef09ca0f844a7f56c4f5023da619f72d`

Esta documentação cria um novo HEAD. O APK só deve ser chamado de entrega final depois que **fast contracts + main CI** também ficarem verdes nesse HEAD documental.
