# Funcionalidades do ProgBase.exe que o app OMEGAS ainda não tem

Gerado por `tools/progbase/inventario_funcionalidades.py` a partir de `tools/progbase/dados/funcionalidades.json` (curado) com evidências conferidas contra a extração (handler, componente ou texto inexistente faz o gerador falhar).

- **Marcas por item:** OBS-COD = a função está no executável; DERIVADO = conclusão tirada de nomes/legendas/chamadas; INFERIDO = dedução; DESCONHECIDO = sem prova.
- **Coluna "No app?":** `não` / `parcial` com a base da afirmação. Para "não", a base é uma busca por palavras-chave no código do app (INFERIDO, não prova de ausência).
- Itens de licença/registro estão aqui só para **descrever a função**; nada é contornado.
- Itens "perigoso" **não devem** ir para o app sem decisão explícita do dono (ver regra do app: gravar é sempre manual, com confirmação e leitura de volta).

| Categoria | Itens |
|---|---:|
| Útil para leigo | 6 |
| Avançado | 8 |
| Instalação / oficina | 6 |
| Perigoso (escreve na ECU ou age no carro) | 8 |

## Útil para leigo (6)

### F01 — Conectar/desconectar e identificar a ECU (versão, tipo OMEGAS 4/EVO 360/EVO OBD/EVO L, número de série na barra de status)

- **Tela:** `TFormMain` · **Marca:** OBS-COD · **No app?** parcial
- **O que é:** Conexão serial, identificação (GetIdentification/GetComFail) e mensagens de erro de conexão (MESSNOCONN, MESSERRCONN, MESSCHECKCONNECTION).
- **Base da coluna "No app?":** O app conecta por USB (MP48) e lê telemetria (STATUS.md); identificação completa (versão/tipo/serial) como no original não está documentada no app. INFERIDO.
- **Evidência:** `TFormMain.ActionConnettiExecute` (0x004FB840); `TFormMain.ActionDisconnettiExecute` (0x004FB858); `TFormMain.StatusBarMainDrawPanel` (0x004FF7D4); `TFormMain.ActionAzzeraIdExecute` (0x005039A0)

### F06 — Diagnóstico: lista de falhas ("(Código) descrição do erro"), apagar erros, zerar tempos a gasolina/gás

- **Tela:** `TFormDiagnosi` · **Marca:** OBS-COD · **No app?** nao
- **O que é:** Leitura das falhas da ECU com descrição, apagar a memória de falhas, contadores de tempo de funcionamento a gasolina e a gás.
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Ler é seguro; apagar falhas some com o histórico.
- **Evidência:** `TFormDiagnosi.FormActivate` (0x004CD6A8); `TFormDiagnosi.ButtonAzzeraErroriClick` (0x004D6694); `TFormDiagnosi.ButtonAzzeraTempiClick` (0x004D6ADC); componente `TFormDiagnosi.ListDiagnosi`

### F10 — Visualização em tempo real dos parâmetros do veículo (rpm, lambda 1/2, MAP, TPS, temperaturas água/gás, pressão, tempos de injeção, tensão dos injetores, estados benzina/gás/cut-off/sob chave)

- **Tela:** `TFormVisualizza` · **Marca:** OBS-COD · **No app?** parcial
- **O que é:** Painel de instrumentos do carro com todos os sensores lidos pela ECU.
- **Base da coluna "No app?":** O app tem cartões do motor e telemetria ao vivo (assets/ui/core/cartoes-motor.js; Mp48TelemetryScale.kt); cobertura de todos os campos do original não verificada. INFERIDO.
- **Evidência:** `TFormVisualizza.Timer1Timer` (0x0050B408); `TFormMain.ActionVisualizzaExecute` (0x00501568)

### F20 — Configuração do comutador: brilho do LED e som do buzzer

- **Tela:** `TFormSwitch` · **Marca:** OBS-COD · **No app?** nao
- **O que é:** Ajusta o LED do botão do comutador e o buzzer.
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Baixo.
- **Evidência:** `TFormSwitch.TrackBarLedChange` (0x00412CEC); `TFormSwitch.ComboBuzzerKeyPress` (0x00412E5C); `TFormSwitch.FormClose` (0x00412AB8)

### F23 — Troca de idioma (26 idiomas)

- **Tela:** `TFormMain` · **Marca:** OBS-COD · **No app?** nao
- **O que é:** Menu Configurações > Idioma com 26 idiomas (LANG_MAIN).
- **Base da coluna "No app?":** O app está em português; seleção de idioma não encontrada no app. INFERIDO.
- **Evidência:** componente `TFormMain.MenuSelLingua`; texto `__OPT_MENU_LANG`

### F24 — Arquivo de instruções de montagem / esquemas em PDF dentro do programa

- **Tela:** `TFormFile` · **Marca:** DERIVADO · **No app?** nao
- **O que é:** Navegador de PDFs de montagem (TFormPdf com a biblioteca PDF embutida).
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Evidência:** `TFormMain.ActionArchivioExecute` (0x004FF6C4); `TFormFile.TreeViewFileClick` (0x004DDC04)

## Avançado (8)

### F07 — Diagnóstico de ligação (switch-on diagnosis) e quadro de congelamento (freeze frame)

- **Tela:** `TFormDiagnosi` · **Marca:** INFERIDO · **No app?** nao
- **O que é:** Habilita o diagnóstico na ignição e escolhe quais variáveis congelar quando ocorre uma falha.
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Escreve opções na ECU.
- **Evidência:** `TFormDiagnosi.CheckSwitchOnDiagnosisClick` (0x004D7E48); `TFormDiagnosi.CheckListBoxFFClickCheck` (0x004D82C0); `TFormDiagnosi.ButtonResetFFClick` (0x004D8360)

### F12 — Gravar aquisição em tempo real (DRT) em arquivo e reabrir em gráfico

- **Tela:** `TFormVisualizza` · **Marca:** OBS-COD · **No app?** parcial
- **O que é:** Gravação dos parâmetros ao vivo em arquivo de texto/XML com data e hora.
- **Base da coluna "No app?":** O app grava sessões (SessionRecorder.kt) em formato próprio; o formato DRT (.txt, XML acquisitionData) do original não é lido/gravado. INFERIDO.
- **Evidência:** `TFormVisualizza.MenuCominciaDrtClick` (0x0050D86C); `TFormVisualizza.MenuTerminaDrtClick` (0x0050DB50); `TFormVisualizza.MenuGraficoClick` (0x0050DC08); componente `TFormVisualizza.SaveDialogDrt`; componente `TFormVisualizza.OpenDialogDrt`

### F13 — Osciloscópio de sinais (gráfico em tempo real) com marcadores, zoom, faixa automática, legenda, carregar/salvar/exportar imagem

- **Tela:** `TFormGraficoScope` · **Marca:** OBS-COD · **No app?** parcial
- **O que é:** Gráfico de qualquer variável lida da ECU contra o tempo, com marcadores (manuais, automáticos e da ECU).
- **Base da coluna "No app?":** O app tem gráficos do AutoCal, mas não um osciloscópio genérico de canais escolhidos. INFERIDO.
- **Evidência:** `TFormMain.Plot1Click` (0x0050417C); `TFormGraficoScope.TimerTimer` (0x004257E4); `TFormGraficoScope.btnSaveClick` (0x004296EC); `TFormGraficoScope.btnLoadClick` (0x0042996C); `TFormGraficoScope.btnExportImageClick` (0x00429A98); `TFormGraficoScope.btnAutoRangeClick` (0x00429DD8); `TFormGraficoScope.ActionAddMarkerExecute` (0x004281FC)

### F14 — Gerir quais traços aparecem no gráfico e imprimir o gráfico

- **Tela:** `TFormGraficoDrt` · **Marca:** OBS-COD · **No app?** nao
- **O que é:** Lista de traços com caixas de seleção e impressão (diálogo de impressão do Windows).
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Evidência:** `TFormGraficoDrt.MenuTracceClick` (0x004E6A84); `TFormGraficoDrt.MenuStampaClick` (0x004E6A50); componente `TFormGraficoDrt.PrintDialog1`

### F16 — Editar centros das células dos mapas (rpm × carga) e referências de mapa do AutoCal (K mín/máx)

- **Tela:** `TFormCentriCelle` · **Marca:** OBS-COD · **No app?** parcial
- **O que é:** Define em quais rpm/pressões ficam as células dos mapas.
- **Base da coluna "No app?":** O app edita Mapa K e Curva K; edição dos eixos (centros) não verificada. INFERIDO.
- **Risco:** Escreve na ECU; muda o significado de todas as células.
- **Evidência:** `TFormCentriCelle.ButtonOkClick` (0x00413524); `TFormCentriCelleK.ButtonOkClick` (0x00413BFC); `TFormRifAutocal.ButtonOkClick` (0x0051B910); `TAutoCalUI.ActionAutoCalRifExecute` (0x005187A0)

### F22 — Estratégias especiais de motor (Panda: habilitar mínimo, reconhecimento do clima, emulação do sensor de pressão; Tempi Morti: tempos mortos por injetor gás/gasolina)

- **Tela:** `TStrategiaPandaUI` · **Marca:** DERIVADO · **No app?** nao
- **O que é:** Telas embutidas (frames) de estratégias para o motor 169A4000: soglias de início/fim da estratégia e tempos mortos.
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Escreve na ECU.
- **Evidência:** `TStrategiaPandaUI.chk_SP_EN_STRATEGIA_MINIMOClick` (0x00522E08); `TStrategiaTempiMortiUI.UpDownGasAClick` (0x00526154); `TStrategiaTempiMortiUI.UpDownPetrolAClick` (0x00526114); texto `LANG_ID__SP__TITLE`

### F27 — Pré-visualizar/editar célula de mapa (variação percentual, valor, referências) por diálogo

- **Tela:** `TFormModMappa` · **Marca:** OBS-COD · **No app?** parcial
- **O que é:** Pequenos diálogos chamados pelo editor de mapa para alterar valor/variação/referências.
- **Base da coluna "No app?":** O app tem editor de mapa (map-editor.js). Diálogos específicos do original não verificados. INFERIDO.
- **Evidência:** `TFormModMappa.EditValoreKeyPress` (0x00969E80); `TFormModificaMappa.EditDataKeyPress` (0x0050EF08); `TFormRiferimenti.FormCloseQuery` (0x009744E0)

### F28 — Configuração do veículo em 33 abas (TFormConfig)

- **Tela:** `TFormConfig` · **Marca:** OBS-COD · **No app?** parcial
- **O que é:** Janela principal de parametrização: cambio, lambda, sensores, temperaturas, injetores, mapas K, etc.
- **Base da coluna "No app?":** O app lê 33 parâmetros (param_snapshot) e grava só o anti-apagão (aba Avançado); as demais abas não existem no app. Detalhe das abas: docs/progbase/abas (outro agente).
- **Risco:** Escreve parâmetros da ECU.
- **Evidência:** `TFormMain.ActionConfExecute` (0x004F95F4); `TFormMain.OpenConfigWindow` (0x004F9190)

## Instalação / oficina (6)

### F02 — Abrir e salvar configuração completa em arquivo (.lnc, .ldc, .lec, .lrc) com escolha do modelo de cilindros

- **Tela:** `TFormMain` · **Marca:** OBS-COD · **No app?** nao
- **O que é:** Backup/restauração da configuração inteira da ECU em arquivo próprio do fabricante.
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Abrir só lê arquivo; aplicar na ECU é outro passo (F03).
- **Evidência:** `TFormMain.ActionApriExecute` (0x004FE618); `TFormMain.ActionSalvaExecute` (0x004FEA54); componente `TFormMain.OpenDialog`; componente `TFormMain.SaveDialog`

### F15 — Calibração automática antiga da central (tela TFormCalibra) e correção por zonas (TFormCorrCalibra: PgUp/PgDn)

- **Tela:** `TFormCalibra` · **Marca:** OBS-COD · **No app?** parcial
- **O que é:** Roteiro guiado de calibração com leitura de lambda/pressão/temperaturas e ajuste de zona a zona da grade K.
- **Base da coluna "No app?":** O app tem AutoCal (nova calibração) e edição de Curva K/Mapa K; o roteiro guiado antigo "Calibrazione automatica centralina" e a edição por zonas com setas não existem como tal. INFERIDO.
- **Risco:** Escreve calibração na ECU.
- **Evidência:** `TFormMain.OpenFormAutotaratura` (0x0050093C); `TFormCalibra.TimerBlinkTimer` (0x004847A0); `TFormCorrCalibra.ActionIncrementaZonaExecute` (0x004CCFFC); `TFormCorrCalibra.ActionDecrementaZonaExecute` (0x004CD134); `TFormCorrCalibra.DrawGridKDrawCell` (0x004CCD08)

### F17 — Landi Connect: telemetria/consumo, aprendizado de marchas, volume do tanque, vazão estática e offset dos injetores, zerar trip/totais

- **Tela:** `TFormLandiConnect` · **Marca:** OBS-COD · **No app?** nao
- **O que é:** Configuração do módulo de comunicação Landi Connect (consumo/nível) e da tabela de offsets dos injetores.
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Escreve na ECU.
- **Evidência:** `TFormLandiConnect.CheckEnableLandiConnectClick` (0x004ED424); `TFormLandiConnect.CheckEnableGearClick` (0x004ED8B0); `TFormLandiConnect.ButtonResetClick` (0x004EDA24); `TFormLandiConnect.DrawGridInjrTOfsTblDrawCell` (0x004EC96C)

### F18 — Gestão de revisões (tagliandi) com PIN: horas a gás até a revisão, revisões feitas, dados do instalador, inibir passagem a gás

- **Tela:** `TFormService` · **Marca:** OBS-COD · **No app?** nao
- **O que é:** Lembrete de revisão pago pelo instalador, protegido por PIN (CryptoAPI) e com dados do instalador cifrados.
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Evidência:** `TFormMain.ImageServiceClick` (0x00503FB0); `TFormService.CheckEnableServiceClick` (0x0050843C); `TFormService.CheckServicePinClick` (0x0050863C); `TFormService.ButtonNewPinClick` (0x005088E8); `TFormService.ButtonAzzeraNumeroTagliandiEffettuatiClick` (0x00508B44); `TFormService.FormClose` (0x00508D28)

### F25 — Licença/registro, chave de hardware e consulta online (SÓ descrição; nada é contornado)

- **Tela:** `TAboutBox` · **Marca:** OBS-COD · **No app?** nao
- **O que é:** O programa exige licença por PC (arquivo License.dat e código de registro no registro do Windows), mostra aviso de expiração da chave de hardware e consulta o servidor do fabricante.
- **Base da coluna "No app?":** Não se aplica ao app (sem licença por PC). INFERIDO.
- **Evidência:** `TAboutBox.ButtonRequestClick` (0x00482254); `TAboutBox.ButtonLoadLicenseClick` (0x00481D40); `TAboutBox.ButtonDeleteLicenseClick` (0x00481FF8); `TAboutBox.OkButtonClick` (0x004813DC); `TFormAggKey.FormShow` (0x0041199C); `TFormMain.TimerUpdateTimer` (0x004F37F4)

### F26 — Atualizar programa e cartões/mapas (executa CopyFiles.exe)

- **Tela:** `TFormMain` · **Marca:** OBS-COD · **No app?** nao
- **O que é:** Lança utilitário auxiliar que copia arquivos de atualização.
- **Base da coluna "No app?":** Não se aplica ao app (atualização por APK). INFERIDO.
- **Evidência:** `TFormMain.ActionAggiornaExecute` (0x005035DC)

## Perigoso (escreve na ECU ou age no carro) (8)

### F03 — Carregar para a ECU / salvar da ECU a configuração pelo "arquivo" de configurações (árvore ConfigCNG/ConfigLPG)

- **Tela:** `TFormFile` · **Marca:** DERIVADO · **No app?** nao
- **O que é:** Navegador de configurações pré-prontas por tipo de combustível; ao confirmar, grava os parâmetros na ECU (com tela de espera TFormAttesa).
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Escreve a configuração inteira da ECU: pode deixar o carro sem funcionar se o arquivo for errado.
- **Evidência:** `TFormMain.ActionApriConfExecute` (0x00500314); `TFormMain.ActionSalvaConfExecute` (0x005000CC); `TFormFile.ButtonOkClick` (0x004D9F30); `TFormFile.FormActivate` (0x004D9968)

### F04 — Reprogramar a ECU / atualizar firmware (busca de arquivos, preparação, gravação, tentativa novamente, aviso de bateria baixa)

- **Tela:** `TFormScarica` · **Marca:** OBS-COD · **No app?** nao
- **O que é:** Fluxo de flash da ECU: conecta, identifica, procura firmware/configuração, grava e confirma; pede desligar a bateria 12V e religar em 10 s para entrar em boot.
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Pode inutilizar (brickar) a ECU se interrompido; exige bateria boa e arquivo correto.
- **Evidência:** `TFormMain.ActionProgrammaExecute` (0x004FDBD4); `TFormMain.ActionUpdateExecute` (0x005035D4); `TFormScarica.ButtonCambiaClick` (0x0050523C); texto `MESSISTRUZPROG`; texto `MESSBATTLOW`; texto `MESSPROGOK`; texto `MESSPROGKO`

### F05 — Reset geral da ECU a partir do PC

- **Tela:** `TFormMain` · **Marca:** INFERIDO · **No app?** nao
- **O que é:** Apaga/redefine os dados da ECU (a pergunta de confirmação existe na tabela de textos). O item de menu "Reset ECU" está vazio (só RET); a rotina real é uma ação sem menu.
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Apaga calibração e configuração.
- **Evidência:** `TFormMain.ActionResetDaPcExecute` (0x00501248); `TFormMain.ActionResetCentralinaExecute` (0x00501244); texto `MESSCONFERMARESET`; texto `MESSRESETOK`; texto `MESSRESETERROR`

### F08 — Exclusão (liga/desliga) de cada injetor de gás A..I e leitura dos tempos por injetor

- **Tela:** `TFormDiagnosi` · **Marca:** DERIVADO · **No app?** nao
- **O que é:** Desativa individualmente um injetor para teste; mostra tempos de injeção por cilindro.
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Desligar injetor altera a mistura e pode danificar o motor se esquecido.
- **Evidência:** `TFormDiagnosi.PanelOnClick` (0x004D7774); `TFormDiagnosi.PanelOffClick` (0x004D7664); `TFormDiagnosi.TimerTempiTimer` (0x004D4BDC)

### F09 — Teste de saídas: eletroválvulas 1 e 2 e saída do comutador (On/Off)

- **Tela:** `TFormDiagnosi` · **Marca:** DERIVADO · **No app?** nao
- **O que é:** Aciona as saídas da ECU para teste de bancada/instalação; ao fechar a tela desliga os testes.
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Acionar válvula de gás com motor parado/ligado: risco de vazamento.
- **Evidência:** `TFormDiagnosi.PanelTestOnClick` (0x004D7888); `TFormDiagnosi.PanelTestOffClick` (0x004D7C78); `TFormDiagnosi.FormClose` (0x004D1B18); texto `MESSERRORSETDIAGNOSYSTEST`; texto `LABELDIAGNOSIATTENTION`

### F11 — Comutador manual gasolina/gás pelo PC

- **Tela:** `TFormVisualizza` · **Marca:** INFERIDO · **No app?** nao
- **O que é:** Botão "Comutador" que força a troca de combustível.
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Muda o combustível em uso com o carro ligado.
- **Evidência:** `TFormVisualizza.ButtonCommutatoreClick` (0x0050DD14)

### F19 — Inibir a passagem de gasolina para gás (e número de partidas a gás permitidas) por PIN

- **Tela:** `TFormService` · **Marca:** INFERIDO · **No app?** nao
- **O que é:** Trava o carro em gasolina (modo de revisão vencida/emergência).
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Impede o uso do gás: o dono pode ficar sem GNV.
- **Evidência:** `TFormService.CheckDisableGasClick` (0x005085CC); `TFormService.EditStandardKeyPress` (0x00506D9C)

### F21 — Comunicação sem fio Bee: varredura de rede e energia por canal, PIN, potência, conectar a dispositivo remoto, programar firmware do dongle e do MCU

- **Tela:** `TAebBeeFormLight` · **Marca:** INFERIDO · **No app?** nao
- **O que é:** Gerenciador do adaptador sem fio (dongle) e dos módulos remotos, incluindo gravação de firmware por rádio.
- **Base da coluna "No app?":** Busca por palavras-chave em app/src/main (assets/ui e java) não achou superfície de UI/protocolo equivalente; STATUS.md não a lista. INFERIDO.
- **Risco:** Gravar firmware do dongle/MCU pode inutilizá-lo.
- **Evidência:** `TFormMain.ImageBeeClick` (0x004F917C); `TAebBeeFormLight.ButtonScanClick` (0x00918BF4); `TAebBeeFormLight.ButtonProgramDongleClick` (0x00919F80); `TAebBeeFormLight.ButtonProgramMcuClick` (0x0091AE74); `TAebBeeFormLight.ButtonChangePinClick` (0x00918158)
