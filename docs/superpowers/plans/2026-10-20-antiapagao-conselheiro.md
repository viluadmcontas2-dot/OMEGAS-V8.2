# Anti-apagão conselheiro (SOMBRA) — plano

Spec (dono, 2026-10-20): "me ajude a configurar os valores certos do anti-apagão". Hoje `AntiStallSessionAnalysis.kt` só conta quedas e diz
"Ainda não há dados para escolher um novo ajuste". Meta: ao fim da viagem, mostrar o EFEITO de cada Rotação máxima (1200..2200 de 200 em 200)
nos dados do próprio carro: quantas quedas fortes ela pegaria e quantas vezes agiria à toa. SÓ SUGERE: nenhuma escrita, nada automático (AGENTS.md, invariante D1/SOMBRA).
Só evidência, sem relógio: conta leituras. Referência da regra: `tools/sessoes/sugerir_antiapagao.py` (`corpus_quedas`: queda = >=350 rpm/s em 2 leituras; cruzamento M quando rpm[i-2] >= M > rpm[i]; "terminou em quase-apagão" = rpm < 350 nas 24 leituras seguintes).
Não alterar classes de comando da central. Só os 5 números continuam lidos/gravados como hoje; sem mudar `anti-apagao-valores.js`.

## Global Constraints
- Desconhecido é "—", nunca 0. Não prometer segurança. Texto simples, pt-BR, sem jargão.
- RED antes de GREEN, com saída mostrada. Testes: `AntiStallSessionAnalysisTest.kt` (JVM; veja `tools/jvm/`) e `node --test tests/ui/anti-apagao-sugestao.test.cjs`.
- Orçamento de arranque da UI: máx. 40 scripts / 1.100.000 bytes (não criar script novo).
- Não regenerar PNGs em docs/evidence/img no commit (git checkout -- docs/evidence/img).

### Task 1: Tabela por Rotação máxima na análise nativa
Arquivo: `app/src/main/java/com/omegas/prohub/autocal/AntiStallSessionAnalysis.kt` (+ teste em `app/src/test/java/com/omegas/prohub/autocal/AntiStallSessionAnalysisTest.kt`).
Em `finish()`, acrescentar a chave JSON `porRotacaoMaxima`: array de `{rotacaoMaxima, quedasPegas, quedasTotal, acionamentos, quedasSemRecuperar}` para M em 1200,1400,...,2200, calculada das leituras de rpm já observadas na sessão (guarde só o necessário; sem relógio, conta de leituras). `acionamentos` = cruzamentos de M com queda forte. Se a viagem tiver menos de 50 leituras, o array vem vazio. Não mudar as chaves existentes nem `motivo`.
Teste: sessão sintética com uma queda 1700→300 e um cruzamento sem queda; conferir os números para M=1600 e M=1800.

### Task 2: Mostrar na tela Valores (cartão da viagem)
Arquivo: `app/src/main/assets/ui/components/advanced.js` (cartão `.vv-sug`, ~linha 518) + teste em `tests/ui/anti-apagao-sugestao.test.cjs`.
Quando `sug.porRotacaoMaxima` tiver itens, mostrar uma frase por linha, só para os 3 valores mais próximos do atual da central: "Com 1.800: pegaria 3 de 4 quedas; agiria 12 vezes à toa." e uma linha final "Só informação: nada foi gravado." Sem botão que grave e sem atalho "usar sugestão" novo. Sem o campo (versão antiga) ou array vazio: manter o texto atual. Escapar HTML, números inteiros validados.

### Task 3: Nenhum campo numérico abre teclado sozinho
Campos: `components/advanced.js` (`data-adv-field`, já tem −/+ e lápis), `core/autocal-preferencias.js` (`data-pref-field`, `data-pref="refreshMs"`), `components/drawers.js` (`data-session-maxmb`, `data-session-keep`), `index.html` (`#mapAdjustmentValue`, `#curveTargetFactor`; ver `screens/map.js` e `screens/curve.js`).
Regra do dono (2026-10-20): ajustar valor = botões − / + ao lado, SEM teclado; teclado só se o dono quiser. Todo campo numérico ganha `inputmode="none"` (teclado não sobe ao tocar) e um jeito de abrir o teclado de propósito (o lápis do `advanced.js` já existe: reaproveitar o padrão, não inventar outro; ao tocar, vira `inputmode` numérico/decimal e foca). Onde faltarem botões − / + (`#mapAdjustmentValue`, `#curveTargetFactor`, maxmb, keep), adicionar com o passo do próprio campo e a mesma classe `step-btn`/`num-stepper`. Não mudar a lógica de validar/gravar. O `minRpmLearn` já é `readonly`: deixar.
Teste novo `tests/ui/sem-teclado-obrigatorio.test.cjs`: abre cada tela, confere `inputmode="none"` e que o − / + muda o valor sem foco no campo. Depois rodar `node --test tests/ui/*.test.cjs` inteiro (esperado 0 falhas) e reverter PNGs (`git checkout -- docs/evidence/img; git clean -fdq docs/evidence/img`).
