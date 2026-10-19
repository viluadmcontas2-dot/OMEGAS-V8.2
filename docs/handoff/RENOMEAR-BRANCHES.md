# Renomear branches legadas (executar pelo dono ou pelo Codex SOB ORDEM EXPLÍCITA DO DONO)
Não apaga nada: renomeia para `apagar/<nome>` (reversível). Antes, trocar o padrão do repositório (Diamante é o padrão hoje).
```
R=repos/viluadmcontas2-dot/OMEGAS-V8.2
gh api -X PATCH $R -f default_branch=OmegasVermelho
for b in OmegasPlatina main OmegasDiamante OMEGASCINZA work/platina-final work/codex-audit-progbase wip/codex-fatia1 wip/codex-fatia1b ccr-29d075f0-4kzalm ccr-165939bd-uds4fs ccr-7eed5bd3-251im7 work/platina-obsidian-enxuto codex/firmware-observation-8a2d297c; do
  gh api -X POST "$R/branches/$(printf %s "$b" | sed 's#/#%2F#g')/rename" -f new_name="apagar/$b" --jq .name
done
```
Manter sem renomear: OmegasVermelho (canônica), evidence/progbase-original (evidência), claude/carregar-sessao-r7j85r (ativa; renomear só depois que o Codex terminar, pois renomear no meio quebra o push dele).
Depois, apagar as `apagar/*` pela interface do GitHub (Branches) quando tiver certeza.

## Resultado

Verificação em 2026-10-19T14:21:39.022735+00:00

A ordem explícita desta rodada substitui as instruções antigas acima: padrão desejado `claude/carregar-sessao-r7j85r`; renomear as 14 branches indicadas para `apagar/…`; preservar a ativa e `evidence/progbase-original`; não apagar nada.

**Bloqueado no passo 1.** A chamada REST PATCH para alterar `default_branch` foi rejeitada pelo proxy com HTTP 403: `Repository settings writes are not permitted through this proxy.` Nenhuma renomeação foi executada; nenhuma branch foi apagada. Não foram alteradas tags, releases ou outras configurações.

Branch padrão observada após a tentativa: `OmegasDiamante`.

Lista final conferida com `gh api repos/viluadmcontas2-dot/OMEGAS-V8.2/branches --paginate --jq ' .[].name'` (sem o espaço inicial na expressão):

```text
OMEGASCINZA
OmegasDiamante
OmegasPlatina
OmegasVermelho
ccr-7eed5bd3-251im7
ccr-29d075f0-4kzalm
ccr-165939bd-uds4fs
claude/carregar-sessao-r7j85r
codex/firmware-observation-8a2d297c
evidence/progbase-original
main
wip/codex-fatia1b
wip/codex-fatia1
work/codex-audit-progbase
work/platina-final
work/platina-obsidian-enxuto
```
