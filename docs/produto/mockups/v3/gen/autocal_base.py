# Base estatica do grafico do AutoCal (sem pontos). Painel de quadradinhos (Aquisicao) na DIREITA.
X0, X1, Y0, Y1 = 72, 766, 40, 270
MS0, MS1, MAP0, MAP1 = 1.0, 11.5, 0.15, 1.10
def X(ms): return X0 + (ms-MS0)/(MS1-MS0)*(X1-X0)
def Y(m): return Y1 - (m-MAP0)/(MAP1-MAP0)*(Y1-Y0)
def f(v): return ('%.1f' % v).rstrip('0').rstrip('.')
def gas(ms): return 0.04 + 0.09*ms
import sys
mode = sys.argv[1] if len(sys.argv) > 1 else 'full'   # full | empty
acq = {'z1': True, 'z2': True, 'z3': False, 'z4': False} if mode == 'full' else {'z1': False, 'z2': False, 'z3': False, 'z4': False}
out = []
bands = [(MAP0, 0.43, 'z1'), (0.43, 0.65, 'z2'), (0.65, 0.87, 'z3'), (0.87, 1.08, 'z4')]
for lo, hi, z in bands:
    fill = 'rgba(56,211,159,.10)' if acq[z] else 'rgba(154,169,188,.05)'
    out.append('<rect x="%d" y="%s" width="%d" height="%s" fill="%s"/>' % (X0, f(Y(hi)), X1-X0, f(Y(lo)-Y(hi)), fill))
for m in [0.2,0.4,0.6,0.8,1.0]:
    out.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#1b2631" stroke-width="1"/>' % (X0, f(Y(m)), X1, f(Y(m))))
for ms in range(1, 12):
    out.append('<line x1="%s" y1="%d" x2="%s" y2="%d" stroke="#1b2631" stroke-width="1"/>' % (f(X(ms)), Y0, f(X(ms)), Y1))
for m in [0.43, 0.65, 0.87, 1.08]:
    out.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#cbd5e1" stroke-opacity=".55" stroke-width="1.5" stroke-dasharray="6 5"/>' % (X0, f(Y(m)), X1, f(Y(m))))
if mode == 'full':
    out.append('<rect x="%d" y="%s" width="%d" height="%s" fill="#f4f7fb" fill-opacity=".08"/>' % (X0, f(Y(0.42)), X1-X0, f(Y(0.36)-Y(0.42))))
    ms_list = [1.3+0.5*i for i in range(21) if 1.3+0.5*i <= 11.4]
    out.append('<polyline points="%s" fill="none" stroke="#ff7a7a" stroke-width="3" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(gas(m)))) for m in ms_list))
    out.append('<polyline points="%s" fill="none" stroke="#5fe38a" stroke-width="3" stroke-linejoin="round"/>' % ' '.join('%s,%s' % (f(X(m)), f(Y(gas(m)+0.004+0.004*max(0,m-5)))) for m in ms_list))
    ax, ay = X(3.44), Y(0.39)
    out.append('<line x1="%s" y1="%d" x2="%s" y2="%d" stroke="#f4f7fb" stroke-opacity=".6" stroke-width="1.5"/>' % (f(ax), Y0, f(ax), Y1))
    out.append('<path d="M%s %s l9 9 l-9 9 l-9 -9 z" fill="#ff6b6b" stroke="#070a0f" stroke-width="1.5"/>' % (f(ax), f(ay-9)))
for m in [0.2,0.4,0.6,0.8,1.0]:
    out.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14" text-anchor="end">%s</text>' % (X0-8, f(Y(m)+5), ('%.2f' % m).replace('.', ',')))
for ms in range(1, 12, 2):
    out.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">%d</text>' % (f(X(ms)), Y1+20, ms))
out.append('<text x="%s" y="%d" fill="#9aa9bc" font-size="14" text-anchor="middle">Injeção na gasolina (ms)</text>' % (f((X0+X1)/2), Y1+38))
KY0, KY1 = 322, 384
def KY(k): return KY1 - (k-0.4)/(2.0-0.4)*(KY1-KY0)
out.append('<rect x="%d" y="%d" width="%d" height="%d" rx="4" fill="#0b1017"/>' % (X0, KY0, X1-X0, KY1-KY0))
out.append('<line x1="%d" y1="%s" x2="%d" y2="%s" stroke="#ff6b6b" stroke-width="1.5" stroke-dasharray="6 5"/>' % (X0, f(KY(1.8)), X1, f(KY(1.8))))
if mode == 'full':
    kv = [1.10,1.12,1.15,1.18,1.21,1.23,1.25,1.26,1.27,1.27,1.26,1.25,1.23,1.21,1.19,1.17,1.15,1.14,1.13,1.12,1.11,1.10,1.10,1.09,1.09,1.08,1.08,1.07,1.07,1.06]
    pk = ' '.join('%s,%s' % (f(X0+8+i*(X1-X0-16)/29), f(KY(k))) for i, k in enumerate(kv))
    out.append('<polyline points="%s" fill="none" stroke="#2f8cff" stroke-width="3" stroke-linejoin="round"/>' % pk)
out.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14" text-anchor="end">1,8</text>' % (X0-8, f(KY(1.8)+5)))
out.append('<text x="%d" y="%s" fill="#9aa9bc" font-size="14" text-anchor="end">1,0</text>' % (X0-8, f(KY(1.0)+5)))
# quadradinhos de aquisicao (a direita): 18 faixas
for b in range(18):
    m = 0.20 + b*0.049
    cy = Y(m)
    if mode == 'full':
        if b <= 16:
            out.append('<rect x="780" y="%s" width="14" height="10" rx="2" fill="#ff7a7a"/>' % f(cy-5))
        if b <= 9:
            out.append('<rect x="800" y="%s" width="14" height="10" rx="2" fill="#5fe38a"/>' % f(cy-5))
    else:
        out.append('<rect x="780" y="%s" width="14" height="10" rx="2" fill="none" stroke="#263342" stroke-width="1.5"/>' % f(cy-5))
        out.append('<rect x="800" y="%s" width="14" height="10" rx="2" fill="none" stroke="#263342" stroke-width="1.5"/>' % f(cy-5))
print('\n'.join(out))
print('AGORA_X', f(X(3.44)), 'ZONE_BOTTOMS', [f(Y(m)) for m in [0.43,0.65,0.87,1.08]], 'KY1.8', f(KY(1.8)), 'KY1.0', f(KY(1.0)))
