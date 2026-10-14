# Bootloader: pesquisa pública e respostas esperadas

## Resultado

É possível simular o contrato externo do bootloader a partir das respostas que o ProgBase valida. O emulador x86 já exercitava programação, linhas e finalização. Nesta rodada, as rotinas originais de identificação e apagamento também foram executadas com respostas sintéticas estruturadas. A implementação da decifração interna permanece DESCONHECIDA.

### Respostas exercitadas no código original (OBS-COD)

Todas as sequências da coluna resposta vêm depois do eco integral do TX. Os dados de identidade/status são hipóteses controladas do ensaio, não uma captura da ECU.

| Operação | TX | Resposta sintética aceita | Resultado observado no host |
|---|---|---|---|
| Progflash | 93 93 | A7 A7 | sucesso; 4 tentativas diante de ACK negativo |
| Linha S0/S1/S2/S9 | S + tipo + hex completo | A7 A7 ou 53 53 | aceitação; 9 tentativas diante de eco/ACK divergente |
| Endflash standalone | 85 85 | 53 53 | sucesso |
| Cancflash | 00 09 09 | 53 01 00 54 | retorno 0 |
| Cancflash | 00 09 09 | 53 01 01 55 | retorno 1 |
| GetIdentification | 00 02 02 | 53 04 63 03 0B 01 C9 | retorno 1; destino recebe 63 03 0B 01 |

GetIdentification usado somente como teste de formato: ID 63, tipo 03 (boot), família 0B e versão 01 foram escolhidos para o ensaio. Não identificam o hardware real. Cancflash devolve o status; não se afirma comportamento de polling não exercitado. Scripts e recibo em test_additional_replies.py/additional-replies.json. Endereços exportados: GetIdentification 0x923DA4, Cancflash 0x9282B0. Nenhuma porta real foi aberta.

## Pesquisa na internet

Foram pesquisados Landi/Omegas/EVO/AEB, bootloader, dump, ENCRYPTED_ECU_FILE e possíveis famílias de MCU. Não foi localizado um dump verificável do bootloader específico EVO/EVO-L nas fontes examinadas. Isso é limite desta busca, não afirmação de inexistência pública.

### Fontes primárias / materiais encontrados

1. [OpenBLT, porta HCS12](https://www.feaser.com/openblt/doku.php?id=manual:ports:hcs12) e [fonte oficial](https://github.com/feaser/openblt): bootloader de código aberto e exemplos de memória/endereçamento. Não há evidência de identidade com Landi nem confirmação da família exata de MCU dos anexos. Não implementa por associação a cifra proprietária encontrada.
2. [NXP AN3275](https://www.nxp.com/docs/en/application-note/AN3275.pdf): bootloader de referência S12 para programação/erase de Flash/EEPROM e transferência S19. Protocolo/menu de terminal diferente do contrato binário AEB. Serve como referência técnica, não como bootloader da ECU.
3. [Catálogo Landi do distribuidor HybridSupply](https://hybrid.supply/shop_content.php?coID=50&language=de&manufacturer=landi-renzo). Quatro ZIPs públicos foram baixados e inspecionados sem executar programas. Tamanhos e hashes estão em package-inventory.json.

### Achados concretos dos arquivos

- [EVO 479](https://hybrid.supply/downloads/landi-renzo/hsd_9485.zip): um .ple. Removida a camada externa: marca interna V_03.00, continua cifrado.
- [Omegas firmware](https://hybrid.supply/downloads/landi-renzo/hsd_9486.zip): 18 .plr/.pln. Removida a camada externa: todos têm V_01.01, continuam cifrados. A soma aditiva de linhas não implica contagem padrão ou código em claro. Os 19 arquivos novos ampliam o corpus; não forneceram bootloader separado.
- [Plus Upgrade 20110203](https://hybrid.supply/downloads/landi-renzo/hsd_9490.zip): três Intel HEX, LoaderPC e ciphapi.dll. Os três HEX passaram integralmente os checksums e comprimentos; 81.200, 84.336 e 107.876 bytes de dados únicos, entropias entre 6,95 e 7,14 bits/byte e tabelas estruturadas sugerem código em claro. São LC02/Omegas Plus, outra linha; arquitetura, presença de bootloader e equivalência com EVO/EVO-L não foram demonstradas.
- ciphapi.dll (65.536 bytes) exporta CryptInit, DataDecrypt, FileDecrypt etc. Estática: CryptInit admite chaves/blocos de 16/24/32 bytes; foram encontradas S-box e tabela T0 AES/Rijndael em offsets 0x88E0 e 0x8AE0. Isso identifica material criptográfico concreto para investigação. O algoritmo completo/modos e sua relação com a segunda camada EVO não foram validados. A DLL não foi executada.
- [Omegas/EVO 2.16.9](https://hybrid.supply/downloads/landi-renzo/hsd_9489.zip): instalador setup.exe; o ZIP externo não contém bootloader separado. O conteúdo interno do instalador não foi extraído nesta rodada.

## O que a simulação resolve

OBS-COD: o exe descreve como o host reconhece respostas bem-sucedidas, falhas de transporte e formatos de dados. Isso permite executar o host com uma ECU virtual e observar os bytes enviados.

DESCONHECIDO: respostas curtas como A7/53 não contêm a chave ou a transformação ciphertext→plaintext. Um peer que apenas devolve ACK valida o caminho do host, não a decifração ou gravação da ECU. A simulação completa dessa parte precisa do código/dump correspondente, implementação equivalente comprovada ou da inferência criptográfica ainda aberta.

Próximas pistas concretas: conteúdo interno do instalador, handlers/chaves do LoaderPC e ciphapi, e comparação dos HEX legados com o novo corpus. Nenhuma dessas pistas foi promovida a algoritmo EVO confirmado. Todos os arquivos analisados continuam locais; nenhuma ECU foi comunicada e nenhum arquivo foi instalado.
