# PROGRESSO D3a (as 16 primeiras abas do TFormConfig) - checkpoint

(Pedido dizia PROGRESSO-B.md; gravei como PROGRESSO-A.md para nao colidir com o D3b.)

## Prontas (gerado, nao commitado)
Todas as 16 abas: `docs/progbase/abas/<Aba>.md` + `.json` (382 controles no DFM; ~190 interativos): TabCambio 106, TabLambda 45, TabEmissioni 34, TabSensore 59, TabPressione 31, TabTemp 23, TabKCoefficienti 32, TabCorrettori 12, TabKOpenLoop 1, TabKFactor 12, TabKTempRid 4, TabKTempMot 4, TabKPressDiff 4, TabPressAss 2, TabKTempi 11, TabKTempiEx 2.
Extras: `_indice.json`, `_funcoes_auxiliares.json` (curvas NTC), `_log_leituras.json` (LOG do dono, sem SC 0), `_i18n_usado.json`.
Codigo: `tools/progbase/abas_{a,motor,emul,log,textos}.py`; teste `tests/test_progbase_abas_a.py` (4 testes verdes; pulam sem saida/.exe).
Regerar: definir PROGBASE_EXE, PORTMON_LOG, MANUAL_SW_TXT, I18N_ALL e rodar `python -I tools/progbase/abas_a.py` (2 execucoes = mesmos bytes, nao verificado byte a byte ainda).

## Descobertas
- Controle -> codigo de dado: rotina 0x48A228 ([ctl+0x2EC], indice +0x2F0, combo +0x330); codigos != SerialCode, mapeados pelos casos de 0x436444/0x434CC0/0x43734C/0x435C6C.
- Formulas executadas no Unicorn: ex. EditGiriCambio=1600 -> `13 10 00 40 06 69`; tempo morto gas 1,0 ms = 391 (bate com o LOG); extra-inj 1,4 ms = 547.
- "Tipo di cambio" (ComboCambio): bit0 de GIRI_MIN_CAMBIO (SC16): 0=em aceleracao, 1=em desaceleracao, valor 0 = "Partenza a gas". Dono: 0x04B0 = 1200 rpm, bit0=0 -> EM ACELERACAO (responde o DESCONHECIDO do catalogo).
- Quadro de SetNumber e confirmado ACK `53 00 53` (nao `53 01..`).
- TNumericEdit: Enter limita a [Min;Max] se Min!=0 ou Max!=0; Precision = passo de arredondamento.
- Lambda refs (EditRif*) ficam Visible=False e nenhuma rotina os mostra.

## Falta / armadilhas
- Edits com handler proprio (Cicchetto*, Livello*, Pressione, Flash Lube...) sem formula: so lista de acessos; combos so parcialmente emulados (convencao eax=Self, edx=indice e INFERIDA).
- Grades (DrawGrid*): celulas e quadros por celula nao decodificados.
- Tipo de curva NTC da ECU do dono ([SD+0x272C]) desconhecido; amostras usam tipo 0.
- Base de tempo [SD+0x2980] = SC121 e INFERIDO.
- Textos leigos: ~40% so pelo rotulo (marca INFERIDO, "sem fonte").
- Revisar a mao: associacao rotulo<->campo (ComboAccensione mostra eng "No. of cylinders", provavel erro do i18n original).
- Nada commitado.
