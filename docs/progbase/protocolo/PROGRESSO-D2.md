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
