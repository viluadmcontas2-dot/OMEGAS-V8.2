# DESIGN — anatomia única de toda aba (decisão do dono, 2026-10-04)

O app é um produto só. Toda aba (01 Agora … 08 Diagnóstico) tem a MESMA anatomia, de cima para baixo:

1. **Faixa de status compacta** — mesma altura em todas as abas (chip/valores do veículo; sem cartão extra).
2. **Elemento visual principal (gráfico/mapa/valor)** — SEMPRE o primeiro, ocupando ≥ 50% da altura útil (`min-height: var(--chart-min-h)`).
3. **UMA frase de estado em português + UMA ação primária** (`.btn-primary`, 58 px; AutoCal 52 px).
4. **Secundário abaixo**, na rolagem vertical da rota (`.screen.active` já tem `overflow-y:auto`).

Mesmo cabeçalho (`.page-intro`), cartão (`.card`), botão (`.btn*`), chip e faixa em todas as abas — só tokens de `tokens.css` + `styles-premium.css`.

## Proibido em qualquer tela
- Rolagem HORIZONTAL (cartões, listas, inspetores, tabelas): quebre linha ou empilhe.
- Cartão/subcartão com texto < 20 px aberto ao tocar.
- Código cru (bytes, hex, nomes de campo) antes do texto humano; o detalhe técnico vai para a aba 08 Diagnóstico.
- Botão "Entendi" que some; aviso que o dono precisa dispensar para continuar.
- Cartão horizontal gigante para uma única informação.

## Medidas e classes (F1)
Toque `--btn-h` 58 px · `--btn-h-compact` 52 px · fonte de botão ≥ 18 px (`--btn-font`) · texto crítico ≥ 22 px · raios `--radius-s/--radius/--radius-l` · sombras `--shadow-1/2/3` · espaços `--space-1…7` · `--chart-min-h`.
Classes: `.btn .btn-primary .btn-secondary .btn-ghost .btn-danger .btn-compact`, `.field`, `.segmented`, `.card`, `.chart-surface`, `.scroll-y`. Campos (`input`, `select`, `textarea`, `checkbox` como toggle, `range`) são estilizados globalmente. Cores: `--ok-soft/--warn-soft/--danger-soft` para fundos de estado; nunca cinza chapado.

## Processo
Preview em screenshot (1280×720) por aba para aprovação do dono antes de mesclar (D7).

## Tema
Um só: **escuro premium** (dirige à noite) — fundo grafite/azul-noite, superfícies em camadas (`--surface/-2/-3`), texto claro, acentos semânticos com contraste AA, sem cinza chapado e sem controle nativo. Não há tema claro nem chave de tema; tudo via `var(--x)` de `tokens.css`. Trilho achatado, sem números nos itens.

## Refinamento elástico — 2026-10-04
- A decisão atual do dono substitui a ordem rígida e o piso permanente de 22 ms. AutoCal e Refino: estado integrado ao cabeçalho, gráfico dominante no centro, trilho de ações ao alcance.
- Área útil é a área da WebView, descontadas as barras do Android pelo host. Validar também 1280×672 e 1280×648; nunca dimensionar por 720 fixos.
- AutoCal: Leitura anterior junto à pausa; zonas, releituras e opções em um só trilho. Refino mantém intenção, permissões e Desfazer reais.
- Escala automática usa somente séries visíveis com coordenadas válidas e pequena margem nos dois eixos. A referência válida e a leitura anterior selecionada nunca são cortadas. Faixa inteira expande para ao menos 22 ms/1,15 bar, sem limitar valores maiores.
- Gasolina/GNV são opções de apresentação, nunca aquisição ou gravação. Ao menos uma série fica visível. Nenhum novo timer, protocolo ou escritor.
- tokens.css continua autoridade das cores, sombras e tipografia. styles-diamante.css adapta a composição; CurveChart é dono único do desenho e da escala.
- A autorização atual dispensa nova aprovação estética; evidência de CI, navegador e Android é obrigatória antes da entrega. Teste físico cabe ao dono.
