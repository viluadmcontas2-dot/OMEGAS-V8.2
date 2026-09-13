# OMEGAS RED — contrato operacional

## Autoridade e linha de trabalho

- O repositório é a fonte canônica de código, requisitos, decisões, status, testes e evidências.
- Branch canônica atual: `work/omegas-blue-causal-engine`.
- Spec Kit canônico do fechamento atual: `specs/004-red-final-engine-closure/`.
- Epic canônico: #30.
- Gate físico separado: #25.
- Não criar genealogias paralelas nem reescrever falhas intermediárias como sucesso.

## Issue-first + Spec Kit

- GitHub Issues são as unidades canônicas de execução.
- Cada mudança de produção deve pertencer a uma Issue aberta com objetivo, escopo, arquivos/seams, testes RED/GREEN, critérios de aceite e evidência exigida.
- `spec.md` define requisitos e comportamento; `plan.md` define arquitetura, dependências e ordem; `tasks.md` mapeia execução para as GitHub Issues.
- Commits, checkpoints, revisões e recibos devem citar a Issue correspondente (`#NN`).
- Não criar um sistema paralelo de IDs de tarefas/itens em chat ou documentação como autoridade de execução.
- Antes de iniciar ou retomar uma Issue, reler o HEAD remoto, a Issue e os arquivos Spec Kit canônicos.
- Antes de qualquer write remoto, reler o HEAD da branch canônica e reconciliar divergência; sem force-push.
- Um agente sucessor continua pelas Issues abertas e pelos critérios ainda não provados; não reconstrói o projeto a partir de memória/chat.
- Issue só fecha com evidência remota do comportamento aceito no SHA correspondente.

## Issues atuais

- #31 — core RED: gasolina permanente, equivalência física, interpolação e ganho causal K.
- #36 — Predictor + Aprender: superfície estimada convergente e UI operacional.
- #32 — runtime: telemetria fluida e cálculo fora do hot path.
- #33 — OBD witness/learner e sugestão Map K runtime.
- #34 — consumo: distância, abastecimento e incerteza.
- #35 — release: matriz adversarial, revisão independente e APK verificável.
- #25 — validação física humana no veículo.

## Autoridades científicas

- `BlueCausalEngine` é a autoridade de comparação medida gasolina↔GNV e atribuição causal de intervenção K.
- Gasolina válida compõe uma superfície permanente `(RPM, MAP) -> Petrol Inj.`; ela não expira por tempo, fuel switch, epoch GNV, Curva K ou Mapa K.
- Janela temporal curta não pode decidir a existência de referência de gasolina; ela pode ser usada apenas para atribuição causal before/after de intervenção K.
- Interpolação deve funcionar em vizinhança física suportada 2D, incluindo horizontal, vertical, diagonal e irregular; fora de suporte, abstém.
- `OBSERVED`, `INTERPOLATED` e `PREDICTED` não são sinônimos e devem permanecer distinguíveis.
- Predictor é projeção estimativa da mesma autoridade científica, não engine concorrente; observado forte vence previsão.
- O motor OBD é autônomo para STFT Bank 1 do GNV por RPM × MAP; OBD não substitui a matemática MP48.
- MP48 não exige OBD. Witness OBD READY/fresco/coerente pode bonificar confiança; ausência ou conflito não bloqueia nem recalcula o alvo MP48.
- LTFT não participa da matemática de correção.
- Mapa K físico usa linha = Petrol Inj. atual do GNV e coluna = RPM atual do GNV; gasolina-alvo não é o endereço físico GNV.

## Engenharia

- TDD obrigatório: escrever teste de comportamento, observar RED real, implementação mínima, GREEN focado e regressão ampla.
- Teste que nunca foi observado falhando não conta como prova RED.
- Investigar causa antes de corrigir sintoma.
- Não criar engine, owner ou pipeline paralelo quando existe owner canônico.
- Persistência quente é atômica/coalescida; escrita de evidência não pode dominar polling.
- Superfície/Predictor pesados ficam fora do hot path e fora do render tick; cache/revisão semântica/coalescência são preferidos.
- Freshness da telemetria é independente de mudança dos valores visuais arredondados; scheduler de UI não acumula ticks.

## Segurança de calibração

- Observar, aprender, interpolar, prever, abrir editor e preparar proposta não escreve na ECU.
- Escrita é manual: preparar → revisar → confirmar → write → ACK → readback.
- Serviço ativo, USB, ECU pronta, telemetria fresca, confirmação humana, ACK e readback continuam gates de escrita.
- Mapa K editável permanece em 100..180.
- Alteração confirmada cria novo estado/epoch GNV afetado; não invalida a superfície permanente de gasolina.
- Falha, ambiguidade ou endereço não resolvido produz abstenção.

## UI operacional

- Learning prioriza contexto RPM/MAP, gasolina esperada, GNV observado, desvio, confiança/origem e sugestão quando existir.
- Jargão interno como nome de engine, epochs, IDs, calibration state e explicação ACK/readback não pertence ao painel operacional principal.
- Dados técnicos podem continuar disponíveis em diagnóstico sem poluir a operação diária.

## Verificação e CI

- GitHub Actions é a execução primária.
- Gate de software final em #35: FAST → JVM/unit → lint no mesmo SHA + revisão adversarial independente.
- Qualquer alteração de produto após review reinicia os gates no novo SHA.
- APK requer workflow canônico owner-authorized e pertence ao SHA final aceito.
- `PROVEN` exige SHA/árvore/testes/runs/evidência compatíveis com a Issue.
- CI não substitui veículo: não alegar economia, estabilidade física, ausência de ANR/soak ou compatibilidade física sem #25/evidência real.
