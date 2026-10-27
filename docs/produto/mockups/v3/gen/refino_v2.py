"""Gera 05-Refino.dc.html (versão 2): trilha de 4 passos, gráfico "holofote" e 6 estados.
Uso: python3 -I refino_v2.py  (lê a casca da 05 atual ou da cópia salva em gen/05-casca.html; escreve project/05-Refino.dc.html)
"""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.join(HERE, '..', 'project')
OUT = os.path.join(PROJ, '05-Refino.dc.html')
CASCA = os.path.join(HERE, '05-casca.html')

# casca (raiz, barra do topo, navegação): guardada na 1ª execução, reaproveitada nas seguintes
if not os.path.exists(CASCA):
    shutil_src = open(OUT, encoding='utf-8').read()
    open(CASCA, 'w', encoding='utf-8').write(shutil_src)
old = open(CASCA, encoding='utf-8').read()
i = old.index('<div style="--bg:'); j = old.index('>', i) + 1
ROOT_OPEN = old[i:j]
a = old.index('<div style="position:absolute;left:0;top:0;width:1280px;height:56px')
b = old.index('<div style="position:absolute;left:32px;top:64px')
TOPBAR = old[a:b].rstrip() + '\n'
n0 = old.index('<nav aria-label="Áreas do aplicativo"'); n1 = old.index('</nav>') + 6
NAV = old[n0:n1]
HELMET = old[old.index('<helmet>'):old.index('</helmet>') + 9]

# ---------- geometria do gráfico (coordenadas dentro do SVG, que fica em left:0;top:40 do cartão) ----------
X0, X1, Y0, Y1 = 70, 800, 8, 206
MS0, MS1, MAP0, MAP1 = 1.0, 11.5, 0.15, 1.10
def X(ms): return X0 + (ms - MS0) / (MS1 - MS0) * (X1 - X0)
def Y(m): return Y1 - (m - MAP0) / (MAP1 - MAP0) * (Y1 - Y0)
def f(v): return ('%.1f' % v).rstrip('0').rstrip('.')
def gas(ms): return 0.04 + 0.09 * ms
def meas(ms): return gas(ms) + 0.045 * (1 - ((ms - 6) / 5.0) ** 2)
def today(ms): return gas(ms) + 0.02 * (ms - 6) / 5

svg = []
for lo, hi in [(MAP0, 0.43), (0.43, 0.65), (0.65, 0.87), (0.87, 1.08)]:
    svg.append('<rect x="%d" y="%s" width="%d" height="%s" fill="rgba(56,211,159,.05)"/>' % (X0, f(Y(hi)), X1 - X0, f(Y(lo) - Y(hi))))
for m in (0.2, 0.4, 0.6, 0.8, 1.0):
    svg.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#1b2631" stroke-width="1"/>' % (X0, f(Y(m)), X1, f(Y(m))))
for ms in range(1, 12):
    svg.append('<line x1="%s" y1="%d" x2="%s" y2="%d" stroke="#1b2631" stroke-width="1"/>' % (f(X(ms)), Y0, f(X(ms)), Y1))
for m in (0.43, 0.65, 0.87, 1.08):
    svg.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#cbd5e1" stroke-opacity=".28" stroke-width="1.5" stroke-dasharray="6 5"/>' % (X0, f(Y(m)), X1, f(Y(m))))
for name, m in (('Zona 1 · leve', 0.29), ('Zona 2 · normal', 0.54), ('Zona 3 · firme', 0.76), ('Zona 4 · a fundo', 0.975)):
    svg.append('<text x="%d" y="%s" fill="#b6c3d4" font-size="14" font-weight="600" text-anchor="end" stroke="#0f1923" stroke-width="4" style="paint-order:stroke" stroke-linejoin="round">%s</text>' % (X1 - 8, f(Y(m) + 5), name))
for m in (0.2, 0.4, 0.6, 0.8, 1.0):
    svg.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14" text-anchor="end">%s</text>' % (X0 - 8, f(Y(m) + 5), ('%.2f' % m).replace('.', ',')))
for ms in range(1, 12, 2):
    svg.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">%d</text>' % (f(X(ms)), Y1 + 20, ms))
svg.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">Injeção na gasolina (ms)</text>' % (f((X0 + X1) / 2), Y1 + 40))
ms_list = [1.3 + 0.5 * i for i in range(21) if 1.3 + 0.5 * i <= 11.4]
mm = [2.2, 2.8, 3.4, 3.9, 4.4, 5.0, 5.6, 6.2, 6.8, 7.4, 8.0, 8.6, 9.3]
dots = []
dots.append('<polyline points="%s" fill="none" stroke="#ff7a7a" stroke-width="3" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(gas(m)))) for m in ms_list))
dots.append('<polyline points="%s" fill="none" stroke="#3f8f5c" stroke-width="2" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(today(m)))) for m in ms_list))
dots.append('<polyline points="%s" fill="none" stroke="#5fe38a" stroke-width="3" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(meas(m)))) for m in mm))
for m in mm:
    dots.append('<rect x="%s" y="%s" width="10" height="10" rx="2" fill="#5fe38a" stroke="#070a0f" stroke-width="1.5"/>' % (f(X(m) - 5), f(Y(meas(m)) - 5)))
for m in (2.0, 3.0, 4.2, 5.3, 6.6, 7.7):
    dots.append('<circle cx="%s" cy="%s" r="5" fill="#ff7a7a" stroke="#070a0f" stroke-width="1.5"/>' % (f(X(m)), f(Y(gas(m)))))
BASE_SVG = '\n'.join(svg)
DOTS_SVG = '\n'.join(dots)

# linha "GNV depois": só muda dentro da região
def shifted(ms):
    d = 0.12 * max(0.0, min(1.0, (ms - 1.9) / 0.4, (3.9 - ms) / 0.4))
    return X(ms + d), Y(meas(ms))
seg = [1.5 + 0.1 * k for k in range(0, 33)]
AFTER = ' '.join('%s,%s' % (f(shifted(m)[0]), f(shifted(m)[1])) for m in seg)
BEFORE = ' '.join('%s,%s' % (f(X(m)), f(Y(meas(m)))) for m in seg)

# região do engasgo
RX0, RX1, RY0, RY1 = X(1.9), X(3.9), Y(0.50), Y(0.19)
box = dict(left=RX0, top=40 + RY0, w=RX1 - RX0, h=RY1 - RY0)

# marcadores (posição no cartão = SVG + 40 em y)
def mk(ms, kind):
    cy = Y(meas(ms)) - (14 if kind == 'x' else 0) + (16 if kind == 'd' else 0)
    return dict(l=round(X(ms) - 9), t=round(40 + cy - 9), kind=kind)
M_OK = [mk(2.8, 'x'), mk(3.1, 'x'), mk(2.5, 'd'), mk(2.7, 'd'), mk(3.3, 'd'), mk(3.5, 'd')]
M_PIOR = M_OK + [mk(2.4, 'x'), mk(3.6, 'x'), mk(2.9, 'd')]
M_MELHOR = [mk(3.3, 'd')]
import json
J = lambda o: json.dumps(o, ensure_ascii=False)

# faixa da Curva K (dentro do cartão: top 304, altura 66)
KX0, KX1, KT, KB = X0, X1, 304, 370
def KY(k): return KB - (k - 0.4) / (2.0 - 0.4) * (KB - KT)
kv = [1.02, 1.03, 1.04, 1.05, 1.055, 1.06, 1.065, 1.07, 1.07, 1.065, 1.06, 1.05, 1.04, 1.03, 1.02, 1.01, 1.0, 1.0, 0.995, 0.99, 0.99, 0.985, 0.985, 0.98, 0.98, 0.98, 0.975, 0.975, 0.97, 0.97]
kp = [k + (0.12 if 1 <= i <= 6 else 0.05 if 7 <= i <= 12 else -0.01) for i, k in enumerate(kv)]
def kpoly(vals): return ' '.join('%s,%s' % (f(KX0 + 8 + i * (KX1 - KX0 - 16) / 29), f(KY(k) - KT)) for i, k in enumerate(vals))
KSTRIP = ('<rect x="%d" y="0" width="%d" height="%d" rx="4" fill="#0b1017"/>' % (KX0, KX1 - KX0, KB - KT)
          + '<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#ff6b6b" stroke-opacity=".8" stroke-width="1.5" stroke-dasharray="6 5"/>' % (KX0, f(KY(1.8) - KT), KX1, f(KY(1.8) - KT))
          + '<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#cbd5e1" stroke-opacity=".35" stroke-width="1" stroke-dasharray="2 4"/>' % (KX0, f(KY(1.0) - KT), KX1, f(KY(1.0) - KT))
          + '<polyline points="%s" fill="none" stroke="#2f8cff" stroke-opacity=".5" stroke-width="2.5" stroke-linejoin="round"/>' % kpoly(kv))
KNEW = '<polyline points="%s" fill="none" stroke="#2f8cff" stroke-width="3.5" stroke-linejoin="round"/>' % kpoly(kp)

# ---------- ícones ----------
def ico(path, color='currentColor', w=2, size=24):
    return '<svg width="%d" height="%d" viewBox="0 0 24 24" fill="none" stroke="%s" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">%s</svg>' % (size, size, color, w, path)
CHECK = '<path d="M20 6 9 17l-5-5"/>'
I_INFO = '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>'
I_WARN = '<path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><path d="M12 9v4M12 17h.01"/>'
I_UNDO = '<path d="M1 4v6h6M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>'
I_HELP = '<circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3M12 17h.01"/>'
I_PULSE = '<path d="M22 12h-4l-3 9L9 3l-3 9H2"/>'

# ---------- modelo ----------
TEMPLATE = r'''<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>Refino</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
@@HELMET@@
@@ROOT_OPEN@@

@@TOPBAR@@
<div style="position:absolute;left:32px;top:64px;width:1216px;height:56px;display:flex;align-items:center;gap:18px">
<h1 style="margin:0;flex:none;font-size:30px;font-weight:700;letter-spacing:-.01em">Refino</h1>
<ol aria-label="Passos do Refino" style="margin:0;padding:0;list-style:none;flex:none;display:flex;align-items:center;gap:8px">
<sc-for list="{{ passos }}" as="p" hint-placeholder-count="4">
<li style="display:flex;align-items:center;gap:8px">
<sc-if value="{{ p.temLinha }}" hint-placeholder-val="{{ false }}"><span style="width:22px;height:3px;border-radius:2px;background:{{p.linha}}"></span></sc-if>
<span style="flex:none;width:30px;height:30px;box-sizing:border-box;display:flex;align-items:center;justify-content:center;border-radius:50%;background:{{p.bg}};border:2px solid {{p.bd}};font-size:16px;font-weight:800;color:{{p.fgNum}}">
<sc-if value="{{ p.feito }}" hint-placeholder-val="{{ false }}"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#04150e" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg></sc-if>
<sc-if value="{{ p.naoFeito }}" hint-placeholder-val="{{ true }}">{{ p.n }}</sc-if>
</span>
<span style="font-size:18px;font-weight:{{p.peso}};color:{{p.fg}}">{{ p.nome }}</span>
</li>
</sc-for>
</ol>
<div style="flex:1;min-width:0;box-sizing:border-box;height:48px;padding:0 16px;display:flex;align-items:center;gap:10px;border-radius:12px;background:{{msgBg}};border:1px solid {{msgBd}};font-size:17px;font-weight:600;white-space:nowrap">
<span style="flex:none;display:flex;color:{{msgFg}}"><sc-if value="{{ msgI }}" hint-placeholder-val="{{ true }}">@@I_MSG@@</sc-if><sc-if value="{{ msgOk }}" hint-placeholder-val="{{ false }}">@@I_CHECK_OK@@</sc-if><sc-if value="{{ msgBad }}" hint-placeholder-val="{{ false }}">@@I_WARN_MSG@@</sc-if></span>
<span style="overflow:hidden;text-overflow:ellipsis">{{ msgText }}</span>
</div>
</div>

<aside aria-label="O que o Refino achou" style="position:absolute;left:32px;top:128px;width:370px;height:396px;box-sizing:border-box;padding:14px 16px;background:var(--surface);border:1px solid var(--line);border-radius:14px;overflow:hidden">

<sc-if value="{{ vAjuda }}" hint-placeholder-val="{{ false }}">
<div style="font-size:14px;font-weight:700;letter-spacing:.12em;color:var(--dim)">AJUDA · REFINO</div>
<div style="margin-top:12px;display:flex;flex-direction:column;gap:14px">
<div><div style="font-size:15px;font-weight:700;color:var(--accent)">O que é</div><div style="font-size:18px;line-height:1.35">O app confere onde o carro engasga e monta a correção.</div></div>
<div><div style="font-size:15px;font-weight:700;color:var(--accent)">O que fazer</div><div style="font-size:18px;line-height:1.35">Dirija normalmente. Quando a correção estiver pronta, confira e toque em Gravar.</div></div>
<div><div style="font-size:15px;font-weight:700;color:var(--accent)">O que acontece</div><div style="font-size:18px;line-height:1.35">Grava, a central confere e o app mede se melhorou. Você pode desfazer.</div></div>
</div>
</sc-if>

<sc-if value="{{ vEsperando }}" hint-placeholder-val="{{ false }}">
<div style="font-size:14px;font-weight:700;letter-spacing:.12em;color:var(--dim)">A CENTRAL ESTÁ APRENDENDO</div>
<div style="margin-top:12px;display:flex;align-items:center;gap:14px"><b style="font-size:46px;line-height:1;font-weight:800;font-variant-numeric:tabular-nums">2 de 3</b><span style="display:flex;gap:6px" aria-hidden="true"><span style="width:14px;height:14px;border-radius:50%;background:var(--accent)"></span><span style="width:14px;height:14px;border-radius:50%;background:var(--accent)"></span><span style="width:14px;height:14px;box-sizing:border-box;border-radius:50%;border:2px solid var(--muted)"></span></span></div>
<div style="margin-top:6px;font-size:17px;color:var(--muted)">ajustes da central prontos</div>
<div style="margin-top:20px;font-size:19px;line-height:1.35;font-weight:600">O Refino começa sozinho quando ela terminar. Você não precisa fazer nada.</div>
<div style="margin-top:20px;display:flex;gap:10px;align-items:flex-start;font-size:16px;line-height:1.35;color:var(--muted)"><span style="flex:none;margin-top:1px;display:flex">@@I_WARN_SM@@</span>Dá para refinar antes, por sua conta. Mas mexer agora pode atrapalhar o aprendizado.</div>
</sc-if>

<sc-if value="{{ vSem }}" hint-placeholder-val="{{ false }}">
<div style="font-size:14px;font-weight:700;letter-spacing:.12em;color:var(--dim)">O APP ESTÁ OLHANDO</div>
<div style="margin-top:8px;font-size:44px;line-height:1.05;font-weight:800;color:var(--ok)">0 engasgos</div>
<div style="margin-top:6px;font-size:17px;line-height:1.3;color:var(--muted)">em 1.420 leituras desde que a central terminou.</div>
<div style="margin-top:16px;display:flex;align-items:baseline;gap:8px;font-size:14px;font-weight:700;letter-spacing:.12em;color:var(--dim)">FAIXAS MEDIDAS <b style="font-size:16px;letter-spacing:0;color:var(--text)">3 de 5</b></div>
<div style="margin-top:8px;display:flex;gap:6px">
<sc-for list="{{ faixas }}" as="f" hint-placeholder-count="5">
<div style="flex:1;min-width:0;height:48px;box-sizing:border-box;border-radius:8px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:1px;background:{{f.bg}};border:1px solid {{f.bd}};color:{{f.fg}};line-height:1.1"><b style="font-size:14px;font-weight:700;white-space:nowrap">{{ f.nome }}</b><span style="font-size:14px;font-weight:600">{{ f.estado }}</span></div>
</sc-for>
</div>
<div style="margin-top:12px;font-size:17px;line-height:1.35;font-weight:600">Para o app propor, acelere mais firme no GNV: injeção de 7,5 a 12 ms.</div>
</sc-if>

<sc-if value="{{ vProposta }}" hint-placeholder-val="{{ true }}">
<div style="font-size:14px;font-weight:700;letter-spacing:.12em;color:var(--dim)">ACHAMOS</div>
<div style="margin-top:6px;display:flex;align-items:baseline;gap:10px"><b style="font-size:40px;line-height:1.05;font-weight:800;color:var(--warn)">6 engasgos</b></div>
<div style="margin-top:4px;font-size:17px;line-height:1.3;color:var(--muted)">na aceleração leve (zona 1). Em 2, o motor apagou.</div>
<div style="margin-top:14px;font-size:14px;font-weight:700;letter-spacing:.12em;color:var(--dim)">VAI MUDAR</div>
<div style="margin-top:8px;display:flex;flex-direction:column;gap:8px">
<sc-for list="{{ mudancas }}" as="c" hint-placeholder-count="2">
<button type="button" onClick="{{ c.alternar }}" aria-pressed="{{ c.aria }}" style="box-sizing:border-box;width:100%;text-align:left;padding:10px 12px;display:flex;align-items:center;gap:12px;border-radius:12px;background:{{c.bg}};border:1px solid {{c.bd}};color:var(--text);font-family:inherit;cursor:pointer">
<span style="flex:none;width:30px;height:30px;box-sizing:border-box;display:flex;align-items:center;justify-content:center;border-radius:8px;background:{{c.boxBg}};border:2px solid {{c.boxBd}}"><sc-if value="{{ c.on }}" hint-placeholder-val="{{ true }}"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg></sc-if></span>
<span style="display:flex;flex-direction:column;gap:1px;min-width:0"><b style="font-size:19px;font-weight:700">{{ c.titulo }}</b><span style="font-size:15px;line-height:1.25;color:var(--muted)">{{ c.efeito }}</span><span style="font-size:14px;font-weight:600;color:var(--dim)">{{ c.tag }}</span></span>
</button>
</sc-for>
</div>
<sc-if value="{{ manual }}" hint-placeholder-val="{{ false }}"><div style="margin-top:8px;display:flex;gap:8px;align-items:center;font-size:14px;color:var(--warn)"><span style="flex:none;display:flex">@@I_WARN_XS@@</span>Refinando por sua conta: a central ainda aprende.</div></sc-if>
</sc-if>

<sc-if value="{{ vMedindo }}" hint-placeholder-val="{{ false }}">
<div style="box-sizing:border-box;padding:12px 14px;border-radius:12px;background:var(--ok-soft);border:1px solid #1d5a46">
<div style="display:flex;align-items:center;gap:10px;font-size:19px;font-weight:700;color:var(--ok)"><span style="flex:none;display:flex">@@I_CHECK_OK@@</span>Gravado e conferido pela central</div>
<div style="margin-top:6px;display:flex;flex-direction:column;gap:2px;font-size:16px;line-height:1.3;color:var(--text)">
<sc-for list="{{ gravadas }}" as="g" hint-placeholder-count="2"><div>{{ g.texto }}</div></sc-for>
</div>
</div>
<div style="margin-top:14px;font-size:14px;font-weight:700;letter-spacing:.12em;color:var(--dim)">MEDINDO</div>
<div style="margin-top:6px;display:flex;align-items:baseline;gap:10px"><b style="font-size:40px;line-height:1.05;font-weight:800;font-variant-numeric:tabular-nums">12 de 100</b><span style="font-size:17px;color:var(--muted)">leituras novas</span></div>
<div style="margin-top:10px;height:10px;border-radius:5px;background:var(--surface3);overflow:hidden" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="12" aria-label="Leituras medidas"><div style="width:12%;height:100%;background:var(--accent)"></div></div>
<div style="margin-top:12px;font-size:17px;line-height:1.35;font-weight:600">Até agora: 2 trechos melhoraram, 0 pioraram.</div>
<div style="margin-top:10px;font-size:15px;line-height:1.35;color:var(--muted)">Dirija normalmente. Os pontos de GNV recomeçam do zero depois de gravar o Mapa K: é normal.</div>
</sc-if>

<sc-if value="{{ vMelhorou }}" hint-placeholder-val="{{ false }}">
<div style="display:flex;align-items:center;gap:10px;font-size:40px;line-height:1.05;font-weight:800;color:var(--ok)"><span style="flex:none;display:flex">@@I_CHECK_OK_BIG@@</span>Melhorou</div>
<div style="margin-top:14px;font-size:16px;font-weight:600;color:var(--muted)">Trechos com o GNV igual à gasolina</div>
<div style="margin-top:0;display:flex;align-items:baseline;gap:10px;font-size:44px;line-height:1.1;font-weight:800;font-variant-numeric:tabular-nums"><span style="color:var(--muted)">2</span><span style="color:var(--muted);font-size:28px">→</span><span style="color:var(--ok)">5</span><span style="font-size:17px;font-weight:600;color:var(--muted)">de 5</span></div>
<div style="margin-top:12px;font-size:16px;font-weight:600;color:var(--muted)">Engasgos na região</div>
<div style="margin-top:0;display:flex;align-items:baseline;gap:10px;font-size:44px;line-height:1.1;font-weight:800;font-variant-numeric:tabular-nums"><span style="color:var(--muted)">6</span><span style="color:var(--muted);font-size:28px">→</span><span style="color:var(--ok)">1</span></div>
<div style="margin-top:12px;font-size:16px;line-height:1.35;color:var(--muted)">Medido em 100 leituras. O app segue vigiando e propõe de novo se achar outro engasgo.</div>
</sc-if>

<sc-if value="{{ vPiorou }}" hint-placeholder-val="{{ false }}">
<div style="display:flex;align-items:center;gap:10px;font-size:40px;line-height:1.05;font-weight:800;color:var(--danger)"><span style="flex:none;display:flex">@@I_WARN_BIG@@</span>Piorou</div>
<div style="margin-top:14px;font-size:16px;font-weight:600;color:var(--muted)">Pontos da Curva K que pioraram</div>
<div style="margin-top:0;display:flex;align-items:baseline;gap:10px;font-size:44px;line-height:1.1;font-weight:800;font-variant-numeric:tabular-nums"><span style="color:var(--danger)">2</span><span style="font-size:17px;font-weight:600;color:var(--muted)">de 12</span></div>
<div style="margin-top:12px;font-size:16px;font-weight:600;color:var(--muted)">Engasgos na região</div>
<div style="margin-top:0;display:flex;align-items:baseline;gap:10px;font-size:44px;line-height:1.1;font-weight:800;font-variant-numeric:tabular-nums"><span style="color:var(--muted)">6</span><span style="color:var(--muted);font-size:28px">→</span><span style="color:var(--danger)">9</span></div>
<div style="margin-top:12px;font-size:16px;line-height:1.35;color:var(--muted)">Restaurar volta só os 2 pontos que pioraram. Desfazer tudo volta as duas mudanças.</div>
</sc-if>

</aside>

<section aria-label="Onde o carro engasga" style="position:absolute;left:418px;top:128px;width:830px;height:396px;background:var(--surface);border:1px solid var(--line);border-radius:14px;overflow:hidden">

<div style="position:absolute;left:16px;top:10px;height:36px;display:flex;align-items:center;gap:18px;font-size:15px;color:var(--muted);white-space:nowrap">
<sc-if value="{{ legGas }}" hint-placeholder-val="{{ false }}">
<span style="display:flex;align-items:center;gap:7px"><span style="width:12px;height:12px;border-radius:50%;background:var(--petrol)"></span>Gasolina</span>
<span style="display:flex;align-items:center;gap:7px"><span style="width:12px;height:12px;border-radius:3px;background:var(--gas)"></span>GNV</span>
</sc-if>
<sc-if value="{{ legEng }}" hint-placeholder-val="{{ true }}">
<span style="display:flex;align-items:center;gap:7px"><svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M3 3l10 10M13 3 3 13" stroke="#ff6b6b" stroke-width="3" stroke-linecap="round"/></svg>Apagou</span>
<span style="display:flex;align-items:center;gap:7px"><svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M8 1.5 14.5 8 8 14.5 1.5 8z" fill="#0b1017" stroke="#f3ba59" stroke-width="2.5"/></svg>Quase apagou</span>
<span style="display:flex;align-items:center;gap:7px"><span style="width:18px;height:12px;box-sizing:border-box;border-radius:3px;border:2px dashed {{boxCor}}"></span>{{ legBox }}</span>
</sc-if>
</div>

<sc-if value="{{ temAD }}" hint-placeholder-val="{{ true }}">
<div role="group" aria-label="Ver o gráfico" style="position:absolute;right:16px;top:6px;display:flex;gap:6px">
<button type="button" onClick="{{ verAntes }}" aria-pressed="{{ ariaAntes }}" style="box-sizing:border-box;height:40px;padding:0 18px;border-radius:10px;border:1px solid {{adBdA}};background:{{adBgA}};color:{{adFgA}};font:700 16px Inter,Roboto,Arial,sans-serif;cursor:pointer">Antes</button>
<button type="button" onClick="{{ verDepois }}" aria-pressed="{{ ariaDepois }}" style="box-sizing:border-box;height:40px;padding:0 18px;border-radius:10px;border:1px solid {{adBdD}};background:{{adBgD}};color:{{adFgD}};font:700 16px Inter,Roboto,Arial,sans-serif;cursor:pointer">Depois</button>
</div>
</sc-if>

<svg width="830" height="270" viewBox="0 0 830 270" style="position:absolute;left:0;top:40px" role="img" aria-label="Gráfico: pressão da admissão contra injeção na gasolina, com as quatro zonas de uso">
@@BASE_SVG@@
<g opacity="{{dotsOp}}">
@@DOTS_SVG@@
</g>
</svg>

<sc-if value="{{ showAntes }}" hint-placeholder-val="{{ false }}"><svg width="830" height="270" viewBox="0 0 830 270" style="position:absolute;left:0;top:40px" aria-hidden="true"><polyline points="@@BEFORE@@" fill="none" stroke="#9fe6b8" stroke-width="4" stroke-linejoin="round" stroke-linecap="round"/></svg></sc-if>
<sc-if value="{{ showDepois }}" hint-placeholder-val="{{ false }}"><svg width="830" height="270" viewBox="0 0 830 270" style="position:absolute;left:0;top:40px" aria-hidden="true"><polyline points="@@AFTER@@" fill="none" stroke="#5fe38a" stroke-width="4.5" stroke-linejoin="round" stroke-linecap="round"/></svg></sc-if>

<sc-if value="{{ temBox }}" hint-placeholder-val="{{ true }}">
<div style="position:absolute;left:@@BOX_L@@px;top:@@BOX_T@@px;width:@@BOX_W@@px;height:@@BOX_H@@px;box-sizing:border-box;border-radius:8px;border:2px {{boxEstilo}} {{boxCor}};background:{{boxFundo}}"></div>
<div style="position:absolute;left:@@BOX_L@@px;top:@@BOX_LABEL_T@@px;height:28px;box-sizing:border-box;padding:0 10px;display:flex;align-items:center;border-radius:8px;background:{{boxCor}};color:#14110a;font-size:15px;font-weight:800;white-space:nowrap">{{ boxRotulo }}</div>
</sc-if>

<sc-for list="{{ marcas }}" as="m" hint-placeholder-count="6">
<span style="position:absolute;left:{{m.l}}px;top:{{m.t}}px;width:18px;height:18px;display:block;opacity:{{m.op}}">
<sc-if value="{{ m.x }}" hint-placeholder-val="{{ false }}"><svg width="18" height="18" viewBox="0 0 16 16" aria-hidden="true"><path d="M3 3l10 10M13 3 3 13" stroke="#ff6b6b" stroke-width="3.2" stroke-linecap="round"/></svg></sc-if>
<sc-if value="{{ m.d }}" hint-placeholder-val="{{ false }}"><svg width="18" height="18" viewBox="0 0 16 16" aria-hidden="true"><path d="M8 1.5 14.5 8 8 14.5 1.5 8z" fill="#0b1017" stroke="#f3ba59" stroke-width="2.5"/></svg></sc-if>
</span>
</sc-for>

<sc-if value="{{ temChip }}" hint-placeholder-val="{{ true }}">
<div style="position:absolute;left:84px;top:52px;height:40px;box-sizing:border-box;padding:0 14px;display:flex;align-items:center;gap:10px;border-radius:10px;background:{{chipBg}};border:1px solid {{chipBd}};font-size:18px;font-weight:700;color:{{chipFg}};white-space:nowrap">
<sc-if value="{{ chipOk }}" hint-placeholder-val="{{ false }}"><span style="flex:none;display:flex">@@I_CHECK_OK_SM@@</span></sc-if>
<sc-if value="{{ chipWarn }}" hint-placeholder-val="{{ false }}"><span style="flex:none;display:flex">@@I_WARN_SM_C@@</span></sc-if>
{{ chipTexto }}
</div>
</sc-if>

<sc-if value="{{ vEsperando }}" hint-placeholder-val="{{ false }}">
<div style="position:absolute;left:210px;top:96px;width:420px;height:120px;box-sizing:border-box;padding:0 24px;display:flex;align-items:center;gap:18px;border-radius:14px;background:rgba(11,17,23,.94);border:1px solid var(--line)">
<span style="flex:none;width:56px;height:56px;display:flex;align-items:center;justify-content:center;border-radius:50%;background:var(--accent-soft);border:1px solid #2a4a7e;color:var(--accent)">@@I_PULSE_BIG@@</span>
<div style="display:flex;flex-direction:column;gap:3px"><b style="font-size:24px;font-weight:700">Esperando a central</b><span style="font-size:17px;line-height:1.3;color:var(--muted)">Os engasgos aparecem aqui quando ela terminar.</span></div>
</div>
</sc-if>

<div style="position:absolute;left:10px;top:304px;width:58px;height:66px;display:flex;align-items:center;font-size:14px;font-weight:700;white-space:nowrap;color:var(--accent)">Curva K</div>
<svg width="@@KW@@" height="66" viewBox="0 0 @@KW@@ 66" style="position:absolute;left:0;top:304px" aria-hidden="true">
<g transform="translate(0,0)">@@KSTRIP@@</g>
<sc-if value="{{ temKNova }}" hint-placeholder-val="{{ true }}">@@KNEW@@</sc-if>
<text x="796" y="26" fill="#ff9b9b" font-size="14" font-weight="600" text-anchor="end">limite 1,8</text>
<text x="796" y="60" fill="#9aa9bc" font-size="14" text-anchor="end">{{ kLegenda }}</text>
</svg>
</section>

<div style="position:absolute;left:32px;top:538px;width:1216px;height:70px;display:flex;align-items:center;gap:12px">
<sc-if value="{{ bGravar }}" hint-placeholder-val="{{ true }}">
<button type="button" onClick="{{ gravar }}" style="flex:none;height:58px;padding:0 28px;display:flex;align-items:center;gap:10px;border:0;border-radius:12px;background:{{gravarBg}};color:{{gravarFg}};font:700 20px Inter,Roboto,Arial,sans-serif;cursor:pointer"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>{{ gravarTexto }}</button>
</sc-if>
<sc-if value="{{ bManual }}" hint-placeholder-val="{{ false }}">
<button type="button" onClick="{{ porMinhaConta }}" style="flex:none;height:58px;padding:0 24px;display:flex;align-items:center;border:1px solid var(--line);border-radius:12px;background:var(--surface2);color:var(--text);font:600 20px Inter,Roboto,Arial,sans-serif;cursor:pointer">Refinar por minha conta</button>
</sc-if>
<sc-if value="{{ bRestaurar }}" hint-placeholder-val="{{ false }}">
<button type="button" onClick="{{ restaurar }}" style="flex:none;height:58px;padding:0 28px;display:flex;align-items:center;gap:10px;border:0;border-radius:12px;background:#1f6fd8;color:#fff;font:700 20px Inter,Roboto,Arial,sans-serif;cursor:pointer"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M1 4v6h6M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>Restaurar 2 pontos</button>
</sc-if>
<sc-if value="{{ bDesfazer }}" hint-placeholder-val="{{ false }}">
<button type="button" onClick="{{ desfazer }}" style="flex:none;height:58px;padding:0 24px;display:flex;align-items:center;gap:10px;border:{{desfBd}};border-radius:12px;background:{{desfBg}};color:{{desfFg}};font:700 20px Inter,Roboto,Arial,sans-serif;cursor:pointer"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M1 4v6h6M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>{{ desfTexto }}</button>
</sc-if>
<button type="button" onClick="{{ alternarAjuda }}" aria-label="Ajuda do Refino" aria-pressed="{{ ariaAjuda }}" style="flex:none;width:58px;height:58px;display:flex;align-items:center;justify-content:center;border:1px solid {{ajudaBd}};border-radius:12px;background:{{ajudaBg}};color:var(--text);cursor:pointer">@@I_HELP@@</button>
<span style="flex:1"></span>
<span style="flex:none;font-size:14px;color:var(--dim);white-space:nowrap">Prévia:</span>
<div role="group" aria-label="Estados da prévia" style="flex:none;display:flex;gap:6px">
<sc-for list="{{ estados }}" as="e" hint-placeholder-count="6">
<button type="button" onClick="{{ e.ir }}" aria-pressed="{{ e.aria }}" style="box-sizing:border-box;height:44px;padding:0 9px;border-radius:10px;border:1px solid {{e.bd}};background:{{e.bg}};color:{{e.fg}};font:600 14px Inter,Roboto,Arial,sans-serif;cursor:pointer;white-space:nowrap">{{ e.nome }}</button>
</sc-for>
</div>
</div>

@@NAV@@

</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":1280,"height":720}}'>
class Component extends DCLogic {
  state = { fase: 'proposta', mapa: true, curva: true, ver: 'depois', ajuda: false, aviso: '', manual: false, gravadas: null };
  renderVals() {
    const st = Object.assign({ fase: 'proposta', mapa: true, curva: true, ver: 'depois', ajuda: false, aviso: '', manual: false, gravadas: null }, this.state || {});
    const fase = st.fase;
    const ok = '#38d39f', warn = '#f3ba59', danger = '#ff6b6b', info = '#2f8cff';
    const n = (st.mapa ? 1 : 0) + (st.curva ? 1 : 0);
    const ir = (f, extra) => () => this.setState(Object.assign({ fase: f, aviso: '', ajuda: false }, extra || {}));

    // trilha de 4 passos
    const nomes = ['Achar', 'Conferir', 'Gravar', 'Medir'];
    const agora = { esperando: 0, sem: 0, proposta: 1, medindo: 3, melhorou: 4, piorou: 4 }[fase];
    const passos = nomes.map((nome, i) => {
      const ruim = fase === 'piorou' && i === 3;
      const feito = i < agora && !ruim, atual = i === agora;
      return { nome, n: ruim ? '!' : i + 1, feito, naoFeito: !feito, temLinha: i > 0,
        linha: i <= agora ? (ruim ? danger : ok) : '#263342',
        bg: ruim ? danger : feito ? ok : atual ? '#14284a' : 'transparent',
        bd: ruim ? danger : feito ? ok : atual ? info : '#8c9bae',
        fgNum: ruim ? '#2a0b0b' : atual ? '#f4f7fb' : '#9aa9bc',
        fg: feito ? '#f4f7fb' : (atual || ruim) ? '#f4f7fb' : '#9aa9bc',
        peso: (atual || ruim) ? 800 : 600 };
    });

    // mensagem do cabeçalho
    const msgs = {
      esperando: ['A central ainda aprende. O Refino começa sozinho.', 'i'],
      sem: ['Nenhum engasgo até agora. Continue dirigindo.', 'ok'],
      proposta: [st.aviso || 'Achamos onde o carro engasga. Confira e grave.', st.aviso ? 'ok' : 'i'],
      medindo: ['Gravado e conferido. Agora o app mede se melhorou.', 'ok'],
      melhorou: ['Melhorou: GNV igual à gasolina em 5 de 5 trechos.', 'ok'],
      piorou: ['Piorou: 2 pontos pioraram. Restaure só esses pontos.', 'bad']
    };
    const [msgText, kind] = msgs[fase];
    const tone = { i: ['#151e29', '#263342', info], ok: ['#0f2b23', '#1d5a46', ok], bad: ['#2e1618', '#7a3036', danger] }[kind];

    // mudanças propostas
    const mud = (id, titulo, efeito, tag) => {
      const on = !!st[id];
      return { titulo, efeito, tag, on, aria: on ? 'true' : 'false',
        bg: on ? '#14284a' : '#151e29', bd: on ? '#2a4a7e' : '#263342',
        boxBg: on ? '#1f6fd8' : 'transparent', boxBd: on ? '#1f6fd8' : '#8c9bae',
        alternar: () => this.setState({ [id]: !on }) };
    };
    const mudancas = [
      mud('mapa', 'Reforço na região', 'Mais gás onde o carro engasgou.', 'Mapa K · 6 células'),
      mud('curva', 'Ajuste geral', 'Gás igual à gasolina em toda a faixa.', 'Curva K · 12 pontos')
    ];
    const grav = st.gravadas || { mapa: st.mapa, curva: st.curva };
    const gravadas = [];
    if (grav.mapa) gravadas.push({ texto: 'Reforço na região · 6 células do Mapa K' });
    if (grav.curva) gravadas.push({ texto: 'Ajuste geral · 12 pontos da Curva K' });

    // marcas no gráfico
    const OK = @@M_OK@@, PIOR = @@M_PIOR@@, MELHOR = @@M_MELHOR@@;
    const baseMarcas = { proposta: OK, medindo: OK, melhorou: OK, piorou: PIOR }[fase] || [];
    const marcas = baseMarcas.map(m => ({ l: m.l, t: m.t, x: m.kind === 'x', d: m.kind === 'd',
      op: (fase === 'medindo' || fase === 'melhorou') ? 0.35 : 1 }));
    if (fase === 'melhorou') marcas.push({ l: MELHOR[0].l, t: MELHOR[0].t, x: false, d: true, op: 1 });

    // caixa da região
    const caixa = {
      proposta: ['#f3ba59', 'dashed', 'rgba(243,186,89,.10)', 'Aqui o carro engasga'],
      medindo: ['#38d39f', 'solid', 'rgba(56,211,159,.10)', 'Reforçado: +5 % de gás'],
      melhorou: ['#38d39f', 'solid', 'rgba(56,211,159,.10)', 'Reforçado: +5 % de gás'],
      piorou: ['#ff6b6b', 'dashed', 'rgba(255,107,107,.10)', 'Piorou aqui']
    }[fase];
    const verDepois = st.ver !== 'antes';

    // chip de resultado dentro do gráfico
    const chips = {
      sem: ['Tudo liso: 0 engasgos', ok, '#0f2b23', '#1d5a46', true, false],
      proposta: ['+5 % de gás nas 6 células', warn, '#2d240f', '#6b5622', false, false],
      medindo: ['Engasgos desde que gravou: 0', ok, '#0f2b23', '#1d5a46', true, false],
      melhorou: ['1 engasgo depois da correção', ok, '#0f2b23', '#1d5a46', true, false],
      piorou: ['9 engasgos depois da correção', danger, '#2e1618', '#7a3036', false, true]
    }[fase];

    const fx = [['3–4,5', true], ['4,5–6', true], ['6–7,5', true], ['7,5–9', false], ['9–12', false]];
    const faixas = fx.map(([nome, ok2]) => ({ nome, estado: ok2 ? 'ok' : 'falta', bg: ok2 ? '#0f2b23' : '#151e29', bd: ok2 ? '#1d5a46' : '#3a4a5c', fg: ok2 ? '#38d39f' : '#9aa9bc' }));
    const botoes = {
      esperando: { manual: true },
      sem: {},
      proposta: { gravar: true },
      medindo: { desfazer: true },
      melhorou: { desfazer: true },
      piorou: { desfazer: true, restaurar: true }
    }[fase];
    const temAjuda = !!st.ajuda;
    const estadosPrev = [['esperando', 'Esperando'], ['sem', 'Sem engasgos'], ['proposta', 'Proposta'], ['medindo', 'Medindo'], ['melhorou', 'Melhorou'], ['piorou', 'Piorou']];
    const esconderPanel = temAjuda;

    return {
      passos, msgText, msgBg: tone[0], msgBd: tone[1], msgFg: tone[2], msgI: kind === 'i', msgOk: kind === 'ok', msgBad: kind === 'bad',
      faixas, mudancas, gravadas, manual: !!st.manual && fase === 'proposta',
      vAjuda: temAjuda, ariaAjuda: temAjuda ? 'true' : 'false',
      vEsperando: !esconderPanel && fase === 'esperando',
      vSem: !esconderPanel && fase === 'sem',
      vProposta: !esconderPanel && fase === 'proposta',
      vMedindo: !esconderPanel && fase === 'medindo',
      vMelhorou: !esconderPanel && fase === 'melhorou',
      vPiorou: !esconderPanel && fase === 'piorou',
      legGas: fase === 'esperando' || fase === 'sem', legEng: !(fase === 'esperando' || fase === 'sem'),
      legBox: fase === 'proposta' ? 'Onde vai o reforço' : fase === 'piorou' ? 'Região' : 'Região reforçada',
      boxCor: caixa ? caixa[0] : '#f3ba59',
      boxEstilo: caixa ? caixa[1] : 'dashed', boxFundo: caixa ? caixa[2] : 'transparent', boxRotulo: caixa ? caixa[3] : '',
      temBox: !!caixa,
      temAD: fase === 'proposta', ariaAntes: !verDepois ? 'true' : 'false', ariaDepois: verDepois ? 'true' : 'false',
      adBgA: !verDepois ? '#14284a' : '#151e29', adBdA: !verDepois ? '#2a4a7e' : '#263342', adFgA: !verDepois ? '#f4f7fb' : '#9aa9bc',
      adBgD: verDepois ? '#14284a' : '#151e29', adBdD: verDepois ? '#2a4a7e' : '#263342', adFgD: verDepois ? '#f4f7fb' : '#9aa9bc',
      verAntes: () => this.setState({ ver: 'antes' }), verDepois: () => this.setState({ ver: 'depois' }),
      dotsOp: fase === 'sem' ? 1 : fase === 'esperando' ? 0.3 : 0.25,
      showAntes: fase === 'proposta' && !verDepois, showDepois: fase === 'proposta' && verDepois,
      marcas,
      temChip: !!chips, chipTexto: chips ? chips[0] : '', chipFg: chips ? chips[1] : '', chipBg: chips ? chips[2] : '', chipBd: chips ? chips[3] : '',
      chipOk: chips ? chips[4] : false, chipWarn: chips ? chips[5] : false,
      temKNova: (fase === 'proposta' && st.curva) || ((fase === 'medindo' || fase === 'melhorou' || fase === 'piorou') && grav.curva),
      kLegenda: ((fase === 'proposta' && st.curva) || ((fase === 'medindo' || fase === 'melhorou' || fase === 'piorou') && grav.curva)) ? 'claro = hoje · forte = depois' : 'a curva fica como está',
      bGravar: !!botoes.gravar, bManual: !!botoes.manual, bDesfazer: !!botoes.desfazer, bRestaurar: !!botoes.restaurar,
      gravarTexto: n === 2 ? 'Gravar 2 mudanças' : n === 1 ? 'Gravar 1 mudança' : 'Escolha o que gravar',
      gravarBg: n > 0 ? '#1f6fd8' : '#1b2633', gravarFg: n > 0 ? '#fff' : '#9aa9bc',
      gravar: () => { if (n > 0) this.setState({ fase: 'medindo', gravadas: { mapa: st.mapa, curva: st.curva }, aviso: '', ajuda: false }); },
      porMinhaConta: () => this.setState({ fase: 'proposta', manual: true, aviso: '', ajuda: false }),
      desfazer: () => this.setState({ fase: 'proposta', manual: false, ver: 'depois', aviso: 'Desfeito: os valores anteriores voltaram.', ajuda: false }),
      desfTexto: botoes.restaurar ? 'Desfazer tudo' : 'Desfazer',
      desfBg: '#151e29', desfFg: '#f4f7fb', desfBd: '1px solid #263342',
      restaurar: () => this.setState({ fase: 'proposta', manual: false, ver: 'depois', aviso: 'Restaurado: os 2 pontos voltaram ao valor anterior.', ajuda: false }),
      alternarAjuda: () => this.setState({ ajuda: !temAjuda }),
      ajudaBg: temAjuda ? '#14284a' : '#151e29', ajudaBd: temAjuda ? '#2a4a7e' : '#263342',
      estados: estadosPrev.map(([id, nome]) => ({ nome, aria: id === fase ? 'true' : 'false',
        bg: id === fase ? '#14284a' : 'transparent', bd: id === fase ? '#2a4a7e' : '#263342', fg: id === fase ? '#f4f7fb' : '#9aa9bc',
        ir: ir(id, id === 'proposta' ? { manual: false } : {}) }))
    };
  }
}
</script>
</body>
</html>
'''

html = TEMPLATE
rep = {
    '@@HELMET@@': HELMET, '@@ROOT_OPEN@@': ROOT_OPEN, '@@TOPBAR@@': TOPBAR, '@@NAV@@': NAV,
    '@@BASE_SVG@@': BASE_SVG, '@@DOTS_SVG@@': DOTS_SVG, '@@BEFORE@@': BEFORE, '@@AFTER@@': AFTER,
    '@@BOX_L@@': f(box['left']), '@@BOX_T@@': f(box['top']), '@@BOX_W@@': f(box['w']), '@@BOX_H@@': f(box['h']),
    '@@BOX_LABEL_T@@': f(box['top'] - 32),
    '@@KW@@': '830', '@@KSTRIP@@': KSTRIP, '@@KNEW@@': KNEW,
    '@@M_OK@@': J(M_OK), '@@M_PIOR@@': J(M_PIOR), '@@M_MELHOR@@': J(M_MELHOR),
    '@@I_MSG@@': ico(I_INFO, 'currentColor', 2, 24), '@@I_WARN_MSG@@': ico(I_WARN, 'currentColor', 2.2, 24),
    '@@I_WARN_SM@@': ico(I_WARN, '#f3ba59', 2, 22), '@@I_WARN_XS@@': ico(I_WARN, '#f3ba59', 2, 18),
    '@@I_CHECK_OK@@': ico(CHECK, '#38d39f', 3, 24), '@@I_CHECK_OK_BIG@@': ico(CHECK, '#38d39f', 3.5, 40), '@@I_CHECK_OK_SM@@': ico(CHECK, '#38d39f', 3, 22),
    '@@I_WARN_BIG@@': ico(I_WARN, '#ff6b6b', 2.2, 40), '@@I_WARN_SM_C@@': ico(I_WARN, '#ff6b6b', 2.2, 22),
    '@@I_PULSE_BIG@@': ico(I_PULSE, 'currentColor', 2, 30), '@@I_HELP@@': ico(I_HELP, 'currentColor', 2, 26),
}
for k, v in rep.items():
    html = html.replace(k, v)
assert '@@' not in html, re.findall(r'@@\w+@@', html)
open(OUT, 'w', encoding='utf-8').write(html)
print('escrito', OUT, len(html))
