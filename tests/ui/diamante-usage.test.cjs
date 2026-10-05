'use strict';
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const W = require('./wiring/world.cjs');
const { assert } = L;
const frames = W.realFrames('ref_', f => f.fuel === 'GNV' && f.petrol_ms > 2);
function boot(route='dashboard') { const w=new W.World(); w.setFrame(frames[10]); const app=L.boot({world:w});app.go(route);app.settle(3);return app; }
test('Diamante: gasolina, GNV e MAP no estado persistente; dados vencidos não fingem ser atuais',()=>{
 const app=boot(); const strip=app.byId('vehicleStatusStrip');
 for(const key of ['petrol','gas','map']) assert.ok(strip.querySelector(`[data-vehicle-fact="${key}"]`),key+' no topo');
 app.advance(4500);
 for(const key of ['petrol','gas','map']) assert.equal(strip.querySelector(`[data-vehicle-fact="${key}"] b`).textContent,'—',key+' vencido');
 app.destroy();
});
test('Diamante: Agora apresenta 4 blocos de direção, resultado e próxima intenção (mockup aprovado 2026-10-05)',()=>{
 const app=boot();
 assert.equal(app.$('[data-screen="dashboard"]').querySelectorAll('.now-tile').length,4);
 for (const id of ['dashDriveFuel','dashDriveMs','dashDriveRpm','dashDriveMap']) assert.ok(app.byId(id),id);
 assert.ok(app.byId('dashNext'));assert.ok(app.byId('dashEquivalence'));assert.ok(app.byId('dashHealth'));
 L.assertClean(app,'Agora');app.destroy();
});
test('Diamante: próxima ação do Agora usa a visão enriquecida do Refino, inclusive correção local de engasgos',()=>{
 const app=boot();
 app.win.OmegasUi.AutoCalApi.equivalence=()=>({ok:true,index:0.74,coverage:0.62,provisional:true,autopilot:{phase:'COLETANDO_NOSSOS'},nextAction:{kind:'APPLY',local:true,text:'Ajustar a região com engasgos',route:'refino'},points:[]});
 app.win.OmegasUi.AutoCalApi.equivalenceResult=()=>({ok:true,available:true,index:0.74,coverage:0.62,nextAction:{kind:'COLLECT',text:'Continue medindo',route:'refino'}});
 app.advance(4000);
 assert.equal(app.byId('dashNext').textContent,'Ajustar a região com engasgos');
 assert.equal(app.byId('dashEquivalence').textContent,'74%');
 app.destroy();
});
test('Diamante: proposta local da ponte aparece em Sugestões e só grava após o toque, pelo escritor existente',()=>{
 const w=new W.World(); w.setFrame(frames[10]);
 const before=w.curve.slice(),after=before.slice();after[4]=before[4]+120;
 w.equivalence={...W.equivalenceFor('COLETANDO_NOSSOS'),nextAction:{kind:'APPLY',local:true,text:'Ajustar a região com engasgos',currentRaw:before,refinedRaw:after,pointIndexes:[4]},refinoState:{phase:'Ajuste local',whatNow:'Há um ajuste local disponível.',nextAction:'Aplicar ajuste',canAct:true}};
 const app=L.boot({world:w});app.go('refino');app.settle(4);
 const button=app.$('[data-refino-primary]');
 assert.equal(button.hidden,false,'o ajuste local tem ação');
 assert.match(app.byId('refinoProposals').textContent,/Ponto 5/);
 assert.equal(w.callsOf('startCurveBatchWrite').length,0,'só observar não grava');
 button.click();app.settle(8);
 assert.equal(w.callsOf('startCurveBatchWrite').length,1,'um toque, caminho habitual de foto e conferência');
 assert.equal(w.curve[4],after[4]);
 L.assertClean(app,'proposta local');app.destroy();
});
test('Diamante: fallback de GNV ausente no status nativo não aparece como zero',()=>{
 const app=boot();
 const read=app.win.OmegasUi.LiveStore.read;
 const state={telemetry:{valid:false},status:{usbConnected:true,directTelemetryAgeMs:60,rpm:900,petrolMs:4,mapBar:.45,fuelState:'GNV',gasMs:0}};
 assert.equal(read(state,{fallback:true}).gasMs,null);
 state.status.gasMs=6.5;assert.equal(read(state,{fallback:true}).gasMs,6.5);
 app.destroy();
});
test('Diamante: intenção APPLY local prevalece sobre fase estável/encerrada e respeita canAct e barreiras da ECU',()=>{
 const app=boot('refino'), m=app.win.OmegasUi.RefinoModel;
 const before=app.world.curve.slice(), after=before.slice();after[4]+=120;
 const eq={nextAction:{kind:'APPLY',local:true,currentRaw:before,refinedRaw:after,pointIndexes:[4]},refinoState:{canAct:true},autopilot:{phase:'ESTAVEL'}};
 const generic={available:true,points:[]};
 for(const phase of ['ESTAVEL','TENTATIVA_ENCERRADA','COLETANDO_NOSSOS']) {eq.autopilot.phase=phase;assert.equal(m.primaryAction(eq,generic).kind,'review',phase);}
 eq.refinoState.canAct=false;assert.equal(m.primaryAction(eq,generic).kind,'none','canAct=false não é substituído por analysis.available');
 eq.refinoState.canAct=true;
 for(const phase of ['SEM_ECU','LENDO_ECU','ECU_TRABALHANDO','VERIFICANDO']) {eq.autopilot.phase=phase;assert.equal(m.primaryAction(eq,generic).kind,'none',phase);}
 app.destroy();
});
test('Diamante: Refino atualiza a permissão de agir mesmo quando a frase e a proposta não mudam',()=>{
 const w=new W.World();w.setFrame(frames[10]);const before=w.curve.slice(),after=before.slice();after[4]+=120;
 w.equivalence={...W.equivalenceFor('COLETANDO_NOSSOS'),nextAction:{kind:'APPLY',local:true,currentRaw:before,refinedRaw:after,pointIndexes:[4]},refinoState:{phase:'Ajuste local',whatNow:'Ajuste disponível.',nextAction:'Aplicar ajuste',canAct:true}};
 const app=L.boot({world:w});app.go('refino');app.settle(4);
 assert.equal(app.$('[data-refino-primary]').hidden,false);
 w.equivalence.refinoState.canAct=false;app.advance(4000);
 assert.equal(app.$('[data-refino-primary]').hidden,true,'permissão nova precisa invalidar a apresentação anterior');
 assert.equal(w.callsOf('startCurveBatchWrite').length,0);app.destroy();
});
test('Diamante: Diagnóstico apresenta jerkPct como percentual e aceita o nome gas do contrato',()=>{
 const app=boot('diagnostico');
 app.world.equivalence={...W.equivalenceFor('COLETANDO_NOSSOS'),fluidity:{petrol:{index:.94,jerks:1.3,samples:40},gas:{index:.81,jerks:7.6,samples:40}},stalls:{regions:[]}};
 app.advance(4000);
 const model=app.win.OmegasUi.DiagnosticoModel.fluidityOf(app.world.equivalence);
 assert.equal(model.gnv.jerks,7.6);
 assert.match(app.byId('diagnosticoHost').textContent,/Variação brusca: 7,6%/);
 assert.doesNotMatch(app.byId('diagnosticoHost').textContent,/7,6 solavancos|Gasolina é linear;/);
 app.destroy();
});

test('Diamante: ícones de navegação são SVG incorporado, sem máscaras externas incompatíveis com file:// no WebView',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const html=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/ui/index.html'),'utf8');
 assert.equal((html.match(/<svg class="nav-icon"/g)||[]).length,8);
 assert.doesNotMatch(html,/--nav-icon:url/);
});
