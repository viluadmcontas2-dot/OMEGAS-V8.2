# Volta automática à gasolina e pressão baixa (0x4A9484 e 0x4C526C)

Fonte: desmontagem (capstone) do ProgBase.exe sha256 `8a2d297c…36f4` e execução dos setters originais no Unicorn
(`tools/emulacao/volta_gasolina_original.py`; teste `tests/test_progbase_volta_gasolina.py`).
A ECU no emulador é stub (eco + `53 00 53`, INFERIDO do LOG). Formato do quadro já conhecido: `[op][SC lo][SC hi]([índice])[valor LE][soma]`.

## 1. CheckRitornoBenzinaClick 0x4A9484 (caixa "Retorno automático para a gasolina")

O que faz `[OBS-COD]`:
1. Lê `CheckRitornoBenzina.Checked` (form+0xCAC, vmt+0x104).
2. Mostra/esconde `LabelRitornoBenzina` (+0xCB0), `EditRitornoBenzina` (+0xCB4) e `LabelS12` (+0xCB8): visível = marcado E `[CheckRitornoBenzina+0x69]` (provável Visible do próprio check — INFERIDO).
3. Se `[form+0x1280] != 0` não envia nada (guarda; provável "carregando a aba" — INFERIDO).
4. **Desmarcar:** guarda o tempo atual `[form+0x1270] = trunc(GetEsc(28))` e grava **SetEsc(28, 255.0)**.
5. **Marcar:** se o tempo guardado > 0 grava SetEsc(28, guardado); senão SetEsc(28, 0.0). Depois põe no edit `trunc(GetEsc(28))`.

Vetor: código 28 → **TEMPO_RITORNO_BENZINA, SC 62 (0x3E), u8, s** `[OBS-COD]`; tela→ECU `trunc(v)` (setter 0x436CAB).
Conclusão: **255 = volta automática desligada; 0..20 = tempo em s com ela ligada** `[OBS-COD]` (o código grava 255 ao desmarcar). A ECU interpretar 255 como "nunca" é INFERIDO.

Quadros (executados do original, `[OBS-COD]`):

| Ação | Quadro |
|---|---|
| desmarcar (255) | `12 3E 00 FF 4F` |
| marcar sem valor guardado (0) | `12 3E 00 00 50` |
| marcar e restaurar 3 s | `12 3E 00 03 53` |

Resposta esperada: eco + `53 00 53` `[OBS-FIO]` (padrão do LOG para SetNumber).

## 2. EditBassaPressioneMpKeyPress 0x4C526C (campo "Valore di bassa pressione per il ritorno", bar)

Só age com Enter (`key == 0x0D`) `[OBS-COD]`. Desliga o TimerDati (+0x3B0), cursor ampulheta, e **recalcula a tabela de 15 pontos
RIF_PRESS_DIFF / COEFF_PRESS_DIFF** — não grava um escalar `[OBS-COD]`:

- `P0 = RIF_PRESS_DIFF[10] × 0.001` bar (getter 0x43277C, cache SD+0x1D5C, mbar → bar) `[OBS-COD]`.
- `Pmp` = valor digitado (EditBassaPressioneMp +0xCEC, GetValue 0x96BA2C) `[OBS-COD]`.
- `X = (P0 − 0.95) × 1.176470588235294 + 1.1` (consts 0x4C5474/7C/84) `[OBS-COD]`.
- Pressões `A[i]`: `A[0]=Pmp`; `A[1..9] = Pmp + (P0−Pmp)·i/10`; `A[10]=P0`; `A[11..13] = P0 + (X−P0)·(i−10)/4`; `A[14]=X` `[OBS-COD]`.
- Coeficientes `B[i] = (1 − √(A[i]/P0)) × 125` (0x691094 = `fsqrt`; consts 1.0 e 125.0) `[OBS-COD]`.
- Para i=0..14: `0x4327C0(i, A[i])` → RIF_PRESS_DIFF[i] = `trunc(A·1000)` (mbar) e `0x432670(i, B[i])` → COEFF_PRESS_DIFF[i] `[OBS-COD]`.
  - COEFF: limita a ±99.9; se `[SD+0x1DD4]==0` bruto = `trunc(B·100)` (s16); senão `trunc(B·10/3)` limitado a −128..127 `[OBS-COD]`. Qual ECU liga `SD+0x1DD4`: DESCONHECIDO.
- Por fim 0x4A1458 (só relê RIF_PRESS_DIFF, sem envio — INFERIDO por ter só chamadas ao getter), cursor normal, TimerDati ligado.

Vetores: **RIF_PRESS_DIFF SC 123 (0x7B)** u16×15 e **COEFF_PRESS_DIFF SC 124 (0x7C)** s16×15 `[OBS-COD, DFM TStreamDati]`.
Envio: um quadro indexado por elemento (`14 SC 00 i lo hi soma`), 30 quadros ao todo, alternando 7B/7C `[OBS-COD]`.

Exemplo executado (`--pmp 1.25 --p0-bruto 1500`; **P0=1500 é EXEMPLO, não o valor da ECU do dono**):

| i | bar | RIF quadro | COEFF bruto | COEFF quadro |
|---|---|---|---|---|
| 0 | 1.25 | `14 7B 00 00 E2 04 75` | 1089 | `14 7C 00 00 41 04 D5` |
| 5 | 1.375 | `14 7B 00 05 5F 05 F8` | 532 | `14 7C 00 05 14 02 AB` |
| 10 | 1.5 | `14 7B 00 0A DC 05 7A` | 0 | `14 7C 00 0A 00 00 9A` |
| 14 | 1.747 | `14 7B 00 0E D3 06 76` | −990 | `14 7C 00 0E 22 FC BC` |

(Os 15 pontos completos: rodar o script.) Os quadros dependem de P0 = RIF_PRESS_DIFF[10] da ECU: **DESCONHECIDO para a ECU do dono**
(o LOG Portmon não está nesta máquina). Menor próximo passo: extrair do LOG a leitura `29 7B 00 ck` → resposta e rodar
`volta_gasolina_original.py --p0-bruto <RIF[10]>`.

## 3. O "1,25 bar" que o dono vê

Há dois campos de pressão baixa na TabSensore `[OBS-COD]`:
- `EditBassaPressione` (só família LR, `[SD+0x80D]!=0`): código 68 → **PRESS_BASSA_RETROPAS_LR SC 152 (0x98)** u16, `bruto = trunc(bar×1000)` (mbar).
  1,25 bar → **`13 98 00 E2 04 91`** (executado do original, `[OBS-COD]`).
- `EditBassaPressioneMp` (acima): 1,25 bar vira o ponto 0 da tabela RIF_PRESS_DIFF (1250 mbar) + recálculo da tabela inteira.

Qual dos dois o dono vê na tela dele: **DESCONHECIDO** (depende da família da ECU; o print do dono ou o flag `SD+0x80D` decide).
Em ambos a unidade na ECU é mbar (×1000) `[OBS-COD]`.

## 4. O que não se sabe (DESCONHECIDO) e o menor próximo passo

- Valor atual de TEMPO_RITORNO_BENZINA, RIF_PRESS_DIFF e COEFF_PRESS_DIFF na ECU do dono → ler o LOG Portmon (`PORTMON_LOG`).
- Se a ECU do dono é LR (`SD+0x80D`) e se `SD+0x1DD4` está ligado → achar quem escreve esses bytes (xrefs de escrita) no .exe.
- Ordem real no fio e ACK entre os 30 quadros da tabela → só com captura física (classe 5) ou LOG que contenha um Enter nesse campo.
- Significado de `[form+0x1280]` e de `[CheckRitornoBenzina+0x69]` → xrefs de escrita (INFERIDO hoje).

## 5. Resolvido em 2026-10-20 (o app grava como o original)

- **Precisão da FPU** `[OBS-COD]`: o ProgBase roda com `Default8087CW = 0x1332` (`fldcw [0xAAF454]`, precisão estendida).
  O Unicorn começa com FPCW = 0 (24 bits) e o emulador antigo errava 1 mbar em alguns pontos. Agora o gerador fixa 0x1332 e
  EXECUTA também a conta de 0x4C526C (`tabela_original`), não só os setters. Ex. (1,25 bar, P0 1500): o ponto 1 sai **1274**
  (o original trunca 1274,999…), não 1275; e 1,20 bar digitado vira 1199 mbar no ponto 0.
- **Oráculo**: `tools/emulacao/volta_gasolina_grade.py` → `tests/fixtures/volta_gasolina_original_grade.json` (tempos 0..20 e 255;
  70 pares pressão × P0, 30 quadros cada, e os doubles A[i]/B[i]). `VoltaGasolinaCore.kt` reproduz tudo bit a bit
  (aritmética estendida exata em BigInteger) — `VoltaGasolinaCoreTest`.
- **Central do dono** `[OBS-FIO]` (PortmonLOGNOVO, `tests/ui/fixtures/ecu-estado-lognovo.json`): `09 3E 00` → 0 s;
  `29 7B 00` → 1250 1324 1399 1475 1550 1625 1699 1774 1850 1925 **2000** 2083 2167 2251 2335; `29 7C 00` → 2617 … 0 … −1007.
  É exatamente a tabela do original para **1,25 bar com P0 = 2000**. Logo: o "1,25 bar" da tela F4 é **RIF_PRESS_DIFF[0]**
  (campo EditBassaPressioneMp); o coeficiente vai ×100 em 16 bits (SD+0x1DD4 = 0); e o SC 152 desta central é TINJ_FILTRO
  (lido com ACK no LOG), não a família LR.
- **Faixas** `[OBS-COD, DFM]`: espera 0..3 s (EditRitornoBenzina MaxValue 3); pressão 0,2..2,0 bar, passo 0,005.
- **Trava do app**: só grava a pressão se a tabela lida na hora for a que o original montaria a partir do próprio ponto 0 e do
  ponto 10 (`consistentWithOriginal`); senão recusa sem enviar. Cada quadro exige ACK; depois relê os dois vetores e compara.
  Falha no meio = "Desfazer" regrava a tabela de antes.
- **Ainda sem prova** (classe 5, com o dono): ACK da central aos 30 quadros `14 7B|7C` no carro e o efeito (voltar à gasolina
  quando a Press.diff. cai abaixo do ponto 0).
