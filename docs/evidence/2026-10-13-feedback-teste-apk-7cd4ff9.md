# Feedback do teste do APK 7cd4ff9 (dono, 2026-10-13 ~19:50)

## Observações do dono
- Cursor em tempo real "bem melhor, muito bom" (Refino a 50 ms, como o AutoCal).
- Refino quase não sugere: é a porta de gravação (ADR-001) — só propõe com a ECU concluída. O dono concluiu que é o mais coerente.
- Atraso de ~3 s para os pontos do Refino aparecerem e "mais exigente" que o APK anterior.
- Tensão da ECU só aparece no GNV.
- Bolinhas do GNV mudam de lugar com o carro parado.
- Quer botão "Reler ECU" no Ajuste global, mesmo com leitura automática.

## O que o código e os dados dizem
- **Tensão:** byte 19 do `48 01` (÷16) — no corpus real (177 mil amostras): GNV 199–232 (12,4–14,5 V, plausível); **GASOLINA: 24.192 amostras, todas 0**; DESLIGADO todas 0. A ECU só preenche esse byte em GNV, então a barra mostra "—" em gasolina. Não é bug da tela. Outra fonte de tensão (outro byte) NÃO foi identificada.
- **Atraso do Refino (projeto, não defeito):** (1) a tela relia os dados a cada 2 s (status 1 s × 2); (2) o ponto só nasce com ≥ 5 leituras estáveis na faixa de 0,025 bar (janela estável de 3 quadros); (3) memória de apresentação no Kotlin. Soma típica 2–4 s. Mudança: relê a cada 1 s (`DATA_EVERY_TICKS = 1`). Os 5 pontos mínimos e a janela estável ficam (confiança do ponto).
- **"Mais exigente" que o APK anterior:** NÃO provado. Pode ser a espera por contadores zerados do defeito já corrigido (`86d9337`), que mantinha o combustível "pendente"; só dá para afirmar com o APK novo.
- **Escopo do aprendizado do Refino** (`EquivalenceLedger.accept`): aprende de QUALQUER quadro estável de gasolina ou GNV, em qualquer RPM e MAP (inclui cruzeiro e carga alta); não é limitado às zonas da ECU. Estável = 3 quadros seguidos com RPM e MAP quase parados (transitório de aceleração é descartado). O par GNV×gasolina é feito na mesma célula RPM×MAP; sem gasolina própria na célula, usa a curva de gasolina da ECU como referência (≥ 6 pontos maduros, cobrindo ≥ 0,20 bar). Gasolina com motor frio nunca vira referência; célula com 8 leituras estáveis congela.
- **Bolinhas mexendo com o carro parado:** são os buffers de aquisição da ECU (médias que a ECU atualiza a cada amostra); a curva (linha) só muda no AutoMatch. Não são correções.

## Mudanças desta rodada
- Botão "Reler ECU" de volta no rodapé da Curva K (automático continua).
- Refino relê os dados a cada 1 s.
