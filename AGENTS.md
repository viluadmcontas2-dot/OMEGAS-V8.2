# OMEGAS V8.2 — contrato operacional

## Autoridade e retomada

1. **REMOTE FIRST:** o ref remoto ativo é a única verdade durável de engenharia. Ler `AGENTS.md` → `PROJECT.md` → `STATUS.md` → WorkUnit ativa → Issue ativa.
2. Continuar sempre de `next_unproven_item`; trabalho já provado não é reaberto sem evidência nova que o contradiga.
3. Uma WorkUnit material = uma Issue = uma branch = um PR = uma trilha de evidência. Não criar genealogias paralelas de auditoria.
4. Chat, memória, Brainbase, plugins, Notion, Linear, sandboxes, `/mnt/data`, caches e executores são superfícies de contexto/execução, nunca autoridade técnica.
5. Uma mutação material só é durável depois de escrita no repositório remoto **e lida de volta** do commit/ref resultante.

## Contrato científico

- `(RPM, MAP)` identifica a condição física comparável; `Petrol Inj.` é a resposta observada/aprendida nessa condição.
- Em gasolina, aprender `Tinj_petrol_ref(RPM, MAP)`; em GNV, observar `Tinj_petrol_on_CNG(RPM, MAP)`; o resíduo compara esses tempos sob suporte físico compatível.
- `RPM × Petrol Inj.` localiza downstream a célula física do Mapa K conforme os eixos vivos da ECU.
- Frame/janela repetidos podem melhorar **precisão local**, mas não fabricam persistência entre sessões. Episódio, sessão e época são massas de evidência distintas.
- Split de validação é cronológico/held-out. Random shuffle de frames/episódios adjacentes é proibido para claims de generalização.
- Temperatura, ΔP e ambiente permanecem diagnóstico/ablação; não viram dimensão primária nesta WU sem falsificação e nova decisão canônica.
- Curva K, Mapa K, AutoCal, superfícies observacionais e sugestões permanecem semanticamente separados.

## Engenharia e evidência

- Mudança comportamental: reproduzir/estabelecer o problema → teste RED pelo motivo esperado → menor implementação segura → GREEN → regressão proporcional.
- Não alterar dado, reduzir cobertura, afrouxar ciência ou mudar métrica só para obter GREEN.
- Falha inesperada exige investigação de causa antes de editar produção.
- Evidência nunca sobe de nível por narrativa: `STATIC` → `UNIT/COMPONENT` → `REPLAY/INTEGRATION` → `ANDROID/RUNTIME` → `VEHICLE`.
- Build/CI/replay provam apenas a camada exercitada. `REPLAY_PROVEN` não significa `VEHICLE_PROVEN`.
- Plugins/skills reduzem risco, mas não ampliam autoridade. Se Codex Engineering Guardrails ou outro router não estiver callable, aplicar `governance/engineering-guardrails.md`; o gate não é pulado.

## Actions-last / economia de prova

Use a menor superfície que realmente prova o claim:

- **T0:** estático, schema, testes sintéticos/pure logic em executor efêmero quando disponível.
- **T1:** testes focados da unidade/contrato alterado.
- **T2:** replay/corpus governado, Python/Node e falsificação offline.
- **T3:** Android/JVM afetado, lint ou integração Android somente quando o claim exigir.
- **T4:** regressão full + assemble/APK apenas no gate de candidato físico/release.

GitHub Actions é permitido seletivamente quando é a melhor superfície de prova remota (por exemplo fixture governado só no repo ou ausência de clone/SDK no host), mas nunca como loop pesado por microcommit. Workflows científicos devem usar `contents: read`, checkout do SHA exato, `concurrency` com cancelamento, timeout, falha visível e artifact/receipt quando houver evidência material. Docs-only não justificam Android/APK.

## Segurança

- UI/UX atual está congelada nesta WorkUnit.
- Predictor permanece fail-closed/ABSTAIN enquanto risco e `P(improve)` não forem empiricamente calibrados.
- Escrita ECU é sempre manual: preparar → revisar → confirmar → ACK → readback. Divergência nunca é sucesso.
- `AUTO_WRITE_ECU=false`; replay/software nunca autoriza escrita física nem claim veicular.
