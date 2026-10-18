# As caixas de flag do ProgBase original e o estado delas na ECU do dono (2026-10-18)

## Resposta curta
- O ProgBase guarda as caixas de configuração em **4 vetores** da ECU: `FLAG_CONF1` (código 3), `FLAG_CONF2` (192, ou `FLAG_CONF2_LR` 193), `FLAG_CONF3` (250) e `MASK_FUNCTION` (194, só leitura). Cada caixa é **um bit** de uma palavra de 16 bits.
- A tabela completa (vetor, palavra, máscara, caso, caixa da tela, rótulo) sai do **próprio .exe** por `tools/emulacao/flags_do_original.py` e está em `docs/evidence/flags-do-original.json` (`--check` confere que o JSON versionado bate com o .exe sha256 `8a2d297c…`).
- **Anti-stallo** = `FLAG_CONF3` palavra 0 máscara `0x0020` (caso 4). **Abilita progressioni** = `FLAG_CONF3` palavra 0 máscara `0x0010` (caso 3), **o mesmo bit** da caixa "Cambio con sovrapposizione" (as duas caixas mexem no mesmo bit). [PROVADO]
- Na ECU do dono (log Portmon de 12/08/2026): `FLAG_CONF1 = 0x2486 0x1051`, `FLAG_CONF2 = 0x0001 0x0000`, `FLAG_CONF3 = 0x0000 0x0000` (tudo desligado, inclusive anti-stallo e progressões), `FLAG_CONF2_LR` não foi lido. [PROVADO no fio]

## Como foi tirado (conferível)
1. **Onde ficam os vetores** — tabela de campos RTTI do `TStreamDati` (offset u32, tipo u16, nome): `FLAG_CONF1` +0x90, `FLAG_CONF2` +0x2E8, `MASK_FUNCTION` +0x2EC, `FLAG_CONF3` +0x39C, `FLAG_CONF2_LR` +0x474. [PROVADO]
2. **Getter/setter de cada vetor** — despachos `jmp [edx*4+tabela]` com `test byte [obj+C],máscara` (ler) e `or/and` (escrever) na cópia local C do vetor: [PROVADO]
   - `FLAG_CONF1`: ler 0x42CFB8, escrever 0x42D300 (36 casos, cópia +0x2984); envia `FLAG_CONF1` se `[obj+0x80E]≠0`.
   - `FLAG_CONF2`: ler 0x42DF14, escrever 0x42E16C (30 casos, cópia +0x298C); envia `FLAG_CONF2_LR` se `[obj+0x80D]≠0`, senão `FLAG_CONF2` (ambos só se `[obj+0x80E]≠0`).
   - `FLAG_CONF3`: ler 0x42E754, escrever 0x42E8D8 (19 casos, cópia +0x2994); envia `FLAG_CONF3` só se `[obj+0x80D]=0` e `[obj+0x80E]≠0`.
   - `MASK_FUNCTION`: 11 funções-predicado (0x434690…0x4347D4, 0x434B7C) que testam um **byte** da cópia +0x29EC, só se `[obj+0x2A64]≠0` (máscara carregada). Nada escreve nela: é "o que esta ECU sabe fazer".
   - Envio: `TAebVector.SetValues` = 0x978F8C (achado como o call que recebe o vetor). Em todas as 87 linhas de flag, ler e escrever usam o mesmo bit (`coerente: true`).
3. **Qual caixa usa cada caso** — cada `call` ao getter/setter com `edx = caso`; a caixa é o campo lido com `GetChecked` (vtable +0x104) ou escrito com `SetChecked` (+0x108), slots tirados da RTTI da propriedade `Checked`; `Enabled` (+0x84) e `Visible` (0x984438) idem. Nome do campo pela RTTI da classe dona da rotina (método publicado, ex. `TFormConfig.CheckAntiStalloClick`); rótulo pelo DFM e `i18n_all.json`. [PROVADO para caixas; DERIVADO para "dependentes" e rotinas sem nome]
4. **Estado na ECU** — `python3 tools/sessoes/portmon_ler_vetor.py PortmonLOGNOVO.LOG <código>`; cada vetor veio igual nas 3 leituras do log, checksum ok. [PROVADO]

## Condição `[obj+0x80D]` (o que ainda não sei)
Alguns casos de `FLAG_CONF1` (28–35) só existem quando `[obj+0x80D]≠0`; nesse modo o ProgBase grava em `FLAG_CONF2_LR` e **não envia** `FLAG_CONF3`. O que é 0x80D é **DESCONHECIDO** (provavelmente um tipo/família de ECU). Neste log o ProgBase leu 192 e nunca 193, o que combina com `0x80D = 0` na ECU do dono [INFERIDO]; por isso essas caixas aparecem como "inativo se 0x80D=0".

## Tabela (uma linha por bit)
Colunas: vetor · código · palavra · máscara · caso do getter/setter [condição] · caixa da tela "rótulo ita" / "rótulo eng" · controles que dependem do bit (propriedade) · estado na ECU do dono · marca.
Marca: **PROVADO** = bit e caixa ligados no código; **DERIVADO** = bit usado só por lógica do programa (habilita/esconde outra coisa) sem caixa própria; **DESCONHECIDO** = o programa tem o caso, mas nenhuma chamada achada usa.
Notas: em `MASK_FUNCTION` a "palavra" é o índice do **byte** (o estado por byte do fio é INFERIDO como igual ao da cópia local); os 4 combos de `FLAG_CONF1` usam o mesmo slot virtual do `Checked`, mas para combo isso é outra propriedade (INFERIDO: ItemIndex) — o bit é PROVADO, o significado de cada valor do combo não. "Rótulo eng" = "?" quando o i18n não tem tradução.

| vetor | cód. | palavra | máscara | caso | caixa (ita / eng) | dependentes | ECU do dono | marca |
|---|---|---|---|---|---|---|---|---|
| FLAG_CONF1 | 3 | 0 | 0x0002 | 1 | ComboCarburante "?" / "?" | — | ligado | PROVADO |
| FLAG_CONF1 | 3 | 0 | 0x0008 | 12 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF1 | 3 | 0 | 0x0010 | 14 | ComboMisuratorePressione "?" / "?" | — | desligado | PROVADO |
| FLAG_CONF1 | 3 | 0 | 0x0020 | 27 | CheckSequenzaInj "Abilitazione manuale sequenza iniezione" / "Enable manual injection sequence" | CheckCambioConSovrapp (Enabled, invertido) | desligado | PROVADO |
| FLAG_CONF1 | 3 | 0 | 0x0040 | 18 | — | — | desligado | DERIVADO |
| FLAG_CONF1 | 3 | 0 | 0x0080 | 19 | CheckElettrovalvole "Elettrovalvole gas aperte con anticipo" / "Gas electrovalves opened in advance" | — | ligado | PROVADO |
| FLAG_CONF1 | 3 | 0 | 0x0100 | 0 | — | — | desligado | DERIVADO |
| FLAG_CONF1 | 3 | 0 | 0x0200 | 2 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF1 | 3 | 0 | 0x0400 | 3 | — | — | ligado | DESCONHECIDO |
| FLAG_CONF1 | 3 | 0 | 0x0800 | 4 | — | — | desligado | DERIVADO |
| FLAG_CONF1 | 3 | 0 | 0x1000 | 5 | — | — | desligado | DERIVADO |
| FLAG_CONF1 | 3 | 0 | 0x2000 | 13 | ComboPressioneMisurata "?" / "?" | — | ligado | PROVADO |
| FLAG_CONF1 | 3 | 0 | 0x4000 | 6 | — | — | desligado | DERIVADO |
| FLAG_CONF1 | 3 | 0 | 0x8000 | 7 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF1 | 3 | 0 | 0x8000 | 28 [obj+0x80D!=0] | CheckInibisciEmulazione "Disattiva emulazione per giri bassi" / "Disable emulation at low rpm" | — | desligado (inativo se 0x80D=0) | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x0001 | 8 | CheckTempAcqua "Abilita cambio con temperatura acqua" / "?" | — | ligado | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x0002 | 9 | CheckTempGas "Abilita cambio con temperatura gas" / "?" | LabelTempCambio (Visible) | desligado | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x0004 | 10 | CheckTempMotore "Utilizza sensore di temperatura motore della vettura" / "?" | — | desligado | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x0008 | 11 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF1 | 3 | 1 | 0x0008 | 29 [obj+0x80D!=0] | CheckConsumometro "Attiva consumometro (solo GPL)" / "Enable consume meter (only LPG)" | — | desligado (inativo se 0x80D=0) | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x0010 | 15 | MenuControlloIniettori "Abilita controllo iniettori" / "?" | — | ligado | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x0020 | 16 | ComboTurbo "?" / "?" | — | desligado | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x0040 | 30 [obj+0x80D!=0] | CheckBassaPressione "Ritorno automatico con bassa pressione impostabile" / "Automatic change back with low pressure setting possible" | — | ligado (inativo se 0x80D=0) | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x0080 | 20 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF1 | 3 | 1 | 0x0080 | 31 [obj+0x80D!=0] | CheckMapConfigurato "Map configurato correttamente" / "Map correctly configured" | — | desligado (inativo se 0x80D=0) | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x0080 | 33 [obj+0x80D==0] | — | — | desligado | DERIVADO |
| FLAG_CONF1 | 3 | 1 | 0x0100 | 21 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF1 | 3 | 1 | 0x0100 | 32 [obj+0x80D!=0] | CheckPassPRail "Ritorno automatico a benzina con la pressione del rail" / "?" | — | desligado (inativo se 0x80D=0) | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x0100 | 34 [obj+0x80D==0] | — | — | desligado | DERIVADO |
| FLAG_CONF1 | 3 | 1 | 0x0200 | 33 [obj+0x80D!=0] | — | — | desligado (inativo se 0x80D=0) | DERIVADO |
| FLAG_CONF1 | 3 | 1 | 0x0400 | 17 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF1 | 3 | 1 | 0x0400 | 34 [obj+0x80D!=0] | — | — | desligado (inativo se 0x80D=0) | DERIVADO |
| FLAG_CONF1 | 3 | 1 | 0x0800 | 26 | CheckAlimentazione "Utilizzo filo EV2 per STAP" / "Use of wire EV2 for STAP" | — | desligado | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x0800 | 35 [obj+0x80D!=0] | CheckAdattaBenzina "Adattività con mappa benzina" / "Adaptivity with petrol map" | PanelAdatta (Visible) | desligado (inativo se 0x80D=0) | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x1000 | 22 | CheckIstantaneo "Passaggio benzina gas in modo sequenziale" / "?" | — | ligado | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x2000 | 23 | CheckParzializza "Contributo automatico benzina ad alti giri" / "?" | — | desligado | PROVADO |
| FLAG_CONF1 | 3 | 1 | 0x4000 | 24 | — | — | desligado | DERIVADO |
| FLAG_CONF1 | 3 | 1 | 0x8000 | 25 | CheckAdattativita "Adattività OBD" / "OBD adaptivity" | PanelAdattaObd (Visible) | desligado | PROVADO |
| FLAG_CONF2 | 192 | 0 | 0x0001 | 0 | CheckTagliaExtraIniettate "Taglio extrainiettate" / "?" | — | ligado | PROVADO |
| FLAG_CONF2 | 192 | 0 | 0x0002 | 1 | CheckPartenzaGasCaldo "Partenza a gas a motore caldo" / "Start on gas with hot engine" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 0 | 0x0004 | 2 | CheckValvetronik "Valvetronik" / "Valvetronik" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 0 | 0x0008 | 3 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF2 | 192 | 0 | 0x0010 | 4 | CheckAzzeraErroriObd "Reset errori OBD" / "?" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 0 | 0x0020 | 5 | CheckDisabilitaCommutatore "Disabilita commutatore" / "?" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 0 | 0x0040 | 6 | CheckRiscaldamento "Riscaldamento iniettori" / "?" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 0 | 0x0080 | 7 | CheckAzzeraBlackListObd "Reset selettivo errori OBD" / "?" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 0 | 0x0100 | 8 | CheckSpegniBuzzer "Buzzer a tempo" / "?" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 0 | 0x0200 | 9 | CheckSplitFuel "Abilita split fuel" / "?" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 0 | 0x0400 | 10 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF2 | 192 | 0 | 0x0800 | 11 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF2 | 192 | 0 | 0x1000 | 12 | CheckStartAndStop "Start && Stop" / "Start && Stop" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 0 | 0x2000 | 13 | CheckSwitchOnDiagnosis "?" / "?" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 0 | 0x4000 | 14 | CheckContributi "Enable petrol addition map" / "?" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 0 | 0x8000 | 15 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF2 | 192 | 1 | 0x0001 | 21 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF2 | 192 | 1 | 0x0002 | 22 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF2 | 192 | 1 | 0x0004 | 23 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF2 | 192 | 1 | 0x0008 | 24 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF2 | 192 | 1 | 0x0010 | 25 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF2 | 192 | 1 | 0x0020 | 26 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF2 | 192 | 1 | 0x0040 | 27 | — | — | desligado | DERIVADO |
| FLAG_CONF2 | 192 | 1 | 0x0080 | 28 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF2 | 192 | 1 | 0x0100 | 16 | CheckFlexFuel "Flex fuel" / "Flex fuel" | RadioMapAlcol (Visible) | desligado | PROVADO |
| FLAG_CONF2 | 192 | 1 | 0x0200 | 17 | CheckboxPreheatOnlyAfterSleepMode "Riscaldamento iniettori solo dopo sleep mode" / "?" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 1 | 0x0400 | 18 | CheckIncrementoMaxCorrente "Incrementa max corrente al cambio gas" / "?" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 1 | 0x0800 | 19 | CheckboxPreheatAsyncMode "Riscaldamento iniettori asincrono" / "?" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 1 | 0x2000 | 29 | CheckInjAcquisition "Sequenza di iniezione manuale" / "?" | — | desligado | PROVADO |
| FLAG_CONF2 | 192 | 1 | 0x4000 | 20 | CheckFlashLube "Abilita diagnosi livello lubrificante " / "?" | — | desligado | PROVADO |
| FLAG_CONF3 | 250 | 0 | 0x0002 | 0 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF3 | 250 | 0 | 0x0004 | 1 | CheckTaglioPompaBenzina "Taglio pompa benzina" / "Petrol pump cutting" | CheckContributi (Enabled, invertido), CheckProgressioni (Enabled, invertido) | desligado | PROVADO |
| FLAG_CONF3 | 250 | 0 | 0x0008 | 2 | CheckPetrolObdConnection "Connessione obd in funzionameno benzina" / "?" | — | desligado | PROVADO |
| FLAG_CONF3 | 250 | 0 | 0x0010 | 3 | CheckCambioConSovrapp "Cambio con sovrapposizione" / "?", CheckProgressioni "Abilita progressioni" / "?" | EditParamProgress0 (Visible) | desligado | PROVADO |
| FLAG_CONF3 | 250 | 0 | 0x0020 | 4 | CheckAntiStallo "Abilitazione anti-stallo" / "Enable un-stalling strategy" | — | desligado | PROVADO |
| FLAG_CONF3 | 250 | 0 | 0x0040 | 12 | CheckDeleteErrorMotoreSpento "Cancellazione errori OBD a motore spento" / "?" | — | desligado | PROVADO |
| FLAG_CONF3 | 250 | 0 | 0x0080 | 5 | CheckFlashLubeSuTempAcqua "Diagnosis on the water temperature sensor for VP system" / "Diagnosis on the water temperature sensor for VP system" | — | desligado | PROVADO |
| FLAG_CONF3 | 250 | 0 | 0x0100 | 6 | CheckOutDiagInfoSuKLine "Output high-level on k-line during diagnostic error" / "?" | — | desligado | PROVADO |
| FLAG_CONF3 | 250 | 0 | 0x0400 | 7 | CheckUsaCanPerObd "Usa CAN per OBD" / "?" | — | desligado | PROVADO |
| FLAG_CONF3 | 250 | 0 | 0x0800 | 8 | CheckAttivaPompaInRiserva "Abilita riattivazione pompa in riserva" / "?" | — | desligado | PROVADO |
| FLAG_CONF3 | 250 | 0 | 0x1000 | 9 | CheckRiconoscimentoPieno "Riconoscimento pieno" / "Recognition of full" | — | desligado | PROVADO |
| FLAG_CONF3 | 250 | 0 | 0x2000 | 10 | CheckGiriRuotaFonica "ruota fonica" / "?" | — | desligado | PROVADO |
| FLAG_CONF3 | 250 | 0 | 0x4000 | 11 | CheckEnableLandiConnect "?" / "?" | ButtonResetTotal (Enabled), ButtonResetTrip (Enabled), CheckCambioAutomatico (Enabled), DrawGridInjrTOfsTbl (Enabled), EditInjrGasFlow (Enabled), EditMgssMaxMnfldPress (Enabled), EditNormPress (Enabled), EditNormTemp (Enabled), EditTankVol (Enabled) | desligado | PROVADO |
| FLAG_CONF3 | 250 | 0 | 0x8000 | 13 | CheckCambioAutomatico "?" / "?" | — | desligado | PROVADO |
| FLAG_CONF3 | 250 | 1 | 0x0002 | 14 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF3 | 250 | 1 | 0x0004 | 15 | chkRitardoPassaggioInMinuti "Ritardo passaggio in minuti" / "?" | — | desligado | PROVADO |
| FLAG_CONF3 | 250 | 1 | 0x0008 | 16 | CheckBoxEnableExtLedKLine "Enable external LED on K-Line" / "?" | — | desligado | PROVADO |
| FLAG_CONF3 | 250 | 1 | 0x0010 | 17 | — | — | desligado | DESCONHECIDO |
| FLAG_CONF3 | 250 | 1 | 0x0020 | 18 | CheckBoxEnDiagDtcPowertrain "En Diag DTC POWERTRAIN" / "?" | — | desligado | PROVADO |
| MASK_FUNCTION | 194 | 4 | 0x000C | — [obj+0x2A64!=0] | — | — | desligado | DERIVADO |
| MASK_FUNCTION | 194 | 16 | 0x0040 | — [obj+0x2A64!=0] | — | CheckUsaCanPerObd (Enabled) | ligado | DERIVADO |
| MASK_FUNCTION | 194 | 24 | 0x0001 | — [obj+0x2A64!=0] | — | CheckRiscaldamento (Enabled) | desligado | DERIVADO |
| MASK_FUNCTION | 194 | 24 | 0x0004 | — [obj+0x2A64!=0] | — | — | desligado | DERIVADO |
| MASK_FUNCTION | 194 | 24 | 0x0008 | — [obj+0x2A64!=0] | — | CheckAzzeraBlackListObd (Enabled) | desligado | DERIVADO |
| MASK_FUNCTION | 194 | 24 | 0x0040 | — [obj+0x2A64!=0] | — | — | desligado | DERIVADO |
| MASK_FUNCTION | 194 | 24 | 0x0080 | — [obj+0x2A64!=0] | — | CheckValvetronik (Enabled) | desligado | DERIVADO |
| MASK_FUNCTION | 194 | 28 | 0x0010 | — [obj+0x2A64!=0] | — | — | desligado | DERIVADO |
| MASK_FUNCTION | 194 | 28 | 0x0020 | — [obj+0x2A64!=0] | — | — | desligado | DERIVADO |
| MASK_FUNCTION | 194 | 28 | 0x0040 | — [obj+0x2A64!=0] | — | CheckStartAndStop (Enabled) | desligado | DERIVADO |
| MASK_FUNCTION | 194 | 28 | 0x0080 | — [obj+0x2A64!=0] | — | — | desligado | DERIVADO |

## O que isto NÃO prova
- Que a ECU esteja assim hoje (o log é de 12/08/2026).
- O efeito de cada caixa no motor: vem só do rótulo do original.
- Os bits sem caixa (DESCONHECIDO) podem ser usados pela ECU; o ProgBase só não os mostra.

## Reproduzir
`python3 tools/emulacao/flags_do_original.py --exe <ProgBase.exe> --check` (≈25 s) · testes: `python3 -m unittest tests.test_flags_do_original` (caso 4/anti-stallo, progressioni, idempotência, mutação de 1 byte do .exe).
