# Incidente — AutoCal expunha Manual AutoMatch sem fidelidade ao comportamento observado

## Sintoma e impacto
O fluxo Android manteve no passado uma ação explícita `NATIVE_AUTOMATCH` com modo `0x08`. A revalidação direta do ProgBase 4.2.0.6 pelo RTTI de métodos + disassembly fecha a identidade real: `0x08 = Modify map refs`, `0x01 = Manual AutoMatch`, `0x02 = Reset petrol point`, `0x04 = Reset gas point`. `Reset all` usa outra rotina (`ActionResetAllExecute -> 0x005185D4`) e não deve ser confundido com `0x04`. Nos Portmons fornecidos, a ECU altera `MUL_ACT` e incrementa o contador de AutoMatch sem um comando host Manual AutoMatch adjacente; portanto continua correto não expor Manual AutoMatch no fluxo normal.

## Causa imediata
A implementação anterior transformou a existência conhecida do modo nativo `0x08` em uma ação operacional exposta ao usuário, antes de existir evidência de que o ProgBase o disparava manualmente no fluxo normal.

## Causa estrutural
O contrato de AutoCal não separava com rigor três responsabilidades: habilitar/pausar a aquisição nativa, observar o estado/contador e reconhecer uma mudança de `MUL_ACT` feita internamente pela ECU. Com isso, uma capacidade de protocolo foi confundida com intenção de produto.

## Por que os testes não detectaram
Os testes protegiam a presença e a confirmação do próprio `NATIVE_AUTOMATCH`; portanto validavam a hipótese antiga em vez de confrontá-la com o tráfego real do ProgBase.

## Evidência nova
`PortmonLOGNOVO.LOG` contém 39.524 escritas seriais e 20.456 consultas de telemetria `48 01 49`. Não há `02 24 04 01 2B` (Manual AutoMatch) junto dos três ciclos automáticos observados; mesmo assim, o contador `0x0174` evolui `0 → 1 → 2 → 3` e `MUL_ACT` muda. O executável original mapeia `ActionAutoCalRifExecute@0x005189B4 -> 0x08`, `ActionAutoMatchExecute@0x005189C0 -> 0x01`, `ActionResetPetrolExecute@0x005189CC -> 0x02`, `ActionResetGasExecute@0x005189D8 -> 0x04`; `ActionResetAllExecute@0x005189E4` segue uma rotina distinta. O frame `02 24 04 04 2E` aparece nos dois Portmons e, no Lognovo, seu antes/depois mostra efeito amplo: buffers/referências de gasolina e GNV zerados e `MUL_ACT` retornando a Q14 1.0. Os frames de controle observados continuam `12 4A 01 01 5E` (Enable) e `12 4A 01 00 5D` (Disable).

## Correção
- remover `NATIVE_AUTOMATCH` do fluxo operacional normal;
- expor Enable/Pause da Auto Calibration nativa com frames exatos e readback obrigatório de `AUTO_CAL_ENABLE`;
- usar `48 0B 53` como probe leve e snapshot completo somente por evento/mudança relevante;
- tratar `0x0165:2` semanticamente como `MAX_AUTOMATCH`, nunca como threshold de maturidade;
- quando contador AutoMatch subir e `MUL_ACT` mudar de verdade no readback, abrir nova época GNV como observação `ECU_NATIVE_AUTOCAL`;
- snapshot pausado/congelado não entra como evidência nova;
- nenhuma observação nativa autoriza escrita do app ou vira automaticamente Mapa K.

## Teste de regressão
`tests/test_native_autocal_contract.py` exige ausência da rota Manual AutoMatch, frames exatos de Enable/Disable/probe, readback do enable flag, semântica `MAX_AUTOMATCH`, ausência de timer/thread serial paralelo e tratamento de snapshot pausado. `tests/fixtures/progbase-autocal-action-map-v1.json` congela a identidade original-derived das ações para impedir nova troca semântica. `AutoCalNativeActionManagerTest` cobre ACK sem readback coerente como falha.

## Evidência de validação
O gate local `tools/run_checks.py` passou integralmente após a integração. O harness de protocolo compila e executa `AutoCalProtocol.kt` real para validar os frames e o decoder do probe.

## Risco residual
A compilação Android/Gradle completa e a validação com MP48/ECU física ainda são pendentes. O penúltimo byte do probe `48 0B 53` permanece com nome neutro até segunda prova independente. O valor de `MAX_AUTOMATCH` deve sempre ser lido da ECU; `3` é valor observado nesta central, não constante universal.
