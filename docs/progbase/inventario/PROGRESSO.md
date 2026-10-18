# PROGRESSO D3b (abas finais do TFormConfig) - checkpoint

## PRONTO
- tools/progbase/abas_b_re.py: Ctx (RTTI/xrefs/dump anotado), DFM/i18n, defaults do construtor do TStreamDati (40 stores), tabelas de despacho set/get esc/idx.
- tools/progbase/abas_b_log.py: le o Portmon e monta a "memoria da ECU do dono" (197 SerialCodes). Copia versionada: docs/progbase/abas/_ecu_dono_vetores.json.
- tools/progbase/abas_b_emul.py: Unicorn executa handlers REAIS (TStreamDati com 314 vetores do DFM, 935 controles falsos, ECU falsa = log, MM borlndmm em Python, DLL de personalizacao em Python, hooks de UI). Classe Sessao: cenario()/estado_dono()/rodar().
- tests/test_progbase_abas_b.py: 2 testes verdes (pulam sem .exe): CheckAntiStallo -> `35 FA 00 20 00 00 00 4F`; EditAperturaInj 2,5 ms -> `13 53 00 D1 03 3A` (raw 977 = LOG).
- NAO gerados ainda: docs/progbase/abas/<Aba>.md/.json (nenhuma aba documentada).

## DESCOBERTAS (OBS-COD executado / OBS-FIO)
- Dono: modelo 0x4F ("OMEGAS 4", 2o byte da ident `53 04 FE 4F 45 0B`), [80D]=0 (nao-LR). MASK_FUNCTION[14]=0x43 -> bit0=1: anti-stallo HABILITADO na ECU do dono (LOG linha 1850).
- Escrita de ANTI_STALLO[i]: le o vetor (`29 33 01`) e grava SO o elemento: `14 33 01 <i> lo hi ck` (nao o vetor todo). Conversao identidade (rpm, rpm/ciclo, ms).
- Caixa anti-stallo: `35 FA 00 <w0> <w1> ck` (FLAG_CONF3 inteiro, preserva os outros bits).
- IDENTIFICATIVO (SC 53): apos 1a gravacao com [SD+0x1AB8]&0x80 (ligado no construtor e ao abrir projeto cuja chave Generale/Ufficiale e 1) envia `13 35 00 00 00 48` = IDENTIFICATIVO[0]=[1AB8]&~0x80 (flag "configuracao oficial"). Na ECU do dono IDENTIFICATIVO[0]=0 (LOG:4066): no-op.
- Tempos: raw = trunc(ms/(base*0.00025*0.004)+0.5), base=[SD+0x2980]=2560 (BASE_TEMPI) => 0,00256 ms/unidade. Arricchimento (SC116) raw=valor; GIRI cutoff raw=rpm.
- Modelos (ActionAzzeraId 0x5039A0): 0x48 EVO, 0x4F OMEGAS 4, 0x63 EVO 360/OBD, 0x58, 0x5D, 0x5E; [80D]=1 so para 0x14/0x17/0x47/0x48 (SetModel 0x42B964).
- Amarracao controle->codigo: rotina 0x48A228 depende de [80D]; handler generico EditRifLambdaKeyPress usa [ctl+0x2EC]=codigo, [ctl+0x2F0]=indice (-1 = escalar). Handlers despacham por Sender.Tag (+0xC).
- Nada novo contra apagao alem de: progressoes/anti-stallo/gas-no-cutoff ja catalogados; Show de TabIniezione ainda nao rodou ate o fim.

## PROXIMO PASSO exato
1. Terminar TabIniezioneShow na emulacao: instalar o MemoryManager do Delphi: registro em 0xAAFB84..0xAAFB98 (GetMem/FreeMem/ReallocMem... apontam p/ 0x68F258.. que dao "Runtime error 203"); sobrescrever esses ponteiros com hooks de MaquinaAbas._montar_mm (mm_funcs). Rodar `Sessao.rodar(m, metodos_inv['TabIniezioneShow'], m.ctl('TabIniezione'))`, comparar estados visivel/habilitado (m.visivel/habilitado) com sensibilidade (modelo 0x271D, MASK 0x29EC+4*i, caches de FLAG).
2. Escrever abas_b_gerar.py: varrer controles das 14 abas (DFM+i18n+hints HINT<nome>), emular handler (edits: 3 valores; checks: 0/1; radios; combos [+0x2E8]), ajustar formula raw=a*v+b, valor do dono = inverse do LOG, saida docs/progbase/abas/<Aba>.{md,json}.
3. Citar paginas do manual (scratchpad web/sw.txt).

## FALTA
Todos os .md/.json por aba; checkboxes via flags-do-original.json; TValueEditEx (sem KeyPress; so OnExit); radios/combos/botoes/grids; TAutoCalNew/TabAdvanced/Taratura/Variatore/Test grids; PanelBarra/menus/popup; frames TStrategia*; EditVH34Param (nao gravou: provavel condicao de modelo); EditTMaxInjBenz, TempoMassimaCorrente, CorrenteCutoff etc. nao gravam no estado do dono (codigo -1/LR); EditPreheatTempMotoreTh grava sempre 0 (checar); manual/leigo.

## ARMADILHAS
- Outro agente usa abas_emul.py/abas_log.py/abas_motor.py: usar so abas_b_*. (rm/sed -i negados.)
- Carregar tudo (0x439E94) falha (DLL de personalizacao, MM); nao insistir: usar defaults do construtor + estado_dono().
- Delphi: TNumericEdit valor double +0x2E0, precisao +0x2C8; SetValue/IntToStr/FloatToStr precisam de hook (0x96BB88 texto, 0xA4ED60, 0x4080D4); null-page mapeada (cuidado: escritas poluem).
- Sem log, testes usam _ecu_dono_vetores.json; sem .exe pulam.
