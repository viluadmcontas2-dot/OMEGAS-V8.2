# Graphify do ProgBase — relatório

Gerado por `tools/progbase/grafo_progbase.py` (determinístico). Consulta: `graphify query "TFormDiagnosi" --graph docs/progbase/grafo/graph.json`; `graphify path "TFormDiagnosi" "SendCommand" --graph docs/progbase/grafo/graph.json`; índice humano: `docs/progbase/INDEX.md`.

- Nós: 11879 · Arestas: 18659 · Órfãos: 4282 (críticos: 3)

## Nós por tipo
- arquivo_omegas: 11
- captura: 23
- comando: 34
- controle: 740
- doc: 83
- formulario: 43
- funcao: 10459
- opcode: 13
- reconstrucao: 3
- resposta: 41
- unidade: 40
- vetor: 389

## Arestas por relação
- abre: 59
- chama: 9909
- cita: 13
- contem: 740
- definida_em: 40
- dispara: 1317
- documentada_em: 3
- equivalente_no_omegas: 17
- escreve_com: 389
- espera_resposta: 41
- implementado_em: 38
- le_com: 389
- menciona: 5657
- reconstroi: 9
- rotina: 14
- visto_em: 24

## Mais conectados
- funcoes-app.md (2436)
- backlog_funcoes.md (1193)
- TFormConfig (Configurazione veicolo) (530)
- backlog.md (425)
- TFormConfig.md (268)
- SendMessageW @0x00A5077C (243)
- TAebCheckBox.set_Caption @0x007EE0E0 (171)
- opcode 0x29 (170)
- TAebCheckBox.get_Caption @0x007EE08C (165)
- TFormConfig.EditGiriCambioEnter @0x004A98D8 (161)
- TFormConfig.EditGiriCambioExit @0x004A9CC0 (157)
- opcode 0x09 (156)
- TFormConfig.ControlMouseDown @0x004C6020 (152)
- TAebCheckBox.set_Visible @0x007EDFA8 (139)
- TFormConfig.EditRifLambdaKeyPress @0x004A6A00 (129)

## Fontes ausentes na geração (tolerado; rode de novo quando existirem)
- docs/progbase/grafo/ligacoes_manuais.json

## Órfãos críticos
- doc:docs/progbase/decomp/execucao-original.md
- doc:docs/progbase/inventario/funcoes.md
- doc:docs/progbase/protocolo/matriz-comando-resposta.md
