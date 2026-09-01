# Status do OMEGAS V8.2

- WorkUnit ativa: `OMEGAS-WU-006`
- Issue: #7
- Branch ativa: `work/wu-006-calibration-science-hardening`
- Estado da WorkUnit: `IMPLEMENTING`
- Gates fechados: `G1_CORPUS_CONTRACT=PROVEN`, `G2_INDEPENDENT_REPLAY=PROVEN`, `G3_TEMPORAL_INDEPENDENCE=PROVEN_OFFLINE_METHOD`, `G4_BLIND_WALK_FORWARD=PROVEN`
- Próximo item não provado: `G5_RPM_MAP_TINJ_TUNING`
- Evidência G2–G4: `docs/evidence/OMEGAS-WU-006-G2-G4.md`
- UI/UX: congelada
- Governança: `REMOTE_FIRST_REPO_AUTHORITY=TRUE`
- Guardrails: `governance/engineering-guardrails.md`
- Política de custo: `ZERO_MONETARY_SPEND=ABSOLUTE`
- Rota pelo PC do proprietário: proibida
- Predictor: `ABSTAIN_UNCHANGED`
- `AUTO_WRITE_ECU=false`

## Contrato científico ativo

`(RPM, MAP)` identifica a condição operacional. Em gasolina aprende-se `Tinj_petrol_ref(RPM, MAP)`; em GNV observa-se `Tinj_petrol_on_CNG(RPM, MAP)`; equivalência/resíduo compara esses tempos sob suporte físico compatível. `RPM × Petrol Inj.` localiza downstream a célula do Mapa K.

Temperatura, ΔP e ambiente não são eixos do Mapa K nem dimensões primárias obrigatórias desta WorkUnit. Permanecem diagnóstico/ablação offline.

## G1 — Corpus Contract — PROVEN

- 44 ocorrências de pacotes → 33 sessões lógicas deduplicadas;
- lane científica V8: `8.0.0-test-debug` + `mp48-progbase-v2`;
- 1.708 episódios: 266 GASOLINA + 1.442 GNV;
- fixture gzip 34.846 bytes, SHA-256 `9fd4a4fda3d907af67c9c29c01b17b54cb607f13c3351b66aff553e962980d94`;
- JSONL reconstruído 347.449 bytes, SHA-256 `ae050e6770143bd042cc0416fc66cbd91d5694d7ca7917e2d9cfdf078f34a8fd`;
- 8 shards hash-bound + reconstrução fail-closed.

## G2–G4 — replay science — PROVEN no escopo offline

Fonte científica final:
- SHA `29e2f9356a45b31395e83d2c98a07552985ed7cc`
- tree `0c80dbbc790e0e0180afdfe5240a6b88b5862252`
- workflow run `33500808742`
- job `99833459688`
- artifact `9797604261`
- artifact digest `sha256:e4a787b446ea9c42d0a44d94e99f2309e53f921613288925663026996c628dcb`

Prova remota:
- 13 contratos de corpus = GREEN;
- G2 = 3/3 GREEN;
- G3 = 9/9 GREEN;
- G4 = 4/4 GREEN;
- receipt + upload artifact = GREEN;
- `leakage_violations=0`.

### G2 — Independent Replay

Fixture governado é reconstruído e verificado fail-closed. O replay não consulta `runtime sample_state`; estabilidade, gaps, trajectory break e não sobreposição são contratos independentes.

### G3 — Temporal / Session Independence

A evidência agora distingue precisão local de persistência entre sessões no lab científico.

- GASOLINA: 266 episódios; 7 regiões com suporte local; 6 com >=3 sessões independentes; 1 insuficiente.
- GNV: 1.442 episódios; 15 regiões; 13 auditadas; 2 insuficientes.
- duplicar densidade dentro da mesma sessão não cria voto independente adicional no estimator balanceado por sessão;
- LOSO e decomposição within/between-session tornam drift explícito.

`production_runtime_integrated=false`: isso fecha o método científico offline, não a integração Kotlin, que permanece G11.

### G4 — Blind Walk-Forward

247 episódios futuros de gasolina foram avaliados usando somente treino anterior.

| Estimator | Supported | Coverage | Abstention | Mean abs rel error | Median | P90 | P95 | Median independent sessions |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| WU-006 neighbor baseline | 213 | 0.8623481781 | 0.1376518219 | 0.0219332429 | 0.0125276948 | 0.0540617710 | 0.0801364710 | 3 |
| Pooled Gaussian | 231 | 0.9352226721 | 0.0647773279 | 0.0415702051 | 0.0228080838 | 0.1108447704 | 0.1566843485 | 4 |
| Session-balanced Gaussian | 231 | 0.9352226721 | 0.0647773279 | 0.0442942972 | 0.0221272123 | 0.1196947272 | 0.1566843485 | 4 |

Conclusão científica atual: os Gaussianos aumentam cobertura, porém pioram erro médio e de cauda; **não foram promovidos a modelos de produção**. G5 deve otimizar a fronteira precisão×cobertura×velocidade sem usar contagem bruta como objetivo.

## Próximo gate — G5

`G5_RPM_MAP_TINJ_TUNING`: tuning exclusivamente sob split walk-forward já congelado. Nenhum tuning pode usar sessão futura para escolher parâmetros do passado.

## Estados de claim

- `REPLAY_PROVEN_G1_G4=true` — somente para os contratos/métodos offline explicitamente exercitados;
- `MODEL_PROVEN=false`;
- `KOTLIN_RUNTIME_INTEGRATED=false`;
- `APK_READY_FOR_PHYSICAL_TEST=false`;
- `VEHICLE_PROVEN=false`.

## Release funcional anterior preservada — WU-005

- fonte funcional do APK: `da8191416d4fbd3d9b7253b10bdbe438323e8822`
- tree: `d052b6930ce3a20ab396dfbc4455ab25c8260f60`
- Android/JVM: 939 testes
- `lintDebug=PASS`, `assembleDebug=PASS`
- APK SHA-256 `e020afacf94e21eef085f36552f7f9bada4a67ee35bd0c3f631d43615adba07b`
- package `com.omegas.v7.test`
- `PHYSICAL_VALIDATION_CLAIMED=false`
