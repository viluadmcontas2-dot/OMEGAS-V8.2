# Anti-apagão: o teste do dono no log novo (OBS-FIO)
Fonte: `omegasaudit.LOG` (Drive CAPTURAS ORIGINAIS), analisado por `tools/sessoes/log_novo_antistallo.py` (determinístico; conta amostras de telemetria `48 01`, sem relógio). 14 932 amostras; o dono ligou/desligou a caixa 11 vezes (`35 FA 00 20…` liga, `…00…` desliga) e fez cortes de propósito com ela desligada (parado, pisar fundo e soltar).

| Estado | Amostras | Queda brusca de rotação | Quase-apagão (<450) | Pós-corte: rotação mínima | Vezes que afundou abaixo de 600 rpm |
|---|---|---|---|---|---|
| **LIGADO** | 14 258 | 0 | 0 | **680 rpm** | **0** |
| **DESLIGADO** | 315 | 0 | 1 | **447 rpm** | **26** |

Leitura simples: com o anti-apagão **desligado**, depois de cada corte a rotação afundou até 447 rpm e passou de 600 para baixo 26 vezes; com ele **ligado**, o pior caso foi 680 rpm e nunca passou de 600. É exatamente o que o dono sentiu ("desligado quer morrer, ligado para"). Limite: o trecho desligado é curto (315 amostras); mesmo assim o contraste é nítido. INFERIDO: a ajuda é a central segurar com gasolina por instantes (ver 11 trocas para gasolina com a função ligada, 2 com ela desligada — a conferir por vetor de combustível).
Próximo (Codex): pareamento req→resp dos quadros `35 FA` e leitura do vetor 307 (`29 33 01`) para ver os 5 números durante o teste; confirmar se o dono editou algum (`14 33 01 idx`).
