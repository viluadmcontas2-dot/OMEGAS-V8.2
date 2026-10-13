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

## Segunda rodada de feedback (20:07)
- **Exportar a sessão em dezenas de partes:** no código desta linha (OmegasVermelho) o app publicava uma parte de ~100 KB a cada 2 min (e a cada evento do AutoCal): 90 min = 49 ZIPs. Correção: durante a gravação nada vai para Download/Drive (só flash, manifesto e razões, local); ao fechar a sessão — ou na próxima abertura, se o app morreu — sai UM ZIP `<sessão>.zip` com os eventos inteiros. Sessões que já tinham partes continuam em partes. Para ter o ZIP no meio de uma volta: encerrar a gravação em Ferramentas e iniciar outra.
- **Refino "não funciona como antes":** não foi mudança de código do piloto. No log da sessão das 17:46 o Refino abria porque a ECU estava "terminada": contador de AutoMatch parado em 0 de 3 há 67–110 min (a regra `SEM_AUTOMATICO_NOVO` pede ≥ 3 zonas ADQUIRIDAS nos DOIS combustíveis + 25 min quieta, e naquela sessão a gasolina tinha 3 zonas). Na sessão atual a gasolina está com menos de 3 zonas, então a regra nunca fecha e o Refino espera "a ECU terminar" para sempre — espera algo que o fluxo da conversora (15–20 min, só GNV) não vai produzir.
- **Exportação proposta com dados incompletos:** o APK 7cd4ff9 não tem a porta de gravação (ela está nos commits seguintes); a proposta mostrada ("144 células + 30 pontos… inclui o refino de 17 pontos") é só prévia, nada é gravado ("Nada foi gravado"). Com a porta, a exportação fica bloqueada enquanto a ECU estiver no automático.
- **Saída para o fluxo curto:** botão "Pausar a ECU e refinar agora" no Refino (só quando a ECU ainda está no automático): usa a ação já existente de pausar a aquisição da ECU, conferida por readback; com a ECU pausada nenhum AutoMatch novo cai em cima do que for gravado e o Refino libera. Retomar: "Retomar aprendizado" no AutoCal.
- **Ideia (não feita): usar as faixas prontas da ECU como pontos.** A ECU já guarda, por combustível, as faixas maduras (tempo e MAP médios) mesmo de dias anteriores; ler isso ao conectar daria pontos de GNV e de gasolina imediatos, sem esperar 5 leituras estáveis por faixa do nosso lado. Falta provar: método da média da faixa e se os valores persistem entre sessões.
