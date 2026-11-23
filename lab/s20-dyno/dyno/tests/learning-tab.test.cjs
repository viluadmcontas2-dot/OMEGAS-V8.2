const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const root=path.join(__dirname,'../src/main/assets');
const html=fs.readFileSync(path.join(root,'dashboard.html'),'utf8');
const code=fs.readFileSync(path.join(root,'dashboard.js'),'utf8');
function mount(){
 let tick=()=>{};
 const nodes={};
 function node(){return {
   hidden:false,disabled:false,value:'',content:'',children:[],attributes:{},
   set textContent(s){this.content=String(s);this.children=[];},
   get textContent(){return this.content+this.children.map(n=>n.textContent).join(' ');},
   addEventListener(k,fn){this[k]=fn;},
   setAttribute(k,v){this.attributes[k]=v;},
   append(...a){this.children.push(...a);},replaceChildren(...a){this.children=a;this.content='';},
   getBoundingClientRect(){return {width:0,height:0}}
  };}
 for(const [,id] of html.matchAll(/\bid="([^"]+)"/g))nodes[id]=node();
 const status={discovery:{},runtimeStatus:'OBSERVANDO',liveSourceCounts:{obd:77,mp48:212},
  learning:{state:'OBSERVANDO',stats:{updates:11,geminiReviews:1,persistent:true},
    lastCycleEpochMs:1795468000000,
    findings:[{id:'tps-rpm-response',title:'Borboleta e rotação',
      state:'EM_INVESTIGACAO',evidence:'52 mudanças associadas, resolução TPS 960 ms',
      nextStep:'Comparar carga e combustível',updatedAtEpochMs:1795468000000}],
    reviews:[{findingId:'tps-rpm-response',model:'gemini-flash',
      text:'Sugestão: correlacionar mudanças de TPS, não é prova',
      status:'INTERPRETACAO_NAO_COMPROVADA',atEpochMs:1795468000000}]}};
 const window={DynoNative:{getSnapshot:()=>JSON.stringify({
   status:{capturing:false},obd:{connected:false},mp48:{connected:false}}),
   getIntelligence:()=>JSON.stringify(status)},addEventListener(){}};
 vm.runInNewContext(code,{document:{getElementById:id=>nodes[id],createElement:node},
   window,setInterval:fn=>tick=fn});
 return {nodes,tick, setLearning:l=>{status.learning=l;tick();}};
}
test('navigation exposes dedicated persistent learning tab',()=>{
 assert.match(html,/id="tab-learning"/);
 assert.match(html,/id="learning"[^>]*hidden/);
 const {nodes}=mount();
 nodes['tab-learning'].click();
 assert.equal(nodes.learning.hidden,false);
 assert.equal(nodes.live.hidden,true);
 assert.equal(nodes['tab-learning'].attributes['aria-pressed'],'true');
});
test('learning tab explains evidence and outstanding investigation',()=>{
 const {nodes}=mount();nodes['tab-learning'].click();
 assert.match(nodes['learning-cards'].textContent,/Borboleta e rotação/);
 assert.match(nodes['learning-cards'].textContent,/52 mudanças associadas/);
 assert.match(nodes['learning-cards'].textContent,/Comparar carga e combustível/);
 assert.match(nodes['learning-cards'].textContent,/Em investigação|EM_INVESTIGACAO/);
});
test('Gemini output is shown as interpretation, not definitive fact',()=>{
 const {nodes}=mount();nodes['tab-learning'].click();
 assert.match(nodes['learning-reviews'].textContent,/Gemini|gemini/i);
 assert.match(nodes['learning-reviews'].textContent,/não é prova|não comprovada/i);
 assert.match(nodes['learning-reviews'].textContent,/correlacionar mudanças de TPS/);
});
test('learning displays current stream and memory counts',()=>{
 const {nodes}=mount();nodes['tab-learning'].click();
 assert.match(nodes['learning-status'].textContent,/observando|analisando/i);
 assert.match(nodes['learning-updates'].textContent,/11/);
});
