# Plano — inteligência de ponta a ponta (dono, 2026-10-16)

Canônica: **`OmegasVermelho`** (decisão do dono, 2026-10-16; substitui "OmegasDiamante" no AGENTS.md, no hook de regras e no STATUS — ajustar na fatia 1.9).
Pedido do dono, em uma frase: *o app tem de perceber sozinho onde o GNV difere da gasolina (principalmente na saída da lenta), refinar a curva a cada AutoMatch da ECU, guardar o que aprendeu entre versões e tirar toda a carga do usuário — sem pressa de APK; evidência antes de regra.*

## 0. Evidência já medida (sessões "Novas" do Drive, 15–16/10, 5 sessões, 53 min)
- 40,3 min em GNV, **1,8 min em gasolina** → ainda não dá para comparar GNV × gasolina nestas sessões; a base de gasolina tem de vir do corpus antigo (`tests/fixtures/sessoes-uteis`, pasta "Sessoes Uteis" do Drive).
- Arrancadas no GNV (lenta ≥ 1,5 s → +500 rpm em 3 s): **54**; 12 (22 %) não ganharam +600 rpm em 3 s; 29 (54 %) tiveram tranco (queda ≥ 80 rpm na subida); MAP mediano 0,8 bar; razão gás/gasolina 2,5; gás a 48 °C. Gasolina: só 2 arrancadas (0 trancos) — amostra insuficiente. Ferramenta: `tools/sessoes/analise_arrancadas.py`.
- **A sessão grava 1 quadro a cada ~290 ms** (10 quadros crus por amostra; p90 até 1,1 s). Um tranco dura 0,5–1,5 s: hoje ele cabe em 2–4 amostras e some. Esta é a maior lacuna da telemetria.
- 69 afundamentos de RPM e 2 quase-apagões (ex.: 1513 → 462 rpm, MAP 0,43 bar, 16/10 13:35).
- ECU: AutoMatch 3/3 e AutoCal **desligado** em todas; Refino em `PROPOSTA_PRONTA` e depois "—". 1 gravação do Mapa K confirmada pela ECU (16/10 13:31) — a gravação funciona ao menos aqui; o "não percebe que gravou" fica como pergunta Q4.
- `.omegas` (aprendizado exportado 16/10 14:53): formato v3/6, 158 regiões, 64 células, 39 comparações, 458 K confirmados — é o que precisa voltar sozinho ao app após atualizar.

## 1. Decisões que só o dono toma
- **D1 — Refino grava sozinho logo após cada escrita da ECU (AutoMatch)?** Hoje a regra é "nada grava sem toque". Recomendação: **sim, limitado**: só Curva K; só suavização/correção dentro das travas (0,8 ≤ K ≤ 1,8; mapa > 200 → K ≤ 1,5); no máximo ±5 % por ponto por AutoMatch; readback obrigatório; registro na sessão; um interruptor único para desligar. Condição: a pergunta Q1 (abaixo) tem de ser respondida pelo corpus antes de ligar.
- **D2 — API do Claude dentro do app em tempo real?** Recomendação: **não** no laço de controle: cobra créditos de API (fora do Claude Max), exige internet no carro, e um laço de controle precisa ser determinístico e auditável. Alternativa que entrega o mesmo: a IA (aqui, nas sessões) deriva as regras e os números; o app roda regras determinísticas; cada sessão exporta um "resumo para IA" rico (fase 2.1) para a próxima rodada de estudo.

## 2. Perguntas que decidem o desenho (respondidas no corpus antes de codar)
- **Q1** A ECU calcula o AutoMatch sobre a curva viva ou sobre uma foto tirada no início da aquisição? (Se foto: nossa escrita no meio é apagada, e o refino tem de entrar *antes* da próxima aquisição começar, ou após o 3/3 com readquisição.) Fonte: `analise_automatch.py` + snapshots `autocal_native_snapshot` + `k_batch_confirmed` nas sessões.
- **Q2** Quanto do erro por faixa de injeção depende do RPM (vai ao Mapa K) e quanto só do ms (fica na Curva K)? Define o fator da exportação Curva→Mapa (o dono: "não deve ser 100 %; mapa alto dá erro no primeiro uso do dia"). Fonte: `EquivalenceAttribution` + pares do Ledger por RPM×ms + gas_c.
- **Q3** Na arrancada, o que o GNV faz que a gasolina não faz no mesmo MAP (ms, razão gás/gasolina, tempo até +600 rpm, trancos, gas_c)? Define o "regime arrancada" e para onde vai a correção (células de RPM baixo × ms alto do Mapa K).
- **Q4** Por que a confirmação da escrita trava ou entra em laço (Mapa e Curva)? Fonte: `app_log` de readback nas sessões + `KWriteManager`/`KFactorManager`. Meta: confirmar em segundos, sem mudar bytes.

## 3. Fatias, em ordem (uma branch por fatia a partir da Vermelho; CI verde no SHA; prova 1–3 no CI, 4 no Chromium, 5 só o dono)

### Fase 0 — mapa e evidência (sem mudar o app)
- 0.1 `graphify` do repositório uma vez: quem produz e quem consome cada dado (telemetria → Ledger/Journal/Autopilot → telas → gravadores). Base para "costurar tudo".
- 0.2 Corpus único (Novas + Sessões Úteis + fixtures); rodar `analise_arrancadas`, `analise_quedas`, `analise_linearidade_queda`, `analise_automatch`, `replay_sessao`; responder Q1–Q4 em `docs/evidence/2026-10-16-evidencia-arrancada-e-automatch.md` (curto).
- 0.3 Fechar D1 e D2 com o dono.

### Fase 1 — segurança, correções da auditoria e limpeza (ponytail; sem tela nova)
- 1.1 Porta de gravação **no toque** (Gravar, +/−, exportação) e uma checagem equivalente no Kotlin antes dos bytes; estado desconhecido continua liberando (decisão do dono de 13/10). [auditoria 2]
- 1.2 Curva→Mapa: etapa 2 falhou → plano preservado + botão "Concluir curva" + aviso correto; fator parcial vem de Q2. [auditoria 1]
- 1.3 Persistência à prova de queda: um escritor só (fsync + `.bak` + volta ao `.bak`) para Ledger, Journal, StallWatch, Autopilot; `MotorLearningMemory` salva no máximo a cada 30 s; nunca zerar calado — avisar em palavras. [3, 9]
- 1.4 "Piorou" honesto: só com diferença ≥ 3 %, em ≥ 3 células distintas, janela sem sobreposição; margem 4 % alvo / 5 % aceito mantida. [5]
- 1.5 Apagar painel Wi-Fi e Link (código, prefs, permissões de rede não usadas), `maxStep`/`pauseMs`/`MAX_SAFE_STEP` e `ExistingCalibrationWriterV7` (ajustar os 2 contratos Python que o citam). Gravação direta como no original; só a conferência fica. [6, 7, 12, 13]
- 1.6 Sessões: `stop()` só marca e a publicação roda em segundo plano; poda por teto de tamanho/idade, com aviso. [10, 11]
- 1.7 Confirmação de escrita rápida (Q4): encurtar prazos e laços sem mudar bytes; portar o purge serial da CINZA se o corpus mostrar que ajuda.
- 1.8 Backups da Curva K: nenhum arquivo automático na gravação manual; **Desfazer** usa a leitura feita antes de gravar (memória, sem arquivo); botão para apagar backups.
- 1.9 Registro: AGENTS.md, hook de regras, STATUS e checkpoint passam a dizer Vermelho; regra "nenhuma escrita automática" ganha a exceção de D1 se aprovada.

### Fase 2 — inteligência (o coração)
- 2.1 Telemetria de transientes: além da amostra de 290 ms, um buffer circular de quadros crus (≈20 Hz) de ±4 s salvo em volta de eventos (arrancada, afundamento, troca de combustível, escrita da ECU). Pequeno, e dá replay fiel. Resumo para IA por sessão (arrancadas, quedas, escritas, cobertura) junto do ZIP.
- 2.2 Detector de arrancada (saída da lenta) nativo: mesmo evento da ferramenta 0.2; compara com a gasolina no mesmo MAP; vira "regime arrancada" do Refino, com correção nas células de RPM baixo × ms alto do Mapa K. Sem aba nova: uma frase + uma ação no Refino.
- 2.3 Refino acompanha o AutoMatch: detectar a escrita da ECU (`NativeKMovementTracker` já vê antes/depois + contador), ler as zonas marcadas, calcular a suavização (anti-serrilhado) a partir dos nossos pares + referência da ECU, dentro das travas; gravar conforme D1 e Q1.
- 2.4 Bins de RPM e ms lidos da ECU (portar `KMapEcuAxes` da CINZA): toda conta por faixa real da ECU.
- 2.5 Inteligência por faixa de RPM (850…3500): atribuição local (Mapa K) × global (Curva K) — `EquivalenceAttribution` existe; sem sugerir mudança < 3 %.
- 2.6 Memória que sobrevive a versões: ao iniciar com Ledger vazio, reconstruir sozinho a partir das sessões em Documents/OMEGAS e do último `.omegas` (zero toque); curva canônica da gasolina = mediana das aquisições de gasolina do AutoCal no corpus.
- 2.7 Leve na central: recálculo fora da trava, cache de 2 s, salvar em thread própria; cursor mais rápido. [8]

### Fase 3 — telas (mockup PNG primeiro, 1 por tela; `omegas-ui-fluxo` e medida no Chromium)
- AutoCal: sem a Curva K; gráfico de zonas esticado na vertical; "quadradinhos" das faixas prontas explicados (aguardando a foto do dono).
- Ajuste global (Curva K): cursor ao vivo (onde o motor está na curva); +/− e arrastar gravam sozinhos ~0,6 s após o último toque (um toque = uma gravação com readback); botão Gravar permanece.
- Refino: botões persistentes (estado guardado no Kotlin, não na tela); "Ver alterações" não some ao trocar de aba.
- Ferramentas/Sessões: lista rolável e didática; "tamanho e retenção" funcionando; log enxuto (técnico vai para o arquivo da sessão).

### Fase 4 — um APK só, no fim, com link e SHA-256; validação no carro (classe 5, só o dono).

## 4. O que NÃO entra
APK antes do fim · API do Claude no app (salvo ordem do dono) · apagamento automático (AutoIdle) — não portar · backup automático em gravação manual · mudança de bytes da ECU · tela nova sem mockup aprovado.

## 5. Interpretações registradas (uma linha cada)
- "não quero backup automático" = nenhum arquivo visível; o Desfazer continua pela leitura prévia em memória.
- "gravar ao clicar em +/−" = sim, com junção de toques em 0,6 s para não virar 10 gravações.
- "5 telas" = navegação atual da Vermelho (autocal, refino, map, curve, tools); a regra das 8 abas é da linha CINZA e não se aplica.
