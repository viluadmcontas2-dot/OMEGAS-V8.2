# Arquitetura OMEGAS V7

## Camadas

1. **UI limpa (`assets/ui`)** — navegação, visualização e intenção humana.
2. **Bridges Android** — API explícita entre WebView e Kotlin.
3. **Serviço de telemetria** — conexão MP48, estado e aquisição.
4. **Aprendizado** — evidências, equivalências e sugestões.
5. **Calibração** — leitura, checkpoint, writer, ACK e readback.

A interface não calcula protocolo, não confirma escrita e não mantém uma cópia paralela da autoridade da ECU.

## Fluxo manual do Mapa K

`Ler ECU → selecionar até 16 células → escolher percentual/incremento → revisar antes/depois → confirmar → startKBatchWrite único → ACK/readback → atualizar mapa`

A caixa de sugestões não participa desse fluxo.

## Fluxo das sugestões

`Aprendizado → advisor → proposta → abrir região no Ajuste → seleção explícita → mesmo fluxo manual`
