# Os pares do Ledger não seguem o K (e por isso a "re-escala por K_novo/K_velho" não foi feita)

Fonte: corpus `tests/fixtures/sessoes-uteis` (31 gravações reais de Curva K, 93 sessões). Script reproduzível: `python3 -I tools/sessoes/pares_vs_k.py`. Só leitura.

**Pergunta.** O doc de 16/10 propôs re-escalar os pares GNV do Ledger por K_novo/K_velho em vez de apagá-los. Aquele teste mediu `gas_ms/petrol_ms` (tempo de gás COMANDADO ÷ gasolina), que é K×Mapa por construção. Mas o par do Ledger é outro: `Obs.petrolMs` lido em GNV, o tempo de injeção da gasolina que a ECU original calcula enquanto o motor roda a GNV (índice > 1 = "GNV pobre, a ECU compensa somando").

**Teste.** Por gravação de K: mediana de `petrol_ms` em GNV por célula rpm×MAP (≥ 3 leituras de cada lado), 90 s antes × 8–98 s depois, contra ln(K_novo/K_velho) na faixa de ms da célula.

**Resultado (OBSERVADO).**
- 36 células comparáveis; 19 afetadas pelo K (|ln| ≥ 5 %); 16 de controle (sem mudança de K na faixa).
- Inclinação Δln(petrol_ms GNV) ÷ ln(K_novo/K_velho) = **−0,15** (se a ECU compensasse inverso ao K: −1; se não seguisse: 0). Ruído do controle: desvio 0,20.
- Maiores mudanças de K: +50 % (n=313/168 leituras) → Δ = +0,005; −50 % (n=131/45) → Δ = −0,007. Praticamente nada.

**Conclusão (limites honestos).** Em até ~100 s depois de uma gravação, o `petrol_ms` medido em GNV não acompanha o K. Re-escalar os pares por K_novo/K_velho inventaria um deslocamento que os dados não mostram; **não foi implementado**. Mantém-se o descarte por faixa (`invalidateGas`), que é conservador. NÃO está provado que a grandeza nunca siga o K: um ajuste lento da ECU (minutos) não cabe nessa janela, e são só 19 células. Refazer com janelas de 5–15 min quando houver sessões com gravação seguida de rodagem longa.

**Consequência para a inteligência.** Se o `petrol_ms` em GNV não reage ao K, a "equivalência por pares" mede principalmente a diferença de regime/carga entre GNV e gasolina, e só indiretamente a mistura. Vale checar isso antes de pesar mais esses pares na curva.
