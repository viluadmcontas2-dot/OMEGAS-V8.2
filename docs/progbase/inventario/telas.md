# Telas do ProgBase.exe: árvore tela → controle → handler

Gerado por `tools/progbase/inventario_telas.py` (SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`). Um arquivo por tela em `telas/`; dados completos em `telas.json`.

| Tela | Papel | Título | Componentes | Menus | Ações | Controles c/ evento | Diálogos | Handlers | Aberta por |
|---|---|---|---:|---:|---:|---:|---:|---:|---|
| [TAboutBox](telas/TAboutBox.md) | diálogo | About | 23 | 0 | 0 | 8 | 1 | 9 | `TFormMain.Create`, `TFormMain.MenuAboutClick` |
| [TAebBeeFormLight](telas/TAebBeeFormLight.md) | tela de função | Wireless Communication | 98 | 0 | 0 | 24 | 1 | 29 | `TAebProtocol::ShowAebBeeSettings` |
| [TAutocalColorSettings](telas/TAutocalColorSettings.md) | diálogo | AutocalColorSettings | 54 | 0 | 0 | 17 | 1 | 5 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)`, `TAutoCalSettings.BtnChangeColorClick` |
| [TAutoCalDM](telas/TAutoCalDM.md) | módulo de dados |  | 36 | 0 | 0 | 4 | 0 | 2 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TAutoCalDM_EE](telas/TAutoCalDM_EE.md) | módulo de dados |  | 15 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TAutoCalSettings](telas/TAutoCalSettings.md) | diálogo | Autocal settings | 25 | 0 | 0 | 5 | 0 | 6 | `TAutoCalUI.SettingsImgClick` |
| [TAutoCalUI](telas/TAutoCalUI.md) | frame |  | 98 | 17 | 16 | 22 | 1 | 47 |  |
| [TFFDataModuleSingleArray](telas/TFFDataModuleSingleArray.md) | módulo de dados |  | 4 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TFormAggKey](telas/TFormAggKey.md) | diálogo | Upgrade hardware key | 7 | 0 | 0 | 3 | 0 | 1 | `0x00414B8C`, `0x0041A680` |
| [TFormAttesa](telas/TFormAttesa.md) | diálogo | Finestra di attesa | 5 | 0 | 0 | 0 | 0 | 0 | `TFormConfig.ComboCarburanteKeyPress`, `TFormConfig.ComboTurboKeyPress` |
| [TFormCalibra](telas/TFormCalibra.md) | tela de função | Calibrazione automatica centralina | 58 | 0 | 0 | 4 | 0 | 5 | `TFormMain.OpenFormAutotaratura` |
| [TFormCentriCelle](telas/TFormCentriCelle.md) | diálogo | Centri celle mappa di carburazione | 37 | 0 | 0 | 3 | 0 | 3 | `0x004B4594`, `TFormConfig.DrawGridEmissioniMultipointDblClick` |
| [TFormCentriCelleK](telas/TFormCentriCelleK.md) | diálogo | Centri celle mappa dei coefficienti k | 53 | 0 | 0 | 3 | 0 | 4 | `TFormConfig.ButtonModificaRifTarClick` |
| [TFormConfig](telas/TFormConfig.md) | tela de função | Configurazione veicolo | 992 | 9 | 0 | 479 | 0 | 258 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TFormCorrCalibra](telas/TFormCorrCalibra.md) | tela de função | Modifica calibrazione | 21 | 0 | 3 | 5 | 0 | 6 | `TFormMain.ActionCorrTaraExecute` |
| [TFormDiagnosi](telas/TFormDiagnosi.md) | tela de função | Diagnosi centralina | 129 | 0 | 0 | 39 | 0 | 25 | `TFormConfig.LabelDiagnosiClick`, `TFormMain.ActionDiagnosiExecute` |
| [TFormFile](telas/TFormFile.md) | diálogo | FormFile | 12 | 0 | 0 | 6 | 0 | 11 | `TFormMain.ActionArchivioExecute`, `TFormMain.ActionSalvaConfExecute` |
| [TFormGestioneTracce](telas/TFormGestioneTracce.md) | diálogo | Aggiungi/rimuovi tracce | 3 | 0 | 0 | 2 | 0 | 0 | `TFormGraficoDrt.MenuTracceClick` |
| [TFormGraficoDrt](telas/TFormGraficoDrt.md) | tela de função | Grafico | 24 | 7 | 0 | 1 | 1 | 5 | `TFormVisualizza.MenuGraficoClick` |
| [TFormGraficoScope](telas/TFormGraficoScope.md) | tela de função | Grafico | 81 | 5 | 9 | 21 | 4 | 34 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)`, `TFormMain.Plot1Click` |
| [TFormHelpScope](telas/TFormHelpScope.md) | não usada (INFERIDO) | Legenda marker | 15 | 0 | 0 | 2 | 0 | 0 |  |
| [TFormLandiConnect](telas/TFormLandiConnect.md) | tela de função |  | 40 | 0 | 0 | 13 | 0 | 11 | `TFormMain.ImageLandiConnectClick` |
| [TFormMain](telas/TFormMain.md) | janela principal | Initializing... | 84 | 24 | 22 | 19 | 3 | 38 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TFormMainBase](telas/TFormMainBase.md) | ancestral | FormMainBase | 2 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TFormMessaggio](telas/TFormMessaggio.md) | não usada (INFERIDO) | FormMessaggio | 2 | 0 | 0 | 0 | 0 | 0 |  |
| [TFormModificaMappa](telas/TFormModificaMappa.md) | diálogo | Variazione cella | 5 | 0 | 0 | 4 | 0 | 2 | `TFormConfig.Create`, `0x004ECD9C` |
| [TFormModMappa](telas/TFormModMappa.md) | diálogo | Modifica valore | 5 | 0 | 0 | 4 | 0 | 6 | `TAebGrid::EditValoriMappa`, `TAebVectorItems::EditValoriMappa` |
| [TFormPdf](telas/TFormPdf.md) | diálogo | FormPdf | 2 | 0 | 0 | 0 | 0 | 0 | `TFormFile.ButtonOkClick` |
| [TFormRifAutocal](telas/TFormRifAutocal.md) | diálogo | Riferimenti per l'autocalibrazione | 83 | 0 | 0 | 2 | 0 | 1 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)`, `TAutoCalUI.ActionAutoCalRifExecute` |
| [TFormRiferimenti](telas/TFormRiferimenti.md) | diálogo | Modifica riferimenti mappa | 5 | 0 | 0 | 3 | 0 | 1 | `TAebGrid::EditRiferimentiMappa` |
| [TFormScarica](telas/TFormScarica.md) | diálogo | Programmazione centralina | 9 | 0 | 0 | 3 | 1 | 1 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TFormService](telas/TFormService.md) | tela de função | Gestione tagliandi | 36 | 0 | 0 | 12 | 0 | 21 | `TFormMain.ImageServiceClick` |
| [TFormSplash](telas/TFormSplash.md) | diálogo | FormSplash | 2 | 0 | 0 | 0 | 0 | 0 | `TFormMain.Create` |
| [TFormStampa](telas/TFormStampa.md) | não usada (INFERIDO) | FormStampa | 2 | 0 | 0 | 0 | 0 | 0 |  |
| [TFormSwitch](telas/TFormSwitch.md) | diálogo | Switch settings | 11 | 0 | 0 | 6 | 0 | 9 | `TFormConfig.ButtonSwitchSettingsClick` |
| [TFormVisualizza](telas/TFormVisualizza.md) | tela de função | Visualizzazione parametri vettura | 85 | 0 | 0 | 3 | 2 | 6 | `TFormMain.ActionVisualizzaExecute` |
| [TStrategiaPandaDM](telas/TStrategiaPandaDM.md) | módulo de dados |  | 22 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TStrategiaPandaDM_EE](telas/TStrategiaPandaDM_EE.md) | módulo de dados |  | 1 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TStrategiaPandaUI](telas/TStrategiaPandaUI.md) | frame |  | 46 | 0 | 0 | 3 | 0 | 3 |  |
| [TStrategiaTempiMortiDM](telas/TStrategiaTempiMortiDM.md) | módulo de dados |  | 3 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TStrategiaTempiMortiDM_EE](telas/TStrategiaTempiMortiDM_EE.md) | módulo de dados |  | 1 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TStrategiaTempiMortiUI](telas/TStrategiaTempiMortiUI.md) | frame |  | 105 | 0 | 0 | 20 | 0 | 2 |  |
| [TStreamDati](telas/TStreamDati.md) | módulo de dados |  | 321 | 0 | 0 | 0 | 0 | 1 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
