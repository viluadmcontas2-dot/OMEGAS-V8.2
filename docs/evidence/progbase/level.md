# Tema 6 — Level (nível do cilindro de GNV)

Fontes: telemetria (`telemetria.md` byte 13), DFM do DUMP (`DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin`, `__TFORMCONFIG__0.bin`, `__TFORMVISUALIZZA__0.bin`; offsets em `fontes/parametros-dfm-inventario.json`), desmontagem de `TFormConfig/GroupRifSensore` (VAs de estudo anterior sobre `DUMP/ProgBase.exe.Dump.bin`, não reexecutados nesta branch: `lacunas.md` L-12) e LN.

## 6.1 Sinal vivo

| Item | Valor | Selo | Fonte |
|---|---|---|---|
| Origem | byte 13 do payload de `48 01`, U8, **sem conversão** | PROVADO | desmontagem: copiado para `TFormConfig+0x1800` pelo `TimerDatiTimer` |
| Exibição no ProgBase | `TFormVisualizza.LabLivello` mostra o número bruto ("Livello 0") | PROVADO | DFM `DUMP/RT_RCDATA(10)__TFORMVISUALIZZA__0.bin` (`LabLivello`, `Caption = '0'`) |
| Valores reais | LN: 177 (0xB1) em quase toda a sessão (9.108 quadros); com a chave desligada (byte 11 = `00`, rpm 0) o raw decai 51 → 21 em dez quadros (seq 2055–2064) e fica em 21 (300 quadros) | PROVADO | LN |
| Sentido | não provado (sensor pode ser direto ou invertido; ver `TIPO_SENSORE`) | DESCONHECIDO | — |

## 6.2 Parâmetros de configuração (valores desta ECU)

| SC | Nome | Forma | Valor (LN) | Papel no ProgBase | Selo |
|---:|---|---|---|---|---|
| 36 | `TIPO_SENSORE` | U8 | `0x81` = 129 (seq 125) | `ComboSensore` (`TFormConfig` @0x11661): opções na ordem da UI `A.E.B.`, `0 - 90 ohm`, `Landi Renzo`, `Sensata HD`, `Cartesio`, `Non standard`, `Non standard invertito`; a correspondência índice↔valor gravado não foi fechada (`ComboSensoreKeyPress`); `0x81` sugere bit 7 = flag (inversão?) + tipo 1 | PROVADO (valor) / DESCONHECIDO (enum) |
| 37 | `RIF_SENSORE` | U8 × 4 | `39 93 143 219` (seq 126) | quatro referências em raw do sensor, rotuladas `Riserva`, `1/4`, `2/4`, `3/4` (`LabelSensore1..4` @0x11917–0x11A85) | PROVADO |
| 71 / 72 | `LIVELLO_EMUL_ALTO` / `_BASSO` | U8 | `41` / `5` | emulação do indicador de nível do painel original (`TFormConfig`) | PROVADO (valor) / INFERIDO (papel) |
| 276 | `LO_PASS_FILT_CON_FAST` (idx 0) / `_SLOW` (idx 1) | U16 × 2, `raw/32768` | `0x8000` → 1,0 ; `0x1333` → 0,15 (seq 285–286) | coeficientes do filtro passa-baixa do nível (`VectAgaslevKFilter`, precisão 0,0001) | PROVADO (valores; o default DFM `0,015` citado antes estava errado: a ECU tem 0,15) / DESCONHECIDO (equação temporal e critério fast/slow) |
| 300 | `ISTERESI_RIACCENSIONE` (idx 0), `SOGLIA_LED_1..4` (idx 1..4) | U8 × 5 | `3 10 37 62 90` (seq 298–302) | histerese e limiares dos 4 LEDs do comutador (`VectRiaccLed`); defaults DFM `3 12 37 62 87` | PROVADO |
| 313 | `TANK_VOL` / `INJR_GAS_FLOW` | — | `CA 01 10` | **não existe nesta ECU** (Landi Connect) | PROVADO |

## 6.3 Como o ProgBase aprende as referências (`TFormConfig/GroupRifSensore`)

`PROVADO` por desmontagem (VAs de estudo anterior, `lacunas.md` L-12):

1. O "canal" interno `0x0E` lê `TStreamDati+0x2BE4`, uma palavra cujo byte baixo é a referência mínima aprendida e o byte alto a máxima (getter `0x00434CC0`/caso `0x00435792`; setter `0x00436444`/caso `0x00436F07`).
2. `ButtonMinLevelClick` (`0x004C598C`) substitui o byte baixo pelo LEVEL raw atual; `ButtonMaxLevelClick` (`0x004C5ADC`) substitui o byte alto.
3. `ButtonDoLevelClick` "Find levels" (`0x004C5C30`) calcula `step = |max − min| × 0,2` e gera as quatro referências em `min + step×{1,2,3,4}` (ordem invertida quando o flag de sentido do sensor exige), que vão para `RIF_SENSORE`.
4. `CheckRiconoscimentoPieno` (reconhecimento de cheio) existe no DFM com `Visible=False`; predicado não reconstruído (`DESCONHECIDO`).

Conferência com os valores desta ECU: `39 93 143 219` não são equidistantes (54, 50, 76), logo **não** saíram de "Find levels" com min/max; foram editados/importados de outra forma (`INFERIDO`).

## 6.4 Como o original converte raw em nível exibido

- Na tela de visualização, **não converte**: mostra o raw (6.1).
- Nos LEDs do comutador (feitos pela ECU, não pelo PC): `INFERIDO` que a ECU compara o raw filtrado com as referências `RIF_SENSORE` para posicionar o nível em quartos e usa `SOGLIA_LED_1..4` (`10/37/62/90`, parecem percentuais) com histerese 3 para acender/apagar. Com raw 177 entre 143 (2/4) e 219 (3/4) a leitura ficaria entre meio e três quartos; raw 251 > 219 = cheio. Nenhuma fórmula de interpolação da ECU foi vista.
- Nenhuma fórmula de percentual (`raw/255`, `(255 − raw)/255` ou similar) existe no ProgBase: a tela mostra o raw e a ECU faz os LEDs (`PROVADO` por ausência no DFM e na desmontagem conhecida).

## 6.5 O que falta (ver `lacunas.md` L-08)

Enum de `TIPO_SENSORE` (índice da UI → valor; significado do bit 7), equação do filtro (`FAST`/`SLOW`, quando cada um atua), regra exata dos LEDs e do "cheio". Como provar: captura Portmon ao trocar o tipo no `ComboSensore` e ao abastecer (raw vs LEDs físicos), e desmontagem de `ComboSensoreKeyPress`/`CheckRiconoscimentoPienoClick`.
