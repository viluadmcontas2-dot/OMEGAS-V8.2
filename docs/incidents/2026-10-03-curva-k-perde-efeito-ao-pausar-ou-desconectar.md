# Incidente — Curva K "perde o efeito" ao clicar em Pausar aquisição ou ao remover o cabo

Data: 2026-10-03. Base analisada: `work/platina-refino-blackbox-20261003` @ `836d6165` (fonte do último APK, PR #122) comparada com `main` @ `a3db5e93`. Classe desta investigação: **1** (leitura de código das duas versões) + **3** (replay do corpus real `PortmonLOGNOVO.LOG` e de sessões reais gravadas pelo app em 01/10 e 02/10). Nenhuma ECU física, nenhuma escrita, nenhuma mudança de UI.

Selos: `PROVADO` = visto na fiação, no código ou nas sessões gravadas; `INFERIDO` = dedução com a cadeia de evidência em uma linha; `DESCONHECIDO` = lacuna, com o que provaria.

## Sintoma relatado pelo proprietário

Com o cabo ligado e o OMEGAS em segundo plano, a aquisição, o AutoCal e a Curva K seguem funcionando. Ao remover o cabo ou ao clicar em **Pausar aquisição**, "a calibração da curva se perde" e o consumo volta a cair. No ProgBase existe o checkbox *Enable Auto Calibration*, que exibe um aviso ao ser desmarcado, e a dúvida é se existe algum outro passo do ciclo AutoCal que "firma" a curva e que o app não faz.

## O que o app faz hoje (`PROVADO`, idêntico em `main` e na branch do APK)

| Gesto do motorista | Caminho no código | Frame enviado à ECU |
|---|---|---|
| **Pausar aquisição** (toggle do cockpit AutoCal, `autocal-cockpit.js` `AutoCalUxModel.toggleAction`) | `DISABLE_AUTO_CAL` → `AutoCalNativeActionManager` → `AutoCalProtocol.setEnabled(false)` + readback obrigatório de `AUTO_CAL_ENABLE` | `12 4A 01 00 5D` (escreve `AUTO_CAL_ENABLE = 0`) |
| **Pausar** da notificação (`ACTION_TOGGLE_ENGINE`) | `TelemetryForegroundService.toggleEngine` → `NativeRuntimeManager.stop` → `engine.stop(graceful = true)` → `gracefulDisconnect()` | `00 01 01` (fecha a sessão MP48); nenhum frame AutoCal |
| Remover o cabo | `handleUsbTransition` → `endUsbSession("USB_DISCONNECTED")`; nada é transmitido | nenhum |
| Reconectar | handshake `00 02 02` → `01 00 3A 3B` → `00 25 25`; o app **não** reaplica `AUTO_CAL_ENABLE` (regra do protocolo físico, fase H) | leitura apenas |

Consequências diretas:

- **"Pausar aquisição" não pausa o app. Pausa o módulo AutoCal da ECU.** É exatamente o frame do checkbox *Abilita autocalibrazione* do ProgBase (LN seq 12615/12821 `12 4A 01 00 5D`; seq 12691/12863 `12 4A 01 01 5E`). O cockpit já declara isso em texto pequeno ("Pausar aquisição é a única ação desta tela que solicita AUTO_CAL_ENABLE=0"), mas o rótulo induz a ler como pausa de coleta do app.
- A pausa real do app (notificação) e a remoção do cabo não tocam em nenhum objeto AutoCal.
- O app nunca envia `01 12 00 13`. O ProgBase envia esse frame imediatamente antes de `00 01 01` ao desconectar depois de uma sessão com escritas (LN seq 7549 e 19890, sempre logo após blocos `14 3D 00`/`14 37 00`). Semântica `DESCONHECIDO` (lacuna L-01 da branch `evidence/progbase-original`). Resposta `53 00` em ~10 ms, igual à de `00 01 01`: não há latência compatível com gravação de EEPROM em lote.

## O que a ECU faz com a curva (fiação real + sessões reais do app)

1. **`MUL_ACT` persiste na ECU através de desconexões** (`PROVADO`).
   - LN: após `Reset All` (seq 1487) a curva vai a `1,000 × 30` (seq 1496) e **todas as 21 leituras seguintes do prefixo** continuam neutras, atravessando duas desconexões (`01 12 00` + `00 01 01` em 7549–7550 e 19890–19891) e duas reconexões com dump completo.
   - Sessões do app (JSONL do SessionRecorder, Drive): `events_0001.jsonl (31).json` de 01/10 18:49 UTC lê `AUTO_CAL_ENABLE = 1`, `NUM_AUTOMATCH_EXECUTED = 2`; `session_2026-10-02_20-57-58_ec1b2fd9` parte 0002 (02/10 23:58 UTC) lê `AUTO_CAL_ENABLE = 1`, contador `2`. O flag de habilitação sobreviveu a tudo o que aconteceu entre as duas sessões sem nenhum `01 12 00`.
   - Logo, **a curva não "some" da ECU ao desconectar e o enable não é desfeito por perda de sessão.** O que muda é se a ECU **aplica** a curva na injeção de GNV.
2. **A Curva K é objeto do módulo AutoCal, não da calibração base** (`PROVADO`): `MUL_ACT` 0x0161 e seu eixo `PETR_INJ_TBP` 0x014B vivem em `TAutoCalDM`; o Mapa K (`MAP_K` SC 84) vive em `TStreamDati`. A ECU altera `MUL_ACT` sozinha no AutoMatch (três épocas no LN com o PC só lendo) e no `Reset All`.
3. **O ProgBase prevê um passo de consolidação que o app não tem** (`PROVADO` existência e semântica na desmontagem; `EVIDENCE_GATED` para mutação no app): `ActionExportToKExecute` @ `0x00518514` — "Export to K". É matemática no PC (`0x00512708` bracket + `0x0051280C` interpolação linear sobre `PETR_INJ_TBP`) seguida de escrita do Mapa K pelo caminho normal `14 54 00 rr cc vv`. Em `docs/autocal/progbase-host-parity-v0.md` essa operação está listada como *EVIDENCE_GATED: no mutation promoted until exact host semantics are closed*. **Atualização com o EXE (DUMP_1.zip, seção "Evidência do ProgBase.exe" abaixo):** a semântica agora está fechada na desmontagem (`PROVADO`), e a ação é **código morto** nesta build: a `TAction ActionExportToK` não está ligada a nenhum botão ou menu no DFM `TAUTOCALUI` e nenhum código do formulário referencia o campo da ação nem chama o handler. O proprietário não executou um Export to K pela UI porque ele não é alcançável nela. `ActionFinishAutocal` está em `MenuAutoCal` com `Enabled = False`.
4. **Durante o período com `AUTO_CAL_ENABLE = 0` os vetores AutoCal congelam** (`PROVADO`, incidente 2026-08-12): buffers e `MUL_ACT` repetem o mesmo payload. O LN não permite medir o efeito na injeção porque os quatro toggles (seq 12615–12863) ocorreram em gasolina com `MUL_ACT = 1,0`.

## Hipótese de causa

- **H1 (`PROVADO` como comportamento declarado pelo fabricante; efeito físico não medido):** com `AUTO_CAL_ENABLE = 0` a ECU deixa de aplicar `MUL_ACT` à injeção de GNV e volta a usar apenas o Mapa K. "Pausar aquisição" é, portanto, "desligar a correção da Curva K". O próprio ProgBase pergunta antes de desmarcar o checkbox: *"Warning: if you disable autocalibration, it will cause no effects of the K correction values (blue line) on the gas injection computation. Are you really sure?"* (id `AUTOCALWARNINGDISABLE`, tabela de mensagens do `ProgBase.exe`). Ver a seção "Evidência do ProgBase.exe". O proprietário também observa que desmarcar o checkbox faz o ProgBase parar de adquirir pontos, coerente com o congelamento dos buffers do item 4 acima.
- **H2 (`INFERIDO`, consistente com o relato, não provada):** a ECU aplica `MUL_ACT` apenas enquanto existe sessão de comunicação ativa com AutoCal habilitado (procedimento de autocalibração em curso, como o ProgBase o usa); sem sessão (`00 01 01` ou cabo removido) usa só o Mapa K. É a única leitura que explica, com os dados acima, por que o cabo removido e o "Pausar" da notificação produzem o mesmo sintoma do `AUTO_CAL_ENABLE = 0`, já que nenhum deles altera a curva nem o flag.
- **Descartado como causa principal:** reversão RAM→EEPROM ao encerrar a sessão (a persistência observada no LN e nas sessões do app contradiz) e qualquer diferença entre `main` e a branch do APK nesse caminho (não há).
- **O que fecharia H1/H2:** o protocolo físico abaixo (classe 5, do proprietário), ou a desmontagem do consumidor de `AUTO_CAL_ENABLE`/do estado de sessão no firmware, fora do alcance do DUMP do PC.

## Correção proposta (decisão do proprietário)

1. **Operação imediata, sem código:** enquanto a consolidação não existir, a Curva K só tem efeito com AutoCal habilitado e, segundo o relato, com sessão ativa. Não usar **Pausar aquisição** como "pausar o app": ele desliga a correção na ECU. Para interromper o app sem tocar na ECU, usar o **Pausar** da notificação (fecha a sessão) ou deixar em segundo plano, sabendo que H2 prevê perda de efeito também aí.
2. **UI (congelada; exige decisão explícita):** o rótulo e a confirmação do toggle devem dizer o que o frame faz: "Desligar Auto Calibration — a ECU deixa de aplicar a Curva K até religar", com aviso em paridade com o do ProgBase. Nenhuma mudança foi feita aqui.
3. **Produto, paridade ProgBase — "Consolidar Curva K no Mapa K" (Export to K)**, manual e revisável no fluxo já existente do Mapa K (`preparar → revisar → confirmar → escrever célula a célula → readback`). Algoritmo **extraído da desmontagem do ProgBase** (`PROVADO` estaticamente; sem teste em ECU):
   - para cada linha `r` do Mapa K (as 12 linhas escritas pelo ProgBase), `x_r` é a referência de tempo de gasolina da linha; se `PETR_INJ_TBP[0] <= x_r <= PETR_INJ_TBP[último]`, `f_r` é a interpolação linear de `MUL_ACT` entre os dois pontos do eixo que cercam `x_r`; **linhas fora da faixa da curva não são alteradas**;
   - para cada uma das 12 colunas: `novo = clamp(trunc(atual × f_r), 0, 255)`. O arredondamento é **truncamento** (modo de arredondamento da FPU em corte) e a escrita satura em 0 e 255. Como `f` é um multiplicador puro, **não há valor neutro do mapa envolvido** (100 ou 128 não importam), o que fecha esse ponto aberto;
   - a célula é gravada pelo `TAebMatrix.SetData` normal; com a ECU conectada isso é a escrita `14 54 00 rr cc vv` (144 frames como no LN);
   - **ao final o ProgBase zera a curva na mesma operação:** a última instrução da rotina chama `MUL_ACT.ResetDefault(true)` (`TAebVector::ResetDefault`, slot `0x94` da vtable). Ela devolve cada ponto ao valor padrão do DFM (`MUL_ACT` = 1,0 nos 30 pontos) e grava o vetor inteiro na ECU. Assim o ganho não vale duas vezes. Um "Consolidar" do app deve manter essa garantia, com a escrita de 30 pontos como etapa visível no preview e no readback (o fluxo de reset da Curva K já existe);
   - a unidade, as dimensões e o estado final do reset estão provados (seção do EXE). O replay offline com dados reais foi feito (ver acima). Falta a validação física no carro. **Nenhuma escrita é implementada sem a decisão do proprietário.**
4. **`01 12 00` antes de `00 01 01`:** manter como lacuna documentada; não há evidência de que explique o sintoma e sua semântica continua `DESCONHECIDO`. Só promover a paridade depois de desmontar `TAebProtocol.Disconnect` (a exportação `TAebProtocol.CheckEEpromWrite` no DUMP sugere que o protocolo tem noção de gravação em EEPROM, mas nada liga isso a este frame).

## Pergunta do proprietário: o ProgBase tem "salvar/gravar a configuração" na ECU?

Resposta curta: **não existe um comando ou botão "gravar configuração na ECU" no ProgBase.** "Salva configurazione" grava um arquivo no PC; a volta para a ECU é "Carica nuova configurazione", que reescreve os objetos um a um. Fontes: DFMs `TFORMMAIN`, `TFORMCONFIG`, `TAUTOCALUI`, `TAUTOCALDM` da pasta DUMP (decodificados com `dfm2txt.py` da branch `evidence/progbase-original`), `ProgBase.exe.ExportFunctions.txt` e o LOG LN. O `ProgBase.exe`/`Dump.bin` (12,6 MB) não puderam ser baixados do Drive nesta sessão (limite de 10 MB do conector).

| O que o ProgBase oferece | O que faz de fato | Selo | Fonte |
|---|---|---|---|
| Menu "Salva configurazione..." / "Salva solo mappa in configurazione..." / "Salva configurazione su file hcf..." (`ActionSalvaConf`) | `SaveDialog` "Salva file di configurazione" → arquivo `.lec`/`.hcf` no PC via `TAeb*.WriteToFile/WriteToHCFFile`. Nenhum frame para a ECU. | PROVADO | DFM `TFORMMAIN`; exportações `TAebNumber/TAebVector/TAebMatrix@WriteToFile` |
| "Carica nuova configurazione" (`ActionApriConf`) | Lê o arquivo e envia cada objeto para a ECU (`SetDataInEcu`). Ordem e lotes `DESCONHECIDO` (lacuna L-10). | INFERIDO | nomes de menu + exportações |
| Checkbox *Abilita autocalibrazione* | `TAebCheckBox CheckAutoCalEnable`, `Value = AutoCalDM.AUTO_CAL_ENABLE`, `CheckedValue = 1`, `OnBeforeSetData = CheckAutoCalEnableBeforeSetData` (é aí que sai o aviso). O frame resultante é o mesmo `12 4A 01 xx` do toggle do app. | PROVADO | DFM `TAUTOCALUI` + LN seq 12615–12863 |
| "&Conferma" em `TFormConfig` | Só no grupo *Livello GAS* (referência do sensor de nível). | PROVADO | DFM `TFORMCONFIG` |
| "Azzera centralina e ritorna ai parametri di base" | Botão oculto (`Visible = False`) na aba Cambio; é reset de fábrica, não gravação. | PROVADO | DFM `TFORMCONFIG` |
| "Programmazione centralina" | Gravação de **firmware** (`PreparaProg/Progflash/Endflash/Cancflash`), não de configuração. | PROVADO | exportações `TAebProtocol` |
| `01 12 00 13` antes de `00 01 01` | Enviado pelo `TAebProtocol.Disconnect` **só** nas sessões em que houve escrita (LN: sessões seq 14–7550 e 7551–19891 terminam com ele; a sessão seq 5–10, sem escrita, termina só com `00 01 01`). ACK `53 00` em 10,5 ms e 10,3 ms, igual a `00 01 01` (10,3–11,2 ms), a `12 4A 01 xx` (11,4–12,4 ms) e às 144 escritas `14 54 00` (mediana 13,8 ms): nenhuma latência de gravação de EEPROM em lote. | PROVADO (ocorrência) / DESCONHECIDO (semântica) | LN; `tools/omegas/portmon_session_boundary_report.py` |
| `TAebProtocol.CheckEEpromWrite(int, int)` | Existe como método exportado; nenhum consumidor ligado a AutoCal ou ao `Disconnect` foi identificado sem o `Dump.bin`. | DESCONHECIDO | `ExportFunctions.txt` |

O que o arquivo de configuração carrega do AutoCal (DFM `TAUTOCALDM`, propriedade `FileSection = AutoCal`):

- gravados no `.lec`: `AUTO_CAL_ENABLE` (`AutoCalEnable`), `PETR_INJ_TBP` (`PetrInjTBp`), **`MUL_ACT` (`AutoCalMulAct`)**, `MNFLD_PRESS_THD`, `MaxAutomatch`, `NumAutomatchExecuted`, `LIMIT_PRESSURE_MIN/MAX`, `MAX_RPM_FOR_AUTOCAL`, `DISABLE_ACQ_BAND`, limiares `DELTA_*/DIFF_*`, `EN_CDN_T_THD`, `CALIBRATION_VAL_1`, `MODULE_VERSION`;
- **não** gravados (`ReadOnly = True`, sem `FileSection`): `PETR_INJ_TBUF`, `MNFLD_PRESS_BUF`, `NUM_BUF_UPD_PETR/GAS`, `*_GAS_PREV`, `ACQUIRED_ZONES_*`, `PETR/GAS_MNFLD_PRESS_RV`, `*_POINT_2DELETE`.

Ou seja: **a Curva K faz parte da "configuração" que o ProgBase salva e recarrega**, e recarregá-la significa reescrever `MUL_ACT` ponto a ponto (`14 61 01 ii lo hi`). Isso não cria nenhum estado novo na ECU além do que a escrita normal já cria.

Equivalente já existente no app (`PROVADO`, branch do APK): na tela **Curva K**, "Salvar curva atual" (`KFactorManager.saveCurrentBackup`: lê `MUL_ACT` + eixo da ECU e grava `MANUAL-<ts>-<hash>.json`) e "Restaurar backup" (`KFactorManager.prepareRestore` → `startBatchWrite`: preparar → revisar antes/depois → confirmar → escrita `14 61 01` por ponto → ACK → readback; bloqueia se o eixo `PETR_INJ_TBP` mudou ou se algum fator estiver fora da faixa segura). É a mesma função do `.lec` restrita à Curva K. Serve para **reaplicar uma curva boa** depois de um Reset/AutoMatch ruim; **não** altera se a ECU *aplica* a curva (H1/H2), portanto não é a correção do sintoma.

Conclusão para a pergunta: não há rotina de "gravar configuração" a acrescentar ao ciclo. O único frame de encerramento que o ProgBase envia e o app não (`01 12 00`) tem ocorrência provada e semântica desconhecida, e a persistência de `MUL_ACT` e do enable já foi observada sem ele (seção "O que a ECU faz com a curva", item 1). Ele entra no protocolo físico abaixo como P4 para fechar a lacuna, não como correção presumida.

## Evidência do ProgBase.exe (DUMP_1.zip) e manuais do fabricante

Fonte: `DUMP_1.zip` enviado pelo proprietário (sha256 `7d3e4e571bbb56f7c3aaa02d454662164f04e7c23824783629fba43c503f2196`), contendo `ProgBase.exe.Dump.bin` (sha256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`), `Stri4ngs.txt`, `Resources_StringTable.txt` e os DFMs. Reproduzir: `python3 tools/omegas/progbase_exe_messages.py ProgBase.exe.Dump.bin`. Desmontagem estática com capstone; nada executado.

**1. O que o fabricante diz que o checkbox faz.** O EXE embute uma tabela de traduções JSON (1.160 entradas, deslocamento `0x723970`). O handler `CheckAutoCalEnableBeforeSetData` (`0x0051A474`), ao tentar desmarcar, abre um diálogo Sim/Não com botão padrão Não (flags `0x124`) e cancela a mudança se a resposta não for Sim. O texto vem do campo preenchido com o id `AUTOCALWARNINGDISABLE` (índice `0x41F`, empilhado em `0x0051524B`):

> ita: *Attenzione: disattivando l'autocalibrazione si disabiliterà completamente l'effetto della correzione K (linea blu) sull'iniezione di GAS. Sei veramente sicuro?*
> eng: *Warning: if you disable autocalibration, it will cause no effects of the K correction values (blue line) on the gas injection computation. Are you really sure?*

A "linha azul" é o gráfico `ChartKLine`/`KLine` (a Curva K, `MUL_ACT`). Isto é a declaração do fabricante de H1: **desligar a autocalibração remove completamente o efeito da Curva K na injeção de GNV.** Nada no texto fala de sessão, cabo ou USB, então H2 continua sem apoio documental.

**2. Export to K, desmontado.** `ActionExportToKExecute` (`0x00518514`): pergunta *"Export on k-map?"* (`MESSEXPORTKTOMAP`, OK/Cancelar, padrão Cancelar), e se confirmado chama `0x00512624(AutoCalDM)`, que para cada linha do Mapa K faz o bracket (`0x00512708`), a interpolação linear (`0x0051280C`) e aplica o fator às células (`0x005127B0`: `fild célula; fmul f; _ftol; SetData`). O `_ftol` (`0x00A45A58`) força a palavra de controle da FPU a truncar, e o setter de célula (`0x0042ECD8`) satura em 0..255 e, com o flag de conexão ligado, escreve na ECU. **Ao fim do laço a rotina chama `MUL_ACT.ResetDefault(true)`** (vtable de `TAebVector`, slot `0x94`, `0x0097B58C`): copia o `DefaultValue` de cada ponto (1,0) para o vetor e, com o argumento verdadeiro, grava o vetor inteiro na ECU (`SetDataInEcu`). Depois redesenha o gráfico (`0x00516044`, `0x005162F8`) e dispara o evento do formulário. O handler do Reset K (`0x0051A070`) é uma ação separada que faz o mesmo por ponto com `SetDouble(1.0)`.

**Correção registrada:** uma versão anterior deste doc dizia que o Export to K não zerava `MUL_ACT`. Estava errado: eu não tinha resolvido a chamada virtual final. A leitura da vtable mostra que ela é o reset.

**Pontos que estavam em aberto e foram fechados lendo o EXE e o LOG:**

| Ponto | Resultado | Selo |
|---|---|---|
| Unidade da referência de linha | `x_ms = bruto × BASE_TEMPI_GLOBALE × 1e-6` (getter `0x0042EE14`; a escala vem de `0x00433FFC`). No LN, `BASE_TEMPI_GLOBALE` = `0x0A00` = 2560 e os 12 brutos de `TEMPI_PER_K` (781, 977, 1172, 1367, 1758, 2344, 3125, 3906, 4687, 5469, 6250, 7031) dão 2,00 / 2,50 / 3,00 / 3,50 / 4,50 / 6,00 / 8,00 / 10,0 / 12,0 / 14,0 / 16,0 / 18,0 ms, as mesmas faixas de `KMapPhysicalAxes`. O eixo `PETR_INJ_TBP` está em ms (bruto/512) e tem 30 pontos de 0,5 a 22,0 ms, estritamente crescente. Mesma unidade, e as 12 linhas caem dentro da faixa. | PROVADO (código + LN) |
| Dimensões do laço | O construtor do objeto do Mapa K fixa 12 linhas e 12 colunas (`mov [+0xCF8], 0xC` e `mov [+0xCFC], 0xC` em `0x0042AE4C/58`). O DFM do `MAP_K` declara 13 linhas por 12 colunas; a 13ª não entra no laço. | PROVADO (código + DFM) |
| Borda `x1 == x0` | `0x0051280C` devolve 0,0 se os dois pontos do eixo coincidem. O eixo da ECU é estritamente crescente, então não ocorre aqui. O app deve recusar um eixo com pontos repetidos em vez de zerar a linha. | PROVADO (código) |
| Frame do reset final | Com `RowIndex = -1` e o flag de escrita por elemento desligado (padrão do construtor, `0x00978898`), `TAebVector::SetDataInEcu(int*)` cai em `TAebProtocol::SetVector`: um frame de vetor longo para os 30 pontos, não 30 frames indexados. Pelo código do `SetVector` o formato é `37 61 3E 01` + 60 bytes de dado + checksum (opcode `0x30|min(len,7)`, SC lo, `len+1` quando `len ≥ 7`, SC hi, dados em little-endian). O formato longo nunca apareceu no LN. | INFERIDO (código, sem captura) |
| Efeito do reset | Estado final igual ao do Reset K por ponto (`14 61 01 ii 00 40`, 30 vezes): `MUL_ACT = 1,0`. O app pode usar o reset por ponto que já existe e já tem ACK e readback; o frame do ProgBase não precisa ser replicado. | PROVADO (estado final) |

**Replay offline com dados reais (feito).** `python3 tools/omegas/export_to_k_replay.py PortmonLOGNOVO.LOG` aplica o algoritmo ao Mapa K e à `MUL_ACT` reais do log, sem tocar na ECU. Resultado no mapa original do carro (leitura seq 36, valores 162 a 181) com a curva aprendida pelo carro (seq 342, fatores 0,788 a 1,337):

| Linha | ref (ms) | fator | exemplo (1ª coluna) |
|---|---|---|---|
| 0 | 2,00 | 0,808 | 162 → 130 |
| 1 | 2,50 | 0,788 | 162 → 127 |
| 4 | 4,50 | 1,112 | 167 → 185 |
| 5 | 6,00 | 1,301 | 166 → 215 |
| 6 | 8,00 | 1,255 | 172 → 215 |
| 8 | 12,0 | 0,987 | 168 → 165 |
| 11 | 18,0 | 1,087 | 165 → 179 |

- As 12 linhas caem dentro do eixo, as 144 células mudam, nenhuma satura em 0 ou 255, e a 13ª linha fica intacta.
- A interpolação confere com a leitura direta da curva (por exemplo, 2,501 ms fica entre os pontos de 2,5 e 3,0 ms e dá 0,788).
- Divergência entre ponto flutuante e racional exato: 0 células. O risco do x87 do ProgBase contra o `double` do Python não aparece nestes dados.
- Um segundo export com a curva já em 1,0 não altera nenhuma célula, então o reset final torna a operação idempotente.
- Com a curva neutra (seq 1496, depois do Reset All) o mapa não muda.
- Estresse com as 9 capturas de curva do app (4 curvas distintas, fatores de 0,865 a 1,500): nenhuma saturação, nenhuma linha pulada.
- Os mesmos 144 cálculos rodam nas três leituras do Mapa K do log (162/165, 149 e 166). A diferença entre as leituras é o problema L-07, não do algoritmo.

**O que o replay não prova:** que o resultado seja o que a ECU aplica como correção física. Isso só se fecha no carro. O Export to K nunca foi executado (código morto), então não há resultado do ProgBase para comparar. Por isso a escrita no app continua dependendo da decisão do proprietário e de uma validação física.

**3. Alcançabilidade.** Nenhuma chamada direta ao handler, nenhuma referência ao campo da ação (`+0x324`) no código do `TAutoCalUI` e nenhum menu ou botão no DFM: o Export to K não é executável pela interface desta build.

**4. Salvar/carregar configuração.** O texto `AUTOCALWARNINGDOWNLOAD` (*"autocalibration parameters in current configuration are not compatible with ecu firmware. Default configuration parameters will be programmed"*) mostra que carregar um `.lec` com parâmetros de AutoCal incompatíveis faz a ECU receber os valores padrão, ou seja, o carregamento escreve `MUL_ACT`/enable e não há passo de gravação separado. Isso reforça a conclusão da seção anterior: não existe comando "gravar na ECU".

**5. Mensagens úteis para a UI (tradução do próprio fabricante).** `AUTOCALFORCEFINISH`: *Terminate autocalibration procedure?* · `AUTOCALRESETKFACTOR`: *Reset correction factors?* · `AUTOCALSWITCHTOGAS`: *Switch to gas to acquire gas points* · `CHECKAUTOCALENABLE`: *Enable autocalibration*. O texto do aviso (item 1) é o que o toggle do app deveria espelhar se o proprietário decidir mudar a UI (hoje congelada).

**Manuais públicos da Landi Renzo/AEB.** Localizados na busca, mas ilegíveis nesta sessão (o proxy de rede bloqueia landi.pl, lpgtech.ua, hybridsupply.uk/.de, aeb.it, landirenzo.com, scribd.com, pdfcoffee.com, coursehero.com). Continuam úteis para confirmar o efeito físico e a definição do coeficiente K (o texto público fala em 128 como mesma injeção nos dois combustíveis), mas **a pergunta central, o que o checkbox faz, já está respondida pelo próprio EXE**. Se o proprietário quiser o cruzamento, basta colocar o PDF na pasta DUMP (abaixo de 10 MB): *Software manual LR Omegas 2.16.4 C*, boletim *LR Omegas 4.2.0.85*, *Manuale software 4.1.0 S*, *OMEGAS PLUS software manual*, *AEB software manual*.

## Protocolo de validação física (classe 5, barato, pelo proprietário)

Registrar tudo com a sessão do app gravando (`telemetry` já traz `gas_ms_diagnostic`, `petrol_ms`, `rpm`, `load_bar`).

- **P1 (H1):** em GNV, cabo ligado, AutoCal ligado, manter uma faixa estável (ex.: ~2000 rpm, ~0,5 bar) e anotar `gas_ms/petrol_ms`. Clicar **Pausar aquisição** (confirmar readback `AUTO_CAL_ENABLE = 0`). Repetir a mesma faixa. Razão mudou → H1 confirmada; igual → H1 refutada.
- **P2 (H2):** religar AutoCal. Na mesma faixa, anotar a razão. Clicar **Pausar** da notificação (sessão fechada com `00 01 01`, nada de AutoCal). Repetir a faixa lendo o consumo pelo instrumento do carro ou reconectando logo depois. Repetir com o cabo removido. Razão mudou nos dois casos → H2 confirmada.
- **P3 (persistência):** ao reconectar após P2, pedir snapshot e conferir `MUL_ACT`, `AUTO_CAL_ENABLE` e contador iguais aos anteriores. Diferentes → reabrir a hipótese de reversão.
- **P4 (lacuna `01 12 00`):** com a curva não neutra lida no snapshot inicial, fechar a sessão pelo **Pausar** da notificação (só `00 01 01`), desligar e religar a ignição, reconectar e reler `MUL_ACT` e `AUTO_CAL_ENABLE`. Igual ao snapshot ⇒ `01 12 00` não é necessário para persistir a curva nem o enable; diferente ⇒ reabrir a hipótese de commit e capturar uma desconexão do ProgBase após escrita de curva para comparar.
- Nenhum desses passos escreve na ECU além do toggle já existente; nenhum resultado aqui pode ser alegado como `PROVADO` sem a sessão gravada.

## Bônus: evidência nova para a lacuna L-07 (Mapa K lido ≠ escrito)

Na fiação, entre as três leituras do Mapa K, o ProgBase escreveu `100` em todas as 144 células e leu depois `149` e, na terceira, `166`; a linha `0C` acompanhou `137 → 186 → 203`. Os deslocamentos são **aditivos e uniformes**: `+49` e `+17` em todas as células e também na linha `0C`. No primeiro intervalo há **quatro** execuções da calibração clássica (`00 13` em 1903, 2097, 2252, 2992, com polls `00 14` percorrendo `sel` 1…9) e nenhuma escrita `14 54 00`; no segundo, **duas** (`15550`, `15841`) e nenhuma escrita. `INFERIDO`: a "Calibrazione" clássica (`TFormCalibra`) soma um deslocamento global ao Mapa K; não é `01 12 00`, nem `Reset All`, nem AutoMatch (que só começa em 24597). O que provaria: captura com uma única calibração clássica isolada e releitura do mapa na mesma sessão.

## Reprodução

```
# fiação do ProgBase (LOG dentro de PortmonLOGNOVO.zip, sha256 1e9c75fadc4eb6092502b60454b260a6a32afb9dbf57487e983e504758534a99)
python3 tools/omegas/portmon_session_boundary_report.py PortmonLOGNOVO.LOG
python3 tools/omegas/portmon_session_boundary_report.py PortmonLOGNOVO.LOG --json > boundary-report.json
# mensagens do fabricante e índices empilhados pelo TAutoCalUI (ProgBase.exe.Dump.bin da pasta DUMP)
python3 tools/omegas/progbase_exe_messages.py ProgBase.exe.Dump.bin
# replay offline do Export to K com o Mapa K e a Curva K reais do log
python3 tools/omegas/export_to_k_replay.py PortmonLOGNOVO.LOG
```

Sessões do app usadas (Drive do proprietário, somente leitura): `events_0001.jsonl (31).json` (sessão de 01/10 18:49 UTC, 1 snapshot nativo) e `session_2026-10-02_20-57-58_ec1b2fd9_parte_0002/0003/0008/0029/0030/0035/0055/0100/0105/0107.zip` (summary por parte: `actionReceipts` 0→1→3, `autoMatchExecuted` 2→3→0→1→3, `autoCalEnabled` sempre 1; `MUL_ACT` extraído dos eventos `autocal_native_snapshot`). O que extrair de cada uma está descrito nos itens 1 e 2 da seção "O que a ECU faz com a curva".

## Limites

- Sem ECU física nesta análise; H1 e H2 são inferidas e o protocolo acima é o que as fecha.
- Nenhuma escrita em ECU, nenhuma mudança de UI, nenhuma alteração de `STATUS.md` ou de prova `PROVEN`.
- `PHYSICAL_VALIDATION_CLAIMED=false`.
