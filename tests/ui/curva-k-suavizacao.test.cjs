'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const { smooth, roughness } = require('../../app/src/main/assets/ui/core/curve-smoothing.js');
const root = path.resolve(__dirname, '../..');
const events = zlib.gunzipSync(fs.readFileSync(path.join(root, 'tests/fixtures/sessoes-uteis/eventos-calibracao.jsonl.gz'))).toString().trim().split('\n').map(JSON.parse);
function real(prefix) {
  const e = events.find(e => e.sessao.startsWith(prefix) && e.type === 'autocal_native_snapshot');
  assert.ok(e, prefix);
  const raw = Buffer.from(e.data.fields.find(f => f.key === 'MUL_ACT').rawPayloadHex, 'hex');
  assert.equal(raw.length, 60);
  return Array.from({ length: 30 }, (_, i) => raw.readUInt16LE(i * 2) / 16384);
}
test('métrica é segunda diferença; curvas constantes/lineares/suaves ficam intactas e idempotentes', () => {
  for (const a of [Array(30).fill(1), Array.from({length:30}, (_,i)=>0.9+i*.02), Array.from({length:30},(_,i)=>1+.05*Math.sin(i/5))]) {
    const r = smooth(a);
    assert.deepEqual(r.after,a); assert.deepEqual(r.changed,[]);
    assert.deepEqual(smooth(r.after).after,a);
  }
  const a=Array(30).fill(1); a[10]=1.2;
  assert.ok(Math.abs(roughness(a).max-.4)<1e-12);
});
for (const prefix of ['076_MEDIA_2026-10-10','036_RICA_2026-10-02']) test('corpus real Q14: '+prefix, () => {
  const a=real(prefix), original=a.slice(), r=smooth(a);
  assert.ok(r.changed.length>0);
  assert.ok(r.roughnessAfter.rms<r.roughnessBefore.rms);
  assert.deepEqual(a,original);
  assert.deepEqual(r.before,a);
  assert.deepEqual(r.after.slice(0,5),a.slice(0,5)); assert.equal(r.after[29],a[29]);
  assert.ok(r.after.every(v=>v>=.8&&v<=1.8));
  assert.deepEqual(r.changed,r.after.flatMap((v,i)=>v===a[i]?[]:[{index:i,before:a[i],after:v}]));
  assert.deepEqual(smooth(a),r);
});
test('evidência maior protege o nó; extremos e baixa carga são âncoras',()=>{
  const a=Array(30).fill(1); a[10]=1.6; a[2]=1.4;
  const counts=Array(30).fill(0); counts[10]=100;
  const plain=smooth(a), weighted=smooth(a,{evidenceCounts:counts});
  assert.ok(Math.abs(weighted.after[10]-a[10])<Math.abs(plain.after[10]-a[10]));
  assert.equal(weighted.after[2],a[2]);
});
test('limites e dados inválidos não produzem proposta parcial ou valores inventados',()=>{
  for(const a of [[],Array(30).fill(null),Array(30).fill(NaN),Array(30).fill(.79),Array(30).fill(1.81)]) assert.throws(()=>smooth(a));
  assert.throws(()=>smooth(Array(30).fill(1),{evidenceCounts:[1]}));
  assert.throws(()=>smooth(Array(30).fill(1),{threshold:-1}));
  const a=Array.from({length:30},(_,i)=>i%2?.8:1.8);
  assert.ok(smooth(a).after.every(v=>v>=.8&&v<=1.8));
});

const vm = require('node:vm');
function screenHarness() {
  const listeners = {};
  const context = { console, document: { getElementById: id => ({ addEventListener: (event, fn) => { listeners[id] = fn; } }), querySelectorAll: () => [] } };
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(path.join(root,'app/src/main/assets/ui/core/curve-smoothing.js'),'utf8'),context);
  vm.runInContext(fs.readFileSync(path.join(root,'app/src/main/assets/ui/screens/curve.js'),'utf8'),context);
  const screen = Object.create(context.OmegasUi.CurveScreen.prototype);
  const factors = real('076_MEDIA_2026-10-10');
  Object.assign(screen, { proposals:new Map(), activeIndex:null, restoreContext:null,
    bindDrag(){}, points:()=>factors.map((factor,index)=>({factor,index})), renderChart(){},renderProposalList(){},
    alert(message){this.message=message;},
    api:{ previewCurvePoint(index,targetFactor){return {ok:true,changed:true,index,targetFactor,currentFactor:factors[index],currentRaw:Math.round(factors[index]*16384),targetRaw:Math.floor(targetFactor*16384)};},
      writeCurve(){throw Error('Suavizar não pode gravar');} } });
  return {screen, listeners};
}
test('Suavizar prepara prévias nativas, conserva edições pendentes e não escreve',()=>{
  const {screen,listeners}=screenHarness();
  screen.bind();
  assert.equal(screen.proposals.size,0, 'ligar a tela não prepara nem grava');
  listeners.curveSmoothButton();
  assert.ok(screen.proposals.size>0);
  assert.match(screen.message,/Confira.*nada foi gravado/);
  const once=[...screen.proposals];
  screen.prepareSmoothing();
  assert.deepEqual([...screen.proposals],once);
});
test('falha de uma prévia é atômica; leitura/escrita em curso bloqueiam preparação',()=>{
  const {screen}=screenHarness();
  const preview=screen.api.previewCurvePoint;
  let calls=0;
  screen.api.previewCurvePoint=(...args)=>++calls===2?{ok:false,error:'Leitura mudou'}:preview(...args);
  screen.prepareSmoothing(); assert.equal(screen.proposals.size,0); assert.match(screen.message,/Leitura mudou/);
  for(const flag of ['reading','writing']) {
    screen[flag]=true;
    screen.api.previewCurvePoint=()=>{throw Error('Não deveria preparar');};
    screen.prepareSmoothing(); screen[flag]=false;
  }
});
test('botão carrega módulo antes da tela e possui texto explícito de prévia',()=>{
  const html=fs.readFileSync(path.join(root,'app/src/main/assets/ui/index.html'),'utf8');
  assert.match(html,/id="curveSmoothButton"[^>]*>Suavizar<\/button>/);
  // A tela da Curva K e o módulo de suavização chegam ao abrir a aba (core/router.js ROUTE_SCRIPTS), o módulo primeiro.
  const router=fs.readFileSync(path.join(root,'app/src/main/assets/ui/core/router.js'),'utf8');
  assert.ok(router.indexOf("'core/curve-smoothing.js'")>=0);
  assert.ok(router.indexOf("'core/curve-smoothing.js'")<router.indexOf("'screens/curve.js'"));
  const grupo=/\n    curve: \[([^\]]*)\]/.exec(router);assert.ok(grupo,'grupo da aba curve');
  assert.ok(grupo[1].indexOf('core/curve-smoothing.js')>=0&&grupo[1].indexOf('core/curve-smoothing.js')<grupo[1].indexOf('screens/curve.js'),'módulo dentro do grupo da aba, antes da tela');
  assert.doesNotMatch(html,/curve-smoothing\.js|screens\/curve\.js/,'nada da Curva K no arranque');
});

test('sem curva, curva suave e restauração pendente não criam proposta',()=>{
  for(const kind of ['missing','smooth','restore']) {
    const {screen}=screenHarness();
    if(kind==='missing') screen.points=()=>[];
    if(kind==='smooth') screen.points=()=>Array.from({length:30},(_,index)=>({index,factor:1}));
    if(kind==='restore') screen.restoreContext={fileName:'backup'};
    screen.api.previewCurvePoint=()=>{throw Error('Não deveria preparar');};
    screen.prepareSmoothing();
    assert.equal(screen.proposals.size,0);
    assert.doesNotMatch(screen.message,/Não deveria preparar/);
  }
});
