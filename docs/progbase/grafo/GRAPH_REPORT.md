# Graphify do ProgBase — relatório

Gerado por `tools/progbase/grafo_progbase.py` (determinístico). Consulta: `graphify query "TFormDiagnosi" --graph docs/progbase/grafo/graph.json`; `graphify path "TFormDiagnosi" "SendCommand" --graph docs/progbase/grafo/graph.json`; índice humano: `docs/progbase/INDEX.md`.

- Nós: 11848 · Arestas: 14238 · Órfãos: 4733 (críticos: 0)

## Nós por tipo
- arquivo_omegas: 7
- captura: 23
- comando: 34
- controle: 740
- doc: 57
- formulario: 43
- funcao: 10458
- opcode: 13
- reconstrucao: 3
- resposta: 41
- unidade: 40
- vetor: 389

## Arestas por relação
- abre: 59
- chama: 9905
- cita: 15
- contem: 740
- definida_em: 40
- dispara: 1317
- documentada_em: 3
- equivalente_no_omegas: 17
- escreve_com: 389
- espera_resposta: 41
- implementado_em: 38
- le_com: 389
- menciona: 1238
- reconstroi: 9
- rotina: 14
- visto_em: 24

## Mais conectados
- TFormConfig (Configurazione veicolo) (511)
- TFormConfig.md (268)
- SendMessageW @0x00A5077C (243)
- TAebCheckBox.set_Caption @0x007EE0E0 (170)
- opcode 0x29 (170)
- TAebCheckBox.get_Caption @0x007EE08C (164)
- opcode 0x09 (156)
- TFormConfig.ControlMouseDown @0x004C6020 (145)
- TFormConfig.EditGiriCambioEnter @0x004A98D8 (144)
- TFormConfig.EditGiriCambioExit @0x004A9CC0 (140)
- TAebCheckBox.set_Visible @0x007EDFA8 (138)
- opcode 0x37 (128)
- funcionalidades.md (119)
- TBrush.set_Color @0x007900B4 (116)
- TFormConfig.EditRifLambdaKeyPress @0x004A6A00 (112)

## Fontes ausentes na geração (tolerado; rode de novo quando existirem)
- docs/progbase/grafo/ligacoes_manuais.json

## Órfãos críticos
- nenhum
