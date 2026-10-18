# Que quadro o original envia para ligar o anti-apagão? (prova pelo código, sem log do evento)

Marcas: OBS-COD = visto no código do ProgBase.exe (sha256 8a2d297c…); OBS-FIO = visto na captura Portmon; DERIVADO = conta feita a partir dos dois.

## Resposta
Ligar a caixa "Abilitazione anti-stallo" grava o vetor **FLAG_CONF3** (código 250 = `FA 00`, 2 × u16) com o bit `0x20` ligado na palavra 0. Na ECU do dono (FLAG_CONF3 = `0000 0000`) o quadro é:

`35 FA 00 20 00 00 00 4F`   (opcode 0x35, código FA 00, palavra 0 = 0x0020, palavra 1 = 0x0000, checksum 0x4F)

Resposta esperada da ECU: eco `35 FA 00 20 00 00 00 4F` e depois ACK `53 00 53`. Para desligar: palavra 0 com o bit 0x20 apagado (`35 FA 00 00 00 00 00 2F` partindo de 0020 0000... em geral: palavra0 & ~0x20, palavra1 intacta).

## Cadeia de prova
1. **O clique chama o setter genérico** — `CheckAntiStalloClick` (0x4C982C) → `0x42E8D8(StreamDati, 4, estado)`. [OBS-COD]
2. **O setter só mexe no bit 0x20 da palavra 0 e reenvia as DUAS palavras** — caso 4 (0x42E9E5): `or ecx,0x20` / `and eax,0xFFFFFFDF` sobre a cópia local em `[obj+0x2994]`; a palavra 1 (`[obj+0x2998]`) vai intacta (0x42E8E1..0x42E8F0). Em 0x42EC3A..0x42EC54: se `[obj+0x80D]==0` e `[obj+0x80E]!=0`, chama `0x978F8C(vetor_em_[obj+0x39C], &duas_palavras)`; senão só atualiza a cópia e o valor vai na gravação geral. [OBS-COD]
3. **`0x978F8C` é o `TAebVector.SetValues` genérico** — para a máscara do objeto (`[this+0x54]`), faz leitura-modificação-escrita sobre o cache `[this+0x148]` (preserva bits fora da máscara; FLAG_CONF3 tem DataMask 65535, ou seja, palavra inteira) e chama `0x97909C`, que monta o quadro pelo mesmo caminho de TODOS os vetores. [OBS-COD]
4. **O formato desse caminho já foi visto no fio para um vetor de mesmo formato** — `PortmonLOGNOVO.LOG` linha 31284: o ProgBase gravou `FLAG_CONF1` (código 3, TAebVector, DataLength 2, 2 elementos) com `35 03 00 86 2C 51 10 4B`; a ECU devolveu o eco e `53 00 53`. Checksum = soma dos bytes anteriores (0x14B → 0x4B). [OBS-FIO]
5. **O app já gera esse quadro byte a byte** — `AutoCalProtocol.writeVectorU16(3, [0x2C86, 0x1051])` = `35 03 00 86 2C 51 10 4B` (teste novo em `AutoCalProtocolTest`) e `writeVectorU16(250, [0x0020, 0])` = `35 FA 00 20 00 00 00 4F`. Conferido também por uma conta independente em Python. [DERIVADO]

## O que continua NÃO provado
- O quadro **para o código 250 nunca foi visto ao vivo** (nenhuma captura alterna a caixa). Prova indireta forte: o caminho de montagem é o mesmo para qualquer vetor e o formato foi visto para um vetor de 2 × u16.
- O efeito físico (a ECU realmente passar a atuar no anti-apagão) e a ordem campo→rótulo dos 5 números do `ANTI_STALLO`.
- Se a ECU aceita a escrita fora do modo/condição do original (`[obj+0x80D]`/`[obj+0x80E]`, ligados ao modelo de ECU 0x14/0x17/0x48/0x47): o app só deve gravar depois de ler o valor atual e conferir pela leitura de volta.
