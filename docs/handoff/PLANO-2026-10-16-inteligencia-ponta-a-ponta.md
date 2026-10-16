# Plano v2 — inteligência de ponta a ponta (dono, 2026-10-16; refinado com evidência)

Canônica: **`OmegasVermelho`**. Decisões do dono (16/10): **D1 = sim**, o Refino grava sozinho logo após cada escrita da ECU, sem teto por ponto — só as travas absolutas (0,8 ≤ K ≤ 1,8; mapa > 200 → K ≤ 1,5; mapa nunca abaixo nem acima do que o dono fixou); **D2 = não** à API do Claude dentro do app. Evidência completa: `docs/evidence/2026-10-16-evidencia-arrancada-e-automatch.md`.

## 1. Problema (uma frase)
**Como fazer o app perceber sozinho onde o GNV se afasta da gasolina — sobretudo na saída da lenta — e corrigir rápido, sem apagar o que já aprendeu e sem pedir nada a quem dirige?**
Para quem: o dono, leigo, dirigindo um carro a GNV com ECU Landi-tipo, numa central Android fraca. Sucesso: tempo até +600 rpm no GNV ≤ 1,15 × gasolina no mesmo MAP; zero quase-apagões na saída; equivalência por célula dentro de ±4 % (até 5 %) nas faixas que o carro usa; nenhuma volta a 1,0 "para recomeçar".

## 2. O que os dados dizem (resumo)
A ECU aplica o AutoMatch sobre a curva viva → nossa escrita logo depois é aproveitada (Q1). A lenta no GNV é rica (−14 %) e 2500 rpm em carga é pobre (+5 a +9 %) → correção por RPM = Mapa K (Q2). Na arrancada o GNV recebe 3–9 % mais gás que em regime e demora 50 % mais para ganhar rotação; os quase-apagões são saídas em carga com gás a 20–25 ms (Q3). Gravar + confirmar leva < 1 s na curva e < 3,4 s no mapa; o que trava é a espera de 10 s do pipeline, checkpoints e o Link morto (Q4). E o app zera o aprendizado do GNV a cada gravação, por isso oscila.

## 3. Variações consideradas (e o que ficou)
- **Inversão — a ECU já mediu; leia os buffers dela.** Em vez de só coletar pontos nossos, ingerir `MNFLD_PRESS_BUF`/`PETR_INJ_TBUF` (gasolina e GNV) como pares no Ledger. Fica (fase 2.2): custo quase zero, evidência que a própria ECU usa.
- **Memória com época, não zerada.** Pares medidos sob a curva antiga são re-escalados pela razão K_novo/K_velho da faixa, com peso menor, em vez de apagados. Fica (2.1): resolve a oscilação observada.
- **Refino acompanha o AutoMatch (pedido do dono).** Detectar a escrita da ECU (já existe, ~3 s), suavizar o serrilhado e corrigir pela equivalência, gravar na hora. Fica (2.3), com D1.
- **Um número por célula.** Simplificação: a razão petrol_ms GNV/gasolina por célula RPM×MAP é o estado inteiro do problema; curva corrige o que é comum a todos os RPMs de uma faixa de ms, mapa corrige o resto. Fica como modelo central (2.4).
- **Replay no telefone.** Ao iniciar sem memória, reler as sessões em Documents/OMEGAS e o último `.omegas`. Fica (2.5).
- **Quadros crus em volta de eventos.** Buffer de 20 Hz ±4 s salvo só em arrancada, afundamento, troca de combustível e escrita da ECU. Fica (2.6): barato e dá o replay que falta.
- **Lente de calibrador.** ECUs originais tratam arrancada com enriquecimento transiente por variação de carga; aqui não há esse controle, então a saída tem de ser resolvida no estado estacionário das células de RPM baixo × ms alto (menos gás) e na lenta (rica). Fica como hipótese de correção a provar no carro.
- **API do Claude no laço.** Descartada (D2). O estudo fica aqui; o app roda regras.
- **10×: controle de lambda próprio.** Sem sonda nos dados; descartado.

## 4. Suposições a validar (como)
- [ ] Q1 vale sempre (curva viva): a cada sessão com AutoCal ligado, `analise_janela_automatch.py` recalcula o desvio por hipótese.
- [ ] Menos gás na saída melhora a arrancada: medir tempo até +600 rpm GNV × gasolina no mesmo MAP antes/depois da correção (ferramenta já existe).
- [ ] A lenta rica (−14 %) se mantém com base de gasolina maior e MAP igual: pedir nada ao dono; usar as próximas sessões com paradas em gasolina.
- [ ] Re-escalar pares por K_novo/K_velho reproduz o que a ECU faz: comparar pares re-escalados × pares novos na sessão seguinte à gravação.

## 5. Fatias (ordem; uma branch por fatia; CI verde no SHA; prova 1–3 no CI, 4 no Chromium, 5 só o dono)

### Fase 1 — segurança, correções e limpeza (em execução, 4 agentes com arquivos disjuntos)
- A (JS): porta de gravação no toque; Curva→Mapa com etapa 2 recuperável ("Concluir curva"); botões do Refino persistentes.
- B (Kotlin autocal): persistência à prova de queda (fsync + .bak + fallback, sem zerar calado); "piorou" só com ≥ 3 % e ≥ 3 células; janela sem sobreposição; Ledger calcula fora da trava.
- C (Kotlin/JS limpeza): apagar painel Wi-Fi e Link; apagar limite de passo e writer morto; espera do pipeline antes de gravar ≤ 1 s.
- D (Kotlin sessão/aprendizado): parar sessão sem travar; poda por teto; salvar aprendizado a cada ≥ 30 s; flush do pipeline ≤ 1 s.
- Depois: registro (AGENTS, hook de regras, STATUS → Vermelho; regra "nenhuma escrita automática" ganha a exceção D1); backups: botão apagar; nenhum arquivo automático em gravação manual; Desfazer pela leitura prévia em memória.

### Fase 2 — inteligência
- 2.1 Memória com época: cada par guarda a época da curva/mapa; na gravação, re-escalar em vez de zerar (`LEARNING-RESET` some); histerese: proposta nova só com ≥ N células distintas remedidas sob a época nova e sem inverter direção com menos evidência que a anterior.
- 2.2 Buffers da ECU como pares do Ledger (os "quadradinhos"), marcados como origem ECU.
- 2.3 Refino após AutoMatch (D1): ao `FACTOR_CHANGE_CONFIRMED`, calcular (a) suavização do serrilhado (nós fora da tendência dos vizinhos), (b) correção por faixa de ms pela equivalência global, gravar Curva K com readback e registro; (c) correção por faixa de RPM → Mapa K, dentro das travas absolutas; um interruptor único para desligar; sem teto por ponto.
- 2.4 Bins de RPM e ms lidos da ECU (porte de `KMapEcuAxes` da CINZA); tabela de equivalência por célula como estado central; "regime arrancada" = células RPM < 1600 × ms ≥ 6, com a gasolina no mesmo MAP como referência.
- 2.5 Memória que sobrevive a versões: reconstruir do `.omegas` e das sessões ao iniciar vazio (zero toque); curva canônica da gasolina = mediana das aquisições de gasolina do AutoCal no corpus.
- 2.6 Telemetria de transientes (20 Hz ±4 s em eventos) + resumo para IA por sessão (arrancadas, quedas, escritas, equivalência por célula).
- 2.7 Apagar `k_interpolated` (sempre 0) ou calculá-lo de verdade; cursor e telas só repintam o que muda.

### Fase 3 — telas (mockup PNG antes, 1 por tela)
AutoCal sem a Curva K e com o gráfico esticado (quadradinhos = buffers da ECU, explicados em uma frase); Ajuste global com cursor ao vivo e gravação ao tocar +/− ou arrastar (junção de toques 0,6 s; Gravar fica); Sessões roláveis e didáticas, "tamanho e retenção" funcionando; log enxuto.

### Fase 4 — um APK, SHA-256, carro (classe 5, só o dono).

## 6. Não fazer (e por quê)
- Teto por ponto / por AutoMatch — o dono recusou: atrasa a correção; as travas absolutas bastam.
- API do Claude no app — custo de API fora do Max, internet no carro, laço de controle não determinístico.
- Apagamento automático de pontos (AutoIdle) — o dono quer aprendizado melhor, não apagar.
- Backup automático em gravação manual — o dono não quer arquivo; Desfazer continua pela leitura prévia.
- Tela nova sem mockup aprovado; mudança de bytes da ECU; APK antes do fim.

## 7. Perguntas em aberto
- A lenta rica é efeito da curva (nós 0–3, 0,5–2 ms) ou do Mapa K na primeira linha? (Decide onde entra a correção da lenta; a tabela por célula responde quando houver mais gasolina parada.)
- Quantos AutoMatches por aquisição a ECU faz com AutoCal religado após refino nosso (3 ou 4)? Conta nas próximas sessões.
