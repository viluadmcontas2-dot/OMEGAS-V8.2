# Passagem de sessão — 07/10/2026 (tarde)

## Feito e salvo
- Mesclados na OmegasDiamante: #165 (8 abas), #167, #168 (JS do #158), #169 (replay do apagamento), #170 (revisão final).
- Branch `work/platina-ui-rodada2` (PR ainda NÃO aberto): UI rodada 2 — Ajuste GNV, AutoCal, Mapa K e Curva K com rodapé único; Sessões sem sobreposição; skill `omegas-ui-fluxo`; regras 12 e 13 no AGENTS.md; testes `btn-bar-rule` e `layout-lock` (verdes).
- Mockups em quadro de rabiscos (artefato claude.ai, uma rodada nova por vez, rascunhos separados por rodada).

## Pendências (ordem)
1. Abrir PR da rodada 2. Ainda falham 4 testes antigos de render que afirmam a estrutura anterior: `fix-ux-render` (aviso sobre botão no Curva K/trilho/botão reservado), `lote-f-render`, `ts-diag-render`, `diamante-layout`. Atualizar de propósito e dizer no commit.
2. Mockups pendentes do dono: Agora, Ferramentas (ok), Diagnóstico (cartões fazem sentido), Sessões v2 (rodada 4, aguardando OK). Botões que abrem cartão (Sugestões, Ver detalhes, Detalhes, Fotos): dono acha ruim; perguntar qual.
3. Branches: tags `archive/` NÃO podem ser enviadas desta sessão (proxy 403). Ainda existem `work/platina-live-acquisition` e `main` (contidas na Diamante; PR #158 guarda o commit em refs/pull/158/head). Dono autorizou apagar; falta fazer (e a branch padrão já é OmegasDiamante).
4. Ajuste GNV/Sugestões nunca geram: causa provável = cérebro recusa condução (≥3 faixas com ≥8 pares) e a tela só mostra "faltam trechos"; dois caminhos de cálculo discordam. Primeiro passo aprovado em mockup: mostrar o motivo exato. Não implementado.
5. Janela entre decidir apagar e enviar (troca de combustível): não corrigida (arquivo congelado); decisão do dono.

## Original (ProgBase)
- `ProgBase.exe` íntegro (SHA-256 8a2d297c…). Cadência no AutoCal (captura real): telemetria ~50 ms; buffers ~2 s; referências ~3,7 s; nenhuma leitura de mapa K/curva K. App fica sem releitura completa por AutoMatch.
- Bancada PRIVADA `viluadmcontas2-dot/omegas-bancada` (exe + ecu_sim + fluxo Wine). Execução local foi bloqueada pelo classificador; dono mandou rodar remoto (GitHub Actions) — run disparado às 17:12 UTC. Resultado: artefato `bancada-resultados`.
- NUNCA colocar o exe em repositório público.

## Ambiente
- Codex: login por device-auth; segredo `CODEX_AUTH_JSON_B64` + script `tools/codex-ensure-login.sh`; Codex Cloud não enxerga o ambiente (verificar workspace). Codex local com ~6% de uso: evitar.
- GitHub às vezes devolve 500 em escrita: repetir.
