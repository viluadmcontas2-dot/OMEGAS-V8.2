import math
BASE = [1.10,1.12,1.15,1.18,1.21,1.23,1.25,1.26,1.27,1.27,1.26,1.25,1.23,1.21,1.19,1.17,1.15,1.14,1.13,1.12,1.11,1.10,1.10,1.09,1.09,1.08,1.08,1.07,1.07,1.06]
AFTER = [round(math.sqrt(k),3) for k in BASE]
def f(v): return ('%.1f' % v).rstrip('0').rstrip('.')
def Y(k): return 270 - 452*(k-0.9)
def X(i): return 52 + i*(344-16)/29
pb = ' '.join('%s,%s' % (f(X(i)), f(Y(k))) for i,k in enumerate(BASE))
pa = ' '.join('%s,%s' % (f(X(i)), f(Y(k))) for i,k in enumerate(AFTER))
print('BEFORE', pb)
print('AFTER', pa)
print('max after', max(AFTER))
print('Y1.0', f(Y(1.0)), 'Y1.1', f(Y(1.1)), 'Y1.2', f(Y(1.2)), 'Y1.3', f(Y(1.3)), 'Y1.4', f(Y(1.4)), 'Y0.9', f(Y(0.9)))
