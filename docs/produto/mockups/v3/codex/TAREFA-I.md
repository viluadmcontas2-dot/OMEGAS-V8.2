
# TAREFA I: refazer o LAYOUT de 4 telas existentes (arquivos em `ref/`; escreva o resultado em `out/project/` com os MESMOS nomes)

Arquivos: `10-Anti-apagao.dc.html`, `11-Carro-apagando.dc.html`, `12-Avisos-da-central.dc.html`, `13-Viagens.dc.html`. Leia a LAYOUT-V3 (acima). Mantenha conteúdo, textos, estados, ids de estado e links; mude SÓ o layout e o que a LAYOUT-V3 pede. A barra do topo e a navegação já estão certas: copie-as sem mexer.

- Todas: botão Voltar (link para `09-Ferramentas.dc.html`) no cabeçalho antes do título; remover a barra de ações (`top:538px`); painel e cartão com `height:496px`; ações no fundo do painel esquerdo.
- 10 Anti-apagão: o gráfico de barras usa a altura toda do cartão (colunas bem mais altas, rótulos ≥ 18 px); no painel: interruptor, "Rotação máxima para agir" com −50/+50 e lápis, texto do efeito; acima dos botões, 2 linhas de 48 px "Como funciona" e "Outros valores (em teste)"; no fundo "Restaurar fábrica" e "Desfazer" (este só depois de gravar). Aviso fixo "Gravado e conferido pela central: …" no painel, acima dos botões. Selo ATIVIDADE DA CENTRAL no topo, "pronto" depois de qualquer gravação.
- 11 Carro apagando: a altura extra vai para as causas (mais respiro, texto de evidência a 18 px); no fundo do painel "Compartilhar com o instalador" e "Desfazer" (este só depois de gravar). Selo ATIVIDADE ao tocar "Usar 1.800 giros".
- 12 Avisos da central: as 14 verificações em 2 colunas de 7 linhas, ~62 px cada; "Apagar avisos de falha" e "Ver detalhes" no fundo do painel. Sem barra embaixo.
- 13 Viagens: lista com linhas maiores (cabem 6 viagens sem rolar); painel da viagem escolhida com "Compartilhar resumo", "Pacote completo" e "Apagar" (e Desfazer após apagar) no fundo; estado vazio ocupando a área toda. Selo ATIVIDADE ao compartilhar/apagar.
Use `ref/09-Ferramentas.dc.html` só como exemplo do estilo; NÃO o edite.
