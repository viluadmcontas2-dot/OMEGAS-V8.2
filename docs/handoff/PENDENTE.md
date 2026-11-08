# Pendente — ledger único (lido no início de cada sessão; atualizado a cada marco)

Regras permanentes: `docs/REGRAS-DO-DONO.md` e `AGENTS.md`. Quem é o dono e como pede: `CONTEXTO-DO-DONO.md`. Histórico de documentos antigos: `docs/archive/HANDOFF-2026-10.md`. Item feito sai daqui (fica no `git log`); nenhuma pendência aberta pode sumir sem ser feita ou bloqueada com causa.

## Estado verificado (2026-10-20)
- Branch canônica `OMEGASCANONICO`. Último APK oficial: v14, commit `404e83f`, `apk.yml` run 39435537729 (exige `build_apk=true`), SHA-256 `1abe83266aa25b31ea1173a03aade0e4ba53bb577115415ddec8d663511e3dc3`. CI de 6 jobs verde nesse commit. Não provado no aparelho (USB e WebView reais). Detalhes de APKs: `STATUS.md`.
- Anti-apagão grava em UMA chamada nativa (`applyAntiStalloFlag` / `applyAntiStalloValues`: ler, gravar e reler numa só etapa MANUAL_WRITE). Prepare/confirm ficou só como reserva da ponte.
- Regra sem atrito: 1 toque = ação feita (sem pergunta, 2º toque, janela, rolagem; nada por tempo). O app nunca grava sozinho; o toque é a ordem.
- Anti-apagão em movimento (93 sessões, 12,3 h): 20 apagões. Rotação máxima 1800 vê 20/20; a de fábrica 1600 vê 19/20; 1400 vê 15/20 (`docs/progbase/bins/limiares-em-movimento.md`). Engasgos parados são abstração (dono). O app não tem número fixo (decisão testada em `anti-apagao-sugestao.test.cjs`).
- Corpus completo: 177.457 leituras, 89 sessões com dados; 31 quedas, 28 paradas do motor (`docs/progbase/bins/apagoes-em-movimento.*`). Sem velocidade/chave, movimento e causa seguem desconhecidos.
- Evidência de volta por pressão baixa: pressão normal do gás ≈ 2,3-2,5 bar; 8 das 28 trocas GNV para gasolina ocorreram com pressão muito abaixo (0,26 a 1,34 bar). O app registra nível e pressão a cada troca.

## Omega Aura (2026-10-27; branch `claude/omega-aura`)
- Plano vivo, unidades de trabalho e estado: `docs/aura/PLANO.md`. Contrato de fluxo (botão principal fixo, Desfazer não toma o lugar de Gravar): `docs/aura/CONTRATO-DE-FLUXO.md`. Auditorias: `docs/aura/auditoria/`.

## Redesenho de UX: canvas v3 (2026-10-27; frente "design", branch `claude/cool-curie-hs5nk9`)
- Fonte: `docs/produto/mockups/v3/` (23 telas e quadros, `LEIA-ME.md`, briefs do Codex, geradores em `gen/`). Artefato Design do dono: https://claude.ai/artifact/4J4VM2Bkc6zW4d1sNXe8w4 (versão 41, lida de volta: igual aos arquivos do repositório). Conferência: `tools/design/render_dc.cjs` + `gen/lint_dc.py` (23 telas, 78 capturas, 0 erro; teste `tests/design/render_dc.test.cjs`, 8 passam). Sincronia entre agentes: `tools/sync-agentes.sh`.
- Outra frente em paralelo: `origin/nova-experiencia` (4 abas; spec `docs/superpowers/specs/2026-10-26-nova-experiencia-design.md`). Choca com a regra 8 (5 abas). Decisão do dono em aberto: 5 abas (recomendado: o AutoCal fica a 1 toque) ou 4. Reaproveitar nas duas: ajuda "?" por tela, primeiro uso, estado único da central.
- Decisões do dono (2026-10-26/27): pode mexer nas 4 abas travadas (mexida boa) e mostrar água e as 14 verificações (bem orientado); NÃO travar gravação com o carro andando; pode pedir GPS; "Viagens" no lugar de "Sessões"; barra do topo por prioridade (No gás, Nível, Inj. gasolina, MAP, RPM, Tensão; rótulos curtos MAP e RPM); AutoCal: tocar na zona apaga com 1 toque, a faixa "Curva K" sai e os botões de apagar/reaprender ficam sob o gráfico; Refino com 1 botão; aviso de gravação como selo discreto no canto direito da barra do topo (sem travar a tela); balão com bolinhas do gás e mais de um estilo; leitura do nível do gás precisa; melhorar o filtro que apaga pontos com RPM baixo.
- [ ] Nada do canvas foi implementado no app. Só implementar depois do OK do dono sobre TODAS as telas + mapa de navegação (regra 10). Testes que travam texto/estrutura e vão mudar nessa hora: `linguagem-leiga` ("MAP"/"RPM" na barra), `autocal-original-parity` (sai a faixa Curva K; zonas viram células que apagam), `refino-mesmo-grafico`, `refino-screen.test.cjs` (botão único), `anti-apagao-toque-real` (o aviso fixo "Gravado e conferido pela central: …" vira o selo do topo + frase do cabeçalho), `sem_rolagem_abas` (sem faixa de ações).
- [ ] NOVO a provar/criar: contar engasgos antes → depois da gravação (hoje o histórico nunca zera e só a Curva K tem veredito); "Para você agora"; "O carro está apagando?" (tela 11); permissão de GPS (pedir só ao abrir Viagens); 3 estilos do balão (Kotlin `TelemetryOverlayController`, observacional); calibrar nível do gás em 2 pontos (`registerRefuel` já existe na ponte); estado global "atividade da central" (gravando / conferindo / pronto / erro) alimentando o selo do topo; apagar por zona e escolher pontos por arrasto como LOTE do apagar-ponto que já existe (máscaras 0x016E GNV / 0x016D gasolina + commit, sem mudar bytes); botões "Reaprender GNV / gasolina" em 1 toque (invariante: o reset do GNV leva backup completo antes e leitura de volta); "Refinar por minha conta" na tela do Refino (05d).
- [ ] Codex: a franquia do ChatGPT Plus acabou em 2026-10-27 no meio das tarefas M, O e P (telas 16, 14, 15). As saídas parciais foram revisadas e terminadas pelo Claude; a 16 foi refeita por gerador. Nada foi cobrado por API. Retomar quando o Codex liberar (ele informa o horário); não usar API paga.
- [ ] Decisão em aberto, risco do "toque sem janela": apagar por zona e "Reaprender GNV / gasolina" não têm Desfazer. Recomendação: manter 1 toque (pedido do dono) e guardar o estado anterior para um "Desfazer" até a próxima ação, se a central permitir restaurar.
- [ ] Filtro que apaga pontos com RPM baixo (dono: "não funciona muito bem"). Investigação por leitura de código + simulação offline (2026-10-27; verifiquei no código o filtro, o id da leitura e a parada em segundo plano; os trechos Kotlin vêm do relato do agente, citados com arquivo:linha; nada rodado no carro):
  - É só JavaScript da WebView (`ui/core/low-rpm-filter.js`, `ponto-limpeza.js`, `screens/autocal-cockpit.js`); o apagar é escrita real na central (máscaras U8[18] 0x016E GNV / 0x016D gasolina, 0 = apagar, + commit `01 24 05 2A`, e releitura). Limite: constante 1000 (`low-rpm-filter.js:6`) + preferência local `minRpmLearn` (0–3000, 0 desliga); o 1.200 vem do mockup (interruptor, 800–2.000), ainda não implementado.
  - Problemas por impacto (todos só lógica do app; nenhum mexe em byte/comando da central):
    1. Relógio errado: o id da leitura é `snapshot.capturedAtMs` (`autocal-cockpit.js:1081`), que só muda em snapshot COMPLETO (`AutoCalSnapshot.kt:265`); o filtro sai cedo nas leituras de 1 s (`low-rpm-filter.js:45`) e o menor RPM se acumula entre snapshots completos (mediana 38 s, p90 184 s no corpus). Em simulação com `tests/fixtures/sessoes-uteis` (aproximação, o corpus é anterior ao filtro): a regra dispara em 76% das janelas e condena 554 bandas; só 29% eram de marcha lenta, 43% eram de carro andando. Proposta: id = carimbo do contador `NUM_BUF_UPD_*` e RPM com carimbo casado a esse instante.
    2. Sem atribuição por banda: qualquer RPM baixo na janela condena todas as bandas que subiram (`:49-59`), inclusive arrancada, desaceleração, apagão e troca de marcha. Proposta: RPM mínimo por banda (casar pelo MAP da banda) + N amostras seguidas.
    3. Segundo plano: `scheduler.stop()` com a página oculta (`app.js:477`) e sem `reset()` ao voltar: o 1º RPM baixo depois apaga tudo que subiu fora. Proposta: `reset()` no `visibilitychange`; o ideal é o filtro no serviço Kotlin (toca classe protegida: pede autorização).
    4. Contador saturado em 10 não é visto (só `value > before`): comparar também injeção/MAP guardados da banda.
    5. Laço na marcha lenta (inferido): a central readquire a banda e o filtro apaga de novo; sem freio. Proposta: após N apagamentos da mesma banda no mesmo episódio, parar e avisar 1×, contando leituras (não tempo).
    6. Falha do apagar automático é descartada (só "ocupado" espera): re-pedir na próxima leitura nova, com texto próprio.
    7. Regra fraca: só RPM (a velocidade do GPS existe e não chega ao filtro), apaga a gasolina também sem dizer qual, e o Refino aceita qualquer rpm > 0.
    8. Menores: a conferência conta a mesma leitura 2× com a aba aberta; o recibo não marca que foi o filtro (tocaria `AutoCalNativeActionManager`, protegido).
  - Provado: filtro isolado e fila/lote/conferência (unitários, 34/34 passam, dados sintéticos). NÃO provado: cockpit→filtro→ponte→central com ids reais, replay de sessão real, app em segundo plano, laço na lenta, efeito no carro. Próximo passo (depois do OK do dono): RED por replay do corpus real (janela por snapshot completo × por banda), correção mínima em JS, GREEN, CI.
- Problemas reais do Refino achados por leitura do código (não rodados no aparelho):
  - Contrato de gravação: o Kotlin devolve "Gravado" no ACK com `readbackValid=false` (`KFactorManager.kt:480-482`) e a ponte da Curva K só aceita `readbackValid=true` (`V7JavascriptBridge.kt:302`; `refino.js:524`): uma gravação feita pode aparecer como "não confirmado". No Mapa K a ponte põe `true` logo após o ACK (`V7:509`), antes da leitura de volta; o Refino chama `writeVerify.resolve()` e não `begin()` (`refino.js:526,596`). Precisa de teste no carro.
  - "Ver alterações" abre a proposta antes do "restaurar" (`refino.js:339-345`): o restaurar pode ficar inalcançável quando existem os dois.
  - Engasgos nunca zeram (`stall_watch.json` guarda 50 de cada tipo, sem limpeza): a mesma proposta (+5 % ou +10 %) volta depois de gravada e pode empilhar.
  - Gravar o Mapa K apaga todos os pontos de GNV medidos e interrompe a verificação aberta (`TelemetryForegroundService.kt:250`; `RefinementCycle.kt:133-137`) sem avisar no cartão.
  - "Estável: pode desconectar" pode aparecer com engasgos (o índice só vê 3–12 ms; o engasgo mora em 1,5–3,5 ms). Hint "Toque em Revisar ajuste local" aponta botão que não existe (`RefinementAutopilot.kt:427`). `tools/ci/refino_mapa.cjs` tem 2 asserts defasados.

## Prioridade 0 (dono: o carro apaga em movimento; inteligência, sem desperdiçar gasolina)
- [ ] Sugestão ADAPTATIVA do anti-apagão, recalculada por evento (fim de sessão) com as sessões reais do app; nunca sugerir diminuir se houve apagão em movimento com valor menor; mostrar em linguagem leiga "o que mudou e por quê", 1 toque para usar, Desfazer. A recomendação constante foi retirada por falta de prova.
- [ ] Semântica dos 4 extras do anti-apagão e unidade de "Queda de rotação" (rpm por ciclo = quanto?): sem dado, ficam "sem dado para sugerir". Provar com o código decompilado.
- [ ] Separar apagão de motor desligado nos logs (17 quedas ambíguas).
- [ ] Provar no carro a gravação dos 5 números do anti-apagão: desde 2026-10-20 o app manda, como o código original emulado, UM `14 33 01 <índice> lo hi soma` por campo (ex.: 1800 = `14 33 01 00 08 07 57`, EV-ANTIAPAGAO-031) no lugar do antigo `37 33 0C 01 …`. Primeira prova física = dono (ver na tela "Gravado" após a leitura de volta).
- [ ] CORREÇÃO DE ESCOPO do dono: o cartão Anti-apagão deve ter INTERRUPTOR + UM número ("Rotação máxima para ativar", 1600 rpm) como no original (`docs/progbase/capturas/originais/LEIA-ME.md`); os outros 4 ficam recolhidos como experimental. Regra geral: só mexer no que foi visto funcionando nas telas do original. Confirmar no log o quadro da escrita do elemento 0 do vetor 307 (`14 33 01 00 lo hi cs`; 0 escritas `14 33 01` vistas).
- [ ] Exportação de sessões em um toque, abrindo direto o resumo, com nome do carro e data (o ZIP já tem resumo HTML e JSON de análise; ainda usa seletor Android).
- [ ] Copy para leigo: revisar todos os erros e avisos e os três tamanhos (1280x720, 1024x600, 800x480) em cada aba; ampliar `tests/ui/copy-sem-ambiguidade.test.cjs` (todo número diz de quê, sem sigla solta).
- [ ] Validar integração Android e suíte completa na CI do SHA enviado. Nenhum novo APK sem ordem do dono.
- [ ] APK na pasta OMEGAS do Drive: o conector não sobe 4,9 MB; o dono arrasta ou libera `gh release create`.

## Revisão de código do app inteiro (2026-10-20, commit eb42e72): ainda aberto
- KWriteManager: se o "ativar K insertion" chega e a resposta falha, a saída segura 0x24 não é enviada (flag só vira true após o ACK). Rodada própria com teste (mexe no Mapa K).
- KWriteManager/KFactorManager: `busy` liberado duas vezes; "Gravado" publicado só com ACK (decisão do dono de 2026-10-19: confirmar se vale como exceção à regra "só depois da leitura de volta").
- Mapa K compara `current` com o cache em arquivo, não com a central (a Curva K relê a central).
- AntiStalloParamsCore aceita 0 como piso (DERIVADO): o JS manda o `before` exibido e o nativo recusa se a leitura ao vivo diferir.
- "Já está assim" abre janela "Aviso" (2º toque): a mensagem deve ir na linha de estado.
- Código morto do caminho antigo (fase `preview`, janelas "Gravar estes valores?", `data-adv-save`, `decodeFlags`) e superfície prepare/confirm no nativo: remover.
- MainActivity: `allowFileAccess`; `shouldOverrideUrlLoading` só bloqueia não-file://; CoroutineScope solto; ~56 `catch` vazios. O espelho de sessões em Download/Omegas é público (privacidade).
- Possível flake `leveza-orcamento` "200 trocas" (falhou 1x no CI, passou 3x local e no re-run): investigar se repetir. Painel de Ferramentas regrava `data-mode/data-view` iguais; por isso 'tools' está fora do teste de leveza por ora.
- Teste CSS morto (TODO antigo).

## Pendências por tela
Lista completa com prova, esforço e prioridade: `docs/produto/lacunas-por-tela.md` (telas existentes só listadas; regra 12).
- [ ] P1 (ver o arquivo): primeiro uso guiado; Preferências do AutoCal + pergunta da água 80-90 °C; Diagnóstico por item; quando o gás entra; volta por pressão; nível do gás; painel ao vivo completo.
- [ ] Interface leiga na multimídia (1280x720, 1024x600, 800x480): estilo da tela AutoCal, sem jargão. Em 1024x600 e 800x480 só Sessões e Ferramentas foram redesenhadas; AutoCal, Mapa K e Curva K cortam conteúdo (diagnóstico: `tools/ci/sem_rolagem_abas.cjs`).
- [ ] AutoCal, Refino, Ajuste local e Ajuste global ainda dizem "ECU", "RPM" e "MAP" (o teste `linguagem-leiga` marca como todo): trocar por "central" e "Rotação".
- [ ] Bug "Ferramentas vazia" no WebView real: a causa provável (carga preguiçosa) foi removida e há cartão de falha "Não foi possível abrir esta parte"; confirmar no aparelho.
- [ ] AutoCal (multimídia): apagar os pontos de cada zona com 1 toque, sem confirmação; o balão de informação não pode ficar por cima ao alterar algo. Desenhado no canvas v3 (tela 03: tocar na célula Zona × Combustível; tela 16 em 800×480); falta implementar depois do OK do dono.
- [ ] Cartão "Marcha lenta": hoje só leitura e sugestão. Escrita ainda não implementada (ver abaixo).
- [ ] Leveza (orçamentos de nós, timers e renders) e testes e2e de toque verdes.
- [ ] Limpar menções ao fabricante/legado em `app/` (comentários, textos, nomes); a pesquisa fica fora do app. Listar com `git grep -il "platina\|vermelho\|legado\|landi\|aeb"` e, para cada um, reescrever ou remover com teste de contrato (`tests/test_missao_final_contract.py`).

## Marcha lenta: escrita (próxima versão)
Provado no emulador (`tools/emulacao/cpu_setter_original.py --modo-lenta`, RadioGasClick 0x4BFAD8; ECU do dono 80D=0):
- Gás:      `35 03 00 86 24 51 10 43 ; 14 A1 00 00 00 00 B5`
- Volta:    `35 03 00 86 24 51 10 43 ; 14 A1 00 00 4C 04 05`
- Gasolina: `35 03 00 86 24 51 11 44 ; 14 A1 00 00 4C 04 05` (bit = 0x0100 da palavra 1, NÃO 0x0400)
w0/w1 do FLAG_CONF1 e GIRI[1] são preservados; GIRI vai por indexado 0x14 só no elemento 0. O handler original NÃO confere a 1ª gravação antes da 2ª (provado).
- [ ] Falta: `IdleModeCore`/`Writer` (Kotlin; ordem: rpm 0 para 1100 primeiro, 1100 para 0 bit primeiro; 2ª falha = PARTIAL + Desfazer), fluxo JS, ponte, testes JUnit/ECU emulada.

## Cartões, bins e velocidade (detalhes em `PROGRESSO-CARTOES.md`)
- [ ] Volta automática à gasolina: a caixa F4 não é bit de FLAG_CONF1; tempo = SC 62 u8. Decodificar `CheckRitornoBenzinaClick` 0x4A9484 e `EditBassaPressioneMpKeyPress` 0x4C526C (Unicorn) antes de gravar. Limiar 1,25 bar: a hipótese do dono (baixar para ~1,0 bar) é plausível; o cartão permite editar.
- [ ] Bins do Mapa K: RPM = SC 61 (`14 3D 00 idx lo hi`); vetor das linhas de ms NÃO provado (SC 120 é MAP_ESTERNO). Janela "Centros das células" (12 rpm x 12 ms, OK/Cancelar), teste "A central aceitou 0 ms" (capturar no original); bins personalizáveis (0 ms no ajuste local, adicionar linha) como no original (TFormRiferimenti, TFormCentriCelle/K, TFormRifAutocal).
- [ ] Velocidade da Curva K/Mapa K: Curva K em 1 quadro de vetor 0x37 (32 para 3 transações, -90%) e repetição por evento em erro de ACK; precisa de OK do dono e ECU real (`PROGRESSO-CARTOES.md`, `tools/emulacao/simular_gravacao_k.py`).

## Engenharia reversa e rastreabilidade
- [ ] Funções em aberto: 0x4A9484 e 0x4C526C; função gigante 0x00439E94 (289 KB); aba B; separar as 2.329 funções do programa das 7.132 sem nome (biblioteca Delphi). REA estoura 60 s no `ProgBase.exe`: usar Ghidra em segundo plano.
- [ ] Log novo `omegasaudit.LOG` (97 MB, ~810 mil eventos) e capturas do original (Drive, CAPTURAS ORIGINAIS): fixtures, pareamento requisição/resposta, matriz comando x resposta nova, contraste REA, `PerfilCentral` (o app só oferece o que a central suporta; NACK `CA` como sinal). Não versionar o LOG bruto; achados em `docs/progbase/bins/LOG-NOVO-omegasaudit-achados.md`.
- [ ] Evidence ids `EV-<área>-<nnn>` em `docs/progbase/evidencias/INDEX.json` + teste de CI (id citado inexistente, id sem fonte, INFERIDO usado como fato na interface); `graphify update .` + `tools/grafo_conhecimento.py` + `tools/progbase/grafo_progbase.py`; cobertura 100% do ProgBase (`COBERTURA.md`) e réplica fiel dos comandos úteis ao app.
- [ ] Contrato de coerência por função (Ferramentas, Anti-apagão, Marcha lenta, AutoCal, Mapa K, Curva K, Sessões): JS/Kotlin, ponte, comando, resposta, estado na tela; e2e tipo WebView (scripts em ordem, sem cache, `console.error` = falha); estado visível ("Central conectada / lendo / sem resposta / não suportada"); CI falha se faltar elo. Nenhuma aba abre vazia.
- [ ] Funções de referência da AutoCal em Ferramentas, cartão a cartão no padrão do anti-apagão (só o que o dono viu funcionar entra; o resto recolhido como experimental).
- [ ] Mock-up didático do original (`docs/progbase/explorar/index.html`, gerado por `tools/progbase/gerar_explorar.py`), ECU simulada ligada ao original (porta serial virtual com `tools/emulacao/ecu_emulator.py`), guia curto do app (1 página por tela; "Carro apagando" com UMA sequência), manuais públicos (MAN-*) no grafo.
- [ ] Diagnóstico inteligente: analisa a sessão, acha onde apaga, sugere (nunca aplica sozinho) anti-apagão, marcha lenta e saída do corte. Perfil da central (modelo/versão da ECU, MASK_FUNCTION).
- [ ] Docs sem duplicatas: um .md por assunto (auditar com `tools/auditoria.py`); enxugar repo/CI/testes (`ponytail-audit`).

## Ideias herdadas (não verificadas se já feitas)
- [ ] Experimento do dono dirigindo: K nos nós 0-3 (3 valores x ~20 retomadas pós-corte) para provar se K evita o apagão; sessão nova pedindo "Ler mapa K" para o mapa ideal absoluto.
- [ ] Equivalência % por nó x rpm (backend + painel) e re-escala K_novo/K_velho no Ledger (depois do OK do mockup 3; mockups 1/2/3 do dono ainda sem OK: AutoCal sem Curva K; cursor AGORA + gravar ao tocar +/-; painel do Refino).
- [ ] Mapa K perto de 255: propor renormalização (base 100) com a curva absorvendo o ganho; 1 toque, cópia antes, Desfazer, "Gravado" só com readback; semântica do Mapa K (neutro 100 ou 128?) com dado do carro ou `TFormModificaMappa`.
- [ ] Atribuição por RPM do Refino (`EquivalenceAttribution`) na tela do AutoCal, sem leitura anterior (prévia PNG antes).
- [ ] Perguntas em aberto: a lenta rica (-14%) é efeito da curva (nós 0-3) ou do Mapa K na 1ª linha? Quantos AutoMatches a ECU faz com o AutoCal religado após refino nosso (3 ou 4)?
- [ ] Segunda conta do Codex (segredo `CODEX_AUTH_JSON_B64_2`, opcional). Apagar as branches `apagar/*` só com ok do dono.

## Fora de escopo agora
Teto por ponto no Refino, API do Claude dentro do app, apagamento automático fora da exceção das regras, backup automático em gravação manual, tela nova sem mockup aprovado, mudança de bytes da ECU, APK antes do fim.

## Auditoria ponta a ponta cabo → export (2026-10-23)
Fluxo mapeado: cabo USB → permissão USB → central reconhecida → primeira tela → "Central conectada" → gravação automática → AutoCal/refino/anti-apagão → abastecer → fechar → Sessões → Exportar. Corrigido nesta rodada: Voltar nas Preferências; limpar pontos rápido e conferido; tela Sessões vazia convida a agir conforme a gravação automática; LEIA-ME e mapa do carro em todo pacote.
- [ ] P1 Exportar não dá retorno: o toque abre o seletor do Android e, ao voltar, nada diz "Salvo em …" nem o tamanho. Mostrar aviso de resultado na linha de estado (a ponte já devolve ok/erro).
- [ ] P1 Primeiro uso: sem guia do que fazer com o cabo desconectado (tela vazia da barra do topo só mostra "—"). Cartão único "Conecte o cabo da central" com estado da permissão USB.
- [ ] P2 "Aplicar" em Tamanho das gravações: trocar por "Aplicar tamanho" (verbo + objeto) junto com o aviso.
- [ ] P2 Exportar resumo e Exportar tudo: um toque só com a escolha lembrada (pasta Drive) em vez do seletor toda vez.
- [ ] P2 Desfazer da limpeza de pontos é impossível (a central apaga o buffer); por isso a limpeza mostra contagem "N pontos limpos" e destaque do botão, não janela. Avaliar guardar os contadores antes para "refazer leitura" só como informação.
- [ ] P2 Filtro de RPM baixo roda a cada leitura mesmo em outra aba: medir custo de `projection()` a 1 Hz no rk3326 no carro.
- [ ] P3 Serial e firmware da central ainda desconhecidos no MAPA-DO-CARRO (identidade da central incompleta).
- [ ] P3 Causa de mudança do mapa por eventos: gravações vindas de outro programa só aparecem como "mudou sem registro" (comparação entre leituras).

## Gate de CI Aura, verificação 2026-11-07 08h BRT
- [ ] **Bloqueio reproduzido remotamente:** PR #185, HEAD `01490fae51bd7599a6e54052f4e1655164493add`, workflow `42358145174`: JavaScript/navegador, Kotlin/JVM e lint **success**; grafo, contratos Python e integração **failure**. Não marcar como CI verde.
- [ ] **Correção pendente, sem editar gerados à mão:** em checkout efêmero do SHA exato executar `python3 -B tools/grafo_producao.py` (sem `--check`), revisar o diff de `docs/grafo/producao.json` e `docs/grafo/PRODUCAO.md`, publicar ambos com commit pequeno em `claude/omega-aura`, reler HEAD e aguardar CI completo no novo SHA. O gerador é determinístico; `--check` valida a consistência, não regenera. A adição de `tests/ui/aura-cinco-areas-regressao.test.cjs` mudou a entrada do grafo. Confirmar pelo log da execução antes de atribuir qualquer falha Python adicional exclusivamente ao grafo.
- [ ] Após CI verde, ainda faltam provas de interface real nas três resoluções, revisão dos caminhos de escrita/USB e aprovação de UX. **Sem merge nem APK** até esses gates.


## Marco de integridade do WriteJournal (2026-11-08)

- CI completo do Aura HEAD `8aa82794d4daa814c4f728979fa5c78156850812`: run #1694, seis jobs verdes (JavaScript/Chromium, Python, Kotlin/JVM, lint, grafo e agregação).
- Auditoria do `WriteJournal.kt`: `FileJournalStore.write()` apagava o destino antes de confirmar a segunda renomeação; falhas de leitura e conteúdo não vazio inválido eram convertidos em ausência de pendência. RED reproduzido por três testes no SHA `3009566091089684d6f1520732c24f9f664b992c`, run #42436845146.
- Correção isolada em PR #186: commit `1ad4ebe98364f6e86057d73c3073e4092cc89802` troca o arquivo sem apagar previamente, propaga I/O e distingue corrupção de diário vazio; commit `3bca735d46eebcdc645e132f2114fbdc4433252c` cobre substituição válida. CI final desses commits ainda pendente no momento deste registro.
- Limite importante: `WriteJournal` continua sem consumidor no `TelemetryForegroundService`; `ConnectionSupervisor.pending` ainda não é alimentado pelo diário. Esta correção não completa a recuperação nem autoriza retomada automática.
- A primeira execução RED também falhou uma vez em `TelemetryStateStoreTest.liveObject...`, que compara JSON contendo `ageMs` dinâmico; falha separada, não alterada nesta unidade e a observar na repetição.
- Nenhum arquivo de protocolo/USB/gravador protegido foi alterado. Nenhuma escrita na central, merge ou APK foi realizado. PR #185 permanece draft.

## Atualização do WriteJournal — 2026-11-08 (SHA a28b4e2)

- A falha de leitura foi refinada com novo RED: `File.exists()` podia retornar falso quando um componente do caminho não era diretório, fazendo caminho inacessível parecer diário ausente. O teste falhou no run #42440912188, SHA `ceeed1ed03bd806843db025af7a46c7deb5f4a9e` (1091 testes, 1 falha, exatamente no novo caso).
- Correção no SHA `a28b4e2854115ca476c7faff5a4a5d150b3e5a54`: `FileJournalStore.read()` abre o caminho via `Files.newInputStream` e só converte `NoSuchFileException` em ausência; `NotDirectoryException`, permissão e outras falhas de I/O propagam. `app/build.gradle.kts` confirma `minSdk=26`, compatível com NIO.
- CI integral do SHA `a28b4e2`, run #42441102377: **6/6 success** — JavaScript/Chromium, contratos Python, Kotlin/JVM, Android lint, grafo e `build_and_test`. Kotlin terminou com `BUILD SUCCESSFUL`; a execução JavaScript também concluiu e publicou as capturas.
- Artefatos do mesmo SHA: Playwright `ferramental-ux-85e905e2a5caa3be50adacd41f135e28fd22acd3`, 18.463.875 bytes, digest ZIP `sha256:bd59c60578235b121e55ee1cb6268250d01c0fe56dbe728fceb6ea9cd394cb31`; grafo `omegas-grafo`, 1.292.107 bytes, digest ZIP `sha256:6059eefebd90df23c9fd0046ab30e1fb6001fe908429312a5b0f2f8cf044bd2a`.
- Escopo continua em três arquivos: `WriteJournal.kt`, `WriteJournalTest.kt`, este ledger. Nenhum byte/protocolo/gravador/USB protegido foi alterado. O diário ainda **não** é consumidor do `TelemetryForegroundService` nem alimenta `ConnectionSupervisor.pending`; não há retomada automática.
- PR #186 segue draft, seis commits à frente da Aura. Revisão independente continua bloqueada: o AICtrl não tem este repositório conectado e todos os dispositivos Desktop Commander estão offline. CI verde não substitui review nem teste físico.
- Próximo passo: revisão independente do diff; depois, unidade separada de integração do diário ao supervisor/serviço, com RED→GREEN e sem tocar nas classes protegidas. Sem merge, APK ou gravação na central.


## Fechamento da unidade WriteJournal — 2026-11-08 (código validado em 6625cb1; registro documental em ea333c7)

- SHA de código validado: `6625cb12823e0edd927c63dce451acf4997f9994`; o registro documental anterior foi commitado em `ea333c72f79549b6f40ef1ffcb6311e8678fbbb3`.
- O RED foi confirmado no SHA `2a652e3681e460ebcb5a6666f0f940771ed83e92` (run #42444984972): duas falhas novas provaram que uma nova abertura sobrescrevia gravação interrompida e diário corrompido; o caso de abertura após todas as etapas conferidas passou.
- Correção em `WriteJournal.open()` bloqueia nova abertura quando `pending()` encontra etapa pendente, corrupção ou falha de leitura. Não há retomada automática.
- CI completo do SHA corrigido `6625cb12823e0edd927c63dce451acf4997f9994`, run #42445182597: 6/6 success — JavaScript/Chromium, contratos Python, Kotlin/JVM, Android lint, grafo e `build_and_test`.
- Revisão do diff confirma escopo restrito a `WriteJournal.kt`, `WriteJournalTest.kt` e este ledger. Nenhuma classe de protocolo, USB ou gravador nativo protegido foi alterada.
- PR #186 permanece draft e sem review independente submetido. O resultado CI verde não substitui revisão independente nem teste físico.
- Próxima etapa: obter revisão independente; depois, integrar o diário ao supervisor/serviço em unidade separada, com teste de reconexão e sem retomada automática. Nenhuma gravação na central, APK ou merge foi realizado.


## Correção final de ambiguidade do caminho no WriteJournal — 2026-11-08

- Investigação: `FileJournalStore.read()` ainda convertia qualquer `NoSuchFileException` em diário ausente. O runtime Android pode devolver essa exceção também quando um componente pai existe como arquivo, não diretório.
- RED: SHA `26b91b97f3e89f758b763560dbab979fffa3a22e`, run #42453647463. Kotlin/JVM executou 1.095 testes e falhou exatamente no novo caso sintético que injeta `NoSuchFileException` com pai que é arquivo. Python e grafo passaram.
- GREEN de código: SHA `ed96cf36796010eab10cb57a107cdfa993010650`. `FileJournalStore.read()` agora consulta `BasicFileAttributes` do pai antes de tratar `NoSuchFileException` como ausência; pai existente não-diretório propaga `NotDirectoryException`; arquivo realmente ausente com pai válido continua sem pendência. O teste usa uma função de abertura injetável somente para reproduzir deterministamente a variação do runtime; o padrão de produção continua `Files.newInputStream`.
- CI completo no SHA de código `ed96cf`, run de push #42453841428 e PR #42453845466: **6/6 success** (JavaScript/Chromium, contratos Python, Kotlin/JVM, Android lint, grafo e `build_and_test`).
- Artefato visual no mesmo SHA: #13606732059, 18.464.107 bytes, 81 PNG. Inclui capturas Playwright nas viewports 1280×720, 1024×600 e 800×480 (PNG em escala 2). O Mapa K ainda apresenta truncamento/overlap no painel direito, mais evidente em 800×480; a correção visual permanece bloqueada pela aprovação do dono para o conjunto das 23 telas e mapa de navegação.
- Revisão do diff: apenas `WriteJournal.kt`, `WriteJournalTest.kt` e este ledger nesta unidade; nenhum protocolo, USB ou gravador nativo protegido alterado. Revisão independente ainda não registrada.
- Limites: `WriteJournal` ainda não é consumidor do `TelemetryForegroundService`; `ConnectionSupervisor.pending` não é alimentado pelo diário; não há retomada automática. Nenhuma gravação na central, APK ou merge.
- Próximo passo: revisão independente da PR #186; depois, unidade separada para integração do diário ao supervisor/serviço com testes de reconexão, sem retomada automática.


## Checkpoint remoto — 2026-11-08 (estado do fechamento Aura)

### HEADs e CI reconsultados
- `OMEGASCANONICO`: `28afdaa74df1cb81e84d9f4ee3261d92050ad976`; CI #40173175791 SUCCESS.
- `claude/omega-aura`: `8aa82794d4daa814c4f728979fa5c78156850812`; CI #42435347134 SUCCESS.
- `claude/cool-curie-hs5nk9`: `c00717c75f100581b7f221c4438d9453fefdb9d8`; CI #40549838808 SUCCESS; ancestral já incorporada na Aura.
- `nova-experiencia`: `96c5a4b26469158eedd62ad4eded69e52888a0f4`; CI #40331380851 FAILURE; 5 commits exclusivos em relação à Aura, triagem seletiva, sem merge.
- `claude/aura-write-journal-safety`: `2379647592ef89b3eff7699e14a354fda37255cd`; CI #42454423803 SUCCESS, 6/6.
- `claude/aura-diario-store-safety`: `b3c4424aacdc95834e8e43161192f9548a3d0efe`; CI #42449830895 SUCCESS, 6/6.
- `claude/aura-detach-no-retry`: HEAD em validação no PR #188; CI atual #42459213752 ainda queued na última consulta.

### PRs
- #185 coordenador Aura: draft, base canônica; Aura 89 commits à frente, sem commits atrás.
- #186 WriteJournal: draft, 14 commits à frente da Aura, CI verde no SHA acima; sem review independente.
- #187 DiarioStore: draft, 10 commits à frente da Aura, CI 6/6 verde; sem review independente.
- #188 conexão: draft, branch `claude/aura-detach-no-retry`, 7 commits à frente da Aura no último HEAD `503bc2167356ad46522e31e3cc6d3beefcd79930`; CI #42459213752 pendente.
- #183 permanece uma frente histórica com base divergente e não foi incorporada.

### Conexão observacional, sem controle do transporte
- O RED original provou que `UsbDetached` não deve devolver CONNECT; somente novo ATTACHED pode solicitar a ação. A correção foi publicada em #188.
- `TelemetryForegroundService` agora observa porta aberta/fechada e quadros aceitos pelo `TelemetryStateStore`; `HubJavascriptBridge.getConnectionStatus()` expõe estado de leitura à WebView.
- A ação retornada pelo supervisor é informativa e ignorada pelo serviço. `UsbSerialManager` permanece dono do transporte/reconexão; nenhuma lógica de conexão ou escrita foi movida para o supervisor.
- `ConnectionSupervisor.pending` ainda não é alimentado por `WriteJournal`/DiarioStore; a cadeia de recuperação não está concluída e não existe retomada automática de gravação.
- O primeiro CI desta unidade revelou que `TelemetryStateStoreTest` comparava `ageMs` calculado em duas leituras distintas. O teste foi estabilizado comparando os campos persistentes e validando `ageMs` separadamente.
- A nova ponte `getConnectionStatus` adicionou um nó ao grafo de produção. O runner Graphify gerou o artefato; os blobs gerados foram conferidos por SHA contra o artefato e commitados em `503bc216`. O workflow original foi restaurado no mesmo commit. O grafo registra 122 pontes, 441 arquivos/testes conforme o contador, e zero elos essenciais quebrados. `getConnectionStatus` aparece apenas como ponte informativa ainda sem consumidor na UI.

### Branches remotas: inventário atualizado
- 17 branches preservadas, sem exclusões ou force push.
- Ancestrais absorvidas: `OmegasVermelho`, `claude/carregar-sessao-r7j85r` e seus arquivos, além de `claude/cool-curie-hs5nk9` na linhagem da Aura.
- Unidades ativas: `claude/omega-aura`, `claude/aura-write-journal-safety`, `claude/aura-diario-store-safety`, `claude/aura-detach-no-retry`.
- Novidade única preservada para triagem: `evidence/progbase-original` (14 commits exclusivos; cópia `arquivo/evidence-progbase-original`).
- Divergências preservadas: `OmegasDiamante`/arquivo correspondente, `work/codex-audit-progbase`/arquivo correspondente e `nova-experiencia`.
- As cópias `arquivo/*` continuam preservadas; nenhuma mudança histórica foi mesclada indiscriminadamente.

### UX, revisão e limites
- O contrato permanente de navegação continua em cinco áreas: AutoCal, Refino, Ajuste local, Ajuste global e Ferramentas. Sessões e Diagnóstico permanecem dentro de Ferramentas; nenhuma alternativa de quatro abas foi incorporada.
- As 23 telas do canvas e o mapa de navegação ainda não receberam aprovação conjunta do proprietário. Nenhuma tela existente foi alterada nesta unidade.
- Revisão independente continua bloqueada: o AICtrl informa que o repositório não está conectado à organização; os cinco dispositivos Desktop Commander estão offline. Revisão própria não será apresentada como independente.
- Nenhuma classe protegida `UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager` ou `AutoCalNativeActionManager` foi alterada. Nenhuma gravação na central, APK ou entrega no Drive foi realizada.

### Próxima sequência
- Concluir CI do SHA exato do PR #188.
- Obter revisão independente das PRs de persistência e conexão.
- Alimentar `ConnectionSupervisor.pending` a partir do diário persistente e testar interrupção/reconexão/Desfazer, sem retomada automática.
- Aprovar o conjunto de 23 telas e o mapa de navegação antes de qualquer alteração visual.
- Só depois avaliar merge para `OMEGASCANONICO`, APK ARM32 e Drive.
