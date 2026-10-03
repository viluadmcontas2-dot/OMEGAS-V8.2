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
3. **O ProgBase prevê um passo de consolidação que o app não tem** (`PROVADO` existência / `EVIDENCE_GATED` semântica): `ActionExportToKExecute` @ `0x00518514` — "Export to K". É matemática no PC (`0x00512708` bracket + `0x0051280C` interpolação linear sobre `PETR_INJ_TBP`) seguida de escrita do Mapa K pelo caminho normal `14 54 00 rr cc vv`. Em `docs/autocal/progbase-host-parity-v0.md` essa operação está listada como *EVIDENCE_GATED: no mutation promoted until exact host semantics are closed*. **Correção (DFM `TAUTOCALUI` do DUMP, decodificado nesta análise):** a `TAction ActionExportToK` (caption `ExportToK...`) existe na `TActionList AzioniAutoCal`, mas **não está ligada a nenhum botão nem item de menu** (`MenuAutoCal`, `MenuChartK`, `MenuChartData`, `MenuChartPoint` e os botões do painel só referenciam Reset/AutoMatch/Refs/Export-to-file/Settings/Colors/FinishAutocal). `ActionFinishAutocal` está em `MenuAutoCal`, porém com `Enabled = False` no DFM. Logo, nesta build (4.2.0.6) o proprietário **não executou** um Export to K pela UI; ele continua sendo o candidato natural de consolidação, não um passo que faltou reproduzir.
4. **Durante o período com `AUTO_CAL_ENABLE = 0` os vetores AutoCal congelam** (`PROVADO`, incidente 2026-08-12): buffers e `MUL_ACT` repetem o mesmo payload. O LN não permite medir o efeito na injeção porque os quatro toggles (seq 12615–12863) ocorreram em gasolina com `MUL_ACT = 1,0`.

## Hipótese de causa

- **H1 (`INFERIDO`, forte):** com `AUTO_CAL_ENABLE = 0` a ECU deixa de aplicar `MUL_ACT` à injeção de GNV e volta a usar apenas o Mapa K. "Pausar aquisição" é, portanto, "desligar a correção da Curva K". Cadeia: é o módulo AutoCal quem possui a curva (item 2); o módulo é desligado pelo flag (item 1 da tabela); o próprio ProgBase avisa ao desmarcar o checkbox e oferece Export to K para tornar o ganho permanente (item 3).
- **H2 (`INFERIDO`, consistente com o relato, não provada):** a ECU aplica `MUL_ACT` apenas enquanto existe sessão de comunicação ativa com AutoCal habilitado (procedimento de autocalibração em curso, como o ProgBase o usa); sem sessão (`00 01 01` ou cabo removido) usa só o Mapa K. É a única leitura que explica, com os dados acima, por que o cabo removido e o "Pausar" da notificação produzem o mesmo sintoma do `AUTO_CAL_ENABLE = 0`, já que nenhum deles altera a curva nem o flag.
- **Descartado como causa principal:** reversão RAM→EEPROM ao encerrar a sessão (a persistência observada no LN e nas sessões do app contradiz) e qualquer diferença entre `main` e a branch do APK nesse caminho (não há).
- **O que fecharia H1/H2:** o protocolo físico abaixo (classe 5, do proprietário), ou a desmontagem do consumidor de `AUTO_CAL_ENABLE`/do estado de sessão no firmware, fora do alcance do DUMP do PC.

## Correção proposta (decisão do proprietário)

1. **Operação imediata, sem código:** enquanto a consolidação não existir, a Curva K só tem efeito com AutoCal habilitado e, segundo o relato, com sessão ativa. Não usar **Pausar aquisição** como "pausar o app": ele desliga a correção na ECU. Para interromper o app sem tocar na ECU, usar o **Pausar** da notificação (fecha a sessão) ou deixar em segundo plano, sabendo que H2 prevê perda de efeito também aí.
2. **UI (congelada; exige decisão explícita):** o rótulo e a confirmação do toggle devem dizer o que o frame faz: "Desligar Auto Calibration — a ECU deixa de aplicar a Curva K até religar", com aviso em paridade com o do ProgBase. Nenhuma mudança foi feita aqui.
3. **Produto, paridade ProgBase — "Consolidar Curva K no Mapa K" (Export to K)**, manual e revisável no fluxo já existente do Mapa K (`preparar → revisar → confirmar → escrever célula a célula → readback`), seguido do já existente **Resetar Curva K para 1.0** para a correção não ser aplicada duas vezes enquanto o AutoCal estiver ligado. Algoritmo candidato (`INFERIDO`; **não implementar escrita antes de fechar a semântica**):
   - para cada linha `r` do Mapa K, `Tpet_r` vem de `KMapPhysicalAxes.petrolBins()`; `fator_r = interp(MUL_ACT, PETR_INJ_TBP, Tpet_r)` com a mesma rotina de bracket + interpolação linear de `KFactorProtocol.interpolateFactor`;
   - para cada célula `(r, c)`: `novo = clamp(round(atual × fator_r), 0, 255)`;
   - pontos em aberto: composição multiplicativa com neutro 100 (`curvas-mapas.md` 5.3 marca o neutro como `INFERIDO`), arredondamento do ProgBase e se a linha `0C` participa. Prova necessária: desmontagem de `0x00518514` no `ProgBase.exe.Dump.bin` **ou** captura Portmon de um Export to K real (Mapa K antes, frames `14 54 00`, Mapa K depois, `MUL_ACT` antes/depois).
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

## Evidência documental do fabricante (a obter)

Observação do proprietário (2026-10-03): no ProgBase, desmarcar *Abilita autocalibrazione* também **para a aquisição de pontos**. Isso bate com o item 4 da seção "O que a ECU faz com a curva" (buffers e `MUL_ACT` congelam com `AUTO_CAL_ENABLE = 0`) e com H1: o flag desliga o módulo inteiro, aquisição e correção juntas.

A busca por manuais da Landi Renzo/AEB nesta sessão localizou as fontes abaixo, mas **nenhuma pôde ser lida**: o proxy de rede do ambiente bloqueia os domínios (landi.pl, lpgtech.ua, hybridsupply.uk/.de, aeb.it, landirenzo.com, scribd.com, pdfcoffee.com, coursehero.com) e o Drive do proprietário não contém manuais do Omegas. Caminho operacional: baixar o PDF e colocá-lo na pasta DUMP do Drive (abaixo de 10 MB) para leitura pelo conector.

| Fonte | O que procurar |
|---|---|
| Landi Renzo, *Software manual LR Omegas* 2.16.4 C (landi.pl `MANUALE_SW_2164C_gb1.pdf`) | capítulo de autocalibração: o que acontece ao desabilitar; se a correção aprendida permanece aplicada; procedimento de "K insertion"; salvar/carregar configuração |
| Landi Renzo, boletim *LR Omegas software program version 4.2.0.85* (hybridsupply.uk `hsd-49504-...pdf`) | mesma geração 4.2.0.x do ProgBase do DUMP; "linear autocalibration", acquisition, automatch, Export to K |
| Landi Renzo, *Manuale software 4.1.0 S* (coursehero) e *Software manual for LRE 184/188/194/198/EVO* (pdfcoffee) | idem, versões 4.x |
| Landi Renzo, *OMEGAS PLUS software manual* (scribd `BLUE-GB`) e *User Manual Omegas Software* (scribd) | definição do coeficiente K (128 = mesmo tempo gasolina/gás), mapa, autocalibração |
| AEB, *AEB software manual* (lpgautosupplies.co.uk) e folha *MP48 OBD* (aeb.it) | lado AEB da mesma família MP48: autocalibração, "self-mapping", memorização |

Evidência mais barata e mais direta: **o texto do aviso que o próprio ProgBase mostra ao desmarcar o checkbox** (`CheckAutoCalEnableBeforeSetData`). Ele é a declaração do fabricante sobre o que o flag faz e não está nos DFMs (é resourcestring do EXE; o `Strings` do DUMP tem 11,8 MB e não baixa). Um print da caixa de diálogo, anexado ao Drive, fecha isso sem desmontagem.

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
```

Sessões do app usadas (Drive do proprietário, somente leitura): `events_0001.jsonl (31).json` (sessão de 01/10 18:49 UTC, 1 snapshot nativo) e `session_2026-10-02_20-57-58_ec1b2fd9_parte_0002/0003/0008/0029/0030/0035/0055/0100/0105/0107.zip` (summary por parte: `actionReceipts` 0→1→3, `autoMatchExecuted` 2→3→0→1→3, `autoCalEnabled` sempre 1; `MUL_ACT` extraído dos eventos `autocal_native_snapshot`). O que extrair de cada uma está descrito nos itens 1 e 2 da seção "O que a ECU faz com a curva".

## Limites

- Sem ECU física nesta análise; H1 e H2 são inferidas e o protocolo acima é o que as fecha.
- Nenhuma escrita em ECU, nenhuma mudança de UI, nenhuma alteração de `STATUS.md` ou de prova `PROVEN`.
- `PHYSICAL_VALIDATION_CLAIMED=false`.
