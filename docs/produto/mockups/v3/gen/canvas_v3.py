"""Monta canvas.json a partir dos arquivos em project/ (tamanho vem do $preview de cada tela).
Uso: python3 -I canvas_v3.py   (idempotente)"""
import json, os, re
HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.join(HERE, '..', 'project')
GAP = 80
LINHAS = [
    (0, ['Main']),
    (2180, ['01-Conectar', '01b-Conectar-permissao', '01c-Conectar-retomando', '01d-Conectar-problema', '02-AutoCal-esperando', '03-AutoCal']),
    (3120, ['05-Refino', '05b-Refino-tudo-certo', '05c-Refino-piorou', '05d-Refino-central-aprendendo', '06-Ajuste-local', '07-Ajuste-global', '08-Curva-para-Mapa']),
    (4060, ['09-Ferramentas', '10-Anti-apagao', '11-Carro-apagando', '12-Avisos-da-central', '13-Viagens', '21-Memoria']),
    (4860, ['19-Balao', '20-Nivel-do-gas']),
    (5780, ['14-Estados', '15-Padroes', '16-AutoCal-800x480', '17-Ajuste-local-800x480']),
]
TITULOS = {
    '01b-Conectar-permissao': '01b · Conectar: permissão', '01c-Conectar-retomando': '01c · Conectar: retomando', '01d-Conectar-problema': '01d · Conectar: problema', '21-Memoria': '21 · Memória e coerência',
    'Main': 'Jornada e navegação', '01-Conectar': '01 · Conectar', '02-AutoCal-esperando': '02 · AutoCal esperando o motor', '03-AutoCal': '03 · AutoCal: toque para apagar',
    '05-Refino': '05 · Refino: achei um problema', '05b-Refino-tudo-certo': '05b · Refino: tudo certo', '05c-Refino-piorou': '05c · Refino: piorou',
    '05d-Refino-central-aprendendo': '05d · Refino: central ainda aprende', '06-Ajuste-local': '06 · Ajuste local', '07-Ajuste-global': '07 · Ajuste global',
    '08-Curva-para-Mapa': '08 · Curva para Mapa K', '09-Ferramentas': '09 · Ferramentas', '10-Anti-apagao': '10 · Anti-apagão', '11-Carro-apagando': '11 · Carro apagando',
    '12-Avisos-da-central': '12 · Avisos da central', '13-Viagens': '13 · Viagens', '19-Balao': '19 · Balão sobre outros apps', '20-Nivel-do-gas': '20 · Nível do gás',
    '14-Estados': '14 · Estados', '15-Padroes': '15 · Padrões', '16-AutoCal-800x480': '16 · AutoCal 800×480', '17-Ajuste-local-800x480': '17 · Ajuste local 800×480', '18-Funcoes': '18 · Nada some',
}
EXTRA_INTERATIVAS = {'01-Conectar', '01b-Conectar-permissao', '01c-Conectar-retomando', '01d-Conectar-problema', '21-Memoria'}  # só links, mas é tela de ação
NOTAS = [('n-visao', -300, 'Visão geral', None), ('n-1', 2060, '1 · Começar: conectar e aprender (o app mede sozinho)', 2180), ('n-2', 3000, '2 · Refinar e afinar', 3120),
         ('n-3', 3940, '3 · Ferramentas, balão e nível do gás', 4060), ('n-4', 5660, '4 · Estados, padrões, telas pequenas e inventário', 5780)]

def tamanho(nome):
    s = open(os.path.join(PROJ, nome + '.dc.html'), encoding='utf-8').read()
    m = re.search(r'"\$preview"\s*:\s*\{\s*"width"\s*:\s*(\d+)\s*,\s*"height"\s*:\s*(\d+)', s)
    return (int(m.group(1)), int(m.group(2))), ('onClick=' in s or nome in EXTRA_INTERATIVAS)

def main():
    boards, ordem, larg = {}, [], {}
    y_ultimo = 0
    for y, nomes in LINHAS:
        x = 0
        for n in nomes:
            (w, h), inter = tamanho(n)
            b = {'x': x, 'y': y, 'w': w, 'h': h, 'title': TITULOS[n]}
            if inter:
                b['is_interactive'] = True
            boards[n + '.dc.html'] = b
            ordem.append(n + '.dc.html')
            x += w + GAP
            y_ultimo = max(y_ultimo, y + h)
        larg[y] = x - GAP
    # inventário embaixo de tudo
    (w, h), inter = tamanho('18-Funcoes')
    y18 = y_ultimo + 120
    boards['18-Funcoes.dc.html'] = {'x': 0, 'y': y18, 'w': w, 'h': h, 'title': TITULOS['18-Funcoes']}
    ordem.append('18-Funcoes.dc.html')
    notas = {}
    for k, y, txt, linha in NOTAS:
        notas[k] = {'x': 0, 'y': y, 'text': txt, 'kind': 'title1', 'maxW': (larg.get(linha, 1600) if linha is not None else 1600)}
    # a nota 4 cobre também o inventário
    notas['n-4']['maxW'] = max(larg[5780], 1800)
    p = os.path.join(PROJ, 'canvas.json')
    antigo = json.load(open(p, encoding='utf-8'))
    novo = {k: v for k, v in antigo.items() if k not in ('boards', 'order', 'notes')}
    novo['order'] = ordem; novo['boards'] = boards; novo['notes'] = notas
    json.dump(novo, open(p, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
    print('canvas.json:', len(boards), 'telas; inventário em y =', y18)

if __name__ == '__main__':
    main()
