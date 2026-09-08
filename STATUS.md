# Status — OMEGAS V8.2 Blue

- Branch: `work/omegas-blue-causal-engine`
- Estado do código: `RECOVERY_IMPLEMENTATION_COMPLETE`
- Epic: `#18 BLUE-RECOVERY-001`
- Convergência estrutural: `#16`
- Work Unit: `OMEGAS-BLUE-RECOVERY-001`
- Spec Kit: `specs/003-blue-system-recovery/`
- Autoridade matemática: `BlueCausalEngine`
- Escrita automática ECU: `FALSE`
- APK por push normal: `FALSE`
- APK manual sem autorização do owner: `BLOCKED`

## Evidência remota de base antes da reconciliação final
- SHA: `19a116d978d2eed8ece66cc0f71e73b8a1a53bf1`
- OMEGAS Blue CI run `34169619370`: `completed/success`
- `FAST contracts`: success
- `FULL JVM lint`: success
- `Owner-authorized APK artifact`: skipped
- MMMACHINE FAST no snapshot exato: `QUALITY_GATE_FAST=PASS python=22 node=23`

## Recuperações verificadas
- #17: Agora/OBD possuem fallback não vazio, bootstrap real em Chrome e layout essencial sem dependência de `:has()`.
- #19: comparações Blue chegam ao Learning, `quality` é preservada, TRANSITION é gasolina e tolerâncias foram classificadas/isoladas.
- #20: freshness usa timestamp físico, delivery lag fica observável, latest-only/generation reset permanecem limitados, foreground/overlay têm contrato de restore.
- #21: disclosure/foco de Ferramentas sobrevive aos refreshes; browser smoke real cobre a regressão.
- #22: Seleção ON/OFF, drag, destaque, delta e atribuição absoluta têm teste de browser real.
- #23: MP48 continua erro primário; STFT GNV é witness; LTFT não decide; OBD não escreve; Mapa K usa a região GNV atual.
- #16: uma única autoridade Blue, Auto-Cal consome proposta Blue, safety não usa RPM como gate e sessões úteis/vault seguem a Constituição.

## TDD adicional da reconciliação
Foi encontrado um duplicate ingest em `AutoCalJavascriptBridge`: a mesma importação de snapshot chamava `blueIngestLearningSnapshot()` duas vezes. O teste `test_blue_autocal_single_ingest_contract.py` falhou com `found 2 ingests`; a correção mínima deixa exatamente uma chamada e o teste passa. Testes JVM adicionais cobrem log-ratio, ganho causal, revisões Curva/Mapa independentes e PROBE/VALID/PROTECTED.

## Cleanup
- Nenhum `blue-*-apply.yml`, `recovery_apply`, payload/staging transport ou artefato temporário permanece no repo.
- `.github/workflows/red-fast-learning-one-shot.yml` não é transporte temporário: é a CI do branch RED e não inclui a branch Blue.
- `.github/workflows/blue-ci.yml` é a única CI canônica da branch Blue e o APK nela é somente `workflow_dispatch + build_apk=true`.

## Regra de prontidão
Este documento não transforma um comando disparado em sucesso. `READY FOR APK GENERATION` só pode ser declarado fora do repo depois de read-back do HEAD remoto e de uma `OMEGAS Blue CI` `completed/success` nesse mesmo SHA. Nenhuma validação física/economia é alegada por esse gate.
