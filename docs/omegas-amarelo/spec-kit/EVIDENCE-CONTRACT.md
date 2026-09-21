# Evidence Contract — OMEGAS Amarelo

## Hierarquia
1. Tráfego cru observado + binário original para fatos do AutoCAL/protocolo.
2. Replay determinístico extraído do corpus real.
3. Código atual do Verde/SIL como hipótese, implementação ou evidência secundária.
4. Testes sintéticos para falsificação; nunca como única prova de comportamento nativo.
5. Chat/relato humano como sintoma/hipótese, não causa-raiz automática.

## Artefatos obrigatórios
- ProgBase 4.2.0.6
  - SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`
- PortmonAUTOCAL
  - SHA-256 `4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b`
- PortmonLOGNOVO
  - SHA-256 `43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64`

## Regras
- Fixture compacto guarda nome, SHA, recipe e índices/ranges.
- Todo decoder mantém raw bytes junto do valor decodificado.
- Toda relação de consumer graph é `PROVEN | INFERRED | UNKNOWN`.
- Nenhum UNKNOWN é promovido por conveniência.
- Ambos os Portmon entram em todos os gates científicos relevantes.
- O fixture atual do Verde é candidato HARVEST, não substitui o log cru.
- AgentRed pode processar artefatos grandes; o recibo/resultados relevantes retornam ao GitHub.

## Distinções obrigatórias
- ECU native mutation vs host write.
- UI state vs ECU state.
- current curve vs inferred curve.
- raw observation vs stable estimate.
- SIL/replay proof vs physical vehicle proof.