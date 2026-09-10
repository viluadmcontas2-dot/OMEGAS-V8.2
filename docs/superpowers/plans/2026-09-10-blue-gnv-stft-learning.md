# Plano — OMEGAS Blue GNV STFT

## Restrições globais

- Branch única: `work/omegas-blue-causal-engine`.
- GitHub remoto é autoridade; nenhum checkout/branch local.
- `BlueCausalEngine` permanece autoridade matemática.
- STFT GNV é o erro primário; gasolina, LTFT e comparação MP48 não votam.
- MP48 é contexto opcional e writer manual.
- Nenhuma escrita automática.
- Cada mudança de produção exige teste RED anterior, GREEN focado e CI ampla no SHA exato.
- APK somente após CI final verde e autorização do owner já concedida nesta execução.

## Tarefas

1. RED: testes comportamentais do motor STFT, persistência, época, outliers e ausência de gasolina/readback.
2. GREEN: motor robusto e persistente por RPM × MAP.
3. RED/GREEN integração: aquisição 010C/010B/0106 delimitada e ingestão antes da hidratação Blue.
4. RED/GREEN proposta: medição imediata; readback somente para alvo exato manual.
5. Revisão arquitetural e regressão ampla.
6. Atualizar AGENTS/PROJECT/STATUS/workunit com recibos exatos.
7. CI final no HEAD, dispatch manual de APK, polling até terminal e verificação do artefato.
