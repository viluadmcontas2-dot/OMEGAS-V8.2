import math
X0, X1, Y0, Y1 = 112, 816, 40, 270
MS0, MS1, MAP0, MAP1 = 1.0, 11.5, 0.15, 1.10
def X(ms): return X0 + (ms-MS0)/(MS1-MS0)*(X1-X0)
def Y(m): return Y1 - (m-MAP0)/(MAP1-MAP0)*(Y1-Y0)
def f(v): return ('%.1f' % v).rstrip('0').rstrip('.')
def gas(ms): return 0.04 + 0.09*ms
def jit(i, a=0.011): return (((i*37) % 13) - 6)/6*a
lim = [0.43, 0.65, 0.87, 1.08]
out = []
# zonas: Z1 bottom ... Z4 top
bands = [(MAP0, 0.43, 'z1'), (0.43, 0.65, 'z2'), (0.65, 0.87, 'z3'), (0.87, 1.08, 'z4')]
acq = {'z1': True, 'z2': True, 'z3': False, 'z4': False}
for lo, hi, z in bands:
    y_top, y_bot = Y(hi), Y(lo)
    fill = 'rgba(56,211,159,.10)' if acq[z] else 'rgba(154,169,188,.05)'
    out.append('<rect x="%d" y="%s" width="%d" height="%s" fill="%s"/>' % (X0, f(y_top), X1-X0, f(y_bot-y_top), fill))
# grid
for m in [0.2,0.4,0.6,0.8,1.0]:
    out.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#1b2631" stroke-width="1"/>' % (X0, f(Y(m)), X1, f(Y(m))))
for ms in range(1, 12):
    out.append('<line x1="%s" y1="%d" x2="%s" y2="%d" stroke="#1b2631" stroke-width="1"/>' % (f(X(ms)), Y0, f(X(ms)), Y1))
# limites
for m in lim:
    out.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#cbd5e1" stroke-opacity=".55" stroke-width="1.5" stroke-dasharray="6 5"/>' % (X0, f(Y(m)), X1, f(Y(m))))
# faixa atual
out.append('<rect x="%d" y="%s" width="%d" height="%s" fill="#f4f7fb" fill-opacity=".08"/>' % (X0, f(Y(0.42)), X1-X0, f(Y(0.36)-Y(0.42))))
# curvas
pg = ' '.join('%s,%s' % (f(X(ms)), f(Y(gas(ms)))) for ms in [1.3+0.5*i for i in range(21)] if ms <= 11.4)
pn = ' '.join('%s,%s' % (f(X(ms)), f(Y(gas(ms)+0.004+0.004*max(0,ms-5)))) for ms in [1.3+0.5*i for i in range(21)] if ms <= 11.4)
out.append('<polyline points="%s" fill="none" stroke="#ff7a7a" stroke-width="3" stroke-linejoin="round"/>' % pg)
out.append('<polyline points="%s" fill="none" stroke="#5fe38a" stroke-width="3" stroke-linejoin="round"/>' % pn)
# pontos gasolina (circulos)
gm = [1.7,2.1,2.6,3.0,3.5,3.9,4.4,4.9,5.3,5.8,6.3,6.8,7.3,7.8,8.3,8.8,9.4,9.9,10.4]
for i, ms in enumerate(gm):
    out.append('<circle cx="%s" cy="%s" r="5.5" fill="#ff7a7a" stroke="#070a0f" stroke-width="1.5"/>' % (f(X(ms)), f(Y(gas(ms)+jit(i)))))
# pontos GNV (quadrados)
nm = [1.8,2.3,2.8,3.3,3.8,4.3,4.8,5.3,5.8,6.3,7.2,7.8]
for i, ms in enumerate(nm):
    cy = Y(gas(ms)+0.004+0.004*max(0,ms-5)+jit(i+5))
    cx = X(ms)
    out.append('<rect x="%s" y="%s" width="10" height="10" rx="2" fill="#5fe38a" stroke="#070a0f" stroke-width="1.5"/>' % (f(cx-5), f(cy-5)))
# agora
ax, ay = X(3.44), Y(0.39)
out.append('<line x1="%s" y1="%d" x2="%s" y2="%d" stroke="#f4f7fb" stroke-opacity=".6" stroke-width="1.5"/>' % (f(ax), Y0, f(ax), Y1))
out.append('<path d="M%s %s l9 9 l-9 9 l-9 -9 z" fill="#ff6b6b" stroke="#070a0f" stroke-width="1.5"/>' % (f(ax), f(ay-9)))
# eixo y rotulos
for m in [0.2,0.4,0.6,0.8,1.0]:
    out.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14" text-anchor="end">%s</text>' % (X0-8, f(Y(m)+5), ('%.2f' % m).replace('.', ',')))
# eixo x rotulos
for ms in range(1, 12, 2):
    out.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">%d</text>' % (f(X(ms)), Y1+20, ms))
out.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">Injeção na gasolina (ms)</text>' % (f((X0+X1)/2), Y1+38))
# K strip
KY0, KY1 = 322, 384  # top/bottom
def KY(k): return KY1 - (k-0.4)/(2.0-0.4)*(KY1-KY0)
out.append('<rect x="%d" y="%d" width="%d" height="%d" rx="4" fill="#0b1017"/>' % (X0, KY0, X1-X0, KY1-KY0))
out.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#ff6b6b" stroke-width="1.5" stroke-dasharray="6 5"/>' % (X0, f(KY(1.8)), X1, f(KY(1.8))))
kv = [1.02,1.03,1.04,1.05,1.055,1.06,1.065,1.07,1.07,1.065,1.06,1.05,1.04,1.03,1.02,1.01,1.0,1.0,0.995,0.99,0.99,0.985,0.985,0.98,0.98,0.98,0.975,0.975,0.97,0.97]
pk = ' '.join('%s,%s' % (f(X0+8+i*(X1-X0-16)/29), f(KY(k))) for i, k in enumerate(kv))
out.append('<polyline points="%s" fill="none" stroke="#2f8cff" stroke-width="3" stroke-linejoin="round"/>' % pk)
out.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14" text-anchor="end">1,8</text>' % (X0-8, f(KY(1.8)+5)))
out.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14" text-anchor="end">1,0</text>' % (X0-8, f(KY(1.0)+5)))
# acquisition stacks (18 bandas)
for b in range(18):
    m = 0.20 + b*0.049
    cy = Y(m)
    if b <= 16:
        out.append('<rect x="18" y="%s" width="14" height="10" rx="2" fill="#ff7a7a"/>' % f(cy-5))
    if b <= 9:
        out.append('<rect x="40" y="%s" width="14" height="10" rx="2" fill="#5fe38a"/>' % f(cy-5))
print('\n'.join(out))
