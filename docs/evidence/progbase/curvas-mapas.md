# Tema 5 — Curva K (`MUL_ACT`) e Mapa K (`MAP_K`)

Os dois objetos permanecem separados no ProgBase: a Curva K vive no `TAutoCalDM` (`MUL_ACT` 0x0161, eixo `PETR_INJ_TBP` 0x014B, tela `TAutoCalUI/ChartKLine`); o Mapa K vive no `TStreamDati` (`MAP_K` SC 84, eixos SC 55/61, telas `TFormCentriCelleK` e `TAebGrid`). Fontes: Lognovo 63 MB (`LN seq`), DFM `RCDATA_TFORMCENTRICELLEK_0.dfm.txt` (`1XE8pV9AE17u4OZVIdTuJ42l7l2AMAGFY`), desmontagem citada em `autocal.md`.

## 5.1 Mapa K — endereços, encoding e eixos

| Item | Valor | Selo | Fonte |
|---|---|---|---|
| Objeto | `MAP_K`, SC 84 (`0x0054`), `TAebMatrix`, `DataMask` 255 (U8), exibição identidade (DFM) | PROVADO | `TSTREAMDATI` @0x4ADF; `fontes/brave-darwin-FORMULAS-20261002.md` §1.2 |
| Leitura | `2A 54 00 rr` → `53 0C` + 12 bytes; o ProgBase lê **13 linhas** (`rr` = 00…0C) no dump de cada conexão | PROVADO | LN seq 36–48, 7565–7577, 19906–19918 |
| Escrita | `14 54 00 rr cc vv` (uma célula por frame) → `53 00`; só linhas 00…0B são escritas | PROVADO | LN seq 1054–1197 |
| Eixo de linhas | `TEMPI_PER_K` SC 55, 12×S16, tempo de injeção gasolina em contagens da base de tempo; tela "Righe dei tempi inj (sec)", precisão 0,01, máx. 40 | PROVADO | LN seq 33; DFM `TFormCentriCelleK.NumEditRifMap0..11` |
| Eixo de colunas | `GIRI_PER_K` SC 61, 12×S16 rpm; tela "Colonne dei GIRI (rpm)", 200–8000, precisão 1 | PROVADO | LN seq 34; DFM `EditRifGiri0..11` |
| Edição dos eixos | `14 3D 00 ii lo hi` ×12 (RPM) e `14 37 00 ii lo hi` ×12 (tempos), em bloco, depois `01 12 00 13`, `00 01 01`, fecha e reconecta com dump completo | PROVADO | LN seq 7525–7561, 15226–15249 (valores alterados: `1000/1500/2000` → `850/1350/1850`) |
| Valores dos eixos nesta ECU | tempos `781 977 1172 1367 1758 2344 3125 3906 4687 5469 6250 7031` (= 2,0…18,0 ms); rpm final `850 1350 1850 2500 3000 3500 4000 4500 5000 5500 6000 6500` | PROVADO | LN; coincidem com `config/mp48-k-map-physical-axes.lock.json` do OMEGAS, que porém **não os lê da ECU** |
| 13ª linha (`0C`) | lida sempre, nunca escrita; tem o perfil `+0 +0 +0 +0 +0 +0 +2 +3 +4 +5 +6 +6` sobre uma base própria | PROVADO (observação) / DESCONHECIDO (significado) | LN: `137…143` → `186…192` → `203…209` nas três passagens |

## 5.2 Como o ProgBase escreve o Mapa K (sequência real)

1. `35 03 00 86 2C 51 10` — liga o bit 0x0800 de `FLAG_CONF1` (LN seq 719). Telemetria continua normal depois disso.
2. … (edição na tela; 335 transações de telemetria/diagnóstico no meio) …
3. **144 frames `14 54 00 rr cc 64`** em sequência, linhas 00…0B, colunas 00…0B, **sem telemetria, sem purge extra, sem pausa**: mediana 13,8 ms de IRP por célula, total ≈ 2,0 s (LN seq 1054–1197; `PROVADO`).
4. Volta imediata à telemetria (seq 1198); **nenhum readback** das linhas; **nenhum `01 12 00`** aqui.
5. `35 03 00 86 24 51 10` — desliga o bit (seq 1332), 135 transações depois.
6. Readback só acontece no dump da próxima conexão (seq 7565–7577).

Observação crítica (`PROVADO` como fato, `DESCONHECIDO` como mecanismo): antes do bloco a matriz era `162…181` (linha 0 toda 162); o ProgBase escreveu `100` em todas as 144 células; na leitura seguinte (após reconexão) todas as 144 células valiam **149**, e numa terceira leitura, sem nenhuma outra escrita `14 54 00` no log, **166**. A linha `0C` acompanhou (`137`→`186`→`203`). Ou seja, **o valor lido não é o valor escrito**: a ECU transforma ou reescala o Mapa K (hipóteses: a escrita é relativa; ou a ECU recombina o mapa com outro fator interno; entre as leituras houve Reset All, AutoCal religado e AutoMatch). O que provaria: captura com uma única célula escrita e releitura imediata, repetida com AutoCal desligado.

Matrizes completas (12 colunas por linha; `LN seq 36–48 / 7565–7577 / 19906–19918`):

```
antes do bloco (seq 36-48)         após (seq 7565-77)   terceira (seq 19906-18)
00 162×12                          149×12               166×12
01 162 162 162 162 165×8           149×12               166×12
02 168 168 168 165×9               149×12               166×12
03 167 167 172 165×9               149×12               166×12
04 167 167 172 165 162 165×7       149×12               166×12
05 166 169 172 165×9               149×12               166×12
06 172 181 165×10                  149×12               166×12
07 166 166 176 168 168 168 168 168 165×4   149×12       166×12
08 168×9 165 175 175               149×12               166×12
09 165 165 175×6 165×4             149×12               166×12
0A 165×12                          149×12               166×12
0B 165×12                          149×12               166×12
0C 137 137 137 137 137 137 139 140 141 142 143 143
   186 186 186 186 186 186 188 189 190 191 192 192
   203 203 203 203 203 203 205 206 207 208 209 209
```

## 5.3 Valor neutro do Mapa K

- `INFERIDO` (neutro = **100**): o único valor que o ProgBase escreveu no mapa foi `0x64 = 100` em todas as células (operação de "zerar/uniformizar" o mapa); as tabelas de coeficiente U8 da mesma ECU usam 100 como neutro (`COEFF_TEMP_GAS` = `107…92` em torno de 100; `COEFF_TEMP_RID` começa em 100; `parametros.md` 3.5); o `.lec` do carro após calibração mostra `MappaK` em `124…145`, isto é +24 a +45 % sobre 100 (`fontes/lec-208-pegeot-pos-cali.md`).
- `DESCONHECIDO`: o OMEGAS modela `K1 = raw/128` com 128 neutro (`CalibrationPhysicsFoundation.kt`); **nenhuma fonte original** (DFM, desmontagem, fiação) sustenta 128. `K_MAPPA_NEUTRO` SC 188 = 85 nesta ECU não encaixa em nenhuma das duas hipóteses e sua semântica é desconhecida.
- Como fechar: ler `MAP_K` com um mapa conhecido (ex. logo após o bloco de 100) **na mesma sessão**, e desmontar o consumidor do SC 84 em `TAebGrid.GetInterpolatedValue`/`CalcolaColore` para ver o valor de referência de cor.

## 5.4 Curva K (`MUL_ACT`)

| Item | Valor | Selo | Fonte |
|---|---|---|---|
| Objeto | `MUL_ACT` 0x0161, U16 Q14 (`1,0 = 0x4000`), 30 pontos nesta ECU (DFM declara 18) | PROVADO | `autocal.md` 4.1; `scale-dfm-v1.json` |
| Eixo | `PETR_INJ_TBP` 0x014B, U16 `/512` ms: `0,5 1,0 … 10,0 11 12 13 14 15 16 17 18 20 22` | PROVADO | LN `29 4B 01 75` |
| Leitura | `29 61 01 8B` → 60 bytes, a cada ~2,0 s | PROVADO | LN 27× |
| Escrita por ponto | `14 61 01 ii lo hi` (`SetNumber` indexado, 2 bytes) | PROVADO (desmontagem `TAebVector.SetDouble → SetData → SetDataInEcu`; frames `14 61 01 00 00 40 B6 … 14 61 01 1D 00 40 D3`) / não observado na fiação | `dump-contract-v2.json`, `reset-k-factor-progbase-2026-09-28.md` |
| Reset da curva | 30 escritas de `0x4000` (`ActionResetKFactorExecute` `0x0051A070`) | PROVADO (desmontagem) | idem |
| Edição manual | arrasto ou teclado no `ChartKLine`: passos de 0,05, faixa 0,1–10,0, commit via `SetDouble` do ponto editado | PROVADO (desmontagem `0x005178CC`, `0x00517980`, `0x005179E0`) | `progbase-autocal-ui-handler-map-v1.json` (Verde) |
| Pausas/lotes | não há evidência de lote de 30 escritas com pausa na fiação; o Reset é um laço simples sem `Sleep` | PROVADO (desmontagem) | idem |
| Readback | o ProgBase não relê após escrever; confia na próxima leitura periódica (~2 s) | PROVADO (ausência no LN para `12 4A 01`; padrão geral) | LN seq 12616 |
| Quem altera `MUL_ACT` além do usuário | a ECU, no AutoMatch nativo (três épocas com o PC só lendo) e no Reset All (`→ 1,0`) | PROVADO | `autocal.md` 4.3–4.4 |
| Valores nesta ECU | `0,840 0,839 0,838 0,807 0,787 0,793 0,819 0,867 1,112 1,338 1,326 1,301 1,227 1,163 1,192 1,254 1,251 1,303 1,304 1,260 1,034 0,987 1,027 1,037 1,087 ×6` (Q14 `B1 35 … 91 45`) antes do Reset All; `1,000 ×30` depois | PROVADO | LN seq 342, 1496 |

## 5.5 Backup e restauração do original

| Item | ProgBase | Selo | Fonte |
|---|---|---|---|
| Backup | menu "Salva configurazione attuale" grava um `.lec` (INI cifrado MLTP) com **todos** os parâmetros do `TStreamDati` (`[MappaCoefficientiK] MappaK<r>_<c>`, `[Temperatura]`, flags…) via `TAeb*.WriteToFile` | PROVADO (arquivo real decifrado; método em `fontes/lec-208-pegeot-pos-cali.md`) | `.lec` do carro; `README_RELATORIO.md` §6 (`ReadFromFile/ReadFromHCFFile`) |
| Restauração | "Carica nuova configurazione" lê o `.lec` e envia os objetos para a ECU (`SetDataInEcu`) | INFERIDO (nome do menu + métodos exportados) | nenhuma captura de carga de `.lec` existe; sequência e ordem na fiação `DESCONHECIDO` |
| Curva K no `.lec` | não visto no prefixo decifrado | DESCONHECIDO | decifrar o arquivo inteiro (ver `lacunas.md`) |
| OMEGAS | backups JSON próprios (`k_map_backups/`, `k_factor_backups/`), readback por linha após escrita | — | `KWriteManager.kt`, `KFactorManager.kt` |

## 5.6 Anotações para o contraste

- OMEGAS usa eixos fixos de um arquivo de lock; o original lê `TEMPI_PER_K`/`GIRI_PER_K` da ECU e o usuário pode editá-los (e o dono editou: 1000→850 rpm).
- OMEGAS assume que a célula lida após a escrita é igual à escrita; a fiação mostra 100 → 149 → 166 sem escritas intermediárias.
- OMEGAS assume neutro 128; a evidência original aponta 100 (`INFERIDO`).
- OMEGAS grava `FLAG_CONF1` com valores fixos deste carro (`protocolo.md` 1.6).
- OMEGAS escreve o reset da Curva K em `MUL_ACT` como o original, mas o original não faz readback explícito.
