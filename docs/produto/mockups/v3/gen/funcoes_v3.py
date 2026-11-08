"""Reescreve os dados da folha 18-Funcoes ("Nada some") para o layout v3. Uso: python3 -I funcoes_v3.py"""
import json, os, re
HERE = os.path.dirname(os.path.abspath(__file__))
P = os.path.join(HERE, '..', 'project', '18-Funcoes.dc.html')
E, M, N, O = 'EXISTE', 'MUDA DE LUGAR', 'NOVO', 'PRECISA OK'

AUTOCAL = [
 ['"Pausar aprendizado" / "Retomar aprendizado"', 'Interruptor "Aprendizado automático: Ligado / Desligado" no topo do painel esquerdo (tela 03)', M],
 ['"AutoMatch manual (aplica as médias)"', 'Botão "AutoMatch manual" sob o gráfico; cinza, com o motivo ("espera a central"), enquanto ela termina os ajustes', M],
 ['"Reaprender GNV (apaga o atual)"', 'Botão "Reaprender GNV" sob o gráfico: 1 toque, sem janela', M],
 ['"Reaprender gasolina (apaga o atual)"', 'Botão "Reaprender gasolina" sob o gráfico: 1 toque', M],
 ['Ações avançadas › "Curva K toda em 1,0"', 'Mais › "Curva K toda em 1,0" (o menu "Ações avançadas" passa a se chamar "Mais")', M],
 ['Ações avançadas › "Recomeçar tudo"', 'Mais › "Recomeçar tudo" (vermelho)', M],
 ['"Preferências"', 'Mais › Preferências (mesma tela cheia)', M],
 ['Círculos de zona (16 px, sem texto)', 'Matriz Zona × Combustível: tocar numa célula apaga os pontos daquela zona e daquele combustível (1 toque, sem janela; "Toque para apagar" na vertical)', M],
 ['Bolinha de ponto › "Reaprender este ponto"', 'Tocar no ponto do gráfico o escolhe; "Apagar 1 ponto" sob o gráfico', M],
 ['"Marcar mais pontos" / "Desmarcar" / "Reaprender N marcados" / "Limpar" (só desmarca)', 'Arrastar o dedo sobre o gráfico escolhe vários pontos de uma vez (caixa tracejada); "Apagar N pontos" apaga; tocar fora desmarca. Some o modo "Limpar pontos" e a volta', N],
 ['(não existe) Pontos fora da curva', 'Botão "Limpar N pontos · fora da curva" sob o gráfico; os pontos aparecem com anel tracejado', N],
 ['Filtro de giros baixos (automático)', 'Ferramentas › Aprendizado › Limpeza automática: liga/desliga e giros mínimos (±50, padrão 1.200); avisa "Apaguei N pontos com RPM baixo". Algoritmo a melhorar (pedido do dono)', O],
 ['Quadradinhos da "Aquisição"', 'Continuam à direita do gráfico (em 800×480 saem por falta de espaço)', M],
 ['Zonas, "#n/d", eixos e Curva K do gráfico', 'Zonas e eixos continuam (as zonas ganham nome: leve, normal, firme, a fundo). A faixa "Curva K" sai do AutoCal (dono: inútil ali); a curva fica no Ajuste global e no Refino', M],
 ['"Ajustes automáticos X/Y" e "Zona Zn" (cabeçalho)', 'Chip "Aprendendo ●●○ 2 de 3" no cabeçalho; a zona de agora fica marcada na matriz', M],
 ['Bolinhas Gasolina Z1–Z4, GNV Z1–Z4 e Ajustes', 'Matriz Zona × Combustível: Pronta, 60 % ou Falta', M],
 ['Pergunta "A água chegou a 80–90 °C?" (só no programa original)', 'Some: o app lê a temperatura da água e mostra "Esperando o motor esquentar" (tela 02)', N],
 ['"Ver sessões" / "Exportar" / "Contexto da aquisição" (ocultos hoje)', 'Continuam ocultos; as sessões vão para Ferramentas › Viagens', E],
 ['(não existe) Aviso de gravação', 'Selo discreto no canto direito da barra do topo: "Gravando…" · "Conferindo…" · "Gravado e conferido" · "Não gravou". A tela nunca trava (tela 14)', N],
]
REFINO = [
 ['“Ver alterações” (abre cartão)', 'Painel “O que vou fazer” sempre visível: uma linha por mudança (Mapa K · Curva K); sem cartão', M],
 ['“Gravar na Curva K” e “Gravar no Mapa K”', 'Um botão só: “Gravar correção” (grava o Mapa K e depois a Curva K, cada etapa conferida pela central)', O],
 ['Gravar Mapa K e Curva K (hoje 2 botões independentes)', 'Se parar no meio (ex.: cabo solto), o app avisa “Mapa K gravado. Curva K ainda não.” e oferece “Concluir curva” (tela 14)', O],
 ['“Refinar por minha conta” / “Esperar a central terminar”', 'Tela 05d: com a central ainda aprendendo, “Gravar correção” fica cinza com o motivo e “Refinar por minha conta” libera, com aviso de risco', M],
 ['“Desfazer última gravação”', '“Desfazer” no painel depois de gravar. Quando piorou (05c): “Restaurar N pontos” (principal) e “Desfazer tudo”', M],
 ['Chip de fase (Central no automático … Estável)', 'Sem trilha de passos: o chip diz só o estado (2 correções prontas · Medindo · Tudo certo · Piorou · Esperando a central) e o painel mostra a única ação possível', M],
 ['Medindo a gravação (“faltam N trechos · X de 100 leituras”)', 'Depois de gravar: “12 de 100 leituras novas” e “2 trechos melhoraram, 0 pioraram”', E],
 ['Dica do que falta (“rode no GNV passando por 3,0–3,5 ms”)', 'Tela 05b: 5 faixas (ok/falta) e a frase “acelere mais firme no GNV: injeção de 7,5 a 12 ms”', M],
 ['Gráfico do Refino = gráfico do AutoCal (legenda de 5 itens)', 'Mesmo gráfico, com o resto apagado; legenda de 3 itens (Apagou · Quase apagou · Onde vai o reforço)', O],
 ['Proposta só em texto (a região não aparece no gráfico)', 'Caixa “Aqui o carro engasga” no gráfico e Antes | Depois da linha do GNV', N],
 ['Resultado só da Curva K (o Mapa K não tem veredito)', 'Melhorou/Piorou com “Trechos com o GNV igual à gasolina 2 → 5” (já existe) e “Engasgos na região 6 → 9” (NOVO: contar engasgos depois de gravar)', N],
 ['“Entendi” / “Pronto” / “Voltar ao refino”', 'Avisos fixos, sem botão extra', M],
 ['(não existe) Histórico de correções', 'Tela 05b: “Últimas correções” (Melhorou · Medindo · Piorou ›); Piorou leva à tela 05c', N],
 ['(não existe) Ajuda do Refino', 'Botão ? no cabeçalho: O que é · O que fazer · O que acontece (balão ao lado, nunca por cima)', N],
]
AJUSTE_LOCAL = [
 ['"Selecionar tudo" / "Limpar"', 'Iguais, no painel à esquerda', M],
 ['"Curva K → Mapa K"', 'Assistente "Passar a curva para o Mapa K" (tela 08)', M],
 ['"Concluir curva"', 'Aparece no assistente quando a etapa 2 falha', E],
 ['"Equivalência % por RPM" / "Preparar sugestão"', '"Equivalência por giros" e "Região do engasgo" (sugestão do app, já escolhida)', M],
 ['"Como ajustar" (lista: Percentual / Somar valor / Definir valor)', 'Três botões lado a lado: Percentual · Somar · Definir', M],
 ['Campo "Valor" + ✎', '✎ dentro do número, entre − e +', M],
 ['"−5 −1 +1 +5"', '−5 −1 [valor ✎] +1 +5 no painel', M],
 ['"Gravar na central" / "Gravar N alterações"', '"Gravar N células" no fundo do painel; depois vira "Desfazer"', M],
 ['Cabeçalhos de linha e de coluna', 'Iguais; arrastar o dedo também seleciona', E],
 ['"AGORA …" (só texto)', 'Célula em uso marcada na grade (anel tracejado)', N],
 ['Desfazer no Mapa K', '"Desfazer" depois de gravar (usa a cópia interna que já existe; falta a ponte)', O],
]
AJUSTE_GLOBAL = [
 ['Nós do gráfico (tocar ou arrastar)', 'Iguais; arrastar também move um trecho inteiro', E],
 ['"Fator desejado" + ✎', 'Caixa do valor com ✎ no painel', M],
 ['"−0,05 −0,01 +0,01 +0,05"', 'Iguais, no painel', M],
 ['"Preparar este ponto" e "Limpar" (propostas)', 'A proposta aparece na hora (curva tracejada); tocar fora do trecho limpa', M],
 ['"Gravar na central" / "Gravar N pontos"', '"Gravar N pontos" no fundo do painel; depois vira "Desfazer"', M],
 ['"Reler central"', 'Botão ↻ ao lado do chip de estado', M],
 ['"Suavizar"', 'Igual; agora também só no trecho escolhido', E],
 ['"Guardar backup" / "Backups (N)" / "Restaurar backup"', 'Mais › "Guardar cópia" e "Restaurar cópia"', M],
 ['"Curva K toda em 1,0"', 'Mais › "Curva K toda em 1,0"; se a curva está longe de 1,0, avisa antes e oferece "Passar para o Mapa K"', N],
 ['Escolher um trecho da curva', '"Onde estou agora", "Início", "Meio", "Fim", "Tudo" e "Nivelar"', N],
]
FERRAMENTAS = [
 ['Anti-apagão (cartão com interruptor)', 'Evitar apagão › Anti-apagão: interruptor no próprio cartão', E],
 ['Valores do anti-apagão', 'Tela 10: "Rotação máxima para agir", barras das suas viagens, ±50, Restaurar fábrica, Desfazer, Como funciona', M],
 ['Saída do corte e Marcha lenta', 'Evitar apagão (só leitura, como hoje)', E],
 ['Troca de gasolina para gás', 'Gasolina ⇄ gás › Troca: interruptor "um cilindro por vez" + 6 leituras', E],
 ['Volta para gasolina', 'Gasolina ⇄ gás › Volta para gasolina: interruptor, pressão, espera e Desfazer', E],
 ['Injetores e Outras configurações', 'Peças do gás (só leitura; "Outras configurações" mostra só o que existe)', M],
 ['Quando o gás entra · Nível do gás · Avisos da central (14 verificações)', 'Cartões novos em Ferramentas (telas 09, 12 e 20); a água e as 14 verificações aparecem sem o usuário pedir', N],
 ['(não existe) Calibrar o nível do gás', 'Tela 20: marque Cheio e Reserva uma vez; o app cruza com as últimas 40 leituras da central e sugere "Parece que você abasteceu". As bolinhas aparecem no topo e no balão', O],
 ['Cópia de segurança [Salvar cópia]', 'Viagens e arquivos › Cópia de segurança', M],
 ['Sessões (cartão e tela)', 'Viagens e arquivos › Viagens (tela 13); a palavra "Sessões" vira "Viagens" (aprovado pelo dono)', M],
 ['Gravação [Começar a gravar] / [Parar de gravar]', 'Indicador "Gravando agora" + interruptor "Gravar sozinho ao conectar"', M],
 ['Gravações salvas [Resumo da sessão] [Pacote completo]', 'Lista de viagens + "Compartilhar resumo" + "Pacote completo"', M],
 ['Ajustes da gravação (… [Aplicar])', 'Viagens › Ajustes da gravação (o botão vira "Aplicar tamanho")', E],
 ['Refino automático · Bateria · Balão sobre outros apps · Multimídia (Car Info)', 'Ajustes do app', M],
 ['Registro do app [Salvar registro] [Testar o app] · Teste de gravação', 'Diagnóstico', M],
 ['Balão flutuante (Ω + 4 linhas em jargão: CÉLULA, STFT, PETROL, RPM)', 'Tela 19: pílula com estado (No gás, Em gasolina, Pouco gás, Tensão alta, Sem central) e número; 3 estilos (Compacto · Mínimo · Completo) com as bolinhas do nível do gás; "Abrir o OMEGAS"; tamanho, lado e modo discreto', O],
 ['(não existe) Sugestões do app reunidas', 'Ferramentas › "Para você agora": o app cruza viagens e central e traz o botão que resolve (Usar 1.800 giros, Ver aviso, Compartilhar resumo)', N],
 ['(não existe) Por que o carro apagou?', 'Ferramentas › Diagnóstico › "O carro está apagando?" (tela 11): linha do tempo da telemetria e a causa mais provável, só leitura', N],
 ['Gravar e apagar com o carro andando', 'Sem trava por velocidade, como hoje (dono, 2026-10-27). O GPS só ajuda a explicar apagões', E],
 ['(não existe) Localização (GPS)', 'Ferramentas › Aprendizado › Localização (GPS): o Android pede a permissão uma vez; o app sabe a velocidade e guarda o trajeto das viagens', N],
]
BARRAS = [
 ['Barra do topo: Tensão, Pressão da admissão, Inj. gasolina, Rotação, Nível e lâmpada', 'Mesma informação em ordem de prioridade: No gás · Nível · Inj. gasolina · MAP · RPM · Tensão (rótulos curtos MAP e RPM)', M],
 ['"ECU online" + "GNV" + marca "Ω OMEGAS" na barra de baixo', 'Saem da barra de baixo; a lâmpada do topo mostra o combustível; a marca vai para "Exportar e sobre"', M],
 ['Navegação: AutoCal · Refino · Ajuste local · Ajuste global · Ferramentas', 'Os mesmos 5 botões, agora com ícone e selo de aviso (ex.: 12 em Refino)', E],
 ['Faixa de botões embaixo de cada tela', 'Some: os botões ficam no fundo do painel esquerdo e o gráfico usa a altura toda', M],
 ['(não existe) Aviso de gravação', 'Selo discreto à direita da barra do topo; nunca bloqueia a tela (tela 14)', N],
]

def js(rows):
    return json.dumps(rows, ensure_ascii=False, indent=2)

def main(altura=3400):
    s = open(P, encoding='utf-8').read()
    a = s.index('    const autoCal = linhas(')
    b = s.index('    return { autoCal, refino, ajusteLocal, ajusteGlobal, ferramentas, barras };')
    novo = ''.join('    const %s = linhas(%s);\n' % (n, js(r)) for n, r in (('autoCal', AUTOCAL), ('refino', REFINO), ('ajusteLocal', AJUSTE_LOCAL), ('ajusteGlobal', AJUSTE_GLOBAL), ('ferramentas', FERRAMENTAS), ('barras', BARRAS)))
    s = s[:a] + novo + s[b:]
    s = re.sub(r'hint-placeholder-count="\d+"', lambda m: m.group(0), s)
    s = re.sub(r'(width:1800px;height:)\d+(px;overflow:hidden)', r'\g<1>%d\2' % altura, s, count=1)
    s = re.sub(r'("\$preview":\{"width":1800,"height":)\d+', r'\g<1>%d' % altura, s, count=1)
    open(P, 'w', encoding='utf-8').write(s)
    print('18-Funcoes atualizada:', sum(map(len, (AUTOCAL, REFINO, AJUSTE_LOCAL, AJUSTE_GLOBAL, FERRAMENTAS, BARRAS))), 'linhas; altura', altura)

if __name__ == '__main__':
    import sys
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 3400)
