# Inventário (backlog vivo) — atualizar a cada passo

Legenda: [ ] aberto · [~] em andamento · [x] feito · [-] adiado (com motivo)

## Já na OmegasDiamante (mesclado, CI verde)
F0–F4, F6, endurecimento de gravação/Desfazer/serial/evidência (#144).

## Em branches a integrar em `work/platina-integra` (nesta ordem)
- [~] Lotes A+B+C+E (`work/platina-polish`, PR #146): becos do Refino, cursor, ordem de invalidação, histerese/episódios.
- [~] Lote D (`work/platina-acq`): aquisição fatiada, AnalysisLane, revisões por tipo.
- [~] Lote F (`work/platina-ui`): Agora 4 valores, gráfico único (Refino=faixas do meio, AutoCal=18 da ECU), vocabulário/formatos, defeitos visuais, curvas por mudança, cursor leve.
- [~] Lote H (`work/platina-fine`): coleta fina (~48 faixas), `bands18` + `betweenBands`.
- [~] Lote W (`work/platina-wiring`): testes de uso, produtor×consumidor, idempotência, mutantes; `tests/wiring/DEFECTS.md`.

## Depois da integração
- [ ] Lote K (Kotlin de coerência): textos de fase/notificação (EquivalencePhases, NotificationController), RESUMO.md (gap em % como a tela), formatos pt-BR fixos (`Units.kt`), `EquivalenceView` sem vazar índice antigo, remover `MANUAL_AUTOMATCH` morto.
- [ ] Consumir `revisions` por tipo na UI (push `OmegasOnRevision(kind, revision)`), mantendo poll de vigia ≥ 2 s.
- [ ] Zerar todos os `todo` de `tests/wiring/DEFECTS.md`.
- [ ] Revisão de equipe (performance, UX/legibilidade, robustez) sobre o resultado integrado; consenso; lote único de correção.
- [ ] CI único → merge na OmegasDiamante → render no emulador (workflow do CI, uma vez) → APK com SHA-256.

## Adiado, com motivo
- [-] Extrair `RefinementObserver` do `TelemetryForegroundService` e mover pacotes `Equivalence*`: correto, mas arriscado sem SDK local; não muda o uso. Depois do APK.
- [-] "Acumular alvos de GNV entre gravações de K": premissa física não verificada (marcha lenta a contradiz).
- [-] Leitura por contador antes dos buffers: invenção nossa, não existe no original.
- [-] Tempo mínimo de permanência nas fases do Refino: testes antigos trocam de fase sem avançar relógio.
