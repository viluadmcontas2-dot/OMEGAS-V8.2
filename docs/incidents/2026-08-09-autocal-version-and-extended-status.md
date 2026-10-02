# Incidente — forma AutoCal sem versão + status 0xCA genérico

## Sintoma e impacto
Quatro vetores AutoCal aceitam 18 ou 30 elementos conforme variante, mas o V8 importado não lia `MODULE_VERSION 0x0173` para validar a forma esperada. Em paralelo, respostas estendidas `0xCA` eram preservadas em bytes, porém o handshake tratava a classe de forma genérica.

## Causa estrutural
Decodificação de bytes e política de variante/recuperação estavam incompletamente separadas: o decoder era tolerante, mas faltava contexto de versão; o transporte preservava frame, mas faltava semântica operacional comprovada.

## Correção
- `MODULE_VERSION 0x0173` read-only entra primeiro no snapshot;
- versão 4 exige 30 elementos nos quatro vetores dinâmicos; versões conhecidas diferentes exigem 18; versão ausente não inventa forma;
- `0x015B/0x015C` permanecem 18×U16;
- `UsbProtocolReply` classifica ACK, extended retryable, extended non-retryable, extended unknown e unknown;
- `CA 01 08` segue recuperação controlada; `CA 01 10` e CA desconhecido não recebem retry cego;
- frame bruto continua preservado;
- nomes de handshake foram neutralizados onde a finalidade exata não está comprovada.

## Testes
- `AutoCalProtocolTest`;
- `AutoCalSnapshotTest`;
- `UsbProtocolReplyTest`;
- `tests/test_mp48_extended_status_contract.py` no fast gate.

## Risco residual
Comportamento em variantes de ECU e respostas reais continuam **AGUARDANDO VEÍCULO**. A classificação foi baseada no corpus forense já documentado; novos códigos CA devem permanecer unknown até evidência nova.