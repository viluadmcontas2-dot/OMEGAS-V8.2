# Plano executável — OMEGAS: verdade, recuperação e UX
Data: 2026-10-03. Base: 9691b5f9708fbcc016ff13be5ea0b15ff54cb5d9. Branch: work/platina-blueprint-ux-20261003.
## Autoridade e fontes
GitHub controla código, contratos, checkpoints e provas. Notion somente leitura:
- CUSTOMROM https://app.notion.com/p/3b68ee52ac5481839046f36b482aab44
- Omega Dev https://app.notion.com/p/3b78ee52ac548170b5c1fb69606ced21
- Automotivo https://app.notion.com/p/3b88ee52ac5481988c28db1600330335
Pedido do dono autoriza execução, APK e branch própria. Não reintegrar PR127 sem GREEN.
## Produto e invariantes
Motorista conecta MP48 e acompanha aprendizado; entende estado e próximo passo em 2 segundos.
Sem escrita automática K; manual exige intenção, ACK e readback. Sessão automática.
Preservar motor, ciência Kotlin, LEVELS RAW, sem RESET_ALL, SIL/CIU ou migração estética.
## Sequência e unidades
1. Auditoria: telas e fluxos Refino/AutoCal/Sugestões/balão; inventário de produtores, consumidores, dados antigos, latches. Fotografia remota, corpus e renders; guardar achados sem presumir causa.
2. Caixa-preta: concluir PR127 separadamente; cada decisão tem causa e operandos; transporte/cálculo/armazenamento distintos; fim de sessão não perde transições.
3. D2: RefinementPolicy única com unidade, razão e versão. Contrato de equivalência entre consumidores, sem escolher novos limites sem dados.
4. D3: gêmeo fechado e comparação histerese/incerteza. Milhares de sementes, falha-e-cura, restart, relógio, metamórficos; publicar falso positivo E detecção real.
5. D4: mesmo conjunto de pares, grandeza monótona explicitada, limites e passo; contraexemplo reduzido vira regressão.
6. D6: somente sugestão local Mapa K pelo ledger compartilhado; aviso abre destino correto; abrir nunca escreve.
7. D5 UX vertical: Refino primeiro, depois AutoCal. Sem scroll horizontal inclusive interno; frase e ação principal visíveis; problema dominante; rollback contextual; dados ausentes —. Gráfico de época com fonte/frescor honestos.
8. D7: COERENCIA.md antes de código; conversões, estabilidade, projeção e precedência. H1–H7 no corpus com número e CONFIRMADA/REFUTADA/INCONCLUSIVA; S1–S8 e resíduo MAP/ms.
9. Coesão: projetar único estado nativo para telas; remover cálculo/controle duplicado comprovado; infraestrutura atrás de fronteiras pequenas quando teste/segurança exigirem.
10. Falsificação e entrega: revisão integrada, radar11, build/render/hash no mesmo SHA. Nunca alegar classe5.
## Contrato de UX por fase
Sem conexão: conectar ECU, dados atuais —.
Procurando/conectando: explicar tentativa e teto; não permitir escrita.
ECU trabalhando: acompanhar automaticamente; sem progresso inventado.
Coleta/vazio/parcial: dizer evidência que falta; não mandar buscar log.
Dados válidos/proposta: explicar diferença e revisar ajuste manual.
Executando: feedback imediato, comando protegido; confirmação só readback.
Falha: impacto e próxima ação; normalidade perde prioridade.
Conclusão: resultado medido e restaurar quando válido.
Retomada: contexto preservado; dados antigos identificados, sem sucesso atual.
Cada tela terá os11 estados ou inaplicabilidade justificada; targets56–68dp, essencial>=12sp, 3 níveis máximos, aquisição separada de apresentação.
## Primeiro incremento nesta branch
Ampliar teste Android existente: detectar scroll interno, viewport/documento e elementos fora do limite. Commit somente teste, obter RED válido antes de CSS. Se não reproduzir, acrescentar cenário real expandido/dados longos; não assumir correção.
## Gates
RED remoto → mínimo fix → GREEN focado → mutantes → diff → ampla CI.
Classes1 contrato,2 simulação,3 replay,4 APK emulador1280x720,5 somente dono.
Sem taxa/tempo medido, registrar não medido. Prints devem ser inspecionados, não só jobs verdes.
APK verde-apk-now build_apk=true, artifact/hash, render e STATUS exatos. Sem merge antes de gates.
## Rollback e radar
Reverter PR por tema, preservando contratos e evidência. Antes de commit responder radar11:
leitura2s, pior caso, teto, dado inventado, fonte única, restart/reconexão/relógio/formato velho,
prova comportamento, justificativa blueprint, teste omitido, atrito criado, falsificação hostil.
