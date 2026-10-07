# Continuação OMEGAS (sessão em nuvem, sem acesso à máquina do dono)

Objetivo: deixar o app redondo e pronto na `OmegasDiamante`. Tudo o que precisa está no GitHub (`viluadmcontas2-dot/OMEGAS-V8.2`). Comece verificando o estado real: `git ls-remote --heads origin`, `gh pr list --state open`, `gh run list --branch OmegasDiamante --limit 3`. Se a sessão tiver um despachante Codex, use-o com escopo fechado; senão execute direto. Diga o que está fazendo antes de cada etapa longa.

## Estado ao encerrar a sessão anterior (confirme antes de agir)
- Mesclados na Diamante: #161 (apagamento automático fora da curva em GNV e gasolina, refino por regime, serial, UI), #162 (achados F1–F14 em `docs/teia/ACHADOS.md`), #163 (temperatura da água, limiar de regime, poda, #152 e parte Kotlin do #158), #164 (garimpo), #166 (replay Export-to-K, relatórios passivos, nota do incidente, overflow do render).
- **Aberto #165** (UI, branch `work/platina-diamante-ui`, commit `8cb6060e`): 8 abas fixas, AutoCal com gráfico grande e barra única, `canWrite`, poda de CSS/JS. O `elastic-browser` (render real) já passou. O último commit corrigiu o que falhava em `build_and_test` e `multimedia-render` (14 blocos de CSS podados por engano, grade do Mapa K de volta a 44 px, texto "regiões medidas" no `RefinoRenderTest`); o CI foi reiniciado e **não terminou** quando a sessão acabou.
- **Aberto #158** (`work/platina-live-acquisition`): falta só o JS.
- 26 branches apagadas, todas com tag `archive/<nome>` no GitHub. Restam: `OmegasDiamante`, `OmegasPlatina`, `evidence/progbase-original`, `main`, `work/platina-diamante-ui`, `work/platina-live-acquisition`.

## O que falta, em ordem
1. **#165:** `gh pr checks 165`. Os jobs `elastic-browser` e `multimedia-render` rodam render real; o log de um job só aparece quando ele termina (`gh api repos/viluadmcontas2-dot/OMEGAS-V8.2/actions/jobs/<id>/logs`). Conserte o que falhar e mescle quando estiver CLEAN.
2. **Portar o JS do #158** sobre a UI nova (`effectiveEpoch` mascara o combustível alvo com RESET pendente; `projectionWarming`) com `tests/ui/autocal-pending-reacquisition-state.test.cjs`; fechar o #158.
3. **Replay do apagamento automático** (classe 3) com as fixtures reais em `fixtures/autocal/real/` (`pista_2026-10-06_2030.jsonl.gz`, `util_2026-10-06_1921.jsonl.gz`; formato no README da pasta). Verifique: apagamentos por sessão; nenhum repetido antes de 5 s; gasolina nunca apagada com o carro em GNV e vice-versa; na sessão `util` compare o que o detector apagaria com os 15 `DELETE_POINT` manuais do dono (bandas 4, 5 e 7 foram as mais apagadas).
4. **Revisão final com o modelo mais forte (uma chamada)** só no código de apagamento: `AutoIdleCleanupCoordinator`, `AutoIdlePointCleaner`, `IdleAcquisitionTracker`, `OutlierCurveTracker`, `AutoCalNativeActionManager.executeAutomaticPointDelete`.
5. **Branches:** apagar `work/platina-live-acquisition` depois do item 2. `main` e `OmegasVerde` só depois de `gh repo edit viluadmcontas2-dot/OMEGAS-V8.2 --default-branch OmegasDiamante`. Ficam: `OmegasDiamante`, `OmegasPlatina`, `evidence/progbase-original`. Antes de apagar: tag `archive/<nome>` enviada e conferida (`git ls-remote --tags origin`). O dono autorizou apagar as desnecessárias com autonomia, desde que tudo esteja na canônica.
6. **Prova final:** cada branch de trabalho em `origin/OmegasDiamante` (`git merge-base --is-ancestor`), nenhum PR aberto, CI da Diamante verde no commit atual.

## Decisões do dono (não reabrir)
- O app apaga sozinho, sem confirmação, pontos do AutoCal fora da curva (GNV e gasolina), com o carro rodando no combustível do ponto; só 5 s entre apagamentos, sem teto por ponto ou sessão. O ponto contaminado volta no mesmo lugar porque parado o carro injeta mais: repetição não prova forma real.
- Navegação: 8 abas de primeiro nível (Agora, Mapa K, Curva K, AutoCal, Ajuste GNV, Sessões, Ferramentas, Diagnóstico); AutoCal independente. Protegido pela regra 11 do `AGENTS.md` e por `tests/ui/nav-contract.test.cjs`.
- AutoCal: uma linha, Pausar aprendizado | Reler GNV | Reler gasolina, um toque, sem menu de opções, detalhes ou histórico.
- O teto 1,50 do MUL_ACT é da ECU; o app não age sobre ele.
- Pisos de toque do projeto (`tests/ui/lote-f-gate.test.cjs`): 44 px na grade, 58 px nos alvos. A poda de CSS já quebrou isso uma vez; não reduza.
- O dono refinará a interface depois; spec do gráfico em `docs/superpowers/specs/2026-10-07-autocal-grafico-informativo-screenspec.md`.

## Para avisar o dono (não alterado)
- `KWriteManager.startBatchWrite` ignora o parâmetro `maxStep` (`@Suppress("UNUSED_PARAMETER")`): o limite de passo por gravação nunca foi imposto; só `MIN_SAFE_K = 100` é.
- A parte 95 da sessão de 02/10 não existe no Drive do dono (partes 94 e 96 sim).

## Ambiente
O CI do GitHub é a fonte de verdade (`gh workflow run ci.yml --ref <branch>`; o render real só roda em branch com nome `work/platina-diamante-ui` ou `work/platina-autocal-coerencia`). Testes locais: `python -B tests/test_*.py` e `node --test tests/ui/<arquivo>`; no Linux as falhas "só do Windows" (`test_poda_2_contract`, `lote-f-gate`) não existem. Gradle exige Android SDK (`local.properties` com `sdk.dir`, ignorado pelo git); se não houver, confie no CI. O GitHub já devolveu `Internal Server Error` em toda escrita por alguns minutos: tente de novo.
