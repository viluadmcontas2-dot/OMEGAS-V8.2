# OMEGAS-WU-006 — ProgBase AutoCal parity + global reality gate

## Controle

- Estado: `ACTIVE — RENDER/GLOBAL E2E`
- Epic: #81
- Spec Kit: `OMEGAS-SK-001`
- Branch: `OmegasVerde`
- GitHub remoto é autoridade.

## Resultado observável

Ao final, AutoCal deve apresentar curvas gasolina/GNV e estado atual equivalentes ao comportamento original comprovado, com HMI mais simples e segura. Todas as superfícies dependentes de MP48/ECU devem possuir gate E2E derivado de dados reais até a maior fronteira real aplicável, incluindo WebView real em 1280×720 para comportamento visível.

## Sub-objetivo A — original como evidência

**CONCLUÍDO.**
- EXE/hash congelados.
- Portmon real destilado em fixture versionado.
- Byte/endereço → producer → cadência → consumer original mapeados em #82.
- Desconhecidos permanecem explícitos; SIL/CIU não é autoridade.

## Sub-objetivo B — OMEGAS parity

**CONCLUÍDO NO CORPUS/ORACLE.**
- #83 fechada.
- Cadeia ECU/MP48 → scheduler/runtime → NativeAutoCalMonitor → snapshot → projeção → bridge → JS mapeada.
- Forensic fan-out #13: 256 PASS / 0 RED / 0 BROKEN.

## Sub-objetivo C — correções provadas

**GREEN NO CORPUS/CI.**
- LEVELS live freshness.
- CurrentBand.
- refresh operacional ~2 s completo para os consumers de aquisição.
- refresh de referência ~4 s coerente.
- LEVELS RAW no Dashboard; porcentagem não calibrada removida.
- MAP live alinhado a S16LE com fronteira high-bit fail-closed.
- single serial authority e no-auto-write preservados.

## Sub-objetivo D — realidade visual/runtime

**ATIVO / GATE DE FECHAMENTO.**

Pipeline:
`fixture real -> maior fronteira real aplicável -> bridge real -> assets reais -> WebView real -> 1280×720 -> DOM/assertions -> screenshot + receipt`.

Estado:
- Android emulator/KVM: provado.
- app + instrumentation APK: build/install provados.
- harness Bash versionado para executar os cenários.
- próximo GREEN necessário: Dashboard fresh LEVELS, Dashboard invalid/stale placeholder e AutoCal fresh control.
- após estabilizar o harness, ampliar no mesmo AVD para Learning, Map, Curve, OBD, reconnect e sessão.

## Sub-objetivo E — produto humano

**PARCIALMENTE CONCLUÍDO.**
- LEVELS RAW está na superfície principal sem semântica física inventada.
- #86 provou que AutoCal já pertence à sessão canônica; não há segundo recorder/export authority.
- UX ainda precisa de prova renderizada antes de PASS de produto.

## Estratégia de execução

### Padrão
GitHub Actions é executor primário.

### Paralelismo
- até 256 lanes direcionadas no fan-out forense;
- fan-out global dinâmico por contrato real de produto;
- integração/writes compartilhados permanecem serializados.

### Uso local excepcional
MMMACHINE/AgentRed apenas quando a próxima pergunta depende de bytes ainda não destilados do EXE, Portmon bruto, Drive ou log pesado. Uma vez versionado o fixture/oracle, o gate deve ser independente da máquina local.

## Fechamento

A WorkUnit fecha somente quando:
- #82–#86 e repairs relacionadas estiverem reconciliadas;
- #84/#85/#95 atingirem o gate renderizado aplicável;
- STATUS/spec/plan/evidence refletirem o SHA final;
- CI canônica estiver verde no mesmo SHA final;
- screenshots/receipts estiverem arquivados;
- limites de evidência física forem declarados;
- nenhuma alegação de veículo real for inferida apenas de CI/emulator.

## NON-GOAL

Portar SIL/CIU ou gerar APK final sem autorização explícita do owner.
