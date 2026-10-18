# Tela "anti-stallo" do ProgBase original (2026-10-18)

Legenda: OBS-COD = visto no arquivo decodificado; DERIVADO = conta feita a partir do que foi visto; INFERIDO = dedução; DESCONHECIDO = não há prova.

## Qual formulário
- OBS-COD: os controles estão no formulário **TFormConfig** (recurso `RT_RCDATA(10)__TFORMCONFIG__0.bin`, 334.188 bytes, decodificado inteiro, 334188/334188 bytes consumidos). Texto em `docs/evidence/dfm-original/TFORMCONFIG.txt`.
- OBS-COD: caminho no formulário: `FormConfig > PageControl > TabIniezione (Caption 'Iniettori') > ScrollBoxIniezione > PanelSubInjection` (painel Left 0, Top 430, 820x1768). Coordenadas abaixo são relativas a esse painel.
- OBS-COD: o mesmo recurso aparece no tradutor `i18n_all.json` com as chaves CHECKANTISTALLO e LABELANTISTALLO0..4 (inglês: "Enable un-stalling strategy", "Max RPM to enable un-stalling", "Delta RPM to enable un-stalling", "Delay to exit gas mode", "Hysteresys for enabling threshold", "Delta RPM to disenable un-stalling").
- OBS-COD: existe print `TFORMCONFIG.png` na pasta de prints (id 1pyoWtubNYBmPI37s8W6EvC69JHlJpPx4), mas **não foi aberto** por mim. Sem print, a aparência real (cores, rolagem) não foi confirmada.

## Tabela de controles (ordem de tabulação = TabOrder)
Todos têm `Visible = False` no DFM (OBS-COD): são mostrados por código que o DFM não contém (DESCONHECIDO quando/por quê). Fonte Arial 24 negrito, cor 10158080 nos rótulos e edits.

| TabOrder | Componente | Tipo | Left | Top | Larg x Alt | Texto / limites | Observação |
|---|---|---|---|---|---|---|---|
| 28 | CheckAntiStallo | TCheckBox | 38 | 1120 | 715x32 | 'Abilitazione anti-stallo' | OnClick=CheckAntiStalloClick; OnKeyPress=CheckTpsBlocKeyPress. Único com Visible padrão (True) |
| 29 | EditAntiStallo0 | TNumericEdit | 548 | 1159 | 72x37 | Max 6000, Precision 1, DataIndex 0 | Rótulo: 'Max giri attivazione anti-stallo' (LabelAntiStallo0, L63 T1162); unidade 'rpm' (LabAntiStallo0, L630) |
| 30 | EditAntiStallo1 | TNumericEdit | 548 | 1198 | 72x37 | Max 1000, Precision 1, DataIndex 1 | 'Delta giri attivazione anti-stallo' (L63 T1201); 'rpm/ciclo' |
| 31 | EditAntiStallo2 | TNumericEdit | 548 | 1237 | 72x37 | Max 10000, Precision 1, DataIndex 2 | 'Attesa uscita gas' (L63 T1240); 'ms' |
| 32 | EditAntiStallo3 | TNumericEdit | 548 | 1277 | 72x37 | Max 1000, Precision 1, DataIndex 3 | 'Isteresi su soglia attivazione' (L63 T1279); 'rpm/ciclo' |
| 33 | EditAntiStallo4 | TNumericEdit | 548 | 1317 | 72x37 | Max 1000, Precision 1, DataIndex 4 | 'Delta giri disattivazione anti-stallo' (L63 T1320); 'rpm/ciclo' |

- OBS-COD: encadeamento de Tab (NextControl/PrevControl): CheckAntiStallo -> Edit0 -> Edit1 -> Edit2 -> Edit3 -> Edit4 -> volta ao CheckAntiStallo.
- OBS-COD: rótulos têm Width de 196 a 390; unidades (LabAntiStallo0..4) em Left 630. Nenhum MinValue aparece (só MaxValue).
- OBS-COD: eventos dos edits: OnEnter/OnExit = EditGiriCambioEnter/Exit (os mesmos usados pelos edits de "giri cambio"), OnKeyPress = EditAntiStalloKeyPress.
- DERIVADO: as unidades vêm dos rótulos; tipo 'rpm/ciclo' para [1],[3],[4] é texto do rótulo, não de campo de unidade do edit.

## Ligação com a ECU
- OBS-COD: em `TSTREAMDATI` existe `ANTI_STALLO: TAebVector`, SerialCode 307 (0x133), DataLength 2, ArrayDimension 5, FileSection 'AntiStallo', FileKeyName 'Param', padrão (1600, 100, 1500, 2, 20). Texto em `docs/evidence/dfm-original/TSTREAMDATI.txt` (linha ~3721).
- INFERIDO (forte): EditAntiStallo0..4 ligam-se a ANTI_STALLO[0..4] pelo `DataIndex` 0..4 e pelo tamanho 5 do vetor e pelo nome. **O DFM não contém nenhuma propriedade que cite ANTI_STALLO** nos edits (sem 'Vector'/'DataSource'); a ligação real está no código Delphi, que não foi visto.
- INFERIDO (depende da ligação acima): [0]=1600 rpm máx giri ativação; [1]=100 rpm/ciclo delta ativação; [2]=1500 ms atraso saída do gás; [3]=2 rpm/ciclo histerese; [4]=20 rpm/ciclo delta desativação. Isso contradiz a suposição de `2026-10-18-anti-stallo.md` de que [2] seria rpm: pela tela, [2] é tempo em ms (MaxValue 10000).

## Onde a caixa 'Abilitazione anti-stallo' é gravada
- **DESCONHECIDO.** O DFM do CheckAntiStallo não tem DataIndex, Vector, nem bit; só OnClick. O tratador `CheckAntiStalloClick` é código Delphi dentro do ProgBase.exe, que não está nos arquivos verificados.
- OBS-COD: não existe na `TSTREAMDATI` nenhum TAebNumber/TAebVector com nome contendo 'stallo' além de ANTI_STALLO (busca 'stall', sem distinção de caixa: 1 resultado, linha 3721 e a FileSection).
- INFERIDO (fraco): como o ANTI_STALLO só tem 5 números (todos usados pelos 5 edits), o liga/desliga deve ser um bit de um dos vetores de flags (FLAG_CONF1 #3, FLAG_CONF2 #192, FLAG_CONF2_LR #193, FLAG_CONF3 #250, todos U16 x2) ou estar em MASK_FUNCTION (#194, 30 bytes). Nenhum dado do DFM escolhe qual; não afirmar.
- Mesmo grupo de dúvida: o checkbox usa OnKeyPress=CheckTpsBlocKeyPress (o mesmo de checkboxes do "TPS bloc"), o que é só indício de código compartilhado, sem relação de parâmetro.

## Arquivos verificados
- Com 'stall': TFORMCONFIG (esta tela) e TSTREAMDATI (vetor ANTI_STALLO) e i18n_all.json.
- Sem o termo (conforme pedido): TFORMMAIN, TFORMLANDICONNECT, TFORMSERVICE, TFORMCALIBRA, TFORMCORRCALIBRA, TFORMRIFAUTOCAL, TFORMVISUALIZZA.
- Listagem do Drive: pasta dos .bin lista 5 itens por página e a página 3 deu erro (token inválido); a lista lida (2 páginas) incluía todos os TFORM*/TSTRATEGIA*/TAUTOCAL* conhecidos. Não baixei ProgBase.exe nem Dump.bin.

## O que falta
- Para saber onde a caixa é gravada: código de `TFormConfig.CheckAntiStalloClick` e do carregamento da tela, só no ProgBase.exe (decompilar com REA a partir do endereço do método `CheckAntiStalloClick`, ou captura Portmon marcando/desmarcando só a caixa e comparando o frame de escrita).
- Mesma captura confirmaria a ligação Edit0..4 <-> ANTI_STALLO[0..4].
