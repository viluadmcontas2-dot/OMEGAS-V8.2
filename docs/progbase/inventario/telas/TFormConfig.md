# TFormConfig (recurso `TFORMCONFIG`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormConfig`; unidade: `@:Config`; ancestral: `TForm`; tamanho de instância: 0x18C4; componentes no DFM: 992.
- Título: "Configurazione veicolo" — eng: "Vehicle configuration" / por: "Configuração do veículo"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` (0x004111F8)

## Menus (9)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| MainMenu1/MenuTest | TMenuItem | &Funzioni di test / &Test functions / &Test functions |  |  |
| MainMenu1/MenuTest/MenuDiagnosiProg | TMenuItem | Diagnosi &Programma / Program diagnosis / Program diagnosis |  |  |
| MainMenu1/MenuTest/MenuSbloccaLimiti | TMenuItem | &Sblocca limiti / Unlock limits / Unlock limits |  | OnClick→`MenuSbloccaLimitiClick` (0x004A95C8) |
| MainMenu1/MenuTest/MenuControlloIniettori | TMenuItem | Abilita controllo iniettori |  | OnClick→`MenuControlloIniettoriClick` (0x004BA408) |
| MainMenu1/MenuTest/N1 | TMenuItem | - |  |  |
| MainMenu1/MenuTest/MenuComunicazioneSerialeCostruita | TMenuItem | Comunicazione seriale costruita |  | OnClick→`MenuComunicazioneSerialeCostruitaClick` (0x004AE008) |
| MainMenu1/MenuTest/MenuAebBee | TMenuItem | Proprietà Aeb Device |  | OnClick→`MenuAebBeeClick` (0x004AE414) |
| PopupMenuGrid/MenuCopia | TMenuItem | &Copia / &Copy / &Copiar | Ctrl+C | OnClick→`MenuCopiaTaraturaClick` (0x004AD0B0) |
| PopupMenuGrid/MenuIncolla | TMenuItem | &Incolla / &Paste / &Colar | Ctrl+V | OnClick→`MenuIncollaTaraturaClick` (0x004AD4B8) |

## Controles com evento/ação (479)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormConfig | TFormConfig | Configurazione veicolo / Vehicle configuration / Configuração do veículo |  | OnClose→`FormClose` (0x00490C98); OnCloseQuery→`FormCloseQuery` (0x004ADE94); OnDestroy→`FormDestroy` (0x00490BB4); OnKeyDown→`FormKeyDown` (0x0049EB24); OnResize→`FormResize` (0x00490CB8); OnShow→`FormShow` (0x00490028) |
| PanelBarra/PanelArr/LabelDiagnosi | TLabel | DIAGNOSI |  | OnClick→`LabelDiagnosiClick` (0x004CB060) |
| PanelBarra/PanelStato/ImageCommutatore | TImage |  |  | OnClick→`LabGasClick` (0x004C9A7C) |
| PanelBarra/PanelStato/LabGas | TLabel | BENZINA / PETROL / GASOLINA |  | OnClick→`LabGasClick` (0x004C9A7C) |
| PageControl | TPageControl |  |  | OnChanging→`PageControlChanging` (0x004ADD20); OnDrawTab→`PageControlDrawTab` (0x0049E83C) |
| PageControl/TabCambio | TTabSheet | Cambio GAS / Gas change-over / Mudança do gás |  | OnShow→`TabCambioShow` (0x004A6ACC) |
| PageControl/TabCambio/ScrollBoxCambio/LabelRitardoCambioGas | TLabel | Ritardo passaggio benzina-gas con temp. gas / Delay in petrol-gas switch over with gas temp. / Atraso na reversão gasolina-gás com temp. Gás |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelSovrapposizione | TLabel | Tempo di sovrapposizione / Overlapping time / Tempo de sobreposição |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelTipoIniettore | TLabel | Tipo iniettore / Injector type / Tipo de injetor |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelTurbo | TLabel | Motore / Engine / Motor |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelTInjBenzMaxPerCambio | TLabel | Tinj benzina massimo per cambio gas / Max petrol inj. time for gas change-over / T.inj gasolina máximo para mudança gás |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelRitardoCambio | TLabel | Ritardo passaggio benzina-gas / Switch-over from petrol to gas delay / Atraso na passagem gasolina/gás |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelTempCambio | TLabel | Temperatura gas per il cambio / Gas temperature for change-over / Temperatura do gás para o cambio |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelTempMotore | TLabel | Temperatura acqua per il cambio / Water temperature for change-over / Temperatura da água para o cambio |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelGiriCambio | TLabel | Soglia giri per il cambio / Revs. threshold for change-over / Nº de rotações para a mudança |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelCambio | TLabel | Tipo di cambio / Type of change-over / Tipo de mudança |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelAccensione | TLabel | Tipo di accensione / Ignition type / Tipo de ignição |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelCilindri | TLabel | Numero cilindri / No. of cylinders / Número de cilindros |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelSegnaleGiri | TLabel | Tipo segnale giri |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelCilindrata | TLabel | Cilindrata (cc) / Displacement (cc) / Cilindrada (cc) |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelCarburante | TLabel | Tipo carburante / Fuel type / Tipo de combustível |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelTaglia | TLabel | Taglia / Size / Medida |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelCicchetto | TLabel | Tempo di cicchetto / Priming time / Priming time |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelSogliaGiri | TLabel | Soglia / Threshold / Threshold |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelIstantaneoValue | TLabel | Num. iniettate tra cilindri per cambio gas / Num.inj. for sequential cylinders change-over / Num.inj. for sequential cylinders change-over |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelCicchettoRpm | TLabel | Soglia giri per crank / RPM threshold for crank / RPM threshold for crank |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelCicchettoInj | TLabel | Tempo di iniezione gas in cicchetto / Gas injection time in priming / Gas injection time in priming |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelCicchettoNum | TLabel | Numero iniettate gas in cicchetto / Number of gas injections in priming / Number of gas injections in priming |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelNumDenti | TLabel | n. denti / teeth n. / teeth n. |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/LabelLetturaGiri | TLabel | Lettura giri: / RPM reading: / RPM reading: |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/DrawDelayGasTemp | TDrawGrid |  |  | OnDblClick→`DrawDelayGasTempDblClick` (0x004C42E4); OnDrawCell→`DrawDelayGasTempDrawCell` (0x004C3EA4); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawDelayGasTempKeyPress` (0x004C42D4) |
| PageControl/TabCambio/ScrollBoxCambio/EditSovrapposizione | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabCambio/ScrollBoxCambio/EditCicchetto | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabCambio/ScrollBoxCambio/EditCicchettoRpm | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditAdvParamInjGasKeyPress` (0x004C7150) |
| PageControl/TabCambio/ScrollBoxCambio/CheckSegnaleGiri | TCheckBox | Tipo segnale giri |  | OnClick→`CheckSegnaleGiriClick` (0x004C5494); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/CheckDisabilitaCommutatore | TCheckBox | Disabilita commutatore / Disable switch / Inabil. interruptor |  | OnClick→`CheckDisabilitaCommutatoreClick` (0x004C1C04); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/CheckValvetronik | TCheckBox | Valvetronik |  | OnClick→`CheckValvetronikClick` (0x004C2B14); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/ComboTipoIniettore | TKeyComboBox |  |  | OnKeyPress→`ComboTipoIniettoreKeyPress` (0x004BA4B4) |
| PageControl/TabCambio/ScrollBoxCambio/EditTipoIniettore | TNumericEdit |  |  | OnKeyPress→`EditTipoIniettoreKeyPress` (0x004BACE0) |
| PageControl/TabCambio/ScrollBoxCambio/ComboTurbo | TKeyComboBox |  |  | OnKeyPress→`ComboTurboKeyPress` (0x004B9C44) |
| PageControl/TabCambio/ScrollBoxCambio/ComboSegnaleGiri | TKeyComboBox |  |  | OnKeyPress→`ComboSegnaleGiriKeyPress` (0x004ACDC8) |
| PageControl/TabCambio/ScrollBoxCambio/ComboCilindri | TKeyComboBox |  |  | OnKeyPress→`ComboCilindriKeyPress` (0x004BA2EC) |
| PageControl/TabCambio/ScrollBoxCambio/ComboAccensione | TKeyComboBox |  |  | OnKeyPress→`ComboAccensioneKeyPress` (0x004BA230) |
| PageControl/TabCambio/ScrollBoxCambio/ComboCambio | TKeyComboBox |  |  | OnKeyPress→`ComboCambioKeyPress` (0x004A8FB8) |
| PageControl/TabCambio/ScrollBoxCambio/EditGiriCambio | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabCambio/ScrollBoxCambio/EditTempMotore | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditTempMotoreKeyPress` (0x004C8F84) |
| PageControl/TabCambio/ScrollBoxCambio/EditTempCambio | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabCambio/ScrollBoxCambio/EditRitMin | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabCambio/ScrollBoxCambio/EditTInjBenzMaxPerCambio | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabCambio/ScrollBoxCambio/ButtonReset | TButton | Azzera centralina e ritorna ai parametri di base / Reset ECU and load default parameters / Redefina a central de comando e retorne aos parâmetros de base |  | OnClick→`ButtonResetClick` (0x004BA188); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/ComboCarburante | TKeyComboBox |  |  | OnKeyPress→`ComboCarburanteKeyPress` (0x004B96DC) |
| PageControl/TabCambio/ScrollBoxCambio/EditCilindrata | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabCambio/ScrollBoxCambio/DrawGridPassaggio | TDrawGrid |  |  | OnDblClick→`DrawGridPassaggioDblClick` (0x004B3128); OnDrawCell→`DrawGridPassaggioDrawCell` (0x004B2D64); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridPassaggioKeyPress` (0x004B3118) |
| PageControl/TabCambio/ScrollBoxCambio/DrawGridPassaggioGas | TDrawGrid |  |  | OnDblClick→`DrawGridPassaggioGasDblClick` (0x004B3A24); OnDrawCell→`DrawGridPassaggioGasDrawCell` (0x004B3660); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridPassaggioGasKeyPress` (0x004B3A14) |
| PageControl/TabCambio/ScrollBoxCambio/ComboTaglia | TKeyComboBox |  |  | OnKeyPress→`ComboTagliaKeyPress` (0x004BCD94) |
| PageControl/TabCambio/ScrollBoxCambio/CheckSpegniBuzzer | TCheckBox | Buzzer a tempo / Time limited buzzer / Time limited buzzer |  | OnClick→`CheckSpegniBuzzerClick` (0x004C1C34); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/CheckPartenzaGasCaldo | TCheckBox | Partenza a gas a motore caldo / Start on gas with hot engine / Partida gás com motor aquecido |  | OnClick→`CheckPartenzaGasCaldoClick` (0x004C4FB8); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/EditTempGasCaldo | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabCambio/ScrollBoxCambio/CheckStartAndStop | TCheckBox | Start && Stop |  | OnClick→`CheckStartAndStopClick` (0x004C2BD8); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/EditSogliaGiri | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditSogliaGiriKeyPress` (0x004C58DC) |
| PageControl/TabCambio/ScrollBoxCambio/EditIstantaneoValue | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabCambio/ScrollBoxCambio/CheckFlexFuel | TCheckBox | Flex fuel |  | OnClick→`CheckFlexFuelClick` (0x004C2C9C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/EditCicchettoInj | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditAdvParamInjGasKeyPress` (0x004C7150) |
| PageControl/TabCambio/ScrollBoxCambio/EditCicchettoNum | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditAdvParamInjGasKeyPress` (0x004C7150) |
| PageControl/TabCambio/ScrollBoxCambio/CheckGiriAlberoCamme | TCheckBox | albero a camme / camshaft / camshaft |  | OnClick→`CheckGiriAlberoCammeClick` (0x004C850C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/EditNumDenti | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabCambio/ScrollBoxCambio/PanelSequenceAcquisition/EditRpmTachimetro | TNumericEdit |  |  | OnKeyPress→`EditRpmTachimetroKeyPress` (0x004A647C) |
| PageControl/TabCambio/ScrollBoxCambio/PanelSequenceAcquisition/ButtonSequenceAcquisition | TButton | Acquisisci sequenza / Sequence acquisition / Sequence acquisition |  | OnClick→`ButtonSequenceAcquisitionClick` (0x004A62A8) |
| PageControl/TabCambio/ScrollBoxCambio/PanelSequenceAcquisition/CheckInjAcquisition | TCheckBox | Sequenza di iniezione manuale / Manual injection sequence / Manual injection sequence |  | OnClick→`CheckInjAcquisitionClick` (0x004A61AC); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabCambio/ScrollBoxCambio/ComboFullGroup | TKeyComboBox |  |  | OnKeyPress→`ComboFullGroupKeyPress` (0x004C6830) |
| PageControl/TabCambio/ScrollBoxCambio/ButtonSwitchSettings | TButton | Switch settings |  | OnClick→`ButtonSwitchSettingsClick` (0x004C90E0) |
| PageControl/TabCambio/ScrollBoxCambio/PanelCambio/CheckCambioConSovrapp | TCheckBox | Cambio con sovrapposizione / Change-over with overlapping / Cambio con sovrapposizione |  | OnClick→`CheckCambioConSovrappClick` (0x004C8B04); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/PanelProgressioni/CheckProgressioni | TCheckBox | Abilita progressioni / Enable progressions / Enable progressions |  | OnClick→`CheckProgressioniClick` (0x004CA544); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabCambio/ScrollBoxCambio/PanelProgressioni/DrawGridProgressioni | TDrawGrid |  |  | OnDblClick→`DrawGridProgressioniDblClick` (0x004CA9A0); OnDrawCell→`DrawGridProgressioniDrawCell` (0x004CA74C); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridProgressioniKeyPress` (0x004CA990) |
| PageControl/TabCambio/ScrollBoxCambio/CheckGiriRuotaFonica | TCheckBox | ruota fonica / flyweel / flyweel |  | OnClick→`CheckGiriRuotaFonicaClick` (0x004C86D4); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/chkRitardoPassaggioInMinuti | TCheckBox | Ritardo passaggio in minuti |  | OnClick→`chkRitardoPassaggioInMinutiClick` (0x004CB300); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCambio/ScrollBoxCambio/CheckBoxEnableExtLedKLine | TCheckBox | Enable external LED on K-Line |  | OnClick→`CheckBoxEnableExtLedKLineClick` (0x004CB328); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabLambda | TTabSheet | Lambda |  | OnShow→`TabLambdaShow` (0x004A6770) |
| PageControl/TabLambda/LabelTipoLambda | TLabel | Tipo di sonda Lambda / Type of Lambda probe / Tipo de sonda Lambda |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabLambda/LabelNumeroSonde | TLabel | Numero sonde |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabLambda/LabelCorrBancata2 | TLabel | Correttore 2° bancata / Fuel trim bank 2 / Corretor bancada 2 |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabLambda/LabelTipoSonda1 | TLabel | Sonda Lambda 1 (filo viola) / Oxygen Sensor 1 (purple wire) / Sonda Lambda 1 (fio roxo) |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabLambda/LabelTipoSonda2 | TLabel | Sonda Lambda 2 (filo viola/nero) / Oxygen Sensor 2 (purple/black wire) / Sonda Lambda 2 (fio roxo/preto) |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabLambda/EditRifLambda | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabLambda/EditRitardoLbd | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabLambda/EditSondaFredda | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabLambda/EditRifSupFredda | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabLambda/EditRifInfFredda | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabLambda/EditRifSupCalda | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabLambda/EditRifInfCalda | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabLambda/ComboTipoLambda | TKeyComboBox |  |  | OnKeyPress→`ComboTipoLambdaKeyPress` (0x004A6674) |
| PageControl/TabLambda/ComboNumeroSonde | TKeyComboBox |  |  | OnKeyPress→`ComboNumeroSondeKeyPress` (0x004A9664) |
| PageControl/TabLambda/EditCorrBancata2 | TNumericEdit |  |  | OnKeyPress→`EditCorrBancata2KeyPress` (0x004C2CCC) |
| PageControl/TabLambda/ComboSonda1 | TKeyComboBox |  |  | OnKeyPress→`ComboSonda1KeyPress` (0x004C2D94) |
| PageControl/TabLambda/ComboSonda2 | TKeyComboBox |  |  | OnKeyPress→`ComboSonda1KeyPress` (0x004C2D94) |
| PageControl/TabLambda/PanelFlashLube/LabelFlashLube0 | TLabel | Soglia livello di riserva |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabLambda/PanelFlashLube/LabelFlashLube1 | TLabel | Tempo di filtro |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabLambda/PanelFlashLube/LabelFlashLube2 | TLabel | Tempo basso livello per retropassaggio |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabLambda/PanelFlashLube/LabelFlashLube3 | TLabel | Tempo alto livello per reset diagnosi  |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabLambda/PanelFlashLube/CheckFlashLube | TCheckBox | Abilita diagnosi livello lubrificante  |  | OnClick→`CheckFlashLubeClick` (0x004C88AC); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabLambda/PanelFlashLube/EditFlashLube0 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditFlashLubeKeyPress` (0x004C8948) |
| PageControl/TabLambda/PanelFlashLube/EditFlashLube1 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditFlashLubeKeyPress` (0x004C8948) |
| PageControl/TabLambda/PanelFlashLube/EditFlashLube2 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditFlashLubeKeyPress` (0x004C8948) |
| PageControl/TabLambda/PanelFlashLube/EditFlashLube3 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditFlashLubeKeyPress` (0x004C8948) |
| PageControl/TabEmissioni | TTabSheet | Emissioni / Emissions / Emissões |  | OnShow→`TabEmissioniShow` (0x004A8964) |
| PageControl/TabEmissioni/LabelSmagrRientroCutoff | TLabel | Smagrimento rientro da cutoff / Exit from cutoff leaning / Abaixamento saída da cutoff |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabEmissioni/LabelNumeroIniettateSmagr | TLabel | Numero iniettate in smagrimento / Leaning number of injections / Abaixamento número de injeções |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabEmissioni/DrawGridEmissioniMultipoint | TDrawGrid |  |  | OnDblClick→`DrawGridEmissioniMultipointDblClick` (0x004C33E0); OnDrawCell→`DrawGridEmissioniMultipointDrawCell` (0x004C2E3C); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridEmissioniMultipointKeyPress` (0x004C33D0) |
| PageControl/TabEmissioni/EditLivEmulAlto | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabEmissioni/EditLivEmulBasso | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabEmissioni/EditRitardoGiriEmulazione | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabEmissioni/CheckInibisciEmulazione | TCheckBox | Disattiva emulazione per giri bassi / Disable emulation at low rpm / Desativa a emulação de rotações baixas |  | OnClick→`CheckInibisciEmulazioneClick` (0x004B9280); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabEmissioni/EditSmagrRientroCutoff | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabEmissioni/EditNumeroIniettateSmagr | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabEmissioni/EditCorrIntInf | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditCorrIntInfKeyPress` (0x004B91BC) |
| PageControl/TabEmissioni/EditCorrIntSup | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditCorrIntInfKeyPress` (0x004B91BC) |
| PageControl/TabEmissioni/DrawGridEmissioni | TDrawGrid |  |  | OnDblClick→`DrawGridEmissioniDblClick` (0x004B4530); OnDrawCell→`DrawGridEmissioniDrawCell` (0x004B3F68); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridEmissioniKeyPress` (0x004B4520) |
| PageControl/TabEmissioni/DrawGridAnticipoMultipoint | TDrawGrid |  |  | OnDblClick→`DrawGridAnticipoMultipointDblClick` (0x004C3C38); OnDrawCell→`DrawGridAnticipoMultipointDrawCell` (0x004C3944); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridAnticipoMultipointKeyPress` (0x004C3C28) |
| PageControl/TabEmissioni/EditLivEmulStabilizzato | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabEmissioni/EditLivEmulCutoff | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSensore | TTabSheet | Livello GAS |  | OnShow→`TabSensoreShow` (0x004AA5BC) |
| PageControl/TabSensore/ScrollBoxLivello/LabelSensoreLivello | TLabel | Tipo di sensore livello GAS / Type of GAS level sensor / Tipo de sensor de nível do gás |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/LabelRitornoBenzina | TLabel | Tempo di bassa pressione per il ritorno / Low pressure time for change back / Tempo de baixa pressão para retornar |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/LabelPressPass | TLabel | Pressioni per il retropassaggio benzina / Pressure for change back to petrol / Pressões para a reversão gasolina |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/LabelBassaPressioneMp | TLabel | Valore di bassa pressione per il ritorno / Low pressure value for change back / Valor da baixa pressão para a reversão |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/LabelAnticipoEV1 | TLabel | Anticipo apertura EV1 / EV1 opening advance / EV1 opening advance |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/LabelAnticipoEV2 | TLabel | Anticipo apertura EV2 / EV2 opening advance / EV2 opening advance |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/ComboSensore | TKeyComboBox |  |  | OnKeyPress→`ComboSensoreKeyPress` (0x004A8C60) |
| PageControl/TabSensore/ScrollBoxLivello/GroupRifSensore/LabelSensore1 | TLabel | Riserva / Reserve / Reserva |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/GroupRifSensore/LabelSensore2 | TLabel | 1/4 |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/GroupRifSensore/LabelSensore3 | TLabel | 2/4 |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/GroupRifSensore/LabelSensore4 | TLabel | 3/4 |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/GroupRifSensore/EditLivello1 | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSensore/ScrollBoxLivello/GroupRifSensore/EditLivello2 | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSensore/ScrollBoxLivello/GroupRifSensore/EditLivello3 | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSensore/ScrollBoxLivello/GroupRifSensore/EditLivello4 | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSensore/ScrollBoxLivello/GroupRifSensore/ButtonAccetta | TButton | &Conferma |  | OnClick→`ButtonAccettaClick` (0x004A8D74); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/GroupRifSensore/ButtonMinLevel | TButton | Set min level |  | OnClick→`ButtonMinLevelClick` (0x004C598C); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/GroupRifSensore/ButtonMaxLevel | TButton | Set max level |  | OnClick→`ButtonMaxLevelClick` (0x004C5ADC); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/GroupRifSensore/ButtonDoLevel | TButton | Find levels |  | OnClick→`ButtonDoLevelClick` (0x004C5C30); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/CheckRitornoBenzina | TCheckBox | &Ritorno automatico a benzina / &Automatic change-back to petrol / &Automatic change-back to petrol |  | OnClick→`CheckRitornoBenzinaClick` (0x004A9484); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/EditRitornoBenzina | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSensore/ScrollBoxLivello/CheckPassPRail | TCheckBox | Ritorno automatico a benzina con la pressione del rail / Automatic return to petrol with rail pressure / Reversão automática a gasolina com a pressão do rail |  | OnClick→`CheckPassPRailClick` (0x004BCB10); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/CheckBassaPressione | TCheckBox | Ritorno automatico con bassa pressione impostabile / Automatic change back with low pressure setting possible / Reversão automática por baixa pressão impostável |  | OnClick→`CheckBassaPressioneClick` (0x004BCA04); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/EditBassaPressione | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSensore/ScrollBoxLivello/CheckElettrovalvole | TCheckBox | Elettrovalvole gas aperte con anticipo / Gas elettrovalves opened in advance / Gas elettrovalves opened in advance |  | OnClick→`CheckElettrovalvoleClick` (0x004C5048); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/CheckAlimentazione | TCheckBox | Utilizzo filo EV2 per STAP / Use of wire EV2 for STAP / Use of wire EV2 for STAP |  | OnClick→`CheckAlimentazioneClick` (0x004C6780); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/DrawGridPressPass | TDrawGrid |  |  | OnDblClick→`DrawGridPressPassDblClick` (0x004B6EF4); OnDrawCell→`DrawGridPressPassDrawCell` (0x004B6B1C); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridPressPassKeyPress` (0x004B6F58) |
| PageControl/TabSensore/ScrollBoxLivello/EditBassaPressioneMp | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditBassaPressioneMpKeyPress` (0x004C526C) |
| PageControl/TabSensore/ScrollBoxLivello/EditAnticipoEV1 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSensore/ScrollBoxLivello/EditAnticipoEV2 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSensore/ScrollBoxLivello/CheckTaglioPompaBenzina | TCheckBox | Taglio pompa benzina / Petrol pump cutting / Petrol pump cutting |  | OnClick→`CheckTaglioPompaBenzinaClick` (0x004C8B68); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabSensore/ScrollBoxLivello/EditRpmAttivPompa | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSensore/ScrollBoxLivello/EditRpmAnticipoPompa | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSensore/ScrollBoxLivello/EditTempoAttivPompa | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSensore/ScrollBoxLivello/EditPeriodoRiattivPompa | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSensore/ScrollBoxLivello/CheckEnabledDhlpDiagnosis | TCheckBox | Abilita diagnosi perdite gas / Enable gas leaks diagnosis / Enable gas leaks diagnosis |  | OnClick→`CheckEnabledDhlpDiagnosisClick` (0x004C975C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabSensore/ScrollBoxLivello/CheckAttivaPompaInRiserva | TCheckBox | Abilita riattivazione pompa in riserva / Enable pump reactivation in reserve / Enable pump reactivation in reserve |  | OnClick→`CheckAttivaPompaInRiservaClick` (0x004CB030); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabSensore/ScrollBoxLivello/CheckRiconoscimentoPieno | TCheckBox | Riconoscimento pieno / Recognition of full / Recognition of full |  | OnClick→`CheckRiconoscimentoPienoClick` (0x004CB12C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabSensore/ScrollBoxLivello/CheckBoxEV2NotConnected | TCheckBox | EV2 non collegata / EV2 not connected / EV2 not connected |  | OnClick→`CheckBoxEV2NotConnectedClick` (0x004C6710); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabPressione | TTabSheet | Pressione / Pressure / Pressão |  | OnShow→`TabPressioneShow` (0x004AE788) |
| PageControl/TabPressione/LabelTipoPressioneMisurata | TLabel | Lettura della pressione / Pressure reading / Leitura da pressão |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabPressione/LabelTipoMisuratore | TLabel | Sensore di pressione gas / Gas pressure sensor / Sensor pressão gás |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabPressione/ComboPressioneMisurata | TKeyComboBox |  |  | OnKeyPress→`ComboPressioneKeyPress` (0x004AE420) |
| PageControl/TabPressione/ComboTipoMisuratore | TKeyComboBox |  |  | OnKeyPress→`ComboTipoMisuratoreKeyPress` (0x004BEC34) |
| PageControl/TabPressione/ComboMisuratorePressione | TKeyComboBox |  |  | OnKeyPress→`ComboPressioneKeyPress` (0x004AE420) |
| PageControl/TabPressione/GroupMisuratorePressione/EditPendenzaDiff | TNumericEdit |  |  | OnKeyPress→`EditOffsetKeyPress` (0x004AF360) |
| PageControl/TabPressione/GroupMisuratorePressione/EditOffsetDiff | TNumericEdit |  |  | OnKeyPress→`EditOffsetKeyPress` (0x004AF360) |
| PageControl/TabPressione/GroupParametri/ButtonRicercaParametri | TButton | Avvia ricerca guidata parametri map esterno / Start guided search for external map parameters / Inicie a procura guiada dos parâmetros do mapa externo |  | OnClick→`ButtonRicercaParametriClick` (0x004AF3A8); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabPressione/GroupParametri/EditPendenza | TNumericEdit |  |  | OnKeyPress→`EditOffsetKeyPress` (0x004AF360) |
| PageControl/TabPressione/GroupParametri/EditOffset | TNumericEdit |  |  | OnKeyPress→`EditOffsetKeyPress` (0x004AF360) |
| PageControl/TabPressione/GroupParametri/CheckMapConfigurato | TCheckBox | Map configurato correttamente / Map correctly configured / Map configurado corretamente |  | OnClick→`CheckMapConfiguratoClick` (0x004BCB40); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabPressione/EditSogliaPress | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabPressione/EditTempAcqua | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditTempAcquaKeyPress` (0x004BCB78) |
| PageControl/TabPressione/EditSogliaMap | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditTempAcquaKeyPress` (0x004BCB78) |
| PageControl/TabPressione/EditPressRid | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditTempAcquaKeyPress` (0x004BCB78) |
| PageControl/TabTemp | TTabSheet | Temperatura / Temperature / Temperatura |  | OnShow→`TabTempShow` (0x004B92C8) |
| PageControl/TabTemp/LabelParametriTemp | TLabel | Parametri per il sensore di temperatura motore della vettura / Parameter for the water temperature sensor of the car / Parameter for the water temperature sensor of the car |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabTemp/LabelRPullUp | TLabel | R pull-up |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabTemp/LabelDeltaT | TLabel | Delta T |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabTemp/LabelTMin | TLabel | T min |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabTemp/LabelTMax | TLabel | T max |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabTemp/LabelCaratteristicaTemp | TLabel | Caratteristica sensore di temperatura motore della vettura / Characteristic of engine temperature sensor of the car / Characteristic of engine temperature sensor of the car |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabTemp/LabelTempGas | TLabel | Tipo di sensore temperatura gas / Gas temperature sensor type / Tipo de sensor da temp. do gás |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabTemp/CheckTempMotore | TCheckBox | Utilizza sensore di temperatura motore della vettura / Use vehicle's engine temperature sensor / Utiliza sensor de temperatura motor do veículo |  | OnClick→`CheckTempMotoreClick` (0x004B8E88); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabTemp/CheckTempAcqua | TCheckBox | Abilita cambio con temperatura acqua / Enable change-over with water temperature / Habilita mudança com temperatura da água |  | OnClick→`CheckTempAcquaClick` (0x004B8B30); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabTemp/CheckTempGas | TCheckBox | Abilita cambio con temperatura gas / Enable change-over with gas temperature / Habilita mudança com temperatura do gás |  | OnClick→`CheckTempRiduttoreClick` (0x004B8E10); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabTemp/EditDeltaT | TNumericEdit |  |  | OnEnter→`EditRPullUpEnter` (0x004B86C4); OnExit→`EditRPullUpExit` (0x004B88C0) |
| PageControl/TabTemp/EditRPullUp | TNumericEdit |  |  | OnEnter→`EditRPullUpEnter` (0x004B86C4); OnExit→`EditRPullUpExit` (0x004B88C0) |
| PageControl/TabTemp/EditTMax | TNumericEdit |  |  | OnEnter→`EditRPullUpEnter` (0x004B86C4); OnExit→`EditRPullUpExit` (0x004B88C0) |
| PageControl/TabTemp/EditTMin | TNumericEdit |  |  | OnEnter→`EditRPullUpEnter` (0x004B86C4); OnExit→`EditRPullUpExit` (0x004B88C0) |
| PageControl/TabTemp/ComboTempRiduttore | TKeyComboBox |  |  | OnKeyPress→`ComboTempRiduttoreKeyPress` (0x004B9494) |
| PageControl/TabTemp/ButtonNuoviParametri | TButton | &Modifica parametri sensore temperatura motore |  | OnClick→`ButtonNuoviParametriClick` (0x004B9000); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabTemp/DrawGridTemp | TDrawGrid |  |  | OnDblClick→`DrawGridTempDblClick` (0x004B81F4); OnDrawCell→`DrawGridTempDrawCell` (0x004B7874); OnEnter→`DrawGridTempEnter` (0x004B868C); OnExit→`DrawGridTempExit` (0x004B86A4); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridTempKeyPress` (0x004B80B0) |
| PageControl/TabTemp/CheckFlashLubeSuTempAcqua | TCheckBox | Diagnosis on the water temperature sensor for VP system |  | OnClick→`CheckFlashLubeSuTempAcquaClick` (0x004B8C98) |
| PageControl/TabKCoefficienti | TTabSheet | Coefficienti k |  | OnShow→`TabKCoefficientiShow` (0x004B0EA0) |
| PageControl/TabKCoefficienti/DrawGridK | TDrawGrid |  |  | OnDblClick→`DrawGridKDblClick` (0x004B1940); OnDrawCell→`DrawGridKDrawCell` (0x004B12EC); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridKKeyPress` (0x004B18F4) |
| PageControl/TabKCoefficienti/ButtonTaratura | TButton | &Inizia taratura / &Start calibration / &Iniciar calibrag. |  | OnClick→`ButtonTaraturaClick` (0x004ACFC0); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabKCoefficienti/ButtonModificaRifTar | TButton | &Mod. rif. mappa / &Modify map refs. / &Modificar ref. mapa |  | OnClick→`ButtonModificaRifTarClick` (0x004B10C8); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabKCoefficienti/ButtonCommutatore | TButton | &Commutatore / &Switch / &Comutador |  | OnClick→`ButtonCommutatoreClick` (0x004B1178); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabKCoefficienti/PageControlK/TabKOpenLoop/DrawGridOpenLoop | TDrawGrid |  |  | OnDblClick→`DrawGridOpenLoopDblClick` (0x004B29E0); OnDrawCell→`DrawGridOpenLoopDrawCell` (0x004B25B4); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridOpenLoopKeyPress` (0x004B29D0) |
| PageControl/TabKCoefficienti/PageControlK/TabKFactor | TTabSheet | K factor |  | OnShow→`TabKFactorShow` (0x004C49FC) |
| PageControl/TabKCoefficienti/PageControlK/TabKFactor/LabelKFactor | TLabel | K factor |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabKCoefficienti/PageControlK/TabKFactor/ComboK | TKeyComboBox |  |  | OnKeyPress→`ComboKKeyPress` (0x004C4880) |
| PageControl/TabKCoefficienti/PageControlK/TabKFactor/EditKMappaNeutro | TNumericEdit |  |  | OnKeyPress→`EditKMappaNeutroKeyPress` (0x004C499C) |
| PageControl/TabKCoefficienti/PageControlK/TabKFactor/EditTInj1 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabKCoefficienti/PageControlK/TabKFactor/EditK1 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabKCoefficienti/PageControlK/TabKFactor/EditTInj2 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabKCoefficienti/PageControlK/TabKFactor/EditK2 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabKCoefficienti/PageControlK/TabKTempRid/DrawGridTempRid | TDrawGrid |  |  | OnDblClick→`DrawGridTempRidDblClick` (0x004B4DAC); OnDrawCell→`DrawGridTempRidDrawCell` (0x004B49E8); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridTempRidKeyPress` (0x004B4D9C) |
| PageControl/TabKCoefficienti/PageControlK/TabKTempMot/DrawGridTempMot | TDrawGrid |  |  | OnDblClick→`DrawGridTempMotDblClick` (0x004B54F8); OnDrawCell→`DrawGridTempMotDrawCell` (0x004B5134); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridTempMotKeyPress` (0x004B54E8) |
| PageControl/TabKCoefficienti/PageControlK/TabKPressDiff/DrawGridPressDiff | TDrawGrid |  |  | OnDblClick→`DrawGridPressDiffDblClick` (0x004B5B68); OnDrawCell→`DrawGridPressDiffDrawCell` (0x004B5880); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridPressDiffKeyPress` (0x004B5B58) |
| PageControl/TabKCoefficienti/PageControlK/TabPressAss/DrawGridPressAss | TDrawGrid |  |  | OnDblClick→`DrawGridPressAssDblClick` (0x004B6178); OnDrawCell→`DrawGridPressAssDrawCell` (0x004B5E90); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridPressAssKeyPress` (0x004B6168) |
| PageControl/TabKCoefficienti/PageControlK/TabKTempi | TTabSheet | Tempi / Times / Tempos |  | OnShow→`TabKTempiShow` (0x004B1254) |
| PageControl/TabKCoefficienti/PageControlK/TabKTempi/LabelTempoMortoBenz | TLabel | T morto iniettore benzina / Petrol injector dead time / Tempo morto do injetor de gasolina |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabKCoefficienti/PageControlK/TabKTempi/LabelTempoMortoGas | TLabel | T morto iniettore gas / Gas injector dead time / Tempo morto do injetor de gás |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabKCoefficienti/PageControlK/TabKTempi/LabelExtraIniettate | TLabel | extrainiettate / extra-injections / extra-injeção |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabKCoefficienti/PageControlK/TabKTempi/EditTempoMortoBenz | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditTempoMortoBenzKeyPress` (0x004B11B0) |
| PageControl/TabKCoefficienti/PageControlK/TabKTempi/EditTempoMortoGas | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditTempoMortoBenzKeyPress` (0x004B11B0) |
| PageControl/TabKCoefficienti/PageControlK/TabKTempi/EditTempoBenzExtra | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabKCoefficienti/PageControlK/TabKTempi/EditTempoGasExtra | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabKCoefficienti/PageControlK/TabKTempiEx/DrawGridTempiLr | TDrawGrid |  |  | OnDblClick→`DrawGridTempiLrDblClick` (0x004BE5A8); OnDrawCell→`DrawGridTempiLrDrawCell` (0x004BE278); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridTempiLrKeyPress` (0x004BE598) |
| PageControl/TabKCoefficienti/PageControlK/TabKVarie | TTabSheet | Smagrimento / Tip IN / Várias |  | OnShow→`TabKVarieShow` (0x004B94C8) |
| PageControl/TabKCoefficienti/PageControlK/TabKVarie/LabelSogliaFS2 | TLabel | Soglia t inj per intervento filtro / T inj. threshold for filtering / Limiar tempo de injeção para intervenção do filtro |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabKCoefficienti/PageControlK/TabKVarie/LabelGiriKFiltro | TLabel | Giri per smagrimento (RPM) / Tours for leaning (RPM) / Tours for leaning (RPM) |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabKCoefficienti/PageControlK/TabKVarie/EditFiltro | TNumericEdit |  |  | OnEnter→`EditFiltroEnter` (0x004BDFD4); OnExit→`EditFiltroExit` (0x004BE208); OnKeyPress→`EditFiltroKeyPress` (0x004B9644) |
| PageControl/TabKCoefficienti/PageControlK/TabKVarie/EditSogliaFS2 | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabKCoefficienti/PageControlK/TabKVarie/EditKFiltro | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditKFiltroKeyPress` (0x004B95C0) |
| PageControl/TabKCoefficienti/PageControlK/TabKVarie/EditGiriKFiltro | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditGiriKFiltroKeyPress` (0x004BEB24) |
| PageControl/TabKCoefficienti/PageControlK/TabKVarie/EditKFiltroGiriAlti | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabKCoefficienti/PageControlK/TabKVarie/EditGiriAltiKFiltro | TNumericEdit |  |  | OnEnter→`EditGiriKFiltroEnter` (0x004BE90C); OnExit→`EditGiriKFiltroExit` (0x004BEABC); OnKeyPress→`EditGiriKFiltroKeyPress` (0x004BEB24) |
| PageControl/TabKCoefficienti/RadioMapPetrol | TRadioButton | petrol |  | OnClick→`RadioMapClick` (0x004B23A0) |
| PageControl/TabKCoefficienti/RadioMapAlcol | TRadioButton | alcohol |  | OnClick→`RadioMapClick` (0x004B23A0) |
| PageControl/TabKCoefficienti/RadioMapAdatta | TRadioButton | target OBD |  | OnClick→`RadioMapClick` (0x004B23A0) |
| PageControl/TabAdvanced | TTabSheet | Advanced |  | OnShow→`TabAdvancedShow` (0x004C9BBC) |
| PageControl/TabAdvanced/CheckContributi | TCheckBox | Enable petrol addition map |  | OnClick→`CheckContributiClick` (0x004C9C1C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdvanced/DrawGridContributi | TDrawGrid |  |  | OnDblClick→`DrawGridContributiDblClick` (0x004CA288); OnDrawCell→`DrawGridContributiDrawCell` (0x004C9DF0); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridContributiKeyPress` (0x004CA278) |
| PageControl/TabCalibration | TTabSheet | Calibra inj. / Inj calibration / Inj calibration |  | OnShow→`TabCalibrationShow` (0x004C7904) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOffF | TImage |  |  | OnClick→`ImageInjOffClick` (0x004C81B0) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOffG | TImage |  |  | OnClick→`ImageInjOffClick` (0x004C81B0) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOnG | TImage |  |  | OnClick→`ImageInjOnClick` (0x004C80A8) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOffE | TImage |  |  | OnClick→`ImageInjOffClick` (0x004C81B0) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOnE | TImage |  |  | OnClick→`ImageInjOnClick` (0x004C80A8) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOnF | TImage |  |  | OnClick→`ImageInjOnClick` (0x004C80A8) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOffL | TImage |  |  | OnClick→`ImageInjOffClick` (0x004C81B0) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOnL | TImage |  |  | OnClick→`ImageInjOnClick` (0x004C80A8) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOnH | TImage |  |  | OnClick→`ImageInjOnClick` (0x004C80A8) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOffH | TImage |  |  | OnClick→`ImageInjOffClick` (0x004C81B0) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOnI | TImage |  |  | OnClick→`ImageInjOnClick` (0x004C80A8) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOnC | TImage |  |  | OnClick→`ImageInjOnClick` (0x004C80A8) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOnD | TImage |  |  | OnClick→`ImageInjOnClick` (0x004C80A8) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOffC | TImage |  |  | OnClick→`ImageInjOffClick` (0x004C81B0) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOffB | TImage |  |  | OnClick→`ImageInjOffClick` (0x004C81B0) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOffD | TImage |  |  | OnClick→`ImageInjOffClick` (0x004C81B0) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOnB | TImage |  |  | OnClick→`ImageInjOnClick` (0x004C80A8) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOnA | TImage |  |  | OnClick→`ImageInjOnClick` (0x004C80A8) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOffA | TImage |  |  | OnClick→`ImageInjOffClick` (0x004C81B0) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/LabelEsclusioneGas | TLabel | Esclusione iniettori gas / Gas injectors cut out / Deslig. dos injectores gás |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/ImageInjOffI | TImage |  |  | OnClick→`ImageInjOffClick` (0x004C81B0) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/PanelBancata1/LabelCalibCorrettori1 | TLabel | Correttori Bancata 2 |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabCalibration/PanelEsclusioneGINJ/PanelBancata0/LabelCalibCorrettori0 | TLabel | Correttori Bancata 1 |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione | TTabSheet | Iniettori / Injectors / Injectores |  | OnShow→`TabIniezioneShow` (0x004AB8BC) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelSfasatura | TLabel | Anticipo fasatura iniettori gas / Gas injectors timing advance / Antecipação sincronização injetores gás |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelArricchimento | TLabel | Arricchimento in accelerazione / Enrichment in acceleration / Enriquecimento de comb. em aceler. |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelTAperturaInj | TLabel | Tempo minimo apertura iniettore gas / Gas injector minimum opening time / Tempo mínimo de abertura do injetor |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelAperturaExtra | TLabel | Tempo minimo apertura extrainiettate gas / Gas extra-injection minimum opening time / Tempo mínimo de abertura extra-injeção |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelGiriCutoff | TLabel | Giri per pilotaggio gas in cutoff / RPMs for gas injectors driving in cutoff / Rotações por pilotagem gás em cutoff |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelTInjCutoff | TLabel | Tempo di attivazione inj gas in cutoff / Gas injection time during cutoff / Tempo de ativação injeção gás em cutoff |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelSmagrimento | TLabel | Smagrimento su Mazda™ / Weakening on Mazda™ / Empobrecimento nas Mazda™ |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelPiccoRiscaldamento | TLabel | Tempo di picco / Peak time / Peak time |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelPeriodoRiscaldamento | TLabel | Periodo di attivazione / Activation period / Activation period |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelMantRiscaldamento | TLabel | Tempo di mantenimento / Hold time / Hold time |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelPreheatPInjTimeTh | TLabel | Tempo di inj. benzina min / Min petrol injection time / Min petrol injection time |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelPreheatPGasTh | TLabel | Pressione gas max / Max gas pressure / Max gas pressure |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelPreheatTempGasTh | TLabel | Temperatura gas / Gas temperature / Temperatura do gás |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelMaxCorrentePrimoCiclo | TLabel | Num. cicli max corrente 1° cambio / Num. cycles max current 1st change-over / Num. cycles max current 1st change-over |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelMaxCorrenteCiclo | TLabel | Num. cicli max corrente / Num. cycles max current / Num. cycles max current |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelPressioneCutoff | TLabel | Pressione di attivazione inj gas in cutoff / Pressure for inj gas activation in cutoff / Pressure for inj gas activation in cutoff |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/LabelTBenzMaxCutoff | TLabel | T. benz. max per scarico press. in cutoff / T. petrol max for press. discharge in cutoff / T. petrol max for press. discharge in cutoff |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditTMaxExtraInjBenz | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditChiusuraInj | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditAperturaInjKeyPress` (0x004ACF68) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/ComboSfasatura | TKeyComboBox |  |  | OnKeyPress→`ComboSfasaturaKeyPress` (0x004BD748) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/CheckboxPreheatAsyncMode | TCheckBox | Riscaldamento iniettori asincrono / Asynchronous heating injectors / Asynchronous heating injectors |  | OnClick→`CheckboxPreheatAsyncModeClick` (0x004C72D0); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditArricchimento | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditGiriCutoff | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditCutoffKeyPress` (0x004C82B8) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditTInjSup22 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditTInjSup22KeyPress` (0x004C3E44) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditTMaxInjBenz | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/CheckTagliaExtraIniettate | TCheckBox | Taglio extrainiettate / Extra-injections cutting / Corte extra-injecção |  | OnClick→`CheckTagliaExtraIniettateClick` (0x004C1BD4); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditAperturaExtra | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditAperturaInj | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditAperturaInjKeyPress` (0x004ACF68) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditTInjCutoff | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditCutoffKeyPress` (0x004C82B8) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditSmagrimento | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/CheckIniezioneContinua | TCheckBox | Abilitazione retropassaggio benzina per iniezione continua / Enable switch back to Petrol for continuous injection / Habilitaçãoretropassagem gasolina por injeção contínua |  | OnClick→`CheckIniezioneContinuaClick` (0x004BC858); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditIniezioneContinua | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/CheckSequenzaInj | TCheckBox | Abilitazione manuale sequenza iniezione / Enable manual injection sequence / Habilitação manual seqüência injeção |  | OnClick→`CheckSequenzaInjClick` (0x004BC008); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/DrawGridSequenzaInjBenz | TDrawGrid |  |  | OnDrawCell→`DrawGridSequenzaInjBenzDrawCell` (0x004BBB88); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnSelectCell→`DrawGridSequenzaInjBenzSelectCell` (0x004BBF2C) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/ButtonSequenzaInj | TButton | Acquisisci sequenza iniezione benzina / Get petrol injection sequence / Adquirir seqüência injeção gasolina |  | OnClick→`ButtonSequenzaInjClick` (0x004BC2DC) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/DrawGridSequenzaInj | TDrawGrid |  |  | OnDrawCell→`DrawGridSequenzaInjDrawCell` (0x004BB664); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnSelectCell→`DrawGridSequenzaInjSelectCell` (0x004BBAB0) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/ComboSequenzaInj | TComboBox |  |  | OnChange→`ComboSequenzaInjChange` (0x004BBF3C); OnKeyDown→`ComboSequenzaInjKeyDown` (0x004BBF9C) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/ButtonModificaInj | TButton | Modifica sequenza iniezione gas / Modify gas injection sequence / Modificação sequência injecção gás |  | OnClick→`ButtonModificaInjClick` (0x004BC47C) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/CheckRiscaldamento | TCheckBox | Riscaldamento iniettori / Heating injectors / Heating injectors |  | OnClick→`CheckRiscaldamentoClick` (0x004C6A64); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditPiccoRiscaldamento | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRiscaldamentoKeyPress` (0x004C7478) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditPeriodoRiscaldamento | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRiscaldamentoKeyPress` (0x004C7478) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditPreheatTempMotoreTh | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/CheckboxPreheatOnlyAfterSleepMode | TCheckBox | Riscaldamento iniettori solo dopo sleep mode / Heating injectors only after sleep mode / Heating injectors only after sleep mode |  | OnClick→`CheckboxPreheatOnlyAfterSleepModeClick` (0x004C724C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditMantRiscaldamento | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRiscaldamentoKeyPress` (0x004C7478) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditPreheatPInjTimeTh | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRiscaldamentoKeyPress` (0x004C7478) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditPreheatPGasTh | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRiscaldamentoKeyPress` (0x004C7478) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditPreheatTempGasTh | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRiscaldamentoKeyPress` (0x004C7478) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/CheckIncrementoMaxCorrente | TCheckBox | Incrementa max corrente al cambio gas / Increase max current in gas change-over / Increase max current in gas change-over |  | OnClick→`CheckIncrementoMaxCorrenteClick` (0x004C721C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditMaxCorrenteCiclo | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditAdvParamInjGasKeyPress` (0x004C7150) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditMaxCorrentePrimoCiclo | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditAdvParamInjGasKeyPress` (0x004C7150) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditPressioneCutoff | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditCutoffKeyPress` (0x004C82B8) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/CheckAntiStallo | TCheckBox | Abilitazione anti-stallo / Enable un-stalling strategy / Enable un-stalling strategy |  | OnClick→`CheckAntiStalloClick` (0x004C982C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditAntiStallo4 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditAntiStalloKeyPress` (0x004C97D8) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditAntiStallo3 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditAntiStalloKeyPress` (0x004C97D8) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditAntiStallo2 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditAntiStalloKeyPress` (0x004C97D8) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditAntiStallo1 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditAntiStalloKeyPress` (0x004C97D8) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditAntiStallo0 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditAntiStalloKeyPress` (0x004C97D8) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditPreheatSyncInjNum | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditPreheatSyncInjNumKeyPress` (0x004C78A4) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditLimiteRitardoAttivWarmUp | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditPreheatSyncInjNumKeyPress` (0x004C78A4) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelSubInjection/EditTBenzMaxCutoff | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditCutoffKeyPress` (0x004C82B8) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettings/LabelMaxCorrenteCambio | TLabel | Tempo max corrente al cambio gas / Max current time in gas change-over / Max current time in gas change-over |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettings/EditTempoMassimaCorrente | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettings/EditCorrenteCutoff | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettings/EditParamInj | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettings/EditTempoInterSpegnimento | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettings/EditValPercHoldingCurrent | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettings/EditCorrente | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettings/EditImpedenzaIniettori | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettings/CheckTempoMaxCorrente | TCheckBox | Correzione corrente di mantenimento con tensione iniettori / Correction of the holding current with injector voltage / Correção atual de manutenção com voltagem dos injetores |  | OnClick→`CheckTempoMaxCorrenteClick` (0x004B0E9C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettings/EditMaxCorrenteCambio | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditAdvParamInjGasKeyPress` (0x004C7150) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/CheckDemagMode | TCheckBox | Fast demag mode |  | OnClick→`CheckDemagModeClick` (0x004CAE44); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditCorrenteDiPicco | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditDurataFase2 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditDurataMaxFase1 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditCorrenteDiPiccoCutoff | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditDurataFase2Cutoff | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditDurataMaxFase1Cutoff | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditCorrenteDiPiccoWarmUp | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditDurataFase2WarmUp | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditDurataMaxFase1WarmUp | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditDurataFaseDiDemag | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditCorrenteDiHold | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditCorrenteDiHoldTemp | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditDurataCorrenteDiHoldTemp | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabIniezione/ScrollBoxIniezione/PanelInjection/PageControlInj/TabInjSettingsSt/EditFrequenzaSwitching | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditVH34ParamKeyPress` (0x004CAE48) |
| PageControl/TabTaratura | TTabSheet | Varie / Miscellaneous / Miscelânea |  | OnShow→`TabTaraturaShow` (0x004A9CD0) |
| PageControl/TabTaratura/EditTempTaratura | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabTaratura/EditScartoMinFuoriMin | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditScartoMinFuoriMinKeyPress` (0x004BA1E8) |
| PageControl/TabTaratura/EditScartoMinAlMin | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabTaratura/EditGiriAutotaratura | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabTaratura/EditDeltaK | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabTaratura/EditNumControlli | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabTaratura/EditNumControlliOk | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabTaratura/DrawGridTInjDefault | TDrawGrid |  |  | OnDblClick→`DrawGridTInjDefaultDblClick` (0x004B6868); OnDrawCell→`DrawGridTInjDefaultDrawCell` (0x004B64A0); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridTInjDefaultKeyPress` (0x004B68CC) |
| PageControl/TabVariatore/DrawGridAnticipo | TDrawGrid |  |  | OnDblClick→`DrawGridAnticipoDblClick` (0x004BD2E4); OnDrawCell→`DrawGridAnticipoDrawCell` (0x004BCF1C); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridAnticipoKeyPress` (0x004BD348) |
| PageControl/TabBenzina | TTabSheet | Gas/benzina / Gas/petrol / Gás/gasolina |  | OnShow→`TabBenzinaShow` (0x004BF004) |
| PageControl/TabBenzina/ScrollBox1/LabelRpmForSplitFuel | TLabel | RPM per attivazione split fuel / RPM for split fuel activation / RPM para activação split fuel |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupMinimoBenzina/LabelGiriMinimoPerBenzina | TLabel | Giri per identificazione del minimo / RPM for idle identifying / RPM para identificação mínimo |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupMinimoBenzina/LabelIniettate | TLabel | Num inj min |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupMinimoBenzina/LabelUscita | TLabel | Progress |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupMinimoBenzina/LabelIniettateCutoff | TLabel | Num inj exit cutoff |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupMinimoBenzina/RadioGas | TRadioButton | Gas |  | OnClick→`RadioGasClick` (0x004BFAD8); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupMinimoBenzina/EditGiriMinimoPerBenzina | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditGiriMinimoPerBenzinaKeyPress` (0x004BFE6C) |
| PageControl/TabBenzina/ScrollBox1/GroupMinimoBenzina/RadioBenzina | TRadioButton | Benzina / Petrol / Gasolina |  | OnClick→`RadioGasClick` (0x004BFAD8); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupMinimoBenzina/RadioRientro | TRadioButton | Rientro a benzina / Return to petrol / Passar a gasolina |  | OnClick→`RadioGasClick` (0x004BFAD8); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupMinimoBenzina/EditIniettate | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabBenzina/ScrollBox1/GroupMinimoBenzina/EditUscita | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabBenzina/ScrollBox1/GroupMinimoBenzina/EditIniettateCutoff | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabBenzina/ScrollBox1/CheckParzializza | TCheckBox | Contributo automatico benzina ad alti giri / Automatic petrol contribution at high rpm / Deslig. dos injectores gás |  | OnClick→`CheckParzializzaClick` (0x004BFAA0); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupAltiGiriBenzina/LabelGiriPerBenzina | TLabel | Giri per funzionamento benzina / RPM for petrol operation / RPM para func. a gasolina |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupAltiGiriBenzina/LabelTempiBenzina | TLabel | Tempo di iniezione per funz. benzina |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupAltiGiriBenzina/LabelTempoBenzina1 | TLabel | Valore di contributo benzina / Amount of petrol addition / Quant. de gasolina adic. |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupAltiGiriBenzina/RadioGasAltiGiri | TRadioButton | Gas |  | OnClick→`RadioGasAltiGiriClick` (0x004BFEF4); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupAltiGiriBenzina/RadioBenzinaAltiGiri | TRadioButton | Benzina / Petrol / Gasolina |  | OnClick→`RadioGasAltiGiriClick` (0x004BFEF4); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupAltiGiriBenzina/EditTempoBenzina3 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditTempoBenzinaKeyPress` (0x004C0570) |
| PageControl/TabBenzina/ScrollBox1/GroupAltiGiriBenzina/EditGiriPerBenzina | TNumericEdit |  |  | OnKeyPress→`EditGiriPerBenzinaKeyPress` (0x004C04E8) |
| PageControl/TabBenzina/ScrollBox1/GroupAltiGiriBenzina/EditTempoBenzina1 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditTempoBenzinaKeyPress` (0x004C0570) |
| PageControl/TabBenzina/ScrollBox1/GroupAltiGiriBenzina/RadioContributoAltiGiri | TRadioButton | Contributo benzina / Petrol addition / Adic. gasolina |  | OnClick→`RadioGasAltiGiriClick` (0x004BFEF4); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/GroupAltiGiriBenzina/EditGiriSupBenzina | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabBenzina/ScrollBox1/EditRpmForSplitFuel | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabBenzina/ScrollBox1/DrawGridSplitFuel | TDrawGrid |  |  | OnDblClick→`DrawGridSplitFuelDblClick` (0x004C2788); OnDrawCell→`DrawGridSplitFuelDrawCell` (0x004C2420); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridSplitFuelKeyPress` (0x004C27EC) |
| PageControl/TabBenzina/ScrollBox1/CheckIstantaneo | TCheckBox | Passaggio benzina gas in modo sequenziale / Sequential fuel changeover / Mudança sequencial combust. |  | OnClick→`CheckIstantaneoClick` (0x004BFA68); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabBenzina/ScrollBox1/CheckSplitFuel | TCheckBox | Abilita split fuel / Enable split fuel / Enable split fuel |  | OnClick→`CheckSplitFuelClick` (0x004C1C64); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabAdatta | TTabSheet | Adattività OBD / OBD adaptivity / Adaptabilidade OBD |  | OnShow→`TabAdattaShow` (0x004C0854) |
| PageControl/TabAdatta/LabelTipoObd | TLabel | Tipo connessione OBD / Type of OBD connection / Tipo de conexão OBD |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabAdatta/CheckAdattaBenzina | TCheckBox | Adattività con mappa benzina / Adaptivity with petrol map / Adaptabilidade com mapa gasolina |  | OnClick→`CheckAdattaBenzinaClick` (0x004C0E34); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/CheckAdattativita | TCheckBox | Adattività OBD / OBD adaptivity / Adaptabilidade OBD |  | OnClick→`CheckAdattativitaClick` (0x004C0D50); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabAdatta/PanelAdatta/EditKi | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabAdatta/PanelAdatta/EditKp | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabAdatta/PanelAdatta/DrawGridBenzina | TDrawGrid |  |  | OnDblClick→`DrawGridBenzinaDblClick` (0x004C157C); OnDrawCell→`DrawGridBenzinaDrawCell` (0x004C11B8); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridBenzinaKeyPress` (0x004C15E0) |
| PageControl/TabAdatta/PanelAdattaObd/LabelTipoCorrettori | TLabel | Tipo correttori BENZINA / Type of PETROL fuel trims / Tipo de compens. GASOLINA |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabAdatta/PanelAdattaObd/LabelSensAdatta | TLabel | Sensibilità adattività / Adaptivity sensitivity / Sensibilidade adaptabilidae |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabAdatta/PanelAdattaObd/PanelTipoCorr1/RadioAdatta25 | TRadioButton | max ±25% |  | OnClick→`RadioAdatta25Click` (0x004C05F0) |
| PageControl/TabAdatta/PanelAdattaObd/PanelTipoCorr1/RadioAdatta50 | TRadioButton | max ±50% |  | OnClick→`RadioAdatta25Click` (0x004C05F0) |
| PageControl/TabAdatta/PanelAdattaObd/PanelTipoCorr1/RadioAdatta100 | TRadioButton | max ±100% |  | OnClick→`RadioAdatta25Click` (0x004C05F0) |
| PageControl/TabAdatta/PanelAdattaObd/PanelTipoCorr2/RadioAdattaDiritti | TRadioButton | diritti / rigth / direita |  | OnClick→`RadioAdattaDirittiClick` (0x004C06EC) |
| PageControl/TabAdatta/PanelAdattaObd/PanelTipoCorr2/RadioAdattaInvertiti | TRadioButton | invertiti / inverted / invertido |  | OnClick→`RadioAdattaDirittiClick` (0x004C06EC) |
| PageControl/TabAdatta/PanelAdattaObd/EditSensAdatta | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditSensAdattaKeyPress` (0x004C07D0) |
| PageControl/TabAdatta/PanelAdattaObd/EditSogliaAdatta | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabAdatta/ComboTipoObd | TKeyComboBox |  |  | OnKeyPress→`ComboTipoObdKeyPress` (0x004C0F18) |
| PageControl/TabAdatta/CheckAzzeraErroriObd | TCheckBox | Reset errori OBD / Reset OBD errors / Reset erro OBD |  | OnClick→`CheckAzzeraErroriObdClick` (0x004C1AAC); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928); OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabAdatta/CheckAzzeraBlackListObd | TCheckBox | Reset selettivo errori OBD / Selective reset of OBD errors / Selective reset of OBD errors |  | OnClick→`CheckAzzeraBlackListObdClick` (0x004C1ADC); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/CheckOutDiagInfoSuKLine | TCheckBox | Output high-level on k-line during diagnostic error |  | OnClick→`CheckOutDiagInfoSuKLineClick` (0x004C1B74); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/CheckPetrolObdConnection | TCheckBox | Connessione obd in funzionameno benzina / Obd connection in petrol mode / Obd connection in petrol mode |  | OnClick→`CheckPetrolObdConnectionClick` (0x004C1B0C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/PanelListObd/ScrollObd/CheckObd05 | TCheckBox | PID 0x05 |  | OnClick→`CheckObdClick` (0x004CAE4C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/PanelListObd/ScrollObd/CheckBox2 | TCheckBox | PID 0x0B |  | OnClick→`CheckObdClick` (0x004CAE4C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/PanelListObd/ScrollObd/CheckBox3 | TCheckBox | PID 0x0C |  | OnClick→`CheckObdClick` (0x004CAE4C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/PanelListObd/ScrollObd/CheckBox4 | TCheckBox | PID 0x14 |  | OnClick→`CheckObdClick` (0x004CAE4C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/PanelListObd/ScrollObd/CheckBox5 | TCheckBox | PID 0x15 |  | OnClick→`CheckObdClick` (0x004CAE4C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/PanelListObd/ScrollObd/CheckBox6 | TCheckBox | PID 0x18 |  | OnClick→`CheckObdClick` (0x004CAE4C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/PanelListObd/ScrollObd/CheckBox7 | TCheckBox | PID 0x19 |  | OnClick→`CheckObdClick` (0x004CAE4C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/PanelListObd/ScrollObd/CheckBox8 | TCheckBox | PID 0x34 |  | OnClick→`CheckObdClick` (0x004CAE4C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/PanelListObd/ScrollObd/CheckBox9 | TCheckBox | PID 0x38 |  | OnClick→`CheckObdClick` (0x004CAE4C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/PanelListObd/ScrollObd/CheckBox10 | TCheckBox | PID 0x55 |  | OnClick→`CheckObdClick` (0x004CAE4C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/PanelListObd/ScrollObd/CheckBox11 | TCheckBox | PID 0x56 |  | OnClick→`CheckObdClick` (0x004CAE4C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/PanelListObd/ScrollObd/CheckBox12 | TCheckBox | PID 0x57 |  | OnClick→`CheckObdClick` (0x004CAE4C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/PanelListObd/ScrollObd/CheckBox13 | TCheckBox | PID 0x58 |  | OnClick→`CheckObdClick` (0x004CAE4C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/CheckUsaCanPerObd | TCheckBox | Usa CAN per OBD / Use CAN for OBD / Use CAN for OBD |  | OnClick→`CheckUsaCanPerObdClick` (0x004C1B3C); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/CheckDeleteErrorMotoreSpento | TCheckBox | Cancellazione errori OBD a motore spento |  | OnClick→`CheckDeleteErrorMotoreSpentoClick` (0x004C1BA4); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabAdatta/CheckBoxEnDiagDtcPowertrain | TCheckBox | En Diag DTC POWERTRAIN |  | OnClick→`CheckBoxEnDiagDtcPowertrainClick` (0x004CB358); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabConsumometro | TTabSheet | Consume meter |  | OnShow→`TabConsumometroShow` (0x004C1044) |
| PageControl/TabConsumometro/CheckConsumometro | TCheckBox | Attiva consumometro (solo GPL) / Enable consume meter (only LPG) / Habil. medidor consumo (só GPL) |  | OnClick→`CheckConsumometroClick` (0x004C1108); OnKeyPress→`CheckTpsBlocKeyPress` (0x004A8928) |
| PageControl/TabConsumometro/EditLitriSerbatoio | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabConsumometro/ComboTagliaIniettori | TKeyComboBox |  |  | OnKeyPress→`ComboTagliaIniettoriKeyPress` (0x004C1138) |
| PageControl/TabFrest | TTabSheet | Frest model |  | OnShow→`TabFrestShow` (0x004C6324) |
| PageControl/TabFrest/EditFrestGasExtraInjThd | TNumericEdit |  |  | OnEnter→`EditFrestEnter` (0x004C611C); OnExit→`EditFrestExit` (0x004C6314); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjOffset | TNumericEdit |  |  | OnEnter→`EditFrestEnter` (0x004C611C); OnExit→`EditFrestExit` (0x004C6314); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjGain | TNumericEdit |  |  | OnEnter→`EditFrestEnter` (0x004C611C); OnExit→`EditFrestExit` (0x004C6314); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditlFrestGasNormTemp | TNumericEdit |  |  | OnEnter→`EditFrestEnter` (0x004C611C); OnExit→`EditFrestExit` (0x004C6314); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasNormPress | TNumericEdit |  |  | OnEnter→`EditFrestEnter` (0x004C611C); OnExit→`EditFrestExit` (0x004C6314); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestRailPressCoeff | TNumericEdit |  |  | OnEnter→`EditFrestEnter` (0x004C611C); OnExit→`EditFrestExit` (0x004C6314); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjVect0 | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjVect1 | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjVect2 | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjVect3 | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjVect4 | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjVect5 | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjBreak2 | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjBreak3 | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjBreak4 | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjBreak5 | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjBreak0 | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabFrest/EditFrestGasInjBreak1 | TNumericEdit |  |  | OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSwitchToPetrol | TTabSheet | Switch to petrol |  | OnShow→`TabSwitchToPetrolShow` (0x004C65A4) |
| PageControl/TabSwitchToPetrol/EditSwitchToPetrol4 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSwitchToPetrol/EditSwitchToPetrol3 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSwitchToPetrol/EditSwitchToPetrol2 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSwitchToPetrol/EditSwitchToPetrol1 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSwitchToPetrol/EditSwitchToPetrol0 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSwitchToPetrol/EditSwitchToPetrol5 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSwitchToPetrol/EditSwitchToPetrol6 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSwitchToPetrol/EditSwitchToPetrol7 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabSwitchToPetrol/EditSwitchToPetrol8 | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabDhlpAgaslev | TTabSheet | DHLP/AGASLEV |  | OnShow→`TabDhlpAgaslevShow` (0x004C8EC4) |
| PageControl/TabDhlpAgaslev/LabelDeltaTInterruzioneWarmup | TLabel | DeltaTInterruzioneWarmup |  | OnMouseDown→`ControlMouseDown` (0x004C6020) |
| PageControl/TabDhlpAgaslev/EditPressInsulDiagAftCrkDly | TValueEditEx |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0) |
| PageControl/TabDhlpAgaslev/EditLoPressInsulDiagFallThd | TValueEditEx |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0) |
| PageControl/TabDhlpAgaslev/EditPressInsulDiagMinTankLvlThd | TValueEditEx |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0) |
| PageControl/TabDhlpAgaslev/EditPressInsulDiagPressZnt | TValueEditEx |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0) |
| PageControl/TabDhlpAgaslev/EditPressInsulDiagPressZntOut | TValueEditEx |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0) |
| PageControl/TabDhlpAgaslev/EditPressInsulDiagWatTempHiThd | TValueEditEx |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0) |
| PageControl/TabDhlpAgaslev/EditPressInsulDiagWatTempLoThd | TValueEditEx |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0) |
| PageControl/TabDhlpAgaslev/EditLoPassFiltConFast | TValueEditEx |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0) |
| PageControl/TabDhlpAgaslev/EditLoPassFiltConSlow | TValueEditEx |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0) |
| PageControl/TabDhlpAgaslev/EditIsteresiRiaccensione | TValueEditEx |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0) |
| PageControl/TabDhlpAgaslev/EditAnticipoInterruzioneWarmup | TValueEditEx |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0) |
| PageControl/TabDhlpAgaslev/EditDeltaTInterruzioneWarmup | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditDeltaTInterruzioneWarmupKeyPress` (0x004C8ECC) |
| PageControl/TabDhlpAgaslev/ButtonDebug | TButton | Enable debug |  | OnClick→`ButtonDebugClick` (0x004C8EC8) |
| PageControl/TabDhlpAgaslev/EditDeltaTRailPerBloccoDHLP | TValueEditEx |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0) |
| PageControl/TabTest | TTabSheet | Dati di test / Test data / Dados de teste |  | OnShow→`TabTestShow` (0x004BA440) |
| PageControl/TabTest/EditTestWord | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabTest/EditTestByte | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabTest/EditTestTempo | TNumericEdit |  |  | OnEnter→`EditGiriCambioEnter` (0x004A98D8); OnExit→`EditGiriCambioExit` (0x004A9CC0); OnKeyPress→`EditRifLambdaKeyPress` (0x004A6A00) |
| PageControl/TabTest/DrawGridTest | TDrawGrid |  |  | OnDblClick→`DrawGridTestDblClick` (0x004B75EC); OnDrawCell→`DrawGridTestDrawCell` (0x004B71B8); OnKeyDown→`DrawGridKKeyDown` (0x004B2514); OnKeyPress→`DrawGridTestKeyPress` (0x004B7650) |
| PageControl/TabAutoCalNew | TTabSheet | Auto calibrazione |  | OnShow→`TabAutoCalNewShow` (0x004CB1C4) |
| PageControl/TabEsci | TTabSheet | - Esc Esci / - Esc Exit / - Esc Sair |  | OnShow→`TabEsciShow` (0x004AE418) |
| PopupMenuGrid | TPopupMenu |  |  | OnPopup→`PopupMenuGridPopup` (0x004ADCEC) |

## Temporizadores (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Intervalo ms |
|---|---|---|---|---|---|
| TimerDati | TTimer |  |  | OnTimer→`TimerDatiTimer` (0x004A307C) | 75 |

## Handlers (258): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ButtonAccettaClick` | 0x004A8D74 | 577 | INFERIDO | toca EditLivello1, EditLivello2, EditLivello3, EditLivello4 |  |  |  |
| `ButtonCommutatoreClick` | 0x004B1178 | 55 | INFERIDO | chama APIs do Windows Sleep |  | Sleep | PostMessageW |
| `ButtonDebugClick` | 0x004C8EC8 | 1 | OBS-COD | Handler vazio: o corpo é só RET (nada acontece neste evento). |  |  |  |
| `ButtonDoLevelClick` | 0x004C5C30 | 978 | INFERIDO | chama TFormConfig.ButtonAccettaClick; toca EditLivello1, EditLivello2, EditLivello3, EditLivello4, LabelMaxLevel, LabelMinLevel |  |  |  |
| `ButtonMaxLevelClick` | 0x004C5ADC | 337 | INFERIDO | chama TAebProtocol::IsConnect; toca LabelMaxLevel |  |  |  |
| `ButtonMinLevelClick` | 0x004C598C | 334 | INFERIDO | chama TAebProtocol::IsConnect; toca LabelMinLevel |  |  |  |
| `ButtonModificaInjClick` | 0x004BC47C | 270 | INFERIDO | chama TAebProtocol::IsConnect; toca DrawGridSequenzaInj |  |  |  |
| `ButtonModificaRifTarClick` | 0x004B10C8 | 136 | INFERIDO | chama TFormCentriCelleK.Create; toca DrawGridK, DrawGridOpenLoop |  |  |  |
| `ButtonNuoviParametriClick` | 0x004B9000 | 443 | INFERIDO | usa textos BUTTONNUOVIPARAMETRI, BUTTONNUOVIPARAMETRI2; chama TTranslatorExt::GetString; toca ButtonNuoviParametri, DrawGridTemp, EditDeltaT, EditRPullUp, EditTMax, EditTMin | BUTTONNUOVIPARAMETRI, BUTTONNUOVIPARAMETRI2 |  | PostMessageW |
| `ButtonResetClick` | 0x004BA188 | 94 | INFERIDO | toca TabCambio, TimerDati |  |  |  |
| `ButtonRicercaParametriClick` | 0x004AF3A8 | 509 | INFERIDO | usa textos BUTTONRICERCAPARAMETRI1; chama APIs do Windows GetAsyncKeyState; chama TTranslatorExt::GetString; toca ButtonRicercaParametri, LabLambda, TimerDati | BUTTONRICERCAPARAMETRI1 | GetAsyncKeyState |  |
| `ButtonSequenceAcquisitionClick` | 0x004A62A8 | 467 | INFERIDO | chama TAebProtocol::IsConnect, TAebProtocol::SendCommand; toca ButtonSequenceAcquisition, EditRpmTachimetro, LabRpmTachimetro, LabelRpmTachimetro |  |  |  |
| `ButtonSequenzaInjClick` | 0x004BC2DC | 414 | INFERIDO | chama TAebProtocol::IsConnect; toca ComboSequenzaInj, DrawGridSequenzaInj, DrawGridSequenzaInjBenz |  |  |  |
| `ButtonSwitchSettingsClick` | 0x004C90E0 | 1624 | INFERIDO | usa textos MESSERRORECUSWITCHCALIBRATION, MESSERRORFIRMWARESWITCHCALIBRATION, MESSERRORGIRISWITCHCALIBRATION, MESSERRORNOSWITCHCALIBRATION, MESSERRORSTARTSWITCHCALIBRATION; chama TAebProtocol::GetComFail, TAebProtocol::IsConnect, TAebProtocol::SendCommand, TTranslatorExt::GetString | MESSERRORECUSWITCHCALIBRATION, MESSERRORFIRMWARESWITCHCALIBRATION, MESSERRORGIRISWITCHCALIBRATION, MESSERRORNOSWITCHCALIBRATION, MESSERRORSTARTSWITCHCALIBRATION |  | SWITCHSETTINGSBUTTONEXIT, SWITCHSETTINGSCAPTION, SWITCHSETTINGSLABELBUZZER, SWITCHSETTINGSLABELLED, Sleep |
| `ButtonTaraturaClick` | 0x004ACFC0 | 238 | INFERIDO | chama APIs do Windows Sleep; toca ButtonCommutatore, ButtonTaratura, StaticTaratura |  | Sleep | PostMessageW |
| `CheckAdattaBenzinaClick` | 0x004C0E34 | 227 | INFERIDO | toca CheckAdattaBenzina, CheckAdattativita, LabCorrGas, LabelCorrGas, PanelAdatta, PanelAdattaObd |  |  | PostMessageW |
| `CheckAdattativitaClick` | 0x004C0D50 | 227 | INFERIDO | toca CheckAdattaBenzina, CheckAdattativita, LabCorrGas, LabelCorrGas, PanelAdatta, PanelAdattaObd |  |  | PostMessageW |
| `CheckAlimentazioneClick` | 0x004C6780 | 173 | INFERIDO | toca CheckAlimentazione |  |  | PostMessageW |
| `CheckAntiStalloClick` | 0x004C982C | 591 | INFERIDO | toca CheckAntiStallo, EditAntiStallo0, EditAntiStallo1, EditAntiStallo2, EditAntiStallo3, EditAntiStallo4, LabAntiStallo0, LabAntiStallo1 |  |  |  |
| `CheckAttivaPompaInRiservaClick` | 0x004CB030 | 48 | INFERIDO | toca CheckAttivaPompaInRiserva |  |  |  |
| `CheckAzzeraBlackListObdClick` | 0x004C1ADC | 48 | INFERIDO | toca CheckAzzeraBlackListObd |  |  |  |
| `CheckAzzeraErroriObdClick` | 0x004C1AAC | 48 | INFERIDO | toca CheckAzzeraErroriObd |  |  |  |
| `CheckBassaPressioneClick` | 0x004BCA04 | 27 | INFERIDO | toca CheckBassaPressione |  |  |  |
| `CheckBoxEV2NotConnectedClick` | 0x004C6710 | 111 | INFERIDO | sem fatos diretos extraíveis (111 bytes; chama 2 funções sem nome) |  |  |  |
| `CheckBoxEnDiagDtcPowertrainClick` | 0x004CB358 | 48 | INFERIDO | toca CheckBoxEnDiagDtcPowertrain |  |  |  |
| `CheckBoxEnableExtLedKLineClick` | 0x004CB328 | 48 | INFERIDO | toca CheckBoxEnableExtLedKLine |  |  |  |
| `CheckCambioConSovrappClick` | 0x004C8B04 | 98 | INFERIDO | chama TValueEditEx::SetVisible; toca CheckCambioConSovrapp, EditParamProgress0, EditParamProgress1 |  |  |  |
| `CheckConsumometroClick` | 0x004C1108 | 48 | INFERIDO | toca CheckConsumometro |  |  | PostMessageW |
| `CheckContributiClick` | 0x004C9C1C | 467 | INFERIDO | toca CheckContributi, DrawGridContributi, LabContribBenz, LabelContribBenz, RadioContributoAltiGiri, RadioGasAltiGiri |  |  |  |
| `CheckDeleteErrorMotoreSpentoClick` | 0x004C1BA4 | 48 | INFERIDO | toca CheckDeleteErrorMotoreSpento |  |  |  |
| `CheckDemagModeClick` | 0x004CAE44 | 1 | OBS-COD | Handler vazio: o corpo é só RET (nada acontece neste evento). |  |  |  |
| `CheckDisabilitaCommutatoreClick` | 0x004C1C04 | 48 | INFERIDO | toca CheckDisabilitaCommutatore |  |  |  |
| `CheckElettrovalvoleClick` | 0x004C5048 | 545 | INFERIDO | toca CheckElettrovalvole, EditAnticipoEV1, EditAnticipoEV2 |  |  | PostMessageW |
| `CheckEnabledDhlpDiagnosisClick` | 0x004C975C | 123 | INFERIDO | sem fatos diretos extraíveis (123 bytes; chama 2 funções sem nome) |  |  |  |
| `CheckFlashLubeClick` | 0x004C88AC | 151 | INFERIDO | toca CheckFlashLube, TabLambda |  |  |  |
| `CheckFlashLubeSuTempAcquaClick` | 0x004B8C98 | 29 | INFERIDO | toca CheckFlashLubeSuTempAcqua |  |  |  |
| `CheckFlexFuelClick` | 0x004C2C9C | 48 | INFERIDO | toca CheckFlexFuel |  |  |  |
| `CheckGiriAlberoCammeClick` | 0x004C850C | 456 | INFERIDO | toca CheckGiriAlberoCamme, CheckGiriRuotaFonica, CheckSegnaleGiri, ComboAccensione, EditNumDenti, LabelNumDenti |  |  | GetTickCount, Sleep |
| `CheckGiriRuotaFonicaClick` | 0x004C86D4 | 469 | INFERIDO | toca CheckGiriAlberoCamme, CheckGiriRuotaFonica, CheckSegnaleGiri, ComboAccensione, EditNumDenti, LabelNumDenti |  |  | GetTickCount, Sleep |
| `CheckIncrementoMaxCorrenteClick` | 0x004C721C | 48 | INFERIDO | toca CheckIncrementoMaxCorrente |  |  |  |
| `CheckInibisciEmulazioneClick` | 0x004B9280 | 72 | INFERIDO | toca CheckInibisciEmulazione |  |  | PostMessageW |
| `CheckIniezioneContinuaClick` | 0x004BC858 | 421 | INFERIDO | toca CheckIniezioneContinua, EditIniezioneContinua, LabelIniezioneContinua, LabelSIniezioneContinua |  |  |  |
| `CheckInjAcquisitionClick` | 0x004A61AC | 251 | INFERIDO | chama TAebProtocol::IsConnect, TAebProtocol::SendCommand; toca ButtonSequenceAcquisition, CheckInjAcquisition, EditRpmTachimetro, LabRpmTachimetro, LabelRpmTachimetro |  |  |  |
| `CheckIstantaneoClick` | 0x004BFA68 | 56 | INFERIDO | toca CheckIstantaneo |  |  | PostMessageW |
| `CheckMapConfiguratoClick` | 0x004BCB40 | 54 | INFERIDO | toca CheckMapConfigurato |  |  | PostMessageW |
| `CheckObdClick` | 0x004CAE4C | 481 | INFERIDO | toca ScrollObd |  |  |  |
| `CheckOutDiagInfoSuKLineClick` | 0x004C1B74 | 48 | INFERIDO | toca CheckOutDiagInfoSuKLine |  |  |  |
| `CheckPartenzaGasCaldoClick` | 0x004C4FB8 | 144 | INFERIDO | toca CheckPartenzaGasCaldo, EditTempGasCaldo, LabelT4 |  |  |  |
| `CheckParzializzaClick` | 0x004BFAA0 | 56 | INFERIDO | toca CheckParzializza |  |  | PostMessageW |
| `CheckPassPRailClick` | 0x004BCB10 | 48 | INFERIDO | toca CheckPassPRail |  |  | PostMessageW |
| `CheckPetrolObdConnectionClick` | 0x004C1B0C | 48 | INFERIDO | toca CheckPetrolObdConnection |  |  |  |
| `CheckProgressioniClick` | 0x004CA544 | 519 | INFERIDO | toca CheckProgressioni, DrawGridProgressioni, EditIstantaneoValue, LabelIstantaneoValue, LabelProgressCol0, LabelProgressCol1, LabelProgressCol2, LabelProgressRow0 |  |  |  |
| `CheckRiconoscimentoPienoClick` | 0x004CB12C | 48 | INFERIDO | toca CheckRiconoscimentoPieno |  |  |  |
| `CheckRiscaldamentoClick` | 0x004C6A64 | 1771 | INFERIDO | usa textos LABELDURATAATTIVAZIONEINJ, LABELPICCORISCALDAMENTO; chama TTranslatorExt::GetString; toca CheckRiscaldamento, CheckboxPreheatAsyncMode, CheckboxPreheatOnlyAfterSleepMode, EditLimiteRitardoAttivWarmUp, EditMantRiscaldamento, EditPeriodoRiscaldamento, EditPiccoRiscaldamento, EditPreheatPGasTh | LABELDURATAATTIVAZIONEINJ, LABELPICCORISCALDAMENTO |  |  |
| `CheckRitornoBenzinaClick` | 0x004A9484 | 322 | INFERIDO | toca CheckRitornoBenzina, EditRitornoBenzina, LabelRitornoBenzina, LabelS12 |  |  |  |
| `CheckSegnaleGiriClick` | 0x004C5494 | 1092 | INFERIDO | toca ButtonSequenceAcquisition, CheckGiriAlberoCamme, CheckGiriRuotaFonica, CheckInjAcquisition, CheckSegnaleGiri, ComboAccensione, ComboSegnaleGiri, EditNumDenti |  |  | GetTickCount, Sleep |
| `CheckSequenzaInjClick` | 0x004BC008 | 722 | INFERIDO | chama TAebNumber::SetEcuData; toca ButtonModificaInj, ButtonSequenzaInj, CheckSequenzaInj, ComboSequenzaInj, DrawGridSequenzaInj, DrawGridSequenzaInjBenz, LabelSequenzaInj, LabelSequenzaInjBenz |  |  | PostMessageW |
| `CheckSpegniBuzzerClick` | 0x004C1C34 | 48 | INFERIDO | toca CheckSpegniBuzzer |  |  |  |
| `CheckSplitFuelClick` | 0x004C1C64 | 1978 | INFERIDO | toca CheckSplitFuel, DrawGridSplitFuel, EditRpmForSplitFuel, LabRpmForSplitFuel, LabelCoeffSplitFuel, LabelRifSplitFuel, LabelRpmForSplitFuel |  |  |  |
| `CheckStartAndStopClick` | 0x004C2BD8 | 195 | INFERIDO | toca CheckStartAndStop |  |  |  |
| `CheckTagliaExtraIniettateClick` | 0x004C1BD4 | 45 | INFERIDO | toca CheckTagliaExtraIniettate |  |  |  |
| `CheckTaglioPompaBenzinaClick` | 0x004C8B68 | 857 | INFERIDO | toca CheckAlimentazione, CheckBoxEV2NotConnected, CheckTaglioPompaBenzina, RadioGas, RadioGasAltiGiri |  |  | PostMessageW |
| `CheckTempAcquaClick` | 0x004B8B30 | 357 | INFERIDO | toca CheckTempAcqua, DrawGridPassaggio, EditRitMin, LabelPassaggioGradi, LabelPassaggioSecondi, LabelRitardoCambio, LabelS1 |  |  | PostMessageW |
| `CheckTempMotoreClick` | 0x004B8E88 | 374 | INFERIDO | toca ButtonNuoviParametri, CheckTempMotore, DrawGridTemp, EditDeltaT, EditRPullUp, EditTMax, EditTMin, LabDeltaT |  |  | PostMessageW |
| `CheckTempRiduttoreClick` | 0x004B8E10 | 118 | INFERIDO | toca CheckTempGas, EditTempCambio, LabelT1, LabelTempCambio |  |  | PostMessageW |
| `CheckTempoMaxCorrenteClick` | 0x004B0E9C | 1 | OBS-COD | Handler vazio: o corpo é só RET (nada acontece neste evento). |  |  |  |
| `CheckTpsBlocKeyPress` | 0x004A8928 | 57 | INFERIDO | chama APIs do Windows SendMessageW |  | SendMessageW |  |
| `CheckUsaCanPerObdClick` | 0x004C1B3C | 56 | INFERIDO | toca CheckUsaCanPerObd |  |  |  |
| `CheckValvetronikClick` | 0x004C2B14 | 195 | INFERIDO | toca CheckValvetronik |  |  |  |
| `CheckboxPreheatAsyncModeClick` | 0x004C72D0 | 421 | INFERIDO | toca CheckRiscaldamento, CheckboxPreheatAsyncMode, EditPeriodoRiscaldamento, EditPreheatSyncInjNum, LabPeriodoRiscaldamento, LabelPeriodoRiscaldamento, LabelPreheatSyncInjNum |  |  |  |
| `CheckboxPreheatOnlyAfterSleepModeClick` | 0x004C724C | 132 | INFERIDO | toca CheckboxPreheatOnlyAfterSleepMode |  |  |  |
| `ComboAccensioneKeyPress` | 0x004BA230 | 188 | INFERIDO | toca ComboAccensione, ComboCilindri |  |  |  |
| `ComboCambioKeyPress` | 0x004A8FB8 | 253 | INFERIDO | toca ComboCambio |  |  | PostMessageW |
| `ComboCarburanteKeyPress` | 0x004B96DC | 1348 | INFERIDO | chama APIs do Windows Sleep; chama TFormAttesa.Create, TFormConfig.FormResize; toca ComboCarburante, TabCambio, TabPressione, TimerDati |  | Sleep | DeleteFileW, FindResourceW, GetTempFileNameW, GetTempPathW |
| `ComboCilindriKeyPress` | 0x004BA2EC | 284 | INFERIDO | toca CheckInjAcquisition, ComboAccensione, ComboCilindri |  |  | PostMessageW |
| `ComboFullGroupKeyPress` | 0x004C6830 | 27 | INFERIDO | toca ComboFullGroup |  |  |  |
| `ComboKKeyPress` | 0x004C4880 | 282 | INFERIDO | toca ComboK |  |  |  |
| `ComboNumeroSondeKeyPress` | 0x004A9664 | 64 | INFERIDO | sem fatos diretos extraíveis (64 bytes; chama 3 funções sem nome) |  |  |  |
| `ComboPressioneKeyPress` | 0x004AE420 | 872 | INFERIDO | usa textos COMBOPRESSIONEMISURATA0, COMBOPRESSIONEMISURATA1, COMBOPRESSIONEMISURATA2, COMBOPRESSIONEMISURATA3, LABELTIPOPRESSIONEMIS, LABELTIPOPRESSIONEMIS1; chama TTranslatorExt::GetString; toca ComboMisuratorePressione, ComboPressioneMisurata, LabelTipoPressioneMisurata | COMBOPRESSIONEMISURATA0, COMBOPRESSIONEMISURATA1, COMBOPRESSIONEMISURATA2, COMBOPRESSIONEMISURATA3, LABELTIPOPRESSIONEMIS, LABELTIPOPRESSIONEMIS1 |  | PostMessageW |
| `ComboSegnaleGiriKeyPress` | 0x004ACDC8 | 390 | INFERIDO | toca ComboSegnaleGiri, EditSogliaGiri, LabSogliaGiri, LabelSogliaGiri |  |  |  |
| `ComboSensoreKeyPress` | 0x004A8C60 | 86 | INFERIDO | toca ComboSensore |  |  |  |
| `ComboSequenzaInjChange` | 0x004BBF3C | 95 | INFERIDO | toca ComboSequenzaInj, DrawGridSequenzaInj |  |  |  |
| `ComboSequenzaInjKeyDown` | 0x004BBF9C | 106 | INFERIDO | sem fatos diretos extraíveis (106 bytes; chama 1 funções sem nome) |  |  |  |
| `ComboSfasaturaKeyPress` | 0x004BD748 | 2188 | INFERIDO | chama TAebNumber::SetEcuData, TAebProtocol::IsConnect; toca RadioGasAltiGiri |  |  | PostMessageW |
| `ComboSonda1KeyPress` | 0x004C2D94 | 166 | INFERIDO | toca ComboSonda1, ComboSonda2 |  |  |  |
| `ComboTagliaIniettoriKeyPress` | 0x004C1138 | 126 | INFERIDO | toca ComboTagliaIniettori |  |  |  |
| `ComboTagliaKeyPress` | 0x004BCD94 | 391 | INFERIDO | toca ComboTaglia |  |  | GetTickCount, Sleep |
| `ComboTempRiduttoreKeyPress` | 0x004B9494 | 52 | INFERIDO | sem fatos diretos extraíveis (52 bytes; chama 2 funções sem nome) |  |  |  |
| `ComboTipoIniettoreKeyPress` | 0x004BA4B4 | 2089 | INFERIDO | chama APIs do Windows Sleep; chama TFormAttesa.Create, TFormConfig.FormResize; toca ComboTipoIniettore, EditTempoMortoBenz, EditTempoMortoGas, LabelGC, LabelMsBenz, LabelMsGas, LabelTempoMortoGas, TabCambio |  | Sleep | DeleteFileW, FindResourceW, GetTempFileNameW, GetTempPathW |
| `ComboTipoLambdaKeyPress` | 0x004A6674 | 251 | INFERIDO | toca ComboTipoLambda, EditLivEmulAlto, EditLivEmulBasso, EditRifInfCalda, EditRifInfFredda, EditRifLambda, EditRifSupCalda, EditRifSupFredda |  |  |  |
| `ComboTipoMisuratoreKeyPress` | 0x004BEC34 | 975 | INFERIDO | chama TFormConfig.TabPressioneShow; toca ComboTipoMisuratore |  |  | PostMessageW |
| `ComboTipoObdKeyPress` | 0x004C0F18 | 298 | INFERIDO | toca ComboTipoObd |  |  |  |
| `ComboTurboKeyPress` | 0x004B9C44 | 1348 | INFERIDO | chama APIs do Windows Sleep; chama TFormAttesa.Create, TFormConfig.FormResize; toca ComboTurbo, TabCambio, TabPressione, TimerDati |  | Sleep | DeleteFileW, FindResourceW, GetTempFileNameW, GetTempPathW |
| `ControlMouseDown` | 0x004C6020 | 250 | INFERIDO | sem fatos diretos extraíveis (250 bytes; chama 8 funções sem nome) |  |  |  |
| `DrawDelayGasTempDblClick` | 0x004C42E4 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawDelayGasTemp |  | GetCursorPos, ScreenToClient |  |
| `DrawDelayGasTempDrawCell` | 0x004C3EA4 | 1070 | INFERIDO | usa textos CHANGEDELAYCALIBR, CHANGEDELAYREFS, CHANGEDELAYSWITCH; chama TTranslatorExt::GetString; toca DrawDelayGasTemp | CHANGEDELAYCALIBR, CHANGEDELAYREFS, CHANGEDELAYSWITCH |  |  |
| `DrawDelayGasTempKeyPress` | 0x004C42D4 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridAnticipoDblClick` | 0x004BD2E4 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridAnticipo |  | GetCursorPos, ScreenToClient |  |
| `DrawGridAnticipoDrawCell` | 0x004BCF1C | 967 | INFERIDO | toca DrawGridAnticipo |  |  |  |
| `DrawGridAnticipoKeyPress` | 0x004BD348 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridAnticipoMultipointDblClick` | 0x004C3C38 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridAnticipoMultipoint |  | GetCursorPos, ScreenToClient |  |
| `DrawGridAnticipoMultipointDrawCell` | 0x004C3944 | 740 | INFERIDO | usa textos LABELRITEMULRICCA; chama TTranslatorExt::GetString; toca DrawGridAnticipoMultipoint | LABELRITEMULRICCA |  |  |
| `DrawGridAnticipoMultipointKeyPress` | 0x004C3C28 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridBenzinaDblClick` | 0x004C157C | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridBenzina |  | GetCursorPos, ScreenToClient |  |
| `DrawGridBenzinaDrawCell` | 0x004C11B8 | 964 | INFERIDO | toca DrawGridBenzina |  |  |  |
| `DrawGridBenzinaKeyPress` | 0x004C15E0 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridContributiDblClick` | 0x004CA288 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridContributi |  | GetCursorPos, ScreenToClient |  |
| `DrawGridContributiDrawCell` | 0x004C9DF0 | 1159 | INFERIDO | toca DrawGridContributi |  |  |  |
| `DrawGridContributiKeyPress` | 0x004CA278 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridEmissioniDblClick` | 0x004B4530 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridEmissioni |  | GetCursorPos, ScreenToClient |  |
| `DrawGridEmissioniDrawCell` | 0x004B3F68 | 1461 | INFERIDO | usa textos LABELGIRIEMUL, LABELRIFLAMBDAEMUL, LABELRIFLAMBDAEMULUEGO, LABELRITEMULRICCA; chama TTranslatorExt::GetString; toca DrawGridEmissioni | LABELGIRIEMUL, LABELRIFLAMBDAEMUL, LABELRIFLAMBDAEMULUEGO, LABELRITEMULRICCA |  |  |
| `DrawGridEmissioniKeyPress` | 0x004B4520 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridEmissioniMultipointDblClick` | 0x004C33E0 | 243 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; chama TFormCentriCelle.Create; toca DrawGridEmissioniMultipoint |  | GetCursorPos, ScreenToClient |  |
| `DrawGridEmissioniMultipointDrawCell` | 0x004C2E3C | 1426 | INFERIDO | toca DrawGridEmissioni, DrawGridEmissioniMultipoint |  |  |  |
| `DrawGridEmissioniMultipointKeyPress` | 0x004C33D0 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridKDblClick` | 0x004B1940 | 153 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridK, RadioMapAdatta, RadioMapPetrol |  | GetCursorPos, ScreenToClient | PostMessageW |
| `DrawGridKDrawCell` | 0x004B12EC | 1544 | INFERIDO | toca DrawGridK, RadioMapAdatta, RadioMapPetrol |  |  |  |
| `DrawGridKKeyDown` | 0x004B2514 | 159 | INFERIDO | sem fatos diretos extraíveis (159 bytes; chama 2 funções sem nome) |  |  |  |
| `DrawGridKKeyPress` | 0x004B18F4 | 74 | INFERIDO | toca RadioMapAdatta, RadioMapPetrol |  |  | PostMessageW |
| `DrawGridOpenLoopDblClick` | 0x004B29E0 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridOpenLoop |  | GetCursorPos, ScreenToClient | PostMessageW |
| `DrawGridOpenLoopDrawCell` | 0x004B25B4 | 1049 | INFERIDO | toca DrawGridOpenLoop |  |  |  |
| `DrawGridOpenLoopKeyPress` | 0x004B29D0 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  | PostMessageW |
| `DrawGridPassaggioDblClick` | 0x004B3128 | 38 | INFERIDO | chama APIs do Windows GetCursorPos; toca DrawGridPassaggio |  | GetCursorPos |  |
| `DrawGridPassaggioDrawCell` | 0x004B2D64 | 75 | INFERIDO | toca DrawGridPassaggio |  |  |  |
| `DrawGridPassaggioGasDblClick` | 0x004B3A24 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridPassaggioGas |  | GetCursorPos, ScreenToClient |  |
| `DrawGridPassaggioGasDrawCell` | 0x004B3660 | 945 | INFERIDO | usa textos LABELOLTRE; chama TTranslatorExt::GetString; toca DrawGridPassaggioGas | LABELOLTRE |  |  |
| `DrawGridPassaggioGasKeyPress` | 0x004B3A14 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridPassaggioKeyPress` | 0x004B3118 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridPressAssDblClick` | 0x004B6178 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridPressAss |  | GetCursorPos, ScreenToClient |  |
| `DrawGridPressAssDrawCell` | 0x004B5E90 | 728 | INFERIDO | toca DrawGridPressAss |  |  |  |
| `DrawGridPressAssKeyPress` | 0x004B6168 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridPressDiffDblClick` | 0x004B5B68 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridPressDiff |  | GetCursorPos, ScreenToClient |  |
| `DrawGridPressDiffDrawCell` | 0x004B5880 | 728 | INFERIDO | toca DrawGridPressDiff |  |  |  |
| `DrawGridPressDiffKeyPress` | 0x004B5B58 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridPressPassDblClick` | 0x004B6EF4 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridPressPass |  | GetCursorPos, ScreenToClient |  |
| `DrawGridPressPassDrawCell` | 0x004B6B1C | 982 | INFERIDO | toca DrawGridPressPass |  |  |  |
| `DrawGridPressPassKeyPress` | 0x004B6F58 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridProgressioniDblClick` | 0x004CA9A0 | 86 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridProgressioni |  | GetCursorPos, ScreenToClient |  |
| `DrawGridProgressioniDrawCell` | 0x004CA74C | 577 | INFERIDO | chama TAebMatrix::GetInt; toca DrawGridProgressioni |  |  |  |
| `DrawGridProgressioniKeyPress` | 0x004CA990 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridSequenzaInjBenzDrawCell` | 0x004BBB88 | 930 | INFERIDO | toca DrawGridSequenzaInjBenz |  |  |  |
| `DrawGridSequenzaInjBenzSelectCell` | 0x004BBF2C | 13 | INFERIDO | sem fatos diretos extraíveis (13 bytes; chama 0 funções sem nome) |  |  |  |
| `DrawGridSequenzaInjDrawCell` | 0x004BB664 | 1099 | INFERIDO | toca DrawGridSequenzaInj |  |  |  |
| `DrawGridSequenzaInjSelectCell` | 0x004BBAB0 | 213 | INFERIDO | toca ComboSequenzaInj, DrawGridSequenzaInj |  |  |  |
| `DrawGridSplitFuelDblClick` | 0x004C2788 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridSplitFuel |  | GetCursorPos, ScreenToClient |  |
| `DrawGridSplitFuelDrawCell` | 0x004C2420 | 870 | INFERIDO | toca DrawGridSplitFuel |  |  |  |
| `DrawGridSplitFuelKeyPress` | 0x004C27EC | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridTInjDefaultDblClick` | 0x004B6868 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridTInjDefault |  | GetCursorPos, ScreenToClient |  |
| `DrawGridTInjDefaultDrawCell` | 0x004B64A0 | 968 | INFERIDO | toca DrawGridTInjDefault |  |  |  |
| `DrawGridTInjDefaultKeyPress` | 0x004B68CC | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridTempDblClick` | 0x004B81F4 | 174 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca ButtonNuoviParametri, DrawGridTemp |  | GetCursorPos, ScreenToClient |  |
| `DrawGridTempDrawCell` | 0x004B7874 | 2090 | INFERIDO | usa textos LABELINDEXTEMP, LABELNTCTEMP, LABELTEMPTEMP, LABELVOLTTEMP; chama TTranslatorExt::GetString; toca DrawGridTemp, EditDeltaT, EditRPullUp, EditTMin | LABELINDEXTEMP, LABELNTCTEMP, LABELTEMPTEMP, LABELVOLTTEMP |  |  |
| `DrawGridTempEnter` | 0x004B868C | 23 | INFERIDO | sem fatos diretos extraíveis (23 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridTempExit` | 0x004B86A4 | 32 | INFERIDO | toca DrawGridTemp, LabelInstr |  |  |  |
| `DrawGridTempKeyPress` | 0x004B80B0 | 324 | INFERIDO | toca ButtonNuoviParametri, DrawGridTemp |  |  |  |
| `DrawGridTempMotDblClick` | 0x004B54F8 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridTempMot |  | GetCursorPos, ScreenToClient |  |
| `DrawGridTempMotDrawCell` | 0x004B5134 | 948 | INFERIDO | usa textos LABELOLTRE; chama TTranslatorExt::GetString; toca DrawGridTempMot | LABELOLTRE |  |  |
| `DrawGridTempMotKeyPress` | 0x004B54E8 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridTempRidDblClick` | 0x004B4DAC | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridTempRid |  | GetCursorPos, ScreenToClient |  |
| `DrawGridTempRidDrawCell` | 0x004B49E8 | 948 | INFERIDO | usa textos LABELOLTRE; chama TTranslatorExt::GetString; toca DrawGridTempRid | LABELOLTRE |  |  |
| `DrawGridTempRidKeyPress` | 0x004B4D9C | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridTempiLrDblClick` | 0x004BE5A8 | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridTempiLr |  | GetCursorPos, ScreenToClient |  |
| `DrawGridTempiLrDrawCell` | 0x004BE278 | 799 | INFERIDO | toca DrawGridTempiLr |  |  |  |
| `DrawGridTempiLrKeyPress` | 0x004BE598 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `DrawGridTestDblClick` | 0x004B75EC | 99 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridTest |  | GetCursorPos, ScreenToClient |  |
| `DrawGridTestDrawCell` | 0x004B71B8 | 1074 | INFERIDO | toca DrawGridTest |  |  |  |
| `DrawGridTestKeyPress` | 0x004B7650 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 1 funções sem nome) |  |  |  |
| `EditAdvParamInjGasKeyPress` | 0x004C7150 | 187 | INFERIDO | sem fatos diretos extraíveis (187 bytes; chama 7 funções sem nome) |  |  |  |
| `EditAntiStalloKeyPress` | 0x004C97D8 | 81 | INFERIDO | chama TAebVector::GetInt, TAebVector::SetInt |  |  |  |
| `EditAperturaInjKeyPress` | 0x004ACF68 | 85 | INFERIDO | sem fatos diretos extraíveis (85 bytes; chama 2 funções sem nome) |  |  |  |
| `EditBassaPressioneMpKeyPress` | 0x004C526C | 520 | INFERIDO | sem fatos diretos extraíveis (520 bytes; chama 9 funções sem nome) |  |  |  |
| `EditCorrBancata2KeyPress` | 0x004C2CCC | 197 | INFERIDO | toca EditCorrBancata2 |  |  |  |
| `EditCorrCarbMinimoKeyPress` (sem evento no DFM) | 0x0049F0E4 | 137 | INFERIDO | sem fatos diretos extraíveis (137 bytes; chama 5 funções sem nome) |  |  |  |
| `EditCorrIntInfKeyPress` | 0x004B91BC | 193 | INFERIDO | toca EditCorrIntInf, EditCorrIntSup |  |  |  |
| `EditCutoffKeyPress` | 0x004C82B8 | 577 | INFERIDO | toca EditGiriCutoff, EditPressioneCutoff, EditTBenzMaxCutoff, EditTInjCutoff |  |  |  |
| `EditDeltaTInterruzioneWarmupKeyPress` | 0x004C8ECC | 182 | INFERIDO | chama TAebNumber::GetInt, TAebNumber::SetInt; toca EditDeltaTInterruzioneWarmup |  |  |  |
| `EditFiltroEnter` | 0x004BDFD4 | 564 | INFERIDO | toca EditFiltro |  |  |  |
| `EditFiltroExit` | 0x004BE208 | 112 | INFERIDO | toca EditFiltro |  |  |  |
| `EditFiltroKeyPress` | 0x004B9644 | 149 | INFERIDO | toca EditFiltro |  |  |  |
| `EditFlashLubeKeyPress` | 0x004C8948 | 406 | INFERIDO | toca EditFlashLube0, EditFlashLube1, EditFlashLube2, EditFlashLube3 |  |  |  |
| `EditFrestEnter` | 0x004C611C | 503 | INFERIDO | toca LabFrestGasInjOffset, LabelLimite |  |  |  |
| `EditFrestExit` | 0x004C6314 | 14 | INFERIDO | sem fatos diretos extraíveis (14 bytes; chama 1 funções sem nome) |  |  |  |
| `EditGiriCambioEnter` | 0x004A98D8 | 1000 | INFERIDO | toca LabelLimite |  |  |  |
| `EditGiriCambioExit` | 0x004A9CC0 | 14 | INFERIDO | sem fatos diretos extraíveis (14 bytes; chama 1 funções sem nome) |  |  |  |
| `EditGiriKFiltroEnter` | 0x004BE90C | 430 | INFERIDO | sem fatos diretos extraíveis (430 bytes; chama 10 funções sem nome) |  |  |  |
| `EditGiriKFiltroExit` | 0x004BEABC | 102 | INFERIDO | sem fatos diretos extraíveis (102 bytes; chama 5 funções sem nome) |  |  |  |
| `EditGiriKFiltroKeyPress` | 0x004BEB24 | 269 | INFERIDO | toca EditGiriAltiKFiltro, EditGiriKFiltro |  |  |  |
| `EditGiriMinimoPerBenzinaKeyPress` | 0x004BFE6C | 136 | INFERIDO | sem fatos diretos extraíveis (136 bytes; chama 5 funções sem nome) |  |  |  |
| `EditGiriPerBenzinaKeyPress` | 0x004C04E8 | 136 | INFERIDO | sem fatos diretos extraíveis (136 bytes; chama 5 funções sem nome) |  |  |  |
| `EditKFiltroKeyPress` | 0x004B95C0 | 130 | INFERIDO | sem fatos diretos extraíveis (130 bytes; chama 5 funções sem nome) |  |  |  |
| `EditKMappaNeutroKeyPress` | 0x004C499C | 96 | INFERIDO | toca EditKMappaNeutro |  |  |  |
| `EditOffsetKeyPress` | 0x004AF360 | 72 | INFERIDO | sem fatos diretos extraíveis (72 bytes; chama 4 funções sem nome) |  |  |  |
| `EditPreheatSyncInjNumKeyPress` | 0x004C78A4 | 95 | INFERIDO | sem fatos diretos extraíveis (95 bytes; chama 5 funções sem nome) |  |  |  |
| `EditRPullUpEnter` | 0x004B86C4 | 506 | INFERIDO | toca LabelLimite |  |  |  |
| `EditRPullUpExit` | 0x004B88C0 | 624 | INFERIDO | toca DrawGridTemp, EditDeltaT, EditRPullUp, EditTMax, EditTMin, LabelLimite |  |  |  |
| `EditRifLambdaKeyPress` | 0x004A6A00 | 201 | INFERIDO | sem fatos diretos extraíveis (201 bytes; chama 6 funções sem nome) |  |  |  |
| `EditRiscaldamentoKeyPress` | 0x004C7478 | 1052 | INFERIDO | toca EditMantRiscaldamento, EditPeriodoRiscaldamento, EditPiccoRiscaldamento, EditPreheatPGasTh, EditPreheatPInjTimeTh, EditPreheatTempGasTh |  |  |  |
| `EditRpmTachimetroKeyPress` | 0x004A647C | 502 | INFERIDO | chama TAebProtocol::IsConnect, TAebProtocol::SendCommand; toca EditRpmTachimetro, LabRpmTachimetro, LabelRpmTachimetro |  |  |  |
| `EditScartoMinFuoriMinKeyPress` | 0x004BA1E8 | 71 | INFERIDO | sem fatos diretos extraíveis (71 bytes; chama 4 funções sem nome) |  |  |  |
| `EditSensAdattaKeyPress` | 0x004C07D0 | 132 | INFERIDO | sem fatos diretos extraíveis (132 bytes; chama 5 funções sem nome) |  |  |  |
| `EditSogliaGiriKeyPress` | 0x004C58DC | 140 | INFERIDO | toca EditSogliaGiri |  |  |  |
| `EditTInjSup22KeyPress` | 0x004C3E44 | 96 | INFERIDO | toca EditTInjSup22 |  |  |  |
| `EditTempAcquaKeyPress` | 0x004BCB78 | 479 | INFERIDO | toca EditPressRid, EditSogliaMap, EditTempAcqua |  |  |  |
| `EditTempMotoreKeyPress` | 0x004C8F84 | 348 | INFERIDO | chama TAebNumber::GetInt, TAebNumber::SetInt; toca EditDeltaTInterruzioneWarmup, EditTempMotore |  |  |  |
| `EditTempoBenzinaKeyPress` | 0x004C0570 | 127 | INFERIDO | sem fatos diretos extraíveis (127 bytes; chama 4 funções sem nome) |  |  |  |
| `EditTempoMortoBenzKeyPress` | 0x004B11B0 | 162 | INFERIDO | sem fatos diretos extraíveis (162 bytes; chama 3 funções sem nome) |  |  |  |
| `EditTipoIniettoreKeyPress` | 0x004BACE0 | 436 | INFERIDO | toca EditTipoIniettore |  |  |  |
| `EditVH34ParamKeyPress` | 0x004CAE48 | 1 | OBS-COD | Handler vazio: o corpo é só RET (nada acontece neste evento). |  |  |  |
| `FormClose` | 0x00490C98 | 32 | INFERIDO | sem fatos diretos extraíveis (32 bytes; chama 1 funções sem nome) |  |  |  |
| `FormCloseQuery` | 0x004ADE94 | 372 | INFERIDO | chama TFormConfig.ButtonNuoviParametriClick, TFormConfig.ButtonTaraturaClick; toca PageControl, TabKCoefficienti, TabTemp |  |  |  |
| `FormDestroy` | 0x00490BB4 | 187 | INFERIDO | sem fatos diretos extraíveis (187 bytes; chama 1 funções sem nome) |  |  |  |
| `FormKeyDown` | 0x0049EB24 | 1469 | INFERIDO | chama TFormConfig.ButtonNuoviParametriClick, TFormConfig.ButtonTaraturaClick; toca PageControl, TabKCoefficienti, TabTemp |  |  |  |
| `FormResize` | 0x00490CB8 | 4190 | INFERIDO | toca DrawGridAnticipo, DrawGridAnticipoMultipoint, DrawGridContributi, DrawGridEmissioni, DrawGridEmissioniMultipoint, DrawGridK, DrawGridOpenLoop, DrawGridPassaggio |  |  |  |
| `FormShow` | 0x00490028 | 1418 | INFERIDO | usa textos COMBOACCENSIONE0, COMBOACCENSIONE1, COMBOACCENSIONE2, COMBOACCENSIONE3, COMBOACCENSIONE4, COMBOACCENSIONE5; chama TTranslatorExt::GetString; toca ComboCilindri, ComboFullGroup, ComboTipoLambda | COMBOACCENSIONE0, COMBOACCENSIONE1, COMBOACCENSIONE2, COMBOACCENSIONE3, COMBOACCENSIONE4, COMBOACCENSIONE5 |  |  |
| `ImageInjOffClick` | 0x004C81B0 | 264 | INFERIDO | chama TAebNumber::GetEcuData, TAebNumber::SetEcuData, TAebProtocol::IsConnect |  |  |  |
| `ImageInjOnClick` | 0x004C80A8 | 262 | INFERIDO | chama TAebNumber::GetEcuData, TAebNumber::SetEcuData, TAebProtocol::IsConnect |  |  |  |
| `LabGasClick` | 0x004C9A7C | 318 | INFERIDO | chama APIs do Windows Sleep; toca ImageCommutatore, LabGas |  | Sleep | PostMessageW |
| `LabelDiagnosiClick` | 0x004CB060 | 165 | INFERIDO | chama TFormDiagnosi.Create; toca TimerDati |  |  |  |
| `MenuAebBeeClick` | 0x004AE414 | 1 | OBS-COD | Handler vazio: o corpo é só RET (nada acontece neste evento). |  |  |  |
| `MenuComunicazioneSerialeCostruitaClick` | 0x004AE008 | 1033 | INFERIDO | chama APIs do Windows CloseHandle, CreateProcessW, WaitForSingleObject; chama TAebProtocol::Connect, TAebProtocol::Disconnect, TAebProtocol::IsConnect, TAebProtocol::OffLine, TAebProtocol::ReadPort |  | CloseHandle, CreateProcessW, WaitForSingleObject |  |
| `MenuControlloIniettoriClick` | 0x004BA408 | 54 | INFERIDO | toca MenuControlloIniettori |  |  | PostMessageW |
| `MenuCopiaTaraturaClick` | 0x004AD0B0 | 1031 | INFERIDO | toca DrawGridK, RadioMapAdatta, RadioMapPetrol |  |  |  |
| `MenuEsciClick` (sem evento no DFM) | 0x004ACF60 | 6 | INFERIDO | sem fatos diretos extraíveis (6 bytes; chama 1 funções sem nome) |  |  |  |
| `MenuIncollaTaraturaClick` | 0x004AD4B8 | 2097 | INFERIDO | sem fatos diretos extraíveis (2097 bytes; chama 20 funções sem nome) |  |  |  |
| `MenuSbloccaLimitiClick` | 0x004A95C8 | 26 | INFERIDO | toca MenuSbloccaLimiti |  |  |  |
| `PageControlChanging` | 0x004ADD20 | 372 | INFERIDO | chama TFormConfig.ButtonNuoviParametriClick, TFormConfig.ButtonTaraturaClick; toca PageControl, TabKCoefficienti, TabTemp |  |  |  |
| `PageControlDrawTab` | 0x0049E83C | 741 | INFERIDO | toca PageControl |  |  |  |
| `PopupMenuGridPopup` | 0x004ADCEC | 51 | INFERIDO | toca MenuIncolla |  |  |  |
| `RadioAdatta25Click` | 0x004C05F0 | 251 | INFERIDO | sem fatos diretos extraíveis (251 bytes; chama 3 funções sem nome) |  |  |  |
| `RadioAdattaDirittiClick` | 0x004C06EC | 226 | INFERIDO | sem fatos diretos extraíveis (226 bytes; chama 3 funções sem nome) |  |  |  |
| `RadioGasAltiGiriClick` | 0x004BFEF4 | 798 | INFERIDO | toca EditGiriPerBenzina, EditGiriSupBenzina, EditTempoBenzina1 |  |  | PostMessageW |
| `RadioGasClick` | 0x004BFAD8 | 906 | INFERIDO | toca EditGiriMinimoPerBenzina, EditIniettate, EditIniettateCutoff, EditUscita, LabGiriMinimoPerBenzina, LabelGiriMinimoPerBenzina, LabelIniettate, RadioGas |  |  | PostMessageW |
| `RadioMapClick` | 0x004B23A0 | 362 | INFERIDO | toca DrawGridK, TabKCoefficienti |  |  |  |
| `TabAdattaShow` | 0x004C0854 | 1254 | INFERIDO | toca CheckAdattaBenzina, CheckAdattativita, CheckAzzeraBlackListObd, CheckAzzeraErroriObd, CheckDeleteErrorMotoreSpento, CheckOutDiagInfoSuKLine, CheckPetrolObdConnection, CheckUsaCanPerObd |  |  |  |
| `TabAdvancedShow` | 0x004C9BBC | 95 | INFERIDO | toca CheckContributi |  |  |  |
| `TabAutoCalNewShow` | 0x004CB1C4 | 314 | INFERIDO | toca AutoCalPage |  |  |  |
| `TabBenzinaShow` | 0x004BF004 | 262 | INFERIDO | toca EditGiriMinimoPerBenzina, EditGiriPerBenzina, EditGiriSupBenzina, EditTempoBenzina1 |  |  |  |
| `TabCalibrationShow` | 0x004C7904 | 268 | INFERIDO | toca ShapeInjA, ShapeInjB, ShapeInjC, ShapeInjD, ShapeInjE, ShapeInjF, ShapeInjG, ShapeInjH |  |  |  |
| `TabCambioShow` | 0x004A6ACC | 2652 | INFERIDO | usa textos LABELTAGLIAGRANDE, LABELTAGLIAMEDIA, LABELTAGLIAPICCOLA; chama TTranslatorExt::GetString; toca ComboCarburante, ComboFullGroup, ComboTaglia, ComboTipoIniettore, EditTipoIniettore, LabelTaglia | LABELTAGLIAGRANDE, LABELTAGLIAMEDIA, LABELTAGLIAPICCOLA |  |  |
| `TabConsumometroShow` | 0x004C1044 | 178 | INFERIDO | toca CheckConsumometro, ComboTagliaIniettori, EditLitriSerbatoio |  |  |  |
| `TabDhlpAgaslevShow` | 0x004C8EC4 | 1 | OBS-COD | Handler vazio: o corpo é só RET (nada acontece neste evento). |  |  |  |
| `TabEmissioniShow` | 0x004A8964 | 722 | INFERIDO | toca EditCorrIntInf, EditCorrIntSup, EditLivEmulAlto, EditLivEmulBasso, EditLivEmulCutoff, EditLivEmulStabilizzato, EditNumeroIniettateSmagr, EditRitardoGiriEmulazione |  |  |  |
| `TabEsciShow` | 0x004AE418 | 6 | INFERIDO | sem fatos diretos extraíveis (6 bytes; chama 1 funções sem nome) |  |  |  |
| `TabFrestShow` | 0x004C6324 | 52 | INFERIDO | toca EditlFrestGasNormTemp |  |  |  |
| `TabIniezioneShow` | 0x004AB8BC | 2768 | INFERIDO | usa textos LABELDURATAATTIVAZIONEINJ, LABELPICCORISCALDAMENTO; chama TTranslatorExt::GetString; toca CheckIncrementoMaxCorrente, CheckRiscaldamento, CheckboxPreheatAsyncMode, CheckboxPreheatOnlyAfterSleepMode, EditAperturaExtra, EditAperturaInj, EditChiusuraInj, EditLimiteRitardoAttivWarmUp | LABELDURATAATTIVAZIONEINJ, LABELPICCORISCALDAMENTO |  |  |
| `TabKCoefficientiShow` | 0x004B0EA0 | 550 | INFERIDO | toca ButtonTaratura, EditAperturaInj, PageControlK, RadioMapAdatta, RadioMapAlcol, RadioMapPetrol, TabCorrettori, TabKFactor |  |  |  |
| `TabKFactorShow` | 0x004C49FC | 1464 | INFERIDO | toca ComboK, EditK1, EditK2, EditKMappaNeutro, EditTInj1, EditTInj2, LabelInjK, LabelK |  |  |  |
| `TabKTempiShow` | 0x004B1254 | 150 | INFERIDO | toca EditTempoBenzExtra, EditTempoGasExtra, EditTempoMortoBenz, EditTempoMortoGas |  |  |  |
| `TabKVarieShow` | 0x004B94C8 | 239 | INFERIDO | toca EditFiltro, EditGiriAltiKFiltro, EditGiriKFiltro, EditKFiltro, EditKFiltroGiriAlti, EditSogliaFS2 |  |  |  |
| `TabLambdaShow` | 0x004A6770 | 653 | INFERIDO | toca CheckFlashLube, ComboNumeroSonde, ComboSonda1, ComboSonda2, ComboTipoLambda, EditRifInfCalda, EditRifInfFredda, EditRifLambda |  |  |  |
| `TabPressioneShow` | 0x004AE788 | 2153 | INFERIDO | usa textos COMBOPRESSIONEMISURATA0, COMBOPRESSIONEMISURATA1, COMBOPRESSIONEMISURATA2, COMBOPRESSIONEMISURATA3, LABELTIPOPRESSIONEMIS, LABELTIPOPRESSIONEMIS1; chama TTranslatorExt::GetString; toca CheckMapConfigurato, ComboMisuratorePressione, ComboPressioneMisurata, ComboTipoMisuratore, EditOffset, EditOffsetDiff, EditPendenza, EditPendenzaDiff | COMBOPRESSIONEMISURATA0, COMBOPRESSIONEMISURATA1, COMBOPRESSIONEMISURATA2, COMBOPRESSIONEMISURATA3, LABELTIPOPRESSIONEMIS, LABELTIPOPRESSIONEMIS1 |  |  |
| `TabSensoreShow` | 0x004AA5BC | 3924 | INFERIDO | usa textos CHECKALIMENTAZIONEFILODEDICATO, CHECK_STAP_ON_EV2, COMBOSENSORE1, COMBOSENSORE2; chama TFormConfig.CheckRitornoBenzinaClick, TTranslatorExt::GetString; toca CheckAlimentazione, CheckAttivaPompaInRiserva, CheckBassaPressione, CheckBoxEV2NotConnected, CheckEnabledDhlpDiagnosis, CheckPassPRail, CheckRiconoscimentoPieno, CheckRitornoBenzina | CHECKALIMENTAZIONEFILODEDICATO, CHECK_STAP_ON_EV2, COMBOSENSORE1, COMBOSENSORE2 |  |  |
| `TabSwitchToPetrolShow` | 0x004C65A4 | 361 | INFERIDO | toca EditSwitchToPetrol0, EditSwitchToPetrol1, EditSwitchToPetrol2, EditSwitchToPetrol3, EditSwitchToPetrol4, EditSwitchToPetrol5, EditSwitchToPetrol6, EditSwitchToPetrol7 |  |  |  |
| `TabTaraturaShow` | 0x004A9CD0 | 242 | INFERIDO | toca EditDeltaK, EditGiriAutotaratura, EditNumControlli, EditNumControlliOk, EditScartoMinAlMin, EditScartoMinFuoriMin, EditTempTaratura |  |  |  |
| `TabTempShow` | 0x004B92C8 | 129 | INFERIDO | toca CheckFlashLubeSuTempAcqua, CheckTempAcqua, CheckTempGas, CheckTempMotore |  |  |  |
| `TabTestShow` | 0x004BA440 | 114 | INFERIDO | toca EditTestByte, EditTestTempo, EditTestWord |  |  |  |
| `TimerDatiTimer` | 0x004A307C | 1586 | INFERIDO | chama TStreamDati.TimerIOTimer; toca LabApMot, LabApMot2, LabApMot2Benz, LabApMotBenz, LabContribBenz, LabDeltaGiri, LabGiri, LabLambda |  |  |  |
| `chkRitardoPassaggioInMinutiClick` | 0x004CB300 | 39 | INFERIDO | toca chkRitardoPassaggioInMinuti |  |  |  |

### Cadeia de subfunções sem nome alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonAccettaClick` (0x004A8D74):
  - sub_40487C (45 B)
  - sub_4306C0 (116 B): chama TAebVector::SetEcuData
  - sub_434440 (21 B)
  - sub_96B924 (79 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `ButtonCommutatoreClick` (0x004B1178):
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  - … +22 (ver telas.json)
- `ButtonDoLevelClick` (0x004C5C30):
  - sub_40487C (45 B)
  - sub_434440 (21 B)
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_96B97C (174 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - … +14 (ver telas.json)
- `ButtonMaxLevelClick` (0x004C5ADC):
  - sub_40487C (45 B)
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -     sub_42A52C (358 B)
  -     sub_433FFC (14 B)
  - … +3 (ver telas.json)
- `ButtonMinLevelClick` (0x004C598C):
  - sub_40487C (45 B)
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -     sub_42A52C (358 B)
  -     sub_433FFC (14 B)
  - … +3 (ver telas.json)
- `ButtonModificaInjClick` (0x004BC47C):
  - sub_40487C (45 B)
  - sub_433AEC (26 B)
  - sub_433B08 (144 B): chama TAebMatrix::SetEcuData; toca EditRifSupCalda
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `ButtonModificaRifTarClick` (0x004B10C8):
  - sub_49F170 (277 B)
  -   sub_42ED90 (36 B)
  -   sub_42EE14 (100 B)
  -   sub_42F338 (7 B)
  -   sub_42F340 (7 B)
  -     sub_433FFC (14 B)
- `ButtonNuoviParametriClick` (0x004B9000):
  - sub_40487C (45 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  - … +22 (ver telas.json)
- `ButtonRicercaParametriClick` (0x004AF3A8):
  - sub_40487C (45 B)
  - sub_42A8F4 (235 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_4B0E45 (88 B)
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  -   sub_433FFC (14 B)
- `ButtonSequenceAcquisitionClick` (0x004A62A8):
  - sub_40487C (45 B)
  - sub_96C73C (114 B)
- `ButtonSequenzaInjClick` (0x004BC2DC):
  - sub_40487C (45 B)
  - sub_434218 (261 B): chama TAebProtocol::GetComFail,TAebVector::GetEcuData
  - sub_434320 (285 B): chama TAebVector::GetEcuData
  - sub_4BC72C (297 B)
  -   sub_433B08 (144 B): chama TAebMatrix::SetEcuData; toca EditRifSupCalda
  -   sub_4048AC (68 B)
  -   sub_433AEC (26 B)
  -   sub_4BC58C (415 B)
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_43400C (7 B)
- `ButtonSwitchSettingsClick` (0x004C90E0):
  - sub_40487C (45 B)
  - sub_412430 (1643 B): textos MESSERRORGIRISWITCHCALIBRATION,SWITCHSETTINGSBUTTONEXIT,SWITCHSETTINGSCAPTION,SWITCHSETTINGSLABELBUZZER; APIs Sleep; chama TAboutBox.Create,TAebProtocol::SendCommand,TTranslatorExt::GetString; toca LabelV2,MenuComunicazioneSerialeCostruita,PageControl,TabCambio
  - sub_434AC8 (34 B)
  -   sub_96B924 (79 B)
  -   sub_96B97C (174 B)
  -     sub_4080D4 (67 B)
  -     sub_96BD74 (19 B)
  -     sub_981F2C (447 B)
- `ButtonTaraturaClick` (0x004ACFC0):
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  - … +22 (ver telas.json)
- `CheckAdattaBenzinaClick` (0x004C0E34):
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  - … +22 (ver telas.json)
- `CheckAdattativitaClick` (0x004C0D50):
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  - … +22 (ver telas.json)
- `CheckAlimentazioneClick` (0x004C6780):
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_431878 (110 B): chama TAebVector::GetEcuData; toca MenuCopia
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData; toca MenuCopia,MenuIncolla
  - sub_434BA0 (34 B)
  - sub_4A9DC4 (2036 B): toca CheckAlimentazione,CheckElettrovalvole,EditAnticipoEV1,EditAnticipoEV2
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - … +41 (ver telas.json)
- `CheckAntiStalloClick` (0x004C982C):
  - sub_42E754 (385 B)
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  - sub_434AEC (34 B)
  - sub_96C73C (114 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckAttivaPompaInRiservaClick` (0x004CB030):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckAzzeraBlackListObdClick` (0x004C1ADC):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckAzzeraErroriObdClick` (0x004C1AAC):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckBoxEV2NotConnectedClick` (0x004C6710):
  - sub_431878 (110 B): chama TAebVector::GetEcuData; toca MenuCopia
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData; toca MenuCopia,MenuIncolla
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckBoxEnDiagDtcPowertrainClick` (0x004CB358):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckBoxEnableExtLedKLineClick` (0x004CB328):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckCambioConSovrappClick` (0x004C8B04):
  - sub_42E754 (385 B)
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckConsumometroClick` (0x004C1108):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  - … +21 (ver telas.json)
- `CheckContributiClick` (0x004C9C1C):
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_42DF14 (599 B)
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  - sub_489034 (110 B)
  - sub_4893D4 (60 B)
  - sub_489410 (60 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckDeleteErrorMotoreSpentoClick` (0x004C1BA4):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckDisabilitaCommutatoreClick` (0x004C1C04):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckElettrovalvoleClick` (0x004C5048):
  - sub_40487C (45 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_42E754 (385 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - sub_4A9DC4 (2036 B): toca CheckAlimentazione,CheckElettrovalvole,EditAnticipoEV1,EditAnticipoEV2
  - sub_96BB88 (321 B)
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  - … +46 (ver telas.json)
- `CheckEnabledDhlpDiagnosisClick` (0x004C975C):
  - sub_431878 (110 B): chama TAebVector::GetEcuData; toca MenuCopia
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData; toca MenuCopia,MenuIncolla
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckFlashLubeClick` (0x004C88AC):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - … +3 (ver telas.json)
- `CheckFlexFuelClick` (0x004C2C9C):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckGiriAlberoCammeClick` (0x004C850C):
  - sub_42B834 (160 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_96B97C (174 B)
  - sub_96C73C (114 B)
  -   sub_438204 (205 B): APIs GetTickCount,Sleep; chama TAebProtocol::Connect,TAebProtocol::DisableErrorHandling,TAebProtocol::Disconnect,TAebProtocol::EnableErrorHandling
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  - … +17 (ver telas.json)
- `CheckGiriRuotaFonicaClick` (0x004C86D4):
  - sub_42B834 (160 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_96B97C (174 B)
  - sub_96C73C (114 B)
  -   sub_438204 (205 B): APIs GetTickCount,Sleep; chama TAebProtocol::Connect,TAebProtocol::DisableErrorHandling,TAebProtocol::Disconnect,TAebProtocol::EnableErrorHandling
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  - … +17 (ver telas.json)
- `CheckIncrementoMaxCorrenteClick` (0x004C721C):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckInibisciEmulazioneClick` (0x004B9280):
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  - … +22 (ver telas.json)
- `CheckIniezioneContinuaClick` (0x004BC858):
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_96BB88 (321 B)
  - sub_96C73C (114 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_96BD74 (19 B)
  - … +12 (ver telas.json)
- `CheckInjAcquisitionClick` (0x004A61AC):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  - sub_96C73C (114 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckIstantaneoClick` (0x004BFA68):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  - … +21 (ver telas.json)
- `CheckMapConfiguratoClick` (0x004BCB40):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_4AF018 (840 B): toca ButtonRicercaParametri,CheckMapConfigurato,EditPressRid,EditSogliaMap
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  - … +27 (ver telas.json)
- `CheckObdClick` (0x004CAE4C):
  - sub_434568 (20 B)
  - sub_43457C (274 B): chama TAebProtocol::Connect,TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebProtocol::SendCommand
- `CheckOutDiagInfoSuKLineClick` (0x004C1B74):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckPartenzaGasCaldoClick` (0x004C4FB8):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  - sub_96C73C (114 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckParzializzaClick` (0x004BFAA0):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  - … +21 (ver telas.json)
- `CheckPassPRailClick` (0x004BCB10):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  - … +21 (ver telas.json)
- `CheckPetrolObdConnectionClick` (0x004C1B0C):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckProgressioniClick` (0x004CA544):
  - sub_42CFB8 (839 B)
  - sub_42E754 (385 B)
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  - sub_43481C (34 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - sub_96BB88 (321 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  - … +11 (ver telas.json)
- `CheckRiconoscimentoPienoClick` (0x004CB12C):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckRiscaldamentoClick` (0x004C6A64):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_42DF14 (599 B)
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  - sub_432CAC (16 B)
  - sub_434840 (34 B)
  - sub_434A80 (34 B)
  - sub_489034 (110 B)
  - sub_489398 (60 B)
  - sub_4893D4 (60 B)
  - … +6 (ver telas.json)
- `CheckRitornoBenzinaClick` (0x004A9484):
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_96B97C (174 B)
  - sub_96C73C (114 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4080D4 (67 B)
  - … +13 (ver telas.json)
- `CheckSegnaleGiriClick` (0x004C5494):
  - sub_40487C (45 B)
  - sub_42B834 (160 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - sub_42CFB8 (839 B)
  - sub_42DF14 (599 B)
  - sub_42E754 (385 B)
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  - sub_4310E8 (14 B)
  - sub_4310F8 (103 B): chama TAebNumber::SetEcuData
  - sub_4348AC (34 B)
  - sub_4348F4 (34 B)
  - sub_434AA4 (34 B)
  - sub_434CC0 (834 B)
  - … +16 (ver telas.json)
- `CheckSequenzaInjClick` (0x004BC008):
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  - sub_433AEC (26 B)
  - sub_43414C (110 B)
  - sub_4341BC (89 B)
  - sub_43481C (34 B)
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  - … +29 (ver telas.json)
- `CheckSpegniBuzzerClick` (0x004C1C34):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckSplitFuelClick` (0x004C1C64):
  - sub_42CFB8 (839 B)
  - sub_42DF14 (599 B)
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  - sub_42FFC8 (16 B)
  - sub_432A74 (100 B): chama TAebVector::SetEcuData
  - sub_432B1C (184 B): chama TAebVector::SetEcuData
  - sub_4A1880 (80 B)
  - sub_96C73C (114 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_432AD8 (47 B)
- `CheckStartAndStopClick` (0x004C2BD8):
  - sub_40487C (45 B)
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckTagliaExtraIniettateClick` (0x004C1BD4):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckTaglioPompaBenzinaClick` (0x004C8B68):
  - sub_40487C (45 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  - sub_42E754 (385 B)
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  - sub_431878 (110 B): chama TAebVector::GetEcuData; toca MenuCopia
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData; toca MenuCopia,MenuIncolla
  - sub_43400C (7 B)
  - sub_434A14 (34 B)
  - sub_434A38 (34 B)
  - sub_434BA0 (34 B)
  - sub_434BC4 (34 B)
  - … +47 (ver telas.json)
- `CheckTempAcquaClick` (0x004B8B30):
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_431878 (110 B): chama TAebVector::GetEcuData; toca MenuCopia
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData; toca MenuCopia,MenuIncolla
  - sub_434548 (14 B)
  - sub_96C73C (114 B)
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - … +26 (ver telas.json)
- `CheckTempMotoreClick` (0x004B8E88):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_96C73C (114 B)
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  - … +22 (ver telas.json)
- `CheckTempRiduttoreClick` (0x004B8E10):
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_96C73C (114 B)
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  - … +23 (ver telas.json)
- `CheckUsaCanPerObdClick` (0x004C1B3C):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckValvetronikClick` (0x004C2B14):
  - sub_40487C (45 B)
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckboxPreheatAsyncModeClick` (0x004C72D0):
  - sub_42DF14 (599 B)
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  - sub_432CAC (16 B)
  - sub_432CBC (84 B): chama TAebVector::SetEcuData; toca LabelTinjBenzPerc
  - sub_48944C (154 B)
  - sub_96C73C (114 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckboxPreheatOnlyAfterSleepModeClick` (0x004C724C):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  - sub_432CAC (16 B)
  - sub_432CBC (84 B): chama TAebVector::SetEcuData; toca LabelTinjBenzPerc
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `ComboAccensioneKeyPress` (0x004BA230):
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
- `ComboCambioKeyPress` (0x004A8FB8):
  - sub_40487C (45 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_43074C (41 B)
  - sub_4A90B8 (957 B): toca EditCicchetto,EditCicchettoInj,EditCicchettoNum,EditCicchettoRpm
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  - … +37 (ver telas.json)
- `ComboCarburanteKeyPress` (0x004B96DC):
  - sub_40487C (45 B)
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_42FFC8 (16 B)
  - sub_43362C (14 B)
  - sub_439E94 (288664 B): APIs Sleep,SendMessageW; chama TAebMatrix::GetEcuData,TAebMatrix::SetColumnDimension,TAebMatrix::SetEcuData,TAebNumber::GetEcuData; toca EditAnticipoEV2,EditPressRid
  - sub_48944C (154 B)
  - sub_49F170 (277 B)
  - sub_49F804 (216 B)
  - sub_4A0378 (199 B)
  - sub_4A0820 (199 B)
  - sub_4A1458 (80 B)
  - … +54 (ver telas.json)
- `ComboCilindriKeyPress` (0x004BA2EC):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_43414C (110 B)
  - sub_4341BC (89 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  - … +24 (ver telas.json)
- `ComboKKeyPress` (0x004C4880):
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -     sub_42A52C (358 B)
  -     sub_433FFC (14 B)
  -     sub_42B224 (15 B)
  - … +2 (ver telas.json)
- `ComboNumeroSondeKeyPress` (0x004A9664):
  - sub_433938 (128 B): chama TAebVector::SetEcuData
  - sub_4A96A4 (563 B): toca EditCorrBancata2,LabApMot,LabApMot2,LabApMot2Benz
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_433918 (29 B)
  -   sub_434530 (21 B)
  -   sub_434CC0 (834 B)
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_96B97C (174 B)
  -   sub_96C73C (114 B)
  -     sub_42A788 (358 B)
  - … +7 (ver telas.json)
- `ComboPressioneKeyPress` (0x004AE420):
  - sub_40487C (45 B)
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_4310E8 (14 B)
  - sub_4310F8 (103 B): chama TAebNumber::SetEcuData
  - sub_4AF018 (840 B): toca ButtonRicercaParametri,CheckMapConfigurato,EditPressRid,EditSogliaMap
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - … +30 (ver telas.json)
- `ComboSegnaleGiriKeyPress` (0x004ACDC8):
  - sub_43109C (73 B): chama TAebNumber::SetEcuData
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_96BB88 (321 B)
  - sub_96C73C (114 B)
  -   sub_42B224 (15 B)
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - … +7 (ver telas.json)
- `ComboSensoreKeyPress` (0x004A8C60):
  - sub_4304F0 (411 B): chama TAebNumber::SetEcuData; toca EditCorrBancata2
  - sub_4A8CB8 (186 B): toca EditLivello1,EditLivello2,EditLivello3,EditLivello4
  -   sub_42B224 (15 B)
  -   sub_42CFB8 (839 B)
  -   sub_4306C0 (116 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_4304E8 (7 B)
  -   sub_43068C (51 B)
  -   sub_96B97C (174 B)
  -     sub_434440 (21 B)
  -     sub_4080D4 (67 B)
  - … +2 (ver telas.json)
- `ComboSfasaturaKeyPress` (0x004BD748):
  - sub_40487C (45 B)
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  - sub_433AEC (26 B)
  - sub_433B08 (144 B): chama TAebMatrix::SetEcuData; toca EditRifSupCalda
  - sub_43414C (110 B)
  - sub_4341BC (89 B)
  - sub_434218 (261 B): chama TAebProtocol::GetComFail,TAebVector::GetEcuData
  - sub_434320 (285 B): chama TAebVector::GetEcuData
  - sub_43481C (34 B)
  - … +33 (ver telas.json)
- `ComboSonda1KeyPress` (0x004C2D94):
  - sub_433A00 (75 B)
  - sub_433A4C (160 B): chama TAebNumber::SetEcuData; toca LabelLivEmulBasso
  - sub_4A96A4 (563 B): toca EditCorrBancata2,LabApMot,LabApMot2,LabApMot2Benz
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_433918 (29 B)
  -   sub_434530 (21 B)
  -   sub_434CC0 (834 B)
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_96B97C (174 B)
  -   sub_96C73C (114 B)
  - … +8 (ver telas.json)
- `ComboTagliaIniettoriKeyPress` (0x004C1138):
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -     sub_42A52C (358 B)
  -     sub_433FFC (14 B)
  -     sub_42B224 (15 B)
  - … +2 (ver telas.json)
- `ComboTagliaKeyPress` (0x004BCD94):
  - sub_42B834 (160 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - sub_42CFB8 (839 B)
  - sub_42FFC8 (16 B)
  - sub_43362C (14 B)
  - sub_43363C (110 B): chama TAebNumber::SetEcuData; toca TimerDati
  - sub_43400C (7 B)
  - sub_439358 (2592 B): chama TAebMatrix::SetDefValue,TAebVector::SetDefValue
  -   sub_438204 (205 B): APIs GetTickCount,Sleep; chama TAebProtocol::Connect,TAebProtocol::DisableErrorHandling,TAebProtocol::Disconnect,TAebProtocol::EnableErrorHandling
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -     sub_4382D4 (119 B): APIs GetTickCount,Sleep; chama TAebNumber::GetEcuData
- `ComboTempRiduttoreKeyPress` (0x004B9494):
  - sub_430878 (403 B): chama TAebNumber::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_432024 (38 B)
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432420 (63 B)
  -   sub_432464 (178 B): chama TAebVector::SetEcuData; toca MainMenu1
  -   sub_434CC0 (834 B)
  - … +9 (ver telas.json)
- `ComboTipoIniettoreKeyPress` (0x004BA4B4):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_42B834 (160 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - sub_42CFB8 (839 B)
  - sub_42FFC8 (16 B)
  - sub_42FFD8 (84 B): chama TAebVector::SetEcuData
  - sub_43362C (14 B)
  - sub_43400C (7 B)
  - sub_439358 (2592 B): chama TAebMatrix::SetDefValue,TAebVector::SetDefValue
  - sub_439E94 (288664 B): APIs Sleep,SendMessageW; chama TAebMatrix::GetEcuData,TAebMatrix::SetColumnDimension,TAebMatrix::SetEcuData,TAebNumber::GetEcuData; toca EditAnticipoEV2,EditPressRid
  - sub_489034 (110 B)
  - … +48 (ver telas.json)
- `ComboTipoLambdaKeyPress` (0x004A6674):
  - sub_43405C (237 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  -   sub_42F83C (275 B): chama TAebVector::SetEcuData
  -   sub_42FA10 (307 B): chama TAebMatrix::SetEcuData
  -   sub_433FCC (47 B)
  -   sub_43402C (21 B)
  -   sub_434044 (21 B)
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -     sub_42B224 (15 B)
  - … +2 (ver telas.json)
- `ComboTipoMisuratoreKeyPress` (0x004BEC34):
  - sub_40487C (45 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_43048C (91 B): chama TAebVector::SetEcuData
  - sub_430878 (403 B): chama TAebNumber::SetEcuData
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  - … +24 (ver telas.json)
- `ComboTipoObdKeyPress` (0x004C0F18):
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -     sub_42A52C (358 B)
  -     sub_433FFC (14 B)
  -     sub_42B224 (15 B)
  - … +2 (ver telas.json)
- `ComboTurboKeyPress` (0x004B9C44):
  - sub_40487C (45 B)
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_42FFC8 (16 B)
  - sub_43362C (14 B)
  - sub_439E94 (288664 B): APIs Sleep,SendMessageW; chama TAebMatrix::GetEcuData,TAebMatrix::SetColumnDimension,TAebMatrix::SetEcuData,TAebNumber::GetEcuData; toca EditAnticipoEV2,EditPressRid
  - sub_48944C (154 B)
  - sub_49F170 (277 B)
  - sub_49F804 (216 B)
  - sub_4A0378 (199 B)
  - sub_4A0820 (199 B)
  - sub_4A1458 (80 B)
  - … +54 (ver telas.json)
- `DrawDelayGasTempDblClick` (0x004C42E4):
  - sub_4C4348 (1317 B)
  -   sub_404C40 (81 B)
  -   sub_405754 (48 B)
  -   sub_4057C0 (139 B)
  -   sub_431F34 (54 B)
  -   sub_431F6C (181 B): chama TAebMatrix::SetEcuData
  -   sub_432024 (38 B)
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_489034 (110 B)
  -   sub_489320 (60 B)
  -   sub_48935C (60 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  - … +6 (ver telas.json)
- `DrawDelayGasTempDrawCell` (0x004C3EA4):
  - sub_40487C (45 B)
  - sub_431F34 (54 B)
  - sub_432024 (38 B)
  -   sub_42A52C (358 B)
- `DrawDelayGasTempKeyPress` (0x004C42D4):
  - sub_4C4348 (1317 B)
  -   sub_404C40 (81 B)
  -   sub_405754 (48 B)
  -   sub_4057C0 (139 B)
  -   sub_431F34 (54 B)
  -   sub_431F6C (181 B): chama TAebMatrix::SetEcuData
  -   sub_432024 (38 B)
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_489034 (110 B)
  -   sub_489320 (60 B)
  -   sub_48935C (60 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  - … +6 (ver telas.json)
- `DrawGridAnticipoDblClick` (0x004BD2E4):
  - sub_4BD358 (991 B): toca DrawGridAnticipo,TimerDati
  -   sub_431D08 (53 B)
  -   sub_431D58 (178 B): chama TAebVector::SetEcuData; toca ComboNumeroSonde
  -   sub_431E24 (16 B)
  -   sub_431E34 (97 B): chama TAebVector::SetEcuData; toca TabEmissioni
  -   sub_431E98 (26 B)
  -   sub_431EB4 (128 B): chama TAebMatrix::SetEcuData; toca LabelLivEmulAlto
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  - … +2 (ver telas.json)
- `DrawGridAnticipoDrawCell` (0x004BCF1C):
  - sub_40487C (45 B)
  - sub_431D08 (53 B)
  - sub_431E24 (16 B)
  - sub_431E98 (26 B)
- `DrawGridAnticipoKeyPress` (0x004BD348):
  - sub_4BD358 (991 B): toca DrawGridAnticipo,TimerDati
  -   sub_431D08 (53 B)
  -   sub_431D58 (178 B): chama TAebVector::SetEcuData; toca ComboNumeroSonde
  -   sub_431E24 (16 B)
  -   sub_431E34 (97 B): chama TAebVector::SetEcuData; toca TabEmissioni
  -   sub_431E98 (26 B)
  -   sub_431EB4 (128 B): chama TAebMatrix::SetEcuData; toca LabelLivEmulAlto
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  - … +2 (ver telas.json)
- `DrawGridAnticipoMultipointDblClick` (0x004C3C38):
  - sub_4C3C9C (405 B): toca DrawGridAnticipoMultipoint,DrawGridEmissioni,TimerDati
  -   sub_42FC4C (16 B)
  -   sub_42FC5C (97 B): chama TAebVector::SetEcuData
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_4080D4 (67 B)
- `DrawGridAnticipoMultipointDrawCell` (0x004C3944):
  - sub_40487C (45 B)
  - sub_42FC4C (16 B)
  - sub_4A6168 (67 B)
- `DrawGridAnticipoMultipointKeyPress` (0x004C3C28):
  - sub_4C3C9C (405 B): toca DrawGridAnticipoMultipoint,DrawGridEmissioni,TimerDati
  -   sub_42FC4C (16 B)
  -   sub_42FC5C (97 B): chama TAebVector::SetEcuData
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_4080D4 (67 B)
- `DrawGridBenzinaDblClick` (0x004C157C):
  - sub_4C15F0 (1196 B)
  -   sub_432F00 (47 B)
  -   sub_432F44 (184 B): chama TAebVector::SetEcuData; toca LabelV5
  -   sub_43300C (16 B)
  -   sub_43301C (107 B): chama TAebVector::SetEcuData; toca LabelRifSupCalda
  -   sub_433088 (104 B)
  -   sub_43310C (271 B): chama TAebMatrix::SetEcuData; toca LabelRifInfCalda
  -   sub_4A0820 (199 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - … +4 (ver telas.json)
- `DrawGridBenzinaDrawCell` (0x004C11B8):
  - sub_40487C (45 B)
  - sub_432F00 (47 B)
  - sub_43300C (16 B)
  - sub_433088 (104 B)
  -   sub_433FFC (14 B)
- `DrawGridBenzinaKeyPress` (0x004C15E0):
  - sub_4C15F0 (1196 B)
  -   sub_432F00 (47 B)
  -   sub_432F44 (184 B): chama TAebVector::SetEcuData; toca LabelV5
  -   sub_43300C (16 B)
  -   sub_43301C (107 B): chama TAebVector::SetEcuData; toca LabelRifSupCalda
  -   sub_433088 (104 B)
  -   sub_43310C (271 B): chama TAebMatrix::SetEcuData; toca LabelRifInfCalda
  -   sub_4A0820 (199 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - … +4 (ver telas.json)
- `DrawGridContributiDblClick` (0x004CA288):
  - sub_4CA2EC (583 B): toca DrawGridContributi,TimerDati
  -   sub_42F22C (40 B)
  -   sub_42F254 (217 B): chama TAebMatrix::SetEcuData
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_4080D4 (67 B)
- `DrawGridContributiDrawCell` (0x004C9DF0):
  - sub_40487C (45 B)
  - sub_4080D4 (67 B)
  - sub_42DF14 (599 B)
  - sub_42ED90 (36 B)
  - sub_42EE14 (100 B)
  - sub_42F22C (40 B)
  -   sub_42F340 (7 B)
  -   sub_42F338 (7 B)
  -   sub_433FFC (14 B)
- `DrawGridContributiKeyPress` (0x004CA278):
  - sub_4CA2EC (583 B): toca DrawGridContributi,TimerDati
  -   sub_42F22C (40 B)
  -   sub_42F254 (217 B): chama TAebMatrix::SetEcuData
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_4080D4 (67 B)
- `DrawGridEmissioniDblClick` (0x004B4530):
  - sub_4B4594 (1050 B): chama TFormCentriCelle.Create; toca DrawGridEmissioni,TimerDati
  -   sub_42F7C4 (94 B)
  -   sub_42F83C (275 B): chama TAebVector::SetEcuData
  -   sub_42FC4C (16 B)
  -   sub_42FC5C (97 B): chama TAebVector::SetEcuData
  -   sub_434044 (21 B)
  -   sub_49F804 (216 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  - … +4 (ver telas.json)
- `DrawGridEmissioniDrawCell` (0x004B3F68):
  - sub_40487C (45 B)
  - sub_42F758 (16 B)
  - sub_42F7C4 (94 B)
  - sub_42FC4C (16 B)
  - sub_434044 (21 B)
  - sub_4A6168 (67 B)
- `DrawGridEmissioniKeyPress` (0x004B4520):
  - sub_4B4594 (1050 B): chama TFormCentriCelle.Create; toca DrawGridEmissioni,TimerDati
  -   sub_42F7C4 (94 B)
  -   sub_42F83C (275 B): chama TAebVector::SetEcuData
  -   sub_42FC4C (16 B)
  -   sub_42FC5C (97 B): chama TAebVector::SetEcuData
  -   sub_434044 (21 B)
  -   sub_49F804 (216 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  - … +4 (ver telas.json)
- `DrawGridEmissioniMultipointDblClick` (0x004C33E0):
  - sub_49F804 (216 B)
  - sub_4C34D4 (1118 B)
  -   sub_42F758 (16 B)
  -   sub_42FB68 (47 B)
  -   sub_42F7C4 (94 B)
  -   sub_42F83C (275 B): chama TAebVector::SetEcuData
  -   sub_42F974 (131 B)
  -   sub_42FA10 (307 B): chama TAebMatrix::SetEcuData
  -   sub_434044 (21 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  - … +4 (ver telas.json)
- `DrawGridEmissioniMultipointDrawCell` (0x004C2E3C):
  - sub_40487C (45 B)
  - sub_42F758 (16 B)
  - sub_42F7C4 (94 B)
  - sub_42F974 (131 B)
  - sub_42FB68 (47 B)
  - sub_434044 (21 B)
  - sub_4A6168 (67 B)
- `DrawGridEmissioniMultipointKeyPress` (0x004C33D0):
  - sub_4C34D4 (1118 B)
  -   sub_42F7C4 (94 B)
  -   sub_42F83C (275 B): chama TAebVector::SetEcuData
  -   sub_42F974 (131 B)
  -   sub_42FA10 (307 B): chama TAebMatrix::SetEcuData
  -   sub_434044 (21 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_4080D4 (67 B)
  - … +1 (ver telas.json)
- `DrawGridKDblClick` (0x004B1940):
  - sub_4B19DC (865 B): toca ButtonCommutatore,ButtonTaratura,DrawGridK,StaticTaratura
  - sub_4B1D60 (683 B): toca DrawGridK,TimerDati
  - sub_4B201C (865 B): toca ButtonCommutatore,ButtonTaratura,DrawGridK,StaticTaratura
  -   sub_42CFB8 (839 B)
  -   sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42EC94 (65 B)
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_404C40 (81 B)
  -   sub_405754 (48 B)
  -   sub_42F6A8 (30 B)
  -   sub_42F6C8 (143 B): chama TAebMatrix::SetEcuData
  - … +30 (ver telas.json)
- `DrawGridKDrawCell` (0x004B12EC):
  - sub_40487C (45 B)
  - sub_4080D4 (67 B)
  - sub_42EC94 (65 B)
  - sub_42ED90 (36 B)
  - sub_42EE14 (100 B)
  - sub_42F130 (65 B)
  - sub_42F6A8 (30 B)
  - sub_4A6168 (67 B)
  -   sub_42F338 (7 B)
  -   sub_42F340 (7 B)
  -   sub_433FFC (14 B)
- `DrawGridKKeyPress` (0x004B18F4):
  - sub_4B19DC (865 B): toca ButtonCommutatore,ButtonTaratura,DrawGridK,StaticTaratura
  - sub_4B1D60 (683 B): toca DrawGridK,TimerDati
  - sub_4B201C (865 B): toca ButtonCommutatore,ButtonTaratura,DrawGridK,StaticTaratura
  -   sub_42CFB8 (839 B)
  -   sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42EC94 (65 B)
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_404C40 (81 B)
  -   sub_405754 (48 B)
  -   sub_42F6A8 (30 B)
  -   sub_42F6C8 (143 B): chama TAebMatrix::SetEcuData
  - … +30 (ver telas.json)
- `DrawGridOpenLoopDblClick` (0x004B29E0):
  - sub_4B2A44 (784 B): toca ButtonCommutatore,ButtonTaratura,DrawGridOpenLoop,StaticTaratura
  -   sub_42CFB8 (839 B)
  -   sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42EC94 (65 B)
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -   sub_42EF74 (100 B)
  -   sub_42EFF4 (282 B): chama TAebVector::SetEcuData
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42A788 (358 B)
  -     sub_42A8F4 (235 B)
  -     sub_42B224 (15 B)
  - … +25 (ver telas.json)
- `DrawGridOpenLoopDrawCell` (0x004B25B4):
  - sub_40487C (45 B)
  - sub_42EC94 (65 B)
  - sub_42ED90 (36 B)
  - sub_42EF74 (100 B)
  - sub_4A6168 (67 B)
  -   sub_42F338 (7 B)
  -   sub_42F340 (7 B)
  -   sub_433FFC (14 B)
- `DrawGridOpenLoopKeyPress` (0x004B29D0):
  - sub_4B2A44 (784 B): toca ButtonCommutatore,ButtonTaratura,DrawGridOpenLoop,StaticTaratura
  -   sub_42CFB8 (839 B)
  -   sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42EC94 (65 B)
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -   sub_42EF74 (100 B)
  -   sub_42EFF4 (282 B): chama TAebVector::SetEcuData
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42A788 (358 B)
  -     sub_42A8F4 (235 B)
  -     sub_42B224 (15 B)
  - … +25 (ver telas.json)
- `DrawGridPassaggioDrawCell` (0x004B2D64):
  - sub_40487C (45 B)
- `DrawGridPassaggioGasDblClick` (0x004B3A24):
  - sub_4B3A88 (1229 B)
  -   sub_432E8C (16 B)
  -   sub_432E9C (97 B): chama TAebVector::SetEcuData; toca LabelV3
  -   sub_433560 (63 B)
  -   sub_4335A4 (133 B): chama TAebVector::SetEcuData; toca LabelRifInfFredda
  -   sub_48944C (154 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_42A52C (358 B)
  -     sub_42A698 (235 B)
  - … +1 (ver telas.json)
- `DrawGridPassaggioGasDrawCell` (0x004B3660):
  - sub_40487C (45 B)
  - sub_432E8C (16 B)
  - sub_433560 (63 B)
  -   sub_42A52C (358 B)
- `DrawGridPassaggioGasKeyPress` (0x004B3A14):
  - sub_4B3A88 (1229 B)
  -   sub_432E8C (16 B)
  -   sub_432E9C (97 B): chama TAebVector::SetEcuData; toca LabelV3
  -   sub_433560 (63 B)
  -   sub_4335A4 (133 B): chama TAebVector::SetEcuData; toca LabelRifInfFredda
  -   sub_48944C (154 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_42A52C (358 B)
  -     sub_42A698 (235 B)
  - … +1 (ver telas.json)
- `DrawGridPassaggioKeyPress` (0x004B3118):
  - sub_4B318C (1218 B)
  -   sub_432DF8 (16 B)
  -   sub_432E08 (129 B): chama TAebVector::SetEcuData; toca EditRifInfFredda
  -   sub_433470 (63 B)
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData; toca EditRifSupFredda
  -   sub_48944C (154 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_42A788 (358 B)
  -     sub_42A8F4 (235 B)
  - … +1 (ver telas.json)
- `DrawGridPressAssDblClick` (0x004B6178):
  - sub_4B61DC (691 B): toca DrawGridPressAss,TimerDati
  -   sub_432838 (28 B)
  -   sub_432868 (208 B): chama TAebVector::SetEcuData; toca LabelTempoSondaFredda
  -   sub_43294C (47 B)
  -   sub_432990 (200 B): chama TAebVector::SetEcuData; toca LabelS3
  -   sub_4A161C (80 B)
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_40487C (45 B)
- `DrawGridPressAssDrawCell` (0x004B5E90):
  - sub_40487C (45 B)
  - sub_432838 (28 B)
  - sub_43294C (47 B)
- `DrawGridPressAssKeyPress` (0x004B6168):
  - sub_4B61DC (691 B): toca DrawGridPressAss,TimerDati
  -   sub_432838 (28 B)
  -   sub_432868 (208 B): chama TAebVector::SetEcuData; toca LabelTempoSondaFredda
  -   sub_43294C (47 B)
  -   sub_432990 (200 B): chama TAebVector::SetEcuData; toca LabelS3
  -   sub_4A161C (80 B)
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_40487C (45 B)
- `DrawGridPressDiffDblClick` (0x004B5B68):
  - sub_4B5BCC (691 B): toca DrawGridPressDiff,TimerDati
  -   sub_432610 (59 B)
  -   sub_432670 (231 B): chama TAebVector::SetEcuData
  -   sub_43277C (47 B)
  -   sub_4327C0 (114 B): chama TAebVector::SetEcuData
  -   sub_4A1458 (80 B)
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_40487C (45 B)
- `DrawGridPressDiffDrawCell` (0x004B5880):
  - sub_40487C (45 B)
  - sub_432610 (59 B)
  - sub_43277C (47 B)
- `DrawGridPressDiffKeyPress` (0x004B5B58):
  - sub_4B5BCC (691 B): toca DrawGridPressDiff,TimerDati
  -   sub_432610 (59 B)
  -   sub_432670 (231 B): chama TAebVector::SetEcuData
  -   sub_43277C (47 B)
  -   sub_4327C0 (114 B): chama TAebVector::SetEcuData
  -   sub_4A1458 (80 B)
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_40487C (45 B)
- `DrawGridPressPassDblClick` (0x004B6EF4):
  - sub_4B6F68 (575 B): toca DrawGridPressPass,PanelBarra,TimerDati
  -   sub_4320D0 (47 B)
  -   sub_432114 (168 B): chama TAebVector::SetEcuData
  -   sub_4321C8 (53 B)
  -   sub_432218 (178 B): chama TAebVector::SetEcuData; toca LabelRitardoLambda
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_40487C (45 B)
- `DrawGridPressPassDrawCell` (0x004B6B1C):
  - sub_40487C (45 B)
  - sub_42EE14 (100 B)
  - sub_4320D0 (47 B)
  - sub_4321C8 (53 B)
  -   sub_42F338 (7 B)
  -   sub_433FFC (14 B)
- `DrawGridPressPassKeyPress` (0x004B6F58):
  - sub_4B6F68 (575 B): toca DrawGridPressPass,PanelBarra,TimerDati
  -   sub_4320D0 (47 B)
  -   sub_432114 (168 B): chama TAebVector::SetEcuData
  -   sub_4321C8 (53 B)
  -   sub_432218 (178 B): chama TAebVector::SetEcuData; toca LabelRitardoLambda
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_40487C (45 B)
- `DrawGridProgressioniDblClick` (0x004CA9A0):
  - sub_4CA9F8 (1082 B): chama TAebMatrix::GetInt,TAebMatrix::SetInt
  -   sub_404C40 (81 B)
  -   sub_405754 (48 B)
  -   sub_4057C0 (139 B)
  -   sub_489034 (110 B)
  -   sub_489320 (60 B)
  -   sub_48935C (60 B)
  -   sub_489398 (60 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_4080D4 (67 B)
- `DrawGridProgressioniDrawCell` (0x004CA74C):
  - sub_40487C (45 B)
  - sub_4080D4 (67 B)
- `DrawGridProgressioniKeyPress` (0x004CA990):
  - sub_4CA9F8 (1082 B): chama TAebMatrix::GetInt,TAebMatrix::SetInt
  -   sub_404C40 (81 B)
  -   sub_405754 (48 B)
  -   sub_4057C0 (139 B)
  -   sub_489034 (110 B)
  -   sub_489320 (60 B)
  -   sub_48935C (60 B)
  -   sub_489398 (60 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_4080D4 (67 B)
- `DrawGridSequenzaInjBenzDrawCell` (0x004BBB88):
  - sub_40487C (45 B)
  - sub_4048AC (68 B)
  - sub_433AEC (26 B)
  - sub_434CC0 (834 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_42A52C (358 B)
  -     sub_433FFC (14 B)
- `DrawGridSequenzaInjDrawCell` (0x004BB664):
  - sub_40487C (45 B)
  - sub_4048AC (68 B)
  - sub_434CC0 (834 B)
  - sub_4BC58C (415 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_43400C (7 B)
  -     sub_42A52C (358 B)
  -     sub_433FFC (14 B)
- `DrawGridSequenzaInjSelectCell` (0x004BBAB0):
  - sub_434CC0 (834 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_42A52C (358 B)
  -     sub_433FFC (14 B)
- `DrawGridSplitFuelDblClick` (0x004C2788):
  - sub_4C27FC (774 B): toca DrawGridSplitFuel,TimerDati
  -   sub_432A64 (16 B)
  -   sub_432A74 (100 B): chama TAebVector::SetEcuData
  -   sub_432AD8 (47 B)
  -   sub_432B1C (184 B): chama TAebVector::SetEcuData
  -   sub_48944C (154 B)
  -   sub_4A1880 (80 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  - … +2 (ver telas.json)
- `DrawGridSplitFuelDrawCell` (0x004C2420):
  - sub_40487C (45 B)
  - sub_432A64 (16 B)
  - sub_432AD8 (47 B)
- `DrawGridSplitFuelKeyPress` (0x004C27EC):
  - sub_4C27FC (774 B): toca DrawGridSplitFuel,TimerDati
  -   sub_432A64 (16 B)
  -   sub_432A74 (100 B): chama TAebVector::SetEcuData
  -   sub_432AD8 (47 B)
  -   sub_432B1C (184 B): chama TAebVector::SetEcuData
  -   sub_48944C (154 B)
  -   sub_4A1880 (80 B)
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  - … +2 (ver telas.json)
- `DrawGridTInjDefaultDblClick` (0x004B6868):
  - sub_4B68DC (557 B): toca DrawGridTInjDefault,TimerDati
  -   sub_43002C (109 B)
  -   sub_4300B8 (218 B): chama TAebMatrix::SetEcuData
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_433FFC (14 B)
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_40487C (45 B)
- `DrawGridTInjDefaultDrawCell` (0x004B64A0):
  - sub_40487C (45 B)
  - sub_43002C (109 B)
  -   sub_433FFC (14 B)
- `DrawGridTInjDefaultKeyPress` (0x004B68CC):
  - sub_4B68DC (557 B): toca DrawGridTInjDefault,TimerDati
  -   sub_43002C (109 B)
  -   sub_4300B8 (218 B): chama TAebMatrix::SetEcuData
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_433FFC (14 B)
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_40487C (45 B)
- `DrawGridTempDblClick` (0x004B81F4):
  - sub_4B82A4 (951 B): toca DrawGridTemp,EditRPullUp,TimerDati
  -   sub_43040C (16 B)
  -   sub_43041C (84 B): chama TAebVector::SetEcuData
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_96B924 (79 B)
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_40487C (45 B)
- `DrawGridTempDrawCell` (0x004B7874):
  - sub_40487C (45 B)
  - sub_4080D4 (67 B)
  - sub_43040C (16 B)
  - sub_96B924 (79 B)
- `DrawGridTempKeyPress` (0x004B80B0):
  - sub_43041C (84 B): chama TAebVector::SetEcuData
  - sub_4B82A4 (951 B): toca DrawGridTemp,EditRPullUp,TimerDati
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_43040C (16 B)
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -   sub_96B924 (79 B)
  -     sub_40487C (45 B)
- `DrawGridTempMotDblClick` (0x004B54F8):
  - sub_4B555C (786 B)
  -   sub_432384 (16 B)
  -   sub_432394 (138 B): chama TAebVector::SetEcuData; toca TabCambio
  -   sub_432518 (63 B)
  -   sub_43255C (178 B): chama TAebVector::SetEcuData; toca PageControl
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_42A788 (358 B)
  -     sub_42A8F4 (235 B)
  -     sub_4080D4 (67 B)
- `DrawGridTempMotDrawCell` (0x004B5134):
  - sub_40487C (45 B)
  - sub_432384 (16 B)
  - sub_432518 (63 B)
  -   sub_42A788 (358 B)
- `DrawGridTempMotKeyPress` (0x004B54E8):
  - sub_4B555C (786 B)
  -   sub_432384 (16 B)
  -   sub_432394 (138 B): chama TAebVector::SetEcuData; toca TabCambio
  -   sub_432518 (63 B)
  -   sub_43255C (178 B): chama TAebVector::SetEcuData; toca PageControl
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_42A788 (358 B)
  -     sub_42A8F4 (235 B)
  -     sub_4080D4 (67 B)
- `DrawGridTempRidDblClick` (0x004B4DAC):
  - sub_4B4E10 (786 B)
  -   sub_4322E8 (16 B)
  -   sub_4322F8 (138 B): chama TAebVector::SetEcuData; toca MenuDiagnosiProg
  -   sub_432420 (63 B)
  -   sub_432464 (178 B): chama TAebVector::SetEcuData; toca MainMenu1
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_42A52C (358 B)
  -     sub_42A698 (235 B)
  -     sub_4080D4 (67 B)
- `DrawGridTempRidDrawCell` (0x004B49E8):
  - sub_40487C (45 B)
  - sub_4322E8 (16 B)
  - sub_432420 (63 B)
  -   sub_42A52C (358 B)
- `DrawGridTempRidKeyPress` (0x004B4D9C):
  - sub_4B4E10 (786 B)
  -   sub_4322E8 (16 B)
  -   sub_4322F8 (138 B): chama TAebVector::SetEcuData; toca MenuDiagnosiProg
  -   sub_432420 (63 B)
  -   sub_432464 (178 B): chama TAebVector::SetEcuData; toca MainMenu1
  -   sub_50ECB0 (149 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_42A52C (358 B)
  -     sub_42A698 (235 B)
  -     sub_4080D4 (67 B)
- `DrawGridTempiLrDblClick` (0x004BE5A8):
  - sub_4BE60C (750 B): toca DrawGridTempiLr,TimerDati
  -   sub_4336AC (44 B)
  -   sub_4336DC (146 B): chama TAebVector::SetEcuData; toca LabelV10
  -   sub_433770 (74 B)
  -   sub_4337D8 (288 B): chama TAebVector::SetEcuData; toca LabelV11
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42A52C (358 B)
  -     sub_42A698 (235 B)
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_433FFC (14 B)
  - … +1 (ver telas.json)
- `DrawGridTempiLrDrawCell` (0x004BE278):
  - sub_40487C (45 B)
  - sub_4336AC (44 B)
  - sub_433770 (74 B)
  -   sub_42A52C (358 B)
  -   sub_433FFC (14 B)
- `DrawGridTempiLrKeyPress` (0x004BE598):
  - sub_4BE60C (750 B): toca DrawGridTempiLr,TimerDati
  -   sub_4336AC (44 B)
  -   sub_4336DC (146 B): chama TAebVector::SetEcuData; toca LabelV10
  -   sub_433770 (74 B)
  -   sub_4337D8 (288 B): chama TAebVector::SetEcuData; toca LabelV11
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42A52C (358 B)
  -     sub_42A698 (235 B)
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  -     sub_433FFC (14 B)
  - … +1 (ver telas.json)
- `DrawGridTestDblClick` (0x004B75EC):
  - sub_4B7660 (514 B): toca DrawGridTest,TimerDati
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42A52C (358 B)
  -     sub_42A788 (358 B)
  -     sub_433FFC (14 B)
  -     sub_42A698 (235 B)
  -     sub_42A8F4 (235 B)
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  - … +1 (ver telas.json)
- `DrawGridTestDrawCell` (0x004B71B8):
  - sub_40487C (45 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  -   sub_433FFC (14 B)
- `DrawGridTestKeyPress` (0x004B7650):
  - sub_4B7660 (514 B): toca DrawGridTest,TimerDati
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_42A52C (358 B)
  -     sub_42A788 (358 B)
  -     sub_433FFC (14 B)
  -     sub_42A698 (235 B)
  -     sub_42A8F4 (235 B)
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
  - … +1 (ver telas.json)
- `EditAdvParamInjGasKeyPress` (0x004C7150):
  - sub_432BE4 (16 B)
  - sub_432BF4 (84 B): chama TAebVector::SetEcuData
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  - … +7 (ver telas.json)
- `EditAntiStalloKeyPress` (0x004C97D8):
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  -     sub_98211C (33 B)
  -     sub_982150 (25 B)
  -     sub_982174 (25 B)
  -     sub_982198 (25 B)
  -     sub_9821B8 (25 B)
- `EditAperturaInjKeyPress` (0x004ACF68):
  - sub_4312A8 (593 B): chama TAebNumber::SetEcuData
  - sub_96BA2C (343 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_40487C (45 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  -     sub_98211C (33 B)
  -     sub_982150 (25 B)
  -     sub_982174 (25 B)
  - … +2 (ver telas.json)
- `EditBassaPressioneMpKeyPress` (0x004C526C):
  - sub_432670 (231 B): chama TAebVector::SetEcuData
  - sub_43277C (47 B)
  - sub_4327C0 (114 B): chama TAebVector::SetEcuData
  - sub_4A1458 (80 B)
  - sub_96BA2C (343 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_40487C (45 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  - … +5 (ver telas.json)
- `EditCorrBancata2KeyPress` (0x004C2CCC):
  - sub_434CC0 (834 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A52C (358 B)
  -   sub_433FFC (14 B)
  -   sub_42A698 (235 B)
  - … +15 (ver telas.json)
- `EditCorrCarbMinimoKeyPress` (0x0049F0E4):
  - sub_42F348 (27 B)
  - sub_42F568 (215 B)
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  -   sub_42EC94 (65 B)
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -   sub_42F338 (7 B)
  -   sub_42F340 (7 B)
  -   sub_42F364 (135 B): chama TAebMatrix::SetEcuData
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  - … +9 (ver telas.json)
- `EditCorrIntInfKeyPress` (0x004B91BC):
  - sub_42FCC0 (16 B)
  - sub_42FCD0 (84 B): chama TAebVector::SetEcuData
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_40487C (45 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  -     sub_98211C (33 B)
  - … +4 (ver telas.json)
- `EditCutoffKeyPress` (0x004C82B8):
  - sub_42FD24 (124 B)
  - sub_42FDBC (165 B): chama TAebVector::SetEcuData
  - sub_432D74 (16 B)
  - sub_432D84 (116 B): chama TAebVector::SetEcuData; toca PanelTinjBenz,ShapeTinjBenz
  - sub_434CC0 (834 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  - … +22 (ver telas.json)
- `EditDeltaTInterruzioneWarmupKeyPress` (0x004C8ECC):
  - sub_42A788 (358 B)
  - sub_42A8F4 (235 B)
  - sub_434CC0 (834 B)
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_40487C (45 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_42A52C (358 B)
  -     sub_433FFC (14 B)
  - … +6 (ver telas.json)
- `EditFiltroEnter` (0x004BDFD4):
  - sub_40487C (45 B)
  - sub_42CFB8 (839 B)
  - sub_96BCD0 (42 B)
  - sub_96BCFC (118 B)
- `EditFiltroKeyPress` (0x004B9644):
  - sub_431514 (16 B)
  - sub_431524 (84 B): chama TAebVector::SetEcuData
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  -     sub_98211C (33 B)
  - … +4 (ver telas.json)
- `EditFlashLubeKeyPress` (0x004C8948):
  - sub_432D10 (16 B)
  - sub_432D20 (84 B): chama TAebVector::SetEcuData
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  - … +7 (ver telas.json)
- `EditFrestEnter` (0x004C611C):
  - sub_40487C (45 B)
  - sub_96BCD0 (42 B)
- `EditGiriCambioEnter` (0x004A98D8):
  - sub_40487C (45 B)
  - sub_96BCD0 (42 B)
- `EditGiriKFiltroEnter` (0x004BE90C):
  - sub_40487C (45 B)
  - sub_96BCD0 (42 B)
- `EditGiriKFiltroKeyPress` (0x004BEB24):
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_40487C (45 B)
  - … +13 (ver telas.json)
- `EditGiriMinimoPerBenzinaKeyPress` (0x004BFE6C):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  -   sub_433FFC (14 B)
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  - … +9 (ver telas.json)
- `EditGiriPerBenzinaKeyPress` (0x004C04E8):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  -   sub_433FFC (14 B)
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  - … +9 (ver telas.json)
- `EditKFiltroKeyPress` (0x004B95C0):
  - sub_431514 (16 B)
  - sub_431524 (84 B): chama TAebVector::SetEcuData
  - sub_96B97C (174 B)
  - sub_96BA2C (343 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -   sub_40487C (45 B)
  -     sub_9820F0 (31 B)
  - … +5 (ver telas.json)
- `EditKMappaNeutroKeyPress` (0x004C499C):
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_40487C (45 B)
  - … +13 (ver telas.json)
- `EditOffsetKeyPress` (0x004AF360):
  - sub_430470 (27 B)
  - sub_43048C (91 B): chama TAebVector::SetEcuData
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  -     sub_98211C (33 B)
  - … +4 (ver telas.json)
- `EditPreheatSyncInjNumKeyPress` (0x004C78A4):
  - sub_432CAC (16 B)
  - sub_432CBC (84 B): chama TAebVector::SetEcuData; toca LabelTinjBenzPerc
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_40487C (45 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  -     sub_98211C (33 B)
  - … +4 (ver telas.json)
- `EditRPullUpEnter` (0x004B86C4):
  - sub_40487C (45 B)
  - sub_96BCD0 (42 B)
- `EditRPullUpExit` (0x004B88C0):
  - sub_4303A8 (16 B)
  - sub_4303B8 (84 B): chama TAebVector::SetEcuData
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  -     sub_98211C (33 B)
  - … +4 (ver telas.json)
- `EditRifLambdaKeyPress` (0x004A6A00):
  - sub_434CC0 (834 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A52C (358 B)
  -   sub_433FFC (14 B)
  -   sub_42A698 (235 B)
  - … +15 (ver telas.json)
- `EditRiscaldamentoKeyPress` (0x004C7478):
  - sub_42A52C (358 B)
  - sub_42A698 (235 B)
  - sub_42A788 (358 B)
  - sub_42A8F4 (235 B)
  - sub_432BE4 (16 B)
  - sub_432BF4 (84 B): chama TAebVector::SetEcuData
  - sub_434840 (34 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  - sub_96BA2C (343 B)
  - … +15 (ver telas.json)
- `EditRpmTachimetroKeyPress` (0x004A647C):
  - sub_40487C (45 B)
  - sub_96C73C (114 B)
- `EditScartoMinFuoriMinKeyPress` (0x004BA1E8):
  - sub_42FE7C (89 B)
  - sub_42FEF4 (185 B): chama TAebVector::SetEcuData
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_433FFC (14 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_40487C (45 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  - … +5 (ver telas.json)
- `EditSensAdattaKeyPress` (0x004C07D0):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  -   sub_433FFC (14 B)
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  - … +9 (ver telas.json)
- `EditSogliaGiriKeyPress` (0x004C58DC):
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_40487C (45 B)
  - … +13 (ver telas.json)
- `EditTInjSup22KeyPress` (0x004C3E44):
  - sub_4301AC (209 B)
  - sub_4302A0 (233 B): chama TAebVector::SetEcuData
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_433FFC (14 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_40487C (45 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  - … +5 (ver telas.json)
- `EditTempAcquaKeyPress` (0x004BCB78):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  -   sub_433FFC (14 B)
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  - … +12 (ver telas.json)
- `EditTempMotoreKeyPress` (0x004C8F84):
  - sub_42A788 (358 B)
  - sub_42A8F4 (235 B)
  - sub_434CC0 (834 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A698 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_40487C (45 B)
  - … +13 (ver telas.json)
- `EditTempoBenzinaKeyPress` (0x004C0570):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  -   sub_433FFC (14 B)
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  - … +9 (ver telas.json)
- `EditTempoMortoBenzKeyPress` (0x004B11B0):
  - sub_4312A8 (593 B): chama TAebNumber::SetEcuData
  - sub_4337D8 (288 B): chama TAebVector::SetEcuData; toca LabelV11
  - sub_96BA2C (343 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_40487C (45 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  -     sub_98211C (33 B)
  -     sub_982150 (25 B)
  - … +3 (ver telas.json)
- `EditTipoIniettoreKeyPress` (0x004BACE0):
  - sub_40487C (45 B)
  - sub_42FFC8 (16 B)
  - sub_42FFD8 (84 B): chama TAebVector::SetEcuData
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  - … +5 (ver telas.json)
- `FormCloseQuery` (0x004ADE94):
  - sub_40487C (45 B)
  - sub_42CFB8 (839 B)
- `FormKeyDown` (0x0049EB24):
  - sub_40487C (45 B)
  - sub_42CFB8 (839 B)
- `FormResize` (0x00490CB8):
  - sub_42F338 (7 B)
  - sub_42F340 (7 B)
- `FormShow` (0x00490028):
  - sub_40487C (45 B)
  - sub_43400C (7 B)
  - sub_434768 (34 B)
  - sub_4348D0 (34 B)
  - sub_4A96A4 (563 B): toca EditCorrBancata2,LabApMot,LabApMot2,LabApMot2Benz
  -   sub_433918 (29 B)
  -   sub_434530 (21 B)
  -   sub_434CC0 (834 B)
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_96B97C (174 B)
  -   sub_96C73C (114 B)
  -     sub_42A788 (358 B)
  - … +7 (ver telas.json)
- `ImageInjOffClick` (0x004C81B0):
  - sub_42CFB8 (839 B)
  - sub_43481C (34 B)
  - sub_4C7A10 (344 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect; toca ImageInjOffA,ImageInjOnA,ImageInjOnB,ImageInjOnC
- `ImageInjOnClick` (0x004C80A8):
  - sub_42CFB8 (839 B)
  - sub_43481C (34 B)
  - sub_4C7A10 (344 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect; toca ImageInjOffA,ImageInjOnA,ImageInjOnB,ImageInjOnC
- `LabGasClick` (0x004C9A7C):
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  - … +22 (ver telas.json)
- `LabelDiagnosiClick` (0x004CB060):
  - sub_4381F4 (7 B)
- `MenuComunicazioneSerialeCostruitaClick` (0x004AE008):
  - sub_40487C (45 B)
  - sub_4080D4 (67 B)
- `MenuControlloIniettoriClick` (0x004BA408):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  - … +21 (ver telas.json)
- `MenuCopiaTaraturaClick` (0x004AD0B0):
  - sub_40487C (45 B)
  - sub_4080D4 (67 B)
  - sub_42EC94 (65 B)
  - sub_42F130 (65 B)
  - sub_42F6A8 (30 B)
  - sub_4A6168 (67 B)
  -   sub_42F338 (7 B)
  -   sub_42F340 (7 B)
- `MenuIncollaTaraturaClick` (0x004AD4B8):
  - sub_4047FC (125 B)
  - sub_40487C (45 B)
  - sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  - sub_42F174 (181 B): chama TAebMatrix::SetEcuData
  - sub_42F6C8 (143 B): chama TAebMatrix::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_42F338 (7 B)
  -   sub_42F340 (7 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `PageControlChanging` (0x004ADD20):
  - sub_40487C (45 B)
  - sub_42CFB8 (839 B)
- `PageControlDrawTab` (0x0049E83C):
  - sub_40487C (45 B)
  - sub_4080D4 (67 B)
- `RadioAdatta25Click` (0x004C05F0):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  -   sub_433FFC (14 B)
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `RadioAdattaDirittiClick` (0x004C06EC):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  -   sub_433FFC (14 B)
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `RadioGasAltiGiriClick` (0x004BFEF4):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_434CC0 (834 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - sub_96B97C (174 B)
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - … +31 (ver telas.json)
- `RadioGasClick` (0x004BFAD8):
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  - sub_42E754 (385 B)
  - sub_434CC0 (834 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - sub_489034 (110 B)
  - sub_489398 (60 B)
  - sub_4893D4 (60 B)
  - sub_489410 (60 B)
  - … +40 (ver telas.json)
- `RadioMapClick` (0x004B23A0):
  - sub_42F338 (7 B)
  - sub_42F340 (7 B)
- `TabAdattaShow` (0x004C0854):
  - sub_42CFB8 (839 B)
  - sub_42DF14 (599 B)
  - sub_42E754 (385 B)
  - sub_434568 (20 B)
  - sub_4346FC (34 B)
  - sub_4349F0 (34 B)
  - sub_434B34 (34 B)
  - sub_434B7C (34 B)
  - sub_434C9C (34 B)
  - sub_434CC0 (834 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_96B97C (174 B)
  - … +15 (ver telas.json)
- `TabAdvancedShow` (0x004C9BBC):
  - sub_42DF14 (599 B)
  - sub_42E754 (385 B)
- `TabAutoCalNewShow` (0x004CB1C4):
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_489034 (110 B)
  - sub_489398 (60 B)
  - sub_4893D4 (60 B)
  - sub_489410 (60 B)
- `TabBenzinaShow` (0x004BF004):
  - sub_434CC0 (834 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_96B97C (174 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A52C (358 B)
  -   sub_433FFC (14 B)
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  - … +5 (ver telas.json)
- `TabCalibrationShow` (0x004C7904):
  - sub_434CC0 (834 B)
  - sub_4C7A10 (344 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect; toca ImageInjOffA,ImageInjOnA,ImageInjOnB,ImageInjOnC
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42CFB8 (839 B)
  -   sub_43481C (34 B)
  -     sub_42A52C (358 B)
  -     sub_433FFC (14 B)
- `TabCambioShow` (0x004A6ACC):
  - sub_40487C (45 B)
  - sub_42CFB8 (839 B)
  - sub_42DF14 (599 B)
  - sub_42FFC8 (16 B)
  - sub_43362C (14 B)
  - sub_9691D4 (114 B)
  - sub_96B924 (79 B)
  - sub_96B97C (174 B)
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  - … +5 (ver telas.json)
- `TabConsumometroShow` (0x004C1044):
  - sub_42CFB8 (839 B)
  - sub_434CC0 (834 B)
  - sub_96B97C (174 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_42A52C (358 B)
  -     sub_433FFC (14 B)
  -     sub_9820F0 (31 B)
  - … +5 (ver telas.json)
- `TabEmissioniShow` (0x004A8964):
  - sub_42FCC0 (16 B)
  - sub_434044 (21 B)
  - sub_434CC0 (834 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_48944C (154 B)
  - sub_96BB88 (321 B)
  - sub_96C73C (114 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A52C (358 B)
  -   sub_433FFC (14 B)
  - … +8 (ver telas.json)
- `TabFrestShow` (0x004C6324):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  -   sub_433FFC (14 B)
- `TabIniezioneShow` (0x004AB8BC):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_42A52C (358 B)
  - sub_42A788 (358 B)
  - sub_42DF14 (599 B)
  - sub_431160 (300 B)
  - sub_432BE4 (16 B)
  - sub_432CAC (16 B)
  - sub_4346B4 (34 B)
  - sub_434840 (34 B)
  - … +20 (ver telas.json)
- `TabKCoefficientiShow` (0x004B0EA0):
  - sub_42CFB8 (839 B)
  - sub_42DF14 (599 B)
  - sub_431160 (300 B)
  - sub_4347F8 (34 B)
  - sub_48944C (154 B)
  - sub_96BB88 (321 B)
  -   sub_433FFC (14 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  -     sub_98211C (33 B)
  -     sub_982150 (25 B)
  - … +3 (ver telas.json)
- `TabKFactorShow` (0x004C49FC):
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_42CFB8 (839 B)
  - sub_434CC0 (834 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_489034 (110 B)
  - sub_489398 (60 B)
  - sub_4893D4 (60 B)
  - sub_489410 (60 B)
  - sub_9691D4 (114 B)
  - sub_96BB88 (321 B)
  - … +14 (ver telas.json)
- `TabKTempiShow` (0x004B1254):
  - sub_431160 (300 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_96BB88 (321 B)
  -   sub_433FFC (14 B)
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  -     sub_98211C (33 B)
  -     sub_982150 (25 B)
  -     sub_982174 (25 B)
  - … +2 (ver telas.json)
- `TabKVarieShow` (0x004B94C8):
  - sub_431514 (16 B)
  - sub_434CC0 (834 B)
  - sub_96B97C (174 B)
  - sub_96BB88 (321 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_42A52C (358 B)
  -     sub_433FFC (14 B)
  - … +6 (ver telas.json)
- `TabLambdaShow` (0x004A6770):
  - sub_42DF14 (599 B)
  - sub_433918 (29 B)
  - sub_433A00 (75 B)
  - sub_434918 (34 B)
  - sub_434CC0 (834 B)
  - sub_48944C (154 B)
  - sub_9691D4 (114 B)
  - sub_96B97C (174 B)
  - sub_96BB88 (321 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - … +11 (ver telas.json)
- `TabPressioneShow` (0x004AE788):
  - sub_40487C (45 B)
  - sub_42CFB8 (839 B)
  - sub_42FFC8 (16 B)
  - sub_430470 (27 B)
  - sub_434CC0 (834 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_48944C (154 B)
  - sub_4AF018 (840 B): toca ButtonRicercaParametri,CheckMapConfigurato,EditPressRid,EditSogliaMap
  - sub_9691D4 (114 B)
  - sub_96B97C (174 B)
  - sub_96BB88 (321 B)
  -   sub_42A788 (358 B)
  - … +14 (ver telas.json)
- `TabSensoreShow` (0x004AA5BC):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_42CFB8 (839 B)
  - sub_42DF14 (599 B)
  - sub_42E754 (385 B)
  - sub_42FFC8 (16 B)
  - sub_4304E8 (7 B)
  - sub_431878 (110 B): chama TAebVector::GetEcuData; toca MenuCopia
  - sub_43277C (47 B)
  - sub_43400C (7 B)
  - … +33 (ver telas.json)
- `TabSwitchToPetrolShow` (0x004C65A4):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_96BB88 (321 B)
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  -   sub_433FFC (14 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  -     sub_98211C (33 B)
  -     sub_982150 (25 B)
  -     sub_982174 (25 B)
  -     sub_982198 (25 B)
  - … +1 (ver telas.json)
- `TabTaraturaShow` (0x004A9CD0):
  - sub_42FE7C (89 B)
  - sub_434CC0 (834 B)
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_96B97C (174 B)
  - sub_96BB88 (321 B)
  -   sub_433FFC (14 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_42A52C (358 B)
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  - … +7 (ver telas.json)
- `TabTempShow` (0x004B92C8):
  - sub_42CFB8 (839 B)
  - sub_42E754 (385 B)
- `TabTestShow` (0x004BA440):
  - sub_434CC0 (834 B)
  - sub_96B97C (174 B)
  - sub_96BB88 (321 B)
  -   sub_42A788 (358 B)
  -   sub_433FCC (47 B)
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4080D4 (67 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_42A52C (358 B)
  -     sub_433FFC (14 B)
  -     sub_9820F0 (31 B)
  - … +5 (ver telas.json)
- `TimerDatiTimer` (0x004A307C):
  - sub_40487C (45 B)
  - sub_4080D4 (67 B)
  - sub_4339B8 (72 B)
  - sub_433FFC (14 B)
  - sub_434530 (21 B)
  - sub_4381FC (6 B)
  - sub_4A6168 (67 B)
- `chkRitardoPassaggioInMinutiClick` (0x004CB300):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
