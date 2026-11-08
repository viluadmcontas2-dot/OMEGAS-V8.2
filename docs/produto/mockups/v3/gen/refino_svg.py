import math
X0, X1, Y0, Y1 = 112, 816, 40, 270
MS0, MS1, MAP0, MAP1 = 1.0, 11.5, 0.15, 1.10
def X(ms): return X0 + (ms-MS0)/(MS1-MS0)*(X1-X0)
def Y(m): return Y1 - (m-MAP0)/(MAP1-MAP0)*(Y1-Y0)
def f(v): return ('%.1f' % v).rstrip('0').rstrip('.')
def gas(ms): return 0.04 + 0.09*ms
out = []
for lo, hi, a in [(MAP0, 0.43, .06), (0.43, 0.65, .06), (0.65, 0.87, .06), (0.87, 1.08, .06)]:
    out.append('<rect x="%d" y="%s" width="%d" height="%s" fill="rgba(56,211,159,%s)"/>' % (X0, f(Y(hi)), X1-X0, f(Y(lo)-Y(hi)), a))
for m in [0.2,0.4,0.6,0.8,1.0]:
    out.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#1b2631" stroke-width="1"/>' % (X0, f(Y(m)), X1, f(Y(m))))
for ms in range(1, 12):
    out.append('<line x1="%s" y1="%d" x2="%s" y2="%d" stroke="#1b2631" stroke-width="1"/>' % (f(X(ms)), Y0, f(X(ms)), Y1))
for m in [0.43, 0.65, 0.87, 1.08]:
    out.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#cbd5e1" stroke-opacity=".5" stroke-width="1.5" stroke-dasharray="6 5"/>' % (X0, f(Y(m)), X1, f(Y(m))))
# regiao do engasgo (reforco no Mapa K)
rx0, rx1, ry0, ry1 = X(1.9), X(3.9), Y(0.50), Y(0.19)
out.append('<rect x="%s" y="%s" width="%s" height="%s" rx="6" fill="#f3ba59" fill-opacity=".10" stroke="#f3ba59" stroke-opacity=".7" stroke-width="1.5" stroke-dasharray="5 4"/>' % (f(rx0), f(ry0), f(rx1-rx0), f(ry1-ry0)))
# gasolina referencia
ms_list = [1.3+0.5*i for i in range(21) if 1.3+0.5*i <= 11.4]
out.append('<polyline points="%s" fill="none" stroke="#ff7a7a" stroke-width="3" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(gas(m)))) for m in ms_list))
# GNV hoje na central (fina)
def today(ms): return gas(ms) + 0.02*(ms-6)/5
out.append('<polyline points="%s" fill="none" stroke="#3f8f5c" stroke-width="2" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(today(m)))) for m in ms_list))
# GNV medido por nos
def meas(ms): return gas(ms) + 0.045*(1-((ms-6)/5.0)**2)
mm = [2.2,2.8,3.4,3.9,4.4,5.0,5.6,6.2,6.8,7.4,8.0,8.6,9.3]
out.append('<polyline points="%s" fill="none" stroke="#5fe38a" stroke-width="3" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(meas(m)))) for m in mm))
for m in mm:
    out.append('<rect x="%s" y="%s" width="10" height="10" rx="2" fill="#5fe38a" stroke="#070a0f" stroke-width="1.5"/>' % (f(X(m)-5), f(Y(meas(m))-5)))
# engasgos: x = apagou ; losango vazado = quase apagou
def xmark(cx, cy):
    return '<path d="M%s %s l12 12 M%s %s l-12 12" stroke="#ff6b6b" stroke-width="3.5" stroke-linecap="round"/>' % (f(cx-6), f(cy-6), f(cx+6), f(cy-6))
def dmark(cx, cy):
    return '<path d="M%s %s l8 8 l-8 8 l-8 -8 z" fill="#0b1017" stroke="#f3ba59" stroke-width="3"/>' % (f(cx), f(cy-8))
for ms, kind in [(2.8,'x'),(3.1,'x'),(2.5,'d'),(2.7,'d'),(3.3,'d'),(3.5,'d')]:
    cx, cy = X(ms), Y(meas(ms)) - (14 if kind=='x' else 0) + (16 if kind=='d' else 0)
    out.append(xmark(cx, cy) if kind=='x' else dmark(cx, cy))
# agora
ax, ay = X(3.44), Y(0.39)
out.append('<line x1="%s" y1="%d" x2="%s" y2="%d" stroke="#f4f7fb" stroke-opacity=".5" stroke-width="1.5"/>' % (f(ax), Y0, f(ax), Y1))
for m in [0.2,0.4,0.6,0.8,1.0]:
    out.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14" text-anchor="end">%s</text>' % (X0-8, f(Y(m)+5), ('%.2f' % m).replace('.', ',')))
for ms in range(1, 12, 2):
    out.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">%d</text>' % (f(X(ms)), Y1+20, ms))
out.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">Injeção na gasolina (ms)</text>' % (f((X0+X1)/2), Y1+38))
# K strip
KY0, KY1 = 322, 384
def KY(k): return KY1 - (k-0.4)/(2.0-0.4)*(KY1-KY0)
out.append('<rect x="%d" y="%d" width="%d" height="%d" rx="4" fill="#0b1017"/>' % (X0, KY0, X1-X0, KY1-KY0))
out.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#ff6b6b" stroke-width="1.5" stroke-dasharray="6 5"/>' % (X0, f(KY(1.8)), X1, f(KY(1.8))))
kv = [1.02,1.03,1.04,1.05,1.055,1.06,1.065,1.07,1.07,1.065,1.06,1.05,1.04,1.03,1.02,1.01,1.0,1.0,0.995,0.99,0.99,0.985,0.985,0.98,0.98,0.98,0.975,0.975,0.97,0.97]
kp = [k + (0.12 if 1 <= i <= 6 else 0.05 if 7 <= i <= 12 else -0.01) for i, k in enumerate(kv)]
def poly(vals): return ' '.join('%s,%s' % (f(X0+8+i*(X1-X0-16)/29), f(KY(k))) for i, k in enumerate(vals))
out.append('<polyline points="%s" fill="none" stroke="#2f8cff" stroke-opacity=".55" stroke-width="2" stroke-linejoin="round"/>' % poly(kv))
out.append('<polyline points="%s" fill="none" stroke="#2f8cff" stroke-width="3" stroke-linejoin="round"/>' % poly(kp))
out.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14" text-anchor="end">1,8</text>' % (X0-8, f(KY(1.8)+5)))
out.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14" text-anchor="end">1,0</text>' % (X0-8, f(KY(1.0)+5)))
print('\n'.join(out))
print('REGION', f(rx0), f(ry0), f(rx1), f(ry1))
