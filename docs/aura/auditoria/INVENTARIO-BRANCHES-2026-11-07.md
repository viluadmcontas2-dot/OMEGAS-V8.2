# Inventário remoto e decisão de integração — 2026-11-07

Fonte: GitHub compare API, base `claude/omega-aura`, consultada nesta execução. Inventário completo: 14 branches (paginação encerrada). Não equivale a auditoria de cada commit ou teste de hardware.

| Branch | Ahead / behind da Aura | Classificação e decisão |
|---|---:|---|
| OMEGASCANONICO | 0 / 70 (comparação canônica→Aura: Aura +70, -0) | Canônica; destino protegido, ainda não mesclar |
| claude/omega-aura | 0 / 0 | Integração ativa, PR #185 |
| claude/cool-curie-hs5nk9 | 0 / 65 | Ancestral absorvida, canvas v3; não repetir merge |
| claude/carregar-sessao-r7j85r | 0 / 204 | Ancestral absorvida |
| arquivo/claude-carregar-sessao-r7j85r | 0 / 204 | Arquivo ancestral |
| OmegasVermelho | 0 / 509 | Ancestral absorvida |
| arquivo/OmegasVermelho | 0 / 509 | Arquivo ancestral |
| OmegasDiamante | 350 / 638 | Divergente histórica; examinar novidades específicas antes de reutilizar |
| arquivo/OmegasDiamante | 350 / 638 | Arquivo divergente; não mesclar |
| work/codex-audit-progbase | 349 / 638 | Divergente de auditoria; examinar provas únicas, não mesclar |
| arquivo/work-codex-audit-progbase | 349 / 638 | Arquivo divergente; não mesclar |
| evidence/progbase-original | 14 / 1722 | Divergente de evidência; verificar se 14 commits têm documentos ainda ausentes |
| arquivo/evidence-progbase-original | 14 / 1722 | Arquivo divergente; não mesclar |
| nova-experiencia | 5 / 70 | Divergente ativa, alternativa de quatro áreas; reaproveitar seletivamente ajuda/componentes, não roteador |

## Provas e bloqueios

- `OMEGASCANONICO` HEAD no compare: `28afdaa74df1cb81e84d9f4ee3261d92050ad976`.
- Aura avançou pelo menos dois commits depois do checkpoint `be4ff4d5bec6c4fbbbfc4bf819f3d047e84cdebf`, alterando `docs/grafo/PRODUCAO.md` e `docs/grafo/producao.json`. Reconsultar HEAD imediatamente antes de qualquer integração.
- Workflow de PR associado ao checkpoint `be4ff4d5...`: run `42344282974` concluído como **cancelled**; não é verde. CI do HEAD atual e demais jobs precisam de nova verificação.
- Comparação de nomes de arquivo mostra `nova-experiencia` com `components/ajuda.js`, `core/ajuda-textos.js`, `core/central-estado.js` e `core/router.js`; isso indica candidatos à revisão, não autorização para cherry-pick.
- Nenhuma mudança de protocolo, bytes, ECU, gravador, branch histórica ou dados nesta auditoria. Nenhum APK gerado.
- Próximo passo: ler patches e commits únicos das branches divergentes, registrar origem SHA e decisão por componente; comparar 23 telas com cinco áreas; exigir CI e capturas reais nas três resoluções antes do merge.

Status: inventário concluído; triagem semântica das divergentes ainda pendente.
