# Incidente — autoridade serial MP48 fragmentada

## Sintoma e impacto
A telemetria MP48 podia permanecer íntegra por transação e ainda perder fluidez quando Mapa K, Curva K ou AutoCal executavam sequências próprias. Cada manager possuía executor e alcançava `UsbSerialManager.protocolTransaction()` diretamente. O mutex justo do USB impedia colisão de bytes, mas não garantia quando a telemetria voltaria a receber a porta.

## Causa imediata
`ResponseDrivenEcuEngine` possuía sua própria fila, enquanto `KWriteManager`, `KFactorManager` e os managers AutoCal contornavam essa fila e disputavam diretamente o lock final do USB.

## Causa estrutural
Exclusão mútua foi tratada como se fosse escalonamento. A arquitetura tinha proteção de transação, mas não uma autoridade única para prioridade, unidade de trabalho e oportunidade de telemetria.

## Por que os testes não detectaram
Os contratos anteriores verificavam ACK/readback e a presença de proteção serial, mas não proibiam rotas paralelas ao scheduler nem simulavam duas leituras secundárias ou uma unidade `write + readback` sob telemetria contínua.

## Evidência externa
O `PortmonLOGNOVO.LOG` confirma que o ProgBase intercala telemetria rápida com trabalho secundário. Também mostra telemetria enquanto K insertion está ativo e, separadamente, um bloco específico de 144 escritas K sem telemetria no meio. Portanto a unidade correta é semântica: insertion ativo não significa silêncio obrigatório, enquanto certos blocos podem exigir exclusividade delimitada.

## Correção preparada
- `ResponseDrivenEcuEngine` implementa o scheduler MP48 único.
- `SAFETY`, `MANUAL_WRITE` e `READ_ONLY` possuem prioridade explícita e FIFO dentro da classe.
- Mapa K, Curva K e AutoCal recebem o scheduler e não acessam o transporte diretamente.
- leitura observacional é uma unidade e cede oportunidade à telemetria após terminar;
- escrita manual + readback imediato são uma unidade indivisível;
- saída de segurança pode usar prioridade `SAFETY` sem introduzir writer automático;
- uma escrita já confirmada/enfileirada não retorna timeout de espera enquanto continua escondida na fila: espera ACK/readback ou falha real da sessão.

## Teste de regressão
`tests/test_mp48_serial_scheduler_contract.py` prova:
1. nenhuma chamada direta a `protocolTransaction()` fora de transporte/engine;
2. KWrite/KFactor/AutoCal usam scheduler;
3. prioridade/FIFO e oportunidade de telemetria no engine;
4. com o `ResponseDrivenEcuEngine` real compilado em harness: duas leituras secundárias recebem telemetria entre si; `write + readback` ficam consecutivos; telemetria retorna após a unidade.

O gate local completo resultou em `QUALITY_GATE_FAST=PASS`.

## Risco residual
Ainda é necessária validação Android completa e física com MP48/ECU, inclusive durante edição real de Mapa K/Curva K, AutoCal e desconexão no meio de uma unidade. Nenhum resultado físico é afirmado por este incidente.
