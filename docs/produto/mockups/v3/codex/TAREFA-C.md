# TAREFA C: tabela "Nada some" (arquivo `out/project/18-Funcoes.dc.html`)

TAMANHO ESPECIAL: 1800×2500 px (`$preview` 1800×2500; raiz com `width:1800px;height:2500px`). ESTÁTICA (sem estado). NÃO desenhe barra do topo nem navegação. Use as variáveis CSS e a fonte dos modelos. Transcreva os textos da tabela abaixo EXATAMENTE como estão (inclusive aspas, setas e travessões); não resuma, não reordene, não invente linhas.

EXCEÇÃO ao vocabulário do BRIEF: na coluna "Antes" os rótulos antigos ficam como são (podem ter "RPM", "ECU", "Sessões"); isto é documento de projeto, não tela do app.

## Layout
- Raiz `padding:56px`, fundo `--bg`.
- Cabeçalho: título "Nada some: onde cada função mora" (44 px, 800) e uma frase (20 px, --muted): "Toda função e todo botão de hoje continuam. Esta tabela diz onde cada um fica na nova experiência." + legenda das 4 marcas (chips de 15 px, 800, caixa alta): EXISTE (fundo --ok, texto #04150e) = "já é assim hoje" · MUDA DE LUGAR (fundo --surface3, texto --text, borda --line) = "mesma função, outro lugar" · NOVO (fundo --accent-btn, texto branco) = "ainda não existe" · PRECISA OK (fundo --warn, texto #1b1303) = "muda o que a central recebe ou pede leitura nova".
- Corpo em 2 colunas (`display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:48px`). Coluna esquerda: seções AutoCal, Refino, Ajuste local. Coluna direita: Ajuste global, Ferramentas, Barras e navegação.
- Cada seção: título (26 px, 700, cor --accent) + lista de linhas. Cada linha (altura mínima 56 px; `display:grid;grid-template-columns:300px 24px minmax(0,1fr) 112px;gap:12px;align-items:center;padding:8px 0;border-bottom:1px solid var(--line)`): coluna 1 = "Antes" (17 px, --muted); coluna 2 = seta "→" (--dim); coluna 3 = "Agora" (17 px, --text); coluna 4 = marca (chip).
- Gere as linhas com `<sc-for>` a partir de arrays calculados em `renderVals()` (um array por seção; cada item `{antes, agora, marca, bg, fg, bd}` com as cores do chip já prontas). Cabeçalho de cada coluna da tabela ("Antes", "Agora", "Marca") em 14 px, caixa alta, --dim.

## Tabela (a fonte da verdade; Antes | Agora | Marca)
### AutoCal
"Pausar aprendizado" / "Retomar aprendizado" | Interruptor "Aprendizado automático: Ligado / Desligado" na barra de baixo (tela 03) | MUDA DE LUGAR
"AutoMatch manual (aplica as médias)" | Ações avançadas ▴, sempre visível; mostra o motivo quando está bloqueado | MUDA DE LUGAR
"Reaprender GNV (apaga o atual)" | Limpar pontos › "Todo o GNV" (tela 04) | MUDA DE LUGAR
"Reaprender gasolina (apaga o atual)" | Limpar pontos › "Toda a gasolina" (tela 04) | MUDA DE LUGAR
Ações avançadas › "Curva K toda em 1,0" | Igual, em Ações avançadas ▴ | EXISTE
Ações avançadas › "Recomeçar tudo" | Igual, em Ações avançadas ▴ (vermelho) | EXISTE
"Preferências" | Ações avançadas ▴ › Preferências (mesma tela cheia) | MUDA DE LUGAR
Círculos de zona (16 px, sem texto) | Limpar pontos: 8 botões grandes "Limpar N", por zona e combustível (tela 04) | MUDA DE LUGAR
Bolinha de ponto › "Reaprender este ponto" | Limpar pontos: tocar no ponto marca; "Limpar N pontos" apaga (2N+1 toques viram N+1) | MUDA DE LUGAR
"Marcar mais pontos" / "Desmarcar" / "Reaprender N marcados" / "Limpar" (só desmarca) | "Desmarcar" e "Limpar N pontos"; arrastar o dedo sobre o gráfico marca vários de uma vez | NOVO
Filtro de giros baixos (automático) | Igual: automático, com o aviso "Apaguei N pontos…" | EXISTE
Quadradinhos da "Aquisição" | Continuam, agora à direita do gráfico | MUDA DE LUGAR
Zonas, "#n/d", eixos e Curva K do gráfico | Continuam; as zonas ganham nome (leve, normal, firme, a fundo) | EXISTE
"Ajustes automáticos X/Y" e "Zona Zn" (cabeçalho) | Painel "Progresso" à esquerda: "Ajustes da central 2 de 3" e "Agora: zona 1" | MUDA DE LUGAR
Bolinhas Gasolina Z1–Z4, GNV Z1–Z4 e Ajustes | Matriz Zona × Combustível: Pronta, 60 % ou Falta | MUDA DE LUGAR
Pergunta "A água chegou a 80–90 °C?" (só no programa original) | Some: o app lê a temperatura da água e mostra "Esperando o motor esquentar" (tela 02) | NOVO
"Ver sessões" / "Exportar" / "Contexto da aquisição" (ocultos hoje) | Continuam ocultos; as sessões vão para Ferramentas › Viagens | EXISTE
### Refino
"Ver alterações" | Painel "O que vai mudar" sempre visível, sem abrir cartão | MUDA DE LUGAR
"Gravar na Curva K" | Linha "Curva K" no painel + botão "Gravar 2 mudanças" (ou só a linha marcada) | MUDA DE LUGAR
"Gravar no Mapa K (reforço do engasgo)" | Linha "Mapa K · reforço do engasgo" no painel | MUDA DE LUGAR
Gravar Curva K e Mapa K em um toque só | "Gravar 2 mudanças": grava o mapa e depois a curva, cada etapa conferida | PRECISA OK
"Refinar por minha conta" / "Esperar a central terminar" | Faixa "A central ainda está aprendendo" com "Assumir o controle" (só aparece quando precisa) | MUDA DE LUGAR
"Desfazer última gravação" | Igual, na barra de baixo | EXISTE
Chip de fase (Central no automático … Estável) | "Passo 3 de 5" com 5 pontos | MUDA DE LUGAR
"Entendi" / "Pronto" / "Voltar ao refino" | Avisos fixos, sem botão extra | MUDA DE LUGAR
### Ajuste local
"Selecionar tudo" / "Limpar" | Iguais, no painel à esquerda | MUDA DE LUGAR
"Curva K → Mapa K" | Assistente "Passar a curva para o Mapa K" (tela 08) | MUDA DE LUGAR
"Concluir curva" | Aparece no assistente quando a etapa 2 falha | EXISTE
"Equivalência % por RPM" / "Preparar sugestão" | "Equivalência por giros" e "Região do engasgo" (sugestão do app) | MUDA DE LUGAR
"Como ajustar" (lista: Percentual / Somar valor / Definir valor) | Três botões lado a lado: Percentual · Somar · Definir | MUDA DE LUGAR
Campo "Valor" + ✎ | Botão ✎ ao lado dos −/+ | MUDA DE LUGAR
"−5 −1 +1 +5" | Iguais, na barra de baixo | MUDA DE LUGAR
"Gravar na central" / "Gravar N alterações" | "Gravar N células" na barra de baixo | MUDA DE LUGAR
Cabeçalhos de linha e de coluna | Iguais; arrastar o dedo também seleciona | EXISTE
"AGORA …" (só texto) | Célula em uso marcada na grade (anel tracejado) | NOVO
Desfazer no Mapa K | "Desfazer" depois de gravar (usa a cópia interna que já existe; falta a ponte) | PRECISA OK
### Ajuste global
Nós do gráfico (tocar ou arrastar) | Iguais; arrastar também move um trecho inteiro | EXISTE
"Fator desejado" + ✎ | Botão ✎ na barra de baixo | MUDA DE LUGAR
"−0,05 −0,01 +0,01 +0,05" | Iguais, na barra de baixo | MUDA DE LUGAR
"Preparar este ponto" e "Limpar" (propostas) | A proposta aparece na hora (curva tracejada); tocar fora do trecho limpa | MUDA DE LUGAR
"Gravar na central" / "Gravar N pontos" | "Gravar N pontos" na barra de baixo | MUDA DE LUGAR
"Reler central" | Botão ↻ ao lado do chip de estado | MUDA DE LUGAR
"Suavizar" | Igual; agora também só no trecho escolhido | EXISTE
"Guardar backup" / "Backups (N)" / "Restaurar backup" | "Guardar cópia" e "Restaurar cópia" no painel | MUDA DE LUGAR
"Curva K toda em 1,0" | Igual, no painel; se a curva está longe de 1,0 oferece antes "Passar para o Mapa K" | NOVO
Escolher um trecho da curva | "Onde estou agora", "Início", "Meio", "Fim", "Tudo" e "Nivelar" | NOVO
### Ferramentas
Anti-apagão (cartão com interruptor) | Evitar apagão › Anti-apagão: interruptor no próprio cartão | EXISTE
Valores do anti-apagão | Tela 10: "Rotação máxima para agir", barras das suas viagens, ±50, Restaurar fábrica, Desfazer, Como funciona | MUDA DE LUGAR
Saída do corte e Marcha lenta | Evitar apagão (só leitura, como hoje) | EXISTE
Troca de gasolina para gás | Gasolina ⇄ gás › Troca: interruptor "um cilindro por vez" + 6 leituras | EXISTE
Volta para gasolina | Gasolina ⇄ gás › Volta para gasolina: interruptor, pressão, espera e Desfazer | EXISTE
Injetores e Outras configurações | Peças do gás (só leitura; "Outras configurações" mostra só o que existe) | MUDA DE LUGAR
Quando o gás entra · Nível do gás · Avisos da central (14 verificações) | Cartões novos em Ferramentas (telas 09 e 12) | NOVO
Cópia de segurança [Salvar cópia] | Viagens e arquivos › Cópia de segurança | MUDA DE LUGAR
Sessões (cartão e tela) | Viagens e arquivos › Viagens (tela 13); a palavra "Sessões" vira "Viagens" | PRECISA OK
Gravação [Começar a gravar] / [Parar de gravar] | Indicador "Gravando agora" + interruptor "Gravar sozinho ao conectar" | MUDA DE LUGAR
Gravações salvas [Resumo da sessão] [Pacote completo] | Lista de viagens + "Compartilhar resumo" + "Pacote completo" | MUDA DE LUGAR
Ajustes da gravação (… [Aplicar]) | Viagens › Ajustes da gravação (o botão vira "Aplicar tamanho") | EXISTE
Refino automático · Bateria · Balão sobre outros apps · Multimídia (Car Info) | Ajustes do app | MUDA DE LUGAR
Registro do app [Salvar registro] [Testar o app] · Teste de gravação | Diagnóstico | MUDA DE LUGAR
### Barras e navegação
Barra do topo: Tensão, Pressão da admissão, Inj. gasolina, Rotação, Nível e lâmpada | Igual, com um só indicador de combustível | EXISTE
"ECU online" + "GNV" + marca "Ω OMEGAS" na barra de baixo | Saem da barra de baixo; a lâmpada do topo mostra o combustível; a marca vai para "Exportar e sobre" | MUDA DE LUGAR
Navegação: AutoCal · Refino · Ajuste local · Ajuste global · Ferramentas | Os mesmos 5 botões, agora com ícone e selo de aviso (ex.: 12 em Refino) | EXISTE
