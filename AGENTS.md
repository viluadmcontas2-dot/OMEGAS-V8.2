# OMEGAS Diamante — contrato do agente

**Meta:** o motor, no GNV, se comporta como na gasolina. O app sabe quão perto está, onde falta e qual é a única próxima ação. Observa sozinho; só muda algo quando o dono toca.

**Direção:** spec `docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md` · índice `docs/superpowers/plans/2026-10-03-00-norte-unico-index.md` (a Reconciliação R1–R10 prevalece sobre os planos de fatia).
**Método de UI/UX (Notion, só leitura):** [Blueprint CUSTOMROM](https://app.notion.com/p/3b68ee52ac5481839046f36b482aab44) (`CR §`) · [Omega Dev 4.0](https://app.notion.com/p/3b78ee52ac548170b5c1fb69606ced21) (`OD §`).

## Regras invariantes (spec §0.2)

1. Observar é automático; mudar é sempre o dono. Nada grava K, zera, restaura ou aplica sozinho. Exceção: o OMEGAS apaga sozinho pontos fora da curva do GNV e da gasolina (spec 2026-10-07-autocal-apagar-lenta rev2), com o carro rodando no combustível do ponto, readback e registro; Curva K continua só com o dono.
2. Todo botão é um toque: sem confirmação, sem segurar. Proteção = foto antes + Desfazer depois. Mesma exceção da regra 1.
3. O fim de toda ação é o readback da ECU; "Gravado" só depois dele.
4. Uma autoridade de estado: uma ponte, um `snapshot()` com revisão, uma fila de operações.
5. Nenhuma falha derruba o app: toda exceção vira estado `✗` legível com próxima ação.
6. Dois níveis: frase humana primeiro; comando, bytes e readback em "Detalhes técnicos".
7. Erro de transporte ≠ erro da ECU.
8. Comandos de leitura/escrita da ECU não mudam (`UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager`). Mesma exceção da regra 1. O automático reusa os bytes do apagamento manual (máscaras 0x016D/0x016E + `01 24 05`), um combustível por comando, com a máscara do outro toda preservada.
9. A fonte de verdade dos testes é o CI no GitHub Actions; a sessão pode rodar testes locais para iterar, mas só o CI verde no SHA vale para entrar.
10. Uma direção visual: tokens, cor com semântica, normalidade compacta.
11. Navegação inferior = 8 abas de primeiro nível, nesta ordem: Agora, Mapa K, Curva K, AutoCal, Ajuste GNV, Sessões, Ferramentas, Diagnóstico. O AutoCal é aba independente e não pode ser removido, escondido nem agrupado (nada de menu "Avançado"). Mudar isso exige pedido explícito do dono.

12. Direção visual dos botões (dono, 2026-10-07): toda barra de botões usa `.btn-bar`: tamanho natural do texto, 58 px de altura, 8 px entre eles, mesma linha, nunca esticada; botão `hidden` não ocupa lugar. Princípio: rodapé único por tela (todo botão realocável desce para a mesma barra; o espaço liberado vai para o conteúdo). Fluxo de UI obrigatório: skill `.claude/skills/omegas-ui-fluxo` (dizer, mudar, medir, olhar, corrigir, mostrar); o teste `tests/ui/btn-bar-rule.test.cjs` mede todas as abas no CI.

13. Telas travadas (dono, 2026-10-07): Curva K, Ajuste GNV, AutoCal e Mapa K estão aprovadas. A estrutura delas (sem cabeçalho, gráfico/grade grandes, rodapé único em ordem) é protegida por `tests/ui/layout-lock.test.cjs`; mudar exige pedido explícito do dono. Cartões continuam válidos em telas de informação (ex.: Diagnóstico); o critério é coerência, não "tudo vira rodapé".

14. Reset de gasolina/GNV nunca pausa o aprendizado (dono, 2026-10-08): `AUTO_CAL_ENABLE` termina em 1 após qualquer reset (RESET_PETROL/GAS/ALL), conferido por readback, mesmo em falha parcial/timeout (erro de transporte ≠ erro da ECU). Protegem: `tests/test_reset_nunca_pausa_aprendizado.py` + `ResetNuncaPausaAprendizadoTest.kt` (nome da trava: `reset-nunca-pausa-aprendizado`), no `tools/run_checks.py` e no `ci.yml`, mais o mutante `reset-sem-religar`. Não remover nem enfraquecer.

15. Salvar a Curva K é SEMPRE manual (dono, 2026-10-08): arquivo só quando o dono toca em Salvar (`curveSaveButton` -> `saveBackup`), em `Download/Omegas/Curva/`, nome didático `Curva K - dd-MM-aaaa HHhMMmSSs - N pontos - salva manualmente.json`. Nada de autosave, timer, foto ou aviso de "backup salvo" ao Gravar/Resetar/Desfazer/entrar na aba; a proteção do Desfazer é a foto PREWRITE privada do app (sem arquivo visível). Protegem: `tests/test_curva_salvamento_so_manual.py` + `KFactorCurveFileNameTest.kt` (nome da trava: `curva-salvamento-so-manual`), no `tools/run_checks.py` e no `ci.yml`, mais os mutantes `curva-*`. Não remover nem enfraquecer.

Também: `applicationId` continua `com.omegas.v7.test`. Validação física (classe 5) só com o dono no carro. SIL/CIU é independente: não portar nem copiar código SIL/CIU sem autorização explícita do dono.

## As 8 abas

01 Agora · 02 Mapa K · 03 Curva K · 04 AutoCal · 05 Refino · 06 Sessões · 07 Ferramentas · 08 Diagnóstico. Viewport 1280×720; toque ≥ 58 px (decisão do dono, 2026-10-04; era 76); texto crítico ≥ 22 px.

## Como trabalhar (enxuto — R12 do índice)

- Base `OmegasDiamante` (canônica); uma branch de trabalho por vez (`work/platina-<assunto>`), apagada no merge; PR agrupa fatias e entra com `build_and_test` verde no SHA.
- Issues simples: uma por fatia (`F<N> · …`, #132–#140); PR fecha com `Fecha #n`; épico #131 mapeia fatias e branches.
- Custo decide onde: escrever e testar local ou direto no GitHub, o que gastar menos tokens. Máximo de mudança antes de testar; teste só quando decide algo.
- Emulador só no fechamento de UI/UX (F7/F8). Um APK só, no fim (`verde-apk-now.yml`), com SHA-256.
- Cada PR diz o que mudou, a classe de prova (1 contrato · 2 sintético · 3 replay real · 4 emulador · 5 físico) e o que ficou não provado.
- Plano não bate com o código: decida, registre na Issue da fatia em uma linha e siga. Se muda o que o dono vê ou o que a ECU recebe, pare e pergunte.

## Ordem

Planos em `docs/superpowers/plans/` (índice `2026-10-03-00-norte-unico-index.md` + F1–F8), executados em sequência, agrupados em poucos PRs.

Estado do último APK: `STATUS.md`. Arquitetura: `docs/ARCHITECTURE.md`. Testes: `docs/TEST_STRATEGY.md`. Histórico: `docs/archive/`.
