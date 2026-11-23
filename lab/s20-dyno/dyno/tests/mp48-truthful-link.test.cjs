const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const root=path.join(__dirname,'../src/main/assets');
const html=fs.readFileSync(path.join(root,'dashboard.html'),'utf8');
const script=fs.readFileSync(path.join(root,'dashboard.js'),'utf8');
function mount(initial){
  let current=initial;
  const nodes={};
  function el(){
    return {hidden:false,disabled:false,content:'',children:[],attributes:{},
      set textContent(v){this.content=String(v);this.children=[];},
      get textContent(){return this.content+this.children.map(c=>c.textContent).join(' ');},
      setAttribute(k,v){this.attributes[k]=v;},
      addEventListener(name,handler){this[name]=handler;},
      append(...children){this.children.push(...children);},
      replaceChildren(...children){this.children=children;this.content='';},
      getBoundingClientRect(){return {width:0,height:0};}
    };
  }
  for(const [,id] of html.matchAll(/\bid="([^"]+)"/g))nodes[id]=el();
  let tick=()=>{};
  vm.runInNewContext(script,{
    document:{getElementById:id=>nodes[id],createElement:el},
    window:{DynoNative:{getSnapshot:()=>JSON.stringify(current)},addEventListener(){}},
    setInterval:fn=>tick=fn
  });
  return {nodes,set:data=>{current=data;tick();}};
}
const snapshot=(mp48,status)=>({mp48,obd:{connected:false},status:{capturing:false,mp48:status}});
test('USB plug present and link handshaking must not imply physical MP48 disconnected',()=>{
  const {nodes}=mount(snapshot({connected:false,usbPresent:true,usbAuthorized:true,
    lastGoodAgeMs:4000},'conectando'));
  assert.match(nodes['mp48-status'].textContent,/USB conectada|USB detectada/);
  assert.match(nodes['mp48-status'].textContent,/sincroniz|aguardando.*central/i);
  assert.doesNotMatch(nodes['mp48-status'].textContent,/Desconectad/i);
  assert.match(nodes['connect-mp48'].textContent,/Sincronizando MP48|Retomando MP48/);
});
test('link retry reports no live packets, last-good recency and automatic recovery',()=>{
  const {nodes}=mount(snapshot({connected:false,usbPresent:true,usbAuthorized:true,
    lastGoodAgeMs:24500},'reconectando em 15 s'));
  assert.match(nodes['mp48-status'].textContent,/24,5|24\.5|24\s*s/);
  assert.match(nodes['mp48-status'].textContent,/retom|reconex/i);
  assert.match(nodes['connect-mp48'].textContent,/Reconexão MP48/);
  assert.doesNotMatch(nodes['mp48-status'].textContent,/Recebendo leituras/);
});
test('USB connected with live packets renders actual reading state, not connecting',()=>{
  const {nodes,set}=mount(snapshot({connected:false,usbPresent:true,usbAuthorized:true},'conectando'));
  set(snapshot({connected:true,usbPresent:true,usbAuthorized:true,rpm:1744,
    ageMs:100,lastGoodAgeMs:100},'conectado'));
  assert.match(nodes['mp48-status'].textContent,/Recebendo leituras/);
  assert.match(nodes['connect-mp48'].textContent,/MP48.*recebendo|MP48 conectada/);
});
test('transport connected with no fresh frames is not falsely called actively reading',()=>{
  const {nodes}=mount(snapshot({connected:true,usbPresent:true,usbAuthorized:true,
    rpm:'—',ageMs:7500,lastGoodAgeMs:7500},'conectado'));
  assert.doesNotMatch(nodes['mp48-status'].textContent,/Recebendo leituras/);
  assert.doesNotMatch(nodes['connect-mp48'].textContent,/recebendo dados/i);
});
