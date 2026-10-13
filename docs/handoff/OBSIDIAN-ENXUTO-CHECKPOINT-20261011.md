# Checkpoint — Obsidian enxuto (2026-10-11 17:45 UTC)

Registro canônico para retomar sem reconstruir a história. Branch `work/platina-obsidian-enxuto`.

## Objetivo e restrições (dono)

- Base exata do APK "Obsidian" do dono: commit `2c456bca` (APK 8.0.0-test, run 37077178649). Nada da Platina posterior entra sem porte explícito.
- Objetivo único do app: aprender o comportamento da gasolina e deixar o GNV equivalente. O AutoCal nativo da ECU é a base; o OMEGAS observa sozinho, propõe e só grava quando o dono toca (foto antes, readback, Desfazer).
- App para leigo: simples, didático, sem complexidade visual; sem "Detalhes", sem "Entendi", sem botão "Abas"; navegação inferior fixa com 8 abas; nenhuma tela rola nem tem sub-rolagem; gráficos usam o máximo de espaço; botões embaixo, alinhados à esquerda.
- Margem do dono: alvo ±4%, até 5% aceito; nada de ação por 4,1–4,2%.
- Bytes de comando da ECU não mudam (UsbSerialManager, ResponseDrivenEcuEngine, AutoCalProtocol, KFactorManager, KWriteManager, AutoCalNativeActionManager — exceção: trava reset-nunca-pausa, decisão do dono 2026-10-08). Erro de transporte ≠ erro da ECU. Desconhecido = "—", nunca 0.
- Um APK só, no fim (`verde-apk-now.yml`, `build_apk=true`), com SHA-256. Validação física só com o dono no carro.

## Estado verificado (remoto = HEAD `131a7153`)

| Commit | Fatia |
|---|---|
| `a346e200` | navegação inferior; Agora sem cabeçalho |
| `1a9e68eb` | AutoCal sem leitura anterior; Readquirir em um toque |
| `216f1957` | Curva K relê a ECU ao entrar; rodapé único; Backups (N) |
| `b8614f38` | telemetria: tela só repinta o que mudou (AutoCal 942→147 mutações/s) |
| `e2c69330` | AutoCal: gráfico grande, rodapé à esquerda |
| `029de5e6` | trava reset-nunca-pausa-aprendizado (porte Diamante) |
| `d3643657` | refino: verificação com prazo, lenta fora, gasolina da ECU |
| `20a44307` | selo da Curva K só escreve quando muda |
| `5f1df892` | CI: trava "nenhuma aba rola" (`tools/ci/sem_rolagem_8_abas.cjs`) |
| `019cbf57` | sem "Detalhes" no AutoCal/Refino; Curva K com a curva à esquerda |
| `ab8cecf4` | gráficos do AutoCal/Refino na largura toda |
| `cd805290` | cartão AutoMatch X/Y fixo no topo do AutoCal |
| `748aa9ba` | este checkpoint |
| `310ac875` | ponte leve `getPresentSnapshotIfChanged` (como na cinza) |
| `27f8d770` | Aprender e Ferramentas sem rolagem, sem "Detalhes" e sem código cru |
| `b714edb0` | enxugar: 9 arquivos de interface mortos apagados + contrato que impede a volta |
| `ef347b19` | refino rodada 2: sem falso "piorou", sem laço Desfazer/Restaurar, margem 4%/5%, curva só ≥ 1350 rpm |
| `a6703aa8` | dados: resultado do AutoCal visível; desconhecido nunca vira 0; Mapa K sem resultado da Curva K |
| `63f58a81` | UX: fila de sugestões congelada fora da tela; Tolerâncias honestas (só água mínima); textos do Aprender/Sugestões/Ferramentas |
| `5b798567` | perf: AutoCal sem leitura repetida no pacote de 1 s; Ferramentas não relê o disco a cada 2 s |
| `3953de6e` | ponte: gravadores diretos e leituras síncronas sem tela fora do JS; faixa oculta não redesenha |
| `36a3aa6c` | textos para leigo (Agora, AutoCal, Mapa K, Curva K, Refino); Curva K em 1,0 em um toque com cópia antes |
| `7aae7e77` | aviso à direita acima da barra (não cobre os botões da esquerda) |
| `f4eee9a8` | refino: a curva aprende tudo (lenta incluída) e sabe onde ajustar o quê (local separável → Mapa K) |
| `131a7153` | ajuste local do Mapa K pela prévia Kotlin (sem conta no JS) |

Provas locais em `131a7153`: JVM 619/619 + lint 0 erros, node UI 194/194, unittest Python OK, `run_checks` PASS, `sem_rolagem_8_abas` PASS (22 estados), blueprint AutoCal PASS. Replay real: REF E0 — 51 pares de lenta entram, 0 ações locais, teste cego 4,01% (atual 6,69%); AUTOMATCH E2 — lenta +20% não separável vai para a curva (3,5–4,5 ms sobem).

## Decisões do dono registradas (2026-10-11)

- "A curva deve aprender tudo e saber onde ajustar o quê": sem piso de RPM; diferença só vira local (Mapa K) quando separável (≥ 8 pares, ≥ 3 visitas, outras colunas no mesmo t) e > 5% (sai em 4%).
- Botões do AutoCal e do Mapa K sem ECU: não desabilitar.
- Telemetria fora da thread da ECU (F4b): descartada por medição (~43 µs/quadro na JVM).
- Fila V7 de sugestões não exibida (sem alimentador desde a base); o Refino propõe curva e ajuste local.

## Próximos passos, em ordem

1. CI remota verde em `131a7153` (`ci.yml` e `autocal-blueprint-layout.yml`).
2. Um APK (`verde-apk-now.yml`, `build_apk=true`) desse SHA, com SHA-256.
3. Teste no carro com o dono (classe 5): lenta e cruzeiro no GNV, Refino propõe curva/ajuste local, gravação com readback, Desfazer.
4. Ainda em aberto para decidir com dado do carro: gasolina própria primeiro × referência da ECU; semântica do Mapa K (multiplicativo? neutro 100 ou 128? interpola?).

## Ambiente de engenharia reversa (REA) — decisão do dono 2026-10-12: rotina, persistente, via MCP

- `tools/rea/install.sh` instala/atualiza REA 6.4.0, JADX headless 0.7.1 (JAR auditado por SHA-256) e Ghidra 12.1.4 (zip oficial `ghidra_12.1.4_PUBLIC_20260921.zip`, SHA-256 `ddac49f9…`, conferido); exporta `REA_JADX_MCP_JAR`/`GHIDRA_INSTALL_DIR` para a sessão (`CLAUDE_ENV_FILE`); registra e completa o MCP `rea` no Claude Code. Idempotente (reexecução: 7 s). Ferramentas em `~/.omegas-rea/`.
- `.claude/settings.json` + `.claude/hooks/session-start.sh`: hook SessionStart assíncrono (até 15 min) só na nuvem. `.mcp.json`: servidor MCP `rea` do projeto, com as mesmas variáveis.
- Provado nesta sessão (classe 2): `rea doctor` → motor ghidra 12.1.4 disponível; `inspect-android-package`/`search-android-classes` sobre o APK `74ebda24…` (2 566 classes, `com.omegas.v7.test` 8.0.0-test-debug, achou `KFactorManager`); `analyze /usr/bin/unzip --provider ghidra` → ELF x86_64 reconhecido; `search` de strings com endereços de imagem (base 0x100000); `decompile /usr/bin/unzip 0x1072b0 --provider ghidra` → pseudocódigo C real da entrada (`__libc_start_main(FUN_00104b10, …)`). Endereços de análise = endereço no arquivo + 0x100000 (Ghidra carrega com base de imagem).
- Limites: IDA/Hopper não configurados (pagos; Ghidra cobre). Sem USB na nuvem: ADB/ECU real continuam no carro. API de releases do GitHub negada pelo proxy (download direto do asset passa; URL fixada no script).
- Próximo uso: ProgBase original (quando o dono enviar o executável) e APKs autorizados de outros apps de GNV.

## Atualização 2026-10-13 (remoto = HEAD desta branch, PR #177)

Commits depois de `131a7153`:

| Commit | Fatia |
|---|---|
| `39bcc363` | Sugestões mostra cartão da curva + cartão do ajuste local; sem contadores zero |
| `f0b3aa30` | bug do carro: "Reaprender GNV" ficava em 12/18. Época do AutoCal só aceita novo grupo depois que os contadores daquele combustível voltam a 0 (`GAS_RESET_NOT_APPLIED_YET`/`PETROL_RESET_NOT_APPLIED_YET`); AutoMatch nativo não zera nada. Testes em `NativeAutoCalAcquisitionEpochTest.kt` |
| `c5159005`, `a69811a6`, `903860f8`, `c5374039` | ambiente REA persistente (`tools/rea/install.sh`, `.mcp.json`, hook SessionStart) |
| `c8b24f42` | `docs/reverse/PROGBASE-LOGNOVO-RESET-2026-10-12.md`: o log real prova que a ECU zera os contadores na hora no `RESET_ALL` e que o ProgBase nunca usa reset seletivo |

Fatos do ProgBase/log já estabelecidos (não redescobrir):
- `PortmonLOGNOVO.LOG`: 2 631 711 linhas, 39 524 escritas; 0 `RESET_GAS`/`RESET_PETROL`/`MANUAL_AUTOMATCH`; 1 `RESET_ALL` (`02 24 04 04 2E`); `NUM_BUF_UPD_GAS` vai de `[10×16,0,0]` para `[0×18]` no ciclo seguinte; ciclo de leitura ~1,83 s; `AUTO_CAL_ENABLE` 4× liga/4× desliga; nenhuma escrita direta de `MUL_ACT`; todos os endereços lidos já são conhecidos pelo app.
- `ProgBase.exe` (C++Builder, PE32 x86, SHA-256 `8a2d297c…`): unidades `Autocal_dm` (init `0x5134a4`), `Autocal_dm_ee` (`0x513610`), `Autocal_ui` (`0x51b5d4`), `Autocal_settings` (`0x514380`). Identificadores: `TAutoCalDM/_EE`, `TAutoCalUI`, `TFormRifAutocal`, `TFormCalibra`, `TFormCorrCalibra`, `TFormModificaMappa`, `TStrategiaPandaDM`, `TStrategiaTempiMortiDM`, `ActionAutoMatchExecute`, `AutoCalMulAct`, `BtnFinishAutomatch`, `CalcolaWorkingPoint`, `CalcolaCoordinateWorkPoint`, `EditSogliaGiri/Map/Press/Adatta`, `EditNumAutomatch`, campos `MUL_PREV_EE`, `MUL_UPD_CALL_CNTR_EE`, `GAS_POINT_2DELETE`, `AUTO_CALIBR_BYTE_ARRAY/WORD_ARRAY(D)`, `PETR_INJ_TBUFL`.
- Consequência para o app: a seletividade de `0x02`/`0x01` (Reaprender só GNV/só gasolina) nunca foi exercitada pelo original. Teste no carro: AutoCal → Reaprender GNV → Ferramentas → Log do sistema → `autocal_native_action` antes/depois de `NUM_BUF_UPD_GAS`. Se não zerar, só "Recomeçar tudo" é real.

Como usar o REA no ProgBase (lição desta sessão): o MCP `rea` conecta (170 ferramentas), mas no executável de 6,3 MB `search_procedures`/decompile estouram os 60 s do MCP. Use o CLI em segundo plano, com instantâneo:

```
cd <pasta com ProgBase.exe>
REA_GHIDRA_STARTUP_TIMEOUT_MS=3600000 GHIDRA_HEADLESS_MAXMEM=8G \
  npx -y rea-agents@6.4.0 search "$PWD/ProgBase.exe" "AutoMatch|MulAct|WorkingPoint|Strategia" \
  --mode regex --provider ghidra --snapshot "$PWD/progbase.rea-snapshot" --format json
npx -y rea-agents@6.4.0 decompile "$PWD/ProgBase.exe" 0x<addr> --provider ghidra --snapshot "$PWD/progbase.rea-snapshot"
```

Os arquivos (ProgBase.exe, Resources.dll, PortmonLOGNOVO.zip) vêm dos links do Drive do dono ("qualquer um com o link"); baixar com `https://drive.usercontent.google.com/download?id=<id>&export=download&confirm=t`. O instantâneo (`.rea-snapshot`) é da sessão e some com o contêiner: reconstruir custa ~6 min.

Trabalho concorrente já na branch (2026-10-13, outro agente; ler antes de tocar nos mesmos temas, não refazer): `docs/superpowers/specs/2026-10-13-obsidian-autocal-contextual-surfaces-design.md` (superfícies contextuais do AutoCal, refino estreitado ao residual por RPM), `docs/evidence/2026-10-13-sessaoutil-rpm-residual-investigation.md` + `tools/autocal_refine/rpm_residual_*.py` (replay causal do residual RPM), `docs/workunits/PLATINA-PROTOCOL-AUDIT-178.md` + `docs/workunits/OMEGAS-WU-006.md` (auditoria de protocolo #178–#181: `AutoCalProtocol.kt`, `KFactorProtocol.kt`, `UsbProtocolReply.kt` alterados com evidência do original — conferir com o dono que isso foi autorizado, pois a regra é não mudar bytes/classes de comando da ECU).

## Próximos passos, em ordem (2026-10-13)

1. Decompilar `ActionAutoMatchExecute`, `AutoCalMulAct`, `CalcolaWorkingPoint`, `TStrategiaPandaDM` e registrar em `docs/reverse/PROGBASE-AUTOMATCH-<data>.md` o que o original faz com os pares ms×MAP e como escolhe ponto de trabalho; contrastar com `AutoMatchRefinedEngine`/`EquivalenceAttribution`.
2. Inteligência global × local na tela do AutoCal (pedido do dono, foto da bifurcação em Z1): mostrar na tela do AutoCal a atribuição por RPM vinda do Refino (`EquivalenceAttribution`), sem leitura anterior. Prévia PNG antes de implementar.
3. Mapa K perto de 255: quando células saturam, propor renormalização do mapa (base 100) com a curva absorvendo o ganho; um toque, cópia antes, Desfazer depois, "Gravado" só com readback. Decidir a semântica do Mapa K (multiplicativo? neutro 100 ou 128?) com dado do carro ou com o decompilado de `TFormModificaMappa`.
4. CI remota verde no SHA final; um APK (`verde-apk-now.yml`, `build_apk=true`) com SHA-256; descrição do PR #177; teste no carro (classe 5).

## Primeiro item ainda não provado

Comportamento do AutoMatch original (passo 1) e CI/APK do SHA atual; depois validação física no carro.

Resultado da busca REA (2026-10-13, `docs/reverse/evidence/progbase-rea-search-*.json`): só RTTI e exportações; o AutoMatch não tem função nomeada. Para chegar ao código: `xrefs` em `BtnFinishAutomatchClick&` (`0xa9fbe3`, tabela de métodos publicados do form) e em `@TAeb3DMap@CalcolaWorkingPoint$qqrv` (`0xc44801`), depois `decompile` no endereço apontado. `CalcolaWorkingPoint` pertence a `TAeb3DMap` (mapa 3D), não ao AutoCal.
