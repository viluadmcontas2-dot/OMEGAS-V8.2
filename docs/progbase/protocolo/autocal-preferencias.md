# Preferências do AutoCal original

OBS-COD — EV-AUTOCAL-001 a EV-AUTOCAL-006. Fonte: ProgBase SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`; DFM/RTTI em `../inventario/`, pseudocódigo em `../decomp/pseudocodigo/pc_01.jsonl.gz` e `pc_14.jsonl.gz`. Endereços virtuais, x86. Escopo: AutoCalUI, Settings e referências alcançáveis pelo menu; não confundir TabCalibration antigo ou TabKFactor com este painel.

OBS-COD — Emulação: `python3 -B tools/emulacao/autocal_preferencias_original.py --exe /caminho/ProgBase.exe`. Executa os gravadores **RAW** `TAebNumber.SetDataInEcu 0x977014` e `TAebVector.SetDataInEcu 0x9796B8`, até WriteFile. Não executa o clique, validação visual ou conversão física. Objetos mínimos montados com SC, largura e índice do DFM. Leitores `0x976D98/0x978DCC` executam até o último byte da requisição; não há resposta/readback emulado. ACK das escritas `53 00 53` é stub, não OBS-FIO. O binário é somente lido; SHA divergente é recusado; sem binário o script pula, exit 0.

DERIVADO — As tabelas abaixo inventariam os controles e separam ajustes locais, parâmetros da central, estado e ações. **DefaultValue do DFM é padrão do programa, não prova de fábrica da central.** Valor de fábrica real de todos os parâmetros: DESCONHECIDO; próximo passo mínimo: ler uma configuração comprovadamente de fábrica, sem resetar a central do dono.

## Controles da central

OBS-COD — Todas as linhas desta tabela vêm dos DFM `TAUTOCALUI`, `TAUTOCALSETTINGS`, `TAUTOCALDM`; efeito indicado separadamente. `N` = gravador RAW 0x977014; caminho habitual do controle vinculado passa por SetData 0x976DF0. SC em decimal; índices a partir de zero. Faixa “não explícita” não significa irrestrita. Unidade marcada DERIVADO vem da escala/nome, não de calibração física.

| Nome/legenda original | Controle | Vetor SC:índice; caminho | Unidade / faixa explícita / padrão DFM | O que faz para o usuário |
|---|---|---|---|---|
| Num automatch | EditNumAutomatch, Panel2 | VECT_AUTOCAL_U8_2 357:2; N | contagem / 1–99 / 3 | DERIVADO: escolhe a cota de AutoMatch; não é o contador executado. |
| Abilita autocalibrazione__ | CheckAutoCalEnable; BeforeSetData 0x51A474 | AUTO_CAL_ENABLE 330; N | 0/1 / faixa não explícita / ausente | DERIVADO: liga/desliga a autocalibração. Não autoriza o app a gravar sozinho. |
| Max RPM for autocalibration | RPMMax | MAX_RPM_FOR_AUTOCAL 378; N | RPM / 400–8000 / 3000 | DERIVADO do rótulo: limita os giros para autocalibração; comportamento do firmware não comprovado. |
| Disable highest acquisition band | CheckDisableHigestAcqband | DISABLE_ACQ_BAND 395; N | CheckedValue=1 / faixa não explícita / ausente | DERIVADO do rótulo: pede desativação da faixa superior; quais pontos o firmware exclui não comprovado. |
| CursorLimitMAP_MIN | série arrastável ChartData | LIMIT_PRESSURE_MIN 361; 0x519144 → SetDouble 0x976D18 → N | bar (DERIVADO), raw/1024; signed / dinâmica / 0,05 | OBS-COD: desloca e grava o limite inferior de MAP; efeito interno não comprovado. |
| CursorLimitMAP_MAX | série arrastável ChartData | LIMIT_PRESSURE_MAX 362; mesmo caminho | bar (DERIVADO), raw/1024; signed / dinâmica / 0,95 | OBS-COD: desloca e grava o limite superior de MAP; efeito interno não comprovado. |
| EnCdnTThd | EditEnCdnTThd | EN_CDN_T_THD 359:1; N | unidade DESCONHECIDA, raw/1024 / não explícita / 1 | DESCONHECIDO: efeito não comprovado. |
| DIFF_ENG_SPD_THD | EditDiffEngSpdThd | 387; N | RPM (DERIVADO) / DisableLimits=True / 400 | DESCONHECIDO: efeito não comprovado. |
| DELTA_ENG_SPD_THD | ValueEditEx4 | 388; N | RPM (DERIVADO) / DisableLimits=True / 200 | DESCONHECIDO: efeito não comprovado. |
| DIFF_MNFLD_PRESS_THD | ValueEditEx5 | 389; N | bar (DERIVADO), raw/1024 / DisableLimits=True / 0,5 | DESCONHECIDO: efeito não comprovado. |
| DELTA_MNFLD_PRESS_THD | ValueEditEx2 | 390; N | bar (DERIVADO), raw/1024 / não explícita / 0,05 | DESCONHECIDO: efeito não comprovado. |
| DIFF_PETR_TINJ_T_THD | ValueEditEx6 | 391; N | ms (DERIVADO), raw/512 / DisableLimits=True / 4 | DESCONHECIDO: efeito não comprovado. |
| DELTA_PETR_INJ_T_THD | ValueEditEx3 | 392; N | ms (DERIVADO), raw/512 / não explícita / 1 | DESCONHECIDO: efeito não comprovado. |

OBS-COD — MAP: selecionar série `ChartDataClickSeries 0x518C3C`; arrastar `ChartDataMouseMove 0x517F60`; teclado `0x51939C/0x519528`; soltar `0x519134 → 0x519144`. `0x519224` limita pelo eixo e pelo outro cursor. DESCONHECIDO: margem exata entre cursores; próximo passo: executar esse helper com os dois limites e extremos do eixo, pois o pseudocódigo x87 perde operandos. Não há preferência separada “RPM mínimo” encontrada nos controles dessa tela.

OBS-COD — `SettingsImgClick 0x519708` abre o diálogo por `0x513630`. O construtor mostra PanelParametri, CalibrationVal e PanelTempoRefresh somente quando seu argumento de perfil vale **100**; PanelRPM quando >1. DESCONHECIDO: perfil efetivo em cada central/licença; próximo passo: observar `AutoCalUI+0x4B8` na abertura. Não prometer ao usuário que todos esses campos são visíveis na configuração dele.

## Grade avançada CalibrationVal: 12 parâmetros, não 12 índices sequenciais

OBS-COD — `CalibrationValWriteItem 0x5140F0` usa uma tabela montada em `0x513C00..0x513D66`; leitura em `0x513DB0`. Linhas 0/1 chamam SetInt número `0x976CB8`; demais SetInt vetor `0x979E48` → SetData `0x979534` → gravador `0x9796B8`. Unidade: contagens RAW (DERIVADO dos rótulos); faixa UI DESCONHECIDA (próximo passo: abrir editor TAebVectorItems); representação é U8. Efeito de **cada linha**: DESCONHECIDO, “efeito não comprovado”; menor próximo passo: correlacionar uma alteração isolada com os contadores de aquisição nos logs existentes, sem inventar regras do firmware.

| Linha / texto original ItemsStrings | Alvo | Padrão DFM |
|---|---|---|
| 0 AutoCalIdleMinBufPetrThd | VECT_AUTOCAL_U8_0, SC357:0 | 1 |
| 1 AutoCalIdleMinBufUpdPetrThd | VECT_AUTOCAL_U8_1, SC357:1 | 6 |
| 2 AutoCalIdleSumBufPetrThd | CALIBRATION_VAL_1, SC370:0 | 0 |
| 3 AutoCalIdleMinBufGasThd | SC370:4 | 0 |
| 4 AutoCalIdleMinBufUpdGasThd | SC370:5 | 0 |
| 5 AutoCalIdleSumBufGasThd | SC370:6 | 0 |
| 6 AutoCalMinBufPetrThd | SC370:1 | 0 |
| 7 AutoCalMinBufUpdPetrThd | SC370:2 | 0 |
| 8 AutoCalSumBufPetrThd | SC370:3 | 0 |
| 9 AutoCalMinBufGasThd | SC370:7 | 0 |
| 10 AutoCalMinBufUpdGasThd | SC370:8 | 0 |
| 11 AutoCalSumBufGasThd | SC370:9 | 0 |

OBS-COD — DFM ainda contém strings excedentes `11`, `12`, mas VectorSize/RowNo e o laço são **12**: não são parâmetros extras. Os zeros de SC370 são defaults do recurso; `0x510DF8` também inicializa defaults em runtime. DESCONHECIDO: defaults efetivos após essa rotina em cada perfil; próximo passo: executá-la com MODULE_VERSION/perfil conhecido.

## Preferências locais e i18n

OBS-COD — `FormClose 0x513E68` persiste opções locais; não envia SC. Para todas as linhas abaixo, quadros de leitura/gravação na central: **não aplicáveis**. Valores padrão descritos são do host.

| Controle / nome original | i18n original (id: inglês / italiano) | Handler / faixa / padrão | O que faz para o usuário |
|---|---|---|---|
| CheckShowStatusMessages / Show status messages | AUTOCALSETTINGSSHOWSTATUS: Show status messages / Mostra messaggi di stato | FormClose; booleano; fallback true; DFM Visible=False | DERIVADO: mostra mensagens de estado. |
| CheckShowChartZones / Show acquisition zones in chart | AUTOCALSETTINGSSHOWZONES: Show acquisition zones in chart / Mostra zone acquisite | FormClose; booleano; fallback true | DERIVADO: mostra zonas no gráfico. |
| CheckShowChartPoint / Show chart point | AUTOCALSETTINGSSHOWPOINT: Show chart acquisition points / Mostra grafico dei punti acquisiti | FormClose; booleano; fallback true | DERIVADO: mostra gráfico dos pontos adquiridos. |
| CheckShowChartKLine / Show chart k-line | AUTOCALSETTINGSCHARTK: Show chart K-line / Mostra grafico dei K | FormClose; booleano; fallback DESCONHECIDO | DERIVADO: mostra gráfico K. |
| RefreshTime / Update period | tradução específica DESCONHECIDA | RefreshTimeChange 0x5141A4; 200–2000; 300 | DERIVADO: ajusta atualização do host, não o aprendizado interno da central; unidade exata DESCONHECIDA. |
| BtnChangeColor / Setup colors | tradução específica DESCONHECIDA | 0x51420C → TAutocalColorSettings; seleção de cor | OBS-COD: abre personalização das cores. |

OBS-COD — i18n adicional em `i18n.json`: LABELNUMAUTOMATCH = “Num automatch” (eng/ita); AUTOCALSETTINGSRPMMAX = “Max RPM for autocalibration” / “Max giri per autocal”; AUTOCALSETTINGSDISABLEHBAND = “Disable highest acquisition band” / “Disabilita acquisizione fascia alta”. Essas chaves não têm português no inventário. Os nomes técnicos da grade/PanelParametri são os literais do DFM; tradução localizada não comprovada. DESCONHECIDO: demais traduções/fallback efetivo; próximo passo: seguir os GetString do construtor e conferir a chave no i18n versionado.

OBS-COD — Cores editáveis (`_AcqusitionAreas0Click 0x510954`): `_AcqusitionAreas0`, `_AcqusitionAreas1` (ACQ/NACQ), `_CurrentBand`, `_CursorLimitMAP_MAX`, `_CursorLimitMAP_MIN`, `_GasLine`, `_GasPoint`, `_GasPointPrev`, `_PetrolLine`, `_PetrolPoint`, `_RunAxes`, `_KLine`, `_Text`. Unidade cor, faixa do seletor; fábrica não aplicável. Efeito DERIVADO: altera a cor do elemento nomeado. DESCONHECIDO: defaults efetivos/persistência de cada cor; próximo passo: seguir o load/save de TAutocalColorSettings. `AcqusitionAreas` é uma série gráfica, não um vetor editável de zonas; as zonas adquiridas SC367/368 são ReadOnly no DFM.

## Menu, referências e elementos que não são preferências

OBS-COD — `BtnMenuClick 0x51A038` abre MenuAutoCal: Modify map refs, Go to min calibration, Manual automatch, Reset petrol point, Reset gas point, Reset all, FinishAutocal (Action inicialmente Enabled=False). São ações, não números configuráveis. SettingsImg abre opções separadamente. O menu contextual ChartData contém exportação, apagar pontos selecionados, resets, Modify map refs e Customize chart colors (`0x51AE78`); ChartK contém exportar e Reset k-factor. Não converter resets em preferências.

OBS-COD — “Modify map refs” (`0x5187A0 → TFormRifAutocal`, OK `0x51B910`) permite **18 referências MAP** (`EditRifMap0..17`, SC332 MNFLD_PRESS_THD, raw/1024, bar, MaxValue=4) e **18 tempos de injeção** (`EditRifInj0..17`, SC331 PETR_INJ_TBP, raw/512, ms, MaxValue=40). Faixa inferior implícita DESCONHECIDA. Handler exige ordem crescente; grava os elementos e pode ajustar LIMIT_PRESSURE_MIN/MAX aos extremos. Efeito OBS-COD: redefine as referências usadas pelo gráfico/AutoCal; efeito de aprendizado na central não comprovado. Os 18 índices de cada vetor são executados na captura abaixo com entradas RAW sintéticas; não representam uma tabela de referências recomendada.

OBS-COD — Defaults DFM MAP: [0.2,0.245,0.29,0.335,0.38,0.425,0.47,0.515,0.56,0.605,0.65,0.695,0.74,0.785,0.83,0.87,0.91,0.95]; injeção: [1.5,2,2.5,3,3.5,4,4.5,5,5.5,6,7,8,9,10,12,14,16,18]. Não são valores de fábrica comprovados.

OBS-COD — No mesmo diálogo, EditKMin (0,1–1) e EditKMax (1–10) são **limites do eixo K**: `0x51B910` guarda +0x500/+0x508 e `0x5187A0` aplica TChartAxis.SetMinimum/Maximum. Efeito: muda a escala visual. SC/quadro não aplicável; default efetivo vem do eixo ao abrir, não do Text='0' do DFM. Não confundir com gravação da curva MUL_ACT ou travas de escrita do OMEGAS.

OBS-COD — Module rev. / ValueEditEx1 liga MODULE_VERSION SC371 (ReadOnly); contagem executada é NUM_ATUOMATCH_EXECUTED SC372, distinta da cota SC357:2. Séries de pontos, contadores, CurrentBand e RunPoint mostram estado. PanelDbg contém DUMP/DUMP EE, pontos/zonas aleatórios, apagar, Finish automatch, ShowBand; é painel de depuração oculto, não preferências normais.

OBS-FIO — Catálogo das capturas originais, 11-54-23: pergunta “água em 80–90 °C?” antes de iniciar. DESCONHECIDO: não há evidência de **limiar de temperatura editável no AutoCal**; não transformar a pergunta em parâmetro. Próximo passo: seguir o handler da pergunta; os ajustes de troca por temperatura da F1/F6 são outro assunto.

## Quadros capturados, incluindo checksum

OBS-COD — Registro abaixo gerado executando o .exe em CPU emulada, não por fórmula Python. `raw` é entrada deliberada do ensaio; **não é medida da central, recomendação ou conversão de DefaultValue**. `indice=-1` significa número escalar. Leitura = pedido completo, não resposta. Vetores são lidos inteiros por 29; números por 09; elementos por 0A. Último byte = soma dos anteriores módulo 256 (DERIVADO, conferido em todas as capturas). Não há OBS-FIO novo nesta rodada.

```json
{
  "sha256": "8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4",
  "marca": "OBS-COD",
  "resposta_leitura": "não executada",
  "casos": [
    {"nome": "VECT_AUTOCAL_U8_2", "sc": 357, "indice": 2, "raw": 3, "gravacao": "13 65 01 02 03 7E", "leitura": "0A 65 01 02 72"},
    {"nome": "MAX_RPM_FOR_AUTOCAL", "sc": 378, "indice": -1, "raw": 3000, "gravacao": "13 7A 01 B8 0B 51", "leitura": "09 7A 01 84"},
    {"nome": "DISABLE_ACQ_BAND", "sc": 395, "indice": -1, "raw": 1, "gravacao": "12 8B 01 01 9F", "leitura": "09 8B 01 95"},
    {"nome": "AUTO_CAL_ENABLE", "sc": 330, "indice": -1, "raw": 1, "gravacao": "12 4A 01 01 5E", "leitura": "09 4A 01 54"},
    {"nome": "LIMIT_PRESSURE_MIN", "sc": 361, "indice": -1, "raw": 51, "gravacao": "13 69 01 33 00 B0", "leitura": "09 69 01 73"},
    {"nome": "LIMIT_PRESSURE_MAX", "sc": 362, "indice": -1, "raw": 972, "gravacao": "13 6A 01 CC 03 4D", "leitura": "09 6A 01 74"},
    {"nome": "EN_CDN_T_THD", "sc": 359, "indice": 1, "raw": 1024, "gravacao": "14 67 01 01 00 04 81", "leitura": "0A 67 01 01 73"},
    {"nome": "DIFF_ENG_SPD_THD", "sc": 387, "indice": -1, "raw": 400, "gravacao": "13 83 01 90 01 28", "leitura": "09 83 01 8D"},
    {"nome": "DELTA_ENG_SPD_THD", "sc": 388, "indice": -1, "raw": 200, "gravacao": "13 84 01 C8 00 60", "leitura": "09 84 01 8E"},
    {"nome": "DIFF_MNFLD_PRESS_THD", "sc": 389, "indice": -1, "raw": 512, "gravacao": "13 85 01 00 02 9B", "leitura": "09 85 01 8F"},
    {"nome": "DELTA_MNFLD_PRESS_THD", "sc": 390, "indice": -1, "raw": 51, "gravacao": "13 86 01 33 00 CD", "leitura": "09 86 01 90"},
    {"nome": "DIFF_PETR_TINJ_T_THD", "sc": 391, "indice": -1, "raw": 2048, "gravacao": "13 87 01 00 08 A3", "leitura": "09 87 01 91"},
    {"nome": "DELTA_PETR_INJ_T_THD", "sc": 392, "indice": -1, "raw": 512, "gravacao": "13 88 01 00 02 9E", "leitura": "09 88 01 92"},
    {"nome": "VECT_AUTOCAL_U8_0", "sc": 357, "indice": 0, "raw": 1, "gravacao": "13 65 01 00 01 7A", "leitura": "0A 65 01 00 70"},
    {"nome": "VECT_AUTOCAL_U8_1", "sc": 357, "indice": 1, "raw": 6, "gravacao": "13 65 01 01 06 80", "leitura": "0A 65 01 01 71"},
    {"nome": "CALIBRATION_VAL_1", "sc": 370, "indice": 0, "raw": 1, "gravacao": "13 72 01 00 01 87", "leitura": "29 72 01 9C"},
    {"nome": "CALIBRATION_VAL_1", "sc": 370, "indice": 1, "raw": 2, "gravacao": "13 72 01 01 02 89", "leitura": "29 72 01 9C"},
    {"nome": "CALIBRATION_VAL_1", "sc": 370, "indice": 2, "raw": 3, "gravacao": "13 72 01 02 03 8B", "leitura": "29 72 01 9C"},
    {"nome": "CALIBRATION_VAL_1", "sc": 370, "indice": 3, "raw": 4, "gravacao": "13 72 01 03 04 8D", "leitura": "29 72 01 9C"},
    {"nome": "CALIBRATION_VAL_1", "sc": 370, "indice": 4, "raw": 5, "gravacao": "13 72 01 04 05 8F", "leitura": "29 72 01 9C"},
    {"nome": "CALIBRATION_VAL_1", "sc": 370, "indice": 5, "raw": 6, "gravacao": "13 72 01 05 06 91", "leitura": "29 72 01 9C"},
    {"nome": "CALIBRATION_VAL_1", "sc": 370, "indice": 6, "raw": 7, "gravacao": "13 72 01 06 07 93", "leitura": "29 72 01 9C"},
    {"nome": "CALIBRATION_VAL_1", "sc": 370, "indice": 7, "raw": 8, "gravacao": "13 72 01 07 08 95", "leitura": "29 72 01 9C"},
    {"nome": "CALIBRATION_VAL_1", "sc": 370, "indice": 8, "raw": 9, "gravacao": "13 72 01 08 09 97", "leitura": "29 72 01 9C"},
    {"nome": "CALIBRATION_VAL_1", "sc": 370, "indice": 9, "raw": 10, "gravacao": "13 72 01 09 0A 99", "leitura": "29 72 01 9C"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 0, "raw": 512, "gravacao": "14 4C 01 00 00 02 63", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 1, "raw": 512, "gravacao": "14 4C 01 01 00 02 64", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 2, "raw": 512, "gravacao": "14 4C 01 02 00 02 65", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 3, "raw": 512, "gravacao": "14 4C 01 03 00 02 66", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 4, "raw": 512, "gravacao": "14 4C 01 04 00 02 67", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 5, "raw": 512, "gravacao": "14 4C 01 05 00 02 68", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 6, "raw": 512, "gravacao": "14 4C 01 06 00 02 69", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 7, "raw": 512, "gravacao": "14 4C 01 07 00 02 6A", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 8, "raw": 512, "gravacao": "14 4C 01 08 00 02 6B", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 9, "raw": 512, "gravacao": "14 4C 01 09 00 02 6C", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 10, "raw": 512, "gravacao": "14 4C 01 0A 00 02 6D", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 11, "raw": 512, "gravacao": "14 4C 01 0B 00 02 6E", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 12, "raw": 512, "gravacao": "14 4C 01 0C 00 02 6F", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 13, "raw": 512, "gravacao": "14 4C 01 0D 00 02 70", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 14, "raw": 512, "gravacao": "14 4C 01 0E 00 02 71", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 15, "raw": 512, "gravacao": "14 4C 01 0F 00 02 72", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 16, "raw": 512, "gravacao": "14 4C 01 10 00 02 73", "leitura": "29 4C 01 76"},
    {"nome": "MNFLD_PRESS_THD", "sc": 332, "indice": 17, "raw": 512, "gravacao": "14 4C 01 11 00 02 74", "leitura": "29 4C 01 76"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 0, "raw": 1024, "gravacao": "14 4B 01 00 00 04 64", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 1, "raw": 1024, "gravacao": "14 4B 01 01 00 04 65", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 2, "raw": 1024, "gravacao": "14 4B 01 02 00 04 66", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 3, "raw": 1024, "gravacao": "14 4B 01 03 00 04 67", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 4, "raw": 1024, "gravacao": "14 4B 01 04 00 04 68", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 5, "raw": 1024, "gravacao": "14 4B 01 05 00 04 69", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 6, "raw": 1024, "gravacao": "14 4B 01 06 00 04 6A", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 7, "raw": 1024, "gravacao": "14 4B 01 07 00 04 6B", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 8, "raw": 1024, "gravacao": "14 4B 01 08 00 04 6C", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 9, "raw": 1024, "gravacao": "14 4B 01 09 00 04 6D", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 10, "raw": 1024, "gravacao": "14 4B 01 0A 00 04 6E", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 11, "raw": 1024, "gravacao": "14 4B 01 0B 00 04 6F", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 12, "raw": 1024, "gravacao": "14 4B 01 0C 00 04 70", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 13, "raw": 1024, "gravacao": "14 4B 01 0D 00 04 71", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 14, "raw": 1024, "gravacao": "14 4B 01 0E 00 04 72", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 15, "raw": 1024, "gravacao": "14 4B 01 0F 00 04 73", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 16, "raw": 1024, "gravacao": "14 4B 01 10 00 04 74", "leitura": "29 4B 01 75"},
    {"nome": "PETR_INJ_TBP", "sc": 331, "indice": 17, "raw": 1024, "gravacao": "14 4B 01 11 00 04 75", "leitura": "29 4B 01 75"}
  ]
}
```

## Limites da entrega e próximos passos mínimos

DESCONHECIDO — Conversão física e limites aplicados antes dos gravadores RAW não foram exercitados: executar SetDouble com objetos completos e comparar entradas fracionárias/limítrofes. As escalas do DFM não provam arredondamento. Semântica DIFF/DELTA/EN_CDN: primeiro cruzar logs existentes com mudanças isoladas; só depois, se insuficiente, observação autorizada com central. Respostas/ACK reais dos parâmetros: localizar transações dos SC correspondentes nos logs já disponíveis; não usar o stub como prova.

DESCONHECIDO — Fallback da opção de gráfico K e unidade da atualização: seguir construtor 0x513630 e consumidor do valor salvo. Faixa inferior dos editores de referência: ler defaults de TNumericEdit e validação; MaxValue do DFM não prova clamp, pois os editores MAP declaram Validation=False.

OBS-COD — Alterações limitadas ao documento, emulador, unittest e apêndice do índice. Unittest fornecido (contratos versionados independentes do .exe e replay opcional), não executado nesta rodada por pedido de rodar somente o auditor. Graphify query não encontrou graphify-out/graph.json; não foi gerado grafo para respeitar o escopo de arquivos. HEAD remoto não pôde ser resolvido por falha de conexão nas duas tentativas; base local 9df6676606f6db45b14a33ebbe6939409c094098, sem commit. REA/skill reverse-engineer-anything não disponível nesta sessão; não se alega verificação REA.
