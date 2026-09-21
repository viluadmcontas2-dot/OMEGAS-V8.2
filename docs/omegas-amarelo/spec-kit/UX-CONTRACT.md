# UX Contract — OMEGAS Amarelo

Norte derivado dos documentos de UX do owner:
- Blueprint Premium UI/UX — Método CUSTOMROM reutilizável.
- Método aplicado — Omega Dev 4.0 Premium UI/UX.

Os princípios necessários estão copiados aqui para a execução permanecer repo-first.

## Princípios
- A UI representa o trabalho humano, não subsistemas técnicos.
- Uma única autoridade de estado.
- Intenção -> ação -> consequência visível.
- Normalidade é compacta; problema ganha espaço.
- Resumo humano primeiro; detalhe técnico sob demanda.
- Cor tem semântica.
- Contexto não é perdido ao navegar.
- Evidência pode ser aprofundada sem poluir a superfície primária.
- Legibilidade automotiva 1280×720 é requisito, não acabamento.

## AutoCAL principal
Gráfico central inspirado na semântica do ProgBase, não em sua estética:
- Y = MAP [bar].
- X = Petrol injection time [ms].
- referência gasolina;
- GNV atual/anterior quando suportado;
- curva/estado nativo;
- fade semântico por idade, validade e confiança;
- região/ponto ativo sem ruído.

Gráfico secundário:
- Curva K / MUL_ACT atual;
- mudança antes/depois quando houver evento comprovado.

## Estados humanos
Exemplos de projeção:
- Aguardando condições.
- Coletando gasolina.
- Coletando GNV.
- Ajustando na ECU.
- Verificando resultado.
- Concluído.
- Degradado / dado stale / desconectado.

Não expor `TAutoCalDM`, endereços ou buffers como modelo mental primário.

## Detalhe técnico
Sob demanda:
- buffers;
- zonas;
- contador AutoMatch;
- MUL_ACT;
- snapshot/hash;
- protocolo/raw bytes;
- timing/proveniência.