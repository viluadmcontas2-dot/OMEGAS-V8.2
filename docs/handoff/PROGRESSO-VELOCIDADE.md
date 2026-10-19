# Progresso: velocidade de gravar Curva K / Mapa K (2026-10-19)

## Medido
- Original (ProgBase, omegasaudit.LOG): gravação = quadro -> eco -> `53 00 53` -> PURGE; NÃO relê nada (TabKFactor.md linhas 75/122/170). Leitura de linha `2A 54` ≈ 77-91 ms; vetor `29 61` 17-74 ms.
- Antes (OMEGAS), Mapa K: R leituras prévias + insertion + N escritas + insertion + R leituras = 2R+N+2, em 3 vezes na porta (fila + telemetria entre elas). Curva K: 1+N+1 (já lia o vetor inteiro).
- Tela: "0 de N células confirmadas" ficava parado até o fim (confirmedCells só muda no fim do lote) e o título dizia "Conferindo linha i de R".
- `tools/emulacao/simular_gravacao_k.py` conta transações no emulador (antes x depois).

## Feito
- `calibration/KBatchVerifyCore.kt` (+ `KBatchVerifyCoreTest.kt`): regras puras (linhas afetadas, só células alteradas, mensagens "Gravando i de N…").
- `KWriteManager`: sem conferência prévia; escrita + readback por linha na MESMA vez da porta; só células alteradas; cache recebe a linha lida.
- `KFactorManager`: readback do vetor compara só pontos alterados; "Gravando i de N…".
- `screens/map.js`: ocupado mostra progresso real do gravador, não "0 de N confirmadas".

## Pendente (precisa autorização do dono / ECU real)
- Meta -70% só com 1 quadro de vetor (0x37, `writeVectorU16`) para a Curva K: 32 -> 3 transações. Muda o quadro; não ativado.
