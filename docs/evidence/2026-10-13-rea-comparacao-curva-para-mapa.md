# REA — comparação das versões da interface (02cc916 → 1eaafa5), 2026-10-13

`compare_application_versions` sobre `app/src/main/assets/ui` (antes: `ev_5210cce4…`, depois: `ev_292f9336…`; ambos grafos completos, sem omissões).
Itens: 26 iguais · 35 adicionados · 32 removidos · 4 alterados · 0 desconhecidos.

Arquivos (nível de ativo): **adicionado** `core/balance-runner.js`; **alterados** `core/curve-to-map.js`, `screens/map.js` (+ `index.html`, relações do renderer/artefato). Nenhum outro arquivo da interface mudou.
Os 33 "módulos" adicionados e 31 removidos são funções internas dos arquivos alterados (reescrita de `plan`/`lerp` por `balance`), não arquivos novos.

Limite: comparação estática; não prova comportamento. O comportamento é provado por `tests/ui/curva-k-para-mapa-k.test.cjs` e `tools/ci/curva_para_mapa.cjs`.
Execução do código x87 do original: `docs/evidence/2026-10-13-exporttok-x87-executado.txt` (`tools/reverse/exporttok_x87.py`).
