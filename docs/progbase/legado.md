# Inventário de nomes e documentação histórica

DERIVADO — inventário produzido em 2026-10-19 por `git grep -il "platina\|vermelho\|legado\|landi\|aeb"`, antes da substituição dos workflows. Uma correspondência não autoriza excluir evidência. A classificação abaixo é uma proposta verificável; apenas os workflows foram tratados nesta ação.

OBS-COD — CI agora descobre todos os contratos Python e testes JavaScript, em jobs independentes de Kotlin, lint e grafo. Contratos JavaScript que exigirem resultados JVM rodam depois de Kotlin. O status obrigatório `build_and_test` exige sucesso de todos os grupos. Não há disparo automático do APK. O workflow manual `apk.yml` conserva autorização explícita e identidade do SHA; artefato APK com versão e SHA, ABI arm64. Nenhum APK foi compilado.

DESCONHECIDO — execução remota ainda pendente no momento deste inventário. `tools/auditoria.py` não existe; não foi introduzido comando inválido na CI. Release com R8 e assinatura não foi migrada: o workflow mantém build Debug assinado existente. Limpeza de app e migração de documentação exigem ações próprias; não estão concluídas.

OBS-COD — contrato `tests/test_missao_final_contract.py`: RED pela presença de `verde-apk-now.yml`; GREEN após migração, preservando proibições de escrita automática e classificação de lacunas. A mudança não remove testes descobertos.

## Lista completa da busca

| Arquivo | Destino proposto / estado | Justificativa |
|---|---|---|
| `.claude/hooks/codex-primeiro.txt` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `.claude/skills/emular-por-observacao/SKILL.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `.github/workflows/ci.yml` | Concluído nesta ação | CI limpa e dividida; APK manual renomeado para apk.yml. |
| `.github/workflows/verde-apk-now.yml` | Concluído nesta ação | CI limpa e dividida; APK manual renomeado para apk.yml. |
| `AGENTS.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `PROJECT.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `STATUS.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `app/src/main/assets/ui/components/live-bar.js` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/assets/ui/core/advanced-flags-data.js` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/assets/ui/core/curve-to-map.js` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/assets/ui/core/idle-mode.js` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/assets/ui/screens/autocal-cockpit.js` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/assets/ui/screens/autocal-original.js` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/assets/ui/screens/refino.js` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/assets/ui/styles-autocal-cockpit.css` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/assets/ui/styles-calibration-obd.css` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/assets/ui/styles-refine.css` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/autocal/AntiStalloParamsCore.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/autocal/AutoCalBridgeProvider.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/autocal/AutoCalUiProjection.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/autocal/EcuPetrolReference.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/autocal/EquivalenceLedger.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/autocal/RefinementAutopilot.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/autocal/RefinementJournal.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/calibration/KFactorManager.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/calibration/V7CalibrationCoordinator.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/ecu/AutoCalPointDeleteProtocol.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/ecu/AutoCalScale.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/ecu/Mp48Protocol.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/learning/LearningGridProjection.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/learning/LearningTemperatureSettings.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/telemetry/TelemetryStateStore.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/main/java/com/omegas/v7/protocol/AebProtocolFramesV7.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/test/java/com/omegas/prohub/autocal/AntiStalloParamsCoreTest.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/test/java/com/omegas/prohub/autocal/EquivalenceLedgerTest.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/test/java/com/omegas/prohub/autocal/PreAutoMatchStateTest.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/test/java/com/omegas/prohub/autocal/RefinementAutopilotTest.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/test/java/com/omegas/prohub/autocal/RefinementCycleTest.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `app/src/test/java/com/omegas/v7/protocol/AebProtocolFramesV7Test.kt` | Reescrever | Limpar texto/comentários; preservar identificadores de protocolo e contratos até migração testada. |
| `config/omegas-release.json` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/MIGRATION.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/autocal/CURRENT_HOST_PARITY_CHECKPOINT_2026-09-28.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/autocal/progbase-host-parity-v0.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/evidence/2026-09-21-progbase-autocal-byte-consumer-matrix.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-09-28-progbase-autocal-porting-readiness.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-13-auditoria-telas.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-13-autocal-original-vs-omegas.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-13-feedback-teste-apk-7cd4ff9.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-13-noite16-auto-sync-estresse-aquisicao.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-13-o-que-sao-os-quadradinhos-e-xizinhos.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-13-sessaoutil-rpm-residual-investigation.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-16-sessao-18-07-apagoes.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-18-anti-stallo-caixa-re.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-18-anti-stallo-quadro-de-escrita.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-18-anti-stallo.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-18-catalogo-funcoes-originais.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-18-cpu-setter-original.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-18-flags-do-original.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-18-manuais-publicos.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-18-progbase-no-wine.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/2026-10-18-tela-anti-stallo-original.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/PLATINA-ENCODERS-180.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/PLATINA-PROTOCOL-AUDIT-178.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/PLATINA-REPLIES-181.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/PLATINA-TRANSPORT-179.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/codex-2026-10-16/analisar_corpus.py` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/codex-2026-10-16/relatorio-corpus.md` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/dfm-original/TFORMCONFIG.txt` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/dfm-original/TSTREAMDATI.txt` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/dfm-original/i18n_TFORMCONFIG.json` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/flags-do-original.json` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/2026-10-13-autocal-omegas-original-fiel-ln400.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/2026-10-13-autocal-original-x-omegas.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/comparacao-original/comparacao.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/comparacao-original/original.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/config-abas/TabIniezione.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/config-abas/TabInjSettings.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/config-abas/TabInjSettingsSt.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/config-abas/TabKTempMot.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/e2e-1280x720-automatch-1-antes-do-toque.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/e2e-1280x720-automatch-2-depois-do-toque.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/e2e-avancado-1280x720-1-antes-da-leitura.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/e2e-avancado-1280x720-2-desligado.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/e2e-avancado-412x915-8-falha-ignoreWrite.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/e2e-avancado-rotacao-740x360-previa.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/e2e-navegacao-1280x720-autocal.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/e2e-navegacao-1280x720-curve.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/e2e-navegacao-1280x720-sessions.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/e2e-navegacao-800x480-refino.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/e2e-ux-800x480-campos-editaveis.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/previas-2026-10-13/00-comparacao-original-x-omegas.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/previas-2026-10-13c/12-mapa-k-tropecos.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/ux-1024x600-3-anti-apagao-pronto.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/ux-1280x720-12-sessoes-tamanho-das-gravacoes.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/ux-1280x720-6-valor-fora-da-faixa.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/ux-800x480-10-registro-do-app.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/img/ux-800x480-5-valores-do-anti-apagao.png` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/landirenzo-report-manifest-20261012.json` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/progbase-replies-attestation-181.json` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/progbase-replies-saved-project-181.json` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/progbase-retained-protocol-export-v1.json` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/progbase-saved-project-179.json` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/progbase-saved-project-attestation-179.json` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/evidence/rea-progbase-179.json` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/handoff/AUTOMATCH-MANUAL-2026-10-17.md` | Revisar antes de remover | Histórico pode conter decisão ainda vigente; verificar substituto e backlinks no ledger. |
| `docs/handoff/CONTEXTO-DO-DONO.md` | Revisar antes de remover | Histórico pode conter decisão ainda vigente; verificar substituto e backlinks no ledger. |
| `docs/handoff/CONTRATO-CODEX.md` | Revisar antes de remover | Histórico pode conter decisão ainda vigente; verificar substituto e backlinks no ledger. |
| `docs/handoff/OBSIDIAN-ENXUTO-CHECKPOINT-20261011.md` | Revisar antes de remover | Histórico pode conter decisão ainda vigente; verificar substituto e backlinks no ledger. |
| `docs/handoff/PENDENTE.md` | Revisar antes de remover | Histórico pode conter decisão ainda vigente; verificar substituto e backlinks no ledger. |
| `docs/handoff/PLANO-2026-10-16-inteligencia-ponta-a-ponta.md` | Revisar antes de remover | Histórico pode conter decisão ainda vigente; verificar substituto e backlinks no ledger. |
| `docs/handoff/RETOMADA-2026-10-16.md` | Revisar antes de remover | Histórico pode conter decisão ainda vigente; verificar substituto e backlinks no ledger. |
| `docs/incidents/2026-08-12-autocal-manual-automatch-fidelity.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/manual/OMEGAS-guia-do-app.html` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/manual/OMEGAS-guia-do-app.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/mapa-de-fontes/MAPA.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/mapa-de-fontes/drive-inventario.json` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/mapa-de-fontes/progbase.json` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/mapa-de-fontes/topicos.json` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/platinum/AUTOCAL_ACTION_PARITY.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/platinum/DUMP_AUTOCAL_COHERENCE.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/platinum/README.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/platinum/evidence/autocal-maturity-boundary-2026-09-28.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/platinum/evidence/reset-k-factor-progbase-2026-09-28.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/progbase/RETOMAR.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabCambio.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabCambio.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabCorrettori.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabEmissioni.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabEmissioni.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabKCoefficienti.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabKFactor.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabKFactor.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabKOpenLoop.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabKOpenLoop.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabKPressDiff.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabKTempMot.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabKTempRid.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabKTempi.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabKTempi.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabKTempiEx.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabKTempiEx.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabLambda.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabLambda.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabPressAss.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabPressione.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabPressione.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabSensore.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabSensore.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabTemp.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/TabTemp.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/abas/_i18n_usado.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/decomp/COBERTURA.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/decomp/alvos_decomp.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/decomp/execucao-original.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/decomp/execucao/06-F5-programmazione.png` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/decomp/funcoes-app.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/decomp/lacunas.tsv.gz` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/decomp/pseudocodigo/pc_00.jsonl.gz` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/decomp/pseudocodigo/pc_03.jsonl.gz` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/decomp/rtti.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/grafo/GRAPH_REPORT.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/grafo/graph.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/backlog.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/backlog_funcoes.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/backlog_lacunas.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/classes.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/classes.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/INDICE.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/TAEBBEEFORMLIGHT.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/TAUTOCALDM.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/TAUTOCALDM_EE.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/TAUTOCALSETTINGS.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/TAUTOCALUI.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/TFFDATAMODULESINGLEARRAY.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/TFORMCONFIG.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/TFORMLANDICONNECT.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/TFORMMAIN.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/TSTRATEGIAPANDADM.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/TSTRATEGIAPANDAUI.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/TSTRATEGIATEMPIMORTIDM.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dfm/TSTREAMDATI.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/dialogos.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/funcionalidades.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/funcionalidades.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/funcoes.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/funcoes.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/i18n.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/i18n.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/menus_atalhos.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TAboutBox.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TAebBeeFormLight.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TAutoCalDM.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TAutoCalSettings.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TAutoCalUI.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormCalibra.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormCentriCelle.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormCentriCelleK.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormConfig.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormCorrCalibra.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormDiagnosi.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormFile.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormGraficoScope.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormLandiConnect.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormMain.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormModMappa.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormRifAutocal.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormRiferimenti.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormService.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormSwitch.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TFormVisualizza.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TStrategiaPandaUI.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TStrategiaTempiMortiUI.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/inventario/telas/TStreamDati.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/manual/manual-2.16.4C.txt` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/manual/paginas.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/protocolo/PROGRESSO-D2.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/protocolo/backlog.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/protocolo/backlog.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/protocolo/comandos.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/protocolo/comandos.md` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/progbase/protocolo/vetores.json` | Manter | Pesquisa do programa original; nomes são evidência, não marca do produto. |
| `docs/reference/progbase/ACHADOS-DIRIGIDOS-20261002.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/reference/progbase/CATALOGO.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/reference/progbase/FORMULAS.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/reference/progbase/LACUNAS.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/reference/progbase/README.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/reference/progbase/parametros.json` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/research/omegas-verde/confidence-surface/corpus_audit.json` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/research/omegas-verde/confidence-surface/frozen_gate.json` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/research/omegas-verde/confidence-surface/manifest.json` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/research/omegas-verde/confidence-surface/preholdout_manifest.json` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/reverse/evidence/progbase-rea-search-automatch-2026-10-13.json` | Reescrever/migrar | Preservar evidência; consolidar em docs/progbase antes de retirar origem e atualizar referências. |
| `docs/spec-kits/PLATINA-ENCODERS-180.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/spec-kits/PLATINA-PROTOCOL-AUDIT-178.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/spec-kits/PLATINA-REPLIES-181.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/spec-kits/PLATINA-TRANSPORT-179.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/workunits/OMEGAS-WU-006.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/workunits/PLATINA-ENCODERS-180.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/workunits/PLATINA-PROTOCOL-AUDIT-178.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/workunits/PLATINA-REFINO.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/workunits/PLATINA-REPLIES-181.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `docs/workunits/PLATINA-TRANSPORT-179.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `lab/contracts/README.md` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `lab/contracts/fixtures/obd_mp48_replay.json` | Revisar | Correspondência textual não demonstra obsolescência; confirmar autoridade e referências. |
| `tests/fixtures/platinum-autocal-action-parity-v1.json` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/fixtures/progbase-autocal-action-map-v1.json` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/fixtures/progbase-autocal-consumer-map-v1.json` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/fixtures/progbase-autocal-grid-510df8.bin.b64` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/fixtures/progbase-autocal-resource-defaults-v1.json` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/fixtures/sessoes-uteis/telemetria.csv.gz` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/test_anti_apagao_valores_ecu_emulada.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/test_cpu_setter_original.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/test_missao_final_contract.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/test_progbase_autocal_resource_defaults_contract.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/test_protocolo_artifact_template.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/test_usb_permission_identity_contract.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/test_verificar_protocolo_autocal.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/ui/autocal-ci-no-apk.test.cjs` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/ui/autocal-consumer-graph.test.cjs` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/ui/autocal-didactic-cockpit.test.cjs` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/ui/autocal-original-parity.test.cjs` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tests/ui/curva-k-para-mapa-k.test.cjs` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/ci/comparar_com_original.cjs` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/ci/gerar_flags_avancado.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/emulacao/cpu_setter_original.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/emulacao/dfm_renderer.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/emulacao/ecu_emulator.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/emulacao/flags_do_original.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/emulacao/regras_tformconfig.json` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/mapa_de_fontes.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/abas_a.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/abas_b_emul.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/abas_b_re.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/abas_emul.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/abas_re.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/abas_textos.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/dados/cobertura_externa.json` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/dados/descricoes_handlers.json` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/dados/descricoes_telas.json` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/dados/funcionalidades.json` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/inventario_codigo.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/inventario_tudo.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/manual_extrair.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/protocolo_backlog.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/protocolo_comandos.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/protocolo_vetores.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/progbase/protocolo_x87.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/protocolo-artifact/build.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/protocolo-artifact/template.html` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/sessoes/original_do_estado.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
| `tools/sessoes/verificar_protocolo_autocal.py` | Reescrever | Atualizar referências junto com alvo; preservar todas as verificações de segurança. |
