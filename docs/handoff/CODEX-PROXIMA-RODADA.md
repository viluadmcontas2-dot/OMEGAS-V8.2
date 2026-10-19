# Próxima rodada do Codex (dono, 2026-10-19) — PRIORIDADE 0, antes de tudo que estiver em CODEX-AUTONOMO.md

Leia: AGENTS.md → CONTEXTO-DO-DONO.md (regras **sem-atrito** e **copy para leigo**) → este arquivo → HANDOFF-ENGENHARIA-REVERSA-CODEX.md.
Estado: o APK com Valores do anti-apagão em cards e "Sugestão para o seu carro" está em `claude/carregar-sessao-r7j85r`. Sugestão atual = CONSTANTE calculada de 2 fontes (teste parado do dono + corpus). O dono quer mais: **inteligente e adaptativa**.

## 1. Anti-apagão adaptativo (o que o dono pediu)
1. **Olhar TODAS as sessões reais**, não só o teste parado: corpus compacto `tests/fixtures/sessoes-uteis/` (93 sessões, 177 mil amostras) e, se precisar do bruto, os zips do Drive `OMEGAS/Sessoes Uteis` (conector baixa um zip por vez). O problema real é o carro **morrer em movimento** (embreagem, ré, redução); parado ele só "quase morria".
2. Para CADA apagão/quase-apagão em movimento (motor parando e voltando a ligar): linha do tempo (rotação, combustível, MAP, estado da central) antes/durante/depois; o que a central fez; tempo até religar. Entregar `docs/progbase/bins/apagoes-em-movimento.md` (tabela + conclusões) e um JSON versionado.
3. **Refinar os números sem desperdiçar gasolina e sem margem sobrando**: para cada ajuste (Rotação máxima para agir, Queda de rotação para agir, Espera para sair do gás, Folga, Queda para parar), medir (a) quantas quedas reais cobre, (b) quantas vezes atuaria à toa (gasolina). Já existe `tools/sessoes/sugerir_antiapagao.py` (+ `gerar_sugestao_js.py`): estender. Conferir a semântica dos 4 extras (unidade "rpm por ciclo") com o código decompilado antes de recomendar números neles; sem prova, ficam "sem dado para sugerir".
4. **Adaptativo**: a sugestão tem que se recalcular sozinha a partir da telemetria/sessões do PRÓPRIO app do dono (não só constante no JS), por evento (ao terminar uma sessão), e nunca sugerir diminuir um valor se houve apagão em movimento com valor menor. Mostrar na tela, em linguagem leiga, "o que mudou e por quê", 1 toque para usar, Desfazer.

## 2. Aba Ferramentas: aprendizado e exportação
- **Remover "Aprendizado de outro arquivo"** (botão, ponte, código e testes). O app funciona com a telemetria atual; "não reciclar coisa antiga".
- Revisar "Aprendizado" (salvar): descobrir para que serve. Se for redundante com a exportação de sessões, unificar; qualquer sincronização tem que ser **automática e idempotente**, sem botão.
- **Melhorar a exportação das sessões**: um arquivo claro, nome com data/carro, abre direto, sem passos extras; incluir os apagões/quedas detectados e o que o app sugeriu.
- Meta: app **inteligente e veloz**, reduzindo a curva de aprendizado com inteligência, não com histórico reciclado.

## 3. Copy para leigo (teste que FALHA)
Varrer TODO texto e botão do app (todas as abas, avisos, erros). Para cada frase: "um usuário extremamente leigo entende? sabe de QUÊ está falando (rotação de quê?), o que fazer e o que acontece?". Ampliar `tests/ui/copy-sem-ambiguidade.test.cjs` (nenhum termo solto, nenhuma sigla, frases curtas, todo número diz de quê). Se não passa, o teste falha e o texto muda. Cruzar semântica ↔ escrita ↔ função (Graphify): cada botão aponta para o comando certo.

## 4. Depois
`HANDOFF-ENGENHARIA-REVERSA-CODEX.md` (evidence IDs, auto-auditoria, referências da AutoCal em Ferramentas).

## Regras de trabalho (aprendidas hoje)
- **Cada push cancela o CI em andamento.** Trabalho longo: `git worktree add /tmp/wt -b wip-x` e só faça merge+push quando o conjunto estiver pronto. APK: `apk.yml` (compila direto; só libera com lint + suíte do MESMO commit verdes).
- Testes locais mínimos; a suíte inteira é do CI (`ci.yml`, jobs paralelos). Teste de contrato de fluxo antigo (2 toques) deve ser reescrito, não enfraquecido.
- 1 toque, sem pergunta, sem janela, sem rolagem, sem botão "tentar de novo". Tela sempre olhada em 1280x720, 1024x600 e 800x480 (screenshot) antes de entregar.
- Comentar cada feature no issue #182. Nunca apagar branches, force push, expor token.
