# Diamante — refinamento elástico
Base remota: 2d43f76064ea5148f8c71bf8b43a8f119e3b6fe1. Branch work/platina-diamante-ui; PR #151 → OmegasDiamante.

## Contrato
O pedido atual substitui o mínimo permanente de 22 ms e a ordem rígida anterior. Mantidos escritores nativos, foto anterior, readback, canAct, Desfazer, protocolo, algoritmo, timers e SVG incorporados. AutoCal não recebe betweenPoints do OMEGAS. Refino continua lendo refinoState; Diagnóstico continua lendo stalls.regions e fluidity.

## Causas e mudança
- Escala: focusDomain antes reservava no mínimo 22 ms inclusive para coordenadas sem MAP utilizável. Agora considera coordenadas válidas nas séries visíveis, história selecionada e, no Refino, betweenPoints e projeção equivalente. Pequena margem em X e MAP; 22 ms/1,15 bar só ao selecionar Faixa inteira. Valores maiores nunca limitados.
- AutoCal: Leitura anterior saía da legenda, aumentando sua altura. Estado ocupava outra linha. Estado integrado a título/zona/AutoMatch, histórico junto da pausa, zonas/releituras/opções em um trilho.
- Refino: intenção integrada ao cabeçalho; sugestões e detalhes conservam conteúdo e permissões; ação e Desfazer disponíveis pelo estado real.
- Mapa: campo numérico vazio deixava overrides da prévia antiga; agora invalida a prévia. Contexto do painel conservava coordenadas do último toque após desmarcar: agora acompanha seleção/valor atual/prévia. Pointer capture entregava a célula de origem no fim do arrasto: resolução pela posição real, cancelamento seguro e alternativa por teclado.
- Agora/Curva: agrupamento e alinhamento; Sessões: duas colunas com fatos na base de cada sessão; Ferramentas: hierarquia e espaçamento comuns; Diagnóstico: gráfico/fluidez lado a lado e resultado/histórico agrupados abaixo.

## Provas
Tudo executado no GitHub Actions. A primeira rodada de reprodução recebeu shutdown do runner, portanto não conta como RED válido. Rodada focada posterior e validação de regressões precisam de resultados remotos.
- CI canônica: tools/run_checks.py, JVM e lint.
- Chromium: mock-bridge.js, fixtures reais + estados sintéticos, oito rotas em 1280×720/672/648; coleta, proposta, verificação, resultado/ausência, edição, teclado, filtros e contagem de redesenhos.
- Android: preservados AutoCal ações, Mapa contexto, Curva 30 pontos e latência Refino; adicionados coleta, proposta e verificação Refino. Checkout e metadados no SHA do evento.
- APK gated só depois dos jobs de navegador e Android; fonte SHA explícita. Conferir SHA-256 do download e do arquivo substituído no Drive.

## Limites
Foto da multimídia mencionada não foi localizada neste contexto nem na busca de imagens. Área útil menor é prova conservadora de layout, não medição da barra do aparelho. Nenhuma prova física é afirmada. O APK antigo e as rodadas aprovadas anteriores permanecem rastreáveis. Não há merge nem mudança da branch de destino nesta missão.
