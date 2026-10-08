# Refino — comparação e correção (em verificação)

## Requisitos e plano

Método solicitado: entender → teste vermelho → mudança mínima → verificação → commits pequenos.

- Curva nativa como base; pontos próprios refinam também onde existe cobertura nativa.
- Margem 4% (até 5% com referência), histerese, K 12288–19661 e passo ≤15%.
- Lenta/parado e outliers não alteram a proposta; nenhum requisito de um minuto na faixa.
- Mesma API e motivos em RefinoState/refino.js. Nenhum comando/byte da ECU ou arquivo de UI alterado.
- Comparar Platina e Diamante no mesmo gerador e replay; provar 10→25→50, reset e invariantes.
- Executar testes Kotlin puros e contratos locais; integração depende do CI verde no SHA.

## Evidência inicial (teste vermelho)

Erro médio |ln(K/Kideal)| nas regiões plantadas; média das sementes 42/43/44. `n` é pontos por faixa, como no oráculo existente (13 faixas próprias), não o total de quadros.

| Base de aquisição | n | Platina original | Diamante inicial |
|---|---:|---:|---:|
| Nativa + próprios | 10 | 0,016195 | 0,024343 |
| Nativa + próprios | 25 | 0,017361 | 0,024343 |
| Nativa + próprios | 50 | 0,018883 | 0,024343 |
| Gasolina nativa + GNV próprio | 10 | 0,019771 | 0,026472 |
| Gasolina nativa + GNV próprio | 25 | 0,023192 | 0,031244 |
| Gasolina nativa + GNV próprio | 50 | 0,026763 | 0,028095 |

Sem proposta: 0,0369. Ambos regridem ao acrescentar pontos. A cópia da Platina fica exclusivamente em `src/test`, com SHA de origem no arquivo. Compartilha aquisição/pareamento atual para isolar o algoritmo numérico. Não representa uma execução integral do app antigo.

O replay inicial da Diamante oferece APPLY em 4/10 snapshots amostrados de pista, 0/2 da sessão parada. O teste novo exige cada transição não crescente, sem folga numérica de 0,002; o teste original só comparava 10 com 50 com essa folga.

## Diagnóstico em andamento

A Diamante descartava pontos próprios em regiões cobertas pela nativa. O pareamento também comparava ms em MAPs próximos sem compensar a inclinação da referência. O teste específico de transporte de MAP falha na implementação inicial (4,08 ms esperados, 3,978 obtidos).

## Limites da prova

Classes 1/2/3 locais não substituem CI. Não há Android SDK, emulador, APK nem validação física nesta sessão. O replay não contém K ideal medido: pode provar integridade/limites e oportunidades de proposta, não melhoria física do motor.

Commits são registrados pelo executor externo a cada 3 minutos: a sessão não pode escrever no diretório Git comum, que fica fora da área gravável do sandbox.
