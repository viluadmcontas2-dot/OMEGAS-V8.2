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

Trabalho concorrente já na branch (2026-10-13, outro agente; ler antes de tocar nos mesmos temas, não refazer): `docs/superpowers/specs/2026-10-13-obsidian-autocal-contextual-surfaces-design.md` (superfícies contextuais do AutoCal, refino estreitado ao residual por RPM), `docs/evidence/2026-10-13-sessaoutil-rpm-residual-investigation.md` + `tools/autocal_refine/rpm_residual_*.py` (replay causal do residual RPM), `docs/workunits/PLATINA-PROTOCOL-AUDIT-178.md` + `docs/workunits/OMEGAS-WU-006.md` (auditoria de protocolo #178–#181: `AutoCalProtocol.kt`, `KFactorProtocol.kt`, `UsbProtocolReply.kt` alterados com evidência do original — mudanças feitas pelo Codex e aprovadas pelo dono em 2026-10-13; não reverter. A regra de não mexer nesses arquivos vale para este agente, salvo nova autorização).

## Próximos passos, em ordem (2026-10-13)

1. Decompilar `ActionAutoMatchExecute`, `AutoCalMulAct`, `CalcolaWorkingPoint`, `TStrategiaPandaDM` e registrar em `docs/reverse/PROGBASE-AUTOMATCH-<data>.md` o que o original faz com os pares ms×MAP e como escolhe ponto de trabalho; contrastar com `AutoMatchRefinedEngine`/`EquivalenceAttribution`.
2. Inteligência global × local na tela do AutoCal (pedido do dono, foto da bifurcação em Z1): mostrar na tela do AutoCal a atribuição por RPM vinda do Refino (`EquivalenceAttribution`), sem leitura anterior. Prévia PNG antes de implementar.
3. Mapa K perto de 255: quando células saturam, propor renormalização do mapa (base 100) com a curva absorvendo o ganho; um toque, cópia antes, Desfazer depois, "Gravado" só com readback. Decidir a semântica do Mapa K (multiplicativo? neutro 100 ou 128?) com dado do carro ou com o decompilado de `TFormModificaMappa`.
4. CI remota verde no SHA final; um APK (`verde-apk-now.yml`, `build_apk=true`) com SHA-256; descrição do PR #177; teste no carro (classe 5).

## Primeiro item ainda não provado

Comportamento do AutoMatch original (passo 1) e CI/APK do SHA atual; depois validação física no carro.

Resultado da busca REA (2026-10-13, `docs/reverse/evidence/progbase-rea-search-*.json`): só RTTI e exportações; o AutoMatch não tem função nomeada. Para chegar ao código: `xrefs` em `BtnFinishAutomatchClick&` (`0xa9fbe3`, tabela de métodos publicados do form) e em `@TAeb3DMap@CalcolaWorkingPoint$qqrv` (`0xc44801`), depois `decompile` no endereço apontado. `CalcolaWorkingPoint` pertence a `TAeb3DMap` (mapa 3D), não ao AutoCal.


## Atualização 2026-10-13 (tarde) — AutoCal por observação

- `PROTOCOLO-LANDI` @ `84968e3`: docs 17–20 (ciclo AutoCal, escalas, endereços, tela) + `tools/` (Portmon → JSONL). Fontes: `PortmonAUTOCAL (1).LOG` `4a70f5ae…`, `PortmonLOGNOVO.LOG` `43a63272…` (Drive, pasta compartilhada pelo dono).
- Artefato do relatório republicado (v5) com `tools/protocolo-artifact/build.py`; tabela do canal `0x01` do modelo corrigida.
- Divergências do OMEGAS: `docs/evidence/2026-10-13-autocal-original-vs-omegas.md`. Mais graves: zonas deslocadas uma banda (`autocal-cockpit.js:353`, `:120`) e `0x94` = GNV em corte tratado como desconhecido (`Mp48Protocol.kt`). Nada corrigido no código ainda.
- Incógnitas registradas no REA (`record_unknown`): frames dos botões sem captura, "#n/3", quadrados/cores, animações, campos constantes.
- Primeiro item não provado desta frente: frames dos botões Reset petrol/gas, Manual automatch e Reset K no original (exige nova captura Portmon apertando cada botão).

### Atualização 2026-10-13 (noite) — protocolo gráfico do original

- `PROTOCOLO-LANDI` @ `bd1c65d`: docs 21 (protocolo gráfico) e 22 (registro de 393 parâmetros), `dfm/`, `reverse/ghidra/`, `tools/`, `evidencia/`. Artefato do relatório republicado (v6, mesma URL).
- **Provado no código do `ProgBase.exe`:** Reset gasolina/GNV/all e Manual automatch = `SendCommand(0x24, {04, modo})` com modos `01/02/04/08` (`push 1/2/4/8` em `0x5189C0/CC/D8/B4`); zonas por banda `0–4/5–8/9–12/13–17` com fronteiras `THD[5/9/13]`; rótulo `#n/d` = entradas ≠ 0 do buffer de MAP do combustível em uso ÷ limiar; painel Acquisition = contador ≥ limiar; curva K editável por arrasto (grava `MUL_ACT[i]` ao soltar, `0,1 < K < 10`); apagar pontos = `0x016D/0x016E`; desligar o Enable exige Sim/Não com aviso de que a curva K deixa de atuar.
- Divergências do OMEGAS (12 defeitos + conflitos com invariantes): `docs/evidence/2026-10-13-autocal-original-vs-omegas.md`, com imagem lado a lado. Nada corrigido no código.
- Emulação: `orig_emulator.py` (tela original por instante da captura) e `render_omegas.cjs` (a `index.html` real do OMEGAS com dados da captura) no PROTOCOLO-LANDI/`tools`.
- Incógnitas que ficaram (REA `record_unknown`): cor NACQ padrão, pontos vermelhos pequenos e "+" verde da S2, critério da ECU para acender as flags de zona e para o limite de passo do K, animações.
- Primeiro item não provado desta frente: comportamento do OMEGAS **no carro** com as correções de zona/`0x94`/K a cada ciclo (nada foi alterado ainda; precisa de prévia PNG aprovada antes de mexer na tela).

## Atualização 2026-10-13 (noite, 2) — branch `OmegasVermelho`

- Branch nova `OmegasVermelho` (a partir de `work/platina-obsidian-enxuto` @2796780) nos dois repositórios; as outras branches ficaram intactas. Nenhuma branch será apagada sem ordem do dono.
- PROTOCOLO-LANDI @`8e09b04`: docs 17–24 (AutoCal completo, protocolo gráfico, comunicação, inventário de 32 telas), emulador de ECU (9 testes), emulador da tela original, renderizador de telas do DFM. Artefato publicado v8 com esse estado.
- AGENTS.md corrigido: modos da ação AutoCal (1 gasolina, 2 GNV, 4 Reset all, 8 manual); o texto "0x04 = reset de gás" estava errado.
- Não alterado ainda: código do OMEGAS. Conflitos com invariantes do dono (GasPointPrev, Reset all, Enable em 1 toque) seguem para decisão na auditoria (`docs/evidence/2026-10-13-autocal-original-vs-omegas.md`).
- Primeiro item não comprovado: efeito da ECU dos modos 1/2/8; critério dos patamares do K; anomalia do releitura do Mapa K (doc 23).

## Atualização 2026-10-13 (noite, 3) — tensão da ECU e Export→Mapa K

- Faixa de status ganhou **TENSÃO ECU\*** (byte 19 ÷ 16, provisório, "—" na gasolina, alerta ≥ 14,1 V). RED→GREEN: `tests/ui/status-tensao-ecu.test.cjs` (4 testes). Suíte `tests/ui`: 199 passam, 1 falha já existente (`learning-jvm-payload` precisa do resultado do Gradle, ausente neste ambiente).
- Primeiro item não comprovado: a escala ÷16 do byte 19. Comparar com o módulo OBD no celular (mesmo instante, GNV ligado). Se não bater, remover o campo ou trocar a escala.
- Decisão do dono registrada no AGENTS.md: sem `ExportToK` do original; interpolação curva K → Mapa K por escritas manuais do Mapa K.
- Não feito: rotina de interpolação curva K → Mapa K; aviso antes de Reset K/Reset all. Entrada prevista: `MapKManualPlanner.kt`.

## Atualização 2026-10-13 (noite, 4) — tela AutoCal fiel ao original, em tempo real

- Novo `screens/autocal-original.js` (modelo + SVG): zonas por banda **0–4/5–8/9–12/13–17** (o OMEGAS usava 0–5/6–9/10–13/14–17, deslocado uma banda), fronteiras `THD[5]/[9]/[13]`, eixos fixos (X 1…último nó, Y 0,15–1,10), rótulos `#n/d`, painel Acquisition, curva K (0,4–1,6), limites MIN/MAX, banda atual e ponto de operação. Desenha direto dos vetores; **não espera "referência utilizável"** (causa provável do gráfico que travava depois de Reset/épocas incoerentes: ele caía em "SEM REFERÊNCIA"/"comparação suspensa"). Sem GasPointPrev, sem Manual automatch, sem Finish.
- `autocal-cockpit.js`: `zoneForBand`/`zoneSurface` corrigidos; botão e rótulo de Manual automatch removidos (dono); o gráfico usa o novo desenho quando há eixo e limiares.
- Testes: `tests/ui/autocal-original-parity.test.cjs` (13, com fixture da captura real e verificação por mutação das bordas das zonas); 3 testes antigos ajustados porque fixavam o erro de zona/Manual automatch. `tests/ui`: 212 passam, 1 falha já existente (`learning-jvm-payload`, precisa do resultado do Gradle). Python 208 OK. `SEM_ROLAGEM_8_ABAS=PASS`. Imagens em `docs/evidence/img/2026-10-13-autocal-omegas-original-fiel-*.png`.
- NÃO comprovado: o travamento real ao clicar Reset gasolina/GNV (só existe no aparelho). A causa acima é hipótese; falta rodar no aparelho. Reset all continua exposto em "Ações avançadas" (AGENTS.md diz que não deveria; testes antigos dizem que sim): decisão do dono pendente.
- Falta: aviso antes de Reset K/Reset all com a tensão; rotina curva K → Mapa K; gravar nó da curva K por arrasto (não será feita: o dono não quer escrita da curva sem Mapa K); ler `0x018D/E` e `0x0161` a cada ciclo (itens 4–7 do doc de divergências).

## Correção 2026-10-13 (noite, 5) — risco do reset da curva K é condicional

Texto anterior ("zerar a curva faz a correção explodir") estava largo demais. Certo, segundo o dono: só explode se a curva estava exercendo correção grande. Com a correção no Mapa K (~220) e a curva em 1,0, o reset da curva é irrelevante e o AutoCal só refina. O aviso antes de Reset K deve depender do afastamento atual da curva em relação a 1,0 (ex.: max|K−1|), não ser fixo. AGENTS.md e comentário da faixa de status corrigidos.

## Atualização 2026-10-13 (noite, 6) — tensão no AutoCal, arrasto na Curva K, Curva K → Mapa K

- Tensão da ECU (byte 19 ÷ 16, provisória, alerta ≥ 14,1 V) agora também na barra do AutoCal (`autocal-cockpit.js`, `tests/ui/autocal-tensao-ecu.test.cjs`). O aviso condicional antes do Reset K foi **dispensado pelo dono** (não fazer).
- Aba Curva K (Ajuste global): arrasto de nó; durante o arrasto só o nó e a linha tracejada se movem (zero redesenho, medido no Chromium: `tools/ci/curva_k_arrasto.cjs`); soltar prepara a prévia (nunca grava).
- `core/curve-to-map.js` + botão "Curva K → Mapa K": fórmula do `ExportToK` por desmontagem (PROTOCOLO-LANDI doc 26); propõe nas células, gravar segue o fluxo manual. `tools/ci/curva_para_mapa.cjs` no app real (demonstração).
- Corrigido erro antigo: o botão dizia "alteraçãoões".
- Não comprovado: F = 2560 (escala do eixo de linhas do Mapa K), a 13ª linha do Mapa K, e tudo que depende do aparelho (travamento ao resetar, escala da tensão).

## Atualização 2026-10-13 (noite, 7) — fechamento da rodada

- Curva K: teto de escrita **1,8** (Kotlin `KFactorProtocol.MAX_WRITE_*` + JS). CI remota `ci.yml` (dispatch na branch) verde em `b91e760`: contratos rápidos, testes JVM e lint.
- AutoCal: bolinhas de estado no topo (verde completa, azul coletando, laranja falta; uma por AutoMatch); gasolina vermelha, GNV verde, nada roxo; K de 0,4 a 2,0 com linha do teto 1,8; área de toque maior (celular). Manual automatch e Finish fora da tela (dono).
- Previas enviadas ao dono: `docs/evidence/img/previas-2026-10-13/`. Skill do método: `.claude/skills/emular-por-observacao/` (+ artefato).
- REA: `compare_application_versions` entre `2796780` e a branch confirmou 2 módulos novos (`autocal-original.js`, `curve-to-map.js`) e os arquivos alterados; desconhecidos residuais registrados com `record_unknown`.
- Mantido por decisão: leitura da curva K e de `0x018D/E` a cada 4 s (contratos de tempo real); K só muda em automatch.
- Não comprovado: travamento real ao resetar (só no aparelho), escala da tensão (byte 19 ÷ 16), proposta Curva K → Mapa K no aparelho, retrato em celular na vertical (o casco do app não foi feito para isso).

## Atualização 2026-10-13 (noite, 8) — eixo flexível, legenda auditada, evidência do retorno do corte

- Eixo X do gráfico só até onde há aquisição (+1 ms, passo 2, mínimo 6 ms, teto no último nó); linhas das zonas grossas e claras; legenda refeita com os mesmos glifos e variáveis de cor do gráfico; zona não adquirida cinza; bolinhas vermelho/verde.
- Aviso "células passam de 255" na proposta Curva K → Mapa K.
- Evidência: `docs/evidence/2026-10-13-retorno-do-corte-e-inteligencia-local.md` (GNV injeta 12–30 % menos logo após sair do corte, em ~2 ms) + proposta de inteligência local.
- ERRO MEU registrado: o APK `9954128` foi gerado e enviado antes da aprovação das prévias. Regra no AGENTS.md: sem APK sem aprovação.
- Primeiro item não comprovado: causa da interrupção no aparelho; falta uma sessão gravada com a interrupção (o detector da proposta 1 serve para isso).

## Atualização 2026-10-13 (noite, 9) — Curva→Mapa refatorada, corpus das Sessões Úteis, StallWatch v2

- **Curva K → Mapa K** agora refatora o produto Mapa×Curva sem valor fixo (`core/curve-to-map.js` `balance()`, erro ≤ 0,35% com folga; ingênuo errava 12,3%) e grava em 2 etapas (mapa, depois curva neutra, `core/balance-runner.js`, sem regravar se já no alvo). x87 do `ExportToK` original EXECUTADO no Unicorn (`tools/reverse/exporttok_x87.py`): K e Trunc idênticos; F=2560 segue inferido.
- **Corpus das Sessões Úteis** (Drive `OMEGAS/Sessoes Uteis`): `tests/fixtures/sessoes-uteis/` (LEIA-ME, IDs do Drive, teste de integridade); bruto só no Drive; conector: 1 zip por vez, zips > ~6 MB falham.
- **Evidência:** `docs/evidence/2026-10-13-sessoes-uteis-quedas-e-automatch.md` — 13 apagões reais + 26 tropeços (88% com Petrol Inj. mín. 1,5–3,5 ms); a regra antiga contava 5 desligamentos de chave como apagão; a ECU zera as zonas de GNV em todo AutoMatch (14/14).
- **StallWatch v2** (`StallWatch.kt` + `StallWatchTest.kt`): tropeço (kind `NEAR_STALL`, `nearCount/nearRegions` no JSON), chave na lenta não conta, `afterCutoff`. Réplica Python validada no corpus (`tools/sessoes/replay_stallwatch.py`). CI remota disparada em `26a67c0`.
- Coerência visual: gasolina vermelha/GNV verde em Refino e Curva K; gráfico do AutoCal mantém o último estado válido (não reconstrói em reset/zona nova).
- Primeiro item não comprovado: Refino usar tropeços para sugerir (mapa × curva) — NÃO feito; depende do dono aprovar o plano. Também pendentes: auditoria tela por tela, poda, APK (só com aprovação das prévias).

## 2026-10-13 · noite 10 — poda do Aprender + Refino local + gráfico único

- Dono: "podar do código" a aba Aprender (coleta, tolerâncias, grade física) e pôr no lugar o **Refino local** (1 botão, sem navegar); o gráfico do **Refino** deve ser o **mesmo objeto do AutoCal**, sem o painel de aquisição, com nossos pontos/curva/marcas por cima.
- Feito (local → commit desta noite): removidos `screens/learning.js`, `core/learning-model.js`, `components/physical-grid.js` e os testes que só exerciam essas telas; `screens/local-refino.js` (rota `local`: lê Mapa K → escolhe células do engasgo → "Gravar" com ACK+readback → "Desfazer"); `autocal-original.js` ganhou `build(..., {extras})` + grupo `ao-ours`; `refino.js` usa `O.build/renderMain/renderK` e a camada ao vivo; caminho morto `stallStalls` saiu do `map.js`.
- Provas locais: `node --test tests/ui/*.test.cjs` 235/0; `unittest` 207 OK (4 skip); e2e `sem_rolagem_8_abas`, `curva_para_mapa`, `curva_k_arrasto`, `refino_local` PASS. Prévias: `docs/evidence/img/previas-2026-10-13d/`.
- Não provado: comportamento no aparelho (velocidade do cursor, gravação real do reforço), efeito físico do +% no engasgo. APK só após aprovação das prévias.
- Primeiro item ainda não comprovado: CI remota (`ci.yml`) desse commit; Kotlin `StallWatch` com CUTOFF/`getStalls` (commit 72400c4) também sem resultado verificado.

## 2026-10-13 · noite 11 — barra persistente, poda de Agora/Sugestões, aquisição sem ordem
- Dono: barra do topo (tensão, MAP, Petrol Inj., RPM) persistente em todas as abas; podar Agora e Sugestões; abrir AutoCal sem lag; GNV e gasolina adquiridos em qualquer ordem; auditoria de interface.
- Feito: `components/live-bar.js` + `<header id="liveBar">`; 6 abas (AutoCal, Refino, Refino local, Ajuste local, Ajuste global, Ferramentas), padrão AutoCal; quadro rápido em todas as abas (também corrige o cursor AGORA do Refino, que antes não recebia telemetria); poda de `dashboard.js`, `suggestion-model.js`, `curve-prediction-state.js`, `split-layout.js`; sem roxo (+ teste); `NativeAutoCalAcquisitionEpoch.kt`: cada combustível decide por si e o reset vale como aplicado quando os contadores zeram OU caem abaixo do que havia (antes só zero: pendente para sempre se o carro voltasse a adquirir entre duas leituras). Auditoria: `docs/evidence/2026-10-13-auditoria-interface-6-abas.md`.
- Provas locais: node 218/0, unittest OK, e2e sem_rolagem_abas/refino_local/curva_para_mapa PASS, `autocal_abertura.cjs` (≤ 55 ms a 4× de lentidão). Kotlin: testes novos em `NativeAutoCalAcquisitionEpochTest.kt` SEM prova local (sem SDK) — dependem da CI.
- Não provado: aparelho; se a ECU adquire GNV parada em marcha lenta após o reset (causa do "não coleta" pode ser da ECU).
- Primeiro item ainda não comprovado: CI do commit desta noite.

## 2026-10-13 · noite 12 — Refino com botões claros, gráfico coerente, comparação com o original, linearidade
- Dono (offline, autonomia): interações claras ("um botão para gravar na curva e um para gravar no mapa"), sem banner grande, sem botão "Aguardando", gráfico do Refino com UMA lógica, testes de desempenho e comparação com o original por emulação antes de qualquer APK/CI.
- Feito: aba "Refino local" podada (virou o botão "Gravar no Mapa K" do Refino; fluxo em `core/map-fix.js`, lê o Mapa K, mostra o resumo, grava só no 2º toque, conferido, com Desfazer); Refino: botões "Gravar na Curva K" / "Gravar no Mapa K" (com legenda "reforço do engasgo" ou "diferença por RPM") / "Desfazer", só aparecem quando há ação; gráfico = gasolina (referência) + GNV medido por nós (+ GNV hoje na ECU apagado) + ✕/◇; 5 abas.
- Provas locais: node 224/0; unittest OK; e2e `refino_mapa`, `sem_rolagem_abas`, `curva_para_mapa`, `curva_k_arrasto` PASS; `tools/ci/comparar_com_original.cjs` (original por emulador Python do PROTOCOLO-LANDI × OMEGAS, mesmo estado): rótulos #n/d, pontos de gasolina/GNV e faixa de MAP iguais; `tools/ci/desempenho_cursor.cjs` (CPU 4× mais lenta): p50 1,7 ms, p95 3,8 ms por quadro, 0 tarefas longas, gráfico não refeito (Obsidian Enxuto: p95 6,7 ms).
- Protocolo de aquisição (revisão): bytes dos botões iguais ao original (modos 1/2/4/8 via `FUN_00517568`, OBS-COD); só o modo 4 (Reset all) foi visto no fio; 1/2 são DESCONHECIDOS no fio. Nada no app bloqueia a leitura do GNV (a época só marca "pendente", não esconde dados). Teste no carro: Reaprender GNV e olhar `NUM_BUF_UPD_GAS` no log da sessão.
- Ciência: `docs/evidence/2026-10-13-linearidade-gasolina-x-gnv-na-queda.md` — o engasgo do GNV parece transitório (volta de carga alta e depois do corte), sem sinal de mistura pobre antes; +5 % no Mapa K não tem prova física.
- Primeiro item ainda não comprovado: CI desta noite; aparelho.

## 2026-10-13 · noite 13 — poda da Curva K (Aprendizado global/Evidência), ciência só em Ferramentas, CSS morto
- Curva K: saíram "Aprendizado global" e "Evidência técnica" (HTML, `curve.js`, polling). A ciência (`scienceSnapshotSince`) agora só é lida na aba Ferramentas (Mapa K e Curva K não usam).
- `tools/ci/podar_css_morto.py`: removeu 368 seletores/regras sem uso (CSS −30 KB); `tools/ci/capturar_estados.cjs` + `comparar_pngs.py` provam que 9 estados da tela ficaram **pixel a pixel iguais** antes e depois (0 pixels diferentes).
- Provas: node 223/0, unittest OK, `run_checks` PASS, e2e sem_rolagem/refino_mapa/curva_para_mapa/curva_k_arrasto/comparar_com_original PASS.

## 2026-10-13 · noite 14 — legibilidade do gráfico e pulo de tamanho ao abrir a aba
- CIs verdes: 4721c70 (inclui `NativeAutoCalAcquisitionEpochTest` novos) e 7938d37.
- HMI (NHTSA, diretrizes de distração de 2012–2013, não revalidadas): olhadas ≤ 2 s, ≤ 6 toques em 12 s por tarefa, evitar > 30 caracteres de texto. Nossas ações de gravar levam 2 toques (Mapa K) ou 2–3 (Curva K). Letra mínima/alvo de toque não veio da pesquisa (ISO 15008 seria a fonte).
- Gráfico: números dos eixos de 9,5 px → 12 px, rótulo de 0,10 em 0,10 (grade continua de 0,05), eixo K de 0,4 em 0,4. Antes o painel da Curva K aparecia cortado até a releitura de 1 s: o gráfico agora é medido de novo 2 quadros depois de abrir a aba (AutoCal e Refino), tamanho estável nos três caminhos testados.
- Provas locais: node 223/0, unittest OK, run_checks PASS, e2e PASS, original×OMEGAS PASS.
