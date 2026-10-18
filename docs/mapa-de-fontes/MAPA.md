# Mapa de fontes do OMEGAS

Antes de pedir qualquer arquivo ao dono: procure o assunto aqui. Cada fonte diz ONDE está (repo, repo privado, Drive) e se o conector do Drive consegue baixar (limite 10 MB).

Repo privado do protocolo: viluadmcontas2-dot/PROTOCOLO-LANDI (clonar com add_repo; índice 00-LEIA-ME.md)

## ANTI_STALLO (anti-apagão da ECU)
- No repo: docs/evidence/2026-10-18-anti-stallo.md
- No repo: docs/evidence/2026-10-18-tela-anti-stallo-original.md
- No repo: docs/evidence/2026-10-18-apagoes-x-anti-stallo.md
- No repo: docs/evidence/dfm-original/TFORMCONFIG.txt
- No repo: docs/evidence/dfm-original/TSTREAMDATI.txt
- No repo: tools/sessoes/apagoes_x_antistallo.py
- No repo: app/src/main/java/com/omegas/prohub/autocal/EcuParameterSnapshot.kt
- No PROTOCOLO-LANDI: dfm/i18n_all.json (chaves CHECKANTISTALLO, LABELANTISTALLO0..4)
- No PROTOCOLO-LANDI: dfm/TSTREAMDATI.txt
- No PROTOCOLO-LANDI: 22-REGISTRO-PARAMETROS.md
- No Drive: DUMP (1SMZqx2Sd1HKooJ5GVCSuYDPvkCn6R0kH): formulários RT_RCDATA(10)__*.bin extraídos do ProgBase
- **Falta:** Onde a caixa 'Abilitazione anti-stallo' é gravada (código CheckAntiStalloClick): só no ProgBase.exe inteiro ou captura Portmon.

## ProgBase.exe (programa original) e partes extraídas
- No repo: .claude/skills/emular-por-observacao/SKILL.md
- No PROTOCOLO-LANDI: reverse/ghidra/*.c (handlers decompilados)
- No PROTOCOLO-LANDI: tools/dfm.py
- No PROTOCOLO-LANDI: tools/orig_emulator.py
- No PROTOCOLO-LANDI: tools/ecu_emulator.py (se existir)
- No PROTOCOLO-LANDI: 00-LEIA-ME.md
- No Drive: Progbase.exe 12,6 MB: id 1NZLR6P3CZw19j_orR6Wdy6mOFrMG0SHd (NÃO baixável pelo conector, >10 MB)
- No Drive: Progbase.exe (cópia): id 1OO6mzKKD9QLopd-wwIwjcEQn4iEg--Q1
- No Drive: ProgBase (3).exe: id 1VL7Nq5KD2pJB-Ohchy1JG6qVvxRo6Oga
- No Drive: DUMP/ (id 1SMZqx2Sd1HKooJ5GVCSuYDPvkCn6R0kH): formulários .bin, Resources_StringTable.txt (21 KB, baixável), ExportFunctions.txt (83 KB, baixável)
- No Drive: ProgBase.exe.Dump.bin, Stri4ngs.txt, String5s.txt: ~12 MB cada, NÃO baixáveis
- **Falta:** Para decompilar qualquer handler novo: o .exe precisa estar na máquina (zip ~5 MB resolve).

## Sessões reais (telemetria, eventos, curvas K)
- No repo: tests/fixtures/sessoes-uteis/LEIA-ME.md
- No repo: tests/fixtures/sessoes-uteis/drive-ids.json
- No repo: tools/sessoes/corpus.py
- No repo: docs/evidence/2026-10-18-quadro-gasolina-gnv-k.md
- No Drive: Sessoes Uteis (1P4gTYUxCRetkdFD10aCjYHGIko33t3-J) e pacotes por riqueza (ids em drive-ids.json)
- No Drive: Novas (1-I_WZ-dSRblOtSN4BpiUxI1SLqODykJz): 2026-10-16 13-42 (5,6 MB, limite do conector), 18-07, 18-38 (já analisada), aprendizado .omegas das 14-53, archive.zip
- **Falta:** Sessão 2026-10-16 13-42 e o .omegas ainda não entraram no corpus compacto.

## Gasolina × GNV × Curva K (de onde vêm os números)
- No repo: docs/evidence/2026-10-18-quadro-gasolina-gnv-k.md
- No repo: tools/sessoes/gerar_referencia_gasolina.py
- No repo: app/src/main/assets/ui/data/gasolina-referencia.json
- No repo: app/src/main/assets/ui/core/gasoline-reference.js
- **Falta:** Mapa K por sessão ainda não cruzado nó a nó com a gasolina.

## AutoMatch manual, buffers e sonda de escrita
- No repo: docs/handoff/AUTOMATCH-MANUAL-2026-10-17.md
- No repo: docs/handoff/D1-REFINO-AUTOMATICO-2026-10-18.md
- No repo: app/src/main/java/com/omegas/prohub/autocal/BufferWriteProbeCore.kt

## Tudo por evento (nada por tempo) e regras do dono
- No repo: docs/handoff/EVENTOS-SEM-TEMPO.md
- No repo: docs/handoff/FECHAMENTO-2026-10-18.md
- No repo: tests/test_sem_tempo_como_gatilho.py
- No repo: AGENTS.md

## Emulação por observação (receita)
- No repo: .claude/skills/emular-por-observacao/SKILL.md
- No PROTOCOLO-LANDI: tools/dfm.py
- No PROTOCOLO-LANDI: tools/portmon_transacoes.py
- No PROTOCOLO-LANDI: tools/orig_emulator.py
- No PROTOCOLO-LANDI: tools/capture_state.py

## Grafo de conhecimento (graphify)
- No repo: .github/workflows/ci.yml (job grafo)
- No repo: tools/mapa_de_fontes.py
- No repo: docs/mapa-de-fontes/MAPA.md
- **Falta:** Extração semântica de docs (LLM) não roda na CI; o grafo da CI é de código e estrutura.
