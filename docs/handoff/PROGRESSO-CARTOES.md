# Progresso das frentes de 2026-10-19 — cartões e velocidade de gravação

## Cartões Anti-apagão / Volta à gasolina / Bins / sem lazy-load (2026-10-19)
## Marco 0 (achados de evidência, antes de codar)
- F4 "Automatic change back to petrol" (CheckRitornoBenzina) NÃO tem bit em FLAG_CONF1: seu estado vem de TEMPO_RITORNO_BENZINA (SC 62, u8, escalar, `12 3E 00 <v> ck`, OBS-COD via Unicorn: `12 3E 00 00 50`). O bit FLAG_CONF1 palavra 1 0x0040 é OUTRA caixa (CheckBassaPressione, "mudança com pressão baixa impostável").
- "Low pressure value" 1,25 bar (campo EditBassaPressioneMp) NÃO é SC 189 (PRESS_RETROPASSAGGIO = grade 12×u16 em mbar: 1800..2200 na central do dono) nem SC 152 (LR; na central do dono 152 é TINJ_FILTRO, 8×u16). O campo usa métodos próprios do TStreamDati (0x43277C/0x4327C0/0x432670: RIF_PRESS_DIFF, COEFF_PRESS_DIFF) = valor derivado, fórmula DESCONHECIDA. Não gravar.
- Marco 1: tarefa 4 (sem lazy-load) implementada: index.html com todos os scripts, router sem loadRoute, app.js com cartão 'Não foi possível abrir esta parte' (teste novo tests/ui/todas-as-telas-no-arranque.test.cjs RED→GREEN). Ajustando testes antigos que dependiam do lazy-load.
- Marco 2 (feito, verde local): (4) sem lazy-load: index.html carrega tudo em ordem; router sem loadRoute; falha de tela = estado "Reconectando…" e o app tenta sozinho no próximo dado válido (sem botão, por ordem do dono). Testes: todas-as-telas-no-arranque (novo, RED→GREEN), write-gate, curva-k-suavizacao, leveza-orcamento, manual-automatch-advice, global-live-bar, refino-screen ajustados sem enfraquecer. (1) Anti-apagão: tela de valores mostra 1 número em destaque (Rotação máxima para agir) + "Ajustes extras (experimental)" recolhido com os outros 4; fluxo seguro já existente (prévia, 2º toque, ACK, leitura de volta, Desfazer, "A central não aceitou").
- Falha pré-existente (não é nossa): ferramentas-webview-regression #14.
## PENDENTE (não feito nesta rodada, prazo)
- (2) Volta automática à gasolina: ver Marco 0. Tempo (SC 62, u8, `12 3E 00 v ck`, faixa DFM 0–3 / manual 0–20 s) é provável; a caixa não tem bit (estado derivado de SC 62, regra do clique não decodificada: SetEsc(28,…)); pressão 1,25 bar = EditBassaPressioneMp, fórmula via RIF_PRESS_DIFF/COEFF_PRESS_DIFF não decodificada. Falta: decodificar 0x4A9484/0x4C526C com Unicorn (cpu_setter_original.py) e só então escritor genérico por vetor (Kotlin) + cartão.
- (3) Bins K: janela de 12 colunas RPM (SC 61, `14 3D 00 idx lo hi cs`) e 12 linhas de ms; vetor de ms NÃO provado (SC 120 = MAP_ESTERNO, não é bins). Não iniciado; teste de aceitação "A central aceitou 0 ms" pendente. Depende do escritor do Mapa K (outro agente).
- (5) Nível do gás/pressão no estado + evento `voltou_para_gasolina` nas sessões: não iniciado.

## Velocidade de gravar Curva K / Mapa K (2026-10-19)

### Medido
- Original (ProgBase, omegasaudit.LOG): gravação = quadro -> eco -> `53 00 53` -> PURGE; NÃO relê nada (TabKFactor.md linhas 75/122/170). Leitura de linha `2A 54` ≈ 77-91 ms; vetor `29 61` 17-74 ms.
- Antes (OMEGAS), Mapa K: R leituras prévias + insertion + N escritas + insertion + R leituras = 2R+N+2, em 3 vezes na porta (fila + telemetria entre elas). Curva K: 1+N+1 (já lia o vetor inteiro).
- Tela: "0 de N células confirmadas" ficava parado até o fim (confirmedCells só muda no fim do lote) e o título dizia "Conferindo linha i de R".
- `tools/emulacao/simular_gravacao_k.py` conta transações no emulador (antes x depois).

### Feito
- `calibration/KBatchVerifyCore.kt` (+ `KBatchVerifyCoreTest.kt`): regras puras (linhas afetadas, só células alteradas, mensagens "Gravando i de N…").
- `KWriteManager`: sem conferência prévia; escrita + readback por linha na MESMA vez da porta; só células alteradas; cache recebe a linha lida.
- `KFactorManager`: readback do vetor compara só pontos alterados; "Gravando i de N…".
- `screens/map.js`: ocupado mostra progresso real do gravador, não "0 de N confirmadas".

### Pendente (precisa autorização do dono / ECU real)
- Meta -70% só com 1 quadro de vetor (0x37, `writeVectorU16`) para a Curva K: 32 -> 3 transações. Muda o quadro; não ativado.
