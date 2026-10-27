import re, sys, colorsys
from html.parser import HTMLParser
VOID = {'meta','link','br','hr','img','input','source','wbr'}
class P(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack=[]; self.errs=[]; self.text=[]; self.loopvars=set(); self.in_script=False
    def handle_starttag(self, tag, attrs):
        d=dict(attrs)
        if tag=='script': self.in_script=True
        if tag=='sc-for' and 'as' in d: self.loopvars.add(d['as'])
        if tag in VOID: return
        self.stack.append((tag,self.getpos()))
    def handle_startendtag(self, tag, attrs):
        d=dict(attrs)
        if tag=='sc-for' and 'as' in d: self.loopvars.add(d['as'])
    def handle_endtag(self, tag):
        if tag=='script': self.in_script=False
        if tag in VOID: return
        if not self.stack: self.errs.append('fecha </%s> sem abrir (linha %d)'%(tag,self.getpos()[0])); return
        t,pos=self.stack.pop()
        if t!=tag:
            self.errs.append('esperava </%s> (aberto na linha %d) e veio </%s> na linha %d'%(t,pos[0],tag,self.getpos()[0]))
            # tenta recuperar
            while self.stack and t!=tag:
                t,pos=self.stack.pop()
    def handle_data(self, data):
        if not self.in_script and data.strip(): self.text.append(data.strip())
def lint(path):
    src=open(path,encoding='utf-8').read()
    p=P(); p.feed(src); p.close()
    errs=list(p.errs)
    for t,pos in p.stack: errs.append('sem fechar <%s> (linha %d)'%(t,pos[0]))
    if '<script src="./support.js"></script>' not in src: errs.append('falta a linha do support.js')
    if '<x-dc>' not in src or '</x-dc>' not in src: errs.append('falta x-dc')
    m=re.search(r'<script type="text/x-dc"[^>]*>(.*?)</script>',src,re.S)
    script=m.group(1) if m else ''
    if not m: errs.append('falta script x-dc')
    template=src.split('<x-dc>')[1].split('</x-dc>')[0] if '<x-dc>' in src else ''
    holes=re.findall(r'\{\{(.*?)\}\}',template,re.S)
    bad=set(); missing=set()
    for h in holes:
        h2=h.strip()
        if h2 in ('true','false','null'): continue
        if not re.fullmatch(r'[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*',h2):
            bad.add(h2); continue
        root=h2.split('.')[0]
        if root in p.loopvars or root=='$index': continue
        if not re.search(r'\b'+re.escape(root)+r'\b',script): missing.add(root)
    for b in sorted(bad): errs.append('expressão/ilegal em {{ }}: %r'%b)
    for mi in sorted(missing): errs.append('{{ %s }} não existe no script'%mi)
    # handlers
    for hname in re.findall(r'on[A-Z]\w+="\{\{\s*([^}]+?)\s*\}\}"',template):
        root=hname.split('.')[0]
        if root in p.loopvars: continue
        if not re.search(r'\b'+re.escape(root)+r'\b',script): errs.append('handler %s não existe no script'%hname)
    # hex hue (roxo)
    for hx in set(re.findall(r'#([0-9a-fA-F]{6})\b',src)):
        r,g,b=[int(hx[i:i+2],16)/255 for i in (0,2,4)]
        h,l,s=colorsys.rgb_to_hls(r,g,b)
        if s>0.18 and 238<=h*360<=335 and 0.08<l<0.92: errs.append('cor roxa/violeta #%s (matiz %d)'%(hx,h*360))
    # fontes pequenas (o selo numérico da aba Refino em 800×480 é um número dentro de um círculo: 12 px é aceito)
    src_f = re.sub(r'<span[^>]*background:var\(--warn\);color:#1b1303;font-size:12px[^>]*>\d+</span>', '', src)
    for fs in set(re.findall(r'font-size:\s*(\d+(?:\.\d+)?)px',src_f)):
        if float(fs)<14: errs.append('font-size %spx < 14'%fs)
    for fs in set(re.findall(r'font:[^;"\']*?(\d+(?:\.\d+)?)px',src_f)):
        if float(fs)<14: errs.append('font: %spx < 14'%fs)
    # vocabulário
    vis=' '.join(p.text)
    for pat,label in [(r'\bECU\b','ECU'),(r'readback','readback'),(r'\bACK\b','ACK'),(r'\bNAK\b','NAK'),(r'\bciclos?\b','ciclo'),(r'\bflags?\b','flag'),(r'\bbits?\b','bit'),(r'\b0x[0-9a-f]+','hex'),(r'\bAvançado\b','Avançado'),(r'^Detalhes$','Detalhes')]:
        if re.search(pat,vis): errs.append('vocabulário proibido na tela: %s'%label)
    if 'emoji' in src.lower(): pass
    if re.search('[\U0001F300-\U0001FAFF☀-⛿]',vis): errs.append('emoji no texto')
    return errs,len(holes)
if __name__=='__main__':
    ok=True
    for f in sys.argv[1:]:
        e,n=lint(f)
        print(('OK   ' if not e else 'ERRO ')+f.split('/')[-1]+'  (%d buracos)'%n)
        for x in e: print('     - '+x); ok=False
