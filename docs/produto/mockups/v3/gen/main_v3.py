"""Atualiza Main.dc.html (visão geral) para o layout v3 e registra as decisões do dono. Uso: python3 -I main_v3.py [altura]"""
import os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
P = os.path.join(HERE, '..', 'project', 'Main.dc.html')

CHECK = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#38d39f" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" style="flex:none;margin-top:2px"><path d="M20 6 9 17l-5-5"/></svg>'

def troca(s, a, b, tag):
    assert a in s, tag
    return s.replace(a, b, 1)

DECIDIDOS = [
    ('Mexer nas 4 abas', 'desde que seja uma mexida boa'),
    ('Mostrar a água e as 14 verificações', 'desde que bem orientado'),
    ('Gravar com o carro andando', 'sem trava por velocidade'),
    ('GPS', 'o app pode pedir a permissão'),
    ('“Viagens” no lugar de “Sessões”', 'palavra mais simples'),
    ('Balão refeito', '3 estilos, mais discreto, com as bolinhas do gás'),
    ('Barra do topo', 'combustível, nível, injeção, MAP, RPM e tensão, nessa ordem'),
    ('AutoCal', 'tocar na zona apaga; botões de apagar sob o gráfico; sem a faixa Curva K'),
]

PENDENTES = [
    ('5 abas ou 4 abas', 'Esta proposta mantém 5 (sua regra: até 5 botões). A outra frente do app usa 4.', 'Recomendo manter as 5: o AutoCal fica a um toque.'),
    ('Apagar com um toque, sem Desfazer', 'Tocar na zona ou em “Reaprender GNV / gasolina” apaga na hora, como você pediu. Um toque errado custa tempo de aprendizado.', 'Recomendo manter 1 toque e mostrar “Desfazer” até a próxima ação.'),
    ('Gravar correção: um botão para Mapa K e Curva K', 'Grava o Mapa K e depois a Curva K, cada etapa conferida. Se parar no meio, o app avisa e oferece “Concluir curva”.', 'Muda o que a central recebe: preciso do seu OK.'),
    ('Contar engasgos antes → depois', 'Hoje só a Curva K tem veredito (leituras por trecho). Contar os engasgos da região depois de gravar é novo.', 'Precisa de teste com o carro real antes de prometer.'),
    ('Limpeza automática de giros baixos', 'Achei a causa: o filtro só enxerga a cada leitura completa da central (mediana de 38 s) e apaga todas as bandas que subiram nesse intervalo, até de carro andando.', 'Proposta no PENDENTE; muda só a lógica do app. Preciso do seu OK.'),
    ('Nível do gás em dois pontos', 'Marcar Cheio e Reserva e usar a média das leituras (tela 20), para a leitura ficar precisa.', 'É proposta: confirmar com tanque real.'),
    ('GPS só na hora em que serve', 'O Android pede a permissão ao abrir Viagens, nunca na abertura do app. Sem ela, tudo segue igual.', 'Recomendo assim.'),
]

def regra(t, d):
    return '<div style="box-sizing:border-box;padding:16px 18px;display:flex;flex-direction:column;gap:6px;background:var(--surface);border:1px solid var(--line);border-radius:14px"><div style="font-size:20px;font-weight:700">%s</div><div style="font-size:17px;line-height:1.4;color:var(--muted)">%s</div></div>' % (t, d)

def decidido(t, d):
    return '<div style="display:flex;gap:10px;align-items:flex-start">%s<div style="font-size:17px;line-height:1.35"><b style="font-weight:700">%s</b> <span style="color:var(--muted)">· %s</span></div></div>' % (CHECK, t, d)

def pendente(t, d, r):
    return ('<div style="box-sizing:border-box;padding:16px 18px;display:flex;flex-direction:column;gap:6px;background:var(--surface);border:1px solid #6b5420;border-radius:14px">'
            '<div style="display:flex;align-items:center;gap:10px"><span style="flex:none;font-size:14px;font-weight:800;letter-spacing:.06em;color:#1b1303;background:var(--warn);border-radius:6px;padding:2px 7px">PRECISA OK</span><div style="font-size:19px;font-weight:700;line-height:1.25">%s</div></div>'
            '<div style="font-size:16px;line-height:1.4;color:var(--muted)">%s</div>'
            '<div style="font-size:16px;line-height:1.35;font-weight:600;color:var(--warn)">%s</div></div>') % (t, d, r)

def main(altura):
    s = open(os.path.join(HERE, 'parts', 'Main.base.html'), encoding='utf-8').read()  # sempre parte da versão antiga: idempotente
    # jornada
    s = troca(s, 'Telas 02, 03, 03b e 04</div>', 'Telas 02, 03 e 16 (800×480)</div>', 'jornada-aprender')
    s = troca(s, 'Tela 05 · 6 estados</div>', 'Telas 05, 05b, 05c e 05d</div>', 'jornada-refinar')
    # abas
    s = troca(s, '<span>Limpar pontos por zona ou arrastando o dedo</span>', '<span>Tocar na zona apaga; arrastar escolhe pontos</span>', 'aba-autocal-b3')
    s = troca(s, 'Telas 02, 03, 03b, 04 e 16 (800×480)</div>', 'Telas 02, 03 e 16 (800×480)</div>', 'aba-autocal-link')
    s = troca(s, '<span>Trilha: Achar · Conferir · Gravar · Medir</span>', '<span>Um botão: Gravar correção; sem trilha de passos</span>', 'aba-refino-b1')
    s = troca(s, '<span>Mede se melhorou; se piorar, o Desfazer vira o botão principal</span>', '<span>Mede se melhorou; se piorar: Restaurar N pontos</span>', 'aba-refino-b2')
    s = troca(s, '<div style="font-size:16px;font-weight:600;color:var(--accent)">Tela 05</div>', '<div style="font-size:16px;font-weight:600;color:var(--accent)">Telas 05, 05b, 05c e 05d</div>', 'aba-refino-link')
    s = troca(s, 'Tudo o mais, em 7 grupos', 'Tudo o mais, em 8 grupos', 'aba-ferr-t')
    s = troca(s, '<span>Balão sobre outros apps refeito: avisa de relance (tela 19)</span>', '<span>Balão com 3 estilos e as bolinhas do gás (19); nível do gás (20)</span>', 'aba-ferr-b2')
    s = troca(s, 'Telas 09 a 13 e 19</div>', 'Telas 09 a 13, 19 e 20</div>', 'aba-ferr-link')
    # regras
    s = troca(s, 'Arrastar para selecionar vários e aplicar de uma vez.', 'Tocar na zona apaga a zona toda; arrastar escolhe vários pontos.', 'regra-lote')
    s = troca(s, 'Gravou? Aparece “Gravado e conferido” e fica na tela, com Desfazer.', 'Gravou? Um selo discreto no canto de cima diz “Gravado e conferido”; Desfazer fica no painel. Nada trava.', 'regra-toque')
    s = troca(s, 'Painel e botões à esquerda, gráfico à direita. Sem central, igual em toda aba.', 'Painel e botões à esquerda, gráfico à direita usando a tela toda. Sem central, igual em toda aba.', 'regra-lugar')
    # seção de baixo: regras + decididos, depois pendentes
    a = s.index('<section style="display:grid;grid-template-columns:minmax(0,1fr) 520px;gap:24px">')
    b = s.index('</section>', s.index('PRECISA OK', a)) + len('</section>')
    regras = [('Uma frase, uma ação', 'A aba abre dizendo em que pé está e qual é o próximo toque.'),
              ('O app mede, você decide', 'Água, gás, falhas e giros são lidos sozinhos. Nada de pergunta que o app já sabe.'),
              ('Em lote, não ponto a ponto', 'Tocar na zona apaga a zona toda; arrastar escolhe vários pontos.'),
              ('Cada número se explica', 'Diz o que é e o que muda se subir ou descer.'),
              ('Um toque, feito', 'Gravou? Um selo discreto no canto de cima diz “Gravado e conferido”; Desfazer fica no painel. Nada trava.'),
              ('Mesmo lugar, mesma palavra', 'Painel e botões à esquerda, gráfico à direita usando a tela toda. Sem central, igual em toda aba.')]
    novo = ('<section style="display:grid;grid-template-columns:minmax(0,1fr) 520px;gap:24px">\n'
            '<div style="display:flex;flex-direction:column;gap:16px">\n<div style="font-size:14px;font-weight:700;letter-spacing:.14em;color:var(--dim)">SEIS REGRAS EM TODA TELA</div>\n'
            '<div style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px">\n' + '\n'.join(regra(t, d) for t, d in regras) + '\n</div>\n</div>\n'
            '<div style="box-sizing:border-box;padding:22px;display:flex;flex-direction:column;gap:12px;background:var(--surface);border:1px solid #1d5a46;border-radius:14px">\n'
            '<div style="display:flex;align-items:center;gap:10px"><div style="font-size:14px;font-weight:800;letter-spacing:.08em;color:#04150e;background:var(--ok);border-radius:6px;padding:3px 8px">DECIDIDO</div><div style="font-size:22px;font-weight:700">O que você já decidiu</div></div>\n'
            + '\n'.join(decidido(t, d) for t, d in DECIDIDOS) + '\n</div>\n</section>\n\n'
            '<section style="display:flex;flex-direction:column;gap:16px">\n<div style="font-size:14px;font-weight:700;letter-spacing:.14em;color:var(--dim)">O QUE EU AINDA PRECISO DO SEU OK</div>\n'
            '<div style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px">\n' + '\n'.join(pendente(*p) for p in PENDENTES) +
            '\n<div style="box-sizing:border-box;padding:16px 18px;display:flex;flex-wrap:wrap;gap:10px 16px;align-items:center;font-size:16px;color:var(--muted)"><span style="display:flex;align-items:center;gap:6px"><b style="font-size:14px;letter-spacing:.06em;color:#04150e;background:var(--ok);border-radius:5px;padding:2px 6px">EXISTE</b>já está no app</span><span style="display:flex;align-items:center;gap:6px"><b style="font-size:14px;letter-spacing:.06em;color:#fff;background:var(--accent-btn);border-radius:5px;padding:2px 6px">NOVO</b>ainda não existe</span><span style="display:flex;align-items:center;gap:6px"><b style="font-size:14px;letter-spacing:.06em;color:#1b1303;background:var(--warn);border-radius:5px;padding:2px 6px">PRECISA OK</b>muda o que a central recebe</span></div>\n'
            '</div>\n</section>')
    s = s[:a] + novo + s[b:]
    s = re.sub(r'(position:relative;width:1600px;height:)\d+(px;box-sizing:border-box;padding:64px)', r'\g<1>%d\2' % altura, s, count=1)
    s = re.sub(r'("\$preview":\{"width":1600,"height":)\d+', r'\g<1>%d' % altura, s, count=1)
    open(P, 'w', encoding='utf-8').write(s)
    print('Main atualizado; altura', altura)

if __name__ == '__main__':
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 2500)
