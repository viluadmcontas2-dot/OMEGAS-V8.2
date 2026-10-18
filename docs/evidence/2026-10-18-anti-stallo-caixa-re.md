# Caixa 'Abilitazione anti-stallo': onde é gravada e o que vai para a ECU (2026-10-18)

Fonte: ProgBase.exe original (sha256 8a2d297c…36f4), análise estática com pefile+capstone (sem Ghidra). Somente leitura.
Legenda: OBS-COD = visto no disassembly/binário; DERIVADO = conta feita a partir do visto; INFERIDO = dedução; DESCONHECIDO = sem prova.

## Resposta curta
- A caixa **liga/desliga o bit 0x0020 da palavra 0 de FLAG_CONF3** (TAebVector, SerialCode 250 = 0x00FA, 2 × u16). OBS-COD (cadeia abaixo).
- Clicar na caixa **grava na hora** FLAG_CONF3 inteiro (as 2 palavras) na ECU, se o modo "escrita direta" estiver ativo. Não espera o vetor ANTI_STALLO. OBS-COD o caminho até `TAebVector.SetValues`. INFERIDO (forte) que isso vira um SetVector (0x30–0x36) do SerialCode 250; o opcode dentro de 0x97909C não foi seguido.
- O bit 0 de `MASK_FUNCTION[14]` (SerialCode 194) é só **capacidade**: diz se a ECU suporta a estratégia. Ele deixa a caixa habilitada ou cinza (Enabled), mas **não** liga nem desliga nada. OBS-COD.
- Correção do que já se dava por provado: (1) `CheckAntiStalloClick` **não** mexe só na UI: em 0x4C983F..0x4C9858 ele grava o estado. (2) Em 0x4ACA9C..0x4ACAA5, o resultado de 0x434AEC vai para **Enabled** (VMT+0x84), não para Checked. Checked vem de 0x42E754(obj,4) (VMT+0x108), em 0x4ACA6B..0x4ACA83. OBS-COD.

## 1. O objeto global [[0xAEBED8]] = instância TStreamDati
- OBS-COD 0x4115F4: `mov ecx,[0xAEBED8]; mov edx,[0xA71380]; call 0x7B22C4`. É Application.CreateForm(TStreamDati, var): [0xAEBED8] = 0xAECFB4, o endereço da variável global; [0xA71380] = classe TStreamDati (VMT 0xA713D8, nome 'TStreamDati', tamanho da instância 0x3EBC).
- OBS-COD, tabela de campos publicados de TStreamDati (0xA71424, 320 entradas): AebConnection +0x6C, FLAG_CONF1 +0x90, FLAG_CONF2 +0x2E8, **MASK_FUNCTION +0x2EC**, **FLAG_CONF3 +0x39C**, FLAG_CONF2_LR +0x474, ANTI_STALLO +0x4EC.

## 2. Checked = bit 5 do cache de FLAG_CONF3 (StreamDati+0x2994)
- OBS-COD 0x42E754 (getter, `cmp edx,0x12` + tabela de 19 casos em 0x42E764). Caso 4 → 0x42E7EC: `test byte [eax+0x2994],0x20; setne`.
- OBS-COD 0x42E8D8 (setter, mesma tabela de 19 casos em 0x42E901). Caso 4 → 0x42E9E5: `or ecx,0x20` / `and eax,0xFFFFFFDF` sobre [ebx+0x2994], numa cópia local {0x2994, 0x2998} na pilha. Depois, em 0x42EC3A:
```
42ec3a: cmp byte [ebx+0x80d],0 ; jne 42ec60
42ec43: cmp byte [ebx+0x80e],0 ; je  42ec60
42ec4c: push esp                     ; &{novo 0x2994, 0x2998}
42ec4d: mov ecx,[ebx+0x39c] ; push ecx   ; FLAG_CONF3
42ec54: call 0x978f8c                ; TAebVector.SetValues
42ec5c: test al,al ; je 42ec90       ; falhou -> não atualiza o cache
42ec60: call 0x42b224 / 0x431738 / 0x4316ec   (DESCONHECIDO: efeito)
42ec7d: mov [ebx+0x2994],eax ; mov [ebx+0x2998],edx
```
- OBS-COD, quem enche o cache (rotina grande de carga/gravação 0x439E94..0x4807B0, bloco de FLAG_CONF3 em 0x43B117..0x43B5DB), conforme os bits do parâmetro [ebp+0xC]:
  - bit 0x1: zera 0x2994/0x2998 e lê do arquivo de projeto, seção 'Generale', chaves **'Flag5'** (→0x2994) e **'Flag6'** (→0x2998) (strings 0xA60041/0xA6003B/0xA6004A, call 0x42BB6C).
  - bit 0x2: `FLAG_CONF3.vmt[0x78]` (recarrega da ECU) e depois `0x978DCC(FLAG_CONF3, StreamDati+0x2994)`, que copia os valores em dwords; [0x299D] = leitura ok.
  - bit 0x4: grava 0x2994/0x2998 no arquivo (0x42C094).
  - bit 0x8: `0x978F8C(FLAG_CONF3, StreamDati+0x2994)` = escreve na ECU.
- DERIVADO: 0x2994 = FLAG_CONF3[0] (Flag5 no arquivo) e 0x2998 = FLAG_CONF3[1] (Flag6). Caixa anti-stallo = **FLAG_CONF3[0] & 0x0020**.

## 3. O clique
```
4c9831: cmp byte [ebx+0x1280],0 ; jne 4c985d      ; trava "carregando tela"
4c983f: mov eax,[ebx+0xf84] ; call [edx+0x104]    ; CheckAntiStallo.GetChecked
4c984d: mov ecx,eax ; mov eax,[[0xaebed8]] ; mov edx,4
4c9858: call 0x42e8d8                             ; Set(StreamDati, 4, Checked)
4c985d: ... 0x42e754(obj,4) && 0x434aec(obj) -> habilita os 5 edits
```
- OBS-COD, VMT de TCheckBox (0x74B7CC): +0x68 = 0x7ECECC (lê byte +0x6A = FEnabled → GetEnabled); +0x84 = 0x7EDFE0 (grava +0x6A e manda CM_ENABLEDCHANGED 0xB00C → SetEnabled); +0x104 = 0x758DFC (`State==cbChecked` → GetChecked); +0x108 = 0x758E1C (SetChecked).
- OBS-COD: na carga da tela, [ebx+0x1280] é zerado em 0x4ACA89, logo depois do SetChecked; vários trechos o põem em 1 (0x4905F1, 0x4A6989, 0x4AA9D9…). INFERIDO: é a trava que impede que o SetChecked da carga grave de volta na ECU.
- OBS-COD, outros usos de +0xF84: 0x48EEC8 copia a fonte de LabelTChiusuraInj para CheckAntiStallo (0x7ED668); 0x497AAD põe o Caption vindo da tabela de textos (id 0x3C3, call 0x7EE0E0). Os dois só mexem na UI. Os nomes vêm da tabela de campos de TFormConfig (VMT 0xA7FEEC): 0xF84 = CheckAntiStallo, 0xBD4 = LabelTChiusuraInj.

## 4. MASK_FUNCTION[14] bit 0 = capacidade (0x434AEC)
- OBS-COD: a família 0x434690..0x434CBC é toda do mesmo tipo: `cmp byte [eax+0x2A64],0; test byte [eax+0x29F0..0x2A28], bit`.
- OBS-COD 0x43E8C2..0x43E978: buffer StreamDati+0x29EC com 30 (0x1E) posições em dword, preenchido assim:
  - modo bit 0x1: máscara padrão montada em 0x51CEBC (global 0xAED12C; inclui `or byte [ebx+0xE],1` em 0x51CF2B) e copiada por 0x51CFC8; depois 0x2A64 = 1.
  - modo bit 0x2/0x8: `MASK_FUNCTION.vmt[0x78]` (recarrega) e depois `0x978DCC(MASK_FUNCTION, +0x29EC)`; 0x51CF90 copia de volta para a global; 0x2A64 = ((AebConnection→[+0x40]→[+0x44]) & 4) == 0.
- DERIVADO: 0x29EC + 30×4 = 0x2A64, ou seja, 0x2A64 é o indicador "máscara válida" logo depois do buffer. (0x2A24 − 0x29EC)/4 = 14, então 0x434AEC = `válida && (MASK_FUNCTION[14] & 1)`.
- OBS-COD: MASK_FUNCTION só é usado (+0x2EC a partir de StreamDati) em 0x43E909/0x43E937/0x43EA53, todos de leitura. Os outros hits de +0x2EC (0x4A6A5A, 0x4C9025, 0x506DF6…) são campo de TNumericEdit, não de StreamDati. Nenhuma escrita de MASK_FUNCTION foi encontrada. Ele não tem FileSection no DFM.
- INFERIDO (forte): MASK_FUNCTION é a máscara de funções que o firmware tem, lida da ECU. O bit [14].0 diz "a ECU suporta anti-stallo" e só controla Enabled e a cor da caixa (0x9B0000 se habilitada, 0x808080 se não).

## 5. Quando a escrita do clique acontece de fato
- OBS-COD: a escrita só sai se [StreamDati+0x80D]==0 **e** [StreamDati+0x80E]!=0.
- OBS-COD: 0x80D é posto em 1 quando o código do modelo de ECU [+0x271D] vale 0x14/0x17/0x48/0x47 (0x42B8F7..0x42B915 e 0x42B9A5..0x42B9B9), e em 0 nos outros casos.
- OBS-COD: 0x80E só é posto em 1 em 0x42AD3C (init). Não achei quem o zere com `mov byte`.
- INFERIDO: 0x80E = "escrita imediata ligada" e 0x80D = "modelo/estado sem escrita direta". Fora dessas condições, o clique só atualiza o cache 0x2994. Aí o bit vai para a ECU pelo caminho de gravação geral (modo bit 0x8, 0x43B4B7..0x43B4D4) e para o projeto como Flag5.
- OBS-COD 0x978F8C (SetValues): se DataMask ≠ (1<<8·DataLength)−1, faz recarregar → mesclar com a máscara → escrever. Para FLAG_CONF3 (DataMask 65535, DataLength 2) a máscara é cheia: DERIVADO que escreve direto (0x979087 → 0x97909C) as **2 palavras inteiras**, com os outros bits que estiverem no cache.

## O que falta (menor próximo passo)
- Seguir 0x97909C até o frame do protocolo para confirmar o opcode (SetVector 0x30|n) e o payload (SerialCode 250 + 2×u16). Ou fazer uma captura Portmon marcando/desmarcando só a caixa: deve sair um único SetVector do SerialCode 250 com a palavra 0 mudando 0x0020.
- Descobrir quem zera 0x80E (pode ser escrita indireta) e o que fazem 0x42B224/0x431738/0x4316EC depois da escrita.
