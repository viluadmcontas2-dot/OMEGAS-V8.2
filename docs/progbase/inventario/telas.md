# Telas do ProgBase.exe: árvore tela → controle → handler

Gerado por `tools/progbase/inventario_telas.py` (SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`). Um arquivo por tela em `telas/`; dados completos em `telas.json`.

| Tela | Recurso | Título | Componentes | Menus | Ações | Controles c/ evento | Diálogos | Handlers | Aberta por |
|---|---|---|---:|---:|---:|---:|---:|---:|---|
| [TAboutBox](telas/TAboutBox.md) | TABOUTBOX | About | 23 | 0 | 0 | 8 | 1 | 9 | `TFormMain.Create`, `TFormMain.MenuAboutClick` |
| [TAebBeeFormLight](telas/TAebBeeFormLight.md) | TAEBBEEFORMLIGHT | Wireless Communication | 98 | 0 | 0 | 24 | 1 | 29 | `TAebProtocol::ShowAebBeeSettings` |
| [TAutocalColorSettings](telas/TAutocalColorSettings.md) | TAUTOCALCOLORSETTINGS | AutocalColorSettings | 54 | 0 | 0 | 17 | 1 | 5 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)`, `TAutoCalSettings.BtnChangeColorClick` |
| [TAutoCalDM](telas/TAutoCalDM.md) | TAUTOCALDM |  | 36 | 0 | 0 | 4 | 0 | 2 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TAutoCalDM_EE](telas/TAutoCalDM_EE.md) | TAUTOCALDM_EE |  | 15 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TAutoCalSettings](telas/TAutoCalSettings.md) | TAUTOCALSETTINGS | Autocal settings | 25 | 0 | 0 | 5 | 0 | 6 | `TAutoCalUI.SettingsImgClick` |
| [TAutoCalUI](telas/TAutoCalUI.md) | TAUTOCALUI |  | 98 | 17 | 16 | 22 | 1 | 47 |  |
| [TFFDataModuleSingleArray](telas/TFFDataModuleSingleArray.md) | TFFDATAMODULESINGLEARRAY |  | 4 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TFormAggKey](telas/TFormAggKey.md) | TFORMAGGKEY | Upgrade hardware key | 7 | 0 | 0 | 3 | 0 | 1 | `0x00414B8C`, `0x0041A680` |
| [TFormAttesa](telas/TFormAttesa.md) | TFORMATTESA | Finestra di attesa | 5 | 0 | 0 | 0 | 0 | 0 | `TFormConfig.ComboCarburanteKeyPress`, `TFormConfig.ComboTurboKeyPress` |
| [TFormCalibra](telas/TFormCalibra.md) | TFORMCALIBRA | Calibrazione automatica centralina | 58 | 0 | 0 | 4 | 0 | 5 | `TFormMain.OpenFormAutotaratura` |
| [TFormCentriCelle](telas/TFormCentriCelle.md) | TFORMCENTRICELLE | Centri celle mappa di carburazione | 37 | 0 | 0 | 3 | 0 | 3 | `0x004B4594`, `TFormConfig.DrawGridEmissioniMultipointDblClick` |
| [TFormCentriCelleK](telas/TFormCentriCelleK.md) | TFORMCENTRICELLEK | Centri celle mappa dei coefficienti k | 53 | 0 | 0 | 3 | 0 | 4 | `TFormConfig.ButtonModificaRifTarClick` |
| [TFormConfig](telas/TFormConfig.md) | TFORMCONFIG | Configurazione veicolo | 992 | 9 | 0 | 479 | 0 | 258 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TFormCorrCalibra](telas/TFormCorrCalibra.md) | TFORMCORRCALIBRA | Modifica calibrazione | 21 | 0 | 3 | 5 | 0 | 6 | `TFormMain.ActionCorrTaraExecute` |
| [TFormDiagnosi](telas/TFormDiagnosi.md) | TFORMDIAGNOSI | Diagnosi centralina | 129 | 0 | 0 | 39 | 0 | 25 | `TFormConfig.LabelDiagnosiClick`, `TFormMain.ActionDiagnosiExecute` |
| [TFormFile](telas/TFormFile.md) | TFORMFILE | FormFile | 12 | 0 | 0 | 6 | 0 | 11 | `TFormMain.ActionArchivioExecute`, `TFormMain.ActionSalvaConfExecute` |
| [TFormGestioneTracce](telas/TFormGestioneTracce.md) | TFORMGESTIONETRACCE | Aggiungi/rimuovi tracce | 3 | 0 | 0 | 2 | 0 | 0 | `TFormGraficoDrt.MenuTracceClick` |
| [TFormGraficoDrt](telas/TFormGraficoDrt.md) | TFORMGRAFICODRT | Grafico | 24 | 7 | 0 | 1 | 1 | 5 | `TFormVisualizza.MenuGraficoClick` |
| [TFormGraficoScope](telas/TFormGraficoScope.md) | TFORMGRAFICOSCOPE | Grafico | 81 | 5 | 9 | 21 | 4 | 34 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)`, `TFormMain.Plot1Click` |
| [TFormHelpScope](telas/TFormHelpScope.md) | TFORMHELPSCOPE | Legenda marker | 15 | 0 | 0 | 2 | 0 | 0 |  |
| [TFormLandiConnect](telas/TFormLandiConnect.md) | TFORMLANDICONNECT |  | 40 | 0 | 0 | 13 | 0 | 11 | `TFormMain.ImageLandiConnectClick` |
| [TFormMain](telas/TFormMain.md) | TFORMMAIN | Initializing... | 84 | 24 | 22 | 19 | 3 | 38 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TFormMainBase](telas/TFormMainBase.md) | TFORMMAINBASE | FormMainBase | 2 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TFormMessaggio](telas/TFormMessaggio.md) | TFORMMESSAGGIO | FormMessaggio | 2 | 0 | 0 | 0 | 0 | 0 |  |
| [TFormModificaMappa](telas/TFormModificaMappa.md) | TFORMMODIFICAMAPPA | Variazione cella | 5 | 0 | 0 | 4 | 0 | 2 | `TFormConfig.Create`, `0x004ECD9C` |
| [TFormModMappa](telas/TFormModMappa.md) | TFORMMODMAPPA | Modifica valore | 5 | 0 | 0 | 4 | 0 | 6 | `TAebGrid::EditValoriMappa`, `TAebVectorItems::EditValoriMappa` |
| [TFormPdf](telas/TFormPdf.md) | TFORMPDF | FormPdf | 2 | 0 | 0 | 0 | 0 | 0 | `TFormFile.ButtonOkClick` |
| [TFormRifAutocal](telas/TFormRifAutocal.md) | TFORMRIFAUTOCAL | Riferimenti per l'autocalibrazione | 83 | 0 | 0 | 2 | 0 | 1 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)`, `TAutoCalUI.ActionAutoCalRifExecute` |
| [TFormRiferimenti](telas/TFormRiferimenti.md) | TFORMRIFERIMENTI | Modifica riferimenti mappa | 5 | 0 | 0 | 3 | 0 | 1 | `TAebGrid::EditRiferimentiMappa` |
| [TFormScarica](telas/TFormScarica.md) | TFORMSCARICA | Programmazione centralina | 9 | 0 | 0 | 3 | 1 | 1 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TFormService](telas/TFormService.md) | TFORMSERVICE | Gestione tagliandi | 36 | 0 | 0 | 12 | 0 | 21 | `TFormMain.ImageServiceClick` |
| [TFormSplash](telas/TFormSplash.md) | TFORMSPLASH | FormSplash | 2 | 0 | 0 | 0 | 0 | 0 | `TFormMain.Create` |
| [TFormStampa](telas/TFormStampa.md) | TFORMSTAMPA | FormStampa | 2 | 0 | 0 | 0 | 0 | 0 |  |
| [TFormSwitch](telas/TFormSwitch.md) | TFORMSWITCH | Switch settings | 11 | 0 | 0 | 6 | 0 | 9 | `TFormConfig.ButtonSwitchSettingsClick` |
| [TFormVisualizza](telas/TFormVisualizza.md) | TFORMVISUALIZZA | Visualizzazione parametri vettura | 85 | 0 | 0 | 3 | 2 | 6 | `TFormMain.ActionVisualizzaExecute` |
| [TStrategiaPandaDM](telas/TStrategiaPandaDM.md) | TSTRATEGIAPANDADM |  | 22 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TStrategiaPandaDM_EE](telas/TStrategiaPandaDM_EE.md) | TSTRATEGIAPANDADM_EE |  | 1 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TStrategiaPandaUI](telas/TStrategiaPandaUI.md) | TSTRATEGIAPANDAUI |  | 46 | 0 | 0 | 3 | 0 | 3 |  |
| [TStrategiaTempiMortiDM](telas/TStrategiaTempiMortiDM.md) | TSTRATEGIATEMPIMORTIDM |  | 3 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TStrategiaTempiMortiDM_EE](telas/TStrategiaTempiMortiDM_EE.md) | TSTRATEGIATEMPIMORTIDM_EE |  | 1 | 0 | 0 | 0 | 0 | 0 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
| [TStrategiaTempiMortiUI](telas/TStrategiaTempiMortiUI.md) | TSTRATEGIATEMPIMORTIUI |  | 105 | 0 | 0 | 20 | 0 | 2 |  |
| [TStreamDati](telas/TStreamDati.md) | TSTREAMDATI |  | 321 | 0 | 0 | 0 | 0 | 1 | `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` |
