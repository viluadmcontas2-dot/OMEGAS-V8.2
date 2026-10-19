# Conexão: quando o programa considera a central conectada

**OBS-COD — MAN-PROTOCOLO-CONNECT-00923FDC:** leitura do registro `a=0x00923FDC` em `docs/progbase/decomp/pseudocodigo/pc_13.jsonl.gz`, função exportada `TAebProtocol.Connect`. O identificador MAN aponta para esta análise manual, não para um registro REA. Não houve execução dessa função nesta rodada.

**DERIVADO:** os nomes dos estados abaixo são explicações dos ramos do código, não enumerações recuperadas do programa.

| Estado / condição | Ação observada no código | Próximo estado |
|---|---|---|
| OBS-COD: dispositivo `+0x40` ausente | Retorna falso sem emitir comando | Encerrado |
| OBS-COD: dispositivo presente, porta fechada | Pede abertura e consulta novamente | Porta aberta ou retorno falso |
| OBS-COD: porta aberta | `+0x2B0=0`, `+0x2B1=1`, versão `+0x2C4=0` | Conectando |
| OBS-COD: opção `+0x2BD` ativa | Marca `+0x2B0=1` sem troca serial neste ramo | Notificação |
| OBS-COD: opção `+0x2BD` inativa | Envia `01 00 3A 3B`, lê ACK, comprimento e checksum; `HandleFail` controla repetição | Resultado do Connect |
| OBS-COD: Connect termina com `ComFail(+0x44)=0` | Marca conectado antes de consultar a versão | Consultando versão |
| OBS-COD: Connect falha | Mantém desconectado; fecha porta se `+0x2BF` ativo | Notificação |
| OBS-COD: consultando versão | Envia `00 <+0x2C9> ck`; lê quantidade e bytes; checksum válido copia primeiro byte para `+0x2C4`; `HandleFail` controla repetição | Notificação |
| OBS-COD: notificação habilitada (`+0x26C=0`) | Chama callback ou publica mensagem `0x8190` com ComFail e estado conectado | Finalização |
| OBS-COD: finalização | Eventual callback de texto; zera `+0x2B1`; retorna `+0x2B0` | Encerrado |

**OBS-COD:** falha na consulta da versão não tem, nesta função, uma atribuição explícita de zero ao estado conectado. O sinal “conectado” e a versão conhecida são propriedades diferentes. A versão começa em zero: não interpretar automaticamente esse zero como versão confirmada pela central.

**OBS-FIO (catálogo existente, não replay novo):** `comandos.json` registra quatro Connect com ACK e quatro versões iguais a 2. A resposta à versão é `53 01 02 56`; esses casos não cobrem a falha da consulta nem o ramo `+0x2BD`.

**OBS-COD (catálogo existente):** GetNumber usa `09 lo hi ck` em v2 e `08 end ck` em v0/v1; v1 estende endereço `>=0xFF` com `FF (end+1)&FF`. As demais famílias estão em `comandos.md`; uma versão desconhecida não autoriza escolher v2.

**DESCONHECIDO:** não foi executado um teste de transição real desta função, dos callbacks, de resposta com comprimento zero ou de perda da porta durante consulta. O significado funcional completo da opção `+0x2BD` exige seu setter/chamadores; este documento não a chama de bypass de licença. Identificação de alto nível (`0x00927D80`) e flash (`0x00928E78`) continuam fora desta máquina parcial. Não enviar comandos derivados desta análise a uma ECU real sem confirmar os bytes no log.
