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
