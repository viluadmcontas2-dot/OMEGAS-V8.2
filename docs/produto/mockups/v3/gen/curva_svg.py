PX0, PX1 = 82, 806
Y1, SCALE = 324, 290/1.4
def KY(k): return Y1 - (k-0.5)*SCALE
def f(v): return ('%.1f' % v).rstrip('0').rstrip('.')
out = []
out.append('<defs><pattern id="hatchK" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><line x1="0" y1="0" x2="0" y2="8" stroke="#ff6b6b" stroke-opacity=".35" stroke-width="2"/></pattern></defs>')
# area proibida
out.append('<rect x="70" y="34" width="746" height="%s" fill="url(#hatchK)"/>' % f(KY(1.8)-34))
out.append('<rect x="70" y="%s" width="746" height="%s" fill="url(#hatchK)"/>' % (f(KY(0.6)), f(324-KY(0.6))))
out.append('<rect x="70" y="%s" width="746" height="%s" fill="rgba(56,211,159,.05)"/>' % (f(KY(1.8)), f(KY(0.6)-KY(1.8))))
for k in [0.8,1.2,1.4,1.6]:
    out.append('<line x1="70" y1="%s" x2="816" y2="%s" stroke="#1b2631" stroke-width="1"/>' % (f(KY(k)), f(KY(k))))
out.append('<line x1="70" y1="%s" x2="816" y2="%s" stroke="#f4f7fb" stroke-opacity=".55" stroke-width="1.5" stroke-dasharray="3 5"/>' % (f(KY(1.0)), f(KY(1.0))))
out.append('<line x1="70" y1="%s" x2="816" y2="%s" stroke="#ff6b6b" stroke-width="2" stroke-dasharray="7 5"/>' % (f(KY(1.8)), f(KY(1.8))))
out.append('<line x1="70" y1="%s" x2="816" y2="%s" stroke="#ff6b6b" stroke-opacity=".6" stroke-width="1.5" stroke-dasharray="7 5"/>' % (f(KY(0.6)), f(KY(0.6))))
for k, lab, col, wt in [(1.8,'1,8','#ff8a8a',700),(1.6,'1,6','#9aa9bc',400),(1.4,'1,4','#9aa9bc',400),(1.2,'1,2','#9aa9bc',400),(1.0,'1,0','#f4f7fb',700),(0.8,'0,8','#9aa9bc',400),(0.6,'0,6','#ff8a8a',700)]:
    out.append('<text x="62" y="%s" fill="%s" font-size="14" font-weight="%d" text-anchor="end">%s</text>' % (f(KY(k)+5), col, wt, lab))
labs = [(0,'1,5'),(5,'3,3'),(10,'5,0'),(15,'6,8'),(20,'8,5'),(25,'10,3'),(29,'11,6')]
step = (PX1-PX0)/29
for i, t in labs:
    x = PX0 + i*step
    out.append('<line x1="%s" y1="324" x2="%s" y2="329" stroke="#9aa9bc" stroke-width="1.5"/>' % (f(x), f(x)))
    out.append('<text x="%s" y="346" fill="#9aa9bc" font-size="14" text-anchor="middle">%s</text>' % (f(x), t))
out.append('<text x="443" y="368" fill="#9aa9bc" font-size="14" text-anchor="middle">Injeção (ms)</text>')
# cursor agora: indice 5.6
cx = PX0 + 5.6*step
out.append('<line x1="%s" y1="34" x2="%s" y2="324" stroke="#f4f7fb" stroke-opacity=".6" stroke-width="1.5"/>' % (f(cx), f(cx)))
print('\n'.join(out))
print('CURSOR', f(cx), 'STEP', f(step), 'KY1.0', f(KY(1.0)), 'KY1.8', f(KY(1.8)), 'KY0.6', f(KY(0.6)))
