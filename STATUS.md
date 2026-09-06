# Status — OMEGAS V8.2 Blue

- Branch: `work/omegas-blue-causal-engine`
- Estado: `SYSTEM_RECOVERY_IN_PROGRESS`
- Epic: `#18 BLUE-RECOVERY-001`
- Work Unit: `OMEGAS-BLUE-RECOVERY-001`
- Spec Kit: `specs/003-blue-system-recovery/`
- Autoridade: `REPO_FIRST_ENGINEERING=TRUE`
- Autoridade matemática: `BlueCausalEngine`
- Escrita automática ECU: `FALSE`
- Novo APK durante triagem: `BLOCKED_BY_RECOVERY_GATE`
- Referência funcional: `hotfix/v8.0-red-performance`

## Por que o estado anterior foi reaberto

A validação física no carro em 2026-09-06 mostrou que os gates anteriores eram insuficientes para afirmar usabilidade do APK Blue. Build, lint e contratos estáticos chegaram a ficar verdes enquanto telas e interações essenciais falhavam na WebView real.

A recuperação atual não aceita `CI verde` como sinônimo de `produto validado`.

## Regressões confirmadas

1. Agora e OBD podem abrir com área principal vazia.
2. Learning `Desvio medido` não recebe comparações Blue no payload atual.
3. Qualidade de evidência pode aparecer 0 por divergência `quality` × `confidence` na projeção.
4. Semântica de TRANSITION no `MotorSampleAnalyzer` conflita com a verdade física confirmada: durante TRANSITION ainda há gasolina.
5. Tolerâncias antigas continuam expostas e acopladas a múltiplos subsistemas; precisam de classificação/redução.
6. Telemetria é percebida como lenta/stale no carro; o estágio responsável ainda precisa ser medido.
7. Ferramentas reconstrói DOM periodicamente e pode fechar o disclosure de retenção sozinho.
8. Overlay nativo existe, mas o caminho de habilitação/permissão/restore precisa ser recuperado.
9. Curva K tem seleção pouco didática e jank físico; semântica batch precisa de prova comportamental.

## Causas já provadas

- `LearningUiSnapshotAssembler` força `comparisons=[]` e contagem 0.
- `BlueEvidenceStore` persiste `quality`; `LearningGridProjection` agrega apenas `confidence` ausente, produzindo 0.
- `Drawers.renderTools()` usa `host.innerHTML` em refresh periódico e recria `<details>`.
- O antigo hardening deixou hosts Agora/OBD vazios dependentes de bootstrap JS e usou `:has()` em layout essencial.
- O CI anterior possuía testes de presença/contrato que não inicializavam a UI real.

## Estrutura de recuperação

- #17 — bootstrap/runtime Agora e OBD.
- #19 — Learning, qualidade, Desvio, fuel boundary e tolerâncias.
- #20 — telemetria, backpressure, background e overlay.
- #21 — Ferramentas/logs/retenção.
- #22 — Curva K touch UX/performance.

## Gates atuais

- G0 Repo/Issue/Work Unit/Spec Kit: `PASS`
- G1 Triagem Red→Blue: `IN_PROGRESS`
- G2 Learning/ciência: `FAIL/OPEN`
- G3 Runtime WebView: `FAIL/OPEN`
- G4 Telemetria/background: `OPEN`
- G5 Tools: `FAIL/OPEN`
- G6 Curva K: `FAIL/OPEN`
- G7 Browser runtime regression: `FAIL/OPEN`
- G8 FAST/JVM/lint final SHA: `NOT_RUN_AS_RELEASE_GATE`
- G9 APK candidate: `BLOCKED`
- G10 Validação física: `BLOCKED_BY_G2..G9`

## Regra sobre tolerâncias

Nenhuma decisão final de remoção total foi tomada ainda. Cada regra será classificada como:
- hard truth/safety interna;
- qualidade automática de amostragem;
- contexto diagnóstico;
- legado a remover.

A hipótese de trabalho é que os perfis manuais `Muito rigoroso → Muito flexível` não devem continuar na UI normal se alterarem a verdade científica. RPM/MAP/Petrol-Inj stability pode sobreviver como política interna automática; pressão/temperatura precisam de justificativa causal antes de continuar como gate de equivalência.

## Limite da afirmação

Não existe APK Blue novo aprovado neste estado. O último APK fisicamente testado revelou regressões e não deve ser tratado como versão concluída. O próximo candidato só será montado depois do fechamento da epic #18 e dos gates comportamentais no SHA final.