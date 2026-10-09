/* OMEGASCINZA · Studio | Dev-only, not bundled into Android */
(() => {
'use strict';
const $=id=>document.getElementById(id);
const API='/__studio/data.json', APP='/app/src/main/assets/ui/index.html', MOCK='/tests/ui/render/mock-bridge.js';
const STAGE_W=1280,STAGE_H=720;
const st={route:'autocal',scenario:'learning',mode:'inspect',frame:null,selected:null,changes:[],records:[],drawing:null,dragging:null,zoom:null,compare:false,ready:false};
const TOOL_TIPS={
 inspect:'Clique em um componente dentro da prévia para inspecioná-lo.',
 draw:'Risque livremente sobre o aplicativo. Use Desfazer para retirar qualquer marcação.',
 move:'Toque no elemento e arraste. A posição muda só nesta prévia.',
 interact:'Os botões reais funcionam com uma ECU simulada. Nenhuma gravação física.'
};
const SCENARIOS={
 learning:{scn:{autoMatch:0,zonesGas:[0,0,0,0],zonesPetrol:[1,1,1,1]}},
 gasmissing:{scn:{autoMatch:1,zonesGas:[1,0,0,1],zonesPetrol:[1,1,1,1]}},
 petrolmissing:{scn:{autoMatch:1,zonesGas:[1,1,1,1],zonesPetrol:[1,0,0,1]}},
 all:{scn:{autoMatch:3,zonesGas:[1,1,1,1],zonesPetrol:[1,1,1,1]}},
 refineready:{scn:{phase:'PROPOSTA_PRONTA',autoMatch:3,zonesGas:[1,1,1,1],zonesPetrol:[1,1,1,1]},route:'refino'},
 disconnected:{scn:{noUndo:true,noStalls:true},mode:'disconnected'}
};
function status(message){$('status').innerHTML='';const dot=document.createElement('i');dot.className='status-led';$('status').append(dot,document.createTextNode(message));}
function frameDocument(){try{return $('omegas-frame').contentDocument;}catch{return null;}}
function frameWindow(){try{return $('omegas-frame').contentWindow;}catch{return null;}}
function safeSelector(node){
  if(!node || node.nodeType!==1)return '';
  if(node.id)return '#'+CSS.escape(node.id);
  if(node.matches('[data-route]'))return '[data-route="'+CSS.escape(node.dataset.route)+'"]';
  for(const key of ['data-autocal-action','data-screen','data-autocal-zone-surface','data-refino-primary']){
    if(node.hasAttribute(key)){
      const v=node.getAttribute(key);
      return '['+key+(v?'="'+CSS.escape(v)+'"':'')+']';
    }
  }
  const c=Array.from(node.classList||[]).filter(x=>!(/^(active|selected|current|hidden|show|visible|is-)/.test(x))).slice(0,2);
  if(c.length)return node.tagName.toLowerCase()+'.'+c.map(CSS.escape).join('.');
  const parent=node.parentElement;
  if(!parent)return node.tagName.toLowerCase();
  return safeSelector(parent)+' > '+node.tagName.toLowerCase()+':nth-of-type('+
    (Array.from(parent.children).filter(e=>e.tagName===node.tagName).indexOf(node)+1)+')';
}
function shorten(node){
 const s=(node?.getAttribute?.('aria-label')||node?.getAttribute?.('title')||node?.innerText||node?.textContent||'').replace(/\s+/g,' ').trim();
 return s.slice(0,130)||(node?.tagName||'Componente').toLowerCase();
}
function rectFor(el){
 if(!el?.isConnected)return null;
 const r=el.getBoundingClientRect();
 return {x:Math.round(r.left),y:Math.round(r.top),w:Math.round(r.width),h:Math.round(r.height)};
}
function updateSelection(){
 let box=$('selection-outline'),tag=$('selection-tag'),el=st.selected?.node;
 if(!el?.isConnected){st.selected=null;box.hidden=true;$('selected-selector').textContent='Nenhum elemento';$('selected-description').textContent='Use “Selecionar elemento” e toque na tela.';['measure-w','measure-h','measure-pos'].forEach(k=>$(k).textContent='—');$('selected-hide').disabled=true;$('selected-restore').disabled=true;return;}
 const rect=rectFor(el);
 box.hidden=st.compare||!rect||!rect.w||!rect.h;
 if(rect){box.style.left=rect.x+'px';box.style.top=rect.y+'px';box.style.width=rect.w+'px';box.style.height=rect.h+'px'}
 tag.textContent=st.selected.selector;
 $('selected-selector').textContent=st.selected.selector;
 $('selected-description').textContent=shorten(el);
 $('measure-w').textContent=rect?.w+'px';$('measure-h').textContent=rect?.h+'px';
 $('measure-pos').textContent=rect?rect.x+', '+rect.y:'—';
 $('selected-hide').disabled=false;
 $('selected-restore').disabled=false;
}
function selectAt(x,y){
 const doc=frameDocument();if(!doc)return;
 let el=doc.elementFromPoint(x,y);
 if(!el)return;
 const choice=el.closest('button,[id],[data-route],[data-autocal-action],[data-refino-primary],input,svg,[data-autocal-zone-surface]')||el;
 if(choice===doc.documentElement||choice===doc.body)return;
 st.selected={node:choice,selector:safeSelector(choice)};
 updateSelection();
}
function position(e){
 const r=$('studio-stage-overlay').getBoundingClientRect();
 return {x:Math.max(0,Math.min(STAGE_W,Math.round((e.clientX-r.left)*STAGE_W/r.width))),
         y:Math.max(0,Math.min(STAGE_H,Math.round((e.clientY-r.top)*STAGE_H/r.height)))};
}
function pushChange(data,rollback){
 st.changes.push(Object.assign({route:st.route,scenario:st.scenario},data));
 st.records.push(rollback||(()=>{}));updateHistory();
}
function updateHistory(){
 $('change-count').textContent=String(st.changes.length);
 const root=$('change-list');root.replaceChildren();
 if(!st.changes.length){const p=document.createElement('p');p.className='empty-history';p.textContent='Nenhuma alteração ainda. Marque a tela e experimente à vontade.';root.append(p);return}
 st.changes.slice().reverse().forEach((act,i)=>{
   const block=document.createElement('div');block.className='history-item';
   const strong=document.createElement('b'),small=document.createElement('small');
   strong.textContent=({'note':'Comentário','stroke':'Marcação','hide':'Ocultar','restore':'Restaurar','move':'Reposicionar'})[act.kind]||act.kind;
   small.textContent=act.text||act.selector||'Traço no gráfico';
   block.append(strong,small);root.append(block);
 });
}
function undo(){
 const fn=st.records.pop();if(!fn)return;
 st.changes.pop();
 fn();updateHistory();updateSelection();
}
function setMode(mode){
 st.mode=mode;
 document.querySelectorAll('[data-mode]').forEach(b=>{
   const yes=b.dataset.mode===mode;b.classList.toggle('active',yes);b.setAttribute('aria-pressed',String(yes));
 });
 $('studio-stage-overlay').dataset.mode=mode;
 $('tool-help').textContent=TOOL_TIPS[mode];
 $('studio-stage-overlay').style.pointerEvents=mode==='interact'?'none':'auto';
}
function compare(yes){
 st.compare=yes;
 $('compare').setAttribute('aria-pressed',String(yes));
 $('compare').textContent=yes?'Voltar às alterações':'Comparar original';
 $('markup-layer').style.visibility=yes?'hidden':'visible';
 for(const act of st.changes){
   if(!['hide','restore','move'].includes(act.kind))continue;
   const el=frameDocument()?.querySelector(act.selector);if(!el)continue;
   if(yes){el.style.removeProperty('display');el.style.removeProperty('transform')}
   else applyChange(act);
 }
 updateSelection();
}
function applyChange(act){
 const doc=frameDocument();if(!doc||st.compare)return;
 let el;
 try{el=doc.querySelector(act.selector)}catch{return}
 if(!el)return;
 if(act.kind==='hide')el.style.setProperty('display','none','important');
 else if(act.kind==='restore')el.style.removeProperty('display');
 else if(act.kind==='move')el.style.transform='translate('+act.dx+'px,'+act.dy+'px)';
}
function refreshApplied(){
 if(st.compare)return;
 for(const act of st.changes)applyChange(act);
 updateSelection();
}
function startPointer(e){
 if(st.mode==='interact'||st.compare)return;
 e.preventDefault();e.stopPropagation();
 const {x,y}=position(e),overlay=$('studio-stage-overlay');
 overlay.setPointerCapture(e.pointerId);
 if(st.mode==='draw'){
   const id='stroke-'+Date.now()+'-'+st.changes.length;
   const svg=document.createElementNS('http://www.w3.org/2000/svg','polyline');
   svg.setAttribute('id',id);svg.setAttribute('points',x+','+y);$('markup-layer').append(svg);
   st.drawing={points:[[x,y]],element:svg};
 }else if(st.mode==='inspect'){selectAt(x,y)}
 else if(st.mode==='move'){
   selectAt(x,y);
   const node=st.selected?.node;
   if(node){st.dragging={x,y,node,selector:st.selected.selector,prev:node.style.transform,dx:0,dy:0}}
 }
}
function movePointer(e){
 if(st.compare)return;
 const pos=position(e);
 if(st.drawing){st.drawing.points.push([pos.x,pos.y]);st.drawing.element.setAttribute('points',st.drawing.points.map(p=>p.join(',')).join(' '));return}
 if(st.dragging){
   const m=st.dragging;m.dx=pos.x-m.x;m.dy=pos.y-m.y;
   m.node.style.transform='translate('+m.dx+'px,'+m.dy+'px)';
   updateSelection();
 }
}
function endPointer(){
 if(st.drawing){
   const draw=st.drawing;st.drawing=null;
   if(draw.points.length<2){draw.element.remove();return}
   pushChange({kind:'stroke',points:draw.points},()=>draw.element.remove());
 }
 if(st.dragging){
   const m=st.dragging;st.dragging=null;
   if(Math.abs(m.dx)+Math.abs(m.dy)<2){m.node.style.transform=m.prev;return}
   pushChange({kind:'move',selector:m.selector,dx:m.dx,dy:m.dy},()=>{if(m.node.isConnected)m.node.style.transform=m.prev});
 }
}
function note(){
 const v=$('note-input').value.trim();
 if(!v){$('note-input').focus();return}
 pushChange({kind:'note',selector:st.selected?.selector||null,text:v},()=>{});
 $('note-input').value='';
}
function hide(){
 const node=st.selected?.node;if(!node)return;
 const selector=st.selected.selector,prev=node.style.getPropertyValue('display'),priority=node.style.getPropertyPriority('display');
 node.style.setProperty('display','none','important');
 pushChange({kind:'hide',selector,text:shorten(node)},()=>{
   if(prev)node.style.setProperty('display',prev,priority);
   else node.style.removeProperty('display');
 });
 updateSelection();
}
function restore(){
 const node=st.selected?.node;if(!node)return;
 const selector=st.selected.selector,prev=node.style.getPropertyValue('display'),priority=node.style.getPropertyPriority('display');
 node.style.removeProperty('display');
 pushChange({kind:'restore',selector},()=>{
   if(prev)node.style.setProperty('display',prev,priority);
   else node.style.removeProperty('display');
 });
 updateSelection();
}
function exportSession(){
 return {schema:'omegascinza.visual-review.v1',viewport:{width:STAGE_W,height:STAGE_H},
   source:'OMEGASCINZA: app/src/main/assets/ui (ECU simulada)',changes:st.changes.map(x=>JSON.parse(JSON.stringify(x)))};
}
function download(){
 const doc=exportSession(),content=JSON.stringify(doc,null,2);
 const a=document.createElement('a');
 const url=URL.createObjectURL(new Blob([content],{type:'application/json'}));
 a.href=url;a.download='omegascinza-revisao-'+new Date().toISOString().slice(0,10)+'.json';
 document.body.append(a);a.click();a.remove();
 setTimeout(()=>URL.revokeObjectURL(url),1000);
 status(st.changes.length+' alterações exportadas. Prontas para converter em código.');
}
function zoom(factor){
 st.zoom=Math.max(.28,Math.min(1.25,factor));fitStage(false);
}
function fitStage(calculate=true){
 const slot=$('stage-slot'),well=$('stage-well');
 const availW=Math.max(250,well.clientWidth-40),availH=Math.max(160,well.clientHeight-36);
 const fit=Math.min(availW/STAGE_W,availH/STAGE_H);
 if(calculate||st.zoom===null)st.zoom=Math.min(fit,1);
 const factor=st.zoom;
 const totalW=STAGE_W*factor,totalH=STAGE_H*factor;
 $('stage-scale').style.transform='scale('+factor+')';
 $('stage-scale').style.left=Math.max(0,(slot.clientWidth-totalW)/2)+'px';
 $('stage-scale').style.top=Math.max(0,(slot.clientHeight-totalH)/2)+'px';
 $('zoom-label').textContent=Math.round(factor*100)+'%';
}
function navigate(route){
 const doc=frameDocument();
 if(!doc)return;
 st.route=route;
 document.querySelectorAll('[data-studio-route]').forEach(b=>b.classList.toggle('active',b.dataset.studioRoute===route));
 const nav=doc.querySelector('[data-route="'+route+'"]');
 if(nav)nav.click();
 setTimeout(refreshApplied,170);
}
function scenarioChanged(name){
 st.scenario=name;const setup=SCENARIOS[name]||SCENARIOS.learning;
 if(setup.route)st.route=setup.route;
 refreshFrame();
}
async function loadSource(){
 const [html,mock,data]=await Promise.all([
   fetch(APP).then(r=>{if(!r.ok)throw Error('index.html do app não encontrado');return r.text()}),
   fetch(MOCK).then(r=>{if(!r.ok)throw Error('Bridge de teste ausente');return r.text()}),
   fetch(API).then(r=>{if(!r.ok)throw Error('Fixture real indisponível');return r.json()})
 ]);
 if(!data.snapshot||!Array.isArray(data.frames)||data.frames.length<2)throw Error('Fixture inválida');
 return {html,mock,data};
}
let source=null;
function injectSrcdoc(){
 const setup=SCENARIOS[st.scenario]||SCENARIOS.learning;
 const inject='window.__DATA='+JSON.stringify(source.data).replace(/</g,'\\u003c')+';'+
   'window.__MODE='+JSON.stringify(setup.mode||'connected')+';window.__SPEED=3;'+
   'window.__SCN='+JSON.stringify(setup.scn||{})+';';
 const scripts='<script>'+inject.replace(/<\/script/gi,'<\\/script')+'<\/script>'+
  '<script>'+source.mock.replace(/<\/script/gi,'<\\/script')+'<\/script>';
 return source.html.replace(/<head[^>]*>/i, '$&<base href="/app/src/main/assets/ui/">')
   .replace('</head>',scripts+'</head>');
}
function refreshFrame(){
 st.selected=null;updateSelection();
 st.ready=false;status('Recarregando dados da ECU simulada...');
 const frame=$('omegas-frame');
 frame.onload=()=>{
   setTimeout(()=>{
     try{
       const doc=frameDocument();
       if(!doc||doc.querySelectorAll('[data-route]').length!==8)throw Error('Rotas do app não foram carregadas');
       // Navegação feita DENTRO do app também atualiza a oficina. Sem interferir na ação.
       doc.addEventListener('click',e=>{
         const nav=e.target.closest?.('[data-route]');
         if(!nav)return;
         st.route=nav.dataset.route;
         document.querySelectorAll('[data-studio-route]').forEach(b=>b.classList.toggle('active',b.dataset.studioRoute===st.route));
         status('Ativo · '+st.route+' · ECU simulada');
         setTimeout(refreshApplied,130);
       },true);
       navigate(st.route);
       st.ready=true;status('Ativo · '+st.route+' · '+$('scenario').selectedOptions[0].textContent);
       refreshApplied();
       setTimeout(refreshApplied,500);
     }catch(e){status('Falha de prévia: '+e.message);console.error(e)}
   },370);
 };
 frame.srcdoc=injectSrcdoc();
}
function keyboard(e){
 if(e.target.closest('input,textarea,select,[contenteditable]'))return;
 if(['1','2','3','4'].includes(e.key)){
   const m={1:'inspect',2:'draw',3:'move',4:'interact'}[e.key];setMode(m);
 }else if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='z'){
   e.preventDefault();undo();
 }
}
function setup(){
 $('studio-stage-overlay').addEventListener('pointerdown',startPointer);
 $('studio-stage-overlay').addEventListener('pointermove',movePointer);
 $('studio-stage-overlay').addEventListener('pointerup',endPointer);
 $('studio-stage-overlay').addEventListener('pointercancel',endPointer);
 document.querySelectorAll('[data-mode]').forEach(b=>b.addEventListener('click',()=>setMode(b.dataset.mode)));
 document.querySelectorAll('[data-studio-route]').forEach(b=>b.addEventListener('click',()=>navigate(b.dataset.studioRoute)));
 $('all-routes').addEventListener('change',e=>{if(e.target.value)navigate(e.target.value)});
 $('scenario').addEventListener('change',e=>scenarioChanged(e.target.value));
 $('restart').addEventListener('click',refreshFrame);
 $('undo').addEventListener('click',undo);
 $('compare').addEventListener('click',()=>compare(!st.compare));
 $('note-save').addEventListener('click',note);
 $('selected-hide').addEventListener('click',hide);
 $('selected-restore').addEventListener('click',restore);
 $('export').addEventListener('click',download);
 $('zoom-in').addEventListener('click',()=>zoom((st.zoom||.7)+.08));
 $('zoom-out').addEventListener('click',()=>zoom((st.zoom||.7)-.08));
 $('fit').addEventListener('click',()=>fitStage(true));
 document.addEventListener('keydown',keyboard);
 window.addEventListener('resize',()=>fitStage(st.zoom===null));
 if(window.ResizeObserver)new ResizeObserver(()=>fitStage(st.zoom===null)).observe($('stage-well'));
 setMode('inspect');fitStage(true);
}
window.__studio={get ready(){return st.ready},exportSession,refreshFrame,mode:setMode};
setup();
loadSource().then(result=>{source=result;refreshFrame()}).catch(error=>{status('Não foi possível carregar a interface: '+error.message);console.error(error)});
})();
