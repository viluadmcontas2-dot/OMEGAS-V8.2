# Lacunas e plano de fechamento

1. **Desmontagem não executada**: sem endereços de rotinas, fórmula completa do tempo do gás, filtros e predicados de comutação NÃO determinados. Próximo passo: obter `.text`/dump íntegro, fixar image base PE e fazer xrefs a strings/objetos no Ghidra/radare2, guardando VA/RVA e evidência.
2. **Read/write não provados universalmente**: DFM confirma os campos mas não se uma ação os lê/escreve nem o comando MP48. Capturar Portmon individualmente ao abrir/editar cada tela, sem executar escrita na ECU de teste sem autorização futura.
3. **Escala, unidade, faixa, default**: propriedades Coeffs/ConversionTable e DefaultValue não convertidas nesta etapa, mantidas null em JSON. Validar via decoder DFM estruturado e logs de leitura de valores conhecidos.
4. **Colisão de SC por família**: componentes LR e MP48 compartilham SCs com semânticas distintas; identificar por MODELLO_HARDWARE e versão antes de consolidar.
5. **Telemetria**: TFORMVISUALIZZA identificado, mas offsets e escalas dos campos não estabelecidos diretamente no ProgBase. Conferir pacote 0x48 0x01 de 34 bytes com captura sincronizada e Mp48TelemetryScale.kt.
6. **Diagnóstico DHLP/MGLEV**: enumerações e ações ainda não correlacionadas com falhas observadas; capturar estados controlados e localizar despacho no código.
7. **DUMP grande**: `ProgBase.exe.String5s.txt` (~11,8 MB) e `ProgBase.exe.Dump.bin` (~12,6 MB) não foram varridos na íntegra nesta etapa; para próxima fase usar busca em partes e xrefs com offset verificável. Nenhuma fórmula deduzida apenas pelo nome.
8. **Sensores/consumo**: identificar a detecção de tanque cheio, escala de sensor, cálculo de autonomia e integração de consumo via capturas antes/depois de abastecimento e filtragem de ruído.

**Critério de fechamento:** evidência de DFM + endereço de rotina ou captura identificável, comparação cruzada com implementação Kotlin, testes em fixtures offline; nenhuma escrita em ECU.

9. **Divergência de cardinalidade AutoCal**: o recurso `RT_RCDATA(10)__TAUTOCALDM__0.bin`, componente `MUL_ACT` em aproximadamente `0x1861`, declara uma dimensão estática distinta da cardinalidade de 30 pontos aplicada pelo `KFactorProtocol.kt` com base em frames observados. `PETR_INJ_TBP` apresenta situação análoga. Não converter essas dimensões do DFM em contrato universal; correlacionar versionamento, RowCount/ArrayDimension e payloads reais por identidade de field. O inventário distingue metadados declarados de shapes empiricamente medidos.
10. **Endereço compartilhado por subcampos**: SC 276 (FAST/SLOW), 300 (LEDs/histerese) e 313 (TANK_VOL/INJR_GAS_FLOW) aparecem com nomes diferentes. Inspecionar propriedades de índice, FileKeyName e seleção de perfil antes de concluir que são campos independentes no fio. Não autorizar qualquer escrita com base apenas no endereço.
