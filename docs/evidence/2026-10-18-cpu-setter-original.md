# Execução emulada do setter da caixa 'Abilitazione anti-stallo' (CPU x86-32 do ProgBase.exe, 2026-10-18)

Binário: ProgBase.exe sha256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4` (só leitura; nada é gravado nele).
Ferramenta: `tools/emulacao/cpu_setter_original.py` (Unicorn 2.1.4, pefile; determinística, CLI). Teste: `tests/test_cpu_setter_original.py` (11 testes; pulam sem .exe/unicorn/capstone/pefile).
Marcas: OBS-COD = executado/lido no código do .exe · OBS-FIO = visto no Portmon · DERIVADO · INFERIDO · DESCONHECIDO · STUB = não é código do .exe.

## Resultado (o que o original REALMENTE produz)

| caso | FLAG_CONF3 inicial (w0 w1) | ação | quadro capturado no ponto de escrita serial | cache final (w0 w1) |
|---|---|---|---|---|
| 1 | 0000 0000 | marcar | `35 FA 00 20 00 00 00 4F` | 0020 0000 |
| 2 | 0020 0000 | desmarcar | `35 FA 00 00 00 00 00 2F` | 0000 0000 |
| 3 | 0000 0000 | desmarcar | `35 FA 00 00 00 00 00 2F` (grava mesmo sem mudança) | 0000 0000 |
| 4 | 0C10 ABCD | marcar | `35 FA 00 30 0C CD AB E3` | 0C30 ABCD |
| 5 | FFFF FFFF | desmarcar | `35 FA 00 DF FF FF FF 0B` | FFDF FFFF |

- **Bate com a previsão derivada** `35 FA 00 20 00 00 00 4F` (caso 1): sim, byte a byte. [OBS-COD, executado]
- Ler-modificar-escrever provado (casos 4 e 5): o bit 0x20 da palavra 0 é o único alterado; os demais bits da palavra 0 e a palavra 1 inteira seguem no quadro (a palavra 1 vem do cache `[obj+0x2998]`). [OBS-COD, executado]
- O original grava também ao "desmarcar" um bit que já estava apagado (caso 3): não há comparação antes de enviar. [OBS-COD, executado]
- Resultado idêntico com a porta em modo *buffered* (1 `WriteFile` de 8 bytes) e *byte a byte* (8 `WriteFile` de 1 byte, eco lido a cada um): os dois ramos do código original (`[porta+0x49]`) foram executados.
- **Calibração contra o fio**: `SetValues` direto num vetor de código 3 com `2C86 1051` gera `35 03 00 86 2C 51 10 4B`, idêntico ao quadro de `PortmonLOGNOVO.LOG` linha 31284. [OBS-FIO = OBS-COD] Isto valida o conjunto (objetos montados + stubs) contra um quadro real.
- Estrutura do quadro, lida no código `0x92617C`: `0x30|min(n,7)` onde n = (nº elementos × bytes/elemento) + 1 se versão do protocolo ≥ 2; depois código (byte baixo e, se versão ≥ 2, byte alto), dados little-endian, checksum = soma dos bytes anteriores mod 256 (acumulada em `[proto+0x48]`). Com versão 1 o mesmo setter geraria `34 FA 20 00 00 00 4E` (rodado). [OBS-COD]
- Resposta da ECU consumida pelo original: eco de cada byte, depois `53` (ACK, `0x923860`), `00` (`0x922B0C`) e checksum `53` (`0x923B30`). [OBS-COD; a sequência `53 00 53` também em OBS-FIO]
- Sem eco/ACK (porta muda) o original **não** atualiza o cache `[obj+0x2994]` (SetValues devolve falso, `0x42EC5E`), fecha a porta (CloseHandle; 1 só WriteFile, sem reenvio) e termina limpo. [OBS-COD, executado]
- Com `[obj+0x80D] != 0` **nenhum** quadro sai e o cache muda mesmo assim (`0x42EC3A`→`0x42EC60`). [OBS-COD, executado]
- Mutação: trocar na memória emulada o imediato de `or ecx,0x20` (0x42E9F1) para 0x40 gera `35 FA 00 40 00 00 00 6F` — prova que o quadro sai do código executado, não de tabela. [teste]

## O que foi executado de verdade (código do .exe, instrução a instrução em Unicorn)
`0x42E8D8` TStreamDati.Set (caso 4) → `0x978F8C` SetValues → `0x97909C` → `0x975EB0` (evento OnBefore, nulo → continua) → `0x92617C` (monta cabeçalho/dados) → `0x922BF8`, `0x922C1C`, `0x922FD0` (acumulam bytes/checksum) → `0x923608` (envia) → `0x91D42C`/`0x91DF00` (TAebCom.Write, chama WriteFile) → `0x91DD38` (TAebCom.Read) → `0x923860`, `0x922B0C`, `0x923B30` (ACK) → `0x975E38` (OnAfter) → `0x97609C` (notifica dependentes) → `0x42B224`, `0x431738` (pós-gravação). Ainda: o prólogo SEH `0xA4A204`, `0xA45414` (mult. 64 bits), `0xA3CFD7` (memset), `0x97492C` (potência), e o corpo dos thunks `jmp [IAT]`. ~1,5 mil instruções (buffered) / ~2,5 mil (byte a byte). A lista de entradas de função executadas sai em `--json` (`funcoes_executadas`).

## STUBS (não é código do .exe) — cada um e sua semântica
| stub | semântica mínima | por quê |
|---|---|---|
| IAT inteira | cada entrada da tabela de imports recebe um endereço fictício; chamar API sem stub **para a emulação** com `erro` (nenhum retorno falso silencioso) | Windows não existe aqui |
| `WriteFile` | **captura** os bytes (hook do ponto de escrita serial), grava `*written=n`, devolve 1 | objetivo |
| `ReadFile` | entrega bytes da fila da ECU fictícia; `*read=n_entregue`; 1 | ECU |
| `PurgeComm`, `SetCommTimeouts`, `CloseHandle` | devolve 1, sem efeito | porta fictícia |
| `GetCommTimeouts`, `ClearCommError` | zera a estrutura de saída; 1 | idem |
| `EnterCriticalSection`/`LeaveCriticalSection` | no-op (um só fio) | lock real não faz diferença sem concorrência |
| `Sleep`, `GetTickCount`, `GetLastError` | relógio sintético determinístico (+10 ms por chamada, `Sleep` soma); erro 0 | determinismo |
| **ECU fictícia** | cada byte escrito volta como eco (half-duplex); ao completar um quadro `0x3n` (tamanho n+3) com checksum correto, enfileira `53 00 53` | INFERIDO do log (eco + ACK vistos no fio); o conteúdo/timing real da ECU é outro assunto |

Nada do RTL Delphi/C++Builder (alocação, strings) foi stubado: o caminho exercitado não os chama (os ramos de log, que usariam strings, ficam desligados por `[proto+0x28A]=0` e `[proto+0x44]=0`).

## Objetos montados na heap emulada (campo → valor → origem)
**TStreamDati** (0x3EC0 bytes zerados): `+0x2994/+0x2998` = FLAG_CONF3[0]/[1] iniciais (entrada do cenário; OBS-COD que são lidos em 0x42E8E1/0x42E8EA); `+0x39C` = ponteiro do vetor FLAG_CONF3 (OBS-COD 0x42EC4D); `+0x80D`=0 e `+0x80E`=1 (OBS-COD exigidos em 0x42EC3A..4A; que valem assim na ECU do dono é INFERIDO em `2026-10-18-flags-do-original.md`); `+0x1AB8`=0 (bit 0x80 apagado; ver limites).
**TAebVector FLAG_CONF3** (vtable real 0xAC9BA8): `+0x44`=2 e `+0x70`=2 (ArrayDimension, DFM; setter 0x978C64), `+0x58`=2 e `+0x74`=2 (DataLength, DFM; setter 0x9751FC), `+0x54`=0xFFFF (DataMask, DFM), `+0x64`=`+0x6C`=250 (SerialCode, DFM; setter 0x9751D0), `+0x4C`=`+0x50`=-1 (Row/ColIndex, DFM e construtor 0x974AA0), `+0x48`=1, `+0x60`=0, `+0xC0`=conexão, `+0x110`=0, `+0x124`=1, `+0x11C`=TList vazia (contagem 0, ver abaixo), `+0x148`=cache de 2 dwords, `+0x158`=`+0x160`=0 e eventos (`+0xDA`,`+0xEA`,`+0xFA`) nulos (padrão do construtor/instância zerada; nenhum setter do TAebVector põe `+0x158/+0x160` em 1: varredura 0x974000..0x97C000). Esses campos não são escolha minha: cada um é lido no caminho executado (0x978F8C/0x97909C/0x975EB0/0x975E38).
**Conexão (TAebConnection)**: `+0x40`=protocolo, `+0x44`=objeto lock (vtable real 0x65D5FC, a mesma classe que o construtor 0x937180 instancia).
**Protocolo** (campos do construtor 0x920128): `+0x258`=1000, `+0x25C`=400, `+0x260`=5, `+0x294`=`+0x298`=5, `+0x2A4`=1, `+0x2B8`=0x1F40, `+0x44`=0 (sem erro), `+0x48`=0 (checksum), `+0x28A`=0 (sem log). **SUPOSIÇÕES de estado de sessão (não do construtor)**: `+0x2B0`=1 (ECU conectada; com 0, `0x92617C` sai sem enviar nada) e **`+0x2C4`=2 (versão do protocolo)** — a versão ≥ 2 é DERIVADA do cabeçalho de 3 bytes visto no fio (`35 03 00 …`); a versão que a ECU do dono negocia (rotina de conexão 0x923FDC) **não foi emulada**.
**Porta (TAebCom, vtable real 0xAB55CC)**: `+0x44`→dword de status 0, `+0x48`=1 (aberta, lido por vmt[0x28]), `+0x49`= 0 ou 1 (vmt[0x58] decide buffered; **o modo real do ProgBase é DESCONHECIDO**, os dois dão o mesmo quadro), `+0x40`=100 (timeout de leitura), `+0x88`=0x1234 (handle fictício ≠ 0/-1), eventos nulos.

## Limites e DESCONHECIDO
- **Não houve etapa de licença/chave no caminho**: nada foi contornado ou alterado no código do .exe (só o teste de mutação mexe na memória emulada, e só quando pedido).
- A lista de dependentes `[vetor+0x11C]` está vazia: `0x97609C` (notificação de UI) não percorreu nenhum filho; na aplicação real a tela se registra nela. Só afeta UI. [DESCONHECIDO o que ela dispara na prática]
- Após gravar com sucesso o setter ainda testa `[obj+0x1AB8] & 0x80` (`0x431738`); se estiver ligado, chama `0x4316EC` que limpa o bit e **grava um segundo vetor (IDENTIFICATIVO, `[obj+0x14C]`, código 53, 30 bytes) via `0x9795F0`**. Com `+0x1AB8`=0 isso não acontece; o valor desse campo numa sessão real é **DESCONHECIDO** e o segundo quadro **não foi emulado**. Se essa bandeira estiver ligada na ECU do dono, sairia um quadro a mais além do de FLAG_CONF3.
- Montei os objetos à mão; o caminho `TFormConfig.CheckAntiStalloClick → 0x42E8D8` (0x4C982C) não foi executado (depende de formulário VCL); o ponto de entrada emulado é o setter genérico, com `edx=4` e `ecx=0/1`, como no clique (OBS-COD 0x4C9858).
- Efeito físico na ECU (ela passar a atuar) e aceitação do quadro pela ECU real: DESCONHECIDO; o ACK é fictício.
- O teste não é bit-exato em relação ao tempo (timeouts/Sleep sintéticos).

## Como reproduzir
`/tmp/uc/bin/python -I tools/emulacao/cpu_setter_original.py --acao marcar --flag3 0000:0000` (também `--acao desmarcar`, `--buffered 0|1`, `--versao-proto N`, `--sem-ecu`, `--obj80D 1`, `--patch ADDR=BYTE`, `--calibracao`, `--json`).
