"""Gera somente o mockup 21 e seus cenários; dados sintéticos, sem ponte/API."""
import json
from pathlib import Path

BASE = Path(__file__).resolve().parent
reference = (BASE / '../project/09-Ferramentas.dc.html').read_text()
# Mesma casca visual de Ferramentas, sem importar sua lógica ou seus controles.
start = reference[reference.index('<div style="--bg:'):reference.index('<sc-if')]
nav = reference[reference.index('<nav aria-label='):reference.index('</nav>') + 6]
body = r'''
<div class="head"><a class="button" href="09-Ferramentas.dc.html">‹ Voltar</a><div><h1>Memória</h1><p>Mais › Histórico · exemplos simulados</p></div><button onClick="{{ alternar }}">{{ modo }}</button></div>
<div class="seal" role="status">{{ selo }}</div>
<section class="history card"><h2>Últimas gravações</h2><p>{{ orientacao }}</p>
<sc-for list="{{ registros }}" as="r">
<article class="record"><div class="recordhead"><b>{{ r.quem }}</b><span>{{ r.alvo }}</span></div><p>{{ r.regiao }}</p><div class="change"><strong>{{ r.mudanca }}</strong><button aria-label="{{ r.rotulo }}" disabled="{{ r.bloqueado }}" onClick="{{ r.desfazer }}">Desfazer</button></div><small>{{ r.resultado }}</small></article>
</sc-for>
<button class="copy" disabled="{{ vazio }}" onClick="{{ copiar }}">Copiar resumo para o assistente</button><small role="status">{{ copia }}</small>
</section>
<section class="coherence card"><div class="sectionhead"><h2>Coerência</h2><span class="tag">Só orienta</span></div>
<div class="notice"><div><b>{{ aviso1 }}</b><p>{{ espera }}</p><small>1.500–2.000 giros × carga leve</small></div><svg width="180" height="86" viewBox="0 0 180 86" role="img" aria-label="Região de carga leve entre 1.500 e 2.000 giros destacada; Refino sobe e AutoCal desce"><rect x="55" y="4" width="62" height="58" rx="8" fill="#2d240f"></rect><path d="M6 61H174 M8 10V61" stroke="#465568" fill="none"></path><path d="M8 50L52 45L85 17L120 30L172 23" stroke="#2f8cff" stroke-width="3" fill="none"></path><path d="M8 50L52 45L85 43L120 35L172 23" stroke="#f3ba59" stroke-width="3" fill="none"></path><text x="44" y="80" fill="#9aa9bc" font-size="14">1.500–2.000</text></svg></div>
<div class="legend">Azul: Refino · Amarelo: AutoCal · faixa em destaque</div>
<div class="actions"><button disabled="{{ vazio }}" onClick="{{ decidir }}">Deixar o AutoCal decidir</button><button disabled="{{ vazio }}" onClick="{{ liberar }}">Liberar este trecho</button></div>
<div class="other"><b>{{ aviso2 }}</b><small>2.000–2.500 giros × carga média</small></div>
<div class="other"><b>{{ aviso3 }}</b><small>900–1.200 giros × carga leve</small></div>
</section>
<section class="learning card"><h2>O que o app aprendeu</h2><div class="learnrow"><span class="dot good"></span><div><b>1.500–2.000 giros · carga leve</b><p>{{ aprendizado1 }}</p></div></div><div class="learnrow"><span class="dot pending"></span><div><b>2.000–2.500 giros · carga média</b><p>{{ aprendizado2 }}</p></div></div></section>
'''
css = '''body{margin:0} *{box-sizing:border-box} h1,h2,p{margin:0} h1{font-size:28px} h2{font-size:21px;line-height:28px} p{font-size:16px;line-height:23px;color:var(--muted)} small{display:block;font-size:14px;line-height:20px;color:var(--muted)} button,.button{height:44px;border:1px solid var(--line);border-radius:10px;background:var(--surface2);color:var(--text);font:600 16px Inter,Arial,sans-serif;padding:0 14px;display:inline-flex;align-items:center;justify-content:center;text-decoration:none;cursor:pointer} button:disabled{color:var(--muted);cursor:default;background:var(--surface)} .head{position:absolute;left:24px;top:64px;width:1232px;height:56px;display:flex;align-items:center;gap:18px}.head>button{margin-left:auto}.seal{position:absolute;right:16px;top:16px;color:var(--ok);font-size:14px}.card{position:absolute;padding:16px;background:var(--surface);border:1px solid var(--line);border-radius:16px}.history{left:24px;top:128px;width:502px;height:496px}.record{height:110px;border-bottom:1px solid var(--line);padding:3px 0}.recordhead{display:flex;justify-content:space-between;font-size:16px;line-height:22px}.recordhead span{color:var(--muted)}.change{display:flex;align-items:center;justify-content:space-between;height:44px}.change strong{font-size:21px}.record p{line-height:20px}.record small{line-height:18px}.copy{width:100%;margin-top:10px;margin-bottom:3px;background:transparent}.coherence{left:542px;top:128px;width:714px;height:326px}.sectionhead{display:flex;justify-content:space-between;align-items:center}.tag{color:var(--ok);font-size:14px}.notice{display:flex;gap:16px;margin-top:10px;height:74px}.notice>div{flex:1}.notice b{font-size:17px;line-height:22px;display:block}.notice svg{flex:none;height:74px}.legend{font-size:14px;color:var(--muted);line-height:22px}.actions{display:flex;gap:12px;margin:3px 0 4px}.actions button:first-child{background:var(--accent-soft);border-color:#2a4a7e}.other{border-top:1px solid var(--line);padding:4px 0 0;margin-top:3px}.other b{font-size:16px;line-height:21px;font-weight:500}.learning{left:542px;top:466px;width:714px;height:158px}.learnrow{display:flex;align-items:center;gap:12px;margin-top:8px}.learnrow b{font-size:16px;line-height:21px}.learnrow p{font-size:14px;line-height:20px}.dot{width:10px;height:32px;border-radius:5px;flex:none}.good{background:var(--ok)}.pending{background:var(--warn)}'''
script = r'''
class Component extends DCLogic {
 state = {vazio:false,desfeitos:[],politica:'esperar',copia:'Nada sai do app até você colar.',selo:'Histórico conferido'};
 renderVals(){
  const s=this.state;
  const dados=[['AutoCal','Curva K','1.500–2.000 giros × carga leve','1,08 → 1,04','1,04 → 1,08'],['Refino · você aplicou','Mapa K','2.000–2.500 giros × carga média','200 → 210','210 → 200'],['Você','Curva K','900–1.200 giros × carga leve','1,02 → 1,00','1,00 → 1,02']];
  const registros=dados.map((d,i)=>{const feito=s.desfeitos.includes(i);return {quem:s.vazio?'Sem gravação':d[0],alvo:s.vazio?'—':d[1],regiao:s.vazio?'Conecte a central e faça seu primeiro ajuste.':d[2],mudanca:s.vazio?'— → —':feito?d[4]:d[3],feito,bloqueado:s.vazio||feito,rotulo:'Desfazer gravação '+(i+1),resultado:s.vazio?'Nenhuma mudança para desfazer':feito?'Desfeito e conferido · simulação':'Conferido · '+[260,420,610][i]+' leituras atrás',desfazer:()=>this.setState({desfeitos:[...s.desfeitos,i],selo:'Gravado e conferido · simulação'})};});
  const resumo='Memória — EXEMPLOS SIMULADOS\n'+registros.map(r=>[r.quem,r.alvo,r.regiao,r.mudanca,r.resultado].join(' · ')).join('\n')+'\nCoerência: '+s.politica+'; faltam 40 leituras no exemplo.\nAprendizado: carga leve, passo menor melhorou em 120 leituras; carga média sem conclusão.';
  return {registros,vazio:s.vazio,selo:s.selo,modo:s.vazio?'Ver exemplos':'Ver estado vazio',orientacao:s.vazio?'O diário começa com a sua primeira mudança.':'Antes → depois · Desfazer restaura o valor anterior.',copia:s.copia,
   aviso1:s.vazio?'Conecte a central para acompanhar mudanças.':s.politica==='autocal'?'O AutoCal decide aqui; o Refino só acompanha.':s.politica==='livre'?'Trecho liberado para novas sugestões.':'O AutoCal baixou o que o Refino subiu aqui.',
   espera:s.vazio?'O app aprende conforme você usa.':s.politica==='esperar'?'Vou esperar mais 40 leituras (260 de 300).':'Nenhuma correção foi gravada por esta ação.',
   aviso2:s.vazio?'As sugestões repetidas vão aparecer aqui.':'Esta sugestão já apareceu 3 vezes e não foi aplicada.',
   aviso3:s.vazio?'Mudanças opostas vão aparecer aqui.':'Você e o Refino mexeram aqui em sentidos opostos.',
   aprendizado1:s.vazio?'Rode no GNV para conhecer esta faixa.':'Passo menor melhorou · 120 leituras conferidas.',
   aprendizado2:s.vazio?'Ainda sem leituras nesta faixa.':'Ainda sem conclusão · 18 leituras; continue no GNV.',
   alternar:()=>this.setState({vazio:!s.vazio,desfeitos:[],politica:'esperar',copia:'Nada sai do app até você colar.',selo:!s.vazio?'Sem gravações':'Histórico conferido'}),
   decidir:()=>this.setState({politica:'autocal'}),liberar:()=>this.setState({politica:'livre'}),
   copiar:async()=>{try{await navigator.clipboard.writeText(resumo);this.setState({copia:'Resumo copiado. Cole no assistente quando quiser.'});}catch(e){this.setState({copia:'Cópia indisponível aqui. Abra a prévia no navegador.'});}}
  };
 }
}
'''
html = '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>Memória e coerência</title><script src="./support.js"></script></head><body><x-dc><helmet><style>'+css+'</style></helmet>'+start+body+nav+'</div></x-dc><script type="text/x-dc" data-dc-script data-props=\'{"$preview":{"width":1280,"height":720}}\'>'+script+'</script></body></html>'
(BASE / '../project/21-Memoria.dc.html').write_text(html)
steps=[('desfez-autocal','button[aria-label="Desfazer gravação 1"]'),('desfez-refino','button[aria-label="Desfazer gravação 2"]'),('desfez-voce','button[aria-label="Desfazer gravação 3"]'),('autocal-decide','button:has-text("Deixar o AutoCal decidir")'),('trecho-liberado','button:has-text("Liberar este trecho")'),('copiar','button:has-text("Copiar resumo")'),('vazio','button:has-text("Ver estado vazio")'),('exemplos','button:has-text("Ver exemplos")')]
(BASE / 'steps/memoria.json').write_text(json.dumps({'21-Memoria.dc.html':[{'nome':n,'click':c} for n,c in steps]},ensure_ascii=False,indent=2)+'\n')
print('Gerados: 21-Memoria.dc.html e steps/memoria.json')
