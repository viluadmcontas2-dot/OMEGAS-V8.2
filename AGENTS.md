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

## Como trabalhar (enxuto — R12 do índice)

- Base `OmegasPlatina`; uma branch de trabalho por vez (`work/platina-<assunto>`), apagada no merge; PR agrupa fatias e entra com `build_and_test` verde no SHA.
- Issues simples: uma por fatia (`F<N> · …`, #132–#140); PR fecha com `Fecha #n`; épico #131 mapeia fatias e branches.
- Custo decide onde: escrever e testar local ou direto no GitHub, o que gastar menos tokens. Máximo de mudança antes de testar; teste só quando decide algo.
- Emulador só no fechamento de UI/UX (F7/F8). Um APK só, no fim (`verde-apk-now.yml`), com SHA-256.
- Cada PR diz o que mudou, a classe de prova (1 contrato · 2 sintético · 3 replay real · 4 emulador · 5 físico) e o que ficou não provado.
- Plano não bate com o código: decida, registre na Issue da fatia em uma linha e siga. Se muda o que o dono vê ou o que a ECU recebe, pare e pergunte.

## Ordem

Planos em `docs/superpowers/plans/` (índice `2026-10-03-00-norte-unico-index.md` + F1–F8), executados em sequência, agrupados em poucos PRs.

Estado do último APK: `STATUS.md`. Arquitetura: `docs/ARCHITECTURE.md`. Testes: `docs/TEST_STRATEGY.md`. Histórico: `docs/archive/`.
