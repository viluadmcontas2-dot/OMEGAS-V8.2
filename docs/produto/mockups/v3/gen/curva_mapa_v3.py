"""Gera 08-Curva-para-Mapa.dc.html no layout v3: sub-tela do Ajuste global (botão Voltar no cabeçalho),
sem barra de ações, curva antes/depois grande e três fatos (sem barras decorativas).
Uso: python3 -I curva_mapa_v3.py
"""
import os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import retopbar as RT
import autocal_v3 as A
import ajuste_local_v3 as AL

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.join(HERE, '..', 'project')
ico = A.ico
def f(v): return ('%.1f' % v).rstrip('0').rstrip('.')

K = [1.10, 1.12, 1.15, 1.18, 1.21, 1.23, 1.25, 1.26, 1.27, 1.27, 1.26, 1.25, 1.23, 1.21, 1.19, 1.17, 1.15, 1.14, 1.13, 1.12, 1.11, 1.10, 1.10, 1.09, 1.09, 1.08, 1.08, 1.07, 1.07, 1.06]
def kAt(ms):
    idx = max(0.0, min(29.0, (ms - 1.5) / (10.1 / 29)))
    a = int(idx); b = min(29, a + 1); t = idx - a
    return K[a] * (1 - t) + K[b] * t
# depois: o mapa assume metade do ganho (raiz do produto); a curva guarda o resto
KD = [1 + (k - 1) * 0.5 if True else k for k in K]
KD = [1.05, 1.06, 1.075, 1.09, 1.105, 1.115, 1.125, 1.13, 1.13, 1.13, 1.13, 1.125, 1.115, 1.105, 1.095, 1.085, 1.075, 1.07, 1.065, 1.06, 1.055, 1.05, 1.05, 1.045, 1.045, 1.04, 1.04, 1.035, 1.035, 1.03]
KD = [1.05, 1.06, 1.075, 1.09, 1.10, 1.115, 1.125, 1.13, 1.13, 1.13, 1.12, 1.115, 1.105, 1.10, 1.09, 1.085, 1.075, 1.07, 1.065, 1.06, 1.055, 1.05, 1.05, 1.045, 1.045, 1.04, 1.04, 1.035, 1.035, 1.03]

PX0, PX1, PY0, PY1 = 70, 560, 56, 392
def px(i): return PX0 + 10 + i * (PX1 - PX0 - 20) / 29
KMIN, KMAX = 0.9, 1.4
def py(k): return PY1 - (k - KMIN) / (KMAX - KMIN) * (PY1 - PY0)

def grafico():
    o = []
    for k in (0.9, 1.0, 1.1, 1.2, 1.3, 1.4):
        dest = k == 1.0
        o.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="%s" stroke-opacity="%s" stroke-width="%s"%s/>' % (PX0, f(py(k)), PX1, f(py(k)), '#f4f7fb' if dest else '#1b2631', '.55' if dest else '1', '1.5' if dest else '1', ' stroke-dasharray="3 5"' if dest else ''))
        o.append('<text x="%d" y="%s" fill="%s" font-size="14" font-weight="%s" text-anchor="end">%s</text>' % (PX0 - 8, f(py(k) + 5), '#f4f7fb' if dest else '#9aa9bc', 700 if dest else 400, ('%.1f' % k).replace('.', ',')))
    for i, t in ((0, '1,5'), (10, '5,0'), (20, '8,5'), (29, '11,6')):
        o.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">%s</text>' % (f(px(i)), PY1 + 22, t))
    o.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">Injeção (ms)</text>' % (f((PX0 + PX1) / 2), PY1 + 46))
    o.append('<polyline points="%s" fill="none" stroke="#2f8cff" stroke-opacity=".5" stroke-width="3.5" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(px(i)), f(py(k))) for i, k in enumerate(K)))
    o.append('<polyline points="%s" fill="none" stroke="#2f8cff" stroke-width="4" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(px(i)), f(py(k))) for i, k in enumerate(KD)))
    return '\n'.join(o)

def main():
    old = open(os.path.join(PROJ, '08-Curva-para-Mapa.dc.html'), encoding='utf-8').read()
    sh = AL.casca(old)
    # a nav do 08 marca "Ajuste global": mantém
    def fato(top, icone, rot, valor, cor, linha):
        return ('<div style="position:absolute;left:600px;top:%dpx;width:214px;height:124px;box-sizing:border-box;padding:10px 14px;display:flex;flex-direction:column;gap:2px;background:var(--surface2);border:1px solid var(--line);border-radius:12px">'
                '<span style="display:flex;align-items:center;gap:8px;font-size:15px;font-weight:600;color:var(--muted)">%s%s</span>'
                '<b style="font-size:28px;line-height:34px;font-weight:700;font-variant-numeric:tabular-nums;white-space:nowrap;color:%s">%s</b>'
                '<span style="font-size:15px;line-height:1.3;color:var(--muted)">%s</span></div>\n') % (top, icone, rot, cor, valor, linha)
    curva = '<path d="M3 17c3-9 6-9 9-5s6 4 9-5"/>'
    grade = '<rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>'
    okc = '<circle cx="12" cy="12" r="10"/><path d="m8 12 3 3 5-6"/>'
    fatos = (fato(40, ico(curva, '#2f8cff', 2, 22), 'Curva K', '<span style="color:var(--muted)">1,27</span> → 1,13', 'var(--text)', 'O ponto mais alto da curva desce.')
             + fato(172, ico(grade, '#2f8cff', 2, 22), 'Mapa K', '+3 a +13 %', 'var(--text)', '144 células do mapa mudam.')
             + fato(304, ico(okc, '#38d39f', 2.2, 22), 'No motor', 'quase igual', 'var(--ok)', 'Mapa × curva dá quase o mesmo.'))
    html = r'''<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>Passar a curva para o Mapa K</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
@@HELMET@@
@@ROOT@@

@@TOPBAR@@
<sc-if value="{{ done }}" hint-placeholder-val="{{ false }}"><div role="status" style="position:absolute;right:16px;top:10px;height:36px;box-sizing:border-box;padding:0 12px;display:flex;align-items:center;gap:7px;border-radius:10px;background:var(--ok-soft);border:1px solid #1d5a46;color:var(--ok);font-size:15px;font-weight:600;white-space:nowrap">@@CK@@Gravado e conferido</div></sc-if>
<div style="position:absolute;left:32px;top:64px;width:1216px;height:56px;display:flex;align-items:center;gap:14px">
<a href="07-Ajuste-global.dc.html" aria-label="Voltar ao Ajuste global" style="flex:none;box-sizing:border-box;width:56px;height:56px;display:flex;align-items:center;justify-content:center;border-radius:12px;border:1px solid var(--line);background:var(--surface2);color:var(--text);text-decoration:none">@@I_BACK@@</a>
<h1 style="margin:0;flex:none;font-size:28px;font-weight:700;letter-spacing:-.01em">Passar a curva para o Mapa K</h1>
<div style="flex:none;box-sizing:border-box;height:36px;padding:0 14px;display:flex;align-items:center;border-radius:18px;background:var(--accent-soft);border:1px solid #2a4a7e;font-size:16px;font-weight:700">2 etapas</div>
<div style="flex:1;min-width:0;box-sizing:border-box;height:48px;padding:0 16px;display:flex;align-items:center;gap:10px;border-radius:12px;background:{{msgBg}};border:1px solid {{msgBd}};font-size:17px;font-weight:600;white-space:nowrap">
<sc-if value="{{ done }}" hint-placeholder-val="{{ false }}">@@I_OK@@</sc-if>
<sc-if value="{{ notDone }}" hint-placeholder-val="{{ true }}">@@I_INFO@@</sc-if>
<span>{{ msgText }}</span>
</div>
</div>

<section aria-label="Antes e depois" style="position:absolute;left:418px;top:128px;width:830px;height:496px;background:var(--surface);border:1px solid var(--line);border-radius:14px;overflow:hidden">
<div style="position:absolute;left:16px;top:12px;display:flex;align-items:center;gap:14px;font-size:15px;color:var(--muted);white-space:nowrap">
<b style="font-size:14px;letter-spacing:.12em;color:var(--dim)">CURVA K</b>
<span style="display:flex;align-items:center;gap:6px"><span style="width:18px;height:3px;border-radius:2px;background:var(--accent);opacity:.5"></span>Antes</span>
<span style="display:flex;align-items:center;gap:6px"><span style="width:18px;height:3px;border-radius:2px;background:var(--accent)"></span>Depois</span>
</div>
<svg width="590" height="496" viewBox="0 0 590 496" style="position:absolute;left:0;top:0;pointer-events:none" role="img" aria-label="Curva K antes e depois: o máximo cai de 1,27 para 1,13">
@@GRAFICO@@
</svg>
<div style="position:absolute;left:200px;top:92px;font-size:15px;font-weight:700;color:var(--muted);background:rgba(7,10,15,.8);padding:0 6px;border-radius:4px;pointer-events:none">antes: máx. 1,27</div>
<div style="position:absolute;left:230px;top:200px;font-size:15px;font-weight:700;color:var(--text);background:rgba(7,10,15,.8);padding:0 6px;border-radius:4px;pointer-events:none">{{ afterLabel }}</div>
<div style="position:absolute;left:600px;top:12px;font-size:14px;font-weight:700;letter-spacing:.12em;color:var(--dim)">O QUE MUDA</div>
@@FATOS@@
<div style="position:absolute;left:600px;top:438px;width:214px;font-size:15px;line-height:1.3;color:var(--muted)">O app guarda uma cópia antes de gravar.</div>
</section>

<aside aria-label="Etapas" style="position:absolute;left:32px;top:128px;width:370px;height:496px;box-sizing:border-box;padding:14px 16px;display:flex;flex-direction:column;gap:10px;background:var(--surface);border:1px solid var(--line);border-radius:14px">
<div style="font-size:14px;font-weight:700;letter-spacing:.12em;color:var(--dim)">COMO VAI SER</div>
@@PASSO1@@
@@PASSO2@@
<div style="display:flex;gap:10px;align-items:flex-start;font-size:15px;line-height:1.35;color:var(--muted)">@@I_WARN@@Não desligue o carro nem tire o cabo enquanto grava. A tela continua livre.</div>
<div style="margin-top:auto;display:flex;flex-direction:column;gap:8px">
<sc-if value="{{ notDone }}" hint-placeholder-val="{{ true }}">
<button type="button" onClick="{{ go }}" style="box-sizing:border-box;width:100%;height:58px;display:flex;align-items:center;justify-content:center;gap:10px;border:0;border-radius:12px;background:var(--accent-btn);color:#fff;font:700 20px Inter,Roboto,Arial,sans-serif;cursor:pointer">@@I_CK2@@Passar para o Mapa K</button>
<a href="07-Ajuste-global.dc.html" style="box-sizing:border-box;width:100%;height:58px;display:flex;align-items:center;justify-content:center;border:1px solid var(--line);border-radius:12px;background:var(--surface2);color:var(--text);font-size:20px;font-weight:600;text-decoration:none">Voltar à curva</a>
</sc-if>
<sc-if value="{{ done }}" hint-placeholder-val="{{ false }}">
<a href="06-Ajuste-local.dc.html" style="box-sizing:border-box;width:100%;height:58px;display:flex;align-items:center;justify-content:center;gap:10px;border-radius:12px;background:var(--accent-btn);color:#fff;font-size:20px;font-weight:700;text-decoration:none">Ver o Mapa K @@I_NEXT@@</a>
<button type="button" onClick="{{ undo }}" style="box-sizing:border-box;width:100%;height:58px;display:flex;align-items:center;justify-content:center;gap:10px;border:1px solid var(--line);border-radius:12px;background:var(--surface2);color:var(--text);font:600 20px Inter,Roboto,Arial,sans-serif;cursor:pointer">@@I_UN2@@Desfazer</button>
</sc-if>
</div>
</aside>

@@NAV@@

</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":1280,"height":720}}'>
class Component extends DCLogic {
  state = { done: false };
  renderVals() {
    const done = !!(this.state && this.state.done);
    return {
      done, notDone: !done,
      afterLabel: done ? 'agora: máx. 1,13' : 'depois: máx. 1,13',
      msgText: done
        ? 'Mapa K e Curva K mudaram. A curva agora chega a 1,13.'
        : 'O Mapa K assume parte da correção. No motor, quase nada muda.',
      msgBg: done ? '#0f2b23' : '#151e29', msgBd: done ? '#1d5a46' : '#263342',
      stepBg: done ? '#0f2b23' : '#151e29', stepBd: done ? '#1d5a46' : '#263342',
      stepFg: done ? '#38d39f' : '#9aa9bc', stepText: done ? 'Gravado e conferido' : 'Falta gravar',
      go: () => this.setState({ done: true }),
      undo: () => this.setState({ done: false })
    };
  }
}
</script>
</body>
</html>
'''
    def passo(n, titulo, sub):
        return ('<div style="box-sizing:border-box;padding:12px 14px;display:flex;align-items:flex-start;gap:12px;border-radius:12px;background:{{stepBg}};border:1px solid {{stepBd}}">'
                '<sc-if value="{{ notDone }}" hint-placeholder-val="{{ true }}"><span style="flex:none;width:36px;height:36px;box-sizing:border-box;display:flex;align-items:center;justify-content:center;border-radius:50%%;border:2px solid var(--muted);font-size:18px;font-weight:800;color:var(--muted)">%d</span></sc-if>'
                '<sc-if value="{{ done }}" hint-placeholder-val="{{ false }}"><span style="flex:none;width:36px;height:36px;display:flex;align-items:center;justify-content:center;border-radius:50%%;background:var(--ok);color:#04150e">%s</span></sc-if>'
                '<div style="display:flex;flex-direction:column;gap:2px"><b style="font-size:20px;font-weight:700">%s</b><span style="font-size:16px;line-height:1.3;color:var(--muted)">%s</span><span style="font-size:15px;font-weight:600;color:{{stepFg}}">{{ stepText }}</span></div></div>') % (n, ico(A.I_CHECK, 'currentColor', 3.5, 22), titulo, sub)
    rep = {'@@HELMET@@': sh['helmet'], '@@ROOT@@': sh['root'], '@@TOPBAR@@': sh['topbar'], '@@NAV@@': sh['nav'], '@@GRAFICO@@': grafico(), '@@FATOS@@': fatos,
           '@@PASSO1@@': passo(1, 'Gravar o Mapa K', '144 células · sobe até +13 %'), '@@PASSO2@@': passo(2, 'Gravar a Curva K', '30 pontos · o máximo cai de 1,27 para 1,13'),
           '@@CK@@': ico(A.I_CHECK, '#38d39f', 3, 20), '@@I_BACK@@': ico('<path d="M19 12H5M12 19l-7-7 7-7"/>', 'currentColor', 2.2, 26),
           '@@I_OK@@': ico(A.I_CHECK, '#38d39f', 3, 24), '@@I_INFO@@': ico(A.I_INFO, '#2f8cff', 2, 24), '@@I_WARN@@': '<span style="flex:none;display:flex;margin-top:1px">' + ico(A.I_WARN, '#f3ba59', 2, 20) + '</span>',
           '@@I_CK2@@': ico(A.I_CHECK, 'currentColor', 2.5, 22), '@@I_NEXT@@': ico('<path d="m9 18 6-6-6-6"/>', 'currentColor', 2.5, 20), '@@I_UN2@@': ico(A.I_UNDO, 'currentColor', 2, 22)}
    for k in ('@@PASSO1@@', '@@PASSO2@@', '@@FATOS@@', '@@GRAFICO@@'):
        html = html.replace(k, rep[k])
    for k, v in rep.items():
        html = html.replace(k, v)
    assert '@@' not in html, re.findall(r'@@\w+@@', html)
    open(os.path.join(PROJ, '08-Curva-para-Mapa.dc.html'), 'w', encoding='utf-8').write(html)
    print('escrito 08-Curva-para-Mapa.dc.html', len(html))

if __name__ == '__main__':
    main()
