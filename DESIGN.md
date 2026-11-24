---
version: alpha
name: "OMEGAS 8.2 · Instrumento"
description: "Painel de instrumentos GNV fiel à central, com identidade escura e navegação adaptativa de multimídia a smartphone"
colors:
  background: "#070A0F"
  surface: "#101720"
  surfaceMuted: "#172330"
  foreground: "#F4F7FB"
  foregroundMuted: "#AEBFD0"
  primary: "#2F8CFF"
  primaryForeground: "#070A0F"
  secondary: "#1D3552"
  toolsAccent: "#54D9BD"
  border: "#2B3E52"
  success: "#38D39F"
  warning: "#F3BA59"
  danger: "#FF6B6B"
typography:
  sans:
    fontFamily: "Inter, Roboto, 'Segoe UI', Arial, sans-serif"
    fontSize: "16px"
    lineHeight: "1.45"
  mono:
    fontFamily: "Consolas, 'Roboto Mono', monospace"
    fontSize: "14px"
rounded:
  DEFAULT: "12px"
  sm: "8px"
  md: "12px"
  lg: "16px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "18px"
  xl: "24px"
components:
  nav: {}
  toolCard: {}
  toolAction: {}
  dataLabel: {}
  status: {}
---
# OMEGAS 8.2 — Design System: Instrumento

## Estrela-guia
Um **instrumento de leitura do carro**: escuro para legibilidade e pouco reflexo, azul para decisão/navegação, verde-água como detalhe de interface e verde/amarelo/vermelho **somente** com significado operacional. O traço característico não é uma animação: é o contraste preciso entre número, unidade, proveniência e próxima ação.

- **Público/trabalho:** pessoa operando o OMEGAS numa central GNV MP48, precisa compreender leitura, sugerir e decidir sem comandos escondidos.
- **Uso:** somente paisagem (horizontal) no Android: multimídia 1280×720 (canônica), 1024×600, 800×480; telefones em paisagem, desktop/ultrawide. Retrato não é alvo de produto.
- **Linguagem:** pt-BR. Chamar de **central** e **giros do motor**, nunca siglas de código como instrução.
- **Registro:** software operacional, não marketing, sem brilho gratuito, contornos neon, gradientes extensos, ícones sem legenda ou microtexto.
- **Controles da ECU:** nenhum ajuste implícito; verde-água nunca se confunde com sucesso de gravação. Somente uma ação explícita inicia escrita autorizada.
- **Identidade anterior:** preservada. O Themely gerou a direção "OMEGAS · Instrumento" em 2026-11-17; a paleta mantém a base escura, o azul e as famílias locais já usados pelo app.

## Propriedade de tokens
**Modelo B — CSS da aplicação é canônico.** `app/src/main/assets/ui/styles.css` é dono dos tokens gerais (`--bg`, `--surface`, `--accent`, `--ok`, `--warn`, `--danger`). `app/src/main/assets/ui/styles-adaptive.css` implementa somente os tokens de Ferramentas (`--tools-panel`, `--tools-outline`, `--tools-copy`, `--tools-accent`, `--tools-elevation`). Este documento **espelha** os valores reais, não gera CSS. Revisar mapeamento e teste quando mudar token.

| Papel | Runtime | Escopo |
| --- | --- | --- |
| Fundo | `--bg` | App inteiro |
| Superfície | `--surface` | App inteiro |
| Ação e foco geral | `--accent` | App inteiro |
| Sucesso, alerta, erro | `--ok`, `--warn`, `--danger` | App inteiro, somente dados reais |
| Superfície técnica elevada | `--tools-panel` | `#toolsDrawer` |
| Borda e leitura | `--tools-outline`, `--tools-copy` | `#toolsDrawer` |
| Detalhe de seleção e foco | `--tools-accent` | `#toolsDrawer` |

## Cor e tipografia
- Alto contraste para números, valores e rótulos; texto secundário `#AEBFD0` sobre superfícies `#101720`–`#172330`; dados ausentes = "—", nunca 0 ou desligado presumido.
- Azul `#2F8CFF` comunica ação/navegação, `#54D9BD` comunica **foco e qualidade de acabamento**, não "central confirmou". **No botão azul de Ferramentas, texto escuro `#070A0F`** em vez de branco (contraste de texto comum: cerca de 6:1 vs. 3,3:1), sem alterar os botões das quatro telas protegidas.
- Corpo usa fonte **local** Inter/Roboto/Segoe UI, sem importar famílias da internet (WebView offline). Número usa algarismos tabulares; não trocar unidades ou arredondamento.
- Texto de Ferramentas deve permanecer legível a distância e quebrar linha sem ocultar o estado; evitar clamp para explicações indispensáveis.
- Hierarquia real: valor atual, rótulo/efeito, status/ação. Não usar mais de um destaque primário por cartão de decisão.

## Composição responsiva
- A navegação contém exatamente AutoCal, Refino, Ajuste local, Ajuste global e Ferramentas. Sessões/Diagnóstico são grupos internos de Ferramentas.
- Centro automotivo: sem deslocamentos horizontais inesperados, alvos grandes, foco claro e ações agrupadas à esquerda.
- Paisagem compacta: cartões, barra ao vivo, cinco destinos e ações permanecem acessíveis em alturas de 360–480 px. Ferramentas mantém o contrato original sem rolagem: preferir paginação quando não couber; nunca cortar botões, textos ou criar overflow horizontal.
- Na paisagem compacta: manter os cinco destinos legíveis e acessíveis; Ferramentas deve conservar cartões inteiros nas duas páginas internas. No ultrawide Ferramentas: cartões limitados a 1680px totais e aproximadamente 196px de altura, começando à esquerda, sem esticar ao tamanho da janela.
- Telas técnicas AutoCal/Refino/Mapa K/Curva K **permanecem sem nova reforma visual** até conjunto completo de mockups + fluxo aprovado pelo dono; tema de Ferramentas é a primeira aplicação isolada.
- Matriz ativa de paisagem: 640×360 (DPR 3), 740×360, 800×480, 1024×600, 1280×720, 1920×1080 e 2560×1080. Os cenários retrato do histórico do PR não são requisito de aceitação do APK.

## Forma, profundidade e movimento
- Cantos 12–16px, superfícies opacas sem glassmorphism; sombra curta apenas em painel que de fato concentra decisões.
- Não criar linha colorida, ícone ou glow que pareça outro sensor/alarme. Componente de estado real continua a usar **texto** e o sistema de estado existente.
- Hover apenas em mouse; foco teclado com borda visível; tocar não cria animação contínua. Respeitar `prefers-reduced-motion`.
- Com o carro em movimento: interação não deve incentivar atenção prolongada ou configuração arriscada.

## Contratos dos componentes
- **Tool card:** título legível, explicação curta e estado real; `data-state=unknown` nunca vira "off" por paleta. Se não houver leitura: mostrar — e explicar.
- **Ação:** verbo + objeto, um toque intencional, sem confirmações artificiais; a segurança nativa continua obrigatória.
- **Valores editáveis:** contexto e efeito de aumentar/diminuir, unidade, campo acessível, falha sem perder valor digitado.
- **Resultado:** leitura/ACK, conferindo, verificado, não suportado e incerto têm texto distintivo; sem alteração da semântica de gravação atual até resolver o bloqueio de produto em PR #195.
- **Sessões:** mostrar armazenamento local e estado de exportação real; nunca sugerir sincronização entre dispositivos que não existe.
- **Validação:** CSS + testes no CI por SHA; WebView e ECU reais ainda exigem confirmação física. A prévia Themely é referência, não screenshot da build.

## Não fazer
- Não adicionar efeitos 3D, animações contínuas, brilho neon ou cartões decorativos.
- Não criar sexto item de navegação nem esconder/renomear ação existente sem registrar.
- Não inventar comando, endereço, unidade ou capability da central para preencher tela vazia.
- Não adicionar fonte externa, biblioteca visual ou segundo autor de navegação/estado.
