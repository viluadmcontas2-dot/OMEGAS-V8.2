"""Gera 02-AutoCal-esperando.dc.html e 03-AutoCal.dc.html no layout v3 (dono, 2026-10-27):
 - sem barra de ações embaixo; painel e gráfico de 496 px de altura; gráfico ocupa o cartão inteiro;
 - tocar na célula Zona × Combustível APAGA os pontos (com a frase "toque para apagar" na vertical);
 - interruptor do aprendizado no topo do painel e menu "Mais" no cabeçalho.
Uso: python3 -I autocal_v3.py     (aproveita a barra do topo e a navegação que já estão nos arquivos)
"""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import retopbar as RT

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.join(HERE, '..', 'project')

def shell(nome):
    t = open(os.path.join(PROJ, nome), encoding='utf-8').read()
    i = t.index('<div style="--bg:'); j = t.index('>', i) + 1
    a = t.index('<div style="position:absolute;left:0;top:0;width:1280px;height:56px')
    b = RT.balanced(t, a)
    n0 = t.index('<nav aria-label="Áreas do aplicativo"'); n1 = t.index('</nav>') + 6
    return dict(root=t[i:j], topbar=t[a:b].rstrip() + '\n', nav=t[n0:n1], helmet=t[t.index('<helmet>'):t.index('</helmet>') + 9])

# ---------- geometria (coordenadas do cartão 830×496) ----------
X0, X1, Y0, Y1 = 64, 692, 52, 372
MS0, MS1, MAP0, MAP1 = 1.0, 11.5, 0.15, 1.10
def X(ms): return X0 + (ms - MS0) / (MS1 - MS0) * (X1 - X0)
def Y(m): return Y1 - (m - MAP0) / (MAP1 - MAP0) * (Y1 - Y0)
def f(v): return ('%.1f' % v).rstrip('0').rstrip('.')
def gas(ms): return 0.04 + 0.09 * ms
def gnv(ms): return gas(ms) + 0.004 + 0.004 * max(0, ms - 5)
def jit(i, a=0.011): return (((i * 37) % 13) - 6) / 6 * a
def zona(m): return 1 if m < 0.43 else 2 if m < 0.65 else 3 if m < 0.87 else 4

def script03(dots, cw, ch, gx0, gx1, gy0, gy1):
    """Script do AutoCal; a geometria (tamanho do cartão e limites do gráfico) muda entre 1280×720 e 800×480."""
    t = SCRIPT_03.replace('@@PONTOS_JSON@@', json.dumps(dots, ensure_ascii=False))
    for k, v in (('@@CW@@', cw), ('@@CH@@', ch), ('@@GX0@@', gx0), ('@@GX1@@', gx1), ('@@GY0@@', gy0), ('@@GY1@@', gy1)):
        t = t.replace(k, str(v))
    return t

def base_cheia(cheio):
    """03 usa a geometria padrão (faixa de ações embaixo); 02 não tem ações e estica o gráfico até embaixo."""
    global Y1
    antigo = Y1
    if not cheio:
        Y1 = 424
    try:
        return base_svg(cheio)
    finally:
        Y1 = antigo

def base_svg(cheio):
    o = []
    for lo, hi, z in [(MAP0, 0.43, 1), (0.43, 0.65, 2), (0.65, 0.87, 3), (0.87, 1.08, 4)]:
        aprendida = cheio and z in (1, 2)
        o.append('<rect x="%d" y="%s" width="%d" height="%s" fill="%s"/>' % (X0, f(Y(hi)), X1 - X0, f(Y(lo) - Y(hi)), 'rgba(56,211,159,.10)' if aprendida else 'rgba(154,169,188,.05)'))
    for m in (0.2, 0.4, 0.6, 0.8, 1.0):
        o.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#1b2631" stroke-width="1"/>' % (X0, f(Y(m)), X1, f(Y(m))))
    for ms in range(1, 12):
        o.append('<line x1="%s" y1="%d" x2="%s" y2="%d" stroke="#1b2631" stroke-width="1"/>' % (f(X(ms)), Y0, f(X(ms)), Y1))
    for m in (0.43, 0.65, 0.87, 1.08):
        o.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#cbd5e1" stroke-opacity=".5" stroke-width="1.5" stroke-dasharray="6 5"/>' % (X0, f(Y(m)), X1, f(Y(m))))
    if cheio:
        o.append('<rect x="%d" y="%s" width="%d" height="%s" fill="#f4f7fb" fill-opacity=".08"/>' % (X0, f(Y(0.42)), X1 - X0, f(Y(0.36) - Y(0.42))))
        ms_list = [1.3 + 0.5 * i for i in range(21) if 1.3 + 0.5 * i <= 11.4]
        o.append('<polyline points="%s" fill="none" stroke="#ff7a7a" stroke-width="3" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(gas(m)))) for m in ms_list))
        o.append('<polyline points="%s" fill="none" stroke="#5fe38a" stroke-width="3" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(gnv(m)))) for m in ms_list))
        ax, ay = X(3.44), Y(0.39)
        o.append('<line x1="%s" y1="%d" x2="%s" y2="%d" stroke="#f4f7fb" stroke-opacity=".6" stroke-width="1.5"/>' % (f(ax), Y0, f(ax), Y1))
        o.append('<path d="M%s %s l9 9 l-9 9 l-9 -9 z" fill="#ff6b6b" stroke="#070a0f" stroke-width="1.5"/>' % (f(ax), f(ay - 9)))
        o.append('<text x="%s" y="%d" fill="#f4f7fb" font-size="15" font-weight="700" text-anchor="middle" stroke="#101720" stroke-width="4" style="paint-order:stroke">Agora</text>' % (f(ax), Y0 - 6))
    for z, (nome, sub, m) in enumerate((('Zona 1', 'leve', 0.29), ('Zona 2', 'normal', 0.54), ('Zona 3', 'firme', 0.76), ('Zona 4', 'a fundo', 0.975)), start=1):
        o.append('<text x="%d" y="%s" fill="#dbe4ef" font-size="14" font-weight="700">%s</text>' % (X1 + 10, f(Y(m) - 2), nome))
        o.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14">%s</text>' % (X1 + 10, f(Y(m) + 15), sub))
    for m in (0.2, 0.4, 0.6, 0.8, 1.0):
        o.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14" text-anchor="end">%s</text>' % (X0 - 8, f(Y(m) + 5), ('%.2f' % m).replace('.', ',')))
    for ms in range(1, 12, 2):
        o.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">%d</text>' % (f(X(ms)), Y1 + 20, ms))
    o.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">Injeção na gasolina (ms)</text>' % (f((X0 + X1) / 2), Y1 + 40))
    # quadradinhos da aquisição, à direita do gráfico
    for b in range(18):
        cy = Y(0.20 + b * 0.049)
        if cheio:
            if b <= 16: o.append('<rect x="780" y="%s" width="14" height="10" rx="2" fill="#ff7a7a"/>' % f(cy - 5))
            if b <= 9: o.append('<rect x="800" y="%s" width="14" height="10" rx="2" fill="#5fe38a"/>' % f(cy - 5))
        else:
            o.append('<rect x="780" y="%s" width="14" height="10" rx="2" fill="none" stroke="#263342" stroke-width="1.5"/>' % f(cy - 5))
            o.append('<rect x="800" y="%s" width="14" height="10" rx="2" fill="none" stroke="#263342" stroke-width="1.5"/>' % f(cy - 5))
    o.append('<text x="797" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">Aquisição</text>' % (Y1 + 20))
    return '\n'.join(o)

# ---------- pontos (posição do centro no cartão) ----------
def pontos():
    d = []
    gm = [1.7, 2.1, 2.6, 3.0, 3.5, 3.9, 4.4, 4.9, 5.3, 5.8, 6.3, 6.8, 7.3, 7.8, 8.3, 8.8, 9.4, 9.9, 10.4]
    for i, ms in enumerate(gm):
        m = gas(ms) + jit(i)
        d.append(dict(z=zona(m), c='g', x=X(ms), y=Y(m), out=False))
    nm = [1.8, 2.3, 2.8, 3.3, 3.8, 4.3, 4.8, 5.3, 5.8, 6.3, 7.2, 7.8]
    for i, ms in enumerate(nm):
        m = gnv(ms) + jit(i + 5)
        z = zona(m)
        if z == 4: continue
        d.append(dict(z=z, c='n', x=X(ms), y=Y(m), out=False))
    # fora da curva (4): 3 de GNV e 1 de gasolina
    for ms, m, c in ((3.1, 0.25, 'n'), (5.6, 0.70, 'n'), (6.9, 0.78, 'n'), (8.0, 0.67, 'g')):
        d.append(dict(z=zona(m), c=c, x=X(ms), y=Y(m), out=True))
    for k, p in enumerate(d):
        p['id'] = k; p['x'] = round(p['x'], 1); p['y'] = round(p['y'], 1)
    return d

def ico(path, cor='currentColor', w=2, size=24):
    return '<svg style="flex:none" width="%d" height="%d" viewBox="0 0 24 24" fill="none" stroke="%s" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">%s</svg>' % (size, size, cor, w, path)
I_ERASER = '<path d="m7 21-4.3-4.3c-1-1-1-2.5 0-3.4l9.6-9.6c1-1 2.5-1 3.4 0l5.6 5.6c1 1 1 2.5 0 3.4L13 21"/><path d="M22 21H7"/><path d="m5 11 9 9"/>'
I_CHECK = '<path d="M20 6 9 17l-5-5"/>'
I_DOTS = '<circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/>'
I_INFO = '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>'
I_WARN = '<path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><path d="M12 9v4M12 17h.01"/>'
I_DRAG = '<path d="M5 3l14 9-6 1.5L9.5 19z"/>'
I_PULSE = '<path d="M22 12h-4l-3 9L9 3l-3 9H2"/>'
I_UNDO = '<path d="M1 4v6h6M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>'
I_SLIDERS = '<path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6"/>'
I_REFRESH = '<path d="M3 12a9 9 0 0 1 15-6.7L21 8M21 3v5h-5M21 12a9 9 0 0 1-15 6.7L3 16M3 21v-5h5"/>'
I_SETTINGS = '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h0a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h0a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v0a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>'

# ---------- peças compartilhadas ----------
CABECALHO = r'''
<div style="position:absolute;left:32px;top:64px;width:1216px;height:56px;display:flex;align-items:center;gap:14px">
<h1 style="margin:0;flex:none;font-size:30px;font-weight:700;letter-spacing:-.01em">AutoCal</h1>
<div style="flex:none;box-sizing:border-box;height:38px;padding:0 14px;display:flex;align-items:center;gap:10px;border-radius:19px;background:{{chipBg}};border:1px solid {{chipBd}};font-size:17px;font-weight:700">
<span style="width:10px;height:10px;border-radius:50%;background:{{chipDot}}"></span>{{ chipText }}
<sc-if value="{{ aprendendo }}" hint-placeholder-val="{{ true }}"><span style="display:flex;gap:4px" aria-hidden="true"><span style="width:9px;height:9px;border-radius:50%;background:var(--accent)"></span><span style="width:9px;height:9px;border-radius:50%;background:var(--accent)"></span><span style="width:9px;height:9px;box-sizing:border-box;border-radius:50%;border:2px solid var(--muted)"></span></span><span style="font-size:15px;font-weight:600;color:var(--muted)">2 de 3</span></sc-if>
</div>
<div style="flex:1;min-width:0;box-sizing:border-box;height:48px;padding:0 16px;display:flex;align-items:center;gap:10px;border-radius:12px;background:var(--surface2);border:1px solid var(--line);font-size:17px;font-weight:600;white-space:nowrap">
<span style="flex:none;display:flex;color:{{msgFg}}">@@I_MSG@@</span><span>{{ msgText }}</span>
</div>
<button type="button" onClick="{{ alternarMenu }}" aria-expanded="{{ ariaMenu }}" aria-label="Mais ações do AutoCal" style="flex:none;height:56px;padding:0 18px;display:flex;align-items:center;gap:10px;border-radius:12px;border:1px solid {{menuBd}};background:{{menuBg}};color:var(--text);font:600 18px Inter,Roboto,Arial,sans-serif;cursor:pointer">@@I_DOTS@@Mais</button>
</div>
'''

MENU = r'''
<sc-if value="{{ menu }}" hint-placeholder-val="{{ false }}">
<div role="menu" aria-label="Mais ações do AutoCal" style="position:absolute;right:32px;top:124px;z-index:5;width:388px;box-sizing:border-box;padding:8px;display:flex;flex-direction:column;gap:4px;background:var(--surface);border:1px solid #2a4a7e;border-radius:14px;box-shadow:0 14px 32px #000a">
<button type="button" role="menuitem" onClick="{{ fecharMenu }}" style="min-height:58px;box-sizing:border-box;padding:6px 12px;display:flex;align-items:center;gap:12px;border:0;border-radius:10px;background:transparent;color:var(--text);font:600 18px Inter,Roboto,Arial,sans-serif;text-align:left;cursor:pointer">@@I_SLIDERS@@Curva K toda em 1,0</button>
<button type="button" role="menuitem" onClick="{{ fecharMenu }}" style="min-height:58px;box-sizing:border-box;padding:6px 12px;display:flex;align-items:center;gap:12px;border:0;border-radius:10px;background:transparent;color:var(--text);font:600 18px Inter,Roboto,Arial,sans-serif;text-align:left;cursor:pointer">@@I_SETTINGS@@Preferências</button>
<button type="button" role="menuitem" onClick="{{ fecharMenu }}" style="min-height:58px;box-sizing:border-box;padding:6px 12px;display:flex;align-items:center;gap:12px;border:0;border-radius:10px;background:transparent;color:var(--danger);font:600 18px Inter,Roboto,Arial,sans-serif;text-align:left;cursor:pointer">@@I_UNDO_R@@Recomeçar tudo</button>
</div>
</sc-if>
'''

def _acao(on, t, sub, cor, bg, bd, aria, cursor='pointer'):
    return ('<button type="button" onClick="{{ %s }}" aria-label="%s" style="box-sizing:border-box;min-width:0;height:56px;padding:0 12px;display:flex;flex-direction:column;justify-content:center;align-items:flex-start;gap:1px;border-radius:12px;background:%s;border:1px solid %s;text-align:left;cursor:%s;font-family:Inter,Roboto,Arial,sans-serif">'
            '<span style="max-width:100%%;font-size:16px;font-weight:700;line-height:20px;white-space:nowrap;color:%s">%s</span>'
            '<span style="max-width:100%%;font-size:14px;font-weight:500;line-height:17px;white-space:nowrap;color:var(--muted)">%s</span></button>') % (on, aria, bg, bd, cursor, cor, t, sub)

ACOES = ('<div style="position:absolute;left:16px;right:16px;top:430px;height:56px;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px">'
         + _acao('limparFora', '{{ acT1 }}', '{{ acS1 }}', '{{ acC1 }}', '{{ foraBg }}', '{{ foraBd }}', 'Apagar pontos', 'pointer')
         + _acao('reaprenderGnv', 'Reaprender GNV', 'apaga o gás', '#ff9b9b', 'var(--surface2)', 'var(--line)', 'Reaprender todo o GNV: apaga os pontos do gás')
         + _acao('reaprenderGas', 'Reaprender gasolina', 'apaga a gasolina', '#ff9b9b', 'var(--surface2)', 'var(--line)', 'Reaprender toda a gasolina: apaga os pontos da gasolina')
         + '<div role="button" aria-disabled="true" aria-label="AutoMatch manual: libera quando a central terminar os ajustes (2 de 3)" style="box-sizing:border-box;min-width:0;height:56px;padding:0 12px;display:flex;flex-direction:column;justify-content:center;gap:1px;border-radius:12px;background:var(--bg);border:1px dashed var(--line);font-family:Inter,Roboto,Arial,sans-serif"><span style="font-size:16px;font-weight:700;line-height:20px;white-space:nowrap;color:var(--dim)">AutoMatch manual</span><span style="font-size:14px;line-height:17px;white-space:nowrap;color:var(--dim)">espera a central</span></div>'
         + '</div>')

REAPRENDER = r'''<button type="button" role="menuitem" onClick="{{ reaprenderGnv }}" style="min-height:58px;box-sizing:border-box;padding:6px 12px;display:flex;align-items:center;gap:12px;border:0;border-radius:10px;background:transparent;color:var(--text);font:600 18px Inter,Roboto,Arial,sans-serif;text-align:left;cursor:pointer">@@I_ERASER_MD@@<span style="display:flex;flex-direction:column"><b style="font-size:18px;font-weight:600">Reaprender todo o GNV</b><span style="font-size:14px;font-weight:500;color:var(--muted)">apaga os pontos do gás</span></span></button>
<button type="button" role="menuitem" onClick="{{ reaprenderGas }}" style="min-height:58px;box-sizing:border-box;padding:6px 12px;display:flex;align-items:center;gap:12px;border:0;border-radius:10px;background:transparent;color:var(--text);font:600 18px Inter,Roboto,Arial,sans-serif;text-align:left;cursor:pointer">@@I_ERASER_MD@@<span style="display:flex;flex-direction:column"><b style="font-size:18px;font-weight:600">Reaprender toda a gasolina</b><span style="font-size:14px;font-weight:500;color:var(--muted)">apaga os pontos da gasolina</span></span></button>'''

SELO = r'''<sc-if value="{{ atividade }}" hint-placeholder-val="{{ false }}"><div role="status" style="position:absolute;right:16px;top:10px;height:36px;box-sizing:border-box;padding:0 12px;display:flex;align-items:center;gap:7px;border-radius:10px;font-size:15px;font-weight:600;white-space:nowrap;background:var(--ok-soft);border:1px solid #1d5a46;color:var(--ok)">@@I_CK_SELO@@{{ atividadeTexto }}</div></sc-if>'''

INTERRUPTOR = r'''
<button type="button" role="switch" aria-checked="{{ ariaLigado }}" onClick="{{ alternarAprendizado }}" style="flex:none;box-sizing:border-box;width:100%;height:58px;padding:0 14px;display:flex;align-items:center;gap:12px;border-radius:12px;background:{{swBg}};border:1px solid {{swBd}};color:var(--text);font:700 19px Inter,Roboto,Arial,sans-serif;cursor:pointer;text-align:left">
<span style="flex:none;position:relative;width:48px;height:28px;border-radius:14px;background:{{swTrilho}}"><span style="position:absolute;top:3px;left:{{swKnob}}px;width:22px;height:22px;border-radius:50%;background:#fff"></span></span>
<span style="flex:1;min-width:0;display:flex;flex-direction:column;line-height:1.15"><span style="font-size:18px">Aprendizado automático</span><span style="font-size:15px;font-weight:700;color:{{swFg}}">{{ swTexto }}</span></span>
</button>
'''

def montar(modo):
    nome = '03-AutoCal.dc.html' if modo == 'aprendendo' else '02-AutoCal-esperando.dc.html'
    sh = shell(nome)
    cheio = modo == 'aprendendo'
    P = pontos()
    html = r'''<!doctype html>
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
<aside aria-label="Estado do aprendizado" style="position:absolute;left:32px;top:128px;width:370px;height:496px;box-sizing:border-box;padding:14px 16px;display:flex;flex-direction:column;gap:10px;background:var(--surface);border:1px solid var(--line);border-radius:14px">
@@INTERRUPTOR@@
@@PAINEL@@
</aside>

<section aria-label="Gráfico do aprendizado" style="position:absolute;left:418px;top:128px;width:830px;height:496px;background:var(--surface);border:1px solid var(--line);border-radius:14px;overflow:hidden">
<div style="position:absolute;left:16px;top:10px;height:34px;display:flex;align-items:center;gap:18px;font-size:15px;color:var(--muted);white-space:nowrap">
<span style="display:flex;align-items:center;gap:7px"><span style="width:12px;height:12px;border-radius:50%;background:var(--petrol)"></span>Gasolina</span>
<span style="display:flex;align-items:center;gap:7px"><span style="width:12px;height:12px;border-radius:3px;background:var(--gas)"></span>GNV</span>
@@LEG_EXTRA@@
</div>
@@DICA@@
<svg width="830" height="496" viewBox="0 0 830 496" style="position:absolute;left:0;top:0" role="img" aria-label="Gráfico: pressão da admissão contra injeção na gasolina, em quatro zonas de uso">
@@BASE@@
</svg>
@@PONTOS@@
@@ACOES@@
@@SOBRE@@
</section>

@@MENU@@
@@NAV@@

</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":1280,"height":720}}'>
@@SCRIPT@@
</script>
</body>
</html>
'''
    rep = {'@@HELMET@@': sh['helmet'], '@@ROOT@@': sh['root'], '@@TOPBAR@@': sh['topbar'], '@@NAV@@': sh['nav'],
           '@@TITULO@@': 'AutoCal' if cheio else 'AutoCal esperando o motor', '@@INTERRUPTOR@@': INTERRUPTOR.strip(),
           '@@MENU@@': MENU.strip(), '@@BASE@@': base_cheia(cheio)}
    cab = CABECALHO.strip()
    rep['@@CABECALHO@@'] = cab
    rep['@@SELO@@'] = SELO.strip() if cheio else ''
    rep['@@ACOES@@'] = ACOES if cheio else ''
    rep['@@REAPRENDER@@'] = ''
    rep['@@I_CK_SELO@@'] = ico(I_CHECK, 'currentColor', 2.5, 20)
    if cheio:
        rep['@@LEG_EXTRA@@'] = ('<span style="display:flex;align-items:center;gap:7px"><svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><path d="M7 1 13 7 7 13 1 7z" fill="#ff6b6b"/></svg>Agora</span>'
                                '<span style="display:flex;align-items:center;gap:7px"><span style="width:14px;height:14px;box-sizing:border-box;border-radius:50%;border:2px dashed var(--warn)"></span>Fora da curva</span>'
                                '<span style="display:flex;align-items:center;gap:7px"><span style="width:16px;height:12px;border-radius:3px;background:rgba(56,211,159,.28)"></span>Zona aprendida</span>')
        rep['@@DICA@@'] = '<div style="position:absolute;right:16px;top:14px;height:26px;display:flex;align-items:center;gap:6px;font-size:14px;font-weight:600;color:var(--muted);white-space:nowrap;pointer-events:none">' + ico(I_DRAG, 'currentColor', 2, 18) + '{{ dica }}</div>'
        rep['@@PONTOS@@'] = r'''<sc-for list="{{ pontos }}" as="p" hint-placeholder-count="30">
<span style="position:absolute;left:{{p.l}}px;top:{{p.t}}px;width:{{p.w}}px;height:{{p.w}}px;box-sizing:border-box;border-radius:{{p.r}};background:{{p.bg}};border:1.5px solid #070a0f;box-shadow:{{p.ring}}"></span>
</sc-for>
<sc-for list="{{ aneis }}" as="a" hint-placeholder-count="4">
<span style="position:absolute;left:{{a.l}}px;top:{{a.t}}px;width:28px;height:28px;box-sizing:border-box;border-radius:50%;border:2px dashed var(--warn);pointer-events:none"></span>
</sc-for>
<sc-if value="{{ temCaixa }}" hint-placeholder-val="{{ false }}"><div style="position:absolute;left:{{caixa.l}}px;top:{{caixa.t}}px;width:{{caixa.w}}px;height:{{caixa.h}}px;box-sizing:border-box;border:2px dashed var(--accent);background:rgba(47,140,255,.14);pointer-events:none"></div></sc-if>
<div onPointerDown="{{ baixo }}" onPointerMove="{{ mover }}" onPointerUp="{{ soltar }}" onPointerCancel="{{ cancelar }}" aria-label="Gráfico: arraste para escolher pontos e apagar" style="position:absolute;left:0;top:0;width:830px;height:496px;touch-action:none;cursor:crosshair"></div>'''
        rep['@@SOBRE@@'] = ''
        rep['@@PAINEL@@'] = r'''<div style="flex:1;min-height:0;display:flex;flex-direction:column;gap:8px">
<div style="display:grid;grid-template-columns:88px 24px minmax(0,1fr) minmax(0,1fr);grid-template-rows:26px repeat(4,68px);gap:6px">
<span></span><span></span>
<span style="display:flex;align-items:center;gap:7px;font-size:15px;font-weight:600;color:var(--muted)"><span style="width:12px;height:12px;border-radius:50%;background:var(--petrol)"></span>Gasolina</span>
<span style="display:flex;align-items:center;gap:7px;font-size:15px;font-weight:600;color:var(--muted)"><span style="width:12px;height:12px;border-radius:3px;background:var(--gas)"></span>GNV</span>
<div style="grid-column:2;grid-row:2 / span 4;display:flex;align-items:center;justify-content:center;writing-mode:vertical-rl;transform:rotate(180deg);gap:8px;font-size:14px;font-weight:800;letter-spacing:.14em;color:var(--danger);white-space:nowrap">TOQUE PARA APAGAR</div>
<sc-for list="{{ zonas }}" as="z" hint-placeholder-count="4">
<div style="display:flex;flex-direction:column;justify-content:center;min-width:0"><b style="font-size:19px;font-weight:700">{{ z.nome }}</b><span style="font-size:14px;font-weight:{{z.agoraPeso}};color:{{z.agoraCor}}">{{ z.sub }}</span></div>
<sc-for list="{{ z.celulas }}" as="c" hint-placeholder-count="2">
<button type="button" onClick="{{ c.apagar }}" aria-label="{{ c.aria }}" style="box-sizing:border-box;width:100%;height:68px;padding:0 10px;display:flex;align-items:center;justify-content:space-between;gap:4px;border-radius:12px;background:{{c.bg}};border:1px solid {{c.bd}};color:{{c.fg}};font:700 17px Inter,Roboto,Arial,sans-serif;cursor:{{c.cursor}}">
<span style="white-space:nowrap">{{ c.texto }}</span>
<sc-if value="{{ c.podeApagar }}" hint-placeholder-val="{{ true }}"><span style="flex:none;display:flex;color:#ff9b9b">@@I_ERASER_SM@@</span></sc-if>
</button>
</sc-for>
</sc-for>
</div>

</div>
<div role="status" aria-live="polite" style="flex:none;min-height:44px;display:flex;gap:10px;align-items:flex-start;font-size:15px;line-height:1.3;color:{{rodapeCor}}"><span style="flex:none;display:flex;margin-top:1px">@@I_RODAPE@@</span><span>{{ rodape }}</span></div>'''
        rep['@@SCRIPT@@'] = script03(P, 830, 496, 54, 702, 42, 382)
    else:
        rep['@@LEG_EXTRA@@'] = ''
        rep['@@DICA@@'] = ''
        rep['@@PONTOS@@'] = ''
        rep['@@SOBRE@@'] = r'''<div style="position:absolute;left:190px;top:130px;width:430px;height:130px;box-sizing:border-box;padding:0 26px;display:flex;align-items:center;gap:18px;border-radius:14px;background:rgba(11,17,23,.94);border:1px solid var(--line)">
<span style="flex:none;width:58px;height:58px;display:flex;align-items:center;justify-content:center;border-radius:50%;background:var(--accent-soft);border:1px solid #2a4a7e;color:var(--accent)">@@I_PULSE_BIG@@</span>
<div style="display:flex;flex-direction:column;gap:4px"><b style="font-size:24px;font-weight:700">Esperando a central</b><span style="font-size:17px;line-height:1.3;color:var(--muted)">O gráfico aparece sozinho quando ela mandar a curva de referência.</span></div>
</div>'''
        rep['@@PAINEL@@'] = r'''<div style="font-size:14px;font-weight:700;letter-spacing:.12em;color:var(--dim)">CONDIÇÕES PARA APRENDER · AUTOMÁTICO</div>
<div style="flex:none;box-sizing:border-box;padding:12px 14px;border-radius:12px;background:var(--warn-soft);border:1px solid #6b5420">
<div style="display:flex;justify-content:space-between;align-items:baseline"><b style="font-size:19px;font-weight:700">Água do motor</b><b style="font-size:22px;font-weight:800;color:var(--warn);font-variant-numeric:tabular-nums">62 °C</b></div>
<div style="margin-top:2px;font-size:15px;color:var(--muted)">Esquentando · de 80 °C</div>
<div style="margin-top:10px;height:10px;border-radius:5px;background:var(--surface3);overflow:hidden" role="progressbar" aria-valuemin="0" aria-valuemax="80" aria-valuenow="62" aria-label="Água do motor"><div style="width:78%;height:100%;background:var(--warn)"></div></div>
</div>
<div style="flex:none;box-sizing:border-box;padding:12px 14px;display:flex;gap:12px;align-items:center;border-radius:12px;background:var(--ok-soft);border:1px solid #1d5a46"><span style="flex:none;display:flex">@@I_CHECK_OK@@</span><span style="display:flex;flex-direction:column"><b style="font-size:19px;font-weight:700">Gás no tanque</b><span style="font-size:15px;color:var(--muted)">¼ · o bastante para aprender</span></span></div>
<div style="flex:none;box-sizing:border-box;padding:12px 14px;display:flex;gap:12px;align-items:center;border-radius:12px;background:var(--ok-soft);border:1px solid #1d5a46"><span style="flex:none;display:flex">@@I_CHECK_OK@@</span><span style="display:flex;flex-direction:column"><b style="font-size:19px;font-weight:700">Central sem falhas</b><span style="font-size:15px;color:var(--muted)">As 14 verificações estão certas</span></span></div>
<div style="margin-top:auto;display:flex;gap:10px;align-items:flex-start;font-size:16px;line-height:1.35;color:var(--muted)"><span style="flex:none;display:flex;margin-top:1px">@@I_INFO_ACC@@</span>O aprendizado começa sozinho quando as três estiverem certas. Você não precisa tocar em nada.</div>'''
        rep['@@SCRIPT@@'] = SCRIPT_02
    rep['@@I_MSG@@'] = '<sc-if value="{{ msgInfo }}" hint-placeholder-val="{{ true }}">' + ico(I_INFO, 'currentColor', 2, 24) + '</sc-if><sc-if value="{{ msgWarn }}" hint-placeholder-val="{{ false }}">' + ico(I_WARN, 'currentColor', 2.2, 24) + '</sc-if>'
    rep['@@I_DOTS@@'] = ico(I_DOTS, 'currentColor', 2, 24)
    rep['@@I_REFRESH@@'] = ico(I_REFRESH, 'currentColor', 2, 24); rep['@@I_SLIDERS@@'] = ico(I_SLIDERS, 'currentColor', 2, 24)
    rep['@@I_SETTINGS@@'] = ico(I_SETTINGS, 'currentColor', 2, 24); rep['@@I_UNDO_R@@'] = ico(I_UNDO, 'currentColor', 2, 24)
    rep['@@I_ERASER_SM@@'] = ico(I_ERASER, 'currentColor', 2, 20); rep['@@I_ERASER_MD@@'] = ico(I_ERASER, 'currentColor', 2, 26)
    rep['@@I_PULSE_BIG@@'] = ico(I_PULSE, 'currentColor', 2, 30); rep['@@I_CHECK_OK@@'] = ico(I_CHECK, '#38d39f', 3, 26)
    rep['@@I_INFO_ACC@@'] = ico(I_INFO, '#2f8cff', 2, 22)
    rep['@@I_RODAPE@@'] = ('<sc-if value="{{ rodapeOk }}" hint-placeholder-val="{{ true }}">' + ico(I_CHECK, 'currentColor', 3, 20) + '</sc-if><sc-if value="{{ rodapeInfo }}" hint-placeholder-val="{{ false }}">' + ico(I_INFO, 'currentColor', 2, 20) + '</sc-if>') if cheio else ''
    # ordem importa: CABECALHO e PAINEL carregam outros marcadores
    for k in ('@@CABECALHO@@', '@@SELO@@', '@@DICA@@', '@@ACOES@@', '@@PAINEL@@', '@@SOBRE@@', '@@PONTOS@@', '@@MENU@@', '@@REAPRENDER@@', '@@INTERRUPTOR@@', '@@SCRIPT@@'):
        html = html.replace(k, rep[k])
    for k, v in rep.items():
        html = html.replace(k, v)
    assert '@@' not in html, re.findall(r'@@\w+@@', html)
    open(os.path.join(PROJ, nome), 'w', encoding='utf-8').write(html)
    print('escrito', nome, len(html))

SCRIPT_03 = r'''class Component extends DCLogic {
  state = { gone: {}, goneIds: {}, sel: {}, drag: null, outGone: false, paused: false, menu: false, msg: '' };
  renderVals() {
    const st = Object.assign({ gone: {}, goneIds: {}, sel: {}, drag: null, outGone: false, paused: false, menu: false, msg: '' }, this.state || {});
    const DOTS = @@PONTOS_JSON@@.map((p, i) => Object.assign({ id: i }, p));
    const vivo = (p, s) => !(s.gone || {})[p.z + p.c] && !(p.out && s.outGone) && !(s.goneIds || {})[p.id];
    const base = { '4g': 'Pronta', '4n': 'Falta', '3g': 'Pronta', '3n': '60 %', '2g': 'Pronta', '2n': 'Pronta', '1g': 'Pronta', '1n': 'Pronta' };
    const vivos = DOTS.filter(p => vivo(p, st));
    const cont = k => vivos.filter(p => p.z + p.c === k).length;
    const todos = k => DOTS.filter(p => p.z + p.c === k).length;
    const nomeComb = c => (c === 'g' ? 'gasolina' : 'GNV');
    const apagar = (z, c) => () => this.setState(s => {
      const k = z + c, n = DOTS.filter(p => p.z + p.c === k && vivo(p, s)).length;
      return { gone: Object.assign({}, s.gone, { [k]: true }), sel: {}, menu: false, msg: 'Apaguei ' + n + (n === 1 ? ' ponto' : ' pontos') + ' de ' + nomeComb(c) + ' na zona ' + z + '. A central volta a aprender sozinha.' };
    });
    const reaprender = c => () => this.setState(s => {
      const ks = [4, 3, 2, 1].map(z => z + c), n = DOTS.filter(p => p.c === c && vivo(p, s)).length;
      const gone = Object.assign({}, s.gone); ks.forEach(k => { gone[k] = true; });
      return { gone, sel: {}, menu: false, msg: 'Apaguei ' + (c === 'n' ? 'todo o GNV' : 'toda a gasolina') + ' (' + n + (n === 1 ? ' ponto' : ' pontos') + '). A central volta a aprender sozinha.' };
    });
    const zonas = [4, 3, 2, 1].map(z => ({
      nome: 'Zona ' + z, sub: z === 1 ? 'leve · agora' : z === 4 ? 'a fundo' : z === 3 ? 'firme' : 'normal',
      agoraCor: z === 1 ? '#2f8cff' : '#9aa9bc', agoraPeso: z === 1 ? 700 : 500,
      celulas: ['g', 'n'].map(c => {
        const k = z + c, n = cont(k), orig = base[k];
        const texto = n === 0 ? 'Falta' : orig === 'Falta' ? 'Pronta' : orig;
        const pronta = n > 0 && texto === 'Pronta', parcial = n > 0 && texto === '60 %', falta = n === 0;
        return { texto, pronta, parcial, falta, podeApagar: n > 0,
          bg: pronta ? '#0f2b23' : parcial ? '#2d240f' : '#151e29', bd: pronta ? '#1d5a46' : parcial ? '#6b5420' : '#263342',
          fg: pronta ? '#38d39f' : parcial ? '#f3ba59' : '#9aa9bc', cursor: n > 0 ? 'pointer' : 'default',
          aria: n > 0 ? 'Apagar os ' + n + ' pontos de ' + nomeComb(c) + ' da zona ' + z + ' (' + texto + ')' : 'Sem pontos de ' + nomeComb(c) + ' na zona ' + z,
          apagar: n > 0 ? apagar(z, c) : () => {} };
      }) }));
    const lista = a => (a.length === 4 ? 'em todas as zonas' : a.length === 1 ? 'na zona ' + a[0] : 'nas zonas ' + a.slice(0, -1).join(', ') + ' e ' + a[a.length - 1]);
    const incompletas = i => zonas.filter(z => !z.celulas[i].pronta).map(z => +z.nome.slice(5)).sort((a, b) => a - b);
    const faltaGnv = incompletas(1), faltaGas = incompletas(0);
    const faltaMsg = faltaGnv.length ? 'Falta o GNV ' + lista(faltaGnv) + '. Vem com o uso normal.'
      : faltaGas.length ? 'Falta a gasolina ' + lista(faltaGas) + '. Vem com o uso normal.' : 'Tudo aprendido por enquanto. A central segue refinando.';
    const fora = vivos.filter(p => p.out).length;
    const rodapeMsg = st.paused ? ['Aprendizado pausado: a central não aprende até você ligar.', '#f3ba59', false]
      : st.msg ? [st.msg, '#f4f7fb', false] : ['Tudo certo: água 86 °C · gás ¼ · sem falhas.', '#38d39f', true];
    const nSel = vivos.filter(p => st.sel[p.id]).length;
    const pontos = vivos.map(p => ({ l: p.x - 5.5, t: p.y - 5.5, w: 11, r: p.c === 'n' ? '2px' : '50%', bg: p.c === 'n' ? '#5fe38a' : '#ff7a7a', ring: st.sel[p.id] ? '0 0 0 3px #f4f7fb' : 'none' }));
    const toCard = e => { const r = e.currentTarget.getBoundingClientRect(); return { x: (e.clientX - r.left) * @@CW@@ / r.width, y: (e.clientY - r.top) * @@CH@@ / r.height }; };
    const noGrafico = q => q.x >= @@GX0@@ && q.x <= @@GX1@@ && q.y >= @@GY0@@ && q.y <= @@GY1@@;
    const lim = (v, a, b) => Math.max(a, Math.min(b, v));
    const d = st.drag, temCaixa = !!(d && d.moved);
    const caixa = temCaixa ? { l: Math.min(d.x0, d.x1), t: Math.min(d.y0, d.y1), w: Math.abs(d.x1 - d.x0), h: Math.abs(d.y1 - d.y0) } : { l: 0, t: 0, w: 0, h: 0 };
    const apagarSel = () => this.setState(s => {
      const ids = vivos.filter(p => (s.sel || {})[p.id]).map(p => p.id);
      const goneIds = Object.assign({}, s.goneIds); ids.forEach(i => { goneIds[i] = true; });
      return { goneIds, sel: {}, menu: false, msg: 'Apaguei ' + ids.length + (ids.length === 1 ? ' ponto escolhido' : ' pontos escolhidos') + '. A central volta a aprender sozinha.' };
    });
    const aneis = vivos.filter(p => p.out).map(p => ({ l: p.x - 14, t: p.y - 14 }));
    const lig = !st.paused;
    return {
      pontos, aneis, zonas, menu: !!st.menu, ariaMenu: st.menu ? 'true' : 'false',
      atividade: !!st.msg, semAtividade: !st.msg, atividadeTexto: 'Apagado e conferido', reaprenderGnv: reaprender('n'), reaprenderGas: reaprender('g'),
      alternarMenu: () => this.setState({ menu: !st.menu }), fecharMenu: () => this.setState({ menu: false }),
      menuBg: st.menu ? '#14284a' : '#151e29', menuBd: st.menu ? '#2a4a7e' : '#263342',
      aprendendo: lig, chipText: lig ? 'Aprendendo' : 'Pausado', chipBg: lig ? '#14284a' : '#2d240f', chipBd: lig ? '#2a4a7e' : '#6b5420', chipDot: lig ? '#2f8cff' : '#f3ba59',
      msgText: lig ? faltaMsg : 'Pausado. A central só aprende de novo quando você ligar.', msgInfo: lig, msgWarn: !lig, msgFg: lig ? '#2f8cff' : '#f3ba59',
      ariaLigado: lig ? 'true' : 'false', swBg: lig ? '#14284a' : '#151e29', swBd: lig ? '#2a4a7e' : '#263342', swTrilho: lig ? '#1f6fd8' : '#3a4a5c', swKnob: lig ? 23 : 3,
      swTexto: lig ? 'Ligado' : 'Desligado', swFg: lig ? '#38d39f' : '#9aa9bc',
      alternarAprendizado: () => this.setState({ paused: !st.paused, menu: false }),
      dica: nSel > 0 ? nSel + (nSel === 1 ? ' escolhido' : ' escolhidos') : 'Arraste para escolher pontos',
      temCaixa, caixa,
      baixo: e => { const q = toCard(e); if (!noGrafico(q)) return; try { e.currentTarget.setPointerCapture(e.pointerId); } catch (err) { /* sem captura */ } this.setState({ drag: { x0: q.x, y0: q.y, x1: q.x, y1: q.y, moved: false }, menu: false }); },
      mover: e => { const cur = this.state && this.state.drag; if (!cur) return; const q = toCard(e); this.setState({ drag: Object.assign({}, cur, { x1: lim(q.x, @@GX0@@, @@GX1@@), y1: lim(q.y, @@GY0@@, @@GY1@@), moved: cur.moved || Math.abs(q.x - cur.x0) > 8 || Math.abs(q.y - cur.y0) > 8 }) }); },
      soltar: () => {
        const cur = this.state && this.state.drag; if (!cur) return;
        if (!cur.moved) {
          let hit = null, bd = 18;
          vivos.forEach(p => { const dd = Math.hypot(p.x - cur.x0, p.y - cur.y0); if (dd < bd) { bd = dd; hit = p; } });
          const sel = Object.assign({}, st.sel);
          if (hit) { if (sel[hit.id]) delete sel[hit.id]; else sel[hit.id] = true; this.setState({ sel, drag: null }); } else this.setState({ sel: {}, drag: null });
          return;
        }
        const xa = Math.min(cur.x0, cur.x1), xb = Math.max(cur.x0, cur.x1), ya = Math.min(cur.y0, cur.y1), yb = Math.max(cur.y0, cur.y1);
        const sel = {}; vivos.forEach(p => { if (p.x >= xa && p.x <= xb && p.y >= ya && p.y <= yb) sel[p.id] = true; });
        this.setState({ sel, drag: null });
      },
      cancelar: () => this.setState({ drag: null }),
      acT1: nSel > 0 ? 'Apagar ' + nSel + (nSel === 1 ? ' ponto' : ' pontos') : fora > 0 ? 'Limpar ' + fora + (fora === 1 ? ' ponto' : ' pontos') : 'Fora da curva',
      acS1: nSel > 0 ? 'toque fora: desmarca' : fora > 0 ? 'fora da curva' : 'nenhum ponto',
      acC1: nSel > 0 ? '#ff9b9b' : fora > 0 ? '#f4f7fb' : '#9aa9bc',
      foraTitulo: nSel > 0 ? 'Apagar ' + nSel + (nSel === 1 ? ' ponto escolhido' : ' pontos escolhidos') : fora > 0 ? 'Limpar ' + fora + (fora === 1 ? ' ponto' : ' pontos') + ' fora da curva' : 'Nada fora da curva',
      foraSub: nSel > 0 ? 'toque fora dos pontos para desmarcar' : fora > 0 ? 'anel tracejado no gráfico' : 'os pontos estão todos na curva',
      foraBg: nSel > 0 ? '#2e1618' : fora > 0 ? '#2d240f' : '#151e29', foraBd: nSel > 0 ? '#7a3036' : fora > 0 ? '#6b5420' : '#263342', foraFg: nSel > 0 ? '#ff9b9b' : fora > 0 ? '#f4f7fb' : '#9aa9bc', foraCursor: nSel > 0 || fora > 0 ? 'pointer' : 'default',
      limparFora: () => { if (nSel > 0) apagarSel(); else if (fora > 0) this.setState({ outGone: true, sel: {}, menu: false, msg: 'Apaguei ' + fora + (fora === 1 ? ' ponto' : ' pontos') + ' fora da curva. A central volta a aprender sozinha.' }); },
      rodape: rodapeMsg[0], rodapeCor: rodapeMsg[1], rodapeOk: rodapeMsg[2], rodapeInfo: !rodapeMsg[2]
    };
  }
}'''

SCRIPT_02 = r'''class Component extends DCLogic {
  state = { paused: false, menu: false };
  renderVals() {
    const st = Object.assign({ paused: false, menu: false }, this.state || {});
    const lig = !st.paused;
    return {
      menu: !!st.menu, ariaMenu: st.menu ? 'true' : 'false',
      alternarMenu: () => this.setState({ menu: !st.menu }), fecharMenu: () => this.setState({ menu: false }),
      menuBg: st.menu ? '#14284a' : '#151e29', menuBd: st.menu ? '#2a4a7e' : '#263342',
      aprendendo: false, chipText: lig ? 'Esperando o motor' : 'Pausado', chipBg: lig ? '#2d240f' : '#151e29', chipBd: lig ? '#6b5420' : '#263342', chipDot: lig ? '#f3ba59' : '#9aa9bc',
      msgText: lig ? 'O motor está esquentando. O app começa a aprender sozinho.' : 'Pausado. A central só aprende de novo quando você ligar.', msgInfo: lig, msgWarn: !lig, msgFg: lig ? '#2f8cff' : '#f3ba59',
      ariaLigado: lig ? 'true' : 'false', swBg: lig ? '#14284a' : '#151e29', swBd: lig ? '#2a4a7e' : '#263342', swTrilho: lig ? '#1f6fd8' : '#3a4a5c', swKnob: lig ? 23 : 3,
      swTexto: lig ? 'Ligado' : 'Desligado', swFg: lig ? '#38d39f' : '#9aa9bc',
      alternarAprendizado: () => this.setState({ paused: !st.paused, menu: false })
    };
  }
}'''

if __name__ == '__main__':
    montar('aprendendo')
    montar('esperando')
