# OMEGAS Blue — Functional Recovery Plan — 2026-09-13

## Regra de execução

Fluxo obrigatório e rastreável: baseline remoto → RED somente contrato/teste/spec → CI falha pela causa esperada → GREEN implementação → FAST/FULL verdes no mesmo SHA → revisão adversarial → gate APK owner-authorized → verificação do artefato → Issues.

## Workstream A — MP48 / #31
1. Separar UUID de evidência de `BlueScientificRegion` versionada RPM×MAP.
2. Gerar identidade científica na comparação e manter matching físico contínuo nas bordas.
3. Endurecer atribuição causal para região física + endereço efetivamente participante.
4. Subir ledger v2 fail-closed para identidade legada.
5. Testar UUIDs distintos, fronteira de quantização, revisão/tempo/ACK/readback e endereço errado.

## Workstream B — Telemetria / #32
1. Freshness da UI independente de mudança visual arredondada.
2. Trocar `setInterval` por scheduler self-paced de um único timeout.
3. Mover persistência OBD para writer coalescido e flush em rotação/shutdown.
4. Coalescer overlay/notificação e reduzir snapshots pesados.
5. Testar backpressure/freshness e contratos do hot path.

## Workstream C — OBD / #33
1. Capturar sessão/época antes do ciclo e rejeitar ciclos obsoletos/duplicados/stale.
2. Exigir READY + GNV + freshness + sessão/época/calibração/região para witness bonificar MP48.
3. Corrigir Mapa K: row=Petrol Inj.; column=RPM.
4. Manter snapshot Map K confirmado para resolvedor; MP48 é somente ponte de endereço.
5. Preparar no máximo uma célula manual, nunca escrever automaticamente.
6. Expor `getObdWitnessStatus` estreito ao browser.

## Workstream D — Consumo / #34
1. Criar `ConsumptionEvidenceEngine` puro.
2. Confirmar km/m³ apenas com distância e volume adicionados válidos.
3. Manter estimativa de pressão separada e explícita.
4. Preservar baseline pré-abastecimento e rejeitar tempo regressivo.
5. Persistir última evidência confirmada e histórico mínimo.

## Workstream E — Release / #35
1. Executar `python3 -B tools/run_checks.py` e JVM/lint na Actions.
2. Corrigir qualquer falha real até FAST + FULL verdes no mesmo SHA final.
3. Revisar diff integrado adversarialmente e preencher matriz requisito→seam→teste→resultado.
4. Reexecutar gate owner autorizado para APK.
5. Conferir SHA/tree/run/jobs, artefato, receipt, unzip, SHA-256, bytes, assinatura e badging.
6. Fechar #31–#35 e #30 somente com recibos; #25 permanece aberto.
