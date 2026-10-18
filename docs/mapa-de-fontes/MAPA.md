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
- No repo: tools/obter_progbase.py
- No repo: docs/mapa-de-fontes/progbase.json
- No repo: tests/test_obter_progbase.py
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
- **Como obter:** python3 tools/obter_progbase.py  (baixa pelo link direto do Drive, confere SHA-256, idempotente; o conector do Drive NÃO serve para arquivos >10 MB). Não versionar nem publicar em release do repositório público.
- **Falta:** Decompilação do que ainda não foi lida (ex.: bit da caixa anti-stallo) está em docs/evidence/2026-10-18-anti-stallo-caixa-re.md quando o subagente concluir.

## Capturas Portmon (tráfego serial real do ProgBase × ECU) e DLL do programa
- No repo: docs/mapa-de-fontes/drive-inventario.json
- No Drive: PortmonLOGNOVO.zip 6.029.222 bytes (baixável pelo conector): id 1idvIhV4eFGXv2VVNsU0CT6ewdtTBqNFp (pasta ARQUIVOS OMEGAS); versão de 14.170.842 bytes id 1KdnYj1CdHJTU7hHvVP4OMvrigAp58Ien (>10 MB, usar link direto)
- No Drive: PortmonAUTOCAL.zip 15.150.667 bytes id 1RvXDA5yxU6DoPK14OSLIibYbU9uJ1nBs (>10 MB, usar link direto)
- No Drive: PortmonAUTOCAL.LOG 162.700.984 bytes id 1KzpWOG6Ou0DRgAeA-cOVOplB8ITB87YM (pasta de outro dono, não percorrida)
- No Drive: Resources.dll 3.289.600 bytes id 1cqhbK0bCUf4M06GidwBa_UhgIQ6XO_mm (pasta ARQUIVOS OMEGAS)
- No Drive: firmwares .ple/.pld/.plr/.pln (47) em ARQUIVOS OMEGAS/Landi Renzo Omegas/Firmware; .s19 em Protocolo Landirenzo/firmware
- **Como obter:** Link direto (sem conector): https://drive.usercontent.google.com/download?id=<ID>&export=download&confirm=t — funciona para arquivos compartilhados por link; conferir tamanho/SHA depois.
- **Falta:** Inventário do Drive é MÍNIMO (a paginação do conector repete itens; ver 'nao_listadas' no JSON).

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

## Inventário do Drive
- 1056 arquivos listados; 23 acima do limite do conector (10 MB):
  - VMOMEGAS/Landirenzo.rar (1138807320 bytes, id 1A6dlpRna3ey7hCrAIQ7Bq1OFS0ZAalg4)
  - (busca global; pasta pai 1ZAHZWfDO_lpaqUylZuOkjTqhID4YHLNn, dono viluadmcontas@gmail.com)/PortmonAUTOCAL.LOG (162700984 bytes, id 1KzpWOG6Ou0DRgAeA-cOVOplB8ITB87YM)
  - OMEGAS/SESSOES-zipadas-por-riqueza_rank001-mais-rica.zip (128434296 bytes, id 1SLwAKF7KoCKMJRgCos-HvJqrb3-hIzT6)
  - Apk /AZDOME_4.3.0.200.apk (122233044 bytes, id 1Pv_wfwLSk-dHuOGXxrFh77N3WD-c62UW)
  - Projetos Carro/Copy of AZDOME_4.3.0.200.apk (122233044 bytes, id 1cz6-S_g6YCe9VDBWA8c_nYWUQJjulfml)
  - OMEGAS/Car+Scanner+ELM+OBD2_2.1.50_APKPure.apk (60735056 bytes, id 1jrBthx-99sURNGzX1ZdcEz2HFTB7efiU)
  - OMEGAS/DUMP.zip (21257754 bytes, id 1_ThmJg2IlYuWPUOMQPxqU5YOeqP55F_x)
  - reverse_report/all_strings_utf16le.tsv (16332171 bytes, id 1m_OadHCwEKjicCU5xjwheKzpeOt4Z83w)
  - ARQUIVOS OMEGAS/PortmonAUTOCAL (1).zip (15150667 bytes, id 1mzuPeWlKbV2NX0J2FuIECFYzase5kso3)
  - OMEGAS/PortmonAUTOCAL.zip (15150667 bytes, id 1RvXDA5yxU6DoPK14OSLIibYbU9uJ1nBs)
  - OMEGAS/Sessoes Uteis/PortmonAUTOCAL.zip (15150667 bytes, id 1Goe9C3zOVQQ28h8IsPbrhmmt4igiwewV)
  - Protocolo Landirenzo/PortmonAUTOCAL (1) (1).zip (15150667 bytes, id 1FaoFi9XmFpovhB0oSbNoX5P80jhi1t-4)
  - ARQUIVOS OMEGAS/PortmonLOGNOVO (1).zip (14170842 bytes, id 10s07RSG4Clg1wC0JclzL1azHKIJ7UZE0)
  - OMEGAS/PortmonLOGNOVO.zip (14170842 bytes, id 1KdnYj1CdHJTU7hHvVP4OMvrigAp58Ien)
  - Protocolo Landirenzo/PortmonLOGNOVO (1).zip (14170842 bytes, id 1PPBroyIZ4bgFUpNGFD9xSI4_4aMzvjmO)
  - ARQUIVOS OMEGAS/ProgBase (3).exe (12643840 bytes, id 1VL7Nq5KD2pJB-Ohchy1JG6qVvxRo6Oga)
  - OMEGAS/DUMP/ProgBase.exe.Dump.bin (12643840 bytes, id 1mnya31-24dyInxs77KcVRla24kRptzdP)
  - OMEGAS/DUMP/ProgBase.exe.Dump1.bin (12643840 bytes, id 1khIP-FFt73aW2mDWArgsBNw27Q0vDqI_)
  - OMEGAS/Progbase.exe (12643840 bytes, id 1OO6mzKKD9QLopd-wwIwjcEQn4iEg--Q1)
  - Protocolo Landirenzo/Progbase.exe (12643840 bytes, id 1NZLR6P3CZw19j_orR6Wdy6mOFrMG0SHd)
  - OMEGAS/DUMP/ProgBase.exe.Stri4ngs.txt (11796863 bytes, id 1DuFPDCuPRnQgahLG9jFiLRDn2SL7l7fc)
  - OMEGAS/DUMP/ProgBase.exe.String5s.txt (11796863 bytes, id 1dBl0wEx25BOXMrCWHEM1Bbc8UhigJIly)
  - ARQUIVOS OMEGAS/Landi Renzo Omegas(1)(1)(1).zip (10396178 bytes, id 1JJ2DmE8Z_VD3g0bu6wU5Q2sG0OJugMwx)
- DLLs: 1
  - ARQUIVOS OMEGAS/Resources.dll (id 1cqhbK0bCUf4M06GidwBa_UhgIQ6XO_mm, 3289600 bytes)
- Lista completa: docs/mapa-de-fontes/drive-inventario.json
