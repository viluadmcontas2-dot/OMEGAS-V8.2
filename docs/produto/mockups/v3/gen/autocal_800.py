"""Gera 16-AutoCal-800x480.dc.html: o AutoCal da tela 03 numa multimídia pequena (800×480).
Mesma lógica da 03 (script03 de autocal_v3), geometria própria: sem faixa de ações, a faixa "Curva K" some
e os botões de apagar ficam logo abaixo do gráfico. Uso: python3 -I autocal_800.py"""
import os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import autocal_v3 as A

PROJ = os.path.join(HERE, '..', 'project')
PARTS = os.path.join(HERE, 'parts')
ico, f = A.ico, A.f

CW, CH = 500, 324                    # cartão do gráfico
GX0, GX1, GY0, GY1 = 50, 420, 34, 214  # área do gráfico dentro do cartão
MS0, MS1, MAP0, MAP1 = A.MS0, A.MS1, A.MAP0, A.MAP1
def X(ms): return GX0 + (ms - MS0) / (MS1 - MS0) * (GX1 - GX0)
def Y(m): return GY1 - (m - MAP0) / (MAP1 - MAP0) * (GY1 - GY0)

def parte(nome): return open(os.path.join(PARTS, nome), encoding='utf-8').read()

def topbar800(lamp_txt='No gás', inj='3,44', mapv='0,39', rpm='1.179', tensao='13,8'):
    """Barra do topo 800×40 em uma linha por célula, na ordem do dono: combustível · nível · injeção · MAP · RPM · tensão."""
    cel = 'height:32px;flex:none;box-sizing:border-box;padding:0 10px;display:flex;align-items:center;gap:6px;background:var(--surface);border:1px solid var(--line);border-radius:10px;white-space:nowrap'
    rot = 'font-size:14px;font-weight:500;color:var(--muted)'
    num = 'font-size:17px;font-weight:700;font-variant-numeric:tabular-nums'
    pont = lambda cor: '<span style="width:9px;height:9px;box-sizing:border-box;border-radius:50%%;background:%s;border:1px solid %s"></span>' % cor
    on, off = ('var(--ok)', 'var(--ok)'), ('var(--surface3)', 'var(--line)')
    return ('<div style="position:absolute;left:0;top:0;width:800px;height:40px;box-sizing:border-box;padding:0 8px;display:flex;align-items:center;gap:6px;border-bottom:1px solid var(--line-soft)">\n'
            '<div style="height:32px;flex:none;box-sizing:border-box;padding:0 10px;display:flex;align-items:center;gap:6px;background:var(--ok-soft);border:1px solid #1d5a46;border-radius:10px;font-size:16px;font-weight:700;color:var(--gas);white-space:nowrap"><span style="width:12px;height:12px;border-radius:50%;background:var(--gas)"></span>' + lamp_txt + '</div>\n'
            '<div style="' + cel + '"><span style="' + rot + '">Nível</span><span style="display:flex;gap:3px">' + ''.join(pont(c) for c in (on, on, off, off, off)) + '</span></div>\n'
            '<div style="' + cel + '"><span style="' + rot + '">Inj. gasolina</span><b style="' + num + '">' + inj + '</b><span style="' + rot + '">ms</span></div>\n'
            '<div style="' + cel + '"><span style="' + rot + '">MAP</span><b style="' + num + '">' + mapv + '</b><span style="' + rot + '">bar</span></div>\n'
            '<div style="' + cel + '"><span style="' + rot + '">RPM</span><b style="' + num + '">' + rpm + '</b></div>\n'
            '<div style="' + cel + '"><span style="' + rot + '">Tensão</span><b style="' + num + '">' + tensao + '</b><span style="' + rot + '">V</span></div>\n'
            '</div>')

def pontos800():
    out = []
    for p in A.pontos():
        q = dict(p)
        q['x'] = round(GX0 + (p['x'] - A.X0) / (A.X1 - A.X0) * (GX1 - GX0), 1)
        q['y'] = round(GY1 - (A.Y1 - p['y']) / (A.Y1 - A.Y0) * (GY1 - GY0), 1)
        out.append(q)
    return out

def base800():
    o = []
    for lo, hi, z in [(MAP0, 0.43, 1), (0.43, 0.65, 2), (0.65, 0.87, 3), (0.87, 1.08, 4)]:
        o.append('<rect x="%d" y="%s" width="%d" height="%s" fill="%s"/>' % (GX0, f(Y(hi)), GX1 - GX0, f(Y(lo) - Y(hi)), 'rgba(56,211,159,.10)' if z in (1, 2) else 'rgba(154,169,188,.05)'))
    for m in (0.2, 0.4, 0.6, 0.8, 1.0):
        o.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#1b2631" stroke-width="1"/>' % (GX0, f(Y(m)), GX1, f(Y(m))))
    for ms in range(1, 12):
        o.append('<line x1="%s" y1="%d" x2="%s" y2="%d" stroke="#1b2631" stroke-width="1"/>' % (f(X(ms)), GY0, f(X(ms)), GY1))
    for m in (0.43, 0.65, 0.87, 1.08):
        o.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#cbd5e1" stroke-opacity=".5" stroke-width="1.5" stroke-dasharray="6 5"/>' % (GX0, f(Y(m)), GX1, f(Y(m))))
    o.append('<rect x="%d" y="%s" width="%d" height="%s" fill="#f4f7fb" fill-opacity=".08"/>' % (GX0, f(Y(0.42)), GX1 - GX0, f(Y(0.36) - Y(0.42))))
    ms_list = [1.3 + 0.5 * i for i in range(21) if 1.3 + 0.5 * i <= 11.4]
    o.append('<polyline points="%s" fill="none" stroke="#ff7a7a" stroke-width="2.5" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(A.gas(m)))) for m in ms_list))
    o.append('<polyline points="%s" fill="none" stroke="#5fe38a" stroke-width="2.5" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(A.gnv(m)))) for m in ms_list))
    ax, ay = X(3.44), Y(0.39)
    o.append('<line x1="%s" y1="%d" x2="%s" y2="%d" stroke="#f4f7fb" stroke-opacity=".6" stroke-width="1.5"/>' % (f(ax), GY0, f(ax), GY1))
    o.append('<path d="M%s %s l8 8 l-8 8 l-8 -8 z" fill="#ff6b6b" stroke="#070a0f" stroke-width="1.5"/>' % (f(ax), f(ay - 8)))
    o.append('<text x="%s" y="%d" fill="#f4f7fb" font-size="14" font-weight="700" stroke="#101720" stroke-width="4" style="paint-order:stroke">Agora</text>' % (f(ax + 7), GY0 + 15))
    for nome, m in (('Zona 1', 0.29), ('Zona 2', 0.54), ('Zona 3', 0.76), ('Zona 4', 0.975)):
        o.append('<text x="%d" y="%s" fill="#dbe4ef" font-size="14" font-weight="700">%s</text>' % (GX1 + 8, f(Y(m) + 5), nome))
    for m in (0.2, 0.4, 0.6, 0.8, 1.0):
        o.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14" text-anchor="end">%s</text>' % (GX0 - 6, f(Y(m) + 5), ('%.2f' % m).replace('.', ',')))
    for ms in range(1, 12, 2):
        o.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">%d</text>' % (f(X(ms)), GY1 + 18, ms))
    o.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">Injeção na gasolina (ms)</text>' % (f((GX0 + GX1) / 2), GY1 + 36))
    return '\n'.join(o)

def acao(on, l1, l2, cor, bg, bd, aria, cursor='pointer'):
    return ('<button type="button" onClick="{{ %s }}" aria-label="%s" style="box-sizing:border-box;min-width:0;height:50px;padding:0 8px;display:flex;flex-direction:column;justify-content:center;align-items:flex-start;gap:0;border-radius:12px;background:%s;border:1px solid %s;text-align:left;cursor:%s;font-family:Inter,Roboto,Arial,sans-serif">'
            '<span style="max-width:100%%;font-size:14px;font-weight:700;line-height:19px;white-space:nowrap;color:%s">%s</span>'
            '<span style="max-width:100%%;font-size:14px;font-weight:500;line-height:17px;white-space:nowrap;color:var(--muted)">%s</span></button>') % (on, aria, bg, bd, cursor, cor, l1, l2)

def main():
    nome = '16-AutoCal-800x480.dc.html'
    P = pontos800()
    script = A.script03(P, CW, CH, GX0, GX1, GY0, GY1)
    for a, b in (("swKnob: lig ? 23 : 3", "swKnob: lig ? 19 : 3"), ("'leve · agora'", "'agora'"), ("'toque fora: desmarca'", "'ou toque fora'"), ("'Arraste para escolher pontos'", "'Arraste para escolher'")):
        assert a in script, a
        script = script.replace(a, b)
    I = lambda p, c='currentColor', w=2, s=20: ico(p, c, w, s)
    html = '''<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>AutoCal 800x480</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
@@HELMET@@
@@ROOT@@
@@TOPBAR@@
<div style="position:absolute;left:12px;top:42px;width:776px;height:44px;display:flex;align-items:center;gap:8px">
<h1 style="margin:0;flex:none;font-size:24px;font-weight:700;letter-spacing:-.01em">AutoCal</h1>
<div style="flex:none;box-sizing:border-box;height:34px;padding:0 10px;display:flex;align-items:center;gap:6px;border-radius:17px;background:{{chipBg}};border:1px solid {{chipBd}};font-size:15px;font-weight:700;white-space:nowrap">
<span style="width:10px;height:10px;border-radius:50%;background:{{chipDot}}"></span>{{ chipText }}
<sc-if value="{{ aprendendo }}" hint-placeholder-val="{{ true }}"><span style="display:flex;gap:3px" aria-hidden="true"><span style="width:9px;height:9px;border-radius:50%;background:var(--accent)"></span><span style="width:9px;height:9px;border-radius:50%;background:var(--accent)"></span><span style="width:9px;height:9px;box-sizing:border-box;border-radius:50%;border:2px solid var(--muted)"></span></span><span style="font-size:14px;font-weight:600;color:var(--muted)">2 de 3</span></sc-if>
</div>
<sc-if value="{{ semAtividade }}" hint-placeholder-val="{{ true }}"><div role="status" aria-live="polite" style="flex:1;min-width:0;box-sizing:border-box;height:38px;padding:0 12px;display:flex;align-items:center;gap:8px;border-radius:12px;background:var(--surface2);border:1px solid var(--line);font-size:14px;font-weight:600;white-space:nowrap"><span style="flex:none;display:flex;color:{{msgFg}}">@@I_MSG@@</span><span>{{ msgText }}</span></div></sc-if>
<sc-if value="{{ atividade }}" hint-placeholder-val="{{ false }}"><div role="status" aria-live="polite" style="flex:1;min-width:0;box-sizing:border-box;height:38px;padding:0 12px;display:flex;align-items:center;gap:8px;border-radius:12px;background:var(--ok-soft);border:1px solid #1d5a46;color:var(--ok);font-size:14px;font-weight:600;white-space:nowrap">@@I_CK@@<span>{{ atividadeTexto }}</span></div></sc-if>
<button type="button" onClick="{{ alternarMenu }}" aria-expanded="{{ ariaMenu }}" aria-label="Mais ações do AutoCal" style="flex:none;width:44px;height:44px;display:flex;align-items:center;justify-content:center;border-radius:12px;border:1px solid {{menuBd}};background:{{menuBg}};color:var(--text);cursor:pointer">@@I_DOTS@@</button>
</div>

<aside aria-label="Estado do aprendizado" style="position:absolute;left:12px;top:92px;width:268px;height:324px;box-sizing:border-box;padding:10px;display:flex;flex-direction:column;gap:8px;background:var(--surface);border:1px solid var(--line);border-radius:14px">
<button type="button" role="switch" aria-checked="{{ ariaLigado }}" onClick="{{ alternarAprendizado }}" style="flex:none;box-sizing:border-box;width:100%;height:50px;padding:0 10px;display:flex;align-items:center;gap:10px;border-radius:12px;background:{{swBg}};border:1px solid {{swBd}};color:var(--text);cursor:pointer;text-align:left;font-family:Inter,Roboto,Arial,sans-serif">
<span style="flex:none;position:relative;width:40px;height:24px;border-radius:12px;background:{{swTrilho}}"><span style="position:absolute;top:3px;left:{{swKnob}}px;width:18px;height:18px;border-radius:50%;background:#fff"></span></span>
<span style="display:flex;flex-direction:column;line-height:1.15"><span style="font-size:16px;font-weight:700">Aprendizado</span><span style="font-size:14px;font-weight:700;color:{{swFg}}">{{ swTexto }}</span></span>
</button>
<div style="display:grid;grid-template-columns:58px 22px minmax(0,1fr) minmax(0,1fr);grid-template-rows:20px repeat(4,48px);gap:6px">
<span></span><span></span>
<span style="display:flex;align-items:center;gap:6px;font-size:14px;font-weight:600;color:var(--muted)"><span style="width:11px;height:11px;border-radius:50%;background:var(--petrol)"></span>Gasolina</span>
<span style="display:flex;align-items:center;gap:6px;font-size:14px;font-weight:600;color:var(--muted)"><span style="width:11px;height:11px;border-radius:3px;background:var(--gas)"></span>GNV</span>
<div style="grid-column:2;grid-row:2 / span 4;display:flex;align-items:center;justify-content:center;writing-mode:vertical-rl;transform:rotate(180deg);font-size:14px;font-weight:800;letter-spacing:.1em;color:var(--danger);white-space:nowrap">TOQUE PARA APAGAR</div>
<sc-for list="{{ zonas }}" as="z" hint-placeholder-count="4">
<div style="display:flex;flex-direction:column;justify-content:center;min-width:0"><b style="font-size:15px;font-weight:700;white-space:nowrap">{{ z.nome }}</b><span style="font-size:14px;font-weight:{{z.agoraPeso}};color:{{z.agoraCor}};white-space:nowrap">{{ z.sub }}</span></div>
<sc-for list="{{ z.celulas }}" as="c" hint-placeholder-count="2">
<button type="button" onClick="{{ c.apagar }}" aria-label="{{ c.aria }}" style="box-sizing:border-box;width:100%;height:48px;padding:0 6px;display:flex;align-items:center;justify-content:center;border-radius:12px;background:{{c.bg}};border:1px solid {{c.bd}};color:{{c.fg}};font:700 15px Inter,Roboto,Arial,sans-serif;white-space:nowrap;cursor:{{c.cursor}}">{{ c.texto }}</button>
</sc-for>
</sc-for>
</div>
</aside>

<section aria-label="Gráfico do aprendizado" style="position:absolute;left:288px;top:92px;width:500px;height:324px;background:var(--surface);border:1px solid var(--line);border-radius:14px;overflow:hidden">
<div style="position:absolute;left:12px;top:6px;height:26px;display:flex;align-items:center;gap:14px;font-size:14px;color:var(--muted);white-space:nowrap">
<span style="display:flex;align-items:center;gap:6px"><span style="width:11px;height:11px;border-radius:50%;background:var(--petrol)"></span>Gasolina</span>
<span style="display:flex;align-items:center;gap:6px"><span style="width:11px;height:11px;border-radius:3px;background:var(--gas)"></span>GNV</span>
<span style="display:flex;align-items:center;gap:6px"><span style="width:13px;height:13px;box-sizing:border-box;border-radius:50%;border:2px dashed var(--warn)"></span>Fora da curva</span>
</div>
<div style="position:absolute;right:12px;top:6px;height:26px;display:flex;align-items:center;gap:6px;font-size:14px;font-weight:600;color:var(--muted);white-space:nowrap;pointer-events:none">@@I_DRAG@@{{ dica }}</div>
<svg width="500" height="324" viewBox="0 0 500 324" style="position:absolute;left:0;top:0" role="img" aria-label="Gráfico: pressão da admissão contra injeção na gasolina, em quatro zonas de uso">
@@BASE@@
</svg>
<sc-for list="{{ pontos }}" as="p" hint-placeholder-count="30">
<span style="position:absolute;left:{{p.l}}px;top:{{p.t}}px;width:{{p.w}}px;height:{{p.w}}px;box-sizing:border-box;border-radius:{{p.r}};background:{{p.bg}};border:1.5px solid #070a0f;box-shadow:{{p.ring}}"></span>
</sc-for>
<sc-for list="{{ aneis }}" as="a" hint-placeholder-count="4">
<span style="position:absolute;left:{{a.l}}px;top:{{a.t}}px;width:28px;height:28px;box-sizing:border-box;border-radius:50%;border:2px dashed var(--warn);pointer-events:none"></span>
</sc-for>
<sc-if value="{{ temCaixa }}" hint-placeholder-val="{{ false }}"><div style="position:absolute;left:{{caixa.l}}px;top:{{caixa.t}}px;width:{{caixa.w}}px;height:{{caixa.h}}px;box-sizing:border-box;border:2px dashed var(--accent);background:rgba(47,140,255,.14);pointer-events:none"></div></sc-if>
<div onPointerDown="{{ baixo }}" onPointerMove="{{ mover }}" onPointerUp="{{ soltar }}" onPointerCancel="{{ cancelar }}" aria-label="Gráfico: arraste para escolher pontos e apagar" style="position:absolute;left:0;top:0;width:500px;height:324px;touch-action:none;cursor:crosshair"></div>
<div style="position:absolute;left:12px;right:12px;top:262px;height:50px;display:grid;grid-template-columns:140px 140px minmax(0,1fr);gap:8px">
@@ACOES@@
</div>
</section>

<sc-if value="{{ menu }}" hint-placeholder-val="{{ false }}">
<div role="menu" aria-label="Mais ações do AutoCal" style="position:absolute;right:12px;top:88px;z-index:5;width:300px;box-sizing:border-box;padding:6px;display:flex;flex-direction:column;gap:4px;background:var(--surface);border:1px solid #2a4a7e;border-radius:14px;box-shadow:0 14px 32px #000a">
<div role="menuitem" aria-disabled="true" style="min-height:50px;box-sizing:border-box;padding:4px 10px;display:flex;align-items:center;gap:10px;border-radius:10px;color:var(--dim)">@@I_REFRESH@@<span style="display:flex;flex-direction:column"><b style="font-size:16px;font-weight:600">AutoMatch manual</b><span style="font-size:14px">libera quando a central terminar (2 de 3)</span></span></div>
<button type="button" role="menuitem" onClick="{{ fecharMenu }}" style="min-height:50px;box-sizing:border-box;padding:4px 10px;display:flex;align-items:center;gap:10px;border:0;border-radius:10px;background:transparent;color:var(--text);font:600 16px Inter,Roboto,Arial,sans-serif;text-align:left;cursor:pointer">@@I_SLIDERS@@Curva K toda em 1,0</button>
<button type="button" role="menuitem" onClick="{{ fecharMenu }}" style="min-height:50px;box-sizing:border-box;padding:4px 10px;display:flex;align-items:center;gap:10px;border:0;border-radius:10px;background:transparent;color:var(--text);font:600 16px Inter,Roboto,Arial,sans-serif;text-align:left;cursor:pointer">@@I_SETTINGS@@Preferências</button>
<button type="button" role="menuitem" onClick="{{ fecharMenu }}" style="min-height:50px;box-sizing:border-box;padding:4px 10px;display:flex;align-items:center;gap:10px;border:0;border-radius:10px;background:transparent;color:var(--danger);font:600 16px Inter,Roboto,Arial,sans-serif;text-align:left;cursor:pointer">@@I_UNDO_R@@Recomeçar tudo</button>
</div>
</sc-if>

@@NAV@@

</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":800,"height":480}}'>
@@SCRIPT@@
</script>
</body>
</html>
'''
    acoes = (acao('limparFora', '{{ acT1 }}', '{{ acS1 }}', '{{ acC1 }}', '{{ foraBg }}', '{{ foraBd }}', 'Apagar pontos', '{{ foraCursor }}')
             + acao('reaprenderGnv', 'Reaprender GNV', 'apaga o gás', '#ff9b9b', 'var(--surface2)', 'var(--line)', 'Reaprender todo o GNV: apaga os pontos do gás')
             + acao('reaprenderGas', 'Reaprender gasolina', 'apaga a gasolina', '#ff9b9b', 'var(--surface2)', 'var(--line)', 'Reaprender toda a gasolina: apaga os pontos da gasolina'))
    rep = {'@@HELMET@@': parte('helmet.txt'), '@@ROOT@@': parte('root800.txt'), '@@TOPBAR@@': topbar800(), '@@NAV@@': parte('nav800-autocal.html'),
           '@@BASE@@': base800(), '@@ACOES@@': acoes, '@@SCRIPT@@': script,
           '@@I_MSG@@': '<sc-if value="{{ msgInfo }}" hint-placeholder-val="{{ true }}">' + I(A.I_INFO, 'currentColor', 2, 20) + '</sc-if><sc-if value="{{ msgWarn }}" hint-placeholder-val="{{ false }}">' + I(A.I_WARN, 'currentColor', 2.2, 20) + '</sc-if>',
           '@@I_CK@@': I(A.I_CHECK, 'currentColor', 2.5, 20), '@@I_DOTS@@': I(A.I_DOTS, 'currentColor', 2, 24), '@@I_DRAG@@': I(A.I_DRAG, 'currentColor', 2, 18),
           '@@I_REFRESH@@': I(A.I_REFRESH, 'currentColor', 2, 22), '@@I_SLIDERS@@': I(A.I_SLIDERS, 'currentColor', 2, 22), '@@I_SETTINGS@@': I(A.I_SETTINGS, 'currentColor', 2, 22), '@@I_UNDO_R@@': I(A.I_UNDO, 'currentColor', 2, 22)}
    for k in ('@@ACOES@@', '@@SCRIPT@@', '@@BASE@@'):
        html = html.replace(k, rep[k])
    for k, v in rep.items():
        html = html.replace(k, v)
    assert '@@' not in html, re.findall(r'@@\w+@@', html)
    open(os.path.join(PROJ, nome), 'w', encoding='utf-8').write(html)
    print('escrito', nome, len(html))

if __name__ == '__main__':
    main()
