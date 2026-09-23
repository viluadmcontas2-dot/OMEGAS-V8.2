# Verde × Amarelo × Atlas — comparação pós-fechamento

Snapshot canônico: 2026-09-23

## Resultado

### Atlas — autoridade científica + árbitro de consumidores
- ProgBase e Portmons canônicos hash-bound.
- **1417/1417** alvos semânticos PROVEN.
- **14/14** gates comportamentais PROVEN.
- RTTI/VMT/DFM, disassembly, Portmon, producer/consumer, render e scheduler reconciliados.
- Agora também valida replay de consumidores e detecta deriva semântica.

### Amarelo — melhor ideia reaproveitada: replay end-to-end
O Atlas validou o replay do HEAD `60505375f0f5334f6bcfb23ce8c2b0a88bbbcadf` novamente contra o LOGNOVO bruto:
- **964/964 transações** coincidem em índice, request e response;
- checksums/echo válidos;
- diferença temporal máxima após arredondamento: **0,0005 ms**;
- o replay passa a ser um challenger de compatibilidade do Atlas, não uma nova fonte de verdade.

### Verde — melhor fonte de integração/UX/campo, mas não oracle
O HEAD `47d75ce19b1b1f10377a742d87c379f970e7de47` continua útil como produto e fonte de incidentes reais. Porém o detector Atlas encontrou **5 conflitos** no fixture de action-map frente ao EXE canônico:
- Manual AutoMatch;
- Reset Petrol;
- Reset Gas;
- Reset All;
- Modify Map Refs.

Atlas registra a deriva; não a importa.

## Arquitetura resultante

`ProgBase EXE + Portmon/LOGNOVO bruto → Atlas proof → replay/compatibility challengers → Verde/Amarelo`

Isso combina o melhor dos três sem misturar autoridade:
- **Atlas:** verdade e fechamento.
- **Amarelo:** prova de replay/runtime.
- **Verde:** produto, segurança, UX e evidência de campo.

O consumidor pode revelar um defeito, mas não pode reescrever a semântica original sem nova prova canônica.
