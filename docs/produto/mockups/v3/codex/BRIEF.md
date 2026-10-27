# BRIEF COMUM (leia inteiro antes de escrever qualquer arquivo)

## O que é
Estou montando um canvas de telas (mockups interativos) para repaginar o app OMEGAS: um aplicativo de multimídia de carro que regula a injeção de GNV. Cada tela é UM arquivo `.dc.html` de 1280×720 px (exceto quando a tarefa disser outro tamanho). Você escreve SÓ os arquivos pedidos, dentro de `out/project/`. Não toque em mais nada. Não use git.

## Leia antes de escrever
- `ref/format.md`: regras do formato .dc.html. Vale para tudo.
- `ref/06-Ajuste-local.dc.html`: modelo de tela INTERATIVA (barra do topo, navegação de baixo, painel esquerdo, cartão direito, barra de ações, script com estado e laços).
- `ref/01-Conectar.dc.html`: modelo de tela ESTÁTICA.
COPIE a barra do topo (o `div` `position:absolute;left:0;top:0;width:1280px;height:56px…`) e a navegação (`<nav aria-label="Áreas do aplicativo" …>`) EXATAMENTE desses modelos. Só mude (a) os valores numéricos da barra do topo, se a tela pedir, e (b) qual aba está ativa. A aba ativa tem `border:1px solid #2a4a7e;background:var(--accent-soft);color:var(--text);font-weight:700` e ícone com `stroke="var(--accent)"`; as inativas têm `border:1px solid transparent;color:var(--muted);font-weight:600`. O selo amarelo "12" fica na aba Refino quando ela NÃO é a ativa.

## Regras do formato (as que mais quebram, sem aviso)
- No head: a linha exata `<script src="./support.js"></script>`. Estrutura: `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>…</title><script src="./support.js"></script></head><body><x-dc><helmet>…</helmet> RAIZ </x-dc><script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":1280,"height":720}}'>class Component extends DCLogic { state = {…}; renderVals(){ return {…}; } }</script></body></html>`.
- `<helmet>` só tem: `<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap">` e `<style>body{margin:0}</style>`.
- RAIZ = um `<div>` com `position:relative;width:1280px;height:720px;overflow:hidden;` mais as variáveis CSS e a fonte (copie do modelo). Estilo é todo inline (`style="…"`), usando `var(--…)`.
- `{{ caminho.pontilhado }}` aceita SÓ caminho pontilhado (`{{ c.nome }}`, `{{ $index }}`); NUNCA expressão (`{{ a + b }}`, `{{ !x }}`, `{{ f() }}`, ternário). Toda conta vai em `renderVals()`.
- Interpolação em atributo é permitida: `style="left:{{d.left}}px;background:{{d.bg}}"`.
- Eventos: `onClick="{{ nomeDaFuncao }}"`; a função vem de `renderVals()`. Em itens de lista, crie a função em cada item dentro de `renderVals()` e use `onClick="{{ it.pick }}"`.
- Repetição: `<sc-for list="{{ itens }}" as="it" hint-placeholder-count="3"> … </sc-for>`. Condição: `<sc-if value="{{ flag }}" hint-placeholder-val="{{ true }}"> … </sc-if>` (flag é booleano calculado; para o contrário crie `naoFlag`). Eles NÃO criam elemento extra (viram fragmentos), então funcionam dentro de flex e grid.
- Feche todo elemento não vazio; ponha aspas em todo atributo. Sem `<iframe>`, sem emoji, sem imagem externa, sem `data:` URI, sem script externo. Ícones = SVG inline de traço (`fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"`), sempre `aria-hidden="true"`.
- Controles reais: `<button type="button">` e `<a href="…">`. Botão só com ícone leva `aria-label`. Link para outra tela = `<a href="NN-Nome.dc.html">` (lista abaixo). Estilize o próprio `<a>` como botão; não ponha `<button>` dentro de `<a>`.
- Estado: `state = {…}` como campo de classe e `this.setState(…)`; leia sempre `this.state || {}` com valores padrão. Nada de timers (`setTimeout`/`setInterval`): mude de estado só por clique.
- Sem rolagem e sem texto cortado. Se faltar espaço, reduza conteúdo, não a fonte.

## Identidade visual (as variáveis já estão nos modelos; copie o bloco `style` da raiz)
Tema escuro único, fonte Inter. Tokens: --bg #070a0f · --rail #0b1017 · --surface #101720 · --surface2 #151e29 · --surface3 #1b2633 · --line #263342 · --text #f4f7fb · --muted #9aa9bc · --dim #8c9bae · --accent #2f8cff · --accent-btn #1f6fd8 (botão principal, texto branco) · --accent-soft #14284a · --ok #38d39f · --ok-soft #0f2b23 · --warn #f3ba59 · --warn-soft #2d240f · --danger #ff6b6b · --danger-soft #2e1618 · gasolina #ff7a7a (círculo) · gás #5fe38a (quadrado) · Curva K azul. PROIBIDO roxo, violeta e magenta.
Tamanhos: botão e alvo de toque ≥ 58 px de altura; texto de corpo ≥ 17 px; legenda ≥ 14 px; título de tela 30 px; números grandes 24–32 px; raio 12–14 px. Contraste ≥ 4,5:1 (texto secundário em `--muted`; `--dim` só ≥ 14 px e nunca como único contraste).
Layout de 2 colunas (REGRA NOVA do dono): painel de controles e estado à ESQUERDA (`left:32px;top:128px;width:370px;height:396px`) e o conteúdo principal à DIREITA (`left:418px;top:128px;width:830px;height:396px`). Barra de ações embaixo à esquerda (`left:32px;top:538px;width:1216px;height:70px;display:flex;align-items:center;gap:12px`), botões de 58 px alinhados à esquerda, sem botão isolado no canto direito. Cabeçalho da tela em `left:32px;top:64px;width:1216px;height:56px`: título (30 px) + chip de estado + UMA frase de estado em pílula.

## Vocabulário (travado por teste do projeto)
Diga "central" (nunca ECU nem módulo), "giros" ou "Rotação" (nunca "RPM" em texto), "Pressão da admissão" (nunca MAP), "folga de pressão". Proibido na tela: ciclo, flag, bit, palavra, 0x, hash, readback, ACK, NAK, histerese, delta, "Ajustes 1/2/3", "Números", "Detalhes", "Avançado". "Rotação máxima" vem sempre seguida de "para agir". Números com vírgula decimal e ponto de milhar (1.600; 0,39). Desconhecido é "—", nunca 0. Botões com verbo + objeto ("Gravar 3 células", "Salvar cópia"); o botão e o aviso usam a mesma palavra.

## Regras de produto (não quebrar)
- O app NUNCA grava na central sozinho: toda gravação é um toque do usuário. Um toque = ação feita (sem janela de confirmação). Depois aparece um aviso FIXO (não some sozinho) "Gravado e conferido pela central: …" e o botão "Desfazer".
- O aprendizado automático (AutoCal) roda sozinho na central; o usuário só liga ou desliga. O app lê a telemetria ao vivo e mostra/decide sozinho tudo o que dá para saber; NÃO pergunta ao usuário o que o app consegue medir. Menos toques é sempre melhor.
- Cada número editável diz o que é e o que muda se subir ou descer; se o efeito não está comprovado, marque "não comprovado".
- Texto curto, claro, sem jargão. Erro diz o que fazer. Tela vazia convida a agir.

## Nomes de arquivo (para os links)
Main · 01-Conectar · 02-AutoCal-esperando · 03-AutoCal · 03b-AutoCal-em-movimento · 04-Limpar-pontos · 05-Refino · 06-Ajuste-local · 07-Ajuste-global · 08-Curva-para-Mapa · 09-Ferramentas · 10-Anti-apagao · 11-Carro-apagando · 12-Avisos-da-central · 13-Viagens · 14-Estados · 15-Padroes · 16-AutoCal-800x480 · 17-Ajuste-local-800x480 · 18-Funcoes (todos com `.dc.html`).
Abas da navegação: AutoCal→03-AutoCal · Refino→05-Refino · Ajuste local→06-Ajuste-local · Ajuste global→07-Ajuste-global · Ferramentas→09-Ferramentas.

## Como entregar
Escreva os arquivos em `out/project/` (crie a pasta). Um arquivo por vez; ao terminar cada um, atualize PROGRESSO.md. NÃO existe navegador aqui: depois de escrever, releia o código procurando erro do formato (tag aberta, atributo sem aspas, expressão dentro de `{{ }}`, função usada no template que não está em `renderVals()`, `<sc-if>`/`<sc-for>` sem fechar). No fim, resumo ≤ 20 linhas: o que cada arquivo mostra, o que é interativo, o que supôs e o que NÃO foi provado.
