# RED Continuous Fast Learning Implementation Plan

> **For agentic workers:** execute inline with Superpowers TDD; every production change requires a failing behavioral test first.

**Goal:** entregar procedência auditável, transferência global+local, sugestões rápidas, Auto-Cal acessível e autonomia manual com K 100–180.

**Architecture:** preservar memória primária e adicionar projeção contínua derivada no Advisor; tendência global por tempo de referência, residual RPM×MAP e grade física apenas no final.

**Tech Stack:** Kotlin/JVM, `org.json`, JavaScript CommonJS/DOM, Python contracts, Gradle Android.

**Spec:** `docs/superpowers/specs/2026-08-30-red-continuous-fast-learning-design.md`

## Restrições globais

- branch `hotfix/v8.0-red-performance`; Issue #9;
- nenhuma escrita automática; confirmação/ACK/readback preservados;
- Mapa K 100–180; Notion/Linear não são autoridade;
- nenhum transporte indiscriminado da V8.2.

### Task 1: Repo-first Spec Kit

- [x] Criar Issue #9.
- [x] Versionar AGENTS/PROJECT/STATUS/WorkUnit/spec/plano.
- [x] Executar gates documentais e publicar checkpoint.

### Task 2: Limites e RPM

- [x] RED: permitir escrita manual acima de 1.200 RPM com demais requisitos verdes.
- [x] GREEN: remover somente a guarda por RPM.
- [x] RED: limites 100/180 e rejeição 99/181 no planner, Advisor e writer.
- [x] GREEN: centralizar o limite operacional solicitado `MAX_ALLOWED_K=180`, sem confundi-lo com o limite U8 do protocolo.

### Task 3: Procedência

- [x] RED Kotlin para procedência completa da comparação.
- [x] RED JS proibindo agregado de ser rotulado como par observado.
- [x] Persistir suporte/distância/spread/época/hash com defaults legados.
- [x] Exibir par usado e resumo projetado em seções distintas.

### Task 4: Campo contínuo

- [x] RED: observação informa vizinhos próximos.
- [x] RED: residual distante não é copiado e global permanece disponível.
- [x] RED: independência por `visit_id` e incerteza explícita.
- [x] Implementar campo bounded RPM×MAP e projeção 144 células.

### Task 5: Sugestões e UI

- [x] RED: sugestão local prevista com suporte próximo.
- [x] RED: abstention para `GLOBAL_ONLY|UNKNOWN`.
- [x] Integrar previsões sem quebrar observações existentes.
- [x] Mostrar direto/previsto/global/desconhecido e incerteza.

### Task 6: Auto-Cal e regressões

- [x] Executar regressão existente do controlador Auto-Cal.
- [x] Adicionar integração ajuste global→Auto-Cal se houver lacuna.
- [x] Corrigir somente se a reprodução falhar.

### Task 7: Verificação e publicação

- [x] Testes focados Node/Python/JVM.
- [x] Gate rápido, Gradle afetado e suíte ampla proporcional.
- [x] Inspecionar diff e custo bounded.
- [x] Publicar commits, revalidar SHA e atualizar STATUS/evidência/Issue.

## Fechamento

- SHA provado: `e8c446b3cbd54194a8bc8b805b44e2770e252a93`.
- CI: [run 33313235501](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/33313235501) — contratos, Kotlin/JVM, lint e APK verdes.
- Validação física de consumo permanece separada da conclusão de engenharia.
