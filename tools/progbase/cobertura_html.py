#!/usr/bin/env python3
"""Gera docs/progbase/cobertura.html: página única e autônoma (sem libs, sem rede) a partir de cobertura.json.

Uso: python3 -B tools/progbase/cobertura_html.py [--check]
Rode antes `tools/progbase/cobertura_re.py`. Determinístico: sem relógio; a data mostrada é `dados_ate`.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / 'docs/progbase/cobertura.json'
OUT = ROOT / 'docs/progbase/cobertura.html'

PAGINA = r'''<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Cobertura da Engenharia Reversa</title>
<style>
:root{--bg:#f6f7f9;--card:#fff;--tx:#14181f;--mut:#5b6573;--bd:#dde1e7;--ok:#1a7f4b;--mid:#b7791f;--bad:#c0392b;--bar:#e6e9ee;--ac:#2057d6}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#0f1318;--card:#171c23;--tx:#e8ebf0;--mut:#9aa4b2;--bd:#283040;--ok:#4cc38a;--mid:#e0b04a;--bad:#ff7a6b;--bar:#262e3a;--ac:#7aa2ff}}
:root[data-theme="dark"]{--bg:#0f1318;--card:#171c23;--tx:#e8ebf0;--mut:#9aa4b2;--bd:#283040;--ok:#4cc38a;--mid:#e0b04a;--bad:#ff7a6b;--bar:#262e3a;--ac:#7aa2ff}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--tx);font:15px/1.45 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
main{max-width:1280px;margin:0 auto;padding:16px}
header{display:flex;flex-wrap:wrap;gap:8px 16px;align-items:center;justify-content:space-between;margin-bottom:12px}
h1{font-size:1.4rem;margin:0}
h2{font-size:1.05rem;margin:24px 0 8px}
.mut{color:var(--mut);font-size:.85rem}
button{background:var(--card);color:var(--tx);border:1px solid var(--bd);border-radius:8px;padding:6px 12px;cursor:pointer;font:inherit}
button:focus-visible{outline:2px solid var(--ac);outline-offset:2px}
.kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:8px}
.kpi,.area{background:var(--card);border:1px solid var(--bd);border-radius:10px;padding:10px 12px}
.kpi b{display:block;font-size:1.4rem}
.areas{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:10px}
.area h3{margin:0 0 6px;font-size:1rem;display:flex;justify-content:space-between;gap:8px}
.bar{height:12px;border-radius:6px;background:var(--bar);overflow:hidden}
.bar i{display:block;height:100%;border-radius:6px}
.row{display:flex;justify-content:space-between;gap:8px;font-size:.82rem;color:var(--mut);margin-top:4px}
.next{font-size:.85rem;margin-top:8px}
.tbl{overflow-x:auto;background:var(--card);border:1px solid var(--bd);border-radius:10px}
table{border-collapse:collapse;width:100%;min-width:560px}
th,td{text-align:left;padding:8px 10px;border-bottom:1px solid var(--bd);vertical-align:top;font-size:.88rem}
th{color:var(--mut);font-weight:600}
tr:last-child td{border-bottom:0}
code{font-size:.8rem}
</style>
</head>
<body>
<main>
<header>
<div><h1>Cobertura da Engenharia Reversa</h1><div class="mut" id="sub"></div></div>
<button id="tema" type="button" aria-label="Alternar tema claro e escuro">Alternar tema</button>
</header>
<section class="kpis" id="kpis" aria-label="Resumo"></section>
<h2>Progresso por área</h2>
<section class="areas" id="areas"></section>
<h2>Maiores DESCONHECIDOS (impacto para o usuário)</h2>
<div class="tbl"><table><thead><tr><th>#</th><th>Área</th><th>Código</th><th>O que falta provar</th></tr></thead><tbody id="desc"></tbody></table></div>
<p class="mut" id="crit"></p>
</main>
<script id="dados" type="application/json">__DADOS__</script>
<script>
(function(){
var D=JSON.parse(document.getElementById('dados').textContent);
function el(t,c,x){var e=document.createElement(t);if(c)e.className=c;if(x!=null)e.textContent=x;return e}
function cor(p){return p>=70?'var(--ok)':p>=35?'var(--mid)':'var(--bad)'}
document.getElementById('sub').textContent='Dados até '+D.dados_ate+' · '+D.total.evidencias+' evidências · '+D.total.pct_provado+'% provado';
var k=document.getElementById('kpis'),T=D.total;
[['Evidências provadas',T.pct_provado+'%'],['Abas inventariadas',T.abas_inventariadas],['Vetores no fio',T.vetores_no_fio+' de '+T.vetores],
 ['Funções sem dono',T.decomp.desconhecida],['Perguntas abertas',T.perguntas_abertas_producao]].forEach(function(x){
 var d=el('div','kpi');d.appendChild(el('b',null,x[1]));d.appendChild(el('span','mut',x[0]));k.appendChild(d)});
var A=document.getElementById('areas');
D.areas.forEach(function(a){
 var d=el('div','area'),h=el('h3');h.appendChild(el('span',null,a.area));h.appendChild(el('span',null,a.pct_cobertura+'%'));d.appendChild(h);
 var b=el('div','bar');b.setAttribute('role','progressbar');b.setAttribute('aria-valuenow',a.pct_cobertura);b.setAttribute('aria-valuemin',0);b.setAttribute('aria-valuemax',100);b.setAttribute('aria-label','Cobertura de '+a.area);
 var i=el('i');i.style.width=a.pct_cobertura+'%';i.style.background=cor(a.pct_cobertura);b.appendChild(i);d.appendChild(b);
 d.appendChild(el('div','row','Provado '+a.pct_provado+'% · '+a.evidencias+' ev.'));
 d.appendChild(el('div','row','Abas c/ vetor '+a.abas.com_vetor_ou_formula+'/'+a.abas.controles_interativos+' · Vetores '+a.vetores.no_fio_com_unidade+'/'+a.vetores.total));
 d.appendChild(el('div','row','App usa '+a.app.evidencias_usadas_no_codigo+'/'+a.evidencias+' · pontes '+a.app.pontes+' · testes '+a.app.testes));
 d.appendChild(el('div','next','Próximo: '+a.proximo_passo));
 A.appendChild(d)});
var B=document.getElementById('desc');
D.desconhecidos_top.forEach(function(x,n){var r=el('tr');
 r.appendChild(el('td',null,n+1));r.appendChild(el('td',null,x.area));
 var c=el('td');c.appendChild(el('code',null,x.id));r.appendChild(c);r.appendChild(el('td',null,x.texto));B.appendChild(r)});
document.getElementById('crit').textContent=D.criterio;
var tb=document.getElementById('tema');
function pega(){try{return localStorage.getItem('tema')}catch(e){return null}}
function poe(v){document.documentElement.setAttribute('data-theme',v);try{localStorage.setItem('tema',v)}catch(e){}}
var t0=pega();if(t0)document.documentElement.setAttribute('data-theme',t0);
tb.addEventListener('click',function(){var cur=document.documentElement.getAttribute('data-theme')||(matchMedia('(prefers-color-scheme:dark)').matches?'dark':'light');poe(cur==='dark'?'light':'dark')});
})();
</script>
</body>
</html>
'''


def gerar():
    dados = json.loads(SRC.read_text(encoding='utf-8'))
    blob = json.dumps(dados, ensure_ascii=False, sort_keys=True, separators=(',', ':')).replace('</', '<\\/')
    return PAGINA.replace('__DADOS__', blob)


def main(argv):
    html = gerar()
    if '--check' in argv:
        if not OUT.exists() or OUT.read_text(encoding='utf-8') != html:
            print('cobertura.html desatualizado. Rode: python3 -B tools/progbase/cobertura_html.py')
            return 1
        print('cobertura.html em dia (%d bytes)' % len(html.encode('utf-8')))
        return 0
    OUT.write_text(html, encoding='utf-8')
    print('escrito %s (%d bytes)' % (OUT.relative_to(ROOT), len(html.encode('utf-8'))))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
