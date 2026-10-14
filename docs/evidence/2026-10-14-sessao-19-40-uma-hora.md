# Sessão das 19:40 (OMEGAS_Sessao_2026-10-13_19-40.zip) — 59,7 min, ZIP único

Replay: `tools/sessoes/replay_sessao.py`. Só observação.

| Item | Valor |
|---|---|
| Quadros | 10 233 (GNV 7 594, gasolina 2 042) |
| Cobertura estável RPM×MAP | GNV 104 células, gasolina 73 |
| Apagões | 1 (aos 44,8 min, volta do corte; GNV 2,2 ms, MAP 0,21 bar, RPM caiu a 610) |
| K gravado | 2 toques manuais de 1 ponto (índice 19: +3,8%; índice 29: +3,6%), nenhum automático |
| Ações na ECU | Readquirir gasolina (1,5 min), Readquirir GNV + gasolina (46,3/46,4), Readquirir ponto (51,8) |
| AutoMatch da ECU | 0/3 até 52,8 min; depois 1/3 (52,8) e 2/3 (56,8) |

Achados
1. **Falso "ECU terminou" (corrigido no código).** O silêncio da ECU (`quietMinutes`) só zerava quando o contador de AutoMatch mudava. Mesmo com 4 readquirir na sessão, aos 52,8 min o app mostrava "quieto 49,8 min, ECU terminou (SEM_AUTOMATICO_NOVO), 21 pontos prontos" e, no mesmo minuto, a ECU rodou o AutoMatch nº 1 por cima da proposta (e outro aos 56,8). A trava de escrita não ajudaria: ela confiava nesse "terminou". Correção: Readquirir/Apagar ponto/AutoMatch manual agora chamam `noteAcquisitionRestart()` (zera silêncio e o "terminou"). Teste novo em `RefinementAutopilotTest`.
2. **Gasolina própria 0.** `petrolObservations = 0` aos 52,8 min. Dois motivos: o Readquirir gasolina (46,4) reiniciou a referência própria; e a água ficou em 40–64 °C a sessão toda, com o corte de motor quente em 55 °C (padrão): 1 382 das 2 042 leituras de gasolina estavam abaixo de 55 °C e não viram referência.
3. 1 apagão em 60 min, no retorno do corte (RPM 1 609→610). Nenhum ponto da região de baixa carga (~2 ms) foi gravado nesta sessão.
4. Telemetria sem buraco longo; fim por USB desconectado (59,6 min), sem perda de pacote registrada.

Não provado: a causa do apagão; se o corte de 55 °C é o certo para o sensor desta ECU (a água nunca passou de 64 °C).
