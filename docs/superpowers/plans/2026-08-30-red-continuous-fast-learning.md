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
- [ ] Executar gates documentais e publicar checkpoint.

### Task 2: Limites e RPM

- [ ] RED: permitir escrita manual acima de 1.200 RPM com demais requisitos verdes.
- [ ] GREEN: remover somente a guarda por RPM.
- [ ] RED: limites 100/180 e rejeição 99/181 no planner, Advisor e writer.
- [ ] GREEN: centralizar `MAX_SAFE_K=180`.

### Task 3: Procedência

- [ ] RED Kotlin para procedência completa da comparação.
- [ ] RED JS proibindo agregado de ser rotulado como par observado.
- [ ] Persistir suporte/distância/spread/época/hash com defaults legados.
- [ ] Exibir par usado e resumo projetado em seções distintas.

### Task 4: Campo contínuo

- [ ] RED: observação informa vizinhos próximos.
- [ ] RED: residual distante não é copiado e global permanece disponível.
- [ ] RED: independência por `visit_id` e incerteza explícita.
- [ ] Implementar campo bounded RPM×MAP e projeção 144 células.

### Task 5: Sugestões e UI

- [ ] RED: sugestão local prevista com suporte próximo.
- [ ] RED: abstention para `GLOBAL_ONLY|UNKNOWN`.
- [ ] Integrar previsões sem quebrar observações existentes.
- [ ] Mostrar direto/previsto/global/desconhecido e incerteza.

### Task 6: Auto-Cal e regressões

- [ ] Executar regressão existente do controlador Auto-Cal.
- [ ] Adicionar integração ajuste global→Auto-Cal se houver lacuna.
- [ ] Corrigir somente se a reprodução falhar.

### Task 7: Verificação e publicação

- [ ] Testes focados Node/Python/JVM.
- [ ] Gate rápido, Gradle afetado e suíte ampla proporcional.
- [ ] Inspecionar diff e custo bounded.
- [ ] Publicar commits, revalidar SHA e atualizar STATUS/evidência/Issue.
