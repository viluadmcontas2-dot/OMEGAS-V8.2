# Checkpoint D2 (PROTOCOLO) — pausa

Nota: o pedido de checkpoint citava docs/progbase/abas/PROGRESSO-A.md (agente das abas); D2 só escreve em protocolo/, então este é o checkpoint de D2.

## Pronto (gerado e conferido contra pb.exe sha 8a2d297c e o log LN completo de 150 MB)
- tools/progbase/protocolo_log.py, protocolo_dfm.py, protocolo_vetores.py, protocolo_comandos.py (43 checagens de instrução OK), protocolo_matriz.py, protocolo_backlog.py, protocolo_x87.py (rotina original x87 rodada no unicorn: 0 divergências em 4800+ casos).
- docs/progbase/protocolo/: vetores.json/md (389 objetos, 314 em TStreamDati), comandos.json/md (34 comandos), matriz-comando-resposta.json/md (LN e AC; emulador regenera 21506 e 22315 pares, 0 divergentes), backlog.json/md.

## Falta
- tests/test_progbase_protocolo.py (RED->GREEN, skip sem exe/log) — NÃO escrito.
- Docs narrativos: README, framing/enlace, máquina de estados (Connect RE:0x923FDC, ident, v0/v1/v2, fluxo de flash RE:0x928E78), conversões x87, correções ao protocolo-landi, botões/comandos (pedido do dono).
- protocolo_gerar.py (orquestrador idempotente); regenerar comandos.json após últimas edições.

## Armadilhas
- Log usado: portmon/big (150 MB, 39 524 transações); portmon/x (63 MB) é prefixo exato dele.
- Corrigir no protocolo-landi: baud table (valores, não divisores), limiar v1 0xFF, SetNumber responde 53 00 53, SendWord é low-first, SetDuty `02 05 id val`, ComFail bits, canal 48 08 é NAK comum (bit 0x10).
- Quadro 14 xx xx a b c é ambíguo (linha,coluna,1B) x (idx,2B).

## Retomada 2026-10-19 — teste e reprodução parcial
- DERIVADO: criado `tests/test_progbase_protocolo.py`: 7 testes, RED (2 erros por orquestrador ausente) → GREEN (5 passaram; 2 skips sem entradas privadas). Framing/ACK/NAK/retry/checksum, leitura fragmentada/fecho, fórmulas, ordem e interrupção do orquestrador. Só esse arquivo foi rodado.
- DERIVADO: criado `tools/progbase/protocolo_gerar.py`, reaproveitando os quatro geradores; valida entradas antes de criar saída, usa mesmo interpretador e interrompe no primeiro erro.
- OBS-COD: `conexao.md` descreve os ramos de `0x00923FDC` a partir do pseudocódigo já versionado (pc_13); `MAN-PROTOCOLO-CONNECT-00923FDC`. Conectado é marcado antes da consulta da versão; falha da versão não zera explicitamente esse sinal na função.
- DESCONHECIDO: REA MCP indisponível neste worker; nenhum ID REA criado. Catálogos não regenerados e orquestrador completo/idempotência ainda não executados contra entradas privadas. Não declarar a frente completa.
- DESCONHECIDO: ttNone (163/2000) segue aberto; pc_14, 0x009752C8 mostra limites conjuntos e caminhos distintos de Single/sinal; não corrigir só pelo pseudocódigo.

### PRÓXIMO PASSO
1. Com caminhos do executável e logs confirmados, rodar os dois oráculos opcionais e gerar em duas pastas temporárias; comparar bytes e substituir catálogos apenas após igualdade.
2. Reproduzir 163/2000 ttNone com corpus e seed preservados, separar limites de arredondamento/sinal/Single e corrigir com RED→GREEN contra CPU original.
3. Validar a máquina de conexão contra instruções/REA e runtime; completar identificação e flash. Vincular MAN-PROTOCOLO-CONNECT-00923FDC ao INDEX/grafo na integração.
