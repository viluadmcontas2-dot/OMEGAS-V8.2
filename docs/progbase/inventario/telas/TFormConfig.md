# TFormConfig (recurso `TFORMCONFIG`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

**Papel:** tela de função (OBS-COD). Configuração do veículo em 33 abas (detalhe em docs/progbase/abas, outro agente).

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
| `ButtonAccettaClick` | 0x004A8D74 | 577 | INFERIDO | Ao clicar em ButtonAccetta «&Conferma»: mexe em EditLivello1, EditLivello2, EditLivello3, EditLivello4. |  |  |  |
| `ButtonCommutatoreClick` | 0x004B1178 | 55 | INFERIDO | Ao clicar em ButtonCommutatore «&Comutador»: chama APIs Sleep. |  | Sleep | PostMessageW |
| `ButtonDebugClick` | 0x004C8EC8 | 1 | OBS-COD | Ao clicar em ButtonDebug «Enable debug». Corpo é só RET: nada acontece neste evento (OBS-COD). |  |  |  |
| `ButtonDoLevelClick` | 0x004C5C30 | 978 | INFERIDO | Ao clicar em ButtonDoLevel «Find levels»: chama TFormConfig.ButtonAccettaClick; mexe em EditLivello1, EditLivello2, EditLivello3, EditLivello4, LabelMaxLevel, LabelMinLevel. |  |  |  |
| `ButtonMaxLevelClick` | 0x004C5ADC | 337 | INFERIDO | Ao clicar em ButtonMaxLevel «Set max level»: chama TAebProtocol::IsConnect; mexe em LabelMaxLevel. |  |  |  |
| `ButtonMinLevelClick` | 0x004C598C | 334 | INFERIDO | Ao clicar em ButtonMinLevel «Set min level»: chama TAebProtocol::IsConnect; mexe em LabelMinLevel. |  |  |  |
| `ButtonModificaInjClick` | 0x004BC47C | 270 | INFERIDO | Ao clicar em ButtonModificaInj «Modificação sequência injecção gás»: chama TAebProtocol::IsConnect; mexe em DrawGridSequenzaInj. |  |  |  |
| `ButtonModificaRifTarClick` | 0x004B10C8 | 136 | INFERIDO | Ao clicar em ButtonModificaRifTar «&Modificar ref. mapa»: chama TFormCentriCelleK.Create; mexe em DrawGridK, DrawGridOpenLoop. |  |  |  |
| `ButtonNuoviParametriClick` | 0x004B9000 | 443 | INFERIDO | Ao clicar em ButtonNuoviParametri «&Modifica parametri sensore temperatura motore»: usa textos BUTTONNUOVIPARAMETRI, BUTTONNUOVIPARAMETRI2; chama TTranslatorExt::GetString; mexe em ButtonNuoviParametri, DrawGridTemp, EditDeltaT, EditRPullUp, EditTMax, EditTMin. | BUTTONNUOVIPARAMETRI, BUTTONNUOVIPARAMETRI2 |  | PostMessageW |
| `ButtonResetClick` | 0x004BA188 | 94 | INFERIDO | Ao clicar em ButtonReset «Redefina a central de comando e retorne aos parâmetros de base»: mexe em TabCambio, TimerDati. |  |  |  |
| `ButtonRicercaParametriClick` | 0x004AF3A8 | 509 | INFERIDO | Ao clicar em ButtonRicercaParametri «Inicie a procura guiada dos parâmetros do mapa externo»: usa textos BUTTONRICERCAPARAMETRI1; chama APIs GetAsyncKeyState; chama TTranslatorExt::GetString; mexe em ButtonRicercaParametri, LabLambda, TimerDati. | BUTTONRICERCAPARAMETRI1 | GetAsyncKeyState |  |
| `ButtonSequenceAcquisitionClick` | 0x004A62A8 | 467 | INFERIDO | Ao clicar em ButtonSequenceAcquisition «Sequence acquisition»: chama TAebProtocol::IsConnect, TAebProtocol::SendCommand; mexe em ButtonSequenceAcquisition, EditRpmTachimetro, LabRpmTachimetro, LabelRpmTachimetro. |  |  |  |
| `ButtonSequenzaInjClick` | 0x004BC2DC | 414 | INFERIDO | Ao clicar em ButtonSequenzaInj «Adquirir seqüência injeção gasolina»: chama TAebProtocol::IsConnect; mexe em ComboSequenzaInj, DrawGridSequenzaInj, DrawGridSequenzaInjBenz. |  |  |  |
| `ButtonSwitchSettingsClick` | 0x004C90E0 | 1624 | INFERIDO | Ao clicar em ButtonSwitchSettings «Switch settings»: usa textos MESSERRORECUSWITCHCALIBRATION, MESSERRORFIRMWARESWITCHCALIBRATION, MESSERRORGIRISWITCHCALIBRATION, MESSERRORNOSWITCHCALIBRATION, MESSERRORSTARTSWITCHCALIBRATION; chama TAebProtocol::GetComFail, TAebProtocol::IsConnect, TAebProtocol::SendCommand, TTranslatorExt::GetString. | MESSERRORECUSWITCHCALIBRATION, MESSERRORFIRMWARESWITCHCALIBRATION, MESSERRORGIRISWITCHCALIBRATION, MESSERRORNOSWITCHCALIBRATION, MESSERRORSTARTSWITCHCALIBRATION |  | SWITCHSETTINGSBUTTONEXIT, SWITCHSETTINGSCAPTION, SWITCHSETTINGSLABELBUZZER, SWITCHSETTINGSLABELLED, Sleep |
| `ButtonTaraturaClick` | 0x004ACFC0 | 238 | INFERIDO | Ao clicar em ButtonTaratura «&Iniciar calibrag.»: chama APIs Sleep; mexe em ButtonCommutatore, ButtonTaratura, StaticTaratura. |  | Sleep | PostMessageW |
| `CheckAdattaBenzinaClick` | 0x004C0E34 | 227 | INFERIDO | Ao clicar em CheckAdattaBenzina «Adaptabilidade com mapa gasolina»: mexe em CheckAdattaBenzina, CheckAdattativita, LabCorrGas, LabelCorrGas, PanelAdatta, PanelAdattaObd. |  |  | PostMessageW |
| `CheckAdattativitaClick` | 0x004C0D50 | 227 | INFERIDO | Ao clicar em CheckAdattativita «Adaptabilidade OBD»: mexe em CheckAdattaBenzina, CheckAdattativita, LabCorrGas, LabelCorrGas, PanelAdatta, PanelAdattaObd. |  |  | PostMessageW |
| `CheckAlimentazioneClick` | 0x004C6780 | 173 | INFERIDO | Ao clicar em CheckAlimentazione «Use of wire EV2 for STAP»: mexe em CheckAlimentazione. |  |  | PostMessageW |
| `CheckAntiStalloClick` | 0x004C982C | 591 | INFERIDO | Ao clicar em CheckAntiStallo «Enable un-stalling strategy»: mexe em CheckAntiStallo, EditAntiStallo0, EditAntiStallo1, EditAntiStallo2, EditAntiStallo3, EditAntiStallo4. |  |  |  |
| `CheckAttivaPompaInRiservaClick` | 0x004CB030 | 48 | INFERIDO | Ao clicar em CheckAttivaPompaInRiserva «Enable pump reactivation in reserve»: mexe em CheckAttivaPompaInRiserva. |  |  |  |
| `CheckAzzeraBlackListObdClick` | 0x004C1ADC | 48 | INFERIDO | Ao clicar em CheckAzzeraBlackListObd «Selective reset of OBD errors»: mexe em CheckAzzeraBlackListObd. |  |  |  |
| `CheckAzzeraErroriObdClick` | 0x004C1AAC | 48 | INFERIDO | Ao clicar em CheckAzzeraErroriObd «Reset erro OBD»: mexe em CheckAzzeraErroriObd. |  |  |  |
| `CheckBassaPressioneClick` | 0x004BCA04 | 27 | INFERIDO | Ao clicar em CheckBassaPressione «Reversão automática por baixa pressão impostável»: mexe em CheckBassaPressione. |  |  |  |
| `CheckBoxEV2NotConnectedClick` | 0x004C6710 | 111 | INFERIDO | Ao clicar em CheckBoxEV2NotConnected «EV2 not connected»: corpo de 111 bytes sem fatos diretos (chama 2 funções sem nome). |  |  |  |
| `CheckBoxEnDiagDtcPowertrainClick` | 0x004CB358 | 48 | INFERIDO | Ao clicar em CheckBoxEnDiagDtcPowertrain «En Diag DTC POWERTRAIN»: mexe em CheckBoxEnDiagDtcPowertrain. |  |  |  |
| `CheckBoxEnableExtLedKLineClick` | 0x004CB328 | 48 | INFERIDO | Ao clicar em CheckBoxEnableExtLedKLine «Enable external LED on K-Line»: mexe em CheckBoxEnableExtLedKLine. |  |  |  |
| `CheckCambioConSovrappClick` | 0x004C8B04 | 98 | INFERIDO | Ao clicar em CheckCambioConSovrapp «Cambio con sovrapposizione»: chama TValueEditEx::SetVisible; mexe em CheckCambioConSovrapp, EditParamProgress0, EditParamProgress1. |  |  |  |
| `CheckConsumometroClick` | 0x004C1108 | 48 | INFERIDO | Ao clicar em CheckConsumometro «Habil. medidor consumo (só GPL)»: mexe em CheckConsumometro. |  |  | PostMessageW |
| `CheckContributiClick` | 0x004C9C1C | 467 | INFERIDO | Ao clicar em CheckContributi «Enable petrol addition map»: mexe em CheckContributi, DrawGridContributi, LabContribBenz, LabelContribBenz, RadioContributoAltiGiri, RadioGasAltiGiri. |  |  |  |
| `CheckDeleteErrorMotoreSpentoClick` | 0x004C1BA4 | 48 | INFERIDO | Ao clicar em CheckDeleteErrorMotoreSpento «Cancellazione errori OBD a motore spento»: mexe em CheckDeleteErrorMotoreSpento. |  |  |  |
| `CheckDemagModeClick` | 0x004CAE44 | 1 | OBS-COD | Ao clicar em CheckDemagMode «Fast demag mode». Corpo é só RET: nada acontece neste evento (OBS-COD). |  |  |  |
| `CheckDisabilitaCommutatoreClick` | 0x004C1C04 | 48 | INFERIDO | Ao clicar em CheckDisabilitaCommutatore «Inabil. interruptor»: mexe em CheckDisabilitaCommutatore. |  |  |  |
| `CheckElettrovalvoleClick` | 0x004C5048 | 545 | INFERIDO | Ao clicar em CheckElettrovalvole «Gas elettrovalves opened in advance»: mexe em CheckElettrovalvole, EditAnticipoEV1, EditAnticipoEV2. |  |  | PostMessageW |
| `CheckEnabledDhlpDiagnosisClick` | 0x004C975C | 123 | INFERIDO | Ao clicar em CheckEnabledDhlpDiagnosis «Enable gas leaks diagnosis»: corpo de 123 bytes sem fatos diretos (chama 2 funções sem nome). |  |  |  |
| `CheckFlashLubeClick` | 0x004C88AC | 151 | INFERIDO | Ao clicar em CheckFlashLube «Abilita diagnosi livello lubrificante »: mexe em CheckFlashLube, TabLambda. |  |  |  |
| `CheckFlashLubeSuTempAcquaClick` | 0x004B8C98 | 29 | INFERIDO | Ao clicar em CheckFlashLubeSuTempAcqua «Diagnosis on the water temperature sensor for VP system»: mexe em CheckFlashLubeSuTempAcqua. |  |  |  |
| `CheckFlexFuelClick` | 0x004C2C9C | 48 | INFERIDO | Ao clicar em CheckFlexFuel «Flex fuel»: mexe em CheckFlexFuel. |  |  |  |
| `CheckGiriAlberoCammeClick` | 0x004C850C | 456 | INFERIDO | Ao clicar em CheckGiriAlberoCamme «camshaft»: mexe em CheckGiriAlberoCamme, CheckGiriRuotaFonica, CheckSegnaleGiri, ComboAccensione, EditNumDenti, LabelNumDenti. |  |  | GetTickCount, Sleep |
| `CheckGiriRuotaFonicaClick` | 0x004C86D4 | 469 | INFERIDO | Ao clicar em CheckGiriRuotaFonica «flyweel»: mexe em CheckGiriAlberoCamme, CheckGiriRuotaFonica, CheckSegnaleGiri, ComboAccensione, EditNumDenti, LabelNumDenti. |  |  | GetTickCount, Sleep |
| `CheckIncrementoMaxCorrenteClick` | 0x004C721C | 48 | INFERIDO | Ao clicar em CheckIncrementoMaxCorrente «Increase max current in gas change-over»: mexe em CheckIncrementoMaxCorrente. |  |  |  |
| `CheckInibisciEmulazioneClick` | 0x004B9280 | 72 | INFERIDO | Ao clicar em CheckInibisciEmulazione «Desativa a emulação de rotações baixas»: mexe em CheckInibisciEmulazione. |  |  | PostMessageW |
| `CheckIniezioneContinuaClick` | 0x004BC858 | 421 | INFERIDO | Ao clicar em CheckIniezioneContinua «Habilitaçãoretropassagem gasolina por injeção contínua»: mexe em CheckIniezioneContinua, EditIniezioneContinua, LabelIniezioneContinua, LabelSIniezioneContinua. |  |  |  |
| `CheckInjAcquisitionClick` | 0x004A61AC | 251 | INFERIDO | Ao clicar em CheckInjAcquisition «Manual injection sequence»: chama TAebProtocol::IsConnect, TAebProtocol::SendCommand; mexe em ButtonSequenceAcquisition, CheckInjAcquisition, EditRpmTachimetro, LabRpmTachimetro, LabelRpmTachimetro. |  |  |  |
| `CheckIstantaneoClick` | 0x004BFA68 | 56 | INFERIDO | Ao clicar em CheckIstantaneo «Mudança sequencial combust.»: mexe em CheckIstantaneo. |  |  | PostMessageW |
| `CheckMapConfiguratoClick` | 0x004BCB40 | 54 | INFERIDO | Ao clicar em CheckMapConfigurato «Map configurado corretamente»: mexe em CheckMapConfigurato. |  |  | PostMessageW |
| `CheckObdClick` | 0x004CAE4C | 481 | INFERIDO | Ao clicar em CheckObd05 «PID 0x05» (+12 componentes): mexe em ScrollObd. |  |  |  |
| `CheckOutDiagInfoSuKLineClick` | 0x004C1B74 | 48 | INFERIDO | Ao clicar em CheckOutDiagInfoSuKLine «Output high-level on k-line during diagnostic error»: mexe em CheckOutDiagInfoSuKLine. |  |  |  |
| `CheckPartenzaGasCaldoClick` | 0x004C4FB8 | 144 | INFERIDO | Ao clicar em CheckPartenzaGasCaldo «Partida gás com motor aquecido»: mexe em CheckPartenzaGasCaldo, EditTempGasCaldo, LabelT4. |  |  |  |
| `CheckParzializzaClick` | 0x004BFAA0 | 56 | INFERIDO | Ao clicar em CheckParzializza «Deslig. dos injectores gás»: mexe em CheckParzializza. |  |  | PostMessageW |
| `CheckPassPRailClick` | 0x004BCB10 | 48 | INFERIDO | Ao clicar em CheckPassPRail «Reversão automática a gasolina com a pressão do rail»: mexe em CheckPassPRail. |  |  | PostMessageW |
| `CheckPetrolObdConnectionClick` | 0x004C1B0C | 48 | INFERIDO | Ao clicar em CheckPetrolObdConnection «Obd connection in petrol mode»: mexe em CheckPetrolObdConnection. |  |  |  |
| `CheckProgressioniClick` | 0x004CA544 | 519 | INFERIDO | Ao clicar em CheckProgressioni «Enable progressions»: mexe em CheckProgressioni, DrawGridProgressioni, EditIstantaneoValue, LabelIstantaneoValue, LabelProgressCol0, LabelProgressCol1. |  |  |  |
| `CheckRiconoscimentoPienoClick` | 0x004CB12C | 48 | INFERIDO | Ao clicar em CheckRiconoscimentoPieno «Recognition of full»: mexe em CheckRiconoscimentoPieno. |  |  |  |
| `CheckRiscaldamentoClick` | 0x004C6A64 | 1771 | INFERIDO | Ao clicar em CheckRiscaldamento «Heating injectors»: usa textos LABELDURATAATTIVAZIONEINJ, LABELPICCORISCALDAMENTO; chama TTranslatorExt::GetString; mexe em CheckRiscaldamento, CheckboxPreheatAsyncMode, CheckboxPreheatOnlyAfterSleepMode, EditLimiteRitardoAttivWarmUp, EditMantRiscaldamento, EditPeriodoRiscaldamento. | LABELDURATAATTIVAZIONEINJ, LABELPICCORISCALDAMENTO |  |  |
| `CheckRitornoBenzinaClick` | 0x004A9484 | 322 | INFERIDO | Ao clicar em CheckRitornoBenzina «&Automatic change-back to petrol»: mexe em CheckRitornoBenzina, EditRitornoBenzina, LabelRitornoBenzina, LabelS12. |  |  |  |
| `CheckSegnaleGiriClick` | 0x004C5494 | 1092 | INFERIDO | Ao clicar em CheckSegnaleGiri «Tipo segnale giri»: mexe em ButtonSequenceAcquisition, CheckGiriAlberoCamme, CheckGiriRuotaFonica, CheckInjAcquisition, CheckSegnaleGiri, ComboAccensione. |  |  | GetTickCount, Sleep |
| `CheckSequenzaInjClick` | 0x004BC008 | 722 | INFERIDO | Ao clicar em CheckSequenzaInj «Habilitação manual seqüência injeção»: chama TAebNumber::SetEcuData; mexe em ButtonModificaInj, ButtonSequenzaInj, CheckSequenzaInj, ComboSequenzaInj, DrawGridSequenzaInj, DrawGridSequenzaInjBenz. |  |  | PostMessageW |
| `CheckSpegniBuzzerClick` | 0x004C1C34 | 48 | INFERIDO | Ao clicar em CheckSpegniBuzzer «Time limited buzzer»: mexe em CheckSpegniBuzzer. |  |  |  |
| `CheckSplitFuelClick` | 0x004C1C64 | 1978 | INFERIDO | Ao clicar em CheckSplitFuel «Enable split fuel»: mexe em CheckSplitFuel, DrawGridSplitFuel, EditRpmForSplitFuel, LabRpmForSplitFuel, LabelCoeffSplitFuel, LabelRifSplitFuel. |  |  |  |
| `CheckStartAndStopClick` | 0x004C2BD8 | 195 | INFERIDO | Ao clicar em CheckStartAndStop «Start && Stop»: mexe em CheckStartAndStop. |  |  |  |
| `CheckTagliaExtraIniettateClick` | 0x004C1BD4 | 45 | INFERIDO | Ao clicar em CheckTagliaExtraIniettate «Corte extra-injecção»: mexe em CheckTagliaExtraIniettate. |  |  |  |
| `CheckTaglioPompaBenzinaClick` | 0x004C8B68 | 857 | INFERIDO | Ao clicar em CheckTaglioPompaBenzina «Petrol pump cutting»: mexe em CheckAlimentazione, CheckBoxEV2NotConnected, CheckTaglioPompaBenzina, RadioGas, RadioGasAltiGiri. |  |  | PostMessageW |
| `CheckTempAcquaClick` | 0x004B8B30 | 357 | INFERIDO | Ao clicar em CheckTempAcqua «Habilita mudança com temperatura da água»: mexe em CheckTempAcqua, DrawGridPassaggio, EditRitMin, LabelPassaggioGradi, LabelPassaggioSecondi, LabelRitardoCambio. |  |  | PostMessageW |
| `CheckTempMotoreClick` | 0x004B8E88 | 374 | INFERIDO | Ao clicar em CheckTempMotore «Utiliza sensor de temperatura motor do veículo»: mexe em ButtonNuoviParametri, CheckTempMotore, DrawGridTemp, EditDeltaT, EditRPullUp, EditTMax. |  |  | PostMessageW |
| `CheckTempRiduttoreClick` | 0x004B8E10 | 118 | INFERIDO | Ao clicar em CheckTempGas «Habilita mudança com temperatura do gás»: mexe em CheckTempGas, EditTempCambio, LabelT1, LabelTempCambio. |  |  | PostMessageW |
| `CheckTempoMaxCorrenteClick` | 0x004B0E9C | 1 | OBS-COD | Ao clicar em CheckTempoMaxCorrente «Correção atual de manutenção com voltagem dos injetores». Corpo é só RET: nada acontece neste evento (OBS-COD). |  |  |  |
| `CheckTpsBlocKeyPress` | 0x004A8928 | 57 | INFERIDO | Ao digitar (filtro de teclas) em CheckSegnaleGiri «Tipo segnale giri» (+66 componentes): chama APIs SendMessageW. |  | SendMessageW |  |
| `CheckUsaCanPerObdClick` | 0x004C1B3C | 56 | INFERIDO | Ao clicar em CheckUsaCanPerObd «Use CAN for OBD»: mexe em CheckUsaCanPerObd. |  |  |  |
| `CheckValvetronikClick` | 0x004C2B14 | 195 | INFERIDO | Ao clicar em CheckValvetronik «Valvetronik»: mexe em CheckValvetronik. |  |  |  |
| `CheckboxPreheatAsyncModeClick` | 0x004C72D0 | 421 | INFERIDO | Ao clicar em CheckboxPreheatAsyncMode «Asynchronous heating injectors»: mexe em CheckRiscaldamento, CheckboxPreheatAsyncMode, EditPeriodoRiscaldamento, EditPreheatSyncInjNum, LabPeriodoRiscaldamento, LabelPeriodoRiscaldamento. |  |  |  |
| `CheckboxPreheatOnlyAfterSleepModeClick` | 0x004C724C | 132 | INFERIDO | Ao clicar em CheckboxPreheatOnlyAfterSleepMode «Heating injectors only after sleep mode»: mexe em CheckboxPreheatOnlyAfterSleepMode. |  |  |  |
| `ComboAccensioneKeyPress` | 0x004BA230 | 188 | INFERIDO | Ao digitar (filtro de teclas) em ComboAccensione: mexe em ComboAccensione, ComboCilindri. |  |  |  |
| `ComboCambioKeyPress` | 0x004A8FB8 | 253 | INFERIDO | Ao digitar (filtro de teclas) em ComboCambio: mexe em ComboCambio. |  |  | PostMessageW |
| `ComboCarburanteKeyPress` | 0x004B96DC | 1348 | INFERIDO | Ao digitar (filtro de teclas) em ComboCarburante: chama APIs Sleep; chama TFormAttesa.Create, TFormConfig.FormResize; mexe em ComboCarburante, TabCambio, TabPressione, TimerDati. |  | Sleep | DeleteFileW, FindResourceW, GetTempFileNameW, GetTempPathW |
| `ComboCilindriKeyPress` | 0x004BA2EC | 284 | INFERIDO | Ao digitar (filtro de teclas) em ComboCilindri: mexe em CheckInjAcquisition, ComboAccensione, ComboCilindri. |  |  | PostMessageW |
| `ComboFullGroupKeyPress` | 0x004C6830 | 27 | INFERIDO | Ao digitar (filtro de teclas) em ComboFullGroup: mexe em ComboFullGroup. |  |  |  |
| `ComboKKeyPress` | 0x004C4880 | 282 | INFERIDO | Ao digitar (filtro de teclas) em ComboK: mexe em ComboK. |  |  |  |
| `ComboNumeroSondeKeyPress` | 0x004A9664 | 64 | INFERIDO | Ao digitar (filtro de teclas) em ComboNumeroSonde: corpo de 64 bytes sem fatos diretos (chama 3 funções sem nome). |  |  |  |
| `ComboPressioneKeyPress` | 0x004AE420 | 872 | INFERIDO | Ao digitar (filtro de teclas) em ComboPressioneMisurata (+1 componentes): usa textos COMBOPRESSIONEMISURATA0, COMBOPRESSIONEMISURATA1, COMBOPRESSIONEMISURATA2, COMBOPRESSIONEMISURATA3, LABELTIPOPRESSIONEMIS; chama TTranslatorExt::GetString; mexe em ComboMisuratorePressione, ComboPressioneMisurata, LabelTipoPressioneMisurata. | COMBOPRESSIONEMISURATA0, COMBOPRESSIONEMISURATA1, COMBOPRESSIONEMISURATA2, COMBOPRESSIONEMISURATA3, LABELTIPOPRESSIONEMIS, LABELTIPOPRESSIONEMIS1 |  | PostMessageW |
| `ComboSegnaleGiriKeyPress` | 0x004ACDC8 | 390 | INFERIDO | Ao digitar (filtro de teclas) em ComboSegnaleGiri: mexe em ComboSegnaleGiri, EditSogliaGiri, LabSogliaGiri, LabelSogliaGiri. |  |  |  |
| `ComboSensoreKeyPress` | 0x004A8C60 | 86 | INFERIDO | Ao digitar (filtro de teclas) em ComboSensore: mexe em ComboSensore. |  |  |  |
| `ComboSequenzaInjChange` | 0x004BBF3C | 95 | INFERIDO | Quando muda o valor de ComboSequenzaInj: mexe em ComboSequenzaInj, DrawGridSequenzaInj. |  |  |  |
| `ComboSequenzaInjKeyDown` | 0x004BBF9C | 106 | INFERIDO | Ao pressionar tecla em ComboSequenzaInj: corpo de 106 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `ComboSfasaturaKeyPress` | 0x004BD748 | 2188 | INFERIDO | Ao digitar (filtro de teclas) em ComboSfasatura: chama TAebNumber::SetEcuData, TAebProtocol::IsConnect; mexe em RadioGasAltiGiri. |  |  | PostMessageW |
| `ComboSonda1KeyPress` | 0x004C2D94 | 166 | INFERIDO | Ao digitar (filtro de teclas) em ComboSonda1 (+1 componentes): mexe em ComboSonda1, ComboSonda2. |  |  |  |
| `ComboTagliaIniettoriKeyPress` | 0x004C1138 | 126 | INFERIDO | Ao digitar (filtro de teclas) em ComboTagliaIniettori: mexe em ComboTagliaIniettori. |  |  |  |
| `ComboTagliaKeyPress` | 0x004BCD94 | 391 | INFERIDO | Ao digitar (filtro de teclas) em ComboTaglia: mexe em ComboTaglia. |  |  | GetTickCount, Sleep |
| `ComboTempRiduttoreKeyPress` | 0x004B9494 | 52 | INFERIDO | Ao digitar (filtro de teclas) em ComboTempRiduttore: corpo de 52 bytes sem fatos diretos (chama 2 funções sem nome). |  |  |  |
| `ComboTipoIniettoreKeyPress` | 0x004BA4B4 | 2089 | INFERIDO | Ao digitar (filtro de teclas) em ComboTipoIniettore: chama APIs Sleep; chama TFormAttesa.Create, TFormConfig.FormResize; mexe em ComboTipoIniettore, EditTempoMortoBenz, EditTempoMortoGas, LabelGC, LabelMsBenz, LabelMsGas. |  | Sleep | DeleteFileW, FindResourceW, GetTempFileNameW, GetTempPathW |
| `ComboTipoLambdaKeyPress` | 0x004A6674 | 251 | INFERIDO | Ao digitar (filtro de teclas) em ComboTipoLambda: mexe em ComboTipoLambda, EditLivEmulAlto, EditLivEmulBasso, EditRifInfCalda, EditRifInfFredda, EditRifLambda. |  |  |  |
| `ComboTipoMisuratoreKeyPress` | 0x004BEC34 | 975 | INFERIDO | Ao digitar (filtro de teclas) em ComboTipoMisuratore: chama TFormConfig.TabPressioneShow; mexe em ComboTipoMisuratore. |  |  | PostMessageW |
| `ComboTipoObdKeyPress` | 0x004C0F18 | 298 | INFERIDO | Ao digitar (filtro de teclas) em ComboTipoObd: mexe em ComboTipoObd. |  |  |  |
| `ComboTurboKeyPress` | 0x004B9C44 | 1348 | INFERIDO | Ao digitar (filtro de teclas) em ComboTurbo: chama APIs Sleep; chama TFormAttesa.Create, TFormConfig.FormResize; mexe em ComboTurbo, TabCambio, TabPressione, TimerDati. |  | Sleep | DeleteFileW, FindResourceW, GetTempFileNameW, GetTempPathW |
| `ControlMouseDown` | 0x004C6020 | 250 | INFERIDO | Ao pressionar o mouse em LabelRitardoCambioGas «Atraso na reversão gasolina-gás com temp. Gás» (+141 componentes): corpo de 250 bytes sem fatos diretos (chama 8 funções sem nome). |  |  |  |
| `DrawDelayGasTempDblClick` | 0x004C42E4 | 99 | INFERIDO | Ao dar duplo clique em DrawDelayGasTemp: chama APIs GetCursorPos, ScreenToClient; mexe em DrawDelayGasTemp. |  | GetCursorPos, ScreenToClient |  |
| `DrawDelayGasTempDrawCell` | 0x004C3EA4 | 1070 | INFERIDO | Ao desenhar célula de DrawDelayGasTemp: usa textos CHANGEDELAYCALIBR, CHANGEDELAYREFS, CHANGEDELAYSWITCH; chama TTranslatorExt::GetString; mexe em DrawDelayGasTemp. | CHANGEDELAYCALIBR, CHANGEDELAYREFS, CHANGEDELAYSWITCH |  |  |
| `DrawDelayGasTempKeyPress` | 0x004C42D4 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawDelayGasTemp: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridAnticipoDblClick` | 0x004BD2E4 | 99 | INFERIDO | Ao dar duplo clique em DrawGridAnticipo: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridAnticipo. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridAnticipoDrawCell` | 0x004BCF1C | 967 | INFERIDO | Ao desenhar célula de DrawGridAnticipo: mexe em DrawGridAnticipo. |  |  |  |
| `DrawGridAnticipoKeyPress` | 0x004BD348 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridAnticipo: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridAnticipoMultipointDblClick` | 0x004C3C38 | 99 | INFERIDO | Ao dar duplo clique em DrawGridAnticipoMultipoint: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridAnticipoMultipoint. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridAnticipoMultipointDrawCell` | 0x004C3944 | 740 | INFERIDO | Ao desenhar célula de DrawGridAnticipoMultipoint: usa textos LABELRITEMULRICCA; chama TTranslatorExt::GetString; mexe em DrawGridAnticipoMultipoint. | LABELRITEMULRICCA |  |  |
| `DrawGridAnticipoMultipointKeyPress` | 0x004C3C28 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridAnticipoMultipoint: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridBenzinaDblClick` | 0x004C157C | 99 | INFERIDO | Ao dar duplo clique em DrawGridBenzina: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridBenzina. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridBenzinaDrawCell` | 0x004C11B8 | 964 | INFERIDO | Ao desenhar célula de DrawGridBenzina: mexe em DrawGridBenzina. |  |  |  |
| `DrawGridBenzinaKeyPress` | 0x004C15E0 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridBenzina: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridContributiDblClick` | 0x004CA288 | 99 | INFERIDO | Ao dar duplo clique em DrawGridContributi: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridContributi. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridContributiDrawCell` | 0x004C9DF0 | 1159 | INFERIDO | Ao desenhar célula de DrawGridContributi: mexe em DrawGridContributi. |  |  |  |
| `DrawGridContributiKeyPress` | 0x004CA278 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridContributi: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridEmissioniDblClick` | 0x004B4530 | 99 | INFERIDO | Ao dar duplo clique em DrawGridEmissioni: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridEmissioni. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridEmissioniDrawCell` | 0x004B3F68 | 1461 | INFERIDO | Ao desenhar célula de DrawGridEmissioni: usa textos LABELGIRIEMUL, LABELRIFLAMBDAEMUL, LABELRIFLAMBDAEMULUEGO, LABELRITEMULRICCA; chama TTranslatorExt::GetString; mexe em DrawGridEmissioni. | LABELGIRIEMUL, LABELRIFLAMBDAEMUL, LABELRIFLAMBDAEMULUEGO, LABELRITEMULRICCA |  |  |
| `DrawGridEmissioniKeyPress` | 0x004B4520 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridEmissioni: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridEmissioniMultipointDblClick` | 0x004C33E0 | 243 | INFERIDO | Ao dar duplo clique em DrawGridEmissioniMultipoint: chama APIs GetCursorPos, ScreenToClient; chama TFormCentriCelle.Create; mexe em DrawGridEmissioniMultipoint. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridEmissioniMultipointDrawCell` | 0x004C2E3C | 1426 | INFERIDO | Ao desenhar célula de DrawGridEmissioniMultipoint: mexe em DrawGridEmissioni, DrawGridEmissioniMultipoint. |  |  |  |
| `DrawGridEmissioniMultipointKeyPress` | 0x004C33D0 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridEmissioniMultipoint: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridKDblClick` | 0x004B1940 | 153 | INFERIDO | Ao dar duplo clique em DrawGridK: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridK, RadioMapAdatta, RadioMapPetrol. |  | GetCursorPos, ScreenToClient | PostMessageW |
| `DrawGridKDrawCell` | 0x004B12EC | 1544 | INFERIDO | Ao desenhar célula de DrawGridK: mexe em DrawGridK, RadioMapAdatta, RadioMapPetrol. |  |  |  |
| `DrawGridKKeyDown` | 0x004B2514 | 159 | INFERIDO | Ao pressionar tecla em DrawDelayGasTemp (+23 componentes): corpo de 159 bytes sem fatos diretos (chama 2 funções sem nome). |  |  |  |
| `DrawGridKKeyPress` | 0x004B18F4 | 74 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridK: mexe em RadioMapAdatta, RadioMapPetrol. |  |  | PostMessageW |
| `DrawGridOpenLoopDblClick` | 0x004B29E0 | 99 | INFERIDO | Ao dar duplo clique em DrawGridOpenLoop: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridOpenLoop. |  | GetCursorPos, ScreenToClient | PostMessageW |
| `DrawGridOpenLoopDrawCell` | 0x004B25B4 | 1049 | INFERIDO | Ao desenhar célula de DrawGridOpenLoop: mexe em DrawGridOpenLoop. |  |  |  |
| `DrawGridOpenLoopKeyPress` | 0x004B29D0 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridOpenLoop: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  | PostMessageW |
| `DrawGridPassaggioDblClick` | 0x004B3128 | 38 | INFERIDO | Ao dar duplo clique em DrawGridPassaggio: chama APIs GetCursorPos; mexe em DrawGridPassaggio. |  | GetCursorPos |  |
| `DrawGridPassaggioDrawCell` | 0x004B2D64 | 75 | INFERIDO | Ao desenhar célula de DrawGridPassaggio: mexe em DrawGridPassaggio. |  |  |  |
| `DrawGridPassaggioGasDblClick` | 0x004B3A24 | 99 | INFERIDO | Ao dar duplo clique em DrawGridPassaggioGas: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridPassaggioGas. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridPassaggioGasDrawCell` | 0x004B3660 | 945 | INFERIDO | Ao desenhar célula de DrawGridPassaggioGas: usa textos LABELOLTRE; chama TTranslatorExt::GetString; mexe em DrawGridPassaggioGas. | LABELOLTRE |  |  |
| `DrawGridPassaggioGasKeyPress` | 0x004B3A14 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridPassaggioGas: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridPassaggioKeyPress` | 0x004B3118 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridPassaggio: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridPressAssDblClick` | 0x004B6178 | 99 | INFERIDO | Ao dar duplo clique em DrawGridPressAss: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridPressAss. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridPressAssDrawCell` | 0x004B5E90 | 728 | INFERIDO | Ao desenhar célula de DrawGridPressAss: mexe em DrawGridPressAss. |  |  |  |
| `DrawGridPressAssKeyPress` | 0x004B6168 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridPressAss: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridPressDiffDblClick` | 0x004B5B68 | 99 | INFERIDO | Ao dar duplo clique em DrawGridPressDiff: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridPressDiff. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridPressDiffDrawCell` | 0x004B5880 | 728 | INFERIDO | Ao desenhar célula de DrawGridPressDiff: mexe em DrawGridPressDiff. |  |  |  |
| `DrawGridPressDiffKeyPress` | 0x004B5B58 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridPressDiff: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridPressPassDblClick` | 0x004B6EF4 | 99 | INFERIDO | Ao dar duplo clique em DrawGridPressPass: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridPressPass. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridPressPassDrawCell` | 0x004B6B1C | 982 | INFERIDO | Ao desenhar célula de DrawGridPressPass: mexe em DrawGridPressPass. |  |  |  |
| `DrawGridPressPassKeyPress` | 0x004B6F58 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridPressPass: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridProgressioniDblClick` | 0x004CA9A0 | 86 | INFERIDO | Ao dar duplo clique em DrawGridProgressioni: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridProgressioni. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridProgressioniDrawCell` | 0x004CA74C | 577 | INFERIDO | Ao desenhar célula de DrawGridProgressioni: chama TAebMatrix::GetInt; mexe em DrawGridProgressioni. |  |  |  |
| `DrawGridProgressioniKeyPress` | 0x004CA990 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridProgressioni: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridSequenzaInjBenzDrawCell` | 0x004BBB88 | 930 | INFERIDO | Ao desenhar célula de DrawGridSequenzaInjBenz: mexe em DrawGridSequenzaInjBenz. |  |  |  |
| `DrawGridSequenzaInjBenzSelectCell` | 0x004BBF2C | 13 | INFERIDO | Evento OnSelectCell de DrawGridSequenzaInjBenz: corpo de 13 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `DrawGridSequenzaInjDrawCell` | 0x004BB664 | 1099 | INFERIDO | Ao desenhar célula de DrawGridSequenzaInj: mexe em DrawGridSequenzaInj. |  |  |  |
| `DrawGridSequenzaInjSelectCell` | 0x004BBAB0 | 213 | INFERIDO | Evento OnSelectCell de DrawGridSequenzaInj: mexe em ComboSequenzaInj, DrawGridSequenzaInj. |  |  |  |
| `DrawGridSplitFuelDblClick` | 0x004C2788 | 99 | INFERIDO | Ao dar duplo clique em DrawGridSplitFuel: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridSplitFuel. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridSplitFuelDrawCell` | 0x004C2420 | 870 | INFERIDO | Ao desenhar célula de DrawGridSplitFuel: mexe em DrawGridSplitFuel. |  |  |  |
| `DrawGridSplitFuelKeyPress` | 0x004C27EC | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridSplitFuel: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridTInjDefaultDblClick` | 0x004B6868 | 99 | INFERIDO | Ao dar duplo clique em DrawGridTInjDefault: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridTInjDefault. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridTInjDefaultDrawCell` | 0x004B64A0 | 968 | INFERIDO | Ao desenhar célula de DrawGridTInjDefault: mexe em DrawGridTInjDefault. |  |  |  |
| `DrawGridTInjDefaultKeyPress` | 0x004B68CC | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridTInjDefault: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridTempDblClick` | 0x004B81F4 | 174 | INFERIDO | Ao dar duplo clique em DrawGridTemp: chama APIs GetCursorPos, ScreenToClient; mexe em ButtonNuoviParametri, DrawGridTemp. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridTempDrawCell` | 0x004B7874 | 2090 | INFERIDO | Ao desenhar célula de DrawGridTemp: usa textos LABELINDEXTEMP, LABELNTCTEMP, LABELTEMPTEMP, LABELVOLTTEMP; chama TTranslatorExt::GetString; mexe em DrawGridTemp, EditDeltaT, EditRPullUp, EditTMin. | LABELINDEXTEMP, LABELNTCTEMP, LABELTEMPTEMP, LABELVOLTTEMP |  |  |
| `DrawGridTempEnter` | 0x004B868C | 23 | INFERIDO | Ao entrar no campo DrawGridTemp: corpo de 23 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridTempExit` | 0x004B86A4 | 32 | INFERIDO | Ao sair do campo DrawGridTemp: mexe em DrawGridTemp, LabelInstr. |  |  |  |
| `DrawGridTempKeyPress` | 0x004B80B0 | 324 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridTemp: mexe em ButtonNuoviParametri, DrawGridTemp. |  |  |  |
| `DrawGridTempMotDblClick` | 0x004B54F8 | 99 | INFERIDO | Ao dar duplo clique em DrawGridTempMot: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridTempMot. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridTempMotDrawCell` | 0x004B5134 | 948 | INFERIDO | Ao desenhar célula de DrawGridTempMot: usa textos LABELOLTRE; chama TTranslatorExt::GetString; mexe em DrawGridTempMot. | LABELOLTRE |  |  |
| `DrawGridTempMotKeyPress` | 0x004B54E8 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridTempMot: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridTempRidDblClick` | 0x004B4DAC | 99 | INFERIDO | Ao dar duplo clique em DrawGridTempRid: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridTempRid. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridTempRidDrawCell` | 0x004B49E8 | 948 | INFERIDO | Ao desenhar célula de DrawGridTempRid: usa textos LABELOLTRE; chama TTranslatorExt::GetString; mexe em DrawGridTempRid. | LABELOLTRE |  |  |
| `DrawGridTempRidKeyPress` | 0x004B4D9C | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridTempRid: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridTempiLrDblClick` | 0x004BE5A8 | 99 | INFERIDO | Ao dar duplo clique em DrawGridTempiLr: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridTempiLr. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridTempiLrDrawCell` | 0x004BE278 | 799 | INFERIDO | Ao desenhar célula de DrawGridTempiLr: mexe em DrawGridTempiLr. |  |  |  |
| `DrawGridTempiLrKeyPress` | 0x004BE598 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridTempiLr: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `DrawGridTestDblClick` | 0x004B75EC | 99 | INFERIDO | Ao dar duplo clique em DrawGridTest: chama APIs GetCursorPos, ScreenToClient; mexe em DrawGridTest. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridTestDrawCell` | 0x004B71B8 | 1074 | INFERIDO | Ao desenhar célula de DrawGridTest: mexe em DrawGridTest. |  |  |  |
| `DrawGridTestKeyPress` | 0x004B7650 | 16 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridTest: corpo de 16 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `EditAdvParamInjGasKeyPress` | 0x004C7150 | 187 | INFERIDO | Ao digitar (filtro de teclas) em EditCicchettoRpm (+5 componentes): corpo de 187 bytes sem fatos diretos (chama 7 funções sem nome). |  |  |  |
| `EditAntiStalloKeyPress` | 0x004C97D8 | 81 | INFERIDO | Ao digitar (filtro de teclas) em EditAntiStallo4 (+4 componentes): chama TAebVector::GetInt, TAebVector::SetInt. |  |  |  |
| `EditAperturaInjKeyPress` | 0x004ACF68 | 85 | INFERIDO | Ao digitar (filtro de teclas) em EditChiusuraInj (+1 componentes): corpo de 85 bytes sem fatos diretos (chama 2 funções sem nome). |  |  |  |
| `EditBassaPressioneMpKeyPress` | 0x004C526C | 520 | INFERIDO | Ao digitar (filtro de teclas) em EditBassaPressioneMp: corpo de 520 bytes sem fatos diretos (chama 9 funções sem nome). |  |  |  |
| `EditCorrBancata2KeyPress` | 0x004C2CCC | 197 | INFERIDO | Ao digitar (filtro de teclas) em EditCorrBancata2: mexe em EditCorrBancata2. |  |  |  |
| `EditCorrCarbMinimoKeyPress` (sem evento no DFM) | 0x0049F0E4 | 137 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): corpo de 137 bytes sem fatos diretos (chama 5 funções sem nome). |  |  |  |
| `EditCorrIntInfKeyPress` | 0x004B91BC | 193 | INFERIDO | Ao digitar (filtro de teclas) em EditCorrIntInf (+1 componentes): mexe em EditCorrIntInf, EditCorrIntSup. |  |  |  |
| `EditCutoffKeyPress` | 0x004C82B8 | 577 | INFERIDO | Ao digitar (filtro de teclas) em EditGiriCutoff (+3 componentes): mexe em EditGiriCutoff, EditPressioneCutoff, EditTBenzMaxCutoff, EditTInjCutoff. |  |  |  |
| `EditDeltaTInterruzioneWarmupKeyPress` | 0x004C8ECC | 182 | INFERIDO | Ao digitar (filtro de teclas) em EditDeltaTInterruzioneWarmup: chama TAebNumber::GetInt, TAebNumber::SetInt; mexe em EditDeltaTInterruzioneWarmup. |  |  |  |
| `EditFiltroEnter` | 0x004BDFD4 | 564 | INFERIDO | Ao entrar no campo EditFiltro: mexe em EditFiltro. |  |  |  |
| `EditFiltroExit` | 0x004BE208 | 112 | INFERIDO | Ao sair do campo EditFiltro: mexe em EditFiltro. |  |  |  |
| `EditFiltroKeyPress` | 0x004B9644 | 149 | INFERIDO | Ao digitar (filtro de teclas) em EditFiltro: mexe em EditFiltro. |  |  |  |
| `EditFlashLubeKeyPress` | 0x004C8948 | 406 | INFERIDO | Ao digitar (filtro de teclas) em EditFlashLube0 (+3 componentes): mexe em EditFlashLube0, EditFlashLube1, EditFlashLube2, EditFlashLube3. |  |  |  |
| `EditFrestEnter` | 0x004C611C | 503 | INFERIDO | Ao entrar no campo EditFrestGasExtraInjThd (+5 componentes): mexe em LabFrestGasInjOffset, LabelLimite. |  |  |  |
| `EditFrestExit` | 0x004C6314 | 14 | INFERIDO | Ao sair do campo EditFrestGasExtraInjThd (+5 componentes): corpo de 14 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `EditGiriCambioEnter` | 0x004A98D8 | 1000 | INFERIDO | Ao entrar no campo EditSovrapposizione (+136 componentes): mexe em LabelLimite. |  |  |  |
| `EditGiriCambioExit` | 0x004A9CC0 | 14 | INFERIDO | Ao sair do campo EditSovrapposizione (+136 componentes): corpo de 14 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `EditGiriKFiltroEnter` | 0x004BE90C | 430 | INFERIDO | Ao entrar no campo EditCorrIntInf (+17 componentes): corpo de 430 bytes sem fatos diretos (chama 10 funções sem nome). |  |  |  |
| `EditGiriKFiltroExit` | 0x004BEABC | 102 | INFERIDO | Ao sair do campo EditCorrIntInf (+17 componentes): corpo de 102 bytes sem fatos diretos (chama 5 funções sem nome). |  |  |  |
| `EditGiriKFiltroKeyPress` | 0x004BEB24 | 269 | INFERIDO | Ao digitar (filtro de teclas) em EditGiriKFiltro (+1 componentes): mexe em EditGiriAltiKFiltro, EditGiriKFiltro. |  |  |  |
| `EditGiriMinimoPerBenzinaKeyPress` | 0x004BFE6C | 136 | INFERIDO | Ao digitar (filtro de teclas) em EditGiriMinimoPerBenzina: corpo de 136 bytes sem fatos diretos (chama 5 funções sem nome). |  |  |  |
| `EditGiriPerBenzinaKeyPress` | 0x004C04E8 | 136 | INFERIDO | Ao digitar (filtro de teclas) em EditGiriPerBenzina: corpo de 136 bytes sem fatos diretos (chama 5 funções sem nome). |  |  |  |
| `EditKFiltroKeyPress` | 0x004B95C0 | 130 | INFERIDO | Ao digitar (filtro de teclas) em EditKFiltro: corpo de 130 bytes sem fatos diretos (chama 5 funções sem nome). |  |  |  |
| `EditKMappaNeutroKeyPress` | 0x004C499C | 96 | INFERIDO | Ao digitar (filtro de teclas) em EditKMappaNeutro: mexe em EditKMappaNeutro. |  |  |  |
| `EditOffsetKeyPress` | 0x004AF360 | 72 | INFERIDO | Ao digitar (filtro de teclas) em EditPendenzaDiff (+3 componentes): corpo de 72 bytes sem fatos diretos (chama 4 funções sem nome). |  |  |  |
| `EditPreheatSyncInjNumKeyPress` | 0x004C78A4 | 95 | INFERIDO | Ao digitar (filtro de teclas) em EditPreheatSyncInjNum (+1 componentes): corpo de 95 bytes sem fatos diretos (chama 5 funções sem nome). |  |  |  |
| `EditRPullUpEnter` | 0x004B86C4 | 506 | INFERIDO | Ao entrar no campo EditDeltaT (+3 componentes): mexe em LabelLimite. |  |  |  |
| `EditRPullUpExit` | 0x004B88C0 | 624 | INFERIDO | Ao sair do campo EditDeltaT (+3 componentes): mexe em DrawGridTemp, EditDeltaT, EditRPullUp, EditTMax, EditTMin, LabelLimite. |  |  |  |
| `EditRifLambdaKeyPress` | 0x004A6A00 | 201 | INFERIDO | Ao digitar (filtro de teclas) em EditSovrapposizione (+103 componentes): corpo de 201 bytes sem fatos diretos (chama 6 funções sem nome). |  |  |  |
| `EditRiscaldamentoKeyPress` | 0x004C7478 | 1052 | INFERIDO | Ao digitar (filtro de teclas) em EditPiccoRiscaldamento (+5 componentes): mexe em EditMantRiscaldamento, EditPeriodoRiscaldamento, EditPiccoRiscaldamento, EditPreheatPGasTh, EditPreheatPInjTimeTh, EditPreheatTempGasTh. |  |  |  |
| `EditRpmTachimetroKeyPress` | 0x004A647C | 502 | INFERIDO | Ao digitar (filtro de teclas) em EditRpmTachimetro: chama TAebProtocol::IsConnect, TAebProtocol::SendCommand; mexe em EditRpmTachimetro, LabRpmTachimetro, LabelRpmTachimetro. |  |  |  |
| `EditScartoMinFuoriMinKeyPress` | 0x004BA1E8 | 71 | INFERIDO | Ao digitar (filtro de teclas) em EditScartoMinFuoriMin: corpo de 71 bytes sem fatos diretos (chama 4 funções sem nome). |  |  |  |
| `EditSensAdattaKeyPress` | 0x004C07D0 | 132 | INFERIDO | Ao digitar (filtro de teclas) em EditSensAdatta: corpo de 132 bytes sem fatos diretos (chama 5 funções sem nome). |  |  |  |
| `EditSogliaGiriKeyPress` | 0x004C58DC | 140 | INFERIDO | Ao digitar (filtro de teclas) em EditSogliaGiri: mexe em EditSogliaGiri. |  |  |  |
| `EditTInjSup22KeyPress` | 0x004C3E44 | 96 | INFERIDO | Ao digitar (filtro de teclas) em EditTInjSup22: mexe em EditTInjSup22. |  |  |  |
| `EditTempAcquaKeyPress` | 0x004BCB78 | 479 | INFERIDO | Ao digitar (filtro de teclas) em EditTempAcqua (+2 componentes): mexe em EditPressRid, EditSogliaMap, EditTempAcqua. |  |  |  |
| `EditTempMotoreKeyPress` | 0x004C8F84 | 348 | INFERIDO | Ao digitar (filtro de teclas) em EditTempMotore: chama TAebNumber::GetInt, TAebNumber::SetInt; mexe em EditDeltaTInterruzioneWarmup, EditTempMotore. |  |  |  |
| `EditTempoBenzinaKeyPress` | 0x004C0570 | 127 | INFERIDO | Ao digitar (filtro de teclas) em EditTempoBenzina3 (+1 componentes): corpo de 127 bytes sem fatos diretos (chama 4 funções sem nome). |  |  |  |
| `EditTempoMortoBenzKeyPress` | 0x004B11B0 | 162 | INFERIDO | Ao digitar (filtro de teclas) em EditTempoMortoBenz (+1 componentes): corpo de 162 bytes sem fatos diretos (chama 3 funções sem nome). |  |  |  |
| `EditTipoIniettoreKeyPress` | 0x004BACE0 | 436 | INFERIDO | Ao digitar (filtro de teclas) em EditTipoIniettore: mexe em EditTipoIniettore. |  |  |  |
| `EditVH34ParamKeyPress` | 0x004CAE48 | 1 | OBS-COD | Ao digitar (filtro de teclas) em EditCorrenteDiPicco (+13 componentes). Corpo é só RET: nada acontece neste evento (OBS-COD). |  |  |  |
| `FormClose` | 0x00490C98 | 32 | INFERIDO | Ao fechar o formulário FormConfig «Configuração do veículo»: corpo de 32 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `FormCloseQuery` | 0x004ADE94 | 372 | INFERIDO | Ao pedir para fechar o formulário FormConfig «Configuração do veículo»: chama TFormConfig.ButtonNuoviParametriClick, TFormConfig.ButtonTaraturaClick; mexe em PageControl, TabKCoefficienti, TabTemp. |  |  |  |
| `FormDestroy` | 0x00490BB4 | 187 | INFERIDO | Evento OnDestroy de FormConfig «Configuração do veículo»: corpo de 187 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `FormKeyDown` | 0x0049EB24 | 1469 | INFERIDO | Ao pressionar tecla em FormConfig «Configuração do veículo»: chama TFormConfig.ButtonNuoviParametriClick, TFormConfig.ButtonTaraturaClick; mexe em PageControl, TabKCoefficienti, TabTemp. |  |  |  |
| `FormResize` | 0x00490CB8 | 4190 | INFERIDO | Ao redimensionar FormConfig «Configuração do veículo»: mexe em DrawGridAnticipo, DrawGridAnticipoMultipoint, DrawGridContributi, DrawGridEmissioni, DrawGridEmissioniMultipoint, DrawGridK. |  |  |  |
| `FormShow` | 0x00490028 | 1418 | INFERIDO | Ao exibir o formulário FormConfig «Configuração do veículo»: usa textos COMBOACCENSIONE0, COMBOACCENSIONE1, COMBOACCENSIONE2, COMBOACCENSIONE3, COMBOACCENSIONE4; chama TTranslatorExt::GetString; mexe em ComboCilindri, ComboFullGroup, ComboTipoLambda. | COMBOACCENSIONE0, COMBOACCENSIONE1, COMBOACCENSIONE2, COMBOACCENSIONE3, COMBOACCENSIONE4, COMBOACCENSIONE5 |  |  |
| `ImageInjOffClick` | 0x004C81B0 | 264 | INFERIDO | Ao clicar em ImageInjOffF (+9 componentes): chama TAebNumber::GetEcuData, TAebNumber::SetEcuData, TAebProtocol::IsConnect. |  |  |  |
| `ImageInjOnClick` | 0x004C80A8 | 262 | INFERIDO | Ao clicar em ImageInjOnG (+9 componentes): chama TAebNumber::GetEcuData, TAebNumber::SetEcuData, TAebProtocol::IsConnect. |  |  |  |
| `LabGasClick` | 0x004C9A7C | 318 | INFERIDO | Ao clicar em ImageCommutatore (+1 componentes): chama APIs Sleep; mexe em ImageCommutatore, LabGas. |  | Sleep | PostMessageW |
| `LabelDiagnosiClick` | 0x004CB060 | 165 | INFERIDO | Ao clicar em LabelDiagnosi «DIAGNOSI»: chama TFormDiagnosi.Create; mexe em TimerDati. |  |  |  |
| `MenuAebBeeClick` | 0x004AE414 | 1 | OBS-COD | Ao clicar em MenuAebBee «Proprietà Aeb Device». Corpo é só RET: nada acontece neste evento (OBS-COD). |  |  |  |
| `MenuComunicazioneSerialeCostruitaClick` | 0x004AE008 | 1033 | INFERIDO | Ao clicar em MenuComunicazioneSerialeCostruita «Comunicazione seriale costruita»: chama APIs CloseHandle, CreateProcessW, WaitForSingleObject; chama TAebProtocol::Connect, TAebProtocol::Disconnect, TAebProtocol::IsConnect, TAebProtocol::OffLine, TAebProtocol::ReadPort. |  | CloseHandle, CreateProcessW, WaitForSingleObject |  |
| `MenuControlloIniettoriClick` | 0x004BA408 | 54 | INFERIDO | Ao clicar em MenuControlloIniettori «Abilita controllo iniettori»: mexe em MenuControlloIniettori. |  |  | PostMessageW |
| `MenuCopiaTaraturaClick` | 0x004AD0B0 | 1031 | INFERIDO | Ao clicar em MenuCopia «&Copiar»: mexe em DrawGridK, RadioMapAdatta, RadioMapPetrol. |  |  |  |
| `MenuEsciClick` (sem evento no DFM) | 0x004ACF60 | 6 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): corpo de 6 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `MenuIncollaTaraturaClick` | 0x004AD4B8 | 2097 | INFERIDO | Ao clicar em MenuIncolla «&Colar»: corpo de 2097 bytes sem fatos diretos (chama 20 funções sem nome). |  |  |  |
| `MenuSbloccaLimitiClick` | 0x004A95C8 | 26 | INFERIDO | Ao clicar em MenuSbloccaLimiti «Unlock limits»: mexe em MenuSbloccaLimiti. |  |  |  |
| `PageControlChanging` | 0x004ADD20 | 372 | INFERIDO | Evento OnChanging de PageControl: chama TFormConfig.ButtonNuoviParametriClick, TFormConfig.ButtonTaraturaClick; mexe em PageControl, TabKCoefficienti, TabTemp. |  |  |  |
| `PageControlDrawTab` | 0x0049E83C | 741 | INFERIDO | Evento OnDrawTab de PageControl: mexe em PageControl. |  |  |  |
| `PopupMenuGridPopup` | 0x004ADCEC | 51 | INFERIDO | Ao abrir o menu de contexto PopupMenuGrid: mexe em MenuIncolla. |  |  |  |
| `RadioAdatta25Click` | 0x004C05F0 | 251 | INFERIDO | Ao clicar em RadioAdatta25 «máx ±25%» (+2 componentes): corpo de 251 bytes sem fatos diretos (chama 3 funções sem nome). |  |  |  |
| `RadioAdattaDirittiClick` | 0x004C06EC | 226 | INFERIDO | Ao clicar em RadioAdattaDiritti «direita» (+1 componentes): corpo de 226 bytes sem fatos diretos (chama 3 funções sem nome). |  |  |  |
| `RadioGasAltiGiriClick` | 0x004BFEF4 | 798 | INFERIDO | Ao clicar em RadioGasAltiGiri «Gás» (+2 componentes): mexe em EditGiriPerBenzina, EditGiriSupBenzina, EditTempoBenzina1. |  |  | PostMessageW |
| `RadioGasClick` | 0x004BFAD8 | 906 | INFERIDO | Ao clicar em RadioGas «Gás» (+2 componentes): mexe em EditGiriMinimoPerBenzina, EditIniettate, EditIniettateCutoff, EditUscita, LabGiriMinimoPerBenzina, LabelGiriMinimoPerBenzina. |  |  | PostMessageW |
| `RadioMapClick` | 0x004B23A0 | 362 | INFERIDO | Ao clicar em RadioMapPetrol «petrol» (+2 componentes): mexe em DrawGridK, TabKCoefficienti. |  |  |  |
| `TabAdattaShow` | 0x004C0854 | 1254 | INFERIDO | Ao exibir o formulário TabAdatta «Adaptabilidade OBD»: mexe em CheckAdattaBenzina, CheckAdattativita, CheckAzzeraBlackListObd, CheckAzzeraErroriObd, CheckDeleteErrorMotoreSpento, CheckOutDiagInfoSuKLine. |  |  |  |
| `TabAdvancedShow` | 0x004C9BBC | 95 | INFERIDO | Ao exibir o formulário TabAdvanced «Advanced»: mexe em CheckContributi. |  |  |  |
| `TabAutoCalNewShow` | 0x004CB1C4 | 314 | INFERIDO | Ao exibir o formulário TabAutoCalNew «Auto calibrazione»: mexe em AutoCalPage. |  |  |  |
| `TabBenzinaShow` | 0x004BF004 | 262 | INFERIDO | Ao exibir o formulário TabBenzina «Gás/gasolina»: mexe em EditGiriMinimoPerBenzina, EditGiriPerBenzina, EditGiriSupBenzina, EditTempoBenzina1. |  |  |  |
| `TabCalibrationShow` | 0x004C7904 | 268 | INFERIDO | Ao exibir o formulário TabCalibration «Inj calibration»: mexe em ShapeInjA, ShapeInjB, ShapeInjC, ShapeInjD, ShapeInjE, ShapeInjF. |  |  |  |
| `TabCambioShow` | 0x004A6ACC | 2652 | INFERIDO | Ao exibir o formulário TabCambio «Mudança do gás»: usa textos LABELTAGLIAGRANDE, LABELTAGLIAMEDIA, LABELTAGLIAPICCOLA; chama TTranslatorExt::GetString; mexe em ComboCarburante, ComboFullGroup, ComboTaglia, ComboTipoIniettore, EditTipoIniettore, LabelTaglia. | LABELTAGLIAGRANDE, LABELTAGLIAMEDIA, LABELTAGLIAPICCOLA |  |  |
| `TabConsumometroShow` | 0x004C1044 | 178 | INFERIDO | Ao exibir o formulário TabConsumometro «Medidor consumo»: mexe em CheckConsumometro, ComboTagliaIniettori, EditLitriSerbatoio. |  |  |  |
| `TabDhlpAgaslevShow` | 0x004C8EC4 | 1 | OBS-COD | Ao exibir o formulário TabDhlpAgaslev «DHLP/AGASLEV». Corpo é só RET: nada acontece neste evento (OBS-COD). |  |  |  |
| `TabEmissioniShow` | 0x004A8964 | 722 | INFERIDO | Ao exibir o formulário TabEmissioni «Emissões»: mexe em EditCorrIntInf, EditCorrIntSup, EditLivEmulAlto, EditLivEmulBasso, EditLivEmulCutoff, EditLivEmulStabilizzato. |  |  |  |
| `TabEsciShow` | 0x004AE418 | 6 | INFERIDO | Ao exibir o formulário TabEsci «- Esc Sair»: corpo de 6 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `TabFrestShow` | 0x004C6324 | 52 | INFERIDO | Ao exibir o formulário TabFrest «Frest model»: mexe em EditlFrestGasNormTemp. |  |  |  |
| `TabIniezioneShow` | 0x004AB8BC | 2768 | INFERIDO | Ao exibir o formulário TabIniezione «Injectores»: usa textos LABELDURATAATTIVAZIONEINJ, LABELPICCORISCALDAMENTO; chama TTranslatorExt::GetString; mexe em CheckIncrementoMaxCorrente, CheckRiscaldamento, CheckboxPreheatAsyncMode, CheckboxPreheatOnlyAfterSleepMode, EditAperturaExtra, EditAperturaInj. | LABELDURATAATTIVAZIONEINJ, LABELPICCORISCALDAMENTO |  |  |
| `TabKCoefficientiShow` | 0x004B0EA0 | 550 | INFERIDO | Ao exibir o formulário TabKCoefficienti «Coefficienti k»: mexe em ButtonTaratura, EditAperturaInj, PageControlK, RadioMapAdatta, RadioMapAlcol, RadioMapPetrol. |  |  |  |
| `TabKFactorShow` | 0x004C49FC | 1464 | INFERIDO | Ao exibir o formulário TabKFactor «K factor»: mexe em ComboK, EditK1, EditK2, EditKMappaNeutro, EditTInj1, EditTInj2. |  |  |  |
| `TabKTempiShow` | 0x004B1254 | 150 | INFERIDO | Ao exibir o formulário TabKTempi «Tempos»: mexe em EditTempoBenzExtra, EditTempoGasExtra, EditTempoMortoBenz, EditTempoMortoGas. |  |  |  |
| `TabKVarieShow` | 0x004B94C8 | 239 | INFERIDO | Ao exibir o formulário TabKVarie «Várias»: mexe em EditFiltro, EditGiriAltiKFiltro, EditGiriKFiltro, EditKFiltro, EditKFiltroGiriAlti, EditSogliaFS2. |  |  |  |
| `TabLambdaShow` | 0x004A6770 | 653 | INFERIDO | Ao exibir o formulário TabLambda «Sonda Lambda»: mexe em CheckFlashLube, ComboNumeroSonde, ComboSonda1, ComboSonda2, ComboTipoLambda, EditRifInfCalda. |  |  |  |
| `TabPressioneShow` | 0x004AE788 | 2153 | INFERIDO | Ao exibir o formulário TabPressione «Pressão»: usa textos COMBOPRESSIONEMISURATA0, COMBOPRESSIONEMISURATA1, COMBOPRESSIONEMISURATA2, COMBOPRESSIONEMISURATA3, LABELTIPOPRESSIONEMIS; chama TTranslatorExt::GetString; mexe em CheckMapConfigurato, ComboMisuratorePressione, ComboPressioneMisurata, ComboTipoMisuratore, EditOffset, EditOffsetDiff. | COMBOPRESSIONEMISURATA0, COMBOPRESSIONEMISURATA1, COMBOPRESSIONEMISURATA2, COMBOPRESSIONEMISURATA3, LABELTIPOPRESSIONEMIS, LABELTIPOPRESSIONEMIS1 |  |  |
| `TabSensoreShow` | 0x004AA5BC | 3924 | INFERIDO | Ao exibir o formulário TabSensore «Livello GAS»: usa textos CHECKALIMENTAZIONEFILODEDICATO, CHECK_STAP_ON_EV2, COMBOSENSORE1, COMBOSENSORE2; chama TFormConfig.CheckRitornoBenzinaClick, TTranslatorExt::GetString; mexe em CheckAlimentazione, CheckAttivaPompaInRiserva, CheckBassaPressione, CheckBoxEV2NotConnected, CheckEnabledDhlpDiagnosis, CheckPassPRail. | CHECKALIMENTAZIONEFILODEDICATO, CHECK_STAP_ON_EV2, COMBOSENSORE1, COMBOSENSORE2 |  |  |
| `TabSwitchToPetrolShow` | 0x004C65A4 | 361 | INFERIDO | Ao exibir o formulário TabSwitchToPetrol «Pass. gasolina»: mexe em EditSwitchToPetrol0, EditSwitchToPetrol1, EditSwitchToPetrol2, EditSwitchToPetrol3, EditSwitchToPetrol4, EditSwitchToPetrol5. |  |  |  |
| `TabTaraturaShow` | 0x004A9CD0 | 242 | INFERIDO | Ao exibir o formulário TabTaratura «Miscelânea»: mexe em EditDeltaK, EditGiriAutotaratura, EditNumControlli, EditNumControlliOk, EditScartoMinAlMin, EditScartoMinFuoriMin. |  |  |  |
| `TabTempShow` | 0x004B92C8 | 129 | INFERIDO | Ao exibir o formulário TabTemp «Temperatura»: mexe em CheckFlashLubeSuTempAcqua, CheckTempAcqua, CheckTempGas, CheckTempMotore. |  |  |  |
| `TabTestShow` | 0x004BA440 | 114 | INFERIDO | Ao exibir o formulário TabTest «Dados de teste»: mexe em EditTestByte, EditTestTempo, EditTestWord. |  |  |  |
| `TimerDatiTimer` | 0x004A307C | 1586 | INFERIDO | A cada tick do temporizador TimerDati (intervalo 75 ms): chama TStreamDati.TimerIOTimer; mexe em LabApMot, LabApMot2, LabApMot2Benz, LabApMotBenz, LabContribBenz, LabDeltaGiri. |  |  |  |
| `chkRitardoPassaggioInMinutiClick` | 0x004CB300 | 39 | INFERIDO | Ao clicar em chkRitardoPassaggioInMinuti «Ritardo passaggio in minuti»: mexe em chkRitardoPassaggioInMinuti. |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonAccettaClick` (0x004A8D74):
  - sub_4306C0 (116 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+4 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonCommutatoreClick` (0x004B1178):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+22 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonDoLevelClick` (0x004C5C30):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+21 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonMaxLevelClick` (0x004C5ADC):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonMinLevelClick` (0x004C598C):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonModificaInjClick` (0x004BC47C):
  - sub_433B08 (144 B): chama TAebMatrix::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonModificaRifTarClick` (0x004B10C8):
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonNuoviParametriClick` (0x004B9000):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+21 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonRicercaParametriClick` (0x004AF3A8):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - (+5 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonSequenceAcquisitionClick` (0x004A62A8):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonSequenzaInjClick` (0x004BC2DC):
  - sub_434218 (261 B): chama TAebProtocol::GetComFail,TAebVector::GetEcuData
  - sub_434320 (285 B): chama TAebVector::GetEcuData
  -   sub_433B08 (144 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonSwitchSettingsClick` (0x004C90E0):
  - sub_412430 (1643 B): textos MESSERRORGIRISWITCHCALIBRATION,SWITCHSETTINGSBUTTONEXIT,SWITCHSETTINGSCAPTION,SWITCHSETTINGSLABELBUZZER; APIs Sleep; chama TAboutBox.Create,TAebProtocol::SendCommand,TTranslatorExt::GetString
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonTaraturaClick` (0x004ACFC0):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+22 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckAdattaBenzinaClick` (0x004C0E34):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+22 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckAdattativitaClick` (0x004C0D50):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+22 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckAlimentazioneClick` (0x004C6780):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  - sub_431878 (110 B): chama TAebVector::GetEcuData
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  - (+38 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckAntiStalloClick` (0x004C982C):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+5 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckAttivaPompaInRiservaClick` (0x004CB030):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckAzzeraBlackListObdClick` (0x004C1ADC):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckAzzeraErroriObdClick` (0x004C1AAC):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckBoxEV2NotConnectedClick` (0x004C6710):
  - sub_431878 (110 B): chama TAebVector::GetEcuData
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckBoxEnDiagDtcPowertrainClick` (0x004CB358):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckBoxEnableExtLedKLineClick` (0x004CB328):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckCambioConSovrappClick` (0x004C8B04):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckConsumometroClick` (0x004C1108):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+21 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckContributiClick` (0x004C9C1C):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckDeleteErrorMotoreSpentoClick` (0x004C1BA4):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckDisabilitaCommutatoreClick` (0x004C1C04):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckElettrovalvoleClick` (0x004C5048):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+42 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckEnabledDhlpDiagnosisClick` (0x004C975C):
  - sub_431878 (110 B): chama TAebVector::GetEcuData
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckFlashLubeClick` (0x004C88AC):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckFlexFuelClick` (0x004C2C9C):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckGiriAlberoCammeClick` (0x004C850C):
  - sub_42B834 (160 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_438204 (205 B): APIs GetTickCount,Sleep; chama TAebProtocol::Connect,TAebProtocol::DisableErrorHandling,TAebProtocol::Disconnect,TAebProtocol::EnableErrorHandling
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4382D4 (119 B): APIs GetTickCount,Sleep; chama TAebNumber::GetEcuData
  - (+21 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckGiriRuotaFonicaClick` (0x004C86D4):
  - sub_42B834 (160 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_438204 (205 B): APIs GetTickCount,Sleep; chama TAebProtocol::Connect,TAebProtocol::DisableErrorHandling,TAebProtocol::Disconnect,TAebProtocol::EnableErrorHandling
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4382D4 (119 B): APIs GetTickCount,Sleep; chama TAebNumber::GetEcuData
  - (+21 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckIncrementoMaxCorrenteClick` (0x004C721C):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckInibisciEmulazioneClick` (0x004B9280):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+22 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckIniezioneContinuaClick` (0x004BC858):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+20 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckInjAcquisitionClick` (0x004A61AC):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckIstantaneoClick` (0x004BFA68):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+21 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckMapConfiguratoClick` (0x004BCB40):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+27 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckObdClick` (0x004CAE4C):
  - sub_43457C (274 B): chama TAebProtocol::Connect,TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckOutDiagInfoSuKLineClick` (0x004C1B74):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckPartenzaGasCaldoClick` (0x004C4FB8):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckParzializzaClick` (0x004BFAA0):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+21 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckPassPRailClick` (0x004BCB10):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+21 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckPetrolObdConnectionClick` (0x004C1B0C):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckProgressioniClick` (0x004CA544):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+19 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckRiconoscimentoPienoClick` (0x004CB12C):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckRiscaldamentoClick` (0x004C6A64):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+12 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckRitornoBenzinaClick` (0x004A9484):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+21 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckSegnaleGiriClick` (0x004C5494):
  - sub_42B834 (160 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  - sub_4310F8 (103 B): chama TAebNumber::SetEcuData
  -   sub_438204 (205 B): APIs GetTickCount,Sleep; chama TAebProtocol::Connect,TAebProtocol::DisableErrorHandling,TAebProtocol::Disconnect,TAebProtocol::EnableErrorHandling
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4382D4 (119 B): APIs GetTickCount,Sleep; chama TAebNumber::GetEcuData
  - (+18 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckSequenzaInjClick` (0x004BC008):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  - (+29 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckSpegniBuzzerClick` (0x004C1C34):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckSplitFuelClick` (0x004C1C64):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  - sub_432A74 (100 B): chama TAebVector::SetEcuData
  - sub_432B1C (184 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+8 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckStartAndStopClick` (0x004C2BD8):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckTagliaExtraIniettateClick` (0x004C1BD4):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckTaglioPompaBenzinaClick` (0x004C8B68):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  - sub_431878 (110 B): chama TAebVector::GetEcuData
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  - (+43 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckTempAcquaClick` (0x004B8B30):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  - sub_431878 (110 B): chama TAebVector::GetEcuData
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  - (+26 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckTempMotoreClick` (0x004B8E88):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+22 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckTempRiduttoreClick` (0x004B8E10):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+23 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckUsaCanPerObdClick` (0x004C1B3C):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckValvetronikClick` (0x004C2B14):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckboxPreheatAsyncModeClick` (0x004C72D0):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  - sub_432CBC (84 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckboxPreheatOnlyAfterSleepModeClick` (0x004C724C):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  - sub_432CBC (84 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboAccensioneKeyPress` (0x004BA230):
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+5 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboCambioKeyPress` (0x004A8FB8):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+36 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboCarburanteKeyPress` (0x004B96DC):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  - sub_439E94 (288664 B): APIs Sleep,SendMessageW; chama TAebMatrix::GetEcuData,TAebMatrix::SetColumnDimension,TAebMatrix::SetEcuData,TAebNumber::GetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_42C1E8 (453 B): APIs DeleteFileW,GetTempFileNameW,GetTempPathW
  - (+51 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboCilindriKeyPress` (0x004BA2EC):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_433B08 (144 B): chama TAebMatrix::SetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+24 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboKKeyPress` (0x004C4880):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboNumeroSondeKeyPress` (0x004A9664):
  - sub_433938 (128 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+15 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboPressioneKeyPress` (0x004AE420):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  - sub_4310F8 (103 B): chama TAebNumber::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+29 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboSegnaleGiriKeyPress` (0x004ACDC8):
  - sub_43109C (73 B): chama TAebNumber::SetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+15 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboSensoreKeyPress` (0x004A8C60):
  - sub_4304F0 (411 B): chama TAebNumber::SetEcuData
  -   sub_4306C0 (116 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+11 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboSfasaturaKeyPress` (0x004BD748):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  - sub_433B08 (144 B): chama TAebMatrix::SetEcuData
  - sub_434218 (261 B): chama TAebProtocol::GetComFail,TAebVector::GetEcuData
  - sub_434320 (285 B): chama TAebVector::GetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  - (+32 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboSonda1KeyPress` (0x004C2D94):
  - sub_433A4C (160 B): chama TAebNumber::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+16 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboTagliaIniettoriKeyPress` (0x004C1138):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboTagliaKeyPress` (0x004BCD94):
  - sub_42B834 (160 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - sub_43363C (110 B): chama TAebNumber::SetEcuData
  - sub_439358 (2592 B): chama TAebMatrix::SetDefValue,TAebVector::SetDefValue
  -   sub_438204 (205 B): APIs GetTickCount,Sleep; chama TAebProtocol::Connect,TAebProtocol::DisableErrorHandling,TAebProtocol::Disconnect,TAebProtocol::EnableErrorHandling
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_4382D4 (119 B): APIs GetTickCount,Sleep; chama TAebNumber::GetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboTempRiduttoreKeyPress` (0x004B9494):
  - sub_430878 (403 B): chama TAebNumber::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -     sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  - (+13 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboTipoIniettoreKeyPress` (0x004BA4B4):
  - sub_42B834 (160 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - sub_42FFD8 (84 B): chama TAebVector::SetEcuData
  - sub_439358 (2592 B): chama TAebMatrix::SetDefValue,TAebVector::SetDefValue
  - sub_439E94 (288664 B): APIs Sleep,SendMessageW; chama TAebMatrix::GetEcuData,TAebMatrix::SetColumnDimension,TAebMatrix::SetEcuData,TAebNumber::GetEcuData
  -   sub_438204 (205 B): APIs GetTickCount,Sleep; chama TAebProtocol::Connect,TAebProtocol::DisableErrorHandling,TAebProtocol::Disconnect,TAebProtocol::EnableErrorHandling
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_42C1E8 (453 B): APIs DeleteFileW,GetTempFileNameW,GetTempPathW
  -   sub_42C4B4 (564 B): APIs DeleteFileW
  -   sub_42C708 (647 B): APIs DeleteFileW,FindResourceW,GetTempFileNameW,GetTempPathW
  -   sub_42C990 (212 B): APIs DeleteFileW
  -   sub_43834C (4108 B): APIs FindResourceW,LoadResource,LockResource,SizeofResource
  -   sub_439DDC (184 B): APIs SendMessageW
  - (+48 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboTipoLambdaKeyPress` (0x004A6674):
  -   sub_42F83C (275 B): chama TAebVector::SetEcuData
  -   sub_42FA10 (307 B): chama TAebMatrix::SetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboTipoMisuratoreKeyPress` (0x004BEC34):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  - sub_43048C (91 B): chama TAebVector::SetEcuData
  - sub_430878 (403 B): chama TAebNumber::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  - (+23 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboTipoObdKeyPress` (0x004C0F18):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboTurboKeyPress` (0x004B9C44):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  - sub_439E94 (288664 B): APIs Sleep,SendMessageW; chama TAebMatrix::GetEcuData,TAebMatrix::SetColumnDimension,TAebMatrix::SetEcuData,TAebNumber::GetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_42C1E8 (453 B): APIs DeleteFileW,GetTempFileNameW,GetTempPathW
  - (+51 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawDelayGasTempDblClick` (0x004C42E4):
  -   sub_431F6C (181 B): chama TAebMatrix::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+12 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawDelayGasTempDrawCell` (0x004C3EA4):
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawDelayGasTempKeyPress` (0x004C42D4):
  -   sub_431F6C (181 B): chama TAebMatrix::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+12 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridAnticipoDblClick` (0x004BD2E4):
  -   sub_431D58 (178 B): chama TAebVector::SetEcuData
  -   sub_431E34 (97 B): chama TAebVector::SetEcuData
  -   sub_431EB4 (128 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridAnticipoDrawCell` (0x004BCF1C):
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridAnticipoKeyPress` (0x004BD348):
  -   sub_431D58 (178 B): chama TAebVector::SetEcuData
  -   sub_431E34 (97 B): chama TAebVector::SetEcuData
  -   sub_431EB4 (128 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridAnticipoMultipointDblClick` (0x004C3C38):
  -   sub_42FC5C (97 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridAnticipoMultipointDrawCell` (0x004C3944):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridAnticipoMultipointKeyPress` (0x004C3C28):
  -   sub_42FC5C (97 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridBenzinaDblClick` (0x004C157C):
  -   sub_432F44 (184 B): chama TAebVector::SetEcuData
  -   sub_43301C (107 B): chama TAebVector::SetEcuData
  -   sub_43310C (271 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+11 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridBenzinaDrawCell` (0x004C11B8):
  - (+4 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridBenzinaKeyPress` (0x004C15E0):
  -   sub_432F44 (184 B): chama TAebVector::SetEcuData
  -   sub_43301C (107 B): chama TAebVector::SetEcuData
  -   sub_43310C (271 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+11 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridContributiDblClick` (0x004CA288):
  -   sub_42F254 (217 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridContributiDrawCell` (0x004C9DF0):
  - (+8 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridContributiKeyPress` (0x004CA278):
  -   sub_42F254 (217 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridEmissioniDblClick` (0x004B4530):
  - sub_4B4594 (1050 B): chama TFormCentriCelle.Create
  -   sub_42F83C (275 B): chama TAebVector::SetEcuData
  -   sub_42FC5C (97 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+11 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridEmissioniDrawCell` (0x004B3F68):
  - (+5 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridEmissioniKeyPress` (0x004B4520):
  - sub_4B4594 (1050 B): chama TFormCentriCelle.Create
  -   sub_42F83C (275 B): chama TAebVector::SetEcuData
  -   sub_42FC5C (97 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+11 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridEmissioniMultipointDblClick` (0x004C33E0):
  -   sub_42F83C (275 B): chama TAebVector::SetEcuData
  -   sub_42FA10 (307 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+12 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridEmissioniMultipointDrawCell` (0x004C2E3C):
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridEmissioniMultipointKeyPress` (0x004C33D0):
  -   sub_42F83C (275 B): chama TAebVector::SetEcuData
  -   sub_42FA10 (307 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridKDblClick` (0x004B1940):
  -   sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -   sub_42F6C8 (143 B): chama TAebMatrix::SetEcuData
  -   sub_42F174 (181 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431B7C (186 B): chama TAebVector::SetEcuData
  -     sub_431C9C (105 B): chama TAebVector::SetEcuData
  -     sub_43204C (129 B): chama TAebVector::SetEcuData
  -     sub_432464 (178 B): chama TAebVector::SetEcuData
  -     sub_43255C (178 B): chama TAebVector::SetEcuData
  -     sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -     sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - (+28 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridKDrawCell` (0x004B12EC):
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridKKeyPress` (0x004B18F4):
  -   sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -   sub_42F6C8 (143 B): chama TAebMatrix::SetEcuData
  -   sub_42F174 (181 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431B7C (186 B): chama TAebVector::SetEcuData
  -     sub_431C9C (105 B): chama TAebVector::SetEcuData
  -     sub_43204C (129 B): chama TAebVector::SetEcuData
  -     sub_432464 (178 B): chama TAebVector::SetEcuData
  -     sub_43255C (178 B): chama TAebVector::SetEcuData
  -     sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -     sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - (+28 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridOpenLoopDblClick` (0x004B29E0):
  -   sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -   sub_42EFF4 (282 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431B7C (186 B): chama TAebVector::SetEcuData
  -     sub_431C9C (105 B): chama TAebVector::SetEcuData
  -     sub_43204C (129 B): chama TAebVector::SetEcuData
  -     sub_432464 (178 B): chama TAebVector::SetEcuData
  -     sub_43255C (178 B): chama TAebVector::SetEcuData
  -     sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -     sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -     sub_43734C (2164 B): chama TAebVector::SetEcuData
  - (+24 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridOpenLoopDrawCell` (0x004B25B4):
  - (+7 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridOpenLoopKeyPress` (0x004B29D0):
  -   sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -   sub_42EFF4 (282 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431B7C (186 B): chama TAebVector::SetEcuData
  -     sub_431C9C (105 B): chama TAebVector::SetEcuData
  -     sub_43204C (129 B): chama TAebVector::SetEcuData
  -     sub_432464 (178 B): chama TAebVector::SetEcuData
  -     sub_43255C (178 B): chama TAebVector::SetEcuData
  -     sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -     sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -     sub_43734C (2164 B): chama TAebVector::SetEcuData
  - (+24 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridPassaggioGasDblClick` (0x004B3A24):
  -   sub_432E9C (97 B): chama TAebVector::SetEcuData
  -   sub_4335A4 (133 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridPassaggioGasDrawCell` (0x004B3660):
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridPassaggioGasKeyPress` (0x004B3A14):
  -   sub_432E9C (97 B): chama TAebVector::SetEcuData
  -   sub_4335A4 (133 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridPassaggioKeyPress` (0x004B3118):
  -   sub_432E08 (129 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridPressAssDblClick` (0x004B6178):
  -   sub_432868 (208 B): chama TAebVector::SetEcuData
  -   sub_432990 (200 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+7 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridPressAssDrawCell` (0x004B5E90):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridPressAssKeyPress` (0x004B6168):
  -   sub_432868 (208 B): chama TAebVector::SetEcuData
  -   sub_432990 (200 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+7 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridPressDiffDblClick` (0x004B5B68):
  -   sub_432670 (231 B): chama TAebVector::SetEcuData
  -   sub_4327C0 (114 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+7 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridPressDiffDrawCell` (0x004B5880):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridPressDiffKeyPress` (0x004B5B58):
  -   sub_432670 (231 B): chama TAebVector::SetEcuData
  -   sub_4327C0 (114 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+7 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridPressPassDblClick` (0x004B6EF4):
  -   sub_432114 (168 B): chama TAebVector::SetEcuData
  -   sub_432218 (178 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridPressPassDrawCell` (0x004B6B1C):
  - (+5 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridPressPassKeyPress` (0x004B6F58):
  -   sub_432114 (168 B): chama TAebVector::SetEcuData
  -   sub_432218 (178 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridProgressioniDblClick` (0x004CA9A0):
  - sub_4CA9F8 (1082 B): chama TAebMatrix::GetInt,TAebMatrix::SetInt
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridProgressioniDrawCell` (0x004CA74C):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridProgressioniKeyPress` (0x004CA990):
  - sub_4CA9F8 (1082 B): chama TAebMatrix::GetInt,TAebMatrix::SetInt
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridSequenzaInjBenzDrawCell` (0x004BBB88):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridSequenzaInjDrawCell` (0x004BB664):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+7 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridSequenzaInjSelectCell` (0x004BBAB0):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+5 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridSplitFuelDblClick` (0x004C2788):
  -   sub_432A74 (100 B): chama TAebVector::SetEcuData
  -   sub_432B1C (184 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridSplitFuelDrawCell` (0x004C2420):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridSplitFuelKeyPress` (0x004C27EC):
  -   sub_432A74 (100 B): chama TAebVector::SetEcuData
  -   sub_432B1C (184 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTInjDefaultDblClick` (0x004B6868):
  -   sub_4300B8 (218 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTInjDefaultDrawCell` (0x004B64A0):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTInjDefaultKeyPress` (0x004B68CC):
  -   sub_4300B8 (218 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTempDblClick` (0x004B81F4):
  -   sub_43041C (84 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTempDrawCell` (0x004B7874):
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTempKeyPress` (0x004B80B0):
  - sub_43041C (84 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTempMotDblClick` (0x004B54F8):
  -   sub_432394 (138 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTempMotDrawCell` (0x004B5134):
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTempMotKeyPress` (0x004B54E8):
  -   sub_432394 (138 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTempRidDblClick` (0x004B4DAC):
  -   sub_4322F8 (138 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTempRidDrawCell` (0x004B49E8):
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTempRidKeyPress` (0x004B4D9C):
  -   sub_4322F8 (138 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTempiLrDblClick` (0x004BE5A8):
  -   sub_4336DC (146 B): chama TAebVector::SetEcuData
  -   sub_4337D8 (288 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTempiLrDrawCell` (0x004BE278):
  - (+4 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTempiLrKeyPress` (0x004BE598):
  -   sub_4336DC (146 B): chama TAebVector::SetEcuData
  -   sub_4337D8 (288 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTestDblClick` (0x004B75EC):
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTestDrawCell` (0x004B71B8):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridTestKeyPress` (0x004B7650):
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditAdvParamInjGasKeyPress` (0x004C7150):
  - sub_432BF4 (84 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+16 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditAntiStalloKeyPress` (0x004C97D8):
  - (+11 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditAperturaInjKeyPress` (0x004ACF68):
  - sub_4312A8 (593 B): chama TAebNumber::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+11 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditBassaPressioneMpKeyPress` (0x004C526C):
  - sub_432670 (231 B): chama TAebVector::SetEcuData
  - sub_4327C0 (114 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+13 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditCorrBancata2KeyPress` (0x004C2CCC):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+21 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditCorrCarbMinimoKeyPress` (0x0049F0E4):
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -   sub_42F364 (135 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+18 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditCorrIntInfKeyPress` (0x004B91BC):
  - sub_42FCD0 (84 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+13 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditCutoffKeyPress` (0x004C82B8):
  - sub_42FDBC (165 B): chama TAebVector::SetEcuData
  - sub_432D84 (116 B): chama TAebVector::SetEcuData
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  - (+25 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditDeltaTInterruzioneWarmupKeyPress` (0x004C8ECC):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+16 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditFiltroEnter` (0x004BDFD4):
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditFiltroKeyPress` (0x004B9644):
  - sub_431524 (84 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+14 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditFlashLubeKeyPress` (0x004C8948):
  - sub_432D20 (84 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+16 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditFrestEnter` (0x004C611C):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditGiriCambioEnter` (0x004A98D8):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditGiriKFiltroEnter` (0x004BE90C):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditGiriKFiltroKeyPress` (0x004BEB24):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+20 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditGiriMinimoPerBenzinaKeyPress` (0x004BFE6C):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+18 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditGiriPerBenzinaKeyPress` (0x004C04E8):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+18 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditKFiltroKeyPress` (0x004B95C0):
  - sub_431524 (84 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+14 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditKMappaNeutroKeyPress` (0x004C499C):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+20 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditOffsetKeyPress` (0x004AF360):
  - sub_43048C (91 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+14 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditPreheatSyncInjNumKeyPress` (0x004C78A4):
  - sub_432CBC (84 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+13 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditRPullUpEnter` (0x004B86C4):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditRPullUpExit` (0x004B88C0):
  - sub_4303B8 (84 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+14 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditRifLambdaKeyPress` (0x004A6A00):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+20 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditRiscaldamentoKeyPress` (0x004C7478):
  - sub_432BF4 (84 B): chama TAebVector::SetEcuData
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+22 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditRpmTachimetroKeyPress` (0x004A647C):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditScartoMinFuoriMinKeyPress` (0x004BA1E8):
  - sub_42FEF4 (185 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+14 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditSensAdattaKeyPress` (0x004C07D0):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+18 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditSogliaGiriKeyPress` (0x004C58DC):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+20 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditTInjSup22KeyPress` (0x004C3E44):
  - sub_4302A0 (233 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+14 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditTempAcquaKeyPress` (0x004BCB78):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+20 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditTempMotoreKeyPress` (0x004C8F84):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+20 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditTempoBenzinaKeyPress` (0x004C0570):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+17 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditTempoMortoBenzKeyPress` (0x004B11B0):
  - sub_4312A8 (593 B): chama TAebNumber::SetEcuData
  - sub_4337D8 (288 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+11 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditTipoIniettoreKeyPress` (0x004BACE0):
  - sub_42FFD8 (84 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+14 subfunções sem fatos diretos ou fora da lista; telas.json)
- `FormCloseQuery` (0x004ADE94):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `FormKeyDown` (0x0049EB24):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `FormResize` (0x00490CB8):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `FormShow` (0x00490028):
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+16 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ImageInjOffClick` (0x004C81B0):
  - sub_4C7A10 (344 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ImageInjOnClick` (0x004C80A8):
  - sub_4C7A10 (344 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `LabGasClick` (0x004C9A7C):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+22 subfunções sem fatos diretos ou fora da lista; telas.json)
- `LabelDiagnosiClick` (0x004CB060):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `MenuComunicazioneSerialeCostruitaClick` (0x004AE008):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `MenuControlloIniettoriClick` (0x004BA408):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+21 subfunções sem fatos diretos ou fora da lista; telas.json)
- `MenuCopiaTaraturaClick` (0x004AD0B0):
  - (+7 subfunções sem fatos diretos ou fora da lista; telas.json)
- `MenuIncollaTaraturaClick` (0x004AD4B8):
  - sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  - sub_42F174 (181 B): chama TAebMatrix::SetEcuData
  - sub_42F6C8 (143 B): chama TAebMatrix::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+4 subfunções sem fatos diretos ou fora da lista; telas.json)
- `PageControlChanging` (0x004ADD20):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `PageControlDrawTab` (0x0049E83C):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `RadioAdatta25Click` (0x004C05F0):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+7 subfunções sem fatos diretos ou fora da lista; telas.json)
- `RadioAdattaDirittiClick` (0x004C06EC):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+7 subfunções sem fatos diretos ou fora da lista; telas.json)
- `RadioGasAltiGiriClick` (0x004BFEF4):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+31 subfunções sem fatos diretos ou fora da lista; telas.json)
- `RadioGasClick` (0x004BFAD8):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+37 subfunções sem fatos diretos ou fora da lista; telas.json)
- `RadioMapClick` (0x004B23A0):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabAdattaShow` (0x004C0854):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+25 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabAdvancedShow` (0x004C9BBC):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabAutoCalNewShow` (0x004CB1C4):
  - (+4 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabBenzinaShow` (0x004BF004):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+15 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabCalibrationShow` (0x004C7904):
  - sub_4C7A10 (344 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+7 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabCambioShow` (0x004A6ACC):
  - (+16 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabConsumometroShow` (0x004C1044):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+16 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabEmissioniShow` (0x004A8964):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+18 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabFrestShow` (0x004C6324):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabIniezioneShow` (0x004AB8BC):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - (+27 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabKCoefficientiShow` (0x004B0EA0):
  - (+15 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabKFactorShow` (0x004C49FC):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+21 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabKTempiShow` (0x004B1254):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - (+13 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabKVarieShow` (0x004B94C8):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+17 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabLambdaShow` (0x004A6770):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+22 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabPressioneShow` (0x004AE788):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+23 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabSensoreShow` (0x004AA5BC):
  - sub_431878 (110 B): chama TAebVector::GetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+39 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabSwitchToPetrolShow` (0x004C65A4):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - (+12 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabTaraturaShow` (0x004A9CD0):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+17 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabTempShow` (0x004B92C8):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabTestShow` (0x004BA440):
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+16 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TimerDatiTimer` (0x004A307C):
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `chkRitardoPassaggioInMinutiClick` (0x004CB300):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
