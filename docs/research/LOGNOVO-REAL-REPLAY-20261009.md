# LOGNOVO real → replay MP48 fiel aos valores (2026-10-09)

**Fonte original:** anexo PortmonLOGNOVO (1).zip, SHA-256 6879fa2a7931d22c207cd7fa47dffb59e1df0fe1de216e34e3f11e0c08cc1c17. O conteúdo PortmonLOGNOVO.LOG tem 149.911.521 bytes, SHA-256 43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64. Coincide com a autoridade registrada nas fixtures do repositório.

## Evidência extraída
- 39.517 transações da captura, parser canônico do projeto.
- 20.451 respostas ao comando de telemetria 48 01 49, sendo 20.450 válidas por ACK, eco, tamanho e checksum.
- O arquivo inclui gasolina, GNV, transição, cutoff, motor desligado e estados não reconhecidos. RPM, MAP e tempos de injeção usam as escalas do Mp48Protocol.kt e Mp48TelemetryScale.kt.
- Reset All sequencial 1.487; contador AutoMatch depois do reset avança 0→1, 1→2 e 2→3 nas sequências 24.622, 27.828 e 30.125. Os bytes e buffers associados já constam na fixture canônica portmon-lognovo-autocal-epochs-v1.json.

## Teia conectada

    LOGNOVO original → SHA verificado → parser Portmon existente
           ↘                              ↓
           recusar arquivo divergente    ACK + checksum + tamanho + eco
                                          ↓
                     combustível, RPM, MAP, ms reais, na ordem real
                                          ↓
                          relógio de reprodução SINTÉTICO
                                          ↓
                      Mock bridge + AutoCal visual 1280×720
                                          ↓
                          SVG e revisão coerentes?
                          ↓ sim              ↓ não
                     evidência visual    diagnóstico da primeira
                          ↓              divergência e novo ensaio
                          └──────────────→ parser/snapshot

## Como reproduzir (offline, sem ECU)

1. Instalar no ambiente o projeto autorizado e ter o LOGNOVO privado em um caminho local.
2. Executar: python3 tools/omegas/lognovo_live_replay.py "/caminho/PortmonLOGNOVO (1).zip" /tmp/omegas-lognovo-live.json --max-frames 6000 --cadence-ms 300
3. Executar: python3 -B tests/test_lognovo_live_replay.py
4. Definir variável de ambiente OMEGAS_LOGNOVO_REPLAY_JSON=/tmp/omegas-lognovo-live.json
5. Usar o Chromium/Playwright disponível e executar tests/ui/autocal-teia.test.cjs e tools/ui_stress/autocal-browser-stress.cjs, com o preparo normal de tests/ui/render/prep.py.

O limite de 6.000 amostra leituras ao longo da sequência para reduzir custo do browser, mas NÃO conserva cada quadro. Sem limite usa os 20.450 quadros válidos. Cada quadro preserva source_sequence e source_event; a cadência dt/t é declaradamente SIMULADA: este formato Portmon não prova um relógio absoluto de condução. Não afirmar latência física a partir dessa reprodução.

## Limites de autoridade e segurança

- O mock visual mistura telemetria real LOGNOVO com snapshot da fixture AutoCal de OUTRA sessão. **Não** é pareamento nativo da mesma sessão nem teste de ponta a ponta de Kotlin/USB.
- O estresse de 4.000 eventos injeta contadores sintéticos para verificar UI e transições. Ele NÃO afirma que a ECU coletou esses pontos no LOGNOVO. A aquisição original nativa deve ser reproduzida separadamente, a partir da fixture portmon-lognovo-autocal-epochs-v1.json.
- Nenhum comando, writer, fórmula da ECU, salvamento automático ou byte de protocolo é alterado. Não commitar o LOG completo, IDs privados nem o ZIP ao repositório público.
- Os 5 testes contratados verificam decoder real, corrupção, hash de referência, contador nativo e parser. O CI GitHub no SHA é a autoridade para integrar.
- Próximo ensaio: acoplar resposta real dos vetores e épocas ao monitor Kotlin, medir evento→revisão→DOM e inspecionar casos em que cursor já mudou de região mas a ECU acabou de confirmar um ponto antigo.
