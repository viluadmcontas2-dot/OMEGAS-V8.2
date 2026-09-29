# OMEGAS Platina — Projeto ativo

## Missão

Entregar o OMEGAS Platina pronto para geração de APK, sem gerar APK nesta missão: AutoCal fiel ao ProgBase/ECU, moderno, claro, recuperável e seguro em multimídia 1280×720.

## Repositório e branch

- Repositório: `viluadmcontas2-dot/OMEGAS-V8.2`
- Branch autorizada: `OmegasPlatina`
- Source authority: GitHub remoto

## Plataforma

Android landscape, WebView, alvo automotivo `1280x720`, MP48/OMEGAS.

## Programa atual

**OMEGAS-PLATINA-FINAL — AutoCal produto + paridade + segurança**

Escopo ativo:
- fidelidade de protocolo e comportamento AutoCal contra DUMP/Portmon/ECU;
- ECU como autoridade do AutoMatch nativo;
- nenhuma escrita automática de K/Mapa por Predictor, AutoMatch ou sugestão;
- mutações somente por intenção explícita, ACK, readback, sessão e recibo;
- UX principal AutoCal superior ao ProgBase, com evidência técnica sob demanda.

## Princípio técnico

O ProgBase original é referência comportamental e semântica. O objetivo não é copiar a UI Delphi: é preservar comandos, produtores, cadência, séries, estados e consumers, apresentando a mesma verdade operacional com menos carga cognitiva e maior segurança.

## UX vinculante para este programa

- intenção humana > subsistema;
- uma superfície dominante;
- estado normal compacto;
- problema ganha espaço só quando necessário;
- contexto preservado;
- detalhe técnico sob demanda;
- feedback imediato;
- ação principal óbvia;
- sem navegação/cliques desnecessários;
- tela principal compreensível em cerca de 2 segundos.

## NON-GOALS

- gerar APK nesta missão;
- reimplementar AutoMatch nativo como writer host-side;
- aplicar Predictor automaticamente;
- copiar UI do ProgBase;
- usar SIL/CIU sem autorização explícita.
