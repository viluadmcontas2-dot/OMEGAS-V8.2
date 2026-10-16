# Progresso — fatia 1 do Refino

## Passo 1 — auditoria e RED (2026-10-16)
- Feito: contratos, PROJECT, STATUS, checkpoint, Spec Kit e WorkUnit locais lidos; WIP inspecionado. Autorização desta rodada permite edição local sem rede, add, commit ou push.
- Validador: `../codex/validador/relatorio.md` encontrado; inconclusivo, zero amostras; recomenda peso probatório zero.
- Falta: fechar épocas/preservação; métrica/JSON; recuperação assíncrona; painel; testes e compilação.
- Próximo passo exato: executar RED de preservação e exclusão de previsões, depois corrigir Ledger.
- Arquivos tocados: PROGRESSO.md; testes e scripts de execução serão registrados abaixo.
- Limitação: HEAD remoto/Issues não consultados por proibição de rede; CI/ECU não provados.

## Passo 1 concluído — RED válido e correção
- RED executado: 2/2 testes novos falharam nos comportamentos observados (previsão decisória e gasolina apagada); log `build/refino-fatia1/red.log`.
- Corrigido: constante de peso zero, gasolina preservada, GNV datado por época e excluído de pares/gráfico atuais; reset preserva histórico; uso reinicia por configuração; formato antigo sem época não vira prova.
- Arquivos tocados: EquivalenceLedger.kt, RefinementMemorySliceTest.kt; runner e logs em build/refino-fatia1/ (efêmeros).
- Próximo passo exato: GREEN dos dois testes e integrar métricas/recuperação.

- GREEN: compilação de fontes puras pelo Kotlin 2.0.21 embutido do Gradle e 2/2 JUnit novos passaram (`build/refino-fatia1/green.log`). Nenhuma prova Android/ECU inferida.
