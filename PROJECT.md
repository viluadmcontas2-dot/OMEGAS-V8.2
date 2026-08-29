# Projeto OMEGAS V8.2

## Objetivo

Entregar um aplicativo Android automotivo pragmático para observar a equivalência entre gasolina e GNV, aprender com evidência real e orientar calibração manual segura.

## Produto ativo

- Dashboard/Agora: telemetria essencial e equivalência atual.
- Aprender: cobertura e evidência em `RPM × MAP`.
- Predictor: diagnóstico com estados direto, previsto, desconhecido e abstention.
- Sugestões: tradução da evidência em propostas revisáveis.
- Mapa K: célula física da ECU em `RPM × Petrol Inj.`.
- Curva K: tendência global por tempo de injeção.
- Ferramentas: sessão, logs e diagnóstico técnico.

OBD não é requisito de evolução desta versão. Código legado pode permanecer até remoção explícita e testada, mas não deve gerar trabalho novo nem competir com o fluxo principal.

## Contrato científico ativo

- `(RPM, MAP)` define a região operacional comparável.
- Em gasolina, o aprendizado constrói `Tinj_petrol_ref(RPM, MAP)`.
- Em GNV, o sistema observa `Tinj_petrol_on_CNG(RPM, MAP)` na região correspondente.
- A equivalência/resíduo nasce da comparação desses tempos de injeção sob suporte RPM×MAP compatível.
- `RPM × Petrol Inj.` é a geometria downstream para localizar a célula física do Mapa K conforme os eixos vivos da ECU.
- Temperatura, ΔP e contexto ambiental não são eixos do Mapa K nem requisitos primários de matching nesta WorkUnit; podem permanecer como diagnóstico offline de robustez.

## Segurança

Observação e aprendizado são passivos. Operações de ECU são manuais e separadas: preparar → revisar → gravar → verificar ACK/readback. Predictor continua fail-closed quando risco/suporte não estão provados.

## Plataforma

Android landscape, alvo físico 1280×720, ABI de release atual solicitada `armeabi-v7a`, JDK 17, SDK Android 35. A WU-005 comprovou que o artifact atual não empacota bibliotecas nativas, portanto a ABI solicitada não deve ser confundida com `APK_NATIVE_ABIS` efetivamente presente.

## Governança ativa

Repo-first.

- WorkUnit ativa: `docs/workunits/OMEGAS-WU-006.md`
- Issue: #7
- Branch: `work/wu-006-calibration-science-hardening`
- Plano: `docs/plans/2026-08-29-wu006-calibration-science-hardening.md`
- Release funcional anterior: WU-005, preservada como provenance histórica.

Retomadas devem começar por `AGENTS.md` → `PROJECT.md` → `STATUS.md` → WU-006 → Issue #7, nunca pela memória do chat.