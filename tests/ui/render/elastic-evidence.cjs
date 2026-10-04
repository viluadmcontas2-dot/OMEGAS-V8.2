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
  const critical=screen.querySelectorAll('[data-autocal-toggle],[data-autocal-action="RESET_GAS"],[data-autocal-action="RESET_PETROL"],[data-refino-primary],[data-refino-undo],#mapReadButton,#mapReviewButton,#mapAdjustmentValue,#curveReadButton,#curveReviewButton,#curveTargetFactor,[data-dash-refino]');
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
 fs.writeFileSync(path.join(out,'metrics.json'),JSON.stringify({source:process.env.OMEGAS_SOURCE_SHA,proof:'Chromium, ponte falsa com fixtures reais; não é aparelho físico',metrics},null,2));
 console.log('ELASTIC_UI_RENDER=PASS '+metrics.length+' estados/áreas úteis');
})().catch(e=>{console.error(e);process.exitCode=1;});
