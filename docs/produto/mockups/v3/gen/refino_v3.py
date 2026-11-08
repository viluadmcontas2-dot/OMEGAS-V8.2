"""Gera as telas do Refino no layout v3 (dono, 2026-10-27):
 05-Refino.dc.html       achei um problema → UM botão "Gravar correção" → gravado e medindo
 05b-Refino-tudo-certo   nada a corrigir; o app segue vigiando (faixas medidas e últimas correções)
 05c-Refino-piorou       Desfazer destacado, sem substituir Gravar correção
Sem trilha de passos, sem chips de estado, sem barra de ações: causa → efeito → um botão.
Uso: python3 -I refino_v3.py   (lê a casca — barra do topo e navegação — do 05 atual ou de gen/05-casca.html)
"""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fluxo_parts as F
import retopbar as RT
import autocal_v3 as A

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.join(HERE, '..', 'project')

def casca():
    t = open(os.path.join(PROJ, '05-Refino.dc.html'), encoding='utf-8').read()
    i = t.index('<div style="--bg:'); j = t.index('>', i) + 1
    a = t.index('<div style="position:absolute;left:0;top:0;width:1280px;height:56px')
    b = RT.balanced(t, a)
    n0 = t.index('<nav aria-label="Áreas do aplicativo"'); n1 = t.index('</nav>') + 6
    return dict(root=t[i:j], topbar=t[a:b].rstrip() + '\n', nav=t[n0:n1], helmet=t[t.index('<helmet>'):t.index('</helmet>') + 9])
SH = casca()

# ---------- geometria do gráfico (cartão 830×496; sem coluna de quadradinhos: o gráfico ocupa o cartão) ----------
X0, X1, Y0, Y1 = 64, 730, 52, 372
MS0, MS1, MAP0, MAP1 = 1.0, 11.5, 0.15, 1.10
def X(ms): return X0 + (ms - MS0) / (MS1 - MS0) * (X1 - X0)
def Y(m): return Y1 - (m - MAP0) / (MAP1 - MAP0) * (Y1 - Y0)
def f(v): return ('%.1f' % v).rstrip('0').rstrip('.')
def gas(ms): return 0.04 + 0.09 * ms
def meas(ms): return gas(ms) + 0.045 * (1 - ((ms - 6) / 5.0) ** 2)
def today(ms): return gas(ms) + 0.02 * (ms - 6) / 5

def base():
    o = []
    for lo, hi in ((MAP0, 0.43), (0.43, 0.65), (0.65, 0.87), (0.87, 1.08)):
        o.append('<rect x="%d" y="%s" width="%d" height="%s" fill="rgba(56,211,159,.05)"/>' % (X0, f(Y(hi)), X1 - X0, f(Y(lo) - Y(hi))))
    for m in (0.2, 0.4, 0.6, 0.8, 1.0):
        o.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#1b2631" stroke-width="1"/>' % (X0, f(Y(m)), X1, f(Y(m))))
    for ms in range(1, 12):
        o.append('<line x1="%s" y1="%d" x2="%s" y2="%d" stroke="#1b2631" stroke-width="1"/>' % (f(X(ms)), Y0, f(X(ms)), Y1))
    for m in (0.43, 0.65, 0.87, 1.08):
        o.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#cbd5e1" stroke-opacity=".28" stroke-width="1.5" stroke-dasharray="6 5"/>' % (X0, f(Y(m)), X1, f(Y(m))))
    for nome, sub, m in (('Zona 1', 'leve', 0.29), ('Zona 2', 'normal', 0.54), ('Zona 3', 'firme', 0.76), ('Zona 4', 'a fundo', 0.975)):
        o.append('<text x="%d" y="%s" fill="#dbe4ef" font-size="14" font-weight="700">%s</text>' % (X1 + 12, f(Y(m) - 2), nome))
        o.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14">%s</text>' % (X1 + 12, f(Y(m) + 15), sub))
    for m in (0.2, 0.4, 0.6, 0.8, 1.0):
        o.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14" text-anchor="end">%s</text>' % (X0 - 8, f(Y(m) + 5), ('%.2f' % m).replace('.', ',')))
    for ms in range(1, 12, 2):
        o.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">%d</text>' % (f(X(ms)), Y1 + 20, ms))
    o.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">Injeção na gasolina (ms)</text>' % (f((X0 + X1) / 2), Y1 + 40))
    return '\n'.join(o)

def dots():
    ms_list = [1.3 + 0.5 * i for i in range(21) if 1.3 + 0.5 * i <= 11.4]
    mm = [2.2, 2.8, 3.4, 3.9, 4.4, 5.0, 5.6, 6.2, 6.8, 7.4, 8.0, 8.6, 9.3, 10.0, 10.8]
    d = ['<polyline points="%s" fill="none" stroke="#ff7a7a" stroke-width="3" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(gas(m)))) for m in ms_list),
         '<polyline points="%s" fill="none" stroke="#3f8f5c" stroke-width="2" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(today(m)))) for m in ms_list),
         '<polyline points="%s" fill="none" stroke="#5fe38a" stroke-width="3" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(meas(m)))) for m in mm)]
    for m in mm:
        d.append('<rect x="%s" y="%s" width="11" height="11" rx="2" fill="#5fe38a" stroke="#070a0f" stroke-width="1.5"/>' % (f(X(m) - 5.5), f(Y(meas(m)) - 5.5)))
    for m in (2.0, 3.0, 4.2, 5.3, 6.6, 7.7, 9.0, 10.2):
        d.append('<circle cx="%s" cy="%s" r="5.5" fill="#ff7a7a" stroke="#070a0f" stroke-width="1.5"/>' % (f(X(m)), f(Y(gas(m)))))
    return '\n'.join(d)

def shifted(ms):
    d = 0.12 * max(0.0, min(1.0, (ms - 1.9) / 0.4, (3.9 - ms) / 0.4))
    return X(ms + d), Y(meas(ms))
seg = [1.5 + 0.1 * k for k in range(0, 33)]
AFTER = ' '.join('%s,%s' % (f(shifted(m)[0]), f(shifted(m)[1])) for m in seg)
BEFORE = ' '.join('%s,%s' % (f(X(m)), f(Y(meas(m)))) for m in seg)
RX0, RX1, RY0, RY1 = X(1.9), X(3.9), Y(0.50), Y(0.19)

def mk(ms, kind):
    cy = Y(meas(ms)) - (16 if kind == 'x' else 0) + (18 if kind == 'd' else 0)
    return dict(l=round(X(ms) - 10), t=round(cy - 10), kind=kind)
M_OK = [mk(2.8, 'x'), mk(3.1, 'x'), mk(2.5, 'd'), mk(2.7, 'd'), mk(3.3, 'd'), mk(3.5, 'd')]
M_PIOR = M_OK + [mk(2.4, 'x'), mk(3.6, 'x'), mk(2.9, 'd')]

KT, KB = 430, 486
def KY(k): return KB - (k - 0.4) / (2.0 - 0.4) * (KB - KT)
kv = [1.02, 1.03, 1.04, 1.05, 1.055, 1.06, 1.065, 1.07, 1.07, 1.065, 1.06, 1.05, 1.04, 1.03, 1.02, 1.01, 1.0, 1.0, 0.995, 0.99, 0.99, 0.985, 0.985, 0.98, 0.98, 0.98, 0.975, 0.975, 0.97, 0.97]
kp = [k + (0.12 if 1 <= i <= 6 else 0.05 if 7 <= i <= 12 else -0.01) for i, k in enumerate(kv)]
def kx(i): return X0 + 8 + i * (X1 - X0 - 16) / 29
def kpoly(vals): return ' '.join('%s,%s' % (f(kx(i)), f(KY(k) - KT)) for i, k in enumerate(vals))
KSTRIP = ('<rect x="%d" y="0" width="%d" height="%d" rx="4" fill="#0b1017"/>' % (X0, X1 - X0, KB - KT)
          + '<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#ff6b6b" stroke-opacity=".8" stroke-width="1.5" stroke-dasharray="6 5"/>' % (X0, f(KY(1.8) - KT), X1, f(KY(1.8) - KT))
          + '<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#cbd5e1" stroke-opacity=".35" stroke-width="1" stroke-dasharray="2 4"/>' % (X0, f(KY(1.0) - KT), X1, f(KY(1.0) - KT))
          + '<polyline points="%s" fill="none" stroke="#2f8cff" stroke-opacity=".5" stroke-width="2.5" stroke-linejoin="round"/>' % kpoly(kv)
          + '<text x="%d" y="%s" fill="#ff9b9b" font-size="14" font-weight="600" text-anchor="end">limite 1,8</text>' % (X1 - 6, f(KY(1.8) - KT + 17)))
KNEW = '<polyline points="%s" fill="none" stroke="#2f8cff" stroke-width="3.5" stroke-linejoin="round"/>' % kpoly(kp)
KATUAL = '<polyline points="%s" fill="none" stroke="#2f8cff" stroke-width="3.5" stroke-linejoin="round"/>' % kpoly(kv)
KRUIM = ''.join('<circle cx="%s" cy="%s" r="6" fill="#ff6b6b" stroke="#070a0f" stroke-width="1.5"/>' % (f(kx(i)), f(KY(kp[i]) - KT)) for i in (3, 5))

ico = A.ico
CHECK = A.I_CHECK; INFO = A.I_INFO; WARN = A.I_WARN; UNDO = A.I_UNDO
HELP = '<circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3M12 17h.01"/>'
UP = '<path d="M12 19V5M5 12l7-7 7 7"/>'
EQ = '<path d="M5 9h14M5 15h14"/>'

SELO = r'''<sc-if value="{{ seloOk }}" hint-placeholder-val="{{ false }}"><div role="status" style="position:absolute;right:16px;top:10px;height:36px;box-sizing:border-box;padding:0 12px;display:flex;align-items:center;gap:7px;border-radius:10px;background:var(--ok-soft);border:1px solid #1d5a46;color:var(--ok);font-size:15px;font-weight:600;white-space:nowrap">@@I_CHECK_SM@@{{ seloTexto }}</div></sc-if>'''

def cabecalho(titulo_chip_js=True):
    return r'''<div style="position:absolute;left:32px;top:64px;width:1216px;height:56px;display:flex;align-items:center;gap:14px">
<h1 style="margin:0;flex:none;font-size:30px;font-weight:700;letter-spacing:-.01em">Refino</h1>
<div style="flex:none;box-sizing:border-box;height:38px;padding:0 14px;display:flex;align-items:center;gap:8px;border-radius:19px;background:{{chipBg}};border:1px solid {{chipBd}};font-size:17px;font-weight:700"><span style="width:10px;height:10px;border-radius:50%;background:{{chipDot}}"></span>{{ chipText }}</div>
<div style="flex:1;min-width:0;box-sizing:border-box;height:48px;padding:0 16px;display:flex;align-items:center;gap:10px;border-radius:12px;background:{{msgBg}};border:1px solid {{msgBd}};font-size:17px;font-weight:600;white-space:nowrap"><span style="flex:none;display:flex;color:{{msgFg}}"><sc-if value="{{ msgI }}" hint-placeholder-val="{{ true }}">@@I_MSG_INFO@@</sc-if><sc-if value="{{ msgOk }}" hint-placeholder-val="{{ false }}">@@I_MSG_OK@@</sc-if><sc-if value="{{ msgBad }}" hint-placeholder-val="{{ false }}">@@I_MSG_BAD@@</sc-if></span><span>{{ msgText }}</span></div>
<button type="button" onClick="{{ alternarAjuda }}" aria-expanded="{{ ariaAjuda }}" aria-label="Ajuda do Refino" style="flex:none;width:56px;height:56px;display:flex;align-items:center;justify-content:center;border-radius:12px;border:1px solid {{ajudaBd}};background:{{ajudaBg}};color:var(--text);cursor:pointer">@@I_HELP@@</button>
</div>
<sc-if value="{{ ajuda }}" hint-placeholder-val="{{ false }}">
<div role="dialog" aria-label="Ajuda do Refino" style="position:absolute;right:32px;top:124px;z-index:5;width:420px;box-sizing:border-box;padding:16px 18px;display:flex;flex-direction:column;gap:12px;background:var(--surface);border:1px solid #2a4a7e;border-radius:14px;box-shadow:0 14px 32px #000a">
<div><div style="font-size:15px;font-weight:700;color:var(--accent)">O que é</div><div style="font-size:18px;line-height:1.35">O app confere onde o carro engasga e monta a correção.</div></div>
<div><div style="font-size:15px;font-weight:700;color:var(--accent)">O que fazer</div><div style="font-size:18px;line-height:1.35">Dirija normalmente. Quando houver correção pronta, toque em Gravar correção.</div></div>
<div><div style="font-size:15px;font-weight:700;color:var(--accent)">O que acontece</div><div style="font-size:18px;line-height:1.35">A central grava e confere. O app mede se melhorou e você pode desfazer.</div></div>
</div>
</sc-if>'''

def pagina(titulo, painel, cartao, script, extra=''):
    script = script.replace('class Component extends DCLogic {', 'class Component extends DCLogic {' + F.LOGICA)
    h = r'''<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>@@TITULO@@</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
@@HELMET@@
@@ROOT@@

@@TOPBAR@@
@@SELO@@
@@CABECALHO@@
<aside aria-label="O que o Refino achou" style="position:absolute;left:32px;top:128px;width:370px;height:496px;box-sizing:border-box;padding:14px 16px;display:flex;flex-direction:column;background:var(--surface);border:1px solid var(--line);border-radius:14px;overflow:hidden">
@@PAINEL@@
</aside>

<section aria-label="Onde o carro engasga" style="position:absolute;left:418px;top:128px;width:830px;height:496px;background:var(--surface);border:1px solid var(--line);border-radius:14px;overflow:hidden">
@@CARTAO@@
</section>

@@NAV@@

</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":1280,"height":720}}'>
@@SCRIPT@@
</script>
</body>
</html>
'''
    r = {'@@TITULO@@': titulo, '@@HELMET@@': SH['helmet'], '@@ROOT@@': SH['root'], '@@TOPBAR@@': SH['topbar'], '@@NAV@@': SH['nav'],
         '@@SELO@@': SELO, '@@CABECALHO@@': cabecalho(), '@@PAINEL@@': painel, '@@CARTAO@@': cartao, '@@SCRIPT@@': script}
    for k in ('@@SELO@@', '@@CABECALHO@@', '@@PAINEL@@', '@@CARTAO@@', '@@SCRIPT@@'):
        h = h.replace(k, r[k])
    h = h.replace('@@I_CHECK_SM@@', ico(CHECK, '#38d39f', 3, 20)).replace('@@I_MSG_INFO@@', ico(INFO, 'currentColor', 2, 24))
    h = h.replace('@@I_MSG_OK@@', ico(CHECK, 'currentColor', 3, 24)).replace('@@I_MSG_BAD@@', ico(WARN, 'currentColor', 2.2, 24)).replace('@@I_HELP@@', ico(HELP, 'currentColor', 2, 26))
    for k, v in r.items():
        h = h.replace(k, v)
    assert '@@' not in h, re.findall(r'@@\w+@@', h)
    return h

LEG_MARCAS = r'''<div style="position:absolute;left:16px;top:10px;height:34px;display:flex;align-items:center;gap:18px;font-size:15px;color:var(--muted);white-space:nowrap">
<span style="display:flex;align-items:center;gap:7px"><svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M3 3l10 10M13 3 3 13" stroke="#ff6b6b" stroke-width="3" stroke-linecap="round"/></svg>Apagou</span>
<span style="display:flex;align-items:center;gap:7px"><svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M8 1.5 14.5 8 8 14.5 1.5 8z" fill="#0b1017" stroke="#f3ba59" stroke-width="2.5"/></svg>Quase apagou</span>
<span style="display:flex;align-items:center;gap:7px"><span style="width:18px;height:12px;box-sizing:border-box;border-radius:3px;border:2px dashed {{boxCor}}"></span>{{ legBox }}</span>
</div>'''
LEG_GAS = r'''<div style="position:absolute;left:16px;top:10px;height:34px;display:flex;align-items:center;gap:18px;font-size:15px;color:var(--muted);white-space:nowrap">
<span style="display:flex;align-items:center;gap:7px"><span style="width:12px;height:12px;border-radius:50%;background:var(--petrol)"></span>Gasolina</span>
<span style="display:flex;align-items:center;gap:7px"><span style="width:12px;height:12px;border-radius:3px;background:var(--gas)"></span>GNV</span>
</div>'''

def svg_plot(op, extra_overlay=''):
    return r'''<svg width="830" height="496" viewBox="0 0 830 496" style="position:absolute;left:0;top:0;pointer-events:none" role="img" aria-label="Gráfico: pressão da admissão contra injeção na gasolina, em quatro zonas de uso">
%s
<g opacity="%s">
%s
</g>
</svg>''' % (base(), op, dots())

def ksvg(nova):
    return r'''<div style="position:absolute;left:6px;top:430px;width:58px;height:56px;display:flex;align-items:center;font-size:14px;font-weight:700;white-space:nowrap;color:var(--accent)">Curva K</div>
<svg width="830" height="496" viewBox="0 0 830 496" style="position:absolute;left:0;top:0;pointer-events:none" aria-hidden="true"><g transform="translate(0,%d)">%s%s</g></svg>''' % (KT, KSTRIP, nova)

def marcas_html():
    return r'''<sc-for list="{{ marcas }}" as="m" hint-placeholder-count="6">
<span style="position:absolute;left:{{m.l}}px;top:{{m.t}}px;width:20px;height:20px;display:block;opacity:{{m.op}};pointer-events:none">
<sc-if value="{{ m.x }}" hint-placeholder-val="{{ false }}"><svg width="20" height="20" viewBox="0 0 16 16" aria-hidden="true"><path d="M3 3l10 10M13 3 3 13" stroke="#ff6b6b" stroke-width="3.2" stroke-linecap="round"/></svg></sc-if>
<sc-if value="{{ m.d }}" hint-placeholder-val="{{ false }}"><svg width="20" height="20" viewBox="0 0 16 16" aria-hidden="true"><path d="M8 1.5 14.5 8 8 14.5 1.5 8z" fill="#0b1017" stroke="#f3ba59" stroke-width="2.5"/></svg></sc-if>
</span>
</sc-for>'''

def caixa_html():
    return '''<div style="position:absolute;left:%spx;top:%spx;width:%spx;height:%spx;box-sizing:border-box;border-radius:8px;border:2px {{boxEstilo}} {{boxCor}};background:{{boxFundo}}"></div>
<div style="position:absolute;left:%spx;top:%spx;height:28px;box-sizing:border-box;padding:0 10px;display:flex;align-items:center;border-radius:8px;background:{{boxCor}};color:#14110a;font-size:15px;font-weight:800;white-space:nowrap">{{ boxRotulo }}</div>''' % (
        f(RX0), f(RY0), f(RX1 - RX0), f(RY1 - RY0), f(RX0), f(RY0 - 32))

def chip_html():
    return r'''<div style="position:absolute;left:78px;top:60px;height:40px;box-sizing:border-box;padding:0 14px;display:flex;align-items:center;gap:10px;border-radius:10px;background:{{chipBg2}};border:1px solid {{chipBd2}};font-size:18px;font-weight:700;color:{{chipFg2}};white-space:nowrap"><sc-if value="{{ chipOk }}" hint-placeholder-val="{{ false }}">@@I_CK@@</sc-if><sc-if value="{{ chipBad }}" hint-placeholder-val="{{ false }}">@@I_WN@@</sc-if>{{ chipTexto }}</div>'''.replace('@@I_CK@@', ico(CHECK, '#38d39f', 3, 22)).replace('@@I_WN@@', ico(WARN, '#ff6b6b', 2.2, 22))

EYE = 'font-size:14px;font-weight:700;letter-spacing:.12em;color:var(--dim)'
BTN_P = 'flex:none;box-sizing:border-box;width:100%;height:58px;display:flex;align-items:center;justify-content:center;gap:10px;border:0;border-radius:12px;background:#1f6fd8;color:#fff;font:700 20px Inter,Roboto,Arial,sans-serif;cursor:pointer'
BTN_S = 'flex:none;box-sizing:border-box;width:100%;height:58px;display:flex;align-items:center;justify-content:center;gap:10px;border:1px solid #263342;border-radius:12px;background:#151e29;color:#f4f7fb;font:700 20px Inter,Roboto,Arial,sans-serif;cursor:pointer'

def linha(icone_svg, titulo, sub):
    return '<div style="display:flex;gap:12px;align-items:flex-start"><span style="flex:none;width:36px;height:36px;display:flex;align-items:center;justify-content:center;border-radius:10px;background:#14284a;border:1px solid #2a4a7e">%s</span><span style="display:flex;flex-direction:column;min-width:0"><b style="font-size:18px;font-weight:700;line-height:1.25">%s</b><span style="font-size:15px;color:var(--muted)">%s</span></span></div>' % (icone_svg, titulo, sub)

COMUM_JS = r'''
    const ok = '#38d39f', warn = '#f3ba59', danger = '#ff6b6b', info = '#2f8cff';
    const tom = { i: ['#151e29', '#263342', info], ok: ['#0f2b23', '#1d5a46', ok], bad: ['#2e1618', '#7a3036', danger], warn: ['#2d240f', '#6b5420', warn] };
    const ajudaPar = { alternarAjuda: () => this.setState({ ajuda: !st.ajuda }), ajuda: !!st.ajuda, ariaAjuda: st.ajuda ? 'true' : 'false', ajudaBg: st.ajuda ? '#14284a' : '#151e29', ajudaBd: st.ajuda ? '#2a4a7e' : '#263342' };
'''

def cartao_05():
    return (LEG_MARCAS + r'''
<sc-if value="{{ temAD }}" hint-placeholder-val="{{ true }}"><div role="group" aria-label="Ver o gráfico" style="position:absolute;right:16px;top:4px;z-index:2;display:flex;gap:6px">
<button type="button" onClick="{{ verAntes }}" aria-pressed="{{ ariaAntes }}" style="box-sizing:border-box;height:44px;padding:0 18px;border-radius:10px;border:1px solid {{adBdA}};background:{{adBgA}};color:{{adFgA}};font:700 16px Inter,Roboto,Arial,sans-serif;cursor:pointer">Antes</button>
<button type="button" onClick="{{ verDepois }}" aria-pressed="{{ ariaDepois }}" style="box-sizing:border-box;height:44px;padding:0 18px;border-radius:10px;border:1px solid {{adBdD}};background:{{adBgD}};color:{{adFgD}};font:700 16px Inter,Roboto,Arial,sans-serif;cursor:pointer">Depois</button>
</div></sc-if>
''' + svg_plot('{{dotsOp}}') + r'''
<sc-if value="{{ showAntes }}" hint-placeholder-val="{{ false }}"><svg width="830" height="496" viewBox="0 0 830 496" style="position:absolute;left:0;top:0;pointer-events:none" aria-hidden="true"><polyline points="''' + BEFORE + r'''" fill="none" stroke="#9fe6b8" stroke-width="4" stroke-linejoin="round" stroke-linecap="round"/></svg></sc-if>
<sc-if value="{{ showDepois }}" hint-placeholder-val="{{ false }}"><svg width="830" height="496" viewBox="0 0 830 496" style="position:absolute;left:0;top:0;pointer-events:none" aria-hidden="true"><polyline points="''' + AFTER + r'''" fill="none" stroke="#5fe38a" stroke-width="4.5" stroke-linejoin="round" stroke-linecap="round"/></svg></sc-if>
''' + caixa_html() + marcas_html() + chip_html() + r'''
<div style="position:absolute;left:6px;top:430px;width:58px;height:56px;display:flex;align-items:center;font-size:14px;font-weight:700;white-space:nowrap;color:var(--accent)">Curva K</div>
<svg width="830" height="496" viewBox="0 0 830 496" style="position:absolute;left:0;top:0;pointer-events:none" aria-hidden="true"><g transform="translate(0,%d)">%s<sc-if value="{{ temKNova }}" hint-placeholder-val="{{ true }}">%s</sc-if></g></svg>
<div style="position:absolute;right:16px;bottom:8px;font-size:14px;color:var(--muted)">{{ kLegenda }}</div>''' % (KT, KSTRIP, KNEW))

# ----------------------------------------------------------------- 05: achei um problema → gravado
def tela_05():
    painel = F.painel('<div style="'+EYE+'">O QUE ACONTECE</div><div style="margin-top:6px;font-size:36px;line-height:1.1;font-weight:800;color:var(--warn)">6 engasgos</div><div style="margin-top:6px;font-size:16px;line-height:1.3">ao acelerar leve (zona 1). Em 2, o motor apagou.</div><div style="margin-top:10px;font-size:16px;line-height:1.3;color:var(--muted)">{{ previa }}</div>')
    cartao = cartao_05()
    script = r'''class Component extends DCLogic {
  state = { fase: 'proposta', ver: 'depois', ajuda: false, aviso: '' };
  renderVals() {
    const st = Object.assign({ fase: 'proposta', ver: 'depois', ajuda: false, aviso: '' }, this.state || {});
''' + COMUM_JS + r'''
    const g = st.fase === 'gravado';
    const verDepois = st.ver !== 'antes';
    const OK = ''' + json.dumps(M_OK) + r''';
    const marcas = OK.map(m => ({ l: m.l, t: m.t, x: m.kind === 'x', d: m.kind === 'd', op: g ? 0.35 : 1 }));
    const msg = g ? ['Pronto. O app mede se melhorou e avisa aqui.', 'ok'] : st.aviso ? [st.aviso, 'ok'] : ['O carro engasga ao acelerar leve no GNV. Dá para corrigir agora.', 'i'];
    const t = tom[msg[1]];
    const cx = g ? ['Engasgos desde que gravou: 0', ok, '#0f2b23', '#1d5a46', true] : ['+5 % de gás nas 6 células', warn, '#2d240f', '#6b5622', false];
    return Object.assign({
      proposta: !g, gravado: g, seloOk: g, seloTexto: 'Gravado e conferido',
      chipText: g ? 'Medindo' : '2 correções prontas', chipBg: g ? '#14284a' : '#2d240f', chipBd: g ? '#2a4a7e' : '#6b5420', chipDot: g ? info : warn,
      msgText: msg[0], msgBg: t[0], msgBd: t[1], msgFg: t[2], msgI: msg[1] === 'i', msgOk: msg[1] === 'ok', msgBad: false,
      gravar: () => this.setState({ fase: 'gravado', aviso: '', ajuda: false }),
      desfazer: () => this.setState({ fase: 'proposta', ver: 'depois', aviso: 'Desfeito: os valores anteriores voltaram para a central.', ajuda: false }),
      legBox: g ? 'Região reforçada' : 'Onde vai o reforço',
      boxCor: g ? '#38d39f' : '#f3ba59', boxEstilo: g ? 'solid' : 'dashed', boxFundo: g ? 'rgba(56,211,159,.10)' : 'rgba(243,186,89,.10)', boxRotulo: g ? 'Reforçado: +5 % de gás' : 'Aqui o carro engasga',
      temAD: !g, ariaAntes: !verDepois ? 'true' : 'false', ariaDepois: verDepois ? 'true' : 'false',
      adBgA: !verDepois ? '#14284a' : '#151e29', adBdA: !verDepois ? '#2a4a7e' : '#263342', adFgA: !verDepois ? '#f4f7fb' : '#9aa9bc',
      adBgD: verDepois ? '#14284a' : '#151e29', adBdD: verDepois ? '#2a4a7e' : '#263342', adFgD: verDepois ? '#f4f7fb' : '#9aa9bc',
      verAntes: () => this.setState({ ver: 'antes' }), verDepois: () => this.setState({ ver: 'depois' }),
      dotsOp: 0.25, showAntes: !g && !verDepois, showDepois: !g && verDepois, marcas,
      chipTexto: cx[0], chipFg2: cx[1], chipBg2: cx[2], chipBd2: cx[3], chipOk: cx[4], chipBad: false,
      temKNova: true, kLegenda: 'claro = hoje · forte = depois'
    }, ajudaPar, this.fluxoVals('proposta'));
  }
}'''
    return pagina('Refino', painel, cartao, script)

# ----------------------------------------------------------------- 05b: tudo certo
def tela_05b():
    chips = ''.join('<div style="flex:1;min-width:0;height:48px;box-sizing:border-box;border-radius:8px;display:flex;flex-direction:column;align-items:center;justify-content:center;line-height:1.1;background:%s;border:1px solid %s;color:%s"><b style="font-size:14px;font-weight:700;white-space:nowrap">%s</b><span style="font-size:14px;font-weight:600">%s</span></div>' % (
        ('#0f2b23', '#1d5a46', '#38d39f', n, 'ok') if okk else ('#151e29', '#3a4a5c', '#9aa9bc', n, 'falta')) for n, okk in (('3–4,5', True), ('4,5–6', True), ('6–7,5', True), ('7,5–9', False), ('9–12', False)))
    def hist(quando, o_que, chip_txt, bg, bd, fg, href=None):
        corpo = '<span style="display:flex;flex-direction:column;min-width:0"><b style="font-size:16px;font-weight:700">%s</b><span style="font-size:14px;color:var(--muted)">%s</span></span><span style="flex:none;box-sizing:border-box;height:30px;padding:0 10px;display:flex;align-items:center;border-radius:8px;background:%s;border:1px solid %s;color:%s;font-size:15px;font-weight:700;white-space:nowrap">%s</span>' % (o_que, quando, bg, bd, fg, chip_txt)
        est = 'box-sizing:border-box;min-height:56px;padding:6px 10px;display:flex;align-items:center;justify-content:space-between;gap:8px;border-radius:10px;background:#151e29;border:1px solid #263342;color:var(--text);text-decoration:none'
        return ('<a href="%s" style="%s">%s</a>' % (href, est, corpo)) if href else ('<div style="%s">%s</div>' % (est, corpo))
    painel = F.painel('<div style="'+EYE+'">O QUE ACONTECE</div><div style="margin-top:6px;font-size:36px;line-height:1.1;font-weight:800;color:var(--ok)">0 engasgos</div><div style="margin-top:6px;font-size:16px;line-height:1.3">em 1.420 leituras. 3 de 5 faixas medidas; acelere de 7,5 a 12 ms para completar.</div><div style="margin-top:10px;font-size:16px;line-height:1.3;color:var(--muted)">{{ previa }}</div>')
    cartao = (LEG_GAS + svg_plot('1') + r'''
<div style="position:absolute;left:78px;top:60px;height:40px;box-sizing:border-box;padding:0 14px;display:flex;align-items:center;gap:10px;border-radius:10px;background:#0f2b23;border:1px solid #1d5a46;font-size:18px;font-weight:700;color:#38d39f;white-space:nowrap">''' + ico(CHECK, '#38d39f', 3, 22) + ' Tudo liso: 0 engasgos</div>' + ksvg(KATUAL))
    script = r'''class Component extends DCLogic {
  state = { ajuda: false };
  renderVals() {
    const st = Object.assign({ ajuda: false }, this.state || {});
''' + COMUM_JS + r'''
    return Object.assign({
      seloOk: false, seloTexto: '', chipText: 'Tudo certo', chipBg: '#0f2b23', chipBd: '#1d5a46', chipDot: ok,
      msgText: 'Nenhum engasgo até agora. O app segue vigiando.', msgBg: tom.ok[0], msgBd: tom.ok[1], msgFg: tom.ok[2], msgI: false, msgOk: true, msgBad: false
    }, ajudaPar, this.fluxoVals('certo'));
  }
}'''
    return pagina('Refino · tudo certo', painel, cartao, script)

# ----------------------------------------------------------------- 05c: piorou
def tela_05c():
    painel = F.painel('<div style="'+EYE+'">O QUE ACONTECE</div><div style="margin-top:6px;font-size:36px;line-height:1.1;font-weight:800;color:var(--danger)">2 pontos pioraram</div><div style="margin-top:6px;font-size:16px;line-height:1.3">A última correção afastou o gás da gasolina. Desfazer restaura os valores anteriores.</div><div style="margin-top:10px;font-size:16px;line-height:1.3;color:var(--muted)">{{ previa }}</div>')
    cartao = (LEG_MARCAS + svg_plot('{{dotsOp}}') + caixa_html() + marcas_html() + chip_html() + r'''
<div style="position:absolute;left:6px;top:430px;width:58px;height:56px;display:flex;align-items:center;font-size:14px;font-weight:700;white-space:nowrap;color:var(--accent)">Curva K</div>
<svg width="830" height="496" viewBox="0 0 830 496" style="position:absolute;left:0;top:0;pointer-events:none" aria-hidden="true"><g transform="translate(0,%d)">%s%s<sc-if value="{{ piorou }}" hint-placeholder-val="{{ true }}">%s</sc-if></g></svg>
<div style="position:absolute;right:16px;bottom:8px;font-size:14px;color:var(--muted)">{{ kLegenda }}</div>''' % (KT, KSTRIP, KNEW, KRUIM))
    script = r'''class Component extends DCLogic {
  state = { fase: 'piorou', ajuda: false };
  renderVals() {
    const st = Object.assign({ fase: 'piorou', ajuda: false }, this.state || {});
''' + COMUM_JS + r'''
    const p = st.fase === 'piorou';
    const PIOR = ''' + json.dumps(M_PIOR) + r''';
    const marcas = (p ? PIOR : PIOR.slice(0, 6)).map(m => ({ l: m.l, t: m.t, x: m.kind === 'x', d: m.kind === 'd', op: p ? 1 : 0.35 }));
    return Object.assign({
      piorou: p, restaurado: !p, seloOk: !p, seloTexto: 'Restaurado e conferido',
      chipText: p ? 'Piorou' : 'Restaurado', chipBg: p ? '#2e1618' : '#0f2b23', chipBd: p ? '#7a3036' : '#1d5a46', chipDot: p ? danger : ok,
      msgText: p ? 'A última correção piorou 2 pontos. Dá para restaurar.' : 'Restaurado: os 2 pontos voltaram ao valor anterior.',
      msgBg: p ? tom.bad[0] : tom.ok[0], msgBd: p ? tom.bad[1] : tom.ok[1], msgFg: p ? danger : ok, msgI: false, msgOk: !p, msgBad: p,
      restaurar: () => this.setState({ fase: 'restaurado', ajuda: false }),
      dotsOp: 0.25, legBox: 'Região', boxCor: p ? '#ff6b6b' : '#38d39f', boxEstilo: p ? 'dashed' : 'solid', boxFundo: p ? 'rgba(255,107,107,.10)' : 'rgba(56,211,159,.10)', boxRotulo: p ? 'Piorou aqui' : 'Reforço mantido',
      marcas, chipTexto: p ? '9 engasgos depois da correção' : 'Engasgos depois: 9 → 6', chipFg2: p ? danger : ok, chipBg2: p ? '#2e1618' : '#0f2b23', chipBd2: p ? '#7a3036' : '#1d5a46', chipOk: !p, chipBad: p,
      kLegenda: p ? 'pontos em vermelho = pioraram' : 'curva restaurada'
    }, ajudaPar, this.fluxoVals('piorou'));
  }
}'''
    return pagina('Refino · piorou', painel, cartao, script)


# ----------------------------------------------------------------- 05d: a central ainda aprende (Refinar por minha conta)
def tela_05d():
    painel = F.painel('<div style="'+EYE+'">O QUE ACONTECE</div><div style="margin-top:6px;font-size:36px;line-height:1.1;font-weight:800;color:var(--warn)">6 engasgos</div><div style="margin-top:6px;font-size:16px;line-height:1.3">A central ainda aprende. Gravar agora pode ser desfeito por ela.</div><div style="margin-top:10px;font-size:16px;line-height:1.3;color:var(--muted)">{{ previa }}</div>')
    cartao = cartao_05()
    script = r"""class Component extends DCLogic {
  state = { fase: 'espera', ajuda: false };
  renderVals() {
    const st = Object.assign({ fase: 'espera', ajuda: false }, this.state || {});
""" + COMUM_JS + r"""
    const f = st.fase, g = f === 'gravado';
    const OK = """ + json.dumps(M_OK) + r""";
    const marcas = OK.map(m => ({ l: m.l, t: m.t, x: m.kind === 'x', d: m.kind === 'd', op: g ? 0.35 : 1 }));
    const msg = f === 'espera' ? ['A central ainda aprende (2 de 3). Gravar espera ela terminar.', 'warn'] : f === 'conta' ? ['Você assumiu o refino. A central ainda aprende; reaprenda o GNV depois.', 'warn'] : ['Pronto. A central vai reler o GNV.', 'ok'];
    const t = tom[msg[1]];
    const cx = g ? ['Engasgos desde que gravou: 0', ok, '#0f2b23', '#1d5a46', true] : ['+5 % de gás nas 6 células', warn, '#2d240f', '#6b5622', false];
    return Object.assign({
      naoGravado: !g, gravado: g, espera: f === 'espera', conta: f === 'conta', seloOk: g, seloTexto: 'Gravado e conferido',
      chipText: f === 'espera' ? 'Esperando a central' : f === 'conta' ? 'Por conta própria' : 'Gravado', chipBg: g ? '#14284a' : '#2d240f', chipBd: g ? '#2a4a7e' : '#6b5420', chipDot: g ? info : warn,
      msgText: msg[0], msgBg: t[0], msgBd: t[1], msgFg: t[2], msgI: false, msgOk: msg[1] === 'ok', msgBad: msg[1] === 'warn',
      assumir: () => this.setState({ fase: 'conta', ajuda: false }),
      gravar: () => this.setState({ fase: 'gravado', ajuda: false }),
      desfazer: () => this.setState({ fase: 'conta', ajuda: false }),
      legBox: g ? 'Região reforçada' : 'Onde vai o reforço',
      boxCor: g ? '#38d39f' : '#f3ba59', boxEstilo: g ? 'solid' : 'dashed', boxFundo: g ? 'rgba(56,211,159,.10)' : 'rgba(243,186,89,.10)', boxRotulo: g ? 'Reforçado: +5 % de gás' : 'Aqui o carro engasga',
      temAD: false, ariaAntes: 'false', ariaDepois: 'true', adBgA: '#151e29', adBdA: '#263342', adFgA: '#9aa9bc', adBgD: '#14284a', adBdD: '#2a4a7e', adFgD: '#f4f7fb',
      verAntes: () => {}, verDepois: () => {},
      dotsOp: 0.25, showAntes: false, showDepois: !g, marcas,
      chipTexto: cx[0], chipFg2: cx[1], chipBg2: cx[2], chipBd2: cx[3], chipOk: cx[4], chipBad: false,
      temKNova: true, kLegenda: 'claro = hoje · forte = depois'
    }, ajudaPar, this.fluxoVals('espera'));
  }
}"""
    return pagina('Refino · central ainda aprende', painel, cartao, script)

if __name__ == '__main__':
    for nome, fn in (('05-Refino.dc.html', tela_05), ('05b-Refino-tudo-certo.dc.html', tela_05b), ('05c-Refino-piorou.dc.html', tela_05c), ('05d-Refino-central-aprendendo.dc.html', tela_05d)):
        h = fn()
        open(os.path.join(PROJ, nome), 'w', encoding='utf-8').write(h)
        print('escrito', nome, len(h))
