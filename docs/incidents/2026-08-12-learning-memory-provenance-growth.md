# Incidente — crescimento de proveniência na memória principal do Learning

## Sintoma e impacto
O `MotorLearningMemory` já limitava a quantidade de regiões, comparações e sessões, mas cada região ainda acumulava listas textuais de `visitId` e `sessionId` sem orçamento próprio. Com uso prolongado, o mesmo conhecimento matemático podia ficar progressivamente mais caro para persistir, restaurar, exportar e analisar apenas porque a proveniência histórica crescia.

Isso contrariava a regra V8.2: **o presente não pode ficar mais caro apenas porque o passado cresceu**.

## Causa imediata
- `LearningRegion.visits` e `LearningRegion.sessions` cresciam sem teto por região.
- A persistência gravava esses IDs completos dentro de cada região.
- O arquivo principal também persistia a projeção derivada `cell`, embora ela possa ser reconstruída a partir da região científica.
- A thread de persistência era separada, mas a montagem do JSON inteiro ainda acontecia dentro do `lock` da memória.
- O Advisor recebia `export()` completo, incluindo projeções/resumos que não consome.

## Causa estrutural
Estado científico consolidado e proveniência textual estavam representados como se tivessem a mesma política de retenção. Assim, médias/pesos/contagens úteis e listas de identificadores históricos cresciam juntas.

## Medição de engenharia
Modelagem sintética da serialização de uma única região mostrou crescimento aproximadamente linear com UUIDs históricos. Em 2.000 regiões, apenas regiões serializadas podem sair de poucos MiB para dezenas de MiB quando centenas de IDs são retidos por região, antes de considerar comparações e outras estruturas.

Essa medição é de bancada/sintética; não substitui medição física do arquivo após horas de uso real.

## Correção preparada
1. Novo `LearningMemoryBudget`:
   - até 16 IDs recentes de visita por região;
   - até 8 IDs recentes de sessão por região;
   - alvo de 5 MiB para o snapshot científico principal;
   - níveis progressivos de compactação somente da proveniência textual.
2. `visit_count` e `session_count` ficam separados das listas retidas e continuam representando a contagem acumulada local conhecida.
3. Médias, pesos, `sampleCount`, regiões e comparações **não são descartados** para atingir o alvo em bytes.
4. Se o estado científico continuar acima do alvo mesmo com proveniência zerada, o arquivo é preservado e o excesso é diagnosticado; não há limpeza silenciosa.
5. A persistência guarda região científica primária; `cell`/grid permanecem deriváveis.
6. O `lock` cobre somente a cópia consistente; construção de JSON, digest e disco ficam fora dele.
7. O Advisor usa `advisorSnapshot()` mínimo, sem grid, integridade, resumos de UI ou histórico de sessões.
8. `LearningGridProjection` usa a contagem acumulada como piso e **não fabrica IDs históricos**.

## Por que os testes anteriores não detectaram
Os testes existentes provavam limites top-level, persistência e integridade funcional, mas não verificavam:
- orçamento por bytes da memória principal;
- tamanho das listas de proveniência dentro de cada região;
- custo de serialização sob lock;
- payload mínimo do Advisor;
- proibição explícita de sintetizar IDs ao compactar.

## Testes de regressão
- `tests/test_learning_memory_budget_contract.py` valida política, tetos, contagens separadas, serialização fora do lock, persistência sem projeção derivada, Advisor mínimo e ausência de IDs fabricados.
- O teste compila e executa `LearningMemoryBudget.kt` real com `kotlinc`.
- `MotorLearningMemoryTest.kt` ganhou cenários JUnit para restauração compactada 40/20 → contagem 40/20 com somente 16/8 IDs, persistência sem `cell`, snapshot mínimo do Advisor e estabilização do tamanho diante de proveniência legada maior.
- `QUALITY_GATE_FAST=PASS` após a alteração.
- Parser smoke do Kotlin não encontrou erro sintático; compilação Android completa segue pendente porque não existe SDK/`android.jar` disponível nesta bancada.

## Evidência
Base remota de origem: `work/v8.2-clean@40aa1769460c771f36d7bf7feca25893a051483c`.
O trabalho permanece fora do GitHub enquanto o bloco maior é consolidado; o snapshot de bancada é sincronizado no Google Drive e governado pelo Notion.

## Risco residual
- A meta de 5 MiB é um **alvo de orçamento**, não uma garantia de tamanho máximo: ciência primária nunca é apagada apenas para caber nele.
- Compilações/JUnit Android completos e medição real em celular/multimídia ainda são necessários.
- Fusões cumulativas entre celulares já possuem semântica própria de merge. A compactação não cria IDs fictícios nem usa contagem para confiança, mas a idempotência científica de sucessivas revisões do Omegas Link deve ser auditada em bloco separado antes de afirmar equivalência perfeita entre dispositivos.
- Uma reconstrução a partir de snapshot compactado preserva contagens e estatísticas, mas IDs antigos descartados não são recriados. Isso é intencional; inventar IDs seria cientificamente pior.