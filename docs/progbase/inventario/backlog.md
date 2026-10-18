# Backlog: o que ainda NÃO foi decompilado / analisado do ProgBase.exe

Gerado por `tools/progbase/inventario_backlog.py` (SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`). Este arquivo é a lista de trabalho; os números vêm da extração e mudam se alguém nomear funções (`tools/progbase/dados/nomes_manuais.json`), curar handlers (`descricoes_handlers.json`) ou atualizar a cobertura (`dados/cobertura_externa.json`).

## 1. Resumo

| Item | Quantidade | Marca |
|---|---:|---|
| Funções alcançadas nas unidades do programa | 4404 | OBS-COD |
| …com nome (RTTI/exportação/vtable/manual) | 1981 | OBS-COD (nome) |
| …sem nome, mas resumidas por algum handler de tela (cadeia ≤ 3) | 665 | OBS-COD (fatos); sentido INFERIDO |
| …sem nome, citadas em documento já existente | 12 | OBS-COD |
| …**sem nome e sem nenhuma análise** | **1746** | DESCONHECIDO |
| Handlers de tela inventariados | 552 (curados: 161; automáticos: 391, dos quais 258 em TFormConfig) | — |
| Eventos do DFM sem método publicado correspondente | 0 | OBS-COD |
| Chaves de texto (i18n) sem ligação a componente nem a chamada | 133 de 1160 | DESCONHECIDO |
| Trechos nunca alcançados nas faixas do programa | 756 (122760 bytes), dos quais "desconhecido": 691 | INFERIDO |

## 2. Prioridade A: funções sem nome que handlers de tela já alcançam (maiores primeiro)

São o caminho mais curto para entender uma tela: o handler chama e a função não tem nome. Top 150 por tamanho (lista completa em `backlog_funcoes.md`).

| Endereço | Tam. | Unidade | Handler(s) que a alcançam |
|---|---:|---|---|
| 0x00439E94 | 288663 | Iodata | `TFormConfig.ComboCarburanteKeyPress`, `TFormConfig.ComboTipoIniettoreKeyPress`, `TFormConfig.ComboTurboKeyPress` |
| 0x004DF6FC | 22080 | Graficodrt | `TFormVisualizza.MenuGraficoClick` |
| 0x00414B8C | 15681 | Commonmodules | `TFormMain.ActionAggiornaExecute`, `TFormMain.ActionApriConfExecute`, `TFormMain.ActionArchivioExecute` |
| 0x004D1C88 | 11926 | Diagnosi | `TFormDiagnosi.ButtonAzzeraErroriClick`, `TFormDiagnosi.ComboAzioneChange`, `TFormDiagnosi.ListDiagnosiChange` |
| 0x004F9C40 | 7167 | Main | `TFormMain.ActionConnettiExecute`, `TFormMain.ActionResetDaPcExecute` |
| 0x0042393C | 6921 | Graficoscope | `TFormGraficoScope.btnLoadClick` |
| 0x004FBE8C | 6490 | Main | `TFormMain.ActionProgrammaExecute`, `TFormMain.ActionUpdateExecute` |
| 0x00501E5C | 5744 | Main | `TFormMain.ActionProgrammaExecute`, `TFormMain.ActionUpdateExecute` |
| 0x004D83CC | 5495 | File | `TFormMain.ActionApriConfExecute`, `TFormMain.ActionArchivioExecute`, `TFormMain.ActionSalvaConfExecute` |
| 0x00487B4C | 5351 | Config | `TFormMain.ActionProgrammaExecute`, `TFormMain.ActionUpdateExecute` |
| 0x0051DB24 | 4247 | Ffdatamodulesinglearray | `TFormDiagnosi.FormActivate` |
| 0x0043834C | 4108 | Iodata | `TFormConfig.ComboCarburanteKeyPress`, `TFormConfig.ComboTipoIniettoreKeyPress`, `TFormConfig.ComboTurboKeyPress` |
| 0x004272D0 | 3882 | Graficoscope | `TFormMain.Plot1Click` |
| 0x004229DC | 3359 | Graficoscope | `TFormGraficoScope.btnRunClick` |
| 0x0042D300 | 3089 | Iodata | `TFormConfig.ButtonCommutatoreClick`, `TFormConfig.ButtonNuoviParametriClick`, `TFormConfig.ButtonTaraturaClick` |
| 0x004F6CC8 | 2878 | Main | `TFormMain.ActionApriExecute` |
| 0x0041F068 | 2706 | Commonmodules | `TAutoCalUI.BtnDumpClick`, `TAutoCalUI.BtnDumpEEClick` |
| 0x0042084C | 2705 | Graficoscope | `TFormMain.Plot1Click` |
| 0x004FDBEC | 2602 | Main | `TFormMain.ActionConnettiExecute`, `TFormMain.ActionResetDaPcExecute` |
| 0x00439358 | 2592 | Iodata | `TFormConfig.ComboCarburanteKeyPress`, `TFormConfig.ComboTagliaKeyPress`, `TFormConfig.ComboTipoIniettoreKeyPress` |
| 0x0048A228 | 2561 | Config | `TFormFile.ButtonOkClick`, `TFormMain.ActionApriExecute`, `TFormMain.ActionProgrammaExecute` |
| 0x004220B0 | 2348 | Graficoscope | `TFormGraficoScope.btnRunClick` |
| 0x004142B0 | 2267 | Commonmodules | `TFormMain.ActionAggiornaExecute`, `TFormMain.ActionApriConfExecute`, `TFormMain.ActionArchivioExecute` |
| 0x0043734C | 2164 | Iodata | `TFormConfig.ButtonCommutatoreClick`, `TFormConfig.ButtonNuoviParametriClick`, `TFormConfig.ButtonTaraturaClick` |
| 0x0051C65C | 2076 | Ecufunctionalities | `TFormMain.ActionProgrammaExecute`, `TFormMain.ActionUpdateExecute` |
| 0x004A9DC4 | 2036 | Config | `TFormConfig.CheckAlimentazioneClick`, `TFormConfig.CheckElettrovalvoleClick`, `TFormConfig.CheckTaglioPompaBenzinaClick` |
| 0x004F8094 | 1948 | Main | `TFormMain.ActionProgrammaExecute`, `TFormMain.ActionUpdateExecute` |
| 0x004BAE94 | 1921 | Config | `TFormConfig.ComboTipoIniettoreKeyPress` |
| 0x00505AA8 | 1907 | Scaricafile | `TFormScarica.ButtonCambiaClick` |
| 0x00435C6C | 1903 | Iodata | `TFormConfig.ButtonCommutatoreClick`, `TFormConfig.ButtonNuoviParametriClick`, `TFormConfig.ButtonRicercaParametriClick` |
| 0x00513630 | 1878 | Autocal_settings | `TAutoCalUI.SettingsImgClick` |
| 0x005078D8 | 1805 | Service | `TFormService.ButtonResetPinClick`, `TFormService.EditInsPinChange` |
| 0x00522E20 | 1772 | Strategiapanda_ui | `TStrategiaPandaUI.chk_SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMAClick`, `TStrategiaPandaUI.chk_SP_EN_STRATEGIA_MINIMOClick`, `TStrategiaPandaUI.chk_SP_EN_STRATEGIA_RIC_CLIMAClick` |
| 0x005162F8 | 1737 | Autocal_ui | `TAutoCalUI.ActionAutoCalRifExecute`, `TAutoCalUI.ActionAutoMatchExecute`, `TAutoCalUI.ActionDeleteSelectedPointsExecute` |
| 0x004ECD9C | 1656 | Landiconnect | `TFormLandiConnect.DrawGridInjrTOfsTblDblClick`, `TFormLandiConnect.DrawGridInjrTOfsTblKeyPress` |
| 0x00412430 | 1641 | Calibraswitch | `TFormConfig.ButtonSwitchSettingsClick` |
| 0x004EC250 | 1549 | Landiconnect | `TFormLandiConnect.TimerUpdateTimer` |
| 0x0042E16C | 1512 | Iodata | `TFormConfig.CheckAzzeraBlackListObdClick`, `TFormConfig.CheckAzzeraErroriObdClick`, `TFormConfig.CheckContributiClick` |
| 0x004F95FC | 1501 | Main | `TFormMain.ActionProgrammaExecute`, `TFormMain.ActionUpdateExecute` |
| 0x00480D60 | 1494 | Calibra | `TFormMain.MenuAboutClick` |
| 0x009167F4 | 1483 | Aebbeelight | `TAebBeeFormLight.ButtonProgramDongleClick`, `TAebBeeFormLight.ButtonProgramMcuClick`, `TAebBeeFormLight.ComboCanaleChange` |
| 0x00436444 | 1379 | Iodata | `TFormConfig.ButtonCommutatoreClick`, `TFormConfig.ButtonDoLevelClick`, `TFormConfig.ButtonMaxLevelClick` |
| 0x004C4348 | 1317 | Config | `TFormConfig.DrawDelayGasTempDblClick`, `TFormConfig.DrawDelayGasTempKeyPress` |
| 0x00428218 | 1252 | Graficoscope | `TFormGraficoScope.ChartDrtClickAxis`, `TFormGraficoScope.ChartDrtMouseMove` |
| 0x004B3A88 | 1229 | Config | `TFormConfig.DrawGridPassaggioGasDblClick`, `TFormConfig.DrawGridPassaggioGasKeyPress` |
| 0x004B318C | 1218 | Config | `TFormConfig.DrawGridPassaggioKeyPress` |
| 0x004C15F0 | 1196 | Config | `TFormConfig.DrawGridBenzinaDblClick`, `TFormConfig.DrawGridBenzinaKeyPress` |
| 0x00937A94 | 1194 | Aebcrypt | `TFormGraficoScope.btnRunClick` |
| 0x0051C1B8 | 1185 | Ecufunctionalities | `TFormMain.ActionAzzeraIdExecute`, `TFormMain.ActionSalvaExecute` |
| 0x00937604 | 1168 | Aebcrypt | `TFormGraficoScope.btnRunClick` |
| 0x00917554 | 1157 | Aebbeelight | `TAebBeeFormLight.ButtonChangePinClick`, `TAebBeeFormLight.ButtonConnectMcuClick`, `TAebBeeFormLight.ButtonDisconnectMcuClick` |
| 0x0050F818 | 1143 | Scopefileutility | `TFormGraficoScope.TimerTimer` |
| 0x004C34D4 | 1118 | Config | `TFormConfig.DrawGridEmissioniMultipointDblClick`, `TFormConfig.DrawGridEmissioniMultipointKeyPress` |
| 0x004E7174 | 1108 | Graficodrt | `TFormVisualizza.MenuGraficoClick` |
| 0x004CA9F8 | 1082 | Config | `TFormConfig.DrawGridProgressioniDblClick`, `TFormConfig.DrawGridProgressioniKeyPress` |
| 0x00433B98 | 1074 | Iodata | `TFormFile.ButtonOkClick`, `TFormMain.ActionSalvaExecute` |
| 0x004B4594 | 1050 | Config | `TFormConfig.DrawGridEmissioniDblClick`, `TFormConfig.DrawGridEmissioniKeyPress` |
| 0x004D7E78 | 1045 | Diagnosi | `TFormDiagnosi.FormActivate` |
| 0x00430A0C | 1008 | Iodata | `TFormConfig.ButtonCommutatoreClick`, `TFormConfig.ButtonNuoviParametriClick`, `TFormConfig.ButtonTaraturaClick` |
| 0x0050F298 | 995 | Scopefileutility | `TFormGraficoScope.btnRunClick` |
| 0x004BD358 | 991 | Config | `TFormConfig.DrawGridAnticipoDblClick`, `TFormConfig.DrawGridAnticipoKeyPress` |
| 0x004FD80C | 966 | Main | `TFormMain.ActionProgrammaExecute` |
| 0x004A90B8 | 957 | Config | `TFormConfig.ComboCambioKeyPress` |
| 0x0042E8D8 | 956 | Iodata | `TFormConfig.CheckAntiStalloClick`, `TFormConfig.CheckAttivaPompaInRiservaClick`, `TFormConfig.CheckBoxEnDiagDtcPowertrainClick` |
| 0x004B82A4 | 951 | Config | `TFormConfig.DrawGridTempDblClick`, `TFormConfig.DrawGridTempKeyPress` |
| 0x00426C18 | 940 | Graficoscope | `TFormGraficoScope.TimerTimer`, `TFormGraficoScope.btnLoadClick` |
| 0x004FBA40 | 933 | Main | `TFormFile.ButtonOkClick`, `TFormMain.ActionApriExecute`, `TFormMain.ActionConnettiExecute` |
| 0x005019A4 | 915 | Main | `TFormMain.ActionProgrammaExecute`, `TFormMain.ActionUpdateExecute` |
| 0x004E5EA0 | 914 | Graficodrt | `TFormVisualizza.MenuGraficoClick` |
| 0x004B19DC | 865 | Config | `TFormConfig.DrawGridKDblClick`, `TFormConfig.DrawGridKKeyPress` |
| 0x004B201C | 865 | Config | `TFormConfig.DrawGridKDblClick`, `TFormConfig.DrawGridKKeyPress` |
| 0x009194A8 | 847 | Aebbeelight | `TAebBeeFormLight.ButtonScanClick`, `TAebBeeFormLight.TabScansioneShow` |
| 0x004AF018 | 840 | Config | `TFormConfig.CheckMapConfiguratoClick`, `TFormConfig.ComboPressioneKeyPress`, `TFormConfig.TabPressioneShow` |
| 0x0042CFB8 | 839 | Iodata | `TFormConfig.ButtonCommutatoreClick`, `TFormConfig.ButtonTaraturaClick`, `TFormConfig.CheckAdattaBenzinaClick` |
| 0x004F8834 | 831 | Main | `TFormMain.ActionConnettiExecute`, `TFormMain.ActionProgrammaExecute`, `TFormMain.ActionResetDaPcExecute` |
| 0x00515D00 | 830 | Autocal_ui | `TAutoCalUI.ActionAutoCalRifExecute` |
| 0x00434CC0 | 829 | Iodata | `TFormConfig.ButtonCommutatoreClick`, `TFormConfig.ButtonDoLevelClick`, `TFormConfig.ButtonMaxLevelClick` |
| 0x004B4E10 | 786 | Config | `TFormConfig.DrawGridTempRidDblClick`, `TFormConfig.DrawGridTempRidKeyPress` |
| 0x004B555C | 786 | Config | `TFormConfig.DrawGridTempMotDblClick`, `TFormConfig.DrawGridTempMotKeyPress` |
| 0x004B2A44 | 784 | Config | `TFormConfig.DrawGridOpenLoopDblClick`, `TFormConfig.DrawGridOpenLoopKeyPress` |
| 0x0051B604 | 777 | Centricelleauto | `TAutoCalUI.ActionAutoCalRifExecute` |
| 0x004C27FC | 774 | Config | `TFormConfig.DrawGridSplitFuelDblClick`, `TFormConfig.DrawGridSplitFuelKeyPress` |
| 0x00912948 | 773 | Aebbee | `TAebBeeFormLight.ButtonChangePinClick`, `TAebBeeFormLight.ButtonRemovePinClick` |
| 0x004EA9F8 | 770 | Httpthread | `TAboutBox.ButtonRequestClick`, `TFormMain.TimerUpdateTimer` |
| 0x004BE60C | 750 | Config | `TFormConfig.DrawGridTempiLrDblClick`, `TFormConfig.DrawGridTempiLrKeyPress` |
| 0x0091C878 | 711 | Aebbeeprog | `TAebBeeFormLight.ButtonProgramDongleClick`, `TAebBeeFormLight.ButtonProgramMcuClick` |
| 0x004B5BCC | 691 | Config | `TFormConfig.DrawGridPressDiffDblClick`, `TFormConfig.DrawGridPressDiffKeyPress` |
| 0x004B61DC | 691 | Config | `TFormConfig.DrawGridPressAssDblClick`, `TFormConfig.DrawGridPressAssKeyPress` |
| 0x004B1D60 | 683 | Config | `TFormConfig.DrawGridKDblClick`, `TFormConfig.DrawGridKKeyPress` |
| 0x0091CB90 | 669 | Aebbeeprog | `TAebBeeFormLight.ButtonProgramDongleClick`, `TAebBeeFormLight.ButtonProgramMcuClick` |
| 0x004E5260 | 662 | Graficodrt | `TFormVisualizza.MenuGraficoClick` |
| 0x00430DFC | 654 | Iodata | `TFormConfig.ButtonCommutatoreClick`, `TFormConfig.ButtonNuoviParametriClick`, `TFormConfig.ButtonTaraturaClick` |
| 0x0042C708 | 647 | Iodata | `TFormConfig.ComboCarburanteKeyPress`, `TFormConfig.ComboTipoIniettoreKeyPress`, `TFormConfig.ComboTurboKeyPress` |
| 0x0050D2A8 | 634 | Visualizza | `TFormVisualizza.Timer1Timer` |
| 0x00913524 | 621 | Aebbee | `TAebBeeFormLight.ButtonChangePinClick`, `TAebBeeFormLight.ButtonConnectMcuClick`, `TAebBeeFormLight.ButtonDisconnectMcuClick` |
| 0x00486F8C | 608 | Calibra | `TFormCalibra.TimerBlinkTimer` |
| 0x0050D5C8 | 608 | Visualizza | `TFormVisualizza.Timer1Timer` |
| 0x0042DF14 | 599 | Iodata | `TFormConfig.CheckContributiClick`, `TFormConfig.CheckRiscaldamentoClick`, `TFormConfig.CheckSegnaleGiriClick` |
| 0x004312A8 | 591 | Iodata | `TFormConfig.EditAperturaInjKeyPress`, `TFormConfig.EditTempoMortoBenzKeyPress` |
| 0x004CA2EC | 583 | Config | `TFormConfig.DrawGridContributiDblClick`, `TFormConfig.DrawGridContributiKeyPress` |
| 0x004B6F68 | 575 | Config | `TFormConfig.DrawGridPressPassDblClick`, `TFormConfig.DrawGridPressPassKeyPress` |
| 0x0042C4B4 | 564 | Iodata | `TFormConfig.ComboCarburanteKeyPress`, `TFormConfig.ComboTipoIniettoreKeyPress`, `TFormConfig.ComboTurboKeyPress` |
| 0x004A96A4 | 563 | Config | `TFormConfig.ComboNumeroSondeKeyPress`, `TFormConfig.ComboSonda1KeyPress`, `TFormConfig.FormShow` |
| 0x004B68DC | 557 | Config | `TFormConfig.DrawGridTInjDefaultDblClick`, `TFormConfig.DrawGridTInjDefaultKeyPress` |
| 0x0091CE30 | 556 | Aebbeeprog | `TAebBeeFormLight.ButtonProgramDongleClick`, `TAebBeeFormLight.ButtonProgramMcuClick` |
| 0x0042BD34 | 549 | Iodata | `TFormConfig.ComboCarburanteKeyPress`, `TFormConfig.ComboTipoIniettoreKeyPress`, `TFormConfig.ComboTurboKeyPress` |
| 0x0051296C | 543 | Autocal_dm | `TAutoCalSettings.FormClose`, `TAutoCalUI.ActionChangeColorExecute`, `TAutoCalUI.SettingsImgClick` |
| 0x0091C038 | 524 | Aebbeeprog | `TAebBeeFormLight.ButtonProgramDongleClick`, `TAebBeeFormLight.ButtonProgramMcuClick` |
| 0x00420448 | 521 | Graficoscope | `TFormMain.Plot1Click` |
| 0x00517254 | 518 | Autocal_ui | `TAutoCalUI.ActionAutoCalRifExecute`, `TAutoCalUI.ActionDeleteSelectedPointsExecute`, `TAutoCalUI.ActionExportToKExecute` |
| 0x004B7660 | 514 | Config | `TFormConfig.DrawGridTestDblClick`, `TFormConfig.DrawGridTestKeyPress` |
| 0x00503DAC | 513 | Main | `TFormMain.ActionConnettiExecute`, `TFormMain.ActionDisconnettiExecute`, `TFormMain.ActionProgrammaExecute` |
| 0x004E5B2C | 508 | Graficodrt | `TFormVisualizza.MenuGraficoClick` |
| 0x004FFC20 | 503 | Main | `TFormMain.ActionApriExecute`, `TFormMain.ActionConnettiExecute`, `TFormMain.ActionResetDaPcExecute` |
| 0x00911658 | 495 | Aebbee | `TAebBeeFormLight.ButtonProgramDongleClick`, `TAebBeeFormLight.ButtonProgramMcuClick`, `TAebBeeFormLight.ComboCanaleChange` |
| 0x00512418 | 480 | Autocal_dm | `TFormMain.ActionProgrammaExecute`, `TFormMain.ActionUpdateExecute` |
| 0x00913344 | 477 | Aebbee | `TAebBeeFormLight.ButtonScanClick` |
| 0x00516D8C | 471 | Autocal_ui | `TAutoCalUI.ActionAutoCalRifExecute`, `TAutoCalUI.ActionAutoMatchExecute`, `TAutoCalUI.ActionDeleteSelectedPointsExecute` |
| 0x00911288 | 471 | Aebbee | `TAebBeeFormLight.ButtonChangePinClick`, `TAebBeeFormLight.ButtonConnectMcuClick`, `TAebBeeFormLight.ButtonDisconnectMcuClick` |
| 0x0042CDE0 | 469 | Iodata | `TFormFile.ButtonOkClick`, `TFormMain.ActionApriExecute`, `TFormMain.ActionAzzeraIdExecute` |
| 0x0042C1E8 | 453 | Iodata | `TFormConfig.ComboCarburanteKeyPress`, `TFormConfig.ComboTipoIniettoreKeyPress`, `TFormConfig.ComboTurboKeyPress` |
| 0x0041E5D0 | 450 | Commonmodules | `TAutoCalUI.BtnDumpClick`, `TAutoCalUI.BtnDumpEEClick` |
| 0x00981F2C | 447 | Unitumtransform | `TAutoCalUI.ActionAutoCalRifExecute`, `TFormCentriCelle.ButtonOkClick`, `TFormCentriCelleK.ButtonOkClick` |
| 0x0051BD3C | 437 | Centricelleauto | `TAutoCalUI.ActionAutoCalRifExecute` |
| 0x004CCB58 | 430 | Corrcalibra | `TFormCorrCalibra.TimerBlinkTimer` |
| 0x0042B964 | 428 | Iodata | `TFormFile.ButtonOkClick`, `TFormMain.ActionApriExecute`, `TFormMain.ActionAzzeraIdExecute` |
| 0x00505258 | 427 | Scaricafile | `TFormMain.ActionProgrammaExecute` |
| 0x004BC58C | 415 | Config | `TFormConfig.ButtonSequenzaInjClick`, `TFormConfig.DrawGridSequenzaInjDrawCell` |
| 0x00414110 | 413 | Commonmodules | `TFormMain.ActionAggiornaExecute`, `TFormMain.ActionApriConfExecute`, `TFormMain.ActionArchivioExecute` |
| 0x0050F67C | 412 | Scopefileutility | `TFormGraficoScope.btnRunClick` |
| 0x00429358 | 411 | Graficoscope | `TFormGraficoScope.btnRunClick` |
| 0x004304F0 | 411 | Iodata | `TFormConfig.ComboSensoreKeyPress` |
| 0x0042CAB0 | 408 | Iodata | `TFormFile.ButtonOkClick`, `TFormMain.ActionApriExecute`, `TFormMain.ActionAzzeraIdExecute` |
| 0x0042CC48 | 408 | Iodata | `TFormFile.ButtonOkClick`, `TFormMain.ActionApriExecute`, `TFormMain.ActionAzzeraIdExecute` |
| 0x004C3C9C | 405 | Config | `TFormConfig.DrawGridAnticipoMultipointDblClick`, `TFormConfig.DrawGridAnticipoMultipointKeyPress` |
| 0x0091C43C | 404 | Aebbeeprog | `TAebBeeFormLight.ButtonProgramDongleClick`, `TAebBeeFormLight.ButtonProgramMcuClick` |
| 0x00430878 | 403 | Iodata | `TFormConfig.ComboTempRiduttoreKeyPress`, `TFormConfig.ComboTipoMisuratoreKeyPress` |
| 0x004828E4 | 390 | Calibra | `TFormMain.ActionProgrammaExecute`, `TFormMain.ActionUpdateExecute` |
| 0x00516F64 | 390 | Autocal_ui | `TAutoCalUI.ActionAutoCalRifExecute`, `TAutoCalUI.ActionAutoMatchExecute`, `TAutoCalUI.ActionDeleteSelectedPointsExecute` |
| 0x0042E754 | 385 | Iodata | `TFormConfig.CheckAlimentazioneClick`, `TFormConfig.CheckAntiStalloClick`, `TFormConfig.CheckCambioConSovrappClick` |
| 0x0091C6FC | 379 | Aebbeeprog | `TAebBeeFormLight.ButtonProgramDongleClick`, `TAebBeeFormLight.ButtonProgramMcuClick` |
| 0x00518610 | 377 | Autocal_ui | `TAutoCalUI.ActionExportChartDataExecute`, `TAutoCalUI.ActionExportChartPointExecute`, `TAutoCalUI.ActionExportKChartExecute` |
| 0x009126A4 | 374 | Aebbee | `TAebBeeFormLight.ButtonChangePinClick` |
| 0x0051ED04 | 373 | Ffdatamodulesinglearray | `TFormDiagnosi.TimerTimer` |
| 0x005104C0 | 365 | Autocal_colorsettings | `TAutocalColorSettings.Image1Click` |
| 0x00501838 | 364 | Main | `TFormMain.ActionAggiornaExecute`, `TFormMain.ActionApriConfExecute`, `TFormMain.ActionArchivioExecute` |
| 0x00916534 | 364 | Aebbeelight | `TAebBeeFormLight.ButtonProgramDongleClick`, `TAebBeeFormLight.ButtonProgramMcuClick`, `TAebBeeFormLight.ComboCanaleChange` |
| 0x00517568 | 359 | Autocal_ui | `TAutoCalUI.ActionAutoMatchExecute`, `TAutoCalUI.ActionResetAllExecute`, `TAutoCalUI.ActionResetGasExecute` |
| 0x0042A52C | 358 | Iodata | `TFormCalibra.TimerBlinkTimer`, `TFormConfig.ButtonCommutatoreClick`, `TFormConfig.ButtonDoLevelClick` |
| 0x0042A788 | 358 | Iodata | `TFormCalibra.TimerBlinkTimer`, `TFormConfig.ButtonCommutatoreClick`, `TFormConfig.ButtonDoLevelClick` |

## 3. Prioridade B: maiores funções sem nome e sem análise (top 150)

| Endereço | Tam. | Unidade | Origem |
|---|---:|---|---|
| 0x00499361 | 20935 | Config | varredura |
| 0x00492A5A | 13384 | Config | varredura |
| 0x0041A680 | 10716 | Commonmodules | call |
| 0x00496A83 | 10462 | Config | varredura |
| 0x004F4A41 | 8838 | Main | varredura |
| 0x004A42CC | 7739 | Config | varredura |
| 0x0048E46A | 7073 | Config | varredura |
| 0x0048CA5A | 6672 | Config | varredura |
| 0x004DC160 | 6527 | File | call |
| 0x005248B0 | 6201 | Strategiatempimorti_ui | call |
| 0x0040C4B8 | 6102 | Aggkey | call |
| 0x004AF68B | 6081 | Config | varredura |
| 0x00909A88 | 5979 | Aebcontrolsregister | call |
| 0x00482F34 | 5939 | Calibra | call |
| 0x0093B5D2 | 5329 | Aebgrid | call |
| 0x00405DCC | 5162 | Cryptengine | call |
| 0x0048AF0A | 5120 | Config | varredura |
| 0x004A7603 | 4860 | Config | varredura |
| 0x00913C2C | 4441 | Aebbeelight | call |
| 0x00409C70 | 4093 | Aggkey | call |
| 0x0090EFAC | 3963 | Aebcontrolsregister | varredura |
| 0x00421410 | 3230 | Graficoscope | call |
| 0x00418950 | 3142 | Commonmodules | call |
| 0x00495EA2 | 3041 | Config | varredura |
| 0x0051F2A0 | 2874 | Ffdatamodulesinglearray | call |
| 0x0090132C | 2827 | Aebcontrolsregister | call |
| 0x004A2558 | 2812 | Config | call |
| 0x00434FFD | 2634 | Iodata | varredura |
| 0x004AC387 | 2601 | Config | varredura |
| 0x0041D060 | 2558 | Commonmodules | call |
| 0x00408574 | 2236 | Licensemanager | call |
| 0x004A3845 | 2197 | Config | varredura |
| 0x00520244 | 2112 | Ffdatamodulesinglearray | varredura |
| 0x00950118 | 2072 | Aebshellctrls | call |
| 0x00520AD4 | 2056 | Ffdatamodulesinglearray | varredura |
| 0x00408E54 | 2030 | Licensemanager | call |
| 0x00521410 | 2026 | Ffdatamodulesinglearray | varredura |
| 0x00419598 | 1956 | Commonmodules | call |
| 0x00419EDC | 1954 | Commonmodules | call |
| 0x00511635 | 1946 | Autocal_dm | varredura |
| 0x008FF830 | 1937 | Aebcontrolsregister | varredura |
| 0x00526414 | 1892 | Strategiatempimorti_ui | varredura |
| 0x00908D20 | 1889 | Aebcontrolsregister | call |
| 0x00491DD5 | 1886 | Config | varredura |
| 0x00510EDA | 1883 | Autocal_dm | varredura |
| 0x00905D44 | 1874 | Aebcontrolsregister | varredura |
| 0x0048C30A | 1872 | Config | varredura |
| 0x004BF320 | 1853 | Config | varredura |
| 0x008FAEE4 | 1842 | Aebcontrolsregister | varredura |
| 0x0040AD6C | 1793 | Aggkey | call |
| 0x00506E88 | 1721 | Service | call |
| 0x0040E308 | 1714 | Aggkey | call |
| 0x00505403 | 1701 | Scaricafile | varredura |
| 0x004F7896 | 1656 | Main | varredura |
| 0x00485E2F | 1642 | Calibra | varredura |
| 0x004A1F34 | 1569 | Config | call |
| 0x004E9E38 | 1564 | Graficodrt | varredura |
| 0x00902DE4 | 1519 | Aebcontrolsregister | call |
| 0x004F8BB4 | 1422 | Main | varredura |
| 0x0049F290 | 1396 | Config | call |
| 0x004EAF48 | 1393 | Httpthread | call |
| 0x0049FE30 | 1338 | Config | call |
| 0x0096ABE8 | 1326 | Numericedit | varredura |
| 0x009686D4 | 1315 | Keycombobox | varredura |
| 0x0042B254 | 1306 | Iodata | call |
| 0x009752C8 | 1292 | Serialdata | call |
| 0x004369A7 | 1269 | Iodata | varredura |
| 0x008F3038 | 1267 | Aebcontrolsregister | call |
| 0x00933602 | 1210 | Aebcheckbox | varredura |
| 0x004D1656 | 1199 | Diagnosi | varredura |
| 0x004925B4 | 1190 | Config | call |
| 0x0041DA60 | 1182 | Commonmodules | call |
| 0x009086EC | 1180 | Aebcontrolsregister | call |
| 0x004E9706 | 1159 | Graficodrt | varredura |
| 0x008FF088 | 1147 | Aebcontrolsregister | call |
| 0x004CB608 | 1139 | Config | varredura |
| 0x004E8C10 | 1139 | Graficodrt | varredura |
| 0x008FE7E8 | 1115 | Aebcontrolsregister | varredura |
| 0x009757DC | 1101 | Serialdata | call |
| 0x004C7C0C | 1045 | Config | varredura |
| 0x0049F8E0 | 1043 | Config | call |
| 0x005198FD | 1036 | Autocal_ui | varredura |
| 0x00911E38 | 1033 | Aebbee | call |
| 0x004A08F4 | 1011 | Config | call |
| 0x005044F8 | 999 | Main | varredura |
| 0x00905968 | 987 | Aebcontrolsregister | varredura |
| 0x004A1AEC | 986 | Config | call |
| 0x004E75E3 | 985 | Graficodrt | varredura |
| 0x004A0448 | 984 | Config | call |
| 0x008FD720 | 963 | Aebcontrolsregister | varredura |
| 0x00410BB4 | 945 | Aggkey | call |
| 0x0050E324 | 913 | Market | call |
| 0x004AB50B | 900 | Config | varredura |
| 0x00988A70 | 887 | Translatorext | varredura |
| 0x009674E4 | 873 | Gauge | call |
| 0x00901E80 | 866 | Aebcontrolsregister | call |
| 0x005005DC | 863 | Main | ref |
| 0x0095DED8 | 859 | Aebstrings | call |
| 0x00968C14 | 855 | Keycombobox | varredura |
| 0x0096B134 | 839 | Numericedit | varredura |
| 0x00959B78 | 830 | Aebshellctrls | call |
| 0x00490663 | 818 | Config | varredura |
| 0x009625BC | 814 | Buttonimage | varredura |
| 0x0040DC90 | 794 | Aggkey | call |
| 0x00486499 | 792 | Calibra | varredura |
| 0x0049E528 | 786 | Config | varredura |
| 0x0095A6A8 | 782 | Aebshellctrls | call |
| 0x00507FE5 | 777 | Service | varredura |
| 0x00907BBC | 772 | Aebcontrolsregister | call |
| 0x008F754C | 763 | Aebcontrolsregister | varredura |
| 0x00516A8E | 760 | Autocal_ui | varredura |
| 0x00437008 | 753 | Iodata | varredura |
| 0x0091E47C | 742 | Aebftdi | call |
| 0x00904038 | 735 | Aebcontrolsregister | call |
| 0x008F90C8 | 727 | Aebcontrolsregister | call |
| 0x0040B5DC | 672 | Aggkey | call |
| 0x004E90E0 | 664 | Graficodrt | varredura |
| 0x008F7BB4 | 662 | Aebcontrolsregister | call |
| 0x00904B90 | 648 | Aebcontrolsregister | call |
| 0x0051FEA4 | 646 | Ffdatamodulesinglearray | call |
| 0x008FD414 | 637 | Aebcontrolsregister | varredura |
| 0x008F7E4C | 628 | Aebcontrolsregister | call |
| 0x008FE1F0 | 627 | Aebcontrolsregister | varredura |
| 0x009081A8 | 620 | Aebcontrolsregister | call |
| 0x00487390 | 619 | Calibra | varredura |
| 0x008FF578 | 612 | Aebcontrolsregister | call |
| 0x00511DCF | 611 | Autocal_dm | varredura |
| 0x00486D00 | 605 | Calibra | call |
| 0x004ED657 | 600 | Landiconnect | varredura |
| 0x00407E7C | 598 | Licensemanager | call |
| 0x00900A10 | 596 | Aebcontrolsregister | varredura |
| 0x009807F3 | 595 | Serialdata | varredura |
| 0x004C6353 | 592 | Config | varredura |
| 0x0097062C | 590 | Panelimage | varredura |
| 0x009576EC | 587 | Aebshellctrls | varredura |
| 0x008F9660 | 586 | Aebcontrolsregister | varredura |
| 0x004894E8 | 579 | Config | call |
| 0x008F5654 | 575 | Aebcontrolsregister | call |
| 0x004E4DC6 | 574 | Graficodrt | varredura |
| 0x008FA5F4 | 566 | Aebcontrolsregister | varredura |
| 0x00967C70 | 546 | Hsv | call |
| 0x008F861C | 541 | Aebcontrolsregister | call |
| 0x008FDAE4 | 530 | Aebcontrolsregister | varredura |
| 0x008FDCF8 | 530 | Aebcontrolsregister | varredura |
| 0x0040FAE4 | 528 | Aggkey | ref |
| 0x008FB864 | 528 | Aebcontrolsregister | varredura |
| 0x004A1670 | 527 | Config | call |
| 0x004A18D4 | 523 | Config | call |
| 0x0052273F | 522 | Strategiapanda_ui | varredura |
| 0x008F9D1C | 521 | Aebcontrolsregister | varredura |

## 4. Por unidade: onde há mais função sem nome (bytes)

| Unidade | Sem nome | Bytes sem nome |
|---|---:|---:|
| Iodata | 293 | 348361 |
| Config | 182 | 164755 |
| Aebcontrolsregister | 588 | 94189 |
| Main | 40 | 50563 |
| Commonmodules | 52 | 48980 |
| Graficodrt | 113 | 39799 |
| Aggkey | 141 | 30750 |
| Graficoscope | 45 | 29980 |
| Aebshellctrls | 148 | 17159 |
| Ffdatamodulesinglearray | 20 | 15906 |
| Calibra | 32 | 15356 |
| File | 25 | 14928 |
| Autocal_ui | 64 | 14382 |
| Diagnosi | 4 | 14218 |
| Aebbee | 67 | 10862 |
| Aebbeelight | 27 | 10305 |
| Strategiatempimorti_ui | 9 | 9538 |
| Autocal_dm | 42 | 8783 |
| Serialdata | 30 | 7664 |
| Licensemanager | 30 | 7062 |
| Cryptengine | 9 | 6939 |
| Aebgrid | 13 | 6199 |
| Pdflib_ocx | 68 | 5535 |
| Service | 13 | 5459 |
| Numericedit | 24 | 5370 |
| Aebbeeprog | 17 | 5222 |
| Landiconnect | 6 | 4126 |
| Scaricafile | 4 | 4106 |
| Keycombobox | 13 | 4087 |
| Ecufunctionalities | 7 | 3649 |

## 5. Handlers de tela só com descrição automática (INFERIDO), fora de TFormConfig

| Tela | Handler | Endereço | Bytes |
|---|---|---|---:|
| TAboutBox | `EditRegCodeKeyPress` | 0x00481744 | 41 |
| TAboutBox | `FormKeyPress` | 0x004813CC | 14 |
| TAebBeeFormLight | `ButtonAbortClick` | 0x00919978 | 26 |
| TAebBeeFormLight | `ButtonScanChannelClick` | 0x00915578 | 3829 |
| TAebBeeFormLight | `ComboCanaleChange` | 0x00917E88 | 287 |
| TAebBeeFormLight | `ComboPotenzaDongleChange` | 0x00917258 | 323 |
| TAebBeeFormLight | `ComboPotenzaMcuChange` | 0x00917D24 | 323 |
| TAebBeeFormLight | `DongleExit` | 0x00916DC0 | 56 |
| TAebBeeFormLight | `EditIndirizzoDongleKeyPress` | 0x00916FA8 | 687 |
| TAebBeeFormLight | `EditIndirizzoMcuKeyPress` | 0x00917BC8 | 347 |
| TAebBeeFormLight | `EditNomeDongleKeyPress` | 0x00916DF8 | 331 |
| TAebBeeFormLight | `EditNomeMcuKeyPress` | 0x00917A14 | 331 |
| TAebBeeFormLight | `EditPinKeyPress` | 0x00918B1C | 30 |
| TAebBeeFormLight | `FormClose` | 0x00914DD0 | 144 |
| TAebBeeFormLight | `FormCloseQuery` | 0x00914DA8 | 37 |
| TAebBeeFormLight | `FormKeyPress` | 0x00914E80 | 38 |
| TAebBeeFormLight | `ListDeviceDblClick` | 0x00919994 | 50 |
| TAebBeeFormLight | `McuExit` | 0x009179DC | 56 |
| TAebBeeFormLight | `TabScansioneShow` | 0x00918B3C | 184 |
| TAutocalColorSettings | `ChartDataAfterDraw` | 0x0051087C | 213 |
| TAutocalColorSettings | `CheckCustomColorsClick` | 0x00510998 | 35 |
| TAutocalColorSettings | `_AcqusitionAreas0Click` | 0x00510954 | 66 |
| TAutoCalSettings | `BitBtn1Click` | 0x005140E8 | 6 |
| TAutoCalSettings | `BtnChangeColorClick` | 0x0051420C | 101 |
| TAutoCalSettings | `FormShow` | 0x00513DA8 | 8 |
| TAutoCalSettings | `RefreshTimeChange` | 0x005141A4 | 104 |
| TAutoCalUI | `BtnDeleteRandomPointClick` | 0x0051A27C | 274 |
| TAutoCalUI | `BtnDumpClick` | 0x005184D4 | 31 |
| TAutoCalUI | `BtnDumpEEClick` | 0x005184F4 | 31 |
| TAutoCalUI | `BtnMenuClick` | 0x0051A038 | 53 |
| TAutoCalUI | `BtnMenuPanelLeftClick` | 0x0051A24C | 48 |
| TAutoCalUI | `BtnShowBandClick` | 0x0051A74C | 108 |
| TAutoCalUI | `ChartDataAfterDraw` | 0x0051A998 | 527 |
| TAutoCalUI | `ChartDataClickAxis` | 0x0051A008 | 45 |
| TAutoCalUI | `ChartDataClickSeries` | 0x00518C3C | 292 |
| TAutoCalUI | `ChartDataDblClick` | 0x00518E3C | 45 |
| TAutoCalUI | `ChartDataKeyDown` | 0x0051939C | 380 |
| TAutoCalUI | `ChartDataKeyUp` | 0x00519528 | 14 |
| TAutoCalUI | `ChartDataMouseMove` | 0x00517F60 | 1071 |
| TAutoCalUI | `ChartDataMouseUp` | 0x00519134 | 14 |
| TAutoCalUI | `ChartKLineClickSeries` | 0x005176D0 | 154 |
| TAutoCalUI | `ChartKLineDblClick` | 0x00517834 | 143 |
| TAutoCalUI | `ChartKLineDragOver` | 0x005178CC | 167 |
| TAutoCalUI | `ChartKLineEndDrag` | 0x00517980 | 95 |
| TAutoCalUI | `ChartKLineKeyDown` | 0x005179E0 | 970 |
| TAutoCalUI | `ChartKLineKeyUp` | 0x00517DD0 | 21 |
| TAutoCalUI | `ChartKLineMouseDown` | 0x00517DE8 | 150 |
| TAutoCalUI | `ChartKLineMouseMove` | 0x00517E80 | 222 |
| TAutoCalUI | `ChartPointMouseMove` | 0x00518390 | 323 |
| TAutoCalUI | `CheckAutoCalEnableBeforeSetData` | 0x0051A474 | 166 |
| TAutoCalUI | `DbgBtnRandomClick` | 0x005189F0 | 381 |
| TAutoCalUI | `KLineGetMarkText` | 0x005177C8 | 107 |
| TAutoCalUI | `MenuChartDataPopup` | 0x005190A0 | 62 |
| TAutoCalUI | `SettingsImgMouseEnter` | 0x005196C0 | 34 |
| TAutoCalUI | `SettingsImgMouseLeave` | 0x005196E4 | 34 |
| TAutoCalUI | `TestAreasClick` | 0x00519538 | 392 |
| TFormCentriCelle | `ButtonAnnullaExit` | 0x00413724 | 12 |
| TFormCentriCelle | `FormShow` | 0x00413730 | 100 |
| TFormCentriCelleK | `ButtonAnnullaExit` | 0x00413E3C | 26 |
| TFormCentriCelleK | `ButtonOkExit` | 0x00413E20 | 26 |
| TFormCentriCelleK | `FormShow` | 0x00413E58 | 113 |
| TFormCorrCalibra | `FormClose` | 0x004CC63C | 40 |
| TFormDiagnosi | `ComboAzioneChange` | 0x004D736C | 313 |
| TFormDiagnosi | `ComboAzioneEnter` | 0x004D74A8 | 52 |
| TFormDiagnosi | `ComboAzioneKeyDown` | 0x004D7164 | 518 |
| TFormDiagnosi | `EditTempBassaMotEnter` | 0x004D75B0 | 178 |
| TFormDiagnosi | `EditTempBassaMotKeyDown` | 0x004D74DC | 79 |
| TFormDiagnosi | `EditTempBassaMotKeyPress` | 0x004D752C | 130 |
| TFormDiagnosi | `ListDiagnosiChange` | 0x004D6E38 | 558 |
| TFormDiagnosi | `ListDiagnosiClick` | 0x004D7068 | 100 |
| TFormDiagnosi | `ListDiagnosiKeyDown` | 0x004D6CB8 | 384 |
| TFormDiagnosi | `ListDiagnosiKeyPress` | 0x004D6C54 | 97 |
| TFormDiagnosi | `ListDiagnosiSelectItem` | 0x004D70CC | 152 |
| TFormFile | `EditNameKeyDown` | 0x004D9F14 | 27 |
| TFormFile | `FormClose` | 0x004D9DD8 | 313 |
| TFormFile | `TreeViewFileChange` | 0x004DDBC0 | 6 |
| TFormFile | `TreeViewFileCompare` | 0x004DDAE0 | 224 |
| TFormFile | `TreeViewFileDragDrop` | 0x004DE1A8 | 7 |
| TFormFile | `TreeViewFileKeyDown` | 0x004DE120 | 132 |
| TFormGraficoDrt | `ChartDrtClickSeries` | 0x004E6E34 | 36 |
| TFormGraficoScope | `ActionSwitchExecute` | 0x004271B4 | 196 |
| TFormGraficoScope | `ChartDrtClickAxis` | 0x00428A98 | 27 |
| TFormGraficoScope | `ChartDrtMouseMove` | 0x00429334 | 35 |
| TFormGraficoScope | `ChartDrtMouseUp` | 0x00429324 | 14 |
| TFormGraficoScope | `ChartDrtZoom` | 0x00428C60 | 8 |
| TFormGraficoScope | `EditMaxRangeEnter` | 0x00428E04 | 534 |
| TFormGraficoScope | `EditMaxRangeExit` | 0x0042901C | 28 |
| TFormGraficoScope | `EditRangeKeyPress` | 0x004270C8 | 68 |
| TFormGraficoScope | `EditResolutionKeyPress` | 0x00427130 | 108 |
| TFormGraficoScope | `FormClose` | 0x00423858 | 226 |
| TFormGraficoScope | `FormCloseQuery` | 0x00423848 | 16 |
| TFormGraficoScope | `FormKeyPress` | 0x0042719C | 24 |
| TFormGraficoScope | `FormMouseWheelDown` | 0x00428C50 | 13 |
| TFormGraficoScope | `FormMouseWheelUp` | 0x00428C40 | 13 |
| TFormGraficoScope | `FormResize` | 0x00427278 | 87 |
| TFormGraficoScope | `FormShow` | 0x00428DA4 | 93 |
| TFormGraficoScope | `GenericOmvlButtonMouseEnter` | 0x00429040 | 14 |
| TFormGraficoScope | `GenericOmvlButtonMouseLeave` | 0x00429050 | 14 |
| TFormLandiConnect | `DrawGridInjrTOfsTblKeyPress` | 0x004EC95C | 14 |
| TFormLandiConnect | `EditEnter` | 0x004EC8B8 | 30 |
| TFormLandiConnect | `EditExit` | 0x004EC8D8 | 30 |
| TFormLandiConnect | `FormKeyPress` | 0x004EC240 | 14 |
| TFormModificaMappa | `FormClose` | 0x0050EDF8 | 272 |
| TFormModMappa | `FormClose` | 0x00969D68 | 277 |
| TFormModMappa | `GetDouble` | 0x0096A238 | 135 |
| TFormModMappa | `GetInt` | 0x0096A170 | 101 |
| TFormModMappa | `SetDouble` | 0x0096A2C8 | 95 |
| TFormModMappa | `SetInt` | 0x0096A1D8 | 93 |
| TFormService | `ButtonExitClick` | 0x005078D0 | 6 |
| TFormService | `EditDataChange` | 0x00508854 | 23 |
| TFormService | `EditPin2Change` | 0x00507850 | 127 |
| TFormService | `EditPinChange` | 0x005077D0 | 127 |
| TFormService | `EditPinKeyPress` | 0x00508AA0 | 32 |
| TFormService | `EditStandardExit` | 0x00506D80 | 28 |
| TFormService | `EditStandardKeyPress` | 0x00506D9C | 231 |
| TFormService | `FormKeyPress` | 0x00507544 | 112 |
| TFormService | `MemoDataKeyPress` | 0x00508AC0 | 58 |
| TFormSwitch | `ComboBuzzerEnter` | 0x00412E40 | 25 |
| TFormSwitch | `ComboBuzzerKeyPress` | 0x00412E5C | 188 |
| TFormSwitch | `EditSwitchEnter` | 0x00412B38 | 50 |
| TFormSwitch | `EditSwitchKeyPress` | 0x00412B6C | 361 |
| TFormSwitch | `FormKeyPress` | 0x00412B20 | 14 |
| TFormSwitch | `TrackBarLedEnter` | 0x00412CD8 | 19 |
| TFormVisualizza | `FormClose` | 0x0050DCEC | 39 |
| TStrategiaPandaUI | `chk_SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMAClick` | 0x00522E18 | 6 |
| TStrategiaPandaUI | `chk_SP_EN_STRATEGIA_MINIMOClick` | 0x00522E08 | 6 |
| TStrategiaPandaUI | `chk_SP_EN_STRATEGIA_RIC_CLIMAClick` | 0x00522E10 | 6 |
| TStrategiaTempiMortiUI | `UpDownGasAClick` | 0x00526154 | 64 |
| TStrategiaTempiMortiUI | `UpDownPetrolAClick` | 0x00526114 | 64 |

## 6. Chaves de texto sem ligação identificada

`__OPT_NAME`, `__OPT_DESC`, `__OPT_ENABLE`, `LABELAGGFIRMWARE`, `LABELCONFIGCENTRALINA`, `FINESTRAAGGIORNA`, `LABELMENU`, `LABELCONNINCORSO`, `TABTPS`, `MENUCONFIGURAZIONE`, `MENUGRAFICOMAPPA`, `LABELTIPOTPS`, `COMBOTPS1`, `COMBOTPS2`, `LABELTPSSWITCH`, `COMBOTPSSWITCH1`, `COMBOTPSSWITCH2`, `COMBOTPSSWITCH3`, `LABELISTTPS`, `COMBOSENSORE0`, `LABELSENSORE5`, `LABELCONFERMA`, `LABELTEMPOMAXCORR12V`, `LABELFREQHOLDINGCURRENT`, `LABELSAFETYTIME`, `LABELSAFEDELAY`, `LABELTEMPGASTARATURA`, `LABELTEMPRIDTARATURA`, `LABELVARTPS`, `LABELTRIDUTTORE`, `LABELCONFDISP`, `LABELDATACONF`, `LABELSALVAPERTARATURA`, `LABELTIPOFILE`, `LABELTEMPERATURA`, `LABELOFFSETINIETTOREGAS`, `CHECKDRIVEBYWIRE1`, `CHECKDRIVEBYWIRE2`, `CHECKTPSBLOC`, `LABELRIFTPSMIN`, `LABELTIPOMAP`, `RADIOMAPINTERNO`, `RADIOMAPESTERNO`, `LABVARTPS`, `LABARRICCHIMENTO`, `LABELTENSIONEBATTERIA`, `LABELCORRETTOREBENZINA`, `LABELCORRETTOREGAS`, `LABELCORREZIONEINTEGRALE`, `CHECKKFILTROTPSPREMUTO`, `MENUINSERT`, `MENUDELETE`, `MENUSCALALOG`, `LABELGRAFICOMAPPA`, `LABELASSEGIRI`, `FINESTRAGRAFICOMAPPA`, `LABELRIGAGRAFICO`, `LABELCOLONNAGRAFICO`, `LABELTEMPO`, `LABELASSEVALORE`, `FINESTRACANCRIGA`, `MESSCANCRIGA`, `FINESTRAINSRIGA`, `MESSINSRIGA`, `LABELCARBCORR`, `LABELCORRCARBMINIMO`, `LABELCORRCARBBASSO`, `LABELCORRCARBMEDIO`, `LABELCORRCARBMASSIMO`, `MENUSTAMPACONFIG`, `MENUSTAMPACORREZIONI`, `MENUSTAMPADIAGNOSI`, `LABELAGGIORNASCHEDE00`, `LABELAGGIORNASCHEDE01`, `LABELAGGIORNASCHEDE02`, `LABELAGGIORNAAVANTI`, `LABELAGGIORNAINDIETRO`, `LABELAGGIORNAFINE`, `LABELAGGIORNASCHEDE10`, `LABELAGGIORNASCHEDE11`, `LABELAGGIORNASCHEDE12`, `LABELAGGIORNASCHEDE13`, `LABELAGGIORNASCHEDE14`, `LABELAGGIORNASCHEDE15`, `LABELAGGIORNASCHEDE16`, `LABELAGGIORNASCHEDE17`, `LABELAGGIORNASCHEDE18`, `LABELAGGIORNASCHEDE19`, `LABELAGGIORNASCHEDE20`, `LABELAGGIORNASCHEDE21`, `LABELAGGIORNASCHEDE22`, `LABELAGGIORNASCHEDE23`, `LABELAGGIORNASCHEDE24`, `LABELAGGIORNASCHEDE25`, `LABELAGGIORNASCHEDE26`, `LABELAGGIORNASCHEDE27`, `LABELAGGIORNASCHEDE28`, `LABELAGGIORNASCHEDE30`, `LABELAGGIORNASCHEDE31`, `LABELAGGIORNASCHEDE32`, `LABELAGGIORNASCHEDE33`, `LABELAGGIORNASCHEDE40`, `LABELAGGIORNASCHEDE41`, `LABELAGGIORNASCHEDE42`, `LABELAGGIORNASCHEDE43`, `LABELAGGIORNASCHEDE44`, `LABELAGGIORNASCHEDE45`, `LABELAGGIORNASCHEDE46`, `MESSAGGIORNAERRORE0`, `MESSAGGIORNAERRORE1`, `MESSAGGIORNAERRORE2`, `LABELTAGLIAMOLTOPICCOLA`, `LABELTAGLIAMOLTOGRANDE`, `LABELSENSOREMAP`, `MESSAGESEGNALEGIRI`, `HINTSENSOREMAP`, `LABEL_WRONG_LICENSE`, `LABELNUMEROINCPRIMAINJ`, `LABELENGSPDTHD`, `RANGE_ESTESO_MAP_AUTOCAL`, `FF_LABELTITLE`, `FF_GASRESET`, `FF_LABELMSG`, `FF_RESET`, `FF_NOFFINMEMORY`, `BUTTONSTAMPA`, `LABELRISOLUZIONE`, `CHECKAUTORANGE`, `BUTTONOSCILLOSCOPIO`, `MENURIPRISTINAZOOMORIGINALE`, `MENUZOOM`, `AUTOCALWARNINGCONF`, `AUTOCALWARNINGDOWNLOAD`

## 7. Eventos do DFM sem método publicado

(nenhum)

## 8. Estruturas e recursos ainda não decodificados

- `TEELCDFONT` (267 720 B) e `TEELEDFONT` (24 448 B): fontes TrueType embutidas; reconhecidas pelo cabeçalho sfnt, **não extraídas** como arquivo (o repositório não versiona o conteúdo). DESCONHECIDO o uso exato.
- `DVCLAL` (16 B): marcador de licença da VCL do Delphi; conteúdo opaco, irrelevante para o app.
- Imagens/ícones/cursores/versão em RT_BITMAP/RT_ICON/RT_GROUP_ICON/RT_VERSION: **não** catalogados por este inventário (só RT_RCDATA e RT_STRING).
- Tabelas de interface (vmt-84), de automação (vmt-80) e de inicialização (vmt-76) das classes: não decodificadas; os campos existem no `classes.json` só se vierem de RTTI de campos/métodos/propriedades.
- Propriedades publicadas de componentes: extraídas do TypeInfo (`classes.json`); valores ligados por DFM foram usados, mas **identificadores de evento sem método** aparecem na seção 7.
- Vetores/escalares da ECU (`TStreamDati`, 320 campos) e protocolo: pertencem ao inventário de protocolo (docs/progbase/protocolo).
- Corpo da maioria dos 258 handlers de `TFormConfig`: pertence a docs/progbase/abas.

## 9. Limitações conhecidas do método

- A descida recursiva não resolve `call [reg+off]` (virtuais) nem despachos além de `jmp [reg*4+tab]` com `cmp` anterior: funções só alcançáveis assim ficam fora ou entram como "varredura" (origem). INFERIDO.
- O limite superior de cada função é o próximo início conhecido; funções com dados no meio têm o tamanho superestimado ("tamanho_ate_proximo") — por isso o resumo usa só instruções realmente alcançadas.
- Os rótulos de "regiao" separam programa × biblioteca pelos marcadores `@@Unidade@Initialize`; o limite inferior 0x405DC0 (antes dele: startup/RTL) é INFERIDO.
