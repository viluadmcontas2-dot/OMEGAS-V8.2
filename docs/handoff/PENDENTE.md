# Pendente — ledger único (lido no início de cada sessão; atualizado a cada marco)

Regras permanentes: `docs/REGRAS-DO-DONO.md` e `AGENTS.md`. Quem é o dono e como pede: `CONTEXTO-DO-DONO.md`. Histórico de documentos antigos: `docs/archive/HANDOFF-2026-10.md`. Item feito sai daqui (fica no `git log`); nenhuma pendência aberta pode sumir sem ser feita ou bloqueada com causa.

## Estado verificado (2026-10-20)
- Branch canônica `OMEGASCANONICO`. Último APK oficial: v14, commit `404e83f`, `apk.yml` run 39435537729 (exige `build_apk=true`), SHA-256 `1abe83266aa25b31ea1173a03aade0e4ba53bb577115415ddec8d663511e3dc3`. CI de 6 jobs verde nesse commit. Não provado no aparelho (USB e WebView reais). Detalhes de APKs: `STATUS.md`.
- Anti-apagão grava em UMA chamada nativa (`applyAntiStalloFlag` / `applyAntiStalloValues`: ler, gravar e reler numa só etapa MANUAL_WRITE). Prepare/confirm ficou só como reserva da ponte.
- Regra sem atrito: 1 toque = ação feita (sem pergunta, 2º toque, janela, rolagem; nada por tempo). O app nunca grava sozinho; o toque é a ordem.
- Anti-apagão em movimento (93 sessões, 12,3 h): 20 apagões. Rotação máxima 1800 vê 20/20; a de fábrica 1600 vê 19/20; 1400 vê 15/20 (`docs/progbase/bins/limiares-em-movimento.md`). Engasgos parados são abstração (dono). O app não tem número fixo (decisão testada em `anti-apagao-sugestao.test.cjs`).
- Corpus completo: 177.457 leituras, 89 sessões com dados; 31 quedas, 28 paradas do motor (`docs/progbase/bins/apagoes-em-movimento.*`). Sem velocidade/chave, movimento e causa seguem desconhecidos.
- Evidência de volta por pressão baixa: pressão normal do gás ≈ 2,3-2,5 bar; 8 das 28 trocas GNV para gasolina ocorreram com pressão muito abaixo (0,26 a 1,34 bar). O app registra nível e pressão a cada troca.

## Redesenho de UX: canvas v3 (2026-10-27; frente "design", branch `claude/cool-curie-hs5nk9`)
- Fonte: `docs/produto/mockups/v3/` (telas, `LEIA-ME.md`, briefs do Codex, geradores). Artefato Design do dono: https://claude.ai/artifact/4J4VM2Bkc6zW4d1sNXe8w4. Ferramenta de conferência: `tools/design/render_dc.cjs` (teste `tests/design/render_dc.test.cjs`). Sincronia entre agentes: `tools/sync-agentes.sh`.
- Outra frente em paralelo: `origin/nova-experiencia` (4 abas: Regular, Carro, Ajuste fino, Ferramentas; spec `docs/superpowers/specs/2026-10-26-nova-experiencia-design.md`). Choca com a regra 8 (5 abas). Decisão do dono em aberto: 5 abas (recomendado: o AutoCal fica a 1 toque) ou 4. Reaproveitar nas duas: ajuda "?" por tela, primeiro uso, estado único da central.
- Decisões do dono (2026-10-26/27): pode mexer nas 4 abas travadas (mexida boa) e mostrar água e as 14 verificações; NÃO travar gravação com o carro andando; pode pedir GPS; "Viagens"; barra do topo por prioridade (No gás, Nível, Inj. gasolina, MAP, RPM, Tensão, rótulos MAP e RPM); zona do AutoCal apaga com 1 toque; Refino com 1 botão; gravar sem travar a tela (aviso discreto); balão com bolinhas do gás e mais de um estilo; leitura do nível do gás precisa; melhorar o filtro que apaga pontos com RPM baixo (1.000/1.200 ou valor configurável).
- [ ] Nada do canvas foi implementado no app. Só implementar depois do OK do dono sobre TODAS as telas + mapa de navegação (regra 10), e atualizar os testes que travam texto (`linguagem-leiga`: "MAP"/"RPM" na barra; `refino-mesmo-grafico`; `refino-screen.test.cjs` botão único).
- [ ] NOVO a provar/criar: contar engasgos antes → depois da gravação (hoje o histórico nunca zera e só a Curva K tem veredito); "Para você agora"; "O carro está apagando?" (tela 11); permissão de GPS para velocidade/viagens; estilos do balão (Kotlin `TelemetryOverlayController`, observacional); calibrar nível do gás no abastecimento (`registerRefuel` já existe na ponte).
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
- [ ] AutoCal (multimídia): botão ao lado de cada zona para APAGAR os pontos daquela zona, 1 toque, sem confirmação; o balão de informação não pode ficar por cima ao alterar algo.
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
