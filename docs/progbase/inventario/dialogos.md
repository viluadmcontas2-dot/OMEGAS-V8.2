# Caixas de diálogo, telas modais e mensagens

Gerado por `tools/progbase/inventario_telas.py`. Componentes de diálogo e botões com ModalResult: OBS-COD (DFM). Mensagens: textos de LANG_MAIN cujo índice é passado a `TTranslatorExt::GetString` dentro de funções de cada unidade (OBS-COD); o texto só é "mensagem ao usuário" por INFERÊNCIA do prefixo MESS*/FINESTRA*.

## Diálogos de arquivo/impressão/cor

| Tela | Componente | Classe | Filtro | Ext. padrão |
|---|---|---|---|---|
| TAboutBox | OpenDialog | TOpenDialog | License file/*.dat | dat |
| TAebBeeFormLight | OpenDialog | TOpenDialog |  | *.* |
| TAutocalColorSettings | MyColorDialog | TColorDialog |  |  |
| TAutoCalUI | SaveDialogChart | TSaveDialog | Bitmap image/*.bmp/Windows metafile/*.wmf |  |
| TFormGraficoDrt | PrintDialog1 | TPrintDialog |  |  |
| TFormGraficoScope | PrintDialog | TPrintDialog |  |  |
| TFormGraficoScope | OpenDialogDrt | TOpenDialog | Grafico dati in tempo reale/*.txt | txt |
| TFormGraficoScope | SaveDialogDrt | TSaveDialog | Acquisizioni in tempo reale/*.txt | txt |
| TFormGraficoScope | EsportDialogDrt | TSaveDialog | File bitmap /*.bmp | hcf |
| TFormMain | SaveDialog | TSaveDialog |  |  |
| TFormMain | OpenDialog | TOpenDialog |  |  |
| TFormMain | PrintDialog | TPrintDialog |  |  |
| TFormScarica | OpenDialogFile | TOpenDialog |  |  |
| TFormVisualizza | OpenDialogDrt | TOpenDialog | Grafico dati in tempo reale/*.txt | txt |
| TFormVisualizza | SaveDialogDrt | TSaveDialog | Acquisizioni in tempo reale/*.txt | txt |

## Botões com ModalResult (telas modais)

| Tela | Componente | Legenda | ModalResult |
|---|---|---|---|
| TAutocalColorSettings | Cancel | Cancel | 2 |
| TFormAggKey | ButtonUpgrade | Ricarica ora | 1 |
| TFormAggKey | ButtonLater | Continua a lavorare | 2 |
| TFormDiagnosi | ButtonEsci | &Esci | 1 |
| TFormFile | ButtonAnnulla | &Annulla | 2 |
| TFormGestioneTracce | ButtonOk | &OK | 1 |
| TFormGestioneTracce | ButtonAnnulla | &Annulla | 2 |
| TFormHelpScope | ButtonEsci | Esci | 2 |
| TFormHelpScope | LogoLegenda |  | 1 |
| TFormModificaMappa | ButtonOK | &OK | 1 |
| TFormModificaMappa | ButtonAnnulla | &Annulla | 2 |
| TFormModMappa | ButtonOK | &OK | 1 |
| TFormModMappa | ButtonAnnulla | &Annulla | 2 |
| TFormRifAutocal | ButtonAnnulla | &Annulla | 2 |
| TFormRiferimenti | ButtonOk | &OK | 1 |
| TFormRiferimenti | ButtonAnnulla | &Annulla | 2 |
| TFormScarica | ButtonRiprogr | &Programma | 1 |
| TFormScarica | ButtonAnnulla | &Annulla | 2 |
| TFormVisualizza | ButtonEsci | &Esci | 2 |

## Mensagens e títulos de janela por unidade (chaves MESS*/FINESTRA*)

### Aggkey

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `MESSBUTTLATER` | Continua a trabalhar | Continue to work | 1 |
| `MESSBUTTOK` | Recarregar agora | Reload now | 1 |
| `MESSCHARGEKEY` | lhe permitirá recarregar a sua chave hardware por um ulterior período de tempo. | will allow you to reload your hardware key for a further period of time. | 1 |
| `MESSCONTACT` | Contactar o mais rapidamente possível o seu revendedor de software, comunicando-lhe o seguinte código: | Apply to your software dealer as soon as possible, by giving him this code: | 1 |
| `MESSEXPIRED` | A validade da chave hardware venceu e não é mais possível usar este programa. | The validity of the hardware key has expired and it is no more possible to use it with this program. | 1 |
| `MESSUPDATECODE` | Você receberá imediatamente um novo código a ser inserido no espaço abaixo que | You will immediately receive a new code to be inserted in the empty space below that | 1 |

### Autocal_ui

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `MESSAUTOCALCONFIRMRESET` |  | Do you confirm selected operation? | 1 |
| `MESSAUTOCALDELETEPOINTS` |  | Delete selected points? | 1 |
| `MESSAUTOCALERRORSENDCOMMAND` |  | Send command error. \nVerify connection and rety | 1 |
| `MESSECUDISCONNECTED` | A central está desconectada porque a conexão não está correta. \nImpossível conectar-se. | Ecu is disconnected since it doesn't reply properly. \nConnection is not available. | 1 |
| `MESSEXPORTKTOMAP` |  | Export on k-map? | 1 |

### Calibra

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `MESSAGGIORNATAR` | Atualização dos dados da central de comandos em andamento... | Updating ECU data... | 1 |
| `MESSATTESARISCALDAMENTO` | Aguarde o aquecimento | Wait warming | 1 |
| `MESSATTESARISCALDAMENTOSEC` | seg. | sec. | 1 |
| `MESSAUTOCALIBRATAR` | Calibração automática em andamento... | Autocalibration in progress... | 1 |
| `MESSECUDISCONNECTED` | A central está desconectada porque a conexão não está correta. \nImpossível conectar-se. | Ecu is disconnected since it doesn't reply properly. \nConnection is not available. | 1 |
| `MESSERRORESTATOTAR` | Erro no pedido do estado da calibração. | Calibration status request failure. | 1 |
| `MESSERRORETAR` | Impossível iniciar a calibração. Tente novamente. | Impossible to start calibration. Try again. | 1 |
| `MESSIMPOSSIBILETAR` | Impossível continuar. | Impossible to continue. | 1 |
| `MESSINIETTORITAR` | Injetores conectados incompatíveis | Installed injectors incompatibility | 1 |
| `MESSINIZIALIZZATAR` | Iniciação em andamento... | Initialization in progress... | 1 |
| `MESSMONOFUELTAR` | Impossível prosseguir, central monofuel | Cannot continue, monofuel control unit | 1 |
| `MESSND` | N.D. | n.a. | 1 |
| `MESSSEGNALEGIRITAR` | Falta sinal dos rotações | No rpm signal. | 1 |
| `MESSSENSOREMAP` | Verifique o sensor map e tente de novo | Check the map sensor and try again | 1 |
| `MESSSOLOOTTIMIZZAZIONE` | Deseja somente optimizar o file carregado nesta central? | Do you want only optimize the configuration file of this ECU ? | 1 |
| `MESSSOTTOCHIAVETAR` | Falta sub-chave | No key-on | 1 |
| `MESSSTABILIZZARSI0TAR` | Estabilizar-se em | Stabilize at | 1 |
| `MESSSTABILIZZARSI1TAR` | rotações no ponto morto... | rpm in idle position... | 1 |
| `MESSSTABILIZZARSI2TAR` | Levar ao mínimo... | Allow engine to idle... | 1 |
| `MESSSTABILIZZARSI3TAR` | Levar fora do mínimo na rua mantendo o pedal do acelerador constante com carga média... | Drive car on road and keep accelerator constantly pressed at medium load... | 1 |
| `MESSTEMPACQUATAR` | Temperatura da água muito baixa. | Water temperature too low. | 1 |
| `MESSTEMPGASTAR` | Temperatura do gás muito baixa. | Gas temperature too low. | 1 |
| `MESSTERMINATATAR` | Calibração automática terminada corretamente. \nYou now need to check the calibration on the road and at full  | Autocalibration completed correctly. \nYou now need to check the calibration on the road and at full load. | 1 |
| `MESSVERIFICATAR` | Verificação da calibração automática em andamento... | Verifying autocalibration... | 1 |
| `MESS_DELETE_LICENSE` | Delete current license file? | Delete current license file? | 1 |
| `MESS_ERROR_DELETE_LICENSE` | Error in deleting license file. \nVerify and retry. | Error in deleting license file. \nVerify and retry. | 1 |
| `MESS_ERROR_INSTALL_CODE` | Unlock code not valid. \nVerify and retry. | Unlock code not valid. \nVerify and retry. | 1 |
| `MESS_ERROR_INSTALL_LICENSE` | Error in installing license file. \nVerify and retry. | Error in installing license file. \nVerify and retry. | 1 |
| `MESS_ERROR_LINK_BROWSER` | Error in opening internet link for registration. \nVerify your default web browser. | Error in opening internet link for registration. \nVerify your default web browser. | 1 |
| `MESS_OK_DELETE_LICENSE` | License file successfully deleted. | License file successfully deleted. | 1 |
| `MESS_OK_INSTALL_LICENSE` | Close and restart the program \nfor using new license. | Close and restart the program \nfor using new license. | 1 |
| `MESS_OVER_WRITE_LICENSE` | Overwrite old license file? | Overwrite old license file? | 1 |

### Calibraswitch

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `MESSERRORGIRISWITCHCALIBRATION` | Impossible to set the switch parameters, \nrun the car and try again. | Impossible to set the switch parameters, \nrun the car and try again. | 1 |

### Centricelle

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `FINESTRACENTRICELLE` | Centros das células no mapa de carburação | Centres of carburation map cells | 1 |
| `MESSBADRIFGIRI` | Atenção: as referências das rotações não estão correctas | Warning: incorrect REVS. refs.! | 1 |
| `MESSBADRIFMAP` | Atenção: o sinal MAP não é correcto! | Warning: MAP refs. incorrect! | 1 |

### Centricelleauto

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `FINESTRACENTRICELLEAUTOCAL` |  | References for autocalibration | 1 |
| `MESSBADRIFMAP` | Atenção: o sinal MAP não é correcto! | Warning: MAP refs. incorrect! | 1 |
| `MESSBADRIFTEMPI` | Atenção: as referências dos tempos injeção não estão corretas! | Caution: The injection time references are not correct! | 1 |

### Centricellek

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `FINESTRACENTRICELLEK` | Centros das células no mapa dos coeficientes k | Map cell centres of the k coefficients | 1 |
| `MESSBADRIFGIRI` | Atenção: as referências das rotações não estão correctas | Warning: incorrect REVS. refs.! | 1 |
| `MESSBADRIFTEMPI` | Atenção: as referências dos tempos injeção não estão corretas! | Caution: The injection time references are not correct! | 1 |

### Commonmodules

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `MESSCONTACT` | Contactar o mais rapidamente possível o seu revendedor de software, comunicando-lhe o seguinte código: | Apply to your software dealer as soon as possible, by giving him this code: | 2 |
| `MESSDAYS` | dias e, depois, não será mais possível usar este programa. | days and afterwards it will be no more possible to use it with this program. | 2 |
| `MESSEXPIRED` | A validade da chave hardware venceu e não é mais possível usar este programa. | The validity of the hardware key has expired and it is no more possible to use it with this program. | 2 |
| `MESSWILLEXPIRE` | A chave hardware vencerá daqui a | The hardware key will expire within | 2 |

### Config

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `FINESTRAMODMAPPA` | Modifique o valor do mapa | Modify map value | 1 |
| `FINESTRASCARICADATI` | Descarregando os dados na central de comando | Downloading data to ECU | 1 |
| `MESSACQUISISCISEQUENZAINIEZIONE` | Sequence acquisition | Sequence acquisition | 1 |
| `MESSACQUISIZIONEECUNONCONNESSA` | Impossible to start acquisition, ecu not connected. | Impossible to start acquisition, ecu not connected. | 1 |
| `MESSACQUISIZIONEERROREAVVIO` | Error in starting acquisition procedure. | Error in starting acquisition procedure. | 1 |
| `MESSACQUISIZIONEERROREGENERICO` | Error in injection sequence acquisition. | Error in injection sequence acquisition. | 1 |
| `MESSACQUISIZIONEERROREGIRI` | Rpm not valid for acquisition procedure. \nMove to idle and try again. | Rpm not valid for acquisition procedure. \nMove to idle and try again. | 1 |
| `MESSACQUISIZIONEERRORETEMP` | Water temperature under 60° C. \nLeave the engine running and try again later. | Water temperature under 60° C. \nLeave the engine running and try again later. | 1 |
| `MESSACQUISIZIONESEQUENZAINCORSO` | Acquisition in progress... Wait… | Acquisition in progress... Wait… | 1 |
| `MESSACQUISIZIONETERMINATAOK` | Acquisition of injection sequence \nproperly terminated. | Acquisition of injection sequence \nproperly terminated. | 1 |
| `MESSAGESEGNALEGIRI_V2` | Warning!!! Remove the rpm signal connection: rpm will be obtained from injection times. | Warning!!! Remove the rpm signal connection: rpm will be obtained from injection times. | 1 |
| `MESSAGEVALVETRONIK` | The +12 key-on signal (white/red wire) must be shut down in the case the engine is off even if the ignition ke | The +12 key-on signal (white/red wire) must be shut down in the case the engine is off even if the ignition ke | 1 |
| `MESSAREAINCOLLA` | As áreas para copiar e colar têm dimensões diferentes. | The copy and paste areas have different sizes. | 1 |
| `MESSAUTOCALCONFIRMRESET` |  | Do you confirm selected operation? | 1 |
| `MESSAUTOCALERRORSENDCOMMAND` |  | Send command error. \nVerify connection and rety | 1 |
| `MESSAVVIAPARAMETRIMAP` | Aviar o processo automático para a adquirição \ndos parâmetros de funcionamento do MAP? \nATENÇÃO: todos os da | Start automatic procedure to acquire map parameters? \nWARNING: all previously acquired data will be lost! | 1 |
| `MESSCAMBIACARBURANTE` | Atenção !!! Se continuar com esta operação se cancelarão \nos parâmetros atuais da carburação. Deseja continua | Warning !!! This operation will delete\nthe current carburation parameters. Continue ? | 1 |
| `MESSCAMBIAINIETTORE` | Atenção !!! O tipo de injetor configurado é diferente do tipo \natualmente conectado. Deseja continuar de qual | Warning !!! The injector type selected is different from \nthe type currently connected. Continue ? | 1 |
| `MESSCAMBIAMODALITA` | Atenção!!! A mudança de modalidade somente é possível com o veículo desligado \ne com a chave retirada. Verifi | Warning!!! Mode change is possible only with engine switched off \nand key removed. Check and try again. | 1 |
| `MESSCAMBIATIPOINIETTORE` | Atenção !!! Se continuar com esta operação se modificarão \nos parâmetros atuais relativo à injeção. Deseja co | Warning !!! This operation will change \nthe current injection parameters. Continue ? | 1 |
| `MESSCOMPLETATO` | Completado | Completed | 1 |
| `MESSCONFIGRESIDDEFAULT` | A modificação realizada gerou uma configuração para a qual NÃO foram encontrados parâmetros de base certos. Co | Configuration change generated a configuration for which the correct basic parameters have NOT been found. Con | 1 |
| `MESSCONFIGRESIDDEFAULT1` | desta configuração de base. Os parâmetros atualmente configurados são completamente análogos aos anteriores ob | existence of such a basic configuration. The parameters currently set are entirely similar to those prior to t | 1 |
| `MESSCONTINUA` | Continuar? | Continue? | 1 |
| `MESSECUDISCONNECTED` | A central está desconectada porque a conexão não está correta. \nImpossível conectar-se. | Ecu is disconnected since it doesn't reply properly. \nConnection is not available. | 1 |
| `MESSELETTROVALVOLA` | Atenção!!! Com esta estratégia habilitada \nnão interromper a bomba de gasolina!!! | Warning!!! With this strategy running \ndo not cut off the petrol pump | 1 |
| `MESSERRORECUSWITCHCALIBRATION` | Impossible to set the switch parameters, \nECU not connected. | Impossible to set the switch parameters, \nECU not connected. | 1 |
| `MESSERRORELIVELLOACQUISITO` | Impossible to acquire current level. \nECU must be connected with car running in petrol. \nCheck and try again | Impossible to acquire current level. \nECU must be connected with car running in petrol. \nCheck and try again | 1 |
| `MESSERRORERIFLIV` | Atenção: as referências do NÍVEL não estão corretas! | Warning: incorrect LEVEL references! | 1 |
| `MESSERRORESEQUENZABENZINA` | Impossível adquirir corretamente a sequência de injeção gasolina. Controlar os cabos e o funcionamento a gasol | Impossible to acquire correctly the petrol injection sequence. \nCheck for wiring and petrol operation and try | 1 |
| `MESSERRORESEQUENZABENZINA1` | Impossível adquirir a sequência de injeção gasolina. A central deve ser conectada e funcionante a gasolina. Ve | Impossible to acquire correctly the petrol injection sequence \nECU must be connected with car running in petr | 1 |
| `MESSERRORESEQUENZAGAS` | Impossível impostar a sequência de injeção gás. A central deve ser conectada e funcionante a gasolina. Verific | Impossible to set manually the gas injection sequence \nECU must be connected wit car running in petrol. \nChe | 1 |
| `MESSERROREVALORILIVELLI` | Impossible to calculate references starting from acquired levels . \nRepeat levels acquisition and try again. | Impossible to calculate references starting from acquired levels . \nRepeat levels acquisition and try again. | 1 |
| `MESSERRORFIRMWARESWITCHCALIBRATION` | Current firmware does not handle the setting of the switch parameters. \nPlease upgrade the firmware and try a | Current firmware does not handle the setting of the switch parameters. \nPlease upgrade the firmware and try a | 1 |
| `MESSERRORGIRISWITCHCALIBRATION` | Impossible to set the switch parameters, \nrun the car and try again. | Impossible to set the switch parameters, \nrun the car and try again. | 1 |
| `MESSERRORNOSWITCHCALIBRATION` | Current switch does not handle the change of parameters. \nPlease update the hardware and try again. | Current switch does not handle the change of parameters. \nPlease update the hardware and try again. | 1 |
| `MESSERRORSTARTSWITCHCALIBRATION` | Error in the parameters communication of the switch. \nPlease check the connections and try again. | Error in the parameters communication of the switch. \nPlease check the connections and try again. | 1 |
| `MESSINIZIATARATURA` | &Iniciar calibrag. | &Start calibration | 1 |
| `MESSINSERISCIGIRIDATACHIMETRO` | Insert rpm read from speedometer and press RETURN | Insert rpm read from speedometer and press RETURN | 1 |
| `MESSMONOFUEL` | Use this option only on monofuel cars. \nOn cars with double carburation it could create problems. | Use this option only on monofuel cars. \nOn cars with double carburation it could create problems. | 1 |
| `MESSND` | N.D. | n.a. | 1 |
| `MESSPROCGRUIDATA1` | Impossível comunicar com a ECU. Verificar e tentar de novo. | Correct Ecu connection is not possible. \nCheck and try again | 1 |
| `MESSPROCGRUIDATA2` | Parâmetros do mapeador externo corretamente adquiridos e calculados | External map parameters correctly acquired and calculated. | 1 |
| `MESSPROCGRUIDATA3` | Impossível adquirir os dados necessários. Verificar e tentar de novo. | Impossible to get required data. \nSwitch off the engine and try again | 1 |
| `MESSPROCGRUIDATA4` | Dados adquiridos incompatíveis com o cálculo a executar (m<=0) | Acquired data incompatible with calculation to perform (m<=0) | 1 |
| `MESSPROCGRUIDATA5` | Dados adquiridos incompatíveis com o cálculo a executar (V2~V1) | Acquired data incompatible with calculation to perform (V2~V1) | 1 |
| `MESSPROCGRUIDATA6` | Dados adquiridos incompatíveis com o cálculo a executar (V2>V1) | Acquired data incompatible with calculation to perform (V2>V1) | 1 |
| `MESSTAGLIOPOMPAEV2` | Use EV2 wire for pump cutting | Use EV2 wire for pump cutting | 1 |
| `MESSTAGLIOPOMPAKLINE` | Use K-Line wire for pump cutting, \nnot for OBD connection | Use K-Line wire for pump cutting, \nnot for OBD connection | 1 |
| `MESSTARATURAK` | Se continuar, o procedimento de calibragem terminará. Deseja continuar ? | If you continue, the calibration procedure will terminate. Continue? | 1 |
| `MESSTERMINAACQUISIZIONESEQUENZAINIEZIONE` | Stop acquisition | Stop acquisition | 1 |
| `MESSTERMINATARATURA` | &Terminar calibrag. | &End calibration | 1 |

### Corrcalibra

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `MESSECUDISCONNECTED` | A central está desconectada porque a conexão não está correta. \nImpossível conectar-se. | Ecu is disconnected since it doesn't reply properly. \nConnection is not available. | 1 |

### Diagnosi

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `LABELDIAGNOSIATTENTION` | Estes testes devem ser realizados com o motor ligado funcionando a gasolina | This tests are to be executed with engine running in petrol mode | 2 |
| `MESSCONFERMATEMPI` | Tem certeza de querer efetuar a operação solicitada? | Confirm the requested operation? | 1 |
| `MESSDIAGNOSISWITCHON0` | (201) High pressure solenoid valve in short circuit or cut wire | (201) High pressure solenoid valve in short circuit or disconnected | 1 |
| `MESSDIAGNOSISWITCHON1` | (202) Predisposition | (202) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON10` | (303) Gas injector B cut wire or short circuit to ground | (303) Gas injector B not connected or short to ground | 1 |
| `MESSDIAGNOSISWITCHON11` | (304) Gas injector B short circuit to plus ignition | (304) Gas injector B short to positive | 1 |
| `MESSDIAGNOSISWITCHON12` | (305) Gas injector C cut wire or short circuit to ground | (305) Gas injector C not connected or short to ground | 1 |
| `MESSDIAGNOSISWITCHON13` | (306) Gas injector C short circuit to plus ignition | (306) Gas injector C short to positive | 1 |
| `MESSDIAGNOSISWITCHON14` | (307) Gas injector D cut wire or short circuit to ground | (307) Gas injector D not connected or short to ground | 1 |
| `MESSDIAGNOSISWITCHON15` | (308) Gas injector D short circuit to plus ignition | (308) Gas injector D short to positive | 1 |
| `MESSDIAGNOSISWITCHON16` | (401) Petrol injector link wire A short circuit to ground | (401) Petrol injector A short to ground | 1 |
| `MESSDIAGNOSISWITCHON17` | (402) Predisposition | (402) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON18` | (403) Petrol injector link wire B short circuit to ground | (403) Petrol injector B short to ground | 1 |
| `MESSDIAGNOSISWITCHON19` | (404) Predisposition | (404) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON2` | (203) Change over switch not detected | (203) Change over switch not detected | 1 |
| `MESSDIAGNOSISWITCHON20` | (405) Petrol injector link wire C short circuit to ground | (405) Petrol injector C short to ground | 1 |
| `MESSDIAGNOSISWITCHON21` | (406) Predisposition | (406) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON22` | (407) Petrol injector link wire D short circuit to ground | (407) Petrol injector D short to ground | 1 |
| `MESSDIAGNOSISWITCHON23` | (408) Predisposition | (408) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON24` | (207) Predisposition | (207) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON25` | (208) Predisposition | (208) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON26` | (209) Water temperature sensor cut wire | (209) Water temperature sensor not connected | 1 |
| `MESSDIAGNOSISWITCHON27` | (210) Water temperature sensor short circuit to plus | (210) Water temperature sensor short to positive | 1 |
| `MESSDIAGNOSISWITCHON28` | (211) Water temperature sensor short circuit to ground | (211) Water temperature sensor short to ground | 1 |
| `MESSDIAGNOSISWITCHON29` | (212) Gas temperature sensor cut wire | (212) Gas temperature sensor not connected | 1 |
| `MESSDIAGNOSISWITCHON3` | (204) Predisposition | (204) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON30` | (213) Gas temperature sensor short circuit to plus | (213) Gas temperature sensor short to positive | 1 |
| `MESSDIAGNOSISWITCHON31` | (214) Gas temperature sensor short circuit to ground | (214) Gas temperature sensor short to ground | 1 |
| `MESSDIAGNOSISWITCHON32` | (215) Predisposition | (215) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON33` | (216) Predisposition | (216) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON34` | (217) Predisposition | (217) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON35` | (218) Predisposition | (218) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON36` | (219) High pressure sensor cut wire or short circuit to plus | (219) High pressure sensor cut wire or short circuit to plus | 1 |
| `MESSDIAGNOSISWITCHON37` | (220) Predisposition | (220) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON38` | (221) Low pressure sensor short circuit to plus | (221) Low pressure sensor short circuit to plus | 1 |
| `MESSDIAGNOSISWITCHON39` | (222) Predisposition | (222) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON4` | (205) Predisposition | (205) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON5` | (206) Predisposition | (206) Predisposition | 1 |
| `MESSDIAGNOSISWITCHON6` | Diagnosis in progress | Diagnosis in progress | 1 |
| `MESSDIAGNOSISWITCHON7` | (200) Diagnosis not successfully completed | (200) Diagnosis not successfully completed | 1 |
| `MESSDIAGNOSISWITCHON8` | (301) Gas injector A cut wire or short circuit to ground | (301) Gas injector A not connected or short to ground | 1 |
| `MESSDIAGNOSISWITCHON9` | (302) Gas injector A short circuit to plus ignition | (302) Gas injector A short to positive | 1 |
| `MESSERRORE` | ERRO | ERROR | 1 |
| `MESSERRORRESETDIAGNOSYSTEST` | Impossível concluir o teste de diagnóstico. \nVerifique a conexão e tente novamente. | Impossible to complete this diagnosis test. \nVerify connection and retry. | 1 |
| `MESSERRORSETDIAGNOSYSTEST` | Impossível realizar o teste de diagnóstico. \nVerifique a conexão e tente novamente. | Impossible to run this diagnosis test. \nVerify connection and retry. | 1 |
| `MESSOK` | OK ! | OK | 1 |

### File

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `FINESTRACARICACONF` | Selecione a configuração para ser carregada na central de comando | Select the configuration to load in ECU | 1 |
| `FINESTRACARICADATI` | Carregando os dados da central de comando | Loading data from ECU | 1 |
| `FINESTRACARICAPDF` | Instruções de montagem | Installation instructions | 2 |
| `FINESTRACONFIG` | Configuração do veículo | Vehicle configuration | 1 |
| `FINESTRASALVADATI` | Guarde os dados da central no arquivo | Save ECU data to file | 1 |
| `FINESTRASALVAFILE` | Salve a configuração com o nome... | Save configuration with name... | 1 |
| `FINESTRASCARICADATI` | Descarregando os dados na central de comando | Downloading data to ECU | 1 |
| `MESSCOMPLETATO` | Completado | Completed | 1 |
| `MESSNOFILE` | Nenhum arquivo na memória | No file in memory! | 1 |
| `MESSNOFOUNDFILE` | ATENÇÃO: problemas na abertura do arquivo | WARNING: problems in opening file. | 1 |
| `MESSOVERWRITE` | Este arquivo já existe. Quer modificar? | The file already exists. Overwrite? | 1 |
| `MESSSALVAFILE` | Salvar os dados na memória em um arquivo? | Save stored data on a file? | 1 |

### Graficodrt

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `FINESTRAGESTIONETRACCE` | Acrescentar/remover traços | Add/remove traces | 1 |
| `FINESTRAGRAFICO` | Gráfico | Graph | 1 |
| `MESSBADFILE` | Formato de arquivo inválido | File format not valid | 1 |

### Graficoscope

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `FINESTRAAPRIDRT` | Abrir arquivo dos dados em tempo real | Open data file in real time | 1 |
| `FINESTRAGRAFICO` | Gráfico | Graph | 1 |
| `FINESTRASALVADRT` | Salvar aquisição com nome... | Save acquisition with name... | 1 |
| `MESSBADFILE` | Formato de arquivo inválido | File format not valid | 1 |
| `MESSBADFILEVERSION` |  | File version not compatible. | 1 |

### Iodata

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `MESSCAMBIAFORZATOINIETTORE` | Atenção !!! O tipo de injetor para o qual a central está configurada \né diferente do tipo atualmente conectad | Warning !!! The injector type selected in the ECU \nis different from the type currently connected. \nModify t | 1 |
| `MESSCONTINUA` | Continuar? | Continue? | 1 |
| `MESSERRCONN` | Impossível conectar | Impossible to connect. | 1 |
| `MESSRIPROVA` | Tentar novamente? | Try again? | 1 |

### Landiconnect

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `MESS_ERROR_LOAD_LANDI_CONNECT` | Error in loading some settings\nfrom Landi Connect device | Error in loading some settings\nfrom Landi Connect device | 1 |

### Main

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `FINESTRACARICADATI` | Carregando os dados da central de comando | Loading data from ECU | 1 |
| `FINESTRACARICAPDF` | Instruções de montagem | Installation instructions | 1 |
| `FINESTRACONFIG` | Configuração do veículo | Vehicle configuration | 3 |
| `FINESTRACORRTARATURA` | Modifique calibração | Modify calibration | 1 |
| `FINESTRADIAGNOSI` | Diagnóstico central de comando | ECU diagnosis | 1 |
| `FINESTRARIPROGRAMMA` | Atualizando o software da central de comando | Update ECU software | 1 |
| `FINESTRASALVADATI` | Guarde os dados da central no arquivo | Save ECU data to file | 1 |
| `FINESTRASCARICADATI` | Descarregando os dados na central de comando | Downloading data to ECU | 1 |
| `FINESTRASCONNETTI` | Conexão terminada | End of connection | 1 |
| `FINESTRATARATURA` | Calibração automática da central de comando | Automatic ECU calibration | 1 |
| `FINESTRAVISUALIZZA` | Visualização dos parâmetros do veículo | Vehicle parameter display | 1 |
| `MESSAGEPERSONALIZZAZIONE` | correctly set. | correctly set. | 1 |
| `MESSAGGIORNAINTERF` | Solicite ao fornecedor uma versão software mais atualizada. | Ask your supplier for a more recent software version. | 1 |
| `MESSBATTLOW` | ATENÇÃO: carga da bateria insuficiente: Tentar novamente? | WARNING: Battery voltage too low: Try again? | 1 |
| `MESSBOOTLOADER` | Please reprogram the ECU without trying to connect \nand then load a new configuration. | Please reprogram the ECU without trying to connect \nand then load a new configuration. | 1 |
| `MESSCAMBIAFORZATOINIETTORE` | Atenção !!! O tipo de injetor para o qual a central está configurada \né diferente do tipo atualmente conectad | Warning !!! The injector type selected in the ECU \nis different from the type currently connected. \nModify t | 1 |
| `MESSCHECKCONNECTION` | Controle a conexão. | Check for diagnosis cable and turn key-on | 1 |
| `MESSCOMPLETATO` | Completado | Completed | 1 |
| `MESSCONFERMARESET` | Tem certeza de querer redefinir todos os dados da central de comando? | Do you really want to reset all the ECU data? | 1 |
| `MESSCONN` | Central de comando ligada | ECU connected | 1 |
| `MESSCPUERRATA` | A central de comando atualmente conectada não é compatível. | The ECU currently connected is not compatible. | 1 |
| `MESSERRCONN` | Impossível conectar | Impossible to connect. | 1 |
| `MESSERRVERSIONECPU` | Erro na identificação da central de comando conectada. | Error in identifying the connected ECU. | 1 |
| `MESSFALLITAPROG` | A tentativa de programação anterior falhou. | The previous programming attempt has failed. | 1 |
| `MESSFILEUFF` | Do you want to make the file official? | Do you want to make the file official? | 1 |
| `MESSGPL` | GLP | LPG | 1 |
| `MESSIDENTIF` | Config | Config | 1 |
| `MESSINITCPU` | Iniciando a central de comando... | Initialising ECU... | 1 |
| `MESSISTRUZPROG` | Desligue 12V da bateria e tente novamente dentro de 10 segundos | Disconnect 12V battery and try again within 4 secs. | 1 |
| `MESSMETANO` | CNG | CNG | 1 |
| `MESSNESSUNACONN1` | ATENÇÃO: não foi detectada a conexão com a central de comando. | WARNING: No connection found with ECU. | 1 |
| `MESSNESSUNACONN2` | As modificações feitas não terão efeito na central de comando. | Last changes won't have any effect on ECU. | 1 |
| `MESSNESSUNACONN3` | antes de modificar os parâmetros estabeleça a conexão (CTRL+C). | Connect to the ECU (CTRL + C) before changing parameters. | 1 |
| `MESSNESSUNAGGIORNAMENTO` | Nenhuma atualização disponível para esta central de comando | No upgrade available for this ECU | 1 |
| `MESSNOCONN` | Central de comando desligada | ECU not connected | 1 |
| `MESSNOECU` | Central de comando não conectada: impossível executar a calibragem! | ECU not connected: calibration function not available! | 1 |
| `MESSNOKEY` | Não é possível consultar esta página sem senha hardware | This page cannot be read without hardware key | 1 |
| `MESSNOSCRITTURACONFIG` | Impossível descarregar os dados de configuração memorizados.anteriormente Deseja repetir ? | Downloading of stored configuration data not possible. Try again? | 1 |
| `MESSNOTARAFILE` | Não foi encontrado nenhum arquivo de calibragem | No calibration file found | 1 |
| `MESSPROGKO` | Impossível efetuar a programação | Programming not possible | 1 |
| `MESSPROGOK` | Programação efetuada com sucesso | Programming completed sucessfully | 1 |
| `MESSRESETERROR` | Impossível efetuar a redefinição da central de comando. | Impossible to reset ECU. | 1 |
| `MESSRESETOK` | Redefinição da central de comando completada. | ECU reset completed. | 1 |
| `MESSRIPROVA` | Tentar novamente? | Try again? | 1 |
| `MESSSAVEFILE` | Salvar os dados na memória em um arquivo? | Save stored data in a file? | 1 |
| `MESSSCARICACONFIGKO` | Impossível configurar os dados carregados. Central de comando desconectada. | Impossible to set loaded data. ECU disconnected. | 1 |
| `MESSSCONNETTI` | Desconectando a central de comando. Aguarde... | Disconnecting. Please wait... | 1 |
| `MESSTENTATIVO` | Tentativa | Attempt | 1 |
| `MESSVERSIONESW` | Versão de software | Software version | 1 |
| `MESSWARNINGINTERF` | Podem ocorrer erros ao carregar alguns dados | Errors could occur in loading some data | 1 |

### Visualizza

| Chave | por | eng | Funções (n) |
|---|---|---|---:|
| `FINESTRAAPRIDRT` | Abrir arquivo dos dados em tempo real | Open data file in real time | 1 |
| `FINESTRASALVADRT` | Salvar aquisição com nome... | Save acquisition with name... | 1 |
| `MESSECUDISCONNECTED` | A central está desconectada porque a conexão não está correta. \nImpossível conectar-se. | Ecu is disconnected since it doesn't reply properly. \nConnection is not available. | 1 |
| `MESSND` | N.D. | n.a. | 1 |
| `MESSNODELFILE` | O arquivo existente não pode ser sobrescrito | Impossible to overwrite existing file. | 1 |
| `MESSRENAMEFILE` | Atribua outro nome ao novo arquivo | Re-name the new file. | 1 |
