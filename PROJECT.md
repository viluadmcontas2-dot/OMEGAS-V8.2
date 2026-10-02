# Projeto OMEGAS V8.2

## Objetivo

Entregar um aplicativo Android automotivo pragmático para observar a equivalência entre gasolina e GNV, aprender com evidência real e orientar calibração manual segura.

## Produto ativo

- Dashboard/Agora: telemetria essencial e equivalência atual.
- AutoCal: coleta nativa da ECU + Equivalência Refinada da Curva K (Gasolina → GNV → Revisar → Gravar e verificar).
- Aprender: cobertura e evidência em `RPM × MAP`.
- Predictor: código preservado, fora da navegação desde a WU-006.
- Sugestões: tradução da evidência em propostas revisáveis.
- Mapa K: célula física da ECU em `RPM × Petrol Inj.`.
- Curva K: tendência global por tempo de injeção.
- Ferramentas: sessão, logs e diagnóstico técnico.

OBD não é requisito de evolução desta versão. Código legado pode permanecer até remoção explícita e testada, mas não deve gerar trabalho novo nem competir com o fluxo principal.

## Segurança

Observação e aprendizado são passivos. Operações de ECU são manuais e separadas: preparar → revisar → gravar → verificar ACK/readback.

## Plataforma

Android landscape, alvo físico 1280×720, ABI de release atual `armeabi-v7a`, JDK 17, SDK Android 35.

## Governança

Repo-first. `docs/workunits/OMEGAS-WU-006.md` controla o refinamento AutoCal atual; a WU-005 permanece como prova do último APK.
