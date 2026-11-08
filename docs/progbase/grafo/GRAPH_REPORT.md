# Graphify do ProgBase — relatório

Gerado por `tools/progbase/grafo_progbase.py` (determinístico). Consulta: `graphify query "TFormDiagnosi" --graph docs/progbase/grafo/graph.json`; `graphify path "TFormDiagnosi" "SendCommand" --graph docs/progbase/grafo/graph.json`; índice humano: `docs/progbase/INDEX.md`.

- Nós: 12091 · Arestas: 19185 · Órfãos: 4280 (críticos: 1)

## Nós por tipo
- arquivo_omegas: 53
- captura: 23
- comando: 34
- controle: 740
- doc: 122
- evidencia: 2
- evidencia_manual: 74
- formulario: 43
- funcao: 10459
- opcode: 13
- pagina_manual: 55
- reconstrucao: 3
- resposta: 41
- unidade: 40
- vetor: 389

## Arestas por relação
- abre: 59
- chama: 9909
- cita: 123
- cita_evidencia: 95
- contem: 740
- definida_em: 40
- dispara: 1317
- documentada_em: 3
- equivalente_no_omegas: 16
- escreve_com: 389
- espera_resposta: 41
- identifica_pagina: 55
- implementado_em: 38
- le_com: 389
- menciona: 5924
- reconstroi: 9
- rotina: 14
- visto_em: 24

## Mais conectados
- funcoes-app.md (2437)
- backlog_funcoes.md (1194)
- TFormConfig (Configurazione veicolo) (532)
- backlog.md (426)
- TFormConfig.md (269)
- SendMessageW @0x00A5077C (243)
- TFormConfig.EditGiriCambioEnter @0x004A98D8 (171)
- TAebCheckBox.set_Caption @0x007EE0E0 (171)
- opcode 0x29 (170)
- TFormConfig.EditGiriCambioExit @0x004A9CC0 (167)
- TAebCheckBox.get_Caption @0x007EE08C (165)
- TFormConfig.ControlMouseDown @0x004C6020 (159)
- opcode 0x09 (156)
- TFormConfig.EditRifLambdaKeyPress @0x004A6A00 (139)
- TAebCheckBox.set_Visible @0x007EDFA8 (139)

## Fontes ausentes na geração (tolerado; rode de novo quando existirem)
- docs/progbase/grafo/ligacoes_manuais.json

## Órfãos críticos
- doc:docs/progbase/protocolo/matriz-comando-resposta.md
