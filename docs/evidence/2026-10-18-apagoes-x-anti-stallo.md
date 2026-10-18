# Apagões × ANTI_STALLO — o que os dados deixam concluir (2026-10-18)

Gerado por `tools/sessoes/apagoes_x_antistallo.py` (determinístico: a mesma entrada gera o mesmo texto). Só leitura de gravações antigas; nada foi escrito na ECU. Nenhuma decisão usa relógio: os eventos e as janelas são por ordem de amostras; `t_ms` só aparece como informação.

## Fontes
- corpus tests/fixtures/sessoes-uteis: 177457 amostras, 89 sessões, 0 eventos `engine_stall` do app.
- sessão 2026-10-16 18-38 (arquivo externo): 4266 amostras, 1 sessões, 1 eventos `engine_stall` do app.

## Hipóteses (escritas ANTES de olhar os eventos)
- **H1** a regra ANTI_STALLO está ligada e é fraca: atua, mas não evita o apagão.
- **H2** a regra está desligada (a caixa "Abilitazione anti-stallo" não está marcada).
- **H3** o apagão acontece fora do alcance da regra (rpm acima de 1600 ou sem queda de 100 rpm antes).

## Definições usadas (testadas em `tests/test_apagoes_antistallo.py`)
- **Apagão em GNV**: em uma sessão, rpm cai de ≥500 (em alguma das 8 amostras anteriores) para <100, sem registro de chave desligada, e o último combustível fora de corte antes da queda é GNV (byte 0x90). A chave NÃO é gravada nas sessões: por isso um apagão e um desligar de chave com giro em queda ficam parecidos nos dados (INFERIDO como apagão).
- **Apagão após saída do GNV**: igual ao apagão, mas houve GNV nas amostras anteriores e o último combustível fora de corte já não era GNV (ex.: estados 0x91, 0x08, 0x09, 0x0C, gasolina). É o caso em que o carro já tinha deixado o GNV e ainda assim apagou. **APAGAO_FORA_DO_GNV** = sem GNV na janela (não é assunto desta análise).
- **Saída GNV→outro**: amostra em GNV seguida de amostra fora do GNV com rpm ≥500. Destino por byte de estado: 0x91, 0x94 (corte), 0x08, 0x0C, GASOLINA_ATIVA (0x80) ou outro.
- **Causa da saída**: CORTE_DESACELERACAO = MAP ≤ 0.35 bar e injeção ≈ 0 (gas_ms e, fora do estado de corte, também petrol_ms < 0.3 ms); ANTI_APAGAO_CANDIDATO = rpm ≤ 1600 e queda ≥ 100 entre amostras vizinhas nas 3 últimas amostras (é o que a regra pediria, mas NÃO prova que foi a regra); o resto é OUTRA_OU_INDETERMINADA.
- **A regra deveria ter atuado?** SIM se, nas 8 amostras antes do apagão, existe par vizinho com rpm ≤ 1600 e queda ≥ 100; NAO se há ≥2 amostras de rpm sem lacuna e nenhum par serve; INDETERMINADO caso contrário.

## Contagem de eventos

| Evento | Quantidade |
|---|---|
| APAGAO_GNV | 8 |
| APAGAO_APOS_SAIDA_GNV | 14 |
| APAGAO_FORA_DO_GNV | 7 |
| SAIDA_GNV | 1384 |

## Apagões, evento a evento

| # | Sessão | Tipo | rpm pico | rpm das amostras anteriores | Maior queda | MAP (mín–máx) | Estados anteriores | A regra deveria ter atuado? |
|---|---|---|---|---|---|---|---|---|
| 1 | 005_RICA_202 | APAGAO_GNV | 1553 | 1553, 1463, 1368, 1256, 952, 541, 541, 541 | 411 | 0.198–0.982 | 0x94 0x94 0x90 0x90 0x90 0x90 0x90 0x90 | **SIM** — rpm 1368 -> 1256: rpm<=1600 e queda>=100 |
| 2 | 014_RICA_202 | APAGAO_GNV | 1237 | 1237, 1191, 1163, 1087, 873, 600, 600, 600 | 273 | 0.207–0.962 | 0x94 0x90 0x90 0x90 0x90 0x90 0x90 0x90 | **SIM** — rpm 1087 -> 873: rpm<=1600 e queda>=100 |
| 3 | 018_RICA_202 | APAGAO_APOS_SAIDA_GNV | 1814 | 1270, 1282, 1536, 1748, 1814, 1239, 768, 246 | 575 | 0.776–0.982 | 0x90 0x90 0x90 0x90 0x90 0x90 0x90 0x91 | **SIM** — rpm 1814 -> 1239: rpm<=1600 e queda>=100 |
| 4 | 027_RICA_202 | APAGAO_APOS_SAIDA_GNV | 1619 | 1619, 1581, 1399, 996, 758, 758, 758, 587 | 403 | 0.217–0.982 | 0x90 0x90 0x90 0x90 0x90 0x90 0x90 0x08 | **SIM** — rpm 1581 -> 1399: rpm<=1600 e queda>=100 |
| 5 | 030_RICA_202 | APAGAO_APOS_SAIDA_GNV | 1431 | 1431, 1080, 847, 511, 296, 358, 358, 358 | 351 | 0.178–0.982 | 0x90 0x90 0x90 0x90 0x88 0x89 0x09 0x09 | **SIM** — rpm 1431 -> 1080: rpm<=1600 e queda>=100 |
| 6 | 030_RICA_202 | APAGAO_APOS_SAIDA_GNV | 1346 | 1346, 1132, 725, 409, 409, 409, 690, 690 | 407 | 0.207–0.982 | 0x90 0x90 0x90 0x90 0x90 0x90 0x08 0x08 | **SIM** — rpm 1346 -> 1132: rpm<=1600 e queda>=100 |
| 7 | 030_RICA_202 | APAGAO_APOS_SAIDA_GNV | 1464 | 1464, 1276, 907, 654, 250, 250, 441, 441 | 404 | 0.207–0.982 | 0x90 0x90 0x90 0x90 0x90 0x89 0x09 0x09 | **SIM** — rpm 1464 -> 1276: rpm<=1600 e queda>=100 |
| 8 | 035_RICA_202 | APAGAO_GNV | 1878 | 1605, 1878, 1578, 1488, 681, 458, 458, 458 | 807 | 0.354–0.982 | 0x90 0x90 0x90 0x90 0x90 0x90 0x90 0x90 | **SIM** — rpm 1878 -> 1578: rpm<=1600 e queda>=100 |
| 9 | 043_RICA_202 | APAGAO_APOS_SAIDA_GNV | 891 | 859, 858, 866, 831, 835, 835, 891, 891 | 35 | 0.296–0.805 | 0x90 0x90 0x90 0x90 0x90 0x90 0x08 0x08 | **NAO** — nenhum par com rpm<=1600 e queda>=100 (maior queda 35) |
| 10 | 038_RICA_202 | APAGAO_APOS_SAIDA_GNV | 1374 | 1374, 1330, 1152, 775, 448, 448, 299, 299 | 377 | 0.325–0.982 | 0x90 0x90 0x90 0x90 0x90 0x90 0x09 0x09 | **SIM** — rpm 1330 -> 1152: rpm<=1600 e queda>=100 |
| 11 | 042_RICA_202 | APAGAO_GNV | 874 | 867, 874, 874, 870, 865, 851, 851, 851 | 14 | 0.325–0.452 | 0x90 0x90 0x90 0x90 0x90 0x90 0x90 0x90 | **NAO** — nenhum par com rpm<=1600 e queda>=100 (maior queda 14) |
| 12 | 045_RICA_202 | APAGAO_APOS_SAIDA_GNV | 926 | 713, 412, 291, 926, 926, 926, 926, 926 | 301 | 0.541–0.982 | 0x90 0x90 0x90 0x8C 0x8C 0x88 0x09 0x09 | **SIM** — rpm 713 -> 412: rpm<=1600 e queda>=100 |
| 13 | 048_MEDIA_20 | APAGAO_GNV | 1783 | 1783, 1717, 1656, 1549, 1274, 859, 525, 301 | 415 | 0.158–0.982 | 0x90 0x94 0x94 0x90 0x90 0x90 0x90 0x90 | **SIM** — rpm 1656 -> 1549: rpm<=1600 e queda>=100 |
| 14 | 048_MEDIA_20 | APAGAO_APOS_SAIDA_GNV | 859 | 859, 525, 301, 0, 463, 254, 254, 254 | 334 | 0.394–0.992 | 0x90 0x90 0x90 0x00 0x88 0x88 0x08 0x08 | **SIM** — rpm 859 -> 525: rpm<=1600 e queda>=100 |
| 15 | 056_MEDIA_20 | APAGAO_FORA_DO_GNV | 868 | 868, 868, 866, 867, 848, 848, 848, 499 | 349 | 0.276–0.394 | GASOLINA_ATIVA GASOLINA_ATIVA GASOLINA_ATIVA GASOLINA_ATIVA GASOLINA_ATIVA GASOLINA_ATIVA GASOLINA_ATIVA 0x81 | **SIM** — rpm 848 -> 499: rpm<=1600 e queda>=100 |
| 16 | 060_MEDIA_20 | APAGAO_FORA_DO_GNV | 974 | 841, 844, 846, 827, 833, 833, 974, 974 | 19 | 0.286–0.394 | 0xB0 0xB0 0xB0 0xB0 0xB0 0xB0 0xB4 0x2C | **NAO** — nenhum par com rpm<=1600 e queda>=100 (maior queda 19) |
| 17 | 053_MEDIA_20 | APAGAO_APOS_SAIDA_GNV | 870 | 870, 834, 834, 834, 834, 465, 465, 465 | 369 | 0.335–0.982 | 0x90 0x90 0x90 0x90 0x90 0x0C 0x0C 0x0C | **SIM** — rpm 834 -> 465: rpm<=1600 e queda>=100 |
| 18 | 055_MEDIA_20 | APAGAO_GNV | 1061 | 863, 833, 783, 1061, 940, 701, 701, 701 | 239 | 0.511–0.982 | 0x90 0x90 0x90 0x90 0x90 0x90 0x90 0x90 | **SIM** — rpm 1061 -> 940: rpm<=1600 e queda>=100 |
| 19 | 055_MEDIA_20 | APAGAO_GNV | 873 | 871, 869, 868, 866, 873, 863, 863, 863 | 10 | 0.354–0.452 | 0x90 0x90 0x90 0x90 0x90 0x90 0x90 0x90 | **NAO** — nenhum par com rpm<=1600 e queda>=100 (maior queda 10) |
| 20 | 057_MEDIA_20 | APAGAO_APOS_SAIDA_GNV | 873 | 873, 865, 824, 824, 824, 300, 300, 300 | 524 | 0.354–0.982 | 0x90 0x90 0x90 0x90 0x90 0x94 0x94 0x0C | **SIM** — rpm 824 -> 300: rpm<=1600 e queda>=100 |
| 21 | 061_MEDIA_20 | APAGAO_APOS_SAIDA_GNV | 873 | 872, 869, 868, 873, 858, 858, 858, 483 | 375 | 0.305–0.472 | 0x90 0x90 0x90 0x90 0x90 0x90 0x90 0x0C | **SIM** — rpm 858 -> 483: rpm<=1600 e queda>=100 |
| 22 | 068_MEDIA_20 | APAGAO_FORA_DO_GNV | 886 | 886, 884, 879, 886, 886, 886, 596, 596 | 290 | 0.315–0.884 | GASOLINA_ATIVA GASOLINA_ATIVA GASOLINA_ATIVA GASOLINA_ATIVA GASOLINA_ATIVA GASOLINA_ATIVA 0x81 0x81 | **SIM** — rpm 886 -> 596: rpm<=1600 e queda>=100 |
| 23 | 075_MEDIA_20 | APAGAO_FORA_DO_GNV | 913 | 878, 877, 888, 884, 913, 913, 913, 544 | 369 | 0.354–0.649 | 0x88 0x88 0x88 0x88 0x88 0x88 0x88 0x09 | **SIM** — rpm 913 -> 544: rpm<=1600 e queda>=100 |
| 24 | 084_MEDIA_20 | APAGAO_GNV | 867 | 867, 865, 862, 859, 864, 838, 838, 838 | 26 | 0.325–0.433 | 0x90 0x90 0x90 0x90 0x90 0x90 0x90 0x90 | **NAO** — nenhum par com rpm<=1600 e queda>=100 (maior queda 26) |
| 25 | 085_MEDIA_20 | APAGAO_FORA_DO_GNV | 873 | 872, 871, 873, 872, 861, 511, 511, 511 | 350 | 0.305–0.972 | GASOLINA_ATIVA GASOLINA_ATIVA GASOLINA_ATIVA GASOLINA_ATIVA GASOLINA_ATIVA 0x81 0x01 0x01 | **SIM** — rpm 861 -> 511: rpm<=1600 e queda>=100 |
| 26 | 092_MEDIA_20 | APAGAO_APOS_SAIDA_GNV | 1026 | 1026, 903, 694, 694, 374, 448, 448, 448 | 320 | 0.492–0.982 | 0x90 0x90 0x90 0x90 0x90 0x90 0x08 0x08 | **SIM** — rpm 1026 -> 903: rpm<=1600 e queda>=100 |
| 27 | 092_MEDIA_20 | APAGAO_FORA_DO_GNV | 1787 | 0, 0, 0, 0, 0, 0, 1787, 1787 | 0 | 0.982–0.992 | 0x00 0x00 0x00 0x00 0x00 GASOLINA_ATIVA 0x88 0x88 | **NAO** — nenhum par com rpm<=1600 e queda>=100 (maior queda 0) |
| 28 | 092_MEDIA_20 | APAGAO_APOS_SAIDA_GNV | 728 | 728, 509, 325, 521, 521, 521, 521, 521 | 219 | 0.707–0.982 | 0x90 0x90 0x08 0x8C 0x88 0x09 0x09 0x09 | **SIM** — rpm 728 -> 509: rpm<=1600 e queda>=100 |
| 29 | 122_POBRE_20 | APAGAO_FORA_DO_GNV | 1785 | 885, 879, 1370, 1785, 1615, 1716, 1763, 1739 | 170 | 0.452–0.609 | 0x88 0x88 0x88 0x88 0x88 0x88 0x88 0x88 | **NAO** — nenhum par com rpm<=1600 e queda>=100 (maior queda 170) |

- Resumo APAGAO_GNV: NAO=3, SIM=5.
- Resumo APAGAO_APOS_SAIDA_GNV: NAO=1, SIM=13.
- Resumo APAGAO_FORA_DO_GNV: NAO=3, SIM=4.

Observação: várias janelas têm o mesmo rpm repetido (ex.: 541, 541, 541); é quadro repetido/parado antes do apagão, não queda lenta. Quando a queda é de 1 amostra só (ex.: de 1000 para <100) a regra tem uma única observação para agir.

## Cruzamento com os `engine_stall` registrados pelo app
- corpus tests/fixtures/sessoes-uteis: o corpus compacto não guarda eventos `engine_stall` (só K, snapshots e logs). Nada a cruzar.
- sessão 2026-10-16 18-38 (arquivo externo): `NEAR_STALL` rpmBefore=1392, minRpm=375, afterCutoff=True. rpm mínimo ≥ 100: NÃO é apagão pela definição acima (é quase-apagão).

## Saídas do GNV por causa

| Estado de destino | Causa | Quantidade |
|---|---|---|
| 0x08 | ANTI_APAGAO_CANDIDATO | 1 |
| 0x08 | OUTRA_OU_INDETERMINADA | 2 |
| 0x8C | ANTI_APAGAO_CANDIDATO | 1 |
| 0x91 | ANTI_APAGAO_CANDIDATO | 8 |
| 0x91 | OUTRA_OU_INDETERMINADA | 73 |
| 0x94 | CORTE_DESACELERACAO | 1196 |
| 0x94 | OUTRA_OU_INDETERMINADA | 74 |
| 0x95 | CORTE_DESACELERACAO | 1 |
| GASOLINA_ATIVA | ANTI_APAGAO_CANDIDATO | 4 |
| GASOLINA_ATIVA | OUTRA_OU_INDETERMINADA | 24 |

Total de saídas: 1384 (ANTI_APAGAO_CANDIDATO 1.0%; CORTE_DESACELERACAO 86.5%; OUTRA_OU_INDETERMINADA 12.5%). Observação: "candidato" é só o formato do gatilho; corte de desaceleração e troca por outro motivo (GIRI_MIN_CAMBIO etc.) também podem coincidir com rpm baixa e caindo.

## O que decide H1 × H2 (e se o corpus decide)

Gatilho da regra = GNV, 500 ≤ rpm ≤ 1600, queda ≥ 100 entre amostras vizinhas. Controle = GNV na mesma faixa de rpm com queda < 50. Resultado nas 4 amostras seguintes:

| Grupo | n | Saiu do GNV | Corte | Apagou | Ficou no GNV |
|---|---|---|---|---|---|
| gatilho | 4328 | 31 (0.7%) | 34 (0.8%) | 10 (0.2%) | 4253 (98.3%) |
| controle | 60811 | 79 (0.1%) | 373 (0.6%) | 18 (0.0%) | 60341 (99.2%) |

Número: a saída do GNV depois do gatilho é 5.5 vezes a do controle (0.7% contra 0.1%).

Leitura: se a regra estivesse ligada e atuando, esperaríamos MUITO mais "saiu do GNV" depois do gatilho do que no controle. Se for parecido, os dados são compatíveis com H2 (desligada) ou com uma regra que não troca de combustível. Mas a troca também acontece por outros motivos, o gatilho de queda de rpm também aparece em desacelerações comuns e a unidade da espera (campo [2]) é desconhecida; então esta comparação só ORIENTA, não decide.

**O corpus NÃO permite decidir H1 × H2.** Dado que decide: (1) o valor da caixa "Abilitazione anti-stallo" lido da ECU (o endereço dela é DESCONHECIDO; precisa de captura do ProgBase original marcando/desmarcando só essa caixa, ou de leitura do endereço certo); (2) o `param_snapshot` da conexão (já gravado a cada conexão) mostrando ANTI_STALLO; (3) uma captura com os quadros `48 01` ao redor de um apagão ou quase-apagão para ver se houve troca de combustível dentro da janela da regra. Sem (1), H1 e H2 dão a mesma coisa nos dados de telemetria: o motor apagou depois de a rpm cair.

## O que é PROVADO / INFERIDO / DESCONHECIDO

| Afirmação | Grau |
|---|---|
| Os valores de fábrica lidos da ECU do dono são 1600·100·1500·2·20 | PROVADO (leitura Portmon) |
| Cada apagão listado acima caiu de ≥500 para <100 rpm nas gravações, pela definição acima | PROVADO (nos dados) |
| Os números "a regra deveria ter atuado" da tabela | PROVADO como conta sobre a ordem das amostras; vale só se a ordem campo→rótulo for a certa |
| [0]=rpm máx., [1]=queda que ativa, [2]=espera, [3]=histerese, [4]=queda que desativa | INFERIDO (numeração dos rótulos da tela do original) |
| Que o evento seja apagão e não chave desligada | INFERIDO (a chave não é gravada) |
| Que a rotação baixa e caindo seja o gatilho real da regra | INFERIDO |
| Se a regra está ligada | DESCONHECIDO |
| Unidade do campo [2] (espera) e onde fica a caixa de habilitação | DESCONHECIDO |
| Se H1, H2 ou H3 vale | DESCONHECIDO (H3 só é compatível com os apagões cuja resposta é NAO; nos SIM, H1 e H2 continuam abertas) |

Limite: poucos apagões, um carro. Não generalizar.
