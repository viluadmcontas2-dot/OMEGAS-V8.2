# Refino — Platina portada x Diamante (decisão)

CI verde no SHA 6e95d18 (run 37857699323, job build_and_test). Dados: artefatos `sessao-ficticia` e `replay-refino-proposta`.

## Oráculo (erro médio |ln K/K ideal| nas faixas plantadas, sementes 42/43/44)
| base | n | Platina portada | Diamante |
|---|---:|---:|---:|
| nativa + próprios | 10 | 0,0164 | 0,0212 |
| nativa + próprios | 25 | 0,0236 | 0,0212 |
| nativa + próprios | 50 | 0,0296 | 0,0212 |
| só próprios (sem nativa) | 10 | 0,0198 | 0,0258 |
| só próprios | 25 | 0,0259 | 0,0258 |
| só próprios | 50 | 0,0270 | 0,0258 |
Sem proposta: 0,0369. Platina PIORA com mais pontos (regride 10→50, +80%); Diamante nunca piora (teste exige não crescente).

## Replay real (fixtures/autocal/real)
- Sessão de pista (10 amostras): Diamante APPLY em 6/10 (inicial do Codex: 4/10); Platina propõe em 10/10, mas com
  160 pontos inseguros (fora da caixa K 12288–19661 ou passo >15%) e erro "depois" maior que "antes" em 3 amostras.
- Sessão parada/utilitária (2 amostras): nenhuma proposta em ambos os lados do que importa (Diamante COLLECT; Platina só POLISH).

## Escolha: Diamante (corrigida pelo Codex); Platina NÃO vira produção
Motivo: respeita caixa e passo, piora nunca, parado/outliers não alteram, reset retira a proposta. A Platina portada
só ganha com n=10 e quebra os limites. Mudanças do Codex que ficam: pontos próprios passam a refinar também onde há
nativa (removidos o teto de peso por faixa e o descarte `NATIVE_COVERED_GAIN`: menos guardas), pareamento GNV x gasolina
transporta o MAP pela curva nativa, reset de combustível não reutiliza pares velhos. API de RefinoState/refino.js e
motivos de "não sugere" intactos; reset não pausa aprendizado (regra 14) intacto.

## Removido / mantido
Removido: teto de peso por faixa, filtro "nativa já cobre". Mantido só em `src/test`: PlatinaRefinedEngine.kt e
MotorComparison.kt (prova da comparação; não entram no APK). Apagar depois que o dono aceitar a escolha.

## Não provado
- "10→25→50 estritamente mais perto": o Diamante fica PLANO (0,0212 nos três), não cai. Garantido só "não piora".
  Causa provável: na nativa o ganho já satura; ajuste fino do peso dos próprios pontos precisa de nova rodada de CI.
- Nenhum K ideal medido no replay: ele prova limites e oportunidade de proposta, não melhora física.
- Só o carro (classe 5) valida. Sem emulador/APK nesta sessão.

## Rodada final: peso dos pontos próprios (PENDENTE, nada aplicado)
Causa da planície 0,0212: o peso não limita; limitam LAMBDA (0,3), a histerese (3,5%) e a coerência E_MAX (0,35).
Ensaio local (kotlinc, 3 s): LAMBDA 0,05 dá 0,0228/0,0209/0,0200 (queda estrita só com nativa); +histerese 2% e E_MAX 0,6
dão ~0,0096 (n=10/25/50, queda de 1e-6) mas 1 teste existente falha e o replay real não foi medido. LAMBDA foi
validado por validação cruzada em sessões reais: mexer sem CI/replay é meia-solução. Revertido ao commit 325a908.
Próximo passo: CI em branch com LAMBDA 0,05 e E_MAX/histerese, checando replay (propostas fora da caixa) e o teste que falha.
