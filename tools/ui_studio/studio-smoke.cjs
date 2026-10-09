'use strict';
/** E2E Chrome required; no ECU commands, no silent skip. */
const assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const path=require('node:path');
const {setTimeout:delay}=require('node:timers/promises');
const {chromium}=require('playwright');
const ROOT=path.resolve(__dirname,'../..'),PORT=18765;
(async()=>{
 const python=process.env.PYTHON||(process.platform==='win32'?'python':'python3');
 const server=spawn(python,[path.join(__dirname,'server.py'),'--port',String(PORT)],{cwd:ROOT,stdio:['ignore','pipe','pipe']});
 let browser;
 try {
   let ready=false;
   for(let i=0;i<50;i++){try {const r=await fetch('http://127.0.0.1:'+PORT+'/__studio/health');if(r.ok){ready=true;break;}}catch(_){}await delay(200);}
   assert.ok(ready,'HTTP server is ready');
   const traversal=await fetch('http://127.0.0.1:'+PORT+'/tools/ui_studio/..%2f..%2fAGENTS.md');
   assert.equal(traversal.status,403,'preview must not expose files outside UI and Studio');
   browser=await chromium.launch({headless:true,executablePath:process.env.OMEGAS_CHROMIUM||undefined,args:['--no-sandbox']});
   const page=await browser.newPage({viewport:{width:1800,height:1030}});
   const errors=[];page.on('pageerror',e=>errors.push(e.message));
   await page.goto('http://127.0.0.1:'+PORT+'/tools/ui_studio/index.html');
   await page.waitForFunction(()=>window.__studio?.ready===true,{timeout:25000});
   const frame=page.frameLocator('#omegas-frame');
   assert.equal(await frame.locator('[data-route]').count(),8,'all 8 real routes');
   await page.locator('#route-autocal').click();
   await frame.locator('[data-screen="autocal"].active').waitFor();
   assert.equal(await frame.locator('#autocalScreenHost').count(),1,'real AutoCal host');
   assert.deepEqual(await page.locator('#omegas-frame').evaluate(el=>[el.width,el.height]),['1280','720']);
   const cssEvidence=await frame.locator('#app').evaluate(el=>({display:getComputedStyle(el).display,fonts:getComputedStyle(el).fontFamily,styles:[...document.styleSheets].length,viewport:document.documentElement.clientWidth}));
   assert.ok(cssEvidence.styles>=12,'real app stylesheets loaded '+JSON.stringify(cssEvidence));
   assert.notEqual(cssEvidence.display,'block','app CSS layout applied '+JSON.stringify(cssEvidence));
   assert.equal(cssEvidence.viewport,1280);
   await page.locator('#tool-inspect').click();
   await page.locator('#studio-stage-overlay').click({position:{x:500,y:250}});
   assert.notEqual(await page.locator('#selected-selector').innerText(),'Nenhum elemento');
   await page.locator('#note-input').fill('Ajustar espaçamento sem esconder botões');
   await page.locator('#note-save').click();
   assert.match(await page.locator('#change-list').innerText(),/Ajustar espaçamento/);
   await page.locator('#selected-hide').click();
   assert.match(await page.locator('#change-list').innerText(),/Ocultar/);
   await page.locator('#undo').click();
   assert.doesNotMatch(await page.locator('#change-list').innerText(),/Ocultar/);
   await page.locator('#tool-draw').click();
   await page.locator('#studio-stage-overlay').dragTo(page.locator('#studio-stage-overlay'),{sourcePosition:{x:240,y:210},targetPosition:{x:435,y:280}});
   assert.ok(await page.locator('#markup-layer polyline').count()>0,'annotations visible');
   const data=await page.evaluate(()=>window.__studio.exportSession());
   assert.equal(data.viewport.width,1280);
   assert.ok(data.changes.some(x=>x.kind==='note'));
   assert.ok(data.changes.some(x=>x.kind==='stroke'));
   await page.locator('#tool-interact').click();
   await frame.locator('[data-route="refino"]').click();
   await frame.locator('[data-screen="refino"].active').waitFor();
   assert.equal(await page.locator('#route-refino').evaluate(el=>el.classList.contains('active')),true,'studio follows real navigation');
   assert.equal(errors.length,0,errors.join(' / '));
   await page.screenshot({path:process.env.OMEGAS_STUDIO_SHOT||path.join(__dirname,'studio-smoke.png'),fullPage:true});
   console.log('OMEGASCINZA_STUDIO_E2E_PASS '+JSON.stringify({routes:8,changes:data.changes.length,errors:errors.length}));
 } finally {if(browser)await browser.close();server.kill();}
})().catch(e=>{console.error('OMEGASCINZA_STUDIO_E2E_FAIL',e.stack||e);process.exitCode=1;});
