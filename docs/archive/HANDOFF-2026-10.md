# Arquivo: documentos de passagem de bastão (out/2026)

Resumo de um parágrafo por documento que saiu de `docs/handoff/` em 2026-10-20. O texto completo continua no histórico do git, no commit `63717bd`:

```
git show 63717bd:docs/handoff/<NOME>
```

O que ainda estava aberto nesses documentos foi para `docs/handoff/PENDENTE.md`. O que era decisão vigente do Refino está em `docs/handoff/REFINO.md`.

| Documento | Resumo |
|---|---|
| `CODEX-AUTONOMO.md` | Modo autônomo do Codex (5 h): ordem de leitura, commit+push a cada ação, um comentário por feature no issue de progresso, método RED/GREEN, emular a central, regras do que nunca fazer (apagar branch, force-push). Prioridades 0/1/2: corrigir Ferramentas vazia (feito), bins de RPM/injeção personalizáveis, README profissional (feito, ver README.md). Regras permanentes já estão em `docs/REGRAS-DO-DONO.md`. |
| `CODEX-PROXIMA-RODADA.md` | Rodada de 2026-10-19: anti-apagão adaptativo a partir de todas as sessões reais, remover "Aprendizado de outro arquivo" (feito, 5963e7d), melhorar a exportação, teste de copy para leigo; log novo `omegasaudit.LOG` (97 MB) e capturas do original. Itens abertos foram para o PENDENTE. |
| `HANDOFF-CODEX.md` | Como o Codex deveria começar (ordem de leitura, testes a rodar, usar graphify). Substituído por `AGENTS.md` e `docs/README.md`. |
| `HANDOFF-ENGENHARIA-REVERSA-CODEX.md` | Meta "nunca mais descompilar o ProgBase": fechar cobertura, ids de evidência `EV-<área>-<nnn>`, auto-auditoria, grafo, perfil da central, funções de referência da AutoCal em Ferramentas. Tudo isso segue em `docs/progbase/` e no PENDENTE. |
| `CONTRATO-CODEX.md` | Pedidos do dono ao Codex: ECU simulada ligada ao original, artefato explorável, guia curto do app, inteligência por trás (diagnóstico, marcha lenta, perfil da central, bins), dois workflows novos (`ci.yml`, `apk.yml` — feito), `tools/auditoria.py` (feito), testes locais mínimos. Pendências abertas estão no PENDENTE. |
| `PROMPT-SESSAO-NOVA.md` | Prompt de abertura de sessão de 2026-10-20: tabela de branches antigas (Vermelho, wip/codex, Platina, Cinza, main, ccr-*) e o que fazer com cada; estado técnico (APK v14 `404e83f`, SHA-256 `1abe8326...e3dc3`); regras do dono. Hoje a canônica é `OMEGASCANONICO`; as demais são antigas, não parta delas. |
| `PROMPT-CORRECAO-TELA-FERRAMENTAS.md` | Bug da aba Ferramentas vazia no WebView do APK v8 e como atacar (carga preguiçosa, exceção sem dados, CSS). Corrigido: carga preguiçosa removida e cartão "Não foi possível abrir esta parte" (teste `todas-as-telas-no-arranque`). |
| `HOOKS-PEDIDOS.txt` | Oito ganchos pedidos pelo dono em 2026-10-18 (sintetizar cada mensagem, REA em toda engenharia reversa, não parar com pendência, watchdog de subagentes, compactar cedo, testes locais mínimos, auto-auditoria). A maioria virou regra em `.claude/skills` e `.claude/hooks/regras-do-dono.txt`. |
| `RENOMEAR-BRANCHES.md` | Plano e resultado (2026-10-19) de renomear branches antigas para `apagar/<nome>` (reversível). Executado; apagar as `apagar/*` é decisão do dono na interface do GitHub. |
| `RETOMADA-2026-10-16.md` | Estado vivo de 2026-10-17: sessão completa gravada, Curva K para Mapa K parcial, prior dos nós baixos; `wip/codex-fatia1b` mesclado e revertido (19/667 testes quebrados); invalidação do Ledger por faixa descartada. Pendências (experimento de K nos nós 0-3, mockups 1/2/3, equivalência % por nó) estão no PENDENTE. |
| `OBSIDIAN-ENXUTO-CHECKPOINT-20261011.md` | Diário de 32 KB (11 a 13/10): base do APK "Obsidian" (`2c456bca`), objetivo "aprender a gasolina e deixar o GNV equivalente", poda de telas, 16 atualizações noturnas com provas. Ideias ainda abertas foram para o PENDENTE ("Ideias herdadas"). |
| `AUDITORIA.md` | Diário de uma linha por ação do Codex (19/10: contrato AutoCal, corpus de apagões, aprendizado, etc.). Substituído por `git log` e por `AUDITORIA.json` (gerado por `tools/auditoria.py`). |
| `PROGRESSO-CI.md` | Marcos de CI de 2026-10-19: contrato `autocal-ci-no-apk` ajustado para jobs separados, auditoria publicada como artefato, `apk.yml` com `run_checks.py --full`. Concluído. |

## Outros arquivos arquivados

| Documento | Resumo |
|---|---|
| `docs/MIGRATION.md` | Migração limpa para o OMEGAS V7 (origem `OMEGAS-V6:v7/end-to-end-runtime`): núcleo Kotlin e testes comportamentais preservados; removidos o shell visual consolidado, assets legados `hub`, navegação duplicada de Sugestões/Calibração, editor de mapa em página longa e chaves de assinatura do repositório. A interface ativa existe só em `app/src/main/assets/ui`. |

## Fundidos (texto preservado, não arquivados)

- `docs/handoff/REFINO.md` reúne aprendizado e fluxo do Refino, AutoMatch manual, D1, fechamento de 10-18, teia do app e plano de 10-16.
- `docs/handoff/PROGRESSO-CARTOES.md` reúne o progresso de cartões e o de velocidade de gravação.
- `docs/CONTRATOS-ARQUITETURA.md` reúne os blocos 1 a 6; `docs/OBD.md` reúne os dois documentos de OBD.
