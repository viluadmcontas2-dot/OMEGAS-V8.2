# Diamante — interface coerente, 2026-10-04

## Decisão do dono

A referência aprovada usa grafite e turquesa, dados da ECU persistentes no topo, navegação inferior e gráfico dominante. O dono autorizou redesenhar e realocar funções, reduzir poluição e substituir a antiga composição do Agora com quatro mostradores enormes. Mantidas as oito rotas, inclusive Diagnóstico. Informação técnica fica disponível sob demanda. Sem mudar o algoritmo de calibração nem o protocolo da ECU.

## Implementação

- Tokens de superfícies, bordas, sombras e estados compartilhados; ícones locais, sem fonte ou recurso remoto.
- Topo único com conexão, combustível, RPM, injeção gasolina/GNV, MAP e atualidade da leitura. Valores ausentes ou vencidos continuam desconhecidos.
- Agora comunica equivalência, cobertura e próxima intenção da visão enriquecida; não repete telemetria.
- Mapa conserva grade de 44 px, cabeçalho fixo e edição visível; apenas a grade rola. Curva mantém gráfico e editor no viewport.
- AutoCal conserva pausa nativa, releituras de gasolina/GNV, zonas e AutoMatch. Aquisição própria do OMEGAS aparece apenas no Refino, com círculos pequenos.
- Refino prioriza whatNow/nextAction e o gráfico. Sugestões locais leem arrays raw e índices já produzidos pela ponte, exigem canAct e respeitam barreiras da ECU. Um toque usa a fila existente, foto anterior, readback e Desfazer; observar nunca escreve.
- Atualizações da permissão de agir invalidam a apresentação anterior. Diagnóstico trata jerks como jerkPct e aceita gas, conforme ARCHITECTURE.md.
- Sessões, Ferramentas, Diagnóstico e flutuante usam o mesmo vocabulário visual. Estado saudável compacto; detalhes preservados.

## Provas e limites

Classe 1: 162 comandos locais de contratos Python/JS concluídos sem falhas antes das duas correções finais; testes direcionados dessas correções reproduziram a falha e passaram depois. Os contratos visuais antigos foram adaptados à decisão expressa do dono; a sensibilidade dos mutantes continua cobrada.

Classes 2/3: ponte simulada e fixtures de replay exercitam leitura, proposta local, um toque, readback e proteção contra dados vencidos. O HTML independente usa assets da implementação com ponte falsa; identifica dados simulados e permite cinco cenários do Refino.

Revisão visual no navegador: oito rotas a 1280×720, topo de 64 px em todas, navegação dentro de 720 px, sem corte horizontal; gráficos AutoCal/Refino com 396/404 px de altura. A ferramenta de render local pulou testes que exigem Chromium indisponível; inspeção do navegador não substitui teste Android.

Pendente: build_and_test no SHA pelo GitHub Actions, emulador Android (classe 4), desempenho na multimídia e validação física (classe 5). Nenhum novo APK ou ganho de desempenho é declarado nesta etapa. Sem blur, animação contínua ou novo polling; limitadores de evidência/cursor e atualização do flutuante preservados.

## Fechamento pedido pelo dono: 22 ms e acabamento

2026-10-04: pedido explícito de APK, prioridade em funcionalidade e fidelidade de contraste/profundidade ao PNG. Seleção múltipla da Curva K permanece fora desta entrega para evitar ampliar o caminho de escrita. Corrigida a escala AutoCal/Refino que antes considerava só os pontos adquiridos e podia excluir o extremo da referência. Agora usa toda a referência, mínimo 22 ms, mantém dados acima de 22 ms e reserva margem para a legenda do extremo. Teste reproduziu 4 ms em vez de 22 ms antes da correção e passou depois.

Gradientes estáticos nos controles e na base do gráfico, traço de fundo leve na curva de GNV e cores mais vibrantes aproximam o PNG. Sem filtros SVG/CSS, animação contínua ou polling novo. Os caminhos de leitura, gravação, readback e Desfazer permanecem preservados.

A geração do APK está explicitamente solicitada pelo dono; envio da branch ao GitHub continua pendente de autorização explícita após bloqueio da revisão automática de aprovação. Ambiente local sem SDK Android. Build, lint, testes JVM e APK com SHA-256 serão realizados no workflow gated existente, sem declarar um pacote gerado antes de obter o artefato.
