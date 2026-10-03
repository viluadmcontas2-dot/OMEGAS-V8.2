# OMEGAS Platina — contrato do agente

**Meta:** o motor, no GNV, se comporta como na gasolina. O app sabe quão perto está, onde falta e qual é a única próxima ação. Observa sozinho; só muda algo quando o dono toca.

**Direção:** spec `docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md` · índice `docs/superpowers/plans/2026-10-03-00-norte-unico-index.md` (a Reconciliação R1–R10 prevalece sobre os planos de fatia).
**Método de UI/UX (Notion, só leitura):** [Blueprint CUSTOMROM](https://app.notion.com/p/3b68ee52ac5481839046f36b482aab44) (`CR §`) · [Omega Dev 4.0](https://app.notion.com/p/3b78ee52ac548170b5c1fb69606ced21) (`OD §`).

## Regras invariantes (spec §0.2)

1. Observar é automático; mudar é sempre o dono. Nada grava K, zera, restaura ou aplica sozinho.
2. Todo botão é um toque: sem confirmação, sem segurar. Proteção = foto antes + Desfazer depois.
3. O fim de toda ação é o readback da ECU; "Gravado" só depois dele.
4. Uma autoridade de estado: uma ponte, um `snapshot()` com revisão, uma fila de operações.
5. Nenhuma falha derruba o app: toda exceção vira estado `✗` legível com próxima ação.
6. Dois níveis: frase humana primeiro; comando, bytes e readback em "Detalhes técnicos".
7. Erro de transporte ≠ erro da ECU.
8. Comandos de leitura/escrita da ECU não mudam (`UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager`).
9. Todo teste roda no GitHub Actions; nada é compilado ou testado na sessão.
10. Uma direção visual: tokens, cor com semântica, normalidade compacta.

Também: `applicationId` continua `com.omegas.v7.test`. Validação física (classe 5) só com o dono no carro. SIL/CIU é independente: não portar nem copiar código SIL/CIU sem autorização explícita do dono.

## As 7 abas

01 Agora · 02 Mapa K · 03 Curva K · 04 AutoCal · 05 Refino · 06 Sessões · 07 Ferramentas. Viewport 1280×720; toque ≥ 76 px; texto crítico ≥ 24 px.

## Como trabalhar

- Base `OmegasPlatina`; uma branch `work/platina-f<N>-<slug>` e um PR por fatia; merge só com `build_and_test` e `Issue gate` verdes no SHA do PR.
- Work Unit = Issue `NORTE-WU-0<N>` (label `wu`, épico #131) + binding `docs/workunits/NORTE-WU-0<N>.md`. Tabela: `docs/superpowers/plans/README-issues.md`.
- Todo commit leva no corpo `NORTE-WU-0<N> · Tarefa <N>.<M>`; todo PR leva `Fecha #<issue>`.
- Teste antes do código. Rodar no GitHub: `tools/ci/remote-test.sh <gradle|node|python|checks|android> <alvo>` → `REMOTE_TEST=PASS|FAIL`.
- Cada PR diz o que mudou, a classe de prova (1 contrato · 2 sintético · 3 replay real · 4 APK no emulador · 5 físico) e o que ficou não provado.
- Plano não bate com o código: comente na Issue o que viu e decidiu. Se muda o que o dono vê ou o que a ECU recebe, pare e pergunte.
- Achado fora da fatia: Issue nova com label `achado`, ligada ao épico. Não consertar de carona.

## Ordem das fatias

| # | Plano | Work Unit |
|---|---|---|
| 0 | `2026-10-03-00-norte-unico-index.md` | NORTE-WU-00 #132 |
| 1 | `2026-10-03-f1-extracoes.md` | NORTE-WU-01 #133 |
| 2 | `2026-10-03-f2-poda-1.md` | NORTE-WU-02 #134 |
| 3 | `2026-10-03-f3-poda-2.md` | NORTE-WU-03 #135 |
| 4 | `2026-10-03-f4-cerebro-unico.md` | NORTE-WU-04 #136 |
| 5 | `2026-10-03-f5-autoridade-unica.md` | NORTE-WU-05 #137 |
| 6 | `2026-10-03-f6-ui-agora-curva-refino.md` | NORTE-WU-06 #138 |
| 7 | `2026-10-03-f7-ui-autocal-mapa-sessoes-ferramentas.md` | NORTE-WU-07 #139 |
| 8 | `2026-10-03-f8-acabamento.md` | NORTE-WU-08 #140 |

Estado do último APK: `STATUS.md`. Arquitetura: `docs/ARCHITECTURE.md`. Testes: `docs/TEST_STRATEGY.md`. Histórico: `docs/archive/`.
