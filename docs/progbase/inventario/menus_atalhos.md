# Menus, ações e atalhos do ProgBase.exe

Gerado por `tools/progbase/inventario_telas.py` (OBS-COD: DFM; handler resolvido pela RTTI). TFormConfig incluído só no que é menu.
- Itens: **112**, dos quais com atalho de teclado: **12**.


## TAutoCalUI

| Tipo | Caminho/componente | Legenda (ita / eng / por) | Atalho | Handler(s) |
|---|---|---|---|---|
| menu | MenuChartK/ExportKchart1 | Export to file... |  | `ActionExportKChartExecute` (0x005185FC) |
| menu | MenuChartK/Resetkfactor1 | Reset k-factor... |  | `ActionResetKFactorExecute` (0x0051A070) |
| menu | MenuChartData/Exporttofile1 | Export to file... |  | `ActionExportChartDataExecute` (0x0051878C) |
| menu | MenuChartData/Deleteselectedpoints1 | Delete selected points |  | `ActionDeleteSelectedPointsExecute` (0x00518E6C) |
| menu | MenuChartData/Resetpetrolpoint1 | Reset petrol point |  | `ActionResetPetrolExecute` (0x005189C0) |
| menu | MenuChartData/Resetgaspoint1 | Reset gas point |  | `ActionResetGasExecute` (0x005189CC) |
| menu | MenuChartData/Resetall1 | Reset all |  | `ActionResetAllExecute` (0x005189D8) |
| menu | MenuChartData/Modifymaprefs2 | Modify map refs |  | `ActionAutoCalRifExecute` (0x005187A0) |
| menu | MenuChartData/Customizechartcolors1 | Customize chart colors... |  | `ActionChangeColorExecute` (0x0051AE78) |
| menu | MenuChartPoint/Exporttofile2 | Export to file... |  | `ActionExportChartPointExecute` (0x00519120) |
| menu | MenuAutoCal/Modifymaprefs1 | Modify map refs |  | `ActionAutoCalRifExecute` (0x005187A0) |
| menu | MenuAutoCal/Gotomincalibration1 | Go to min calibration |  | `ActionGoToMinCalibrationExecute` (0x005189E4) |
| menu | MenuAutoCal/Manualautomatch1 | Manual automatch |  | `ActionAutoMatchExecute` (0x005189B4) |
| menu | MenuAutoCal/Resetpetrolpoint2 | Reset petrol point |  | `ActionResetPetrolExecute` (0x005189C0) |
| menu | MenuAutoCal/Resetgaspoint2 | Reset gas point |  | `ActionResetGasExecute` (0x005189CC) |
| menu | MenuAutoCal/Resetall2 | Reset all |  | `ActionResetAllExecute` (0x005189D8) |
| menu | MenuAutoCal/FinishAutocal1 | FinishAutocal |  | `ActionFinishAutocalExecute` (0x0051A390) |
| ação | AzioniAutoCal/ActionExportToK | ExportToK... |  | `ActionExportToKExecute` (0x00518514) |
| ação | AzioniAutoCal/ActionPreview | Chart preview |  |  |
| ação | AzioniAutoCal/ActionExportKChart | Export to file... |  | `ActionExportKChartExecute` (0x005185FC) |
| ação | AzioniAutoCal/ActionExportChartData | Export to file... |  | `ActionExportChartDataExecute` (0x0051878C) |
| ação | AzioniAutoCal/ActionAutoCalRif | Modify map refs |  | `ActionAutoCalRifExecute` (0x005187A0) |
| ação | AzioniAutoCal/ActionAutoMatch | Manual automatch |  | `ActionAutoMatchExecute` (0x005189B4) |
| ação | AzioniAutoCal/ActionResetPetrol | Reset petrol point |  | `ActionResetPetrolExecute` (0x005189C0) |
| ação | AzioniAutoCal/ActionResetGas | Reset gas point |  | `ActionResetGasExecute` (0x005189CC) |
| ação | AzioniAutoCal/ActionResetAll | Reset all |  | `ActionResetAllExecute` (0x005189D8) |
| ação | AzioniAutoCal/ActionGoToMinCalibration | Go to min calibration |  | `ActionGoToMinCalibrationExecute` (0x005189E4) |
| ação | AzioniAutoCal/ActionDeleteSelectedPoints | Delete selected points |  | `ActionDeleteSelectedPointsExecute` (0x00518E6C) |
| ação | AzioniAutoCal/ActionExportChartPoint | Export to file... |  | `ActionExportChartPointExecute` (0x00519120) |
| ação | AzioniAutoCal/SettingsAutocal | Settings |  |  |
| ação | AzioniAutoCal/ActionResetKFactor | Reset k-factor... |  | `ActionResetKFactorExecute` (0x0051A070) |
| ação | AzioniAutoCal/ActionFinishAutocal | FinishAutocal |  | `ActionFinishAutocalExecute` (0x0051A390) |
| ação | AzioniAutoCal/ActionChangeColor | Customize chart colors... |  | `ActionChangeColorExecute` (0x0051AE78) |

## TFormConfig

| Tipo | Caminho/componente | Legenda (ita / eng / por) | Atalho | Handler(s) |
|---|---|---|---|---|
| menu | MainMenu1/MenuTest | &Funzioni di test / &Test functions / &Test functions |  |  |
| menu | MainMenu1/MenuTest/MenuDiagnosiProg | Diagnosi &Programma / Program diagnosis / Program diagnosis |  |  |
| menu | MainMenu1/MenuTest/MenuSbloccaLimiti | &Sblocca limiti / Unlock limits / Unlock limits |  | `MenuSbloccaLimitiClick` (0x004A95C8) |
| menu | MainMenu1/MenuTest/MenuControlloIniettori | Abilita controllo iniettori |  | `MenuControlloIniettoriClick` (0x004BA408) |
| menu | MainMenu1/MenuTest/N1 | - |  |  |
| menu | MainMenu1/MenuTest/MenuComunicazioneSerialeCostruita | Comunicazione seriale costruita |  | `MenuComunicazioneSerialeCostruitaClick` (0x004AE008) |
| menu | MainMenu1/MenuTest/MenuAebBee | Proprietà Aeb Device |  | `MenuAebBeeClick` (0x004AE414) |
| menu | PopupMenuGrid/MenuCopia | &Copia / &Copy / &Copiar | Ctrl+C | `MenuCopiaTaraturaClick` (0x004AD0B0) |
| menu | PopupMenuGrid/MenuIncolla | &Incolla / &Paste / &Colar | Ctrl+V | `MenuIncollaTaraturaClick` (0x004AD4B8) |

## TFormCorrCalibra

| Tipo | Caminho/componente | Legenda (ita / eng / por) | Atalho | Handler(s) |
|---|---|---|---|---|
| ação | ActionListCorrCalibra/ActionIncrementaZona | &Incrementa zona (PgUp) / &Increase zone (PgUp) / &Incrementar zona (PgUp) | PgUp | `ActionIncrementaZonaExecute` (0x004CCFFC) |
| ação | ActionListCorrCalibra/ActionDecrementaZona | &Decrementa zona (PgDn) / &Decrease zone (PgDn) / &Decrementar zona (PgDn) | PgDn | `ActionDecrementaZonaExecute` (0x004CD134) |
| ação | ActionListCorrCalibra/ActionEsci | &Esci (Esc) / &Exit (Esc) / &Sair (Esc) | Esc | `ActionEsciExecute` (0x004CD26C) |

## TFormGraficoDrt

| Tipo | Caminho/componente | Legenda (ita / eng / por) | Atalho | Handler(s) |
|---|---|---|---|---|
| menu | MainMenu1/MenuGraficoDrt | &Grafico / &Graph / &Gráfico |  |  |
| menu | MainMenu1/MenuGraficoDrt/MenuTracce | Gestione tracce |  | `MenuTracceClick` (0x004E6A84) |
| menu | MainMenu1/MenuGraficoDrt/MenuStampa | &Stampa / &Print / &Imprimir | Ctrl+P | `MenuStampaClick` (0x004E6A50) |
| menu | MainMenu1/MenuGraficoDrt/N2 | - |  |  |
| menu | MainMenu1/MenuGraficoDrt/MenuAnnullaZoom | &Annulla zoom / &Cancel zoom / &Cancelar zoom | Ctrl+Z | `MenuAnnullaZoomClick` (0x004E6A24) |
| menu | MainMenu1/MenuGraficoDrt/N1 | - |  |  |
| menu | MainMenu1/MenuGraficoDrt/MenuEsci | &Esci / &Exit / &Sair | Esc | `MenuEsciClick` (0x004E6A48) |

## TFormGraficoScope

| Tipo | Caminho/componente | Legenda (ita / eng / por) | Atalho | Handler(s) |
|---|---|---|---|---|
| menu | MainMenu/MenuRimuoviMarker | Rimuovi marker |  | `MenuRimuoviMarkerClick` (0x00428B1C) |
| menu | MainMenu/MenuRimuoviCursore | Rimuovi Cursore |  | `ActionRimuoviCursoreExecute` (0x00428C68) |
| menu | MainMenu/MenuMarker | Marker |  |  |
| menu | MainMenu/MenuMarker/MenuPreviousMarker | Marker precedente / Previous marker / None |  | `ActionPreviousMarkerExecute` (0x00428B68) |
| menu | MainMenu/MenuMarker/MenuMarkerSuccessivo | Marker successivo / Next marker / None |  | `ActionNextMarkerExecute` (0x00428B48) |
| ação | ScopeActionList/ActionAddMarker | AddMarker | F12 | `ActionAddMarkerExecute` (0x004281FC) |
| ação | ScopeActionList/ActionZoomIn |  |  |  |
| ação | ScopeActionList/ActionZoomOut |  |  |  |
| ação | ScopeActionList/ActionUndoZoom | ActionUndoZoom |  |  |
| ação | ScopeActionList/ActionNextMarker | ActionNextMarker |  | `ActionNextMarkerExecute` (0x00428B48) |
| ação | ScopeActionList/ActionPreviousMarker | ActionPreviousMarker |  | `ActionPreviousMarkerExecute` (0x00428B68) |
| ação | ScopeActionList/ActionRimuoviCursore | ActionRimuoviCursore |  | `ActionRimuoviCursoreExecute` (0x00428C68) |
| ação | ScopeActionList/ActionTestEcuMarker | ActionTestEcuMarker |  |  |
| ação | ScopeActionList/ActionApriHelp | ActionApriHelp |  |  |

## TFormMain

| Tipo | Caminho/componente | Legenda (ita / eng / por) | Atalho | Handler(s) |
|---|---|---|---|---|
| menu | MainMenu/MenuFile | &File |  |  |
| menu | MainMenu/MenuFile/MenuApri | &Apri configurazione... / &Open configuration... / &Abrir configuração... |  | `ActionApriExecute` (0x004FE618) |
| menu | MainMenu/MenuFile/MenuBloccoNote | Apri configurazione in &blocco note... |  | `ActionBloccoNoteExecute` (0x004FFE18) |
| menu | MainMenu/MenuFile/MenuSalva | &Salva configurazione... / &Save configuration... / &Guardar configuração... |  | `ActionSalvaExecute` (0x004FEA54) |
| menu | MainMenu/MenuFile/MenuSalvaMappa | Salva solo mappa in configurazione... |  | `ActionSalvaMappaExecute` (0x004FFE20) |
| menu | MainMenu/MenuFile/MenuFileHcf | Salva configurazione su file hcf... |  | `ActionFileHcfExecute` (0x004FFE1C) |
| menu | MainMenu/MenuFile/N3 | - |  |  |
| menu | MainMenu/MenuFile/Plot1 | Graph |  | `Plot1Click` (0x0050417C) |
| menu | MainMenu/MenuFile/N4 | - |  |  |
| menu | MainMenu/MenuFile/MenuArchivio | Istruzioni di montaggio / Installation instructions / Instruções de montagem |  | `ActionArchivioExecute` (0x004FF6C4) |
| menu | MainMenu/MenuFile/N1 | - |  |  |
| menu | MainMenu/MenuFile/MenuEsci | Esci / Exit / Sair |  | `ActionEsciExecute` (0x004F8B74) |
| menu | MainMenu/MenuCpu | &Connessione / &Connection / &Conexão |  |  |
| menu | MainMenu/MenuCpu/MenuConnetti | &Connetti / &Connect / &Ligar |  | `ActionConnettiExecute` (0x004FB840) |
| menu | MainMenu/MenuCpu/MenuDisconnetti | &Disconnetti / &Disconnect / &Desconectar |  | `ActionDisconnettiExecute` (0x004FB858) |
| menu | MainMenu/MenuCpu/N2 | - |  |  |
| menu | MainMenu/MenuCpu/MenuResetCpu | &Reset centralina / &Reset ECU / &Reset ECU |  | `ActionResetCentralinaExecute` (0x00501244) |
| menu | MainMenu/MenuCpu/N5 | - |  |  |
| menu | MainMenu/MenuCpu/MenuClientPers | Personalizzazione / Personalization / Personalization |  |  |
| menu | MainMenu/MenuConfig | Impostazioni / Settings / None |  |  |
| menu | MainMenu/MenuConfig/MenuSelLingua | Lingua / Language / Idioma |  |  |
| menu | MainMenu/MenuAiuto | &Aiuto / &Help / &Ajuda |  |  |
| menu | MainMenu/MenuAiuto/MenuAbout | &Informazioni su... / &About... / &Informações sobre... |  | `MenuAboutClick` (0x004F7808) |
| menu | MainMenu/MenuMarket | Market |  |  |
| ação | MainActionList/ActionArchivio | Archivio schede |  | `ActionArchivioExecute` (0x004FF6C4) |
| ação | MainActionList/ActionVisualizza |  |  | `ActionVisualizzaExecute` (0x00501568) |
| ação | MainActionList/ActionConf |  |  | `ActionConfExecute` (0x004F95F4) |
| ação | MainActionList/ActionDiagnosi |  |  | `ActionDiagnosiExecute` (0x004FFE24) |
| ação | MainActionList/ActionEsci |  |  | `ActionEsciExecute` (0x004F8B74) |
| ação | MainActionList/ActionApri | &Apri |  | `ActionApriExecute` (0x004FE618) |
| ação | MainActionList/ActionSalva | &Salva |  | `ActionSalvaExecute` (0x004FEA54) |
| ação | MainActionList/ActionConnetti | &Connetti / &Connect / &Ligar | Ctrl+C | `ActionConnettiExecute` (0x004FB840) |
| ação | MainActionList/ActionDisconnetti | &Disconnetti / &Disconnect / &Desconectar | Ctrl+D | `ActionDisconnettiExecute` (0x004FB858) |
| ação | MainActionList/ActionProgramma |  |  | `ActionProgrammaExecute` (0x004FDBD4) |
| ação | MainActionList/ActionBloccoNote | Apri configurazione in &blocco note |  | `ActionBloccoNoteExecute` (0x004FFE18) |
| ação | MainActionList/ActionSalvaConf |  |  | `ActionSalvaConfExecute` (0x005000CC) |
| ação | MainActionList/ActionApriConf |  |  | `ActionApriConfExecute` (0x00500314) |
| ação | MainActionList/ActionUpdate |  |  | `ActionUpdateExecute` (0x005035D4) |
| ação | MainActionList/ActionAggiorna |  |  | `ActionAggiornaExecute` (0x005035DC) |
| ação | MainActionList/ActionResetCentralina | &Reset centralina / &Reset ECU / &Reset ECU |  | `ActionResetCentralinaExecute` (0x00501244) |
| ação | MainActionList/ActionTara | ActionTara |  | `ActionTaraExecute` (0x00500DDC) |
| ação | MainActionList/ActionCorrTara | ActionCorrTara |  | `ActionCorrTaraExecute` (0x00500DE4) |
| ação | MainActionList/ActionResetDaPc | ActionResetDaPc |  | `ActionResetDaPcExecute` (0x00501248) |
| ação | MainActionList/ActionFileHcf | Salva configurazione su file hcf... |  | `ActionFileHcfExecute` (0x004FFE1C) |
| ação | MainActionList/ActionSalvaMappa | Salva solo mappa in configurazione... |  | `ActionSalvaMappaExecute` (0x004FFE20) |
| ação | MainActionList/ActionAzzeraId | ActionAzzeraId | Ctrl+A | `ActionAzzeraIdExecute` (0x005039A0) |
