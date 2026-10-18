> **ATUALIZAÇÃO 2026-10-18 (dono): o app NÃO grava sozinho.** O modo LIGADO foi removido (serviço recusa `ON`, valor antigo vira SOMBRA, bloco de escrita apagado). O D1 só calcula e registra `d1_decision`; gravar é sempre manual, e o AutoMatch manual continua manual. O texto abaixo descreve o desenho original.

# D1 — Refino automático depois do AutoMatch (decisão do dono 2026-10-16, reafirmada em 2026-10-18)

**Modos** (interruptor único em Ferramentas): DESLIGADO · **SOMBRA** (padrão: calcula e registra na sessão o que gravaria; nunca escreve) · LIGADO (grava sozinho). Ligar exige toque duplo.

**Fluxo.** A ECU muda a contagem de AutoMatch → o monitor guarda a leitura de ANTES (buffers do GNV ainda cheios; a ECU zera o GNV logo depois) → `engineReview` dessa leitura dá a curva ideal nó a nó → o app lê a curva viva da ECU → `AutoRefinePolicy.decide` → evento `d1_decision` na sessão → só em LIGADO: `KFactorManager.startBatchWrite` (ACK + leitura de volta; motivo "D1 · Refino automático após AutoMatch #n").

**Regras (AutoRefinePolicy, testadas):** sem teto por ponto; travas absolutas 0,8 ≤ K ≤ 1,8 e, com qualquer célula do Mapa K > 200 (ou Mapa desconhecido), K ≤ 1,5; a trava nunca inverte a direção pedida pelo motor; uma vez por AutoMatch; mudança < 0,5 % não vale uma escrita (critério do código, não do dono).

**Não está nesta versão.** (c) correção por faixa de RPM no Mapa K; suavização do serrilhado separada; qualquer validação no carro.

**Limite honesto de evidência.** `docs/evidence/2026-10-18-pares-do-ledger-nao-seguem-o-k.md` mostra que o `petrol_ms` em GNV quase não acompanha o K em ~100 s. A curva ideal vem dos buffers da própria ECU (não dos pares do Ledger), mas se a grandeza não responde ao K, o ciclo "grava → mede → corrige" não fecha sozinho: por isso uma escrita por AutoMatch (no máximo 3–4 por aprendizado) e a SOMBRA como padrão. Antes de ligar, olhar nas sessões os `d1_decision`: quantos pontos, de quanto, se `clamped`, e se `idealFactorsRaw` faz sentido contra `liveFactorsRaw`.
