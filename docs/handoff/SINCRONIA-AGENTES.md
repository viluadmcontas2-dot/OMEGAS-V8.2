# Sincronia entre agentes (dono, 2026-10-27)

Claude, Codex e AGY trabalham sobre o mesmo remoto no GitHub; o GitHub é o barramento.

- Antes de escrever: `tools/sync-agentes.sh --so-ver`.
- Depois de cada marco: commit pequeno e `tools/sync-agentes.sh` (busca, mescla sem resolver conflito sozinho, envia sem force e lê o remoto de volta).
- Cada agente escreve só na sua superfície (um dono por arquivo); todo marco entra em `docs/handoff/PENDENTE.md`.
- Só diga "publicado" depois da leitura de volta (o script faz isso e sai com código 0).
- Códigos de saída do script: 0 ok · 2 sem repositório/branch · 3 conflito (nada foi alterado; resolver à mão) · 4 envio recusado · 5 leitura de volta não bate.
