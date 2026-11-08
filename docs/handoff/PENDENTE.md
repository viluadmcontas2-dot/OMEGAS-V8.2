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


## Checkpoint remoto — 2026-11-08 04:44 BRT

### HEADs e CI consultados nesta execução
- `OMEGASCANONICO`: `28afdaa74df1cb81e84d9f4ee3261d92050ad976`; CI no SHA: run [40173175791](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/40173175791), success.
- `claude/omega-aura`: `8aa82794d4daa814c4f728979fa5c78156850812`; push [42435343737](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42435343737) e PR [42435347134](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42435347134), success.
- `claude/cool-curie-hs5nk9`: `c00717c75f100581b7f221c4438d9453fefdb9d8`; CI run [40549838808](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/40549838808), success. Compare com Aura: 84 commits atrás, 0 à frente; já absorvida, não mesclar novamente.
- `nova-experiencia`: `96c5a4b26469158eedd62ad4eded69e52888a0f4`; CI run [40331380851](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/40331380851), failure. Cinco commits exclusivos sobre a canônica; preservar e aproveitar apenas componentes compatíveis, nunca o roteamento de quatro áreas.

### PRs e unidades remotas
- PR #185: draft, Aura `8aa82794` → canônica `28afdaa7`, 89 commits à frente, mergeable=true, sem aprovação; continua bloqueada pelos gates de UX, integração, revisão e validação física.
- PR #186: HEAD `d10e6473f7b8660def64819c68d833af3e0ae147`; CI PR run [42459324121](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42459324121), success. Unidade isolada de WriteJournal; não conectada ao serviço.
- PR #187: HEAD `b3c4424aacdc95834e8e43161192f9548a3d0efe`; CI PR run [42449830895](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42449830895), success. Unidade isolada de DiarioStore; não conectada ao serviço.
- PR #188: HEAD `503bc2167356ad46522e31e3cc6d3beefcd79930`; CI PR run [42459217591](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42459217591), success. Sete commits sobre Aura, sem conflito declarado. Corrige DETACHED para não disparar CONNECT e liga observação de porta aberta/fechada e quadro aceito ao supervisor; expõe `getConnectionStatus()` na ponte.

### Limites ainda abertos da unidade #188
- `getConnectionStatus()` ainda não tem consumidor na UI.
- `ConnectionSupervisor.pending` continua nulo; WriteJournal/DiarioStore não alimentam o supervisor.
- Permissão negada, handshake inválido e falhas transitórias ainda não estão conectados ao supervisor.
- Os testes cobrem a máquina de estados e o estado da telemetria; falta prova de integração Android/WebView e teste físico.
- A unidade não executa CONNECT/reconexão pelo supervisor, não retoma gravações e não escreve na central. `UsbSerialManager` continua dono do transporte.

### Inventário remoto completo — 17 branches
| Branch | HEAD | Classificação |
|---|---|---|
| `OMEGASCANONICO` | `28afdaa7` | Canônica |
| `claude/omega-aura` | `8aa82794` | Integração ativa |
| `claude/aura-detach-no-retry` | `503bc216` | Unidade ativa #188 |
| `claude/aura-diario-store-safety` | `b3c4424a` | Unidade ativa #187 |
| `claude/aura-write-journal-safety` | `d10e6473` | Unidade ativa #186 |
| `claude/cool-curie-hs5nk9` | `c00717c7` | Ancestral já absorvida pela Aura |
| `claude/carregar-sessao-r7j85r` | `8978a3aa` | Ancestral da canônica |
| `OmegasVermelho` | `78525860` | Ancestral da canônica |
| `OmegasDiamante` | `1057b500` | Divergente histórica; preservar |
| `work/codex-audit-progbase` | `188a7b61` | Divergente histórica; preservar |
| `evidence/progbase-original` | `d877c861` | Novidade única de evidências; triagem seletiva |
| `nova-experiencia` | `96c5a4b2` | Novidade de UX; preservar e reaproveitar seletivamente |
| `arquivo/OmegasDiamante` | `1057b500` | Arquivo legado, cópia exata |
| `arquivo/OmegasVermelho` | `78525860` | Arquivo legado, cópia exata |
| `arquivo/claude-carregar-sessao-r7j85r` | `8978a3aa` | Arquivo legado, cópia exata |
| `arquivo/evidence-progbase-original` | `d877c861` | Arquivo legado, cópia exata |
| `arquivo/work-codex-audit-progbase` | `188a7b61` | Arquivo legado, cópia exata |

Comparações com a canônica confirmam: OmegasDiamante e work/codex-audit-progbase são divergentes (350/568 e 349/568); evidence/progbase-original tem 14 commits exclusivos e 1.652 commits atrás; nova-experiencia tem 5 commits exclusivos e 0 atrás. Nenhuma branch foi apagada ou mesclada indiscriminadamente.

### Revisão independente e próximos gates
- Reviews GitHub registradas na PR #188: nenhuma.
- AICtrl informou que o repositório não está conectado à organização; revisão independente só fica disponível após conexão explícita do repositório pelo proprietário: https://aictrl.dev/onboarding/start?continuation=CD7rRxTE7Sjk7AFv7DuZtI78VDhZhE90YcMdrvYDUc0
- Próximo: revisão independente da PR #188; depois, integrar o estado à UI somente após aprovação do fluxo de 23 telas, e ligar Diário/supervisor sem retomada automática. CI verde não substitui revisão nem teste físico.
- Mockups permanecem artefatos de design, não implementação aprovada. Sem aprovação conjunta das 23 telas e mapa de navegação, não reformar telas.
- Sem APK: AGENTS.md exige aprovação explícita por rodada e prévias da interface real antes do workflow. Nenhum teste físico na RK3326 foi realizado nesta execução.
- Nenhuma classe protegida, byte/protocolo, gravador nativo ou transporte USB foi alterado; nenhuma escrita na central foi realizada.


## Checkpoint remoto — 2026-11-08 06:26 BRT

### Boot e autoridade
- Releitura nesta execução: `AGENTS.md` (blob `05e2b4ea`), `docs/REGRAS-DO-DONO.md` (`688b1d3d`), `docs/handoff/PENDENTE.md` (`cf94bc3c`), `docs/aura/PLANO.md` (`fed3efd6`), `docs/aura/CONTRATO-DE-FLUXO.md` (`ed3c29da`), além de `PROJECT.md` (`cc5fa4d4`), `STATUS.md` (`8c86fc8b`), `docs/handoff/CONTEXTO-DO-DONO.md` (`be73a3ba`) e o plano versionado (`482aff46`).
- GitHub remoto continua sendo a autoridade. Graphify não está exposto como ferramenta nesta sessão; portanto, **não** foi executado `graphify query/update`. Em compensação, o job de grafo foi verificado como GREEN nos SHAs Aura e unidades abaixo. Não confundir isso com consulta Graphify local.
- `PROJECT.md` declara esta missão sem geração de APK; `AGENTS.md` e regras do dono também exigem prévias reais e aprovação explícita por rodada. APK permanece bloqueado até os gates de UX/realidade e autorização aplicável.

### HEADs atuais (refs remotas lidos diretamente)
- `OMEGASCANONICO`: `28afdaa74df1cb81e84d9f4ee3261d92050ad976`.
- `claude/omega-aura`: `ff56abc72ed982006ddc7c0a274c84b0c30a6707`.
- `claude/cool-curie-hs5nk9`: `c00717c75f100581b7f221c4438d9453fefdb9d8`.
- `nova-experiencia`: `96c5a4b26469158eedd62ad4eded69e52888a0f4`.
- Unidades: `claude/aura-write-journal-safety` = `33c256b4a4efeb44b4c812fe1d6cf6580b487b7d`; `claude/aura-diario-store-safety` = `b3c4424aacdc95834e8e43161192f9548a3d0efe`; `claude/aura-detach-no-retry` = `64fa532526f04cf6ca13ed01f842b2f40d9fb7db`.

### PRs e CI no SHA exato
- PR #185: `claude/omega-aura` → `OMEGASCANONICO`, HEAD `ff56abc7`, 90 commits/324 arquivos, draft, mergeable=true; zero reviews registradas. CI do HEAD Aura run [42460415772](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42460415772), **6/6 success**.
- PR #186: `claude/aura-write-journal-safety` HEAD `33c256b4`, base Aura `ff56abc7`, 17 commits/3 arquivos, draft, mergeable=true; zero reviews. CI [42460927673](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42460927673), **6/6 success**. A comparação confirma 17 à frente e 0 atrás da Aura atual.
- PR #187: `claude/aura-diario-store-safety` HEAD `b3c4424a`, base registrada `8aa82794`, 10 commits/4 arquivos, draft, mergeable=true; zero reviews. CI [42449830895](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42449830895), **6/6 success**. Comparada à Aura atual: 10 à frente e 1 atrás; reconciliar a base antes de qualquer integração.
- PR #188: `claude/aura-detach-no-retry` HEAD `64fa5325`, base registrada `8aa82794`, 9 commits/8 arquivos, draft, mergeable=true; zero reviews. CI [42465975142](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42465975142), **6/6 success**. Comparada à Aura atual: 9 à frente e 1 atrás; reconciliar a base antes de qualquer integração.
- PR #183: aberta, base `OmegasDiamante`, head `claude/carregar-sessao-r7j85r`, mergeable=false, 434 commits/1.159 arquivos; divergência histórica, não usar como caminho da Aura.
- Nenhuma review submetida nas PRs #185, #186, #187 ou #188. CI verde não substitui revisão independente nem teste físico.

### Correção de checkpoint da unidade de conexão (#188)
- O checkpoint anterior estava desatualizado em `503bc216`. O HEAD remoto atual é `64fa532526f04cf6ca13ed01f842b2f40d9fb7db`.
- TDD registrado na PR: RED `2df6a967` (três referências ainda inexistentes); GREEN `64fa5325`. O serviço agora observa `permissionPending` e `permissionDeniedDeviceName` antes do early-return por ausência de transição física. Teste Kotlin valida PERMISSAO → PROBLEMA/SEM_PERMISSAO sem emitir CONNECT/REQUEST_PERMISSION; contrato Python verifica callback e ordem.
- Artefato UX do run #42465975142: 18.463.804 bytes, digest SHA-256 `e394574212e19e0350e8f453bebb58b25a202f344dd63275ca734dde472b88e4`; artefato do grafo: 1.295.842 bytes, digest `15c6678c00d6d5690f5c87717b04ff08467ff79e56cd7198171811eac2c0f8f2`.
- Limites ainda abertos: `getConnectionStatus()` não tem consumidor na UI; `ConnectionSupervisor.pending` não recebe dados do diário; handshake inválido/falhas transitórias não estão completamente integrados; sem Android/WebView real ou teste físico. A unidade observa permissão, porta e quadro válido; não assume o transporte nem faz escrita.

### Inventário completo — 17 branches remotas (busca paginada esgotada)
| Branch | HEAD | Classificação |
|---|---|---|
| `OMEGASCANONICO` | `28afdaa74df1cb81e84d9f4ee3261d92050ad976` | Canônica |
| `claude/omega-aura` | `ff56abc72ed982006ddc7c0a274c84b0c30a6707` | Integração ativa |
| `claude/aura-write-journal-safety` | `33c256b4a4efeb44b4c812fe1d6cf6580b487b7d` | Unidade ativa #186 |
| `claude/aura-diario-store-safety` | `b3c4424aacdc95834e8e43161192f9548a3d0efe` | Unidade ativa #187 |
| `claude/aura-detach-no-retry` | `64fa532526f04cf6ca13ed01f842b2f40d9fb7db` | Unidade ativa #188 |
| `claude/cool-curie-hs5nk9` | `c00717c75f100581b7f221c4438d9453fefdb9d8` | Ancestral já absorvida; Aura está 85 commits à frente, 0 atrás |
| `claude/carregar-sessao-r7j85r` | `8978a3aac0d9b9afa6a3d938f108d043615a9608` | Ancestral da canônica |
| `OmegasVermelho` | `785258605337d8f3c0d57991e95fc566c832c761` | Ancestral da canônica |
| `OmegasDiamante` | `1057b500460e1fe5e09d2fce93107f3f70a7ccd8` | Divergente histórica; preservar |
| `work/codex-audit-progbase` | `188a7b61ff1c2108b155c40de3355027da31def2` | Divergente histórica; preservar |
| `evidence/progbase-original` | `d877c8610ac036308417666a803e18e813cda2f5` | Novidade única de evidências; triagem seletiva |
| `nova-experiencia` | `96c5a4b26469158eedd62ad4eded69e52888a0f4` | Novidade única de UX; 5 commits exclusivos, preservar e adaptar seletivamente |
| `arquivo/OmegasDiamante` | `1057b500460e1fe5e09d2fce93107f3f70a7ccd8` | Arquivo legado, cópia exata |
| `arquivo/OmegasVermelho` | `785258605337d8f3c0d57991e95fc566c832c761` | Arquivo legado, cópia exata |
| `arquivo/claude-carregar-sessao-r7j85r` | `8978a3aac0d9b9afa6a3d938f108d043615a9608` | Arquivo legado, cópia exata |
| `arquivo/evidence-progbase-original` | `d877c8610ac036308417666a803e18e813cda2f5` | Arquivo legado, cópia exata |
| `arquivo/work-codex-audit-progbase` | `188a7b61ff1c2108b155c40de3355027da31def2` | Arquivo legado, cópia exata |

Comparações atuais: `OmegasDiamante` = 350 à frente/568 atrás da canônica; `work/codex-audit-progbase` = 349/568; `evidence/progbase-original` = 14/1.652; `nova-experiencia` = 5/0. Nenhuma branch removida ou mesclada indiscriminadamente. A alternativa de quatro áreas continua rejeitada pelo contrato de cinco áreas.

### Matriz requisito → prova e bloqueios
| Requisito | Prova atual | Estado honesto |
|---|---|---|
| Segurança do WriteJournal | RED→GREEN; CI 6/6 no HEAD #186 `33c256b4` | Isolado; sem consumidor no serviço |
| Segurança do DiarioStore | RED→GREEN; CI 6/6 no HEAD #187 `b3c4424a` | Isolado; sem integração com o supervisor |
| Observação USB/permissão | RED→GREEN; CI 6/6 no HEAD #188 `64fa5325` | Observacional; sem ação de transporte pelo supervisor |
| CI Aura | run #42460415772 | 6/6 no SHA `ff56abc7` |
| CI canônica | run #40173175791 | SUCCESS no SHA `28afdaa7` (2026-10-25; histórico, não valida a Aura) |
| CI cool-curie | run #40549838808 | SUCCESS no SHA `c00717c7` (histórico) |
| CI nova-experiencia | run #40331380851 | FAILURE no SHA `96c5a4b2` (histórico) |
| 23 telas + fluxo | Mockups v3 e contrato lidos | Não aprovados pelo dono; não são implementação WebView |
| 1280×720 / 1024×600 / 800×480 | Artefatos Playwright remotos | Não substituem WebView Android real nem prova física |
| Revisão independente | Reviews GitHub: zero; AICtrl repo não conectado | Bloqueada até conexão/avaliador independente |
| Graphify | Ferramenta não exposta nesta sessão | Não executado; jobs de grafo remotos passaram nos SHAs citados |
| APK ARM32 + Drive | Sem build nesta execução | Bloqueado por gates de UX/realidade e aprovação; nenhum APK/Drive alegado |
| ECU/USB protegidos | Diff #188 não altera `UsbSerialManager`, bytes/protocolo ou gravadores | Sem escrita na central; sem teste físico |

### Próxima sequência (sem repetir etapas concluídas)
1. Obter revisão independente das PRs #186–#188; não chamar auto-revisão de independente.
2. Atualizar/reconciliar #187 e #188 com o HEAD atual da Aura, mantendo histórico e sem force push; só integrar cada unidade após revisão e CI no SHA resultante.
3. Integrar diário → `ConnectionSupervisor.pending` → serviço/UI apenas após contrato e testes RED→GREEN; oferecer continuação manual, nunca retomar escrita automaticamente.
4. Obter aprovação conjunta das 23 telas e mapa de navegação de cinco áreas antes de qualquer reforma nas telas existentes.
5. Completar evidência WebView Android e teste físico autorizado; só então reavaliar merge canônico e APK conforme regra de aprovação por rodada.

Nenhuma escrita na central, mudança de protocolo/bytes, alteração de classe protegida, APK, merge para canônica ou exclusão de branch nesta execução.


## Checkpoint remoto — 2026-11-08 06:47 BRT

### Boot, autoridade e limites
- Releitura obrigatória nesta execução: `AGENTS.md`, `docs/REGRAS-DO-DONO.md`, `docs/handoff/PENDENTE.md`, `docs/aura/PLANO.md`, `docs/aura/CONTRATO-DE-FLUXO.md`; também `PROJECT.md`, `STATUS.md`, `docs/handoff/CONTEXTO-DO-DONO.md` e o plano Superpowers versionado.
- Graphify não está exposto nesta sessão; nenhuma consulta Graphify foi alegada. Jobs remotos de grafo passaram nos SHAs citados.
- `PROJECT.md` mantém esta missão sem geração de APK. UX ainda sem aprovação do proprietário e não houve teste físico; portanto, nenhum APK nem entrega Drive nesta execução.

### HEADs principais
- `OMEGASCANONICO`: `28afdaa74df1cb81e84d9f4ee3261d92050ad976`.
- `claude/omega-aura`: `7dbd6f33fc0c72e582b612c8563f67441adc6700`.
- `claude/cool-curie-hs5nk9`: `c00717c75f100581b7f221c4438d9453fefdb9d8`.
- `nova-experiencia`: `96c5a4b26469158eedd62ad4eded69e52888a0f4`.

### PRs e CI exatos
- #185 Aura→canônica: HEAD `7dbd6f33`, 91 commits/324 arquivos, draft, mergeable=true; CI push #42467404236 e PR #42467409065, ambos **6/6 success**.
- #186 WriteJournal: HEAD `b1cba1c98e1b4de27c23160351736b7f6106b495`, 19 commits/3 arquivos, draft, mergeable=true. CI do novo merge commit: push #42468556763 e PR #42468560369 em andamento. O SHA de código anterior `33c256b4` teve 6/6 success (#42460927673).
- #187 DiarioStore: HEAD `a864a306e498401a4241e19a9719475e6724d09b`, 12 commits/5 arquivos, draft, mergeable=true. CI do novo merge commit: push #42468561515 e PR #42468565413 em andamento. O SHA de código anterior `b3c4424a` teve 6/6 success (#42449830895).
- #188 conexão/handshake: HEAD `a4729f6541c937901d6d22d01b3c2b963836bc23`, 19 commits/11 arquivos, draft, mergeable=true. CI do novo merge commit: push #42468566077 e PR #42468570020 em andamento. O SHA de código anterior `6814cb94` teve 6/6 success (#42467802267 e #42467804467).
- #189 cobertura UX: HEAD `83dca2e6066e65ba159ea89bc02b9920deaaf634`, 4 commits/3 arquivos, draft, mergeable=true. CI push #42468648538 e PR #42468652821 queued.
- #183 permanece histórico, base `OmegasDiamante`, 434 commits/1.159 arquivos, mergeable=false. Não é caminho da Aura.
- Reviews independentes nas PRs #185–#189: nenhuma. CI verde não substitui review nem teste físico.

### Reconciliação de base das unidades
- #186, #187 e #188 receberam merge commits com a Aura `7dbd6f33` como segundo pai; o HEAD de cada unidade foi preservado como primeiro pai. Antes da operação, a comparação mostrou que a única mudança exclusiva da Aura desde a base comum era `docs/handoff/PENDENTE.md`; os arquivos foram reconciliados com a proveniência dos ledgers antigos.
- Atualizações feitas com `force=false` e `expected_sha`; sem force push. Isto atualiza **as branches de trabalho**, não mescla as PRs na Aura ou na canônica.

### Auditoria visual das 23 telas e implementação
- `canvas.json`: 27 boards = **23 interativos + 4 auxiliares** (Main, Estados, Padrões, Funções).
- Antes da correção, `todas-as-telas.json` tinha 18 roteiros. RED no SHA `1ca6f82e`, run #42468165026: 8/9 testes passaram e o contrato falhou exatamente nos cinco boards ausentes: 01 Conectar, 01b Permissão, 01c Retomando, 01d Problema e 21 Memória.
- HEAD #189 `83dca2e6`: o JSON agora tem 23 chaves e avaliação direta encontrou 23/23 roteiros, sem faltantes. O CI adiciona execução do `render_dc.test.cjs`, render do canvas v3 e artefato `docs/evidence/img/mockups-v3/**`. Ainda aguarda CI; a renderização é **mockup**, não implementação WebView.
- As cinco áreas do app continuam AutoCal, Refino, Ajuste local, Ajuste global e Ferramentas; Sessões é rota interna de Ferramentas. Nenhuma alternativa de quatro áreas foi incorporada.
- Controladores atuais: `conectar.js`, `autocal-cockpit.js`, `autocal-original.js`, `refino.js`, `map.js`, `curve.js`; componentes reutilizáveis: `live-bar.js`, `acao-principal.js`, `advanced.js`, `drawers.js`, `coerencia-cartao.js`; infraestrutura `fluxo.js`, `diario.js`, `write-gate.js`, `write-verify.js`.
- `screens/conectar.js` é carregado, mas não encontrei montagem/uso de `OmegasConectar` no `app.js`; `getConnectionStatus()` ainda não tem consumidor visível. `core/diario.js`, `coerencia.js` e `coerencia-cartao.js` são carregados e a ponte Kotlin/NativeApi de diário existe, mas não foi encontrado consumidor de tela que conecte isso ao fluxo.
- O fluxo Curva→Mapa em `map.js` persiste a etapa 2 em localStorage depois da confirmação do Mapa K e não verifica o retorno de `pending.save()`. A retomada é manual (“Concluir curva”), nunca automática, mas o registro não é o WriteJournal nativo; risco permanece até integração e readback.

### Evidência visual real do app no SHA Aura 7dbd6f33
- Artefato Playwright [ferramental-ux-7dbd6f33](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42467404236/artifacts/13612635600): 18.463.832 bytes; SHA-256 verificado localmente `4c3827dbac1e6081ef9a26cfbf0207667caefd657079b391e95f3d4922903022`.
- 81 PNGs: 25 em viewport lógico 1280×720 (arquivo 2560×1440), 22 em 1024×600 (2048×1200), 22 em 800×480 (1600×960) e 12 mobile.
- Inspeção visual confirmou clipping: em 1024×600, painel Curva K corta “Última gravação/Desfazer”; em 800×480, Mapa K trunca “Curva→Mapa”, “Última gravação” e botão “Gravar 0 células”, com quebra/colisão de texto. Sessões 800×480 foi inspecionada como controle e não mostrou o mesmo problema.
- São capturas Playwright do app Web; não são WebView Android real nem teste físico da RK3326. A UX não foi alterada sem aprovação do proprietário.

### Estado da conexão e persistência
- #188: RED no SHA `901215c6` comprovou seis referências não resolvidas a `observeEngineStatus`; GREEN no SHA `6814cb94`, CI 6/6. `NativeRuntimeManager` encaminha estado estruturado já publicado pelo núcleo ao serviço; supervisor mapeia HANDSHAKE/ONLINE/RECOVERING/RECOVERING_HARD/ERROR. PRONTO só após quadro válido. Sem ação de transporte/escrita.
- #186 WriteJournal e #187 DiarioStore continuam isolados; `ConnectionSupervisor.pending` não é alimentado pelo diário. Nenhuma retomada automática foi implementada.
- Nenhuma classe protegida `UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager` ou `AutoCalNativeActionManager` foi alterada. Sem mudança de bytes/protocolo, sem escrita na central.

### Inventário completo — 18 branches remotas (busca paginada esgotada)
| Branch | HEAD | Classificação |
|---|---|---|
| `OMEGASCANONICO` | `28afdaa74df1cb81e84d9f4ee3261d92050ad976` | Canônica |
| `claude/omega-aura` | `7dbd6f33fc0c72e582b612c8563f67441adc6700` | Integração ativa |
| `claude/aura-write-journal-safety` | `b1cba1c98e1b4de27c23160351736b7f6106b495` | Unidade ativa #186 |
| `claude/aura-diario-store-safety` | `a864a306e498401a4241e19a9719475e6724d09b` | Unidade ativa #187 |
| `claude/aura-detach-no-retry` | `a4729f6541c937901d6d22d01b3c2b963836bc23` | Unidade ativa #188 |
| `claude/aura-ux-coverage` | `83dca2e6066e65ba159ea89bc02b9920deaaf634` | Unidade ativa #189, testes de mockup |
| `claude/cool-curie-hs5nk9` | `c00717c75f100581b7f221c4438d9453fefdb9d8` | Ancestral da Aura; 85 commits atrás, 0 à frente |
| `claude/carregar-sessao-r7j85r` | `8978a3aac0d9b9afa6a3d938f108d043615a9608` | Ancestral da canônica |
| `OmegasVermelho` | `785258605337d8f3c0d57991e95fc566c832c761` | Ancestral da canônica |
| `OmegasDiamante` | `1057b500460e1fe5e09d2fce93107f3f70a7ccd8` | Divergente histórica; preservar |
| `work/codex-audit-progbase` | `188a7b61ff1c2108b155c40de3355027da31def2` | Divergente histórica; preservar |
| `evidence/progbase-original` | `d877c8610ac036308417666a803e18e813cda2f5` | Novidade única de evidências |
| `nova-experiencia` | `96c5a4b26469158eedd62ad4eded69e52888a0f4` | Novidade única de UX; 5 commits exclusivos, seleção manual |
| `arquivo/OmegasDiamante` | `1057b500460e1fe5e09d2fce93107f3f70a7ccd8` | Arquivo legado, cópia exata |
| `arquivo/OmegasVermelho` | `785258605337d8f3c0d57991e95fc566c832c761` | Arquivo legado, cópia exata |
| `arquivo/claude-carregar-sessao-r7j85r` | `8978a3aac0d9b9afa6a3d938f108d043615a9608` | Arquivo legado, cópia exata |
| `arquivo/evidence-progbase-original` | `d877c8610ac036308417666a803e18e813cda2f5` | Arquivo legado, cópia exata |
| `arquivo/work-codex-audit-progbase` | `188a7b61ff1c2108b155c40de3355027da31def2` | Arquivo legado, cópia exata |

### Próxima sequência
1. Concluir CI dos novos merge commits #186–#188 e do novo SHA #189; repetir lint se o HTTP 504 de Gradle reaparecer.
2. Confirmar artefato mockup v3 e report de render sem falhas; manter separado das capturas do app.
3. Obter revisão independente; sem ela, não integrar unidades na Aura.
4. Integrar Diário→WriteJournal→ConnectionSupervisor.pending e fluxo manual de recuperação, com leitura/readback, sem escrita automática.
5. Obter aprovação do proprietário das 23 telas e mapa de cinco áreas; corrigir clipping em unidade visual separada após aprovação.
6. Somente após CI verde no SHA final, revisão independente, UX aprovada e teste físico autorizado, reavaliar merge canônico e APK ARM32.

Nenhum merge para Aura/canônica nesta execução, nenhuma branch apagada, nenhuma escrita ECU, nenhum APK ou Drive alegado.


## Checkpoint remoto — 2026-11-08 07:30 BRT

### Boot e autoridade
- Releitura nesta execução: `AGENTS.md`, `docs/REGRAS-DO-DONO.md`, `docs/handoff/PENDENTE.md`, `docs/aura/PLANO.md`, `docs/aura/CONTRATO-DE-FLUXO.md`; também `PROJECT.md`, `STATUS.md`, `docs/handoff/CONTEXTO-DO-DONO.md` e `docs/superpowers/plans/2026-11-07-omega-aura-fechamento.md`.
- Graphify não está exposto nesta sessão; nenhuma consulta/update Graphify foi alegada. O job remoto `grafo` passou nos SHAs Aura e unidades indicados abaixo.
- `PROJECT.md` define esta missão como “pronto para gerar APK, sem gerar APK”; APK é NON-GOAL. Sem build APK ou entrega Drive nesta missão.

### HEADs principais re-resolvidos
- `OMEGASCANONICO`: `28afdaa74df1cb81e84d9f4ee3261d92050ad976`.
- `claude/omega-aura`: `2f02af38ba4d1bcd58bff0c9d448a9e3bbd56723` (antes deste checkpoint).
- `claude/cool-curie-hs5nk9`: `c00717c75f100581b7f221c4438d9453fefdb9d8`.
- `nova-experiencia`: `96c5a4b26469158eedd62ad4eded69e52888a0f4`.
- A Aura permanece 92 commits à frente da canônica, sem commits atrás; PR #185 draft, 324 arquivos, mergeable=true, sem review.
- CI Aura: run [#42468854752](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42468854752), 6/6 success no SHA `2f02af38`.

### PRs e CI exatos
- #185 Aura→canônica: head `2f02af38`, base `28afdaa7`, 92 commits/324 arquivos, draft, mergeable=true; CI 6/6 success; nenhuma review.
- #186 WriteJournal: head `be43a77db9ad498ee1d0105c4d3a6190a8369e9b`, 24 commits/3 arquivos, draft, mergeable=false. Comparação contra Aura atual: 24 à frente/1 atrás, merge-base `7dbd6f33`. CI [#42470609400](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42470609400): Python, grafo, lint e JS passaram; Kotlin falhou em `TelemetryStateStoreTest.liveObject is the same frame as liveJson without a text round trip` (1 falha em 1.098 testes). O log aponta comparação textual incluindo `ageMs`, calculado por `System.currentTimeMillis()` em chamadas distintas.
- #187 DiarioStore: head `5251b7fd9e54f4557344a1776acb5a4ba2198d60`, 16 commits/5 arquivos, draft, mergeable=false, 16 à frente/1 atrás; CI [#42470639370](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42470639370), 6/6 success.
- #188 conexão/handshake: head `a4729f6541c937901d6d22d01b3c2b963836bc23`, 19 commits/11 arquivos, draft, mergeable=false, 19 à frente/1 atrás; CI [#42468570020](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42468570020), 6/6 success.
- #189 cobertura UX/mockup: head `1feef1f794902f3535c02da9b85ea53c11b8cf54`, 9 commits/7 arquivos, draft, mergeable=true, 9 à frente/1 atrás; CI [#42470782759](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42470782759), 6/6 success. Artefato mockup v3 id `13613895164`, 31.843.079 bytes, ZIP SHA-256 `0317c13276aac420d481bebab65f5e96c0a5d6f133ef4684ba9ffe3dd5552562`.
- #190 estabilização do teste de telemetria: head `9d18164d66801d4cdf6f5cd78f42bba50986aa23`, 1 commit/1 arquivo, draft, base Aura `2f02af38`, mergeable=true. PR run [#42471975498](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42471975498): grafo, Python, Kotlin e lint passaram; JavaScript/navegador ainda em andamento. Push run #42471958374 teve HTTP 502 ao baixar Gradle 8.9; não é falha de teste de código.
- Reviews independentes nas PRs #185–#190: nenhuma.

### Correção test-only em andamento (#190)
- RED observado em #186: o teste comparava JSON completo de duas chamadas, mas `ageMs` é amostrado em instantes diferentes.
- PR #190 compara os campos estáveis do frame e verifica `ageMs >= 0` separadamente; mantém a checagem de cópia independente.
- Alteração somente em `TelemetryStateStoreTest.kt`; sem produção, protocolo, USB ou gravador alterado. GREEN integral ainda depende do CI da PR #190.

### Evidência visual atual
- Artefato Playwright do app no SHA Aura `2f02af38`: run #42468854752, 81 PNGs, 19.209.168 bytes; ZIP SHA-256 `9472f33fa5091fa483ed49968af8eeeb7830a45ccefdd92138f24ac482fbac82`.
- Viewports lógicos: 1280×720 (25 PNGs 2560×1440), 1024×600 (22 PNGs 2048×1200), 800×480 (22 PNGs 1600×960), além de 12 mobile. A escala 2× do PNG foi preservada no relatório.
- Inspeção direta: 800×480 Mapa K mostra truncamento de “Última gravação/Desfazer” e quebra/colisão do botão principal; 1024×600 Curva K trunca o texto de “Última gravação/Desfazer”; 1280×720 AutoCal inspecionado sem o mesmo clipping no estado capturado. Artefato é app Web/Playwright, não WebView Android nem teste físico.
- Artefato mockup v3 da #189: 174 PNGs; inclui as cinco telas antes sem roteiro (Conectar, Permissão, Retomando, Problema e Memória). Contrato agora cobre 23/23 boards interativos; canvas tem 27 boards no total (23 interativos + 4 auxiliares). Inspecionadas amostras `01-Conectar` e `21-Memoria`. É DesignCanvas/mockup, não implementação.
- As cinco áreas permanecem AutoCal, Refino, Ajuste local, Ajuste global e Ferramentas. Nenhuma alternativa de quatro áreas incorporada. Aprovação conjunta do dono ainda pendente.

### Inventário completo — 19 branches remotas
| Branch | HEAD | Classificação |
|---|---|---|
| `OMEGASCANONICO` | `28afdaa74df1cb81e84d9f4ee3261d92050ad976` | Canônica |
| `claude/omega-aura` | `2f02af38ba4d1bcd58bff0c9d448a9e3bbd56723` | Integração ativa |
| `claude/aura-write-journal-safety` | `be43a77db9ad498ee1d0105c4d3a6190a8369e9b` | Unidade ativa #186 |
| `claude/aura-diario-store-safety` | `5251b7fd9e54f4557344a1776acb5a4ba2198d60` | Unidade ativa #187 |
| `claude/aura-detach-no-retry` | `a4729f6541c937901d6d22d01b3c2b963836bc23` | Unidade ativa #188 |
| `claude/aura-ux-coverage` | `1feef1f794902f3535c02da9b85ea53c11b8cf54` | Unidade ativa #189, cobertura de mockups |
| `claude/aura-telemetry-state-test-stability` | `9d18164d66801d4cdf6f5cd78f42bba50986aa23` | Unidade ativa #190, teste-only |
| `claude/cool-curie-hs5nk9` | `c00717c75f100581b7f221c4438d9453fefdb9d8` | Ancestral já absorvida pela Aura; 85 atrás/0 à frente |
| `claude/carregar-sessao-r7j85r` | `8978a3aac0d9b9afa6a3d938f108d043615a9608` | Ancestral da canônica |
| `OmegasVermelho` | `785258605337d8f3c0d57991e95fc566c832c761` | Ancestral da canônica |
| `OmegasDiamante` | `1057b500460e1fe5e09d2fce93107f3f70a7ccd8` | Divergente histórica; preservar |
| `work/codex-audit-progbase` | `188a7b61ff1c2108b155c40de3355027da31def2` | Divergente histórica; preservar |
| `evidence/progbase-original` | `d877c8610ac036308417666a803e18e813cda2f5` | Novidade única de evidências; triagem seletiva |
| `nova-experiencia` | `96c5a4b26469158eedd62ad4eded69e52888a0f4` | Novidade única de UX; 5 commits exclusivos, seleção manual |
| `arquivo/OmegasDiamante` | `1057b500460e1fe5e09d2fce93107f3f70a7ccd8` | Arquivo legado, cópia exata |
| `arquivo/OmegasVermelho` | `785258605337d8f3c0d57991e95fc566c832c761` | Arquivo legado, cópia exata |
| `arquivo/claude-carregar-sessao-r7j85r` | `8978a3aac0d9b9afa6a3d938f108d043615a9608` | Arquivo legado, cópia exata |
| `arquivo/evidence-progbase-original` | `d877c8610ac036308417666a803e18e813cda2f5` | Arquivo legado, cópia exata |
| `arquivo/work-codex-audit-progbase` | `188a7b61ff1c2108b155c40de3355027da31def2` | Arquivo legado, cópia exata |

### Matriz requisito → prova → estado
| Requisito | Prova | Estado |
|---|---|---|
| CI Aura no SHA atual | #42468854752, 6/6 | Verde |
| CI canônica/cool-curie/nova | #40173175791 / #40549838808 / #40331380851 | Canônica e cool verdes históricos; nova falhou JS/Python/grafo |
| WriteJournal | RED→GREEN anterior; CI atual #186 falha por teste preexistente de `ageMs` | Isolado; sem consumidor no serviço |
| DiarioStore | CI #187 6/6 | Isolado; supervisor ainda não recebe pendências |
| Conexão/handshake | CI #188 6/6 | Observacional; sem escrita/transporte automático |
| Cobertura 23 telas | RED 18/23 → roteiros 23/23; CI #189 6/6 | Mockup, aprovação do dono pendente |
| Evidência de app em três viewports | 81 PNGs do SHA Aura 2f02; clipping inspecionado | Playwright Web, não WebView/ECU |
| Revisão independente | Reviews #185–#190 = zero | Bloqueada |
| Graphify local | Ferramenta não exposta | Não executado; job remoto grafo passou |
| APK ARM32/Drive | `PROJECT.md` declara NON-GOAL; UX/realidade não aprovadas | Não gerar nesta missão |
| ECU/USB protegidos | Nenhum diff altera classes protegidas ou bytes/protocolo | Sem escrita na central |

### Próxima sequência
1. Aguardar conclusão do JavaScript/navegador na PR #190; se passar, registrar GREEN e manter a alteração test-only separada.
2. Corrigir o estado de CI #186 após revisão da PR #190; as PRs #186–#189 estão uma revisão de base atrás da Aura e não serão atualizadas/mescladas antes de review independente.
3. Obter revisão independente das PRs #185–#190.
4. Integrar Diario → WriteJournal → `ConnectionSupervisor.pending` → serviço/UI com continuação manual e readback, sem retomada automática.
5. Obter aprovação conjunta das 23 telas e do mapa de cinco áreas; só depois abrir unidade de correção visual do clipping.
6. Testar WebView Android e hardware RK3326 com autorização; somente após os gates reavaliar merge canônico. APK permanece fora desta missão conforme `PROJECT.md`.

Nenhum merge para Aura/canônica, nenhuma branch apagada, nenhuma escrita ECU, nenhum APK ou entrega Drive nesta execução.


## Follow-up de validação — 2026-11-08

- PR #190 GREEN confirmado no SHA `9d18164d66801d4cdf6f5cd78f42bba50986aa23`: run PR [#42471975498](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42471975498), 6/6 success (JavaScript/Chromium, Python, Kotlin/JVM, lint, grafo e integração). A falha do push run #42471958374 foi HTTP 502 ao baixar Gradle 8.9; a execução de PR completou todos os testes.
- Revisão do diff #190: somente `TelemetryStateStoreTest.kt`, 11 adições/1 remoção; campos estáveis comparados separadamente de `ageMs`, sem alteração de produção.
- Revalidação direta do canvas/roteiros da #189: 27 boards, 23 interativos, 23 roteiros, 0 ausentes. Blobs `canvas.json` = `433768e3772ccd2d24bac08eb7a96ad66e31543c`; `todas-as-telas.json` = `4b0aac5e6875e250f8811df969f7eddc4b4d104d`.
- Aura continua em `901c9b5f27632222d78486fac40392a89cdc6579`; o CI disparado pelo checkpoint está em andamento: runs [#42472478570](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42472478570) e [#42472482814](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42472482814). No último poll, grafo, Python e Kotlin passaram; JavaScript/navegador e lint ainda executavam.
- #190 parte do antigo HEAD Aura `2f02af38`; comparação com o checkpoint atual `901c9b5` = 1 commit exclusivo em cada lado, merge-base `2f02af38`. Não mesclar antes de revisão independente e reconciliação de base.


## Checkpoint remoto — 2026-11-08 (execução 11)

### Boot e regras
- Releitura no remoto: `AGENTS.md`, `docs/REGRAS-DO-DONO.md`, `docs/handoff/CONTEXTO-DO-DONO.md`, este ledger, `PROJECT.md`, `STATUS.md`, `docs/aura/PLANO.md`, `docs/aura/CONTRATO-DE-FLUXO.md`, `docs/superpowers/plans/2026-11-07-omega-aura-fechamento.md` e `docs/grafo/PRODUCAO.md`.
- Os documentos Aura existem na branch `claude/omega-aura`; não existem na canônica atual. A canônica continua sendo `OMEGASCANONICO`.
- `PROJECT.md` declara esta missão como preparação sem build APK. O APK permanece bloqueado também por UX não aprovada, ausência de WebView Android/validação física e integração ainda incompleta.
- Dispositivos Desktop Commander: MMMACHINE, localhost, VL e dois Codespaces estão offline. Graphify CLI não está acessível. Foi consultado o artefato Graphify do CI (id `13614091379`, ZIP SHA-256 `8d0bfccfac4e768b53c6435fa63159604c7da52b8fda7a47c04b67cc64b710dc`) e feitas consultas dirigidas ao grafo serializado; não foi alegado `graphify query` local.

### HEADs re-resolvidos antes da escrita
- `OMEGASCANONICO`: `28afdaa74df1cb81e84d9f4ee3261d92050ad976`.
- `claude/omega-aura`: `e31121e51ef4e6aa2fbae260b9f8a02d451e4660` (HEAD no início deste checkpoint; este registro cria o próximo commit documental).
- `claude/cool-curie-hs5nk9`: `c00717c75f100581b7f221c4438d9453fefdb9d8`.
- `nova-experiencia`: `96c5a4b26469158eedd62ad4eded69e52888a0f4`.

### CI no mesmo SHA Aura `e31121e5`
- Runs push `42472695708` e PR `42472699887`: 6/6 jobs SUCCESS em ambos. Jobs: Kotlin/JVM, Python, JavaScript/Chromium, Android lint, grafo e agregador de integração.
- O job JS executou `Capturas da interface testada (sem APK)`; portanto a evidência é Playwright/Web, não Android WebView nem aparelho.
- Artefato visual do run PR: id `13613234857`, 81 PNGs, ZIP SHA-256 `b5e96f1b3352fc3d684e16ce374d5bc6a9221649906a4051b67b42911eb7654c`: 25 em 1280×720 (PNG 2560×1440), 22 em 1024×600 (2048×1200), 22 em 800×480 (1600×960), 6 em 360×740 e 6 em 412×915. Foram abertas e inspecionadas capturas de Mapa K, Curva K, Ferramentas e AutoCal.
- Problemas visíveis no estado capturado: em 800×480, Mapa K e Curva K truncam o texto de "Última gravação"/"Desfazer" e comprimem a ação principal; Ferramentas mantém os oito cartões visíveis; AutoCal em 1280×720 não mostrou clipping equivalente na captura examinada. Não foi alterado CSS.
- Artefato Graphify reporta `built_at_commit=afcf200a5251dbdcd22123df415919989ef8996d`, um commit antes de `e31121e5`; a comparação confirma que a única diferença entre eles é `docs/handoff/PENDENTE.md`. O grafo cobre o mesmo código de app, mas seu marcador não é o SHA exato do checkpoint documental.

### Grafo de produção — lacuna de integração comprovada
- O grafo atual contém `WriteJournal`, `DiarioStore`, `ConnectionSupervisor` e `TelemetryForegroundService`, mas os elos ainda não fecham:
  - `WriteJournal` é referenciado pelos próprios testes; não há chamada do serviço.
  - `ConnectionSupervisor` é referenciado por `ConnectionHub` e testes; não há ligação do serviço.
  - `DiarioStore` é usado por `CalibrationJavascriptBridge`; não está ligado ao `WriteJournal`.
  - `TelemetryForegroundService` ainda não alimenta `ConnectionSupervisor.pending` com pendências reais.
- `docs/grafo/PRODUCAO.md` afirma explicitamente que nenhuma tela do canvas foi implementada no app. As 23 telas/roteiros são DesignCanvas/mockups e não equivalem a implementação. A aprovação conjunta das 23 telas e do mapa continua necessária antes de implementar/reformar a UI. As cinco áreas continuam: AutoCal, Refino, Ajuste local, Ajuste global e Ferramentas.

### PRs e CI atuais
- #185 Aura → canônica: OPEN, Draft, head `e31121e5`, base `28afdaa7`, 94 commits, 324 arquivos, mergeable=true; zero reviews.
- #186 WriteJournal: OPEN, Draft, head atualizado para `0afec022cfde2d4216d8ba78c79268f1db7a2810`, 25 commits à frente/3 atrás da Aura (merge-base `7dbd6f33`), mergeable=false. O CI novo `42473935723` foi disparado e estava em andamento no registro.
- #187 DiarioStore: OPEN, Draft, `5251b7fd`, 16 à frente/3 atrás, mergeable=false; 6/6 checks verdes.
- #188 conexão/detach: OPEN, Draft, `a4729f65`, 19 à frente/3 atrás, mergeable=false; 6/6 checks verdes.
- #189 cobertura UX/mockups: OPEN, Draft, `1feef1f7`, 9 à frente/3 atrás, mergeable=true; 6/6 checks verdes.
- #190 estabilização do teste de telemetria: OPEN, Draft, `9d18164d`, 1 à frente/2 atrás (merge-base `2f02af38`), mergeable=true; run PR `42471975498` 6/6 SUCCESS. O run push teve falha transitória HTTP 502 no download do Gradle.
- Nenhuma review independente foi registrada nas PRs #185–#190.

### TDD — correção test-only portada para #186
- RED reproduzido no CI #186, run `42470609400`: `TelemetryStateStoreTest.liveObject is the same frame as liveJson without a text round trip` comparava o JSON inteiro, incluindo `ageMs` calculado em instantes diferentes.
- GREEN da alteração test-only validado na PR #190, SHA `9d18164d66801d4cdf6f5cd78f42bba50986aa23`, run PR `42471975498`, 6/6.
- A mesma alteração foi portada seletivamente para a branch #186 no commit `0afec022cfde2d4216d8ba78c79268f1db7a2810`, arquivo único `TelemetryStateStoreTest.kt`, com proveniência explícita. Nenhum código de produção foi alterado por essa portabilidade. CI do novo SHA ainda pendente no momento do checkpoint.

### Inventário remoto — 19 branches, todas preservadas
| Branch | HEAD | Classificação |
|---|---|---|
| `OMEGASCANONICO` | `28afdaa7` | Canônica |
| `claude/omega-aura` | `e31121e5` | Integração ativa; este checkpoint gera novo HEAD documental |
| `claude/aura-write-journal-safety` | `0afec022` | Unidade ativa #186 |
| `claude/aura-diario-store-safety` | `5251b7fd` | Unidade ativa #187 |
| `claude/aura-detach-no-retry` | `a4729f65` | Unidade ativa #188 |
| `claude/aura-ux-coverage` | `1feef1f7` | Unidade ativa #189, mockups/roteiros |
| `claude/aura-telemetry-state-test-stability` | `9d18164d` | Unidade ativa #190, teste-only |
| `claude/cool-curie-hs5nk9` | `c00717c7` | Ancestral já absorvida pela Aura |
| `claude/carregar-sessao-r7j85r` | `8978a3aa` | Ancestral da canônica |
| `OmegasVermelho` | `78525860` | Ancestral da canônica |
| `OmegasDiamante` | `1057b500` | Divergente histórica; preservar |
| `work/codex-audit-progbase` | `188a7b61` | Divergente histórica; preservar |
| `evidence/progbase-original` | `d877c861` | Novidade única de evidência; triagem seletiva |
| `nova-experiencia` | `96c5a4b2` | Novidade única de UX; aproveitar só componentes selecionados, não a navegação de quatro áreas |
| `arquivo/OmegasDiamante` | `1057b500` | Arquivo legado, cópia exata |
| `arquivo/OmegasVermelho` | `78525860` | Arquivo legado, cópia exata |
| `arquivo/claude-carregar-sessao-r7j85r` | `8978a3aa` | Arquivo legado, cópia exata |
| `arquivo/evidence-progbase-original` | `d877c861` | Arquivo legado, cópia exata |
| `arquivo/work-codex-audit-progbase` | `188a7b61` | Arquivo legado, cópia exata |

### Matriz requisito → prova → estado
| Requisito | Prova | Estado |
|---|---|---|
| CI JS/Chromium, Python, Kotlin, lint e integração no mesmo SHA | Aura `e31121e5`, runs #42472695708/#42472699887 | Verde |
| Capturas nos três viewports | Artifact #13613234857, ZIP SHA acima | Web/Playwright; não Android |
| 23 telas | Canvas v3 + 23 roteiros; `docs/grafo/PRODUCAO.md` diz nenhuma implementada | Mockup; aprovação pendente |
| Diário → supervisor → serviço | Grafo de produção e classes isoladas | Falta integração |
| Revisão independente | Reviews #185–#190 vazias | Bloqueada |
| Teste físico RK3326/USB/central | Nenhuma execução de dispositivo nesta sessão; devices offline | Não provado |
| APK ARM32 e Drive | `PROJECT.md` NON-GOAL e gates anteriores abertos | Não gerar |

### Próxima sequência
1. Aguardar CI do commit #186 `0afec022`; se verde, registrar GREEN e manter o teste isolado/proveniência #190.
2. Obter revisão independente das PRs #185–#190 antes de reconciliar as unidades; não atualizar bases divergentes cegamente.
3. Integrar DiarioStore → WriteJournal → `ConnectionSupervisor.pending` → serviço/ponte, com recuperação oferecida manualmente, leitura/readback e nenhuma retomada automática.
4. Obter aprovação conjunta do proprietário para o conjunto de 23 telas e mapa de cinco áreas; só então implementar alterações visuais.
5. Capturar WebView Android real e executar testes autorizados na RK3326; CI Playwright não substitui isso.
6. Reavaliar merge para `OMEGASCANONICO` somente com review independente, UX aprovada, zero conflito, todos os gates verdes no mesmo SHA e proveniência reconciliada.

Nenhum merge para Aura/canônica, nenhuma branch apagada, nenhuma alteração em classes/bytes/protocolo protegidos, nenhuma escrita na central, nenhum APK ou entrega Drive nesta execução.


## Checkpoint remoto — 2026-11-08 08:56 BRT (revalidação de CI, proveniência e integração)

### HEADs re-resolvidos
- `OMEGASCANONICO`: `28afdaa74df1cb81e84d9f4ee3261d92050ad976`.
- `claude/omega-aura`: `e2fe60771adfd7ea4aa2143ea71084b75d48786d` (antes deste checkpoint documental).
- `claude/cool-curie-hs5nk9`: `c00717c75f100581b7f221c4438d9453fefdb9d8`; compare: 0 à frente/90 atrás da Aura, ancestral absorvida.
- `nova-experiencia`: `96c5a4b26469158eedd62ad4eded69e52888a0f4`; compare: 5 à frente/0 atrás da canônica.

### CI reconsultado por job e SHA
- Aura `e2fe60771adfd7ea4aa2143ea71084b75d48786d`: runs push #42474078314 e PR #42474082984, ambos 6/6 SUCCESS: Contratos Python, grafo, JavaScript/Chromium, Kotlin/JVM, Android lint e integração `build_and_test`.
- WriteJournal `0afec022cfde2d4216d8ba78c79268f1db7a2810`: run #42473935723, 6/6 SUCCESS. A correção test-only portada da #190 está verde no HEAD atual da unidade.
- DiarioStore `5251b7fd9e54f4557344a1776acb5a4ba2198d60`: run #42470639370, 6/6 SUCCESS.
- Conexão `a4729f6541c937901d6d22d01b3c2b963836bc23`: runs #42468566077 e #42468570020, ambos 6/6 SUCCESS.
- Cobertura UX/mockups `1feef1f794902f3535c02da9b85ea53c11b8cf54`: runs #42470778948 e #42470782759, ambos 6/6 SUCCESS.
- Estabilidade de teste `9d18164d66801d4cdf6f5cd78f42bba50986aa23`: PR run #42471975498, 6/6 SUCCESS; push run #42471958374 falhou no download do Gradle com HTTP 502. A execução PR verde é a prova aplicável ao HEAD da PR.
- Canônica: run #40173175791 SUCCESS no SHA atual, de 2026-10-25. Cool Curie: #40549838808 SUCCESS no SHA atual, de 2026-10-27. Nova experiência: #40331380851 FAILURE no SHA atual, de 2026-10-26. São resultados históricos dos HEADs atuais dessas branches, não CI recente.

### PRs e divergência de base
- #185 Aura → canônica: OPEN/Draft, HEAD `e2fe6077`, base `28afdaa7`, 95 commits à frente/0 atrás, 324 arquivos, mergeable=true, zero reviews.
- #186 WriteJournal: OPEN/Draft, `0afec022`, 25 à frente/4 atrás da Aura, mergeable=false.
- #187 DiarioStore: OPEN/Draft, `5251b7fd`, 16 à frente/4 atrás, mergeable=false.
- #188 conexão: OPEN/Draft, `a4729f65`, 19 à frente/4 atrás, mergeable=false.
- #189 cobertura UX: OPEN/Draft, `1feef1f7`, 9 à frente/4 atrás, mergeable=false.
- #190 estabilidade: OPEN/Draft, `9d18164d`, 1 à frente/3 atrás, mergeable=false.
- Reviews GitHub continuam vazias em #185–#190. AICtrl foi reconsultado: a organização pessoal existe, mas nenhum repositório GitHub está conectado; Code Review independente não está disponível. Não simular review nem pedir auto-review ao próprio autor.

### Inspeção de integração (sem escrita de código)
- Os arquivos `WriteJournal.kt`, `ConnectionSupervisor.kt` e `DiarioStore.kt` já existem na Aura, mas suas versões diferem das propostas nas branches #186/#188/#187. Blobs atuais na Aura: WriteJournal `93db4421`, ConnectionSupervisor `7db641d9`, DiarioStore `159ea959`. Propostas: `df7186f8`, `3e352b66`, `bd7996e3`, respectivamente. `TelemetryForegroundService.kt` também difere entre Aura (`a59a9603`) e #188 (`dda229b5`).
- Na Aura atual, `TelemetryForegroundService.kt` não referencia `ConnectionSupervisor`; `HubJavascriptBridge.kt` e `app.js` não expõem/consomem `getConnectionStatus`. `DiarioStore` é usado pela ponte de sessão, mas não alimenta `WriteJournal`; o supervisor não recebe pendência do diário.
- Na proposta #188, os callbacks observam permissão, porta e estado do núcleo, porém os valores de retorno `ConnectionAction` são descartados. A unidade é observacional: não executa CONNECT/RESTART_ENGINE, não alimenta `pending` e não há consumidor de UI para o novo estado. Não afirmar que a reconexão automática ou a recuperação da gravação estão concluídas.
- TDD/testes de política cobrem estados isolados; ainda falta teste de integração serviço→supervisor→ponte/UI e fluxo real de recuperação manual. Nenhuma escrita automática.
- O grafo serializado em `docs/grafo/producao.json` reporta 9 nós Kotlin, 122 pontes e 441 testes; inclui nós de testes WriteJournal/ConnectionSupervisor, mas não os nós das classes Kotlin. Os arquivos existem no HEAD Aura, portanto o grafo não prova ausência: precisa ser regenerado e conferido com `tools/grafo_producao.py`. Graphify CLI não está acessível nesta sessão; foi feita consulta dirigida ao JSON serializado, não alegado `graphify query` local.

### Inventário remoto revalidado — 19 branches, nenhuma removida
| Branch | HEAD | Classificação |
|---|---|---|
| `OMEGASCANONICO` | `28afdaa7` | Canônica |
| `OmegasDiamante` | `1057b500` | Divergente histórica; preservar |
| `OmegasVermelho` | `78525860` | Ancestral da canônica |
| `arquivo/OmegasDiamante` | `1057b500` | Arquivo legado, cópia exata |
| `arquivo/OmegasVermelho` | `78525860` | Arquivo legado, cópia exata |
| `arquivo/claude-carregar-sessao-r7j85r` | `8978a3aa` | Arquivo legado, cópia exata |
| `arquivo/evidence-progbase-original` | `d877c861` | Arquivo legado, cópia exata |
| `arquivo/work-codex-audit-progbase` | `188a7b61` | Arquivo legado, cópia exata |
| `claude/aura-detach-no-retry` | `a4729f65` | Unidade ativa #188 |
| `claude/aura-diario-store-safety` | `5251b7fd` | Unidade ativa #187 |
| `claude/aura-telemetry-state-test-stability` | `9d18164d` | Unidade ativa #190 |
| `claude/aura-ux-coverage` | `1feef1f7` | Unidade ativa #189, mockups/roteiros |
| `claude/aura-write-journal-safety` | `0afec022` | Unidade ativa #186 |
| `claude/carregar-sessao-r7j85r` | `8978a3aa` | Ancestral da canônica |
| `claude/cool-curie-hs5nk9` | `c00717c7` | Ancestral absorvida pela Aura |
| `claude/omega-aura` | `e2fe6077` | Integração ativa |
| `evidence/progbase-original` | `d877c861` | Evidência única; 14 commits exclusivos, triagem seletiva |
| `nova-experiencia` | `96c5a4b2` | UX única; 5 commits exclusivos, não importar navegação de quatro áreas |
| `work/codex-audit-progbase` | `188a7b61` | Divergente histórica; preservar |

### Matriz requisito → prova → estado
| Requisito | Prova atual | Estado |
|---|---|---|
| CI completo no mesmo SHA Aura | `e2fe6077`, #42474078314/#42474082984, 6/6 | Verde |
| CI completo da unidade WriteJournal | `0afec022`, #42473935723, 6/6 | Verde |
| CI das unidades DiarioStore/Conexão/UX | SHAs e runs acima, 6/6 cada | Verde isolado |
| 23 telas | Canvas v3 + 23 roteiros | Mockup; aprovação conjunta pendente; nenhuma tela implementada a partir do canvas |
| 1280×720, 1024×600, 800×480 | Artifact #13613234857, ZIP SHA `b5e96f1b3352fc3d684e16ce374d5bc6a9221649906a4051b67b42911eb7654c` | Playwright/Web; não Android WebView |
| Diário → WriteJournal → supervisor → serviço → UI | Blobs/diffs acima | Integração incompleta |
| Revisão independente | Reviews #185–#190 vazias; AICtrl sem repo conectado | Bloqueada |
| RK3326/USB físico | Dispositivos offline; nenhuma sessão de aparelho | Não provado |
| APK ARM32/Drive | `PROJECT.md` NON-GOAL e gates de aprovação abertos | Não gerar |

### Próximos passos
1. Conectar explicitamente o repositório ao AICtrl (ação do proprietário) para habilitar Code Review independente; até lá, não mesclar unidades isoladas.
2. Após review, reconciliar as bases divergentes por commits seletivos, sem force push, e regenerar o grafo no SHA integrado.
3. Integrar Diário → WriteJournal → `ConnectionSupervisor.pending` → serviço/ponte/UI; testar falha de leitura, corrupção, permissão negada, cabo removido e reconexão, com continuação apenas por toque explícito.
4. Manter as cinco áreas e não reformar telas existentes. Solicitar aprovação conjunta das 23 telas e mapa antes de qualquer implementação visual do canvas.
5. Capturar Android WebView real nas três resoluções e validar na RK3326; CI Playwright não substitui teste físico.
6. Reavaliar merge canônico só com revisão independente, UX aprovada, bases reconciliadas, zero conflito, CI completo no mesmo SHA e proveniência documentada.

Nenhum merge para Aura/canônica, nenhuma branch apagada, nenhuma alteração de bytes/protocolo/gravadores protegidos, nenhuma escrita na central, nenhum APK ou entrega Drive nesta execução.


## Checkpoint remoto — 2026-11-08 10:03 BRT (CI, artefatos exatos e auditoria de integração)

### Autoridade e HEADs re-resolvidos
- Releitura nesta execução: AGENTS.md, docs/REGRAS-DO-DONO.md, docs/handoff/PENDENTE.md, docs/aura/PLANO.md, docs/aura/CONTRATO-DE-FLUXO.md, PROJECT.md, STATUS.md, docs/superpowers/plans/2026-11-07-omega-aura-fechamento.md, docs/grafo/PRODUCAO.md e docs/handoff/CONTEXTO-DO-DONO.md.
- OMEGASCANONICO: 28afdaa74df1cb81e84d9f4ee3261d92050ad976.
- claude/omega-aura: 542c0c89177851bf0f088208686b90388a726b69 antes deste checkpoint documental.
- claude/cool-curie-hs5nk9: c00717c75f100581b7f221c4438d9453fefdb9d8.
- nova-experiencia: 96c5a4b26469158eedd62ad4eded69e52888a0f4.
- Compare atual: Aura 96 commits à frente/0 atrás da canônica; cool-curie 0 à frente/91 atrás da Aura, ancestral absorvida; nova-experiencia 5 commits à frente/0 atrás da canônica, divergência única preservada.
- O branch inventory API retornou 19 branches; página seguinte vazia. Todas preservadas.

### CI verificado por SHA e jobs
- Aura 542c0c89177851bf0f088208686b90388a726b69: push #42478550058 e PR #42478555405, ambos SUCCESS, 6/6. Jobs: JavaScript/Chromium, grafo, Kotlin/JVM, Android lint, Python e build_and_test.
- WriteJournal 0afec022cfde2d4216d8ba78c79268f1db7a2810: push #42473935723 SUCCESS, 6/6.
- DiarioStore 5251b7fd9e54f4557344a1776acb5a4ba2198d60: push #42470639370 SUCCESS, 6/6.
- Conexão a4729f6541c937901d6d22d01b3c2b963836bc23: PR #42468570020 SUCCESS, 6/6.
- Cobertura UX/mockups 1feef1f794902f3535c02da9b85ea53c11b8cf54: PR #42470782759 SUCCESS, 6/6.
- Estabilidade de telemetria 9d18164d66801d4cdf6f5cd78f42bba50986aa23: PR #42471975498 SUCCESS, 6/6. O push #42471958374 teve erro transitório HTTP 502 ao baixar Gradle; a execução de PR no SHA exato passou.
- Canônica 28afdaa7: último run #40173175791 SUCCESS em 25/10/2026; cool-curie c00717c7: #40549838808 SUCCESS em 27/10/2026; nova-experiencia 96c5a4b2: #40331380851 FAILURE em 26/10/2026. São resultados históricos, não CI recente.
- O PR run #42478555405 usa merge ref 838d21639231ff23e0fb76ef16c85c3bd9b1353e; o push run #42478550058 foi consultado separadamente e os artefatos abaixo foram construídos no SHA exato 542c0c89.

### Grafo Graphify e auditoria estática no SHA exato
- Artefato omegas-grafo id 13616552163, run push #42478550058; ZIP SHA-256 2e76b727421f7f3d104e0201aa4f7335ea27fecd32c7691ce3fd5e2ea9c0d36f.
- graphify-out/graph.json: built_at_commit=542c0c89177851bf0f088208686b90388a726b69, Graphify 0.9.104, 14.203 nós e 31.723 links; SHA-256 9c75ad40d3558d3fbe2bc4943e353538c9865e0a64ae232169f655404cf7976d.
- docs/grafo/producao.json: 9 abas, 98 evidências, 9 nós Kotlin, 121 pontes, 441 testes, 42 UI; SHA-256 f9a1b36185834424d224ee55f61fc83f35a16ab0cd07ed02dc4d4d99805bee76.
- docs/handoff/AUDITORIA.json do mesmo artefato: source_sha=542c0c89, integridade_ok=true, erros=0, arquivos ausentes=0, arestas inválidas=0, 51 pendências abertas. O comando de auditoria não executa testes; testes vêm dos jobs CI.
- Graphify CLI/graphify query não está exposto nesta sessão. Foi inspecionado e consultado o artefato serializado exato; não foi alegada execução local do CLI.

### Auditoria de integração no código e no grafo
- WriteJournal existe (blob 93db4421) e o grafo não mostra consumidor de produção; apenas sua própria suíte. TelemetryForegroundService (blob a59a9603) não referencia ConnectionSupervisor.
- ConnectionSupervisor (blob 7db641d9) mantém pending e ConnectionHub, mas a instância não é atualizada pelo serviço na Aura atual.
- Há ponte somente de leitura CalibrationJavascriptBridge.getConnection() → ConnectionHub.json() e getPendingWrite() → supervisor.pendingJson(); não há consumidor JS no app.js (blob 6dc61826). Não há getConnectionStatus nem OmegasConectar no grafo.
- DiarioStore (blob 159ea959) é acessado por CalibrationJavascriptBridge.loadDiario/saveDiario; native-api.js expõe diarioBridge(), mas app.js não instancia OmegasDiario/OmegasCoerencia nem usa diarioBridge(). WriteJournal e DiarioStore não estão ligados.
- screens/conectar.js é carregado por index.html, mas app.js não monta OmegasConectar. core/diario.js e core/coerencia.js também são carregados, porém não participam do fluxo de runtime.
- O PR #188 propõe observação do supervisor no serviço e uma ponte getConnectionStatus, mas ainda não integra o diário, não alimenta pending e não conecta a UI; ações retornadas continuam observacionais. Não declarar reconexão/recuperação completas.
- TDD isolado das unidades não substitui teste de integração serviço→supervisor→ponte→UI nem replay de recuperação manual. Sem retomada automática.

### Evidência visual do app no SHA exato 542c0c89
- Artefato ferramental-ux-542c0c89 id 13616632714, run push #42478550058; 81 PNGs, 18.463.802 bytes; ZIP SHA-256 b16b3b73ba5142e15deb42cd0c9bb8e6720f1bd54d538cca125ba48fd60372fb.
- 1280×720 lógico: 25 capturas (PNG 2560×1440); 1024×600: 22 (2048×1200); 800×480: 22 (1600×960); mobile: 12.
- Inspeção direta no artefato: Mapa K 800×480 mantém truncamento de Última gravação/Desfazer e colisão/compressão do botão principal; Curva K 1024×600 trunca o texto de Desfazer; AutoCal 1280×720 inspecionado sem clipping equivalente. Cinco áreas aparecem preservadas.
- Evidência é Playwright/Web no artefato do SHA exato, não Android WebView, não ECU real e não teste físico RK3326. Nenhum CSS foi alterado por falta de aprovação conjunta das 23 telas e do mapa de navegação.

### Auditoria de protocolo e arquivos protegidos
- A lista de 324 arquivos do PR #185 não contém UsbSerialManager, ResponseDrivenEcuEngine, AutoCalProtocol, KFactorManager, KWriteManager ou AutoCalNativeActionManager.
- AebProtocolFramesV7.kt foi renomeado para AebProtocolFrames.kt e os símbolos/package foram renomeados. Comparação entre o arquivo canônico e o Aura, normalizando apenas package/import/class/CalibrationShapeV7, resultou em conteúdo idêntico byte a byte (strings do arquivo normalizado): nenhum corpo gerador de frame foi alterado nesta renomeação.
- PRs #186–#190 também não alteram as classes protegidas listadas.
- Nenhum byte/comando foi enviado à central; nenhuma escrita ECU ocorreu.

### PRs e revisão independente
- #185: OPEN/Draft, head 542c0c89, base 28afdaa7, 96 commits à frente/0 atrás, 324 arquivos, mergeable=true; mergeable não significa aprovado.
- #186: OPEN/Draft, head 0afec022, mergeable=false.
- #187: OPEN/Draft, head 5251b7fd, mergeable=false.
- #188: OPEN/Draft, head a4729f65, mergeable=false.
- #189: OPEN/Draft, head 1feef1f7, mergeable=true.
- #190: OPEN/Draft, head 9d18164d, mergeable=true.
- Reviews GitHub #185–#190: zero em todas. AICtrl org pessoal foi localizada, mas get_started retornou onboarding/connect_repository; nenhum repo conectado. Revisão independente segue bloqueada. Link de conexão: https://aictrl.dev/onboarding/start?continuation=wBGFwg7MiqxgnwKcG0mWZZGrujHV9HALWZLdHvwQWAc

### Inventário completo das 19 branches remotas (HEAD atual; nenhuma removida)
| Branch | HEAD | Classificação |
|---|---|---|
| OMEGASCANONICO | 28afdaa74df1cb81e84d9f4ee3261d92050ad976 | Canônica |
| OmegasDiamante | 1057b500460e1fe5e09d2fce93107f3f70a7ccd8 | Divergente histórica |
| OmegasVermelho | 785258605337d8f3c0d57991e95fc566c832c761 | Ancestral da canônica |
| arquivo/OmegasDiamante | 1057b500460e1fe5e09d2fce93107f3f70a7ccd8 | Arquivo legado, cópia exata |
| arquivo/OmegasVermelho | 785258605337d8f3c0d57991e95fc566c832c761 | Arquivo legado, cópia exata |
| arquivo/claude-carregar-sessao-r7j85r | 8978a3aac0d9b9afa6a3d938f108d043615a9608 | Arquivo legado, cópia exata |
| arquivo/evidence-progbase-original | d877c8610ac036308417666a803e18e813cda2f5 | Arquivo legado, cópia exata |
| arquivo/work-codex-audit-progbase | 188a7b61ff1c2108b155c40de3355027da31def2 | Arquivo legado, cópia exata |
| claude/aura-detach-no-retry | a4729f6541c937901d6d22d01b3c2b963836bc23 | Unidade ativa #188 |
| claude/aura-diario-store-safety | 5251b7fd9e54f4557344a1776acb5a4ba2198d60 | Unidade ativa #187 |
| claude/aura-telemetry-state-test-stability | 9d18164d66801d4cdf6f5cd78f42bba50986aa23 | Unidade ativa #190 |
| claude/aura-ux-coverage | 1feef1f794902f3535c02da9b85ea53c11b8cf54 | Unidade ativa #189, mockups/roteiros |
| claude/aura-write-journal-safety | 0afec022cfde2d4216d8ba78c79268f1db7a2810 | Unidade ativa #186 |
| claude/carregar-sessao-r7j85r | 8978a3aac0d9b9afa6a3d938f108d043615a9608 | Ancestral da canônica |
| claude/cool-curie-hs5nk9 | c00717c75f100581b7f221c4438d9453fefdb9d8 | Ancestral absorvida pela Aura |
| claude/omega-aura | 542c0c89177851bf0f088208686b90388a726b69 | Integração ativa |
| evidence/progbase-original | d877c8610ac036308417666a803e18e813cda2f5 | Novidade única de evidência; triagem seletiva |
| nova-experiencia | 96c5a4b26469158eedd62ad4eded69e52888a0f4 | Novidade única de UX; 5 commits exclusivos, seleção manual |
| work/codex-audit-progbase | 188a7b61ff1c2108b155c40de3355027da31def2 | Divergente histórica |

### Matriz requisito → prova → estado
| Requisito | Prova | Estado |
|---|---|---|
| CI JS/Chromium, Python, Kotlin/JVM, lint, grafo e integração | 542c0c89, push #42478550058 e PR #42478555405, 6/6 | Verde no SHA |
| CI das unidades isoladas | SHAs e runs acima, 6/6 cada | Verde isolado; bases divergentes |
| 23 telas | Canvas v3 + 23 roteiros | Mockup; aprovação do dono pendente; não implementadas |
| 1280×720/1024×600/800×480 | Artefato #13616632714, ZIP SHA acima | Playwright/Web; não Android |
| Diário→WriteJournal→supervisor→serviço→UI | Grafo exato e inspeção de código acima | Falta integração |
| Revisão independente | Reviews vazias e AICtrl sem repo conectado | Bloqueada |
| RK3326/USB físico | Nenhuma sessão de aparelho autorizada/disponível nesta execução | Não provado |
| APK/Drive | PROJECT.md declara NON-GOAL desta missão; integração e UX ainda abertas | Não gerar nesta etapa |

### Próxima sequência
1. Conectar o repositório ao AICtrl para revisão independente; até então, não reconciliar/mesclar unidades divergentes.
2. Após review, reconciliar #186–#190 por commits seletivos, sem force push, mantendo proveniência e repetindo CI no SHA integrado.
3. Integrar DiarioStore→WriteJournal→ConnectionSupervisor.pending→serviço→ponte/UI com RED antes, recuperação somente por toque, reler estado atual antes de cada etapa e nenhum retry/escrita automática.
4. Obter aprovação conjunta do proprietário das 23 telas e do mapa de cinco áreas antes de reformar qualquer tela existente; corrigir clipping em unidade separada.
5. Produzir render Android WebView real e validação física RK3326 somente com ambiente/aparelho disponível e autorização.
6. Reavaliar promoção à canônica somente com review independente, UX aprovada, bases reconciliadas, zero conflito, CI completo no mesmo SHA e proveniência.
7. APK permanece NON-GOAL em PROJECT.md para esta missão; não gerar até mudança explícita do escopo e conclusão dos gates.

Nenhum merge para Aura/canônica nesta execução, nenhuma branch apagada, nenhum force push, nenhuma alteração de bytes/protocolo/gravadores protegidos, nenhuma escrita na central, nenhum APK ou entrega Drive.

## Checkpoint remoto — 2026-11-08 11:34 BRT (revalidação após CI e inventário completo)

### Boot, autoridade e limites
- Releitura nesta execução: `AGENTS.md` (blob `ec893379`), `docs/REGRAS-DO-DONO.md` (`688b1d3d`), `docs/handoff/PENDENTE.md` (`67b60f1d`, antes deste checkpoint), `docs/aura/PLANO.md` (`fed3efd6`), `docs/aura/CONTRATO-DE-FLUXO.md` (`ed3c29da`), `docs/handoff/CONTEXTO-DO-DONO.md` (`be73a3ba`), `PROJECT.md` (`cc5fa4d4`), `STATUS.md` (`8c86fc8b`), `docs/grafo/PRODUCAO.md` (`61625366`) e `docs/superpowers/plans/2026-11-07-omega-aura-fechamento.md` (`482aff46`).
- GitHub remoto é a autoridade. Graphify CLI/`graphify query` não está exposto nesta sessão; não foi alegada consulta local. O arquivo de grafo de produção foi lido e o job remoto `grafo` passou no SHA Aura abaixo.
- `PROJECT.md` ainda declara esta missão sem geração de APK. Apesar da meta futura de ARM32, não há aprovação UX, WebView Android nem validação física RK3326; nenhum APK/Drive nesta execução.

### HEADs atuais e comparação
- `OMEGASCANONICO`: `28afdaa74df1cb81e84d9f4ee3261d92050ad976`.
- `claude/omega-aura`: `32b55a588760e572f43ec8c6276693f8825bc1c0`.
- `claude/cool-curie-hs5nk9`: `c00717c75f100581b7f221c4438d9453fefdb9d8`.
- `nova-experiencia`: `96c5a4b26469158eedd62ad4eded69e52888a0f4`.
- Compare `OMEGASCANONICO...claude/omega-aura`: 97 commits à frente, 0 atrás. Compare `claude/omega-aura...claude/cool-curie-hs5nk9`: cool-curie 0 à frente/92 atrás; ancestral já absorvida. Compare canônica...nova-experiencia: 5 commits à frente/0 atrás; preservar apenas componentes compatíveis, nunca o roteamento de quatro áreas.

### CI da Aura no mesmo SHA
- `32b55a588760e572f43ec8c6276693f8825bc1c0`: push run [#42484256706](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42484256706) e PR run [#42484260733](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/42484260733), ambos SUCCESS, 6/6.
- Jobs confirmados em ambos: JavaScript/navegador, grafo, contratos Python, Kotlin/JVM, Android lint e build/integration (`build_and_test`).
- O commit `32b55a58` é documental: registra a auditoria e o CI verde do SHA de código anterior `542c0c89`; não é uma nova mudança funcional.

### PRs e unidades atuais
| PR | HEAD | Estado | Mergeability | Compare da branch de trabalho contra Aura | CI verificado |
|---|---|---|---|---|---|
| [#185 Aura](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/pull/185) | `32b55a58` | Draft | mergeable=true | 97 à frente/0 atrás da canônica | Aura 6/6 no SHA exato |
| [#186 WriteJournal](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/pull/186) | `0afec022` | Draft | false | 25 à frente/6 atrás; merge-base `7dbd6f33` | push #42473935723, 6/6 |
| [#187 DiarioStore](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/pull/187) | `5251b7fd` | Draft | false | 16 à frente/6 atrás; merge-base `7dbd6f33` | push #42470639370, 6/6 |
| [#188 Conexão](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/pull/188) | `a4729f65` | Draft | false | 19 à frente/6 atrás; merge-base `7dbd6f33` | PR #42468570020, 6/6 |
| [#189 Cobertura UX](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/pull/189) | `1feef1f7` | Draft | true | 9 à frente/6 atrás; merge-base `7dbd6f33` | PR #42470782759, 6/6 |
| [#190 Estabilidade telemetria](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/pull/190) | `9d18164d` | Draft | true | 1 à frente/5 atrás; merge-base `2f02af38` | PR #42471975498, 6/6 |

- Reviews GitHub consultadas: nenhuma submissão nas PRs #185–#190. `mergeable=true` não significa aprovação. As unidades #186–#190 têm bases divergentes; não fazer merge cego. Mesmo #189/#190, apesar de mergeable=true, aguardam revisão independente e reconciliação seletiva.
- PR #183 continua histórica: head `claude/carregar-sessao-r7j85r`, base `OmegasDiamante`, mergeable=false; não é caminho da Aura.

### Inventário remoto completo — 19 branches (HEADs reconsultados)
| Branch | HEAD completo | Classificação |
|---|---|---|
| `OMEGASCANONICO` | `28afdaa74df1cb81e84d9f4ee3261d92050ad976` | Canônica |
| `OmegasDiamante` | `1057b500460e1fe5e09d2fce93107f3f70a7ccd8` | Divergente histórica (350 à frente/568 atrás da canônica) |
| `OmegasVermelho` | `785258605337d8f3c0d57991e95fc566c832c761` | Ancestral da canônica (0 à frente/439 atrás) |
| `arquivo/OmegasDiamante` | `1057b500460e1fe5e09d2fce93107f3f70a7ccd8` | Arquivo legado, cópia exata |
| `arquivo/OmegasVermelho` | `785258605337d8f3c0d57991e95fc566c832c761` | Arquivo legado, cópia exata |
| `arquivo/claude-carregar-sessao-r7j85r` | `8978a3aac0d9b9afa6a3d938f108d043615a9608` | Arquivo legado, cópia exata |
| `arquivo/evidence-progbase-original` | `d877c8610ac036308417666a803e18e813cda2f5` | Arquivo legado, cópia exata |
| `arquivo/work-codex-audit-progbase` | `188a7b61ff1c2108b155c40de3355027da31def2` | Arquivo legado, cópia exata |
| `claude/aura-detach-no-retry` | `a4729f6541c937901d6d22d01b3c2b963836bc23` | Unidade ativa #188 |
| `claude/aura-diario-store-safety` | `5251b7fd9e54f4557344a1776acb5a4ba2198d60` | Unidade ativa #187 |
| `claude/aura-telemetry-state-test-stability` | `9d18164d66801d4cdf6f5cd78f42bba50986aa23` | Unidade ativa #190 |
| `claude/aura-ux-coverage` | `1feef1f794902f3535c02da9b85ea53c11b8cf54` | Unidade ativa #189, mockups/roteiros |
| `claude/aura-write-journal-safety` | `0afec022cfde2d4216d8ba78c79268f1db7a2810` | Unidade ativa #186 |
| `claude/carregar-sessao-r7j85r` | `8978a3aac0d9b9afa6a3d938f108d043615a9608` | Ancestral da canônica (0 à frente/134 atrás) |
| `claude/cool-curie-hs5nk9` | `c00717c75f100581b7f221c4438d9453fefdb9d8` | Ancestral absorvida pela Aura (0 à frente/92 atrás) |
| `claude/omega-aura` | `32b55a588760e572f43ec8c6276693f8825bc1c0` | Integração ativa |
| `evidence/progbase-original` | `d877c8610ac036308417666a803e18e813cda2f5` | Novidade única de evidência (14 à frente/1.652 atrás); triagem seletiva |
| `nova-experiencia` | `96c5a4b26469158eedd62ad4eded69e52888a0f4` | Novidade única de UX (5 à frente/0 atrás); seleção manual sem quatro áreas |
| `work/codex-audit-progbase` | `188a7b61ff1c2108b155c40de3355027da31def2` | Divergente histórica (349 à frente/568 atrás) |

Nenhuma branch foi removida. Nenhuma branch histórica foi mesclada indiscriminadamente.

### Matriz requisito → prova → estado
| Requisito | Prova | Estado honesto |
|---|---|---|
| CI JavaScript/navegador, Python, Kotlin/JVM, lint, grafo e integração | Aura push #42484256706 e PR #42484260733, 6/6 no SHA `32b55a58` | Verde nesse SHA |
| CI das unidades | Runs #42473935723, #42470639370, #42468570020, #42470782759 e #42471975498, todos 6/6 nos SHAs das unidades | Verde isolado; não prova integração |
| 23 telas | Canvas v3 e 23 roteiros interativos | Mockups; não são implementação WebView nem aprovação do dono |
| Três resoluções | Artefato Playwright #13616632714, 81 PNGs no SHA de código `542c0c89` | Evidência Web; não Android WebView nem teste físico |
| Fluxo Diário→WriteJournal→supervisor→serviço→ponte/UI | Grafo de produção e auditoria do SHA de código `542c0c89` | Integração ainda incompleta |
| Revisão independente | Reviews vazias; AICtrl `get_started` exige `connect_repository` | Bloqueada até conectar o repo |
| RK3326/USB físico | Sem sessão física nesta execução | Não provado |
| APK/Drive | `PROJECT.md` marca APK como NON-GOAL nesta missão; gates de UX/realidade ainda abertos | Não gerar nesta etapa |

### Revisão independente e próximo passo externo
- Organização pessoal AICtrl localizada; `get_started` retornou `onboarding/connect_repository`, sem repositório conectado. Link atual: https://aictrl.dev/onboarding/start?continuation=x3X1hymymZc2wDQlD_qcxFOUMwtVKhT_G6lcoi9wYiU
- Depois da conexão, revisar cada PR no HEAD exato. Até lá, não chamar a revisão do próprio agente de independente.

### Próxima sequência (sem repetir trabalho concluído)
1. Conectar o repositório ao AICtrl e obter revisão independente das PRs #185–#190.
2. Reconciliar as unidades divergentes por commits seletivos com proveniência, sem force push; repetir CI no SHA integrado.
3. Implementar o elo Diário→WriteJournal→ConnectionSupervisor.pending→serviço→ponte/UI com RED antes, GREEN focado e recuperação somente por ação manual.
4. Obter aprovação conjunta das 23 telas e do mapa de navegação das cinco áreas; não reformar telas existentes antes do OK.
5. Produzir render Android WebView real nas três resoluções e validação física RK3326.
6. Reavaliar APK ARM32 debug apenas depois dos gates e de resolver o conflito de escopo entre a meta de entrega e o NON-GOAL atual em `PROJECT.md`; verificar package id, SHA-256 e integridade antes de qualquer entrega.

Nenhum merge para Aura/canônica, nenhuma branch apagada, nenhum force push, nenhuma alteração de bytes/protocolo/gravadores protegidos, nenhuma escrita na central, nenhum APK ou entrega Drive nesta execução.
