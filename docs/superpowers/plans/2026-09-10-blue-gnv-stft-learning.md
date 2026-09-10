# Plano — OMEGAS Blue com MP48 e OBD independentes

## Restrições globais

- Branch única: `work/omegas-blue-causal-engine`.
- GitHub remoto é autoridade; nenhum checkout/branch local.
- `BlueCausalEngine` permanece autoridade matemática.
- O motor MP48 preserva o método original por Petrol Inj. gasolina × GNV.
- O motor OBD aprende separadamente por RPM × MAP × STFT em GNV.
- Nenhuma coleta, qualidade, prontidão, persistência ou correção é compartilhada entre os motores.
- Nenhuma escrita automática.
- Cada mudança de produção exige teste RED anterior, GREEN focado e CI ampla no SHA exato.
- APK somente após CI final verde e autorização do owner já concedida nesta execução.

## Tarefas

1. Corrigir a especificação publicada antes do código.
2. RED: provar o método MP48 original, o motor OBD sem MP48 e o isolamento bilateral.
3. GREEN: implementar motor OBD robusto e persistente por RPM × MAP sem alterar a matemática MP48.
4. RED/GREEN integração: aquisição OBD 010C/010B/0106 delimitada, prontidão honesta e ingestão independente.
5. RED/GREEN apresentação: resultados e estados separados; recomendação OBD percentual quando não houver endereçamento seguro.
6. Revisão arquitetural, matemática, transporte Android, persistência, UX, segurança e regressão ampla.
7. Atualizar AGENTS/PROJECT/STATUS/workunit com recibos exatos.
8. CI final no HEAD, dispatch manual de APK, polling até terminal e verificação do artefato.
