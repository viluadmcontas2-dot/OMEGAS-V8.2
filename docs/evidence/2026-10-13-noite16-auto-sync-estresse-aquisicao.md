# Noite 16 — atualização automática, aquisição emulada, estresse da interface

## Pedidos do dono (foto do aparelho + mensagem)
1. Export Curva K → Mapa K "só refina depois do 3º AutoMatch" → corrigido: o refino do piloto entra em QUALQUER fase (antes só em `PROPOSTA_PRONTA`, que é depois da ECU terminar). Ainda vale a guarda: ponto cujo valor na ECU já mudou é ignorado (`refinementTargets`). Limite honesto: o motor só propõe onde há evidência (amostras suficientes na faixa); onde não há, só a suavização atua.
2. Sem botão "Reler ECU": Curva K e Mapa K se atualizam ao entrar na aba, ao reconectar a ECU e (Curva K) a cada 15 s, em leitura silenciosa. Não mexe enquanto há proposta preparada, revisão ou gravação.
3. Refino mais limpo: no máximo 10 pontos medidos por combustível (vizinhos viram um, ponderado por amostras); só a tela resume, o motor segue usando tudo. Legenda só com o que é desenhado; "apagou/quase apagou" em branco (vermelho é gasolina).
4. Cursor do Refino em tempo real: o Refino rodava a 200 ms (5 quadros/s); agora 50 ms como o AutoCal.

## Provas
- `tools/jvm/rodar_aquisicao_emulada.sh`: o `NativeAutoCalMonitor` REAL (kotlinc 2.0.21 + org.json, `SystemClock` trocado por relógio de teste) lê uma ECU falsa que só adquire o combustível em uso. 5 cenários (gasolina→GNV, Reaprender e GNV antes da gasolina, alternando com AutoMatch nativo, reset com carro já adquirindo): app = ECU em todos, nenhum combustível preso em "pendente". Mutação: voltando a regra antiga ("só zero libera") o cenário E falha — o teste enxerga o bug.
- `tools/ci/estresse_interface.cjs` (CPU 4× mais lenta, 600 quadros, 20 Hz e 100 Hz, toques simulados): p95 por quadro ≤ 4,5 ms (limite 8), 0 tarefas longas, gráfico não refeito por telemetria, memória estável; medidor de uso 0,29 µs por toque, envio máximo 4,2 KB.
- node 232/0, unittest OK, run_checks PASS, e2e PASS.

## Não provado
- Aparelho real; efeito físico do refino; se a ECU adquire GNV parada em marcha lenta (a ECU falsa assume o que o dono descreveu).
- Kotlin do `recordUiUsage` (ponte Android): só na CI.

## Observação do dono: curva manual que parou os apagões
Curva K toda em 1,0 e extremidades levantadas (baixa injeção e alta injeção) → parou de apagar. Coerente com apagões em transitório (retorno de carga alta / pós-corte), onde a ECU interpola mal nas pontas. Hipótese a medir (não implementada): engasgos/h por faixa antes e depois, no StallWatch.

## Cabo de guerra do GNV (pontos "fora da curva")
Os pontos roxos formam outra curva: provavelmente a mesma relação GNV×gasolina em RPM diferente (dimensão escondida). Curva K é 1-D (tempo de injeção); a diferença por RPM pertence ao Mapa K. Plano (não implementado): ajuste 2-D robusto (mediana por faixa de injeção → Curva K; resíduo por RPM → Mapa K local), histerese para não puxar de volta o que acabou de corrigir. Exige mudar o motor Kotlin do refino (`RefinementAutopilot`/`LocalMapPlanner`) e emular com sessões reais antes.
