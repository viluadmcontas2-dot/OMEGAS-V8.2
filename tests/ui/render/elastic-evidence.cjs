'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {open,go,playwright}=require('./lib.js');
const out=process.argv[2]||'rendered-evidence/elastic';
fs.mkdirSync(out,{recursive:true});
const metrics=[];
async function audit(page,route,height,name){
 const m=await page.evaluate(()=>{
  const rect=e=>{const b=e.getBoundingClientRect();return {x:b.x,y:b.y,w:b.width,h:b.height,r:b.right,b:b.bottom};};
  const visible=e=>{const c=getComputedStyle(e),b=e.getBoundingClientRect();return b.width>0&&b.height>0&&c.display!=='none'&&c.visibility!=='hidden'&&!e.closest('[hidden]')&&!e.closest('details:not([open]) *:not(summary)');};
  const screen=document.querySelector('.screen.active'),nav=rect(document.querySelector('.side-nav'));
  const plot=screen.querySelector('.ar-chart-host');
  const critical=screen.querySelectorAll('#curveBackupSave,#curveBackupSelect,#curveResetButton,[data-autocal-reacquire-selected],[data-autocal-clear-point-selection],[data-autocal-sessions],[data-autocal-action="RESET_K_FACTOR"],[data-refino-reset-gas],[data-refino-acquisition],[data-autocal-toggle],[data-autocal-action="RESET_GAS"],[data-autocal-action="RESET_PETROL"],[data-refino-primary],[data-refino-undo],#mapReadButton,#mapReviewButton,#mapAdjustmentValue,#curveReadButton,#curveReviewButton,#curveTargetFactor,[data-dash-refino]');
  return {view:{w:innerWidth,h:innerHeight},screen:rect(screen),nav,header:rect(document.querySelector('.workspace-head')),
   plot:plot?rect(plot):null,domain:window.OmegasUi.CurveChart.shared.scale?{xMax:window.OmegasUi.CurveChart.shared.scale.xMax,yMin:window.OmegasUi.CurveChart.shared.scale.yMin,yMax:window.OmegasUi.CurveChart.shared.scale.yMax}:null,
   overflow:screen.scrollWidth>screen.clientWidth+1,
   important:[...critical].filter(visible).map(e=>({id:e.id||e.dataset.autocalAction||e.textContent.trim(),...rect(e)})),
   bad:/\bNaN\b|\bundefined\b|\[object Object\]/.test(screen.innerText)};
 });
 metrics.push({name,route,...m});fs.writeFileSync(path.join(out,'metrics.json'),JSON.stringify({source:process.env.OMEGAS_SOURCE_SHA,metrics},null,2));await page.screenshot({path:path.join(out,name+'.png')});
 console.log(name+' '+JSON.stringify(m));
 assert.equal(m.overflow,false,name+': corte lateral');
 assert.equal(m.bad,false,name+': texto inválido');
 assert.ok(m.nav.b<=height+1&&m.nav.h>=76,name+': navegação cabe');
 for(const b of m.important) assert.ok(b.x>=-1&&b.r<=1281&&b.b<=m.nav.y+1&&b.y>=m.header.b-1,name+': controle fora do alcance '+JSON.stringify(b));
 if(m.plot&&['autocal','refino'].includes(route)) assert.ok(m.plot.h>=m.screen.h*.52,name+': gráfico achatado '+m.plot.h+'/'+m.screen.h);

}
(async()=>{
 const pw=playwright();assert.ok(pw,'Playwright é obrigatório nesta prova');
 for(const height of [720,672,648]){
  const {browser,page,errors}=await open(pw.chromium,'connected',{viewport:{width:1280,height},scn:{noStalls:true}});
  try{
   for(const route of ['dashboard','map','curve','autocal','refino','sessions','tools','diagnostico']){
    await go(page,route);await page.waitForTimeout(['map','curve'].includes(route)?2600:1700);
    await audit(page,route,height,height+'-'+route);
   }
   assert.deepEqual(errors,[]);
  }finally{await browser.close();}
 }
 // Comandos reais no HTML: pontos, seleção e feedback pendente sem reconstruir a tela.
 {
  const {browser,page,errors}=await open(pw.chromium,'connected',{viewport:{width:1280,height:648},scn:{noStalls:true}});
  try {
   await go(page,'autocal');await page.waitForTimeout(1700);
   const targets=await page.evaluate(()=>{
    window.__pointSvg=document.querySelector('#autocalReferenceChart svg');
    return [...document.querySelectorAll('#autocalReferenceChart .autocal-acquired-hit[data-autocal-acquired-fuel]')].filter(node=>{const r=node.getBoundingClientRect();return document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)===node;}).slice(0,2).map(node=>({fuel:node.dataset.autocalAcquiredFuel,index:node.dataset.autocalAcquiredIndex}));
   });
   assert.equal(targets.length,2,'pontos precisam ser tocáveis no HTML real');
   for(const target of targets){
    await page.locator('#autocalReferenceChart .autocal-acquired-hit[data-autocal-acquired-fuel="'+target.fuel+'"][data-autocal-acquired-index="'+target.index+'"]').click();
    // Tocar no ponto já marca (P2 seleção): não há mais botão "Selecionar ponto".
   }
   assert.equal(await page.locator('#autocalChartInspector button').count(),0);
   assert.equal(await page.evaluate(()=>window.__pointSvg===document.querySelector('#autocalReferenceChart svg')),true);
   await audit(page,'autocal',648,'autocal-selecao-pontos');
   await page.locator('[data-autocal-reacquire-selected]').click();
   assert.equal(await page.evaluate(()=>window.__pointTargets.length),2);
   assert.equal(await page.locator('[data-autocal-reacquire-selected]').isDisabled(),true);
   await page.evaluate(()=>{window.__pointAction={action:'DELETE_POINT',state:'FAILED',busy:false};window.OmegasApp.autoCalCockpit.refresh();});
   assert.equal(await page.evaluate(()=>window.OmegasApp.autoCalCockpit.selectedAcquiredPoints.size),2);
   assert.equal(await page.locator('[data-autocal-reacquire-selected]').isDisabled(),false);
   await page.locator('[data-autocal-reacquire-selected]').click();
   await page.evaluate(()=>{window.__pointAction={action:'DELETE_POINT',state:'CONFIRMED',busy:false};window.OmegasApp.autoCalCockpit.refresh();});
   assert.equal(await page.evaluate(()=>window.OmegasApp.autoCalCockpit.selectedAcquiredPoints.size),0);
   assert.deepEqual(errors,[]);
  } finally { await browser.close(); }
 }
 const states=[
 ['autocal-completo','autocal',{zonesGas:[1,1,1,1],zonesPetrol:[1,1,1,1],autoMatch:3}],
 ['autocal-coleta','autocal',{zonesGas:[1,1,0,1]}],
 ['refino-proposta','refino',{phase:'PROPOSTA_PRONTA'}],
 ['refino-verificando','refino',{phase:'VERIFICANDO'}],
 ['refino-estavel','refino',{phase:'ESTAVEL',noUndo:true}],
 ['refino-sem-ecu','refino',{phase:'SEM_ECU',noUndo:true}],
 ['diagnostico-engasgos','diagnostico',{}]
 ];
 for(const [name,route,scn] of states){
  const {browser,page,errors}=await open(pw.chromium,name==='refino-sem-ecu'?'disconnected':'connected',{viewport:{width:1280,height:672},scn});
  try{
   await go(page,route);await page.waitForTimeout(2500);
   if(name==='refino-proposta'){
    const action=page.locator('[data-refino-primary]');
    assert.equal(await action.isVisible(),true,'proposta precisa oferecer sua ação');
    assert.equal(await action.getAttribute('data-kind'),'review');
    assert.match(await action.textContent(),/Aplicar ajuste/);
   }
   if(['refino-verificando','refino-estavel','refino-sem-ecu'].includes(name)) assert.equal(await page.locator('[data-refino-primary]').isVisible(),false,'sem ação de gravação neste estado');
   await audit(page,route,672,name);assert.deepEqual(errors,[]);
  }
  finally{await browser.close();}
 }
 for(const route of ['autocal','refino']){
  const {browser,page}=await open(pw.chromium,'connected',{viewport:{width:1280,height:672},scn:{phase:'PROPOSTA_PRONTA',noStalls:true}});
  try{
   await go(page,route);await page.waitForTimeout(2500);
   const summary=route==='autocal'?'.autocal-reset-menu summary':'.refino-details summary';
   await page.locator(summary).click();await page.waitForTimeout(150);
   const original=await page.evaluate(()=>window.OmegasUi.CurveChart.shared.scale.xMax);
   await page.locator('.screen.active [data-chart-view="fullRange"]').check();await page.waitForTimeout(150);
   const full=await page.evaluate(()=>window.OmegasUi.CurveChart.shared.scale.xMax);
   assert.ok(full>=22&&full>=original);
   await page.locator('.screen.active [data-chart-view="fullRange"]').uncheck();
   await page.locator('.screen.active [data-chart-view="gas"]').uncheck();await page.waitForTimeout(150);
   assert.equal(await page.locator('.ar-chart-host .autocal-reference-line.gas').count(),0);
   assert.equal(await page.locator('.ar-chart-host .chart-between.gas').count(),0);
   await page.locator('.screen.active [data-chart-view="gas"]').check();
   await page.screenshot({path:path.join(out,route+'-opcoes.png')});
   await page.locator(summary).click();
   if(route==='refino'){
    const beforeWrites=await page.evaluate(()=>Object.entries(window.__mockCalls).filter(([k])=>/Write|Restore|startCurveReset|executeNativeAction/.test(k)));
    const petrolPath=await page.locator('.screen.active .autocal-reference-line.petrol').getAttribute('d');
    await page.locator(summary).click();
    assert.equal(await page.locator('.screen.active [data-refino-reset-gas]').evaluate(button => { const r=button.getBoundingClientRect(); return button.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)); }),true,'detalhes não podem cobrir o comando principal');
    await page.locator('.screen.active [data-refino-reset-gas]').click();
    await page.waitForTimeout(200);
    assert.equal(await page.evaluate(()=>window.OmegasUi.AutoCalApi.equivalence().gasObservations),0);
    assert.equal(await page.locator('.screen.active .chart-between.gas').count(),0);
    assert.equal(await page.locator('.screen.active .autocal-reference-line.petrol').getAttribute('d'),petrolPath);
    assert.deepEqual(await page.evaluate(()=>Object.entries(window.__mockCalls).filter(([k])=>/Write|Restore|startCurveReset|executeNativeAction/.test(k))),beforeWrites);
    await page.screenshot({path:path.join(out,'refino-reinicio-gnv.png')});
    await page.locator(summary).click();
   }
   const before=await page.evaluate(()=>window.OmegasUi.CurveChart.shared.renders);
   await page.waitForTimeout(4000);
   assert.equal(await page.evaluate(()=>window.OmegasUi.CurveChart.shared.renders),before,'não repinta por relógio');
  }finally{await browser.close();}
 }
 for(const route of ['autocal','refino']){
  const {browser,page}=await open(pw.chromium,'connected',{viewport:{width:1280,height:648},scn:{noStalls:true}});
  try{
   await go(page,route);await page.waitForTimeout(1800);
   const summary=route==='autocal'?'.ar-buttons .instrument-details summary':'.refino-details summary';
   await page.locator(summary).click();
   const bounds=await page.evaluate(()=>{
    const pop=document.querySelector('.screen.active details[open] > .ar-secondary,.screen.active details[open] > .instrument-detail-content').getBoundingClientRect();
    const top=document.querySelector('.workspace-head').getBoundingClientRect().bottom;
    const bottom=document.querySelector('.side-nav').getBoundingClientRect().top;
    return {top,bottom,y:pop.y,b:pop.bottom};
   });
   assert.ok(bounds.y>=bounds.top-1&&bounds.b<=bounds.bottom+1,route+': opções na área útil '+JSON.stringify(bounds));
   await page.screenshot({path:path.join(out,'648-'+route+'-detalhes.png')});
  }finally{await browser.close();}
 }
 const {browser,page}=await open(pw.chromium,'connected',{viewport:{width:1280,height:672}});
 try{
  await go(page,'map');await page.waitForTimeout(3000);
  await page.locator('.screen.active .map-k-cell[data-row="2"][data-column="3"]').click();
  await page.locator('.screen.active [data-map-mode="target"]').click();
  await page.locator('#mapAdjustmentValue').fill('150');
  await page.waitForTimeout(200);
  assert.equal(await page.locator('#mapReviewButton').isEnabled(),true);
  await audit(page,'map',672,'mapa-edicao');
  await page.locator('#mapAdjustmentValue').fill('');
  assert.equal(await page.locator('#mapReviewButton').isEnabled(),false);
  await page.locator('#mapClearSelection').click();
  assert.equal(await page.locator('#mapActiveCell').textContent(),'Toque em uma célula');
  await page.locator('.map-k-cell[data-row="1"][data-column="1"]').focus();
  await page.keyboard.press('Enter');
  assert.match(await page.locator('#mapSelectionCount').textContent(),/1 selecionada/);
 }finally{await browser.close();}
 // Transições sintéticas sobre os vetores reais: nenhuma escrita/protocolo é simulada como prova física.
 for(const transition of ['petrol','gas','automatch']){
  const fuel=transition==='automatch'?'gas':transition;
  const {browser,page,errors}=await open(pw.chromium,'connected',{viewport:{width:1280,height:672},scn:{noStalls:true}});
  try{
   await go(page,'autocal');await page.waitForTimeout(1700);
   await page.evaluate(({fuel,transition})=>{
    const screen=window.OmegasApp.autoCalCockpit;
    window.__transitionRoot=document.querySelector('.screen.active');
    const original=screen.api.projection();window.__transitionOriginal=original;
    const next=JSON.parse(JSON.stringify(original));next.referenceUsable=false;
    next.acquisitionZones[fuel]=[];
    next.liveAcquisitionEpoch={comparisonAllowed:false,petrolPending:fuel==='petrol',gasPending:fuel==='gas',petrolReferencePending:fuel==='petrol',gasReferencePending:fuel==='gas',petrolGeneration:fuel==='petrol'?1:0,gasGeneration:fuel==='gas'?1:0,nativeAutoMatchCount:transition==='automatch'?2:1};
    const keys=fuel==='petrol'?['PETR_INJ_TBP','PETR_MNFLD_PRESS_RV','PETR_INJ_TBUF','MNFLD_PRESS_BUF','NUM_BUF_UPD_PETR','ACQUIRED_ZONES_PETROL']:['GAS_MNFLD_PRESS_RV','PETR_INJ_TBUF_GAS','MNFLD_PRESS_BUF_GAS','NUM_BUF_UPD_GAS','ACQUIRED_ZONES_GAS'];
    for(const snap of [next.snapshot,next.nativeSnapshot])for(const field of snap.fields||[])if(keys.includes(field.key)){field.status='STALE_EPOCH';field.rawValues=[];field.physicalValues=[];}
    window.__transitionProjection=next;
    screen.api={...screen.api,projection:()=>window.__transitionProjection,actionStatus:()=>transition==='automatch'?{}:({action:fuel==='petrol'?'RESET_PETROL':'RESET_GAS',state:'CONFIRMED',busy:false})};screen.refresh();
   },{fuel,transition});
   // A mudança de mensagem pode alterar a altura disponível: concluir o layout
   // antes de medir atualizações com geometria estável, sem parar a leitura.
   await page.waitForTimeout(250);
   await page.evaluate(()=>{window.OmegasApp.autoCalCockpit.refresh();window.__transitionSvg=document.querySelector('#autocalReferenceChart svg');});
   assert.equal(await page.locator('#autocalReferenceChart svg').count(),1,'reinício mantém um gráfico com escala');
   assert.ok(await page.locator('#autocalReferenceChart .autocal-axis-tick-x').count()>0,'eixos permanecem legíveis na reaquisição');
   assert.equal(await page.locator('#autocalReferenceChart .autocal-reference-line.'+fuel).count(),0,'curva reiniciada não aparece como atual');
   if(fuel==='gas')assert.equal(await page.locator('#autocalReferenceChart .autocal-reference-line.petrol').count(),1,'reset GNV preserva gasolina');
   const {diagnostics,...live}=await page.evaluate(()=>{
    const s=window.OmegasApp.autoCalCockpit;const svg=window.__transitionSvg;
    const beforeKey=JSON.parse(s.epochChartKey),beforeSize=[s.epochChartHost.clientWidth,s.epochChartHost.clientHeight];
    s.refresh();s.refresh();
    const layer=svg.querySelector('[data-chart-live]');const scale=s.chartScale;
    const set=(fraction)=>{const t=s.store.get().telemetry;const ms=scale.xMin+(scale.xMax-scale.xMin)*fraction;
     s.store.patch({telemetry:{...t,valid:true,ageMs:0,telemetryAgeMs:0,sequence:(t.sequence||0)+1,live:{...t.live,petrol_ms:ms,load_bar:(scale.yMin+scale.yMax)/2,rpm:1500,fuel:'GNV'}}});s.renderLiveCursor();for(let i=0;i<30;i++)s.animateCursor(performance.now()+i*20);};
    set(.3);const before=layer.style.transform;set(.6);
    const afterKey=JSON.parse(s.epochChartKey);
    return {sameSvg:document.querySelector('#autocalReferenceChart svg')===svg,sameScreen:document.querySelector('.screen.active')===window.__transitionRoot,moved:before!==layer.style.transform,visible:!layer.hasAttribute('display'),diagnostics:{beforeSize,afterSize:[s.epochChartHost.clientWidth,s.epochChartHost.clientHeight],changedKeyParts:beforeKey.map((v,i)=>JSON.stringify(v)===JSON.stringify(afterKey[i])?null:i).filter(v=>v!==null)}};
   });
   console.log('TRANSITION '+transition+' '+JSON.stringify(diagnostics));
   assert.deepEqual(live,{sameSvg:true,sameScreen:true,moved:true,visible:true},'telemetria permanece viva sem remontar tela ou SVG');
   await audit(page,'autocal',672,'autocal-reinicio-'+transition);
   await page.evaluate(()=>{window.__transitionProjection=window.__transitionOriginal;window.OmegasApp.autoCalCockpit.refresh();});
   assert.equal(await page.locator('#autocalReferenceChart .autocal-reference-line.petrol').count(),1,'referência nova volta sem recarregar a tela');
   await audit(page,'autocal',672,'autocal-retomada-'+transition);assert.deepEqual(errors,[]);
  }finally{await browser.close();}
 }
 {
  const {browser,page,errors}=await open(pw.chromium,'connected',{viewport:{width:1280,height:672},scn:{noStalls:true}});
  try{
   await go(page,'refino');await page.waitForTimeout(1700);
   await page.evaluate(()=>{window.__refinoRoot=document.querySelector('.screen.active');window.__S.phase='VERIFICANDO';window.OmegasApp.refino.refresh(true,true);});
   assert.equal(await page.locator('#refinoPhaseChip').textContent(),'Verificando');
   assert.equal(await page.locator('[data-refino-primary]').isVisible(),false,'verificação não oferece gravação');
   const stable=await page.evaluate(()=>{
    const s=window.OmegasApp.refino;const svg=document.querySelector('#refinoChart svg');s.refresh(true,true);
    const layer=svg.querySelector('[data-chart-live]'),scale=s.chartScale;
    const set=fraction=>{const t=s.store.get().telemetry;s.store.patch({telemetry:{...t,valid:true,ageMs:0,telemetryAgeMs:0,sequence:(t.sequence||0)+1,live:{...t.live,petrol_ms:scale.xMin+(scale.xMax-scale.xMin)*fraction,load_bar:(scale.yMin+scale.yMax)/2,rpm:1500,fuel:'GNV'}}});s.renderLive();for(let i=0;i<30;i++)s.animateLive(performance.now()+i*20);};
    set(.3);const before=layer.style.transform;set(.6);
    return {sameSvg:document.querySelector('#refinoChart svg')===svg,sameScreen:document.querySelector('.screen.active')===window.__refinoRoot,moved:before!==layer.style.transform};
   });
   assert.deepEqual(stable,{sameSvg:true,sameScreen:true,moved:true},'Refino continua vivo durante verificação');
   await audit(page,'refino',672,'refino-transicao-verificando');assert.deepEqual(errors,[]);
  }finally{await browser.close();}
 }
 fs.writeFileSync(path.join(out,'metrics.json'),JSON.stringify({source:process.env.OMEGAS_SOURCE_SHA,proof:'Chromium, ponte falsa com fixtures reais; não é aparelho físico',metrics},null,2));
 console.log('ELASTIC_UI_RENDER=PASS '+metrics.length+' estados/áreas úteis');
})().catch(e=>{console.error(e);process.exitCode=1;});
