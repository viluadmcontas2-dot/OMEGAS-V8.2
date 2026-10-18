# Emulação da tela de configuração do ProgBase (aba Iniettori, anti-stallo) — 2026-10-18

Marcas: **OBS-FIO** = visto no fio (Portmon) · **OBS-COD** = provado no código/recurso do ProgBase · **DERIVADO** = conta feita sobre o que foi visto · **INFERIDO** = dedução · **DESCONHECIDO** = não há prova.

## O que foi feito
- Emulador: `tools/emulacao/orig_config_emulator.py` (só Python padrão, mesma entrada → mesmos bytes).
  Uso: `python3 tools/emulacao/orig_config_emulator.py --valores tests/fixtures/config-iniettori-lognovo-v1.json --caixa auto|on|off --capacidade on|off --saida tela.html`
- Testes: `python3 -B -m unittest tests.test_orig_config_emulator` (10 testes, verdes, inclusive o que relê o log Portmon quando ele existe na máquina). Teste de mutação manual: trocar o bit 0x20, ignorar a caixa ou ler sempre ANTI_STALLO[0] faz cada um falhar.
- Imagens (Chromium headless):
  - `docs/evidence/img/emulado-config-iniettori-caixa-off.png` — valores reais do dono: caixa **desmarcada**, edits ocultos.
  - `docs/evidence/img/emulado-config-iniettori-caixa-on.png` — **simulação**: caixa forçada marcada (`--caixa on`), 5 edits visíveis com 1600/100/1500/2/20. Esses números são ao mesmo tempo os de fábrica (TSTREAMDATI, OBS-COD) e os do carro do dono (OBS-FIO).
  - Ambas trazem a legenda `EMULADO — regras: OBS-COD, valores: OBS-FIO` e dizem que a capacidade é simulada.

## Regras usadas
| Regra | Marca |
|---|---|
| Posição e tamanho de cada controle (caixa L38 T1120 715x32; EditAntiStallo0..4 L548, T1159/1198/1237/1277/1317, 72x37; rótulos L63; unidades L630) saem do DFM `TFORMCONFIG.txt` | OBS-COD |
| Fonte Arial 24 negrito; cor 10158080 = 0x9B0000 em BGR = azul-marinho #00009B | OBS-COD |
| Textos: `i18n_all.json` (CHECKANTISTALLO, LABELANTISTALLO0..4), idioma `por` (hoje igual ao inglês), senão `eng`, senão Caption do DFM. Unidades (rpm, rpm/ciclo, ms, rpm/ciclo, rpm/ciclo) não têm chave no i18n e vêm do DFM | OBS-COD |
| Caixa marcada ⇔ bit 0x20 da palavra 0 de FLAG_CONF3 (cód. 250) | OBS-COD (getter 0x42E754 caso 4) + DERIVADO (0x2994 = FLAG_CONF3[0], via 'Flag5') |
| Caixa habilitada (texto #00009B) ou desabilitada (cinza #808080) conforme a capacidade da ECU | OBS-COD |
| Origem da capacidade (`[obj+0x2A24]`) | **DESCONHECIDO** → opção `--capacidade`, padrão `on`. Pista não usada: `docs/evidence/2026-10-18-anti-stallo-caixa-re.md` liga isso a MASK_FUNCTION[14] bit 0 (INFERIDO); a forma de indexar o vetor não está provada |
| Edits, rótulos e unidades só aparecem com a caixa marcada **e** habilitada | OBS-COD |
| EditAntiStalloN mostra ANTI_STALLO[N] | OBS-COD |
| Valores lidos: ANTI_STALLO = [1600, 100, 1500, 2, 20], FLAG_CONF3 = [0, 0], checksum ok nas 3 leituras de cada | OBS-FIO (`tests/fixtures/config-iniettori-lognovo-v1.json`) |
| Fundo do painel cl3DLight = #E3E3E3, borda/fundo do edit, desenho da caixinha | INFERIDO (cores padrão do Windows 10; o DFM só dá o nome da cor) |
| Outros controles da faixa (retropassaggio, sequenza iniezione) têm Visible=False no DFM; aparecem só como contorno tracejado com o nome | DESCONHECIDO (regra de exibição deles não foi lida no código) |

## O que NÃO foi provado
- **Não há print real para comparar.** O `TFORMCONFIG.png` da pasta de prints do Drive não é uma foto da tela do ProgBase: é um desenho do DFM em 800x600; e a cópia local está corrompida. Não foi usado como prova. Por isso o passo "comparar com o print do dono" da receita não foi feito.
- A aparência exata (cores do sistema, borda do TNumericEdit, alinhamento do número, posição da rolagem do ScrollBox) é aproximada.
- De onde vem a capacidade da ECU (ver acima).
- O que o firmware faz com esses números não faz parte desta tela e não foi modelado.
- A captura atual tem a caixa desmarcada; a tela "caixa marcada" é simulação, nunca foi vista no fio nem em print.
