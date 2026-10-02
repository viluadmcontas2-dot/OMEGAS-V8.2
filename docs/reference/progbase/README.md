# Referência ProgBase (Delphi / MP48)

Data: 2026-10-02. Fonte original: Drive pasta DUMP `1SMZqx2Sd1HKooJ5GVCSuYDPvkCn6R0kH`.

Escopo desta versão: extração estática de TAeb/SerialCode de 5 recursos binários, catálogo por função, oportunidades e lacunas. Não é mapeamento ponta a ponta de rotinas: houve desmontagem **pontual** da seção .text documentada em FORMULAS.md, mas não validação de todas as telas, strings ou firmware da ECU; nenhuma fórmula causal da ECU foi identificada como provada. Nenhum binário foi incorporado ao Git. Nenhuma escrita em ECU foi feita.

## Reprodução

1. Obter os cinco arquivos citados no campo `secao_arquivo` do JSON no Drive do dono, verificar origem e manter fora do repositório.
2. Ler streams de DFM Delphi (`TPF0`); para cada componente `TAebNumber/Vector/Matrix`, extrair `SerialCode` em little-endian, `DataLength`, `Signed`, `ArrayDimension`, `RowCount` e `ColCount`. Guardar offset em bytes; campos ausentes são `null`, nunca estimativas silenciosas.
3. Ordenar por SC e nome, manter colisões por nome/perfil. Cruzar `ja_usado_pelo_app` com `AutoCalProtocol.kt`, `KFactorProtocol.kt`, `Mp48Protocol.kt`, `Mp48TelemetryScale.kt` na branch `claude/brave-darwin-wuliyo`.
4. Rodar `python -m json.tool docs/reference/progbase/parametros.json >/dev/null` e validar todos os SC citados em CATALOGO contra valores no JSON.

**Semântica de evidência**: `CONFIRMADO` atesta nome/SC/estrutura lidos no DFM; `INFERIDO` indica hipótese. A propriedade `ja_usado_pelo_app` significa correspondência textual de nome/endereço nos quatro arquivos, não garantia de uso em runtime. Tipo nulo = DataLength não expresso. Escalas/unidades/faixas/padrões nulos = não extraídos nem demonstrados.

Arquivos: `parametros.json`, `CATALOGO.md`, `FORMULAS.md`, `OPORTUNIDADES.md`, `LACUNAS.md`.

## Estudo dirigido adicional, 2026-10-02

[**ACHADOS-DIRIGIDOS-20261002.md**](ACHADOS-DIRIGIDOS-20261002.md) confronta diretamente os recursos do ProgBase com as rotas Kotlin vigentes: superfície do injetor SC 313–318, famílias de pressão/temperatura, limiares nativos AutoCal SC 361/362/387–392, nível dependente de sensor, telemetria MGLEV e autoridade da escala de Mapa K. Cada seção separa **CONFIRMADO-DFM**, **CONFIRMADO-APP**, **INFERIDO** e **ABERTO**, com offsets e testes de fechamento. As novas prioridades estão resumidas em OPORTUNIDADES.md e as limitações em LACUNAS.md. Sem alteração na lógica do aplicativo nem operação em ECU.
