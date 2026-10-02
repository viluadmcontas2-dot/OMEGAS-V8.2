# OMEGAS-WU-006 — ProgBase AutoCal parity + global reality gate

## Controle

- Estado: `FINAL GATE — SAME-SHA REVALIDATION`
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

**GREEN NO SHA DE PRODUTO / REVALIDAÇÃO FINAL NO HEAD.**

Pipeline:
`fixture real -> maior fronteira real aplicável -> bridge real -> assets reais -> WebView real -> 1280×720 -> DOM/assertions -> screenshot + receipt`.

Estado:
- Android emulator/KVM: provado.
- app + instrumentation APK: build/install provados.
- harness Bash versionado para executar os cenários.
- Android render #61 / `35809110846` no SHA `aee50900b907c44de2cb8c562ed998b8a5d39314`: 13 cenários PASS com receipts/screenshots 1280×720 inspecionados.
- cenários: Dashboard fresh/invalid, AutoCal fresh/reference/shifted/**sparse-zone-map**, Learning, Map, Curve offline/original-derived, OBD offline, session invalidated/recovered.
- `autocal-sparse-zone-map` prova a HMI de identidade espacial Z1..Z4 com `OK/FALTA/AGORA`; a mutação esparsa é explicitamente visual-only e não é usada como oracle científico.
- shifted permanece `SYNTHETIC_NON_SCIENTIFIC / VISUAL_ONLY_NON_SCIENTIFIC`; AutoCal reference e Curve K positive usam bytes ORIGINAL_DERIVED de Lognovo/ProgBase.
- Curve K offline ganhou RED renderizado #44 e correção mínima em `87a4ffbd...`: falha agora sai de `is-reading` e apresenta “Curva não confirmada”.
- global reality #36: 159 lanes PASS / 0 RED / 0 BROKEN.
- como commits posteriores alteraram o workflow autorizado de APK, o fechamento exige rerun dos gates no HEAD corrente.

## Sub-objetivo E — produto humano

**CONCLUÍDO NO ESCOPO DETERMINÍSTICO/RENDER.**
- LEVELS RAW está na superfície principal sem semântica física inventada.
- #86 provou que AutoCal já pertence à sessão canônica; não há segundo recorder/export authority.
- UX foi provada em WebView Android real 1280×720 nos cenários vinculantes. Isso não equivale a validação física no veículo.
- O resumo N/4 não é mais a única orientação: os flags nativos `0x016F/0x0170` aparecem como Z1..Z4 com estado `OK/FALTA`, e CurrentBand orienta a zona `AGORA`.
- A curva principal foi despoluída: a narrativa live redundante fica fora da superfície visual principal; RPM, Petrol Inj. e MAP permanecem nos cards.
- A identidade de ações foi reconciliada diretamente com RTTI/disassembly do ProgBase: AutoMatch=`0x01`, ResetPetrol=`0x02`, ResetGas=`0x04`, Modify refs=`0x08`; ResetAll usa rota separada.
- O efeito amplo original de ResetGas está documentado; resets destrutivos permanecem intertravados e exigem backup pré-mutação durável antes de qualquer envio.

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
- #84/#85/#95 permanecerem reconciliadas e #98 tiver a correção P0 revalidada;
- STATUS/spec/plan/evidence refletirem o SHA final;
- CI canônica estiver verde no mesmo SHA final;
- screenshots/receipts estiverem arquivados;
- limites de evidência física forem declarados;
- nenhuma alegação de veículo real for inferida apenas de CI/emulator.

## NON-GOAL

Portar SIL/CIU ou gerar APK final sem autorização explícita do owner.
