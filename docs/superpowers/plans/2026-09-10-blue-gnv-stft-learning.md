# Plano — Issue #29: MP48 e OBD/STFT independentes

## Linha rastreável

1. Base: `e340709908f74a7e0181e81ad665aae2dcbcd408`.
2. Governança inicial: `554c37f`; corrigida após revisão em `b8f64f9`.
3. Testes RED: `a3c830e`, run `34520184617`, falha esperada por símbolos inexistentes.
4. Implementação GREEN será ligada a `Refs #29`.
5. Revisão e correções terão commits próprios, sempre ligados à Issue.
6. STATUS/workunit registrarão SHA, árvore, run, jobs e limitações.
7. APK será disparado manualmente somente após CI final verde.

## Entregas

- Motor OBD puro e persistível por RPM × MAP × época.
- Ciclo 010C/010B/0106 completo e prontidão honesta.
- Declaração explícita de modo GNV.
- Sugestão OBD própria de Mapa K sobre readback confirmado.
- MP48 original preservado.
- Bônus OBD no MP48 somente quando concordante; nunca bloqueante.
- UI com duas autoridades claras.
- Testes comportamentais, contratos, CI e APK verificável.

## Invariantes

- GitHub remoto é autoridade; branch única `work/omegas-blue-causal-engine`.
- Nenhuma escrita automática.
- Nenhum dado MP48 participa da matemática OBD.
- Nenhum dado OBD altera o erro ou alvo MP48.
- Falha de um motor não paralisa o outro.
