# AutoCal — apagar automaticamente ponto GNV aprendido na lenta

Data: 2026-10-07 · Branch alvo: OmegasDiamante (79afa705) · Evidência: `docs/teia/ACHADOS.md` (F5, F9, F11)

## Objetivo

Alinhar a curva do GNV à da gasolina (gasolina = base). Hoje 16–43% das aquisições nativas do GNV nas bandas 2–9 acontecem em marcha lenta, e o ponto aprendido na lenta sai 11–26% acima do aprendido andando (F11). A comparação GNV × gasolina mistura regimes e acusa diferença que não existe. O app passa a apagar sozinho esses pontos para a ECU readquirir.

## Regra

**Banda do GNV cuja última aquisição foi na lenta → quando o carro estiver andando, apaga.**

Sem categoria de "suspeito", sem teto de tentativas por sessão. Readquirir de novo na lenta é aceito; o app apaga de novo na próxima vez que o carro andar.

### Definições (cruas, iguais às da análise F11)

- **Aquisição da banda i:** `NUM_BUF_UPD_GAS[i]` subiu entre dois snapshots nativos consecutivos sem reset no meio (o valor novo é maior que o anterior).
- **Aquisição na lenta:** na telemetria do intervalo entre esses dois snapshots, com `fuel = GNV` e `|load_bar − MAP_da_banda| < 0,03`, pelo menos 80% das leituras com `rpm < 1000`. `MAP_da_banda = MNFLD_PRESS_BUF_GAS[i] / 1024`. Precisa de pelo menos 3 leituras; com menos, a banda não é marcada.
- **Carro andando:** a leitura de telemetria atual tem `fuel = GNV` e `rpm ≥ 1000`.

### Disparo

Quando existir pelo menos uma banda marcada como "lenta" e o carro estiver andando, o app manda um `DELETE_POINT` com todas as bandas marcadas, no comando de máscara que já existe.

### Efeito sobre o automatch (intencional)

Apagar uma banda ruim antes do automatch **atrasa o automatch**, e esse é o objetivo: a ECU só fecha a conta quando a banda tiver sido reaprendida, de preferência rodando. Em trânsito de cidade, uma banda que volta sempre na lenta vai sendo apagada a cada saída, e o automatch fica adiado enquanto isso durar. Isso é aceito por design; não há guarda contra.

### Guardas (as únicas)

1. **Intervalo mínimo entre apagamentos: 5 s.** É o ACK/readback (~1,2 s) mais um snapshot nativo (mediana ~3 s). O valor é configurável.
2. **Só GNV.** A máscara da gasolina (0x016D) vai inteira como "preservar" (1). Depois do readback, se qualquer banda da gasolina tiver mudado (contador ou valor), o apagamento automático é desligado na sessão e o app registra o evento. A gasolina aprendeu só 3 vezes em 43 sessões; perder um ponto dela não tem volta.
3. **Seleção limpa.** Depois do readback, toda banda apagada sai da seleção da tela. Hoje há 3 casos de apagamento manual de banda que já estava zerada (seleção fantasma).

## Onde entra no código (Diamante)

- **Detecção:** nova unidade `autocal/IdleAcquisitionTracker.kt`. Recebe snapshots nativos (via `NativeAutoCalMonitor`) e telemetria. Mantém por banda: último contador, último snapshot, regime da última aquisição. Sem I/O, só uma função pura do estado.
- **Política:** nova unidade `autocal/AutoIdlePointCleaner.kt`. Decide "apagar agora: bandas [..]" a partir do tracker, da telemetria atual, do relógio (intervalo) e do estado do automatch.
- **Execução:** reaproveita `AutoCalNativeActionManager.executePointDelete` / `AutoCalPointDeleteProtocol` (máscaras 0x016D/0x016E + commit `01 24 05`), sem mudar bytes. Um novo caminho não-humano com `automatic = true` e `humanConfirmed = false` vai no recibo.
- **Registro:** cada apagamento automático vira `autocal_native_action` com `automatic: true`, as bandas, o motivo ("lenta"), a evidência (n leituras, fração rpm<1000) e o readback.

## Mudança de regra do projeto (precisa do dono)

`AGENTS.md`, regras 1 e 2, hoje: nada zera ou restaura sozinho, e toda ação é um toque do dono. Emenda proposta:

> Exceção: o OMEGAS apaga sozinho pontos do GNV aprendidos na marcha lenta (spec 2026-10-07-autocal-apagar-lenta), com readback e registro. Gasolina e Curva K continuam só com o dono.

## Teste

- **Unidade (CI):** tracker e política com sequências sintéticas: aquisição na lenta → andou → apaga; aquisição andando → não apaga; segundo disparo antes de 5 s → espera; gasolina mudou no readback → desliga; apagamento na véspera do automatch → apaga normalmente.
- **Replay real (classe 3):** fixtures recortadas (< 300 KB) das sessões 2026-10-06 19:21 e 20:30. O replay precisa reproduzir quantas bandas seriam marcadas e quando o app dispararia. Esperado nestes dados: as bandas b4–b7 marcadas várias vezes.
- **Físico (classe 5, só o dono):** uma sessão em pista com o apagamento ligado.

## Critério de sucesso (medido nos logs, sem interpretação)

1. A fração de aquisições do GNV nas bandas 2–9 que ficam na curva vindas da lenta cai (hoje 16–43%).
2. Por banda, a razão GNV/gasolina dos buffers fica mais perto de 1 (hoje 3,5–5 ms: 1,04; 1–2,5 ms: 0,89).
3. Nenhuma banda da gasolina muda por causa de um apagamento automático.

## Pendências antes do plano

- Confirmar no Portmon (2 logs, ainda não analisados) a ordem real dos bytes do comando de máscara e do commit.
