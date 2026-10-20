# Anti-apagão: os bytes que o original manda (aba F9)

Fonte: ProgBase.exe sha256 `8a2d297c…36f4`, código ORIGINAL executado no Unicorn (`tools/emulacao/antiapagao_original.py`;
teste `tests/test_progbase_antiapagao_original.py`). ECU stub: eco + `53 00 53` nas escritas e, na leitura `29`, a resposta
`53 n dados soma` com os valores de fábrica do LOG novo (`40 06 64 00 DC 05 02 00 14 00`).

## 1. Caixa "Enable un-stalling strategy" (CheckAntiStalloClick 0x4C982C) — EV-ANTIAPAGAO-025

| Ação | Quadro | Marca |
|---|---|---|
| marcar | `35 FA 00 20 00 00 00 4F` | OBS-COD (emulado) = OBS-FIO (EV-ANTIAPAGAO-003) |
| desmarcar | `35 FA 00 00 00 00 00 2F` | OBS-COD (emulado) = OBS-FIO (EV-ANTIAPAGAO-004) |

O mesmo handler também mostra/esconde `LabelDeltaGiri`/`LabDeltaGiri` (o ΔRPM do rodapé) junto com os 5 campos `[OBS-COD]`.

## 2. Número "Max RPM to enable un-stalling" e os 4 extras (EditAntiStalloKeyPress 0x4C97D8) — EV-ANTIAPAGAO-026/027

Só age com Enter. `valor = TNumericEdit.GetValue 0x96B924` (arredonda ±0,5 e trunca para inteiro), depois
`0x979E48(ANTI_STALLO = [TStreamDati+0x4EC], DataIndex do edit = [edit+0x2F0], valor)` → `0x979534` → protocolo `[OBS-COD]`.
No fio: **lê o vetor inteiro e grava UM elemento indexado** (SetNumber indexado `14 SC_lo SC_hi índice lo hi soma`) `[OBS-COD]`:

| Campo (índice) | Valor | Bytes enviados (em ordem) |
|---|---|---|
| 0 Max RPM | 1600 → 1800 | `29 33 01 5D` · `14 33 01 00 08 07 57` |
| 0 Max RPM | 1600 (igual) | `29 33 01 5D` · `14 33 01 00 40 06 8E` (envia mesmo sem mudar) |
| 0 Max RPM | 1601 | `29 33 01 5D` · `14 33 01 00 41 06 8F` |
| 1 Delta para ligar | 150 | `29 33 01 5D` · `14 33 01 01 96 00 DF` |
| 2 Espera p/ sair do gás | 1000 | `29 33 01 5D` · `14 33 01 02 E8 03 35` |
| 3 Histerese | 5 | `29 33 01 5D` · `14 33 01 03 05 00 50` |
| 4 Delta para desligar | 30 | `29 33 01 5D` · `14 33 01 04 1E 00 6A` |

Resposta esperada do `14`: eco + `53 00 53` (mesmo padrão OBS-FIO de `14 7B 00 i …` e `14 54 00 …`). Que a leitura `29` antes da
escrita aconteça sempre na central real (e não só porque o objeto emulado começa "sem cache") é INFERIDO.

## 3. Divergência app × original — EV-ANTIAPAGAO-028 — **CORRIGIDO** (2026-10-20, EV-ANTIAPAGAO-031)

**CORRIGIDO** com autorização explícita do dono (2026-10-20): `AntiStalloParamsCore.Plan.frames` → `AutoCalProtocol.writeIndexedU16`
manda agora, como o original, `29 33 01 5D` (leitura) e UM `14 33 01 <índice> <u16 LE> <soma>` por campo **alterado**, em ordem 0..4,
cada um esperando eco + `53 00 53`; um quadro sem ACK interrompe a sequência (`PARTIAL`: estado legível + Desfazer, que restaura só os
campos já gravados, também por `14`). "Gravado" só depois de `29 33 01` igual ao vetor inteiro. Ex.: 1600 → 1800 = `14 33 01 00 08 07 57`.
Diferença consciente: sem mudança, o app não manda nada (o original manda; §2). A caixa (§1) já batia byte a byte (`35 FA 00 20 00 00 00 4F` /
`35 FA 00 00 00 00 00 2F` com a palavra a zero; o app preserva os outros bits lidos, como o original preserva os do cache).
Provas: `AntiStalloParamsCoreTest`, `AntiStalloParamsWriterTest`, `AntiStalloDialogoEcuEmuladaTest` × `tests/fixtures/anti-apagao-dialogo-ecu.json`
(gerado por `tests/test_dialogo_antiapagao_ecu.py` com a central emulada). Prova física na central: pendente (dono).

Histórico (antes da correção): o app (`AntiStalloParamsCore.Plan.frame` → `AutoCalProtocol.writeVectorU16`) mandava o **vetor inteiro** num SetVector estendido:
`37 33 0C 01 <10 bytes> soma` (ex.: 1800,100,1500,2,20 → `37 33 0C 01 08 07 64 00 DC 05 02 00 14 00 E1`).
O original **nunca** monta esse quadro para o vetor 307: manda `14 33 01 <índice> <u16 LE> <soma>` (7 bytes), um por campo alterado.
Byte a byte: opcode `37`≠`14`; byte 2 `0C` (comprimento) ≠ `01` (SC alto); byte 3 `01` (SC alto) ≠ índice; corpo 10 bytes ≠ 2 bytes.
Recomendação (APLICADA em 2026-10-20, ver acima): trocar a gravação por `14 33 01 i lo hi ck`,
um quadro por campo mudado, mantendo a leitura de volta `29 33 01`.

## 4. "Queda de rotação" (ΔRPM) — EV-ANTIAPAGAO-029/030

- O ΔRPM do rodapé é `IntToStr([form+0x1820])`, e `[form+0x1820] = s16` lido em `TStreamDati+0x293C+0x1E` (0x4A33FC) — **sem escala nem
  conversão** `[OBS-COD]`. O bloco `+0x293C` é a resposta do pedido ao vivo de opcode `0x48` (0x925894, código em `[SD+0x2970]`) `[OBS-COD]`.
- Logo: o número do rodapé está na **mesma unidade crua** que a central usa; os campos 1, 3 e 4 se chamam "rpm/ciclo" (i18n 970).
  Que a ECU compare o mesmo s16 com os campos 1/3/4 é INFERIDO (nome + unidade iguais); "ciclo" = ciclo do motor é INFERIDO.
- Qual código vai no `48 xx` e o período de amostragem da central: DESCONHECIDO. Menor próximo passo: achar o objeto cujo
  `[obj+0x64]` é copiado em 0x42AF84 e casar com um `48 xx` do LOG novo (payload ≥ 0x20 bytes; ΔRPM = bytes 0x1E–0x1F).
- Efeito dos 4 extras dentro da central: DESCONHECIDO (é firmware; o .exe só grava os números).

## 5. Mapa K (0x00439E94, linhas de ms)

Não avançado nesta rodada (DESCONHECIDO). Menor próximo passo: xrefs de escrita aos vetores de ms do Mapa K a partir de 0x00439E94
no mesmo emulador (`antiapagao_original.py` já tem stub de `operator new/delete`, que essa função também deve exigir).
