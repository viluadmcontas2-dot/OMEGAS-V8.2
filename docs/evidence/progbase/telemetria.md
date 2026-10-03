# Tema 2 — Telemetria ao vivo (48 01 → 34 bytes)

Fontes permitidas: LN completo e DUMP. Revisão dirigida desta continuação: Timer1Timer **VA 0x0050B408**, recuperado da tabela de métodos (offset PE 0x699EC7); TimerDatiTimer VA 0x004A307C. Trechos mínimos e offsets em `fontes/desmontagem-dirigida.json`; hashes em `fontes/INDICE-FONTES.md`. Esta revisão substitui as hipóteses antigas /800, 109−raw e raw−20 como descrição do original.

## 2.1 Quadro e atualização

`48 01 49 → eco | 53 | 22 | payload[34] | checksum`. `PROVADO`: 20.450 respostas completas no LN; última requisição 39517 é truncada no EOF e excluída.

`PROVADO` no DUMP: helper 0x004381FC retorna `TStreamDati+0x293C`. Timer1 e TimerDati têm Interval=75 no DFM (offsets 0x13723 em TFORMVISUALIZZA e 0x51589 em TFORMCONFIG). Interval configurado não prova frequência efetiva sob carga. O request_timestamp do LN é zero; o corpus não prova cadência de parede.

## 2.2 Campos reabertos e seus limites

| Offset | Leitura | Uso / transformação no original | Selo e fonte |
|---:|---|---|---|
| 0 | U16LE | RPM, identidade | PROVADO: 0x50B45A–0x50B462 |
| 2 | U8 | campo de tensão normalizado `raw×5/255`, campo Visualizza+0x508 | PROVADO no consumidor 0x50B470–0x50B496; identidade física final desconhecida |
| 3 | U8 | copiado para Visualizza+0x588 | PROVADO em 0x50B4B0–0x50B4C6; semântica desconhecida |
| 4–5 | U16LE | terceiro tempo de injeção na rotina Visualizza, escala de 2.3; LN prefixo sempre zero | PROVADO leitura 0x50B604–0x50B656; identidade do canal e suporte desta ECU desconhecidos |
| 6–7 | U16LE | Gas Inj. banco 1, escala de 2.3 | PROVADO em 0x50B544–0x50B5F6 |
| 8–9 | U16LE | Petrol Inj. banco 1; também RunPoint.x | PROVADO em 0x50B5A4–0x50B5F6; consumidor AutoCal anterior ainda sujeito ao checklist L-12 |
| 10 | S8 / U8 conforme consumidor | TimerDati lê S8 para campo +0x1700 e U8 normalizado ×5/255 para +0x1718 | PROVADO em 0x4A3100–0x4A3137; nome/finalidade desconhecidos |
| 11 | U8 bitfield | flags de apresentação; ver 2.4 | PROVADO em 0x50B664–0x50B66E |
| 12 | U8 | canal de temperatura A; destino/conversão dependem do ramo de configuração, 2.5 | PROVADO leitura; escala física final não fechada |
| 13 | U8 | LEVEL raw; sem unidade física no quadro | PROVADO em 0x50B67C–0x50B686 e 0x4A3239–0x4A3243; level.md |
| 14–15 | **S16LE** | pressão: quantização /10, ring de 10 amostras, média inteira; display base ×0,01 ou base + MAP (2.5) | PROVADO em 0x50B4D4–0x50B536 e 0x50C0FA–0x50C291 |
| 16 | U8 | canal de temperatura B, ramo dependente (2.5) | PROVADO leitura; escala física final não fechada |
| 17–18 | **S16LE** | MAP mostrado = `trunc(raw/10)×0,01` bar; resolução centésimos | PROVADO em 0x50B789–0x50B79A e 0x50BAB4–0x50BAC0 |
| 19 | U8 | tensão dos injetores; conversão condicional por tipo, 2.6 | PROVADO: helper 0x480908 lê +0x294F = payload+19; Timer1 alimenta LabTensioneIniettori |
| 20 | S8 / U8 | TimerDati lê S8 para +0x17FC e U8 para +0x1738 | PROVADO em 0x4A3148 e 0x4A32E7; semântica desconhecida |
| 21 | U8 | canal normalizado ×5/255 quando gate de banco adicional permite | PROVADO em 0x50B807–0x50B824; semântica desconhecida |
| 22–23 | — | não identificados nesta revisão | DESCONHECIDO |
| 24–25 | U16LE | Gas Inj. banco 2, escala 2.3; leitura condicionada pelo suporte do banco | PROVADO em 0x50B832–0x50B884 |
| 26–27 | U16LE | terceiro tempo do banco adicional, escala 2.3 | PROVADO em 0x50B8F2–0x50B944; identidade física desconhecida |
| 28–29 | U16LE | Petrol Inj. banco 2, escala 2.3 | PROVADO em 0x50B892–0x50B8E4 |
| 30–31 | S16LE | TimerDati lê para +0x1820 | PROVADO em 0x4A33F7–0x4A3400; semântica desconhecida |
| 32–33 | — | não identificados nesta revisão | DESCONHECIDO |

A regressão histórica que agrupa bytes 2–3 como U16 e os correlaciona com MAP **não identifica um campo original**: os consumidores reabertos tratam esses bytes separadamente. O padrão do LN pode ser registrado como correlação, não como nome/endianidade do protocolo.

## 2.3 Escala dos tempos: vínculo ao parâmetro fechado

`PROVADO` no DUMP: getter 0x433FFC lê cache TStreamDati+0x2980. Loader 0x43B602–0x43B642 toma objeto +0x214 e chama GetEcuData com endereço desse cache. A tabela de campos liga +0x214 a **BASE_TEMPI_GLOBALE** (offset PE 0x6711C1); o DFM TSTREAMDATI identifica SC 121, SerialCode em 0x768C.

Timer1 multiplica o inteiro sem sinal por esse cache, extended80 **0,00025** (VA 0x50D200) e double **0,004** (0x50D20C): `ms = raw×BASE_TEMPI_GLOBALE×10⁻⁶`. O loader também contém fallback explícito **2560** (0x43B5EF/0x43B644); o gatilho completo de fallback ainda deve ser identificado antes de imitá-lo.

`PROVADO` na fiação: seq 28, `09 79 00 82 → 53 02 00 0A`, SC 121=2560. Ex.: LN seq 6109, gasolina 3999 → **10,23744 ms**, gás 7755 → **19,8528 ms**. Isso valida a aritmética do display, não a calibração física de um instrumento externo.

Os vetores AutoCal usam representação distinta (/512 ms, /1024 bar): autocal.md. Não transferir escalas entre objetos por semelhança de nome.

## 2.4 Byte 11: flags usados na apresentação

- `0x80` testado em 0x50BC0A e usado em imagem de estado. O nome exato do estado ainda deve ser ligado à tabela de campos; “motor funcionando” permanece INFERIDO pelos números do LN.
- `0x04` testado em 0x50BC3F; uso no indicador de cutoff precisa da ligação final de campos. Associação com injeções zero e RPM>0 é INFERIDA no LN.
- `0x10` testado em 0x50BC74; modo gás é consistente com o LN e o seletor AutoCal anterior.
- `0x08` como transição e `0x01` como partida não foram fechados no código desta revisão. Não assumir enum simples.

## 2.5 Pressão e temperaturas: dependem da configuração

**Pressão PROVADA como processamento de apresentação:** `q=trunc(S16LE(payload[14:16])/10)`; Timer1 guarda q no ring +0x548 com índice módulo 10 (+0x570), soma os dez e faz outra divisão inteira por 10 para +0x53C. Quando flag TStreamDati+0x80D é não zero, LabPressione (+0x408 na tabela de campos, offset PE 0x6999EA) recebe `média×0,01`. Quando zero, recebe `(média + trunc(MAP_raw/10))×0,01`. A identificação da flag e a semântica absoluta/diferencial permanecem abertas.

Exemplo reproduzível de dez amostras iguais ao raw **1767** do LN seq 6109: q=176, média=176, display base **1,76 bar**; com MAP raw492 e ramo aditivo, **2,25 bar**. **/800 não é o processamento encontrado no original.** O exemplo calcula regime estável; não promete a leitura exata da tela no instante da captura nem o estado inicial do ring.

**Temperaturas PROVADAS como despacho, conversão final ainda parcial:** Timer1 usa helpers **0x42A52C** e **0x42A788**, que interpolam tabelas em memória e tratam extremidades. Tabelas são inicializadas em rotinas a partir de 0x430A46; não usar o snapshot de zeros em .data como tabela válida em execução.

| Flag +0x80D | LabTempRiduttore (+0x454) | LabTempMotore (+0x45C) |
|---|---|---|
| não zero | helper 0x42A52C(byte12) | helper 0x42A788(byte16) |
| zero | helper 0x42A52C(byte16) | helper 0x42A788(byte12) |

Raw zero recebe sentinela float VA 0x50D214 e cai no texto de ausência; valor **0,0** conferido nos bytes; as bordas da interpolação ainda devem ser preservadas antes de portar. **109−raw e raw−20 ficam retirados como fórmulas do original**, pois não representam esses caminhos. A tabela escolhida por tipo de sensor e os valores numéricos precisam da próxima desmontagem (L-04).

## 2.6 Tensão dos injetores: identidade fechada, dois ramos

Cadeia PROVADA: Timer1 0x50B7A9 → helper **0x480908** → campo +0x530 → formatação → LabTensioneIniettori (+0x47C, tabela de campos em offset PE 0x699C2A).

O helper lê U8 TStreamDati+0x294F (=payload19), retorna 0 para raw<80; para raw≥80, testa byte TStreamDati+0x271D. Tipos 0x5D/0x5E usam raw×5×133×0,00011883541295306001; demais usam raw×5×147×0,00008343763037129746. Nome/SC do seletor ainda desconhecidos.

Constantes: VA 0x480988=80 (float), 0x480994=5, 0x480998=133, 0x48099C=coeficiente extended80 especial; 0x4809A8=147, 0x4809AC=coeficiente extended80 normal. LN seq 6073 raw221 → **13,55319 V** no ramo normal, **17,46465 V** no especial. A leitura raw é observada; o ramo efetivo desta ECU não foi provado. Não manter a aproximação raw/16 como fórmula original.

## Próxima ação dirigida

Identificar +0x80D e +0x271D; reconstruir as tabelas inicializadas e o tipo de sensor que escolhe cada uma; revalidar offsets/consumidores restantes e a política de sentinelas. Estes são gaps de DUMP, não exigem modificar OMEGAS ou escrever na ECU.
